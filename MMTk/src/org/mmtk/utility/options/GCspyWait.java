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

/**
 * Should the VM wait for the visualiser to connect?
 * 
 * $Id$
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class GCspyWait extends BooleanOption {
  /**
   * Create the option
   */
  public GCspyWait() {
    super("GCSpy Wait",
          "Should the VM wait for the visualiser to connect?",
        false);
  }
}
