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
package org.mmtk.plan.generational.immix;

import org.mmtk.plan.generational.*;
import org.mmtk.policy.Space;
import org.mmtk.policy.immix.MutatorLocal;
import org.mmtk.utility.alloc.Allocator;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior and state for
 * the <code>GenImmix</code> two-generational copying collector.<p>
 *
 * Specifically, this class defines mutator-time semantics specific to the
 * mature generation (<code>GenMutator</code> defines nursery semantics).
 * In particular the mature space allocator is defined (for mutator-time
 * allocation into the mature space via pre-tenuring), and the mature space
 * per-mutator thread collection time semantics are defined (rebinding
 * the mature space allocator).<p>
 *
 * See {@link GenImmix} for a description of the <code>GenImmix</code> algorithm.
 *
 * @see GenImmix
 * @see GenImmixCollector
 * @see org.mmtk.plan.generational.GenMutator
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 * @see org.mmtk.plan.Phase
 */
@Uninterruptible
public class GenImmixMutator extends GenMutator {

  /******************************************************************
   * Instance fields
   */

  /**
   * The allocator for the mark-sweep mature space (the mutator may
   * "pretenure" objects into this space which is otherwise used
   * only by the collector)
   */
  private final MutatorLocal mature;


  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public GenImmixMutator() {
    mature = new MutatorLocal(GenImmix.immixSpace, false);
  }

  /****************************************************************************
   *
   * Mutator-time allocation
   */

  /**
   * Allocate memory for an object.
   *
   * @param bytes The number of bytes required for the object.
   * @param align Required alignment for the object.
   * @param offset Offset associated with the alignment.
   * @param allocator The allocator associated with this request.
   * @param site Allocation site
   * @return The low address of the allocated memory.
   */
  @Inline
  public final Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == GenImmix.ALLOC_MATURE) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false); // no pretenuring yet
      return mature.alloc(bytes, align, offset);
    }
    return super.alloc(bytes, align, offset, allocator, site);
  }

  /**
   * Perform post-allocation actions.  For many allocators none are
   * required.
   *
   * @param ref The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  @Inline
  public final void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {
    if (allocator == GenImmix.ALLOC_MATURE) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false); // no pretenuring yet
    } else {
      super.postAlloc(ref, typeRef, bytes, allocator);
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
    if (a == mature) return GenImmix.immixSpace;

    // a does not belong to this plan instance
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
    if (space == GenImmix.immixSpace) return mature;
    return super.getAllocatorFromSpace(space);
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-mutator collection phase.
   *
   * @param phaseId Collection phase to perform
   * @param primary Is this thread to do the one-off thread-local tasks
   */
  @NoInline
  public void collectionPhase(short phaseId, boolean primary) {
    if (global().traceFullHeap()) {
      if (phaseId == GenImmix.PREPARE) {
        super.collectionPhase(phaseId, primary);
        if (global().gcFullHeap) mature.prepare();
        return;
      }

      if (phaseId == GenImmix.RELEASE) {
        if (global().gcFullHeap) mature.release();
        super.collectionPhase(phaseId, primary);
        return;
      }
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as a <code>GenImmix</code> instance. */
  @Inline
  private static GenImmix global() {
    return (GenImmix) VM.activePlan.global();
  }
}
