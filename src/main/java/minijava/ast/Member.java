package minijava.ast;

import java.util.List;

public interface Member<TRef> {
  <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor);

  class Field<TRef> implements Member<TRef> {

    public final Type<TRef> type;
    public final String name;

    public Field(Type<TRef> type, String name) {
      this.type = type;
      this.name = name;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitField(this);
    }
  }

  class Method<TRef> implements Member<TRef> {
    public final boolean isStatic;
    public final Type<TRef> returnType;
    public final String name;
    public final List<Parameter<TRef>> parameters;
    public final Block body;

    public Method(
        boolean isStatic,
        Type<TRef> returnType,
        String name,
        List<Parameter<TRef>> parameters,
        Block body) {
      this.isStatic = isStatic;
      this.returnType = returnType;
      this.name = name;
      this.parameters = parameters;
      this.body = body;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitMethod(this);
    }
  }

  class Parameter<TRef> {
    public final Type<TRef> type;
    public final String name;

    public Parameter(Type<TRef> type, String name) {
      this.type = type;
      this.name = name;
    }
  }

  interface Visitor<TRef, TReturn> {

    TReturn visitField(Field<TRef> that);

    TReturn visitMethod(Method<TRef> that);
  }
}
