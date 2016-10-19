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
    /**
     * Did the command argument parsing fail?
     */
    private final boolean invalidArguments;


    Cli(OutputStream out, OutputStream err, String... args) {
        this.out = new PrintStream(out);
        this.err = new PrintStream(err);
        JCommander commander;
        try {
            commander = new JCommander(this, args);
        } catch (ParameterException ex){
            commander = null;
            this.err.println(ex.getMessage());
            this.err.println();
        }
        this.jCommander = new JCommander(this);
        this.invalidArguments = commander == null;
    }

    int run() {
        if (invalidArguments){
            StringBuilder sb = new StringBuilder();
            jCommander.usage(sb);
            err.print(sb.toString());
            return 1;
        }
        if(help) {
            StringBuilder sb = new StringBuilder();
            jCommander.usage(sb);
            out.print(sb.toString());
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

}
