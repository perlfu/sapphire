/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 *
 * (C) Copyright Richard Jones, 2005-6
 * Computing Laboratory, University of Kent at Canterbury
 */
package org.mmtk.plan.semispace.gcspy;

import org.mmtk.plan.Trace;
import org.mmtk.plan.semispace.SSTraceLocal;
import org.mmtk.policy.Space;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implments the core functionality for a transitive
 * closure over the heap graph.
 * 
 *
 * @author Steve Blackburn
 * @author Perry Cheng
 * @author Robin Garner
 * @author Daniel Frampton
 * @author <a href="http://www.cs.kent.ac.uk/~rej">Richard Jones</a>
 * 
 */
@Uninterruptible public class SSGCspyTraceLocal extends SSTraceLocal {
  /**
   * Constructor
   */
  public SSGCspyTraceLocal(Trace trace) {
    super(trace);
  }

  /****************************************************************************
   * 
   * Externally visible Object processing and tracing
   */

  /**
   * This method is the core method during the trace of the object graph.
   * The role of this method is to:
   * 
   * 1. Ensure the traced object is not collected.
   * 2. If this is the first visit to the object enqueue it to be scanned.
   * 3. Return the forwarded reference to the object.
   * 
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Inline
  public ObjectReference traceObject(ObjectReference object) { 
    if (object.isNull()) return object;
    if (Space.isInSpace(SSGCspy.GCSPY, object))
      return SSGCspy.gcspySpace.traceObject(this, object);
    return super.traceObject(object);
  }

  /**
   * Will this object move from this point on, during the current trace ?
   * 
   * @param object The object to query.
   * @return True if the object will not move.
   */
  public boolean willNotMove(ObjectReference object) {
    if (Space.isInSpace(SSGCspy.GCSPY, object))
      return true; 
    return super.willNotMove(object);
  }
}
