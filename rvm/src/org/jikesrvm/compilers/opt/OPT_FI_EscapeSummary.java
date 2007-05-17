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

import java.util.HashMap;
import org.jikesrvm.compilers.opt.ir.OPT_Register;

/**
 * This class holds the results of a flow-insensitive escape analysis
 * for a method.
 */
class OPT_FI_EscapeSummary {

  /**
   * Returns true iff ANY object pointed to by symbolic register r
   * MUST be thread local
   */
  boolean isThreadLocal(OPT_Register r) {
    Object result = hash.get(r);
    return result != null && result == THREAD_LOCAL;
  }

  /**
   * Returns true iff ANY object pointed to by symbolic register r
   * MUST be method local
   */
  boolean isMethodLocal(OPT_Register r) {
    Object result = hash2.get(r);
    return result != null && result == METHOD_LOCAL;
  }

  /**
   * record the fact that ALL object pointed to by symbolic register r
   * MUST (or may) escape this thread
   */
  void setThreadLocal(OPT_Register r, boolean b) {
    if (b) {
      hash.put(r, THREAD_LOCAL);
    } else {
      hash.put(r, MAY_ESCAPE_THREAD);
    }
  }

  /**
   * Record the fact that ALL object pointed to by symbolic register r
   * MUST (or may) escape this method
   */
  void setMethodLocal(OPT_Register r, boolean b) {
    if (b) {
      hash2.put(r, METHOD_LOCAL);
    } else {
      hash2.put(r, MAY_ESCAPE_METHOD);
    }
  }

  /** Implementation */
  /**
   * A mapping that holds the analysis result for thread-locality for each
   * OPT_Register.
   */
  private final HashMap<OPT_Register, Object> hash = new HashMap<OPT_Register, Object>();

  /**
   * A mapping that holds the analysis result for method-locality for each
   * OPT_Register.
   */
  private final HashMap<OPT_Register, Object> hash2 = new HashMap<OPT_Register, Object>();

  /**
   * Static object used to represent analysis result
   */
  static final Object THREAD_LOCAL = new Object();
  /**
   * Static object used to represent analysis result
   */
  static final Object MAY_ESCAPE_THREAD = new Object();
  /**
   * Static object used to represent analysis result
   */
  static final Object METHOD_LOCAL = new Object();
  /**
   * Static object used to represent analysis result
   */
  static final Object MAY_ESCAPE_METHOD = new Object();
}



