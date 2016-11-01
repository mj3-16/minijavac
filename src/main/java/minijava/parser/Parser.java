package minijava.parser;

import static minijava.token.Terminal.INT;
import static minijava.token.Terminal.SEMICOLON;

import minijava.lexer.Lexer;
import minijava.token.Terminal;
import minijava.token.Token;

public class Parser {
  private Token currentToken;
  private Lexer lexer;

  public Parser(Lexer lexer) {
    this.lexer = lexer;
  }

  private void consumeToken() {
    this.currentToken = lexer.next();
  }

  private void expectTokenAndConsume(Terminal terminal) {
    if (currentToken.isTerminal(terminal)) {
      throw new ParserError("");
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

  public void parse() {
    consumeToken();
    parseProgramm();
  }

  /** Program -> ClassDeclaration* */
  private void parseProgramm() {
    while (isCurrentTokenNotTypeOf(Terminal.EOF)) {
      parseClassDeclaration();
    }
    expectTokenAndConsume(Terminal.EOF);
  }

  /** ClassDeclaration -> class IDENT { PublicClassMember* } */
  private void parseClassDeclaration() {
    expectTokenAndConsume(Terminal.CLASS);
    expectTokenAndConsume(Terminal.IDENT);
    expectTokenAndConsume(Terminal.LCURLY);
    while (isCurrentTokenNotTypeOf(Terminal.RCURLY) && isCurrentTokenNotTypeOf(Terminal.EOF)) {
      parsePublicClassMember();
    }
    expectTokenAndConsume(Terminal.RCURLY);
  }

  /** PublicClassMember -> public ClassMember */
  private void parsePublicClassMember() {
    expectTokenAndConsume(Terminal.PUBLIC);
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
    expectTokenAndConsume(Terminal.STATIC);
    expectTokenAndConsume(Terminal.VOID);
    expectTokenAndConsume(Terminal.IDENT);
    expectTokenAndConsume(Terminal.LPAREN);
    expectTokenAndConsume(Terminal.STRING);
    expectTokenAndConsume(Terminal.LBRACKET);
    expectTokenAndConsume(Terminal.RBRACKET);
    expectTokenAndConsume(Terminal.IDENT);
    expectTokenAndConsume(Terminal.RPAREN);
    parseBlock();
  }

  /** TypeIdentFieldOrMethod -> Type IDENT FieldOrMethod */
  private void parseTypeIdentFieldOrMethod() {
    parseType();
    expectTokenAndConsume(Terminal.IDENT);
    parseFieldOrMethod();
  }

  /** FieldOrMethod -> ; | Method */
  private void parseFieldOrMethod() {
    if (isCurrentTokenTypeOf(Terminal.SEMICOLON)) {
      expectTokenAndConsume(Terminal.SEMICOLON);
    } else {
      parseMethod();
    }
  }

  /** Method -> ( Parameters? ) Block */
  private void parseMethod() {
    expectTokenAndConsume(Terminal.LPAREN);
    if (isCurrentTokenNotTypeOf(Terminal.RPAREN)) {
      parseParameters();
    }
    expectTokenAndConsume(Terminal.RPAREN);
    parseBlock();
  }

  /** Parameters -> Parameter | Parameter , Parameters */
  private void parseParameters() {
    parseParameter();
    if (isCurrentTokenTypeOf(Terminal.COMMA)) {
      parseParameters();
    }
  }

  /** Parameter -> Type IDENT */
  private void parseParameter() {
    parseType();
    expectTokenAndConsume(Terminal.IDENT);
  }

  /** Type -> BasicType ([])* */
  private void parseType() {
    parseBasicType();
    while (isCurrentTokenTypeOf(Terminal.LBRACKET) && isCurrentTokenNotTypeOf(Terminal.EOF)) {
      expectTokenAndConsume(Terminal.LBRACKET);
      expectTokenAndConsume(Terminal.RBRACKET);
    }
  }

  /** BasicType -> int | boolean | void | IDENT */
  private void parseBasicType() {
    switch (currentToken.terminal) {
      case INT:
      case BOOLEAN:
      case VOID:
      case IDENT:
        consumeToken();
        break;
      default:
        throw new ParserError("");
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
    expectTokenAndConsume(Terminal.LCURLY);
    while (isCurrentTokenNotTypeOf(Terminal.RCURLY) && isCurrentTokenNotTypeOf(Terminal.EOF)) {
      parseBlockStatement();
    }
    expectTokenAndConsume(Terminal.RCURLY);
  }

  /** BlockStatement -> Statement | LocalVariableDeclarationStatement */
  private void parseBlockStatement() {
    switch (currentToken.terminal) {
      case INT:
      case BOOLEAN:
      case VOID:
      case IDENT:
        parseLocalVariableDeclarationStatement();
        break;
      default:
        parseStatement();
        break;
    }
  }

  /** LocalVariableDeclarationStatement -> Type IDENT (= Expression)? ; */
  private void parseLocalVariableDeclarationStatement() {
    parseType();
    expectTokenAndConsume(Terminal.IDENT);
    if (isCurrentTokenTypeOf(Terminal.EQUAL_SIGN)) {
      expectTokenAndConsume(Terminal.EQUAL_SIGN);
      parseExpression();
    }
    expectTokenAndConsume(Terminal.SEMICOLON);
  }

  /** EmptyStatement -> ; */
  private void parseEmptyStatement() {
    expectTokenAndConsume(Terminal.SEMICOLON);
  }

  /** WhileStatement -> while ( Expression ) Statement */
  private void parseWhileStatement() {
    expectTokenAndConsume(Terminal.WHILE);
    expectTokenAndConsume(Terminal.LPAREN);
    parseExpression();
    expectTokenAndConsume(Terminal.RPAREN);
    parseStatement();
  }

  /** IfStatement -> if ( Expression ) Statement (else Statement)? */
  private void parseIfStatement() {
    expectTokenAndConsume(Terminal.IF);
    expectTokenAndConsume(Terminal.LPAREN);
    parseExpression();
    expectTokenAndConsume(Terminal.RPAREN);
    parseStatement();
    if (isCurrentTokenTypeOf(Terminal.ELSE)) {
      expectTokenAndConsume(Terminal.ELSE);
      parseStatement();
    }
  }

  /** ExpressionStatement -> Expression ; */
  private void parseExpressionStatement() {
    parseExpression();
    expectTokenAndConsume(Terminal.SEMICOLON);
  }

  /** ReturnStatement -> return Expression? ; */
  private void parseReturnStatement() {
    expectTokenAndConsume(Terminal.RETURN);
    if (isCurrentTokenNotTypeOf(Terminal.SEMICOLON)) {
      parseExpression();
    }
    expectTokenAndConsume(Terminal.SEMICOLON);
  }

  /** Expression -> AssignmentExpression */
  private void parseExpression() {
    parseAssignmentExpression();
  }

  /** AssignmentExpression -> LogicalOrExpression (= AssignmentExpression)? */
  private void parseAssignmentExpression() {
    parseLogicalOrExpression();
    if (isCurrentTokenTypeOf(Terminal.EQUAL_SIGN)) {
      expectTokenAndConsume(Terminal.EQUAL_SIGN);
      parseAssignmentExpression();
    }
  }

  /** LogicalOrExpression -> (LogicalOrExpression ||)? LogicalAndExpression */
  private void parseLogicalOrExpression() {}

  private void parseLogicalAndExpression() {}

  private void parseEqualityExpression() {}

  private void parseRelationalExpression() {}

  private void parseAdditiveExpression() {}

  private void parseMultiplicativeExpression() {}

  private void parseUnaryExpression() {}

  private void parsePostfixExpression() {}

  private void parsePostfixOp() {}

  private void parseMethodInvocation() {}

  private void parseFieldAccess() {}

  private void parseArrayAccess() {}

  private void parseArguments() {}

  private void parsePrimaryExpression() {}

  private void parseNewObjectExpression() {}

  private void parseNewArrayExpression() {}
}
