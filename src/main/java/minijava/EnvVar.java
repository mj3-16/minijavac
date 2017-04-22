package minijava;

import java.util.ArrayList;

public enum EnvVar {
  MJ_OPT_USE_INLINER("Set to \"0\" to turn off inliner in optimizations."),
  MJ_GRAPH("Set to \"1\" to turn on graph printing."),
  MJ_DBG,
  MJ_USE_GC("Set to \"1\" to use the bdwgc."),
  MJ_GCC_APP,
  MJ_FILENAME,
  MJ_OUTPUTFILENAME("Specifies output file name."),
  MJ_OPT_TIME("Set timeout for optimizations in seconds.");

  public final String description;

  EnvVar() {
    description = "No description given.";
  };

  EnvVar(String description) {
    this.description = description;
  }

  public String value() {
    if (isAvailable()) {
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
    String value = System.getenv(this.name());
    return value;
  }

  public boolean isAvailable() {
    return System.getenv().containsKey(this.name());
  }

  public static String[] getAllEnvVarDescriptions() {
    ArrayList<String> descriptions = new ArrayList<String>();
    for (EnvVar envVariable : EnvVar.values()) {
      descriptions.add(envVariable.name() + ": " + envVariable.description);
    }
    return descriptions.toArray(new String[0]);
  }
}
