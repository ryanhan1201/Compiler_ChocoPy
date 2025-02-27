package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.types.*;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.*;
import chocopy.common.analysis.types.FuncType;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Analyzes declarations to create a top-level symbol table.
 */
public class DeclarationAnalyzer extends AbstractNodeAnalyzer<Type> {

    /** Current symbol table.  Changes with new declarative region. */
    private SymbolTable<Type> sym = new SymbolTable<>();
    /** Global symbol table. */
    private final SymbolTable<Type> globals = sym;
    /** Receiver for semantic error messages. */
    private final Errors errors;
    private Stack<SymbolTable<Type>> stack = new Stack<>();

    private void err(Node node, String message, Object... args) {
        errors.semError(node, message, args);
    }
    /** A new declaration analyzer sending errors to ERRORS0. */
    public DeclarationAnalyzer(Errors errors0) {
        errors = errors0;
    }

    public SymbolTable<Type> getGlobals() {
        return globals;
    }

    @Override
    public Type analyze(Program program) {
        defaultStuff();
        stack.push(globals);
        sym = stack.peek();
        for (Declaration decl : program.declarations) {
            Identifier id = decl.getIdentifier();
            String name = id.name;

            Type type = decl.dispatch(this);

            if (type != null) {
                if (sym.declares(name)) {
                    err(id, "Duplicate declaration of identifier in same scope: %s", name);
                } else {
                    sym.put(name, type);
                }
            }
        }
        return null;
    }

    @Override
    public Type analyze(VarDef varDef) {
        return ValueType.annotationToValueType(varDef.var.type);
    }

    @Override
    public Type analyze(ClassDef cDef) {
        String parentName = cDef.superClass.name;
        Identifier sID = cDef.superClass;
        String cName = cDef.name.name;

        SymbolTable<Type> parentSym = globals.getSymbolTable(parentName);
        SymbolTable<Type> newSym = new SymbolTable<>(parentSym);

        if (!globals.declares(parentName)) {
            err(sID, "Super-class not defined: %s", parentName);
            return null;
        }
        if (!(globals.get(parentName) instanceof DefClassType)) {
            err(sID, "Super-class must be a class: %s", parentName);
            return null;
        }
        if (((DefClassType) globals.get(parentName)).specialChecker()) {
            err(sID, "Cannot extend special class: %s", parentName);
            return null;
        }

        globals.addScope(cName, newSym);

        stack.push(newSym);

        sym = stack.peek();
        sym.put(cName, new DefClassType(parentName, cName));

        for (Declaration d : cDef.declarations) {
            Identifier dID = d.getIdentifier();
            String dName = dID.name;
            Type type = d.dispatch(this);
            if (sym.declares(dName)) {
                err(dID, "Duplicate declaration of identifier in same scope: %s", dName);
            }
            if (globals.declares(dName) && globals.get(dName) instanceof DefClassType) {
                err(dID, "Cannot shadow class name: %s", dName);
            }

            if (type instanceof ValueType && parentSym.declares(dName)) {
                err(dID, "Cannot re-define attribute: %s", dName);
            }
            sym.put(dName, type);
        }

        stack.pop();
        sym = stack.peek();

        return new DefClassType(parentName, cName);
    }

    private void defaultStuff() {
        List<ValueType> p = new ArrayList<>();
        p.add(ValueType.OBJECT_TYPE);

        globals.put("int", new DefClassType("object"));
        globals.put("str", new DefClassType("object"));
        globals.put("bool", new DefClassType("object"));

        FuncType initFunc = new FuncType(p, ValueType.NONE_TYPE);
        globals.put("len", new FuncType(p, ValueType.INT_TYPE));
        globals.put("input", new FuncType(ValueType.STR_TYPE));
        globals.put("print", new FuncType(p, ValueType.NONE_TYPE));

        SymbolTable<Type> objSym = new SymbolTable<>(globals);
        globals.addScope("object", objSym);
        objSym.put("__init__", initFunc);
        globals.put("object", new DefClassType(null, "object"));
    }

    @Override
    public Type analyze(ClassType cType) {
        if (cType.className.equals("<None>")) {
            return Type.NONE_TYPE;
        }
        return ValueType.annotationToValueType(cType);
    }

    @Override
    public Type analyze(TypedVar t) {
        return t.type.dispatch(this);
    }

    @Override
    public Type analyze(ListType l) {
        return ValueType.annotationToValueType(l);
    }

    @Override
    public Type analyze(FuncDef f) {
        SymbolTable<Type> fSym = new SymbolTable<>(sym);
        stack.push(fSym);
        String fName = f.getIdentifier().name;
        sym.addScope(fName, fSym);
        sym = stack.peek();
        List<ValueType> paramList = new ArrayList<>();
        for (TypedVar p : f.params) {
            Type pType = p.dispatch(this);
            String pName = p.identifier.name;

            if (pType != null){
                if (sym.declares(pName)) {
                    err(p.identifier, "Duplicate declaration of identifier in same scope: %s", pName);
                }
                else {
                    sym.put(pName, pType);
                    paramList.add((ValueType) pType);
                }
            }
        }

        for (Declaration d : f.declarations) {
            String declName = d.getIdentifier().name;
            Type declType = d.dispatch(this);

            if (declType != null){
                if (sym.declares(declName)) {
                    err(d.getIdentifier(), "Duplicate declaration of identifier in same scope: %s", declName);
                } else {
                    sym.put(declName, declType);

                }
            }
        }
        TypeAnnotation r =  f.returnType;
        ValueType retType = (ValueType) r.dispatch(this);
        sym.put("return", retType);
        stack.pop();
        sym = stack.peek();
        return new FuncType(paramList, retType);

    }


}