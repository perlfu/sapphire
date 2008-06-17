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
package org.jikesrvm.adaptive.recompilation.instrumentation;

import org.jikesrvm.adaptive.util.AOSOptions;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.opt.driver.InstrumentationPlan;

/**
 * An instance of this class is created for each method that is
 * instrumented by the adaptive system.  It serves as a place to put
 * information that is needed by the instrumentation phases.  Is is
 * different from an InstrumentationPlan because it contains
 * information that the non-adaptive opt-compiler can't see.
 */
public class AOSInstrumentationPlan extends InstrumentationPlan {
  /**
   * Construct empty plan, must setup manually
   **/
  public AOSInstrumentationPlan(NormalMethod method) {
  }

  /**
   * Construct based on options
   **/
  public AOSInstrumentationPlan(AOSOptions options, NormalMethod method) {
    // If we want to collect method invocation counts.
    if (options.INSERT_METHOD_COUNTERS_OPT) {
    }
  }

  /**
   * Initialize instrumentation by the opt compiler immediately before
   * compilation begins.
   **/
  public void initInstrumentation(NormalMethod method) {
  }

  /**
   * Called after compilation is complete.  If instrumentation has
   * occurred, perform some cleanup/finalization
   **/

  public void finalizeInstrumentation(NormalMethod method) {
  }
}

