package minijava.ir.assembler;

import firm.Graph;
import firm.nodes.Block;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import minijava.ir.DefaultNodeVisitor;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.block.CodeSegment;
import minijava.ir.utils.MethodInformation;

/** Generates GNU assembler for a graph */
public class AssemblerGenerator implements DefaultNodeVisitor {

  private final Graph graph;
  private final MethodInformation info;
  private final NodeAllocator allocator;
  private final CodeSegment segment;
  private CodeBlock currentCodeBlock;
  private Map<Block, String> blockLabels;

  public AssemblerGenerator(Graph graph, NodeAllocator allocator) {
    this.graph = graph;
    this.info = new MethodInformation(graph);
    this.allocator = allocator;
    segment = new CodeSegment(new ArrayList<>(), new ArrayList<>());
    segment.addComment(String.format("Segment for method %s", info.name));
    currentCodeBlock = null;
    blockLabels = new TreeMap<>();
  }

  public CodeSegment generate() {
    graph.walkTopological(this);
    segment.addBlock(currentCodeBlock);
    return segment;
  }

  @Override
  public void visit(Block node) {
    if (currentCodeBlock == null) { // the first block
      visitFirstBlock(node);
    } else {
      // store the current code block in the segement
      // and create a new one
      // the next called visit methods should be called on nodes that
      // belong to the given node
      segment.addBlock(currentCodeBlock);
      currentCodeBlock = new CodeBlock(getLabelForBlock(node));
    }
  }

  private String getLabelForBlock(Block block) {
    if (!blockLabels.containsKey(block)) {
      blockLabels.put(block, String.format(".L%d_%s", blockLabels.size(), info.ldName));
    }
    return blockLabels.get(block);
  }

  private void visitFirstBlock(Block node) {
    currentCodeBlock = new CodeBlock(info.ldName);
    // TODO: write frame setup code
  }
}
