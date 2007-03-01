/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.generational;

import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;
import org.mmtk.utility.deque.*;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implments the core functionality for a transitive
 * closure over the heap graph.
 * 
 *
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 */
@Uninterruptible public final class GenNurseryTraceLocal extends TraceLocal {

  /****************************************************************************
   * 
   * Instance fields.
   */
  private final AddressDeque remset;
  private final AddressPairDeque arrayRemset;


  /**
   * Constructor
   */
  public GenNurseryTraceLocal(Trace trace, GenCollector plan) {
    super(trace);
    this.remset = plan.remset;
    this.arrayRemset = plan.arrayRemset;
  }

  /****************************************************************************
   * 
   * Externally visible Object processing and tracing
   */

  /**
   * Is the specified object live?
   * 
   * @param object The object.
   * @return True if the object is live.
   */
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (object.toAddress().GE(Gen.NURSERY_START)) {
      if (object.toAddress().LT(Gen.NURSERY_END))
        return Gen.nurserySpace.isLive(object);
      else
        return Gen.ploSpace.isLive(object);
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(super.isLive(object));
    return true;
  }

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
    if (!object.isNull() && object.toAddress().GE(Gen.NURSERY_START)) {
      if (object.toAddress().LT(Gen.NURSERY_END))
        return Gen.nurserySpace.traceObject(this, object);
      else
        return Gen.ploSpace.traceObject(this, object);
    }
    return object;
  }

  /**
   * Process any remembered set entries.
   */
  @Inline
  protected void processRememberedSets() { 
    logMessage(5, "processing remset");
    while (!remset.isEmpty()) {
      Address loc = remset.pop();
      traceObjectLocation(loc, false);
    }
    logMessage(5, "processing array remset");
    arrayRemset.flushLocal();
    while (!arrayRemset.isEmpty()) {
      Address start = arrayRemset.pop1();
      Address guard = arrayRemset.pop2();
      while (start.LT(guard)) {
        traceObjectLocation(start, false);
        start = start.plus(BYTES_IN_ADDRESS);
      }
    }
  }

  /**
   * @return The allocator to use when copying objects during this trace.
   */
  @Inline
  public int getAllocator() { 
    return Gen.ALLOC_MATURE_MINORGC;
  }

  /**
   * Will the object move from now on during the collection.
   * 
   * @param object The object to query.
   * @return True if the object is guaranteed not to move.
   */
  public boolean willNotMove(ObjectReference object) {
    if (object.isNull()) return false;
    return object.toAddress().LT(Gen.NURSERY_START) ||
    	Space.isInSpace(Plan.PLOS, object);
  }

}
