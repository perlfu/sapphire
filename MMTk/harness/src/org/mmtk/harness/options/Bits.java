/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.harness.options;

import org.mmtk.harness.Harness;

public final class Bits extends org.vmutil.options.IntOption {
  /**
   * Create the option.
   */
  public Bits() {
    super(Harness.options, "Bits",
        "Bits in word",
        Integer.valueOf(System.getProperty("mmtk.harness.bits", "32")));
  }

  @Override
  protected void validate() {
    failIf(!(value == 32 || value == 64), "Bits must be 32 or 64");
  }
}
