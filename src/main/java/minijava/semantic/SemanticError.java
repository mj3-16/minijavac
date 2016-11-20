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

  @Override
  public String getSourceReferencingMessage(List<String> sourceFile) {
    String message =
        getMessage()
            + System.lineSeparator()
            + System.lineSeparator()
            + range.annotateSourceFileExcerpt(sourceFile);
    return message;
  }
}
