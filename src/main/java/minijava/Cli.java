package minijava;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.io.OutputStream;
import java.io.PrintStream;

class Cli {
    @Parameter(names = "--echo", description = "print given file on stdout")
    private String echoPath;

    @Parameter(names = "--help", help = true, description = "print this usage information")
    private boolean help;


    private final PrintStream out;
    private final PrintStream err;
    private final JCommander jCommander;


    Cli(OutputStream out, OutputStream err, String... args) {
        this.out = new PrintStream(out);
        this.err = new PrintStream(err);
        this.jCommander = new JCommander(this, args);
    }

    int run() {
        if(help) {
            StringBuilder sb = new StringBuilder();
            jCommander.usage(sb);
            out.print(sb.toString());
            return 0;
        }
        if (echoPath != null) {
            // TODO: implement echo
            out.println("--echo " + echoPath);
        }
        return 0;
    }

}
