package chocopy.pa2;

import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.analysis.types.ListValueType;
import chocopy.common.analysis.types.FuncType;
import chocopy.common.astnodes.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.BinaryExpr;
import chocopy.common.astnodes.Declaration;
import chocopy.common.astnodes.Errors;
import chocopy.common.astnodes.ExprStmt;
import chocopy.common.astnodes.Identifier;
import chocopy.common.astnodes.IntegerLiteral;
import chocopy.common.astnodes.Node;
import chocopy.common.astnodes.Program;
import chocopy.common.astnodes.Stmt;
import static chocopy.common.analysis.types.Type.INT_TYPE;
import static chocopy.common.analysis.types.Type.OBJECT_TYPE;
import static chocopy.common.analysis.types.Type.*;

/**
 * Analyzer that performs ChocoPy type checks on all nodes.  Applied after
 * collecting declarations.
 */
public class TypeChecker extends AbstractNodeAnalyzer<Type> {

    /**
     * The current symbol table (changes depending on the function
     * being analyzed).
     */

    private SymbolTable<Type> sym;
    private SymbolTable<Type> global;

    /**
     * Collector for errors.
     */
    private Errors errors;

    private Stack<SymbolTable<Type>> stack = new Stack<>();

    /**
     * Creates a type checker using GLOBALSYMBOLS for the initial global
     * symbol table and ERRORS0 to receive semantic errors.
     */
    public TypeChecker(SymbolTable<Type> globalSymbols, Errors errors0) {
        this.global = globalSymbols;
        errors = errors0;
    }

    /**
     * Inserts an error message in NODE if there isn't one already.
     * The message is constructed with MESSAGE and ARGS as for
     * String.format.
     */
    private void err(Node node, String message, Object... args) {
        errors.semError(node, message, args);
    }

    @Override
    public Type analyze(Program program) {
        stack.push(global);
        sym = stack.peek();

        for (Declaration decl : program.declarations) {
            decl.dispatch(this);
        }
        for (Stmt stmt : program.statements) {
            stmt.dispatch(this);
        }

        stack.pop();

        return null;
    }


    @Override
    public Type analyze(ClassDef c) {
        Identifier id = c.getIdentifier();
        String idName = id.name;

        sym = global.getSymbolTable(idName);
        stack.push(sym);

        for (Declaration d : c.declarations) {
            d.dispatch(this);
        }

        stack.pop();
        sym = stack.peek();

        return null;
    }

    @Override
    public Type analyze(FuncDef f) {
        Identifier id = f.getIdentifier();
        String idName = id.name;

        sym = sym.getSymbolTable(idName);
        stack.push(sym);

        for (Declaration d : f.declarations) {
            d.dispatch(this);
        }

        Type retType = null;

        for (Stmt s : f.statements) {
            s.dispatch(this);
            if (s instanceof ReturnStmt && ((ReturnStmt) s).value != null) {
                retType = ((ReturnStmt) s).value.getInferredType();
            }
        }

        if (retType == null && !sym.get("return").equals(NONE_TYPE) && !sym.get("return").equals(OBJECT_TYPE)) {
            err(id, "All paths in this function/method must have a return statement: %s", idName);
        }

        stack.pop();
        sym = stack.peek();

        return null;
    }

    @Override
    public Type analyze(ExprStmt e) {
        e.expr.dispatch(this);
        return null;
    }

    @Override
    public Type analyze(VarDef v) {
        String vName = v.var.identifier.name;
        Type varType = sym.get(vName);
        Type valType = v.value.dispatch(this);
        boolean compatible = false;
        if (varType == null || valType == null) {
            compatible = false;
        } else if (parentCheck(varType, valType)) {
            compatible = true;
        } else if (!varType.isSpecialType() && valType.equals(NONE_TYPE)) {
            compatible = true;
        } else if (varType.isListType() && !varType.elementType().isListType()
                && valType.equals(EMPTY_TYPE)) {
            compatible = true;
        } else if (varType.isListType() && valType.isListType()) {
            compatible = !varType.elementType().isSpecialType() && valType.elementType().equals(NONE_TYPE);
        } else {
            compatible = false;
        }
        if (!compatible) {
            err(v, "Expected type `%s`; got type `%s`", varType, valType);
        }

        return null;
    }

    @Override
    public Type analyze(CallExpr c) {
        String fName = c.function.name;
        Type fType = sym.get(fName);

        List<Type> args = new ArrayList<>();
        for (Expr e : c.args) {
            args.add(e.dispatch(this));
        }

        Type rType;
        List<ValueType> params;

        if (fType instanceof FuncType) {
            c.function.dispatch(this);
            FuncType f = ((FuncType) fType);
            params = f.parameters;
            rType = f.returnType;

        } else if (fType instanceof DefClassType) {
            SymbolTable f = sym.getSymbolTable(fName);
            FuncType scope = ((FuncType) f.get("__init__"));
            params = scope.parameters;
            params = params.subList(1, params.size());
            rType = new ClassValueType(fType.className());

        } else {
            err(c, "Not a function or class: %s", fName);
            return c.setInferredType(OBJECT_TYPE);
        }

        paramChecker(c, params, args, false);

        return c.setInferredType(rType);
    }


    @Override
    public Type analyze(MethodCallExpr m) {
        Type mType = m.method.dispatch(this);
        Type oType = m.method.object.getInferredType();

        if (!mType.isFuncType()) {
            String cName = oType.className();
            String methodName = m.method.member.name;

            err(m, "There is no method named `%s` in class `%s`", methodName, cName);
            return mType;
        }

        List<Type> typeList = new ArrayList<>();
        typeList.add(oType);
        for (Expr e : m.args) {
            typeList.add(e.dispatch(this));
        }

        List<ValueType> params;
        params = ((FuncType) mType).parameters;
        paramChecker(m, params, typeList, true);

        return m.setInferredType(((FuncType) mType).returnType);
    }

    private void paramChecker(Node n, List<ValueType> params, List<Type> paramList, boolean methodCall) {
        if (params.size() != paramList.size()) {
            if (methodCall) {
                err(n, "Expected %d arguments; got %d", params.size() - 1, paramList.size() - 1);
                return;
            } else {
                err(n, "Expected %d arguments; got %d", params.size(), paramList.size());
            }
            return;
        }
        //check if params and args are matching
        for (int i = 0; i < params.size(); i++) {
            Type p = params.get(i);
            Type a = paramList.get(i);
            if (!parentCheck(p, a)) {
                err(n, "Expected type `%s`; got type `%s` in parameter %d", p, a, i);
            }
        }
    }

    @Override
    public Type analyze(ReturnStmt r) {
        Type t = null;
        Type e = sym.get("return");

        if (r.value != null) {
            t = r.value.dispatch(this);
        }
        String type;
        if (t == null) {
            type = "`None`";
        } else {
            type = "type `" + t + "`";
        }
        if (!parentCheck(e, t)) {
            err(r, "Expected type `%s`; got %s", e, type);
        }

        return null;
    }

    @Override
    public Type analyze(IntegerLiteral i) {
        return i.setInferredType(INT_TYPE);
    }


    @Override
    public Type analyze(NoneLiteral n) {
        return n.setInferredType(NONE_TYPE);
    }

    @Override
    public Type analyze(BooleanLiteral b) {
        return b.setInferredType(BOOL_TYPE);
    }

    @Override
    public Type analyze(StringLiteral s) {
        return s.setInferredType(STR_TYPE);
    }


    @Override
    public Type analyze(ListExpr l) {
        if (l.elements == null || l.elements.size() == 0) {
            return l.setInferredType(Type.EMPTY_TYPE);
        } else {
            //Lub of element types
            Type type1 = null;
            for (Expr e : l.elements) {
                Type type2 = e.dispatch(this);
                type1 = lub(type1, type2);

            }
            ListValueType lType = new ListValueType(type1);
            return l.setInferredType(lType);

        }
    }

    public Type analyze(ForStmt f) {
        f.identifier.dispatch(this);
        f.iterable.dispatch(this);

        for (Stmt s : f.body) {
            s.dispatch(this);
        }

        return null;
    }

    public Type analyze(WhileStmt w) {
        w.condition.dispatch(this);

        for (Stmt s : w.body) {
            s.dispatch(this);
        }

        return null;
    }


    public Type analyze(IfStmt i) {
        i.condition.dispatch(this);
        for (Stmt s : i.thenBody) {
            s.dispatch(this);
        }

        for (Stmt s : i.elseBody) {
            s.dispatch(this);
        }

        return null;
    }

    private Type lub(Type type1, Type type2) {
        if (type1 == null) {
            return type2;
        }
        Stack<String> t1Stack = new Stack<>();
        Stack<String> t2Stack = new Stack<>();

        t1Stack = inheritance(type1);
        t2Stack = inheritance(type2);
        ClassValueType answer = null;

        while (!t1Stack.empty() && !t2Stack.empty()) {
            String temp1 = t1Stack.pop();
            String temp2 = t2Stack.pop();
            if (temp1.equals(temp2)) {
                answer = new ClassValueType(temp1);
            }
        }

        return answer;

    }

    @Override
    public Type analyze(AssignStmt assignStmt) {

        Type t2 = assignStmt.value.dispatch(this);
        boolean compatible = false;
        for (Expr target : assignStmt.targets) {
            Type t1 = target.dispatch(this);
            if (target instanceof IndexExpr && t1.equals(STR_TYPE)) {
                err(target, "`%s` is not a list type", t1);
            }
            if (t1 == null || t2 == null) {
                compatible = false;
            } else if (parentCheck(t1, t2)) {
                compatible = true;
            } else if (!t1.isSpecialType() && t2.equals(NONE_TYPE)) {
                compatible = true;
            } else if (t1.isListType() && !t1.elementType().isListType() && t2.equals(EMPTY_TYPE)) {
                compatible = true;
            } else if (t1.isListType() && t2.isListType()) {
                compatible = !t1.elementType().isSpecialType() && t2.elementType().equals(NONE_TYPE);
            } else {
                compatible = false;
            }
            if (!compatible) {
                err(assignStmt, "Expected type `%s`; got type `%s`", t1, t2);
            }

        }
        return null;
    }

    private Stack<String> inheritance(Type t) {

        Stack<String> ret = new Stack<>();
        String currClass = t.className();
        while (currClass != null) {

            ret.push(currClass);
            currClass = ((DefClassType) global.get(currClass)).getParentClass();

        }
        return ret;
    }

    private boolean parentCheck(Type parent, Type child) {
        if (parent == null || child == null) {
            return false;
        }

        // Check for special types
        if (parent.equals(OBJECT_TYPE) && (child.equals(EMPTY_TYPE) || child.equals(NONE_TYPE) || child.isListType())) {
            return true;
        }

        // Check if both are list types and have the same element type
        if (parent.isListType() && child.isListType()) {
            return parent.elementType().equals(child.elementType());
        }

        // Check if both are instances of ClassValueType
        if (!(parent instanceof ClassValueType)
                || !(child instanceof ClassValueType)) {
            return false;
        }

        // Check inheritance between classes
        String parentName = parent.className();
        String childName = child.className();

        while (!parentName.equals(childName)) {
            if (parentName == null || childName == null || global.get(childName) == null) {
                return false;
            }

            DefClassType temp = (DefClassType) global.get(childName);
            childName = temp.getParentClass();

        }
        return true;
    }

    public Type analyze(IndexExpr i) {
        Type t = i.list.dispatch(this);
        Type index = i.index.dispatch(this);

        if (t.isListType()) {
            t = t.elementType();
        } else if (!t.equals(STR_TYPE)) {
            err(i, "Cannot index into type `%s`", t);
            return i.setInferredType(OBJECT_TYPE);
        }
        if (!index.equals(INT_TYPE)) {
            err(i, "Index is of non-integer type `%s`", index);
        }

        return i.setInferredType(t);
    }


    public Type analyze(MemberExpr m) {
        Type objType = m.object.dispatch(this);
        String objName = objType.className();
        String memName = m.member.name;

        SymbolTable<Type> currScope = global.getSymbolTable(objName);

        if (currScope == null || currScope.get(memName) == null) {
            err(m, "There is no attribute named `%s` in class `%s`", memName, objName);
            return OBJECT_TYPE;
        }
        return m.setInferredType(currScope.get(memName));
    }


    @Override
    public Type analyze(BinaryExpr e) {
        Type t1 = e.left.dispatch(this);
        Type t2 = e.right.dispatch(this);

        switch (e.operator) {
            case "and":
            case "or":
                if (BOOL_TYPE.equals(t1) && BOOL_TYPE.equals(t2)) {
                    return e.setInferredType(BOOL_TYPE);
                } else {

                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                    return e.setInferredType(BOOL_TYPE);
                }

            case "<=":
            case ">=":
            case "<":
            case ">":
                if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                    return e.setInferredType(BOOL_TYPE);
                } else {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                    return e.setInferredType(BOOL_TYPE);
                }

            case "==":
            case "!=":
                if ((BOOL_TYPE.equals(t1) && BOOL_TYPE.equals(t2))
                        || (INT_TYPE.equals(t1) && INT_TYPE.equals(t2))) {
                    return e.setInferredType(BOOL_TYPE);
                } else {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                    return e.setInferredType(BOOL_TYPE);
                }
            case "-":
            case "*":
            case "//":
            case "%":
                if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                    return e.setInferredType(INT_TYPE);
                } else {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                    return e.setInferredType(INT_TYPE);
                }
            case "+":
                if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                    return e.setInferredType(INT_TYPE);
                } else if (t1.isListType() && t2.isListType()) {
                    Type t1Type = t1.elementType();
                    Type t2Type = t2.elementType();
                    Type LeastUpperBounds = lub(t1Type, t2Type);
                    return e.setInferredType(new ListValueType(LeastUpperBounds));
                } else if (STR_TYPE.equals(t1) && STR_TYPE.equals(t2)) {
                    return e.setInferredType(STR_TYPE);
                } else {
                    if (!INT_TYPE.equals(t1) && !INT_TYPE.equals(t2)) {
                        err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                                e.operator, t1, t2);
                        return e.setInferredType(OBJECT_TYPE);
                    } else {
                        err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                                e.operator, t1, t2);
                        return e.setInferredType(INT_TYPE);
                    }
                }
            case "is":
                if (!BOOL_TYPE.equals(t1) && !BOOL_TYPE.equals(t2) &&
                        !INT_TYPE.equals(t1) && !INT_TYPE.equals(t2) &&
                        !STR_TYPE.equals(t1) && !STR_TYPE.equals(t2)) {
                    return e.setInferredType(BOOL_TYPE);
                } else {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                    return e.setInferredType(BOOL_TYPE);
                }
            default:
                return e.setInferredType(OBJECT_TYPE);
        }

    }

    @Override
    public Type analyze(Identifier id) {
        String vName = id.name;
        Type vType = sym.get(vName);
        if (vType != null) {
            if (vType instanceof DefClassType) {
                vType = Type.OBJECT_TYPE;
            }
            return id.setInferredType(vType);
        }
        err(id, "Not a variable: %s", vName);
        return id.setInferredType(ValueType.OBJECT_TYPE);
    }

    @Override
    public Type analyze(IfExpr i) {
        i.condition.dispatch(this);

        Type thenType = i.thenExpr.dispatch(this);
        Type elseType = i.elseExpr.dispatch(this);
        Type LeastUpperBound = lub(thenType, elseType);

        return i.setInferredType(LeastUpperBound);
    }

    @Override
    public Type analyze(UnaryExpr expr) {
        Type t1 = expr.operand.dispatch(this);

        switch (expr.operator) {
            case "not":
                if (BOOL_TYPE.equals(t1)) {
                    return expr.setInferredType(BOOL_TYPE);
                } else {
                    err(expr, "Cannot apply operator `%s` on type `%s`",
                            expr.operator, t1);
                    return expr.setInferredType(BOOL_TYPE);
                }

            case "-":
                if (INT_TYPE.equals(t1)) {
                    return expr.setInferredType(INT_TYPE);
                } else {
                    err(expr, "Cannot apply operator `%s` on type `%s`",
                            expr.operator, t1);
                    return expr.setInferredType(INT_TYPE);
                }
            default:
                return expr.setInferredType(OBJECT_TYPE);
        }

    }
}


