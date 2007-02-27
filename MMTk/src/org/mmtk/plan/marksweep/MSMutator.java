/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.plan.marksweep;

import org.mmtk.plan.*;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.policy.Space;

import org.mmtk.utility.alloc.Allocator;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior 
 * and state for the <i>MS</i> plan, which implements a full-heap
 * mark-sweep collector.<p>
 * 
 * Specifically, this class defines <i>MS</i> mutator-time allocation
 * and per-mutator thread collection semantics (flushing and restoring
 * per-mutator allocator state).<p>
 * 
 * @see org.mmtk.plan.markcompact.MC for an overview of the mark-compact algorithm.<p>
 * 
 * FIXME The SegregatedFreeList class (and its decendents such as
 * MarkSweepLocal) does not properly separate mutator and collector
 * behaviors, so the ms field below should really not exist in
 * this class as there is no collection-time allocation in this
 * collector.
 * 
 * @see MS
 * @see MSCollector
 * @see StopTheWorldMutator
 * @see MutatorContext
 * @see SimplePhase#delegatePhase
 * 
 *
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 */
@Uninterruptible public abstract class MSMutator extends StopTheWorldMutator {

  /****************************************************************************
   * Instance fields
   */

  private MarkSweepLocal ms;

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   */
  public MSMutator() {
    ms = new MarkSweepLocal(MS.msSpace);
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
   * @return The low address of the allocated memory.
   */
  @Inline
  public Address alloc(int bytes, int align, int offset, int allocator, int site) { 
    if (allocator == MS.ALLOC_DEFAULT) {
      return ms.alloc(bytes, align, offset, false);
    }
    return super.alloc(bytes, align, offset, allocator, site);
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
    if (allocator == MS.ALLOC_DEFAULT)
      MS.msSpace.postAlloc(ref);
    else
      super.postAlloc(ref, typeRef, bytes, allocator);
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
    if (a == ms) return MS.msSpace;
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
    if (space == MS.msSpace) return ms;
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
  public final void collectionPhase(int phaseId, boolean primary) { 
    if (phaseId == MS.PREPARE_MUTATOR) {
      super.collectionPhase(phaseId, primary);
      ms.prepare();
      return;
    }

    if (phaseId == MS.RELEASE_MUTATOR) {
      ms.releaseCollector();
      ms.releaseMutator(); // FIXME see block comment at top of this class
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }
}
