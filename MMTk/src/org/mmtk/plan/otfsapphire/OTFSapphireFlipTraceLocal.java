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
package org.mmtk.plan.otfsapphire;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

@Uninterruptible
public class OTFSapphireFlipTraceLocal extends TraceLocal {
  public static final boolean SHOWTRACE = false;

  /**
   * Constructor
   */
  public OTFSapphireFlipTraceLocal(Trace trace, boolean specialized) {
    super(specialized ? OTFSapphire.SECOND_SCAN_SS : -1, trace);
  }

  /**
   * Constructor
   */
  public OTFSapphireFlipTraceLocal(Trace trace) {
    this(trace, false); // LPJH: disable specialized scanning
  }

  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param object The object in question
   * @return True if <code>obj</code> is a live object.
   */
  @Override
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (OTFSapphire.inFromSpace(object)) {
      return OTFSapphire.fromSpace().isLive(object);
    } else if (OTFSapphire.inToSpace(object)) {
      return true;
    } else
      return super.isLive(object);
  }

  /**
   * UGAWA
   * On the fly collector needs to deference root locations immediately.  Otherwise
   * the mutator works and stack map changes.
   * Putting references to the rootLocations queue and processing it before restarting
   * the mutator is still not sufficient.  Because, if the local queue overflows,
   * the addresses of this mutator may be passed to other collector thread and this
   * collector thread may restart the mutator before the other collector thread
   * processes the addresses.
   */
  @Override
  @Inline
  public void reportDelayedRootEdge(Address slot) {
    processRootEdge(slot, true);
  }

  @Override
  public void processRoots() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(rootLocations.isEmpty());
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
    if (object.isNull()) return object;
    if (OTFSapphire.inFromSpace(object)) {
      if (VM.VERIFY_ASSERTIONS) if (!ReplicatingSpace.isForwarded(object)) VM.objectModel.dumpObject(object);
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(ReplicatingSpace.isForwarded(object));
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!ReplicatingSpace.getReplicaPointer(object).isNull());
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(OTFSapphire.inToSpace(ReplicatingSpace.getReplicaPointer(object)));
      return ReplicatingSpace.getReplicaPointer(object);
    }
    if (OTFSapphire.inToSpace(object)) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!ReplicatingSpace.isForwarded(object));
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!ReplicatingSpace.getReplicaPointer(object).isNull());
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(OTFSapphire.inFromSpace(ReplicatingSpace.getReplicaPointer(object)));
      return object;
    }
    return super.traceObject(object);
  }

  /**
   * Will this object move from this point on, during the current trace ?
   *
   * @param object The object to query.
   * @return True if the object will not move.
   */
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    return true;
    //    return !STWSapphire.inFromSpace(object);
  }
}
