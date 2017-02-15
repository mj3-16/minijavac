package minijava.backend;

import java.util.HashMap;
import minijava.backend.block.CodeBlock;
import minijava.backend.block.PhiFunction;
import minijava.backend.operands.Operand;

public class PhiFunctionBuilder {

  private final PhiFunction phi;

  private PhiFunctionBuilder(Operand output) {
    this.phi = new PhiFunction(new HashMap<>(), output, null);
  }

  public PhiFunction build() {
    return phi;
  }

  public PhiFunctionBuilder from(CodeBlock pred, Operand input) {
    phi.inputs.put(pred, input);
    return this;
  }

  public static PhiFunctionBuilder newPhi(Operand output) {
    return new PhiFunctionBuilder(output);
  }
}
