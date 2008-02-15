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

import java.util.Arrays;
/*
 * An OsrTypeInfoOperand object keeps type information of locals
 * and stacks at a byte code index.
 */


public final class OsrTypeInfoOperand extends Operand {

  /**
   * The data type.
   */
  public byte[] localTypeCodes;
  public byte[] stackTypeCodes;

  /**
   * Create a new type operand with the specified data type.
   */
  public OsrTypeInfoOperand(byte[] ltcodes, byte[] stcodes) {
    this.localTypeCodes = ltcodes;
    this.stackTypeCodes = stcodes;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  public Operand copy() {
    return new OsrTypeInfoOperand(localTypeCodes, stackTypeCodes);
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
    boolean result = true;

    if (!(op instanceof OsrTypeInfoOperand)) {
      return false;
    }

    OsrTypeInfoOperand other = (OsrTypeInfoOperand) op;

    result =
        Arrays.equals(this.localTypeCodes, other.localTypeCodes) &&
        Arrays.equals(this.stackTypeCodes, other.stackTypeCodes);

    return result;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    StringBuilder buf = new StringBuilder("OsrTypeInfo(");
    for (int i = 0, n = localTypeCodes.length; i < n; i++) {
      buf.append((char) localTypeCodes[i]);
    }

    buf.append(",");
    for (int i = 0, n = stackTypeCodes.length; i < n; i++) {
      buf.append((char) stackTypeCodes[i]);
    }

    buf.append(")");

    return buf.toString();
  }
}
