package minijava.ir.optimize;

import firm.Graph;
import firm.nodes.*;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;

class Worklist {
  private final Queue<Node> queue = new LinkedList<>();

  private Worklist() {}

  /**
   * Creates a new {@code Worklist}, initialized with the nodes of {@code graph} in topological
   * order.
   */
  static Worklist fillTopological(Graph graph) {
    Worklist w = new Worklist();
    graph.walkTopological(new NodeCollector(w));
    return w;
  }

  /** Appends the specified element to the end of this work list. */
  void enqueue(Node n) {
    queue.add(n);
  }

  /**
   * Retrieves and removes the head of this work list.
   *
   * @throws NoSuchElementException if this work list is empty
   */
  Node remove() {
    return queue.remove();
  }

  /** Returns true if this work list contains no elements. */
  boolean isEmpty() {
    return queue.isEmpty();
  }
}
