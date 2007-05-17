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
package org.jikesrvm.adaptive.database.methodsamples;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.VM_CompiledMethod;

/**
 * Wrapper around a pair of parallel arrays:
 *  (1) an array of compiled method id's
 *  (2) an array of counts: how many times each compiled method id is counted
 */
public final class VM_MethodCountSet {
  /**
   * array of compiled methods
   */
  VM_CompiledMethod[] cms;
  /**
   * array of counts
   */
  double[] counters;

  /**
   * Constructor
   *
   * @param _cms array of compiled method ids
   * @param _counters array of counters
   */
  VM_MethodCountSet(VM_CompiledMethod[] _cms, double[] _counters) {
    if (VM.VerifyAssertions) VM._assert(_cms.length == _counters.length);
    cms = _cms;
    counters = _counters;
  }

  /**
   * String representation of fields
   *
   * @return string representation of compiled method id's and thier counts
   */
  public String toString() {
    String ans = "";
    for (int i = 0; i < cms.length; i++) {
      ans += cms[i] + " = " + counters[i] + "\n";
    }
    return ans;
  }
}
