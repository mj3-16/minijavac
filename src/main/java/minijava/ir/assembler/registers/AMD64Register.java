package minijava.ir.assembler.registers;

import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.Function;
import org.jooq.lambda.Seq;

/** General purpose hardware register of the AMD64 ISA. */
public enum AMD64Register implements Register {
  A,
  B,
  C,
  D,
  SP,
  BP,
  SI,
  DI,
  R8,
  R9,
  R10,
  R11,
  R12,
  R13,
  R14,
  R15;

  @Override
  public <T> T match(
      Function<VirtualRegister, T> matchVirtual, Function<AMD64Register, T> matchHardware) {
    return matchHardware.apply(this);
  }

  public static Set<AMD64Register> allocatable =
      Sets.newTreeSet(Seq.of(A, C, D, B, SI, DI, R8, R9, R10, R11, R12, R13, R14, R15));
}
