package minijava.backend;

import java.util.HashMap;
import java.util.Map;
import minijava.backend.block.CodeBlock;
import minijava.backend.block.PhiFunction;
import minijava.backend.operands.Operand;

public class PhiFunctionBuilder {

  private final Operand output;
  private final Map<CodeBlock, Operand> inputs = new HashMap<>();

  private PhiFunctionBuilder(Operand output) {
    this.output = output;
  }

  public PhiFunction build() {
    return new PhiFunction(inputs, output, null);
  }

  public PhiFunctionBuilder from(CodeBlock pred, Operand input) {
    inputs.put(pred, input);
    return this;
  }

  public static PhiFunctionBuilder newPhi(Operand output) {
    return new PhiFunctionBuilder(output);
  }
}
