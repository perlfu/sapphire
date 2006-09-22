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

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.utility.deque.*;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This abstract class implments the core functionality for a transitive
 * closure over the heap graph.
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public abstract class GenMatureTraceLocal extends TraceLocal
  implements Uninterruptible {

  /****************************************************************************
   * 
   * Instance fields.
   */
  private final AddressDeque remset;
  private final AddressPairDeque arrayRemset;

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   */
  public GenMatureTraceLocal(Trace trace, GenCollector plan) {
    super(trace);
    this.remset = plan.remset;
    this.arrayRemset = plan.arrayRemset;
  }

  /****************************************************************************
   * 
   * Object processing and tracing
   */

  /**
   * Is the specified object live?
   * 
   * @param object The object.
   * @return True if the object is live.
   */
  public boolean isLive(ObjectReference object) throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!object.isNull());
    if (object.toAddress().GE(Gen.NURSERY_START)) {
      if (object.toAddress().LT(Gen.NURSERY_END))
      return Gen.nurserySpace.isLive(object);
      else
        return Gen.ploSpace.isLive(object);
    }
    return super.isLive(object);
  }

  /**
   * Return true if this object is guaranteed not to move during this
   * collection (i.e. this object is defintely not an unforwarded
   * object).
   * 
   * @param object
   * @return True if this object is guaranteed not to move during this
   *         collection.
   */
  public boolean willNotMove(ObjectReference object) {
    if (object.toAddress().GE(Gen.NURSERY_START))
      return object.toAddress().GE(Gen.NURSERY_END);
    return super.willNotMove(object);
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
  public ObjectReference traceObject(ObjectReference object)
      throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!object.isNull());
    if (object.toAddress().GE(Gen.NURSERY_START)) {
      if (object.toAddress().LT(Gen.NURSERY_END))
      return Gen.nurserySpace.traceObject(this, object);
      else
        return Gen.ploSpace.traceObject(this, object);
    }
    return super.traceObject(object);
  }

  /**
   * Where do we send copied objects ?
   * 
   * @return The allocator for copied objects
   */
  public final int getAllocator() throws InlinePragma {
    return Gen.ALLOC_MATURE_MAJORGC;
  }

  /**
   * Process any remembered set entries.
   */
  protected void processRememberedSets() {
    logMessage(5, "clearing remset");
    while (!remset.isEmpty()) {
      remset.pop();
    }
    logMessage(5, "clearing array remset");
    while (!arrayRemset.isEmpty()) {
      arrayRemset.pop1();
      arrayRemset.pop2();
    }
  }

}
