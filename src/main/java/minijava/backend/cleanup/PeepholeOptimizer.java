package minijava.backend.cleanup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import minijava.backend.instructions.*;
import minijava.backend.operands.Operand;
import minijava.ir.emit.NameMangler;
import org.jooq.lambda.Seq;

public class PeepholeOptimizer {
  private final List<Instruction> instructions;
  private final Set<String> usedLabels = new HashSet<>();
  private static final Rule[] RULES = {
    rule(Mov.class, i -> i.src.equals(i.dest), delete()),
    rule(Add.class, i -> isImmOf(i.left, 0), delete()),
    rule(Sub.class, i -> isImmOf(i.left, 0), delete()),
    rule(IMul.class, i -> isImmOf(i.left, 1), delete()),
  };

  private static <T extends Instruction> Function<T, Iterable<Instruction>> delete() {
    return i -> Seq.empty();
  }

  private static boolean isImmOf(Operand op, long value) {
    return op.match(imm -> imm.value == value, reg -> false, mem -> false);
  }

  public PeepholeOptimizer(List<Instruction> instructions) {
    this.instructions = instructions;
    usedLabels.add(NameMangler.mangledMainMethodName());
  }

  private List<Instruction> optimize() {
    ArrayList<Instruction> optimized = new ArrayList<>(instructions.size());
    nextInstruction:
    for (Instruction instruction : instructions) {
      ifCanBeCastTo(Jmp.class, instruction, jmp -> usedLabels.add(jmp.label));
      ifCanBeCastTo(Jcc.class, instruction, jcc -> usedLabels.add(jcc.label));
      ifCanBeCastTo(Call.class, instruction, call -> usedLabels.add(call.label));
      for (Rule rule : RULES) {
        if (rule.predicate.test(instruction)) {
          rule.replace.apply(instruction).forEach(optimized::add);
          continue nextInstruction;
        }
      }
      // no rule matched.
      optimized.add(instruction);
    }

    optimized.removeIf(l -> l instanceof Label && !usedLabels.contains(((Label) l).label));
    return optimized;
  }

  private static <T> void ifCanBeCastTo(Class<T> clazz, Object thing, Consumer<T> then) {
    if (clazz.equals(thing.getClass())) {
      then.accept(clazz.cast(thing));
    }
  }

  public static List<Instruction> optimize(List<Instruction> instructions) {
    return new PeepholeOptimizer(instructions).optimize();
  }

  private static <T extends Instruction> Rule rule(
      Class<T> clazz, Predicate<T> predicate, Function<T, Iterable<Instruction>> replace) {
    return new Rule(
        i -> i.getClass().equals(clazz) && predicate.test((T) i), i -> replace.apply((T) i));
  }

  private static class Rule {
    public final Predicate<Instruction> predicate;
    public final Function<Instruction, Iterable<Instruction>> replace;

    private Rule(
        Predicate<Instruction> predicate, Function<Instruction, Iterable<Instruction>> replace) {
      this.predicate = predicate;
      this.replace = replace;
    }
  }
}
