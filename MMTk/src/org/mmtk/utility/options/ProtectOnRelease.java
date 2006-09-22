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
 * Should memory be protected on release?
 * 
 * $Id$
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class ProtectOnRelease extends BooleanOption {
  /**
   * Create the option.
   */
  public ProtectOnRelease() {
    super("Protect On Release",
          "Should memory be protected on release?",
          false);
  }
}
