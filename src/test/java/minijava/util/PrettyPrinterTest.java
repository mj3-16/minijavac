package minijava.util;

import com.google.common.collect.ImmutableList;
import minijava.ast.*;
import minijava.ast.Class;
import minijava.ast.Expression.IntegerLiteralExpression;
import minijava.ast.Method.Parameter;
import minijava.ast.Statement.EmptyStatement;
import minijava.ast.Statement.ExpressionStatement;
import org.junit.Before;
import org.junit.Test;

import java.io.StringWriter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class PrettyPrinterTest {

  private StringWriter out;
  private PrettyPrinter prettyPrinter;

  @Before
  public void setup() {
    out = new StringWriter();
    prettyPrinter = new PrettyPrinter(out);
  }

  @Test
  public void visitEmptyClass() throws Exception {
    Class<Object> node = new Class<>("Foo", ImmutableList.of(), ImmutableList.of());
    node.acceptVisitor(prettyPrinter);
    assertThat(out.toString(), is(equalTo("class Foo { }\n")));
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
                    new EmptyStatement<>(), new EmptyStatement<>(), new EmptyStatement<>(), new EmptyStatement<>())));
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
                    new ExpressionStatement<>(new IntegerLiteralExpression<Object>("0") {}), new EmptyStatement<>())));
    node.acceptVisitor(prettyPrinter);
    assertThat(out.toString(), is(equalTo("public int m() {\n\t0;\n}\n")));
  }
}
