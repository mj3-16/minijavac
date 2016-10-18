package minijava;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;

import static org.junit.Assert.assertEquals;

/**
 * Basic tests of the cli.
 */
public class CliTest {

	/**
	 * Tests the `--echo` option of the cli by calling the `run` script
     */
	@Test
	public void echo() throws Exception {
		Process process = Runtime.getRuntime().exec(new String[]{"./run", "--echo", "non_existing_file"});
		process.waitFor();
		assertEquals("Error code for invalid --echo call", 1, process.exitValue());
		File tmpFile = File.createTempFile("tmp", "tmp");
		FileWriter writer = new FileWriter(tmpFile);
		writer.write("t");
		writer.close();
		process = Runtime.getRuntime().exec(new String[]{"./run", "--echo", tmpFile.getAbsolutePath()});
		process.waitFor();
		assertEquals(0, process.exitValue());
		assertEquals('t', process.getInputStream().read());
		tmpFile.delete();
	}
}