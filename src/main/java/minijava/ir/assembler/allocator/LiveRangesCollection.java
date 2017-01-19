package minijava.ir.assembler.allocator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import minijava.ir.assembler.block.LinearCodeSegment;
import minijava.ir.assembler.instructions.Argument;
import minijava.ir.assembler.instructions.Instruction;

/** A collection of lists of live ranges for each argument used in a piece of code. */
public class LiveRangesCollection {

  private final Map<Argument, LiveRanges> liveRangesForArguments;

  LiveRangesCollection(Map<Argument, LiveRanges> liveRangesForArguments) {
    this.liveRangesForArguments = Collections.unmodifiableMap(liveRangesForArguments);
  }

  static LiveRangesCollection fromLinearCodeSegment(LinearCodeSegment code) {
    Map<Argument, LiveRanges> rangesMap = new HashMap<>();
    code.getUsedArguments()
        .forEach(a -> rangesMap.put(a, LiveRanges.fromLinearCodeSegment(a, code)));
    return new LiveRangesCollection(rangesMap);
  }

  Optional<LiveRanges.Range> getRange(Argument argument, Instruction currentInstruction) {
    return liveRangesForArguments.get(argument).getRange(currentInstruction);
  }
}
