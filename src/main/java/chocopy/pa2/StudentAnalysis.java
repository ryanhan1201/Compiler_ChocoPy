package chocopy.pa2;

import chocopy.common.analysis.types.Type;
import chocopy.common.astnodes.Program;
import chocopy.common.analysis.types.*;
import java.util.*;
import com.sun.tools.javac.code.Attribute;

/** Top-level class for performing semantic analysis. */
public class StudentAnalysis {

    /** Perform semantic analysis on PROGRAM, adding error messages and
     *  type annotations. Provide debugging output iff DEBUG. Returns modified
     *  tree. */
    public static Program process(Program program, boolean debug) {

        if (program.hasErrors()) {
            return program;
        }

        DeclarationAnalyzer declarationAnalyzer =
                new DeclarationAnalyzer(program.errors);
        program.dispatch(declarationAnalyzer);

        SymbolTable<Type> globalSym =
                declarationAnalyzer.getGlobals();

        DeclarationSemanticCheck declarationSemanticCheck =
                new DeclarationSemanticCheck(program.errors, globalSym);
        program.dispatch(declarationSemanticCheck);

        if (!program.hasErrors()) {
            TypeChecker typeChecker =
                    new TypeChecker(globalSym, program.errors);
            program.dispatch(typeChecker);
        }

        return program;
    }
}