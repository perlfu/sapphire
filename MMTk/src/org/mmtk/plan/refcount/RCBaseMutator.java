/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.refcount;

import org.mmtk.plan.Plan;
import org.mmtk.plan.StopTheWorldMutator;
import org.mmtk.plan.refcount.cd.CDMutator;
import org.mmtk.plan.refcount.cd.NullCDMutator;
import org.mmtk.plan.refcount.cd.TrialDeletionMutator;
import org.mmtk.policy.ExplicitFreeListLocal;
import org.mmtk.policy.ExplicitFreeListSpace;
import org.mmtk.policy.ExplicitLargeObjectLocal;
import org.mmtk.policy.Space;

import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior
 * and state for the <i>RCBase</i> plan, which implements the
 * base functionality for reference counted collectors.
 *
 * @see RCBase for an overview of the reference counting algorithm.<p>
 *
 * FIXME The SegregatedFreeList class (and its decendents such as
 * MarkSweepLocal) does not properly separate mutator and collector
 * behaviors, so the ms field below should really not exist in
 * this class as there is no collection-time allocation in this
 * collector.
 *
 * @see RCBase
 * @see RCBaseCollector
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 */
@Uninterruptible public class RCBaseMutator extends StopTheWorldMutator {

  /****************************************************************************
   * Instance fields
   */

  public ExplicitFreeListLocal rc;
  public ExplicitLargeObjectLocal los;
  private ExplicitFreeListLocal smcode = Plan.USE_CODE_SPACE ? new ExplicitFreeListLocal(RCBase.smallCodeSpace) : null;
  private ExplicitLargeObjectLocal lgcode = Plan.USE_CODE_SPACE ? new ExplicitLargeObjectLocal(Plan.largeCodeSpace) : null;

  public ObjectReferenceDeque modBuffer;
  public DecBuffer decBuffer;

  private NullCDMutator nullCD;
  private TrialDeletionMutator trialDeletionCD;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public RCBaseMutator() {
    rc = new ExplicitFreeListLocal(RCBase.rcSpace);
    los = new ExplicitLargeObjectLocal(RCBase.loSpace);
    decBuffer = new DecBuffer(global().decPool);
    modBuffer = new ObjectReferenceDeque("mod buf", global().modPool);
    switch (RCBase.CYCLE_DETECTOR) {
    case RCBase.NO_CYCLE_DETECTOR:
      nullCD = new NullCDMutator();
      break;
    case RCBase.TRIAL_DELETION:
      trialDeletionCD = new TrialDeletionMutator();
      break;
    }
  }

  /****************************************************************************
   *
   * Mutator-time allocation
   */

  /**
   * Allocate memory for an object. This class handles the default allocator
   * from the mark sweep space, and delegates everything else to the
   * superclass.
   *
   * @param bytes The number of bytes required for the object.
   * @param align Required alignment for the object.
   * @param offset Offset associated with the alignment.
   * @param allocator The allocator associated with this request.
   * @param site Allocation site
   * @return The low address of the allocated memory.
   */
  @Inline
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    switch(allocator) {
      case RCBase.ALLOC_NON_MOVING:
      case RCBase.ALLOC_RC:
        return rc.alloc(bytes, align, offset);
      case RCBase.ALLOC_LOS:
      case RCBase.ALLOC_PRIMITIVE_LOS:
          return los.alloc(bytes, align, offset);
      case RCBase.ALLOC_IMMORTAL:
        return immortal.alloc(bytes, align, offset);
      case RCBase.ALLOC_CODE:
        return smcode.alloc(bytes, align, offset);
      case RCBase.ALLOC_LARGE_CODE:
        return lgcode.alloc(bytes, align, offset);
      default:
        VM.assertions.fail("RC not aware of allocator");
        return Address.zero();
    }
  }

  /**
   * Perform post-allocation actions.  Initialize the object header for
   * objects in the mark-sweep space, and delegate to the superclass for
   * other objects.
   *
   * @param ref The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  @Inline
  public void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {
    switch(allocator) {
      case RCBase.ALLOC_NON_MOVING:
      case RCBase.ALLOC_RC:
      case RCBase.ALLOC_CODE:
        ExplicitFreeListSpace.unsyncSetLiveBit(ref);
      case RCBase.ALLOC_LOS:
      case RCBase.ALLOC_LARGE_CODE:
      case RCBase.ALLOC_IMMORTAL:
        if (RCBase.WITH_COALESCING_RC) modBuffer.push(ref);
      case RCBase.ALLOC_PRIMITIVE_LOS:
        RCHeader.initializeHeader(ref, typeRef, true);
        decBuffer.push(ref);
        break;
      default:
        VM.assertions.fail("RC not aware of allocator");
        break;
    }
  }

  /**
   * Return the space into which an allocator is allocating.  This
   * particular method will match against those spaces defined at this
   * level of the class hierarchy.  Subclasses must deal with spaces
   * they define and refer to superclasses appropriately.
   *
   * @param a An allocator
   * @return The space into which <code>a</code> is allocating, or
   *         <code>null</code> if there is no space associated with
   *         <code>a</code>.
   */
  public Space getSpaceFromAllocator(Allocator a) {
    if (a == rc)  return RCBase.rcSpace;
    if (a == los) return RCBase.loSpace;
    if (a == smcode) return RCBase.smallCodeSpace;
    if (a == lgcode) return RCBase.largeCodeSpace;
    return super.getSpaceFromAllocator(a);
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
    if (space == RCBase.rcSpace) return rc;
    if (space == RCBase.loSpace) return los;
    if (space == RCBase.smallCodeSpace) return smcode;
    if (space == RCBase.largeCodeSpace) return lgcode;
    return super.getAllocatorFromSpace(space);
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

    if (phaseId == RCBase.PREPARE) {
      rc.prepare();
      los.prepare();
      smcode.prepare();
      lgcode.prepare();
      decBuffer.flushLocal();
      modBuffer.flushLocal();
      return;
    }

    if (phaseId == RCBase.RELEASE) {
      los.release();
      rc.release();
      smcode.release();
      lgcode.release();
      return;
    }

    if (!cycleDetector().collectionPhase(phaseId)) {
      super.collectionPhase(phaseId, primary);
    }
  }

  /****************************************************************************
   *
   * RC methods
   */

  /**
   * Add an object to the dec buffer for this mutator.
   *
   * @param object The object to add
   */
  public final void addToDecBuffer(ObjectReference object) {
    decBuffer.push(object);
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>RC</code> instance. */
  @Inline
  private static RCBase global() {
    return (RCBase) VM.activePlan.global();
  }

  /** @return The active cycle detector instance */
  @Inline
  public final CDMutator cycleDetector() {
    switch (RCBase.CYCLE_DETECTOR) {
    case RCBase.NO_CYCLE_DETECTOR:
      return nullCD;
    case RCBase.TRIAL_DELETION:
      return trialDeletionCD;
    }

    VM.assertions.fail("No cycle detector instance found.");
    return null;
  }
}
