package minijava.firm;

import firm.*;
import firm.Program;
import firm.Type;
import firm.bindings.binding_ircons;
import firm.nodes.Block;
import firm.nodes.Node;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import firm.nodes.Tuple;
import minijava.ast.*;
import minijava.ast.Class;
import minijava.ast.Field;
import minijava.ast.Method;
import minijava.util.SourceRange;
import org.jooq.lambda.tuple.Tuple2;

/** Draft of a firm graph generating visitor */
public class TestBed
    implements minijava.ast.Program.Visitor,
        Class.Visitor,
        Field.Visitor,
        Method.Visitor,
        minijava.ast.Type.Visitor<Type>,
        minijava.ast.Block.Visitor<Integer>,
        Expression.Visitor,
        BlockStatement.Visitor<Integer> {

  private Logger log = Logger.getLogger("TestBed");
  private minijava.ast.Program program;
  private HashMap<String, ClassType> classTypes = new HashMap<>();
  private HashMap<String, PointerType> ptrsToClasses = new HashMap<>();
  /** Mangled method name → method type */
  private HashMap<String, MethodType> methodTypes;

  private final Type INT_TYPE;
  private final Type BOOLEAN_TYPE;

  public TestBed(minijava.ast.Program program) {
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
      addClassType(klass.name());
    }
    for (Class klass : that.declarations) {
      klass.acceptVisitor(this);
    }
    return null;
  }

  private void addClassType(String className) {
    ClassType type = new ClassType(NameMangler.mangleClassName(className));
    classTypes.put(className, type);
    ptrsToClasses.put(className, new PointerType(type));
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

  private int currentNumberOfLocalVariables = 0;
  private Map<LocalVariable, Integer> localVariableIDs = new HashMap<>();
  private Graph methodGraph;
  private Construction methodCons;
  private Entity methodEnt;
  private Method currentMethod;
  private int returnVariableId = -1;
  private ClassType definingClass;
  private Block returnBlock;
  private int expressionResultVariableId;

  @Override
  public Object visitMethod(Method that) {
    currentMethod = that;
    String name = "main";
    Type classType = Program.getGlobalType();
    Type[] parameterTypes = new Type[0];
    Type[] returnTypes = new Type[0];
    if (!that.isStatic) {
      definingClass = classTypes.get(that.definingClass.name());
      Type thisType = ptrToClass(that.definingClass.name());
      parameterTypes = new Type[that.parameters.size() + 2];
      parameterTypes[0] = thisType;
      localVariableIDs.put(
          new LocalVariable(
              new minijava.ast.Type(
                  new Ref<BasicType>(that.definingClass.def), 0, SourceRange.FIRST_CHAR),
              "this",
              SourceRange.FIRST_CHAR),
          0);
      Type returnType =
          that.returnType.basicType.name().equals("void")
              ? null
              : that.returnType.acceptVisitor(this);
      parameterTypes[1] = returnType;
      for (int i = 2; i < parameterTypes.length; i++) {
        parameterTypes[i] = that.parameters.get(i - 2).type.acceptVisitor(this);
        localVariableIDs.put(that.parameters.get(i - 2), i);
      }
      currentNumberOfLocalVariables = parameterTypes.length;
      name = NameMangler.mangleMethodName(that.definingClass.name(), that.name());
      classType = thisType;
      if (returnType != null) {
        returnTypes = new Type[] {returnType};
      }
    }

    int maxLocals =
        currentNumberOfLocalVariables
            + that.body.acceptVisitor(new NumberOfLocalVariablesVisitor());

    Type methodType = new MethodType(parameterTypes, returnTypes);

    assert classType instanceof CompoundType;
    methodEnt = new Entity((CompoundType) classType, name, methodType);

    // Start actual code creation
    methodGraph = new Graph(methodEnt, maxLocals);
    methodCons = new Construction(methodGraph);

    that.body.acceptVisitor(this);

    methodCons.setCurrentBlock(methodGraph.getEndBlock());
    // Add a return statement to the return block
    Node curMem = methodCons.getCurrentMem();
    Node retn;
    if (returnTypes.length == 1) {
      retn =
          methodCons.newReturn(
              curMem,
              new Node[] {methodCons.getVariable(returnVariableId, modeType(that.returnType))});
    } else {
      retn = methodCons.newReturn(curMem, new Node[0]);
    }
    returnBlock.addPred(retn);

    methodCons.isUnreachable();

    methodCons.finish();

    // Clean up
    localVariableIDs.clear();
    currentNumberOfLocalVariables = 0;
    returnVariableId = -1;
    return null;
  }

  @Override
  public Type visitType(minijava.ast.Type that) {
    assert !that.basicType.name().equals("void");
    Type type = null;
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
    return null;
  }

  private Mode modeType(minijava.ast.Type type) {
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
    return classTypes.get(name);
  }

  private PointerType ptrToClass(String name) {
    return ptrsToClasses.get(name);
  }

  @Override
  public Integer visitBlock(minijava.ast.Block that) {
    int lastLVN = currentNumberOfLocalVariables;

    for (BlockStatement statement : that.statements) {
      statement.acceptVisitor(this);

      // reset the local variables
      currentNumberOfLocalVariables = lastLVN;
    }
    return null;
  }

  @Override
  public Integer visitEmpty(Statement.Empty that) {
    return null;
  }

  @Override
  public Integer visitIf(Statement.If that) {
    int lastLVN = currentNumberOfLocalVariables;

    // Evaluate condition and set the place for the condition result
    returnVariableId = currentNumberOfLocalVariables;
    //that.condition.acceptVisitor(this);

    // next week...

    // Conditional Jump Node with the True+False Proj
    currentNumberOfLocalVariables = lastLVN;
    return null;
  }

  @Override
  public Integer visitExpressionStatement(Statement.ExpressionStatement that) {
    int lastLVN = currentNumberOfLocalVariables;
    returnVariableId = -1;
    that.expression.acceptVisitor(this);
    currentNumberOfLocalVariables = lastLVN;
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
      expressionResultVariableId = returnVariableId;
      that.expression.get().acceptVisitor(this);
    }
    methodCons.setCurrentBlock(block);
    methodCons.newIJmp(methodGraph.getEndBlock());

    // No code should follow a return statement.
    methodCons.setUnreachable();

    return null;
  }

  @Override
  public Object visitBinaryOperator(Expression.BinaryOperator that) {
    int lastLvn = currentNumberOfLocalVariables;
    int lastRet = returnVariableId;

    if (that.op == Expression.BinOp.ASSIGN){
      processAssignment(that);
      return null;
    }

    returnVariableId = currentNumberOfLocalVariables;
    currentNumberOfLocalVariables += 1;
    Node var = methodCons.getVariable(returnVariableId, modeType(that.left.type()));

    that.left.acceptVisitor(this);
    returnVariableId = lastRet;
    that.right.acceptVisitor(this);

    Node var2 = methodCons.getVariable(returnVariableId, modeType(that.right.type()));

    Node op = null;
    switch (that.op){
      case PLUS:
        op = methodCons.newAdd(var, var2);
        break;
      case MINUS:
        op = methodCons.newSub(var, var2);
        break;
      case MULTIPLY:
        op = methodCons.newMul(var, var2);
        break;
      case DIVIDE:
        op = methodCons.newDiv(methodCons.getCurrentMem(), var, var2, binding_ircons.op_pin_state.op_pin_state_exc_pinned);
        break;
      case MODULO:
        op = methodCons.newMod(methodCons.getCurrentMem(), var, var2, binding_ircons.op_pin_state.op_pin_state_exc_pinned);
        break;
      case OR:
        op = methodCons.newOr(var, var2);
        break;
      case AND:
        op = methodCons.newAnd(var, var2);
        break;
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
    }
    methodCons.setVariable(returnVariableId, op);
    currentNumberOfLocalVariables = lastLvn;
    return null;
  }

  private void processAssignment(Expression.BinaryOperator that) {
    int lastLVN = currentNumberOfLocalVariables;
    int lastRet = returnVariableId;
    if (that.left instanceof LocalVariable){
      int locId = localVariableIDs.get((LocalVariable) that.left);
      returnVariableId = locId;
      that.right.acceptVisitor(this);
      methodCons.setVariable(lastRet, methodCons.getVariable(locId, modeType(that.left.type())));
    } else if (that.left instanceof Expression.FieldAccess){
      Expression.FieldAccess field = (Expression.FieldAccess)that.left;
      int rightId = returnVariableId;
      that.right.acceptVisitor(this); // the result should be at returnVariableId

      int leftId = currentNumberOfLocalVariables;
      currentNumberOfLocalVariables++;
      field.self.acceptVisitor(this);
      ClassType leftSelfType = classTypes.get(field.self.type().basicType.name()); // assumption: only class types have fields
      int offset = leftSelfType.getMemberByName(field.field.name()).getOffset();
      Node offsetConst = methodCons.newConst(offset, Mode.getP());
      Node selfVar = methodCons.getVariable(leftId, Mode.getP());
      methodCons.newAdd(selfVar, offsetConst);
      //ja
      // methodCons.newStore(methodCons.getCurrentMem(), )
    }
    currentNumberOfLocalVariables = lastLVN;
  }

  @Override
  public Object visitUnaryOperator(Expression.UnaryOperator that) {
    int lastLvn = currentNumberOfLocalVariables;
    that.expression.acceptVisitor(this);
    if (returnVariableId != -1) { // if the value isn't used, don't bother to calculate it
      Node var, op = null;
      switch (that.op) {
        case NEGATE:
          var = methodCons.getVariable(returnVariableId, Mode.getIs());
          op = methodCons.newMinus(var);
          break;
        case NOT:
          var = methodCons.getVariable(returnVariableId, Mode.getBu());
          op = methodCons.newMinus(var);
      }
      methodCons.setVariable(returnVariableId, op);
    }
    currentNumberOfLocalVariables = lastLvn;
    return null;
  }

  @Override
  public Object visitMethodCall(Expression.MethodCall that) {
    return null;
  }

  @Override
  public Object visitFieldAccess(Expression.FieldAccess that) {
    return null;
  }

  @Override
  public Object visitArrayAccess(Expression.ArrayAccess that) {
    return null;
  }

  @Override
  public Object visitNewObject(Expression.NewObject that) {
    return null;
  }

  @Override
  public Object visitNewArray(Expression.NewArray that) {
    return null;
  }

  @Override
  public Object visitVariable(Expression.Variable that) {
    return null;
  }

  @Override
  public Object visitBooleanLiteral(Expression.BooleanLiteral that) {
    return null;
  }

  @Override
  public Object visitIntegerLiteral(Expression.IntegerLiteral that) {
    return null;
  }

  @Override
  public Object visitReferenceTypeLiteral(Expression.ReferenceTypeLiteral that) {
    return null;
  }

  @Override
  public Integer visitVariable(BlockStatement.Variable that) {
    return null;
  }
}
