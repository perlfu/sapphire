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
package org.mmtk.utility.options;

/**
 * Concurrent trigger percentage
 */
public class ConcurrentTrigger extends org.vmutil.options.IntOption {
  /**
   * Create the option.
   */
  public ConcurrentTrigger() {
    super(Options.set, "Concurrent Trigger",
          "Concurrent trigger percentage, allocation (pages) or time (us)",
          -1);
  }

  public int getValueForMethod(int method) {
    if (this.getValue() == -1) {
      if (method == ConcurrentTriggerMethod.ALLOCATION)
        return 8192;
      else if (method == ConcurrentTriggerMethod.PERCENTAGE)
        return 50;
      else if (method == ConcurrentTriggerMethod.PERIOD)
        return 300 * 1000;
      else if (method == ConcurrentTriggerMethod.TIME)
        return 250 * 1000;
      else
        return 0;
    } else {
      return this.getValue();
    }
  }
  
  /**
   * Only accept valid values
   */
  @Override
  protected void validate() {
    failIf(this.value < -1, "Trigger must be greater than 0");
  }
}
