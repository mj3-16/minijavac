package minijava.parser;

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

  private boolean isCurrentTokenBinaryOperator() {
    if (currentToken.isType(Terminal.TerminalType.OPERATOR)) {
      return true;
    }
    return false;
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
        expectTokenAndConsume(Terminal.INT);
        break;
      case BOOLEAN:
        expectTokenAndConsume(Terminal.BOOLEAN);
        break;
      case VOID:
        expectTokenAndConsume(Terminal.VOID);
        break;
      case IDENT:
        expectTokenAndConsume(Terminal.IDENT);
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

  /** Expression is parsed with Precedence Climbing */
  private void parseExpression() {
    parseExpressionWithPrecedenceClimbing(0);
  }

  private void parseExpressionWithPrecedenceClimbing(int minPrecedence) {
    int result;
    parsePrimaryExpression();

    while (isCurrentTokenBinaryOperator()
        && isOperatorPrecedenceGreaterOrEqualThan(minPrecedence)) {
      //int precedence = currentToken

    }
  }

  private boolean isOperatorPrecedenceGreaterOrEqualThan(int precedence) {
    //if(currentToken.terminal)
    return false;
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

  /** UnaryExpression -> PostfixExpression | (! | -) UnaryExpression */
  private void parseUnaryExpression() {
    switch (currentToken.terminal) {
      case INVERT:
      case MINUS:
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
    while ((isCurrentTokenTypeOf(Terminal.LBRACKET) || isCurrentTokenTypeOf(Terminal.DOT))
        && isCurrentTokenNotTypeOf(Terminal.EOF)) {
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
    expectTokenAndConsume(Terminal.DOT);
    expectTokenAndConsume(Terminal.IDENT);
    // is it FieldAccess (false) or MethodInvocation (true)?
    if (isCurrentTokenTypeOf(Terminal.LPAREN)) {
      parseMethodInvocation();
    }
  }

  /** MethodInvocation -> ( Arguments ) */
  private void parseMethodInvocation() {
    expectTokenAndConsume(Terminal.LPAREN);
    parseArguments();
    expectTokenAndConsume(Terminal.RPAREN);
  }

  /** ArrayAccess -> [ Expression ] */
  private void parseArrayAccess() {
    expectTokenAndConsume(Terminal.LBRACKET);
    parseExpression();
    expectTokenAndConsume(Terminal.RBRACKET);
  }

  /** Arguments -> (Expression (,Expression)*)? */
  private void parseArguments() {
    if (isCurrentTokenNotTypeOf(Terminal.RBRACKET)) {
      parseExpression();
      while (isCurrentTokenTypeOf(Terminal.COMMA) && isCurrentTokenNotTypeOf(Terminal.EOF)) {
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
        expectTokenAndConsume(Terminal.NULL);
        break;
      case FALSE:
        expectTokenAndConsume(Terminal.FALSE);
        break;
      case TRUE:
        expectTokenAndConsume(Terminal.TRUE);
        break;
      case INTEGER_LITERAL:
        expectTokenAndConsume(Terminal.INTEGER_LITERAL);
        break;
      case IDENT:
        expectTokenAndConsume(Terminal.IDENT);
        if (isCurrentTokenTypeOf(Terminal.LPAREN)) {
          expectTokenAndConsume(Terminal.LPAREN);
          parseArguments();
          expectTokenAndConsume(Terminal.RPAREN);
        }
        break;
      case THIS:
        expectTokenAndConsume(Terminal.THIS);
        break;
      case LPAREN:
        expectTokenAndConsume(Terminal.LPAREN);
        parseExpression();
        expectTokenAndConsume(Terminal.RPAREN);
        break;
      case NEW:
        parseNewObjectArrayExpression();
        break;
    }
  }

  /** NewObjectArrayExpression -> BasicType NewArrayExpression | IDENT NewObjectExpression */
  private void parseNewObjectArrayExpression() {
    expectTokenAndConsume(Terminal.NEW);
    switch (currentToken.terminal) {
      case INT:
        expectTokenAndConsume(Terminal.INT);
        parseNewArrayExpression();
        break;
      case BOOLEAN:
        expectTokenAndConsume(Terminal.BOOLEAN);
        parseNewArrayExpression();
        break;
      case VOID:
        expectTokenAndConsume(Terminal.VOID);
        parseNewArrayExpression();
        break;
      case IDENT:
        expectTokenAndConsume(Terminal.IDENT);
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
    expectTokenAndConsume(Terminal.LPAREN);
    expectTokenAndConsume(Terminal.RPAREN);
  }

  /** NewArrayExpression -> [ Expression ] ([])* */
  private void parseNewArrayExpression() {
    expectTokenAndConsume(Terminal.LBRACKET);
    parseExpression();
    expectTokenAndConsume(Terminal.RBRACKET);
    while (isCurrentTokenTypeOf(Terminal.LBRACKET) && isCurrentTokenNotTypeOf(Terminal.EOF)) {
      expectTokenAndConsume(Terminal.LBRACKET);
      expectTokenAndConsume(Terminal.RBRACKET);
    }
  }
}
