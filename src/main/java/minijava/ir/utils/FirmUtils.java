package minijava.ir.utils;

import firm.nodes.Address;

public class FirmUtils {
  public static String getMethodLdName(firm.nodes.Call node) {
    return ((Address) node.getPred(1)).getEntity().getLdName();
  }
}
