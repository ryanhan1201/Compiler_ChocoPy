package chocopy.pa1;
import java_cup.runtime.*;
import java.util.*;
import java.lang.*;
%%

/*** Do not change the flags below unless you know what you are doing. ***/

%unicode
%line
%column

%class ChocoPyLexer
%public

%cupsym ChocoPyTokens
%cup
%cupdebug

%eofclose false

/*** Do not change the flags above unless you know what you are doing. ***/

/* The following code section is copied verbatim to the
 * generated lexer class. */
%{
    /* The code below includes some convenience methods to create tokens
     * of a given type and optionally a value that the CUP parser can
     * understand. Specifically, a lot of the logic below deals with
     * embedded information about where in the source code a given token
     * was recognized, so that the parser can report errors accurately.
     * (It need not be modified for this project.) */

    /** Producer of token-related values for the parser. */


    final ComplexSymbolFactory symbolFactory = new ComplexSymbolFactory();

    /** Return a terminal symbol of syntactic category TYPE and no
     *  semantic value at the current source location. */
    private Symbol symbol(int type) {
        return symbol(type, yytext());
    }

    /** Return a terminal symbol of syntactic category TYPE and semantic
     *  value VALUE at the current source location. */
    private Symbol symbol(int type, Object value) {
        return symbolFactory.newSymbol(ChocoPyTokens.terminalNames[type], type,
            new ComplexSymbolFactory.Location(yyline + 1, yycolumn + 1),
            new ComplexSymbolFactory.Location(yyline + 1,yycolumn + yylength()),
            value);
    }
    //STUFF WE ADDED
    final int INDENTSIZE = 8;
    Stack<Integer> indentStack = new Stack<Integer>();
    int currIndent = 0;
    StringBuffer sb = new StringBuffer();
    int col = 0;

    private Symbol symbol(int type, int x, int y, int len){
        return symbolFactory.newSymbol(ChocoPyTokens.terminalNames[type], type,
        new ComplexSymbolFactory.Location(x + 1, y + 1), new ComplexSymbolFactory.Location(x + 1, y + len));
    }
    private Symbol symbol(int type, Object value, int startCol) {
         return symbolFactory.newSymbol(ChocoPyTokens.terminalNames[type], type,
            new ComplexSymbolFactory.Location(yyline + 1, startCol + 1),
            new ComplexSymbolFactory.Location(yyline + 1,yycolumn + yylength()),
            value);
        }
%}

/* Macros (regexes used in rules below) */

WhiteSpace = [ \t]
LineBreak  = \r|\n|\r\n
IntegerLiteral = 0 | [1-9][0-9]*
BooleanLiteral = true|false

Letters = [a-zA-Z]
Nums = [0-9]
Underscore = _
Identifier = [a-zA-Z_] \w*

//Hard to write chars
Slash = \\\\
Tab = \\t
NewLine = \\n
Quote = \\\"

PossibleStringChars = [\x20\x21\x23-\x5b\x5d-\x7e]
StringLiteral = {PossibleStringChars}*
StringAndClose = ({NewLine}|{PossibleStringChars}|{Quote}|{Tab}|{Slash})*\"

InputChars = [^\r\n]
Comment = "#"{InputChars}*

/* Custom State Defs % */
%state STRING
%state MEAT

%%

<YYINITIAL> {
    //we need to go to the MEAT when we hit a possible input character
    " "             {currIndent++;}
    {Tab}           {currIndent += (INDENTSIZE - currIndent % INDENTSIZE);}
    {LineBreak}     {currIndent = 0;}

    {Comment}       {/*IGNORE*/}
    {InputChars}    {yypushback(1);
                        if(indentStack.empty()) {
                            indentStack.push(0);
                        }
                        if (currIndent > indentStack.peek()) {
                            //start tokenizing the rest of the program
                            yybegin(MEAT);
                            //Deal with indents after doing program stuff
                            int y = indentStack.peek();
                            int len = currIndent - y;
                            indentStack.push(currIndent);
                            return symbol(ChocoPyTokens.INDENT, yyline, y, len);
                        }
                        else if (currIndent == indentStack.peek()){
                            yybegin(MEAT);
                        }
                        else {
                            //need to start getting rid of all the > indents on the stack
                            yypushback(yylength());
                            indentStack.pop();
                            return symbol(ChocoPyTokens.DEDENT);
                        }
                        }


}
<MEAT> {

/* Literals*/
      {LineBreak}                {currIndent = 0;
                                  yybegin(YYINITIAL);
                                  return symbol(ChocoPyTokens.NEWLINE);
                                 }
      {IntegerLiteral}           { return symbol(ChocoPyTokens.NUMBER, Integer.parseInt(yytext())); }



      {Comment}                  {/* IGNORE */}
      {WhiteSpace}               {/* IGNORE */}

      \"{Identifier}\"           { String idString = yytext();
                                    return symbol(ChocoPyTokens.IDSTRING,idString.substring(1, idString.length()-1));
                                 }
      \"                         {int col = yycolumn;
                                  sb.setLength(0);
                                  yybegin(STRING);
                                 }

      "if"                        { return symbol(ChocoPyTokens.IF, yytext()); }
      "elif"                      { return symbol(ChocoPyTokens.ELIF, yytext()); }
      "else"                      { return symbol(ChocoPyTokens.ELSE, yytext()); }
      "None"                      { return symbol(ChocoPyTokens.NONE, yytext()); }
      "for"                       { return symbol(ChocoPyTokens.FOR, yytext()); }
      "while"                     { return symbol(ChocoPyTokens.WHILE, yytext()); }
      "False"                     { return symbol(ChocoPyTokens.FALSE, yytext()); }
      "True"                      { return symbol(ChocoPyTokens.TRUE, yytext()); }
      "assert"                    { return symbol(ChocoPyTokens.ASSERT, yytext()); }
      "async"                     { return symbol(ChocoPyTokens.ASYNC, yytext()); }
      "await"                     { return symbol(ChocoPyTokens.BREAK, yytext()); }
      "class"                     { return symbol(ChocoPyTokens.CLASS, yytext()); }
      "continue"                  { return symbol(ChocoPyTokens.CONTINUE, yytext()); }
      "del"                       { return symbol(ChocoPyTokens.DEL, yytext()); }
      "def"                       { return symbol(ChocoPyTokens.DEF, yytext()); }
      "except"                    { return symbol(ChocoPyTokens.EXCEPT, yytext()); }
      "finally"                   { return symbol(ChocoPyTokens.FINALLY, yytext()); }
      "from"                      { return symbol(ChocoPyTokens.FROM, yytext()); }
      "global"                    { return symbol(ChocoPyTokens.GLOBAL, yytext()); }
      "import"                    { return symbol(ChocoPyTokens.IMPORT, yytext()); }
      "in"                        { return symbol(ChocoPyTokens.IN, yytext()); }
      "is"                        { return symbol(ChocoPyTokens.IS, yytext()); }
      "lambda"                    { return symbol(ChocoPyTokens.LAMBDA, yytext()); }
      "nonlocal"                  { return symbol(ChocoPyTokens.NONLOCAL, yytext()); }
      "not"                       { return symbol(ChocoPyTokens.NOT, yytext()); }
      "pass"                      { return symbol(ChocoPyTokens.PASS, yytext()); }
      "raise"                     { return symbol(ChocoPyTokens.RAISE, yytext()); }
      "return"                    { return symbol(ChocoPyTokens.RETURN, yytext()); }
      "try"                       { return symbol(ChocoPyTokens.TRY, yytext()); }
      "with"                      { return symbol(ChocoPyTokens.WITH, yytext()); }
      "yield"                     { return symbol(ChocoPyTokens.YIELD, yytext()); }
      "True"                      { return symbol(ChocoPyTokens.TRUE, yytext()); }
      "False"                     { return symbol(ChocoPyTokens.FALSE, yytext()); }

      /* Operators. */
      "->"                         { return symbol(ChocoPyTokens.ARROW, yytext()); }
      "+"                          { return symbol(ChocoPyTokens.PLUS, yytext()); }
      "-"                          { return symbol(ChocoPyTokens.MINUS, yytext()); }
      "*"                          { return symbol(ChocoPyTokens.TIMES, yytext()); }
      ">"                          { return symbol(ChocoPyTokens.GREAT, yytext()); }
      "%"                          { return symbol(ChocoPyTokens.MOD, yytext()); }
      "<"                          { return symbol(ChocoPyTokens.LESS, yytext()); }
      ">="                         { return symbol(ChocoPyTokens.GREATEQ, yytext()); }
      "<="                         { return symbol(ChocoPyTokens.LESSEQ, yytext()); }
      "=="                         { return symbol(ChocoPyTokens.EQ, yytext()); }
      "!="                         { return symbol(ChocoPyTokens.NOTEQ, yytext()); }
      "////"                       { return symbol(ChocoPyTokens.DIV, yytext()); }
      "="                          { return symbol(ChocoPyTokens.ASSIGN, yytext()); }
      "("                          { return symbol(ChocoPyTokens.LEFTPAREN, yytext()); }
      ")"                          { return symbol(ChocoPyTokens.RIGHTPAREN, yytext()); }
      "["                          { return symbol(ChocoPyTokens.LEFTBRACKET, yytext()); }
      "]"                          { return symbol(ChocoPyTokens.RIGHTBRACKET, yytext()); }
      ","                          { return symbol(ChocoPyTokens.COMMA, yytext()); }
      ":"                          { return symbol(ChocoPyTokens.COLON, yytext()); }
      "."                          { return symbol(ChocoPyTokens.PERIOD, yytext()); }
      "and"                        { return symbol(ChocoPyTokens.AND, yytext()); }
      "or"                         { return symbol(ChocoPyTokens.OR, yytext()); }
      "not"                        { return symbol(ChocoPyTokens.NOT, yytext()); }
      "as"                         { return symbol(ChocoPyTokens.AS, yytext()); }

      {Identifier}                 { return symbol(ChocoPyTokens.IDENTIFIER, yytext()); }
}

    <STRING> {
      \"                             {yybegin(MEAT);
                                      int temp = col;
                                      col = 0;
                                      int len = sb.toString().length();
                                      return symbol(ChocoPyTokens.STRING,
                                      sb.toString(), temp);
                                     }
      [^\n\r\"\\]+                   { sb.append( yytext() ); }
      \\t                            { sb.append('\t'); }
      \\n                            { sb.append('\n'); }
      \\r                            { sb.append('\r'); }
      \\\"                           { sb.append('\"'); }
      \\                             { sb.append('\\'); }
    }

<<EOF>>                       {if (!indentStack.empty() && indentStack.peek() >0) {
                                    indentStack.pop();
                                    zzAtEOF = false;
                                    return symbol(ChocoPyTokens.DEDENT);
                               }
                               else{
                                    return symbol(ChocoPyTokens.EOF);
                               }
                               }

/* Error fallback. */
[^]                           { return symbol(ChocoPyTokens.UNRECOGNIZED); }
