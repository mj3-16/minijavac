package minijava.utils;

import com.google.common.base.Strings;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import minijava.ast.*;
import minijava.ast.Class;

/**
 * An implementation of an AST visitor that pretty-prints the AST back to source code.
 *
 * <p>Instances of this class <em>are</em> stateful (e.g., current indentation level). It is very
 * cheap to create new instances of this class and therefore it is generally not advisable to reuse
 * instances.
 */
public class PrettyPrinter
    implements Program.Visitor<CharSequence>,
        Class.Visitor<CharSequence>,
        Field.Visitor<CharSequence>,
        Method.Visitor<CharSequence>,
        Type.Visitor<CharSequence>,
        BlockStatement.Visitor<CharSequence>,
        Expression.Visitor<CharSequence> {

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
  public CharSequence visitProgram(Program that) {
    return that.declarations
        .stream()
        .sorted((left, right) -> left.name().compareTo(right.name()))
        .map(c -> c.acceptVisitor(this))
        .collect(Collectors.joining());
  }

  @Override
  public CharSequence visitClass(Class that) {
    StringBuilder sb = new StringBuilder("class ").append(that.name()).append(" {");
    if (that.fields.isEmpty() && that.methods.isEmpty()) {
      return sb.append(" }").append(System.lineSeparator());
    }
    sb.append(System.lineSeparator());
    indentLevel++;
    that.methods
        .stream()
        .sorted((left, right) -> left.name().compareTo(right.name()))
        .map(m -> m.acceptVisitor(this))
        .forEach(s -> sb.append(indent()).append(s).append(System.lineSeparator()));
    that.fields
        .stream()
        .sorted((left, right) -> left.name().compareTo(right.name()))
        .map(m -> m.acceptVisitor(this))
        .forEach(s -> sb.append(indent()).append(s).append(System.lineSeparator()));
    indentLevel--;
    return sb.append("}").append(System.lineSeparator());
  }

  @Override
  public CharSequence visitMethod(Method that) {
    StringBuilder sb = new StringBuilder("public ");
    if (that.isStatic) {
      sb.append("static ");
    }
    sb.append(that.returnType.acceptVisitor(this)).append(" ").append(that.name()).append("(");
    Iterator<? extends LocalVariable> iterator = that.parameters.iterator();
    LocalVariable next;
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
  public CharSequence visitBlock(Block that) {
    StringBuilder sb = new StringBuilder("{");
    List<BlockStatement> nonEmptyStatements =
        that.statements
            .stream()
            .filter(s -> !(s instanceof Statement.Empty))
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
  public CharSequence visitIf(Statement.If that) {
    StringBuilder b = new StringBuilder().append("if (");
    // bracketing exception for condition in if statement applies here
    CharSequence condition = that.condition.acceptVisitor(this);
    b.append(outerParanthesesRemoved(condition)).append(")");
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
    Statement else_ = that.else_.get();
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
  public CharSequence visitWhile(Statement.While that) {
    StringBuilder sb = new StringBuilder("while (");
    // bracketing exception for condition in while statement applies here
    CharSequence condition = that.condition.acceptVisitor(this);
    sb.append(outerParanthesesRemoved(condition)).append(")");
    if (that.body instanceof Block) {
      return sb.append(" ").append(that.body.acceptVisitor(this));
    }
    indentLevel++;
    sb.append(System.lineSeparator()).append(indent());
    indentLevel--;
    return sb.append(that.body.acceptVisitor(this));
  }

  @Override
  public CharSequence visitField(Field that) {
    return new StringBuilder("public ")
        .append(that.type.acceptVisitor(this))
        .append(" ")
        .append(that.name())
        .append(";");
  }

  @Override
  public CharSequence visitType(Type that) {
    StringBuilder b = new StringBuilder(that.basicType.name());
    b.append(Strings.repeat("[]", that.dimension));
    return b;
  }

  @Override
  public CharSequence visitExpressionStatement(Statement.ExpressionStatement that) {
    CharSequence expr = that.expression.acceptVisitor(this);
    return new StringBuilder(outerParanthesesRemoved(expr)).append(";");
  }

  @Override
  public CharSequence visitEmpty(Statement.Empty that) {
    return ";";
  }

  @Override
  public CharSequence visitReturn(Statement.Return that) {
    StringBuilder b = new StringBuilder("return");
    if (that.expression.isPresent()) {
      b.append(" ");
      CharSequence expr = that.expression.get().acceptVisitor(this);
      b.append(outerParanthesesRemoved(expr));
    }
    return b.append(";");
  }

  @Override
  public CharSequence visitVariable(BlockStatement.Variable that) {
    StringBuilder b = new StringBuilder(that.type.acceptVisitor(this));
    b.append(" ");
    b.append(that.name());
    if (that.rhs.isPresent()) {
      b.append(" = ");
      b.append(outerParanthesesRemoved(that.rhs.get().acceptVisitor(this)));
    }
    return b.append(";");
  }

  @Override
  public CharSequence visitBinaryOperator(Expression.BinaryOperator that) {
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
  public CharSequence visitUnaryOperator(Expression.UnaryOperator that) {
    StringBuilder b = new StringBuilder("(");
    b.append(that.op.string);
    b.append(that.expression.acceptVisitor(this));
    b.append(")");
    return b;
  }

  @Override
  public CharSequence visitMethodCall(Expression.MethodCall that) {
    StringBuilder b = new StringBuilder();
    b.append("(");
    b.append(that.self.acceptVisitor(this));
    b.append(".");
    b.append(that.method.name());
    b.append("(");
    Iterator<? extends Expression> iterator = that.arguments.iterator();
    Expression next;
    if (iterator.hasNext()) {
      next = iterator.next();
      b.append(outerParanthesesRemoved(next.acceptVisitor(this)));
      while (iterator.hasNext()) {
        next = iterator.next();
        b.append(", ");
        b.append(outerParanthesesRemoved(next.acceptVisitor(this)));
      }
    }
    b.append("))");
    return b;
  }

  @Override
  public CharSequence visitFieldAccess(Expression.FieldAccess that) {
    StringBuilder b = new StringBuilder("(");
    b.append(that.self.acceptVisitor(this));
    b.append(".");
    b.append(that.field.name());
    b.append(")");
    return b;
  }

  @Override
  public CharSequence visitArrayAccess(Expression.ArrayAccess that) {
    StringBuilder b = new StringBuilder("(").append(that.array.acceptVisitor(this)).append("[");
    CharSequence indexExpr = that.index.acceptVisitor(this);
    b.append(outerParanthesesRemoved(indexExpr));
    b.append("])");
    return b;
  }

  @Override
  public CharSequence visitNewObject(Expression.NewObject that) {
    StringBuilder b = new StringBuilder("(new ");
    b.append(that.class_.name());
    b.append("())");
    return b;
  }

  @Override
  public CharSequence visitNewArray(Expression.NewArray that) {
    StringBuilder b =
        new StringBuilder("(new ").append(that.elementType.basicType.name()).append("[");
    // bracketing exception for definition of array size applies here
    CharSequence sizeExpr = outerParanthesesRemoved(that.size.acceptVisitor(this));
    return b.append(sizeExpr)
        .append("]")
        .append(Strings.repeat("[]", that.elementType.dimension))
        .append(")");
  }

  @Override
  public CharSequence visitVariable(Expression.Variable that) {
    return that.var.name();
  }

  @Override
  public CharSequence visitBooleanLiteral(Expression.BooleanLiteral that) {
    return Boolean.toString(that.literal);
  }

  @Override
  public CharSequence visitIntegerLiteral(Expression.IntegerLiteral that) {
    return that.literal;
  }

  @Override
  public CharSequence visitReferenceTypeLiteral(Expression.ReferenceTypeLiteral that) {
    return that.name();
  }
}
