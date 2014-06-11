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
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

@Uninterruptible
public class OTFSapphireAllocTraceLocal extends TraceLocal {
  private static final boolean SHOWTRACE = OTFSapphire.SHOWTRACES;

  /**
   * Constructor
   */
  public OTFSapphireAllocTraceLocal(Trace trace, boolean specialized) {
    super(specialized ? OTFSapphire.FIRST_SCAN_SS : -1, trace);
  }

  /**
   * Constructor
   */
  public OTFSapphireAllocTraceLocal(Trace trace) {
    this(trace, false); // LPJH: disable specialized scanning
  }

  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  @Override
  public void prepare() {
    super.prepare();
  }

  @Override
  public void release() {
    super.release();
  }

  /**
   * Should reference values be overwritten as the heap is traced?
   */
  protected boolean overwriteReferenceDuringTrace() {
    return false;
  }

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param object The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (OTFSapphire.inFromSpace(object)) {
      return OTFSapphire.fromSpace().isLive(object);
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!OTFSapphire.inToSpace(object));
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
   * This method traces an object with knowledge of the fact that object
   * is a root or not. In simple collectors the fact it is a root is not
   * important so this is the default implementation given here.
   *
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Inline
  @Override
  public ObjectReference traceObject(ObjectReference object, boolean root) {
    if (SHOWTRACE) {
      if (!object.isNull()) {
        Log.write("ALLOC_TR ");
        Log.write(object);
        Log.write(root ? " root " : " non-root ");
        if (Space.isMappedObject(object))
          Log.write(Space.getSpaceForObject(object).getName());
        else
          Log.write("out-of-heap");
        Log.writeln();
      }
    }
    return traceObject(object);
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
      ObjectReference obj = traceObject(object, OTFSapphire.ALLOC_REPLICATING);
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(OTFSapphire.inFromSpace(obj)); // 1st trace should return from-space obj
        VM.assertions._assert(isLive(object)); // object should be considered live after tracing
      }
      return obj;
    }
    if (VM.VERIFY_ASSERTIONS) {
      if (OTFSapphire.inToSpace(object)) {
        Log.write("Failing object => ");
        Log.writeln(object);
        VM.objectModel.dumpObject(object);
        VM.assertions.fail("Should not have a toSpace reference during first trace");
      }
    }
    return super.traceObject(object);
  }

  public void completeTrace() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(rootLocations.isEmpty());
//    logMessage(4, "Processing GC in parallel with Sapphire Hack");
//    if (!rootLocations.isEmpty()) {
//      processRoots();
//    }
    logMessage(5, "processing gray objects with Sapphire Hack");
    assertMutatorRemsetsFlushed();
    do {
      while (!values.isEmpty()) {
        ObjectReference v = values.pop();
        // UGAWA it is not necessary if we use a separate write buffer for the write barrier
        // this work here is necessary because we don't want the insertion barrier to do any work (allocation)
        // therefore the insertion barrier can't mark objects as live so we must do that before we scan the object
// NOW WE HAVE A SEPARATE WRITE BUFFER
        //        traceObject(v); // the object we just popped is not yet live, mark it as live
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isLive(v));
        scanObject(v);  // now that we have made the object live, scan it's children
      }
      processRememberedSets();
    } while (!values.isEmpty());
    assertMutatorRemsetsFlushed();
  }

  /**
   * Process GC work until either complete or workLimit
   * units of work are completed.
   *
   * @param workLimit The maximum units of work to perform.
   * @return True if all work was completed within workLimit.
   */
  @Inline
  public boolean incrementalTrace(int workLimit) {
    VM.assertions.fail("unsupported");
    return values.isEmpty();
  }

  /**
   * Will this object move from this point on, during the current trace ?
   *
   * @param object The object to query.
   * @return True if the object will not move.
   */
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    // if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!Sapphire.inToSpace(object));  // does not work, called with toSpace ref from postCopy
    return !OTFSapphire.inFromSpace(object);
  }
  
  /**
   * Trace an object under a copying collection policy.
   *
   * We use a tri-state algorithm to deal with races to forward
   * the object.  The tracer must wait if the object is concurrently
   * being forwarded by another thread.
   *
   * If the object is already forwarded, the copy is returned.
   * Otherwise, the object is forwarded and the copy is returned.
   *
   * @param trace The trace being conducted.
   * @param object The object to be forwarded.
   * @param allocator The allocator to use when copying.
   * @return The forwarded object.
   */
  @Inline
  private ObjectReference traceObject(ObjectReference object, int allocator) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(OTFSapphire.inFromSpace(object));

    // Check if already has a ForwardingPointer
    if (ReplicatingSpace.isForwarded(object)) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isLive(object));
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!ReplicatingSpace.getReplicaPointer(object).isNull());
      return object;
    }


    // attempt to CAS busy into object, if we win alloc space for replica
    ReplicatingSpace.atomicMarkBusy(object, null);
    if (!ReplicatingSpace.isForwarded(object)) {
      // we are designated thread to alloc space
      ObjectReference toObject;
      toObject = VM.objectModel.createBlankReplica(object, allocator);
      ReplicatingSpace.setReplicaPointer(toObject, object, null, ReplicatingSpace.CHECK_BACKWARD, ReplicatingSpace.FOR_EXISTING_OBJECT);
      ReplicatingSpace.setReplicaPointer(object, toObject, null, ReplicatingSpace.CHECK_FORWARD, ReplicatingSpace.FOR_EXISTING_OBJECT);
      ReplicatingSpace.setForwarded(object, null, ReplicatingSpace.FOR_EXISTING_OBJECT);
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!toObject.isNull());
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(OTFSapphire.inToSpace(toObject));
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isLive(object));
      processNode(object);
    } else {
      // someone else has copied the object behind our back
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!ReplicatingSpace.getReplicaPointer(object).isNull());
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!ReplicatingSpace.isBusy(ReplicatingSpace.getReplicaPointer(object)));
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(OTFSapphire.inToSpace(ReplicatingSpace.getReplicaPointer(object)));
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(ReplicatingSpace.getReplicaPointer(ReplicatingSpace.getReplicaPointer(object)) == object);
      ReplicatingSpace.clearBusy(object, null);
    }
    return object;
  }
}
