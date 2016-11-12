package minijava.parser;

import static minijava.token.Terminal.*;
import static minijava.token.Terminal.Associativity.*;

import com.google.common.collect.ImmutableList;
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
  private static final Expression<String> THIS_EXPR = new Expression.VariableExpression<>("this");
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
    if (currentToken.terminal != terminal
        || currentToken.lexval == null
        || !currentToken.lexval.equals(value)) {
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

  private <T> T unexpectCurrentToken(Terminal... expectedTerminals) {
    throw new ParserError(
        Thread.currentThread().getStackTrace()[2].getMethodName(), currentToken, expectedTerminals);
  }

  private boolean isCurrentTokenTypeOf(Terminal terminal) {
    return currentToken.terminal == terminal;
  }

  private boolean isCurrentTokenNotTypeOf(Terminal terminal) {
    return !isCurrentTokenTypeOf(terminal);
  }

  private boolean isCurrentTokenBinaryOperator() {
    return currentToken.isOperator();
  }

  private boolean isOperatorPrecedenceGreaterOrEqualThan(int precedence) {
    return currentToken.precedence() >= precedence;
  }

  private boolean matchCurrentAndLookAhead(Terminal... terminals) {
    for (int i = 0; i < terminals.length; i++) {
      if (tokens.lookAhead(i).orElse(EOF_TOKEN).terminal != terminals[i]) {
        return false;
      }
    }
    return true;
  }

  public Program<String> parse() {
    consumeToken();
    return parseProgramm();
  }

  /** Program -> ClassDeclaration* */
  private Program<String> parseProgramm() {
    List<Class<String>> classes = new ArrayList<>();
    while (isCurrentTokenNotTypeOf(EOF)) {
      classes.add(parseClassDeclaration());
    }
    expectAndConsume(EOF);
    return new Program<>(classes);
  }

  /** ClassDeclaration -> class IDENT { PublicClassMember* } */
  private Class<String> parseClassDeclaration() {
    expectAndConsume(CLASS);
    String identifier = expectAndConsumeAndReturnValue(IDENT);
    expectAndConsume(LBRACE);
    List<Field<String>> fields = new ArrayList<>();
    List<Method<String>> methods = new ArrayList<>();
    while (isCurrentTokenNotTypeOf(RBRACE) && isCurrentTokenNotTypeOf(EOF)) {
      parsePublicClassMember(fields, methods);
    }
    expectAndConsume(RBRACE);
    return new Class<>(identifier, fields, methods);
  }

  /** PublicClassMember -> public ClassMember */
  private void parsePublicClassMember(List<Field<String>> fields, List<Method<String>> methods) {
    expectAndConsume(PUBLIC);
    parseClassMember(fields, methods);
  }

  /** ClassMember -> MainMethod | FieldOrMethod */
  private void parseClassMember(List<Field<String>> fields, List<Method<String>> methods) {
    switch (currentToken.terminal) {
      case STATIC:
        methods.add(parseMainMethod());
        break;
      default:
        parseTypeIdentFieldOrMethod(fields, methods);
        break;
    }
  }

  /** MainMethod -> static void IDENT ( String [] IDENT ) Block */
  private Method<String> parseMainMethod() {
    expectAndConsume(STATIC);
    expectAndConsume(VOID);
    String name = expectAndConsumeAndReturnValue(IDENT);
    expectAndConsume(LPAREN);
    expectAndConsume(IDENT, "String");
    expectAndConsume(LBRACK);
    expectAndConsume(RBRACK);
    String parameter = expectAndConsumeAndReturnValue(IDENT);
    expectAndConsume(RPAREN);
    Block<String> block = parseBlock();
    List<Method.Parameter<String>> parameters =
        ImmutableList.of(new Method.Parameter<>(new Type<>("String", 1), parameter));
    return new Method<>(true, new Type<>("void", 0), name, parameters, block);
  }

  /** TypeIdentFieldOrMethod -> Type IDENT FieldOrMethod */
  private void parseTypeIdentFieldOrMethod(
      List<Field<String>> fields, List<Method<String>> methods) {
    Type<String> type = parseType();
    String name = expectAndConsumeAndReturnValue(IDENT);
    parseFieldOrMethod(type, name, fields, methods);
  }

  /** FieldOrMethod -> ; | Method */
  private void parseFieldOrMethod(
      Type<String> type, String name, List<Field<String>> fields, List<Method<String>> methods) {
    if (isCurrentTokenTypeOf(SEMICOLON)) {
      expectAndConsume(SEMICOLON);
      fields.add(new Field<>(type, name));
    } else {
      methods.add(parseMethod(type, name));
    }
  }

  /** Method -> ( Parameters? ) Block */
  private Method<String> parseMethod(Type<String> type, String name) {
    List<Method.Parameter<String>> parameters = new ArrayList<>();
    expectAndConsume(LPAREN);
    if (isCurrentTokenNotTypeOf(RPAREN)) {
      parameters = parseParameters();
    }
    expectAndConsume(RPAREN);
    Block<String> block = parseBlock();
    return new Method<>(false, type, name, parameters, block);
  }

  /** Parameters -> Parameter | Parameter , Parameters */
  private List<Method.Parameter<String>> parseParameters() {
    List<Method.Parameter<String>> parameters = new ArrayList<>();
    parameters.add(parseParameter());
    while (isCurrentTokenTypeOf(COMMA)) {
      expectAndConsume(COMMA);
      parameters.add(parseParameter());
    }
    return parameters;
  }

  /** Parameter -> Type IDENT */
  private Method.Parameter<String> parseParameter() {
    Type<String> type = parseType();
    String identifier = expectAndConsumeAndReturnValue(IDENT);
    return new Method.Parameter<>(type, identifier);
  }

  /** Type -> BasicType ([])* */
  private Type<String> parseType() {
    // Only later call is in parseLocalVariableDeclarationStatement()
    // parseType() does not recurse however, so we are safe.
    String type = parseBasicType();
    int dimension = 0;
    while (isCurrentTokenTypeOf(LBRACK) && isCurrentTokenNotTypeOf(EOF)) {
      expectAndConsume(LBRACK);
      expectAndConsume(RBRACK);
      dimension++;
    }
    return new Type<>(type, dimension);
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
  private Statement<String> parseStatement() {
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
  private Block<String> parseBlock() {
    List<BlockStatement<String>> blockStatements = new ArrayList<>();
    expectAndConsume(LBRACE);
    while (isCurrentTokenNotTypeOf(RBRACE) && isCurrentTokenNotTypeOf(EOF)) {
      blockStatements.add(parseBlockStatement());
    }
    expectAndConsume(RBRACE);
    return new Block<>(blockStatements);
  }

  /** BlockStatement -> Statement | LocalVariableDeclarationStatement */
  private BlockStatement<String> parseBlockStatement() {
    if (currentToken.isOneOf(INT, BOOLEAN, VOID)
        || matchCurrentAndLookAhead(IDENT, LBRACK, RBRACK)
        || matchCurrentAndLookAhead(IDENT, IDENT)) {
      return parseLocalVariableDeclarationStatement();
    } else {
      return parseStatement();
    }
  }

  /** LocalVariableDeclarationStatement -> Type IDENT (= Expression)? ; */
  private BlockStatement<String> parseLocalVariableDeclarationStatement() {
    Type<String> type = parseType();
    String identifier = expectAndConsumeAndReturnValue(IDENT);
    Expression<String> expression = null;
    if (isCurrentTokenTypeOf(ASSIGN)) {
      expectAndConsume(ASSIGN);
      expression = parseExpression();
    }
    expectAndConsume(SEMICOLON);
    return new Statement.Variable<>(type, identifier, expression);
  }

  /** EmptyStatement -> ; */
  private Statement<String> parseEmptyStatement() {
    expectAndConsume(SEMICOLON);
    return new Statement.EmptyStatement<>();
  }

  /** WhileStatement -> while ( Expression ) Statement */
  private Statement<String> parseWhileStatement() {
    expectAndConsume(WHILE);
    expectAndConsume(LPAREN);
    Expression<String> condition = parseExpression();
    expectAndConsume(RPAREN);
    Statement<String> body = parseStatement();
    return new Statement.While<>(condition, body);
  }

  /** IfStatement -> if ( Expression ) Statement (else Statement)? */
  private Statement<String> parseIfStatement() {
    expectAndConsume(IF);
    expectAndConsume(LPAREN);
    Expression<String> condition = parseExpression();
    expectAndConsume(RPAREN);
    Statement<String> then = parseStatement();
    Statement<String> else_ = null;
    if (isCurrentTokenTypeOf(ELSE)) {
      expectAndConsume(ELSE);
      else_ = parseStatement();
    }
    return new Statement.If<>(condition, then, else_);
  }

  /** ExpressionStatement -> Expression ; */
  private Statement<String> parseExpressionStatement() {
    Expression<String> expression = parseExpression();
    expectAndConsume(SEMICOLON);
    return new Statement.ExpressionStatement<>(expression);
  }

  /** ReturnStatement -> return Expression? ; */
  private Statement<String> parseReturnStatement() {
    expectAndConsume(RETURN);
    Expression<String> expression = null;
    if (isCurrentTokenNotTypeOf(SEMICOLON)) {
      expression = parseExpression();
    }
    expectAndConsume(SEMICOLON);
    if (null == expression) {
      return new Statement.Return<>();
    }
    return new Statement.Return<>(expression);
  }

  /** Expression is parsed with Precedence Climbing */
  private Expression<String> parseExpression() {
    return parseExpressionWithPrecedenceClimbing(0);
  }

  private Expression<String> parseExpressionWithPrecedenceClimbing(int minPrecedence) {
    // This is the other method that could possibly blow up the stack,
    // which we can do nothing about.
    Expression<String> result = parseUnaryExpression();
    while (isCurrentTokenBinaryOperator()
        && isOperatorPrecedenceGreaterOrEqualThan(minPrecedence)) {
      Expression.BinOp operator = getBinaryOperator(currentToken);
      int precedence = currentToken.precedence();
      if (currentToken.associativity() == LEFT) {
        precedence++;
      }
      consumeToken();
      Expression<String> rhs = parseExpressionWithPrecedenceClimbing(precedence);

      result = new Expression.BinaryOperatorExpression<>(operator, result, rhs);
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
  private Expression<String> parseUnaryExpression() {
    if (currentToken.isOneOf(NOT, SUB)) {
      Expression.UnOp operator = getUnaryOperator(currentToken);
      consumeToken();
      return new Expression.UnaryOperatorExpression<>(operator, parseUnaryExpression());
    }
    return parsePostfixExpression();
  }

  /** PostfixExpression -> PrimaryExpression (PostfixOp)* */
  private Expression<String> parsePostfixExpression() {
    Expression<String> primaryExpression = parsePrimaryExpression();
    while ((isCurrentTokenTypeOf(LBRACK) || isCurrentTokenTypeOf(PERIOD))
        && isCurrentTokenNotTypeOf(EOF)) {
      primaryExpression = parsePostfixOp(primaryExpression);
    }
    return primaryExpression;
  }

  /** PostfixOp -> MethodInvocation | FieldAccess | ArrayAccess */
  private Expression<String> parsePostfixOp(Expression<String> lhs) {
    switch (currentToken.terminal) {
      case PERIOD:
        return parseDotIdentFieldAccessMethodInvocation(lhs);
      case LBRACK:
        return parseArrayAccess(lhs);
      default:
        return unexpectCurrentToken(PERIOD, LBRACK);
    }
  }

  /** DotIdentFieldAccessMethodInvocation -> . IDENT (MethodInvocation)? */
  private Expression<String> parseDotIdentFieldAccessMethodInvocation(Expression<String> lhs) {
    expectAndConsume(PERIOD);
    String identifier = expectAndConsumeAndReturnValue(IDENT);
    // is it FieldAccess (false) or MethodInvocation (true)?
    if (isCurrentTokenTypeOf(LPAREN)) {
      return parseMethodInvocation(lhs, identifier);
    }
    return new Expression.FieldAccessExpression<>(lhs, identifier);
  }

  /** MethodInvocation -> ( Arguments ) */
  private Expression<String> parseMethodInvocation(Expression<String> lhs, String identifier) {
    expectAndConsume(LPAREN);
    List<Expression<String>> arguments = parseArguments();
    expectAndConsume(RPAREN);
    return new Expression.MethodCallExpression<>(lhs, identifier, arguments);
  }

  /** ArrayAccess -> [ Expression ] */
  private Expression<String> parseArrayAccess(Expression<String> array) {
    expectAndConsume(LBRACK);
    Expression<String> index = parseExpression();
    expectAndConsume(RBRACK);
    return new Expression.ArrayAccessExpression<>(array, index);
  }

  /** Arguments -> (Expression (,Expression)*)? */
  private List<Expression<String>> parseArguments() {
    List<Expression<String>> arguments = new ArrayList<>();
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
  private Expression<String> parsePrimaryExpression() {
    Expression<String> primaryExpression = null;
    switch (currentToken.terminal) {
      case NULL:
        expectAndConsume(NULL);
        primaryExpression = new Expression.VariableExpression<>("null");
        break;
      case FALSE:
        expectAndConsume(FALSE);
        primaryExpression = new Expression.BooleanLiteralExpression<>(false);
        break;
      case TRUE:
        expectAndConsume(TRUE);
        primaryExpression = new Expression.BooleanLiteralExpression<>(true);
        break;
      case INTEGER_LITERAL:
        String literal = expectAndConsumeAndReturnValue(INTEGER_LITERAL);
        primaryExpression = new Expression.IntegerLiteralExpression<>(literal);
        break;
      case IDENT:
        String identifier = expectAndConsumeAndReturnValue(IDENT);
        List<Expression<String>> arguments;
        if (isCurrentTokenTypeOf(LPAREN)) {
          expectAndConsume(LPAREN);
          arguments = parseArguments();
          expectAndConsume(RPAREN);
          primaryExpression =
              new Expression.MethodCallExpression<>(THIS_EXPR, identifier, arguments);
        } else {
          primaryExpression = new Expression.FieldAccessExpression<>(THIS_EXPR, identifier);
        }
        break;
      case THIS:
        expectAndConsume(THIS);
        primaryExpression = new Expression.VariableExpression<>("this");
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
  private Expression<String> parseNewObjectArrayExpression() {
    expectAndConsume(NEW);
    switch (currentToken.terminal) {
      case INT:
        expectAndConsume(INT);
        return parseNewArrayExpression(new Type<>("int", 0));
      case BOOLEAN:
        expectAndConsume(BOOLEAN);
        return parseNewArrayExpression(new Type<>("boolean", 0));
      case VOID:
        expectAndConsume(VOID);
        return parseNewArrayExpression(new Type<>("void", 0));
      case IDENT:
        String identifier = expectAndConsumeAndReturnValue(IDENT);
        switch (currentToken.terminal) {
          case LPAREN:
            return parseNewObjectExpression(identifier);
          case LBRACK:
            return parseNewArrayExpression(new Type<>(identifier, 0));
          default:
            return unexpectCurrentToken(LPAREN, LBRACK);
        }
      default:
        return unexpectCurrentToken(INT, BOOLEAN, VOID, IDENT);
    }
  }

  /** NewObjectExpression -> ( ) */
  private Expression<String> parseNewObjectExpression(String type) {
    expectAndConsume(LPAREN);
    expectAndConsume(RPAREN);
    return new Expression.NewObjectExpression<>(type);
  }

  /** NewArrayExpression -> [ Expression ] ([])* */
  private Expression<String> parseNewArrayExpression(Type<String> type) {
    expectAndConsume(LBRACK);
    Expression<String> index = parseExpression();
    expectAndConsume(RBRACK);
    while (matchCurrentAndLookAhead(LBRACK, RBRACK)) {
      expectAndConsume(LBRACK);
      expectAndConsume(RBRACK);
    }
    return new Expression.NewArrayExpression<>(type, index);
  }
}
