package minijava.ir.assembler.instructions;

import minijava.ir.assembler.location.Register;
import org.jooq.lambda.tuple.Tuple2;

/** A binary instruction with two arguments */
public abstract class BinaryInstruction extends Instruction {
  public final Argument left;
  public final Argument right;

  public BinaryInstruction(Argument left, Argument right) {
    Tuple2<Argument, Argument> t = getAdjustedRegisters(left, right);
    this.left = t.v1;
    this.right = t.v2;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return createGNUAssemblerWoComments(left, right);
  }

  @Override
  protected Register.Width getWidthOfArguments() {
    return getMaxWithOfArguments(left, right);
  }

  public static Tuple2<Argument, Argument> getAdjustedRegisters(Argument left, Argument right) {
    if (left instanceof Register && right instanceof Register) {
      Register leftReg = (Register) left;
      Register rightReg = (Register) right;
      Register.Width minWidth = Register.minWidth(leftReg, rightReg);
      if (minWidth == Register.Width.Byte) {
        return new Tuple2<Argument, Argument>(
            Register.getByteVersion(leftReg), Register.getByteVersion(rightReg));
      }
      if (minWidth == Register.Width.Long) {
        return new Tuple2<Argument, Argument>(
            Register.getLongVersion(leftReg), Register.getLongVersion(rightReg));
      }
    }
    return new Tuple2<Argument, Argument>(left, right);
  }
}
