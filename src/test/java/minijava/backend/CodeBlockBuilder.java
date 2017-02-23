package minijava.backend;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import minijava.backend.block.CodeBlock;
import minijava.backend.block.PhiFunction;
import minijava.backend.instructions.CodeBlockInstruction;
import minijava.backend.operands.Operand;

public class CodeBlockBuilder {
  private final CodeBlock block;

  private CodeBlockBuilder(CodeBlock block) {
    this.block = block;
  }

  public CodeBlock build() {
    return block;
  }

  public CodeBlockBuilder addInstruction(CodeBlockInstruction instruction) {
    block.instructions.add(instruction);
    return this;
  }

  public CodeBlockBuilder addLoopBody(Set<CodeBlock> body) {
    block.associatedLoopBody.addAll(body);
    return this;
  }

  public CodeBlockBuilder addLoopBody(CodeBlock... body) {
    return addLoopBody(Sets.newHashSet(body));
  }

  public CodeBlockBuilder addPhi(Operand output, Function<PhiFunctionBuilder, PhiFunction> build) {
    block.phis.add(build.apply(PhiFunctionBuilder.newPhi(output)));
    System.out.println("block.phis.size() = " + block.phis.size());
    return this;
  }

  public static CodeBlockBuilder newBlock(String label) {
    return new CodeBlockBuilder(new CodeBlock(label));
  }

  public static List<CodeBlock> asLinearization(CodeBlock... blocks) {
    List<CodeBlock> ret = new ArrayList<>();
    for (int i = 0; i < blocks.length; i++) {
      CodeBlock block = blocks[i];
      block.linearizedOrdinal = i;
      ret.add(block);
    }
    return ret;
  }
}
