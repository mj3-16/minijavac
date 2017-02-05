package minijava.ir.assembler;

import firm.Mode;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Proj;
import firm.nodes.Start;
import java.util.ArrayList;
import java.util.List;
import minijava.ir.assembler.instructions.Add;
import minijava.ir.assembler.instructions.And;
import minijava.ir.assembler.instructions.CLTD;
import minijava.ir.assembler.instructions.IDiv;
import minijava.ir.assembler.instructions.IMul;
import minijava.ir.assembler.instructions.Instruction;
import minijava.ir.assembler.instructions.Mov;
import minijava.ir.assembler.instructions.Neg;
import minijava.ir.assembler.instructions.Ret;
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
import org.jooq.lambda.function.Function2;
import org.jooq.lambda.function.Function3;

class TreeMatcher extends NodeVisitor.Default {

  private final VirtualRegisterMapping mapping;
  private List<Instruction> instructions;

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
  public void visit(firm.nodes.Anchor node) {
    // We ignore these
  }

  @Override
  public void visit(firm.nodes.Call node) {
    assert false;
  }

  @Override
  public void visit(firm.nodes.Cmp node) {
    assert false;
  }

  @Override
  public void visit(firm.nodes.Cond node) {
    assert false;
  }

  @Override
  public void visit(firm.nodes.Const node) {
    OperandWidth width = FirmUtils.modeToWidth(node.getMode());
    long value = node.getTarval().asLong();
    ImmediateOperand operand = new ImmediateOperand(width, value);
    defineAsCopy(operand, node);
  }

  @Override
  public void visit(firm.nodes.Conv node) {
    OperandWidth width = FirmUtils.modeToWidth(node.getMode());
    unaryOperator(
        node, (op, reg) -> new Mov(op.withChangedWidth(width), new RegisterOperand(width, reg)));
  }

  @Override
  public void visit(firm.nodes.Div node) {
    // Handled in projectDivOrMod
  }

  @Override
  public void visit(firm.nodes.End node) {
    // Don't do anything (?)
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
  public void visit(firm.nodes.NoMem node) {
    // Memory edges are erased
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
      case iro_Div:
      case iro_Mod:
        projectDivOrMod(proj, pred);
      default:
        assert false : "Can't handle Proj on " + pred;
    }
  }

  private void projectDivOrMod(Proj proj, Node node) {
    // We have to copy the left operand into a temporary register, so we can unleash the register
    // constraint on RAX.
    RegisterOperand left = copyOperand(cltd(operandForNode(node.getPred(0))));
    Operand right = operandForNode(node.getPred(1));
    setConstraint(left, AMD64Register.A);

    VirtualRegister quotient;
    VirtualRegister remainder;
    if (node instanceof firm.nodes.Div) {
      quotient = mapping.registerForNode(proj);
      remainder = mapping.freshTemporary();
    } else {
      assert node instanceof firm.nodes.Mod;
      quotient = mapping.freshTemporary();
      remainder = mapping.registerForNode(proj);
    }
    quotient.constraint = AMD64Register.A;
    remainder.constraint = AMD64Register.D;

    instructions.add(new IDiv(left, right, quotient, remainder));
  }

  private RegisterOperand cltd(Operand op) {
    RegisterOperand value = copyOperand(op);
    setConstraint(value, AMD64Register.A);
    VirtualRegister resultLow = mapping.freshTemporary();
    resultLow.constraint = AMD64Register.A;
    VirtualRegister resultHigh = mapping.freshTemporary();
    resultHigh.constraint = AMD64Register.D;
    instructions.add(new CLTD(value, resultLow, resultHigh));
    return new RegisterOperand(op.width, resultLow);
  }

  private void projectLoad(Proj proj, Node pred) {
    Node ptr = pred.getPred(1);
    AddressingMode address = followIndirecion(ptr);
    OperandWidth width = FirmUtils.modeToWidth(proj.getMode());
    defineAsCopy(new MemoryOperand(width, address), proj);
  }

  private AddressingMode followIndirecion(Node ptrNode) {
    // TODO: When making use of addressing modes, we want to get that instead here.
    RegisterOperand ptr = operandForNode(ptrNode);
    return AddressingMode.atRegister(ptr.register);
  }

  private void projectReturnValue(Proj proj) {
    OperandWidth width = FirmUtils.modeToWidth(proj.getMode());
    RegisterOperand operand = new RegisterOperand(width, SystemVAbi.RETURN_REGISTER);
    defineAsCopy(operand, proj);
  }

  private void projectArgument(Proj proj, Start start) {
    assert proj.getPred().getPred(0).equals(start);
    OperandWidth width = FirmUtils.modeToWidth(proj.getMode());
    Operand arg = SystemVAbi.argument(proj.getNum(), width);
    defineAsCopy(arg, proj);
  }

  @Override
  public void visit(firm.nodes.Return node) {
    if (node.getPredCount() > 1) {
      // We have to move the result into a temporary register constrained to RAX.
      RegisterOperand result = copyOperand(operandForNode(node.getPred(1)));
      setConstraint(result, AMD64Register.A);
    }
    instructions.add(new Ret());
  }

  private static AMD64Register setConstraint(RegisterOperand result, AMD64Register constraint) {
    return ((VirtualRegister) result.register).constraint = constraint;
  }

  @Override
  public void visit(firm.nodes.Start node) {
    // Needs to prepend the prologue, but this is the not the right place.
  }

  @Override
  public void visit(firm.nodes.Store store) {
    AddressingMode address = followIndirecion(store.getPtr());
    Operand value = operandForNode(store.getValue());
    OperandWidth width = FirmUtils.modeToWidth(store.getValue().getMode());
    MemoryOperand dest = new MemoryOperand(width, address);
    instructions.add(new Mov(value, dest));
  }

  @Override
  public void visit(firm.nodes.Sub node) {
    binaryOperator(node, Sub::new);
  }

  @Override
  public void visit(firm.nodes.Sync node) {
    // Memory edges are erased
  }

  /**
   * Currently returns a RegisterOperand, e.g. always something referencing a register. This can
   * later be an Operand, but then we'll have to handle the different cases.
   */
  private RegisterOperand operandForNode(firm.nodes.Node value) {
    OperandWidth width = FirmUtils.modeToWidth(value.getMode());
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
      firm.nodes.Node node, Function2<Operand, Register, Instruction> factory) {
    assert node.getPredCount() == 1;
    Operand op = operandForNode(node.getPred(0));
    VirtualRegister result = mapping.registerForNode(node);
    instructions.add(factory.apply(op, result));
  }

  /**
   * Some binary operator that saves its result in the right argument. We need to model those with
   * an extra move instruction, as Linear scan expects 3-address code. Redundant moves can easily be
   * deleted later on.
   */
  private void binaryOperator(
      firm.nodes.Binop node, Function3<Operand, RegisterOperand, Register, Instruction> factory) {
    Operand left = operandForNode(node.getLeft());
    Operand right = operandForNode(node.getRight());
    RegisterOperand copiedRight = copyOperand(right);
    VirtualRegister result = mapping.registerForNode(node);
    instructions.add(factory.apply(left, copiedRight, result));
  }

  private RegisterOperand copyOperand(Operand src) {
    VirtualRegister copy = mapping.freshTemporary();
    RegisterOperand dest = new RegisterOperand(src.width, copy);
    instructions.add(new Mov(src, dest));
    return dest;
  }

  private RegisterOperand defineAsCopy(Operand src, Node value) {
    VirtualRegister register = mapping.registerForNode(value);
    OperandWidth width = FirmUtils.modeToWidth(value.getMode());
    RegisterOperand dest = new RegisterOperand(width, register);
    instructions.add(new Mov(src.withChangedWidth(width), dest));
    return dest;
  }

  private void saveDefinitions() {
    for (Instruction definition : instructions) {
      definition.defined.forEach(
          result -> mapping.setDefinition((VirtualRegister) result, definition));
    }
  }

  public List<Instruction> match(firm.nodes.Node node) {
    instructions = new ArrayList<>();
    node.accept(this);
    saveDefinitions();
    return instructions;
  }
}
