package minijava.ir.assembler.allocator;

import minijava.ir.assembler.NodeAllocator;
import minijava.ir.assembler.block.LinearCodeSegment;
import minijava.ir.utils.MethodInformation;

public abstract class AbstractRegAllocator {

  protected final MethodInformation methodInfo;
  protected final NodeAllocator nodeAllocator;
  protected final LinearCodeSegment code;

  public AbstractRegAllocator(
      MethodInformation methodInfo, LinearCodeSegment code, NodeAllocator nodeAllocator) {
    this.methodInfo = methodInfo;
    this.code = code;
    this.nodeAllocator = nodeAllocator;
  }

  public abstract LinearCodeSegment process();
}
