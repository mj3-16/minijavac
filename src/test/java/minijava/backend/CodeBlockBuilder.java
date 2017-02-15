package minijava.backend;

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

  public CodeBlockBuilder addPhi(Operand output, Function<PhiFunctionBuilder, PhiFunction> build) {
    block.phis.add(build.apply(PhiFunctionBuilder.newPhi(output)));
    return this;
  }

  public static CodeBlockBuilder newBlock(String label) {
    return new CodeBlockBuilder(new CodeBlock(label));
  }
}
