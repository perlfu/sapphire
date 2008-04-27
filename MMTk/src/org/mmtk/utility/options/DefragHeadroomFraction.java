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

import static org.mmtk.policy.immix.ImmixConstants.DEFAULT_DEFRAG_HEADROOM_FRACTION;

public class DefragHeadroomFraction extends FloatOption {
  /**
   * Create the option.
   */
  public DefragHeadroomFraction() {
    super("Defrag Headroom Fraction",
          "Allow the defrag this fraction of the heap as headroom during defrag.",
          DEFAULT_DEFRAG_HEADROOM_FRACTION);
  }

  /**
   * Ensure the value is valid.
   */
  protected void validate() {
    failIf((this.value < 0 || this.value > 1.0), "Ratio must be a float between 0 and 1");
  }
}
