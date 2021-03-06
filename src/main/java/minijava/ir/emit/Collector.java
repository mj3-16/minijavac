package minijava.ir.emit;

import firm.ClassType;
import firm.Entity;
import firm.MethodType;
import firm.SegmentType;
import firm.Type;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import minijava.ast.Class;
import minijava.ast.Field;
import minijava.ast.LocalVariable;
import minijava.ast.Method;
import minijava.ast.Program;
import minijava.ir.utils.MethodInformation;

class Collector implements Program.Visitor<Void> {

  private final IdentityHashMap<Class, ClassType> classTypes;
  private final IdentityHashMap<Field, Entity> fields;
  private final IdentityHashMap<Method, Entity> methods;

  public Collector(
      IdentityHashMap<Class, ClassType> classTypes,
      IdentityHashMap<Field, Entity> fields,
      IdentityHashMap<Method, Entity> methods) {
    this.classTypes = classTypes;
    this.fields = fields;
    this.methods = methods;
  }

  @Override
  public Void visitProgram(Program that) {
    for (Class decl : that.declarations) {
      // We need a first pass through the decls to have all class types available.
      ClassType classType = new ClassType(decl.name());
      classTypes.put(decl, classType);
    }

    for (Class decl : that.declarations) {
      ClassType classType = classTypes.get(decl);
      for (Field f : decl.fields) {
        fields.put(f, createEntity(f));
      }
      if (decl.fields.size() == 0) {
        // We have to prevent class types of length 0, so we insert an unreachable field.
        String unusableFieldName = "0padding"; // unusable because of digit prefix
        Entity fieldEnt = new Entity(classType, unusableFieldName, Types.BOOLEAN_TYPE);
        fieldEnt.setLdIdent(NameMangler.mangleInstanceFieldName(decl.name(), unusableFieldName));
      }
      for (Method m : decl.methods) {
        methods.put(m, createEntity(m));
      }
      classType.layoutFields();
      classType.finishLayout();
    }
    return null;
  }

  private Entity createEntity(Field f) {
    Type type = Types.storageType(f.type, classTypes);
    ClassType definingClass = classTypes.get(f.definingClass.def);
    Entity fieldEnt = new Entity(definingClass, f.name(), type);
    fieldEnt.setLdIdent(NameMangler.mangleInstanceFieldName(definingClass.getName(), f.name()));
    return fieldEnt;
  }

  /**
   * This will *not* go through the body of the method, just analyze stuff that is needed for
   * constructing an entity.
   */
  private Entity createEntity(Method m) {
    if (m.isStatic) {
      return createMainMethod();
    }
    ClassType definingClass = classTypes.get(m.definingClass.def);
    ArrayList<Type> parameterTypes = new ArrayList<>();

    // Add the this pointer. It's always parameter 0, so access will be trivial.
    parameterTypes.add(Types.pointerTo(definingClass));
    for (LocalVariable p : m.parameters) {
      // In the body, we need to refer to local variables by index, so we save that mapping.
      parameterTypes.add(Types.storageType(p.type, classTypes));
    }

    // The visitor returns null if that.returnType was void.
    Type[] returnTypes = {};
    if (!m.returnType.equals(minijava.ast.Type.VOID)) {
      returnTypes = new Type[] {Types.storageType(m.returnType, classTypes)};
    }

    MethodType methodType = new MethodType(parameterTypes.toArray(new Type[0]), returnTypes);

    // Set the mangled name
    Entity methodEnt = new Entity(definingClass, m.name(), methodType);
    methodEnt.setLdIdent(NameMangler.mangleName(new MethodInformation(methodEnt)));
    return methodEnt;
  }

  private Entity createMainMethod() {
    MethodType type = new MethodType(0, 0);
    SegmentType global = firm.Program.getGlobalType();
    Entity mainEnt = new Entity(global, "main", type);
    mainEnt.setLdIdent(NameMangler.mangledMainMethodName());
    return mainEnt;
  }
}
