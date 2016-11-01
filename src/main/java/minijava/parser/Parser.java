package minijava.parser;

import minijava.lexer.Lexer;
import minijava.token.Token;

public class Parser {
    private Token currentToken;
    private Lexer lexer;

    public Parser(Lexer lexer){
        this.lexer = lexer;
    }

    public void parse(){
        currentToken = lexer.next();
        parseProgramm();
    }

    private void parseProgramm(){

    }

    private void parseClassDeclaration(){

    }

    private void parseClassMember(){

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
