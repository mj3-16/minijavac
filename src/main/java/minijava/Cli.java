package minijava;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.List;

class Cli {

    @Parameter(description = "files")
    private List<String> files = new ArrayList<>();

    @Parameter(names = "--echo", description = "only print files to stdout")
    private boolean echo = false;

    @Parameter(names = "--help", help = true, description = "print this usage information")
    private boolean help;


    private final JCommander jCommander;


    Cli(String... args) {
        this.jCommander = new JCommander(this, args);
    }

    void run() {
        if(help) {
            jCommander.usage();
            System.exit(0);
        }
        if (echo) {
            // TODO: implement echo
            System.out.println("--echo " + Joiner.on(' ').join(files));
        }
    }

}
