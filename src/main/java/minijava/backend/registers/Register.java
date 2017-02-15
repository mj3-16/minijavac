package minijava.backend.registers;

import java.util.function.Consumer;
import java.util.function.Function;

/** Register for an assembler register */
public interface Register {
  <T> T match(Function<VirtualRegister, T> matchVirtual, Function<AMD64Register, T> matchHardware);

  default void match(
      Consumer<VirtualRegister> matchVirtual, Consumer<AMD64Register> matchHardware) {
    match(
        vr -> {
          matchVirtual.accept(vr);
          return null;
        },
        hr -> {
          matchHardware.accept(hr);
          return null;
        });
  }
}
