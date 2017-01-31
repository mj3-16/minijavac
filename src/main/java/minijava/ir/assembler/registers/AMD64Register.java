package minijava.ir.assembler.registers;

import com.google.common.collect.ImmutableList;
import java.util.*;
import minijava.ir.assembler.operands.OperandWidth;

/** Register in a register */
public class AMD64Register extends Register {

  public static final AMD64Register BASE_POINTER;
  public static final AMD64Register STACK_POINTER;
  public static final AMD64Register RETURN_REGISTER;
  public static final AMD64Register EAX;
  public static final AMD64Register EBX;
  public static final AMD64Register EDX;
  public static final AMD64Register RAX;
  public static final AMD64Register RBX;
  public static final AMD64Register RDI;
  public static final AMD64Register RSI;
  public static final AMD64Register R14;
  public static final AMD64Register R15;

  private static final Map<String, AMD64Register> registerMap = new HashMap<>();
  public static final List<AMD64Register> methodArgumentQuadRegisters;
  public static final List<AMD64Register> usableRegisters;
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
    List<AMD64Register> regs = new ArrayList<>();
    for (String[] byteLongAndQuadName : byteLongAndQuadNames) {
      String quad = byteLongAndQuadName[2];
      if (!quad.equals("rbp") && !quad.equals("rsp")) {
        regs.add(get(quad));
      }
    }
    usableRegisters = Collections.unmodifiableList(regs);
  }

  private final int registerClassId;
  public final String registerName;
  private AMD64Register byteVersion;
  private AMD64Register longVersion;
  private AMD64Register quadVersion;

  private AMD64Register(OperandWidth width, int registerClassId, String registerName) {
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
    return (obj instanceof AMD64Register)
        && ((AMD64Register) obj).registerClassId == registerClassId;
  }

  private static void init() {
    int i = 0;
    for (String[] longAndQuadName : byteLongAndQuadNames) {
      registerMap.put(
          longAndQuadName[0], new AMD64Register(OperandWidth.Byte, i, longAndQuadName[0]));
      registerMap.put(
          longAndQuadName[1], new AMD64Register(OperandWidth.Long, i, longAndQuadName[1]));
      registerMap.put(
          longAndQuadName[2], new AMD64Register(OperandWidth.Quad, i, longAndQuadName[2]));
      i++;
    }
  }

  public static AMD64Register getByteVersion(AMD64Register reg) {
    if (reg.width.equals(OperandWidth.Byte)) {
      return reg;
    }
    return get(getRegNameRow(reg.registerName)[0]);
  }

  public static AMD64Register getLongVersion(AMD64Register reg) {
    if (reg.width.equals(OperandWidth.Long)) {
      return reg;
    }
    return get(getRegNameRow(reg.registerName)[1]);
  }

  public static AMD64Register getQuadVersion(AMD64Register reg) {
    if (reg.width.equals(OperandWidth.Quad)) {
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

  public static AMD64Register get(String registerName) {
    assert registerMap.containsKey(registerName);
    return registerMap.get(registerName);
  }

  @Override
  public int hashCode() {
    return registerClassId;
  }

  public AMD64Register longVersion() {
    if (longVersion == null) {
      longVersion = getLongVersion(this);
    }
    return longVersion;
  }

  public AMD64Register byteVersion() {
    if (byteVersion == null) {
      byteVersion = getByteVersion(this);
    }
    return byteVersion;
  }

  public AMD64Register quadVersion() {
    if (quadVersion == null) {
      quadVersion = getQuadVersion(this);
    }
    return quadVersion;
  }

  public AMD64Register ofWidth(OperandWidth width) {
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
