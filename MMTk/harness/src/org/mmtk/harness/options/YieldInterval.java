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

/**
 * The maximum heap size.
 */
public final class YieldInterval extends org.vmutil.options.IntOption {
  /**
   * Create the option.
   */
  public YieldInterval() {
    super(Harness.options, "Yield Interval",
        "Yield interval for fixed scheduler policies",
        10);
  }

  protected void validate() {
  }
}
