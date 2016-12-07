package minijava.ir.optimize;

import firm.Graph;
import firm.nodes.Node;
import java.util.Deque;
import java.util.LinkedList;
import java.util.NoSuchElementException;

class Worklist {
  private final Deque<Node> queue = new LinkedList<>();

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

  /** Inserts the specified element at the end of this work list. */
  void addLast(Node n) {
    queue.addLast(n);
  }

  /** Inserts the specified element at the front this work list. */
  void addFirst(Node n) {
    queue.addFirst(n);
  }

  /**
   * Retrieves and removes the head of this work list.
   *
   * @throws NoSuchElementException if this work list is empty
   */
  Node pop() {
    return queue.pop();
  }

  /** Returns true if this work list contains no elements. */
  boolean isEmpty() {
    return queue.isEmpty();
  }
}
