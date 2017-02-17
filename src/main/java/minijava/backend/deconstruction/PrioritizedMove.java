package minijava.backend.deconstruction;

class PrioritizedMove {

  public final Move move;
  public final MovePriority priority;

  PrioritizedMove(Move move, MovePriority priority) {
    this.move = move;
    this.priority = priority;
  }
}
