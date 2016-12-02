package minijava.ir;

import static minijava.ir.Types.*;

import com.google.common.io.Files;
import firm.*;
import firm.Program;
import firm.Type;
import firm.bindings.binding_ircons;
import firm.nodes.*;
import firm.nodes.Block;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import minijava.ast.*;
import minijava.ast.Class;
import minijava.util.SourceRange;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Emits an intermediate representation for a given minijava Program. */
public class IREmitter
    implements minijava.ast.Program.Visitor<Void>,
        minijava.ast.Type.Visitor<Type>,
        minijava.ast.BasicType.Visitor<Type>,
        minijava.ast.Block.Visitor<Void>,
        Expression.Visitor<Node>,
        BlockStatement.Visitor<Void> {

  private final IdentityHashMap<Class, ClassType> classTypes = new IdentityHashMap<>();
  private final IdentityHashMap<Method, Entity> methods = new IdentityHashMap<>();
  private final IdentityHashMap<Field, Entity> fields = new IdentityHashMap<>();

  /** Reference to the method currently under construction. Fields below are reset per method. */
  private Method currentMethod;
  /**
   * Maps local variable definitions such as parameters and local variable definitions to their
   * assigned index. Which is a firm thing.
   */
  private final IdentityHashMap<LocalVariable, Integer> localVarIndexes = new IdentityHashMap<>();
  /** The Basic Block graph of the current function. */
  private Graph graph;
  /**
   * Construction is a firm Node factory that makes sure that we don't duplicate expressions, thus
   * making common sub expressions irrepresentable.
   */
  private Construction construction;
  /**
   * Stores the node's value in the current lvar (if there is any). This is crucial for assignment
   * to work. E.g. in the expression x = 5, we analyze the variable expression x and get back its
   * value, which is irrelevant for assignment, since we need the *address of* x. This is where we
   * need storeInCurrentLval, which would store the expression node of the RHS (evaluating to 5) in
   * the address of x.
   *
   * <p>Now, in an ideal world, this variable would be 'Node currentLval', but firm doesn't offer a
   * function for getting the address of a local variable as a Node. So in order to not duplicate a
   * lot of work (e.g. computing array offsets, etc.) in a mechanism without this variable, we
   * abstract actual assignment out into a function.
   */
  @Nullable private Function<Node, Node> storeInCurrentLval;

  public IREmitter() {
    InitFirm.init();
  }

  @Override
  public Void visitProgram(minijava.ast.Program that) {
    classTypes.clear();
    methods.clear();
    fields.clear();
    that.acceptVisitor(new Collector(classTypes, fields, methods));
    for (Class klass : that.declarations) {
      klass.methods.forEach(this::emitBody);
    }
    return null;
  }

  private void emitBody(Method m) {
    // graph and construction are irrelevant to anything before or after.
    // It's more like 2 additional parameters to the visitor.

    currentMethod = m;
    graph = constructEmptyGraphFromPrototype(m);
    construction = new Construction(graph);

    localVarIndexes.clear();

    if (!m.isStatic) {
      connectParametersToIRVariables(m);
    }

    m.body.acceptVisitor(this);

    finishGraphAndHandleFallThrough(m);

    Dump.dumpGraph(graph, "--after-construction");
    graph.check();
  }

  private Graph constructEmptyGraphFromPrototype(Method that) {
    // So we got our method prototype from the previous pass. Now for the body
    int locals = that.body.acceptVisitor(new NumberOfLocalVariablesVisitor());
    if (!that.isStatic) {
      // We have parameters only when the method is not main.
      // Since that.parameters doesn't contain an entry for this, we take its size +1.
      locals += that.parameters.size() + 1;
    }
    return new Graph(methods.get(that), locals);
  }

  /**
   * Make the connection between function parameters and local firm variables. firm handles this
   * variable stuff so that it can build up the SSA form later on.
   */
  private void connectParametersToIRVariables(Method that) {
    // Never call this on main.
    assert !that.isStatic;

    Node args = graph.getArgs();

    // First a hack for the this parameter. We want it to get allocated index 0, which will be the
    // case if we force its LocalVarIndex first. We do so by allocating an index for a dummy LocalVariable.
    minijava.ast.Type fakeThisType =
        new minijava.ast.Type(new Ref<>(that.definingClass.def), 0, SourceRange.FIRST_CHAR);
    // ... do this just for the allocation effect.
    int thisIdx = getLocalVarIndex(new LocalVariable(fakeThisType, null, SourceRange.FIRST_CHAR));
    // We rely on this when accessing this.
    assert thisIdx == 0;
    construction.setVariable(
        thisIdx, construction.newProj(args, storageModeForType(fakeThisType), thisIdx));

    for (LocalVariable p : that.parameters) {
      // we just made this connection in the loop above
      // Also effectively this should just count up.
      // Also note that we are never trying to access this or the
      int idx = getLocalVarIndex(p);
      // Where is this documented anyway? SimpleIf seems to be the only reference...
      Node param = construction.newProj(args, storageModeForType(p.type), idx);
      construction.setVariable(idx, param);
    }
  }

  /** Finish the graph by adding possible return statements in the case of void */
  private void finishGraphAndHandleFallThrough(Method that) {
    if (!construction.isUnreachable()) {
      // Add an implicit return statement at the end of the block,
      // iff we have return type void. In which case returnTypes has length 0.
      if (that.returnType.equals(minijava.ast.Type.VOID)) {
        Node[] retVal = {};
        if (that.isStatic) {
          retVal = new Node[] {construction.newConst(0, Mode.getIs())};
        }
        Node ret = construction.newReturn(construction.getCurrentMem(), retVal);
        construction.setUnreachable();
        graph.getEndBlock().addPred(ret);
      } else {
        // We can't just conjure a return value of arbitrary type.
        // This must be caught by the semantic pass.
        assert false;
      }
    }

    construction.setCurrentBlock(graph.getEndBlock());
    construction.finish();
  }

  @Override
  public Type visitType(minijava.ast.Type that) {
    Type type = that.basicType.def.acceptVisitor(this);
    if (type == null) {
      // e.g. void
      return null;
    }
    for (int i = 0; i < that.dimension; i++) {
      // We don't know the array statically, so just pass 0
      // of the number of elements (which is allowed according
      // to the docs)
      // TODO shouldn't we reuse types for arrays as well?
      type = new PointerType(new ArrayType(type, 0));
    }
    return type;
  }

  @Override
  public Type visitVoid(BuiltinType that) {
    return null;
  }

  @Override
  public Type visitInt(BuiltinType that) {
    return INT_TYPE;
  }

  @Override
  public Type visitBoolean(BuiltinType that) {
    return BOOLEAN_TYPE;
  }

  @Override
  public Type visitAny(BuiltinType that) {
    // TODO... not sure how to handle this
    assert false;
    return null;
  }

  @Override
  public Type visitClass(Class that) {
    return classTypes.get(that);
  }

  @Override
  public Void visitBlock(minijava.ast.Block that) {
    for (BlockStatement statement : that.statements) {
      statement.acceptVisitor(this);
    }
    return null;
  }

  @Override
  public Void visitEmpty(Statement.Empty that) {
    return null;
  }

  @Override
  public Void visitIf(Statement.If that) {
    // static cmp node for testing
    //    Node falseConst = construction.newConst(0, Mode.getBu());
    //    Node trueConst = construction.newConst(1, Mode.getBu());
    //    Node cmp = construction.newCmp(falseConst, trueConst, Relation.Equal);

    firm.nodes.Block trueBlock = construction.newBlock();
    // block for else part or code after if (if no else part exists)
    firm.nodes.Block falseBlock = construction.newBlock();
    that.condition.acceptVisitor(new CompareNodeVisitor(trueBlock, falseBlock));

    // code in true block
    trueBlock.mature();
    construction.setCurrentBlock(trueBlock);
    that.then.acceptVisitor(this);
    Node endIf = construction.newJmp();

    // code in false block
    construction.setCurrentBlock(falseBlock);
    if (!that.else_.isPresent()) {
      // if construct (without else)
      falseBlock.addPred(endIf);
      falseBlock.mature();
    } else {
      // if-else construct
      falseBlock.mature();
      that.else_.get().acceptVisitor(this);
      Node endElse = construction.newJmp();

      // add block for code after this if-else
      firm.nodes.Block afterElse = construction.newBlock();
      afterElse.addPred(endElse);
      afterElse.addPred(endIf);
      afterElse.mature();
      construction.setCurrentBlock(afterElse);
    }
    return null;
  }

  private class CompareNodeVisitor implements Expression.Visitor<firm.nodes.Node> {
    private final Block trueBlock;
    private final Block falseBlock;

    CompareNodeVisitor(Block trueBlock, Block falseBlock) {
      this.trueBlock = trueBlock;
      this.falseBlock = falseBlock;
    }

    @Override
    public Node visitBinaryOperator(Expression.BinaryOperator that) {
      switch (that.op) {
        case AND:
          // expression = (left && right)
          Block rightBlock = construction.newBlock();
          that.left.acceptVisitor(new CompareNodeVisitor(rightBlock, falseBlock));
          rightBlock.mature();

          construction.setCurrentBlock(rightBlock);
          that.right.acceptVisitor(new CompareNodeVisitor(trueBlock, falseBlock));
          break;
        case OR:
          rightBlock = construction.newBlock();
          that.left.acceptVisitor(new CompareNodeVisitor(trueBlock, rightBlock));
          rightBlock.mature();

          construction.setCurrentBlock(rightBlock);
          that.right.acceptVisitor(new CompareNodeVisitor(trueBlock, falseBlock));
          break;
        case LT:
          compareWithRelation(that, Relation.Less);
          break;
        case LEQ:
          compareWithRelation(that, Relation.LessEqual);
          break;
        case GT:
          compareWithRelation(that, Relation.Greater);
          break;
        case GEQ:
          compareWithRelation(that, Relation.GreaterEqual);
          break;
        case EQ:
          compareWithRelation(that, Relation.Equal);
          break;
        case NEQ:
          Node lhs = that.left.acceptVisitor(this);
          Node rhs = that.right.acceptVisitor(this);
          // we only have a "Equal" relation. Use that and switch true and false around instead.
          Node cmp = construction.newCmp(lhs, rhs, Relation.Equal);
          Node cond = construction.newCond(cmp);
          Node trueProj = construction.newProj(cond, Mode.getX(), Cond.pnTrue);
          Node falseProj = construction.newProj(cond, Mode.getX(), Cond.pnFalse);
          // switch true and false
          trueBlock.addPred(falseProj);
          falseBlock.addPred(trueProj);
          break;
        case PLUS:
        case MINUS:
        case MULTIPLY:
        case MODULO:
        case DIVIDE:
        case ASSIGN:
          return that.acceptVisitor(IREmitter.this);
        default:
          throw new AssertionError("we missed a case in the switch statement!");
      }
      return null;
    }

    private void compareWithRelation(Expression.BinaryOperator binOp, Relation relation) {
      Node lhs = binOp.left.acceptVisitor(IREmitter.this);
      Node rhs = binOp.right.acceptVisitor(IREmitter.this);
      Node cmp = construction.newCmp(lhs, rhs, relation);
      Node cond = construction.newCond(cmp);
      Node trueProj = construction.newProj(cond, Mode.getX(), Cond.pnTrue);
      Node falseProj = construction.newProj(cond, Mode.getX(), Cond.pnFalse);
      trueBlock.addPred(trueProj);
      falseBlock.addPred(falseProj);
    }

    @Override
    public Node visitUnaryOperator(Expression.UnaryOperator that) {
      switch (that.op) {
        case NOT:
          that.expression.acceptVisitor(new CompareNodeVisitor(falseBlock, trueBlock));
          break;
        default:
          throw new AssertionError("not implemented yet");
      }
      return null;
    }

    @Override
    public Node visitMethodCall(Expression.MethodCall that) {
      return that.acceptVisitor(IREmitter.this);
    }

    @Override
    public Node visitFieldAccess(Expression.FieldAccess that) {
      return that.acceptVisitor(IREmitter.this);
    }

    @Override
    public Node visitArrayAccess(Expression.ArrayAccess that) {
      return that.acceptVisitor(IREmitter.this);
    }

    @Override
    public Node visitNewObject(Expression.NewObject that) {
      return that.acceptVisitor(IREmitter.this);
    }

    @Override
    public Node visitNewArray(Expression.NewArray that) {
      return that.acceptVisitor(IREmitter.this);
    }

    @Override
    public Node visitVariable(Expression.Variable that) {
      return that.acceptVisitor(IREmitter.this);
    }

    @Override
    public Node visitBooleanLiteral(Expression.BooleanLiteral that) {
      Node literal =
          construction.newConst(that.literal ? TargetValue.getBTrue() : TargetValue.getBFalse());
      Node cond = construction.newCond(literal);
      Node trueProj = construction.newProj(cond, Mode.getX(), Cond.pnTrue);
      Node falseProj = construction.newProj(cond, Mode.getX(), Cond.pnFalse);
      trueBlock.addPred(trueProj);
      falseBlock.addPred(falseProj);
      return null;
    }

    @Override
    public Node visitIntegerLiteral(Expression.IntegerLiteral that) {
      return that.acceptVisitor(IREmitter.this);
    }

    @Override
    public Node visitReferenceTypeLiteral(Expression.ReferenceTypeLiteral that) {
      return that.acceptVisitor(IREmitter.this);
    }
  }

  @Override
  public Void visitExpressionStatement(Statement.ExpressionStatement that) {
    // We evaluate this just for the side effects, e.g. the memory edges this adds.
    that.expression.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitWhile(Statement.While that) {
    Node endStart = construction.newJmp();

    firm.nodes.Block conditionBlock = construction.newBlock();
    firm.nodes.Block bodyBlock = construction.newBlock();
    firm.nodes.Block endBlock = construction.newBlock();

    // code for the condition
    conditionBlock.addPred(endStart);
    construction.setCurrentBlock(conditionBlock);
    construction.getCurrentMem(); // See the slides, we need those PhiM nodes
    that.condition.acceptVisitor(new CompareNodeVisitor(bodyBlock, endBlock));
    bodyBlock.mature();
    endBlock.mature();

    // code in body
    construction.setCurrentBlock(bodyBlock);
    that.body.acceptVisitor(this);
    // jump back to the loop condition
    Node endBody = construction.newJmp();
    conditionBlock.addPred(endBody);
    conditionBlock.mature();

    construction.setCurrentBlock(endBlock);

    graph.keepAlive(conditionBlock); // Also in the slides
    return null;
  }

  @Override
  public Void visitReturn(Statement.Return that) {
    Node[] retVals = {};
    if (that.expression.isPresent()) {
      retVals = new Node[] {that.expression.get().acceptVisitor(this)};
    }

    if (currentMethod.isStatic) {
      retVals = new Node[] {construction.newConst(0, Mode.getIs())};
    }

    Node ret = construction.newReturn(construction.getCurrentMem(), retVals);
    // Judging from other examples, we don't need to set currentmem here.
    graph.getEndBlock().addPred(ret);

    // No code should follow a return statement.
    construction.setUnreachable();

    return null;
  }

  @Override
  public Node visitBinaryOperator(Expression.BinaryOperator that) {
    Node left = that.left.acceptVisitor(this);
    // Save the store emitter of the left expression (if there's one, e.g. iff it's an lval).
    // See the comments on storeInCurrentLval.
    Function<Node, Node> storeInLeft = storeInCurrentLval;

    Node right = that.right.acceptVisitor(this);

    assert storeInLeft != null; // This should be true after semantic analysis.

    // This can never produce an lval (an assignable expression)
    storeInCurrentLval = null;

    switch (that.op) {
      case ASSIGN:
        // See the comment on storeInCurrentLval. Assignment is basically outsourced to the
        // resp. expression visitor
        return storeInLeft.apply(right);
      case PLUS:
        return construction.newAdd(left, right);
      case MINUS:
        return construction.newSub(left, right);
      case MULTIPLY:
        return construction.newMul(left, right);
      case DIVIDE:
        // A `div` operation results in an element of the divmod tuple in memory
        // We convert the values from int to long, to prevent the INT_MIN / -1 exception
        // This shouldn't make any difference performance wise on 64 bit systems
        Node leftConv = construction.newConv(left, Mode.getLs());
        Node rightConv = construction.newConv(right, Mode.getLs());
        Node divNode =
            construction.newDiv(
                construction.getCurrentMem(),
                leftConv,
                rightConv,
                binding_ircons.op_pin_state.op_pin_state_pinned);
        // Fetch the result from memory
        Node retProj = construction.newProj(divNode, Mode.getLs(), Div.pnRes);
        // Convert it back to int
        return construction.newConv(retProj, Mode.getIs());
      case MODULO:
        // A `mod` operation results in an element of the divmod tuple in memory
        // We convert the values from int to long, to prevent the INT_MIN / -1 exception
        // This shouldn't make any difference performance wise on 64 bit systems
        Node leftModConv = construction.newConv(left, Mode.getLs());
        Node rightModConv = construction.newConv(right, Mode.getLs());
        Node modNode =
            construction.newMod(
                construction.getCurrentMem(),
                leftModConv,
                rightModConv,
                binding_ircons.op_pin_state.op_pin_state_pinned);
        // Fetch the result from memory
        Node retModProj = construction.newProj(modNode, Mode.getLs(), Mod.pnRes);
        // Convert it back to int
        return construction.newConv(retModProj, Mode.getIs());
      case OR:
        return construction.newOr(left, right);
      case AND:
        return construction.newAnd(left, right);
      case EQ:
        Node cmp = construction.newCmp(left, right, Relation.Equal);
        // TODO: How to project out a byte flag?
        throw new UnsupportedOperationException();
      case NEQ:
        throw new UnsupportedOperationException();
      case LT:
        throw new UnsupportedOperationException();
      case LEQ:
        throw new UnsupportedOperationException();
      case GT:
        throw new UnsupportedOperationException();
      case GEQ:
        throw new UnsupportedOperationException();
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public Node visitUnaryOperator(Expression.UnaryOperator that) {
    if (that.op == Expression.UnOp.NEGATE && that.expression instanceof Expression.IntegerLiteral) {
      // treat this case special in case the integer literal is 2147483648 (doesn't fit in int)
      int lit = Integer.parseInt("-" + ((Expression.IntegerLiteral) that.expression).literal);
      return construction.newConst(lit, storageModeForType(minijava.ast.Type.INT));
    }
    // This can never produce an lval (an assignable expression)
    storeInCurrentLval = null;
    Node expression = that.expression.acceptVisitor(this);
    switch (that.op) {
      case NEGATE:
        return construction.newMinus(expression);
      case NOT:
        return construction.newNot(expression);
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public Node visitMethodCall(Expression.MethodCall that) {
    if (that.self.type == minijava.ast.Type.SYSTEM_OUT && that.method.name().equals("println")) {
      return visitSystemOutPrintln(that.arguments.get(0));
    }

    Entity method = methods.get(that.method.def);

    List<Node> args = new ArrayList<>(that.arguments.size() + 1);
    // first argument is always this (static calls are disallowed)
    // this == that.self (X at `X.METHOD()`)
    Node thisVar = that.self.acceptVisitor(this);
    args.add(thisVar);
    for (Expression a : that.arguments) {
      args.add(a.acceptVisitor(this));
    }

    storeInCurrentLval = null;
    Node call = callFunction(method, args.toArray(new Node[0]));
    minijava.ast.Type returnType = that.method.def.returnType;
    if (!returnType.equals(minijava.ast.Type.VOID)) {
      return unpackCallResult(call, storageModeForType(returnType));
    }
    return call;
  }

  @Nullable
  private Node callFunction(Entity func, Node[] args) {
    // A call node expects an address that it can call
    Node funcAddress = construction.newAddress(func);
    // the last argument is (according to the documentation) the type of the called procedure
    Node call =
        construction.newCall(construction.getCurrentMem(), funcAddress, args, func.getType());
    // Set a new memory dependency for the result
    construction.setCurrentMem(construction.newProj(call, Mode.getM(), Call.pnM));
    // unpacking the result needs to be done separately with `unpackCallResult`
    return call;
  }

  private Node unpackCallResult(Node call, Mode mode) {
    // a method returns a tuple
    Node resultTuple = construction.newProj(call, Mode.getT(), Call.pnTResult);
    // at index 0 this tuple contains the result
    return construction.newProj(resultTuple, mode, 0);
  }

  private Node visitSystemOutPrintln(Expression argument) {
    return callFunction(PRINT_INT, new Node[] {argument.acceptVisitor(this)});
  }

  @Override
  public Node visitFieldAccess(Expression.FieldAccess that) {
    // This produces an lval
    Node self = that.self.acceptVisitor(this);
    Entity field = fields.get(that.field.def);
    Node absOffset = construction.newMember(self, field);

    // We store val at the absOffset
    storeInCurrentLval = (Node val) -> store(absOffset, val);

    return load(absOffset, field.getType().getMode());
  }

  @Override
  public Node visitArrayAccess(Expression.ArrayAccess that) {
    Node array = that.array.acceptVisitor(this);
    Node index = that.index.acceptVisitor(this);
    minijava.ast.Type arrayType = that.array.type;
    minijava.ast.Type elementType =
        new minijava.ast.Type(arrayType.basicType, arrayType.dimension - 1, arrayType.range());

    Node address = construction.newSel(array, index, new ArrayType(visitType(elementType), 0));

    // We store val at the absOffset
    storeInCurrentLval = (Node val) -> store(address, val);

    // Now just dereference the computed offset
    return load(address, storageModeForType(elementType));
  }

  private Node store(Node address, Node value) {
    Node store = construction.newStore(construction.getCurrentMem(), address, value);
    construction.setCurrentMem(construction.newProj(store, Mode.getM(), Store.pnM));
    return value;
  }

  private Node load(Node address, Mode mode) {
    Node load = construction.newLoad(construction.getCurrentMem(), address, mode);
    construction.setCurrentMem(construction.newProj(load, Mode.getM(), Load.pnM));
    return construction.newProj(load, mode, Load.pnRes);
  }

  @Override
  public Node visitNewObject(Expression.NewObject that) {
    Type type = visitType(that.type);
    storeInCurrentLval = null;
    Node num = construction.newConst(1, Mode.getP());
    Node size = construction.newSize(Mode.getIs(), type);
    return calloc(num, size);
  }

  @Override
  public Node visitNewArray(Expression.NewArray that) {
    // The number of array elements (the name clash is real)
    Node num = that.size.acceptVisitor(this);
    // The size of each array element. We can't just visit NewArray.elementType
    // because this will do the wrong thing for classes, namely returning the size of the
    // class. But in the class case we rather want to allocate an array of references.
    // The access mode is really what we want to query here.
    Type elementType = getStorageType(that.elementType);
    Node size = construction.newSize(Mode.getIs(), elementType);
    storeInCurrentLval = null;
    return calloc(num, size);
  }

  private Node calloc(Node num, Node size) {
    // calloc takes two parameters, for the number of elements and the size of each element.
    // both are of type size_t, so calloc expects them to be word sized. The best approximation
    // is to use the pointer (P) mode.
    // The fact that we called the array length size (which is parameter num to calloc) and
    // that here the element size is called size may be confusing, but whatever, I warned you.
    Node numNode = construction.newConv(num, Mode.getP());
    Node sizeNode = construction.newConv(size, Mode.getP());
    Node call = callFunction(CALLOC, new Node[] {numNode, sizeNode});
    return unpackCallResult(call, Mode.getP());
  }

  @Override
  public Node visitVariable(Expression.Variable that) {
    Mode mode = storageModeForType(that.type);
    // This will allocate a new index if necessary.
    int idx = getLocalVarIndex(that.var.def);
    storeInCurrentLval =
        (Node val) -> {
          construction.setVariable(idx, val);
          return val;
        };
    return construction.getVariable(idx, mode);
  }

  @Override
  public Node visitBooleanLiteral(Expression.BooleanLiteral that) {
    storeInCurrentLval = null;
    return construction.newConst(
        that.literal ? 1 : 0, storageModeForType(minijava.ast.Type.BOOLEAN));
  }

  @Override
  public Node visitIntegerLiteral(Expression.IntegerLiteral that) {
    // We handled the 0x80000000 case while visiting the unary minus
    int lit = Integer.parseInt(that.literal);
    storeInCurrentLval = null;
    return construction.newConst(lit, storageModeForType(minijava.ast.Type.INT));
  }

  @Override
  public Node visitReferenceTypeLiteral(Expression.ReferenceTypeLiteral that) {
    storeInCurrentLval = null;
    switch (that.name()) {
      case "this":
        // access parameter 0 as a pointer, that's where this is to be found
        return construction.getVariable(0, Mode.getP());
      case "null":
        return construction.newConst(0, Mode.getP());
      case "System.out":
        // we should have catched this earlier, in the resepective MethodCall.
        assert false;
        return null;
      default:
        throw new UnsupportedOperationException(); // This should be exhaustive.
    }
  }

  @Override
  public Void visitVariable(BlockStatement.Variable that) {
    int idx = getLocalVarIndex(that);
    if (that.rhs.isPresent()) {
      Node rhs = that.rhs.get().acceptVisitor(this);
      construction.setVariable(idx, rhs);
    }

    return null;
  }

  /**
   * This will do the mapping from local variables to their firm variable indices. It will compute
   * new indices as needed, so we need to process new variables in the exact order they should be
   * allocated. Although the exact mapping is rather an implementation detail. E.g. mapping the
   * first parameter to index 5 instead of index 1 isn't bad if we always use 5 when we refer to the
   * first parameter.
   *
   * <p>So, whenever computing variable indices, use this function.
   */
  private int getLocalVarIndex(LocalVariable var) {
    if (localVarIndexes.containsKey(var)) {
      return localVarIndexes.get(var);
    } else {
      // allocate a new index
      int free = localVarIndexes.size();
      localVarIndexes.put(var, free);
      return free;
    }
  }

  private static void lower() {
    for (Type type : Program.getTypes()) {
      if (type instanceof ClassType) {
        lowerClass((ClassType) type);
      }
    }
    Util.lowerSels();
  }

  /** Copied from the jFirm repo's Lower class */
  private static void lowerClass(ClassType cls) {
    for (int m = 0; m < cls.getNMembers(); /* nothing */ ) {
      Entity member = cls.getMember(m);
      Type type = member.getType();
      if (!(type instanceof MethodType)) {
        ++m;
        continue;
      }

      /* methods get implemented outside the class, move the entity */
      member.setOwner(Program.getGlobalType());
    }
  }

  private static void assemble(String outFile) throws IOException {
    /* based on BrainFuck.main */
    /* dump all firm graphs to disk */
    for (Graph g : Program.getGraphs()) {
      Dump.dumpGraph(g, "--finished");
    }
    /* use the amd64 backend */
    Backend.option("isa=amd64");
    /* transform to x86 assembler */
    Backend.createAssembler(String.format("%s.s", outFile), "<builtin>");
    /* assembler */

    File runtime = getRuntimeFile();

    Process p =
        Runtime.getRuntime()
            .exec(String.format("gcc %s %s.s -o %s", runtime.getAbsolutePath(), outFile, outFile));
    int c;
    while ((c = p.getErrorStream().read()) != -1) {
      System.out.print(Character.toString((char) c));
    }
    int res = -1;
    try {
      res = p.waitFor();
    } catch (Throwable t) {
    }
    if (res != 0) System.err.println("Warning: Linking step failed");
  }

  @NotNull
  private static File getRuntimeFile() throws IOException {
    File runtime = new File(Files.createTempDir(), "mj_runtime.c");
    runtime.deleteOnExit();
    InputStream s = ClassLoader.getSystemResourceAsStream("mj_runtime.c");
    if (s == null) {
      throw new RuntimeException("");
    }
    FileUtils.copyInputStreamToFile(s, runtime);
    return runtime;
  }

  public static void compile(minijava.ast.Program program, String outFile) throws IOException {
    IREmitter emitter = new IREmitter();
    emitter.visitProgram(program);
    for (Graph g : Program.getGraphs()) {
      //g.check();
      //binding_irgopt.remove_unreachable_code(g.ptr);
      //binding_irgopt.remove_bads(g.ptr);
    }

    lower();
    assemble(outFile);
  }

  public static void compileAndRun(minijava.ast.Program program, String outFile)
      throws IOException {
    compile(program, outFile);
    Process p = Runtime.getRuntime().exec("./" + outFile);
    int c;
    while ((c = p.getInputStream().read()) != -1) {
      System.out.print(Character.toString((char) c));
    }
    int res = -1;
    try {
      res = p.waitFor();
    } catch (Throwable t) {
    }
  }
}
