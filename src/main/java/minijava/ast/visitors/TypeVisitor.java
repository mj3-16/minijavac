package minijava.ast.visitors;

import minijava.ast.type.Type;

public interface TypeVisitor<TRef, TReturn> {

  TReturn visitArray(Type<TRef> elementType);

  TReturn visitClass(TRef classRef);

  TReturn visitVoid();

  TReturn visitBoolean();

  TReturn visitInt();
}
