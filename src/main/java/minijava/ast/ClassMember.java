package minijava.ast;

import java.util.List;

public interface ClassMember<TRef> {
  <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor);

  class FieldClassMember<TRef> implements ClassMember<TRef> {

    public final Type<TRef> type;
    public final String name;

    public FieldClassMember(Type<TRef> type, String name) {
      this.type = type;
      this.name = name;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitField(type, name);
    }
  }

  class MethodClassMember<TRef> implements ClassMember<TRef> {
    public final boolean isStatic;
    public final Type<TRef> returnType;
    public final String name;
    public final List<Parameter<TRef>> parameters;
    public final Statement.Block body;

    public MethodClassMember(
        boolean isStatic,
        Type<TRef> returnType,
        String name,
        List<Parameter<TRef>> parameters,
        Statement.Block body) {
      this.isStatic = isStatic;
      this.returnType = returnType;
      this.name = name;
      this.parameters = parameters;
      this.body = body;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitMethod(isStatic, returnType, name, parameters, body);
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

    TReturn visitField(Type<TRef> type, String name);

    TReturn visitMethod(
            boolean isStatic,
            Type<TRef> returnType,
            String name,
            List<Parameter<TRef>> parameters,
            Statement.Block body);
  }
}
