package chocopy.pa3;

import java.util.List;

import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.astnodes.Stmt;
import chocopy.common.astnodes.ReturnStmt;
import chocopy.common.codegen.CodeGenBase;
import chocopy.common.codegen.FuncInfo;
import chocopy.common.codegen.Label;
import chocopy.common.codegen.RiscVBackend;
import chocopy.common.codegen.SymbolInfo;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.*;
import chocopy.common.codegen.*;
import static chocopy.common.codegen.RiscVBackend.Register.*;

/**
 * This is where the main implementation of PA3 will live.
 *
 * A large part of the functionality has already been implemented
 * in the base class, CodeGenBase. Make sure to read through that
 * class, since you will want to use many of its fields
 * and utility methods in this class when emitting code.
 *
 * Also read the PDF spec for details on what the base class does and
 * what APIs it exposes for its sub-class (this one). Of particular
 * importance is knowing what all the SymbolInfo classes contain.
 */
public class CodeGenImpl extends CodeGenBase {

    /** A code generator emitting instructions to BACKEND. */
    public CodeGenImpl(RiscVBackend backend) {
        super(backend);
    }

    /** Operation on None. */
    private final Label errorNone = new Label("error.None");
    /** Division by zero. */
    private final Label errorDiv = new Label("error.Div");
    /** Index out of bounds. */
    private final Label errorOob = new Label("error.OOB");
    private final Label makeInt = new Label("makeint");
    private final Label makeBool = new Label("makebool");
    private final Label consList = new Label("consList");
    private final Label stringConcat = new Label("strConcat");

    /**
     * Emits the top level of the program.
     *
     * This method is invoked exactly once, and is surrounded
     * by some boilerplate code that: (1) initializes the heap
     * before the top-level begins and (2) exits after the top-level
     * ends.
     *
     * You only need to generate code for statements.
     *
     * @param statements top level statements
     */
    protected void emitTopLevel(List<Stmt> statements) {
        StmtAnalyzer stmtAnalyzer = new StmtAnalyzer(null);
        backend.emitADDI(SP, SP, -2 * backend.getWordSize(),
                "Saved FP and saved RA (unused at top level).");
        backend.emitSW(RA, SP, 0, "Top saved FP is 0.");
        backend.emitSW(SP, SP, 4, "Top saved RA is 0.");
        backend.emitADDI(FP, SP, 2 * backend.getWordSize(),
                "Set FP to previous SP.");

        for (Stmt stmt : statements) {
            stmt.dispatch(stmtAnalyzer);
        }
        backend.emitLI(A0, EXIT_ECALL, "Code for ecall: exit");
        backend.emitEcall(null);
    }

    /**
     * Emits the code for a function described by FUNCINFO.
     *
     * This method is invoked once per function and method definition.
     * At the code generation stage, nested functions are emitted as
     * separate functions of their own. So if function `bar` is nested within
     * function `foo`, you only emit `foo`'s code for `foo` and only emit
     * `bar`'s code for `bar`.
     */
    protected void emitUserDefinedFunction(FuncInfo funcInfo) {
        backend.emitGlobalLabel(funcInfo.getCodeLabel());
        StmtAnalyzer stmtAnalyzer = new StmtAnalyzer(funcInfo);

        // Copied from above
        backend.emitADDI(SP, SP, -2 * backend.getWordSize(),
                "Saved FP and saved RA.");
        backend.emitSW(FP, SP, 0, "Save FP.");
        backend.emitSW(RA, SP, 4, "Save RA.");
        backend.emitADDI(FP, SP, 2 * backend.getWordSize(),
                "Set FP to previous SP.");
        List<StackVarInfo> stackInfoList = funcInfo.getLocals();
        for (StackVarInfo varInfo : stackInfoList) {
            ValueType vType = varInfo.getVarType();
            Literal val = varInfo.getInitialValue();

            if (vType != null) {
                if (vType.equals(Type.INT_TYPE)) {
                    int intValue = ((IntegerLiteral) val).value;
                    backend.emitLI(T0, intValue , "Load initial value (int) to reg T0");
                } else if (vType.equals(Type.BOOL_TYPE)) {
                    int boolValue = 1;
                    if (!((BooleanLiteral) val).value) {
                        boolValue = 0;
                    }
                    backend.emitLI(T0, boolValue ,"Load initial value (bool) to reg T0");
                }
            }

            backend.emitADDI(SP, SP, -1 * backend.getWordSize(), "Move SP to save space for local var");
            backend.emitSW(T0, SP, 0, "Save val of local var onto stack");
        }

        for (Stmt stmt : funcInfo.getStatements()) {
            stmt.dispatch(stmtAnalyzer);
        }

        backend.emitMV(A0, ZERO, "Returning None implicitly");
        backend.emitLocalLabel(stmtAnalyzer.epilogue, "Epilogue");
        backend.emitLW(RA, FP, -4, "Resetting RA.");
        backend.emitMV(SP, FP, "Resetting SP.");
        backend.emitLW(FP, SP, -8, "Resetting FP.");;
        backend.emitJR(RA, "Returning to caller");
    }

    /** An analyzer that encapsulates code generation for statments. */
    private class StmtAnalyzer extends AbstractNodeAnalyzer<Void> {
        /*
         * The symbol table has all the info you need to determine
         * what a given identifier 'x' in the current scope is. You can
         * use it as follows:
         *   SymbolInfo x = sym.get("x");
         *
         * A SymbolInfo can be one the following:
         * - ClassInfo: a descriptor for classes
         * - FuncInfo: a descriptor for functions/methods
         * - AttrInfo: a descriptor for attributes
         * - GlobalVarInfo: a descriptor for global variables
         * - StackVarInfo: a descriptor for variables allocated on the stack,
         *      such as locals and parameters
         *
         * Since the input program is assumed to be semantically
         * valid and well-typed at this stage, you can always assume that
         * the symbol table contains valid information. For example, in
         * an expression `foo()` you KNOW that sym.get("foo") will either be
         * a FuncInfo or ClassInfo, but not any of the other infos
         * and never null.
         *
         * The symbol table in funcInfo has already been populated in
         * the base class: CodeGenBase. You do not need to add anything to
         * the symbol table. Simply query it with an identifier name to
         * get a descriptor for a function, class, variable, etc.
         *
         * The symbol table also maps nonlocal and global vars, so you
         * only need to lookup one symbol table and it will fetch the
         * appropriate info for the var that is currently in scope.
         */

        /**
         * Symbol table for my statements.
         */
        private SymbolTable<SymbolInfo> sym;

        /**
         * Label of code that exits from procedure.
         */
        protected Label epilogue;

        /**
         * The descriptor for the current function, or null at the top
         * level.
         */
        private FuncInfo funcInfo;

        /**
         * An analyzer for the function described by FUNCINFO0, which is null
         * for the top level.
         */
        StmtAnalyzer(FuncInfo funcInfo0) {
            funcInfo = funcInfo0;
            if (funcInfo == null) {
                sym = globalSymbols;
            } else {
                sym = funcInfo.getSymbolTable();
            }
            epilogue = generateLocalLabel();
        }

        public Void analyze(Program p) {

            for (Declaration d : p.declarations) {
                d.dispatch(this);
            }
            for (Stmt s : p.statements) {
                s.dispatch(this);
            }
            return null;
        }

        @Override
        public Void analyze(ReturnStmt stmt) {
            if (stmt.value == null) {
                backend.emitMV(A0, ZERO, "Returning None implicitly");
            } else {
                stmt.value.dispatch(this);
            }
            backend.emitJ(epilogue, "Go to return");
            return null;
        }

        public Void analyze(IfExpr expr) {
            Label trueBranch = generateLocalLabel();
            Label end = generateLocalLabel();
            expr.condition.dispatch(this);
            backend.emitBNEZ(A0, trueBranch, "If condition is true");
            expr.elseExpr.dispatch(this);
            backend.emitJ(end, "Go to End");
            backend.emitLocalLabel(trueBranch, "Condition True");
            expr.thenExpr.dispatch(this);
            backend.emitLocalLabel(end, "End of If Eexpr");
            return null;
        }

        public Void analyze(IfStmt stmt) {
            stmt.condition.dispatch(this);
            Label cond = generateLocalLabel();
            backend.emitBEQZ(A0, cond, "Jump to else part of if stmt because the condition came out to be false");
            for (Stmt s : stmt.thenBody) {
                s.dispatch(this);
            }
            Label finish = generateLocalLabel();
            backend.emitJ(finish, "Go to end of If stmt because we dont need to look at else stuff");
            backend.emitLocalLabel(cond, "Start of the Else body");
            for (Stmt s : stmt.elseBody) {
                s.dispatch(this);
            }
            backend.emitLocalLabel(finish, "End of If stmt");
            return null;
        }

        public Void analyze(Identifier id) {
            SymbolInfo symbolInfo = sym.get(id.name);
            if (symbolInfo instanceof GlobalVarInfo) {
                GlobalVarInfo globalInfo = (GlobalVarInfo) symbolInfo;
                backend.emitLW(A0, globalInfo.getLabel(), "Loading " + id.name + " into A0");
            } else if (symbolInfo instanceof StackVarInfo) {
                backend.emitMV(T0, FP, "Storing FP into T0");
                SymbolTable<SymbolInfo> curSym = sym;
                FuncInfo curInfo = funcInfo;
                while (!curSym.declares(id.name)) {
                    int paramAmount = curInfo.getParams().size();
                    curSym = curSym.getParent();
                    curInfo = curInfo.getParentFuncInfo();
                    backend.emitLW(T0, T0, paramAmount * backend.getWordSize(), "Load parent function scope.");
                }
                int offset = curInfo.getParams().size() - curInfo.getVarIndex(id.name);
                backend.emitLW(A0, T0, (offset - 1) * backend.getWordSize(), "Load local var: " + id.name);
            }
            return null;
        }

        public Void analyze(IntegerLiteral lit) {
            backend.emitLI(A0, lit.value, "Loading integer " + lit.value);
            return null;
        }

        public Void analyze(StringLiteral literal) {
            Label label = constants.getStrConstant(literal.value);
            backend.emitLA(A0, label, "Load address" + label + "prepare for subsequent operation");
            return null;
        }

        public Void analyze(BooleanLiteral lit) {
            if (lit.value) {
                backend.emitLI(A0, 1, "Loading True");
            } else {
                backend.emitLI(A0, 0, "Loading False");
            }
            return null;
        }

        public Void analyze(UnaryExpr unary) {
            unary.operand.dispatch(this);
            switch (unary.operator) {
                case "-":
                    backend.emitSUB(A0, ZERO, A0, "0 - operand");
                    break;
                case "not":
                    backend.emitXORI(A0, A0, 1, "Flip the true/false value");
                    break;
            }
            return null;
        }

        public Void analyze(CallExpr expr) {
            FuncInfo callInfo = (FuncInfo) sym.get(expr.function.name);
            if (funcInfo != null) {
                FuncInfo currInfo = funcInfo;
                int step = funcInfo.getDepth() - callInfo.getDepth() + 1;
                backend.emitMV(T0, FP, "Saving curr FP to T0");
                for (int i = 0; i < step; i++) {
                    assert currInfo != null : "Curr func must not be NULL";
                    int offset = currInfo.getParams().size();
                    backend.emitLW(T0, T0, offset * backend.getWordSize(), "Loading scope of parent func");
                    currInfo = currInfo.getParentFuncInfo();
                }
                backend.emitADDI(SP, SP, -1 * backend.getWordSize(), "Moving SP to save space static link");
                backend.emitSW(T0, SP, 0, "Loading static link");
            }

            for (int i = 0; i < expr.args.size(); i++) {
                Expr arg = expr.args.get(i);
                String pName = callInfo.getParams().get(i);
                SymbolTable<SymbolInfo> symTbl = callInfo.getSymbolTable();
                StackVarInfo pInfo = (StackVarInfo) symTbl.get(pName);
                arg.dispatch(this);
                if (pInfo.getVarType().equals(Type.OBJECT_TYPE)) {
                    if (arg.getInferredType().equals(Type.BOOL_TYPE)) {
                        backend.emitJAL(makeBool, "Box Boolean");
                    }
                    if (arg.getInferredType().equals(Type.INT_TYPE)) {
                        backend.emitJAL(makeInt, "Box Int");
                    }
                }
                backend.emitADDI(SP, SP, -1 * backend.getWordSize(), "Moving SP to save space for arg.");
                backend.emitSW(A0, SP, 0, "Storing the argument onto the stack");
            }
            String cName = expr.function.name;
            backend.emitJAL(callInfo.getCodeLabel(), cName);
            return null;
        }

        public Void analyze(AssignStmt stmt) {
            stmt.value.dispatch(this);
            for (Expr target : stmt.targets) {
                if (target instanceof Identifier) {
                    SymbolInfo symInfo = sym.get(((Identifier) target).name);
                    if (symInfo instanceof StackVarInfo) {
                        FuncInfo currInfo = funcInfo;
                        SymbolTable<SymbolInfo> currSym = sym;
                        backend.emitMV(T0, FP, "Saving curr FP tp T0");
                        int offset;
                        while (!currSym.declares(((Identifier) target).name)) {
                            currSym = currSym.getParent();
                            currInfo = currInfo.getParentFuncInfo();
                            offset = currInfo.getParams().size();
                            backend.emitLW(T0, T0, offset * backend.getWordSize(), "Loading scope of parent func");
                        }
                        offset = currInfo.getParams().size() - currInfo.getVarIndex(((Identifier) target).name);

                        backend.emitSW(A0, T0, (offset - 1) * backend.getWordSize(), "Storing local var from regis into memory");
                    }
                    if (symInfo instanceof GlobalVarInfo) {
                        backend.emitSW(A0, ((GlobalVarInfo) symInfo).getLabel(), T6, "Storing local var from regis into memory");
                    }
                } else {
                    //Probably not -4, but more like the entire value of the list?might be -4
                    backend.emitSW(A0, SP, -4, "Storing value");
                    backend.emitADDI(SP, SP, -4, "Moving SP");
                    target.dispatch(this);
                    IndexExpr indexExpr = (IndexExpr) target;
                    Identifier id = (Identifier) indexExpr.list;
                    SymbolInfo symInfo = sym.get(id.name);
                }
            }
            return null;
        }


        public Void analyze(ExprStmt expr) {
            expr.expr.dispatch(this);
            return null;
        }

        public Void analyze(BinaryExpr expr) {
            if (expr.getInferredType().equals(Type.STR_TYPE)) {
                return strBinExpr(expr);
            } else if (expr.getInferredType().equals(Type.INT_TYPE) || expr.getInferredType().equals(Type.BOOL_TYPE)) {
                return intBoolBinExpr(expr);
            } else if (expr.getInferredType().isListType()) {
            }
            return null;
        }

        public Void strBinExpr(BinaryExpr expr) {
            if (expr.operator.equals("+")) {
                //this is what is supposed to be.
                expr.left.dispatch(this);
                backend.emitSW(A0, SP, -1 * backend.getWordSize(), "Storing Left str");
                expr.left.dispatch(this);
                backend.emitSW(A0, SP, -2 * backend.getWordSize(), "Storing Right Str");

                backend.emitJAL(stringConcat, "Jump to handle String concat");


            } else if (expr.operator.equals("==")) {
            } else if (expr.operator.equals("!=")) {
            } else {
                return null;
            }

            return null;
        }

        public Void intBoolBinExpr(BinaryExpr expr) {
            if (!(expr.operator.equals("and") || expr.operator.equals("or"))) {
                expr.left.dispatch(this);
                backend.emitSW(A0, SP, -1 * backend.getWordSize(), "Store left into A0");
                expr.right.dispatch(this);
                backend.emitSW(A0, SP, -2 * backend.getWordSize(), "Store right into A0.");
                backend.emitLW(T0, SP, -1 * backend.getWordSize(), "Load left into T0.");
                backend.emitLW(T1, SP, -2 * backend.getWordSize(), "Load right into T1.");
            }
            Label condBranch = null;
            Label finish = null;
            switch (expr.operator) {
                case "+":
                    backend.emitADD(A0, T1, T0, "left + right");
                    break;
                case "-":
                    backend.emitSUB(A0, T0, T1, "left - right");
                    break;
                case "*":
                    backend.emitMUL(A0, T1, T0, "left * right");
                    break;
                case "//":
                    Label contDiv = generateLocalLabel();
                    backend.emitBNEZ(T1, contDiv, "Checking if right is 0");
                    backend.emitJ(errorDiv, "Dividing by 0");
                    backend.emitLocalLabel(contDiv, "Right side was not 0");
                    backend.emitDIV(A0, T0, T1, "left / right");
                    break;
                //for optimization probably need to check for 0 div and check how to handle that later
                case "%":
                    Label contMod = generateLocalLabel();
                    backend.emitBNEZ(T1, contMod, "Checking if right is 0");
                    backend.emitJ(errorDiv, "Dividing by 0");
                    backend.emitLocalLabel(contMod, "Right side was not 0");
                    backend.emitREM(A0, T0, T1, "left % right");
                    break;
                //for optimization probably need to check for 0 div and check how to handle that later
                case "==":
                    condBranch = generateLocalLabel();
                    finish = generateLocalLabel();
                    backend.emitBEQ(T0, T1, condBranch, "Going to is equal branch");
                    backend.emitLI(A0, 0, "Loading value of False, because it was not equal");
                    backend.emitJ(finish, "Go to end of equals");
                    backend.emitLocalLabel(condBranch, "Equal to Condition");
                    backend.emitLI(A0, 1, "Loading value of True, because they were equal");
                    backend.emitLocalLabel(finish, "End of equal comparison");
                    break;
                case "!=":
                    condBranch = generateLocalLabel();
                    finish = generateLocalLabel();
                    backend.emitBNE(T0, T1, condBranch, "Going to not equal branch");
                    backend.emitLI(A0, 0, "Loading value of False, because it was equal");
                    backend.emitJ(finish, "Go to end of equals");
                    backend.emitLocalLabel(condBranch, "Not Equal to Condition");
                    backend.emitLI(A0, 1, "Loading value of True, because they were not equal");
                    backend.emitLocalLabel(finish, "End of equal comparison");
                    break;
                case ">":
                    condBranch = generateLocalLabel(); //Coming in with A0 > T1, but only have < sign so need to make it
                    finish = generateLocalLabel();//into T1 < A0
                    backend.emitBLT(T1, T0, condBranch, "branch if A0 > T1");
                    backend.emitLI(A0, 0, "Loading value of False, A0 was not greater than T1");
                    backend.emitJ(finish, "Go to end of greater than compare");
                    backend.emitLocalLabel(condBranch, "Greater than is true cond");
                    backend.emitLI(A0, 1, "Loading value of True, A0 was greater than T1");
                    backend.emitLocalLabel(finish, "End of greater than comparison");
                    break;
                case "<":
                    condBranch = generateLocalLabel();
                    finish = generateLocalLabel();
                    backend.emitBLT(T0, T1, condBranch, "branch if A0 > T1");
                    backend.emitLI(A0, 0, "Loading value of False, A0 was not less than T0");
                    backend.emitJ(finish, "Go to end of less than compare");
                    backend.emitLocalLabel(condBranch, "Less than is true cond");
                    backend.emitLI(A0, 1, "Loading value of True, A0 was less than T0");
                    backend.emitLocalLabel(finish, "End of less than comparison");
                    break;
                case ">=":
                    condBranch = generateLocalLabel();
                    finish = generateLocalLabel();
                    backend.emitBGE(T0, T1, condBranch, "branch if A0 greater than or equal to T1");
                    backend.emitLI(A0, 0, "Loading value of False, A0 was not greater than or equal to T1");
                    backend.emitJ(finish, "Go to end of greater than or equal to");
                    backend.emitLocalLabel(condBranch, "Greater than or equal to cond");
                    backend.emitLI(A0, 1, "Loading value of True, A0 was greater than or equal to T1");
                    backend.emitLocalLabel(finish, "End of greater than or equal to comparison");
                    break;
                case "<=":
                    condBranch = generateLocalLabel();
                    finish = generateLocalLabel();
                    backend.emitBGE(T1, T0, condBranch, "branch if A0 less than or equal to T1");
                    backend.emitLI(A0, 0, "Loading value of False, A0 was not less than or equal to T1");
                    backend.emitJ(finish, "Go to end of less than or equal to");
                    backend.emitLocalLabel(condBranch, "Less than or equal to cond");
                    backend.emitLI(A0, 1, "Loading value of True, A0 was less than or equal to T1");
                    backend.emitLocalLabel(finish, "End of less than or equal to comparison");
                    break;
                case "or":
                    finish = generateLocalLabel();
                    expr.left.dispatch(this);
                    push(A0);
                    backend.emitBNEZ(A0, finish, "Short-circuit for OR");
                    expr.right.dispatch(this);
                    backend.emitLocalLabel(finish, "End of OR operator");
                    break;
                case "and":
                    finish = generateLocalLabel();
                    expr.left.dispatch(this);
                    push(A0);
                    backend.emitBEQZ(A0, finish, "Short-circuit for AND");
                    expr.right.dispatch(this);
                    backend.emitLocalLabel(finish, "End of AND operator");
                    break;
                default:
                    break;
            }
            return null;
        }


        private void push(RiscVBackend.Register t) {
            backend.emitSW(t, SP, 0, "Push part 1");
            backend.emitADDI(SP, SP, -4, "Push part 2");
        }

        public Void analyze(WhileStmt stmt) {
            Label start = generateLocalLabel();
            backend.emitLocalLabel(start, "Start of While Loop");
            stmt.condition.dispatch(this);
            Label end = generateLocalLabel();
            backend.emitBEQZ(A0, end, "While loop should stop looping. Go to end of While Stmt");
            for (Stmt s : stmt.body) {
                s.dispatch(this);
            }
            backend.emitJ(start, "Go back to start of While loop to keep looping");
            backend.emitLocalLabel(end, "End of the While loop");
            return null;

        }


        public Void analyze(ListExpr listExpr) {
            for (int i = 0; i < listExpr.elements.size(); i++) {
                Expr elementExpr = listExpr.elements.get(i);
                elementExpr.dispatch(this);
                backend.emitSW(A0, SP, 0, "Store List Elem");
                backend.emitADDI(SP, SP, -4, "Updating SP");
            }
            backend.emitLI(A0, listExpr.elements.size(), "Passing List Len");
            backend.emitSW(A0, SP, 0, "Pushing list size onto stack");
            backend.emitJAL(consList, "moving to list obj");
            return null;
        }

        public Void analyze(IndexExpr expr) {
            expr.list.dispatch(this);
            backend.emitMV(A1, A0, "Store address in A1");
            backend.emitBEQZ(A0, errorNone, "Check if list is None before accessing element");
            backend.emitMV(T0, A0, "Load list index object into temporary register");
            expr.index.dispatch(this);
            backend.emitLW(T1, T0, getAttrOffset(listClass, "__len__"), "Load list length");
            backend.emitBLT(A0, ZERO, errorOob, "Check if index is negative");
            backend.emitBGE(A0, T1, errorOob, "Check if index is within bounds"); //A0: Index T1: len
            backend.emitADDI(A0, A0, 4, null);
            backend.emitLI(T0, backend.getWordSize(), "Getting the word size");
            backend.emitMUL(A0, A0, T0, null);
            backend.emitADD(A0, A0, A1, null);
            backend.emitLW(A0, A0, 0, "Load element value from memory");
            return null;
        }


        public Void analyze(ForStmt node) {
            if (node.iterable.getInferredType().isListType()) {
                Label startLoop = generateLocalLabel();
                Label endLoop = generateLocalLabel();
                Label temp = generateLocalLabel();
                SymbolInfo symInfo = sym.get(node.identifier.name);
                node.iterable.dispatch(this);
                backend.emitBNEZ(A0, temp, "Ensure not none");
                backend.emitJ(errorNone, "Empty List");
                backend.emitLocalLabel(temp, "Continue execution for for-loop");
                backend.emitSW(A0, SP, 0, "Storing address");
                backend.emitADDI(SP, SP, -4, "Update SP");
                backend.emitMV(T1, ZERO, "Loop index");
                backend.emitSW(T1, SP, 0, "Storing index");
                backend.emitADDI(SP, SP, -4, "Incrementing SP");
                backend.emitMV(A6, SP, "Storing SP val");
                backend.emitLocalLabel(startLoop, "Start of Loop");
                backend.emitLW(T1, SP, 4, "Getting Loop index");
                backend.emitLW(T0, SP, 8, "Loading Address");
                backend.emitLW(T2, T0, getAttrOffset(listClass, "__len__"), null);
                backend.emitBGE(T1, T2, endLoop, "If Loop index is greater than the size of the list end loop");
                backend.emitADDI(T1, T1, 1, "Increment loop index");
                backend.emitSW(T1, SP, 4, "Storing loop index onto stack");
                backend.emitADDI(T6, ZERO, getAttrOffset(listClass, "__len__"), null);
                backend.emitLI(T5, backend.getWordSize(), null);
                backend.emitDIV(T6, T6, T5, null);
                backend.emitADD(T1, T1, T6, null);
                backend.emitLI(T2, backend.getWordSize(), "Word Size");
                backend.emitMUL(T1, T1, T2, null);
                backend.emitADD(T1, T1, T0, "Address of list element");
                backend.emitLW(T0, T1, 0, "Loading list element");
                if (symInfo instanceof GlobalVarInfo) {
                    GlobalVarInfo globVar = (GlobalVarInfo) symInfo;
                    backend.emitSW(T0, globVar.getLabel(), T1, "Storing");
                }
                for (Stmt s : node.body) {
                    s.dispatch(this);
                }
                backend.emitMV(SP, A6, "Resporting SP");
                backend.emitJ(startLoop, "Back to looping");
                backend.emitLocalLabel(endLoop, "End of loop");
                backend.emitMV(A0, A0, "Location of list");
            }
            return null;
        }
}

    /**
     * Emits custom code in the CODE segment.
     *
     * This method is called after emitting the top level and the
     * function bodies for each function.
     *
     * You can use this method to emit anything you want outside of the
     * top level or functions, e.g. custom routines that you may want to
     * call from within your code to do common tasks. This is not strictly
     * needed. You might not modify this at all and still complete
     * the assignment.
     *
     * To start you off, here is an implementation of three routines that
     * will be commonly needed from within the code you will generate
     * for statements.
     *
     * The routines are error handlers for operations on None, index out
     * of bounds, and division by zero. They never return to their caller.
     * Just jump to one of these routines to throw an error and
     * exit the program. For example, to throw an OOB error:
     *   backend.emitJ(errorOob, "Go to out-of-bounds error and abort");
     *
     */
    protected void emitCustomCode() {
        emitErrorFunc(errorNone, "Operation on None");
        emitErrorFunc(errorDiv, "Division by zero");
        emitErrorFunc(errorOob, "Index out of bounds");
        emitMakeInt(makeInt, "Make Int");
        emitMakeBool(makeBool, "Make Bool");
        emitConsList(consList, "Cons List");
        emitStringConcat(stringConcat, "String Concat");
    }

    /** Emit an error routine labeled ERRLABEL that aborts with message MSG. */
    private void emitErrorFunc(Label errLabel, String msg) {
        backend.emitGlobalLabel(errLabel);
        int errNum = -1;
        if (msg.equals("Operation on None")) {
            errNum = ERROR_NONE;
        }
        else if(msg.equals("Division by zero")) {
            errNum = ERROR_DIV_ZERO;
        }
        else if(msg.equals("Index out of bounds")) {
            errNum = ERROR_OOB;
        }
        backend.emitLI(A0, errNum, "Exit code for: " + msg);
        backend.emitLA(A1, constants.getStrConstant(msg), "Load error message as str");
        backend.emitADDI(A1, A1, getAttrOffset(strClass, "__str__"), "Load address of attribute __str__");
        backend.emitJ(abortLabel, "Abort");
    }


    private void emitMakeBool(Label label, String msg) {
        backend.emitGlobalLabel(label);
        Label falseBranch = generateLocalLabel();
        Label finish = generateLocalLabel();
        Label trueConstLabel = constants.getBoolConstant(true);
        Label falseConstLabel = constants.getBoolConstant(false);
        backend.emitBEQZ(A0, falseBranch, "Check to see if value is False, if is False, then go to false bracn");
        backend.emitLA(A0, trueConstLabel, "Value was not False, then it must be True, so load True Value");
        backend.emitJ(finish, "Go to end of makeBool");
        backend.emitLocalLabel(falseBranch, "False branch");
        backend.emitLA(A0, falseConstLabel, "Load False constant");
        backend.emitLocalLabel(finish, "End of makeBool");
        backend.emitJR(RA, null);
    }

    private void emitMakeInt(Label label, String msg) {
        backend.emitGlobalLabel(label);
        backend.emitADDI(SP, SP, -8, "Make space on the stack for RA and A0");
        backend.emitSW(RA, SP, 4, "Get the value of RA from stack");
        backend.emitSW(A0, SP, 0, "Get the arg that was passed to convert int value int to boxed");
        backend.emitLA(A0, intClass.getPrototypeLabel(), "Get addr for int prototype label and store into A0");
        backend.emitJAL(objectAllocLabel, "Go to Alloc");
        backend.emitLW(T0, SP, 0, null);
        backend.emitSW(T0, A0, getAttrOffset(intClass, "__int__"), null);
        backend.emitLW(RA, SP, 4, "Get the RA off stack");
        backend.emitADDI(SP, SP, 8, "Reduce stack size");
        backend.emitJR(RA, "Back to original function");
    }

    private void emitConsList(Label label, String msg) {
        Label consDone = generateLocalLabel();
        Label consCont = generateLocalLabel();
        backend.emitGlobalLabel(label);
        backend.emitADDI(SP, SP, -8, "Make space on the stack for RA and SP ");
        backend.emitSW(RA, SP, 4, "Store the value of RA to stack");
        backend.emitSW(FP, SP, 0, "Store the value for FP to stack");
        backend.emitADDI(FP, SP, 8, "Setting up new FP");
        backend.emitMV(A1, A0, "Loading the list len");
        backend.emitMV(T6, A1, "storing list len for future use");
        backend.emitLA(A0, listClass.getPrototypeLabel(), "Loading List prototype addr to A0");
        backend.emitBEQZ(A1, consDone, "Checking to see if first arg is None");
        backend.emitADDI(A1, A1, 4, "Adding List header");
        backend.emitJAL(objectAllocResizeLabel, "Alloc 2");
        backend.emitMV(T0, T6, "Storing List Len in T0");
        backend.emitSW(T0, A0, getAttrOffset(listClass, "__len__"), null);
        backend.emitSLLI(T1, T0, 2, null);
        backend.emitADD(T1, T1, FP, null);
        backend.emitADDI(T2, A0, 16, null);
        backend.emitLocalLabel(consCont, "Continue");
        backend.emitLW(T3, T1, 0, "Load element from stack");
        backend.emitSW(T3, T2, 0, "Store element in list");
        backend.emitADDI(T1, T1, -4, "Move to next element in list");
        backend.emitADDI(T2, T2, 4, "move to next element in list");
        backend.emitADDI(T0, T0, -1, "Decrease element count");
        backend.emitBNEZ(T0, consCont, "If there are more elements continue");
        backend.emitLocalLabel(consDone, null);
        backend.emitLW(RA, FP, -4, null);
        backend.emitLW(FP, FP, -8, null);
        backend.emitADDI(SP, SP, 8, null);
        backend.emitJR(RA, null);
    }
    private void emitStringConcat(Label label, String msg) {
        Label firstLenZero = generateLocalLabel();
        Label secondLenZero = generateLocalLabel();
        Label loopFirstStr = generateLocalLabel();
        Label setSecondStr = generateLocalLabel();
        Label secondStr = generateLocalLabel();
        Label addNullTerm = generateLocalLabel();
        Label end = generateLocalLabel();
        backend.emitLocalLabel(label, null);
        backend.emitADDI(SP, SP, -12, null);
        backend.emitSW(RA, SP, 8, null);
        backend.emitSW(FP, SP, 12, null);
        //Need to load the 2 strings
        backend.emitLW(T0, FP, 4, "Loading first string into T0");
        backend.emitLW(T1, FP, 0, "Loading second string into T1");
        //Check if either or is Length of 0
        backend.emitLW(T0, T0, getAttrOffset(strClass, "__len__"), "Getting len of first string");
        //branch out if we see 0
        backend.emitBEQZ(T0, firstLenZero, "Checking to see if First String has a len of zero");
        backend.emitLW(T1, T1, getAttrOffset(strClass, "__len__"), "Getting len of second string");
        backend.emitBEQZ(T1, secondLenZero, "Checking to see if the second has a len of zero");
        //get total len to be able to allocate correct amount
        backend.emitADD(T1, T0, T1, "Add the to len to get total len for mem alloc");
        backend.emitSW(T1, FP, -12, "Store the total len onto stack");
        backend.emitADDI(T1, T1, 4, "Adding 4 to account for end of string char");
        backend.emitSRLI(T1, T1, 2, "Div by 2 to get num of words");
        backend.emitADDI(A1, T1, 4, "Adding the amount to be alloced");
        backend.emitLA(A0, strClass.getPrototypeLabel(), "Loading prototype into A0 to prep for Alloc");
        backend.emitJAL(objectAllocResizeLabel, "Alloc2");
        backend.emitLW(T0, FP, -12, "Loading Concated Stirng Len");
        backend.emitSW(T0, A0, getAttrOffset(strClass, "__len___"), "Storing the string");
        backend.emitLW(T0, FP, 4, "Loading the first string");
        backend.emitLW(T1, T0, getAttrOffset(strClass, "__len__"), "Getting len of first string");
        backend.emitADDI(T0, T0, getAttrOffset(strClass, "__str__"), "moving to front of string");
        //Looping through the first string
        backend.emitLocalLabel(loopFirstStr, "Start of first String loop");
        backend.emitBEQZ(T1, setSecondStr, "Go to 2nd if len is 0");
        backend.emitLBU(T3, T0, 0, "Loading the byte into T3");
        backend.emitSB(T3, T2, 0, "Storing the byte into T2");
        backend.emitADDI(T1, T1, -1, "Decrease len by 1 to account for getting first string");
        backend.emitADDI(T0, T0, 1, "Moving string pntr");
        backend.emitADDI(T2, T2, 1, "move to next pos to put the chars into");
        backend.emitJ(loopFirstStr, "Back to top of loop");
        //setting up for the second string
        backend.emitLocalLabel(setSecondStr, "Setting up for second string");
        backend.emitLW(T0, FP, 0, "Loading second string");
        backend.emitLW(T1, T0, 12, "Getting the len but also skipping over metadata");
        backend.emitADDI(T0, T0, 16, "Move to start of string");
        //second string
        backend.emitLocalLabel(secondStr, "Getting second string");
        backend.emitBEQZ(T1, addNullTerm, "If len is 0, finish up on the string");
        backend.emitLBU(T3, T0, 0, "Loading the byte into T3");
        backend.emitSB(T3, T2, 0, "Storing the byte into T2");
        backend.emitADDI(T1, T1, -1, "Decrease len by 1 to account for getting first string");
        backend.emitADDI(T0, T0, 1, "Moving string pntr");
        backend.emitADDI(T2, T2, 1, "move to next pos to put the chars into");
        backend.emitJ(secondStr, "Back to top of loop");
        //Deal with first string of len 0
        backend.emitLocalLabel(firstLenZero, "Case that first string has len of 0");
        backend.emitLW(A0, FP, 0, null);
        backend.emitJ(end, "Finish up");
        //Deal with second string of len 0
        backend.emitLocalLabel(secondLenZero, "Case that second string has len of 0");
        backend.emitLW(A0, FP, 4, null);
        backend.emitJ(end, "Finish up");
        //adding null term
        backend.emitLocalLabel(addNullTerm, "Adding null to end");
        backend.emitSB(ZERO, T2, 0, "store null term");
        //ending
        backend.emitLocalLabel(end, "end");
        backend.emitLW(RA, FP, -4, "Restore original RA");
        backend.emitLW(FP, FP, -8, "Restore original FP");
        backend.emitADDI(SP, SP, 12, "Restore SP");
        backend.emitJR(RA, "back to original");
    }
}