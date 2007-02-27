/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.alloc;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Callbacks from BumpPointer during a linear scan are dispatched through
 * a subclass of this object.
 * 
 * @author Daniel Frampton
 */
@Uninterruptible
public abstract class LinearScan {
  /**
   * Scan an object.
   * 
   * @param object The object to scan
   */
  public abstract void scan(ObjectReference object);
}
