package chocopy.pa2;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
/** A block-structured symbol table a mapping identifiers to information
 *  about them of type T in a given declarative region. */
public class SymbolTable<T> {

    /** Contents of the current (innermost) region. */
    private final Map<String, T> tab = new HashMap<>();
    /** Enclosing block. */
    private final SymbolTable<T> parent;

    private final Map<String, SymbolTable<T>> subScope = new HashMap<>();
    /** A table representing a region nested in that represented by
     *  PARENT0. */
    public SymbolTable(SymbolTable<T> parent0) {
        parent = parent0;
    }

    /** A top-level symbol table. */
    public SymbolTable() {
        this.parent = null;
    }

    /** Returns the mapping of NAME in the innermost nested region
     *  containing this one. */
    public T get(String name) {
        if (tab.containsKey(name)) {
            return tab.get(name);
        } else if (parent != null) {
            return parent.get(name);
        } else {
            return null;
        }
    }

    /** Adds a new mapping of NAME -> VALUE to the current region, possibly
     *  shadowing mappings in the enclosing parent. Returns modified table. */
    public SymbolTable<T> put(String name, T value) {
        tab.put(name, value);
        return this;
    }


    /** Returns whether NAME has a mapping in this region (ignoring
     *  enclosing regions. */
    public boolean declares(String name) {
        return tab.containsKey(name);
    }

    /** Returns the parent, or null if this is the top level. */
    public SymbolTable<T> getParent() {
        return this.parent;
    }
    public Set<String> getDeclaredSymbols() {
        return tab.keySet();
    }
    public SymbolTable<T> getSymbolTable(String name) {
        if (subScope.containsKey(name)) {
            return subScope.get(name);
        }
        return null;
    }

    public SymbolTable<T> addScope(String name, SymbolTable<T> sym) {
        subScope.put(name, sym);
        return this;
    }

}