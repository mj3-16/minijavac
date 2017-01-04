package minijava.ir;

import firm.*;
import firm.Program;
import firm.Type;
import minijava.ast.*;
import minijava.ast.Class;

class Types {

  static final Type INT_TYPE;
  static final Type BOOLEAN_TYPE;
  static final Type PTR_TYPE;
  static final MethodType CALLOC_TYPE;
  static final Entity CALLOC;
  static final MethodType PRINT_INT_TYPE;
  static final Entity PRINT_INT;

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
  }

  /**
   * Returns the type in which to store AST type @type@. Consider a @boolean b;@ declaration, that
   * should have BOOLEAN_TYPE, whereas @A a;@ for a reference type @A@ should return the type of the
   * reference, e.g. a pointer @new PointerType(actualClassType)@
   *
   * <p>So, for value types just return their value, for reference types return a pointer.
   */
  static Type storageType(minijava.ast.Type type) {
    return type.acceptVisitor(new StorageTypeVisitor());
  }

  private static class StorageTypeVisitor
      implements minijava.ast.Type.Visitor<Type>, minijava.ast.BasicType.Visitor<Type> {

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
        type = new PointerType(new ArrayType(type, 0));
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
      // The actual firm.ClassType is irrelevant for storing references to it.
      return new PrimitiveType(Mode.getP());
    }
  }
}
