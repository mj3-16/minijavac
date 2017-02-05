package minijava;

import java.util.HashMap;
import java.util.Map;

enum EnvVar {
  MJ_OPTIMIZE,
  MJ_OPT_USE_INLINER,
  MJ_GRAPH,
  MJ_DBG,
  MJ_USE_GC,
  MJ_GCC_APP,
  MJ_FILENAME
}

public class EnvironmentVariablesHandler {
  private static Map<EnvVar, EnvironmentVariable> registeredEnvironmentVariables = new HashMap<EnvVar, EnvironmentVariable>() {{
    put(EnvVar.MJ_OPTIMIZE, new EnvironmentVariable(EnvVar.MJ_OPTIMIZE));
    put(EnvVar.MJ_OPT_USE_INLINER, new EnvironmentVariable(EnvVar.MJ_OPT_USE_INLINER));
    put(EnvVar.MJ_GRAPH, new EnvironmentVariable(EnvVar.MJ_GRAPH));
    put(EnvVar.MJ_DBG, new EnvironmentVariable(EnvVar.MJ_DBG));
    put(EnvVar.MJ_USE_GC, new EnvironmentVariable(EnvVar.MJ_USE_GC));
    put(EnvVar.MJ_GCC_APP, new EnvironmentVariable(EnvVar.MJ_GCC_APP));
    put(EnvVar.MJ_FILENAME, new EnvironmentVariable(EnvVar.MJ_FILENAME));
  }};

  public static EnvironmentVariable variable(EnvVar envVar) {
    if(registeredEnvironmentVariables.containsKey(envVar)) {
      return registeredEnvironmentVariables.get(envVar);
    }
    return null;
  }

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
}

class EnvironmentVariable {
  private EnvVar envVar;
  public EnvironmentVariable(EnvVar envVar) {
    this.envVar = envVar;
  }

  public String value() {
    if(isAvailable()) {
      return getValue();
    }
    return "";
  }

  public boolean isSetToZero() {
    return isAvailable() && isSetToValue("0");
  }

  public boolean isSetToOne() {
    return isAvailable() && isSetToValue("1");
  }

  public boolean isSetToValue(String varValue) {
    String value = getValue();
    return value != null && value.equals(varValue);
  }

  private String getValue() {
    String value = System.getenv(this.envVar.toString());
    return value;
  }

  public boolean isAvailable() {
    return System.getenv().containsKey(this.envVar.toString());
  }
}
