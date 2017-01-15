package minijava.ir.assembler.allocator;

import minijava.ir.assembler.NodeAllocator;
import minijava.ir.assembler.block.LinearCodeSegment;

public abstract class AbstractRegAllocator {

  protected final NodeAllocator nodeAllocator;
  protected final LinearCodeSegment code;

  public AbstractRegAllocator(LinearCodeSegment code, NodeAllocator nodeAllocator) {
    this.code = code;
    this.nodeAllocator = nodeAllocator;
  }

  public abstract LinearCodeSegment process();
}
