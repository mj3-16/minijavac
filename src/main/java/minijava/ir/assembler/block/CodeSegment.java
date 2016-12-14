package minijava.ir.assembler.block;

import java.util.Collections;
import java.util.List;

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
      builder.append("/* ").append(String.join("\n", comments)).append("*/\n");
    }
    builder.append("\t.text\n");
    for (CodeBlock block : blocks) {
      builder.append("\n");
      builder.append(block.toGNUAssembler());
    }
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

  public CodeBlock getStartBlock() {
    return blocks.get(0);
  }
}
