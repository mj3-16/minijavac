package minijava.ir.assembler.deconstruction;

import static com.google.common.collect.Lists.newArrayList;
import static minijava.ir.utils.FirmUtils.modeToWidth;
import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Sets;
import firm.Relation;
import java.util.*;
import minijava.ir.assembler.allocation.AllocationResult;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.block.PhiFunction;
import minijava.ir.assembler.instructions.*;
import minijava.ir.assembler.lifetime.BlockPosition;
import minijava.ir.assembler.lifetime.LifetimeInterval;
import minijava.ir.assembler.operands.*;
import minijava.ir.assembler.registers.AMD64Register;
import org.jooq.lambda.Seq;

public class SsaDeconstruction {
  private final List<CodeBlock> linearization;
  private final AllocationResult allocationResult;
  private final Map<CodeBlock, List<Instruction>> resolvedBlocks = new HashMap<>();

  // For caching; O(V) instead of O(E)
  private final Map<CodeBlock, List<LifetimeInterval>> liveAtBegin = new HashMap<>();

  public SsaDeconstruction(List<CodeBlock> linearization, AllocationResult allocationResult) {
    this.linearization = linearization;
    this.allocationResult = allocationResult;
  }

  private List<Instruction> assembleInstructionList() {
    lowerVirtualOperandsAndInsertSpills();
    resolvePhisAndSplitIntervals();
    return flattenIntoInstructionList();
  }

  private void lowerVirtualOperandsAndInsertSpills() {
    for (CodeBlock block : linearization) {
      resolvedBlocks.put(block, InstructionListLowerer.lowerBlock(block, allocationResult));
    }
  }

  private void resolvePhisAndSplitIntervals() {
    // For each control flow edge...
    for (CodeBlock pred : linearization) {
      Set<Move> movesAfterPred = new HashSet<>();
      Set<CodeBlock> successors = pred.exit.getSuccessors();
      boolean predHasMultipleSuccs = successors.size() > 1;
      for (CodeBlock succ : successors) {
        boolean succHasPhis = succ.phis.size() > 0;
        // This check could be more precise if we stored the Preds in the CodeBlock, which we don't.
        // Critical edges without Phis might still slip through, causing trouble for split intervals.
        assert !succHasPhis || !predHasMultipleSuccs
            : "Found a critical edge from " + pred + " to " + succ;
        resolveControlFlowEdge(pred, succ, movesAfterPred);
        // movesAfterPred is the best witness for a critical edge: If there is a move necessary, pred may not have
        // multiple successors. If that was the case, one of the successors could always be scheduled immediately
        // after the pred, since it would not have multiple preds and therefore the edge can't be a back edge.
        System.out.println(movesAfterPred);
        assert !predHasMultipleSuccs || movesAfterPred.isEmpty()
            : "'multiple successors => no moves necessary' was hurt. " + pred + " to " + succ;
      }

      resolvedBlocks.get(pred).addAll(resolveMoves(movesAfterPred));
    }
  }

  private List<Instruction> resolveMoves(Set<Move> moves) {
    List<Instruction> instructions = new ArrayList<>();
    Set<AMD64Register> scratchRegisters = new HashSet<>();

    // As long as moves isn't a perfect permutation, we chip away resolvable moves
    Set<AMD64Register> read = getReadRegisters(moves);
    while (!moves.isEmpty()) {
      AMD64Register scratch =
          scratchRegisters.size() > 0 ? scratchRegisters.iterator().next() : null;
      PrioritizedMove next = findNextBestMove(moves);
      switch (next.priority) {
        case IMMEDIATE_SRC:
          moveViaMov(instructions, scratchRegisters, next.move);
          break;
        case MEM_MEM_UNSAFE:
          // We could potentially make much better use of scratch registers, but I'm not in the mood.
          swapViaStack(instructions, next.move);
          recordSwap(next.move, moves, scratchRegisters);
          break;
        case REG_MEM_UNSAFE:
          // Could split move with scratch
          swapViaXchg(instructions, next.move);
          recordSwap(next.move, moves, scratchRegisters);
          break;
        case REG_REG_UNSAFE:
          swapViaXchg(instructions, next.move);
          recordSwap(next.move, moves, scratchRegisters);
          break;
        case MEM_REG_SAFE:
          moveViaMov(instructions, scratchRegisters, next.move);
          recordMov(next.move, moves, scratchRegisters);
          break;
        case MEM_MEM_SAFE:
          moveViaStack(instructions, scratch, next.move);
          recordMov(next.move, moves, scratchRegisters);
          break;
        case REG_REG_SAFE:
          moveViaMov(instructions, scratchRegisters, next.move);
          recordMov(next.move, moves, scratchRegisters);
          break;
        case REG_MEM_SAFE:
          moveViaMov(instructions, scratchRegisters, next.move);
          recordMov(next.move, moves, scratchRegisters);
          break;
      }

      Set<AMD64Register> stillRead = getReadRegisters(moves);
      Sets.SetView<AMD64Register> notReadAnyMore = Sets.difference(read, stillRead);
      scratchRegisters.addAll(notReadAnyMore);
      read = stillRead;
    }

    return instructions;
  }

  private Set<AMD64Register> getReadRegisters(Set<Move> moves) {
    return seq(moves)
        .map(m -> Sets.union(m.src.reads(false), m.dest.reads(true)))
        .flatMap(Seq::seq)
        .cast(AMD64Register.class)
        .toSet();
  }

  private void moveViaStack(List<Instruction> instructions, AMD64Register scratch, Move move) {
    if (scratch == null) {
      instructions.add(new Push(move.src));
      instructions.add(new Pop(move.dest));
    } else {
      RegisterOperand tmp = new RegisterOperand(move.dest.width, scratch);
      instructions.add(new Mov(move.src, tmp));
      instructions.add(new Mov(tmp, move.dest));
    }
  }

  private void moveViaMov(
      List<Instruction> instructions, Set<AMD64Register> scratchRegisters, Move move) {
    instructions.add(new Mov(move.src, move.dest));
  }

  private void recordMov(Move move, Set<Move> moves, Set<AMD64Register> scratchRegisters) {
    moves.remove(move);
    removeDestRegister(move, scratchRegisters);
  }

  private void recordSwap(Move swap, Set<Move> moves, Set<AMD64Register> scratchRegisters) {
    // The value previously at swap.src is where it belongs now.
    // But now the value previously at swap.dest is at swap.src! So we have to fix up any other moves still
    // referencing swap.src.
    // It's safe to say that there are no further writes to swap.dest.
    moves.remove(swap);
    for (Move move : moves) {
      if (move.src.equals(swap.dest)) {
        move.src = swap.src;
      }
    }
    swap.src = swap.dest;
    removeDestRegister(swap, scratchRegisters);
  }

  private void removeDestRegister(Move move, Set<AMD64Register> scratchRegisters) {
    if (move.dest instanceof RegisterOperand) {
      // The value at move.dest is frozen now. We may not use it as a scratch register anymore.
      RegisterOperand dest = (RegisterOperand) move.dest;
      scratchRegisters.remove(dest.register);
    }
  }

  private void swapViaStack(List<Instruction> instructions, Move move) {
    instructions.add(new Push(move.src));
    instructions.add(new Push(move.dest));
    instructions.add(new Pop(move.src));
    instructions.add(new Pop(move.dest));
  }

  private void swapViaXchg(List<Instruction> instructions, Move move) {
    instructions.add(new Xchg(move.src, move.dest));
  }

  private PrioritizedMove findNextBestMove(Set<Move> remainingMoves) {
    assert !remainingMoves.isEmpty();
    Set<Operand> read = seq(remainingMoves).map(m -> m.src).toSet();
    return seq(remainingMoves)
        .map(
            move -> {
              if (move.src instanceof ImmediateOperand) {
                // These can be retained indefinitely, as their src can't be overwritten.
                return new PrioritizedMove(move, MovePriority.IMMEDIATE_SRC);
              } else {
                // All other moves are between memory and registers.
                boolean isSafe = !read.contains(move.dest);
                boolean needsPushPop =
                    move.isMemToMem(); // Could take scratch registers into account, but not now...
                boolean isRegToReg = move.isRegToReg();
                if (!isSafe) {
                  if (needsPushPop) {
                    return new PrioritizedMove(move, MovePriority.MEM_MEM_UNSAFE);
                  } else if (isRegToReg) {
                    return new PrioritizedMove(move, MovePriority.REG_REG_UNSAFE);
                  } else {
                    return new PrioritizedMove(move, MovePriority.REG_MEM_UNSAFE);
                  }
                } else {
                  if (needsPushPop) {
                    return new PrioritizedMove(move, MovePriority.MEM_MEM_SAFE);
                  } else if (isRegToReg) {
                    return new PrioritizedMove(move, MovePriority.REG_REG_SAFE);
                  } else if (move.src instanceof RegisterOperand) {
                    return new PrioritizedMove(move, MovePriority.REG_MEM_SAFE);
                  } else {
                    return new PrioritizedMove(move, MovePriority.MEM_REG_SAFE);
                  }
                }
              }
            })
        .maxBy(pm -> pm.priority)
        .get();
  }

  private List<LifetimeInterval> liveAtBegin(CodeBlock succ) {
    return liveAtBegin.computeIfAbsent(
        succ, k -> allocationResult.liveIntervalsAt(BlockPosition.beginOf(k)));
  }

  private void resolveControlFlowEdge(CodeBlock pred, CodeBlock succ, Set<Move> movesAfterPred) {
    // For each interval live at the begin of succ
    BlockPosition endOfPred = BlockPosition.endOf(pred);
    BlockPosition beginOfSucc = BlockPosition.beginOf(succ);
    for (LifetimeInterval li : liveAtBegin(succ)) {
      boolean startsAtSucc = succ.equals(li.firstBlock());
      OperandWidth width = modeToWidth(li.register.value.getMode());
      Operand dest = allocationResult.hardwareOperandAt(width, li.register, beginOfSucc);
      Operand src;
      if (startsAtSucc) {
        // li is the interval of a Phi of succ. We don't go through the virtual Phis of succ, but to the allocated
        // Phis at the Label instruction (which always is the first instruction of a lowered block).
        Label label = (Label) resolvedBlocks.get(succ).get(0);
        PhiFunction def =
            seq(label.physicalPhis).filter(phi -> phi.output.equals(dest)).findFirst().get();
        src = def.inputs.get(pred);
      } else {
        src = allocationResult.hardwareOperandAt(width, li.register, endOfPred);
      }

      if (!src.equals(dest)) {
        System.out.println("endOfPred = " + endOfPred);
        System.out.println("beginOfSucc = " + beginOfSucc);
        movesAfterPred.add(new Move(src, dest));
      }
    }
  }

  private List<Instruction> flattenIntoInstructionList() {
    List<Instruction> instructions = new ArrayList<>();
    for (int i = 0; i < linearization.size(); i++) {
      CodeBlock block = linearization.get(i);
      CodeBlock next = i + 1 < linearization.size() ? linearization.get(i + 1) : null;
      instructions.addAll(resolvedBlocks.get(block));
      instructions.addAll(lowerBlockExit(block, next));
    }
    return instructions;
  }

  /**
   * This is a little more complicated than it needs to be, because we try to fall through to
   * successor blocks if possible.
   */
  private ArrayList<Instruction> lowerBlockExit(CodeBlock block, CodeBlock next) {
    return block.exit.match(
        zero -> newArrayList(new Ret()),
        one ->
            one.target.equals(next)
                ? newArrayList() // No jmp needed, just fall through
                : newArrayList(new Jmp(one.target.label)),
        two ->
            two.falseTarget.equals(next)
                ? newArrayList(
                    new Jcc(two.trueTarget.label, two.relation)) // We can fall through to false
                : two.trueTarget.equals(next)
                    ? newArrayList(
                        new Jcc(
                            two.falseTarget.label,
                            negatedWithoutUnordered(two.relation))) // fall through to true
                    : newArrayList(
                        new Jcc(two.trueTarget.label, two.relation),
                        new Jmp(two.falseTarget.label)));
  }

  private static Relation negatedWithoutUnordered(Relation relation) {
    return Relation.fromValue(relation.negated().value() & ~Relation.Unordered.value());
  }

  public static List<Instruction> assembleInstructionList(
      List<CodeBlock> linearization, AllocationResult allocationResult) {
    return new SsaDeconstruction(linearization, allocationResult).assembleInstructionList();
  }

  private enum MovePriority {
    IMMEDIATE_SRC, // Could and should be handled last as it just makes the scheduling situation worse.
    MEM_MEM_UNSAFE, // Needs an exchange through Push Pop
    REG_MEM_UNSAFE, // Needs a Reg/Mem XCHG or a Push Pop (Dunno whats more expensive)
    REG_REG_UNSAFE, // Needs a Reg/Reg XCHG, which is cheap
    MEM_REG_SAFE, // This might block a scratch register
    MEM_MEM_SAFE, // Somewhat expensive because of Push Pop, but at least no exchange necessary
    REG_REG_SAFE, // Cheap as pie, but no real further advantage
    REG_MEM_SAFE // We maybe free a scratch register
  }

  private static class Move {
    public Operand src;
    public Operand dest;

    public Move(Operand src, Operand dest) {
      this.src = src;
      this.dest = dest;
    }

    public boolean isMemToMem() {
      return dest instanceof MemoryOperand && src instanceof MemoryOperand;
    }

    public boolean isRegToReg() {
      return dest instanceof RegisterOperand && src instanceof RegisterOperand;
    }

    @Override
    public String toString() {
      return src + " -> " + dest;
    }
  }

  private static class PrioritizedMove {
    public final Move move;
    public final MovePriority priority;

    private PrioritizedMove(Move move, MovePriority priority) {
      this.move = move;
      this.priority = priority;
    }
  }
}
