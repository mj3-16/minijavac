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
    worklist.addLast(node);
  }

  @Override
  public void visit(Address node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Align node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Alloc node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Anchor node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(And node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Bad node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Bitcast node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Block node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Builtin node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Call node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Cmp node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Cond node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Confirm node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Const node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Conv node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(CopyB node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Deleted node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Div node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Dummy node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(End node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Eor node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Free node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(IJmp node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Id node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Jmp node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Load node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Member node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Minus node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Mod node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Mul node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Mulh node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Mux node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(NoMem node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Not node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Offset node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Or node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Phi node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Pin node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Proj node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Raise node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Return node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Sel node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Shl node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Shr node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Shrs node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Size node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Start node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Store node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Sub node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Switch node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Sync node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Tuple node) {
    worklist.addLast(node);
  }

  @Override
  public void visit(Unknown node) {
    worklist.addLast(node);
  }

  @Override
  public void visitUnknown(Node node) {
    worklist.addLast(node);
  }
}
