package minijava.firm;

import firm.*;
import firm.Program;
import firm.Type;
import firm.bindings.binding_ircons;
import firm.nodes.Block;
import firm.nodes.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import minijava.ast.*;
import minijava.ast.Class;
import minijava.ast.Field;
import minijava.ast.Method;

/** Draft of a firm graph generating visitor */
public class TestBed
    implements minijava.ast.Program.Visitor,
        Class.Visitor,
        Field.Visitor,
        Method.Visitor,
        minijava.ast.Type.Visitor<Type>,
        minijava.ast.Block.Visitor<Integer>,
        Expression.Visitor<Node>,
        BlockStatement.Visitor<Integer> {

  private Logger log = Logger.getLogger("TestBed");
  private minijava.ast.Program program;
  private HashMap<String, ClassType> classTypes = new HashMap<>();
  private Map<LocalVariable, Integer> localVarIndexes = new HashMap<>();
  private Graph methodGraph;
  private Construction methodCons;

  private final Type INT_TYPE;
  private final Type BOOLEAN_TYPE;

  public TestBed(minijava.ast.Program program, Entity methodEnt) {
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
    methodGraph = new Graph(methodEnt, maxLocals);
    methodCons = new Construction(methodGraph);

    that.body.acceptVisitor(this);

    methodCons.setCurrentBlock(methodGraph.getEndBlock());

    if (!methodCons.isUnreachable()) {
      // Add an implicit return statement at the end of the block,
      // iff we have return type void. In which case returnTypes has length 0.
      if (returnTypes.length == 0) {
        Node ret = methodCons.newReturn(methodCons.getCurrentMem(), new Node[0]);
        methodCons.setCurrentMem(ret);
        methodGraph.getEndBlock().addPred(ret);
      } else {
        // We can't just conjure a return value of arbitrary type.
        // This must be caught by the semantic pass.
        assert false;
      }
    }

    methodCons.setUnreachable();
    methodCons.finish();

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

      // reset the local variables
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
    Block block = methodCons.newBlock();
    if (that.expression.isPresent()) {
      that.expression.get().acceptVisitor(this);
    }
    methodCons.setCurrentBlock(block);
    methodCons.newIJmp(methodGraph.getEndBlock());

    // No code should follow a return statement.
    methodCons.setUnreachable();

    return null;
  }

  @Override
  public Node visitBinaryOperator(Expression.BinaryOperator that) {
    Node left =
        methodCons.newConv(that.left.acceptVisitor(this), accessModeForType(that.left.type));
    // TODO: save the address of the left expression (if there's one, e.g. iff it's an lval)
    Node right =
        methodCons.newConv(that.right.acceptVisitor(this), accessModeForType(that.right.type));

    switch (that.op) {
      case ASSIGN:
        // TODO: this is defunct. We need to compute the address of left here.
        // We need to return the address of an expression if we want to make this work (that's an lval).
        // We could set an instance var to the lval of an expression everytime we visit FieldAccess, Variable,
        // ArrayAccess, etc... but let's leave this open for now.

        // we procede as if left was that lval, e.g. computes the address at which to store the
        // calue of the right hand side expression.
        Node store = methodCons.newStore(methodCons.getCurrentMem(), left, right);
        methodCons.setCurrentMem(store);
        return store;
      case PLUS:
        return methodCons.newAdd(left, right);
      case MINUS:
        return methodCons.newSub(left, right);
      case MULTIPLY:
        return methodCons.newMul(left, right);
      case DIVIDE:
        return methodCons.newDiv(
            methodCons.getCurrentMem(),
            left,
            right,
            binding_ircons.op_pin_state.op_pin_state_exc_pinned);
      case MODULO:
        return methodCons.newMod(
            methodCons.getCurrentMem(),
            left,
            right,
            binding_ircons.op_pin_state.op_pin_state_exc_pinned);
      case OR:
        return methodCons.newOr(left, right);
      case AND:
        return methodCons.newAnd(left, right);
      case EQ:
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
    switch (that.op) {
      case NEGATE:
        return methodCons.newMinus(expression);
      case NOT:
        return methodCons.newNot(expression);
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public Node visitMethodCall(Expression.MethodCall that) {
    return null;
  }

  @Override
  public Node visitFieldAccess(Expression.FieldAccess that) {
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
    // TODO: do the rest here. running out of time for today. Reminaing:
    // 1. offset = elementSize * index (express this as a Node)
    // 2. cast array to a pointer
    // 3. dereference *(array + offset), remember to set currentMem accordingly
    return null;
  }

  @Override
  public Node visitNewObject(Expression.NewObject that) {
    Type type = that.type.acceptVisitor(this);
    // See callocOfType for the rationale behind Mode.getP()
    return callocOfType(methodCons.newConst(1, Mode.getP()), type);
  }

  @Override
  public Node visitNewArray(Expression.NewArray that) {
    Type elementType = that.elementType.acceptVisitor(this);
    Node size = that.size.acceptVisitor(this);
    return callocOfType(size, elementType);
  }

  private Node callocOfType(Node num, Type elementType) {
    // calloc takes two parameters, for the number of elements and the size of each element.
    // both are of type size_t, which is mostly a machine word. So the modes used are just
    // an educated guess.
    // The fact that we called the array length size (which is parameter num to calloc) and
    // that here the element size is called size may be confusing, but whatever, I warned you.
    Node numNode = methodCons.newConv(num, Mode.getP());
    Node sizeNode = methodCons.newConst(sizeOf(elementType), Mode.getP());
    Node nodeOfCalloc = null;
    return methodCons.newCall(
        methodCons.getCurrentMem(),
        nodeOfCalloc,
        new Node[] {numNode, sizeNode},
        ptrTo(elementType));
  }

  private int sizeOf(Type elementType) {
    // TODO
    return 0;
  }

  @Override
  public Node visitVariable(Expression.Variable that) {
    return methodCons.getVariable(localVarIndexes.get(that.var.def), accessModeForType(that.type));
  }

  @Override
  public Node visitBooleanLiteral(Expression.BooleanLiteral that) {
    return methodCons.newConst(that.literal ? 1 : 0, accessModeForType(minijava.ast.Type.BOOLEAN));
  }

  @Override
  public Node visitIntegerLiteral(Expression.IntegerLiteral that) {
    // TODO: the 0x80000000 case
    int lit = Integer.parseInt(that.literal);
    return methodCons.newConst(lit, accessModeForType(minijava.ast.Type.INT));
  }

  @Override
  public Node visitReferenceTypeLiteral(Expression.ReferenceTypeLiteral that) {
    switch (that.name()) {
      case "this":
        // access parameter 0 as a pointer, that's where this is to be found
        return methodCons.getVariable(0, Mode.getP());
      case "null":
        return methodCons.newConst(0, Mode.getP());
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
      rhs = methodCons.newConst(0, accessModeForType(that.type));
    }
    methodCons.setVariable(localVarIndexes.get(that), rhs);
    return null;
  }
}
