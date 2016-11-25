package minijava.ir;

import com.beust.jcommander.internal.Nullable;
import firm.*;
import firm.Program;
import firm.Type;
import firm.bindings.binding_ircons;
import firm.nodes.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import minijava.ast.*;
import minijava.ast.Class;
import minijava.ast.Field;
import minijava.ast.Method;

/** Emits an intermediate representation for a given minijava Program. */
public class IREmitter
    implements minijava.ast.Program.Visitor,
        Class.Visitor,
        Field.Visitor,
        Method.Visitor,
        minijava.ast.Type.Visitor<Type>,
        minijava.ast.Block.Visitor<Integer>,
        Expression.Visitor<Node>,
        BlockStatement.Visitor<Integer> {

  private Logger log = Logger.getLogger("IREmitter");
  /**
   * TODO: delete this, irrelevant to the visiting logic
   */
  private minijava.ast.Program program;
  private HashMap<String, ClassType> classTypes = new HashMap<>();
  /**
   * Maps local variable definitions such as parameters and ... local variable definitions
   * to their assigned index. Which is a firm thing.
   */
  private Map<LocalVariable, Integer> localVarIndexes = new HashMap<>();
  /**
   * The Basic Block graph of the current function.
   */
  private Graph graph;
  /**
   * Construction is a firm Node factory that makes sure that we don't duplicate
   * expressions, thus making common sub expressions irrepresentable.
   */
  private Construction construction;
  /**
   * Stores the node's value in the current lvar (if there is any). This is crucial for assignment
   * to work. E.g. in the expression x = 5, we analyze the variable expression x and get back its
   * value, which is irrelevant for assignment, since we need the *address of* x. This is where
   * we need storeInCurrentLval, which would store the expression node of the RHS (evaluating to 5)
   * in the address of x.
   *
   * Now, in an ideal world, this variable would be 'Node currentLval', but firm doesn't offer
   * a function for getting the address of a local variable as a Node. So in order to not duplicate
   * a lot of work (e.g. computing array offsets, etc.) in a mechanism without this variable, we
   * abstract actual assignment out into a function.
   */
  @Nullable
  private Function<Node, Node> storeInCurrentLval;

  private final Type INT_TYPE;
  private final Type BOOLEAN_TYPE;

  public IREmitter(minijava.ast.Program program) {
    this.program = program;
    Firm.init();
    System.out.printf("Firm Version: %1s.%2s\n", Firm.getMajorVersion(), Firm.getMinorVersion());
    INT_TYPE = new PrimitiveType(Mode.getIs());
    BOOLEAN_TYPE = new PrimitiveType(Mode.getBu());
  }

  public void run(boolean outputGraphs) {
    visitProgram(program);
    if (outputGraphs) {
      for (Graph g : Program.getGraphs()) {
        g.check();
        Dump.dumpGraph(g, "");
      }
    }
  }

  public static void main(String[] main_args) {}

  @Override
  public Object visitProgram(minijava.ast.Program that) {
    for (Class klass : that.declarations) {
      klass.acceptVisitor(this);
    }
    return null;
  }

  @Override
  public Object visitClass(Class that) {
    for (Field field : that.fields) {
      field.acceptVisitor(this);
    }
    for (Method method : that.methods) {
      method.acceptVisitor(this);
    }
    return null;
  }

  @Override
  public Object visitField(Field that) {
    Type type = that.type.acceptVisitor(this);
    new Entity(klass(that.name()), that.name(), type);
    return null;
  }

  @Override
  public Object visitMethod(Method that) {
    ClassType definingClass = klass(that.definingClass.name());
    ArrayList<Type> parameters = new ArrayList<>();

    if (that.isStatic) {
      // main has this annoying void parameter hack. Let's compensate
      // for that.
      Type arrayOfString = ptrTo(ptrTo(new PrimitiveType(Mode.getBu())));
      parameters.add(arrayOfString);
    } else {
      // Add the this pointer. It's always parameter 0, so access will be trivial.
      parameters.add(ptrTo(definingClass));
      for (LocalVariable p : that.parameters) {
        // In the body, we need to refer to local variables by index, so we save that mapping.
        localVarIndexes.put(p, parameters.size());
        parameters.add(p.type.acceptVisitor(this));
      }
    }

    // The visitor returns null if that.returnType was void.
    Type returnType = that.returnType.acceptVisitor(this);
    Type[] returnTypes = returnType == null ? new Type[0] : new Type[] {returnType};

    Type methodType = new MethodType(parameters.toArray(new Type[0]), returnTypes);

    // TODO: We probably don't need to include the class name in the mangled name (yet).
    // We will when we desugar ClassType to structs and global functions, though.
    // We probably won't need to mangle names before lowering anyway.
    String mangledName = NameMangler.mangleMethodName(that.definingClass.name(), that.name());
    Entity methodEnt = new Entity(definingClass, mangledName, methodType);

    // So we got our method prototype. Now for the body

    int maxLocals =
        parameters.size() + that.body.acceptVisitor(new NumberOfLocalVariablesVisitor());

    // Start actual code creation
    graph = new Graph(methodEnt, maxLocals);
    construction = new Construction(graph);

    // We now need to make the connection between function parameters and local firm variables.
    // firm handles this variable stuff so that it can build up the SSA form later on.
    Node args = graph.getArgs();
    for (LocalVariable p : that.parameters) {
      // we just made this connection in the loop above
      // Also effectively this should just count up. But we are explicit
      // here that we want to make the connection between a parameter's
      // localVarIndex and actual local variable slots.
      int idx = localVarIndexes.get(p);
      // Where is this documented anyway? SimpleIf seems to be the only reference...
      Node param = construction.newProj(args, accessModeForType(p.type), idx);
      construction.setVariable(idx, param);
    }
    that.body.acceptVisitor(this);

    construction.setCurrentBlock(graph.getEndBlock());

    if (!construction.isUnreachable()) {
      // Add an implicit return statement at the end of the block,
      // iff we have return type void. In which case returnTypes has length 0.
      if (returnTypes.length == 0) {
        Node ret = construction.newReturn(construction.getCurrentMem(), new Node[0]);
        construction.setCurrentMem(ret);
        graph.getEndBlock().addPred(ret);
      } else {
        // We can't just conjure a return value of arbitrary type.
        // This must be caught by the semantic pass.
        assert false;
      }
    }

    construction.setUnreachable();
    construction.finish();

    // Clean up
    localVarIndexes.clear();
    return null;
  }

  @Override
  public Type visitType(minijava.ast.Type that) {
    if (that.basicType.name().equals("void")) {
      return null;
    }
    Type type;
    switch (that.basicType.name) {
      case "int":
        type = INT_TYPE;
        break;
      case "boolean":
        type = BOOLEAN_TYPE;
        break;
      default:
        type = klass(that.basicType.name());
    }
    for (int i = 0; i < that.dimension; i++) {
      type = new PointerType(type);
    }
    return type;
  }

  private Mode accessModeForType(minijava.ast.Type type) {
    if (type.dimension > 0) {
      return Mode.getP();
    }

    switch (type.basicType.name()) {
      case "int":
        return Mode.getIs();
      case "boolean":
        return Mode.getBu();
      default:
        return Mode.getP();
    }
  }

  private ClassType klass(String name) {
    if (classTypes.containsKey(name)) {
      return classTypes.get(name);
    } else {
      // we haven't accessed this class type yet,
      // so we assemble a new ClassType from its mangled name
      // TODO: I don't think we need to mangle names before the lowering step.
      ClassType classType = new ClassType(NameMangler.mangleClassName(name));
      classTypes.put(name, classType);
      return classType;
    }
  }

  private PointerType ptrTo(Type type) {
    return new PointerType(type);
  }

  @Override
  public Integer visitBlock(minijava.ast.Block that) {
    for (BlockStatement statement : that.statements) {
      statement.acceptVisitor(this);
    }
    return null;
  }

  @Override
  public Integer visitEmpty(Statement.Empty that) {
    return null;
  }

  @Override
  public Integer visitIf(Statement.If that) {
    // Evaluate condition and set the place for the condition result
    //that.condition.acceptVisitor(this);

    // next week...

    // Conditional Jump Node with the True+False Proj
    return null;
  }

  @Override
  public Integer visitExpressionStatement(Statement.ExpressionStatement that) {
    // We evaluate this just for the side effects, e.g. the memory edges this adds.
    that.expression.acceptVisitor(this);
    return null;
  }

  @Override
  public Integer visitWhile(Statement.While that) {
    // next week
    return null;
  }

  @Override
  public Integer visitReturn(Statement.Return that) {
    List<Node> retVals = new ArrayList<>(1);
    if (that.expression.isPresent()) {
      retVals.add(that.expression.get().acceptVisitor(this));
    }
    Node ret = construction.newReturn(construction.getCurrentMem(), retVals.toArray(new Node[0]));
    // TODO: do we need to setCurrentMem? If so, what if the return type is void?
    graph.getEndBlock().addPred(ret);

    // No code should follow a return statement.
    construction.setUnreachable();

    return null;
  }

  @Override
  public Node visitBinaryOperator(Expression.BinaryOperator that) {
    Node left =
        construction.newConv(that.left.acceptVisitor(this), accessModeForType(that.left.type));
    // Save the store emitter of the left expression (if there's one, e.g. iff it's an lval).
    // See the comments on storeInCurrentLval.
    Function<Node, Node> storeInLeft = storeInCurrentLval;
    assert storeInLeft != null; // This should be true after semantic analysis.
    Node right =
        construction.newConv(that.right.acceptVisitor(this), accessModeForType(that.right.type));

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
        return construction.newDiv(
            construction.getCurrentMem(),
            left,
            right,
            binding_ircons.op_pin_state.op_pin_state_exc_pinned);
      case MODULO:
        return construction.newMod(
            construction.getCurrentMem(),
            left,
            right,
            binding_ircons.op_pin_state.op_pin_state_exc_pinned);
      case OR:
        return construction.newOr(left, right);
      case AND:
        return construction.newAnd(left, right);
      case EQ:
        Node cmp = construction.newCmp(left, right, Relation.Equal);
        construction.new
        Cmp.
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
    Node expression = that.expression.acceptVisitor(this);

    // This can never produce an lval (an assignable expression)
    storeInCurrentLval = null;

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

    storeInCurrentLval = null;
    return null;
  }

  @Override
  public Node visitFieldAccess(Expression.FieldAccess that) {
    // This produces an lval
    // storeInCurrentLval = ...
    return null;
  }

  @Override
  public Node visitArrayAccess(Expression.ArrayAccess that) {
    Node array = that.array.acceptVisitor(this);
    Node index = that.index.acceptVisitor(this);
    minijava.ast.Type arrayType = that.array.type;
    minijava.ast.Type elementType =
        new minijava.ast.Type(arrayType.basicType, arrayType.dimension - 1, arrayType.range());
    int elementSize = sizeOf(elementType.acceptVisitor(this));

    Node sizeNode = construction.newConst(elementSize, accessModeForType(minijava.ast.Type.INT));
    Node relOffset = construction.newMul(sizeNode, index);
    Node absOffset = construction.newAdd(array, relOffset);
    Mode mode = accessModeForType(elementType);
    storeInCurrentLval = (Node val) -> {
      // We store val at the absOffset
      Node store = construction.newStore(construction.getCurrentMem(), absOffset, val);
      construction.setCurrentMem(construction.newProj(store, Mode.getM(), Store.pnM));
      return store;
    };

    // Now just dereference the computed offset
    Node load = construction.newLoad(construction.getCurrentMem(), absOffset, mode);
    construction.setCurrentMem(construction.newProj(load, Mode.getM(), Load.pnM));
    return construction.newProj(load, mode, Load.pnRes);
  }

  @Override
  public Node visitNewObject(Expression.NewObject that) {
    Type type = that.type.acceptVisitor(this);
    storeInCurrentLval = null;
    // See callocOfType for the rationale behind Mode.getP()
    return callocOfType(construction.newConst(1, Mode.getP()), type);
  }

  @Override
  public Node visitNewArray(Expression.NewArray that) {
    Type elementType = that.elementType.acceptVisitor(this);
    Node size = that.size.acceptVisitor(this);
    storeInCurrentLval = null;
    return callocOfType(size, elementType);
  }

  private Node callocOfType(Node num, Type elementType) {
    // calloc takes two parameters, for the number of elements and the size of each element.
    // both are of type size_t, which is mostly a machine word. So the modes used are just
    // an educated guess.
    // The fact that we called the array length size (which is parameter num to calloc) and
    // that here the element size is called size may be confusing, but whatever, I warned you.
    Node numNode = construction.newConv(num, Mode.getP());
    Node sizeNode = construction.newConst(sizeOf(elementType), Mode.getP());
    Node nodeOfCalloc = null;
    Node call =
        construction.newCall(
            construction.getCurrentMem(),
            nodeOfCalloc,
            new Node[] {numNode, sizeNode},
            ptrTo(elementType));
    construction.setCurrentMem(construction.newProj(call, Mode.getM(), Call.pnM));
    return construction.newProj(call, Mode.getP(), Call.pnTResult);
  }

  private int sizeOf(Type elementType) {
    // TODO
    return 0;
  }

  @Override
  public Node visitVariable(Expression.Variable that) {
    Mode mode = accessModeForType(that.type);
    int idx = localVarIndexes.get(that.var.def);
    storeInCurrentLval = (Node val) -> {
      construction.setVariable(idx, val);
      // This is soooo weird...
      return construction.getVariable(idx, mode);
    };
    return construction.getVariable(idx, mode);
  }

  @Override
  public Node visitBooleanLiteral(Expression.BooleanLiteral that) {
    storeInCurrentLval = null;
    return construction.newConst(that.literal ? 1 : 0, accessModeForType(minijava.ast.Type.BOOLEAN));
  }

  @Override
  public Node visitIntegerLiteral(Expression.IntegerLiteral that) {
    // TODO: the 0x80000000 case
    int lit = Integer.parseInt(that.literal);
    storeInCurrentLval = null;
    return construction.newConst(lit, accessModeForType(minijava.ast.Type.INT));
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
        // TODO: Don't know how to handle this. We should probably
        // catch this case before we can reference System.out, like we did in
        // SemanticAnalyzer
        return null;
      default:
        throw new UnsupportedOperationException(); // This should be exhaustive.
    }
  }

  @Override
  public Integer visitVariable(BlockStatement.Variable that) {
    Node rhs;
    if (that.rhs.isPresent()) {
      rhs = that.rhs.get().acceptVisitor(this);
    } else {
      // This is the sanest default we can get: just 0 initialize.
      rhs = construction.newConst(0, accessModeForType(that.type));
    }
    construction.setVariable(localVarIndexes.get(that), rhs);
    return null;
  }
}
