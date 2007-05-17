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
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Represents a constant float operand.
 *
 * @see OPT_Operand
 */
public final class OPT_FloatConstantOperand extends OPT_ConstantOperand implements VM_SizeConstants {

  /**
   * Value of this operand.
   */
  public float value;

  /**
   * Offset in JTOC where this float constant lives (0 for constants
   * generated from constant folding).
   */
  public Offset offset;

  /**
   * Constructs a new float constant operand with the specified value.
   *
   * @param v value
   */
  public OPT_FloatConstantOperand(float v) {
    value = v;
    if (v == 0.f) {
      offset = VM_Entrypoints.zeroFloatField.getOffset();
    } else if (v == 1.f) {
      offset = VM_Entrypoints.oneFloatField.getOffset();
    } else if (v == 2.f) {
      offset = VM_Entrypoints.twoFloatField.getOffset();
    } else {
      offset = Offset.zero();
    }
  }

  /**
   * Constructs a new float constant operand with the specified value and JTOC offset.
   *
   * @param v value
   * @param i offset in the jtoc
   */
  public OPT_FloatConstantOperand(float v, Offset i) {
    value = v;
    offset = i;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return new OPT_FloatConstantOperand(value, offset);
  }

  /**
   * Return the {@link VM_TypeReference} of the value represented by the operand.
   *
   * @return VM_TypeReference.Float
   */
  public VM_TypeReference getType() {
    return VM_TypeReference.Float;
  }

  /**
   * Does the operand represent a value of the float data type?
   *
   * @return <code>true</code>
   */
  public boolean isFloat() {
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
  public boolean similar(OPT_Operand op) {
    return (op instanceof OPT_FloatConstantOperand) && (value == ((OPT_FloatConstantOperand) op).value);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return Float.toString(value);
  }
}
