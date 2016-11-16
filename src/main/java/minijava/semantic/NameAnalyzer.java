package minijava.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import minijava.ast.*;
import minijava.ast.Class;
import minijava.ast.Method.Parameter;

class NameAnalyzer
    implements Program.Visitor<Nameable, Program<Ref>>,
        Class.Visitor<Nameable, Class<Ref>>,
        Field.Visitor<Nameable, Field<Ref>>,
        Type.Visitor<Nameable, Type<Ref>>,
        Method.Visitor<Nameable, Method<Ref>>,
        Statement.Visitor<Nameable, Statement<Ref>> {

  private SymbolTable types = new SymbolTable();
  private SymbolTable fieldsAndVariables = new SymbolTable();
  private SymbolTable methods = new SymbolTable();

  @Override
  public Program<Ref> visitProgram(Program<? extends Nameable> that) {
    // collect all types first (throws if duplicates exist)
    this.types = that.acceptVisitor(new TypeCollector());
    List<Class<Ref>> refClasses = new ArrayList<>(that.declarations.size());
    for (Class<? extends Nameable> c : that.declarations) {
      Class<Ref> refClass = c.acceptVisitor(this);
      refClasses.add(refClass);
    }
    return new Program<>(refClasses, that.range);
  }

  @Override
  public Class<Ref> visitClassDeclaration(Class<? extends Nameable> that) {
    // fieldsAndVariables in current class
    fieldsAndVariables = new SymbolTable();
    fieldsAndVariables.enterScope();
    List<Field<Ref>> newFields = new ArrayList<>(that.fields.size());
    for (Field<? extends Nameable> f : that.fields) {
      if (fieldsAndVariables.inCurrentScope(f.name())) {
        throw new SemanticError();
      }
      fieldsAndVariables.insert(f.name(), f);
      Field<Ref> field = f.acceptVisitor(this);
      newFields.add(field);
    }

    // methods in current class
    methods = new SymbolTable();
    methods.enterScope();
    List<Method<Ref>> newMethods = new ArrayList<>(that.methods.size());
    for (Method<? extends Nameable> m : that.methods) {
      if (methods.inCurrentScope(m.name())) {
        throw new SemanticError();
      }
      methods.insert(m.name(), m);
      Method<Ref> method = m.acceptVisitor(this);
      newMethods.add(method);
    }
    return new Class<>(that.name(), newFields, newMethods, that.range());
  }

  @Override
  public Field<Ref> visitField(Field<? extends Nameable> that) {
    Type<Ref> type = that.type.acceptVisitor(this);
    return new Field<>(type, that.name(), that.range());
  }

  @Override
  public Type<Ref> visitType(Type<? extends Nameable> that) {
    String typeName = that.typeRef.name();
    Optional<Definition> optDef = types.lookup(typeName);
    if (!optDef.isPresent()) {
      throw new SemanticError("Type " + typeName + " is not defined");
    }
    return new Type<>(new Ref(optDef.get()), that.dimension, that.range);
  }

  @Override
  public Method<Ref> visitMethod(Method<? extends Nameable> that) {
    Type<Ref> returnType = that.returnType.acceptVisitor(this);
    // check types and transform parameters
    List<Parameter<Ref>> newParams = new ArrayList<>(that.parameters.size());
    for (Parameter<? extends Nameable> p : that.parameters) {
      Type<Ref> type = p.type.acceptVisitor(this);
      newParams.add(new Parameter<>(type, p.name(), p.range()));
    }

    // go from field scope to method scope
    fieldsAndVariables.enterScope();

    // check for parameters with same name
    for (Parameter<Ref> p : newParams) {
      if (fieldsAndVariables.inCurrentScope(p.name())) {
        throw new SemanticError();
      }
      fieldsAndVariables.insert(p.name(), p);
    }
    Block<Ref> block = (Block) that.body.acceptVisitor(this);
    // go back to field scope
    fieldsAndVariables.leaveScope();
    return new Method<>(that.isStatic, returnType, that.name(), newParams, block, that.range());
  }

  @Override
  public Block<Ref> visitBlock(Block<? extends Nameable> that) {
    return null;
  }

  private static void test(String s) {
    int s = 5;
  }

  @Override
  public Statement<Ref> visitEmpty(Statement.Empty<? extends Nameable> that) {
    return null;
  }

  @Override
  public Statement<Ref> visitIf(Statement.If<? extends Nameable> that) {
    return null;
  }

  @Override
  public Statement<Ref> visitExpressionStatement(
      Statement.ExpressionStatement<? extends Nameable> that) {
    return null;
  }

  @Override
  public Statement<Ref> visitWhile(Statement.While<? extends Nameable> that) {
    return null;
  }

  @Override
  public Statement<Ref> visitReturn(Statement.Return<? extends Nameable> that) {
    return null;
  }
}
