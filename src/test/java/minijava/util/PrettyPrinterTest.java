package minijava.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import com.google.common.collect.ImmutableList;
import java.io.StringWriter;
import minijava.ast.*;
import minijava.ast.BlockStatement.Variable;
import minijava.ast.Class;
import minijava.ast.Expression.*;
import minijava.ast.Method.Parameter;
import minijava.ast.Statement.EmptyStatement;
import minijava.ast.Statement.ExpressionStatement;
import minijava.ast.Statement.If;
import minijava.ast.Statement.Return;
import org.junit.Before;
import org.junit.Test;

public class PrettyPrinterTest {

  private StringWriter out;
  private PrettyPrinter<Object> prettyPrinter;

  @Before
  public void setup() {
    out = new StringWriter();
    prettyPrinter = new PrettyPrinter<>(out);
  }

  @Test
  public void visitProgramWithTwoEmptyClasses() throws Exception {
    Program<Object> p =
        new Program<>(
            ImmutableList.of(
                new Class<>("A", ImmutableList.of(), ImmutableList.of()),
                new Class<>("B", ImmutableList.of(), ImmutableList.of())));
    p.acceptVisitor(prettyPrinter);
    assertThat(out.toString(), is(equalTo("class A { }\nclass B { }\n")));
  }

  @Test
  public void visitClassWithOneField() throws Exception {
    Class<Object> node =
        new Class<>(
            "Foo", ImmutableList.of(new Field<>(new Type<>("int", 0), "i")), ImmutableList.of());
    node.acceptVisitor(prettyPrinter);
    assertThat(out.toString(), is(equalTo("class Foo {\n\tpublic int i;\n}\n")));
  }

  @Test
  public void visitClassWithMultipleFields_fieldsAreOrderedAlphabetically() throws Exception {
    Class<Object> node =
        new Class<>(
            "Foo",
            ImmutableList.of(
                new Field<>(new Type<>("boolean", 0), "G"),
                new Field<>(new Type<>("int", 0), "U"),
                new Field<>(new Type<>("int", 0), "A"),
                new Field<>(new Type<>("int", 0), "Z"),
                new Field<>(new Type<>("boolean", 0), "B")),
            ImmutableList.of());
    node.acceptVisitor(prettyPrinter);
    assertThat(
        out.toString(),
        is(
            equalTo(
                "class Foo {\n"
                    + "\tpublic int A;\n"
                    + "\tpublic boolean B;\n"
                    + "\tpublic boolean G;\n"
                    + "\tpublic int U;\n"
                    + "\tpublic int Z;\n"
                    + "}\n")));
  }

  @Test
  public void visitClassWithOneEmptyMethod() throws Exception {
    Class<Object> node =
        new Class<>(
            "Foo",
            ImmutableList.of(),
            ImmutableList.of(
                new Method<>(
                    true,
                    new Type<>("int", 0),
                    "m",
                    ImmutableList.of(),
                    new Block<>(ImmutableList.of()))));
    node.acceptVisitor(prettyPrinter);
    assertThat(out.toString(), is(equalTo("class Foo {\n\tpublic static int m() { }\n}\n")));
  }

  @Test
  public void visitClassWithMultipleMethods_methodsAreOrderedAlphabetically() throws Exception {
    Class<Object> node =
        new Class<>(
            "Foo",
            ImmutableList.of(),
            ImmutableList.of(
                new Method<>(
                    false,
                    new Type<>("int", 0),
                    "a",
                    ImmutableList.of(),
                    new Block<>(ImmutableList.of())),
                new Method<>(
                    false,
                    new Type<>("int", 0),
                    "Z",
                    ImmutableList.of(),
                    new Block<>(ImmutableList.of())),
                new Method<>(
                    false,
                    new Type<>("int", 0),
                    "B",
                    ImmutableList.of(),
                    new Block<>(ImmutableList.of()))));
    node.acceptVisitor(prettyPrinter);
    assertThat(
        out.toString(),
        is(
            equalTo(
                "class Foo {\n"
                    + "\tpublic int B() { }\n"
                    + "\tpublic int Z() { }\n"
                    + "\tpublic int a() { }\n"
                    + "}\n")));
  }

  @Test
  public void visitMethodWithParametersAndBodyWithEmptyStatements() throws Exception {
    Method<Object> node =
        new Method<>(
            true,
            new Type<>("void", 0),
            "main",
            ImmutableList.of(
                new Parameter<>(new Type<>("String", 1), "args"),
                new Parameter<>(new Type<>("int", 0), "numArgs")),
            new Block<>(
                ImmutableList.of(
                    new EmptyStatement<>(),
                    new EmptyStatement<>(),
                    new EmptyStatement<>(),
                    new EmptyStatement<>())));
    node.acceptVisitor(prettyPrinter);
    assertThat(
        out.toString(), is(equalTo("public static void main(String[] args, int numArgs) { }\n")));
  }

  @Test
  public void visitMethodWithNonEmptyBody() throws Exception {
    Method<Object> node =
        new Method<>(
            false,
            new Type<>("int", 0),
            "m",
            ImmutableList.of(),
            new Block<>(
                ImmutableList.of(
                    new ExpressionStatement<>(new IntegerLiteralExpression<Object>("0") {}),
                    new EmptyStatement<>())));
    node.acceptVisitor(prettyPrinter);
    assertThat(out.toString(), is(equalTo("public int m() {\n\t0;\n}\n")));
  }

  @Test
  public void visitBlockWithMultipleStatements() throws Exception {
    Block<Object> node =
        new Block<>(
            ImmutableList.of(
                new Variable<>(new Type<>("int", 0), "i", null),
                new Variable<>(new Type<>("String", 2), "s", null),
                new Variable<>(new Type<>("boolean", 0), "b", null)));
    node.acceptVisitor(prettyPrinter);
    assertThat(out.toString(), is(equalTo("int i;\nString[][] s;\nboolean b;\n")));
  }

  @Test
  public void visitIfWithSingleThenStatement() throws Exception {
    If<Object> node =
        new If<>(new Expression.BooleanLiteralExpression<>(true), new EmptyStatement<>(), null);
    node.acceptVisitor(prettyPrinter);
    assertThat(out.toString(), is(equalTo("if true\n\t;\n")));
  }

  @Test
  public void visitIfWithMultipleThenStatements() throws Exception {
    If<Object> node =
        new If<>(
            new Expression.BooleanLiteralExpression<>(true),
            new Block<>(ImmutableList.of(new EmptyStatement<>(), new EmptyStatement<>())),
            null);
    node.acceptVisitor(prettyPrinter);
    assertThat(out.toString(), is(equalTo("if true { }")));
  }

  @Test
  public void sampleProgram() throws Exception {
    Program<Object> program =
        new Program<>(
            ImmutableList.of(
                new Class<>(
                    "HelloWorld",
                    ImmutableList.of(
                        new Field<>(new Type<>("int", 0), "c"),
                        new Field<>(new Type<>("boolean", 1), "array")),
                    ImmutableList.of(
                        new Method<>(
                            true,
                            new Type<>("void", 0),
                            "main",
                            ImmutableList.of(new Parameter<>(new Type<>("String", 1), "args")),
                            new Block<>(
                                ImmutableList.of(
                                    new ExpressionStatement<>(
                                        new MethodCallExpression<>(
                                            new FieldAccessExpression<>(
                                                new VariableExpression<>("System"), "out"),
                                            "println",
                                            ImmutableList.of(
                                                new BinaryOperatorExpression<>(
                                                    BinOp.PLUS,
                                                    new IntegerLiteralExpression("43110"),
                                                    new IntegerLiteralExpression<>("0"))))),
                                    new BlockStatement.Variable(
                                        new Type<>("boolean", 0),
                                        "b",
                                        new BinaryOperatorExpression(
                                            BinOp.AND,
                                            new Expression.BooleanLiteralExpression(true),
                                            new Expression.UnaryOperatorExpression(
                                                Expression.UnOp.NOT,
                                                new Expression.BooleanLiteralExpression(false)))),
                                    new If<>(
                                        new BinaryOperatorExpression(
                                            BinOp.EQ,
                                            new BinaryOperatorExpression(
                                                BinOp.PLUS,
                                                new IntegerLiteralExpression("23"),
                                                new IntegerLiteralExpression("19")),
                                            new BinaryOperatorExpression(
                                                BinOp.MULTIPLY,
                                                new BinaryOperatorExpression(
                                                    BinOp.PLUS,
                                                    new IntegerLiteralExpression("42"),
                                                    new IntegerLiteralExpression("0")),
                                                new IntegerLiteralExpression("1"))),
                                        new Statement.ExpressionStatement<>(
                                            new BinaryOperatorExpression(
                                                BinOp.ASSIGN,
                                                new VariableExpression<>("b"),
                                                new BinaryOperatorExpression(
                                                    BinOp.LT,
                                                    new IntegerLiteralExpression("0"),
                                                    new IntegerLiteralExpression("1")))),
                                        // else part of first if
                                        new If(
                                            new Expression.UnaryOperatorExpression(
                                                Expression.UnOp.NOT,
                                                new Expression.ArrayAccessExpression(
                                                    new VariableExpression("array"),
                                                    new BinaryOperatorExpression(
                                                        BinOp.PLUS,
                                                        new IntegerLiteralExpression("2"),
                                                        new IntegerLiteralExpression("2")))),
                                            new Block(
                                                ImmutableList.of(
                                                    new Variable<>(
                                                        new Type<Object>("int", 0),
                                                        "x",
                                                        new Expression.IntegerLiteralExpression<>(
                                                            "0")),
                                                    new Statement.ExpressionStatement<>(
                                                        new BinaryOperatorExpression<>(
                                                            BinOp.ASSIGN,
                                                            new VariableExpression<>("x"),
                                                            new BinaryOperatorExpression<>(
                                                                BinOp.PLUS,
                                                                new VariableExpression<>("x"),
                                                                new IntegerLiteralExpression<>(
                                                                    "1")))))),
                                            new Block(
                                                ImmutableList.of(
                                                    new Statement.ExpressionStatement<>(
                                                        new MethodCallExpression<>(
                                                            new Expression.NewObjectExpression<>(
                                                                "HelloWorld"),
                                                            "bar",
                                                            ImmutableList.of(
                                                                new BinaryOperatorExpression<>(
                                                                    BinOp.PLUS,
                                                                    new IntegerLiteralExpression<>(
                                                                        "42"),
                                                                    new BinaryOperatorExpression<>(
                                                                        BinOp.MULTIPLY,
                                                                        new IntegerLiteralExpression<>(
                                                                            "0"),
                                                                        new IntegerLiteralExpression<>(
                                                                            "1"))),
                                                                new UnaryOperatorExpression<>(
                                                                    UnOp.NEGATE,
                                                                    new IntegerLiteralExpression<>(
                                                                        "1")))))))))))),
                        new Method<>(
                            false,
                            new Type<>("int", 0),
                            "bar",
                            ImmutableList.of(
                                new Parameter<>(new Type<>("int", 0), "a"),
                                new Parameter<>(new Type<>("int", 0), "b")),
                            new Block<>(
                                ImmutableList.of(
                                    new Return<>(
                                        new BinaryOperatorExpression<>(
                                            BinOp.ASSIGN,
                                            new VariableExpression<>("c"),
                                            new BinaryOperatorExpression<String>(
                                                BinOp.PLUS,
                                                new VariableExpression<>("a"),
                                                new VariableExpression<>("b")))))))))));

    program.acceptVisitor(prettyPrinter);
    System.out.println(out.toString());
  }
}
