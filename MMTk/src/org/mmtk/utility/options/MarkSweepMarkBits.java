/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Number of bits to use for the header cycle of mark sweep spaces.
 * 
 * $Id$
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class MarkSweepMarkBits extends IntOption {
  /**
   * Create the option.
   */
  public MarkSweepMarkBits() {
    super("Mark Sweep Mark Bits",
          "Number of bits to use for the header cycle of mark sweep spaces",
          2);
  }

  /**
   * Ensure the port is valid.
   */
  protected void validate() {
    failIf(this.value <= 0, "Must provide at least one bit");
    failIf(this.value > 4 , "Only 4 bits are reserved in MarkSweepSpace");
  }
}
