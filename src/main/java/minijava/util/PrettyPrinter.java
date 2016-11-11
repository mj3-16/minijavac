package minijava.util;

import com.google.common.base.Strings;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import minijava.ast.*;
import minijava.ast.Class;

public class PrettyPrinter
    implements Program.Visitor<Object, Void>,
        Class.Visitor<Object, Void>,
        Field.Visitor<Object, Void>,
        Method.Visitor<Object, Void>,
        Type.Visitor<Object, Void>,
        BlockStatement.Visitor<Object, Void>,
        Expression.Visitor<Object, Void> {

  private final PrintWriter out;
  private int indentLevel = 0;

  public PrettyPrinter(Writer out) {
    this.out = new PrintWriter(out);
  }

  private void indent() {
    out.print(Strings.repeat("\t", indentLevel));
  }

  @Override
  public Void visitProgram(Program<Object> that) {
    for (Class<Object> classDecl : that.declarations) {
      classDecl.acceptVisitor(this);
    }
    return null;
  }

  @Override
  public Void visitClassDeclaration(Class<Object> that) {
    out.print("class ");
    out.print(that.name);
    if (that.fields.isEmpty() && that.methods.isEmpty()) {
      out.println(" { }");
    } else {
      out.println(" {");
      indentLevel++;
      that.methods
          .stream()
          .sorted((left, right) -> left.name.compareTo(right.name))
          .forEach(
              m -> {
                indent();
                m.acceptVisitor(this);
              });
      that.fields
          .stream()
          .sorted((left, right) -> left.name.compareTo(right.name))
          .forEach(
              f -> {
                indent();
                f.acceptVisitor(this);
              });
      indentLevel--;
      out.println("}");
    }
    return null;
  }

  @Override
  public Void visitField(Field<Object> that) {
    out.print("public ");
    that.type.acceptVisitor(this);
    out.print(" ");
    out.print(that.name);
    out.println(";");
    return null;
  }

  @Override
  public Void visitType(Type<Object> that) {
    out.print(that.typeRef.toString());
    out.print(Strings.repeat("[]", that.dimension));
    return null;
  }

  @Override
  public Void visitMethod(Method<Object> that) {
    out.print("public ");
    if (that.isStatic) {
      out.print("static ");
    }
    that.returnType.acceptVisitor(this);
    out.print(" ");
    out.print(that.name);
    out.print("(");
    Iterator<Method.Parameter<Object>> iterator = that.parameters.iterator();
    Method.Parameter<Object> next;
    if (iterator.hasNext()) {
      next = iterator.next();
      next.type.acceptVisitor(this);
      out.print(" ");
      out.print(next.name);
      while (iterator.hasNext()) {
        next = iterator.next();
        out.print(", ");
        next.type.acceptVisitor(this);
        out.print(" ");
        out.print(next.name);
      }
    }
    out.print(") ");
    that.body.acceptVisitor(this);
    out.println();
    return null;
  }

  @Override
  public Void visitBlock(Block<Object> that) {
    List<BlockStatement<Object>> nonEmptyStatements =
        that.statements
            .stream()
            .filter(s -> !(s instanceof Statement.EmptyStatement))
            .collect(Collectors.toList());
    if (nonEmptyStatements.isEmpty()) {
      out.print("{ }");
    } else {
      out.println("{");
      indentLevel++;
      nonEmptyStatements.forEach(
          s -> {
            indent();
            s.acceptVisitor(this);
          });
      indentLevel--;
      indent();
      out.print("}");
    }
    return null;
  }

  @Override
  public Void visitEmptyStatement(Statement.EmptyStatement<Object> that) {
    indent();
    out.println(";");
    return null;
  }

  @Override
  public Void visitIf(Statement.If<Object> that) {
    out.print("if ");
    that.condition.acceptVisitor(this);
    out.print(" ");
    if (!(that.then instanceof Block)) {
      out.println();
      indent();
      out.print("\t"); // indent one more than current if statement
    }
    that.then.acceptVisitor(this);

    if (that.else_ != null) {
      if (that.then instanceof Block) {
        out.print(" ");
      } else {
        indent();
      }
      out.print("else ");
      that.else_.acceptVisitor(this);
    } else {
      out.println();
    }
    return null;
  }

  @Override
  public Void visitExpressionStatement(Statement.ExpressionStatement<Object> that) {
    that.expression.acceptVisitor(this);
    out.println(";");
    return null;
  }

  @Override
  public Void visitWhile(Statement.While<Object> that) {
    return null;
  }

  @Override
  public Void visitReturn(Statement.Return<Object> that) {
    out.print("return");
    if (that.expression.isPresent()) {
      out.print(" ");
      that.expression.get().acceptVisitor(this);
    }
    out.println(";");
    return null;
  }

  @Override
  public Void visitVariable(BlockStatement.Variable<Object> that) {
    that.type.acceptVisitor(this);
    out.print(" ");
    out.print(that.name);
    if (that.rhs != null) {
      out.print(" = ");
      that.rhs.acceptVisitor(this);
    }
    out.println(";");
    return null;
  }

  @Override
  public Void visitBinaryOperator(Expression.BinaryOperatorExpression<Object> that) {
    out.print("(");
    that.left.acceptVisitor(this);
    // TODO: store strings in op and use it here
    out.print(" ");
    out.print(that.op.string);
    out.print(" ");
    that.right.acceptVisitor(this);
    out.print(")");
    return null;
  }

  @Override
  public Void visitUnaryOperator(Expression.UnaryOperatorExpression<Object> that) {
    out.print("(");
    out.print(that.op.string);
    that.expression.acceptVisitor(this);
    out.print(")");
    return null;
  }

  @Override
  public Void visitMethodCall(Expression.MethodCallExpression<Object> that) {
    that.self.acceptVisitor(this);
    out.print(".");
    out.print(that.method.toString());
    out.print("(");
    Iterator<Expression<Object>> iterator = that.arguments.iterator();
    Expression<Object> next;
    if (iterator.hasNext()) {
      next = iterator.next();
      next.acceptVisitor(this);
      while (iterator.hasNext()) {
        next = iterator.next();
        out.print(", ");
        next.acceptVisitor(this);
      }
    }
    out.print(")");
    return null;
  }

  @Override
  public Void visitFieldAccess(Expression.FieldAccessExpression<Object> that) {
    that.self.acceptVisitor(this);
    out.print(".");
    out.print(that.field.toString());
    return null;
  }

  @Override
  public Void visitArrayAccess(Expression.ArrayAccessExpression<Object> that) {
    that.array.acceptVisitor(this);
    out.print("[");
    that.index.acceptVisitor(this);
    out.print("]");
    return null;
  }

  @Override
  public Void visitNewObjectExpr(Expression.NewObjectExpression<Object> that) {
    out.print("new ");
    out.print(that.type.toString());
    out.print("()");
    return null;
  }

  @Override
  public Void visitNewArrayExpr(Expression.NewArrayExpression<Object> that) {
    out.print("new ");
    out.print(that.type.typeRef.toString());
    out.print("[");
    that.size.acceptVisitor(this);
    out.print("]");
    out.print(Strings.repeat(that.type.typeRef.toString(), that.type.dimension - 1));
    return null;
  }

  @Override
  public Void visitVariable(Expression.VariableExpression<Object> that) {
    out.print(that.var);
    return null;
  }

  @Override
  public Void visitBooleanLiteral(Expression.BooleanLiteralExpression<Object> that) {
    out.print(that.literal);
    return null;
  }

  @Override
  public Void visitIntegerLiteral(Expression.IntegerLiteralExpression<Object> that) {
    out.print(that.literal);
    return null;
  }
}
