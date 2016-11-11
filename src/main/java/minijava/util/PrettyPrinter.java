package minijava.util;

import com.google.common.base.Strings;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import minijava.ast.*;
import minijava.ast.Class;

public class PrettyPrinter<TRef>
    implements Program.Visitor<TRef, Void>,
        Class.Visitor<TRef, Void>,
        Field.Visitor<TRef, Void>,
        Method.Visitor<TRef, Void>,
        Type.Visitor<TRef, Void>,
        BlockStatement.Visitor<TRef, Void>,
        Expression.Visitor<TRef, Void> {

  private final PrintWriter out;
  private int indentLevel = 0;

  public PrettyPrinter(Writer out) {
    this.out = new PrintWriter(out);
  }

  private void indent() {
    out.print(Strings.repeat("\t", indentLevel));
  }

  @Override
  public Void visitProgram(Program<TRef> that) {
    for (Class<TRef> classDecl : that.declarations) {
      classDecl.acceptVisitor(this);
    }
    return null;
  }

  @Override
  public Void visitClassDeclaration(Class<TRef> that) {
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
  public Void visitField(Field<TRef> that) {
    out.print("public ");
    that.type.acceptVisitor(this);
    out.print(" ");
    out.print(that.name);
    out.println(";");
    return null;
  }

  @Override
  public Void visitType(Type<TRef> that) {
    out.print(that.typeRef.toString());
    out.print(Strings.repeat("[]", that.dimension));
    return null;
  }

  @Override
  public Void visitMethod(Method<TRef> that) {
    out.print("public ");
    if (that.isStatic) {
      out.print("static ");
    }
    that.returnType.acceptVisitor(this);
    out.print(" ");
    out.print(that.name);
    out.print("(");
    Iterator<Method.Parameter<TRef>> iterator = that.parameters.iterator();
    Method.Parameter<TRef> next;
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
  public Void visitBlock(Block<TRef> that) {
    List<BlockStatement<TRef>> nonEmptyStatements =
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
  public Void visitEmptyStatement(Statement.EmptyStatement<TRef> that) {
    indent();
    out.println(";");
    return null;
  }

  @Override
  public Void visitIf(Statement.If<TRef> that) {
    out.print("if ");
    that.condition.acceptVisitor(this);
    out.print(" ");
    if (!(that.then instanceof Block)) {
      out.println();
      indent();
      out.print("\t"); // indent one more than current if statement
    }
    that.then.acceptVisitor(this);

    if (that.else_.isPresent()) {
      if (that.then instanceof Block) {
        out.print(" ");
      } else {
        indent();
      }
      out.print("else ");
      that.else_.get().acceptVisitor(this);
    } else {
      out.println();
    }
    return null;
  }

  @Override
  public Void visitExpressionStatement(Statement.ExpressionStatement<TRef> that) {
    that.expression.acceptVisitor(this);
    out.println(";");
    return null;
  }

  @Override
  public Void visitWhile(Statement.While<TRef> that) {
    return null;
  }

  @Override
  public Void visitReturn(Statement.Return<TRef> that) {
    out.print("return");
    if (that.expression.isPresent()) {
      out.print(" ");
      that.expression.get().acceptVisitor(this);
    }
    out.println(";");
    return null;
  }

  @Override
  public Void visitVariable(BlockStatement.Variable<TRef> that) {
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
  public Void visitBinaryOperator(Expression.BinaryOperatorExpression<TRef> that) {
    out.print("(");
    that.left.acceptVisitor(this);
    // TODO: store strings in op and use it here
    out.print(" ");
    out.print(that.op.toString());
    out.print(" ");
    that.right.acceptVisitor(this);
    out.print(")");
    return null;
  }

  @Override
  public Void visitUnaryOperator(Expression.UnaryOperatorExpression<TRef> that) {
    out.print("(");
    out.print(that.op.toString());
    that.expression.acceptVisitor(this);
    out.print(")");
    return null;
  }

  @Override
  public Void visitMethodCall(Expression.MethodCallExpression<TRef> that) {
    that.self.acceptVisitor(this);
    out.print(".");
    out.print(that.method.toString());
    out.print("(");
    Iterator<Expression<TRef>> iterator = that.arguments.iterator();
    Expression<TRef> next;
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
  public Void visitFieldAccess(Expression.FieldAccessExpression<TRef> that) {
    that.self.acceptVisitor(this);
    out.print(".");
    out.print(that.field.toString());
    return null;
  }

  @Override
  public Void visitArrayAccess(Expression.ArrayAccessExpression<TRef> that) {
    that.array.acceptVisitor(this);
    out.print("[");
    that.index.acceptVisitor(this);
    out.print("]");
    return null;
  }

  @Override
  public Void visitNewObjectExpr(Expression.NewObjectExpression<TRef> that) {
    out.print("new ");
    out.print(that.type.toString());
    out.print("()");
    return null;
  }

  @Override
  public Void visitNewArrayExpr(Expression.NewArrayExpression<TRef> that) {
    out.print("new ");
    out.print(that.type.typeRef.toString());
    out.print("[");
    that.size.acceptVisitor(this);
    out.print("]");
    out.print(Strings.repeat(that.type.typeRef.toString(), that.type.dimension - 1));
    return null;
  }

  @Override
  public Void visitVariable(Expression.VariableExpression<TRef> that) {
    out.print(that.var);
    return null;
  }

  @Override
  public Void visitBooleanLiteral(Expression.BooleanLiteralExpression<TRef> that) {
    out.print(that.literal);
    return null;
  }

  @Override
  public Void visitIntegerLiteral(Expression.IntegerLiteralExpression<TRef> that) {
    out.print(that.literal);
    return null;
  }
}
