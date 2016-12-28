package minijava.ir.assembler.location;

import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Location in a register */
public class Register extends Location {

  public static enum Width {
    /** 32 bit */
    Long("l"),
    /** 64 bit */
    Quad("q");

    /** Suffix of instructions like <code>mov</code> that work with registers of this size */
    public final String asm;

    Width(String asm) {
      this.asm = asm;
    }
  }

  public static final Register BASE_POINTER;
  public static final Register STACK_POINTER;
  public static final Register RETURN_REGISTER;
  public static final Register EAX;
  public static final Register EBX;
  public static final Register EDX;
  public static final Register RDI;
  public static final Register RSI;

  private static final Map<String, Register> registerMap = new HashMap<>();
  public static final List<Register> methodArgumentQuadRegisters;
  private static final String[][] longAndQuadNames =
      new String[][] {
        {"eax", "rax"},
        {"ebx", "rbx"},
        {"ecx", "rcx"},
        {"edx", "rdx"},
        {"esi", "rsi"},
        {"edi", "rdi"},
        {"ebp", "rbp"},
        {"esp", "rsp"},
        {"r8d", "r8"},
        {"r9d", "r9"},
        {"r10d", "r10"},
        {"r11d", "r11"},
        {"r12d", "r12"},
        {"r13d", "r13"},
        {"r14d", "r14"},
        {"r15d", "r15"}
      };

  static {
    init();
    BASE_POINTER = get("rbp");
    STACK_POINTER = get("rsp");
    RETURN_REGISTER = get("rax");
    EAX = get("eax");
    EBX = get("ebx");
    EDX = get("edx");
    RDI = get("rdi");
    RSI = get("rsi");
    methodArgumentQuadRegisters =
        ImmutableList.of(get("rdi"), get("rsi"), get("rdx"), get("rcx"), get("r8"), get("r9"));
  }

  public final String registerName;
  public final Width width;

  private Register(String registerName, Width width) {
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

  private static void init() {
    for (String[] longAndQuadName : longAndQuadNames) {
      registerMap.put(longAndQuadName[0], new Register(longAndQuadName[0], Width.Long));
      registerMap.put(longAndQuadName[1], new Register(longAndQuadName[1], Width.Quad));
    }
  }

  public static Register getLongVersion(Register reg) {
    if (reg.width.equals(Width.Long)) {
      return reg;
    }
    for (String[] longAndQuadName : longAndQuadNames) {
      if (longAndQuadName[1].equals(reg.registerName)) {
        return get(longAndQuadName[0]);
      }
    }
    throw new RuntimeException();
  }

  public static Register getQuadVersion(Register reg) {
    if (reg.width.equals(Width.Quad)) {
      return reg;
    }
    for (String[] longAndQuadName : longAndQuadNames) {
      if (longAndQuadName[0].equals(reg.registerName)) {
        return get(longAndQuadName[1]);
      }
    }
    throw new RuntimeException();
  }

  public static Register get(String registerName) {
    assert registerMap.containsKey(registerName);
    return registerMap.get(registerName);
  }

  public static Width minWidth(Register first, Register second) {
    if (first.width.ordinal() < second.width.ordinal()) {
      return first.width;
    }
    return second.width;
  }

  @Override
  public int hashCode() {
    return registerName.hashCode();
  }
}
