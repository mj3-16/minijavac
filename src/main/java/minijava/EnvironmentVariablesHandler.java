package minijava;

public class EnvironmentVariablesHandler {

  public static class Optimization {
    public static boolean turnedOff() {
      return isEnvVarSet("MJ_OPTIMIZE", "0");
    }

    public static boolean dontUseInliner() {
      return isEnvVarSet("MJ_OPT_USE_INLINER", "0");
    }
  }

  public static class Graph {
    public static boolean printGraphs() {
      return isEnvVarSet("MJ_GRAPH", "1");
    }
  }

  public static class Debug {
    public static boolean produceDebuggableBinary() {
      return isEnvVarSet("MJ_DBG", "1");
    }
  }

  public static class GC {
    public static boolean useGC() {
      return isEnvVarSet("MJ_USE_GC", "1");
    }
  }

  private static boolean isEnvVarSet(String varName, String varValue) {
    String value = System.getenv(varName);
    return value != null && value.equals(varValue);
  }

  private static boolean isEnvVarAvailable(String varName) {
    return System.getenv().containsKey(varName);
  }

  private static String getEnvVarValue(String varName) {
    if (isEnvVarAvailable(varName)) {
      return System.getenv(varName);
    }
    return null;
  }
}
