package minijava.ast.visitors;

import java.util.List;
import minijava.ast.classmember.Parameter;
import minijava.ast.statement.Block;
import minijava.ast.type.Type;

public interface ClassMemberVisitor<TRef, TReturn> {

  TReturn visitField(Type<TRef> type, String name);

  TReturn visitMethod(
      boolean isStatic,
      Type<TRef> returnType,
      String name,
      List<Parameter<TRef>> parameters,
      Block body);
}
