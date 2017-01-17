package minijava.ir.emit;

import firm.Mode;
import firm.nodes.Proj;
import org.pcollections.PSet;

public class ControlFlowProjs {
  public final PSet<Proj> true_;
  public final PSet<Proj> false_;

  public ControlFlowProjs(PSet<Proj> true_, PSet<Proj> false_) {
    for (Proj p : true_) {
      assert p.getMode().equals(Mode.getX());
    }
    for (Proj p : false_) {
      assert p.getMode().equals(Mode.getX());
    }
    this.true_ = true_;
    this.false_ = false_;
  }

  public ControlFlowProjs addFalseJmps(PSet<Proj> alsoFalse) {
    return new ControlFlowProjs(true_, false_.plusAll(alsoFalse));
  }

  public ControlFlowProjs addTrueJmps(PSet<Proj> alsoTrue) {
    return new ControlFlowProjs(true_.plusAll(alsoTrue), false_);
  }
}
