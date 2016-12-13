package minijava.ir;

import firm.nodes.*;

public interface DefaultNodeVisitor extends NodeVisitor {
  @Override
  default void visit(Add node) {}

  @Override
  default void visit(Address node) {}

  @Override
  default void visit(Align node) {}

  @Override
  default void visit(Alloc node) {}

  @Override
  default void visit(Anchor node) {}

  @Override
  default void visit(And node) {}

  @Override
  default void visit(Bad node) {}

  @Override
  default void visit(Bitcast node) {}

  @Override
  default void visit(Block node) {}

  @Override
  default void visit(Builtin node) {}

  @Override
  default void visit(Call node) {}

  @Override
  default void visit(Cmp node) {}

  @Override
  default void visit(Cond node) {}

  @Override
  default void visit(Confirm node) {}

  @Override
  default void visit(Const node) {}

  @Override
  default void visit(Conv node) {}

  @Override
  default void visit(CopyB node) {}

  @Override
  default void visit(Deleted node) {}

  @Override
  default void visit(Div node) {}

  @Override
  default void visit(Dummy node) {}

  @Override
  default void visit(End node) {}

  @Override
  default void visit(Eor node) {}

  @Override
  default void visit(Free node) {}

  @Override
  default void visit(IJmp node) {}

  @Override
  default void visit(Id node) {}

  @Override
  default void visit(Jmp node) {}

  @Override
  default void visit(Load node) {}

  @Override
  default void visit(Member node) {}

  @Override
  default void visit(Minus node) {}

  @Override
  default void visit(Mod node) {}

  @Override
  default void visit(Mul node) {}

  @Override
  default void visit(Mulh node) {}

  @Override
  default void visit(Mux node) {}

  @Override
  default void visit(NoMem node) {}

  @Override
  default void visit(Not node) {}

  @Override
  default void visit(Offset node) {}

  @Override
  default void visit(Or node) {}

  @Override
  default void visit(Phi node) {}

  @Override
  default void visit(Pin node) {}

  @Override
  default void visit(Proj node) {}

  @Override
  default void visit(Raise node) {}

  @Override
  default void visit(Return node) {}

  @Override
  default void visit(Sel node) {}

  @Override
  default void visit(Shl node) {}

  @Override
  default void visit(Shr node) {}

  @Override
  default void visit(Shrs node) {}

  @Override
  default void visit(Size node) {}

  @Override
  default void visit(Start node) {}

  @Override
  default void visit(Store node) {}

  @Override
  default void visit(Sub node) {}

  @Override
  default void visit(Switch node) {}

  @Override
  default void visit(Sync node) {}

  @Override
  default void visit(Tuple node) {}

  @Override
  default void visit(Unknown node) {}

  @Override
  default void visitUnknown(Node node) {}
}
