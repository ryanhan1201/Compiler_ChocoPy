package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.types.*;
import chocopy.common.astnodes.*;
import java.util.Stack;

public class DeclarationSemanticCheck extends AbstractNodeAnalyzer<Type> {

    private SymbolTable<Type> sym = null;
    private SymbolTable<Type> global;
    private final Errors errors;
    private Stack<SymbolTable<Type>> stack = new Stack<>();
    public DeclarationSemanticCheck(Errors errors0, SymbolTable<Type> globalSymbols) {
        global = globalSymbols;
        errors = errors0;
    }

    private void err(Node node, String message, Object... args) {
        errors.semError(node, message, args);
    }

    @Override
    public Type analyze(Program program) {
        stack.push(global);
        sym = stack.peek();

        for (Declaration d : program.declarations) {
            d.dispatch(this);
        }

        for (Stmt s : program.statements) {
            if (s instanceof ReturnStmt) {
                err(s, "Return statement cannot appear at the top level");
            }
        }
        return null;
    }

    @Override
    public Type analyze(TypedVar tVar) {
        return tVar.type.dispatch(this);
    }

    @Override
    public Type analyze(VarDef vDef) {
        return vDef.var.dispatch(this);
    }


    @Override
    public Type analyze(ClassType t) {
        String cName = t.className;
        if ("<None>".equals(cName)) {
            return Type.NONE_TYPE;
        }
        if (!((global.declares(cName) && global.get(cName) instanceof DefClassType) || (sym.get(cName) != null && sym.get(cName) instanceof DefClassType))) {
            err(t, "Invalid type annotation; there is no class named: %s", cName);
            return null;
        }
        return ValueType.annotationToValueType(t);
    }


    @Override
    public Type analyze(ListType l) {
        return ValueType.annotationToValueType(l);
    }

    @Override
    public Type analyze(FuncDef fDef) {
        Identifier fId = fDef.getIdentifier();
        String fName = fId.name;

        sym = sym.getSymbolTable(fName);
        stack.push(sym);

        for (TypedVar param : fDef.params){
            Identifier paramId = param.identifier;
            String paramName = paramId.name;
            param.dispatch(this);
            if (((global.declares(paramName) && global.get(paramName) instanceof DefClassType) || (sym.get(paramName) != null && sym.get(paramName) instanceof DefClassType))) {
                err(paramId, "Cannot shadow class name: %s", paramName);
            }
        }
        for (Declaration d : fDef.declarations) {
            Identifier dId = d.getIdentifier();
            String dName = dId.name;
            Type dType = d.dispatch(this);
            if (((global.declares(dName) && global.get(dName) instanceof DefClassType) || (sym.get(dName) != null && sym.get(dName) instanceof DefClassType))) {
                err(dId, "Cannot shadow class name: %s", dName);
            }
            if (dType != null && !sym.declares(dName)) {
                sym.put(dName, dType);
            }
        }
        for (Stmt s : fDef.statements) {
            s.dispatch(this);
        }
        fDef.returnType.dispatch(this);

        stack.pop();
        sym = stack.peek();

        return sym.get(fName);
    }


    @Override
    public Type analyze(NonLocalDecl nonlocal) {
        SymbolTable<Type> parent = sym.getParent();
        Identifier id = nonlocal.getIdentifier();
        String IDName = id.name;

        if (parent == global || !parent.get(IDName).isValueType() || !parent.declares(IDName) ) {
            err(id, "Not a nonlocal variable: %s", IDName);
            return null;
        }
        if (sym.declares(IDName)) {
            err(id, "Duplicate declaration of identifier in same scope: %s", IDName);
            return null;
        }

        return parent.get(IDName);

    }

    @Override
    public Type analyze(GlobalDecl glob) {
        Identifier id = glob.getIdentifier();
        String idName = id.name;
        if (!this.global.declares(idName) || !this.global.get(idName).isValueType()) {
            err(id, "Not a global variable: %s", idName);
            return null;
        }
        if (sym.declares(idName)) {
            err(id, "Duplicate declaration of identifier in same scope: %s", idName);
            return null;
        }
        return this.global.get(idName);
    }


    @Override
    public Type analyze(ClassDef cDef) {
        String cName = cDef.name.name;
        sym = global.getSymbolTable(cName);

        if (sym == null) {
            sym = stack.peek();
            return null;
        }

        stack.push(sym);

        for (Declaration d : cDef.declarations) {
            Type t = d.dispatch(this);
            if (!(t == null)) {
                if (t instanceof FuncType) {
                    FuncType funcType = (FuncType) t;
                    String funcName = d.getIdentifier().name;
                    classDefErrorCheck(cName, funcType, d.getIdentifier(), funcName);
                }
            }
        }

        stack.pop();
        sym = stack.peek();

        return global.get(cName);
    }
    @Override
    public Type analyze(AssignStmt s) {
        for (Expr e : s.targets) {
            if (e instanceof Identifier) {
                Identifier id = (Identifier) e;
                String exprName = id.name;
                validateVarDecl(id, exprName);
            }
        }

        return null;

    }

    private void validateVarDecl(Identifier iden, String name) {
        if (!sym.declares(name)) {
            errors.semError(iden, "Cannot assign to variable that is not explicitly declared in this scope: %s", name);
        }
    }

    private void classDefErrorCheck(String className, FuncType fType, Identifier id, String fName) {
        if (fType.parameters.size() == 0 || !(fType.getParamType(0)).className().equals(className)) {
            err(id, "First parameter of the following method must be of the enclosing class: %s", fName);
        } else {
            SymbolTable<Type> parent = sym.getParent();
            if (parent.get(fName) == null || !(parent.get(fName) instanceof ValueType)) {
                FuncType parentType = (FuncType) parent.get(fName);
                if (parentType != null) {
                    if (parentType.parameters.size() != fType.parameters.size()) {
                        err(id, "Method overridden with different type signature: %s", fName);
                    } else {
                        if (fName.equals("__init__") && fType.parameters.size() == 1) {
                            return;
                        } else {
                            for (int i = 1; i < fType.parameters.size(); i++) {
                                ValueType parentParams = parentType.parameters.get(i);
                                ValueType param = fType.parameters.get(i);
                                if (!parentParams.equals(param)) {
                                    err(id, "Method overridden with different type signature: %s", fName);
                                }
                            }
                            if (!parentType.returnType.equals(fType.returnType)) {
                                err(id, "Method overridden with different type signature: %s", fName);
                            }
                        }
                    }
                }
            } else {
                err(id, "Cannot re-define attribute: %s", fName);
            }
        }
    }
}