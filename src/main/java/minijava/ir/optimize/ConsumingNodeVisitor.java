package minijava.ir.optimize;

import firm.nodes.*;
import java.util.function.Consumer;

/** A {@link NodeVisitor} that passes visited nodes to a {@link Consumer<Node>}. */
class ConsumingNodeVisitor extends NodeVisitor.Default {
  private final Consumer<Node> consumer;

  ConsumingNodeVisitor(Consumer<Node> consumer) {
    this.consumer = consumer;
  }

  @Override
  public void defaultVisit(Node n) {
    consumer.accept(n);
  }
}
