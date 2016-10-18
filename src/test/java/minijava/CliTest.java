package minijava;

import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CliTest {

	/**
	 * Tests the `--echo` option of the cli
     */
	@Test
	public void echo() throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		Cli cli = new Cli(output, System.err, new String[]{"--echo", p("empty_file")});
		assertEquals("Exit code", 0, cli.run());
		assertEquals("Empty file should result in no output", "", output.toString());
		output = new ByteArrayOutputStream();
		cli = new Cli(output, System.err, new String[]{"--echo", p("non_empty_file")});
		assertEquals("Exit code", 0, cli.run());
		assertEquals("Non empty file should result in output", "test", output.toString());
		cli = new Cli(output, new ByteArrayOutputStream(), new String[]{"--echo", p("non_existing_file")});
		assertTrue("Exit code should by > 0 for non existing file", cli.run() > 0);
	}

	private String p(String fileName){
		return "src/test/resources/minijava/Cli/" + fileName;
	}
}