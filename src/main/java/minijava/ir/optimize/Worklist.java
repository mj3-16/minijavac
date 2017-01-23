package minijava.ir.optimize;

import firm.nodes.Node;
import java.util.*;

class Worklist {
  /** We use this Set for prevent duplicate nodes in the queue. */
  private final Set<Node> queueSet;

  private final Deque<Node> queue;

  Worklist(Collection<Node> initialWorklist) {
    queue = new ArrayDeque<>(initialWorklist);
    queueSet = new HashSet<>(initialWorklist);
  }

  /** Enqueues the specified element if it's not a duplicate. */
  void enqueue(Node n) {
    if (queueSet.contains(n)) {
      return;
    }
    queueSet.add(n);
    queue.addFirst(n);
  }

  /**
   * Dequeues the next item of the work list.
   *
   * @throws NoSuchElementException if this work list is empty
   */
  Node dequeue() {
    Node n = queue.removeFirst();
    queueSet.remove(n);
    return n;
  }

  /** Returns true if this work list contains no elements. */
  boolean isEmpty() {
    return queue.isEmpty();
  }
}
