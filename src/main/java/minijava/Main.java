package minijava;

import java.nio.file.FileSystems;
import minijava.cli.Cli;

public class Main {
  public static void main(String[] args) {
    // new LexerRepl().run(); //← to test the lexer
    Cli cli = new Cli(System.out, System.err, FileSystems.getDefault());
    int status = cli.run(args);
    System.exit(status);
  }
}
