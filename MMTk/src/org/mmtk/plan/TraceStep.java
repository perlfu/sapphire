/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan;

import org.mmtk.utility.Constants;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class is the fundamental mechanism for performing a
 * transitive closure over an object graph.<p>
 * 
 * @see org.mmtk.plan.TraceLocal
 * 
 * $Id: TraceLocal.java,v 1.7 2006/06/21 07:38:14 steveb-oss Exp $
 * 
 * @author Daniel Frampton
 * @version $Revision: 1.7 $
 * @date $Date: 2006/06/21 07:38:14 $
 */
public abstract class TraceStep implements Constants, Uninterruptible {

  /**
   * Trace a reference during GC.
   * 
   * @param objLoc The location containing the object reference to be
   * traced.
   */
  public abstract void traceObjectLocation(Address objLoc);
}
