package minijava;

public class Main {
    public static void main(String[] args) {
        System.exit(new Cli(System.out, System.err, args).run());
    }
}
