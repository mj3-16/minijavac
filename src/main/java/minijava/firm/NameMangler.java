package minijava.firm;

import java.util.Arrays;
import java.util.stream.Collectors;

public class NameMangler {

  private static final String SEP = "_";

  public static String mangleClassName(String className) {
    return SEP + replaceSep(className);
  }

  public static String mangleMethodName(String className, String methodName) {
    return combineWithSep(mangleClassName(className), "M", replaceSep(methodName));
  }

  public static String mangleInstanceFieldName(String className, String methodName) {
    return combineWithSep(mangleClassName(className), "I", replaceSep(methodName));
  }

  private static String replaceSep(String name) {
    return name.replace(SEP, SEP + SEP);
  }

  private static String combineWithSep(String... names) {
    return Arrays.stream(names).collect(Collectors.joining(SEP));
  }
}
