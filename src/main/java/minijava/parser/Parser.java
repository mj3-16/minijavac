package minijava.parser;

import static minijava.token.Terminal.*;
import static minijava.token.Terminal.Associativity.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import minijava.ast.*;
import minijava.ast.Class;
import minijava.token.Position;
import minijava.token.Terminal;
import minijava.token.Token;
import minijava.util.LookAheadIterator;

public class Parser {
  private static final Token EOF_TOKEN = new Token(EOF, new Position(0, 0), null);
  private final LookAheadIterator<Token> tokens;
  private Token currentToken;

  public Parser(Iterator<Token> tokens) {
    this.tokens = new LookAheadIterator<>(tokens);
  }

  private void consumeToken() {
    if (tokens.hasNext()) {
      this.currentToken = tokens.next();
    } else {
      this.currentToken = EOF_TOKEN;
    }
  }

  private void expectAndConsume(Terminal terminal) {
    if (currentToken.terminal != terminal) {
      throw new ParserError(
          Thread.currentThread().getStackTrace()[2].getMethodName(), terminal, currentToken);
    }
    consumeToken();
  }

  private void expectAndConsume(Terminal terminal, String value) {
    if (currentToken.terminal != terminal || !currentToken.lexval.equals(value)) {
      throw new ParserError(
          Thread.currentThread().getStackTrace()[2].getMethodName(), terminal, value, currentToken);
    }
    consumeToken();
  }

  private String expectAndConsumeAndReturnValue(Terminal terminal) {
    if (currentToken.terminal != terminal) {
      throw new ParserError(
          Thread.currentThread().getStackTrace()[2].getMethodName(), terminal, currentToken);
    }
    String value = currentToken.lexval;
    consumeToken();
    return value;
  }

  private void unexpectCurrentToken(Terminal... expectedTerminals) {
    throw new ParserError(
        Thread.currentThread().getStackTrace()[2].getMethodName(), currentToken, expectedTerminals);
  }

  private boolean isCurrentTokenTypeOf(Terminal terminal) {
    if (currentToken.terminal == terminal) {
      return true;
    }
    return false;
  }

  private boolean isCurrentTokenNotTypeOf(Terminal terminal) {
    return !isCurrentTokenTypeOf(terminal);
  }

  private boolean isCurrentTokenBinaryOperator() {
    if (currentToken.isOperator()) {
      return true;
    }
    return false;
  }

  private boolean isOperatorPrecedenceGreaterOrEqualThan(int precedence) {
    if (currentToken.precedence() >= precedence) {
      return true;
    }
    return false;
  }

  private boolean matchCurrentAndLookAhead(Terminal... terminals) {
    for (int i = 0; i < terminals.length; i++) {
      if (tokens.lookAhead(i).orElse(EOF_TOKEN).terminal != terminals[i]) {
        return false;
      }
    }
    return true;
  }

  public Program parse() {
    consumeToken();
    return parseProgramm();
  }

  /** Program -> ClassDeclaration* */
  private Program parseProgramm() {
    List<Class> classes = new ArrayList<>();
    while (isCurrentTokenNotTypeOf(EOF)) {
      classes.add(parseClassDeclaration());
    }
    expectAndConsume(EOF);
    return new Program(classes);
  }

  /** ClassDeclaration -> class IDENT { PublicClassMember* } */
  private Class parseClassDeclaration() {
    String identifier;
    List<Field> fields = new ArrayList<>();
    List<Method> methods = new ArrayList<>();
    expectAndConsume(CLASS);
    identifier = expectAndConsumeAndReturnValue(IDENT);
    expectAndConsume(LBRACE);
    while (isCurrentTokenNotTypeOf(RBRACE) && isCurrentTokenNotTypeOf(EOF)) {
      Object classMember = parsePublicClassMember();
      if (classMember instanceof Field) {
        fields.add((Field) classMember);
      } else {
        // It must be type of Method, otherwise a CastException will be thrown.
        // As an alternative, we could test classMember with instanceof, but
        // if classMember is in any way not of the expected type, we need to throw
        // an other exception. I don't know which way is better.
        methods.add((Method) classMember);
      }
    }
    expectAndConsume(RBRACE);
    return new Class(identifier, fields, methods);
  }

  /** PublicClassMember -> public ClassMember */
  private Object parsePublicClassMember() {
    expectAndConsume(PUBLIC);
    return parseClassMember();
  }

  /** ClassMember -> MainMethod | FieldOrMethod */
  private Object parseClassMember() {
    Object object;
    switch (currentToken.terminal) {
      case STATIC:
        object = parseMainMethod();
        break;
      default:
        object = parseTypeIdentFieldOrMethod();
        break;
    }
    return object;
  }

  /** MainMethod -> static void IDENT ( String [] IDENT ) Block */
  private Method parseMainMethod() {
    expectAndConsume(STATIC);
    expectAndConsume(VOID);
    String name = expectAndConsumeAndReturnValue(IDENT);
    expectAndConsume(LPAREN);
    expectAndConsume(IDENT, "String");
    expectAndConsume(LBRACK);
    expectAndConsume(RBRACK);
    expectAndConsume(IDENT);
    expectAndConsume(RPAREN);
    Block block = parseBlock();
    return new Method(true, new Type("void", 0), name, new ArrayList<>(), block);
  }

  /** TypeIdentFieldOrMethod -> Type IDENT FieldOrMethod */
  private Object parseTypeIdentFieldOrMethod() {
    Type type = parseType();
    String name = expectAndConsumeAndReturnValue(IDENT);
    return parseFieldOrMethod(type, name);
  }

  /** FieldOrMethod -> ; | Method */
  private Object parseFieldOrMethod(Type type, String name) {
    if (isCurrentTokenTypeOf(SEMICOLON)) {
      expectAndConsume(SEMICOLON);
      return new Field(type, name);
    } else {
      return parseMethod(type, name);
    }
  }

  /** Method -> ( Parameters? ) Block */
  private Object parseMethod(Type type, String name) {
    List<Method.Parameter> parameters = new ArrayList<>();
    expectAndConsume(LPAREN);
    if (isCurrentTokenNotTypeOf(RPAREN)) {
      parameters = parseParameters();
    }
    expectAndConsume(RPAREN);
    Block block = parseBlock();
    return new Method(false, type, name, parameters, block);
  }

  /** Parameters -> Parameter | Parameter , Parameters */
  private List<Method.Parameter> parseParameters() {
    List<Method.Parameter> parameters = new ArrayList<>();
    parameters.add(parseParameter());
    while (isCurrentTokenTypeOf(COMMA)) {
      expectAndConsume(COMMA);
      parameters.add(parseParameter());
    }
    return parameters;
  }

  /** Parameter -> Type IDENT */
  private Method.Parameter parseParameter() {
    Type type = parseType();
    String identifier = expectAndConsumeAndReturnValue(IDENT);
    return new Method.Parameter(type, identifier);
  }

  /** Type -> BasicType ([])* */
  private Type parseType() {
    // Only later call is in parseLocalVariableDeclarationStatement()
    // parseType() does not recurse however, so we are safe.
    String type = parseBasicType();
    int dimension = 0;
    while (isCurrentTokenTypeOf(LBRACK) && isCurrentTokenNotTypeOf(EOF)) {
      expectAndConsume(LBRACK);
      expectAndConsume(RBRACK);
      dimension++;
    }
    return new Type(type, dimension);
  }

  /** BasicType -> int | boolean | void | IDENT */
  private String parseBasicType() {
    switch (currentToken.terminal) {
      case INT:
        expectAndConsume(INT);
        return "int";
      case BOOLEAN:
        expectAndConsume(BOOLEAN);
        return "boolean";
      case VOID:
        expectAndConsume(VOID);
        return "void";
      case IDENT:
        return expectAndConsumeAndReturnValue(IDENT);
      default:
        unexpectCurrentToken(INT, BOOLEAN, VOID, IDENT);
        // will never be returned, but we still need a return value here
        return "Invalid Type";
    }
  }

  /**
   * Statement -> Block | EmptyStatement | IfStatement | ExpressionStatement | WhileStatement |
   * ReturnStatement
   */
  private Statement parseStatement() {
    // Also called from BlockStatement, IfStatement and WhileStatement.
    // There is possibility for endless recursion here, but that's OK
    // because it's not tail recursive (which we have to optimize away
    // with loops). A nested sequence of blocks (e.g. {{{...{{{ ; }}}...}}})
    // will blow the parser up and there's nothing we can do about it,
    // except for allocating more stack space/switching to a table-based
    // parser.
    switch (currentToken.terminal) {
      case LBRACE:
        return parseBlock();
      case SEMICOLON:
        return parseEmptyStatement();
      case IF:
        return parseIfStatement();
      case WHILE:
        return parseWhileStatement();
      case RETURN:
        return parseReturnStatement();
      default:
        return parseExpressionStatement();
    }
  }

  /** Block -> { BlockStatement* } */
  private Block parseBlock() {
    List<BlockStatement> blockStatements = new ArrayList<>();
    expectAndConsume(LBRACE);
    while (isCurrentTokenNotTypeOf(RBRACE) && isCurrentTokenNotTypeOf(EOF)) {
      blockStatements.add(parseBlockStatement());
    }
    expectAndConsume(RBRACE);
    return new Block(blockStatements);
  }

  /** BlockStatement -> Statement | LocalVariableDeclarationStatement */
  private BlockStatement parseBlockStatement() {
    if (currentToken.isOneOf(INT, BOOLEAN, VOID)
        || matchCurrentAndLookAhead(IDENT, LBRACK, RBRACK)
        || matchCurrentAndLookAhead(IDENT, IDENT)) {
      return parseLocalVariableDeclarationStatement();
    } else {
      return parseStatement();
    }
  }

  /** LocalVariableDeclarationStatement -> Type IDENT (= Expression)? ; */
  private BlockStatement parseLocalVariableDeclarationStatement() {
    Type type = parseType();
    String identifier = expectAndConsumeAndReturnValue(IDENT);
    Expression expression = null;
    if (isCurrentTokenTypeOf(ASSIGN)) {
      expectAndConsume(ASSIGN);
      expression = parseExpression();
    }
    expectAndConsume(SEMICOLON);
    return new Statement.Variable(type, identifier, expression);
  }

  /** EmptyStatement -> ; */
  private Statement parseEmptyStatement() {
    expectAndConsume(SEMICOLON);
    return new Statement.EmptyStatement();
  }

  /** WhileStatement -> while ( Expression ) Statement */
  private Statement parseWhileStatement() {
    expectAndConsume(WHILE);
    expectAndConsume(LPAREN);
    Expression condition = parseExpression();
    expectAndConsume(RPAREN);
    Statement body = parseStatement();
    return new Statement.While(condition, body);
  }

  /** IfStatement -> if ( Expression ) Statement (else Statement)? */
  private Statement parseIfStatement() {
    expectAndConsume(IF);
    expectAndConsume(LPAREN);
    Expression condition = parseExpression();
    expectAndConsume(RPAREN);
    Statement then = parseStatement();
    Statement else_ = null;
    if (isCurrentTokenTypeOf(ELSE)) {
      expectAndConsume(ELSE);
      else_ = parseStatement();
    }
    return new Statement.If(condition, then, else_);
  }

  /** ExpressionStatement -> Expression ; */
  private Statement parseExpressionStatement() {
    Expression expression = parseExpression();
    expectAndConsume(SEMICOLON);
    return new Statement.ExpressionStatement(expression);
  }

  /** ReturnStatement -> return Expression? ; */
  private Statement parseReturnStatement() {
    expectAndConsume(RETURN);
    Expression expression = null;
    if (isCurrentTokenNotTypeOf(SEMICOLON)) {
      expression = parseExpression();
    }
    expectAndConsume(SEMICOLON);
    if (null == expression) {
      return new Statement.Return();
    }
    return new Statement.Return(expression);
  }

  /** Expression is parsed with Precedence Climbing */
  private Expression parseExpression() {
    return parseExpressionWithPrecedenceClimbing(0);
  }

  private Expression parseExpressionWithPrecedenceClimbing(int minPrecedence) {
    // This is the other method that could possibly blow up the stack,
    // which we can do nothing about.
    Expression result = parseUnaryExpression();
    while (isCurrentTokenBinaryOperator()
        && isOperatorPrecedenceGreaterOrEqualThan(minPrecedence)) {
      Expression.BinOp operator = getBinaryOperator(currentToken);
      int precedence = currentToken.precedence();
      if (currentToken.associativity() == LEFT) {
        precedence++;
      }
      consumeToken();
      Expression rhs = parseExpressionWithPrecedenceClimbing(precedence);

      result = new Expression.BinaryOperatorExpression(operator, result, rhs);
    }
    return result;
  }

  private Expression.BinOp getBinaryOperator(Token token) {
    switch (token.terminal) {
      case ASSIGN:
        return Expression.BinOp.ASSIGN;
      case OR:
        return Expression.BinOp.OR;
      case AND:
        return Expression.BinOp.AND;
      case EQL:
        return Expression.BinOp.EQ;
      case NEQ:
        return Expression.BinOp.NEQ;
      case LSS:
        return Expression.BinOp.LT;
      case LEQ:
        return Expression.BinOp.LEQ;
      case GTR:
        return Expression.BinOp.GT;
      case GEQ:
        return Expression.BinOp.GEQ;
      case ADD:
        return Expression.BinOp.PLUS;
      case SUB:
        return Expression.BinOp.MINUS;
      case MUL:
        return Expression.BinOp.MULTIPLY;
      case DIV:
        return Expression.BinOp.DIVIDE;
      case MOD:
        return Expression.BinOp.MODULO;
      default:
        throw new ParserError(token.position, "Token is not a BinaryOperator");
    }
  }

  private Expression.UnOp getUnaryOperator(Token token) {
    switch (token.terminal) {
      case NOT:
        return Expression.UnOp.NOT;
      case SUB:
        return Expression.UnOp.NEGATE;
      default:
        throw new ParserError(token.position, "Token is not an UnaryOperator");
    }
  }

  /** UnaryExpression -> PostfixExpression | (! | -) UnaryExpression */
  private Expression parseUnaryExpression() {
    Expression.UnOp operator = null;
    if (currentToken.isOneOf(NOT, SUB)) {
      operator = getUnaryOperator(currentToken);
      consumeToken();
      Expression unaryExpression = parseUnaryExpression();
      return new Expression.UnaryOperatorExpression(operator, unaryExpression);
    }
    Expression postFixExpression = parsePostfixExpression();
    return new Expression.UnaryOperatorExpression(operator, postFixExpression);
  }

  /** PostfixExpression -> PrimaryExpression (PostfixOp)* */
  private Expression parsePostfixExpression() {
    Expression primaryExpression = parsePrimaryExpression();
    while ((isCurrentTokenTypeOf(LBRACK) || isCurrentTokenTypeOf(PERIOD))
        && isCurrentTokenNotTypeOf(EOF)) {
      primaryExpression = parsePostfixOp(primaryExpression);
    }
    return primaryExpression;
  }

  /** PostfixOp -> MethodInvocation | FieldAccess | ArrayAccess */
  private Expression parsePostfixOp(Expression lhs) {
    Expression postFixOp = null;
    switch (currentToken.terminal) {
      case PERIOD:
        postFixOp = parseDotIdentFieldAccessMethodInvocation(lhs);
        break;
      case LBRACK:
        postFixOp = parseArrayAccess(lhs);
        break;
      default:
        unexpectCurrentToken(PERIOD, LBRACK);
    }
    return postFixOp;
  }

  /** DotIdentFieldAccessMethodInvocation -> . IDENT (MethodInvocation)? */
  private Expression parseDotIdentFieldAccessMethodInvocation(Expression lhs) {
    expectAndConsume(PERIOD);
    String identifier = expectAndConsumeAndReturnValue(IDENT);
    // is it FieldAccess (false) or MethodInvocation (true)?
    if (isCurrentTokenTypeOf(LPAREN)) {
      return parseMethodInvocation(lhs, identifier);
    }
    return new Expression.FieldAccessExpression(lhs, identifier);
  }

  /** MethodInvocation -> ( Arguments ) */
  private Expression parseMethodInvocation(Expression lhs, String identifier) {
    expectAndConsume(LPAREN);
    List<Expression> arguments = parseArguments();
    expectAndConsume(RPAREN);
    return new Expression.MethodCallExpression(lhs, identifier, arguments);
  }

  /** ArrayAccess -> [ Expression ] */
  private Expression parseArrayAccess(Expression array) {
    expectAndConsume(LBRACK);
    Expression index = parseExpression();
    expectAndConsume(RBRACK);
    return new Expression.ArrayAccessExpression(array, index);
  }

  /** Arguments -> (Expression (,Expression)*)? */
  private List<Expression> parseArguments() {
    List<Expression> arguments = new ArrayList<>();
    if (isCurrentTokenNotTypeOf(RPAREN)) {
      arguments.add(parseExpression());
      while (isCurrentTokenTypeOf(COMMA) && isCurrentTokenNotTypeOf(EOF)) {
        expectAndConsume(COMMA);
        arguments.add(parseExpression());
      }
    }
    return arguments;
  }

  /**
   * PrimaryExpression -> null | false | true | INTEGER_LITERAL | IDENT | IDENT ( Arguments ) | this
   * | ( Expression ) | NewObjectArrayExpression
   */
  private Expression parsePrimaryExpression() {
    Expression primaryExpression = null;
    switch (currentToken.terminal) {
      case NULL:
        expectAndConsume(NULL);
        primaryExpression = new Expression.VariableExpression("null");
        break;
      case FALSE:
        expectAndConsume(FALSE);
        primaryExpression = new Expression.BooleanLiteralExpression(false);
        break;
      case TRUE:
        expectAndConsume(TRUE);
        primaryExpression = new Expression.BooleanLiteralExpression(true);
        break;
      case INTEGER_LITERAL:
        String literal = expectAndConsumeAndReturnValue(INTEGER_LITERAL);
        primaryExpression = new Expression.IntegerLiteralExpression(literal);
        break;
      case IDENT:
        String identifier = expectAndConsumeAndReturnValue(IDENT);
        List<Expression> arguments;
        if (isCurrentTokenTypeOf(LPAREN)) {
          expectAndConsume(LPAREN);
          arguments = parseArguments();
          expectAndConsume(RPAREN);
          primaryExpression = new Expression.MethodCallExpression(null, identifier, arguments);
        } else {
          primaryExpression = new Expression.FieldAccessExpression(null, identifier);
        }
        break;
      case THIS:
        expectAndConsume(THIS);
        primaryExpression = new Expression.VariableExpression("this");
        break;
      case LPAREN:
        expectAndConsume(LPAREN);
        primaryExpression = parseExpression();
        expectAndConsume(RPAREN);
        break;
      case NEW:
        primaryExpression = parseNewObjectArrayExpression();
        break;
      default:
        unexpectCurrentToken(NULL, FALSE, TRUE, INTEGER_LITERAL, IDENT, THIS, LPAREN, NEW);
    }
    return primaryExpression;
  }

  /** NewObjectArrayExpression -> BasicType NewArrayExpression | IDENT NewObjectExpression */
  private Expression parseNewObjectArrayExpression() {
    Expression expression = null;
    Type type;
    expectAndConsume(NEW);
    switch (currentToken.terminal) {
      case INT:
        expectAndConsume(INT);
        type = new Type("int", 0);
        expression = parseNewArrayExpression(type);
        break;
      case BOOLEAN:
        expectAndConsume(BOOLEAN);
        type = new Type("boolean", 0);
        expression = parseNewArrayExpression(type);
        break;
      case VOID:
        expectAndConsume(VOID);
        type = new Type("void", 0);
        expression = parseNewArrayExpression(type);
        break;
      case IDENT:
        String identifier = expectAndConsumeAndReturnValue(IDENT);
        type = new Type(identifier, 0);
        switch (currentToken.terminal) {
          case LPAREN:
            expression = parseNewObjectExpression(type);
            break;
          case LBRACK:
            expression = parseNewArrayExpression(type);
            break;
          default:
            unexpectCurrentToken(LPAREN, LBRACK);
        }
        break;
      default:
        unexpectCurrentToken(INT, BOOLEAN, VOID, IDENT);
    }
    return expression;
  }

  /** NewObjectExpression -> ( ) */
  private Expression parseNewObjectExpression(Type type) {
    expectAndConsume(LPAREN);
    expectAndConsume(RPAREN);
    return new Expression.NewObjectExpression(type);
  }

  /** NewArrayExpression -> [ Expression ] ([])* */
  private Expression parseNewArrayExpression(Type type) {
    expectAndConsume(LBRACK);
    Expression index = parseExpression();
    expectAndConsume(RBRACK);
    while (matchCurrentAndLookAhead(LBRACK, RBRACK)) {
      expectAndConsume(LBRACK);
      expectAndConsume(RBRACK);
    }
    return new Expression.NewArrayExpression(type, index);
  }
}
