package minijava.ir.utils;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

public class CompileUtils {

  public static void compileAssemblerFile(String assemblerFile, String outputFile)
      throws IOException {
    File runtime = getRuntimeFile();

    boolean useGC =
        System.getenv().containsKey("MJ_USE_GC") && System.getenv("MJ_USE_GC").equals("1");

    String gcApp = "";
    if (useGC) {
      gcApp = " -DUSE_GC -lgc ";
    }
    String cmd =
        String.format(
            "gcc %s %s -o %s %s", runtime.getAbsolutePath(), assemblerFile, outputFile, gcApp);
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
      System.err.println("Warning: Linking step failed");
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
