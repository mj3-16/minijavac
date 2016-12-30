package minijava.ir.assembler;

import static minijava.ir.utils.FirmUtils.getMethodLdName;

import com.sun.jna.Platform;
import firm.*;
import firm.nodes.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import minijava.ir.NameMangler;
import minijava.ir.Types;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.block.CodeSegment;
import minijava.ir.assembler.block.Segment;
import minijava.ir.assembler.instructions.*;
import minijava.ir.assembler.instructions.Add;
import minijava.ir.assembler.instructions.And;
import minijava.ir.assembler.instructions.Call;
import minijava.ir.assembler.instructions.Cmp;
import minijava.ir.assembler.instructions.Div;
import minijava.ir.assembler.instructions.Jmp;
import minijava.ir.assembler.instructions.Mul;
import minijava.ir.assembler.instructions.Sub;
import minijava.ir.assembler.location.Location;
import minijava.ir.assembler.location.RegRelativeLocation;
import minijava.ir.assembler.location.Register;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.MethodInformation;

/**
 * Generates GNU assembler for a graph
 *
 * <p>
 *
 * <p>One of the goals of this implementation is to produce well documented assembly code.
 *
 * <p>
 *
 * <p>Important: it currently ignores any {@link firm.nodes.Conv} nodes
 */
public class AssemblerGenerator extends NodeVisitor.Default {

  private final Graph graph;
  private final MethodInformation info;
  private final SimpleNodeAllocator allocator;
  private CodeSegment segment;
  private Map<Integer, CodeBlock> blocksToCodeBlocks;
  private static Map<Phi, Boolean> isPhiProneToLostCopies;
  private final boolean useABIConformCalling;
  private Map<Register, Location> temporaryParamRegLocation = new HashMap();

  public AssemblerGenerator(Graph graph) {
    this(graph, true);
  }

  public AssemblerGenerator(Graph graph, boolean useABIConformCalling) {
    this.graph = graph;
    this.info = new MethodInformation(graph);
    this.allocator = new SimpleNodeAllocator(graph, useABIConformCalling);
    isPhiProneToLostCopies = new HashMap<>();
    this.useABIConformCalling = useABIConformCalling;
    if (useABIConformCalling) {
      for (int i = 0; i < Register.methodArgumentQuadRegisters.size(); i++) {
        temporaryParamRegLocation.put(
            Register.methodArgumentQuadRegisters.get(i), allocator.createNewTemporaryVariable());
      }
    }
  }

  public Segment generateSegmentForGraph() {
    blocksToCodeBlocks = new HashMap<>();
    segment = new CodeSegment(new ArrayList<>(), new ArrayList<>());
    segment.addComment(String.format("Code segment for method %s", info.name));
    blocksToCodeBlocks = new HashMap<>();
    BackEdges.enable(graph);
    graph.walkTopological(this);
    prependStartBlockWithPrologue();
    segment.addComment(allocator.getActivationRecordInfo());
    return segment;
  }

  private CodeBlock getCodeBlock(Block block) {
    if (!blocksToCodeBlocks.containsKey(block.getNr())) {
      blocksToCodeBlocks.put(block.getNr(), new CodeBlock(getLabelForBlock(block)));
      segment.addBlock(blocksToCodeBlocks.get(block.getNr()));
    }
    return blocksToCodeBlocks.get(block.getNr());
  }

  private String getLabelForBlock(Block block) {
    if (block.getNr() == graph.getStartBlock().getNr()) {
      return info.ldName;
    }
    String ldFormat;
    if (Platform.isLinux()) {
      ldFormat = ".L%d_%s";
    } else {
      ldFormat = "L%d_%s";
    }
    return String.format(ldFormat, block.getNr(), info.ldName);
  }

  /** Get the code block for a given firm node (for the block the node belongs to) */
  private CodeBlock getCodeBlockForNode(Node node) {
    if (node.getBlock() == null) {
      // the passed node is actually a block
      return getCodeBlock((Block) node);
    }
    return getCodeBlock((Block) node.getBlock());
  }

  @Override
  public void visit(firm.nodes.Add node) {
    addBinaryInstruction(Add::new, node);
  }

  @Override
  public void visit(firm.nodes.Sub node) {
    addBinaryInstruction(Sub::new, node);
  }

  @Override
  public void visit(firm.nodes.Mul node) {
    addBinaryInstruction(Mul::new, node);
  }

  @Override
  public void visit(firm.nodes.Div node) {
    visitDivAndMod(node, true);
  }

  @Override
  public void visit(firm.nodes.Mod node) {
    visitDivAndMod(node, false);
  }

  private void visitDivAndMod(Node node, boolean isDiv) {
    Location res = allocator.getResultLocation(node);
    CodeBlock block = getCodeBlockForNode(node);
    List<Argument> args = allocator.getArguments(node);
    assert args.size() == 2;
    Argument firstArg = args.get(0);
    Argument secondArg = args.get(1);
    // move the second argument to a register too (requirement of the idiv instruction)
    Register secondArgIm = Register.EBX;
    block.add(new Mov(secondArg, secondArgIm));
    block.add(
        new Mov(firstArg, Register.EAX)
            .com("copy the first argument (the dividend) into the EAX register"));
    block.add(new CLTD().com("and convert it from 32 Bits to 64 Bits"));
    block.add(
        new Div(secondArgIm)
            .com("the quotient is now in the EAX register and the remainder in the EDX register")
            .firm(node));
    block.add(new Mov(isDiv ? Register.EAX : Register.EDX, res));
  }

  @Override
  public void visit(firm.nodes.Not node) {
    List<Argument> args = allocator.getArguments(node);
    assert args.size() == 1;
    Argument arg = args.get(0);
    Location res = allocator.getResultLocation(node);
    CodeBlock block = getCodeBlockForNode(node);
    if (arg != res) {
      // if the argument isn't equal to the result location
      // then we have to store the argument in a register
      // and copy the register's content into the result location
      // after the operation
      // this is caused by the GNU assembler for amd64 being a two adress
      // format
      Register intermediateReg = Register.EAX;
      block.add(
          new Mov(arg, intermediateReg)
              .com(
                  "Store the first argument in an intermediate register, as "
                      + "the assembler uses a two adress format (the first argument "
                      + "has to be the result location)"));
      block.add(new Neg(intermediateReg).firm(node));
      block.add(new Mov(intermediateReg, res));
    } else {
      // everything is fine here
      block.add(new Neg(arg).firm(node));
    }
  }

  private void addBinaryInstruction(
      BiFunction<Argument, Argument, Instruction> creator, Node node) {
    List<Argument> args = allocator.getArguments(node);
    assert args.size() == 2;
    // swap arguments because GNU assembler has them in reversed order (compared to Intel assembler)
    Argument firstArg = args.get(1);
    Argument secondArg = args.get(0);
    Location res = allocator.getResultLocation(node);
    CodeBlock block = getCodeBlockForNode(node);
    block.add(new Mov(firstArg, Register.EAX));
    block.add(new Mov(secondArg, Register.EBX));
    block.add(creator.apply(Register.EAX, Register.EBX).firm(node));
    block.add(new Mov(Register.EBX, res));
  }

  /**
   * The prepended prolog backups the old base pointer, sets the new and allocates bytes on the
   * stack for the activation record.
   */
  private void prependStartBlockWithPrologue() {
    CodeBlock startBlock = getCodeBlock(graph.getStartBlock());
    List<Instruction> prepended = new ArrayList<>();
    prepended.add(new Push(Register.BASE_POINTER).com("Backup old base pointer"));
    prepended.add(
        new Mov(Register.STACK_POINTER, Register.BASE_POINTER)
            .com("Set base pointer for new activation record"));
    prepended.add(new AllocStack(allocator.getActivationRecordSize()));
    for (int i = 0;
        i < Math.min(Register.methodArgumentQuadRegisters.size(), info.paramNumber);
        i++) {
      prepended.add(
          new Mov(
              Register.methodArgumentQuadRegisters.get(i),
              allocator.getRegPassedParameterLocation(i)));
    }
    startBlock.prepend(prepended.toArray(new Instruction[0]));
  }

  @Override
  public void visit(Return node) {
    CodeBlock codeBlock = getCodeBlockForNode(node);
    if (node.getPredCount() > 1) {
      // we only need this if the return actually returns a value
      List<Argument> args = allocator.getArguments(node);
      assert args.size() == 1;
      Argument arg = args.get(0);
      if (!(Register.RETURN_REGISTER.equals(
          arg))) { // if the argument isn't in the correct register
        codeBlock.add(
            new Mov(arg, Register.RETURN_REGISTER)
                .com("Copy the return value into the correct register"));
      }
    }
    // insert the epilogue
    codeBlock.add(
        new Mov(Register.BASE_POINTER, Register.STACK_POINTER)
            .com("Copy base pointer to stack pointer (free stack)"));
    codeBlock.add(new Pop(Register.BASE_POINTER).com("Restore previous base pointer"));
    codeBlock.add(
        new Ret().com("Jump to return address (and remove it from the stack)").firm(node));
  }

  @Override
  public void visit(firm.nodes.Call node) {
    String ldName = getMethodLdName(node);
    if (useABIConformCalling
        || ldName.equals(NameMangler.mangledPrintIntMethodName())
        || ldName.equals(NameMangler.mangledWriteIntMethodName())
        || ldName.equals(NameMangler.mangledFlushMethodName())
        || ldName.equals(NameMangler.mangledReadIntMethodName())) {
      visitABIConformCall(node);
    } else {
      // simple method calls
      visitMethodCall(ldName, node);
    }
  }

  private void visitABIConformCall(firm.nodes.Call node) {
    MethodInformation info = new MethodInformation(node);
    List<Argument> args = allocator.getArguments(node);
    CodeBlock block = getCodeBlockForNode(node);
    // the first six arguments are passed via registers
    // we don't have to backup them as we only use their copies somewhere on the stack
    // that we created at the beginning of the methods assembly
    for (int i = 0; i < Math.min(args.size(), Register.methodArgumentQuadRegisters.size()); i++) {
      Register reg = Register.methodArgumentQuadRegisters.get(i);
      if (hasTypeLongWidth(info.type.getParamType(0))) {
        // clear the whole register
        block.add(new Mov(new ConstArgument(0), reg));
        // write to the lower 32 bits
        block.add(new Mov(args.get(i), Register.getLongVersion(reg)));
      } else {
        block.add(new Mov(args.get(i), reg));
      }
    }
    // the 64 ABI requires the stack to aligned to 16 bytes
    block.add(new Push(Register.STACK_POINTER).com("Save old stack pointer"));
    block.add(
        new Push(new RegRelativeLocation(Register.STACK_POINTER, 0))
            .com("Save the stack pointer again because of alignment issues"));
    block.add(
        new And(new ConstArgument(-0x10), Register.STACK_POINTER)
            .com("Align the stack pointer to 16 bytes"));
    for (int i = args.size() - 1; i >= Register.methodArgumentQuadRegisters.size(); i--) {
      block.add(new Push(args.get(i)));
    }
    block.add(new Call(getMethodLdName(node)).com("Call the external function").firm(node));
    block.add(
        new DeallocStack(
            Math.max(0, args.size() - Register.methodArgumentQuadRegisters.size()) * 8));
    block.add(
        new Mov(new RegRelativeLocation(Register.STACK_POINTER, 8), Register.STACK_POINTER)
            .com("Restore old stack pointer"));
    if (info.hasReturnValue) {
      block.add(new Mov(Register.RETURN_REGISTER, allocator.getResultLocation(node)));
    }
  }

  private boolean hasTypeLongWidth(Type type) {
    return type instanceof PrimitiveType && !(type.getMode().equals(Types.PTR_TYPE.getMode()));
  }

  private void visitMethodCall(String ldName, firm.nodes.Call node) {
    CodeBlock block = getCodeBlockForNode(node);
    // the arguments start at predecessor number 1
    List<Argument> args = allocator.getArguments(node);
    // push the arguments in reversed order on the stack
    for (int i = args.size() - 1; i >= 0; i--) {
      block.add(new Push(args.get(i)));
    }
    // call the method
    block.add(new Call(ldName).firm(node));
    // free space on the stack (the arguments are needed any more)
    // assumes that a stack slot occupies 8 bytes
    block.add(new DeallocStack(args.size() * 8));
    // copy the result from register RAX into its location
    block.add(new Mov(Register.RETURN_REGISTER, allocator.getResultLocation(node)));
  }

  private Argument getOnlyArgument(Node node) {
    List<Argument> args = allocator.getArguments(node);
    assert args.size() == 1;
    return args.get(0);
  }

  @Override
  public void visit(Block node) {
    CodeBlock codeBlock = getCodeBlock(node);
    // we only handle control flow edges here
    for (Node pred : node.getPreds()) {
      CodeBlock predCodeBlock = getCodeBlockForNode(pred);
      // pred is a predecessor node but not a block
      if (pred instanceof Proj) {
        if (pred.getMode() == Mode.getM()) {
          continue;
        }
        // we might do unnecessary work here, but the {@link CodeBlock} instance takes care of it

        // this edge comes from a conditional jump
        Proj proj = (Proj) pred;
        boolean isTrueEdge = proj.getNum() == 1;
        // the condition
        Cond cond = (Cond) proj.getPred();

        if (cond.getSelector() instanceof firm.nodes.Cmp) {
          // we ignore it as we're really interested in the preceding compare node
          firm.nodes.Cmp cmp = (firm.nodes.Cmp) cond.getSelector();

          List<Argument> args = allocator.getArguments(cmp);
          assert args.size() == 2;
          Argument left = args.get(0);
          Argument right = args.get(1);

          if (right instanceof ConstArgument) {
            // dirty hack as the cmp instruction doesn't allow constants as a right argument
            Location newRight;
            if (left == Register.EAX) {
              newRight = Register.EBX;
            } else {
              newRight = Register.EAX;
            }
            predCodeBlock.addToCmpOrJmpSupportInstructions(new Mov(right, newRight));
            right = newRight;
          } else if (left instanceof RegRelativeLocation && right instanceof RegRelativeLocation) {
            // use dirty hack to prevent cmp instructions with too many memory locations
            // store the left argument in the EAX register
            predCodeBlock.addToCmpOrJmpSupportInstructions(new Mov(left, Register.EAX));
            left = Register.EAX;
          }

          // dirty hack: store the left argument in a register too
          // why? nobody knows. it's amd64 assembler…
          predCodeBlock.addToCmpOrJmpSupportInstructions(new Mov(left, Register.EBX));
          left = Register.EBX;

          // add a compare instruction that compares both arguments
          // we have to swap the arguments of the cmp instruction
          // why? because of GNU assembler…
          predCodeBlock.setCompare((Cmp) new Cmp(right, left).firm(cmp));

          if (isTrueEdge) {
            // choose the right jump instruction
            Instruction jmp = null;
            switch (cmp.getRelation()) {
              case Less:
                jmp = new JmpLess(codeBlock);
                break;
              case LessEqual:
                jmp = new JmpLessOrEqual(codeBlock);
                break;
              case Greater:
                jmp = new JmpGreater(codeBlock);
                break;
              case GreaterEqual:
                jmp = new JmpGreaterOrEqual(codeBlock);
                break;
              case Equal:
                jmp = new JmpEqual(codeBlock);
                break;
              default:
                throw new UnsupportedOperationException();
            }
            // use the selected conditional jump
            predCodeBlock.addConditionalJump((Jmp) jmp.firm(pred));
          } else {
            // use an unconditional jump
            predCodeBlock.setUnconditionalJump((Jmp) new Jmp(codeBlock).firm(pred));
          }
        } else if (cond.getSelector() instanceof Phi) {
          // we should have a Phi b node here
          Phi bPhi = (Phi) cond.getSelector();
          Location res = allocator.getLocation(bPhi);
          for (int i = 0; i < bPhi.getPredCount(); i++) {
            Node inputNode = bPhi.getPred(i);
            // the i.th block belongs to the i.th input edge of the phi node
            Block block = (Block) bPhi.getBlock().getPred(i).getBlock();
            CodeBlock inputCodeBlock = getCodeBlock(block);
            if (inputNode instanceof firm.nodes.Cmp) {
              if (!inputCodeBlock.hasCompare()) {
                // we have to add one
                // a set without a cmp doesn't make any sense
                firm.nodes.Cmp cmp = (firm.nodes.Cmp) inputNode;
                Argument leftArg = allocator.getAsArgument(cmp.getLeft());
                Argument rightArg = allocator.getAsArgument(cmp.getRight());
                Register immLeft = Register.EAX;
                Register immRight = Register.EBX;
                // copy the values into registers
                inputCodeBlock.addToCmpOrJmpSupportInstructions(
                    new Mov(leftArg, immLeft).firm(cmp.getLeft()));
                inputCodeBlock.addToCmpOrJmpSupportInstructions(
                    new Mov(rightArg, immRight).firm(cmp.getRight()));
                // compare the value
                // swap its arguments, GNU assembler...
                inputCodeBlock.setCompare((Cmp) new Cmp(immRight, immLeft).firm(cmp));
              }
              // zero the result location
              inputCodeBlock.addToAfterCompareInstructions(new Mov(new ConstArgument(0), res));
              // set the value to one if condition did hold true
              // TODO: really?   problem we cannot use the set instruction more than once for a given cmp
              inputCodeBlock.addToAfterCompareInstructions(
                  new Set(((firm.nodes.Cmp) inputNode).getRelation(), res).firm(bPhi));
            } else {
              Register intermediateReg = Register.EAX;
              inputCodeBlock.add(
                  new Mov(allocator.getAsArgument(inputNode), intermediateReg).firm(inputNode));
              inputCodeBlock.add(new Mov(intermediateReg, res).firm(bPhi));
            }
          }
          // we have to use the Phis value now
          // first we copy its value in a register
          Register phiVal = Register.EAX;
          predCodeBlock.addToCmpOrJmpSupportInstructions(new Mov(res, phiVal).firm(bPhi));
          // no we compare it with 1 (== true)
          predCodeBlock.setCompare((Cmp) new Cmp(new ConstArgument(1), phiVal).firm(cond));
          if (isTrueEdge) {
            // we jump to the true block if both cmps arguments were equal
            predCodeBlock.addConditionalJump((Jmp) new JmpEqual(codeBlock).firm(proj));
          } else {
            // we jump just to the false node by default
            predCodeBlock.setUnconditionalJump((Jmp) new Jmp(codeBlock).firm(proj));
          }
        } else if (cond.getSelector() instanceof Const) {
          Const constCond = (Const) cond.getSelector(); // true or false
          if (constCond.getTarval().isOne() == isTrueEdge) {
            // take the branch
            predCodeBlock.setUnconditionalJump((Jmp) new Jmp(codeBlock).firm(cond));
          }
        }
      } else {
        // an unconditional jump
        predCodeBlock.setDefaultUnconditionalJump((Jmp) new Jmp(codeBlock).firm(pred));
      }
    }
  }

  @Override
  public void visit(Phi node) {
    boolean isPhiB = node.getMode().equals(Mode.getb());
    if (isPhiB) {
      // we look for other phis that have this node as a predecessor
      // if doesn't have, than we deal with it in the visit(Block) method
      boolean phiLater = false;
      for (BackEdges.Edge edge : BackEdges.getOuts(node)) {
        if (edge.node instanceof Phi) {
          phiLater = true;
          break;
        }
      }
      if (!phiLater) {
        return;
      }
    }
    if (node.getMode().equals(Mode.getM())) {
      // we don't have to deal with memory dependencies here
      return;
    }
    Location res = allocator.getResultLocation(node);
    Location tmpLocation = null;
    if (isPhiProneToLostCopies(node)) {
      // we only need a temporary variable if the phi is prone to lost copy errors
      tmpLocation = allocator.createNewTemporaryVariable();
      // we have copy the temporarily modified value into phis location
      // at the start of the block that the phi is part of
      CodeBlock phisBlock = getCodeBlockForNode(node);
      Register intermediateReg = Register.EAX;
      // we have to use a register to copy the value between two locations
      phisBlock.addBlockStartInstruction(new Mov(tmpLocation, intermediateReg));
      phisBlock.addBlockStartInstruction(new Mov(intermediateReg, res));
    }
    Block block = (Block) node.getBlock();
    for (int i = 0; i < block.getPredCount(); i++) {
      // the i.th block belongs to the i.th input edge of the phi node
      Node inputNode = node.getPred(i);
      // we get the correct block be taking the predecessors of the block of the phi node
      Block inputBlock = (Block) block.getPred(i).getBlock();
      CodeBlock inputCodeBlock = getCodeBlock(inputBlock);
      Argument arg = allocator.getAsArgument(inputNode);
      // we store the argument in a register
      Register intermediateReg = Register.EAX;
      Instruction phiHelperMov = new Mov(arg, intermediateReg).firm(inputNode);
      Instruction phiMov;
      if (isPhiProneToLostCopies(node)) {
        // if this phi is prone to lost copies then we copy the value only
        // into the temporary variable
        phiMov = new Mov(intermediateReg, tmpLocation).firm(node);
      } else {
        // we copy it into Phis location
        phiMov = new Mov(intermediateReg, res).firm(node);
      }
      if (isPhiB && inputNode instanceof firm.nodes.Cmp) {
        // we have to insert the mov instructions behind (!) the compare
        // first we have to make sure that the intermediate register is really zero
        inputCodeBlock.addPhiBHelperInstruction(
            new Mov(new ConstArgument(0), intermediateReg).firm(node));
        // than we have to use the set instruction to set the registers value to one if the condition is true
        // important note: we can only set the lowest 8 bit of the intermediate register
        inputCodeBlock.addPhiBHelperInstruction(
            new Set(
                    ((firm.nodes.Cmp) inputNode).getRelation(),
                    Register.getByteVersion(intermediateReg))
                .firm(inputNode));
        // we copy the intermediate value to its final destination
        inputCodeBlock.addPhiBHelperInstruction(phiMov);
      } else {
        // compares don't matter and therefore we copy the value before the (possible) comparison takes place
        inputCodeBlock.addPhiHelperInstruction(phiHelperMov);
        inputCodeBlock.addPhiHelperInstruction(phiMov);
      }
    }
  }

  @Override
  public void visit(Load node) {
    // firm graph:
    // [Ptr node, might be a calculation!] <- [Load] <- [Proj res]
    Argument arg = allocator.getAsArgument(node.getPtr());
    Location res = allocator.getResultLocation(node);
    CodeBlock block = getCodeBlockForNode(node);
    Register intermediateReg = Register.EAX;
    // load the pointer in a register
    block.add(new Mov(arg, intermediateReg).firm(node.getPtr()));
    // copy the value the pointer points to in a register
    block.add(new Mov(new RegRelativeLocation(intermediateReg, 0), intermediateReg).firm(node));
    // store the result
    block.add(new Mov(intermediateReg, res));
  }

  @Override
  public void visit(Store node) {
    // firm graph:
    // [Proj Mem] <---|
    // [Ptr node] <---|
    // [Value node] <-|Store] <- [Proj MM]
    CodeBlock block = getCodeBlockForNode(node);
    Register intermediateValReg = Register.EAX;
    Register intermediateDestReg = Register.EBX;
    Argument dest = allocator.getAsArgument(node.getPtr());
    Argument newValue = allocator.getAsArgument(node.getValue());
    // copy the new value into a register
    block.add(new Mov(newValue, intermediateValReg).firm(node.getValue()));
    // copy the address into another register
    block.add(new Mov(dest, intermediateDestReg).firm(node.getPtr()));
    // store the new value at its new location
    block.add(
        new Mov(intermediateValReg, new RegRelativeLocation(intermediateDestReg, 0)).firm(node));
  }

  private boolean isPhiProneToLostCopies(Phi phi) {
    if (!isPhiProneToLostCopies.containsKey(phi)) {
      isPhiProneToLostCopies.put(phi, FirmUtils.isPhiProneToLostCopies(phi));
    }
    return isPhiProneToLostCopies.get(phi);
  }
}
