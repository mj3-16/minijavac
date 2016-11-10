package minijava.util;

import com.google.common.base.Strings;
import minijava.ast.*;
import minijava.ast.Class;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.Iterator;

public class PrettyPrinter
    implements Program.Visitor<Object, Void>,
        Class.Visitor<Object, Void>,
        Field.Visitor<Object, Void>,
        Method.Visitor<Object, Void>,
        Type.Visitor<Object, Void>,
        BlockStatement.Visitor<Object, Void>,Expression.Visitor<Object,Void> {

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
    if (that.body.statements.isEmpty()
        || // should we discard EmptyStatements when we create a block? otherwise 'instanceOf' seems to be the best solution.
        that.body.statements.stream().allMatch(s -> s instanceof Statement.EmptyStatement)) {
      out.println("{ }");
    } else {
      out.println("{");
      indentLevel++;
      that.body.acceptVisitor((Block.Visitor) this);
      indentLevel--;
      out.println("}");
    }
    return null;
  }

  @Override
  public Void visitBlock(Block<Object> that) {
    that.statements.stream().filter(s -> !(s instanceof Statement.EmptyStatement)).forEach(s -> {
      indent();
      s.acceptVisitor(this);
    });
    return null;
  }

  @Override
  public Void visitEmptyStatement(Statement.EmptyStatement<Object> that) {
    // omit empty statements
    return null;
  }

  @Override
  public Void visitIf(Statement.If<Object> that) {
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
    return null;
  }

  @Override
  public Void visitVariable(BlockStatement.Variable<Object> that) {
    return null;
  }

  @Override
  public Void visitBinaryOperator(Expression.BinaryOperatorExpression<Object> that) {
    return null;
  }

  @Override
  public Void visitUnaryOperator(Expression.UnaryOperatorExpression<Object> that) {
    return null;
  }

  @Override
  public Void visitMethodCall(Expression.MethodCallExpression<Object> that) {
    return null;
  }

  @Override
  public Void visitFieldAccess(Expression.FieldAccessExpression<Object> that) {
    return null;
  }

  @Override
  public Void visitArrayAccess(Expression.ArrayAccessExpression<Object> that) {
    return null;
  }

  @Override
  public Void visitNewObjectExpr(Expression.NewObjectExpression<Object> that) {
    return null;
  }

  @Override
  public Void visitNewArrayExpr(Expression.NewArrayExpression<Object> size) {
    return null;
  }

  @Override
  public Void visitVariable(Expression.VariableExpression<Object> that) {
    return null;
  }

  @Override
  public Void visitBooleanLiteral(Expression.BooleanLiteralExpression<Object> that) {
    return null;
  }

  @Override
  public Void visitIntegerLiteral(Expression.IntegerLiteralExpression<Object> that) {
    out.print(that.literal);
    return null;
  }
}
