package minijava.parser;

import static minijava.token.Terminal.*;
import static minijava.token.Terminal.Associativity.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import minijava.ast.ClassDeclaration;
import minijava.ast.Program;
import minijava.ast.classmember.ClassMember;
import minijava.ast.classmember.FieldClassMember;
import minijava.ast.classmember.MethodClassMember;
import minijava.ast.classmember.Parameter;
import minijava.ast.statement.Block;
import minijava.ast.type.ArrayType;
import minijava.ast.type.ClassType;
import minijava.ast.type.Type;
import minijava.ast.type.VoidType;
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
    List<ClassDeclaration> classDeclarions = new ArrayList<>();
    while (isCurrentTokenNotTypeOf(EOF)) {
      classDeclarions.add(parseClassDeclaration());
    }
    expectAndConsume(EOF);
    return new Program(classDeclarions);
  }

  /** ClassDeclaration -> class IDENT { PublicClassMember* } */
  private ClassDeclaration parseClassDeclaration() {
    String ident;
    List<ClassMember> classMembers = new ArrayList<>();
    expectAndConsume(CLASS);
    ident = expectAndConsumeAndReturnValue(IDENT);
    expectAndConsume(LBRACE);
    while (isCurrentTokenNotTypeOf(RBRACE) && isCurrentTokenNotTypeOf(EOF)) {
      classMembers.add(parsePublicClassMember());
    }
    expectAndConsume(RBRACE);
    return new ClassDeclaration(ident, classMembers);
  }

  /** PublicClassMember -> public ClassMember */
  private ClassMember parsePublicClassMember() {
    expectAndConsume(PUBLIC);
    return parseClassMember();
  }

  /** ClassMember -> MainMethod | FieldOrMethod */
  private ClassMember parseClassMember() {
    ClassMember classMember;
    switch (currentToken.terminal) {
      case STATIC:
        classMember = parseMainMethod();
        break;
      default:
        classMember = parseTypeIdentFieldOrMethod();
        break;
    }
    return classMember;
  }

  /** MainMethod -> static void IDENT ( String [] IDENT ) Block */
  private ClassMember parseMainMethod() {
    expectAndConsume(STATIC);
    expectAndConsume(VOID);
    String name = expectAndConsumeAndReturnValue(IDENT);
    expectAndConsume(LPAREN);
    expectAndConsume(IDENT, "String");
    expectAndConsume(LBRACK);
    expectAndConsume(RBRACK);
    expectAndConsume(IDENT);
    expectAndConsume(RPAREN);
    parseBlock();
    //return new MethodClassMember(true, new VoidType(), name, );
  }

  /** TypeIdentFieldOrMethod -> Type IDENT FieldOrMethod */
  private ClassMember parseTypeIdentFieldOrMethod() {
    Type type = parseType();
    String name = expectAndConsumeAndReturnValue(IDENT);
    return parseFieldOrMethod(type, name);
  }

  /** FieldOrMethod -> ; | Method */
  private ClassMember parseFieldOrMethod(Type type, String name) {
    if (isCurrentTokenTypeOf(SEMICOLON)) {
      expectAndConsume(SEMICOLON);
      return new FieldClassMember(type, name);
    } else {
      return parseMethod(type, name);
    }
  }

  /** Method -> ( Parameters? ) Block */
  private ClassMember parseMethod(Type type, String name) {
    List<Parameter> parameters = new ArrayList<>();
    expectAndConsume(LPAREN);
    if (isCurrentTokenNotTypeOf(RPAREN)) {
      parameters = parseParameters();
    }
    expectAndConsume(RPAREN);
    Block block = parseBlock();
    return new MethodClassMember(false, type, name, parameters, block);
  }

  /** Parameters -> Parameter | Parameter , Parameters */
  private void parseParameters() {
    parseParameter();
    while (isCurrentTokenTypeOf(COMMA)) {
      expectAndConsume(COMMA);
      parseParameter();
    }
  }

  /** Parameter -> Type IDENT */
  private void parseParameter() {
    parseType();
    expectAndConsume(IDENT);
  }

  /** Type -> BasicType ([])* */
  private void parseType() {
    // Only later call is in parseLocalVariableDeclarationStatement()
    // parseType() does not recurse however, so we are safe.
    parseBasicType();
    while (isCurrentTokenTypeOf(LBRACK) && isCurrentTokenNotTypeOf(EOF)) {
      expectAndConsume(LBRACK);
      expectAndConsume(RBRACK);
    }
  }

  /** BasicType -> int | boolean | void | IDENT */
  private void parseBasicType() {
    switch (currentToken.terminal) {
      case INT:
        expectAndConsume(INT);
        break;
      case BOOLEAN:
        expectAndConsume(BOOLEAN);
        break;
      case VOID:
        expectAndConsume(VOID);
        break;
      case IDENT:
        expectAndConsume(IDENT);
        break;
      default:
        unexpectCurrentToken(INT, BOOLEAN, VOID, IDENT);
    }
  }

  /**
   * Statement -> Block | EmptyStatement | IfStatement | ExpressionStatement | WhileStatement |
   * ReturnStatement
   */
  private void parseStatement() {
    // Also called from BlockStatement, IfStatement and WhileStatement.
    // There is possibility for endless recursion here, but that's OK
    // because it's not tail recursive (which we have to optimize away
    // with loops). A nested sequence of blocks (e.g. {{{...{{{ ; }}}...}}})
    // will blow the parser up and there's nothing we can do about it,
    // except for allocating more stack space/switching to a table-based
    // parser.
    switch (currentToken.terminal) {
      case LBRACE:
        parseBlock();
        break;
      case SEMICOLON:
        parseEmptyStatement();
        break;
      case IF:
        parseIfStatement();
        break;
      case WHILE:
        parseWhileStatement();
        break;
      case RETURN:
        parseReturnStatement();
        break;
      default:
        parseExpressionStatement();
        break;
    }
  }

  /** Block -> { BlockStatement* } */
  private void parseBlock() {
    expectAndConsume(LBRACE);
    while (isCurrentTokenNotTypeOf(RBRACE) && isCurrentTokenNotTypeOf(EOF)) {
      parseBlockStatement();
    }
    expectAndConsume(RBRACE);
  }

  /** BlockStatement -> Statement | LocalVariableDeclarationStatement */
  private void parseBlockStatement() {
    if (currentToken.isOneOf(INT, BOOLEAN, VOID)
        || matchCurrentAndLookAhead(IDENT, LBRACK, RBRACK)
        || matchCurrentAndLookAhead(IDENT, IDENT)) {
      parseLocalVariableDeclarationStatement();
    } else {
      parseStatement();
    }
  }

  /** LocalVariableDeclarationStatement -> Type IDENT (= Expression)? ; */
  private void parseLocalVariableDeclarationStatement() {
    parseType();
    expectAndConsume(IDENT);
    if (isCurrentTokenTypeOf(ASSIGN)) {
      expectAndConsume(ASSIGN);
      parseExpression();
    }
    expectAndConsume(SEMICOLON);
  }

  /** EmptyStatement -> ; */
  private void parseEmptyStatement() {
    expectAndConsume(SEMICOLON);
  }

  /** WhileStatement -> while ( Expression ) Statement */
  private void parseWhileStatement() {
    expectAndConsume(WHILE);
    expectAndConsume(LPAREN);
    parseExpression();
    expectAndConsume(RPAREN);
    parseStatement();
  }

  /** IfStatement -> if ( Expression ) Statement (else Statement)? */
  private void parseIfStatement() {
    expectAndConsume(IF);
    expectAndConsume(LPAREN);
    parseExpression();
    expectAndConsume(RPAREN);
    parseStatement();
    if (isCurrentTokenTypeOf(ELSE)) {
      expectAndConsume(ELSE);
      parseStatement();
    }
  }

  /** ExpressionStatement -> Expression ; */
  private void parseExpressionStatement() {
    parseExpression();
    expectAndConsume(SEMICOLON);
  }

  /** ReturnStatement -> return Expression? ; */
  private void parseReturnStatement() {
    expectAndConsume(RETURN);
    if (isCurrentTokenNotTypeOf(SEMICOLON)) {
      parseExpression();
    }
    expectAndConsume(SEMICOLON);
  }

  /** Expression is parsed with Precedence Climbing */
  private void parseExpression() {
    parseExpressionWithPrecedenceClimbing(0);
  }

  private void parseExpressionWithPrecedenceClimbing(int minPrecedence) {
    // This is the other method that could possibly blow up the stack,
    // which we can do nothing about.
    parseUnaryExpression();
    while (isCurrentTokenBinaryOperator()
        && isOperatorPrecedenceGreaterOrEqualThan(minPrecedence)) {
      int precedence = currentToken.precedence();
      if (currentToken.associativity() == LEFT) {
        precedence++;
      }
      consumeToken();
      parseExpressionWithPrecedenceClimbing(precedence);
    }
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
