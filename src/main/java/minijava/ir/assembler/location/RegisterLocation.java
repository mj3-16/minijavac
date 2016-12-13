package minijava.ir.assembler.location;

/** Location in a register */
public class RegisterLocation extends Location {

  public static enum Register {
    EAX("eax");
    /** GNU assembler representation */
    public final String asm;

    Register(String asm) {
      this.asm = asm;
    }
  }

  public final Register register;

  public RegisterLocation(Register register) {
    this.register = register;
  }

  @Override
  public String toGNUAssembler() {
    return "%" + register.asm;
  }
}
