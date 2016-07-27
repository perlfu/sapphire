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
import org.mmtk.plan.onthefly.OnTheFlyMutator;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.deque.AddressPairDeque;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

@Uninterruptible
public class OTFSapphireMutator extends OnTheFlyMutator {
  public static final boolean DEBUG_EQ_BARRIER = false;

  /****************************************************************************
   * Instance fields
   */
  protected final CopyLocal fromSpaceLocal;
  protected final CopyLocal toSpaceLocal;

  private final TraceWriteBuffer remset;
  private final TraceWriteBuffer fromSpaceRemsetLocal;
  private final AddressPairDeque patchQueue;

  private int barrierFlags = 0;
  static private int newThread_barrierFlags = 0;
  static protected final int BARRIER_FLAG_INSERTION      = 0x00000001;
  static protected final int BARRIER_FLAG_DELETION       = 0x00000002;
  static protected final int BARRIER_FLAG_ALLOC_BLACK    = 0x00000004;
  static protected final int BARRIER_FLAG_SYNC_DBL_UPD   = 0x00000100;
  static protected final int BARRIER_FLAG_DOUBLE_UPDATE  = 0x00000200;
  static protected final int BARRIER_FLAG_CONDITIONAL_POINTER_UPDATE = 0x00000800;
  static protected final int BARRIER_FLAG_POINTER_UPDATE = 0x00001000;
  static protected final int BARRIER_FLAG_EQ             = 0x00002000;
  static protected final int BARRIER_FLAG_ALLOC_TO_SPACE = 0x00004000;
  static protected final int BARRIER_FLAG_STATUS_WORD    = 0x00100000;
 
  @Inline
  protected final boolean barrierEnable_Insertion() {
    return (barrierFlags & BARRIER_FLAG_INSERTION) != 0;
  }
  @Inline
  protected final boolean barrierEnable_Deletion() {
    return (barrierFlags & BARRIER_FLAG_DELETION) != 0;
  }
  @Inline
  protected final boolean barrierEnable_AllocBlack() {
    return (barrierFlags & BARRIER_FLAG_ALLOC_BLACK) != 0;
  }
  @Inline
  protected final boolean barrierEnable_DoubleUpdate() {
    return (barrierFlags & BARRIER_FLAG_DOUBLE_UPDATE) != 0;
  }
  @Inline
  protected final boolean barrierEnable_SyncDoubleUpdate() {
    return (barrierFlags & BARRIER_FLAG_SYNC_DBL_UPD) != 0;
  }
  @Inline
  protected final boolean barrierEnable_ConditionalPointerUpdate() {
    return (barrierFlags & BARRIER_FLAG_CONDITIONAL_POINTER_UPDATE) != 0;
  }
  @Inline
  protected final boolean barrierEnable_PointerUpdate() {
    return (barrierFlags & BARRIER_FLAG_POINTER_UPDATE) != 0;
  }
  @Inline
  protected final boolean barrierEnable_EQ() {
    return (barrierFlags & BARRIER_FLAG_EQ) != 0;
  }
  @Inline
  protected final boolean barrierEnable_AllocToSpace() {
    return (barrierFlags & BARRIER_FLAG_ALLOC_TO_SPACE) != 0;
  }
  @Inline
  protected final boolean barrierEnable_StatusWord() {
    return (barrierFlags & BARRIER_FLAG_STATUS_WORD) != 0;
  }
  @Inline
  protected final boolean barrierEnable() {
    return barrierFlags != 0;
  }
  @Inline
  protected static final void newThreadBarrierSet(int on, int off) {
    newThread_barrierFlags = (newThread_barrierFlags | on) & ~off;
  }
  @Inline
  protected static final boolean newThreadBarrierEnable() {
    return newThread_barrierFlags != 0;
  }
  @Inline
  final void setupBarrier() {
    barrierFlags = newThread_barrierFlags;
  }

  static protected boolean newThread_needPrepareRelease          = false;
  static protected boolean fromSpaceLow                          = true;
  
  protected boolean prepared;
  protected int fromSpaceDescriptor;
  protected int toSpaceDescriptor;
  protected ReplicatingSpace fromSpace;
  private Address fromSpaceReplica = Address.zero();

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public OTFSapphireMutator() {
    fromSpaceLocal = new CopyLocal();
    toSpaceLocal = new CopyLocal();
    remset = new TraceWriteBuffer(global().allocTrace);
    fromSpaceRemsetLocal = new TraceWriteBuffer(global().fromSpaceRemset);
    if (OTFSapphire.MERGE_REPLICATE_PHASE)
      patchQueue = new AddressPairDeque(global().patchQueue);
    else
      patchQueue = null;
  }

  /** @return The active global plan as a <code>SS</code> instance. */
  @Inline
  private static OTFSapphire global() {
    return (OTFSapphire) VM.activePlan.global();
  }

  /**
   * Called before the MutatorContext is used, but after the context has been
   * fully registered and is visible to collection.
   */
  public void initMutator(int id) {
    super.initMutator(id);
    if (newThread_needPrepareRelease) {
      super.collectionPhase(Simple.PREPARE, false);
      prepared = true;
    }
    immortal.init();
    los.initThreadForOTFCollection();
    nonmove.initThreadForOTFCollection();
    if (OTFSapphire.USE_CODE_SPACE) {
      smcode.initThreadForOTFCollection();
      lgcode.initThreadForOTFCollection();
    }
    setupSpace();
    barrierFlags = newThread_barrierFlags;
  }
  
  /**
   * The mutator is about to be cleaned up, make sure all local data is returned.
   */
  public void deinitMutator() { 
    if (VM.VERIFY_ASSERTIONS) if (OTFSapphire.assert_TO_SPLACE_LOCAL_IS_EMPTY) VM.assertions._assert(toSpaceLocal.getCursor().isZero());
    OTFSapphire.tackOnLock.acquire();
    if (OTFSapphire.fromSpaceSweeper.getSpace().getDescriptor() == fromSpaceDescriptor) {
      OTFSapphire.fromSpaceSweeper.tackOn(fromSpaceLocal); // thread is dying, ensure everything it allocated is still scanable
      OTFSapphire.toSpaceSweeper.tackOn(toSpaceLocal); // thread is dying, ensure everything it allocated is still scanable
    } else {
      /* this can be the case in the time window between mutator's RELEASE_FLIP and
       * global RELEASE_FLIP phases.  In mutator's phase, mutators flip toSpaceLocal/fromSpaceLocal
       * whilst the collector flips from/toSpaceSweeper.
       * Remark that from/toSpace() (global spaces) are swapped in RELEASE_FLIP_NEWTHREAD phase
       * which followed by mutator's RELEASE_FLIP phase.  
       * Note that, owe to tackOnLock, from/toSpaceSweeper are never swapped in this critical section.
       */
      OTFSapphire.fromSpaceSweeper.tackOn(toSpaceLocal); // thread is dying, ensure everything it allocated is still scanable
      OTFSapphire.toSpaceSweeper.tackOn(fromSpaceLocal); // thread is dying, ensure everything it allocated is still scanable
    }
    OTFSapphire.immortalSpaceSweeper.tackOn(immortal);
    OTFSapphire.tackOnLock.release();
    flushRememberedSets();
    if (OTFSapphire.MERGE_REPLICATE_PHASE)
      patchQueue.flushLocal();
    super.deinitMutator();
    /* we release GC resources after we flush all buffers */
    if (newThread_needPrepareRelease) {
      super.collectionPhase(Simple.RELEASE, false);
    }
  }

  /****************************************************************************
   *
   * Mutator-time allocation
   */

  /**
   * Reassign the allocator used for a specific type of allocation.
   * 
   * @param allocator
   * @return
   */
  @Inline
  @Override
  public int checkAllocator(int bytes, int align, int allocator) {
    allocator = super.checkAllocator(bytes, align, allocator);
    switch(allocator) {
    case OTFSapphire.ALLOC_REPLICATING:
      return OTFSapphire.ALLOC_REPLICATING;
    case Plan.ALLOC_LOS:
      return OTFSapphire.NO_LOS ? OTFSapphire.ALLOC_IMMORTAL : OTFSapphire.ALLOC_LOS;
    case Plan.ALLOC_NON_MOVING:
      return OTFSapphire.NO_NON_MOVING ? OTFSapphire.ALLOC_IMMORTAL : OTFSapphire.ALLOC_NON_MOVING;
    case Plan.ALLOC_CODE:
      return OTFSapphire.NO_NON_MOVING ? OTFSapphire.ALLOC_IMMORTAL : OTFSapphire.ALLOC_CODE;
    case Plan.ALLOC_LARGE_CODE:
      return OTFSapphire.NO_LOS ? OTFSapphire.ALLOC_IMMORTAL : OTFSapphire.ALLOC_LARGE_CODE;
    default:
      return OTFSapphire.ALLOC_IMMORTAL;
    }
  }

  /**
   * Allocate space (for an object)
   *
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @param allocator The allocator number to be used for this allocation
   * @param site Allocation site
   * @return The address of the first byte of the allocated region
   */
  @Inline
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    Address addy;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(fromSpaceReplica.isZero());
    if (allocator == OTFSapphire.ALLOC_REPLICATING) {
      addy = fromSpaceLocal.alloc(bytes,  align, offset);  // may cause GC
      if (barrierEnable_AllocToSpace())
        addy = allocToSpace(addy, bytes, align, offset);
    } else if (allocator == OTFSapphire.ALLOC_IMMORTAL) {
      addy = immortal.alloc(bytes, align, offset);
    } else {
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(
            allocator == OTFSapphire.ALLOC_LOS || 
            allocator == OTFSapphire.ALLOC_LARGE_CODE ||
            allocator == OTFSapphire.ALLOC_NON_MOVING || 
            allocator == OTFSapphire.ALLOC_CODE
        );
      }
      addy = super.alloc(bytes, align, offset, allocator, site);
    }
    return addy;
  }

  @NoInline
  private Address allocToSpace(Address fromSpaceCopy, int bytes, int align, int offset) {
    if (fromSpaceCopy.isZero())
      return fromSpaceCopy;
    Address addy = toSpaceLocal.alloc(bytes, align, offset); // never triggers GC
    if (!addy.isZero())
      fromSpaceReplica = fromSpaceCopy;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(barrierEnable_AllocToSpace());

    return addy;
  }

  /**
   * Perform post-allocation actions.  For many allocators none are
   * required.
   *
   * @param object The newly allocated object
   * @param typeRef The type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  @Inline
  // postAlloc called for new Scalars and Arrays
  public void postAlloc(ObjectReference object, ObjectReference typeRef, int bytes, int allocator, int align, int offset) {
    if (allocator == OTFSapphire.ALLOC_REPLICATING) {
      ReplicatingSpace.initializeHeader(object, true);
      if (barrierEnable_AllocBlack()) {
        postAllocReplica(object, bytes, align, offset);
      }
      if (VM.VERIFY_ASSERTIONS) {
        if (!fromSpaceReplica.isZero()) {
          error_fromSpaceReplica();
        }
      }
    } else {
      postAllocOOL(object, allocator);
    }
  }
  
  @NoInline
  private void error_fromSpaceReplica() {
    Log.writeln("fromSpaceReplica = ", fromSpaceReplica);
    Log.write("barrierEnable_AllocBlack = ");
    Log.writeln(barrierEnable_AllocBlack());
    Log.write("barrierEnable_AllocToSpace = ");
    Log.writeln(barrierEnable_AllocToSpace());
    VM.assertions.fail("panic");
  }

  @NoInline
  private void postAllocOOL(ObjectReference object, int allocator) {
    // CGR: this basically replicates postAlloc() in MutatorContext...
    switch (allocator) {
    case           Plan.ALLOC_LOS: los.initializeHeader(object); return;
    case      Plan.ALLOC_IMMORTAL: immortal.initializeHeader(object);  return;
    case          Plan.ALLOC_CODE: smcode.initializeHeader(object); return;
    case    Plan.ALLOC_LARGE_CODE: lgcode.initializeHeader(object); return;
    case    Plan.ALLOC_NON_MOVING: nonmove.initializeHeader(object); return;
    default:
      VM.assertions.fail("unknown allocator");
    }
  }
  
  @NoInline
  private void postAllocReplica(ObjectReference object, int bytes, int align, int offset) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(OTFSapphire.fromSpace().getDescriptor() == fromSpaceDescriptor);
    int alignedUpBytes = bytes + (MIN_ALIGNMENT - 1) & ~(MIN_ALIGNMENT - 1);
    if (barrierEnable_AllocToSpace()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!fromSpaceReplica.isZero());
      Address fromAddr = fromSpaceReplica;
      fromSpaceReplica = Address.zero();
      ObjectReference fromObj = VM.objectModel.fillInBlankDoubleRelica(object, fromAddr, bytes);
      ReplicatingSpace.setReplicaPointer(object, fromObj, null, ReplicatingSpace.CHECK_BACKWARD, ReplicatingSpace.FOR_NEW_OBJECT);
      ReplicatingSpace.setReplicaPointer(fromObj, object, null, ReplicatingSpace.CHECK_FORWARD, ReplicatingSpace.FOR_NEW_OBJECT);
      ReplicatingSpace.setForwarded(fromObj, null, ReplicatingSpace.FOR_NEW_OBJECT);
    } else {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(fromSpaceReplica.isZero());
      Address toAddr = toSpaceLocal.alloc(alignedUpBytes, align, offset);
      ObjectReference toObj = VM.objectModel.fillInBlankDoubleRelica(object, toAddr, bytes);
      ReplicatingSpace.setReplicaPointer(object, toObj, null, ReplicatingSpace.CHECK_FORWARD, ReplicatingSpace.FOR_NEW_OBJECT);
      ReplicatingSpace.setReplicaPointer(toObj, object, null, ReplicatingSpace.CHECK_BACKWARD, ReplicatingSpace.FOR_NEW_OBJECT);
      ReplicatingSpace.setForwarded(object, null, ReplicatingSpace.FOR_NEW_OBJECT);
    }
  }

  /**
   * Return the allocator instance associated with a space
   * <code>space</code>, for this plan instance.
   *
   * @param space The space for which the allocator instance is desired.
   * @return The allocator instance associated with this plan instance
   * which is allocating into <code>space</code>, or <code>null</code>
   * if no appropriate allocator can be established.
   */
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == OTFSapphire.repSpace0 || space == OTFSapphire.repSpace1)
      return fromSpaceLocal;
    return super.getAllocatorFromSpace(space);
  }

  protected void setupSpace() {
    fromSpace = OTFSapphire.getSpace(fromSpaceLow);
    ReplicatingSpace toSpace = OTFSapphire.getSpace(!fromSpaceLow);
    fromSpaceLocal.rebind(fromSpace);
    toSpaceLocal.rebind(toSpace);
    fromSpaceDescriptor = fromSpace.getDescriptor();
    toSpaceDescriptor = toSpace.getDescriptor();
  }
  
  @Inline
  public boolean inFromSpace(ObjectReference obj) {
    return inFromSpace(VM.objectModel.refToAddress(obj));
  }
  
  @Inline
  protected boolean inFromSpace(Address addr) {
    return Space.isInSpace(fromSpaceDescriptor, addr);
  }

  @Inline
  public boolean inToSpace(ObjectReference obj) {
    return inToSpace(VM.objectModel.refToAddress(obj));
  }
  
  @Inline
  protected boolean inToSpace(Address addr) {
    return Space.isInSpace(toSpaceDescriptor, addr);
  }
  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-mutator collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {

    /*
     * 1. Mark & Allocate phase
     */

    if (phaseId == Simple.PREPARE) {
      if (primary) {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!OTFSapphireMutator.newThreadBarrierEnable());
        newThread_needPrepareRelease      = true;
        if (OTFSapphire.MERGE_REPLICATE_PHASE)
          newThreadBarrierSet(
              BARRIER_FLAG_INSERTION |
              BARRIER_FLAG_STATUS_WORD |
              BARRIER_FLAG_DOUBLE_UPDATE |
              BARRIER_FLAG_SYNC_DBL_UPD,
              0);
        else
          newThreadBarrierSet(
              BARRIER_FLAG_INSERTION |
              BARRIER_FLAG_STATUS_WORD,
              0);
      }
      setupBarrier();
      if (!prepared)
        super.collectionPhase(Simple.PREPARE, primary);
      prepared = false;
      return;
    }

    if (phaseId == OTFSapphire.PREPARE_ALLOC) {
      if (primary) {
        if (VM.VERIFY_ASSERTIONS) OTFSapphire.assert_TO_SPLACE_LOCAL_IS_EMPTY = false;
        newThreadBarrierSet(BARRIER_FLAG_ALLOC_BLACK, 0);
      }
      setupBarrier();
      immortal.prepare();
      los.prepareOTFCollection();
      nonmove.prepareOTFCollection();
      if (OTFSapphire.USE_CODE_SPACE) {
        smcode.prepareOTFCollection();
        lgcode.prepareOTFCollection();
      }
      return;
    }

    if (OTFSapphire.REFERENCE_PROCESS_YUASA) {
      if (phaseId == OTFSapphire.PREPARE_CLEANING_SETUP_BARRIER) {
        if (primary) {
          newThreadBarrierSet(BARRIER_FLAG_DELETION, 0);
        }
        setupBarrier();
        return;
      }

      if (phaseId == OTFSapphire.PREPARE_CLEANING_SETUP_BARRIER_2) {
        if (primary) {
          newThreadBarrierSet(0, BARRIER_FLAG_INSERTION);
        }
        setupBarrier();
        return;
      }
    }

    // PREPARE_STACKS: nothing special because this collection is stop-the-world

    if (phaseId == Simple.RELEASE) {
      if (primary) {
        if (OTFSapphire.REFERENCE_PROCESS_YUASA)
          newThreadBarrierSet(
              0,
              BARRIER_FLAG_DELETION |
              BARRIER_FLAG_SYNC_DBL_UPD);
        else
          newThreadBarrierSet(
              0,
              BARRIER_FLAG_INSERTION |
              BARRIER_FLAG_SYNC_DBL_UPD);
      }
      setupBarrier();
      if (OTFSapphire.MERGE_REPLICATE_PHASE)
        patchQueue.flushLocal();
      return;
    }

    /*
     * 2. Copy phase
     */
    if (phaseId == OTFSapphire.PREPARE_COPY) {
      if (primary) {
        newThreadBarrierSet(BARRIER_FLAG_DOUBLE_UPDATE, 0);
      }
      setupBarrier();
      /* flush replica shells of objects created during GC for enabling to be filled */
      OTFSapphire.tackOnLock.acquire();
      OTFSapphire.toSpaceSweeper.tackOn(toSpaceLocal);
      toSpaceLocal.reset();  /* allow mutator to continue to create to-space shells */
      OTFSapphire.tackOnLock.release();
      return;
    }

    if (phaseId == OTFSapphire.RELEASE_COPY) {
      return;
    }

    if (OTFSapphire.ENABLE_VERIFY_COPY) {
      if (phaseId == OTFSapphire.VERIFY_COPY) {
        OTFSapphire.tackOnLock.acquire();
        toSpaceLocal.linearScan(OTFSapphire.verifyCopyLinearScan);
        OTFSapphire.tackOnLock.release();
        return;
      }
    }

    /*
     * 3. Flip phase
     */
    if (phaseId == OTFSapphire.PREPARE_FLIP_SETUP_BARRIER_0) {
      if (primary) {
        newThreadBarrierSet(
            BARRIER_FLAG_CONDITIONAL_POINTER_UPDATE |
            BARRIER_FLAG_EQ,
            0);
      }
      setupBarrier();
      return;
    }

    if (phaseId == OTFSapphire.PREPARE_FLIP) {
      if (primary) {
        newThreadBarrierSet(
            BARRIER_FLAG_POINTER_UPDATE |
            BARRIER_FLAG_ALLOC_TO_SPACE,
            BARRIER_FLAG_CONDITIONAL_POINTER_UPDATE);
      }
      setupBarrier();
      return;
    }
    
    /** Flip N */

    if (phaseId == OTFSapphire.FLUSH_IMMORTAL) {
      OTFSapphire.tackOnLock.acquire();
      OTFSapphire.immortalSpaceSweeper.tackOn(immortal);
      OTFSapphire.tackOnLock.release();
      immortal.reset();
      nonmove.flush();
      if (OTFSapphire.USE_CODE_SPACE) {
        smcode.flush();
      }
      return;
    }

    /** Flip S */

    // PREPARE_STACKS: nothing special

    if (phaseId == OTFSapphire.RELEASE_FLIP) {
      if (primary) {
        if (VM.VERIFY_ASSERTIONS) OTFSapphire.assert_FROM_SPACE_FORWARDED_OBJECTS_HAVE_FP = false;
        if (VM.VERIFY_ASSERTIONS) OTFSapphire.assert_ALL_OBJECTS_HAVE_REPLICA = false;
        fromSpaceLow = !OTFSapphire.low;
        newThreadBarrierSet(
            0,
            BARRIER_FLAG_ALLOC_BLACK |
            BARRIER_FLAG_ALLOC_TO_SPACE |
            BARRIER_FLAG_DOUBLE_UPDATE |
            BARRIER_FLAG_EQ |
            BARRIER_FLAG_POINTER_UPDATE |
            BARRIER_FLAG_STATUS_WORD);
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!OTFSapphireMutator.newThreadBarrierEnable());
        newThread_needPrepareRelease         = false;
      }
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(fromSpaceReplica.isZero());
      setupSpace();
      setupBarrier();
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!barrierEnable());
      super.collectionPhase(Simple.RELEASE, primary);
      return;
    }

    // COMPLETE: nothing special

    super.collectionPhase(phaseId, primary);
  }

  /**
   * Prepare the given object for getting its hashcode.  Make its hash state HASHED
   * in the typical implementation.
   *
   * @param obj The object of which we will take a hashcode
   * @return The copy of the object from whose address the VM should generate the hashcode.
   */
  @Override
  public ObjectReference hashByAddress(ObjectReference obj) {
    if (!VM.objectModel.isUnhashed(obj))
      return obj;

    if (!barrierEnable_StatusWord() || !inFromSpace(obj)) {
      VM.objectModel.setHashed(obj);
      return obj;
    }

    if (ReplicatingSpace.isForwarded(obj)) {
      if (!VM.objectModel.isUnhashed(obj))
        return obj;
      ObjectReference toObj = ReplicatingSpace.getReplicaPointer(obj);
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!toObj.isNull());
      VM.objectModel.setHashed(toObj);
      return toObj;
    }

    ReplicatingSpace.atomicMarkBusy(obj, this);
    if (!ReplicatingSpace.isForwarded(obj)) {
      VM.objectModel.setHashed(obj);
      ReplicatingSpace.clearBusy(obj, this);
      return obj;
    }
    ReplicatingSpace.clearBusy(obj, this);

    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(ReplicatingSpace.isForwarded(obj));
    if (!VM.objectModel.isUnhashed(obj))
      return obj;
    ObjectReference toObj = ReplicatingSpace.getReplicaPointer(obj);
    VM.objectModel.setHashed(toObj);
    return toObj;
  }

  /**
   * Lock the status word of object obj.
   * @param obj The object whose status word we attmpt to read/write
   * @return The copy of the object which we can contact
   */
  @Override
  public ObjectReference metaLockObject(ObjectReference obj) {
    if (!barrierEnable_StatusWord() || !inFromSpace(obj)) {
      return obj;
    }
    ObjectReference target = ReplicatingSpace.metaLockObject(obj, this);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(ReplicatingSpace.isBusy(target) || !inFromSpace(target));
    return target;
  }

  @Override
  public void metaUnlockObject(ObjectReference obj) {
    if (!barrierEnable_StatusWord() || !inFromSpace(obj))
      return;
    ReplicatingSpace.metaUnlockObject(obj, this);
  }

  /****************************************************************************
   *
   * Write barrier utilities
   */

  /**
   * Process a reference that may require being enqueued as part of a concurrent
   * collection.
   *
   * @param ref The reference to check.
   * @return true if ref is enqueued for the future copy.  This means that ref is not the final address.
   */
  public void checkAndEnqueueReference(ObjectReference ref) {
    if (ref.isNull()) return;

    if (inFromSpace(ref)) {
      if (!ReplicatingSpace.isForwarded(ref)) {
        fromSpaceRemsetLocal.processNode(ref);  // we put ref without making its replica to avoid an allocation in a write barrier
      }
    } else {
      if (Space.isInSpace(OTFSapphire.IMMORTAL, ref))        OTFSapphire.immortalSpace.traceObject(remset, ref);
      else if (Space.isInSpace(OTFSapphire.LOS,        ref)) OTFSapphire.loSpace.traceObject(remset, ref);
      else if (Space.isInSpace(OTFSapphire.NON_MOVING, ref)) OTFSapphire.nonMovingSpace.traceObject(remset, ref);
      else if (Space.isInSpace(OTFSapphire.SMALL_CODE, ref)) OTFSapphire.smallCodeSpace.traceObject(remset, ref);
      else if (Space.isInSpace(OTFSapphire.LARGE_CODE, ref)) OTFSapphire.largeCodeSpace.traceObject(remset, ref);
      /* otherwise, ref can be in boot image space */
    }
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(VM.objectModel.validRef(ref)); // catch inserting a non valid reference
      if (!Plan.gcInProgress()) {
        /* ref may yet be live if it is in the replicating space */
        if (Space.isInSpace(OTFSapphire.IMMORTAL,   ref)) VM.assertions._assert(OTFSapphire.immortalSpace.isLive(ref));
        else if (Space.isInSpace(OTFSapphire.LOS,        ref)) VM.assertions._assert(OTFSapphire.loSpace.isLive(ref));
        else if (Space.isInSpace(OTFSapphire.NON_MOVING, ref)) VM.assertions._assert(OTFSapphire.nonMovingSpace.isLive(ref));
        else if (Space.isInSpace(OTFSapphire.SMALL_CODE, ref)) VM.assertions._assert(OTFSapphire.smallCodeSpace.isLive(ref));
        else if (Space.isInSpace(OTFSapphire.LARGE_CODE, ref)) VM.assertions._assert(OTFSapphire.largeCodeSpace.isLive(ref));
      }
    }
  }

  /**
   * Flush per-mutator remembered sets into the global remset pool.
   */
  @Override
  public void flushRememberedSets() {
    fromSpaceRemsetLocal.flush();
    remset.flush();
  }

  /**
   * This method judges the object in question is live or not in the alloc phase
   * for reference types. 
   */
  @Override
  protected boolean isLive(ObjectReference obj) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!inToSpace(obj));
    if (inFromSpace(obj)) {
      return fromSpace.isLive(obj);
    } else {
      if (Space.isInSpace(OTFSapphire.IMMORTAL, obj))        return OTFSapphire.immortalSpace.isLive(obj);
      else if (Space.isInSpace(OTFSapphire.LOS,        obj)) return OTFSapphire.loSpace.isLive(obj);
      else if (Space.isInSpace(OTFSapphire.NON_MOVING, obj)) return OTFSapphire.nonMovingSpace.isLive(obj);
      else if (Space.isInSpace(OTFSapphire.SMALL_CODE, obj)) return OTFSapphire.smallCodeSpace.isLive(obj);
      else if (Space.isInSpace(OTFSapphire.LARGE_CODE, obj)) return OTFSapphire.largeCodeSpace.isLive(obj);
      else return true;
    }
  }
  /****************************************************************************
   *
   * Write barrier
   */

  /**
   * Returns the replica object of the given object in an appropriate way
   * if double update is needed.
   * @param object
   * @return Replica of object if double update is needed.  Otherwise, returns null.
   */
  private ObjectReference getReplicaObject(ObjectReference object) {
    if (OTFSapphire.MERGE_REPLICATE_PHASE && barrierEnable_SyncDoubleUpdate()) {
      if (inFromSpace(object)) {
        VM.memory.fence();
        if (ReplicatingSpace.isForwarded(object))
          return ReplicatingSpace.getReplicaPointer(object);
      }
    } else if (barrierEnable_DoubleUpdate()) {
      return ReplicatingSpace.getReplicaPointer(object);
    }
    return null;
  }

  /**
   * Write a boolean. Take appropriate write barrier actions.
   * <p>
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param value The value of the new boolean
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @NoInline
  private void _booleanWrite(ObjectReference src, Address slot, boolean value, Word metaDataA, Word metaDataB, int mode) {
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, src);
    VM.barriers.booleanWrite(src, value, metaDataA, metaDataB, mode);
    ObjectReference forwarded = getReplicaObject(src);
    if (!forwarded.isNull())
      VM.barriers.booleanWrite(forwarded, value, metaDataA, metaDataB, mode);
  }

  @Override
  @Inline
  public void booleanWrite(ObjectReference src, Address slot, boolean value, Word metaDataA, Word metaDataB, int mode) {
    if (barrierEnable()) {
      _booleanWrite(src, slot, value, metaDataA, metaDataB, mode);
    } else {
      VM.barriers.booleanWrite(src, value, metaDataA, metaDataB, mode);
    }
  }
  
  /**
   * A number of booleans are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy).
   * Thus, <code>dst</code> is the mutated object. Take appropriate write barrier actions.
   * <p>
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if left to the caller
   */
  public boolean booleanBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Not actually called yet - something to optimise later
    return false;
  }

  /**
   * Write a byte. Take appropriate write barrier actions.
   * <p>
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param value The value of the new byte
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @NoInline
  private void _byteWrite(ObjectReference src, Address slot, byte value, Word metaDataA, Word metaDataB, int mode) {
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, src);
    VM.barriers.byteWrite(src, value, metaDataA, metaDataB, mode);
    ObjectReference forwarded = getReplicaObject(src);
    if (!forwarded.isNull())
      VM.barriers.byteWrite(forwarded, value, metaDataA, metaDataB, mode);
  }
  
  @Override
  @Inline
  public void byteWrite(ObjectReference src, Address slot, byte value, Word metaDataA, Word metaDataB, int mode) {
    if (barrierEnable()) {
      _byteWrite(src, slot, value, metaDataA, metaDataB, mode);
    } else {
      VM.barriers.byteWrite(src, value, metaDataA, metaDataB, mode);
    }
  }

  /**
   * A number of bytes are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy). Thus,
   * <code>dst</code> is the mutated object. Take appropriate write barrier actions.
   * <p>
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if left to the caller
   */
  public boolean byteBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Not actually called yet - something to optimise later
    return false;
  }

  /**
   * Write a char. Take appropriate write barrier actions.
   * <p>
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param value The value of the new char
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @NoInline
  private void _charWrite(ObjectReference src, Address slot, char value, Word metaDataA, Word metaDataB, int mode) {
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, src);
    VM.barriers.charWrite(src, value, metaDataA, metaDataB, mode);
    ObjectReference forwarded = getReplicaObject(src);
    if (!forwarded.isNull())
      VM.barriers.charWrite(forwarded, value, metaDataA, metaDataB, mode);
  }
  
  @Override
  @Inline
  public void charWrite(ObjectReference src, Address slot, char value, Word metaDataA, Word metaDataB, int mode) {
    if (barrierEnable()) {
      _charWrite(src, slot, value, metaDataA, metaDataB, mode);
    } else {
      VM.barriers.charWrite(src, value, metaDataA, metaDataB, mode);
    }
  }

  /**
   * A number of chars are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy). Thus,
   * <code>dst</code> is the mutated object. Take appropriate write barrier actions.
   * <p>
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if left to the caller
   */
  public boolean charBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Not actually called yet - something to optimise later
    return false;
  }

  /**
   * Write a double. Take appropriate write barrier actions.
   * <p>
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param value The value of the new double
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @NoInline
  private void _doubleWrite(ObjectReference src, Address slot, double value, Word metaDataA, Word metaDataB, int mode) {
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, src);
    VM.barriers.doubleWrite(src, value, metaDataA, metaDataB, mode);
    ObjectReference forwarded = getReplicaObject(src);
    if (!forwarded.isNull())
      VM.barriers.doubleWrite(forwarded, value, metaDataA, metaDataB, mode);
  }
  
  @Override
  @Inline
  public void doubleWrite(ObjectReference src, Address slot, double value, Word metaDataA, Word metaDataB, int mode) {
    if (barrierEnable()) {
      _doubleWrite(src, slot, value, metaDataA, metaDataB, mode);
    } else {
      VM.barriers.doubleWrite(src, value, metaDataA, metaDataB, mode);
    }
  }  
  
  /**
   * A number of doubles are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy). Thus,
   * <code>dst</code> is the mutated object. Take appropriate write barrier actions.
   * <p>
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if left to the caller
   */
  public boolean doubleBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Not actually called yet - something to optimise later
    return false;
  }

  /**
   * Write a float. Take appropriate write barrier actions.
   * <p>
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param value The value of the new float
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @NoInline
  private void _floatWrite(ObjectReference src, Address slot, float value, Word metaDataA, Word metaDataB, int mode) {
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, src);
    VM.barriers.floatWrite(src, value, metaDataA, metaDataB, mode);
    ObjectReference forwarded = getReplicaObject(src);
    if (!forwarded.isNull())
      VM.barriers.floatWrite(forwarded, value, metaDataA, metaDataB, mode);
  }
  
  @Override
  @Inline
  public void floatWrite(ObjectReference src, Address slot, float value, Word metaDataA, Word metaDataB, int mode) {
    if (barrierEnable()) {
      _floatWrite(src, slot, value, metaDataA, metaDataB, mode);
    } else {
      VM.barriers.floatWrite(src, value, metaDataA, metaDataB, mode);
    }
  }

  /**
   * A number of floats are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy). Thus,
   * <code>dst</code> is the mutated object. Take appropriate write barrier actions.
   * <p>
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if left to the caller
   */
  public boolean floatBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Not actually called yet - something to optimise later
    return false;
  }

  /**
   * Write a int. Take appropriate write barrier actions.
   * <p>
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param value The value of the new int
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @NoInline
  private void _intWrite(ObjectReference src, Address slot, int value, Word metaDataA, Word metaDataB, int mode) {
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, src);
    VM.barriers.intWrite(src, value, metaDataA, metaDataB, mode);
    ObjectReference forwarded = getReplicaObject(src);
    if (!forwarded.isNull())
      VM.barriers.intWrite(forwarded, value, metaDataA, metaDataB, mode);
  }
  
  @Override
  @Inline
  public void intWrite(ObjectReference src, Address slot, int value, Word metaDataA, Word metaDataB, int mode) {
    if (barrierEnable()) {
      _intWrite(src, slot, value, metaDataA, metaDataB, mode);
    } else {
      VM.barriers.intWrite(src, value, metaDataA, metaDataB, mode);
    }
  }

  /**
   * A number of ints are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy). Thus,
   * <code>dst</code> is the mutated object. Take appropriate write barrier actions.
   * <p>
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if left to the caller
   */
  public boolean intBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Not actually called yet - something to optimise later
    return false;
  }

  /**
   * Attempt to atomically exchange the value in the given slot with the passed replacement value.
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param old The old int to be swapped out
   * @param value The new int
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   * @return True if the swap was successful.
   */
  public boolean intTryCompareAndSwap(ObjectReference src, Address slot, int old, int value, Word metaDataA, Word metaDataB,
                                      int mode) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!(inFromSpace(slot) || inToSpace(slot)));
    return VM.barriers.intTryCompareAndSwap(src, old, value, metaDataA, metaDataB, mode);
  }

  /**
   * Write a long. Take appropriate write barrier actions.
   * <p>
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param value The value of the new long
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @NoInline
  private void _longWrite(ObjectReference src, Address slot, long value, Word metaDataA, Word metaDataB, int mode) {
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, src);
    VM.barriers.longWrite(src, value, metaDataA, metaDataB, mode);
    ObjectReference forwarded = getReplicaObject(src);
    if (!forwarded.isNull())
      VM.barriers.longWrite(forwarded, value, metaDataA, metaDataB, mode);
  }

  @Override
  @Inline
  public void longWrite(ObjectReference src, Address slot, long value, Word metaDataA, Word metaDataB, int mode) {
    if (barrierEnable()) {
      _longWrite(src, slot, value, metaDataA, metaDataB, mode);
    } else {
      VM.barriers.longWrite(src, value, metaDataA, metaDataB, mode);
    }
  }
  
  /**
   * A number of longs are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy). Thus,
   * <code>dst</code> is the mutated object. Take appropriate write barrier actions.
   * <p>
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if left to the caller
   */
  public boolean longBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Not actually called yet - something to optimise later
    return false;
  }

  /**
   * Attempt to atomically exchange the value in the given slot with the passed replacement value.
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param old The old long to be swapped out
   * @param value The new long
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   * @return True if the swap was successful.
   */
  public boolean longTryCompareAndSwap(ObjectReference src, Address slot, long old, long value, Word metaDataA, Word metaDataB,
                                       int mode) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!(inFromSpace(slot) || inToSpace(slot)));
    return VM.barriers.longTryCompareAndSwap(src, old, value, metaDataA, metaDataB, mode);
  }

  /**
   * Write a short. Take appropriate write barrier actions.
   * <p>
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param value The value of the new short
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @NoInline
  private void _shortWrite(ObjectReference src, Address slot, short value, Word metaDataA, Word metaDataB, int mode) {
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, src);
    VM.barriers.shortWrite(src, value, metaDataA, metaDataB, mode);
    ObjectReference forwarded = getReplicaObject(src);
    if (!forwarded.isNull())
      VM.barriers.shortWrite(forwarded, value, metaDataA, metaDataB, mode);
  }
  
  @Override
  @Inline
  public void shortWrite(ObjectReference src, Address slot, short value, Word metaDataA, Word metaDataB, int mode) {
    if (barrierEnable()) {
      _shortWrite(src, slot, value, metaDataA, metaDataB, mode);
    } else {
      VM.barriers.shortWrite(src, value, metaDataA, metaDataB, mode);
    }
  }
  
  /**
   * A number of shorts are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy). Thus,
   * <code>dst</code> is the mutated object. Take appropriate write barrier actions.
   * <p>
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if left to the caller
   */
  public boolean shortBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Not actually called yet - something to optimise later
    return false;
  }

  /**
   * Write a Word. Take appropriate write barrier actions.
   * <p>
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param value The value of the new Word
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @NoInline
  private void _wordWrite(ObjectReference src, Address slot, Word value, Word metaDataA, Word metaDataB, int mode) {
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, src);
    VM.barriers.wordWrite(src, value, metaDataA, metaDataB, mode);
    ObjectReference forwarded = getReplicaObject(src);
    if (!forwarded.isNull())
      VM.barriers.wordWrite(forwarded, value, metaDataA, metaDataB, mode);
  }
  
  @Override
  @Inline
  public void wordWrite(ObjectReference src, Address slot, Word value, Word metaDataA, Word metaDataB, int mode) {
    if (barrierEnable()) {
      _wordWrite(src, slot, value, metaDataA, metaDataB, mode);
    } else {
      VM.barriers.wordWrite(src, value, metaDataA, metaDataB, mode);
    }
  }

  /**
   * Write a Address during GC into toSpace. Take appropriate write barrier actions.
   * <p>
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param value The value of the new Address
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  // only called from ReferenceProcessor.  Can be inlined
  // This method is executed in collector context.  Mutator context is not valid.
  public void addressWriteDuringGC(ObjectReference src, Address slot, Address value, Word metaDataA, Word metaDataB, int mode) {
    /* Update referent of a reference type.  Replica pointers are followed in ReferenceProcessor if it is needed. */
    VM.barriers.addressWrite(src, value, metaDataA, metaDataB, mode);
    if (!src.isNull() && OTFSapphire.inFromSpace(src) && ReplicatingSpace.isForwarded(src)) {
      ObjectReference forwarded = ReplicatingSpace.getReplicaPointer(src);
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!forwarded.isNull());
      VM.barriers.addressWrite(forwarded, value, metaDataA, metaDataB, mode);
    }
  }

  public void _addressWriteToReferenceTable(ObjectReference src, Address slot, Address value, Word metaDataA, Word metaDataB, int mode) {
    ObjectReference ref = value.toObjectReference();
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertionsObjectReferenceValue(ref, slot);
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, src);
    if (barrierEnable_PointerUpdate() ||
        (barrierEnable_ConditionalPointerUpdate() && inToSpace(slot))) {
      if (inFromSpace(ref))
        ref = ReplicatingSpace.getReplicaPointer(ref);
    }
    VM.barriers.addressWriteToReferenceTable(src, ref.toAddress(), metaDataA, metaDataB, mode);
    ObjectReference forwarded = getReplicaObject(src);
    if (!forwarded.isNull()) {
      if (inFromSpace(ref)) {
        if (ReplicatingSpace.isForwarded(ref))
          ref = ReplicatingSpace.getReplicaPointer(ref);
        else if (OTFSapphire.MERGE_REPLICATE_PHASE) {
          if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(inFromSpace(slot));
          if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(inToSpace(forwarded));
          patchQueue.push(slot, forwarded.toAddress().plus(slot.diff(src.toAddress())));
          return;
        } else
          if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
      } else if (barrierEnable_ConditionalPointerUpdate() && inFromSpace(forwarded)) {
	if (inToSpace(ref)) {
	  ref = ReplicatingSpace.getReplicaPointer(ref);
	  if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(inFromSpace(ref));
	}
      }
      VM.barriers.addressWriteToReferenceTable(forwarded, ref.toAddress(), metaDataA, metaDataB, mode);
    }
  }

  @Inline
  @Override
  public void addressWriteToReferenceTable(ObjectReference src, Address slot, Address value, Word metaDataA, Word metaDataB, int mode) {
    if (barrierEnable()) {
      _addressWriteToReferenceTable(src, slot, value, metaDataA, metaDataB, mode);
    } else {
      VM.barriers.addressWriteToReferenceTable(src, value, metaDataA, metaDataB, mode);
    }
  }

  public void _javaLangReferenceWriteBarrier(ObjectReference reference, Address slot, ObjectReference referent, Word metaDataA, Word metaDataB, int mode) {
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertionsObjectReferenceValue(referent, slot);
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, reference);
    if (barrierEnable_PointerUpdate() ||
        (barrierEnable_ConditionalPointerUpdate() && inToSpace(slot))) {
      if (inFromSpace(referent))
        referent = ReplicatingSpace.getReplicaPointer(referent);
    }
    VM.barriers.addressWrite(reference, referent.toAddress(), metaDataA, metaDataB, mode);
    ObjectReference forwarded = getReplicaObject(reference);
    if (!forwarded.isNull()) {
      if (inFromSpace(referent)) {
        if (ReplicatingSpace.isForwarded(referent))
          referent = ReplicatingSpace.getReplicaPointer(referent);
        else if (OTFSapphire.MERGE_REPLICATE_PHASE) {
          if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(inFromSpace(slot));
          if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(inToSpace(forwarded));
          patchQueue.push(slot, forwarded.toAddress().plus(slot.diff(reference.toAddress())));
          return;
        } else
          if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
      } else if (barrierEnable_ConditionalPointerUpdate() && inFromSpace(forwarded)) {
	if (inToSpace(referent)) {
	  referent = ReplicatingSpace.getReplicaPointer(referent);
	  if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(inFromSpace(referent));
	}
      }
      VM.barriers.addressWrite(forwarded, referent.toAddress(), metaDataA, metaDataB, mode);
    }
  }

  @Inline
  @Override
  public void javaLangReferenceWriteBarrier(ObjectReference reference, Address slot, ObjectReference referent, Word metaDataA, Word metaDataB, int mode) {
    if (barrierEnable()) {
      _javaLangReferenceWriteBarrier(reference, slot, referent, metaDataA, metaDataB, mode);
    } else {
      VM.barriers.addressWrite(reference, referent.toAddress(), metaDataA, metaDataB, mode);
    }
  }

  /**
   * Attempt to atomically exchange the value in the given slot with the passed replacement value.
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param old The old long to be swapped out
   * @param value The new long
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   * @return True if the swap was successful.
   */
  public boolean wordTryCompareAndSwap(ObjectReference src, Address slot, Word old, Word value, Word metaDataA, Word metaDataB,
                                       int mode) {
    
    if (VM.VERIFY_ASSERTIONS) {
      Address lockAddress = src.toAddress().plus(VM.objectModel.getThinLockOffset(src));
      if (slot.NE(lockAddress)) {
        VM.assertions._assert(!inToSpace(slot), "Warning attempting wordTryCompareAndSwap on object in Sapphire toSpace");
        VM.assertions._assert(!inFromSpace(slot), "Warning attempting wordTryCompareAndSwap on object in Sapphire fromSpace");
      }
    }
    return VM.barriers.wordTryCompareAndSwap(src, old, value, metaDataA, metaDataB, mode);
  }

  /**
   * Write a Address. Take appropriate write barrier actions.
   * <p>
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param value The value of the new Address
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @NoInline
  private void _addressWrite(ObjectReference src, Address slot, Address value, Word metaDataA, Word metaDataB, int mode) {
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, src);
    VM.barriers.addressWrite(src, value, metaDataA, metaDataB, mode);
    ObjectReference forwarded = getReplicaObject(src);
    if (!forwarded.isNull())
      VM.barriers.addressWrite(forwarded, value, metaDataA, metaDataB, mode);
  }

  @Override
  @Inline
  public void addressWrite(ObjectReference src, Address slot, Address value, Word metaDataA, Word metaDataB, int mode) {
    if (barrierEnable()) {
      _addressWrite(src, slot, value, metaDataA, metaDataB, mode);
    } else {
      VM.barriers.addressWrite(src, value, metaDataA, metaDataB, mode);
    }
  }

  /**
   * Write a Extent. Take appropriate write barrier actions.
   * <p>
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param value The value of the new Extent
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @NoInline
  private void _extentWrite(ObjectReference src, Address slot, Extent value, Word metaDataA, Word metaDataB, int mode) {
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, src);
    VM.barriers.extentWrite(src, value, metaDataA, metaDataB, mode);
    ObjectReference forwarded = getReplicaObject(src);
    if (!forwarded.isNull())
      VM.barriers.extentWrite(forwarded, value, metaDataA, metaDataB, mode);
  }

  @Override
  @Inline
  public void extentWrite(ObjectReference src, Address slot, Extent value, Word metaDataA, Word metaDataB, int mode) {
    if (barrierEnable()) {
      _extentWrite(src, slot, value, metaDataA, metaDataB, mode);
    } else {
      VM.barriers.extentWrite(src, value, metaDataA, metaDataB, mode);
    }
  }
  
  /**
   * Write a Offset. Take appropriate write barrier actions.
   * <p>
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param value The value of the new Offset
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @NoInline
  private void _offsetWrite(ObjectReference src, Address slot, Offset value, Word metaDataA, Word metaDataB, int mode) {
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, src);
    VM.barriers.offsetWrite(src, value, metaDataA, metaDataB, mode);
    ObjectReference forwarded = getReplicaObject(src);
    if (!forwarded.isNull())
      VM.barriers.offsetWrite(forwarded, value, metaDataA, metaDataB, mode);
  }
  
  @Override
  @Inline
  public void offsetWrite(ObjectReference src, Address slot, Offset value, Word metaDataA, Word metaDataB, int mode) {
    if (barrierEnable()) {
      _offsetWrite(src, slot, value, metaDataA, metaDataB, mode);
    } else {
      VM.barriers.offsetWrite(src, value, metaDataA, metaDataB, mode);
    }
  }

  /**
   * Write an object reference. Take appropriate write barrier actions.
   * <p>
   * <b>By default do nothing, override if appropriate.</b>
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param value The value of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @NoInline
  private void _objectReferenceWrite(ObjectReference src, Address slot, ObjectReference value, Word metaDataA, Word metaDataB,
                                   int mode) {
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertionsObjectReferenceValue(value, slot);
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, src);
    if (barrierEnable_PointerUpdate() ||
        (barrierEnable_ConditionalPointerUpdate() && inToSpace(slot))) {
      if (inFromSpace(value))
        value = ReplicatingSpace.getReplicaPointer(value);
    }
    if (OTFSapphire.REFERENCE_PROCESS_YUASA) 
      if (barrierEnable_Deletion()) {
        ObjectReference old = VM.barriers.objectReferenceRead(src, metaDataA, metaDataB, mode);
        checkAndEnqueueReference(old);
      }
    VM.barriers.objectReferenceWrite(src, value, metaDataA, metaDataB, mode);
    if (barrierEnable_Insertion())
      checkAndEnqueueReference(value);
    ObjectReference forwarded = getReplicaObject(src);
    if (!forwarded.isNull()) {
      if (inFromSpace(value)) {
        if (ReplicatingSpace.isForwarded(value))
          value = ReplicatingSpace.getReplicaPointer(value);
        else if (OTFSapphire.MERGE_REPLICATE_PHASE) {
          /*
           * In order to avoid putting the same slot in the patchQueue many times,
           * we mark the field in the to-space object by setting the lowest bit when we
           * put it on the queue.  Note that the collector may update the value.
           * Also note that multiple mutators races without synchronisation.
           * These lead the second enqueue, which is redundant but not semantically wrong.
           * We leave the from-space value in the higher bits for debugging.
           */
          if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(inFromSpace(slot));
          if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(inToSpace(forwarded));
          ObjectReference prevValue = VM.barriers.objectReferenceRead(forwarded, metaDataA, metaDataB, mode);
          if (!OTFSapphire.DO_MULTIPLE_PATCH && (prevValue.toAddress().toInt() & 1) != 0) {
            if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(inFromSpace(Address.fromIntZeroExtend(prevValue.toAddress().toInt() & ~1)));
          } else {
            patchQueue.push(slot, forwarded.toAddress().plus(slot.diff(src.toAddress())));
            ObjectReference markedValue = Address.fromIntZeroExtend(value.toAddress().toInt() | 1).toObjectReference();
            VM.barriers.objectReferenceWrite(forwarded, markedValue, metaDataA, metaDataB, mode);
          }
          return;
        } else
          if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
      } else if (barrierEnable_ConditionalPointerUpdate() && inFromSpace(forwarded)) {
	if (inToSpace(value)) {
	  value = ReplicatingSpace.getReplicaPointer(value);
	  if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(inFromSpace(value));
	}
      }
      VM.barriers.objectReferenceWrite(forwarded, value, metaDataA, metaDataB, mode);
    }
  }
  
  @Override
  @Inline
  public void objectReferenceWrite(ObjectReference src, Address slot, ObjectReference value, Word metaDataA, Word metaDataB,
      int mode) {
    if (barrierEnable()) {
      _objectReferenceWrite(src, slot, value, metaDataA, metaDataB, mode);
    } else {
      VM.barriers.objectReferenceWrite(src, value, metaDataA, metaDataB, mode);
    }
  }

  /**
   * A number of references are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy).
   * Thus, <code>dst</code> is the mutated object. Take appropriate write barrier actions.
   * <p>
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if left to the caller (always false in this case).
   */
  public boolean objectReferenceBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Not actually called yet - something to optimise later
    return false;
  }

  /**
   * Attempt to atomically exchange the value in the given slot with the passed replacement value. If a new reference is created, we
   * must then take appropriate write barrier actions.
   * <p>
   * <b>By default do nothing, override if appropriate.</b>
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be stored.
   * @param old The old reference to be swapped out
   * @param tgt The target of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   * @return True if the swap was successful.
   */
  @NoInline
  private boolean _objectReferenceTryCompareAndSwap(ObjectReference src, Address slot, ObjectReference old, ObjectReference tgt,
                                                  Word metaDataA, Word metaDataB, int mode) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!(inFromSpace(slot) || inToSpace(slot)));
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertionsObjectReferenceValue(tgt, slot);
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertions(slot, src);
    if (barrierEnable_PointerUpdate() ||
        (barrierEnable_ConditionalPointerUpdate() && inToSpace(slot))) {
      if (inFromSpace(tgt))
        tgt = ReplicatingSpace.getReplicaPointer(tgt);
    }
    if (OTFSapphire.REFERENCE_PROCESS_YUASA)
      if (barrierEnable_Deletion()) checkAndEnqueueReference(old);
    boolean result = VM.barriers.objectReferenceTryCompareAndSwap(src, old, tgt, metaDataA, metaDataB, mode);
    if (result)
      if (barrierEnable_Insertion()) checkAndEnqueueReference(tgt);
    return result;
  }
  
  @Override
  @Inline
  public boolean objectReferenceTryCompareAndSwap(ObjectReference src, Address slot, ObjectReference old, ObjectReference tgt,
      Word metaDataA, Word metaDataB, int mode) {
    if (barrierEnable()) {
      return _objectReferenceTryCompareAndSwap(src, slot, old, tgt, metaDataA, metaDataB, mode);
    } else {
      return VM.barriers.objectReferenceTryCompareAndSwap(src, old, tgt, metaDataA, metaDataB, mode);
    }
  }

  /**
   * A new reference is about to be created in a location that is not
   * a regular heap object.  Take appropriate write barrier actions.<p>
   *
   * In this case, we remember the address of the source of the
   * pointer if the new reference points into the nursery from
   * non-nursery space.
   *
   * @param slot The address into which the new reference will be stored.
   * @param tgt The target of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   */
  @Inline
  public final void objectReferenceNonHeapWrite(Address slot, ObjectReference tgt, Word metaDataA, Word metaDataB) {
    if (VM.VERIFY_ASSERTIONS) writeBarrierAssertionsObjectReferenceValue(tgt, slot);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!inFromSpace(slot));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!inToSpace(slot));
    if (barrierEnable_PointerUpdate()) {
      if (inFromSpace(tgt))
        tgt = ReplicatingSpace.getReplicaPointer(tgt);
    }
    if (OTFSapphire.REFERENCE_PROCESS_YUASA)
    	if (barrierEnable_Deletion()) {
    		ObjectReference old = slot.loadObjectReference();
    		checkAndEnqueueReference(old);
    	}
    VM.barriers.objectReferenceNonHeapWrite(slot, tgt, metaDataA, metaDataB);
    if (barrierEnable_Insertion()) checkAndEnqueueReference(tgt);
  }

  private void writeBarrierAssertions(Address slot, ObjectReference src) {
    if (barrierEnable_SyncDoubleUpdate()) VM.assertions._assert(!inToSpace(src));
    
    if (OTFSapphire.assert_FROM_SPACE_FORWARDED_OBJECTS_HAVE_FP) {
      if (inFromSpace(src)) {
        VM.assertions._assert(ReplicatingSpace.isForwarded(src));
        VM.assertions._assert(!ReplicatingSpace.getReplicaPointer(src).isNull());
      }
    }
    if (OTFSapphire.assert_ALL_OBJECTS_HAVE_REPLICA) {
      if (inFromSpace(src) || inToSpace(src)) {
        VM.assertions._assert(!ReplicatingSpace.getReplicaPointer(src).isNull());
      }
    }
    if (OTFSapphire.assert_TO_SPLACE_LOCAL_IS_EMPTY)
      VM.assertions._assert(toSpaceLocal.getCursor().isZero());
  }

  private void writeBarrierAssertionsObjectReferenceValue(ObjectReference value, Address slot) {
    if (value.isNull()) return;

    if (!Space.isMappedObject(value)) {
      Log.write("attempt to write ");
      Log.write(value);
      Log.write(" to ");
      Log.writeln(slot);
      VM.objectModel.dumpObject(value);
    }
    VM.assertions._assert(Space.isMappedObject(value));

    if (barrierEnable_PointerUpdate()) {
      if (inFromSpace(value)) {
        VM.assertions._assert(ReplicatingSpace.isForwarded(value));
        VM.assertions._assert(!ReplicatingSpace.getReplicaPointer(value).isNull());
      }
    }
  }
  
  @NoInline
  private boolean _objectReferenceCompare(ObjectReference refA, ObjectReference refB) {
    if (refA.toAddress().EQ(refB.toAddress())) return true;  // A and B are equal
    if (DEBUG_EQ_BARRIER) {
      if ((inFromSpace(refA) && ReplicatingSpace.isForwarded(refA) && inToSpace(refB))) {
        Log.write("EQ-barrier ");
        Log.write(refA);
        Log.write(" -> ");
        Log.write(ReplicatingSpace.getReplicaPointer(refA));
        Log.write(" vs ");
        Log.writeln(refB);
        Log.flush();
      }
      if ((inFromSpace(refB) && ReplicatingSpace.isForwarded(refB) && inToSpace(refA))) {
        Log.write("EQ-barrier ");
        Log.write(refB);
        Log.write(" -> ");
        Log.write(ReplicatingSpace.getReplicaPointer(refB));
        Log.write(" vs ");
        Log.writeln(refA);
        Log.flush();
      }
    }
    
    if (barrierEnable_EQ()) {
      if (inFromSpace(refA) && ReplicatingSpace.isForwarded(refA))
        return ReplicatingSpace.getReplicaPointer(refA).toAddress().EQ(refB.toAddress());
      if (inFromSpace(refB) && ReplicatingSpace.isForwarded(refB))
        return ReplicatingSpace.getReplicaPointer(refB).toAddress().EQ(refA.toAddress());
    }
    return false;
  }
  
  @Override
  @Inline
  public boolean objectReferenceCompare(ObjectReference refA, ObjectReference refB) {
    if (barrierEnable()) {
      return _objectReferenceCompare(refA, refB);
    } else {
      return (refA.toAddress().EQ(refB.toAddress()));
    }
  }

  @Inline
  @Override
  public ObjectReference javaLangReferenceReadBarrier(ObjectReference ref, boolean isSoft) {
    if (OTFSapphire.REFERENCE_PROCESS_YUASA) {
    	ObjectReference referent = super.javaLangReferenceReadBarrier(ref, isSoft);
    	if (barrierEnable_Deletion())
    		checkAndEnqueueReference(referent);
    	return referent;
    } else {
    	return super.javaLangReferenceReadBarrier(ref, isSoft);
    }
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * Show the status of each of the allocators.
   */
  public final void show() {
    fromSpaceLocal.show();
    toSpaceLocal.show();
    los.show();
    immortal.show();
  }
}
