package minijava.ir.assembler;

import firm.Graph;
import firm.nodes.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import minijava.ir.DefaultNodeVisitor;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.block.CodeSegment;
import minijava.ir.assembler.instructions.*;
import minijava.ir.assembler.instructions.Add;
import minijava.ir.assembler.instructions.Div;
import minijava.ir.assembler.instructions.Mul;
import minijava.ir.assembler.instructions.Sub;
import minijava.ir.assembler.location.Location;
import minijava.ir.assembler.location.Register;
import minijava.ir.utils.MethodInformation;

/**
 * Generates GNU assembler for a graph
 *
 * <p>One of the goals of this implementation is to produce well documented assembly code.
 *
 * <p>Important: it currently ignores any {@link firm.nodes.Conv} nodes
 */
public class AssemblerGenerator implements DefaultNodeVisitor {

  private final Graph graph;
  private final MethodInformation info;
  private final NodeAllocator allocator;
  private final CodeSegment segment;
  private Map<Block, CodeBlock> blocksToCodeBlocks;

  public AssemblerGenerator(Graph graph, NodeAllocator allocator) {
    this.graph = graph;
    this.info = new MethodInformation(graph);
    this.allocator = allocator;
    allocator.process(graph);
    blocksToCodeBlocks = new HashMap<>();
    segment = new CodeSegment(new ArrayList<>(), new ArrayList<>());
    segment.addComment(String.format("Code segment for method %s", info.name));
    segment.addBlock(getCodeBlock(graph.getStartBlock()));
    blocksToCodeBlocks = new HashMap<>();
  }

  public CodeSegment generate() {
    graph.walkTopological(this);
    prependStartBlockWithPrologue();
    return segment;
  }

  @Override
  public void visit(Block block) {
    segment.addBlock(getCodeBlock(block));
  }

  private CodeBlock getCodeBlock(Block block) {
    return blocksToCodeBlocks.computeIfAbsent(
        block, b -> new CodeBlock(String.format(".L%d_%s", b.getNr(), info.ldName)));
  }

  /** Get the code block for a given firm node (for the block the node belongs to) */
  private CodeBlock getCodeBlockForNode(Node node) {
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
    List<Argument> args = allocator.getArguments(node);
    assert args.size() == 2;
    Argument firstArg = args.get(0);
    Argument secondArg = args.get(1);
    Location res = allocator.getResultLocation(node);
    CodeBlock block = getCodeBlockForNode(node);
    block.add(
        new Mov(firstArg, Register.EAX)
            .com("copy the first argument (the dividend) into the EAX register"));
    block.add(new CLTD().com("and convert it from 32 Bits to 64 Bits"));
    block.add(
        new Div(secondArg)
            .com("the quotient is now in the EAX register and the remainder in the EDX register"));
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
      block.add(new Neg(intermediateReg));
      block.add(new Mov(intermediateReg, res));
    } else {
      // everything is fine here
      block.add(new Neg(arg));
    }
  }

  private void addBinaryInstruction(
      BiFunction<Argument, Argument, Instruction> creator, Node node) {
    List<Argument> args = allocator.getArguments(node);
    assert args.size() == 2;
    Argument firstArg = args.get(0);
    Argument secondArg = args.get(1);
    Location res = allocator.getResultLocation(node);
    CodeBlock block = getCodeBlockForNode(node);
    if (firstArg != res) {
      // if the left argument isn't equal to the result location
      // then we have to store the left argument in a register
      // and copy the register's content into the result location
      // after the operation
      // this is caused by the GNU assembler for amd64 being a two adress
      // format
      Register intermediateReg = Register.EAX;
      block.add(
          new Mov(firstArg, intermediateReg)
              .com(
                  "Store the first argument in an intermediate register, as "
                      + "the assembler uses a two adress format (the first argument "
                      + "has to be the result location)"));
      block.add(creator.apply(intermediateReg, secondArg));
      block.add(new Mov(intermediateReg, res));
    } else {
      // everything is fine here
      block.add(creator.apply(firstArg, secondArg));
    }
  }

  /**
   * The prepended prolog backups the old base pointer, sets the new and allocates bytes on the
   * stack for the activation record.
   */
  private void prependStartBlockWithPrologue() {
    CodeBlock startBlock = segment.getStartBlock();
    startBlock.prepend(new AllocStack(allocator.getActivationRecordSize())); // subq $XX, %rsp
    startBlock.prepend(
        new Mov(Register.STACK_POINTER, Register.BASE_POINTER)
            .com("Set base pointer for new activation record")); // movq %rsp, %rbp
    startBlock.prepend(new Push(Register.BASE_POINTER).com("Backup old base pointer"));
  }

  @Override
  public void visit(Return node) {
    CodeBlock codeBlock = getCodeBlockForNode(node);
    List<Argument> args = allocator.getArguments(node);
    assert args.size() == 1;
    Argument arg = args.get(0);
    if (!(Register.RETURN_REGISTER.equals(arg))) { // if the argument isn't in the correct register
      codeBlock.add(
          new Mov(arg, Register.RETURN_REGISTER)
              .com("Copy the return value into the correct register"));
    }
    // insert the epilogue
    codeBlock.add(
        new Mov(Register.BASE_POINTER, Register.STACK_POINTER)
            .com("Copy base pointer to stack pointer (free stack)"));
    codeBlock.add(new Pop(Register.BASE_POINTER).com("Restore previous base pointer"));
    codeBlock.add(new Ret().com("Jump to return adress (and remove it from the stack)"));
  }
}
