package minijava.backend.selection;

import static minijava.backend.registers.AMD64Register.SP;
import static minijava.ir.utils.FirmUtils.modeToWidth;
import static org.jooq.lambda.Seq.seq;

import firm.BackEdges;
import firm.Mode;
import firm.TargetValue;
import firm.nodes.Div;
import firm.nodes.Load;
import firm.nodes.Mod;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Phi;
import firm.nodes.Proj;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import minijava.backend.SystemVAbi;
import minijava.backend.instructions.Add;
import minijava.backend.instructions.And;
import minijava.backend.instructions.Call;
import minijava.backend.instructions.Cmp;
import minijava.backend.instructions.CodeBlockInstruction;
import minijava.backend.instructions.Cqto;
import minijava.backend.instructions.Enter;
import minijava.backend.instructions.IDiv;
import minijava.backend.instructions.IMul;
import minijava.backend.instructions.Instruction;
import minijava.backend.instructions.Mov;
import minijava.backend.instructions.Neg;
import minijava.backend.instructions.Setcc;
import minijava.backend.instructions.Sub;
import minijava.backend.instructions.TwoAddressInstruction;
import minijava.backend.operands.AddressingMode;
import minijava.backend.operands.ImmediateOperand;
import minijava.backend.operands.MemoryOperand;
import minijava.backend.operands.Operand;
import minijava.backend.operands.OperandUtils;
import minijava.backend.operands.OperandWidth;
import minijava.backend.operands.RegisterOperand;
import minijava.backend.registers.AMD64Register;
import minijava.backend.registers.VirtualRegister;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.MethodInformation;
import minijava.ir.utils.NodeUtils;
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
    if (info.hasReturnValue) {
      // We have to handle these immediately, since the proj might be visited at a point where
      // eax was already overwritten.
      getProjOnSuccessorProj(call, 0)
          .ifPresent(
              proj -> {
                RegisterOperand ret =
                    new RegisterOperand(modeToWidth(proj.getMode()), SystemVAbi.RETURN_REGISTER);
                defineAsCopy(ret, proj);
              });
    }
  }

  private static Optional<Proj> getProjOnSuccessorProj(firm.nodes.Node startOrCall, int index) {
    return seq(BackEdges.getOuts(startOrCall))
        .map(be -> be.node)
        .ofType(Proj.class)
        .filter(proj -> proj.getMode().equals(Mode.getT()))
        .flatMap(t -> seq(BackEdges.getOuts(t)))
        .map(be -> be.node)
        .ofType(Proj.class)
        .filter(proj -> proj.getNum() == index)
        .findFirst();
  }

  private void allocateStackSpace(int bytes) {
    if (bytes > 0) {
      instructions.add(new Sub(OperandUtils.imm(bytes), OperandUtils.reg(SP)));
    } else if (bytes < 0) {
      instructions.add(new Add(OperandUtils.imm(bytes), OperandUtils.reg(SP)));
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
    RegisterOperand op = new RegisterOperand(node, mapping.registerForNode(node));
    instructions.add(new Setcc(op, node.getRelation()));
  }

  @Override
  public void visit(firm.nodes.Const node) {
    defineAsCopy(imm(node), node);
  }

  private ImmediateOperand imm(firm.nodes.Const node) {
    TargetValue tarval = node.getTarval();
    Mode mode = tarval.getMode();
    if (mode.equals(Mode.getb())) {
      return new ImmediateOperand(node, tarval.isOne() ? 1 : 0);
    }
    return new ImmediateOperand(node, tarval.asLong());
  }

  @Override
  public void visit(firm.nodes.Conv node) {
    Operand op = operandForNode(node.getPred(0));
    VirtualRegister reg = mapping.registerForNode(node);
    instructions.add(new Mov(op.withChangedNode(node), new RegisterOperand(node, reg)));
  }

  @Override
  public void visit(firm.nodes.Div node) {
    NodeUtils.getProjSuccessorWithNum(node, Div.pnRes)
        .ifPresent(
            proj -> {
              projectDivOrMod(proj, node);
            });
  }

  @Override
  public void visit(firm.nodes.Load load) {
    // We will completely omit the load if it isn't used anyway. This is OK per the spec, as a seg
    // fault counts as undefined behavior.
    NodeUtils.getProjSuccessorWithNum(load, Load.pnRes)
        .ifPresent(
            proj -> {
              AddressingMode address = followIndirecion(load.getPtr());
              defineAsCopy(new MemoryOperand(proj, address), proj);
            });
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
    NodeUtils.getProjSuccessorWithNum(node, Mod.pnRes)
        .ifPresent(
            proj -> {
              projectDivOrMod(proj, node);
            });
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
        // It's mostly not OK for side effects to handle result projs only when we visit them:
        // memory edges may force the side-effect to take place before we use their results.
        // As such, we handle projs when visiting the side effects.
      case iro_Div:
      case iro_Mod:
        pred.accept(this);
        break;
      case iro_Load:
      case iro_Proj:
        assert mapping.hasRegisterAssigned(proj)
            : "We rely upon side-effect handling when they occur: " + proj;
        break;
      case iro_Call:
      case iro_Start:
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
      defineAsCopy(new RegisterOperand(proj, AMD64Register.A), proj);
    } else {
      assert node instanceof firm.nodes.Mod : "projectOrDivMod called something else: " + node;
      defineAsCopy(new RegisterOperand(proj, AMD64Register.D), proj);
    }
  }

  private RegisterOperand cqto(Operand op) {
    RegisterOperand a = new RegisterOperand(op.irNode, AMD64Register.A);
    instructions.add(new Mov(op, a));
    instructions.add(new Cqto(op.width));
    return a;
  }

  private void projectLoad(Proj proj, Node pred) {
    Node ptr = pred.getPred(1);
    AddressingMode address = followIndirecion(ptr);
    defineAsCopy(new MemoryOperand(proj, address), proj);
  }

  private AddressingMode followIndirecion(Node ptrNode) {
    // TODO: When making use of addressing modes, we want to get that instead here.
    RegisterOperand ptr = operandForNode(ptrNode);
    return AddressingMode.atRegister(ptr.register);
  }

  @Override
  public void visit(firm.nodes.Start start) {
    instructions.add(new Enter());
    // We also have to 'save' hardware registers into virtual ones, so that they are spilled
    // appropriately. We probably don't need to do that for stack arguments, but this is something
    // to worry for later...
    int paramNumber = new MethodInformation(start.getGraph()).paramNumber;
    for (int i = 0; i < paramNumber; i++) {
      getProjOnSuccessorProj(start, i)
          .ifPresent(
              proj -> {
                defineAsCopy(SystemVAbi.argument(proj), proj);
              });
    }
  }

  @Override
  public void visit(firm.nodes.Store store) {
    AddressingMode address = followIndirecion(store.getPtr());
    Operand value = operandForNode(store.getValue());
    OperandWidth width = modeToWidth(store.getValue().getMode());
    MemoryOperand dest = new MemoryOperand(width, address);
    // The irNode is somewhat exceptional: we save the store node in it, so that later
    // the appropriate comment can be generated by looking up the dest operand of the mov.
    // We still have to pass in the width of the value, but the actual 'value' represented is
    // pointed to by the ptr.
    dest.irNode = store.getPtr();
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
    VirtualRegister register = mapping.registerForNode(value);
    return new RegisterOperand(value, register);
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
    // We also flip left and right arguments, as for the case of non-commutative operators like
    // Sub the left operand should be the subtrahend, not the minuend.
    // If we pursue BUPM later on, this will be gone anyway.
    RegisterOperand result = defineAsCopy(left, node);
    instructions.add(factory.apply(right, result));
  }

  private RegisterOperand defineAsCopy(Operand src, Node value) {
    VirtualRegister register = mapping.registerForNode(value);
    RegisterOperand dest = new RegisterOperand(value, register);
    instructions.add(new Mov(src, dest));
    return dest;
  }

  private void saveDefinitions() {
    for (Instruction instruction : instructions) {
      for (VirtualRegister register :
          seq(instruction.defs()).map(use -> use.register).ofType(VirtualRegister.class)) {
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
