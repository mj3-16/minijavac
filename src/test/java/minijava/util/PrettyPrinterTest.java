package minijava.util;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import com.google.common.collect.ImmutableList;
import minijava.ast.*;
import minijava.ast.Class;
import minijava.ast.Expression.*;
import minijava.ast.LocalVariable;
import minijava.ast.Statement.*;
import minijava.lexer.Lexer;
import minijava.parser.Parser;
import org.junit.Before;
import org.junit.Test;

public class PrettyPrinterTest {

  private static final SourceRange r = SourceRange.FIRST_CHAR;
  private PrettyPrinter prettyPrinter;

  @Before
  public void setup() {
    prettyPrinter = new PrettyPrinter();
  }

  @Test
  public void visitProgramWithThreeEmptyClasses_SortedAlphabetically() throws Exception {
    Program p =
        new Program(
            ImmutableList.of(
                new Class("A", ImmutableList.of(), ImmutableList.of(), r),
                new Class("Z", ImmutableList.of(), ImmutableList.of(), r),
                new Class("C", ImmutableList.of(), ImmutableList.of(), r)),
            r);
    CharSequence actual = p.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo(format("class A { }%nclass C { }%nclass Z { }%n"))));
  }

  @Test
  public void visitClassWithOneField() throws Exception {
    Class node =
        new Class(
            "Foo",
            ImmutableList.of(new Field(new Type(new Ref<>("int"), 0, r), "i", r)),
            ImmutableList.of(),
            r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo(format("class Foo {%n\tpublic int i;%n}%n"))));
  }

  @Test
  public void visitClassWithMultipleFields_fieldsAreOrderedAlphabetically() throws Exception {
    Class node =
        new Class(
            "Foo",
            ImmutableList.of(
                new Field(new Type(new Ref<>("boolean"), 0, r), "G", r),
                new Field(new Type(new Ref<>("int"), 0, r), "U", r),
                new Field(new Type(new Ref<>("int"), 0, r), "A", r),
                new Field(new Type(new Ref<>("int"), 0, r), "Z", r),
                new Field(new Type(new Ref<>("boolean"), 0, r), "B", r)),
            ImmutableList.of(),
            r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(
        actual.toString(),
        is(
            equalTo(
                format(
                    "class Foo {%n"
                        + "\tpublic int A;%n"
                        + "\tpublic boolean B;%n"
                        + "\tpublic boolean G;%n"
                        + "\tpublic int U;%n"
                        + "\tpublic int Z;%n"
                        + "}%n"))));
  }

  @Test
  public void visitClassWithOneEmptyMethod() throws Exception {
    Class node =
        new Class(
            "Foo",
            ImmutableList.of(),
            ImmutableList.of(
                new Method(
                    true,
                    new Type(new Ref<>("int"), 0, r),
                    "m",
                    ImmutableList.of(),
                    new Block(ImmutableList.of(), r),
                    r)),
            r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(
        actual.toString(), is(equalTo(format("class Foo {%n\tpublic static int m() { }%n}%n"))));
  }

  @Test
  public void visitClassWithMultipleMethods_methodsAreOrderedAlphabetically() throws Exception {
    Class node =
        new Class(
            "Foo",
            ImmutableList.of(),
            ImmutableList.of(
                new Method(
                    false,
                    new Type(new Ref<>("int"), 0, r),
                    "a",
                    ImmutableList.of(),
                    new Block(ImmutableList.of(), r),
                    r),
                new Method(
                    false,
                    new Type(new Ref<>("int"), 0, r),
                    "Z",
                    ImmutableList.of(),
                    new Block(ImmutableList.of(), r),
                    r),
                new Method(
                    false,
                    new Type(new Ref<>("int"), 0, r),
                    "B",
                    ImmutableList.of(),
                    new Block(ImmutableList.of(), r),
                    r)),
            r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(
        actual.toString(),
        is(
            equalTo(
                format(
                    "class Foo {%n"
                        + "\tpublic int B() { }%n"
                        + "\tpublic int Z() { }%n"
                        + "\tpublic int a() { }%n"
                        + "}%n"))));
  }

  @Test
  public void visitMethodWithParametersAndBodyWithEmptyStatements() throws Exception {
    Method node =
        new Method(
            true,
            new Type(new Ref<>("void"), 0, r),
            "main",
            ImmutableList.of(
                new LocalVariable(new Type(new Ref<>("String"), 1, r), "args", r),
                new LocalVariable(new Type(new Ref<>("int"), 0, r), "numArgs", r)),
            new Block(ImmutableList.of(new Empty(r), new Empty(r), new Empty(r), new Empty(r)), r),
            r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(
        actual.toString(),
        is(equalTo(format("public static void main(String[] args, int numArgs) { }"))));
  }

  @Test
  public void visitMethodWithNonEmptyBody() throws Exception {
    Method node =
        new Method(
            false,
            new Type(new Ref<>("int"), 0, r),
            "m",
            ImmutableList.of(),
            new Block(
                ImmutableList.of(
                    new ExpressionStatement(new IntegerLiteral("0", r) {}, r), new Empty(r)),
                r),
            r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo(format("public int m() {%n\t0;%n}"))));
  }

  @Test
  public void visitBlockWithMultipleStatements() throws Exception {
    Block node =
        new Block(
            ImmutableList.of(
                new BlockStatement.Variable(new Type(new Ref<>("int"), 0, r), "i", null, r),
                new BlockStatement.Variable(new Type(new Ref<>("String"), 2, r), "s", null, r),
                new BlockStatement.Variable(new Type(new Ref<>("boolean"), 0, r), "b", null, r)),
            r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(
        actual.toString(), is(equalTo(format("{%n\tint i;%n\tString[][] s;%n\tboolean b;%n}"))));
  }

  @Test
  public void visitIfWithSingleThenStatement() throws Exception {
    If node = new If(new BooleanLiteral(true, r), new Empty(r), null, r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo(format("if (true)%n\t;"))));
  }

  @Test
  public void visitIfWithMultipleThenStatements() throws Exception {
    If node =
        new If(
            new BooleanLiteral(true, r),
            new Block(ImmutableList.of(new Empty(r), new Empty(r)), r),
            null,
            r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("if (true) { }")));
  }

  @Test
  public void visitLocalVariableDeclaration_WithoutInit() throws Exception {
    BlockStatement.Variable node =
        new BlockStatement.Variable(new Type(new Ref<>("int"), 0, r), "i", null, r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("int i;")));
  }

  @Test
  public void visitLocalVariableDeclaration_WithInit() throws Exception {
    BlockStatement.Variable node =
        new BlockStatement.Variable(
            new Type(new Ref<>("int"), 0, r),
            "i",
            new BinaryOperator(
                BinOp.PLUS, new IntegerLiteral("4", r), new IntegerLiteral("6", r), r),
            r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("int i = 4 + 6;")));
  }

  @Test
  public void visitMethodCall_NoParameters() throws Exception {
    MethodCall node =
        new MethodCall(new Variable(new Ref<>("o"), r), new Ref<>("m"), ImmutableList.of(), r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("(o.m())")));
  }

  @Test
  public void visitMethodCall_OneParameter() throws Exception {
    MethodCall node =
        new MethodCall(
            new Variable(new Ref<>("o"), r),
            new Ref<>("m"),
            ImmutableList.of(new BooleanLiteral(true, r)),
            r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("(o.m(true))")));
  }

  @Test
  public void visitMethodCall_MultipleParameters() throws Exception {
    MethodCall node =
        new MethodCall(
            new Variable(new Ref<>("o"), r),
            new Ref<>("m"),
            ImmutableList.of(
                new BooleanLiteral(true, r),
                new BinaryOperator(
                    BinOp.PLUS, new IntegerLiteral("5", r), new IntegerLiteral("8", r), r)),
            r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("(o.m(true, 5 + 8))")));
  }

  @Test
  public void visitFieldAccess() throws Exception {
    FieldAccess node =
        new FieldAccess(new Variable(new Ref<>("myObject"), r), new Ref<>("field"), r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("(myObject.field)")));
  }

  @Test
  public void visitArrayAccess() throws Exception {
    ArrayAccess node =
        new ArrayAccess(new Variable(new Ref<>("array"), r), new IntegerLiteral("5", r), r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("(array[5])")));
  }

  @Test
  public void visitArrayAccess_IndexIsCompositeExpression() throws Exception {
    ArrayAccess node =
        new ArrayAccess(
            new Variable(new Ref<>("array"), r),
            new UnaryOperator(
                UnOp.NEGATE,
                new BinaryOperator(
                    BinOp.MINUS,
                    new IntegerLiteral("15", r),
                    new UnaryOperator(UnOp.NEGATE, new IntegerLiteral("7", r), r),
                    r),
                r),
            r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("(array[-(15 - (-7))])")));
  }

  @Test
  public void visitNewObjectExpression() throws Exception {
    NewObject node = new NewObject(new Ref<>("MyClass"), r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("(new MyClass())")));
  }

  @Test
  public void visitNewArrayExpression() throws Exception {
    NewArray node =
        new NewArray(new Type(new Ref<>("boolean"), 3, r), new IntegerLiteral("25", r), r);

    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("(new boolean[25][][][])")));
  }

  @Test
  public void visitNewArrayExpression_SizeExpressionIsACompositeExpression() throws Exception {
    NewArray node =
        new NewArray(
            new Type(new Ref<>("boolean"), 3, r),
            new BinaryOperator(
                BinOp.PLUS,
                new BinaryOperator(
                    BinOp.MINUS, new IntegerLiteral("25", r), new IntegerLiteral("5", r), r),
                new UnaryOperator(UnOp.NEGATE, new IntegerLiteral("19", r), r),
                r),
            r);

    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("(new boolean[(25 - 5) + (-19)][][][])")));
  }

  @Test
  public void visitWhile() throws Exception {
    While node =
        new While(
            new BinaryOperator(
                BinOp.PLUS, new IntegerLiteral("6", r), new IntegerLiteral("2", r), r),
            new ExpressionStatement(
                new UnaryOperator(UnOp.NEGATE, new IntegerLiteral("5", r), r), r),
            r);

    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo(format("while (6 + 2)%n\t-5;"))));
  }

  @Test
  public void visitWhile_null() throws Exception {
    While node = new While(new Variable(new Ref<>("null"), r), new Empty(r), r);
    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo(format("while (null)%n\t;"))));
  }

  @Test
  public void visitWhile_EmptyBlock() throws Exception {
    While node =
        new While(
            new BinaryOperator(
                BinOp.PLUS, new IntegerLiteral("6", r), new IntegerLiteral("2", r), r),
            new Block(ImmutableList.of(new Empty(r), new Empty(r)), r),
            r);

    CharSequence actual = node.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo("while (6 + 2) { }")));
  }

  @Test
  public void sampleProgram() throws Exception {
    String input =
        "class HelloWorld\n"
            + "{\n"
            + "\tpublic int c;\n"
            + "\tpublic boolean[] array;\n"
            + "\tpublic static /* blabla */ void main(String[] args)\n"
            + "\t{ System.out.println( (43110 + 0) );\n"
            + "\tboolean b = true && (!false);\n"
            + "\tif (23+19 == (42+0)*1)\n"
            + "\t\tb = (0 < 1);\n"
            + "\t\telse if (!array[2+2]) {\n"
            + "\t\t\tint x = 0;;\n"
            + "\t\t\tx = x+1;\n"
            + "\t\t} else {\n"
            + "\t\t\tnew HelloWorld().bar(42+0*1, -1);\n"
            + "\t\t}\n"
            + "\t}\n"
            + "\tpublic int bar(int a, int b) { return c = (a+b); }\n"
            + "}";

    String expected =
        "class HelloWorld {%n"
            + "\tpublic int bar(int a, int b) {%n"
            + "\t\treturn c = (a + b);%n"
            + "\t}%n"
            + "\tpublic static void main(String[] args) {%n"
            + "\t\t(System.out).println(43110 + 0);%n"
            + "\t\tboolean b = true && (!false);%n"
            + "\t\tif ((23 + 19) == ((42 + 0) * 1))%n"
            + "\t\t\tb = (0 < 1);%n"
            + "\t\telse if (!(array[2 + 2])) {%n"
            + "\t\t\tint x = 0;%n"
            + "\t\t\tx = (x + 1);%n"
            + "\t\t} else {%n"
            + "\t\t\t(new HelloWorld()).bar(42 + (0 * 1), -1);%n"
            + "\t\t}%n"
            + "\t}%n"
            + "\tpublic boolean[] array;%n"
            + "\tpublic int c;%n"
            + "}%n";

    Program program = new Parser(new Lexer(input)).parse();
    CharSequence actual = program.acceptVisitor(prettyPrinter);
    assertThat(actual.toString(), is(equalTo(format(expected))));
  }
}
