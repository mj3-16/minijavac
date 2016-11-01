package minijava.parser;

import minijava.MJError;

class ParserError extends MJError {

    ParserError(String message) {
        super("ParserError:" + message);
    }
}
