package minijava.ir;

import com.sun.jna.Platform;
import java.util.Arrays;
import java.util.stream.Collectors;

public class NameMangler {

  private static final String SEP = "_";

  public static String mangleClassName(String className) {
    return SEP + SEP + replaceSep(className);
  }

  public static String mangleMethodName(String className, String methodName) {
    return combineWithSep(mangleClassName(className), "M", replaceSep(methodName));
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
}
