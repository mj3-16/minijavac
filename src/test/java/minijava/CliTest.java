package minijava;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.google.common.jimfs.Jimfs;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import minijava.cli.Cli;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Before;
import org.junit.Test;

public class CliTest {

  ByteArrayOutputStream out;
  ByteArrayOutputStream err;
  FileSystem fs;
  Cli cli;

  @Before
  public void setup() {
    out = new ByteArrayOutputStream();
    err = new ByteArrayOutputStream();
    fs = Jimfs.newFileSystem();
    cli = new Cli(out, err, fs);
  }

  @Test
  public void fileDoesNotExist_printErrorMessageAndSingalFailure() throws Exception {
    String filename = "non-existing-file";
    int status = cli.run("--echo", filename);
    assertThat(status, is(not(0)));
    assertThat(err.toString(), allOf(containsString(filename), containsString("doesn't exist")));
  }

  @Test
  public void multipleMainArguments_printUsageAndSingalFailure() throws Exception {
    int status = cli.run("--echo", "foo", "bar");
    assertThat(status, is(not(0)));
    assertThat(err.toString(), containsString(Cli.usage));
  }

  @Test
  public void bothLextestAndEchoOptionSet_printUsageAndSingalFailure() throws Exception {
    Path file = fs.getPath("file");
    Files.createFile(file);
    int status = cli.run("--lextest", "--echo", file.toString());
    assertThat(status, is(not(0)));
    assertThat(err.toString(), containsString(Cli.usage));
  }

  @Test
  public void helpAndInvalidOptionCombinationIsSet_printUsageAndSingalSuccess() throws Exception {
    int status = cli.run("--lextest", "--help", "--echo", "arg1", "arg2");
    assertThat(status, is(0));
    assertThat(out.toString(), containsString(Cli.usage));
  }

  @Test
  public void echoFile_contentWasWrittenToOut() throws Exception {
    Path file = fs.getPath("file");
    byte[] content = {5, 123, 100, 58, 39, 69, 26};
    Files.write(file, content);
    int status = cli.run("--echo", file.toString());
    assertThat(status, is(0));
    assertThat(out.toByteArray(), equalTo(content));
  }

  @Test
  public void lextestFile_tokensAreWrittenToOut() throws Exception {
    Path file = fs.getPath("file");
    byte[] content = "asd 01 void   /* comment */".getBytes(StandardCharsets.US_ASCII);
    Files.write(file, content);
    int status = cli.run("--lextest", file.toString());
    assertThat(status, is(0));
    assertThat(
        out.toString(),
        equalTo(
            String.format("identifier asd%ninteger literal 0%ninteger literal 1%nvoid%nEOF%n")));
  }
}
