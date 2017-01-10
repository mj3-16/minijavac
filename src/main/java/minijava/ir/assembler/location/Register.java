package minijava.ir.assembler.location;

import com.google.common.collect.ImmutableList;
import java.util.*;

/** Location in a register */
public class Register extends Location {

  public static enum Width {
    Byte("b", 1),
    /** 32 bit */
    Long("l", 4),
    /** 64 bit */
    Quad("q", 8);

    /** Suffix of instructions like <code>mov</code> that work with registers of this size */
    public final String asm;

    public final int sizeInBytes;

    Width(String asm, int sizeInBytes) {
      this.asm = asm;
      this.sizeInBytes = sizeInBytes;
    }
  }

  public static final Register BASE_POINTER;
  public static final Register STACK_POINTER;
  public static final Register RETURN_REGISTER;
  public static final Register EAX;
  public static final Register EBX;
  public static final Register EDX;
  public static final Register RAX;
  public static final Register RBX;
  public static final Register RDI;
  public static final Register RSI;
  public static final Register R14;
  public static final Register R15;

  private static final Map<String, Register> registerMap = new HashMap<>();
  public static final List<Register> methodArgumentQuadRegisters;
  public static final List<Register> usableRegisters;
  private static final String[][] byteLongAndQuadNames =
      new String[][] {
        {"al", "eax", "rax"},
        {"bl", "ebx", "rbx"},
        {"cl", "ecx", "rcx"},
        {"dl", "edx", "rdx"},
        {"sil", "esi", "rsi"},
        {"dil", "edi", "rdi"},
        {"bpl", "ebp", "rbp"},
        {"spl", "esp", "rsp"},
        {"r8b", "r8d", "r8"},
        {"r9b", "r9d", "r9"},
        {"r10b", "r10d", "r10"},
        {"r11b", "r11d", "r11"},
        {"r12b", "r12d", "r12"},
        {"r13b", "r13d", "r13"},
        {"r14b", "r14d", "r14"},
        {"r15b", "r15d", "r15"}
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
    RAX = get("rax");
    RBX = get("rbx");
    R14 = get("r14");
    R15 = get("r15");
    methodArgumentQuadRegisters =
        ImmutableList.of(get("rdi"), get("rsi"), get("rdx"), get("rcx"), get("r8"), get("r9"));
    List<Register> regs = new ArrayList<>();
    for (String[] byteLongAndQuadName : byteLongAndQuadNames) {
      String quad = byteLongAndQuadName[2];
      if (!quad.equals("rbp") && !quad.equals("rsp") && !quad.equals("rax")) {
        regs.add(get(quad));
      }
    }
    usableRegisters = Collections.unmodifiableList(regs);
  }

  private final int registerClassId;
  public final String registerName;
  private Register byteVersion;
  private Register longVersion;
  private Register quadVersion;

  private Register(Width width, int registerClassId, String registerName) {
    super(width);
    this.registerClassId = registerClassId;
    this.registerName = registerName;
  }

  @Override
  public String toGNUAssembler() {
    return "%" + registerName;
  }

  @Override
  public boolean equals(Object obj) {
    return (obj instanceof Register) && ((Register) obj).registerClassId == registerClassId;
  }

  private static void init() {
    int i = 0;
    for (String[] longAndQuadName : byteLongAndQuadNames) {
      registerMap.put(longAndQuadName[0], new Register(Width.Byte, i, longAndQuadName[0]));
      registerMap.put(longAndQuadName[1], new Register(Width.Long, i, longAndQuadName[1]));
      registerMap.put(longAndQuadName[2], new Register(Width.Quad, i, longAndQuadName[2]));
      i++;
    }
  }

  public static Register getByteVersion(Register reg) {
    if (reg.width.equals(Width.Byte)) {
      return reg;
    }
    return get(getRegNameRow(reg.registerName)[0]);
  }

  public static Register getLongVersion(Register reg) {
    if (reg.width.equals(Width.Long)) {
      return reg;
    }
    return get(getRegNameRow(reg.registerName)[1]);
  }

  public static Register getQuadVersion(Register reg) {
    if (reg.width.equals(Width.Quad)) {
      return reg;
    }
    return get(getRegNameRow(reg.registerName)[2]);
  }

  private static String[] getRegNameRow(String regName) {
    for (String[] row : byteLongAndQuadNames) {
      if (row[0].equals(regName) || row[1].equals(regName) || row[2].equals(regName)) {
        return row;
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
    return registerClassId;
  }

  public Register longVersion() {
    if (longVersion == null) {
      longVersion = getLongVersion(this);
    }
    return longVersion;
  }

  public Register byteVersion() {
    if (byteVersion == null) {
      byteVersion = getByteVersion(this);
    }
    return byteVersion;
  }

  public Register quadVersion() {
    if (quadVersion == null) {
      quadVersion = getQuadVersion(this);
    }
    return quadVersion;
  }

  public Register ofWidth(Width width) {
    switch (width) {
      case Byte:
        return byteVersion();
      case Long:
        return longVersion();
      case Quad:
        return quadVersion();
    }
    throw new RuntimeException();
  }

  @Override
  public String toString() {
    return toGNUAssembler();
  }
}
