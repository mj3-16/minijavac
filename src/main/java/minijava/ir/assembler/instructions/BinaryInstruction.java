package minijava.ir.assembler.instructions;

/** A binary instruction with two arguments */
public abstract class BinaryInstruction extends Instruction {
  public final Argument left;
  public final Argument right;

  public BinaryInstruction(Argument left, Argument right) {
    this.left = left;
    this.right = right;
  }

  @Override
  public String toGNUAssembler() {
    return super.toGNUAssembler()
        + String.join(" ", getType().asm, left.toGNUAssembler(), right.toGNUAssembler());
  }
}
