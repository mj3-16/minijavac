package minijava.semantic;

import java.util.List;
import minijava.MJError;
import minijava.util.SourceRange;

public class SemanticError extends MJError {
  public final SourceRange range;

  SemanticError(SourceRange range, String message) {
    super(String.format("Semantic error at %s: %s", range, message));
    this.range = range;
  }

  SemanticError(SourceRange leftRange, SourceRange rightRange, String message) {
    super(String.format("Semantic error at %s: %s", leftRange, message));
    this.range = leftRange;
  }

  @Override
  public String getSourceReferencingMessage(List<String> sourceFile) {
    return getMessage()
        + System.lineSeparator()
        + System.lineSeparator()
        + range.annotateSourceFileExcerpt(sourceFile);
  }
}
