package minijava.util;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SourceRangeTest {
  private final String sourceFile;
  private final SourceRange range;
  private final String expectedAnnotation;

  public SourceRangeTest(String sourceFile, SourceRange range, String expectedAnnotation) {
    this.sourceFile = sourceFile;
    this.range = range;
    this.expectedAnnotation = expectedAnnotation;
  }

  private static SourceRange sl(int beginLine, int beginColumn, int length) {
    return new SourceRange(new SourcePosition(0, beginLine, beginColumn), length);
  }

  private static SourceRange ml(int beginLine, int beginColumn, int endLine, int endColumn) {
    return new SourceRange(
        new SourcePosition(0, beginLine, beginColumn), new SourcePosition(0, endLine, endColumn));
  }

  private static String f(String s) {
    return String.format(s);
  }

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {"single line", sl(1, 2, 4), f("1| single line%n     ^^^^%n")},
          {"beyond eof", sl(2, 0, 1), f("1| beyond eof%n             ^%n")},
          {f("1%n2%n3%n4"), ml(2, 0, 3, 0), f("2|> 2%n3|> 3%n")},
          {f("1%n2%n3%n4"), ml(1, 0, 2, 0), f("1|> 1%n2|> 2%n")},
          {f("1%n2%n3%n4"), ml(4, 0, 5, 0), f("4|> 4%n")},
          {
            "\t \tsingle line with tabs",
            sl(1, 3, 4),
            f("1| \t \tsingle line with tabs%n   \t \t^^^^%n")
          },
        });
  }

  @Test
  public void annotateSourceFileExcerpt_correctAnnotations() {
    String[] lines = sourceFile.split("\\n");

    String actualAnnotation = range.annotateSourceFileExcerpt(Arrays.asList(lines));

    assertThat(actualAnnotation, is(equalTo(expectedAnnotation)));
  }
}
