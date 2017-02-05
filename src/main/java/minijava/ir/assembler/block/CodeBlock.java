package minijava.ir.assembler.block;

import firm.Relation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import minijava.ir.assembler.instructions.Instruction;

public class CodeBlock {
  public final String label;
  public final Set<PhiFunction> phis = new HashSet<>();
  public final List<Instruction> instructions = new ArrayList<>();
  public ExitArity exit;

  public CodeBlock(String label) {
    this.label = label;
  }

  /** Poor man's ADT */
  public interface ExitArity {
    class Zero implements ExitArity {}

    class One implements ExitArity {
      public final CodeBlock target;

      public One(CodeBlock target) {
        this.target = target;
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
    }
  }
}
