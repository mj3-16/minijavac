package minijava.ir.assembler.block;

import static org.jooq.lambda.Seq.seq;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import minijava.ir.assembler.instructions.Instruction;

/** Like {@link CodeSegment} but linearised. */
public class LinearCodeSegment extends Segment
    implements Iterable<LinearCodeSegment.InstructionOrString> {

  private final List<InstructionOrString> instructionsAndStrings;
  private final List<String> comments;

  public LinearCodeSegment(
      List<InstructionOrString> instructionsAndStrings, List<String> comments) {
    this.instructionsAndStrings = instructionsAndStrings;
    this.comments = comments;
  }

  public static LinearCodeSegment fromCodeSegment(CodeSegment codeSegment) {
    List<InstructionOrString> arr = new ArrayList<>();
    int i = 0;
    for (CodeBlock block : codeSegment.getBlocks()) {
      arr.add(new InstructionOrString(block.label + ":"));
      i++;
      for (Instruction instruction : block) {
        instruction.setNumberInSegment(i);
        instruction.setUsedByRelations();
        i++;
      }
      arr.add(new InstructionOrString(""));
      i++;
      seq(block).map(InstructionOrString::new).forEach(arr::add);
    }
    return new LinearCodeSegment(arr, codeSegment.getComments());
  }

  @Override
  public Iterator<InstructionOrString> iterator() {
    return instructionsAndStrings.iterator();
  }

  @Override
  public String toGNUAssembler() {
    StringBuilder builder = new StringBuilder();
    if (comments.size() > 0) {
      builder
          .append(System.lineSeparator())
          .append("/* ")
          .append(String.join(System.lineSeparator(), comments))
          .append("*/\n");
    }
    builder.append("\t.text").append(System.lineSeparator());
    builder.append(
        seq(this)
            .map(iOrS -> iOrS.map(Instruction::toGNUAssembler, s -> s))
            .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator())));
    return builder.toString();
  }

  public static class InstructionOrString {
    public final Optional<Instruction> instruction;
    public final Optional<String> string;

    public InstructionOrString(Instruction instruction) {
      this.instruction = Optional.of(instruction);
      this.string = Optional.empty();
    }

    public InstructionOrString(String string) {
      this.string = Optional.of(string);
      this.instruction = Optional.empty();
    }

    public <T> T map(
        Function<Instruction, T> instructionConverter, Function<String, T> stringConverter) {
      if (instruction.isPresent()) {
        return instructionConverter.apply(instruction.get());
      }
      return stringConverter.apply(string.get());
    }
  }

  public void replaceInstruction(Instruction oldInstruction, List<Instruction> replacement) {
    for (int i = oldInstruction.getNumberInSegment();
        i < oldInstruction.getNumberInSegment() + replacement.size();
        i++) {
      Instruction instruction = replacement.get(i - oldInstruction.getNumberInSegment());
      instruction.setNumberInSegment(oldInstruction.getNumberInSegment());
      instructionsAndStrings.set(i, new InstructionOrString(instruction));
    }
  }

  public void placeAfter(Instruction instruction, List<Instruction> newInstructions) {
    List<Instruction> instrs = new ArrayList<>();
    instrs.add(instruction);
    instrs.addAll(newInstructions);
    replaceInstruction(instruction, instrs);
  }

  public void placeBefore(Instruction instruction, List<Instruction> newInstructions) {
    List<Instruction> instrs = new ArrayList<>();
    instrs.addAll(newInstructions);
    instrs.add(instruction);
    replaceInstruction(instruction, instrs);
  }

  public void appendToEnd(List<Instruction> instructions) {
    placeAfter(instructions.get(instructions.size() - 1), instructions);
  }

  public List<String> getComments() {
    return Collections.unmodifiableList(comments);
  }
}
