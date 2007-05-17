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
package org.jikesrvm.compilers.opt;

/**
 * OPT_DF_Operator.java
 *
 * represents a function for OPT_DF_LatticeCell values
 */
abstract class OPT_DF_Operator {

  /**
   * Evaluate this equation, setting a new value for the
   * left-hand side.
   *
   * @param operands The operands for this operator.  operands[0]
   *                is the left-hand side.
   * @return true if the lhs value changes. false otherwise.
   */
  abstract boolean evaluate(OPT_DF_LatticeCell[] operands);
}



