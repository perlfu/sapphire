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
package org.jikesrvm.adaptive.database.callgraph;

import org.jikesrvm.classloader.VM_Method;

/**
 * A call site is a pair: <VM_Method, bcIndex>
 */
public final class VM_CallSite {

  /**
   * Caller method
   */
  private final VM_Method method;

  /**
   * bytecode index of callsite in caller method
   */
  private final int bcIndex;

  /**
   * @param m the VM_Method containing the callsite
   * @param bci the bytecode index of the callsite within m
   */
  public VM_CallSite(VM_Method m, int bci) {
    if (org.jikesrvm.VM.VerifyAssertions) org.jikesrvm.VM._assert(m != null);
    method = m;
    bcIndex = bci;
  }

  /**
   * @return method containing the callsite
   */
  public VM_Method getMethod() { return method; }

  /**
   * @return call site's bytecode index in its method
   */
  public int getBytecodeIndex() {return bcIndex;}

  /**
   * @return string representation of call site
   */
  public String toString() {
    return "<" + method + ", " + bcIndex + ">";
  }

  /**
   * Determine if two call sites are the same.  Exact match: no wild cards.
   *
   * @param obj call site to compare to
   * @return true if call sites are the same; otherwise, return false
   */
  public boolean equals(Object obj) {
    if (obj instanceof VM_CallSite) {
      VM_CallSite cs = (VM_CallSite) obj;
      return method.equals(cs.method) && bcIndex == cs.bcIndex;
    } else {
      return false;
    }
  }

  /**
   * @return hash code
   */
  public int hashCode() {
    return bcIndex + method.hashCode();
  }
}
