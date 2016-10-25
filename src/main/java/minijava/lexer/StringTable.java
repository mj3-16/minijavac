package minijava.lexer;

import java.util.HashMap;
import java.util.Map;

/** String table that maps strings to ids (and ids to their corresponding strings). */
public class StringTable {

  private int idCounter;
  private Map<String, Integer> stringIds = new HashMap<>();
  private Map<Integer, String> idToString = new HashMap<>();
  private static Map<String, Integer> defaultStrings = new HashMap<>();
  public static final int BOOLEAN_KEYWORD_ID = addDefaultString("boolean");
  public static final int INT_KEYWORD_ID = addDefaultString("int");
  public static final int CLASS_KEYWORD_ID = addDefaultString("class");
  public static final int NEW_KEYWORD_ID = addDefaultString("new");
  public static final int RETURN_KEYWORD_ID = addDefaultString("return");
  public static final int THIS_KEYWORD_ID = addDefaultString("this");
  public static final int IF_KEYWORD_ID = addDefaultString("if");
  public static final int WHILE_KEYWORD_ID = addDefaultString("while");
  public static final int ELSE_KEYWORD_ID = addDefaultString("else");
  public static final int TRUE_KEYWORD_ID = addDefaultString("true");
  public static final int FALSE_KEYWORD_ID = addDefaultString("false");
  public static final int PUBLIC_KEYWORD_ID = addDefaultString("public");
  public static final int STATIC_KEYWORD_ID = addDefaultString("static");
  public static final int VOID_KEYWORD_ID = addDefaultString("void");
  public static final int NULL_KEYWORD_ID = addDefaultString("null");
  public static final int STRING_KEYWORD_ID = addDefaultString("String");

  public StringTable() {
    this.idCounter = defaultStrings.size();
    for (String str : defaultStrings.keySet()) {
      stringIds.put(str, defaultStrings.get(str));
      idToString.put(defaultStrings.get(str), str);
    }
  }

  /** Returns the id of a given string and adds the string to the table if needed. */
  int getStringId(String str) {
    if (!stringIds.containsKey(str)) {
      return addString(str);
    }
    return stringIds.get(str);
  }

  int addString(String str) {
    int newId = generateNewId();
    stringIds.put(str, newId);
    idToString.put(newId, str);
    return newId;
  }

  private static int addDefaultString(String str) {
    int id = defaultStrings.size();
    defaultStrings.put(str, id);
    return id;
  }

  public String getString(int id) {
    return idToString.get(id);
  }

  public boolean hasId(int id) {
    return idToString.containsKey(id);
  }

  public boolean hasString(String str) {
    return stringIds.containsKey(str);
  }

  private int generateNewId() {
    return idCounter++;
  }
}
