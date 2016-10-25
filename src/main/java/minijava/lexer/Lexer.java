package minijava.lexer;

import java.util.Iterator;

/**
 * Basic interface of a lexer.
 */
public interface Lexer extends Iterator<Token> {

    /**
     * Get the current token.
     */
    Token current();

    /**
     * Get the n.th next token.
     */
    Token lookAhead(int lookAhead);

    StringTable getStringTable();

}
