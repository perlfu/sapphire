/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Trigger cycle detection if the space available falls below this threshold.
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class CycleTriggerThreshold extends PagesOption {
  /**
   * Create the option.
   */
  public CycleTriggerThreshold() {
    super("Cycle Trigger Threshold",
          "Trigger cycle detection if the space available falls below this threshold",
          512);
  }
}
