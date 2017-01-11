package minijava.ir;

import static minijava.ir.Types.*;

import firm.*;
import firm.Type;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import minijava.ast.*;
import minijava.ast.Class;
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
      ClassType classType = new ClassType(decl.name());
      classTypes.put(decl, classType);
      for (Field f : decl.fields) {
        fields.put(f, createEntity(f));
      }
      if (decl.fields.size() == 0) {
        // We have to prevent class types of length 0, so we insert an unreachable field.
        String unusableFieldName = "0padding"; // unusable because of digit prefix
        Entity fieldEnt = new Entity(classType, unusableFieldName, BOOLEAN_TYPE);
        fieldEnt.setLdIdent(NameMangler.mangleInstanceFieldName(decl.name(), unusableFieldName));
      }
      for (Method m : decl.methods) {
        methods.put(m, createEntity(m));
      }
      //System.out.println("# " + classType);
      //System.out.println(classType.getSize());
      classType.layoutFields();
      classType.finishLayout();
      //System.out.println(classType.getSize());
    }
    return null;
  }

  private Entity createEntity(Field f) {
    Type type = storageType(f.type);
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
      return createMainMethod(m);
    }
    ClassType definingClass = classTypes.get(m.definingClass.def);
    ArrayList<Type> parameterTypes = new ArrayList<>();

    // Add the this pointer. It's always parameter 0, so access will be trivial.
    parameterTypes.add(ptrTo(definingClass));
    for (LocalVariable p : m.parameters) {
      // In the body, we need to refer to local variables by index, so we save that mapping.
      parameterTypes.add(storageType(p.type));
    }

    // The visitor returns null if that.returnType was void.
    Type[] returnTypes = {};
    if (!m.returnType.equals(minijava.ast.Type.VOID)) {
      returnTypes = new Type[] {storageType(m.returnType)};
    }

    Type methodType = new MethodType(parameterTypes.toArray(new Type[0]), returnTypes);

    // Set the mangled name
    Entity methodEnt = new Entity(definingClass, m.name(), methodType);
    methodEnt.setLdIdent(NameMangler.mangleName(new MethodInformation(methodEnt)));
    return methodEnt;
  }

  private Entity createMainMethod(Method m) {
    MethodType type = new MethodType(0, 0);
    SegmentType global = firm.Program.getGlobalType();
    Entity mainEnt = new Entity(global, "main", type);
    mainEnt.setLdIdent(NameMangler.mangledMainMethodName());
    return mainEnt;
  }

  private PointerType ptrTo(Type type) {
    return new PointerType(type);
  }
}
