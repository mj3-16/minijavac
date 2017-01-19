package minijava.ir.assembler.allocator;

import static org.jooq.lambda.Seq.seq;

import java.util.*;
import minijava.ir.assembler.block.LinearCodeSegment;
import minijava.ir.assembler.instructions.Argument;
import minijava.ir.assembler.instructions.Evict;
import minijava.ir.assembler.instructions.Instruction;
import minijava.ir.assembler.instructions.MetaCall;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.tuple.Tuple3;

/**
 * A kind of database for the live ranges used by the linear scan allocator. It assumes that every
 * instruction has a correct instruction number.
 */
public class LiveRanges {

  final Argument argument;

  final List<Range> ranges;

  LiveRanges(Argument argument, List<Range> ranges) {
    this.argument = argument;
    this.ranges = Collections.unmodifiableList(ranges);
  }

  Optional<Range> getRange(Instruction instruction) {
    for (Range range : ranges) {
      if (range.contains(instruction)) {
        return Optional.of(range);
      }
      if (instruction.getNumberInSegment() > range.beginInstruction.getNumberInSegment()) {
        break;
      }
    }
    return Optional.empty();
  }

  /** Creates a list of live ranges for a given argument and a given piece of code. */
  static LiveRanges fromLinearCodeSegment(Argument argument, LinearCodeSegment codeSegment) {
    List<Tuple3<Instruction, Instruction, Boolean>> rangeTuples = new ArrayList<>();
    boolean loadFromStackAtBeginning = false;
    Instruction beginInstruction = null;
    Instruction endInstruction = null;
    for (LinearCodeSegment.InstructionOrString instructionOrString : codeSegment) {
      if (instructionOrString.instruction.isPresent()) {
        Instruction instruction = instructionOrString.instruction.get();
        if (instruction instanceof Evict || instruction instanceof MetaCall) {
          // these instructions break an interval into two parts
          if (beginInstruction != null) {
            rangeTuples.add(
                new Tuple3<Instruction, Instruction, Boolean>(
                    beginInstruction, endInstruction, loadFromStackAtBeginning));
            beginInstruction = null;
            endInstruction = null;
            loadFromStackAtBeginning = true;
          }
        }
        if (!instruction.getArguments().contains(argument)) {
          continue;
        }
        if (beginInstruction == null) {
          beginInstruction = instruction;
        }
        endInstruction = instruction;
      }
    }
    if (beginInstruction != null) {
      rangeTuples.add(
          new Tuple3<Instruction, Instruction, Boolean>(
              beginInstruction, endInstruction, loadFromStackAtBeginning));
    }
    return new LiveRanges(
        argument, seq(rangeTuples).map(t -> new Range(t.v1, t.v2, t.v3)).toList());
  }

  static class Range implements Comparable<Instruction> {
    final Instruction beginInstruction;
    /** Inclusive end */
    Instruction endInstruction;

    final boolean loadFromStackAtBeginning;

    Range(
        Instruction beginInstruction,
        Instruction endInstruction,
        boolean loadFromStackAtBeginning) {
      this.beginInstruction = beginInstruction;
      this.endInstruction = endInstruction;
      this.loadFromStackAtBeginning = loadFromStackAtBeginning;
    }

    /** Compares an instruction to this range. An instruction equals this range if it lies in it. */
    @Override
    public int compareTo(@NotNull Instruction instruction) {
      if (instruction.getNumberInSegment() > endInstruction.getNumberInSegment()) {
        return -1;
      }
      if (instruction.getNumberInSegment() < beginInstruction.getNumberInSegment()) {
        return 1;
      }
      return 0;
    }

    boolean contains(Instruction instruction) {
      return instruction.getNumberInSegment() >= beginInstruction.getNumberInSegment()
          && instruction.getNumberInSegment() <= endInstruction.getNumberInSegment();
    }
  }

  static class LatestEndPointComparator implements Comparator<Range> {

    @Override
    public int compare(Range o1, Range o2) {
      return Integer.compare(
          o1.endInstruction.getNumberInSegment(), o2.endInstruction.getNumberInSegment());
    }
  }
}
