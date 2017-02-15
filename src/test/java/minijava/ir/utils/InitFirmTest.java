package minijava.ir.utils;

import firm.Mode;
import org.junit.Test;

public class InitFirmTest {

  @Test
  public void initWithWrapperTwiceDoesNotThrow() throws Exception {
    InitFirm.init();
    InitFirm.init();
    Mode.getIs(); // make sure Firm is initialized (this call would throw otherwise)
  }
}
