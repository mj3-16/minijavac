package minijava.ir;

import firm.Mode;
import minijava.ir.utils.InitFirm;
import org.junit.Test;

public class InitFirmTest {

  @Test
  public void initWithWrapperTwiceDoesNotThrow() throws Exception {
    InitFirm.init();
    InitFirm.init();
    Mode.getIs(); // make sure Firm is initialized (this call would throw otherwise)
  }
}
