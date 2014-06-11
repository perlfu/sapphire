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

import org.mmtk.plan.*;
import org.mmtk.plan.onthefly.OnTheFly;
import org.mmtk.plan.onthefly.OnTheFlyCollector;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.RawPageSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.deque.AddressPairDeque;
import org.mmtk.utility.options.ConcurrentCopyMethod;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

@Uninterruptible
public class OTFSapphireCollector extends OnTheFlyCollector {
  static class RemsetTraceLocal extends TraceLocal {
    public RemsetTraceLocal(Trace trace) {
      super(trace);
    }
    @Uninterruptible
    public void makeReplicaAndTossTo(TraceLocal dst) {
      while (!values.isEmpty()) {
        ObjectReference object = values.pop();
        dst.traceObject(object);
      }
    }
  }

  /****************************************************************************
   * Instance fields
   */

  protected final TraceLocal allocTraceLocal;
  protected final TraceLocal flipTraceLocal;
  protected final TraceLocal verifyTraceLocal;
  protected final CopyLocal ss = new CopyLocal();

  protected final RemsetTraceLocal remsetTraceLocal;
  protected final AddressPairDeque patchQueue;

  protected final OTFSapphireCopyScan copyScan;
  protected final OTFSapphireCopyScan copyScanSTW;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public OTFSapphireCollector() {
    this(OTFSapphire.MERGE_REPLICATE_PHASE ? 
         new OTFSapphireReplicateTraceLocal(global().allocTrace, OTFSapphire.metaDataSpace) :
         new OTFSapphireAllocTraceLocal(global().allocTrace),
         new OTFSapphireFlipTraceLocal(global().flipRootTrace),
         OTFSapphire.VERIFY_FLIP ? new OTFSapphireVerifyTraceLocal(global().verifyTrace) : null,
         OTFSapphire.metaDataSpace);
  }

  /**
   * Constructor
   * @param tr The trace to use
   */
  protected OTFSapphireCollector(TraceLocal alloc, TraceLocal flip, TraceLocal verifyFlip, RawPageSpace rps) {
    allocTraceLocal = alloc;
    flipTraceLocal = flip;
    verifyTraceLocal = verifyFlip;
    remsetTraceLocal = new RemsetTraceLocal(global().fromSpaceRemset);
    if (OTFSapphire.MERGE_REPLICATE_PHASE)
      patchQueue = new AddressPairDeque(global().patchQueue);
    else
      patchQueue = null;

    /* concurrent copy method */
    switch (Options.concurrentCopyMethod.method()) {
      case ConcurrentCopyMethod.MHTM:   
        copyScan = new OTFSapphireCopyScan.CopyScanMHTM(global().flipRootTrace, Options.concurrentCopyTransactionSize.getValue());
        break;
      case ConcurrentCopyMethod.STM: copyScan = new OTFSapphireCopyScan.CopyScanSTM(global().flipRootTrace, rps); break;
      case ConcurrentCopyMethod.MSTM: copyScan = new OTFSapphireCopyScan.CopyScanMSTM(global().flipRootTrace, rps); break;
      case ConcurrentCopyMethod.STMSEQ: copyScan = new OTFSapphireCopyScan.CopyScanSTMseq(global().flipRootTrace, rps); break;
      case ConcurrentCopyMethod.STMSEQ2: copyScan = new OTFSapphireCopyScan.CopyScanSTMseq2(global().flipRootTrace, rps); break;
      case ConcurrentCopyMethod.STMSEQ2P: copyScan = new OTFSapphireCopyScan.CopyScanSTMseq2P(global().flipRootTrace, rps); break;
      case ConcurrentCopyMethod.STMSEQ2N: copyScan = new OTFSapphireCopyScan.CopyScanSTMseq2N(global().flipRootTrace, rps); break;
      default:
        copyScan = global().copyScan;
        break;
    }
    if (Options.concurrentCopyMethod.always())
      copyScanSTW = copyScan;
    else
      copyScanSTW = global().copyScanSTW;
  }

  /****************************************************************************
   *
   * Collection-time allocation
   */

  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   *
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @return The address of the first byte of the allocated region
   */
  @Inline
  public Address allocCopy(ObjectReference original, int bytes, int align, int offset, int allocator) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(allocator == OTFSapphire.ALLOC_REPLICATING);
    return ss.alloc(bytes, align, offset);
  }

  /**
   * Perform any post-copy actions.
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  @Inline
  public void postCopy(ObjectReference from, ObjectReference to, ObjectReference typeRef,
      int bytes, int allocator) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(allocator == OTFSapphire.ALLOC_REPLICATING);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(ReplicatingSpace.isBusy(from));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!ReplicatingSpace.isForwarded(from));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(getCurrentTrace().willNotMoveInCurrentCollection(to));
    ReplicatingSpace.initializeHeader(to, false);
  } 
  
  /**
   * Run-time check of the allocator to use for a given copy allocation
   *
   * At the moment this method assumes that allocators will use the simple
   * (worst) method of aligning to determine if the object is a large object
   * to ensure that no objects are larger than other allocators can handle.
   *
   * @param from The object that is being copied.
   * @param bytes The number of bytes to be allocated.
   * @param align The requested alignment.
   * @param allocator The allocator statically assigned to this allocation.
   * @return The allocator dyncamically assigned to this allocation.
   */
  @Inline
  public int copyCheckAllocator(ObjectReference from, int bytes, int align, int allocator) {
    // Sapphire copies only replicating space objects.
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(allocator == OTFSapphire.ALLOC_REPLICATING);

    /*
     *  We do not take it into account that the host VM require a larger region for the
     * to-space copy than the original copy for some reasons and VM attempts to copy it to LOS.
     */
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Allocator.getMaximumAlignedSize(bytes, align) <= Plan.MAX_NON_LOS_COPY_BYTES);
    
    return allocator;
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-collector collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {
    /**
     * Mark & Allocate
     */
    if (phaseId == Simple.PREPARE) {
      ss.rebind(OTFSapphire.toSpace());
      allocTraceLocal.prepare();
      copyScan.prepare();
      if (copyScanSTW != copyScan) copyScanSTW.prepare();
      super.collectionPhase(Simple.PREPARE, primary);
      return;
    }

    // ROOTS: nothing special

    if (phaseId == OTFSapphire.DEREFERENCE_DELAYED_ROOTS) {
      // This phase is used in the flip phases as well
      getCurrentTrace().processRoots();
      return;
    }

    if (phaseId == OTFSapphire.CLOSURE) {
      remsetTraceLocal.makeReplicaAndTossTo(allocTraceLocal);
      /* no synchronization is needed */
      allocTraceLocal.completeTrace();
      return;
    }

    if (phaseId == OnTheFly.OTF_PROCESS_WEAK_REFS) {
      /* Process soft refs as if they are weak refs. */
      super.collectionPhase(phaseId, primary);
      return;
    }

    if (phaseId == Simple.RELEASE) {
      if (OTFSapphire.MERGE_REPLICATE_PHASE) {
        int count = 0;
        while (!patchQueue.isEmpty()) {
          Address fromSlot = patchQueue.pop1();
          Address toSlot = patchQueue.pop2();
          if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(OTFSapphire.inFromSpace(fromSlot));
          if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(OTFSapphire.inToSpace(toSlot));
          while (true) {
            ObjectReference currentToSpaceVal = toSlot.prepareObjectReference();
            ObjectReference oldFromSpaceVal = fromSlot.loadObjectReference();
            if (!OTFSapphire.inFromSpace(oldFromSpaceVal))
              break;
            ObjectReference fromSpaceEquivalent = ReplicatingSpace.getReplicaPointer(oldFromSpaceVal);
            if (currentToSpaceVal.toAddress().EQ(fromSpaceEquivalent.toAddress()))
              break;
            if (!toSlot.attempt(currentToSpaceVal, fromSpaceEquivalent))
              break;
          }
          count++;
        }
        if (OTFSapphire.REPORT_FIX_POINTERS) {
          Log.write("Pointer fix: ");
          Log.writeln(count);
        }
      }
      allocTraceLocal.release();
      if (OTFSapphire.MERGE_REPLICATE_PHASE) {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(patchQueue.isEmpty());
        patchQueue.reset();
      }
      OTFSapphire.tackOnLock.acquire();
      OTFSapphire.toSpaceSweeper.tackOn(ss);
      OTFSapphire.tackOnLock.release();
      /* no more allocations are made by this thread */
      super.collectionPhase(Simple.RELEASE, primary);
      return;
    }
    
    /**
     * Copy
     */
    if (phaseId == OTFSapphire.COPY) {
      if (primary) {
        if (Options.verbose.getValue() >= 2) {
          Log.write("Concurrent copy method: ");
          Log.writeln((global().mutatorsAreEnabled() && Plan.controlCollectorContext.isOnTheFlyCollection()) ? copyScan.getName() : copyScanSTW.getName());
        }
      }
      if (global().mutatorsAreEnabled() && Plan.controlCollectorContext.isOnTheFlyCollection())
        OTFSapphire.toSpaceSweeper.parallelLinearScan(copyScan);
      else
        OTFSapphire.toSpaceSweeper.parallelLinearScan(copyScanSTW);
      return;
    }

    /**
     * Flip
     */
    if (phaseId == OTFSapphire.PREPARE_FLIP) {
      flipTraceLocal.prepare();
      super.collectionPhase(Simple.PREPARE, primary);
      return;
    }

    // STACK_ROOTS: nothing special
    // ROOTS: nothing special

    // DEREFERENCE_DELAYED_ROOTS: defined in the alloc phases.

    if (phaseId == OTFSapphire.RELEASE_FLIP) {
      if (OTFSapphire.VERIFY_FLIP && primary && !Options.noFinalizer.getValue()) {
        VM.finalizableProcessor.forward(verifyTraceLocal, false);
      }
      flipTraceLocal.release();
      copyScan.release();
      if (copyScanSTW != copyScan) copyScanSTW.release();
      super.collectionPhase(Simple.RELEASE, primary);
      return;
    }

    // COMPLETE: nothing special

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */

  /**
   * Return true if the given reference is to an object that is within
   * one of the semi-spaces.
   *
   * @param object The object in question
   * @return True if the given reference is to an object that is within
   * one of the semi-spaces.
   */
  public static boolean isSemiSpaceObject(ObjectReference object) {
    return Space.isInSpace(OTFSapphire.SS0, object) || Space.isInSpace(OTFSapphire.SS1, object);
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>SS</code> instance. */
  @Inline
  private static OTFSapphire global() {
    return (OTFSapphire) VM.activePlan.global();
  }

  /** @return the current trace object. */
  public TraceLocal getCurrentTrace() {
    if (OTFSapphire.currentTrace == OTFSapphire.ALLOC_TRACE)
      return allocTraceLocal;
    else if (OTFSapphire.currentTrace == OTFSapphire.FLIP_ROOT_TRACE)
      return flipTraceLocal;
    else {
      if (VM.VERIFY_ASSERTIONS) VM.assertions.fail("Unknown currentTrace value");
      return null;
    }
  }

  protected boolean concurrentTraceComplete() {
    return (!global().allocTrace.hasWork()) && (!global().flipRootTrace.hasWork());
  }
}
