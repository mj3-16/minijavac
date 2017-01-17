package minijava.ir.emit;

import com.sun.jna.Platform;
import firm.*;
import java.util.*;
import java.util.stream.Collectors;
import minijava.ir.utils.MethodInformation;

public class NameMangler {

  private static final String SEP = "_";

  public static String mangleClassName(String className) {
    return SEP + SEP + replaceSep(className);
  }

  public static String mangleInstanceFieldName(String className, String fieldName) {
    return combineWithSep(mangleClassName(className), "I", replaceSep(fieldName));
  }

  public static String mangledMainMethodName() {
    return adjustLdNameToPlatform("mjMain");
  }

  public static String mangledPrintIntMethodName() {
    return adjustLdNameToPlatform("print_int");
  }

  public static String mangledCallocMethodName() {
    return adjustLdNameToPlatform("calloc_impl");
  }

  public static String mangledWriteIntMethodName() {
    return adjustLdNameToPlatform("write_int");
  }

  public static String mangledFlushMethodName() {
    return adjustLdNameToPlatform("flush");
  }

  public static String mangledReadIntMethodName() {
    return adjustLdNameToPlatform("read_int");
  }

  private static String adjustLdNameToPlatform(String ldName) {
    if (Platform.isMac() || Platform.isWindows()) {
      return "_" + ldName;
    }
    return ldName;
  }

  private static String replaceSep(String name) {
    return name.replace(SEP, SEP + SEP);
  }

  private static String combineWithSep(String... names) {
    return Arrays.stream(names).collect(Collectors.joining(SEP));
  }

  public static String mangleName(MethodInformation info) {
    String classType = mangleType(((PointerType) info.type.getParamType(0)).getPointsTo());
    StringBuilder builder = new StringBuilder();
    // method name and class
    builder
        .append("_ZN")
        .append(classType)
        .append(info.name.length())
        .append(info.name)
        .append("E");
    if (info.paramNumber == 1) { // only "this" parameter that doesn't count
      builder.append("v");
    } else {
      List<String> params = new ArrayList<>();
      for (int i = 1; i < info.paramNumber; i++) {
        params.add(mangleType(info.type.getParamType(i)));
      }
      Map<String, Integer> argsToIdx = new HashMap<>();
      int prevI = 1;
      for (int i = 1; i < info.paramNumber; i++) {
        Type paramType = info.type.getParamType(i);

        String strArg = params.get(i - 1);
        if (paramType instanceof PointerType) {
          if (argsToIdx.containsKey(strArg)) {
            params.set(i - 1, sArg(argsToIdx.get(strArg)));
          } else {
            for (String deref : getDeRefList(paramType)) {
              if (deref.length() > 1) {
                argsToIdx.put(deref, prevI);
                prevI++;
              }
            }
          }
        }
      }
      params.forEach(x -> builder.append(x));
    }
    return builder.toString();
  }

  private static String sArg(int argIdx) {
    if (argIdx == 0) {
      return "S_";
    }
    return String.format("S%d_", argIdx);
  }

  private static List<String> getDeRefList(Type type) {
    List<String> res = new ArrayList<>();
    while (type instanceof PointerType) {
      res.add(mangleType(type));
      type = ((PointerType) type).getPointsTo();
      if (type instanceof ArrayType) {
        type = ((ArrayType) type).getElementType();
      }
    }
    return res;
  }

  private static String mangleType(Type type) {
    if (type instanceof PointerType) {
      PointerType ptrType = (PointerType) type;
      if (ptrType.getPointsTo() instanceof ArrayType) {
        return "P" + mangleType(((ArrayType) ptrType.getPointsTo()).getElementType());
      }
      return "P" + mangleType(ptrType.getPointsTo());
    }
    if (type instanceof PrimitiveType) {
      if (type.getMode().equals(Types.PTR_TYPE.getMode())) {
        return "Pv";
      }
      return "i";
    }
    String name = ((ClassType) type).getName();
    return name.length() + name;
  }
}
