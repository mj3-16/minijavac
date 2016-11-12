package minijava.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import com.google.common.collect.ImmutableList;
import minijava.ast.*;
import minijava.ast.BlockStatement.Variable;
import minijava.ast.Class;
import minijava.ast.Expression.*;
import minijava.ast.Method.Parameter;
import minijava.ast.Statement.*;
import org.junit.Before;
import org.junit.Test;

public class PrettyPrinterTest {

  private PrettyPrinter prettyPrinter;

  @Before
  public void setup() {
    prettyPrinter = new PrettyPrinter();
  }

  @Test
  public void visitProgramWithThreeEmptyClasses_SortedAlphabetically() throws Exception {
    Program<Object> p =
        new Program<>(
            ImmutableList.of(
                new Class<>("A", ImmutableList.of(), ImmutableList.of()),
                new Class<>("Z", ImmutableList.of(), ImmutableList.of()),
                new Class<>("C", ImmutableList.of(), ImmutableList.of())));
    CharSequence actual = p.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("class A { }\nclass C { }\nclass Z { }\n")));
  }

  @Test
  public void visitClassWithOneField() throws Exception {
    Class<Object> node =
        new Class<>(
            "Foo", ImmutableList.of(new Field<>(new Type<>("int", 0), "i")), ImmutableList.of());
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("class Foo {\n\tpublic int i;\n}\n")));
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
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(
        actual.toString(),
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
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("class Foo {\n\tpublic static int m() { }\n}\n")));
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
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(
        actual.toString(),
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
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(
        actual.toString(), is(equalTo("public static void main(String[] args, int numArgs) { }")));
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
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("public int m() {\n\t0;\n}")));
  }

  @Test
  public void visitBlockWithMultipleStatements() throws Exception {
    Block<Object> node =
        new Block<>(
            ImmutableList.of(
                new Variable<>(new Type<>("int", 0), "i", null),
                new Variable<>(new Type<>("String", 2), "s", null),
                new Variable<>(new Type<>("boolean", 0), "b", null)));
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("{\n\tint i;\n\tString[][] s;\n\tboolean b;\n}")));
  }

  @Test
  public void visitIfWithSingleThenStatement() throws Exception {
    If<Object> node =
        new If<>(new Expression.BooleanLiteralExpression<>(true), new EmptyStatement<>(), null);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("if true\n\t;")));
  }

  @Test
  public void visitIfWithMultipleThenStatements() throws Exception {
    If<Object> node =
        new If<>(
            new Expression.BooleanLiteralExpression<>(true),
            new Block<>(ImmutableList.of(new EmptyStatement<>(), new EmptyStatement<>())),
            null);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("if true { }")));
  }

  @Test
  public void visitLocalVariableDeclaration_WithoutInit() throws Exception {
    Variable<Object> node = new Variable<>(new Type<>("int", 0), "i", null);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("int i;")));
  }

  @Test
  public void visitLocalVariableDeclaration_WithInit() throws Exception {
    Variable<Object> node =
        new Variable<>(
            new Type<>("int", 0),
            "i",
            new BinaryOperatorExpression<>(
                BinOp.PLUS,
                new Expression.IntegerLiteralExpression<>("4"),
                new Expression.IntegerLiteralExpression<>("6")));
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("int i = 4 + 6;")));
  }

  @Test
  public void visitMethodCall_NoParameters() throws Exception {
    MethodCallExpression<Object> node =
        new MethodCallExpression<>(new VariableExpression<>("o"), "m", ImmutableList.of());
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("o.m()")));
  }

  @Test
  public void visitMethodCall_OneParameter() throws Exception {
    MethodCallExpression<Object> node =
        new MethodCallExpression<>(
            new VariableExpression<>("o"),
            "m",
            ImmutableList.of(new BooleanLiteralExpression<>(true)));
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("o.m(true)")));
  }

  @Test
  public void visitMethodCall_MultipleParameters() throws Exception {
    MethodCallExpression<Object> node =
        new MethodCallExpression<>(
            new VariableExpression<>("o"),
            "m",
            ImmutableList.of(
                new BooleanLiteralExpression<>(true),
                new BinaryOperatorExpression<>(
                    BinOp.PLUS,
                    new IntegerLiteralExpression<>("5"),
                    new IntegerLiteralExpression<>("8"))));
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("o.m(true, 5 + 8)")));
  }

  @Test
  public void visitFieldAccess() throws Exception {
    FieldAccessExpression<Object> node =
        new FieldAccessExpression<>(new VariableExpression<>("myObject"), "field");
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("(myObject.field)")));
  }

  @Test
  public void visitArrayAccess() throws Exception {
    ArrayAccessExpression<Object> node =
        new ArrayAccessExpression<>(
            new VariableExpression<>("array"), new IntegerLiteralExpression<>("5"));
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("(array[5])")));
  }

  @Test
  public void visitArrayAccess_IndexIsCompositeExpression() throws Exception {
    ArrayAccessExpression<Object> node =
        new ArrayAccessExpression<>(
            new VariableExpression<>("array"),
            new UnaryOperatorExpression<>(
                UnOp.NEGATE,
                new BinaryOperatorExpression<>(
                    BinOp.MINUS,
                    new Expression.IntegerLiteralExpression<>("15"),
                    new UnaryOperatorExpression<>(
                        UnOp.NEGATE, new Expression.IntegerLiteralExpression<>("7")))));
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("(array[-(15 - (-7))])")));
  }

  @Test
  public void visitNewObjectExpression() throws Exception {
    NewObjectExpression<Object> node = new NewObjectExpression<>("MyClass");
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("(new MyClass())")));
  }

  @Test
  public void visitNewArrayExpression() throws Exception {
    NewArrayExpression<Object> node =
        new NewArrayExpression<>(new Type("boolean", 4), new IntegerLiteralExpression("25"));

    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("(new boolean[25][][][])")));
  }

  @Test
  public void visitNewArrayExpression_SizeExpressionIsACompositeExpression() throws Exception {
    NewArrayExpression<Object> node =
        new NewArrayExpression<>(
            new Type("boolean", 4),
            new BinaryOperatorExpression(
                BinOp.PLUS,
                new BinaryOperatorExpression(
                    BinOp.MINUS,
                    new IntegerLiteralExpression("25"),
                    new IntegerLiteralExpression("5")),
                new UnaryOperatorExpression(UnOp.NEGATE, new IntegerLiteralExpression("19"))));

    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("(new boolean[(25 - 5) + (-19)][][][])")));
  }

  @Test
  public void visitWhile() throws Exception {
    While<Object> node =
        new While<>(
            new BinaryOperatorExpression<>(
                BinOp.PLUS,
                new IntegerLiteralExpression<>("6"),
                new IntegerLiteralExpression<>("2")),
            new ExpressionStatement<>(
                new UnaryOperatorExpression<>(UnOp.NEGATE, new IntegerLiteralExpression<>("5"))));

    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("while (6 + 2)\n\t-5;")));
  }

  @Test
  public void visitWhile_EmptyBlock() throws Exception {
    While<Object> node =
        new While<>(
            new BinaryOperatorExpression<>(
                BinOp.PLUS,
                new IntegerLiteralExpression<>("6"),
                new IntegerLiteralExpression<>("2")),
            new Block<>(
                ImmutableList.of(
                    new Statement.EmptyStatement<>(), new Statement.EmptyStatement<>())));

    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("while (6 + 2) { }")));
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
                                                        new IntegerLiteralExpression<>("0")),
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

    CharSequence actual = program.acceptVisitor(prettyPrinter);

    String expected =
        "class HelloWorld {\n"
            + "\tpublic int bar(int a, int b) {\n"
            + "\t\treturn c = (a + b);\n"
            + "\t}\n"
            + "\tpublic static void main(String[] args) {\n"
            + "\t\t(System.out).println(43110 + 0);\n"
            + "\t\tboolean b = true && (!false);\n"
            + "\t\tif ((23 + 19) == ((42 + 0) * 1))\n"
            + "\t\t\tb = (0 < 1);\n"
            + "\t\telse if (!(array[2 + 2])) {\n"
            + "\t\t\tint x = 0;\n"
            + "\t\t\tx = (x + 1);\n"
            + "\t\t} else {\n"
            + "\t\t\t(new HelloWorld()).bar(42 + (0 * 1), -1);\n"
            + "\t\t}\n"
            + "\t}\n"
            + "\tpublic boolean[] array;\n"
            + "\tpublic int c;\n"
            + "}\n";

    assertThat(actual.toString(), is(equalTo(expected)));
  }
}
