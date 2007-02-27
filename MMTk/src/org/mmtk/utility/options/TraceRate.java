/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.vmmagic.pragma.*;

/**
 * The granularity of the trace being produced.
 * 
 *
 * @author Daniel Frampton
 */
public class TraceRate extends IntOption
  implements org.mmtk.utility.Constants {
  /**
   * Create the option.
   */
  public TraceRate() {
    super("Trace Rate",
        "The granularity of the trace being produced.  By default, the trace has the maximum possible granularity.",
        Integer.MAX_VALUE);
  }

  /**
   * Return the appropriate value.
   * 
   * @return the trace rate.
   */
  @Uninterruptible
  public int getValue() { 
    return (this.value < BYTES_IN_ADDRESS)
      ? 1
        : (this.value >> LOG_BYTES_IN_ADDRESS);
  }

  /**
   * Trace rate must be positive.
   */
  protected void validate() {
    failIf(value <= 0, "Can not have a negative trace rate");
  }
}
