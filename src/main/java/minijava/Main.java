package minijava;

public class Main {
    public static void main(String[] args) {
        try {
            System.exit(Cli.create(System.out, System.err, args).run());
        } catch (InvalidCommandLineArguments invEx) {
            System.err.println(invEx.getMessage());
            System.err.println();
            System.err.println(Cli.getUsageInfo());
            System.exit(1);
        }
    }
}
