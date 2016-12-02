package minijava.ir;

import firm.*;
import org.jetbrains.annotations.NotNull;

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
   * Returns the primitive type in which to store type @type@. Consider a @boolean b;@ declaration,
   * that should have PrimitiveType(Mode.getBu());, wheras @A a;@ for a reference type @A@ should
   * return the type of the reference, e.g. a pointer @new PrimitiveType(Mode.getP());@
   *
   * <p>So, for value types just return their value, for reference types return a pointer.
   */
  @NotNull
  static PrimitiveType getStorageType(minijava.ast.Type type) {
    // TODO shouldn't we reuse these types?
    return new PrimitiveType(storageModeForType(type));
  }

  static Mode storageModeForType(minijava.ast.Type type) {
    if (type.dimension > 0) {
      return Mode.getP();
    }

    switch (type.basicType.name()) {
      case "int":
        return Mode.getIs();
      case "boolean":
        return Mode.getBu();
      default:
        return Mode.getP();
    }
  }
}
