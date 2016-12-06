package minijava.ir.optimize;

import firm.nodes.*;

/** A {@link NodeVisitor} that enqueues visited nodes into a {@link Worklist}. */
class NodeCollector implements NodeVisitor {
  private final Worklist worklist;

  NodeCollector(Worklist worklist) {
    this.worklist = worklist;
  }

  @Override
  public void visit(Add node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Address node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Align node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Alloc node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Anchor node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(And node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Bad node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Bitcast node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Block node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Builtin node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Call node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Cmp node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Cond node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Confirm node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Const node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Conv node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(CopyB node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Deleted node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Div node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Dummy node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(End node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Eor node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Free node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(IJmp node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Id node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Jmp node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Load node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Member node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Minus node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Mod node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Mul node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Mulh node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Mux node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(NoMem node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Not node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Offset node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Or node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Phi node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Pin node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Proj node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Raise node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Return node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Sel node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Shl node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Shr node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Shrs node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Size node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Start node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Store node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Sub node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Switch node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Sync node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Tuple node) {
    worklist.enqueue(node);
  }

  @Override
  public void visit(Unknown node) {
    worklist.enqueue(node);
  }

  @Override
  public void visitUnknown(Node node) {
    worklist.enqueue(node);
  }
}
