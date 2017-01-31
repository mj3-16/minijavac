package minijava.ir.emit;

import static firm.bindings.binding_irgraph.ir_resources_t.IR_RESOURCE_IRN_LINK;
import static org.jooq.lambda.Seq.seq;

import firm.ClassType;
import firm.Construction;
import firm.Entity;
import firm.Graph;
import firm.MethodType;
import firm.Mode;
import firm.Program;
import firm.Relation;
import firm.TargetValue;
import firm.Type;
import firm.bindings.binding_ircons;
import firm.nodes.Block;
import firm.nodes.Call;
import firm.nodes.Cond;
import firm.nodes.Div;
import firm.nodes.Jmp;
import firm.nodes.Load;
import firm.nodes.Mod;
import firm.nodes.Node;
import firm.nodes.Proj;
import firm.nodes.Store;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.BiFunction;
import minijava.Cli;
import minijava.ast.BlockStatement;
import minijava.ast.Class;
import minijava.ast.Expression;
import minijava.ast.Field;
import minijava.ast.LocalVariable;
import minijava.ast.Method;
import minijava.ast.Ref;
import minijava.ast.Statement;
import minijava.ir.InitFirm;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.NodeUtils;
import minijava.util.SourceRange;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.function.Function4;

/** Emits an intermediate representation for a given minijava Program. */
public class IREmitter
    implements minijava.ast.Program.Visitor<Void>,
        minijava.ast.Block.Visitor<Void>,
        Expression.Visitor<ExpressionIR>,
        BlockStatement.Visitor<Void> {

  private final IdentityHashMap<Class, ClassType> classTypes = new IdentityHashMap<>();
  private final IdentityHashMap<Method, Entity> methods = new IdentityHashMap<>();
  private final IdentityHashMap<Field, Entity> fields = new IdentityHashMap<>();

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
    for (Type type : Program.getTypes()) {
      if (type instanceof ClassType) {
        lowerClass((ClassType) type);
      }
    }
    return null;
  }

  private void emitBody(Method m) {
    if (m.isNative) {
      return;
    }
    // graph and construction are irrelevant to anything before or after.
    // It's more like 2 additional parameters to the visitor.

    graph = constructEmptyGraphFromPrototype(m);
    GraphUtils.reserveResource(graph, IR_RESOURCE_IRN_LINK);
    construction = new Construction(graph);

    localVarIndexes.clear();

    if (!m.isStatic) {
      connectParametersToIRVariables(m);
    }

    m.body.acceptVisitor(this);

    finishGraphAndHandleFallThrough(m);
    Cli.dumpGraphIfNeeded(graph, "after-construction");
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
    Type fakeThisStorageType = storageType(fakeThisType);
    construction.setVariable(
        thisIdx, construction.newProj(args, fakeThisStorageType.getMode(), thisIdx));
    NodeUtils.setLink(
        construction.getVariable(thisIdx, fakeThisStorageType.getMode()), fakeThisStorageType.ptr);

    for (LocalVariable p : that.parameters) {
      // we just made this connection in the loop above
      // Also effectively this should just count up.
      // Also note that we are never trying to access this or the
      int idx = getLocalVarIndex(p);
      // Where is this documented anyway? SimpleIf seems to be the only reference...
      Type parameterType = storageType(p.type);
      Node param = construction.newProj(args, parameterType.getMode(), idx);
      NodeUtils.setLink(param, parameterType.ptr);
      construction.setVariable(idx, param);
    }
  }

  private Type storageType(minijava.ast.Type type) {
    return Types.storageType(type, classTypes);
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
      }
      // we don't have to handle the other case
      // as the semantic linter already took care
    }

    construction.setCurrentBlock(graph.getEndBlock());
    construction.finish();
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
    ControlFlowProjs condition = that.condition.acceptVisitor(this).asControlFlow();
    Block afterElse = construction.newBlock();
    Block trueBlock = newLandingBlock(condition.true_);
    Block falseBlock = newLandingBlock(condition.false_);

    // code in true block
    construction.setCurrentBlock(trueBlock);
    that.then.acceptVisitor(this);

    // Only add a jump if this is reachable from control flow
    // E.g., don't add a jump if the last statement was a return.
    if (!construction.isUnreachable()) {
      afterElse.addPred(construction.newJmp());
    }

    // code in false block
    construction.setCurrentBlock(falseBlock);
    that.else_.ifPresent(statement -> statement.acceptVisitor(this));
    if (!construction.isUnreachable()) {
      afterElse.addPred(construction.newJmp());
    }

    construction.setCurrentBlock(afterElse);
    afterElse.mature();
    return null;
  }

  private Jmp newJmpBlock(Node pred) {
    Block block = construction.newBlock();
    block.addPred(pred);
    block.mature();
    construction.setCurrentBlock(block);
    return (Jmp) construction.newJmp();
  }

  /**
   * This constructs a new block with the given predecessors, while avoiding hideous critical edges
   * through inserting intermediate jump blocks as needed.
   */
  private Block newLandingBlock(Iterable<? extends Node> preds) {
    Block landingBlock = construction.newBlock();
    seq(preds).map(this::newJmpBlock).forEach(landingBlock::addPred);
    landingBlock.mature();
    return landingBlock;
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

    Block conditionBlock = construction.newBlock();

    // code for the condition
    conditionBlock.addPred(endStart);
    construction.setCurrentBlock(conditionBlock);
    construction.getCurrentMem(); // See the slides, we need those PhiM nodes
    ControlFlowProjs condition = that.condition.acceptVisitor(this).asControlFlow();
    Block bodyBlock = newLandingBlock(condition.true_);
    Block endBlock = newLandingBlock(condition.false_);

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
      retVals = new Node[] {convControlFlowToBu(that.expression.get().acceptVisitor(this))};
    }

    Node ret = construction.newReturn(construction.getCurrentMem(), retVals);
    // Judging from other examples, we don't need to set currentmem here.
    graph.getEndBlock().addPred(ret);

    // No code should follow a return statement.
    construction.setUnreachable();

    return null;
  }

  public ExpressionIR visitBinaryOperator(Expression.BinaryOperator that) {
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
        return compareWithRelation(that, Relation.LessGreater);
      default:
        throw new UnsupportedOperationException();
    }
  }

  /**
   * Stores the value of `rhs` into the address of `lval`. `lval` must be an expression which has an
   * address for that, e.g. be an lval.
   */
  private ExpressionIR assign(Expression lval, Expression rhs) {
    // This is ugly, as we have to calculate the address of the expression of lval.
    if (lval instanceof Expression.Variable) {
      Expression.Variable use = (Expression.Variable) lval;
      int idx = getLocalVarIndex(use.var.def);
      ExpressionIR value = rhs.acceptVisitor(this);
      Node assignedValue = convControlFlowToBu(value);
      construction.setVariable(idx, assignedValue);
      return convBuToControlFlow(assignedValue);
    } else if (lval instanceof Expression.FieldAccess) {
      Expression.FieldAccess access = (Expression.FieldAccess) lval;
      Node address = calculateOffsetForAccess(access);
      ExpressionIR value = rhs.acceptVisitor(this);
      return store(address, value);
    } else if (lval instanceof Expression.ArrayAccess) {
      Expression.ArrayAccess access = (Expression.ArrayAccess) lval;
      Node address = calculateOffsetOfAccess(access);
      ExpressionIR value = rhs.acceptVisitor(this);
      return store(address, value);
    }
    assert false;
    throw new UnsupportedOperationException("This should be caught in semantic analysis.");
  }

  private ExpressionIR divOrMod(
      Expression.BinaryOperator that,
      Function4<Node, Node, Node, binding_ircons.op_pin_state, Node> newOp) {
    // A `div` or `mod` operation results in an element of the divmod tuple in memory
    // We convert the values from int to long, to prevent the INT_MIN / -1 exception
    // This shouldn't make any difference performance wise on 64 bit systems
    Node leftConv = construction.newConv(that.left.acceptVisitor(this).asValue(), Mode.getLs());
    Node rightConv = construction.newConv(that.right.acceptVisitor(this).asValue(), Mode.getLs());
    Node divOrMod =
        newOp.apply(
            construction.getCurrentMem(),
            leftConv,
            rightConv,
            binding_ircons.op_pin_state.op_pin_state_pinned);
    // Fetch the result from memory
    assert Div.pnRes == Mod.pnRes;
    assert Div.pnM == Mod.pnM;
    Node retProj = construction.newProj(divOrMod, Mode.getLs(), Div.pnRes);
    // Division by zero might overflow, which is a side effect we have to track.
    // However since we consider that undefined behavior, we can ignore the side effect.
    // This is pretty much like unsafeInterleaveIO in Haskell works.
    //construction.setCurrentMem(construction.newProj(divOrMod, Mode.getM(), Div.pnM));
    // Convert it back to int
    return ExpressionIR.fromValue(construction.newConv(retProj, Mode.getIs()));
  }

  private ExpressionIR arithmeticOperator(
      Expression.BinaryOperator that, BiFunction<Node, Node, Node> op) {
    Node left = that.left.acceptVisitor(this).asValue();
    Node right = that.right.acceptVisitor(this).asValue();
    return ExpressionIR.fromValue(op.apply(left, right));
  }

  private ExpressionIR shortciruit(Expression.BinaryOperator that) {
    ControlFlowProjs left = that.left.acceptVisitor(this).asControlFlow();
    construction.getCurrentMem();

    Block rightBlock;
    if (that.op == Expression.BinOp.AND) {
      rightBlock = newLandingBlock(left.true_);
    } else {
      assert that.op == Expression.BinOp.OR;
      rightBlock = newLandingBlock(left.false_);
    }

    construction.setCurrentBlock(rightBlock);
    ControlFlowProjs right = that.right.acceptVisitor(this).asControlFlow();
    ControlFlowProjs result =
        that.op == Expression.BinOp.AND
            ? right.addFalseJmps(left.false_) // We shortcircuit if the left expression is False
            : right.addTrueJmps(left.true_); // We shortcircuit if the left expression is True

    return ExpressionIR.fromControlFlow(result);
  }

  private ExpressionIR compareWithRelation(Expression.BinaryOperator binOp, Relation relation) {
    // We immediately convert the control flow to a byte. This will make one additional jmp,
    // but will never duplicate code/evaluated expressions.
    // E.g.: (this != that) == (expensive() == 5) will first evaluate the lhs, convert it to a byte
    // value (1 jmp), then do the same for the rhs (2 jmps), and finally will compare both bytes
    // and convert that into control flow accordingly (3 jmps).
    // In theory we could evaluate `this`, `that`, `expensive()` and `5` in this block, then
    // make the `!=` comparison. Then we duplicate the right `==` comparison into both target branches,
    // after which we have switched up preds.
    // As this is rather complicated, we leave it open for the optimizer as a reeeeeally low priority.
    Node lhs = convControlFlowToBu(binOp.left.acceptVisitor(this));
    Node rhs = convControlFlowToBu(binOp.right.acceptVisitor(this));
    return convbToControlFlow(construction.newCmp(lhs, rhs, relation));
  }

  private ControlFlowProjs booleanNot(ControlFlowProjs expression) {
    return new ControlFlowProjs(expression.false_, expression.true_);
  }

  @Override
  public ExpressionIR visitUnaryOperator(Expression.UnaryOperator that) {
    if (that.op == Expression.UnOp.NEGATE && that.expression instanceof Expression.IntegerLiteral) {
      // treat this case special in case the integer literal is 2147483648 (doesn't fit in int)
      int lit = Integer.parseInt("-" + ((Expression.IntegerLiteral) that.expression).literal);
      Node node = construction.newConst(lit, storageType(minijava.ast.Type.INT).getMode());
      return ExpressionIR.fromValue(node);
    }
    ExpressionIR expression = that.expression.acceptVisitor(this);
    // This can never produce an lval (an assignable expression)
    switch (that.op) {
      case NEGATE:
        return ExpressionIR.fromValue(construction.newMinus(expression.asValue()));
      case NOT:
        return ExpressionIR.fromControlFlow(booleanNot(expression.asControlFlow()));
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public ExpressionIR visitMethodCall(Expression.MethodCall that) {
    if (that.self.type == minijava.ast.Type.SYSTEM_OUT) {
      switch (that.method.name()) {
        case "println":
          callFunction(
              Types.PRINT_INT, new Node[] {that.arguments.get(0).acceptVisitor(this).asValue()});
          return null;
        case "write":
          callFunction(
              Types.WRITE_INT, new Node[] {that.arguments.get(0).acceptVisitor(this).asValue()});
          return null;
        case "flush":
          callFunction(Types.FLUSH, new Node[] {});
          return null;
        default:
          throw new UnsupportedOperationException("System.out." + that.method.name());
      }
    } else if (that.self.type == minijava.ast.Type.SYSTEM_IN) {
      if (that.method.name.equals("read")) {
        return unpackCallResult(callFunction(Types.READ_INT, new Node[] {}), Types.INT_TYPE);
      } else {
        throw new UnsupportedOperationException("System.in." + that.method.name());
      }
    }

    Entity method = methods.get(that.method.def);

    List<Node> args = new ArrayList<>(that.arguments.size() + 1);
    // first argument is always this (static calls are disallowed)
    // this == that.self (X at `X.METHOD()`)
    Node thisVar = that.self.acceptVisitor(this).asValue();
    args.add(thisVar);
    for (Expression a : that.arguments) {
      args.add(convControlFlowToBu(a.acceptVisitor(this)));
    }

    Node call = callFunction(method, args.toArray(new Node[0]));
    minijava.ast.Type returnType = that.method.def.returnType;
    if (!returnType.equals(minijava.ast.Type.VOID)) {
      return unpackCallResult(call, storageType(returnType));
    }
    return null;
  }

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

  private ExpressionIR unpackCallResult(Node call, Type storageType) {
    // a method returns a tuple
    Node resultTuple = construction.newProj(call, Mode.getT(), Call.pnTResult);
    // at index 0 this tuple contains the result
    Node result = construction.newProj(resultTuple, storageType.getMode(), 0);
    // The precise storageType is needed for alias analyis.
    NodeUtils.setLink(result, storageType.ptr);
    return convBuToControlFlow(result);
  }

  @Override
  public ExpressionIR visitFieldAccess(Expression.FieldAccess that) {
    // This produces an lval
    Node absOffset = calculateOffsetForAccess(that);

    // We store val at the absOffset

    return load(absOffset, storageType(that.type).getMode());
  }

  private Node calculateOffsetForAccess(Expression.FieldAccess that) {
    Node self = that.self.acceptVisitor(this).asValue();
    Entity field = fields.get(that.field.def);
    return construction.newMember(self, field);
  }

  @Override
  public ExpressionIR visitArrayAccess(Expression.ArrayAccess that) {
    Node address = calculateOffsetOfAccess(that);

    // We store val at the absOffset

    // Now just dereference the computed offset
    return load(address, storageType(that.type).getMode());
  }

  private Node calculateOffsetOfAccess(Expression.ArrayAccess that) {
    Node array = that.array.acceptVisitor(this).asValue();
    Node index = that.index.acceptVisitor(this).asValue();
    Type elementType = storageType(that.type);
    return construction.newSel(array, index, Types.arrayOf(elementType));
  }

  private ExpressionIR store(Node address, ExpressionIR value) {
    Node asValue = convControlFlowToBu(value);
    Node store = construction.newStore(construction.getCurrentMem(), address, asValue);
    construction.setCurrentMem(construction.newProj(store, Mode.getM(), Store.pnM));
    return convBuToControlFlow(asValue);
  }

  private ExpressionIR load(Node address, Mode mode) {
    Node load = construction.newLoad(construction.getCurrentMem(), address, mode);
    construction.setCurrentMem(construction.newProj(load, Mode.getM(), Load.pnM));
    return convBuToControlFlow(construction.newProj(load, mode, Load.pnRes));
  }

  @Override
  public ExpressionIR visitNewObject(Expression.NewObject that) {
    Type type = classTypes.get(that.type.basicType.def); // Only class types can be new allocated
    Node num = construction.newConst(1, Mode.getP());
    Node size = construction.newSize(Mode.getIs(), type);
    return calloc(num, size, storageType(that.type));
  }

  @Override
  public ExpressionIR visitNewArray(Expression.NewArray that) {
    // The number of array elements (the name clash is real)
    Node num = that.size.acceptVisitor(this).asValue();
    // The size of each array element. We can't just visit NewArray.elementType
    // because this will do the wrong thing for classes, namely returning the size of the
    // class. But in the class case we rather want to allocate an array of references.
    // The access mode is really what we want to query here.
    Type elementType = storageType(that.elementType);
    Node size = construction.newSize(Mode.getIs(), elementType);
    return calloc(num, size, storageType(that.type));
  }

  private ExpressionIR calloc(Node num, Node size, Type storageType) {
    // calloc takes two parameters, for the number of elements and the size of each element.
    // both are of type size_t, so calloc expects them to be word sized. The best approximation
    // is to use the pointer (P) mode.
    // The fact that we called the array length size (which is parameter num to calloc) and
    // that here the element size is called size may be confusing, but whatever, I warned you.
    Node numNode = construction.newConv(num, Mode.getP());
    Node castSize = construction.newConv(size, Mode.getP());
    Node call = callFunction(Types.CALLOC, new Node[] {numNode, castSize});
    return unpackCallResult(call, storageType);
  }

  @Override
  public ExpressionIR visitVariable(Expression.Variable that) {
    Mode mode = storageType(that.type).getMode();
    // This will allocate a new index if necessary.
    int idx = getLocalVarIndex(that.var.def);
    return convBuToControlFlow(construction.getVariable(idx, mode));
  }

  /**
   * Converts control flow ExpressionIR to Bu, by converting the False case to 0 and the True case
   * to 1.
   */
  private Node convControlFlowToBu(ExpressionIR expression) {
    if (expression.isControlFlow()) {
      ControlFlowProjs projs = expression.asControlFlow();
      Block trueBlock = construction.newBlock();
      Block falseBlock = construction.newBlock();
      Block newBlock = construction.newBlock();

      // We need these intermediate blocks because we might introduce critical edges.
      // Some later step (Codegen probably) could optimize unnecessary jumps away.
      seq(projs.false_).map(this::newJmpBlock).forEach(falseBlock::addPred);
      seq(projs.true_).map(this::newJmpBlock).forEach(trueBlock::addPred);
      falseBlock.mature();
      trueBlock.mature();

      construction.setCurrentBlock(falseBlock);
      newBlock.addPred(construction.newJmp());
      construction.setCurrentBlock(trueBlock);
      newBlock.addPred(construction.newJmp());
      newBlock.mature();

      construction.setCurrentBlock(newBlock);
      Node zero = construction.newConst(0, Mode.getBu());
      Node one = construction.newConst(1, Mode.getBu());
      return construction.newPhi(new Node[] {zero, one}, Mode.getBu());
    }
    return expression.asValue();
  }

  /** Converts a node of mode Bu to control flow by a comparison and a conditional jump. */
  private ExpressionIR convBuToControlFlow(Node expression) {
    if (expression.getMode().equals(Mode.getBu())) {
      Node zero = construction.newConst(0, Mode.getBu());
      Node cmp = construction.newCmp(expression, zero, Relation.LessGreater);
      return convbToControlFlow(cmp);
    }
    return ExpressionIR.fromValue(expression);
  }

  @NotNull
  private ExpressionIR convbToControlFlow(Node node) {
    assert node.getMode().equals(Mode.getb());
    Node cond = construction.newCond(node);
    Proj false_ = (Proj) construction.newProj(cond, Mode.getX(), Cond.pnFalse);
    Proj true_ = (Proj) construction.newProj(cond, Mode.getX(), Cond.pnTrue);
    return ExpressionIR.fromControlFlow(true_, false_);
  }

  @Override
  public ExpressionIR visitBooleanLiteral(Expression.BooleanLiteral that) {
    Node sel =
        construction.newConst(that.literal ? TargetValue.getBTrue() : TargetValue.getBFalse());
    return convbToControlFlow(sel);
  }

  @Override
  public ExpressionIR visitIntegerLiteral(Expression.IntegerLiteral that) {
    // We handled the 0x80000000 case while visiting the unary minus
    int lit = Integer.parseInt(that.literal);
    Node node = construction.newConst(lit, storageType(minijava.ast.Type.INT).getMode());
    return ExpressionIR.fromValue(node);
  }

  @Override
  public ExpressionIR visitReferenceTypeLiteral(Expression.ReferenceTypeLiteral that) {
    switch (that.name()) {
      case "this":
        // access parameter 0 as a pointer, that's where this is to be found
        return ExpressionIR.fromValue(construction.getVariable(0, Mode.getP()));
      case "null":
        return ExpressionIR.fromValue(construction.newConst(0, Mode.getP()));
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
      ExpressionIR rhs = that.rhs.get().acceptVisitor(this);
      construction.setVariable(idx, convControlFlowToBu(rhs));
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
}
