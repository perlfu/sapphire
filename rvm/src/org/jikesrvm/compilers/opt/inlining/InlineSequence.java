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
package org.jikesrvm.compilers.opt.inlining;

import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.util.Stack;

/**
 * Represents an inlining sequence.
 * Used to uniquely identify program locations.
 */
public final class InlineSequence {
  static final boolean DEBUG = false;

  /**
   * Current method.
   */
  public final VM_NormalMethod method;

  /**
   * Caller info.  null if none.
   */
  public final InlineSequence caller;

  /**
   * bytecode index (in caller) of call site
   */
  public int bcIndex;

  /**
   * We need more detailed information of call site than bcIndex.
   */
  final Instruction callSite;

  /**
   * @return contents of {@link #method}
   */
  public VM_NormalMethod getMethod() {
    return method;
  }

  /**
   * @return contents of {@link #caller}
   */
  public InlineSequence getCaller() {
    return caller;
  }

  public boolean equals(Object o) {
    if (!(o instanceof InlineSequence)) return false;
    InlineSequence is = (InlineSequence) o;
    if (method == null) return (is.method == null);
    if (!method.equals(is.method)) return false;
    if (bcIndex != is.bcIndex) return false;
    if (caller == null) return (is.caller == null);
    return (caller.equals(is.caller));
  }

  /**
   * Constructs a new top-level inline sequence operand.
   *
   * @param method current method
   */
  public InlineSequence(VM_NormalMethod method) {
    this(method, null, -1);
  }

  /**
   * Constructs a new inline sequence operand.
   *
   * @param method current method
   * @param caller caller info
   * @param bcIndex bytecode index of call site
   */
  InlineSequence(VM_NormalMethod method, InlineSequence caller, int bcIndex) {
    this.method = method;
    this.caller = caller;
    this.callSite = null;
    this.bcIndex = bcIndex;
  }

  /**
   * Constructs a new inline sequence operand.
   *
   * @param method current method
   * @param caller caller info
   * @param callsite the call site instruction of this callee
   */
  public InlineSequence(VM_NormalMethod method, InlineSequence caller, Instruction callsite) {
    this.method = method;
    this.caller = caller;
    this.callSite = callsite;
    this.bcIndex = callsite.bcIndex;
  }

  public Instruction getCallSite() {
    return this.callSite;
  }

  /**
   * Returns the string representation of this inline sequence.
   */
  public String toString() {
    StringBuilder sb = new StringBuilder(" ");
    for (InlineSequence is = this; is != null; is = is.caller) {
      sb.append(is.method.getDeclaringClass().getDescriptor()).append(" ").
          append(is.method.getName()).append(" ").
          append(is.method.getDescriptor()).append(" ").
          append(is.bcIndex).append(" ");
    }
    return sb.toString();
  }

  /**
   * return the depth of inlining: (0 corresponds to no inlining)
   */
  public int getInlineDepth() {
    int depth = 0;
    InlineSequence parent = this.caller;
    while (parent != null) {
      depth++;
      parent = parent.caller;
    }
    return depth;
  }

  /**
   * Return the root method of this inline sequence
   */
  public VM_NormalMethod getRootMethod() {
    InlineSequence parent = this;
    while (parent.caller != null) {
      parent = parent.caller;
    }
    return parent.method;
  }

  /**
   * Does this inline sequence contain a given method?
   */
  public boolean containsMethod(VM_Method m) {
    if (method == m) return true;
    if (caller == null) return false;
    return (caller.containsMethod(m));
  }

  /**
   * Return a hashcode for this object.
   *
   * TODO: Figure out a better hashcode.  Efficiency doesn't matter
   * for now.
   *
   * @return the hashcode for this object.
   */
  public int hashCode() {
    return bcIndex;
  }

  public java.util.Enumeration<InlineSequence> enumerateFromRoot() {
    return new java.util.Enumeration<InlineSequence>() {
      Stack<InlineSequence> stack;

      {
        stack = new Stack<InlineSequence>();
        InlineSequence parent = InlineSequence.this;
        while (parent.caller != null) {
          stack.push(parent);
          parent = parent.caller;
        }
      }

      public boolean hasMoreElements() {
        return !stack.isEmpty();
      }

      public InlineSequence nextElement() {
        return stack.pop();
      }
    };
  }
}
