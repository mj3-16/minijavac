package minijava.ir.utils;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

public class CompileUtils {

  public static void compileAssemblerFile(
      String assemblerFile, String outputFile, boolean produceDebuggableBinary) throws IOException {
    File runtime = getRuntimeFile();

    boolean useGC =
        System.getenv().containsKey("MJ_USE_GC") && System.getenv("MJ_USE_GC").equals("1");

    String gccApp = "";
    if (useGC) {
      gccApp = " -DUSE_GC -lgc ";
    }

    if (System.getenv().containsKey("MJ_GCC_APP")) {
      gccApp += " " + System.getenv("MJ_GCC_APP");
    }

    if (produceDebuggableBinary) {
      gccApp = " -g3";
    } else {
      gccApp = " -O3";
    }

    String cmd =
        String.format(
            "gcc %s %s -o %s %s", runtime.getAbsolutePath(), assemblerFile, outputFile, gccApp);
    Process p = Runtime.getRuntime().exec(cmd);
    int c;
    while ((c = p.getErrorStream().read()) != -1) {
      System.out.print(Character.toString((char) c));
    }
    int res = -1;
    try {
      res = p.waitFor();
    } catch (Throwable t) {
    }
    if (res != 0) {
      System.err.println("Warning: Assembling and linking with gcc failed");
      System.exit(1);
    }
  }

  @NotNull
  private static File getRuntimeFile() throws IOException {
    File runtime = new File(Files.createTempDir(), "mj_runtime.c");
    runtime.deleteOnExit();
    InputStream s = ClassLoader.getSystemResourceAsStream("mj_runtime.c");
    if (s == null) {
      throw new RuntimeException("");
    }
    FileUtils.copyInputStreamToFile(s, runtime);
    return runtime;
  }
}
