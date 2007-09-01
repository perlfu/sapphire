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
 * Concurrent trigger percentage
 */
public class ConcurrentTrigger extends IntOption {
  /**
   * Create the option.
   */
  public ConcurrentTrigger() {
    super("Concurrent Trigger",
          "Concurrent trigger percentage",
          50);
  }

  /**
   * Only accept values between 1 and 100 (inclusive)
   */
  protected void validate() {
    failIf(this.value <= 0, "Trigger must be between 1 and 100");
    failIf(this.value > 100, "Trigger must be between 1 and 100");
  }
}
