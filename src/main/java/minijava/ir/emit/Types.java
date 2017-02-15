package minijava.ir.emit;

import firm.ArrayType;
import firm.ClassType;
import firm.Entity;
import firm.MethodType;
import firm.Mode;
import firm.PointerType;
import firm.PrimitiveType;
import firm.Program;
import firm.Type;
import java.util.HashMap;
import java.util.Map;
import minijava.ast.BuiltinType;
import minijava.ast.Class;
import minijava.ir.utils.InitFirm;

public class Types {

  public static final Type INT_TYPE;
  public static final Type BOOLEAN_TYPE;
  public static final Type PTR_TYPE;
  public static final MethodType CALLOC_TYPE;
  public static final Entity CALLOC;
  public static final MethodType PRINT_INT_TYPE;
  public static final Entity PRINT_INT;
  public static final MethodType WRITE_INT_TYPE;
  public static final Entity WRITE_INT;
  public static final MethodType FLUSH_TYPE;
  public static final Entity FLUSH;
  public static final MethodType READ_INT_TYPE;
  public static final Entity READ_INT;
  private static final Map<Type, ArrayType> ARRAY_OF = new HashMap<>();
  private static final Map<Type, PointerType> POINTER_TO = new HashMap<>();

  static {
    // If we consistently call InitFirm.init() throughout our code, we guarantee that
    // Firm.init() will be called exactly once, even if e.g. the test suite also needs to
    // call Firm.init().
    InitFirm.init();
    INT_TYPE = new PrimitiveType(Mode.getIs());
    BOOLEAN_TYPE = new PrimitiveType(Mode.getBu());
    PTR_TYPE = new PrimitiveType(Mode.getP());
    // We have to initialize these exactly once because of name clashes.
    CALLOC_TYPE =
        new MethodType(new Type[] {Types.PTR_TYPE, Types.PTR_TYPE}, new Type[] {Types.PTR_TYPE});
    CALLOC =
        new Entity(
            Program.getGlobalType(), NameMangler.mangledCallocMethodName(), Types.CALLOC_TYPE);
    PRINT_INT_TYPE = new MethodType(new Type[] {Types.INT_TYPE}, new Type[] {});
    PRINT_INT =
        new Entity(
            Program.getGlobalType(), NameMangler.mangledPrintIntMethodName(), Types.PRINT_INT_TYPE);
    WRITE_INT_TYPE = new MethodType(new Type[] {Types.INT_TYPE}, new Type[] {});
    WRITE_INT =
        new Entity(
            Program.getGlobalType(), NameMangler.mangledWriteIntMethodName(), Types.WRITE_INT_TYPE);
    FLUSH_TYPE = new MethodType(new Type[] {}, new Type[] {});
    FLUSH =
        new Entity(Program.getGlobalType(), NameMangler.mangledFlushMethodName(), Types.FLUSH_TYPE);
    READ_INT_TYPE = new MethodType(new Type[] {}, new Type[] {Types.INT_TYPE});
    READ_INT =
        new Entity(
            Program.getGlobalType(), NameMangler.mangledReadIntMethodName(), Types.READ_INT_TYPE);
  }

  /** Cashes array types, so that we can test for equality. */
  static ArrayType arrayOf(Type type) {
    return ARRAY_OF.computeIfAbsent(type, t -> new ArrayType(t, 0));
  }

  /** Cashes pointer types, so that we can test for equality. */
  static PointerType pointerTo(Type type) {
    return POINTER_TO.computeIfAbsent(type, PointerType::new);
  }

  /**
   * Returns the type in which to store AST type @type@. Consider a @boolean b;@ declaration, that
   * should have BOOLEAN_TYPE, whereas @A a;@ for a reference type @A@ should return the type of the
   * reference, e.g. a pointer @new PointerType(actualClassType)@
   *
   * <p>So, for value types just return their value, for reference types return a pointer.
   */
  static Type storageType(minijava.ast.Type type, Map<Class, ClassType> classTypes) {
    return type.acceptVisitor(new StorageTypeVisitor(classTypes));
  }

  public static String methodTypeToString(MethodType mt) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < mt.getNParams(); ++i) {
      sb.append(mt.getParamType(0));
      sb.append(" -> ");
    }
    if (mt.getNRess() == 0) {
      sb.append("void");
    } else {
      sb.append(mt.getResType(0));
    }
    return sb.toString();
  }

  private static class StorageTypeVisitor
      implements minijava.ast.Type.Visitor<Type>, minijava.ast.BasicType.Visitor<Type> {

    private final Map<Class, ClassType> classTypes;

    public StorageTypeVisitor(Map<Class, ClassType> classTypes) {
      this.classTypes = classTypes;
    }

    @Override
    public Type visitType(minijava.ast.Type that) {
      Type type = that.basicType.def.acceptVisitor(this);
      if (type == null) {
        // e.g. void
        return null;
      }
      for (int i = 0; i < that.dimension; i++) {
        // We don't know the array statically, so just pass 0
        // of the number of elements (which is allowed according
        // to the docs)
        type = pointerTo(arrayOf(type));
      }
      return type;
    }

    @Override
    public Type visitVoid(BuiltinType that) {
      return null;
    }

    @Override
    public Type visitInt(BuiltinType that) {
      return INT_TYPE;
    }

    @Override
    public Type visitBoolean(BuiltinType that) {
      return BOOLEAN_TYPE;
    }

    @Override
    public Type visitAny(BuiltinType that) {
      assert false;
      return null;
    }

    @Override
    public Type visitClass(Class that) {
      return pointerTo(classTypes.get(that));
    }
  }
}
