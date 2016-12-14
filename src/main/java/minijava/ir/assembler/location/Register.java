package minijava.ir.assembler.location;

/** Location in a register */
public class Register extends Location {

  public static enum Width {
    Long("l"),
    Quad("q");

    /** Suffix of instructions like <code>mov</code> that work with registers of this size */
    public final String asm;

    Width(String asm) {
      this.asm = asm;
    }
  }

  public static final Register BASE_POINTER = new Register("rbp", Width.Quad);
  public static final Register STACK_POINTER = new Register("rsp", Width.Quad);
  public static final Register RETURN_REGISTER = new Register("rax", Width.Long);
  public static final Register EAX = new Register("eax", Width.Long);
  public static final Register EBX = new Register("ebx", Width.Long);
  public static final Register EDX = new Register("edx", Width.Long);
  public static final Register RDI = new Register("rdi", Width.Long);
  public static final Register RSI = new Register("rsi", Width.Long);

  public final String registerName;
  public final Width width;

  public Register(String registerName, Width width) {
    this.registerName = registerName;
    this.width = width;
  }

  @Override
  public String toGNUAssembler() {
    return "%" + registerName;
  }

  @Override
  public boolean equals(Object obj) {
    return (obj instanceof Register) && ((Register) obj).registerName.equals(registerName);
  }
}
