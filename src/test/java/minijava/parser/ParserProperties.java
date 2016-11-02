package minijava.parser;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import java.util.List;
import java.util.stream.Collectors;
import minijava.token.Position;
import minijava.token.Terminal;
import minijava.token.Token;
import org.junit.runner.RunWith;

@RunWith(JUnitQuickcheck.class)
public class ParserProperties {

  @Property
  public void generatedTerminalStreamIsAccepted(@From(ProgramGenerator.class) Program program) {
    List<Token> tokens =
        program
            .terminals
            .stream()
            .map(t -> new Token(t, new Position(0, 0), ""))
            .collect(Collectors.toList());

    new Parser(tokens.iterator()).parse();
  }

  static class Program {
    final List<Terminal> terminals;

    Program(List<Terminal> terminals) {
      this.terminals = terminals;
    }
  }

  public static class ProgramGenerator extends Generator<Program> {

    public ProgramGenerator() {
      super(Program.class);
    }

    @Override
    public Program generate(SourceOfRandomness random, GenerationStatus status) {
      return new Program(TerminalStreamGenerator.generateProgram(random, status));
    }
  }
}
