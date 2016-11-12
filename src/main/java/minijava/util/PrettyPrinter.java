package minijava.util;

import com.google.common.base.Strings;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import minijava.ast.*;
import minijava.ast.Class;

public class PrettyPrinter<TRef>
    implements Program.Visitor<TRef, CharSequence>,
        Class.Visitor<TRef, CharSequence>,
        Field.Visitor<TRef, CharSequence>,
        Method.Visitor<TRef, CharSequence>,
        Type.Visitor<TRef, CharSequence>,
        BlockStatement.Visitor<TRef, CharSequence>,
        Expression.Visitor<TRef, CharSequence> {

  private int indentLevel = 0;

  public PrettyPrinter() {}

  private static CharSequence outerParanthesesRemoved(CharSequence seq) {
    if (seq.charAt(0) == '(') {
      return seq.subSequence(1, seq.length() - 1);
    }
    return seq;
  }

  private CharSequence indent() {
    return Strings.repeat("\t", indentLevel);
  }

  @Override
  public CharSequence visitProgram(Program<TRef> that) {
    return that.declarations
        .stream()
        .sorted((left, right) -> left.name.compareTo(right.name))
        .map(c -> c.acceptVisitor(this))
        .collect(Collectors.joining());
  }

  @Override
  public CharSequence visitClassDeclaration(Class<TRef> that) {
    StringBuilder sb = new StringBuilder("class ").append(that.name).append(" {");
    if (that.fields.isEmpty() && that.methods.isEmpty()) {
      return sb.append(" }").append(System.lineSeparator());
    }
    sb.append(System.lineSeparator());
    indentLevel++;
    that.methods
        .stream()
        .sorted((left, right) -> left.name.compareTo(right.name))
        .map(m -> m.acceptVisitor(this))
        .forEach(s -> sb.append(indent()).append(s).append(System.lineSeparator()));
    that.fields
        .stream()
        .sorted((left, right) -> left.name.compareTo(right.name))
        .map(m -> m.acceptVisitor(this))
        .forEach(s -> sb.append(indent()).append(s).append(System.lineSeparator()));
    indentLevel--;
    return sb.append("}").append(System.lineSeparator());
  }

  @Override
  public CharSequence visitMethod(Method<TRef> that) {
    StringBuilder sb = new StringBuilder("public ");
    if (that.isStatic) {
      sb.append("static ");
    }
    sb.append(that.returnType.acceptVisitor(this)).append(" ").append(that.name).append("(");
    Iterator<Method.Parameter<TRef>> iterator = that.parameters.iterator();
    Method.Parameter<TRef> next;
    if (iterator.hasNext()) {
      next = iterator.next();
      sb.append(next.type.acceptVisitor(this)).append(" ").append(next.name);
      while (iterator.hasNext()) {
        next = iterator.next();
        sb.append(", ").append(next.type.acceptVisitor(this)).append(" ").append(next.name);
      }
    }
    return sb.append(") ").append(that.body.acceptVisitor(this));
  }

  @Override
  public CharSequence visitBlock(Block<TRef> that) {
    StringBuilder sb = new StringBuilder("{");
    List<BlockStatement<TRef>> nonEmptyStatements =
        that.statements
            .stream()
            .filter(s -> !(s instanceof Statement.EmptyStatement))
            .collect(Collectors.toList());
    if (nonEmptyStatements.isEmpty()) {
      return sb.append(" }");
    }
    sb.append(System.lineSeparator());
    indentLevel++;
    nonEmptyStatements
        .stream()
        .map(s -> s.acceptVisitor(this))
        .forEach(s -> sb.append(indent()).append(s).append(System.lineSeparator()));
    indentLevel--;
    return sb.append(indent()).append("}");
  }

  @Override
  public CharSequence visitIf(Statement.If<TRef> that) {
    StringBuilder b = new StringBuilder().append("if ").append(that.condition.acceptVisitor(this));
    // a block follows immediately after a space, a single statement needs new line and indentation
    if (that.then instanceof Block) {
      b.append(" ").append(that.then.acceptVisitor(this));
    } else {
      indentLevel++;
      b.append(System.lineSeparator()).append(indent()).append(that.then.acceptVisitor(this));
      indentLevel--;
    }
    // 2 possible states:
    // if $(expr) { ... }
    // if $(expr)\n$(indent)$(stmt);
    if (!that.else_.isPresent()) {
      return b;
    }
    Statement<TRef> else_ = that.else_.get();
    // if 'then' part was a block, 'else' follows '}' directly
    if (that.then instanceof Block) {
      b.append(" else");
    } else {
      // otherwise break the line and indent first
      b.append(System.lineSeparator()).append(indent()).append("else");
    }
    if (else_ instanceof Block) {
      return b.append(" ").append(else_.acceptVisitor(this));
    } else if (else_ instanceof Statement.If) {
      return b.append(" ").append(else_.acceptVisitor(this));
    } else {
      indentLevel++;
      b.append(System.lineSeparator()).append(indent()).append(else_.acceptVisitor(this));
      indentLevel--;
      return b;
    }
  }

  @Override
  public CharSequence visitWhile(Statement.While<TRef> that) {
    StringBuilder sb =
        new StringBuilder("while (").append(that.condition.acceptVisitor(this)).append(")");
    if (that.body instanceof Block) {
      return sb.append(" ").append(that.body.acceptVisitor(this));
    }
    indentLevel++;
    sb.append(System.lineSeparator()).append(indent());
    indentLevel--;
    return sb.append(that.body.acceptVisitor(this));
  }

  @Override
  public CharSequence visitField(Field<TRef> that) {
    return new StringBuilder("public ")
        .append(that.type.acceptVisitor(this))
        .append(" ")
        .append(that.name)
        .append(";");
  }

  @Override
  public CharSequence visitType(Type<TRef> that) {
    StringBuilder b = new StringBuilder(that.typeRef.toString());
    b.append(Strings.repeat("[]", that.dimension));
    return b;
  }

  @Override
  public CharSequence visitExpressionStatement(Statement.ExpressionStatement<TRef> that) {
    CharSequence expr = that.expression.acceptVisitor(this);
    if (that.expression instanceof Expression.MethodCallExpression) {
      // TODO: MethodCall isn't really an expression, is it? I'd say it's a statement.
      return new StringBuilder(expr).append(";");
    } else {
      return new StringBuilder(outerParanthesesRemoved(expr)).append(";");
    }
  }

  @Override
  public CharSequence visitEmptyStatement(Statement.EmptyStatement<TRef> that) {
    return ";";
  }

  @Override
  public CharSequence visitReturn(Statement.Return<TRef> that) {
    StringBuilder b = new StringBuilder("return");
    if (that.expression.isPresent()) {
      b.append(" ");
      CharSequence expr = that.expression.get().acceptVisitor(this);
      b.append(outerParanthesesRemoved(expr));
    }
    return b.append(";");
  }

  @Override
  public CharSequence visitVariable(BlockStatement.Variable<TRef> that) {
    StringBuilder b = new StringBuilder(that.type.acceptVisitor(this));
    b.append(" ");
    b.append(that.name);
    if (that.rhs != null) {
      b.append(" = ");
      b.append(outerParanthesesRemoved(that.rhs.acceptVisitor(this)));
    }
    return b.append(";");
  }

  @Override
  public CharSequence visitBinaryOperator(Expression.BinaryOperatorExpression<TRef> that) {
    StringBuilder b = new StringBuilder("(");
    CharSequence left = that.left.acceptVisitor(this);
    b.append(left);
    b.append(" ");
    b.append(that.op.string);
    b.append(" ");
    CharSequence right = that.right.acceptVisitor(this);
    b.append(right);

    return b.append(")");
  }

  @Override
  public CharSequence visitUnaryOperator(Expression.UnaryOperatorExpression<TRef> that) {
    StringBuilder b = new StringBuilder("(");
    b.append(that.op.string);
    b.append(that.expression.acceptVisitor(this));
    b.append(")");
    return b;
  }

  @Override
  public CharSequence visitMethodCall(Expression.MethodCallExpression<TRef> that) {
    StringBuilder b = new StringBuilder(that.self.acceptVisitor(this));
    b.append(".");
    b.append(that.method.toString());
    b.append("(");
    Iterator<Expression<TRef>> iterator = that.arguments.iterator();
    Expression<TRef> next;
    if (iterator.hasNext()) {
      next = iterator.next();
      b.append(outerParanthesesRemoved(next.acceptVisitor(this)));
      while (iterator.hasNext()) {
        next = iterator.next();
        b.append(", ");
        b.append(outerParanthesesRemoved(next.acceptVisitor(this)));
      }
    }
    b.append(")");
    return b;
  }

  @Override
  public CharSequence visitFieldAccess(Expression.FieldAccessExpression<TRef> that) {
    StringBuilder b = new StringBuilder("(");
    b.append(that.self.acceptVisitor(this));
    b.append(".");
    b.append(that.field.toString());
    b.append(")");
    return b;
  }

  @Override
  public CharSequence visitArrayAccess(Expression.ArrayAccessExpression<TRef> that) {
    StringBuilder b = new StringBuilder("(").append(that.array.acceptVisitor(this)).append("[");
    CharSequence indexExpr = that.index.acceptVisitor(this);
    b.append(outerParanthesesRemoved(indexExpr));
    b.append("])");
    return b;
  }

  @Override
  public CharSequence visitNewObjectExpr(Expression.NewObjectExpression<TRef> that) {
    StringBuilder b = new StringBuilder("(new ");
    b.append(that.type.toString());
    b.append("())");
    return b;
  }

  @Override
  public CharSequence visitNewArrayExpr(Expression.NewArrayExpression<TRef> that) {
    StringBuilder b = new StringBuilder("(new ").append(that.type.typeRef.toString()).append("[");
    // bracketing exception for definition of array size applies here
    CharSequence sizeExpr = outerParanthesesRemoved(that.size.acceptVisitor(this));
    return b.append(sizeExpr)
        .append("]")
        .append(Strings.repeat("[]", that.type.dimension - 1))
        .append(")");
  }

  @Override
  public CharSequence visitVariable(Expression.VariableExpression<TRef> that) {
    return that.var.toString();
  }

  @Override
  public CharSequence visitBooleanLiteral(Expression.BooleanLiteralExpression<TRef> that) {
    return Boolean.toString(that.literal);
  }

  @Override
  public CharSequence visitIntegerLiteral(Expression.IntegerLiteralExpression<TRef> that) {
    return that.literal;
  }
}
