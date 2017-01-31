package minijava.ir.assembler;

import static minijava.ir.utils.FirmUtils.modeToWidth;

import com.google.common.collect.ImmutableList;
import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.Relation;
import firm.nodes.Block;
import firm.nodes.Call;
import firm.nodes.Cond;
import firm.nodes.Const;
import firm.nodes.Load;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Phi;
import firm.nodes.Proj;
import firm.nodes.Return;
import firm.nodes.Store;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.block.CodeSegment;
import minijava.ir.assembler.instructions.Add;
import minijava.ir.assembler.instructions.CLTD;
import minijava.ir.assembler.instructions.Cmp;
import minijava.ir.assembler.instructions.ConditionalJmp;
import minijava.ir.assembler.instructions.DisableRegisterUsage;
import minijava.ir.assembler.instructions.Div;
import minijava.ir.assembler.instructions.EnableRegisterUsage;
import minijava.ir.assembler.instructions.Evict;
import minijava.ir.assembler.instructions.Instruction;
import minijava.ir.assembler.instructions.Jmp;
import minijava.ir.assembler.instructions.MetaCall;
import minijava.ir.assembler.instructions.MethodPrologue;
import minijava.ir.assembler.instructions.Mov;
import minijava.ir.assembler.instructions.MovFromSmallerToGreater;
import minijava.ir.assembler.instructions.Mul;
import minijava.ir.assembler.instructions.Neg;
import minijava.ir.assembler.instructions.Pop;
import minijava.ir.assembler.instructions.Ret;
import minijava.ir.assembler.instructions.Set;
import minijava.ir.assembler.instructions.Sub;
import minijava.ir.assembler.operands.ImmediateOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.OperandWidth;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.Register;
import minijava.ir.assembler.registers.VirtualRegister;
import minijava.ir.emit.Types;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.MethodInformation;
import minijava.ir.utils.NodeUtils;

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
  private final NodeAllocator allocator;
  private CodeSegment segment;
  private static Map<Phi, Boolean> isPhiProneToLostCopies;

  public AssemblerGenerator(Graph graph) {
    this.graph = graph;
    this.info = new MethodInformation(graph);
    this.allocator = new NodeAllocator(graph);
    isPhiProneToLostCopies = new HashMap<>();
  }

  public CodeSegment generateSegmentForGraph() {
    segment = new CodeSegment();
    segment.addComment(String.format("Code segment for method %s", info.name));
    FirmUtils.withBackEdges(
        graph, () -> GraphUtils.topologicalOrder(graph).forEach(n -> n.accept(this)));
    prependStartBlockWithPrologue();
    return segment;
  }

  private List<Operand> getOperands(Node node) {
    List<Operand> args = new ArrayList<>();
    int start = 0;
    if (NodeUtils.hasSideEffect(node)) {
      start++;
    }
    if (node instanceof Call) {
      // The first operand is the called function, which is always constant for MiniJava.
      start++;
    }
    for (int i = start; i < node.getPredCount(); i++) {
      args.add(allocator.getAsOperand(node.getPred(i)));
    }
    return args;
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
    Register res = allocator.getLocation(node, Types.INT_TYPE.getMode());
    CodeBlock block = segment.getCodeBlockForNode(node);
    List<Operand> args = getOperands(node);
    assert args.size() == 2;
    Operand firstArg = args.get(0);
    Operand secondArg = args.get(1);
    List<AMD64Register> usedRegisters =
        ImmutableList.of(AMD64Register.EAX, AMD64Register.EBX, AMD64Register.EDX);
    block.add(new Evict(usedRegisters));
    block.add(new DisableRegisterUsage(usedRegisters));
    AMD64Register secondArgIm = AMD64Register.EBX;
    block.add(
        new Mov(secondArg, secondArgIm.ofWidth(secondArg.width))
            .com(String.format("Copy second argument %s", secondArg)));
    block.add(
        new Mov(firstArg, AMD64Register.EAX.ofWidth(firstArg.width))
            .com("copy the first argument (the dividend) into the EAX register"));
    block.add(new CLTD().com("and convert it from 32 Bits to 64 Bits"));
    block.add(
        new Div(secondArgIm)
            .com("the quotient is now in the EAX register and the remainder in the EDX register")
            .firm(node));
    block.add(
        new Mov(isDiv ? AMD64Register.EAX : AMD64Register.EDX, res)
            .com(String.format("Move result into %s", res)));
    block.add(new EnableRegisterUsage(usedRegisters));
  }

  @Override
  public void visit(firm.nodes.Minus node) {
    // Note: this method handles the arithmetic negation (unary minus) operation
    List<Operand> args = getOperands(node);
    assert args.size() == 1;
    CodeBlock block = segment.getCodeBlockForNode(node);
    Operand arg = args.get(0);
    Register res = allocator.getLocation(node);
    block.add(new Mov(arg, res));
    block.add(new Neg(res).firm(node));
  }

  private void addBinaryInstruction(BiFunction<Operand, Operand, Instruction> creator, Node node) {
    List<Operand> args = getOperands(node);
    assert args.size() == 2;
    // swap arguments because GNU assembler has them in reversed order (compared to Intel assembler)
    Operand firstArg = args.get(0);
    Operand secondArg = args.get(1);
    Register res = allocator.getLocation(node);
    CodeBlock block = segment.getCodeBlockForNode(node);
    if (secondArg.width.ordinal() < res.width.ordinal()) {
      Register intermLoc = new VirtualRegister(res.width, allocator.genLocationId());
      block.add(new MovFromSmallerToGreater(secondArg, intermLoc));
      secondArg = intermLoc;
    }
    if (firstArg.width == res.width) {
      block.add(new Mov(firstArg, res));
    } else if (firstArg.width.ordinal() < res.width.ordinal()) {
      block.add(new Mov(new ImmediateOperand(res.width, 0), res));
      block.add(new MovFromSmallerToGreater(firstArg, res));
    } else {
      assert false;
    }
    block.add(creator.apply(secondArg, res).firm(node));
  }

  /**
   * The prepended prolog backups the old base pointer, sets the new and allocates bytes on the
   * stack for the activation record.
   */
  private void prependStartBlockWithPrologue() {
    CodeBlock startBlock = segment.getCodeBlock(graph.getStartBlock());
    startBlock.prepend(new MethodPrologue());
  }

  @Override
  public void visit(Return node) {
    CodeBlock codeBlock = segment.getCodeBlockForNode(node);
    if (node.getPredCount() > 1) {
      // we only need this if the return actually returns a value
      List<Operand> args = getOperands(node);
      assert args.size() == 1;
      Operand arg = args.get(0);
      codeBlock.add(new Mov(arg, AMD64Register.RETURN_REGISTER.ofWidth(arg.width)));
    }
    // insert the epilogue
    codeBlock.add(
        new Mov(AMD64Register.BASE_POINTER, AMD64Register.STACK_POINTER)
            .com("Copy base pointer to stack pointer (free stack)"));
    codeBlock.add(new Pop(AMD64Register.BASE_POINTER).com("Restore previous base pointer"));
    codeBlock.add(
        new Ret().com("Jump to return address (and remove it from the stack)").firm(node));
  }

  @Override
  public void visit(firm.nodes.Call node) {
    MethodInformation info = new MethodInformation(node);
    List<Operand> args = getOperands(node);
    CodeBlock block = segment.getCodeBlockForNode(node);
    Optional<Register> ret = Optional.empty();
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
                    + args.stream().map(Operand::toString).collect(Collectors.joining("|"))
                    + " -> "
                    + ret));
    if (info.hasReturnValue) {
      Register returnRegister = allocator.getLocation(node);
      block.add(
          new Mov(AMD64Register.RETURN_REGISTER.ofWidth(returnRegister.width), returnRegister));
    }
  }

  @Override
  public void visit(Block node) {
    CodeBlock codeBlock = segment.getCodeBlock(node);
    // we only handle control flow edges here
    for (Node pred : node.getPreds()) {
      assert pred.getMode().equals(Mode.getX());
      CodeBlock predCodeBlock = segment.getCodeBlockForNode(pred);
      // pred is a predecessor node but not a block
      if (pred instanceof Proj) {
        // we might do unnecessary work here, but the {@link CodeBlock} instance takes care of it
        // this edge comes from a conditional jump
        Proj proj = (Proj) pred;
        boolean isTrueEdge = proj.getNum() == 1;
        // the condition
        Cond cond = (Cond) proj.getPred();

        if (cond.getSelector() instanceof firm.nodes.Cmp) {
          // we ignore it as we're really interested in the preceding compare node
          firm.nodes.Cmp cmp = (firm.nodes.Cmp) cond.getSelector();

          List<Operand> args = getOperands(cmp);
          assert args.size() == 2;
          Operand left = args.get(0);
          Operand right = args.get(1);

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
          Register res = allocator.getLocation(bPhi);
          Block bPhiBlock = (Block) bPhi.getBlock();
          java.util.Set<Block> moreThanOncePredBlocks = blocksThatAreMoreOnceAPredBlock(bPhiBlock);
          for (int i = 0; i < bPhi.getPredCount(); i++) {
            Node inputNode = bPhi.getPred(i);
            // the i.th block belongs to the i.th input edge of the phi node
            Block block = (Block) bPhi.getBlock().getPred(i).getBlock();
            CodeBlock inputCodeBlock = segment.getCodeBlock(block);

            boolean afterCondJumps =
                shouldPlacePhiHelperAfterCondJumps(
                    moreThanOncePredBlocks, (Block) bPhi.getBlock(), i);

            if (inputNode instanceof firm.nodes.Cmp) {
              if (!inputCodeBlock.hasCompare()) {
                // we have to add one
                // a set without a cmp doesn't make any sense
                firm.nodes.Cmp cmp = (firm.nodes.Cmp) inputNode;
                Operand leftArg = allocator.getAsOperand(cmp.getLeft());
                Operand rightArg = allocator.getAsOperand(cmp.getRight());
                // compare the values
                // swap its arguments, GNU assembler...
                inputCodeBlock.setCompare((Cmp) new Cmp(leftArg, rightArg).firm(cmp));
              }
              List<Instruction> phiBHelperInstructions = new ArrayList<>();
              // zero the result registers
              phiBHelperInstructions.add(new Mov(new ImmediateOperand(res.width, 0), res));
              // set the value to one if condition did hold true
              phiBHelperInstructions.add(
                  new Set(((firm.nodes.Cmp) inputNode).getRelation(), res).firm(bPhi));
              phiBHelperInstructions.forEach(
                  afterCondJumps
                      ? inputCodeBlock::addToAfterCompareInstructions
                      : inputCodeBlock::addAfterConditionalJumpsInstruction);
            } else {
              Instruction bPhiMov = new Mov(allocator.getAsOperand(inputNode), res).firm(bPhi);
              if (afterCondJumps) {
                inputCodeBlock.addAfterConditionalJumpsInstruction(bPhiMov);
              } else {
                inputCodeBlock.add(bPhiMov);
              }
            }
          }
          // we compare the phi's value with 1 (== true)
          VirtualRegister immediateReg =
              new VirtualRegister(
                  modeToWidth(Types.BOOLEAN_TYPE.getMode()), allocator.genLocationId());
          immediateReg.setComment("1");
          RegisterOperand immOperand = new RegisterOperand(immediateReg);
          predCodeBlock.addToCmpOrJmpSupportInstructions(
              new Mov(new ImmediateOperand(OperandWidth.Byte, 1), immOperand).firm(node));
          predCodeBlock.setCompare((Cmp) new Cmp(immediateReg, res).firm(cond));
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

  @Override
  public void visit(Phi node) {
    if (node.getMode().equals(Mode.getM())) {
      // we don't have to deal with memory dependencies here
      return;
    }
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
    Register res = allocator.getLocation(node);
    Register tmpRegister = null;
    if (isPhiProneToLostCopies(node)) {
      // we only need a temporary variable if the phi is prone to lost copy errors
      tmpRegister = new VirtualRegister(modeToWidth(node.getMode()), allocator.genLocationId());
      // we have copy the temporarily modified value into phis registers
      // at the start of the block that the phi is part of
      CodeBlock phisBlock = segment.getCodeBlockForNode(node);
      // we have to use a register to copy the value between two locations
      phisBlock.addBlockStartInstruction(new Mov(tmpRegister, res));
    }
    Block block = (Block) node.getBlock();
    java.util.Set<Block> moreThanOncePredBlocks = blocksThatAreMoreOnceAPredBlock(block);
    for (int i = 0; i < block.getPredCount(); i++) {
      // we get the correct block be taking the predecessors of the block of the phi node
      Block inputBlock = (Block) block.getPred(i).getBlock();
      CodeBlock inputCodeBlock = segment.getCodeBlock(inputBlock);
      // we should decide whether we want to place the phi helper instruction before or after the conditional jumps
      boolean afterCondJumps = shouldPlacePhiHelperAfterCondJumps(moreThanOncePredBlocks, block, i);
      // the i.th block belongs to the i.th input edge of the phi node
      Node inputNode = node.getPred(i);
      Operand op = allocator.getAsOperand(inputNode);
      // we store the operand in a register
      Instruction phiMov;
      if (isPhiProneToLostCopies(node)) {
        // if this phi is prone to lost copies then we copy the value only
        // into the temporary variable
        phiMov = new Mov(op, tmpRegister).firm(node);
      } else {
        // we copy it into Phis registers
        phiMov = new Mov(op, res).firm(node);
      }
      if (isPhiB && inputNode instanceof firm.nodes.Cmp) {
        // we have to insert the mov instructions behind (!) the compare
        // first we have to make sure that the intermediate register is really zero
        VirtualRegister intermediateLoc =
            new VirtualRegister(
                modeToWidth(Types.BOOLEAN_TYPE.getMode()), allocator.genLocationId());
        intermediateLoc.setComment("0");
        List<Instruction> phiBHelperInstructions = new ArrayList<>();
        phiBHelperInstructions.add(
            new Mov(new ImmediateOperand(intermediateLoc.width, 0), intermediateLoc).firm(node));
        // than we have to use the set instruction to set the registers value to one if the condition is true
        // important note: we can only set the lowest 8 bit of the intermediate register
        phiBHelperInstructions.add(
            new Set(((firm.nodes.Cmp) inputNode).getRelation(), intermediateLoc).firm(inputNode));
        // we copy the intermediate value to its final destination
        phiBHelperInstructions.add(phiMov);
        phiBHelperInstructions.forEach(
            afterCondJumps
                ? inputCodeBlock::addPhiBHelperInstruction
                : inputCodeBlock::addAfterConditionalJumpsInstruction);
      } else {
        // compares don't matter and therefore we copy the value before the (possible) comparison takes place
        if (afterCondJumps) {
          inputCodeBlock.addAfterConditionalJumpsInstruction(phiMov);
        } else {
          inputCodeBlock.addPhiHelperInstruction(phiMov);
        }
      }
    }
  }

  private java.util.Set<Block> blocksThatAreMoreOnceAPredBlock(Block baseBlock) {
    Map<Block, Integer> blockAsPredCount = new HashMap<>();
    for (Node predNode : baseBlock.getPreds()) {
      if (predNode instanceof Proj) {
        blockAsPredCount.put(
            (Block) predNode.getBlock(), blockAsPredCount.getOrDefault(predNode.getBlock(), 0) + 1);
      }
    }
    java.util.Set<Block> moreThanOnce =
        blockAsPredCount
            .keySet()
            .stream()
            .filter(block -> blockAsPredCount.get(block) > 1)
            .collect(Collectors.toSet());
    return moreThanOnce;
  }

  private boolean shouldPlacePhiHelperAfterCondJumps(
      java.util.Set<Block> moreThanOncePredBlocks, Block baseBlock, int predIndex) {
    Node pred = baseBlock.getPred(predIndex);
    if (moreThanOncePredBlocks.contains((Block) pred.getBlock())) {
      // we're only concerned with blocks that are a predecessor of the current block with at least two different (Proj) nodes
      if (pred instanceof Proj) {
        return ((Proj) pred).getNum() == Cond.pnFalse;
      }
    }
    // don't change the behavior of unaffected phis
    return false;
  }

  @Override
  public void visit(Load node) {
    // firm graph:
    // [Ptr node, might be a calculation!] <- [Load] <- [Proj dest]
    Operand ptr = allocator.getAsOperand(node.getPtr());
    Register dest = allocator.getLocation(node);
    RegisterOperand destOperand = new RegisterOperand(modeToWidth(node.getType().getMode()), dest);
    segment.getCodeBlockForNode(node).add(new Mov(ptr, destOperand));
  }

  @Override
  public void visit(Store node) {
    // firm graph:
    // [Proj Mem] <---|
    // [Ptr node] <---|
    // [Value node] <-|Store] <- [Proj MM]
    CodeBlock block = segment.getCodeBlockForNode(node);
    Operand dest = allocator.getAsOperand(node.getPtr());
    Operand newValue = allocator.getAsOperand(node.getValue());
    block.add(new Mov(newValue, dest).firm(node));
  }

  private boolean isPhiProneToLostCopies(Phi phi) {
    if (!isPhiProneToLostCopies.containsKey(phi)) {
      isPhiProneToLostCopies.put(phi, FirmUtils.isPhiProneToLostCopies(phi));
    }
    return isPhiProneToLostCopies.get(phi);
  }

  public NodeAllocator getAllocator() {
    return allocator;
  }
}
