package minijava.ir.assembler;

import static minijava.ir.utils.FirmUtils.modeToWidth;

import com.sun.jna.Platform;
import firm.*;
import firm.nodes.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import minijava.ir.Types;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.block.CodeSegment;
import minijava.ir.assembler.instructions.*;
import minijava.ir.assembler.instructions.Add;
import minijava.ir.assembler.instructions.Cmp;
import minijava.ir.assembler.instructions.Div;
import minijava.ir.assembler.instructions.Jmp;
import minijava.ir.assembler.instructions.Mul;
import minijava.ir.assembler.instructions.Set;
import minijava.ir.assembler.instructions.Sub;
import minijava.ir.assembler.location.*;
import minijava.ir.optimize.Normalizer;
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
 *
 * <p>The {@link Normalizer} should be run first (binary operations should have their constant
 * argument as their left argument if they have one).
 */
public class AssemblerGenerator extends NodeVisitor.Default {

  private final Graph graph;
  private final MethodInformation info;
  private final SimpleNodeAllocator allocator;
  private CodeSegment segment;
  private Map<Integer, CodeBlock> blocksToCodeBlocks;
  private static Map<Phi, Boolean> isPhiProneToLostCopies;

  public AssemblerGenerator(Graph graph) {
    this.graph = graph;
    this.info = new MethodInformation(graph);
    this.allocator = new SimpleNodeAllocator(graph);
    isPhiProneToLostCopies = new HashMap<>();
  }

  public CodeSegment generateSegmentForGraph() {
    blocksToCodeBlocks = new HashMap<>();
    segment = new CodeSegment(new ArrayList<>(), new ArrayList<>());
    segment.addComment(String.format("Code segment for method %s", info.name));
    blocksToCodeBlocks = new HashMap<>();
    BackEdges.enable(graph);
    graph.walkTopological(this);
    prependStartBlockWithPrologue();
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
    Location res = allocator.getLocation(node, Types.INT_TYPE.getMode());
    CodeBlock block = getCodeBlockForNode(node);
    List<Argument> args = allocator.getArguments(node);
    assert args.size() == 2;
    Argument firstArg = args.get(0);
    Argument secondArg = args.get(1);
    block.add(new Evict(Register.EAX, Register.EBX, Register.EDX));
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
  public void visit(firm.nodes.Minus node) {
    // Note: this method handles the arithmetic negation (unary minus) operation
    List<Argument> args = allocator.getArguments(node);
    assert args.size() == 1;
    CodeBlock block = getCodeBlockForNode(node);
    Argument arg = args.get(0);
    Location res = allocator.getLocation(node);
    block.add(new Mov(arg, res));
    block.add(new Neg(res).firm(node));
  }

  private void addBinaryInstruction(
      BiFunction<Argument, Argument, Instruction> creator, Node node) {
    List<Argument> args = allocator.getArguments(node);
    assert args.size() == 2;
    // swap arguments because GNU assembler has them in reversed order (compared to Intel assembler)
    Argument firstArg = args.get(1);
    Argument secondArg = args.get(0);
    Location res = allocator.getLocation(node);
    CodeBlock block = getCodeBlockForNode(node);
    block.add(new Mov(firstArg, res));
    block.add(creator.apply(secondArg, res).firm(node));
  }

  /**
   * The prepended prolog backups the old base pointer, sets the new and allocates bytes on the
   * stack for the activation record.
   */
  private void prependStartBlockWithPrologue() {
    CodeBlock startBlock = getCodeBlock(graph.getStartBlock());
    startBlock.prepend(new MethodPrologue());
  }

  @Override
  public void visit(Return node) {
    CodeBlock codeBlock = getCodeBlockForNode(node);
    if (node.getPredCount() > 1) {
      // we only need this if the return actually returns a value
      List<Argument> args = allocator.getArguments(node);
      assert args.size() == 1;
      Argument arg = args.get(0);
      codeBlock.add(new Mov(arg, Register.RETURN_REGISTER));
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
    MethodInformation info = new MethodInformation(node);
    List<Argument> args = allocator.getArguments(node);
    CodeBlock block = getCodeBlockForNode(node);
    block.add(new Evict(Register.usableRegisters));
    Optional<Argument> ret = Optional.empty();
    if (info.hasReturnValue) {
      ret = Optional.of(allocator.getResultLocation(node));
    }
    block.add(
        new MetaCall(args, ret, info)
            .firm(node)
            .com(
                "-> "
                    + info.ldName
                    + " "
                    + args.stream().map(Argument::toString).collect(Collectors.joining("|"))
                    + " -> "
                    + ret));
    if (info.hasReturnValue) {
      Location returnLocation = allocator.getLocation(node);
      block.add(new Mov(Register.RETURN_REGISTER.ofWidth(returnLocation.width), returnLocation));
    }
  }

  private boolean hasTypeLongWidth(Type type) {
    return type instanceof PrimitiveType && !(type.getMode().equals(Types.PTR_TYPE.getMode()));
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

          // add a compare instruction that compares both arguments
          // we have to swap the arguments of the cmp instruction
          // why? because of GNU assembler…
          predCodeBlock.setCompare((Cmp) new Cmp(right, left).firm(cmp));

          if (isTrueEdge) {
            // choose the right jump instruction
            ConditionalJmp jmp = new ConditionalJmp(codeBlock, cmp.getRelation());
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
                // compare the values
                // swap its arguments, GNU assembler...
                inputCodeBlock.setCompare((Cmp) new Cmp(leftArg, rightArg).firm(cmp));
              }
              // zero the result location
              inputCodeBlock.addToAfterCompareInstructions(
                  new Mov(new ConstArgument(res.width, 0), res));
              // set the value to one if condition did hold true
              inputCodeBlock.addToAfterCompareInstructions(
                  new Set(((firm.nodes.Cmp) inputNode).getRelation(), res).firm(bPhi));
            } else {
              inputCodeBlock.add(new Mov(allocator.getAsArgument(inputNode), res).firm(bPhi));
            }
          }
          // we compare the phi's value with 1 (== true)
          NodeLocation intermediateLoc =
              new NodeLocation(
                  modeToWidth(Types.BOOLEAN_TYPE.getMode()), allocator.genLocationId());
          intermediateLoc.setComment("1");
          predCodeBlock.addToCmpOrJmpSupportInstructions(
              new Mov(new ConstArgument(Register.Width.Byte, 1), intermediateLoc).firm(node));
          predCodeBlock.setCompare((Cmp) new Cmp(intermediateLoc, res).firm(cond));
          if (isTrueEdge) {
            // we jump to the true block if both cmps arguments were equal
            predCodeBlock.addConditionalJump(
                (Jmp) new ConditionalJmp(codeBlock, Relation.Equal).firm(proj));
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

  private boolean hasCmpBooleanArguments(firm.nodes.Cmp cmp) {
    return cmp.getRight().getMode().equals(Types.BOOLEAN_TYPE.getMode())
        || cmp.getLeft().getMode().equals(Types.BOOLEAN_TYPE.getMode());
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
    Location res = allocator.getLocation(node);
    Location tmpLocation = null;
    if (isPhiProneToLostCopies(node)) {
      // we only need a temporary variable if the phi is prone to lost copy errors
      tmpLocation = new NodeLocation(modeToWidth(node.getMode()), allocator.genLocationId());
      // we have copy the temporarily modified value into phis location
      // at the start of the block that the phi is part of
      CodeBlock phisBlock = getCodeBlockForNode(node);
      Register intermediateReg = Register.EAX.ofWidth(tmpLocation.width);
      // we have to use a register to copy the value between two locations
      phisBlock.addBlockStartInstruction(new Mov(tmpLocation, res));
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
      Instruction phiMov;
      if (isPhiProneToLostCopies(node)) {
        // if this phi is prone to lost copies then we copy the value only
        // into the temporary variable
        phiMov = new Mov(arg, tmpLocation).firm(node);
      } else {
        // we copy it into Phis location
        phiMov = new Mov(arg, res).firm(node);
      }
      if (isPhiB && inputNode instanceof firm.nodes.Cmp) {
        // we have to insert the mov instructions behind (!) the compare
        // first we have to make sure that the intermediate register is really zero
        NodeLocation intermediateLoc =
            new NodeLocation(modeToWidth(Types.BOOLEAN_TYPE.getMode()), allocator.genLocationId());
        intermediateLoc.setComment("0");
        inputCodeBlock.addPhiBHelperInstruction(
            new Mov(new ConstArgument(intermediateLoc.width, 0), intermediateLoc).firm(node));
        // than we have to use the set instruction to set the registers value to one if the condition is true
        // important note: we can only set the lowest 8 bit of the intermediate register
        inputCodeBlock.addPhiBHelperInstruction(
            new Set(((firm.nodes.Cmp) inputNode).getRelation(), intermediateLoc).firm(inputNode));
        // we copy the intermediate value to its final destination
        inputCodeBlock.addPhiBHelperInstruction(phiMov);
      } else {
        // compares don't matter and therefore we copy the value before the (possible) comparison takes place
        inputCodeBlock.addPhiHelperInstruction(phiMov);
      }
    }
  }

  @Override
  public void visit(Load node) {
    // firm graph:
    // [Ptr node, might be a calculation!] <- [Load] <- [Proj res]
    Argument arg = allocator.getAsArgument(node.getPtr());
    Location res = allocator.getLocation(node);
    CodeBlock block = getCodeBlockForNode(node);
    block.add(
        new MetaLoad(
                new MemoryNodeLocation(
                    modeToWidth(node.getType().getMode()),
                    allocator.genLocationId(),
                    node.getPtr(),
                    arg),
                res)
            .firm(node));
  }

  @Override
  public void visit(Store node) {
    // firm graph:
    // [Proj Mem] <---|
    // [Ptr node] <---|
    // [Value node] <-|Store] <- [Proj MM]
    CodeBlock block = getCodeBlockForNode(node);
    Argument dest = allocator.getAsArgument(node.getPtr());
    Argument newValue = allocator.getAsArgument(node.getValue());
    block.add(
        new MetaStore(
                newValue,
                new MemoryNodeLocation(
                    modeToWidth(node.getValue().getMode()),
                    allocator.genLocationId(),
                    node.getPtr(),
                    dest))
            .firm(node));
  }

  private boolean isPhiProneToLostCopies(Phi phi) {
    if (!isPhiProneToLostCopies.containsKey(phi)) {
      isPhiProneToLostCopies.put(phi, FirmUtils.isPhiProneToLostCopies(phi));
    }
    return isPhiProneToLostCopies.get(phi);
  }

  public SimpleNodeAllocator getAllocator() {
    return allocator;
  }
}
