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
package org.jikesrvm.compilers.opt.ir.operand;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_TypeReference;

/**
 * Represents a constant code operand, found for example, from an
 * TIBConstantOperand. NB we don't use an object constant operand
 * because: 1) code doesn't form part of the object literals 2) we
 * need to support replacement
 *
 * @see Operand
 */
public final class CodeConstantOperand extends ConstantOperand {

  /**
   * The non-null method for the code represent
   */
  public final VM_Method value;

  /**
   * Construct a new code constant operand
   *
   * @param v the method of this TIB
   */
  public CodeConstantOperand(VM_Method v) {
    if (VM.VerifyAssertions) VM._assert(v != null);
    value = v;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  public Operand copy() {
    return new CodeConstantOperand(value);
  }

  /**
   * Return the {@link VM_TypeReference} of the value represented by the operand.
   *
   * @return VM_TypeReference.JavaLangObjectArray
   */
  public VM_TypeReference getType() {
    return VM_TypeReference.CodeArray;
  }

  /**
   * Does the operand represent a value of the reference data type?
   *
   * @return <code>true</code>
   */
  public boolean isRef() {
    return true;
  }

  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code>
   *           if they are not.
   */
  public boolean similar(Operand op) {
    return (op instanceof CodeConstantOperand) && value == ((CodeConstantOperand) op).value;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return "code \"" + value + "\"";
  }
}
