package minijava.ir.assembler.instructions;

import com.sun.jna.Platform;
import minijava.ir.assembler.block.CodeBlock;

/** An unconditional jump to a new block */
public class Jmp extends Instruction {

  public final CodeBlock nextBlock;

  public Jmp(CodeBlock nextBlock) {
    this.nextBlock = nextBlock;
  }

  @Override
  public String toGNUAssembler() {
    String label = nextBlock.label;
    if (Platform.isLinux()) {
      label = "." + label;
    }
    return "\n" + super.toGNUAssembler() + " " + label;
  }

  @Override
  public Type getType() {
    return Type.JMP;
  }
}
