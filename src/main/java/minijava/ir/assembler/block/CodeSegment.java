package minijava.ir.assembler.block;

import com.sun.jna.Platform;
import firm.Graph;
import firm.nodes.Block;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import minijava.ir.utils.MethodInformation;

public class CodeSegment extends Segment {

  private final Map<Block, CodeBlock> blocks = new LinkedHashMap<>();
  private final List<String> comments = new ArrayList<>();

  @Override
  public String toGNUAssembler() {
    StringBuilder builder = new StringBuilder();
    if (comments.size() > 0) {
      builder
          .append(System.lineSeparator())
          .append("/* ")
          .append(String.join(System.lineSeparator(), comments))
          .append("*/\n");
    }
    builder.append("\t.text").append(System.lineSeparator());
    builder.append(
        blocks
            .values()
            .stream()
            .map(CodeBlock::toGNUAssembler)
            .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator())));
    return builder.toString();
  }

  public CodeBlock getCodeBlock(Block block) {
    return blocks.computeIfAbsent(block, b -> new CodeBlock(getLabelForBlock(b)));
  }

  private static String getLabelForBlock(Block block) {
    Graph definingGraph = block.getGraph();
    String ldName = new MethodInformation(definingGraph).ldName;
    if (definingGraph.getStartBlock().equals(block)) {
      return ldName;
    }
    String ldFormat;
    if (Platform.isLinux()) {
      ldFormat = ".L%d_%s";
    } else {
      ldFormat = "L%d_%s";
    }
    return String.format(ldFormat, block.getNr(), ldName);
  }

  public void addComment(String comment) {
    comments.add(comment);
  }

  public List<CodeBlock> getBlocks() {
    return new ArrayList<>(blocks.values());
  }

  public List<String> getComments() {
    return comments;
  }
}
