package minijava.parser;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import java.util.ArrayList;
import java.util.List;
import minijava.token.Position;
import minijava.token.Terminal;
import minijava.token.Token;
import org.junit.runner.RunWith;

@RunWith(JUnitQuickcheck.class)
public class ParserProperties {

  @Property(trials = 500)
  public void generatedTerminalStreamIsAccepted(
      @From(ProgramGenerator.class) @Size(max = 1500) Program program) {
    List<Token> tokens = new ArrayList<>(program.terminals.size() + 1);

    for (int i = 0; i < program.terminals.size(); ++i) {
      tokens.add(new Token(program.terminals.get(i), new Position(1, i), null));
    }

    // Debug printfs:
    // System.out.println("Terminal stream: " + program.terminals);

    new Parser(tokens.iterator()).parse();
  }

  static class Program {
    final List<Terminal> terminals;

    Program(List<Terminal> terminals) {
      this.terminals = terminals;
    }
  }

  public static class ProgramGenerator extends Generator<Program> {

    private Size size;

    public ProgramGenerator() {
      super(Program.class);
    }

    @Override
    public Program generate(SourceOfRandomness random, GenerationStatus status) {
      return new Program(TerminalStreamGenerator.generateProgram(random, size.max()));
    }

    public void configure(Size size) {
      this.size = size;
    }
  }
}
