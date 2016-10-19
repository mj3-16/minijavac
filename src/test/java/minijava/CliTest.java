package minijava;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

/**
 * Basic tests of the cli.
 */
public class CliTest {

	/**
	 * Temporary file that should just contain the string "test"
	 */
	private static File tmpFile;

	@BeforeClass
	public static void setUp() throws Exception {
		tmpFile = File.createTempFile("tmp", "tmp");
		tmpFile.deleteOnExit();
		FileWriter writer = new FileWriter(tmpFile);
		writer.write("t");
		writer.close();
	}

	/**
	 * Tests the `--echo` option of the cli by calling the `run` script
	 */
	@Test
	public void echo() throws Exception {
		Process process = Runtime.getRuntime().exec(new String[]{"./run", "--echo", "non_existing_file"});
		process.waitFor();
		assertEquals("Error code for invalid --echo call", 1, process.exitValue());


		process = Runtime.getRuntime().exec(new String[]{"./run", "--echo", tmpFile.getAbsolutePath()});
		process.waitFor();
		assertEquals(0, process.exitValue());
		assertEquals('t', process.getInputStream().read());
	}

	@Test
	public void echoFromATemporaryDirectory() throws Exception {
		File tmpDirectory = Files.createTempDirectory("test").toFile();
		tmpDirectory.deleteOnExit();
		ProcessBuilder builder = new ProcessBuilder(new File("run").getAbsolutePath(), "--echo",
				tmpFile.getAbsolutePath());
		builder.directory(tmpDirectory);
		Process process = builder.start();
		process.waitFor();
		assertEquals("Error code", 0, process.exitValue());
		assertEquals('t', process.getInputStream().read());
	}
}