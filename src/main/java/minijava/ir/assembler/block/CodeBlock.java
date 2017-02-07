package minijava.ir.assembler.block;

import com.google.common.collect.Sets;
import firm.Relation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import minijava.ir.assembler.instructions.Instruction;

public class CodeBlock {
  public final String label;
  public final Set<PhiFunction> phis = new HashSet<>();
  public final List<Instruction> instructions = new ArrayList<>();
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

    class Zero implements ExitArity {
      @Override
      public Set<CodeBlock> getSuccessors() {
        return new HashSet<>();
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
      public String toString() {
        return "<" + relation + " " + trueTarget + " " + falseTarget + ">";
      }
    }
  }
}
