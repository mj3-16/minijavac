package minijava.ir.assembler;

import firm.Graph;
import firm.nodes.Block;
import firm.nodes.Node;
import firm.nodes.Return;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import minijava.ir.DefaultNodeVisitor;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.block.CodeSegment;
import minijava.ir.assembler.instructions.*;
import minijava.ir.assembler.location.Register;
import minijava.ir.utils.MethodInformation;

/**
 * Generates GNU assembler for a graph
 *
 * <p>One of the goals of this implementation is to produce well documented assembly code.
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
