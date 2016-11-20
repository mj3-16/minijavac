package minijava.semantic;

import java.util.List;
import minijava.MJError;
import minijava.util.SourceRange;

public class SemanticError extends MJError {
  public final SourceRange range;
  public final SourceRange secondRange;

  SemanticError(SourceRange range, String message) {
    super(String.format("Semantic error at %s: %s", range, message));
    this.range = range;
    this.secondRange = null;
  }

  SemanticError(SourceRange range, SourceRange secondRange, String message) {
    super(String.format("Semantic error at %s: %s", range, message));
    this.range = range;
    this.secondRange = secondRange;
  }

  @Override
  public String getSourceReferencingMessage(List<String> sourceFile) {
    String message =
        getMessage()
            + System.lineSeparator()
            + System.lineSeparator()
            + range.annotateSourceFileExcerpt(sourceFile);
    if (null != secondRange) {
      message += System.lineSeparator() + secondRange.annotateSourceFileExcerpt(sourceFile);
    }

    return message;
  }
}
