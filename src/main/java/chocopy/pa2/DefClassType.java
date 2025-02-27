package chocopy.pa2;

import chocopy.common.analysis.types.Type;

public class DefClassType extends Type{
    private final Boolean specialCheck;
    public final String parentClass;
    private final String className;

    public DefClassType(String parentClass, String cName) {
        this.specialCheck = false;
        this.className = cName;
        this.parentClass = parentClass;
    }

    public DefClassType(String cName) {
        this.specialCheck = true;
        this.parentClass = "object";
        this.className = cName;
    }
    @Override
    public String className() {
        return this.className;
    }
    public String getParentClass() {
        return this.parentClass;
    }
    public boolean specialChecker() {
        return this.specialCheck;
    }
}