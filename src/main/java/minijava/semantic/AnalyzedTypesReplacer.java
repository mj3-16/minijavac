package minijava.semantic;

import minijava.ast.*;
import minijava.ast.Class;

public class AnalyzedTypesReplacer
    implements Program.Visitor<Void>,
        Class.Visitor<Void>,
        Field.Visitor<Void>,
        Method.Visitor<Void>,
        BlockStatement.Visitor<Void>,
        Expression.Visitor<Void> {

  private SymbolTable<Definition> analyzedTypes = new SymbolTable<>();
  private Type currentClass;

  @Override
  public Void visitProgram(Program that) {
    analyzedTypes = that.acceptVisitor(new TypeCollector());
    for (Class declaration : that.declarations) {
      declaration.acceptVisitor(this);
    }
    return null;
  }

  @Override
  public Void visitClassDeclaration(Class that) {
    currentClass = new Type(new Ref<Definition>(that), 0, that.range());
    for (Field field : that.fields) {
      field.acceptVisitor(this);
    }
    for (Method method : that.methods) {
      method.acceptVisitor(this);
    }
    return null;
  }

  @Override
  public Void visitField(Field that) {
    that.definingClass = currentClass;
    that.type.basicType.def = analyzedTypes.lookup(that.type.basicType.def.name()).get();
    return null;
  }

  @Override
  public Void visitMethod(Method that) {
    that.definingClass = currentClass;
    that.returnType.basicType.def =
        analyzedTypes.lookup(that.returnType.basicType.def.name()).get();
    for (Method.Parameter parameter : that.parameters) {
      parameter.type.basicType.def =
          analyzedTypes.lookup(parameter.type.basicType.def.name()).get();
    }
    that.body.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitBlock(Block that) {
    for (BlockStatement statement : that.statements) {
      statement.acceptVisitor(this);
    }
    return null;
  }

  @Override
  public Void visitVariable(BlockStatement.Variable that) {
    that.type.basicType.def = analyzedTypes.lookup(that.type.basicType.def.name()).get();
    that.rhs.ifPresent(e -> e.acceptVisitor(this));
    return null;
  }

  @Override
  public Void visitEmpty(Statement.Empty that) {
    return null;
  }

  @Override
  public Void visitIf(Statement.If that) {
    that.condition.acceptVisitor(this);
    that.then.acceptVisitor(this);
    that.else_.ifPresent(s -> s.acceptVisitor(this));
    return null;
  }

  @Override
  public Void visitExpressionStatement(Statement.ExpressionStatement that) {
    that.expression.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitWhile(Statement.While that) {
    that.condition.acceptVisitor(this);
    that.body.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitReturn(Statement.Return that) {
    that.expression.ifPresent(e -> e.acceptVisitor(this));
    return null;
  }

  @Override
  public Void visitBinaryOperator(Expression.BinaryOperator that) {
    that.left.acceptVisitor(this);
    that.right.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitUnaryOperator(Expression.UnaryOperator that) {
    that.expression.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitMethodCall(Expression.MethodCall that) {
    for (Expression argument : that.arguments) {
      argument.acceptVisitor(this);
    }
    Type definingClass = ((Method) that.method.def).definingClass;
    // special treatment for System.out.println(int)
    if (definingClass == Type.SYSTEM_OUT && that.method.def.name().equals("println")) {
      return null;
    }
    Class analyzedClass = (Class) analyzedTypes.lookup(definingClass.basicType.name()).get();
    Method methodInAnalyzedClass =
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
  public Void visitFieldAccess(Expression.FieldAccess that) {
    Type definingClass = ((Field) that.field.def).definingClass;
    Class analyzedClass = (Class) analyzedTypes.lookup(definingClass.basicType.name()).get();
    Field fieldInAnalyzedClass =
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
  public Void visitArrayAccess(Expression.ArrayAccess that) {
    that.index.acceptVisitor(this);
    that.array.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitNewObject(Expression.NewObject that) {
    that.class_.def = analyzedTypes.lookup(that.class_.name()).get();
    return null;
  }

  @Override
  public Void visitNewArray(Expression.NewArray that) {
    that.type.basicType.def = analyzedTypes.lookup(that.type.basicType.name()).get();
    that.size.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitVariable(Expression.Variable that) {
    return null;
  }

  @Override
  public Void visitBooleanLiteral(Expression.BooleanLiteral that) {
    return null;
  }

  @Override
  public Void visitIntegerLiteral(Expression.IntegerLiteral that) {
    return null;
  }

  @Override
  public Void visitReferenceTypeLiteral(Expression.ReferenceTypeLiteral that) {
    return null;
  }
}
