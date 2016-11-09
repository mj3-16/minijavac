package minijava.ast;

public interface Type<TRef> {
  <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor);

  class IntType<TRef> implements Type<TRef> {
    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitInt();
    }
  }

  class VoidType<TRef> implements Type<TRef> {
    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitVoid();
    }
  }

  class ClassType<TRef> implements Type<TRef> {

    public final TRef classRef;

    public ClassType(TRef classRef) {
      this.classRef = classRef;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitClass(classRef);
    }
  }

  class BooleanType<TRef> implements Type<TRef> {
    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitBoolean();
    }
  }

  class ArrayType<TRef> implements Type<TRef> {

    public final Type<TRef> elementType;

    public ArrayType(Type<TRef> elementType) {
      this.elementType = elementType;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitArray(elementType);
    }
  }

  interface Visitor<TRef, TReturn> {

    TReturn visitArray(Type<TRef> elementType);

    TReturn visitClass(TRef classRef);

    TReturn visitVoid();

    TReturn visitBoolean();

    TReturn visitInt();
  }
}
