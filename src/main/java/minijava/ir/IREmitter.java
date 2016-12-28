package minijava.ir;

import static minijava.ir.Types.*;

import com.google.common.io.Files;
import com.sun.jna.Pointer;
import firm.*;
import firm.Program;
import firm.Type;
import firm.bindings.binding_ircons;
import firm.bindings.binding_lowering;
import firm.nodes.*;
import firm.nodes.Block;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.BiFunction;
import minijava.ast.*;
import minijava.ast.Class;
import minijava.util.SourceRange;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.function.Function4;

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
  /** The Basic block graph of the current function. */
  private Graph graph;
  /**
   * Construction is a firm Node factory that makes sure that we don't duplicate expressions, thus
   * making common sub expressions irrepresentable.
   */
  private Construction construction;

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
    lower();
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
        Node ret = construction.newReturn(construction.getCurrentMem(), new Node[0]);
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
      if (construction.isUnreachable()) {
        // Why bother generating any more code?
        break;
      }
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
    firm.nodes.Block trueBlock = construction.newBlock();
    firm.nodes.Block afterElse = construction.newBlock();
    firm.nodes.Block falseBlock = that.else_.isPresent() ? construction.newBlock() : afterElse;

    Node condition = that.condition.acceptVisitor(this);
    Node cond = construction.newCond(condition);
    trueBlock.addPred(construction.newProj(cond, Mode.getX(), Cond.pnTrue));
    falseBlock.addPred(construction.newProj(cond, Mode.getX(), Cond.pnFalse));

    // code in true block
    trueBlock.mature();
    construction.setCurrentBlock(trueBlock);
    that.then.acceptVisitor(this);

    // Only add a jump if this is reachable from control flow
    // E.g., don't add a jump if the last statement was a return.
    if (!construction.isUnreachable()) {
      afterElse.addPred(construction.newJmp());
    }

    // code in false block
    if (that.else_.isPresent()) {
      falseBlock.mature();
      construction.setCurrentBlock(falseBlock);
      that.else_.get().acceptVisitor(this);
      if (!construction.isUnreachable()) {
        afterElse.addPred(construction.newJmp());
      }
    }

    construction.setCurrentBlock(afterElse);
    afterElse.mature(); // Should we really do this?
    return null;
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
    Node condition = that.condition.acceptVisitor(this);
    Node cond = construction.newCond(condition);
    bodyBlock.addPred(construction.newProj(cond, Mode.getX(), Cond.pnTrue));
    endBlock.addPred(construction.newProj(cond, Mode.getX(), Cond.pnFalse));
    bodyBlock.mature();
    endBlock.mature();

    // code in body
    construction.setCurrentBlock(bodyBlock);
    that.body.acceptVisitor(this);
    // jump back to the loop condition
    if (!construction.isUnreachable()) {
      conditionBlock.addPred(construction.newJmp());
    }
    conditionBlock.mature();

    construction.setCurrentBlock(endBlock);

    graph.keepAlive(conditionBlock); // Also in the slides
    return null;
  }

  @Override
  public Void visitReturn(Statement.Return that) {
    Node[] retVals = {};
    if (that.expression.isPresent()) {
      retVals = new Node[] {convbToBu(that.expression.get().acceptVisitor(this))};
    }

    Node ret = construction.newReturn(construction.getCurrentMem(), retVals);
    // Judging from other examples, we don't need to set currentmem here.
    graph.getEndBlock().addPred(ret);

    // No code should follow a return statement.
    construction.setUnreachable();

    return null;
  }

  public Node visitBinaryOperator(Expression.BinaryOperator that) {
    switch (that.op) {
      case ASSIGN:
        return assign(that.left, that.right);
      case PLUS:
        return arithmeticOperator(that, construction::newAdd);
      case MINUS:
        return arithmeticOperator(that, construction::newSub);
      case MULTIPLY:
        return arithmeticOperator(that, construction::newMul);
      case DIVIDE:
        return divOrMod(that, construction::newDiv);
      case MODULO:
        return divOrMod(that, construction::newMod);
      case AND:
        return shortciruit(that);
      case OR:
        return shortciruit(that);
      case LT:
        return compareWithRelation(that, Relation.Less);
      case LEQ:
        return compareWithRelation(that, Relation.LessEqual);
      case GT:
        return compareWithRelation(that, Relation.Greater);
      case GEQ:
        return compareWithRelation(that, Relation.GreaterEqual);
      case EQ:
        return compareWithRelation(that, Relation.Equal);
      case NEQ:
        return booleanNot(compareWithRelation(that, Relation.Equal));
      default:
        throw new UnsupportedOperationException();
    }
  }

  /**
   * Stores the value of `rhs` into the address of `lval`. `lval` must be an expression which has an
   * address for that, e.g. be an lval.
   */
  private Node assign(Expression lval, Expression rhs) {
    // This is ugly, as we have to calculate the address of the expression of lval.
    if (lval instanceof Expression.Variable) {
      Expression.Variable use = (Expression.Variable) lval;
      int idx = getLocalVarIndex(use.var.def);
      Node value = rhs.acceptVisitor(this);
      construction.setVariable(idx, convbToBu(value));
      return value;
    } else if (lval instanceof Expression.FieldAccess) {
      Expression.FieldAccess access = (Expression.FieldAccess) lval;
      Node address = calculateOffsetForAccess(access);
      Node value = rhs.acceptVisitor(this);
      return store(address, value);
    } else if (lval instanceof Expression.ArrayAccess) {
      Expression.ArrayAccess access = (Expression.ArrayAccess) lval;
      Node address = calculateOffsetOfAccess(access);
      Node value = rhs.acceptVisitor(this);
      return store(address, value);
    }
    assert false;
    throw new UnsupportedOperationException("This should be caught in semantic analysis.");
  }

  private Node divOrMod(
      Expression.BinaryOperator that,
      Function4<Node, Node, Node, binding_ircons.op_pin_state, Node> newOp) {
    // A `div` or `mod` operation results in an element of the divmod tuple in memory
    // We convert the values from int to long, to prevent the INT_MIN / -1 exception
    // This shouldn't make any difference performance wise on 64 bit systems
    Node leftConv = construction.newConv(that.left.acceptVisitor(this), Mode.getLs());
    Node rightConv = construction.newConv(that.right.acceptVisitor(this), Mode.getLs());
    Node divOrMod =
        newOp.apply(
            construction.getCurrentMem(),
            leftConv,
            rightConv,
            binding_ircons.op_pin_state.op_pin_state_pinned);
    // Fetch the result from memory
    assert Div.pnRes == Mod.pnRes;
    Node retProj = construction.newProj(divOrMod, Mode.getLs(), Div.pnRes);
    // Convert it back to int
    return construction.newConv(retProj, Mode.getIs());
  }

  private Node arithmeticOperator(Expression.BinaryOperator that, BiFunction<Node, Node, Node> op) {
    return op.apply(that.left.acceptVisitor(this), that.right.acceptVisitor(this));
  }

  private Node shortciruit(Expression.BinaryOperator that) {
    Block endBlock = construction.newBlock();
    Block rightBlock = construction.newBlock();

    Node left = that.left.acceptVisitor(this);
    Node cond = construction.newCond(left);

    if (that.op == Expression.BinOp.AND) {
      // We shortcircuit if the left expression is False
      endBlock.addPred(construction.newProj(cond, Mode.getX(), Cond.pnFalse));
      rightBlock.addPred(construction.newProj(cond, Mode.getX(), Cond.pnTrue));
    } else {
      assert that.op == Expression.BinOp.OR;
      // We shortcircuit if the left expression is True
      endBlock.addPred(construction.newProj(cond, Mode.getX(), Cond.pnTrue));
      rightBlock.addPred(construction.newProj(cond, Mode.getX(), Cond.pnFalse));
    }
    rightBlock.mature();

    construction.setCurrentBlock(rightBlock);
    Node leftMem = construction.getCurrentMem();
    Node right = that.right.acceptVisitor(this);
    Node rightMem = construction.getCurrentMem();

    endBlock.addPred(construction.newJmp());
    endBlock.mature();
    construction.setCurrentBlock(endBlock);

    // The return value is either the value of the left expression or
    // that of the right, depending on control flow.
    construction.setCurrentMem(construction.newPhi(new Node[] {leftMem, rightMem}, Mode.getM()));
    return construction.newPhi(new Node[] {left, right}, Mode.getb());
  }

  private Node compareWithRelation(Expression.BinaryOperator binOp, Relation relation) {
    Node lhs = binOp.left.acceptVisitor(this);
    Node rhs = binOp.right.acceptVisitor(this);
    return construction.newCmp(convbToBu(lhs), convbToBu(rhs), relation);
  }

  private Node booleanNot(Node expression) {
    Node trueNode = construction.newConst(TargetValue.getBTrue());
    Node falseNode = construction.newConst(TargetValue.getBFalse());
    return construction.newMux(expression, trueNode, falseNode);
  }

  @Override
  public Node visitUnaryOperator(Expression.UnaryOperator that) {
    if (that.op == Expression.UnOp.NEGATE && that.expression instanceof Expression.IntegerLiteral) {
      // treat this case special in case the integer literal is 2147483648 (doesn't fit in int)
      int lit = Integer.parseInt("-" + ((Expression.IntegerLiteral) that.expression).literal);
      return construction.newConst(lit, storageModeForType(minijava.ast.Type.INT));
    }
    Node expression = that.expression.acceptVisitor(this);
    // This can never produce an lval (an assignable expression)
    switch (that.op) {
      case NEGATE:
        return construction.newMinus(expression);
      case NOT:
        return booleanNot(expression);
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public Node visitMethodCall(Expression.MethodCall that) {
    if (that.self.type == minijava.ast.Type.SYSTEM_OUT) {
      if (that.method.name().equals("println")) {
        return callFunction(PRINT_INT, new Node[] {that.arguments.get(0).acceptVisitor(this)});
      }
      if (that.method.name().equals("write")) {
        return callFunction(WRITE_INT, new Node[] {that.arguments.get(0).acceptVisitor(this)});
      } else if (that.method.name().equals("flush")) {
        return callFunction(FLUSH, new Node[] {});
      }
    } else if (that.self.type == minijava.ast.Type.SYSTEM_IN) {
      if (that.method.name.equals("read")) {
        return unpackCallResult(callFunction(READ_INT, new Node[] {}), INT_TYPE.getMode());
      }
    }

    Entity method = methods.get(that.method.def);

    List<Node> args = new ArrayList<>(that.arguments.size() + 1);
    // first argument is always this (static calls are disallowed)
    // this == that.self (X at `X.METHOD()`)
    Node thisVar = that.self.acceptVisitor(this);
    args.add(thisVar);
    for (Expression a : that.arguments) {
      args.add(convbToBu(a.acceptVisitor(this)));
    }

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
    return convBuTob(construction.newProj(resultTuple, mode, 0));
  }

  @Override
  public Node visitFieldAccess(Expression.FieldAccess that) {
    // This produces an lval
    Node absOffset = calculateOffsetForAccess(that);

    // We store val at the absOffset

    return load(absOffset, storageModeForType(that.type));
  }

  private Node calculateOffsetForAccess(Expression.FieldAccess that) {
    Node self = that.self.acceptVisitor(this);
    Entity field = fields.get(that.field.def);
    return construction.newMember(self, field);
  }

  @Override
  public Node visitArrayAccess(Expression.ArrayAccess that) {
    Node address = calculateOffsetOfAccess(that);

    // We store val at the absOffset

    // Now just dereference the computed offset
    return load(address, storageModeForType(that.type));
  }

  private Node calculateOffsetOfAccess(Expression.ArrayAccess that) {
    Node array = that.array.acceptVisitor(this);
    Node index = that.index.acceptVisitor(this);
    return construction.newSel(array, index, new ArrayType(visitType(that.type), 0));
  }

  private Node store(Node address, Node value) {
    Node store = construction.newStore(construction.getCurrentMem(), address, convbToBu(value));
    construction.setCurrentMem(construction.newProj(store, Mode.getM(), Store.pnM));
    return value;
  }

  private Node load(Node address, Mode mode) {
    Node load = construction.newLoad(construction.getCurrentMem(), address, mode);
    construction.setCurrentMem(construction.newProj(load, Mode.getM(), Load.pnM));
    return convBuTob(construction.newProj(load, mode, Load.pnRes));
  }

  @Override
  public Node visitNewObject(Expression.NewObject that) {
    Type type = visitType(that.type);
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
    return convBuTob(construction.getVariable(idx, mode));
  }

  /**
   * Converts a node of mode b to one of mode Bu, by converting the False case to 0 and the True
   * case to 1.
   */
  private Node convbToBu(Node expression) {
    if (expression.getMode().equals(Mode.getb())) {
      Node zero = construction.newConst(0, Mode.getBu());
      Node one = construction.newConst(1, Mode.getBu());
      return construction.newMux(expression, zero, one);
    }
    return expression;
  }

  /** Converts a node of mode Bu to one of mode b, by val > 0. */
  private Node convBuTob(Node expression) {
    if (expression.getMode().equals(Mode.getBu())) {
      Node zero = construction.newConst(0, Mode.getBu());
      return construction.newCmp(expression, zero, Relation.Greater);
    }
    return expression;
  }

  @Override
  public Node visitBooleanLiteral(Expression.BooleanLiteral that) {
    return construction.newConst(that.literal ? TargetValue.getBTrue() : TargetValue.getBFalse());
  }

  @Override
  public Node visitIntegerLiteral(Expression.IntegerLiteral that) {
    // We handled the 0x80000000 case while visiting the unary minus
    int lit = Integer.parseInt(that.literal);
    return construction.newConst(lit, storageModeForType(minijava.ast.Type.INT));
  }

  @Override
  public Node visitReferenceTypeLiteral(Expression.ReferenceTypeLiteral that) {
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
      construction.setVariable(idx, convbToBu(rhs));
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
    for (Graph g : Program.getGraphs()) {
      // Passing NULL as the callback will lower all Mux nodes
      // TODO: Move this into jFirm
      binding_lowering.lower_mux(g.ptr, Pointer.NULL);
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

    boolean useGC =
        System.getenv().containsKey("MJ_USE_GC") && System.getenv("MJ_USE_GC").equals("1");

    String gcApp = "";
    if (useGC) {
      gcApp = " -DUSE_GC -lgc ";
    }
    String cmd =
        String.format("gcc %s %s.s -o %s %s", runtime.getAbsolutePath(), outFile, outFile, gcApp);
    Process p = Runtime.getRuntime().exec(cmd);
    int c;
    while ((c = p.getErrorStream().read()) != -1) {
      System.out.print(Character.toString((char) c));
    }
    int res = -1;
    try {
      res = p.waitFor();
    } catch (Throwable t) {
    }
    if (res != 0) {
      System.err.println("Warning: Linking step failed");
      System.exit(1);
    }
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

  public static void compile(String outFile) throws IOException {
    for (Graph g : Program.getGraphs()) {
      //g.check();
      //binding_irgopt.remove_unreachable_code(g.ptr);
      //binding_irgopt.remove_bads(g.ptr);
    }
    assemble(outFile);
  }

  public static void compileAndRun(String outFile) throws IOException {
    compile(outFile);
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
