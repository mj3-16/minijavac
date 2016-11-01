package minijava.parser;

import minijava.lexer.Lexer;
import minijava.token.Terminal;
import minijava.token.Token;

public class Parser {
    private Token currentToken;
    private Lexer lexer;

    public Parser(Lexer lexer){
        this.lexer = lexer;
    }

    private void consumeToken()
    {
        this.currentToken = lexer.next();
    }

    private void expectTerminal(Terminal terminal){
        if(currentToken.isTerminal(terminal)){
            //throw ParserException
        }
        consumeToken();
    }

    private boolean isCurrentTokenTypeOf(Terminal terminal){
        if(currentToken.isTerminal(terminal)){
            return true;
        }
        return false;
    }

    private boolean isCurrentTokenNotTypeOf(Terminal terminal){
        return !isCurrentTokenTypeOf(terminal);
    }

    public void parse(){
        consumeToken();
        parseProgramm();
    }

    /**
     * Program -> ClassDeclaration*
     */
    private void parseProgramm(){
        // while it is not EOF it must be a ClassDeclaration
        while(isCurrentTokenNotTypeOf(Terminal.EOF))
        {
            parseClassDeclaration();
        }
        expectTerminal(Terminal.EOF);
    }

    /**
     *  ClassDeclaration -> class IDENT { ClassMember* }
     */
    private void parseClassDeclaration(){
        expectTerminal(Terminal.CLASS);
        expectTerminal(Terminal.IDENT);
        expectTerminal(Terminal.LCURLY);
        // No ClassMember
        if(isCurrentTokenTypeOf(Terminal.RCURLY)){
            consumeToken();
        }
        else {
            while(isCurrentTokenNotTypeOf(Terminal.RCURLY) && isCurrentTokenNotTypeOf(Terminal.EOF)){
                parseClassMember();
            }
        }
    }

    /**
     * ClassMember -> Field | Method | MainMethod
     */
    private void parseClassMember(){
        expectTerminal(Terminal.PUBLIC);
        
    }

    private void parseField(){

    }

    private void parseMainMethod(){

    }

    private void parseMethod(){

    }

    private void parseParameters(){

    }

    private void parseType(){

    }

    private void parseBasicType(){

    }

    private void parseStatement(){

    }

    private void parseBlock(){

    }

    private void parseBlockStatement(){

    }

    private void parseLocalVariableDeclarationStatement(){

    }

    private void parseEmptyStatement(){

    }

    private void parseWhileStatement(){

    }

    private void parseIfStatement(){

    }

    private void parseExpressionStatement(){

    }

    private void parseReturnStatement(){

    }

    private void parseExpression(){

    }

    private void parseAssignmentExpression(){

    }

    private void parseLogicalOrExpression(){

    }

    private void parseLogicalAndExpression(){

    }

    private void parseEqualityExpression(){

    }

    private void parseRelationalExpression(){

    }

    private void parseAdditiveExpression(){

    }

    private void parseMultiplicativeExpression(){

    }

    private void parseUnaryExpression(){

    }

    private void parsePostfixExpression(){

    }

    private void parsePostfixOp(){

    }

    private void parseMethodInvocation(){

    }

    private void parseFieldAccess(){

    }

    private void parseArrayAccess(){

    }

    private void parseArguments(){

    }

    private void parsePrimaryExpression(){

    }

    private void parseNewObjectExpression(){

    }

    private void parseNewArrayExpression(){

    }
}
