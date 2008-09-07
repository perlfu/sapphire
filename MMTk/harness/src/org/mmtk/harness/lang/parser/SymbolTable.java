/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.harness.lang.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.mmtk.harness.lang.Declaration;
import org.mmtk.harness.lang.ast.Type;
import org.mmtk.harness.lang.runtime.BoolValue;
import org.mmtk.harness.lang.runtime.IntValue;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.StringValue;
import org.mmtk.harness.lang.runtime.Value;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Parser symbol table.
 *
 * Implements java-style scoping rules.  Allocates variables to slots in a
 * stack frame linearly and never re-uses slots.
 *
 * TODO - implement stack maps so that out-of-scope variables don't hold on
 * to unreachable objects.
 */
public class SymbolTable {

  /** These are illegal variable and method names */
  public static final List<String> reservedWords = new ArrayList<String>();

  static {
    reservedWords.add("alloc");
    reservedWords.add("assert");
    reservedWords.add("else");
    reservedWords.add("if");
    reservedWords.add("int");
    reservedWords.add("object");
    reservedWords.add("print");
    reservedWords.add("spawn");
    reservedWords.add("while");
  }

  static final boolean TRACE = false;

  /** The table of symbols */
  private Map<String, Symbol> table = new HashMap<String,Symbol>();

  /** The stack frame map */
  private List<Declaration> stackMap = new ArrayList<Declaration>();

  /** The next free stack frame slot */
  private int nextLocation = 0;

  /** The current syntactic scope level */
  private int currentScope = 0;

  int getCurrentScope() { return currentScope; }
  int getFreeLocation() { return nextLocation++; }

  /**
   * Declare a new variable
   *
   * @param name Variable name
   * @param type Variable type
   */
  void declare(String name, Type type) {
    if (reservedWords.contains(name))
      throw new RuntimeException(name + " is a reserved word");
    if (table.containsKey(name))
      throw new RuntimeException("Symbol "+name+" already defined");
    Symbol symbol = new Symbol(this,name,type);
    table.put(name, symbol);
    stackMap.add(new Declaration(symbol,initialValue(type)));
  }

  /**
   * Is the given variable defined ?
   * @param name
   * @return
   */
  boolean isDefined(String name) {
    return table.containsKey(name);
  }

  /**
   * Symbol table entry for the named variable
   * @param name
   * @return
   */
  Symbol getSymbol(String name) {
    return table.get(name);
  }

  /**
   * Type of the named variable
   * @param name
   * @return
   */
  Type getType(String name) {
    return table.get(name).getType();
  }

  /**
   * Stack frame location of the given variable
   * @param name
   * @return
   */
  int getLocation(String name) {
    Symbol symbol = table.get(name);
    if (symbol == null) {
      throw new RuntimeException(String.format("symbol \"%s\" not found",name));
    }
    return symbol.getLocation();
  }

  /**
   * Return the symbol table as a list of declarations
   * @return
   */
  List<Declaration> declarations() {
    return Collections.unmodifiableList(stackMap);
  }

  /**
   * Enter an inner syntactic scope
   */
  void pushScope() {
    currentScope++;
  }

  /**
   * Leave an inner syntactic scope, deleting all inner symbols
   */
  void popScope() {
    Set<Entry<String, Symbol>> entrySet = table.entrySet();
    Iterator<Entry<String, Symbol>> iterator = entrySet.iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, Symbol> entry = iterator.next();
      if (entry.getValue().getLevel() == currentScope)
        iterator.remove();
    }
    currentScope--;
  }

  /**
   * Initial value for a variable of a given type.  Actually allocates
   * the Value object that will hold the variables value.
   *
   * @param type
   * @return
   */
  private static Value initialValue(Type type) {
    switch(type) {
      case INT: return IntValue.ZERO;
      case OBJECT: return new ObjectValue(ObjectReference.nullReference());
      case STRING: return new StringValue("");
      case BOOLEAN: return BoolValue.FALSE;
    }
    throw new RuntimeException("Invalid type");
  }
}
