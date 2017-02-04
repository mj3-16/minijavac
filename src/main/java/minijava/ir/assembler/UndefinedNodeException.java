package minijava.ir.assembler;

import firm.nodes.Node;

public class UndefinedNodeException extends RuntimeException {
  public UndefinedNodeException(Node node) {
    super("Accessed node " + node + " which was explicitly marked as never defined.");
  }
}
