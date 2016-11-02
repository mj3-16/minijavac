package minijava.parser;

import static minijava.token.Terminal.*;

import java.util.Iterator;
import minijava.token.Position;
import minijava.token.Terminal;
import minijava.token.Token;
import minijava.util.LookAheadIterator;

public class Parser {
  private static final Token EOF_TOKEN = new Token(EOF, new Position(0, 0), "");
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
    if (!currentToken.isTerminal(terminal)) {
      throw new ParserError(
          Thread.currentThread().getStackTrace()[2].getMethodName(), terminal, currentToken);
    }
    consumeToken();
  }

  private void expectAndConsume(Terminal terminal, String value) {
    if (!currentToken.isTerminal(terminal) || !currentToken.hasValue(value)) {
      throw new ParserError(
          Thread.currentThread().getStackTrace()[2].getMethodName(), terminal, value, currentToken);
    }
    consumeToken();
  }

  private boolean isCurrentTokenTypeOf(Terminal terminal) {
    if (currentToken.isTerminal(terminal)) {
      return true;
    }
    return false;
  }

  private boolean isCurrentTokenNotTypeOf(Terminal terminal) {
    return !isCurrentTokenTypeOf(terminal);
  }

  private boolean isCurrentTokenBinaryOperator() {
    if (currentToken.isType(TerminalType.OPERATOR)) {
      return true;
    }
    return false;
  }

  private boolean isOperatorPrecedenceGreaterOrEqualThan(int precedence) {
    if (currentToken.terminal.getPrecedence() >= precedence) {
      return true;
    }
    return false;
  }

  private boolean matchCurrentAndLookAhead(Terminal... terminals) {
    for (int i = 0; i < terminals.length; i++) {
      if (!tokens.lookAhead(i).orElse(EOF_TOKEN).isTerminal(terminals[i])) {
        return false;
      }
    }
    return true;
  }

  public void parse() {
    consumeToken();
    parseProgramm();
  }

  /** Program -> ClassDeclaration* */
  private void parseProgramm() {
    while (isCurrentTokenNotTypeOf(EOF)) {
      parseClassDeclaration();
    }
    expectAndConsume(EOF);
  }

  /** ClassDeclaration -> class IDENT { PublicClassMember* } */
  private void parseClassDeclaration() {
    expectAndConsume(CLASS);
    expectAndConsume(IDENT);
    expectAndConsume(LCURLY);
    while (isCurrentTokenNotTypeOf(RCURLY) && isCurrentTokenNotTypeOf(EOF)) {
      parsePublicClassMember();
    }
    expectAndConsume(RCURLY);
  }

  /** PublicClassMember -> public ClassMember */
  private void parsePublicClassMember() {
    expectAndConsume(PUBLIC);
    parseClassMember();
  }

  /** ClassMember -> MainMethod | FieldOrMethod */
  private void parseClassMember() {
    switch (currentToken.terminal) {
      case STATIC:
        parseMainMethod();
        break;
      default:
        parseTypeIdentFieldOrMethod();
        break;
    }
  }

  /** MainMethod -> static void IDENT ( String [] IDENT ) Block */
  private void parseMainMethod() {
    expectAndConsume(STATIC);
    expectAndConsume(VOID);
    expectAndConsume(IDENT);
    expectAndConsume(LPAREN);
    expectAndConsume(IDENT, "String");
    expectAndConsume(LBRACKET);
    expectAndConsume(RBRACKET);
    expectAndConsume(IDENT);
    expectAndConsume(RPAREN);
    parseBlock();
  }

  /** TypeIdentFieldOrMethod -> Type IDENT FieldOrMethod */
  private void parseTypeIdentFieldOrMethod() {
    parseType();
    expectAndConsume(IDENT);
    parseFieldOrMethod();
  }

  /** FieldOrMethod -> ; | Method */
  private void parseFieldOrMethod() {
    if (isCurrentTokenTypeOf(SEMICOLON)) {
      expectAndConsume(SEMICOLON);
    } else {
      parseMethod();
    }
  }

  /** Method -> ( Parameters? ) Block */
  private void parseMethod() {
    expectAndConsume(LPAREN);
    if (isCurrentTokenNotTypeOf(RPAREN)) {
      parseParameters();
    }
    expectAndConsume(RPAREN);
    parseBlock();
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
    parseBasicType();
    while (isCurrentTokenTypeOf(LBRACKET) && isCurrentTokenNotTypeOf(EOF)) {
      expectAndConsume(LBRACKET);
      expectAndConsume(RBRACKET);
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
    }
  }

  /**
   * Statement -> Block | EmptyStatement | IfStatement | ExpressionStatement | WhileStatement |
   * ReturnStatement
   */
  private void parseStatement() {
    switch (currentToken.terminal) {
      case LCURLY:
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
    expectAndConsume(LCURLY);
    while (isCurrentTokenNotTypeOf(RCURLY) && isCurrentTokenNotTypeOf(EOF)) {
      parseBlockStatement();
    }
    expectAndConsume(RCURLY);
  }

  /** BlockStatement -> Statement | LocalVariableDeclarationStatement */
  private void parseBlockStatement() {
    if (currentToken.isOneOf(INT, BOOLEAN, VOID)
        || matchCurrentAndLookAhead(IDENT, LBRACKET, RBRACKET)
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
    if (isCurrentTokenTypeOf(EQUAL_SIGN)) {
      expectAndConsume(EQUAL_SIGN);
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
    parseUnaryExpression();
    while (isCurrentTokenBinaryOperator()
        && isOperatorPrecedenceGreaterOrEqualThan(minPrecedence)) {
      int precedence = currentToken.terminal.getPrecedence();
      if (currentToken.terminal.isLeftAssociative()) {
        precedence++;
      }
      consumeToken();
      parseExpressionWithPrecedenceClimbing(precedence);
    }
  }

  /** UnaryExpression -> PostfixExpression | (! | -) UnaryExpression */
  private void parseUnaryExpression() {
    switch (currentToken.terminal) {
      case INVERT:
      case MINUS:
        consumeToken();
        parseUnaryExpression();
        break;
      default:
        parsePostfixExpression();
        break;
    }
  }

  /** PostfixExpression -> PrimaryExpression (PostfixOp)* */
  private void parsePostfixExpression() {
    parsePrimaryExpression();
    while ((isCurrentTokenTypeOf(LBRACKET) || isCurrentTokenTypeOf(DOT))
        && isCurrentTokenNotTypeOf(EOF)) {
      parsePostfixOp();
    }
  }

  /** PostfixOp -> MethodInvocation | FieldAccess | ArrayAccess */
  private void parsePostfixOp() {
    switch (currentToken.terminal) {
      case DOT:
        parseDotIdentFieldAccessMethodInvocation();
        break;
      case LBRACKET:
        parseArrayAccess();
        break;
    }
  }

  /** DotIdentFieldAccessMethodInvocation -> . IDENT (MethodInvocation)? */
  private void parseDotIdentFieldAccessMethodInvocation() {
    expectAndConsume(DOT);
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
    expectAndConsume(LBRACKET);
    parseExpression();
    expectAndConsume(RBRACKET);
  }

  /** Arguments -> (Expression (,Expression)*)? */
  private void parseArguments() {
    if (isCurrentTokenNotTypeOf(RBRACKET)) {
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
          case LBRACKET:
            parseNewArrayExpression();
            break;
        }
        break;
    }
  }

  /** NewObjectExpression -> ( ) */
  private void parseNewObjectExpression() {
    expectAndConsume(LPAREN);
    expectAndConsume(RPAREN);
  }

  /** NewArrayExpression -> [ Expression ] ([])* */
  private void parseNewArrayExpression() {
    expectAndConsume(LBRACKET);
    parseExpression();
    expectAndConsume(RBRACKET);
    while (matchCurrentAndLookAhead(LBRACKET, RBRACKET)) {
      expectAndConsume(LBRACKET);
      expectAndConsume(RBRACKET);
    }
  }
}
