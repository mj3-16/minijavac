package minijava.backend.deconstruction;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import minijava.backend.instructions.Instruction;
import minijava.backend.instructions.Mov;
import minijava.backend.instructions.Pop;
import minijava.backend.instructions.Push;
import minijava.backend.instructions.Xchg;
import minijava.backend.operands.ImmediateOperand;
import minijava.backend.operands.Operand;
import minijava.backend.operands.RegisterOperand;
import minijava.backend.registers.AMD64Register;
import org.jooq.lambda.Seq;

public class MoveResolver {

  /** Resolves a set of moves which are to happen simultaneously. */
  public static List<Instruction> resolveMoves(Set<Move> moves) {
    assert seq(moves).map(m -> m.dest).distinct().count() == moves.size()
        : "Can't assign the same destination twice";

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

  private static Set<AMD64Register> getReadRegisters(Set<Move> moves) {
    return seq(moves)
        .map(m -> Sets.union(m.src.reads(false), m.dest.reads(true)))
        .flatMap(Seq::seq)
        .map(use -> use.register)
        .cast(AMD64Register.class)
        .toSet();
  }

  private static void moveViaStack(
      List<Instruction> instructions, AMD64Register scratch, Move move) {
    if (scratch == null) {
      instructions.add(new Push(move.src));
      instructions.add(new Pop(move.dest));
    } else {
      RegisterOperand tmp = new RegisterOperand(move.dest.irNode, scratch);
      instructions.add(new Mov(move.src, tmp));
      instructions.add(new Mov(tmp, move.dest));
    }
  }

  private static void moveViaMov(
      List<Instruction> instructions, Set<AMD64Register> scratchRegisters, Move move) {
    instructions.add(new Mov(move.src, move.dest));
  }

  private static void recordMov(Move move, Set<Move> moves, Set<AMD64Register> scratchRegisters) {
    moves.remove(move);
    removeDestRegister(move, scratchRegisters);
  }

  private static void recordSwap(Move swap, Set<Move> moves, Set<AMD64Register> scratchRegisters) {
    // The value previously at swap.src is where it belongs now.
    // But now the value previously at swap.dest is at swap.src! So we have to fix up any other moves still
    // referencing swap.src.
    // It's safe to say that there are no further writes to swap.dest.
    moves.remove(swap);
    List<Move> noops = new ArrayList<>();
    for (Move move : moves) {
      if (move.src.equals(swap.dest)) {
        move.src = swap.src;
      }
      if (move.isNoop()) {
        noops.add(move);
      }
    }
    swap.src = swap.dest;
    noops.forEach(moves::remove);
    removeDestRegister(swap, scratchRegisters);
  }

  private static void removeDestRegister(Move move, Set<AMD64Register> scratchRegisters) {
    if (move.dest instanceof RegisterOperand) {
      // The value at move.dest is frozen now. We may not use it as a scratch register anymore.
      RegisterOperand dest = (RegisterOperand) move.dest;
      scratchRegisters.remove(dest.register);
    }
  }

  private static void swapViaStack(List<Instruction> instructions, Move move) {
    instructions.add(new Push(move.src));
    instructions.add(new Push(move.dest));
    instructions.add(new Pop(move.src));
    instructions.add(new Pop(move.dest));
  }

  private static void swapViaXchg(List<Instruction> instructions, Move move) {
    instructions.add(new Xchg(move.src, move.dest));
  }

  private static PrioritizedMove findNextBestMove(Set<Move> remainingMoves) {
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
}
