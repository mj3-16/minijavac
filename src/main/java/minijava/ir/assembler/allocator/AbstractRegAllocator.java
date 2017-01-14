package minijava.ir.assembler.allocator;

import minijava.ir.assembler.SimpleNodeAllocator;
import minijava.ir.assembler.block.LinearCodeSegment;
import minijava.ir.utils.MethodInformation;

public abstract class AbstractRegAllocator {

  protected final MethodInformation methodInfo;
  protected final SimpleNodeAllocator nodeAllocator;
  protected final LinearCodeSegment code;

  public AbstractRegAllocator(
      MethodInformation methodInfo, LinearCodeSegment code, SimpleNodeAllocator nodeAllocator) {
    this.methodInfo = methodInfo;
    this.code = code;
    this.nodeAllocator = nodeAllocator;
  }

  public abstract LinearCodeSegment process();
}
