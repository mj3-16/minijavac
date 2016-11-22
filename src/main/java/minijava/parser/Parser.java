package minijava.parser;

import static minijava.token.Terminal.*;
import static minijava.token.Terminal.Associativity.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import minijava.ast.*;
import minijava.ast.Class;
import minijava.token.Terminal;
import minijava.token.Token;
import minijava.util.LookAheadIterator;
import minijava.util.SourcePosition;
import minijava.util.SourceRange;

public class Parser {
  private static final Token EOF_TOKEN = new Token(EOF, SourceRange.FIRST_CHAR, null);
  private static final Expression THIS_EXPR =
      Expression.ReferenceTypeLiteral.this_(SourceRange.FIRST_CHAR);
  private final LookAheadIterator<Token> tokens;
  private Token currentToken;

  public Parser(Iterator<Token> tokens) {
    this.tokens = new LookAheadIterator<>(tokens);
  }

  private Token consumeToken() {
    Token eaten = currentToken;
    if (tokens.hasNext()) {
      currentToken = tokens.next();
    } else if (this.currentToken == null) {
      currentToken = EOF_TOKEN;
    } else {
      // Just pretend there are inifinitely many single byte EOF tokens
      currentToken = new Token(EOF, new SourceRange(currentToken.range().end, 1), null);
    }
    return eaten;
  }

  private Token expectAndConsume(Terminal terminal) {
    if (currentToken.terminal != terminal) {
      throw new ParserError(
          Thread.currentThread().getStackTrace()[2].getMethodName(), terminal, currentToken);
    }
    return consumeToken();
  }

  private Token expectAndConsume(Terminal terminal, String value) {
    // some sanity checks, in other cases using this method makes no sense
    assert terminal.hasLexval();
    assert value != null;

    if (currentToken.terminal != terminal || !value.equals(currentToken.lexval)) {
      throw new ParserError(
          Thread.currentThread().getStackTrace()[2].getMethodName(), terminal, value, currentToken);
    }
    return consumeToken();
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

  public Program parse() {
    consumeToken();
    return parseProgramm();
  }

  /** Program -> ClassDeclaration* */
  private Program parseProgramm() {
    SourcePosition begin = SourcePosition.BEGIN_OF_PROGRAM;
    List<Class> classes = new ArrayList<>();
    while (isCurrentTokenNotTypeOf(EOF)) {
      classes.add(parseClassDeclaration());
    }
    SourcePosition end = expectAndConsume(EOF).range().end;
    return new Program(classes, new SourceRange(begin, end));
  }

  /** ClassDeclaration -> class IDENT { PublicClassMember* } */
  private Class parseClassDeclaration() {
    SourcePosition begin = expectAndConsume(CLASS).range().begin;
    Token identifier = expectAndConsume(IDENT);
    expectAndConsume(LBRACE);
    List<Field> fields = new ArrayList<>();
    List<Method> methods = new ArrayList<>();
    while (isCurrentTokenNotTypeOf(RBRACE) && isCurrentTokenNotTypeOf(EOF)) {
      parsePublicClassMember(fields, methods);
    }
    SourcePosition end = expectAndConsume(RBRACE).range().end;
    return new Class(identifier.lexval, fields, methods, new SourceRange(begin, end));
  }

  /** PublicClassMember -> public ClassMember */
  private void parsePublicClassMember(List<Field> fields, List<Method> methods) {
    SourcePosition begin = expectAndConsume(PUBLIC).range().begin;
    parseClassMember(fields, methods, begin);
  }

  /** ClassMember -> MainMethod | FieldOrMethod */
  private void parseClassMember(List<Field> fields, List<Method> methods, SourcePosition begin) {
    switch (currentToken.terminal) {
      case STATIC:
        methods.add(parseMainMethod(begin));
        break;
      default:
        parseTypeIdentFieldOrMethod(fields, methods, begin);
        break;
    }
  }

  /** MainMethod -> static void IDENT ( String [] IDENT ) Block */
  private Method parseMainMethod(SourcePosition begin) {
    expectAndConsume(STATIC);
    Token void_ = expectAndConsume(VOID);
    Type voidType = new Type(new Ref<>("void"), 0, void_.range());
    Token name = expectAndConsume(IDENT);
    expectAndConsume(LPAREN);
    SourcePosition typeBegin = expectAndConsume(IDENT, "String").range().begin;
    expectAndConsume(LBRACK);
    SourcePosition typeEnd = expectAndConsume(RBRACK).range().end;
    Type parameterType = new Type(new Ref<>("String"), 1, new SourceRange(typeBegin, typeEnd));
    Token ident = expectAndConsume(IDENT);
    expectAndConsume(RPAREN);
    Block block = parseBlock();
    Method.Parameter parameter =
        new Method.Parameter(
            parameterType, ident.lexval, new SourceRange(typeBegin, ident.range().end));
    return new Method(
        true,
        voidType,
        name.lexval,
        Arrays.asList(parameter),
        block,
        new SourceRange(begin, block.range().end));
  }

  /** TypeIdentFieldOrMethod -> Type IDENT FieldOrMethod */
  private void parseTypeIdentFieldOrMethod(
      List<Field> fields, List<Method> methods, SourcePosition begin) {
    Type type = parseType();
    String name = expectAndConsume(IDENT).lexval;
    parseFieldOrMethod(type, name, fields, methods, begin);
  }

  /** FieldOrMethod -> ; | Method */
  private void parseFieldOrMethod(
      Type type, String name, List<Field> fields, List<Method> methods, SourcePosition begin) {
    if (isCurrentTokenTypeOf(SEMICOLON)) {
      SourcePosition end = expectAndConsume(SEMICOLON).range().end;
      fields.add(new Field(type, name, new SourceRange(begin, end)));
    } else {
      methods.add(parseMethod(type, name, begin));
    }
  }

  /** Method -> ( Parameters? ) Block */
  private Method parseMethod(Type type, String name, SourcePosition begin) {
    List<Method.Parameter> parameters = new ArrayList<>();
    expectAndConsume(LPAREN);
    if (isCurrentTokenNotTypeOf(RPAREN)) {
      parameters = parseParameters();
    }
    expectAndConsume(RPAREN);
    Block block = parseBlock();
    return new Method(
        false, type, name, parameters, block, new SourceRange(begin, block.range().end));
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
    Token identifier = expectAndConsume(IDENT);
    return new Method.Parameter(
        type, identifier.lexval, new SourceRange(type.range().begin, identifier.range().end));
  }

  /** Type -> BasicType ([])* */
  private Type parseType() {
    // Only later call is in parseLocalVariableDeclarationStatement()
    // parseType() does not recurse however, so we are safe.
    SourcePosition begin = currentToken.range().begin;
    String type = parseBasicType();
    int dimension = 0;
    while (isCurrentTokenTypeOf(LBRACK) && isCurrentTokenNotTypeOf(EOF)) {
      expectAndConsume(LBRACK);
      expectAndConsume(RBRACK);
      dimension++;
    }
    SourcePosition end = currentToken.range().end;
    return new Type(new Ref<>(type), dimension, new SourceRange(begin, end));
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
        return expectAndConsume(IDENT).lexval;
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
    SourcePosition begin = expectAndConsume(LBRACE).range().begin;
    while (isCurrentTokenNotTypeOf(RBRACE) && isCurrentTokenNotTypeOf(EOF)) {
      blockStatements.add(parseBlockStatement());
    }
    SourcePosition end = expectAndConsume(RBRACE).range().end;
    return new Block(blockStatements, new SourceRange(begin, end));
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
    SourcePosition begin = type.range().end;
    String identifier = expectAndConsume(IDENT).lexval;
    Expression expression = null;
    if (isCurrentTokenTypeOf(ASSIGN)) {
      expectAndConsume(ASSIGN);
      expression = parseExpression();
    }
    SourcePosition end = expectAndConsume(SEMICOLON).range().end;
    return new Statement.Variable(type, identifier, expression, new SourceRange(begin, end));
  }

  /** EmptyStatement -> ; */
  private Statement parseEmptyStatement() {
    SourceRange range = expectAndConsume(SEMICOLON).range();
    return new Statement.Empty(range);
  }

  /** WhileStatement -> while ( Expression ) Statement */
  private Statement parseWhileStatement() {
    SourcePosition begin = expectAndConsume(WHILE).range().begin;
    expectAndConsume(LPAREN);
    Expression condition = parseExpression();
    expectAndConsume(RPAREN);
    Statement body = parseStatement();
    return new Statement.While(condition, body, new SourceRange(begin, body.range().end));
  }

  /** IfStatement -> if ( Expression ) Statement (else Statement)? */
  private Statement parseIfStatement() {
    SourcePosition begin = expectAndConsume(IF).range().begin;
    expectAndConsume(LPAREN);
    Expression condition = parseExpression();
    expectAndConsume(RPAREN);
    Statement then = parseStatement();
    SourcePosition end = then.range().end;
    Statement else_ = null;
    if (isCurrentTokenTypeOf(ELSE)) {
      expectAndConsume(ELSE);
      else_ = parseStatement();
      end = else_.range().end;
    }
    return new Statement.If(condition, then, else_, new SourceRange(begin, end));
  }

  /** ExpressionStatement -> Expression ; */
  private Statement parseExpressionStatement() {
    Expression expression = parseExpression();
    SourcePosition begin = expression.range().begin;
    SourcePosition end = expectAndConsume(SEMICOLON).range().end;
    return new Statement.ExpressionStatement(expression, new SourceRange(begin, end));
  }

  /** ReturnStatement -> return Expression? ; */
  private Statement parseReturnStatement() {
    SourcePosition begin = expectAndConsume(RETURN).range().begin;
    Expression expression = null;
    if (isCurrentTokenNotTypeOf(SEMICOLON)) {
      expression = parseExpression();
    }
    SourcePosition end = expectAndConsume(SEMICOLON).range().end;
    return new Statement.Return(expression, new SourceRange(begin, end));
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
      SourcePosition begin = result.range().begin;
      SourcePosition end = rhs.range().end;
      result = new Expression.BinaryOperator(operator, result, rhs, new SourceRange(begin, end));
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
        throw new ParserError(token.range(), "Token is not a BinaryOperator");
    }
  }

  private Expression.UnOp getUnaryOperator(Token token) {
    switch (token.terminal) {
      case NOT:
        return Expression.UnOp.NOT;
      case SUB:
        return Expression.UnOp.NEGATE;
      default:
        throw new ParserError(token.range(), "Token is not an UnaryOperator");
    }
  }

  /** UnaryExpression -> PostfixExpression | (! | -) UnaryExpression */
  private Expression parseUnaryExpression() {
    if (currentToken.isOneOf(NOT, SUB)) {
      Expression.UnOp operator = getUnaryOperator(currentToken);
      SourceRange range = consumeToken().range();
      return new Expression.UnaryOperator(operator, parseUnaryExpression(), range);
    }
    return parsePostfixExpression();
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
  private Expression parseDotIdentFieldAccessMethodInvocation(Expression lhs) {
    expectAndConsume(PERIOD);
    Token identifier = expectAndConsume(IDENT);
    // is it FieldAccess (false) or MethodInvocation (true)?
    if (isCurrentTokenTypeOf(LPAREN)) {
      return parseMethodInvocation(lhs, identifier.lexval);
    }
    SourceRange range = new SourceRange(lhs.range().begin, identifier.range().end);
    return new Expression.FieldAccess(lhs, new Ref<>(identifier.lexval), range);
  }

  /** MethodInvocation -> ( Arguments ) */
  private Expression parseMethodInvocation(Expression lhs, String identifier) {
    expectAndConsume(LPAREN);
    List<Expression> arguments = parseArguments();
    SourcePosition end = expectAndConsume(RPAREN).range().end;
    SourceRange range = new SourceRange(lhs.range().begin, end);
    return new Expression.MethodCall(lhs, new Ref<>(identifier), arguments, range);
  }

  /** ArrayAccess -> [ Expression ] */
  private Expression parseArrayAccess(Expression array) {
    expectAndConsume(LBRACK);
    Expression index = parseExpression();
    SourcePosition end = expectAndConsume(RBRACK).range().end;
    return new Expression.ArrayAccess(array, index, new SourceRange(array.range().begin, end));
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
    SourceRange range;
    switch (currentToken.terminal) {
      case NULL:
        range = expectAndConsume(NULL).range();
        primaryExpression = Expression.ReferenceTypeLiteral.null_(range);
        break;
      case FALSE:
        range = expectAndConsume(FALSE).range();
        primaryExpression = new Expression.BooleanLiteral(false, range);
        break;
      case TRUE:
        range = expectAndConsume(TRUE).range();
        primaryExpression = new Expression.BooleanLiteral(true, range);
        break;
      case INTEGER_LITERAL:
        Token literal = expectAndConsume(INTEGER_LITERAL);
        primaryExpression = new Expression.IntegerLiteral(literal.lexval, literal.range());
        break;
      case IDENT:
        Token identifier = expectAndConsume(IDENT);
        List<Expression> arguments;
        if (isCurrentTokenTypeOf(LPAREN)) {
          expectAndConsume(LPAREN);
          arguments = parseArguments();
          range = new SourceRange(identifier.range().begin, expectAndConsume(RPAREN).range().end);
          primaryExpression =
              new Expression.MethodCall(THIS_EXPR, new Ref<>(identifier.lexval), arguments, range);
        } else {
          primaryExpression =
              new Expression.Variable(new Ref<>(identifier.lexval), identifier.range());
        }
        break;
      case THIS:
        range = expectAndConsume(THIS).range();
        primaryExpression = Expression.ReferenceTypeLiteral.this_(range);
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
    SourcePosition begin = expectAndConsume(NEW).range().begin;
    switch (currentToken.terminal) {
      case INT:
        Token integer = expectAndConsume(INT);
        return parseNewArrayExpression("int", begin, integer.range().begin);
      case BOOLEAN:
        Token bool = expectAndConsume(BOOLEAN);
        return parseNewArrayExpression("boolean", begin, bool.range().begin);
      case VOID:
        Token void_ = expectAndConsume(VOID);
        return parseNewArrayExpression("void", begin, void_.range().begin);
      case IDENT:
        Token identifier = expectAndConsume(IDENT);
        switch (currentToken.terminal) {
          case LPAREN:
            return parseNewObjectExpression(identifier.lexval, begin);
          case LBRACK:
            return parseNewArrayExpression(identifier.lexval, begin, identifier.range().begin);
          default:
            return unexpectCurrentToken(LPAREN, LBRACK);
        }
      default:
        return unexpectCurrentToken(INT, BOOLEAN, VOID, IDENT);
    }
  }

  /** NewObjectExpression -> ( ) */
  private Expression parseNewObjectExpression(String type, SourcePosition begin) {
    expectAndConsume(LPAREN);
    SourcePosition end = expectAndConsume(RPAREN).range().end;
    return new Expression.NewObject(new Ref<>(type), new SourceRange(begin, end));
  }

  /** NewArrayExpression -> [ Expression ] ([])* */
  private Expression parseNewArrayExpression(
      String elementTypeRef, SourcePosition newBegin, SourcePosition typeBegin) {
    expectAndConsume(LBRACK);
    Expression size = parseExpression();
    SourcePosition end = expectAndConsume(RBRACK).range().end;
    int dim = 0;
    while (matchCurrentAndLookAhead(LBRACK, RBRACK)) {
      expectAndConsume(LBRACK);
      end = expectAndConsume(RBRACK).range().end;
      dim++;
    }
    SourceRange typeRange = new SourceRange(typeBegin, end);
    SourceRange newRange = new SourceRange(newBegin, end);
    return new Expression.NewArray(
        new Type(new Ref<>(elementTypeRef), dim, typeRange), size, newRange);
  }
}
