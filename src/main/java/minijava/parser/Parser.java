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
    List<Member> members = new ArrayList<>();
    expectAndConsume(CLASS);
    identifier = expectAndConsumeAndReturnValue(IDENT);
    expectAndConsume(LBRACE);
    while (isCurrentTokenNotTypeOf(RBRACE) && isCurrentTokenNotTypeOf(EOF)) {
      members.add(parsePublicClassMember());
    }
    expectAndConsume(RBRACE);
    return new Class(identifier, members);
  }

  /** PublicClassMember -> public ClassMember */
  private Member parsePublicClassMember() {
    expectAndConsume(PUBLIC);
    return parseClassMember();
  }

  /** ClassMember -> MainMethod | FieldOrMethod */
  private Member parseClassMember() {
    Member member;
    switch (currentToken.terminal) {
      case STATIC:
        member = parseMainMethod();
        break;
      default:
        member = parseTypeIdentFieldOrMethod();
        break;
    }
    return member;
  }

  /** MainMethod -> static void IDENT ( String [] IDENT ) Block */
  private Member parseMainMethod() {
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
    return new Member.Method(
        true, new Type("void", 0), name, new ArrayList<Member.Parameter>(), block);
  }

  /** TypeIdentFieldOrMethod -> Type IDENT FieldOrMethod */
  private Member parseTypeIdentFieldOrMethod() {
    Type type = parseType();
    String name = expectAndConsumeAndReturnValue(IDENT);
    return parseFieldOrMethod(type, name);
  }

  /** FieldOrMethod -> ; | Method */
  private Member parseFieldOrMethod(Type type, String name) {
    if (isCurrentTokenTypeOf(SEMICOLON)) {
      expectAndConsume(SEMICOLON);
      return new Member.Field(type, name);
    } else {
      return parseMethod(type, name);
    }
  }

  /** Method -> ( Parameters? ) Block */
  private Member parseMethod(Type type, String name) {
    List<Member.Parameter> parameters = new ArrayList<>();
    expectAndConsume(LPAREN);
    if (isCurrentTokenNotTypeOf(RPAREN)) {
      parameters = parseParameters();
    }
    expectAndConsume(RPAREN);
    Block block = parseBlock();
    return new Member.Method(false, type, name, parameters, block);
  }

  /** Parameters -> Parameter | Parameter , Parameters */
  private List<Member.Parameter> parseParameters() {
    List<Member.Parameter> parameters = new ArrayList<>();
    parameters.add(parseParameter());
    while (isCurrentTokenTypeOf(COMMA)) {
      expectAndConsume(COMMA);
      parameters.add(parseParameter());
    }
    return parameters;
  }

  /** Parameter -> Type IDENT */
  private Member.Parameter parseParameter() {
    Type type = parseType();
    String identifier = expectAndConsumeAndReturnValue(IDENT);
    return new Member.Parameter(type, identifier);
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
      Terminal currentOperator = currentToken.terminal;
      int precedence = currentToken.precedence();
      if (currentToken.associativity() == LEFT) {
        precedence++;
      }
      consumeToken();
      Expression rhs = parseExpressionWithPrecedenceClimbing(precedence);

      switch (currentOperator) {
        case ASSIGN:
          result = new Expression.BinaryOperatorExpression(Expression.BinOp.ASSIGN, result, rhs);
          break;
        case OR:
          result = new Expression.BinaryOperatorExpression(Expression.BinOp.OR, result, rhs);
          break;
        case AND:
          result = new Expression.BinaryOperatorExpression(Expression.BinOp.AND, result, rhs);
          break;
        case EQL:
          result = new Expression.BinaryOperatorExpression(Expression.BinOp.EQ, result, rhs);
          break;
        case NEQ:
          result = new Expression.BinaryOperatorExpression(Expression.BinOp.NEQ, result, rhs);
          break;
        case LSS:
          result = new Expression.BinaryOperatorExpression(Expression.BinOp.LT, result, rhs);
          break;
        case LEQ:
          result = new Expression.BinaryOperatorExpression(Expression.BinOp.LEQ, result, rhs);
          break;
        case GTR:
          result = new Expression.BinaryOperatorExpression(Expression.BinOp.GT, result, rhs);
          break;
        case GEQ:
          result = new Expression.BinaryOperatorExpression(Expression.BinOp.GEQ, result, rhs);
          break;
        case ADD:
          result = new Expression.BinaryOperatorExpression(Expression.BinOp.PLUS, result, rhs);
          break;
        case SUB:
          result = new Expression.BinaryOperatorExpression(Expression.BinOp.MINUS, result, rhs);
          break;
        case MUL:
          result = new Expression.BinaryOperatorExpression(Expression.BinOp.MULTIPLY, result, rhs);
          break;
        case DIV:
          result = new Expression.BinaryOperatorExpression(Expression.BinOp.DIVIDE, result, rhs);
          break;
        case MOD:
          result = new Expression.BinaryOperatorExpression(Expression.BinOp.MODULO, result, rhs);
          break;
      }
    }
    return result;
  }

  /** UnaryExpression -> PostfixExpression | (! | -) UnaryExpression */
  private void parseUnaryExpression() {
    while (currentToken.isOneOf(NOT, SUB)) {
      consumeToken();
    }
    parsePostfixExpression();
  }

  /** PostfixExpression -> PrimaryExpression (PostfixOp)* */
  private void parsePostfixExpression() {
    parsePrimaryExpression();
    while ((isCurrentTokenTypeOf(LBRACK) || isCurrentTokenTypeOf(PERIOD))
        && isCurrentTokenNotTypeOf(EOF)) {
      parsePostfixOp();
    }
  }

  /** PostfixOp -> MethodInvocation | FieldAccess | ArrayAccess */
  private void parsePostfixOp() {
    switch (currentToken.terminal) {
      case PERIOD:
        parseDotIdentFieldAccessMethodInvocation();
        break;
      case LBRACK:
        parseArrayAccess();
        break;
      default:
        unexpectCurrentToken(PERIOD, LBRACK);
    }
  }

  /** DotIdentFieldAccessMethodInvocation -> . IDENT (MethodInvocation)? */
  private void parseDotIdentFieldAccessMethodInvocation() {
    expectAndConsume(PERIOD);
    expectAndConsume(IDENT);
    // is it FieldAccess (false) or MethodInvocation (true)?
    if (isCurrentTokenTypeOf(LPAREN)) {
      parseMethodInvocation();
    }
  }

  /** MethodInvocation -> ( Arguments ) */
  private void parseMethodInvocation() {
    expectAndConsume(LPAREN);
    parseArguments();
    expectAndConsume(RPAREN);
  }

  /** ArrayAccess -> [ Expression ] */
  private void parseArrayAccess() {
    expectAndConsume(LBRACK);
    parseExpression();
    expectAndConsume(RBRACK);
  }

  /** Arguments -> (Expression (,Expression)*)? */
  private void parseArguments() {
    if (isCurrentTokenNotTypeOf(RPAREN)) {
      parseExpression();
      while (isCurrentTokenTypeOf(COMMA) && isCurrentTokenNotTypeOf(EOF)) {
        expectAndConsume(COMMA);
        parseExpression();
      }
    }
  }

  /**
   * PrimaryExpression -> null | false | true | INTEGER_LITERAL | IDENT | IDENT ( Arguments ) | this
   * | ( Expression ) | NewObjectArrayExpression
   */
  private void parsePrimaryExpression() {
    switch (currentToken.terminal) {
      case NULL:
        expectAndConsume(NULL);
        break;
      case FALSE:
        expectAndConsume(FALSE);
        break;
      case TRUE:
        expectAndConsume(TRUE);
        break;
      case INTEGER_LITERAL:
        expectAndConsume(INTEGER_LITERAL);
        break;
      case IDENT:
        expectAndConsume(IDENT);
        if (isCurrentTokenTypeOf(LPAREN)) {
          expectAndConsume(LPAREN);
          parseArguments();
          expectAndConsume(RPAREN);
        }
        break;
      case THIS:
        expectAndConsume(THIS);
        break;
      case LPAREN:
        expectAndConsume(LPAREN);
        parseExpression();
        expectAndConsume(RPAREN);
        break;
      case NEW:
        parseNewObjectArrayExpression();
        break;
      default:
        unexpectCurrentToken(NULL, FALSE, TRUE, INTEGER_LITERAL, IDENT, THIS, LPAREN, NEW);
    }
  }

  /** NewObjectArrayExpression -> BasicType NewArrayExpression | IDENT NewObjectExpression */
  private void parseNewObjectArrayExpression() {
    expectAndConsume(NEW);
    switch (currentToken.terminal) {
      case INT:
        expectAndConsume(INT);
        parseNewArrayExpression();
        break;
      case BOOLEAN:
        expectAndConsume(BOOLEAN);
        parseNewArrayExpression();
        break;
      case VOID:
        expectAndConsume(VOID);
        parseNewArrayExpression();
        break;
      case IDENT:
        expectAndConsume(IDENT);
        switch (currentToken.terminal) {
          case LPAREN:
            parseNewObjectExpression();
            break;
          case LBRACK:
            parseNewArrayExpression();
            break;
          default:
            unexpectCurrentToken(LPAREN, LBRACK);
        }
        break;
      default:
        unexpectCurrentToken(INT, BOOLEAN, VOID, IDENT);
    }
  }

  /** NewObjectExpression -> ( ) */
  private void parseNewObjectExpression() {
    expectAndConsume(LPAREN);
    expectAndConsume(RPAREN);
  }

  /** NewArrayExpression -> [ Expression ] ([])* */
  private void parseNewArrayExpression() {
    expectAndConsume(LBRACK);
    parseExpression();
    expectAndConsume(RBRACK);
    while (matchCurrentAndLookAhead(LBRACK, RBRACK)) {
      expectAndConsume(LBRACK);
      expectAndConsume(RBRACK);
    }
  }
}
