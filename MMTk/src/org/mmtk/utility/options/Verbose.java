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
package org.mmtk.utility.options;

/**
 * GC verbosity level.
 */
public final class Verbose extends IntOption {
  /**
   * Create the option.
   */
  public Verbose() {
    super("Verbose",
          "GC verbosity level",
          0);
  }

  /**
   * Only accept non-negative values.
   */
  protected void validate() {
    failIf(this.value < 0, "Unreasonable verbosity level");
  }
}
