package minijava.ir.assembler.block;

import com.google.common.collect.Sets;
import firm.Relation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import minijava.ir.assembler.instructions.CodeBlockInstruction;

public class CodeBlock {
  public final String label;
  public final Set<PhiFunction> phis = new HashSet<>();
  public final List<CodeBlockInstruction> instructions = new ArrayList<>();
  public ExitArity exit;
  /** This is the index in the linearization of the CFG. */
  public int linearizedOrdinal;

  public CodeBlock(String label) {
    this.label = label;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CodeBlock codeBlock = (CodeBlock) o;
    return Objects.equals(label, codeBlock.label);
  }

  @Override
  public int hashCode() {
    return Objects.hash(label);
  }

  @Override
  public String toString() {
    return label;
  }

  /** Poor man's ADT */
  public interface ExitArity {
    Set<CodeBlock> getSuccessors();

    <T> T match(Function<Zero, T> matchZero, Function<One, T> matchOne, Function<Two, T> matchTwo);

    class Zero implements ExitArity {
      @Override
      public Set<CodeBlock> getSuccessors() {
        return new HashSet<>();
      }

      @Override
      public <T> T match(
          Function<Zero, T> matchZero, Function<One, T> matchOne, Function<Two, T> matchTwo) {
        return matchZero.apply(this);
      }

      @Override
      public String toString() {
        return "<ret>";
      }
    }

    class One implements ExitArity {
      public final CodeBlock target;

      public One(CodeBlock target) {
        this.target = target;
      }

      @Override
      public Set<CodeBlock> getSuccessors() {
        return Sets.newHashSet(target);
      }

      @Override
      public <T> T match(
          Function<Zero, T> matchZero, Function<One, T> matchOne, Function<Two, T> matchTwo) {
        return matchOne.apply(this);
      }

      @Override
      public String toString() {
        return "<jmp " + target + ">";
      }
    }

    class Two implements ExitArity {
      public final Relation relation;
      public final CodeBlock trueTarget;
      public final CodeBlock falseTarget;

      public Two(Relation relation, CodeBlock trueTarget, CodeBlock falseTarget) {
        this.relation = relation;
        this.trueTarget = trueTarget;
        this.falseTarget = falseTarget;
      }

      @Override
      public Set<CodeBlock> getSuccessors() {
        return Sets.newHashSet(trueTarget, falseTarget);
      }

      @Override
      public <T> T match(
          Function<Zero, T> matchZero, Function<One, T> matchOne, Function<Two, T> matchTwo) {
        return matchTwo.apply(this);
      }

      @Override
      public String toString() {
        return "<" + relation + " " + trueTarget + " " + falseTarget + ">";
      }
    }
  }
}
