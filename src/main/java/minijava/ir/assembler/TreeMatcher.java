package minijava.ir.assembler;

import static minijava.ir.utils.FirmUtils.modeToWidth;
import static minijava.ir.utils.FirmUtils.withBackEdges;
import static org.jooq.lambda.Seq.seq;

import firm.Mode;
import firm.TargetValue;
import firm.nodes.*;
import java.util.ArrayList;
import java.util.List;
import minijava.ir.assembler.instructions.*;
import minijava.ir.assembler.instructions.Add;
import minijava.ir.assembler.instructions.And;
import minijava.ir.assembler.instructions.Call;
import minijava.ir.assembler.instructions.Cmp;
import minijava.ir.assembler.instructions.Sub;
import minijava.ir.assembler.operands.AddressingMode;
import minijava.ir.assembler.operands.ImmediateOperand;
import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.OperandWidth;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.Register;
import minijava.ir.assembler.registers.VirtualRegister;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.MethodInformation;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.function.Function2;
import org.jooq.lambda.function.Function3;

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
    binaryOperator(node, Add::new);
  }

  @Override
  public void visit(firm.nodes.Address node) {
    mapping.markNeverDefined(node);
  }

  @Override
  public void visit(firm.nodes.And node) {
    binaryOperator(node, And::new);
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
      if (dest instanceof RegisterOperand) {
        instructions.add(new Mov(src, (RegisterOperand) dest));
      } else {
        instructions.add(new Mov(src, (MemoryOperand) dest));
      }
      arguments.add(dest);
    }
    instructions.add(new Call(calleeLabel, arguments));
    allocateStackSpace(-parameterRegionSize);
  }

  private void allocateStackSpace(int bytes) {
    ImmediateOperand size = new ImmediateOperand(OperandWidth.Quad, bytes);
    RegisterOperand sp = new RegisterOperand(OperandWidth.Quad, AMD64Register.SP);
    instructions.add(
        bytes > 0 ? new Sub(size, sp, AMD64Register.SP) : new Add(size, sp, AMD64Register.SP));
  }

  @Override
  public void visit(firm.nodes.Cmp node) {
    // We only have to produce the appropriate flags register changes.
    // Sharing a Mode b value if necessary is done in the InstructionSelector.
    Operand left = operandForNode(node.getLeft());
    Operand right = operandForNode(node.getRight());
    instructions.add(new Cmp(left, right));
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

  public ImmediateOperand imm(TargetValue tarval) {
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
    unaryOperator(
        node, (op, reg) -> new Mov(op.withChangedWidth(width), new RegisterOperand(width, reg)));
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
    unaryOperator(node, Neg::new);
  }

  @Override
  public void visit(firm.nodes.Mod node) {
    // Handled in projectDivOrMod
  }

  @Override
  public void visit(firm.nodes.Mul node) {
    binaryOperator(node, IMul::new);
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
    RegisterOperand dividend = cltd(operandForNode(node.getPred(1)));
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

  private RegisterOperand cltd(Operand op) {
    instructions.add(new Cltd(op.width));
    return new RegisterOperand(op.width, AMD64Register.A);
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
    binaryOperator(node, Sub::new);
  }

  /**
   * Currently returns a RegisterOperand, e.g. always something referencing a register. This can
   * later be an Operand, but then we'll have to handle the different cases.
   */
  private RegisterOperand operandForNode(firm.nodes.Node value) {
    OperandWidth width = modeToWidth(value.getMode());
    VirtualRegister register = mapping.registerForNode(value);
    if (mapping.getDefinition(register) == null) {
      // There was no definition before this invocation of TreeMatcher.match().
      // We have to generate code for value.
      value.accept(this);
      // This will have defined the value, but we only store the definition mapping after we
      // matched the whole sub-tree.
      // Note that this implies we duplicate definitions which haven't been prior to this call to
      // TreeMatcher.match().
    }
    return new RegisterOperand(width, register);
  }

  private void unaryOperator(
      firm.nodes.Node node,
      Function2<RegisterOperand, VirtualRegister, CodeBlockInstruction> factory) {
    assert node.getPredCount() == 1;
    RegisterOperand op = operandForNode(node.getPred(0));
    VirtualRegister result = mapping.registerForNode(node);
    instructions.add(factory.apply(op, result));
  }

  /**
   * Some binary operator that saves its output in the right argument. We need to model those with
   * an extra move instruction, as Linear scan expects 3-address code and instructions such as
   * Add/Sub destroy the RHS. Thus, we have to make sure we destroy the last use of the RHS.
   * Redundant moves can easily be deleted later on.
   */
  private void binaryOperator(
      firm.nodes.Binop node,
      Function3<Operand, RegisterOperand, VirtualRegister, CodeBlockInstruction> factory) {
    Operand left = operandForNode(node.getLeft());
    Operand right = operandForNode(node.getRight());
    RegisterOperand copiedRight = copyOperand(right);
    VirtualRegister result = mapping.registerForNode(node);
    instructions.add(factory.apply(left, copiedRight, result));
  }

  private RegisterOperand copyOperand(Operand src) {
    return moveIntoRegister(src, mapping.freshTemporary(src.width));
  }

  @NotNull
  private RegisterOperand moveIntoRegister(Operand src, Register copy) {
    RegisterOperand dest = new RegisterOperand(src.width, copy);
    instructions.add(new Mov(src, dest));
    return dest;
  }

  private RegisterOperand defineAsCopy(Operand src, Node value) {
    VirtualRegister register = mapping.registerForNode(value);
    OperandWidth width = modeToWidth(value.getMode());
    RegisterOperand dest = new RegisterOperand(width, register);
    instructions.add(new Mov(src.withChangedWidth(width), dest));
    return dest;
  }

  private void saveDefinitions() {
    for (Instruction definingInstruction : instructions) {
      System.out.println("definingInstruction = " + definingInstruction);
      for (VirtualRegister register :
          seq(definingInstruction.definitions()).ofType(VirtualRegister.class)) {
        System.out.println("Defining " + register + " at " + definingInstruction);
        mapping.setDefinition(register, definingInstruction);
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
