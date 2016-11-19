package minijava.semantic;

import minijava.ast.*;
import minijava.ast.Class;

public class AnalyzedTypesReplacer
    implements Program.Visitor<Ref, Void>,
        Class.Visitor<Ref, Void>,
        Field.Visitor<Ref, Void>,
        Method.Visitor<Ref, Void>,
        BlockStatement.Visitor<Ref, Void>,
        Expression.Visitor<Ref, Void> {

  private SymbolTable<Definition> analyzedTypes = new SymbolTable<>();
  private Type<Ref> currentClass;

  @Override
  public Void visitProgram(Program<? extends Ref> that) {
    analyzedTypes = that.acceptVisitor(new TypeCollector());
    for (Class<? extends Ref> declaration : that.declarations) {
      declaration.acceptVisitor(this);
    }
    return null;
  }

  @Override
  public Void visitClassDeclaration(Class<? extends Ref> that) {
    currentClass = new Type<>(new Ref(that), 0, that.range());
    for (Field<? extends Ref> field : that.fields) {
      field.acceptVisitor(this);
    }
    for (Method<? extends Ref> method : that.methods) {
      method.acceptVisitor(this);
    }
    return null;
  }

  @Override
  public Void visitField(Field<? extends Ref> that) {
    that.definingClass = currentClass;
    that.type.typeRef.def = analyzedTypes.lookup(that.type.typeRef.def.name()).get();
    return null;
  }

  @Override
  public Void visitMethod(Method<? extends Ref> that) {
    that.definingClass = currentClass;
    that.returnType.typeRef.def = analyzedTypes.lookup(that.returnType.typeRef.def.name()).get();
    for (Method.Parameter<? extends Ref> parameter : that.parameters) {
      parameter.type.typeRef.def = analyzedTypes.lookup(parameter.type.typeRef.def.name()).get();
    }
    that.body.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitBlock(Block<? extends Ref> that) {
    for (BlockStatement<? extends Ref> statement : that.statements) {
      statement.acceptVisitor(this);
    }
    return null;
  }

  @Override
  public Void visitVariable(BlockStatement.Variable<? extends Ref> that) {
    that.type.typeRef.def = analyzedTypes.lookup(that.type.typeRef.def.name()).get();
    that.rhs.ifPresent(e -> e.acceptVisitor(this));
    return null;
  }

  @Override
  public Void visitEmpty(Statement.Empty<? extends Ref> that) {
    return null;
  }

  @Override
  public Void visitIf(Statement.If<? extends Ref> that) {
    that.condition.acceptVisitor(this);
    that.then.acceptVisitor(this);
    that.else_.ifPresent(s -> s.acceptVisitor(this));
    return null;
  }

  @Override
  public Void visitExpressionStatement(Statement.ExpressionStatement<? extends Ref> that) {
    that.expression.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitWhile(Statement.While<? extends Ref> that) {
    that.condition.acceptVisitor(this);
    that.body.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitReturn(Statement.Return<? extends Ref> that) {
    that.expression.ifPresent(e -> e.acceptVisitor(this));
    return null;
  }

  @Override
  public Void visitBinaryOperator(Expression.BinaryOperator<? extends Ref> that) {
    that.left.acceptVisitor(this);
    that.right.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitUnaryOperator(Expression.UnaryOperator<? extends Ref> that) {
    that.expression.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitMethodCall(Expression.MethodCall<? extends Ref> that) {
    for (Expression<? extends Ref> argument : that.arguments) {
      argument.acceptVisitor(this);
    }
    Type<Ref> definingClass = ((Method<Ref>) that.method.def).definingClass;
    // special treatment for System.out.println(int)
    if (definingClass == Type.SYSTEM_OUT && that.method.def.name().equals("println")) {
      return null;
    }
    Class<Ref> analyzedClass =
        (Class<Ref>) analyzedTypes.lookup(definingClass.typeRef.name()).get();
    Method<Ref> methodInAnalyzedClass =
        analyzedClass
            .methods
            .stream()
            .filter(m -> m.name().equals(that.method.name()))
            .findAny()
            .get();
    that.method.def = methodInAnalyzedClass;
    return null;
  }

  @Override
  public Void visitFieldAccess(Expression.FieldAccess<? extends Ref> that) {
    Type<Ref> definingClass = ((Field<Ref>) that.field.def).definingClass;
    Class<Ref> analyzedClass =
        (Class<Ref>) analyzedTypes.lookup(definingClass.typeRef.name()).get();
    Field<Ref> fieldInAnalyzedClass =
        analyzedClass
            .fields
            .stream()
            .filter(f -> f.name().equals(that.field.name()))
            .findAny()
            .get();
    that.field.def = fieldInAnalyzedClass;
    return null;
  }

  @Override
  public Void visitArrayAccess(Expression.ArrayAccess<? extends Ref> that) {
    that.index.acceptVisitor(this);
    that.array.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitNewObject(Expression.NewObject<? extends Ref> that) {
    that.type.def = analyzedTypes.lookup(that.type.name()).get();
    return null;
  }

  @Override
  public Void visitNewArray(Expression.NewArray<? extends Ref> that) {
    that.type.typeRef.def = analyzedTypes.lookup(that.type.typeRef.name()).get();
    that.size.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitVariable(Expression.Variable<? extends Ref> that) {
    return null;
  }

  @Override
  public Void visitBooleanLiteral(Expression.BooleanLiteral<? extends Ref> that) {
    return null;
  }

  @Override
  public Void visitIntegerLiteral(Expression.IntegerLiteral<? extends Ref> that) {
    return null;
  }

  @Override
  public Void visitReferenceTypeLiteral(Expression.ReferenceTypeLiteral<? extends Ref> that) {
    return null;
  }
}
