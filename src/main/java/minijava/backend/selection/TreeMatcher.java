package minijava.backend.selection;

import static minijava.ir.utils.FirmUtils.modeToWidth;

import firm.Mode;
import firm.TargetValue;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Phi;
import firm.nodes.Proj;
import firm.nodes.Start;
import java.util.ArrayList;
import java.util.List;
import minijava.backend.SystemVAbi;
import minijava.backend.instructions.*;
import minijava.backend.operands.AddressingMode;
import minijava.backend.operands.ImmediateOperand;
import minijava.backend.operands.MemoryOperand;
import minijava.backend.operands.Operand;
import minijava.backend.operands.OperandWidth;
import minijava.backend.operands.RegisterOperand;
import minijava.backend.registers.AMD64Register;
import minijava.backend.registers.VirtualRegister;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.MethodInformation;
import org.jooq.lambda.Seq;
import org.jooq.lambda.function.Function2;

class TreeMatcher extends NodeVisitor.Default {

  private final VirtualRegisterMapping mapping;
  private List<CodeBlockInstruction> instructions;

  TreeMatcher(VirtualRegisterMapping mapping) {
    this.mapping = mapping;
  }

  @Override
  public void defaultVisit(firm.nodes.Node node) {
    assert false : "TreeMatcher can't handle node " + node;
  }

  @Override
  public void visit(firm.nodes.Add node) {
    twoAddressInstruction(node, Add::new);
  }

  @Override
  public void visit(firm.nodes.Address node) {
    mapping.markNeverDefined(node);
  }

  @Override
  public void visit(firm.nodes.And node) {
    twoAddressInstruction(node, And::new);
  }

  @Override
  public void visit(firm.nodes.Bad node) {
    assert false : "Can't assemble node " + node;
  }

  @Override
  public void visit(firm.nodes.Block node) {
    assert false : "Can't generate instructions for a block " + node;
  }

  @Override
  public void visit(firm.nodes.Call call) {
    MethodInformation info = new MethodInformation(call);
    String calleeLabel = info.ldName;
    int parameterRegionSize = SystemVAbi.parameterRegionSize(info);
    allocateStackSpace(parameterRegionSize);
    List<Operand> arguments = new ArrayList<>(info.paramNumber);
    for (int i = 0; i < info.paramNumber; ++i) {
      // + 2 accounts for mem pred and callee address
      Operand src = operandForNode(call.getPred(i + 2));
      Operand dest = SystemVAbi.parameter(i, src.width);
      instructions.add(new Mov(src, dest));
      arguments.add(dest);
    }
    instructions.add(new Call(calleeLabel, arguments));
    allocateStackSpace(-parameterRegionSize);
  }

  private void allocateStackSpace(int bytes) {
    ImmediateOperand size = new ImmediateOperand(OperandWidth.Quad, bytes);
    RegisterOperand sp = new RegisterOperand(OperandWidth.Quad, AMD64Register.SP);
    if (bytes > 0) {
      instructions.add(new Sub(size, sp));
    } else if (bytes < 0) {
      instructions.add(new Add(size, sp));
    }
  }

  @Override
  public void visit(firm.nodes.Cmp node) {
    // We only have to produce the appropriate flags register changes.
    // Sharing a Mode b value if necessary is done in the InstructionSelector.
    Operand left = operandForNode(node.getLeft());
    Operand right = operandForNode(node.getRight());
    // node.getRelation() relates left to right, but Cmp relates right to left (by subtracting left from right).
    // This is why we flip the operands.
    instructions.add(new Cmp(right, left));
    // We generate a register for the mode b node by default. In cases where this isn't necessary (immediate Jcc),
    // we just delete the defining instruction.
    OperandWidth width = modeToWidth(Mode.getb());
    RegisterOperand op = new RegisterOperand(width, mapping.registerForNode(node));
    instructions.add(new Setcc(op, node.getRelation()));
  }

  @Override
  public void visit(firm.nodes.Const node) {
    defineAsCopy(imm(node.getTarval()), node);
  }

  private ImmediateOperand imm(TargetValue tarval) {
    Mode mode = tarval.getMode();
    OperandWidth width = modeToWidth(mode);
    if (mode.equals(Mode.getb())) {
      return new ImmediateOperand(width, tarval.isOne() ? 1 : 0);
    }
    return new ImmediateOperand(width, tarval.asLong());
  }

  @Override
  public void visit(firm.nodes.Conv node) {
    OperandWidth width = modeToWidth(node.getMode());
    Operand op = operandForNode(node.getPred(0));
    VirtualRegister reg = mapping.registerForNode(node);
    instructions.add(new Mov(op.withChangedWidth(width), new RegisterOperand(width, reg)));
  }

  @Override
  public void visit(firm.nodes.Div node) {
    // Handled in projectDivOrMod
  }

  @Override
  public void visit(firm.nodes.Load load) {
    // We handle the Proj on load instead.
  }

  @Override
  public void visit(firm.nodes.Minus node) {
    RegisterOperand op = operandForNode(node.getPred(0));
    Operand result = defineAsCopy(op, node);
    // Same trick as for TwoAddressInstructions, where we have to connect the input with the output operand.
    instructions.add(new Neg(result));
  }

  @Override
  public void visit(firm.nodes.Mod node) {
    // Handled in projectDivOrMod
  }

  @Override
  public void visit(firm.nodes.Mul node) {
    twoAddressInstruction(node, IMul::new);
  }

  @Override
  public void visit(Phi phi) {
    // These are handled by the instruction selector. We can be sure that the value of this will
    // reside in a register.
  }

  @Override
  public void visit(firm.nodes.Proj proj) {
    if (proj.getMode().equals(Mode.getM())) {
      // Memory edges are erased
      return;
    }

    assert !proj.getMode().equals(Mode.getX()) : "The TreeMatcher can't handle control flow.";

    Node pred = proj.getPred();
    switch (pred.getOpCode()) {
      case iro_Proj:
        // the pred is either a Call or a Start
        Node startOrCall = pred.getPred(0);
        switch (startOrCall.getOpCode()) {
          case iro_Start:
            projectArgument(proj, (firm.nodes.Start) startOrCall);
            break;
          case iro_Call:
            assert proj.getNum() == 0
                : "Projecting return value " + proj.getNum() + " on " + startOrCall;
            projectReturnValue(proj);
            break;
        }
        break;
      case iro_Load:
        projectLoad(proj, pred);
        break;
      case iro_Div:
      case iro_Mod:
        projectDivOrMod(proj, pred);
        break;
      case iro_Start:
      case iro_Call:
        // We ignore these, the projs on these are what's interesting
        break;
      default:
        assert false : "Can't handle Proj on " + pred;
    }
  }

  private void projectDivOrMod(Proj proj, Node node) {
    RegisterOperand dividend = cqto(operandForNode(node.getPred(1)));
    assert dividend.register == AMD64Register.A;
    Operand divisor = operandForNode(node.getPred(2));

    instructions.add(new IDiv(divisor));

    // We immediately copy the hardware register into a virtual register, so that register allocation can decide
    // what's best.
    if (node instanceof firm.nodes.Div) {
      defineAsCopy(new RegisterOperand(dividend.width, AMD64Register.A), proj);
    } else {
      assert node instanceof firm.nodes.Mod : "projectOrDivMod called something else: " + node;
      defineAsCopy(new RegisterOperand(dividend.width, AMD64Register.D), proj);
    }
  }

  private RegisterOperand cqto(Operand op) {
    RegisterOperand a = new RegisterOperand(op.width, AMD64Register.A);
    instructions.add(new Mov(op, a));
    instructions.add(new Cqto(op.width));
    return a;
  }

  private void projectLoad(Proj proj, Node pred) {
    Node ptr = pred.getPred(1);
    AddressingMode address = followIndirecion(ptr);
    OperandWidth width = modeToWidth(proj.getMode());
    defineAsCopy(new MemoryOperand(width, address), proj);
  }

  private AddressingMode followIndirecion(Node ptrNode) {
    // TODO: When making use of addressing modes, we want to get that instead here.
    RegisterOperand ptr = operandForNode(ptrNode);
    return AddressingMode.atRegister(ptr.register);
  }

  private void projectReturnValue(Proj proj) {
    OperandWidth width = modeToWidth(proj.getMode());
    RegisterOperand operand = new RegisterOperand(width, SystemVAbi.RETURN_REGISTER);
    defineAsCopy(operand, proj);
  }

  private void projectArgument(Proj proj, Start start) {
    assert proj.getPred().getPred(0).equals(start);
    OperandWidth width = modeToWidth(proj.getMode());
    Operand arg = SystemVAbi.argument(proj.getNum(), width);
    defineAsCopy(arg, proj);
  }

  @Override
  public void visit(firm.nodes.Store store) {
    AddressingMode address = followIndirecion(store.getPtr());
    Operand value = operandForNode(store.getValue());
    OperandWidth width = modeToWidth(store.getValue().getMode());
    MemoryOperand dest = new MemoryOperand(width, address);
    instructions.add(new Mov(value, dest));
  }

  @Override
  public void visit(firm.nodes.Sub node) {
    twoAddressInstruction(node, Sub::new);
  }

  /**
   * Currently returns a RegisterOperand, e.g. always something referencing a register. This can
   * later be an Operand, but then we'll have to handle the different cases.
   */
  private RegisterOperand operandForNode(firm.nodes.Node value) {
    // The only case register hasn't been defined yet is when value is a Phi node, in which case we won't generate code
    // (they are handled by the InstructionSelector). Otherwise, the topological order takes care of the definition
    // of operands.
    if (!mapping.hasRegisterAssigned(value)) {
      value.accept(this);
    }
    OperandWidth width = modeToWidth(value.getMode());
    VirtualRegister register = mapping.registerForNode(value);
    return new RegisterOperand(width, register);
  }

  /**
   * Some binary operator that saves its output in the right argument. We need to model those with
   * an extra move instruction, as Linear scan expects 3-address code and instructions such as
   * Add/Sub destroy the RHS. Thus, we have to make sure we destroy the last use of the RHS.
   * Redundant moves can easily be deleted later on.
   */
  private void twoAddressInstruction(
      firm.nodes.Binop node, Function2<Operand, Operand, TwoAddressInstruction> factory) {
    Operand left = operandForNode(node.getLeft());
    Operand right = operandForNode(node.getRight());
    // This is a little like cheating: To ensure the right argument (which is input and output) gets
    // assigned the same register, we write to it after its actual definition.
    RegisterOperand result = defineAsCopy(right, node);
    instructions.add(factory.apply(left, result));
  }

  private RegisterOperand defineAsCopy(Operand src, Node value) {
    VirtualRegister register = mapping.registerForNode(value);
    OperandWidth width = modeToWidth(value.getMode());
    RegisterOperand dest = new RegisterOperand(width, register);
    instructions.add(new Mov(src.withChangedWidth(width), dest));
    return dest;
  }

  private void saveDefinitions() {
    for (Instruction instruction : instructions) {
      for (VirtualRegister register :
          Seq.seq(instruction.defs()).map(use -> use.register).ofType(VirtualRegister.class)) {
        if (mapping.getDefinition(register) == null) {
          // There may be multiple instructions 'defining' a register (e.g. 2-adress code overwriting its right op).
          // This means we don't truly have SSA form, but nothing relies on the fact that the values don't change.
          // The only thing that matters is that they need to be assigned a single memory location.
          // We always take the first definition as the actual definition.
          mapping.setDefinition(register, instruction);
        }
      }
    }
  }

  public List<CodeBlockInstruction> match(firm.nodes.Node node) {
    return FirmUtils.withBackEdges(
        node.getGraph(),
        () -> {
          instructions = new ArrayList<>();
          node.accept(this);
          saveDefinitions();
          return instructions;
        });
  }
}
