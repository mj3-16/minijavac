package minijava.ast;

public interface Type<TRef> {
  <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor);

  class IntType<TRef> implements Type<TRef> {
    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitInt(this);
    }
  }

  class VoidType<TRef> implements Type<TRef> {
    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitVoid(this);
    }
  }

  class ClassType<TRef> implements Type<TRef> {

    public final TRef classRef;

    public ClassType(TRef classRef) {
      this.classRef = classRef;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitClass(this);
    }
  }

  class BooleanType<TRef> implements Type<TRef> {
    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitBoolean(this);
    }
  }

  class ArrayType<TRef> implements Type<TRef> {

    public final Type<TRef> elementType;

    public ArrayType(Type<TRef> elementType) {
      this.elementType = elementType;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitArray(this);
    }
  }

  interface Visitor<TRef, TReturn> {

    TReturn visitArray(ArrayType<TRef> that);

    TReturn visitClass(ClassType<TRef> that);

    TReturn visitVoid(VoidType<TRef> that);

    TReturn visitBoolean(BooleanType<TRef> that);

    TReturn visitInt(IntType<TRef> that);
  }
}
