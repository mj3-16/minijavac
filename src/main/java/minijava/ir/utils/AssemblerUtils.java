package minijava.ir.utils;

public class AssemblerUtils {

  public static boolean doesIntegerFitIntoImmPartOfInstruction(long value) {
    return value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE;
  }
}
