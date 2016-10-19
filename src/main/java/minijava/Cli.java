package minijava;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;

class Cli {
    @Parameter(names = "--echo", description = "print given file on stdout")
    private String echoPath;

    @Parameter(names = "--help", help = true, description = "print this usage information")
    private boolean help;


    private final PrintStream out;
    private final PrintStream err;
    private final JCommander jCommander;

    private Cli(OutputStream out, OutputStream err, String... args) {
        this.out = new PrintStream(out);
        this.err = new PrintStream(err);
        this.jCommander = new JCommander(this, args);
    }

    public static Cli create(OutputStream out, OutputStream err, String... args) throws InvalidCommandLineArguments {
        try {
            return new Cli(out, err, args);
        } catch (ParameterException ex){
            throw new InvalidCommandLineArguments(ex.getMessage());
        }
    }

    int run() {
        if(help) {
            out.print(getUsageInfo());
            return 0;
        }
        if (echoPath != null) {
            try {
                out.print(String.join("\n", Files.readAllLines(Paths.get(echoPath))));
            } catch (IOException e) {
                e.printStackTrace(err);
                return 1;
            }
        }
        return 0;
    }

    public static String getUsageInfo(){
        StringBuilder sb = new StringBuilder();
        new Cli(System.out, System.err).jCommander.usage(sb);
        return sb.toString();
    }

}
