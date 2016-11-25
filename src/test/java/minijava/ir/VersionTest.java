package minijava.ir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import firm.Firm;
import org.junit.Test;

public class VersionTest {

  @Test
  public void version() throws Exception {
    assertThat(Firm.getMajorVersion(), equalTo(1));
  }
}
