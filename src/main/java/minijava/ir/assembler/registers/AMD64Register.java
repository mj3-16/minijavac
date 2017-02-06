package minijava.ir.assembler.registers;

import com.google.common.collect.Sets;
import java.util.Set;

/** General purpose hardware register of the AMD64 ISA. */
public enum AMD64Register implements Register {
  A,
  C,
  D,
  B,
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

  public static Set<AMD64Register> allocatable =
      Sets.newHashSet(A, C, D, B, SI, DI, R8, R9, R10, R11, R12, R13, R14, R15);
}
