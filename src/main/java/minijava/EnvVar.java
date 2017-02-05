package minijava;

public enum EnvVar {
  MJ_OPTIMIZE,
  MJ_OPT_USE_INLINER,
  MJ_GRAPH,
  MJ_DBG,
  MJ_USE_GC,
  MJ_GCC_APP,
  MJ_FILENAME;

  public final String description;

  EnvVar() {
    description = "";
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
}
