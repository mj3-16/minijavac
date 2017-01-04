package minijava.ir.assembler.block;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CodeSegment extends Segment {

  private List<CodeBlock> blocks;
  private List<String> comments;

  public CodeSegment(List<CodeBlock> blocks, List<String> comments) {
    this.blocks = blocks;
    this.comments = comments;
  }

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
            .stream()
            .map(CodeBlock::toGNUAssembler)
            .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator())));
    return builder.toString();
  }

  public void addBlock(CodeBlock block) {
    blocks.add(block);
  }

  public void addComment(String comment) {
    comments.add(comment);
  }

  public List<CodeBlock> getBlocks() {
    return Collections.unmodifiableList(blocks);
  }
}
