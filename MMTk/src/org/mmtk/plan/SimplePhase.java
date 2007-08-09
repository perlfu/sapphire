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
package org.mmtk.plan;

import org.mmtk.utility.Constants;
import org.mmtk.utility.statistics.Timer;
import org.mmtk.utility.Log;

import org.vmmagic.pragma.*;

/**
 * Phases of a garbage collection.
 *
 */
@Uninterruptible
public final class SimplePhase extends Phase
  implements Constants {
  /****************************************************************************
   * Instance fields
   */

  /**
   * Construct a phase given just a name and a global/local ordering
   * scheme.
   *
   * @param name The name of the phase
   */
  protected SimplePhase(String name) {
    super(name);
  }

  /**
   * Construct a phase, re-using a specified timer.
   *
   * @param name Display name of the phase
   * @param timer Timer for this phase to contribute to
   */
  protected SimplePhase(String name, Timer timer) {
    super(name, timer);
  }

  /**
   * Display a phase for debugging purposes.
   */
  protected void logPhase() {
    Log.write("SimplePhase(");
    Log.write(name);
    Log.write(")");
  }
}
