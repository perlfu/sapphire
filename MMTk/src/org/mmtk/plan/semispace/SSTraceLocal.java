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
package org.mmtk.plan.semispace;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the core functionality for a transitive
 * closure over the heap graph.
 */
@Uninterruptible
public class SSTraceLocal extends TraceLocal {
  /**
   * Constructor
   */
  public SSTraceLocal(Trace trace, boolean specialized) {
    super(specialized ? SS.SCAN_SS : -1, trace);
  }

  /**
   * Constructor
   */
  public SSTraceLocal(Trace trace) {
    this(trace, true);
  }

  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(SS.SS0, object))
      return SS.hi ? SS.copySpace0.isLive(object) : true;
    if (Space.isInSpace(SS.SS1, object))
      return SS.hi ? true : SS.copySpace1.isLive(object);
    return super.isLive(object);
  }


  @Override
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    if (object.isNull()) return object;
    if (Space.isInSpace(SS.SS0, object))
      return SS.copySpace0.traceObject(this, object, SS.ALLOC_SS);
    if (Space.isInSpace(SS.SS1, object))
      return SS.copySpace1.traceObject(this, object, SS.ALLOC_SS);
    return super.traceObject(object);
  }

  /**
   * Will this object move from this point on, during the current trace ?
   *
   * @param object The object to query.
   * @return True if the object will not move.
   */
  @Override
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    return (SS.hi && !Space.isInSpace(SS.SS0, object)) ||
           (!SS.hi && !Space.isInSpace(SS.SS1, object));
  }
}
