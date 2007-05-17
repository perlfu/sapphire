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
package org.jikesrvm.compilers.baseline;

/**
 * Profile data for a branch instruction.
 */
public abstract class VM_BranchProfile {
  /**
   * The bytecode index of the branch instruction
   */
  protected final int bci;

  /**
   * The number of times the branch was executed.
   */
  protected final float freq;

  /**
   * @param _bci the bytecode index of the source branch instruction
   * @param _freq the number of times the branch was executed
   */
  VM_BranchProfile(int _bci, float _freq) {
    bci = _bci;
    freq = _freq;
  }

  public final int getBytecodeIndex() { return bci; }

  public final float getFrequency() { return freq; }

}
