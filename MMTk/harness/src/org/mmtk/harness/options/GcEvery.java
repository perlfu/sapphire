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
package org.mmtk.harness.options;

import org.mmtk.harness.Harness;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Alloc;
import org.mmtk.harness.lang.Env;

/**
 * Number of collector threads.
 */
public final class GcEvery extends org.vmutil.options.EnumOption {
  /**
   * Create the option.
   */
  public GcEvery() {
    super(Harness.options, "Gc Every",
          "MMTk Harness gc-stress",
          new String[] { "NONE", "ALLOC", "SAFEPOINT", "WRITEBARRIER" }, "NONE");
  }

  public void apply() {
    switch(getValue()) {
      case 0:
        break;
      case 1:
        Alloc.setGcEveryAlloc();
        break;
      case 2:
        Env.setGcEverySafepoint();
        break;
      case 3:
        Mutator.setGcEveryWB();
        break;
    }
  }

  /**
   * Only accept non-negative values.
   */
  protected void validate() {
  }
}
