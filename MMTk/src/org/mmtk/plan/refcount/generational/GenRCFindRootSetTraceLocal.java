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
package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.policy.Space;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the thread-local core functionality for a transitive
 * closure over the heap graph.
 */
@Uninterruptible
public final class GenRCFindRootSetTraceLocal extends TraceLocal {

  private final ObjectReferenceDeque rootBuffer;

  /**
   * Constructor
   */
  public GenRCFindRootSetTraceLocal(Trace trace, ObjectReferenceDeque rootBuffer) {
    super(trace);
    this.rootBuffer = rootBuffer;
  }

  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  /**
   * Is the specified object reachable?
   *
   * @param object The object.
   * @return <code>true</code> if the object is reachable.
   */
  public boolean isLive(ObjectReference object) {
    return GenRC.isRCObject(object) && RCHeader.isLiveRC(object) ||
          (!Space.isInSpace(GenRC.NURSERY, object) && super.isLive(object));
  }

  /**
   * When we trace a non-root object we do nothing.
   *
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    return traceObject(object, false);
  }

  /**
   * When we trace a root object we remember it.
   *
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Inline
  public ObjectReference traceObject(ObjectReference object, boolean root) {
    if (object.isNull()) return object;

    if (Space.isInSpace(GenRC.NURSERY, object)) {
      object = GenRC.nurserySpace.traceObject(this, object, GenRC.ALLOC_RC);
    } else if (!GenRC.isRCObject(object)) {
      return object;
    }

    if (root) {
      rootBuffer.push(object);
    } else {
      RCHeader.incRC(object);
    }

    return object;
  }

  /**
   * Ensure that the referenced object will not move from this point through
   * to the end of the collection. This can involve forwarding the object
   * if necessary.
   *
   * <i>Non-copying collectors do nothing, copying collectors must
   * override this method in each of their trace classes.</i>
   *
   * @param object The object that must not move during the collection.
   * @return True If the object will not move during collection
   */
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!object.isNull());
    return !(Space.isInSpace(GenRC.NURSERY, object));
  }

  /**
   * Ensure that this object will not move for the rest of the GC.
   *
   * @param object The object that must not move
   * @return The new object, guaranteed stable for the rest of the GC.
   */
  public ObjectReference precopyObject(ObjectReference object) {
    if (Space.isInSpace(GenRC.NURSERY, object)) {
      return GenRC.nurserySpace.traceObject(this, object, GenRC.ALLOC_RC);
    }
    return object;
  }

}
