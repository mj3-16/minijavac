package minijava.ir.utils;

import firm.Mode;
import firm.nodes.Proj;

public class ProjPair {
  public final Proj true_;
  public final Proj false_;

  public ProjPair(Proj true_, Proj false_) {
    assert true_.getMode().equals(Mode.getX());
    assert false_.getMode().equals(Mode.getX());
    this.true_ = true_;
    this.false_ = false_;
  }
}
