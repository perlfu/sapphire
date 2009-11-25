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
package org.mmtk.plan.generational;

import org.mmtk.plan.*;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.HeaderByte;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.statistics.Stats;
import org.mmtk.vm.VM;
import static org.mmtk.plan.generational.Gen.USE_OBJECT_BARRIER_FOR_AASTORE;
import static org.mmtk.plan.generational.Gen.USE_OBJECT_BARRIER_FOR_PUTFIELD;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implements <i>per-mutator thread</i> behavior
 * and state for <i>generational copying collectors</i>.<p>
 *
 * Specifically, this class defines mutator-time allocation into the nursery;
 * write barrier semantics, and per-mutator thread collection semantics
 * (flushing and restoring per-mutator allocator and remset state).
 *
 * @see Gen
 * @see GenCollector
 * @see StopTheWorldMutator
 * @see MutatorContext
 */
@Uninterruptible public class GenMutator extends StopTheWorldMutator {

  /*****************************************************************************
   *
   * Instance fields
   */
  protected final CopyLocal nursery = new CopyLocal(Gen.nurserySpace);

  private final ObjectReferenceDeque modbuf;    /* remember modified scalars */
  protected final WriteBuffer remset;           /* remember modified array fields */
  protected final AddressPairDeque arrayRemset; /* remember modified array ranges */

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * Note that each mutator is a producer of remsets, while each
   * collector is a consumer.  The <code>GenCollector</code> class
   * is responsible for construction of the consumer.
   * @see GenCollector
   */
  public GenMutator() {
    modbuf = new ObjectReferenceDeque("modbuf", global().modbufPool);
    remset = new WriteBuffer(global().remsetPool);
    arrayRemset = new AddressPairDeque(global().arrayRemsetPool);
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
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == Gen.ALLOC_NURSERY) {
      if (Stats.GATHER_MARK_CONS_STATS) Gen.nurseryCons.inc(bytes);
      return nursery.alloc(bytes, align, offset);
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
  public void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {
    if (allocator != Gen.ALLOC_NURSERY) {
      super.postAlloc(ref, typeRef, bytes, allocator);
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
    if (space == Gen.nurserySpace) return nursery;
    return super.getAllocatorFromSpace(space);
  }

  /****************************************************************************
   *
   * Barriers
   */

  /**
   * Perform the write barrier fast path, which may involve remembering
   * a reference if necessary.
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param mode The mode of the store (eg putfield, putstatic etc)
   */
  @Inline
  private void fastPath(ObjectReference src, Address slot, ObjectReference tgt, int mode) {
    if (Gen.GATHER_WRITE_BARRIER_STATS) Gen.wbFast.inc();
    if ((mode == ARRAY_ELEMENT && USE_OBJECT_BARRIER_FOR_AASTORE) ||
        (mode == INSTANCE_FIELD && USE_OBJECT_BARRIER_FOR_PUTFIELD)) {
      if (HeaderByte.isUnlogged(src)) {
        if (Gen.GATHER_WRITE_BARRIER_STATS) Gen.wbSlow.inc();
        HeaderByte.markAsLogged(src);
        modbuf.insert(src);
      }
    } else {
      if (!Gen.inNursery(slot) && Gen.inNursery(tgt)) {
        if (Gen.GATHER_WRITE_BARRIER_STATS) Gen.wbSlow.inc();
        remset.insert(slot);
      }
    }
  }

  /**
   * A new reference is about to be created.  Take appropriate write
   * barrier actions.<p>
   *
   * In this case, we remember the address of the source of the
   * pointer if the new reference points into the nursery from
   * non-nursery space.
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The mode of the store (eg putfield, putstatic etc)
   */
  @Inline
  public final void objectReferenceWrite(ObjectReference src, Address slot,
      ObjectReference tgt, Word metaDataA,
      Word metaDataB, int mode) {
    fastPath(src, slot, tgt, mode);
    VM.barriers.objectReferenceWrite(src, tgt, metaDataA, metaDataB, mode);
  }


  /**
   * Perform the root write barrier fast path, which may involve remembering
   * a reference if necessary.
   *
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param mode The mode of the store (eg putfield, putstatic etc)
   */
  @Inline
  private void fastPath(Address slot, ObjectReference tgt) {
    if (Gen.GATHER_WRITE_BARRIER_STATS) Gen.wbFast.inc();
    if (Gen.inNursery(tgt)) {
      if (Gen.GATHER_WRITE_BARRIER_STATS) Gen.wbSlow.inc();
      remset.insert(slot);
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
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   */
  @Inline
  public final void objectReferenceNonHeapWrite(Address slot, ObjectReference tgt,
      Word metaDataA, Word metaDataB) {
    fastPath(slot, tgt);
    VM.barriers.objectReferenceNonHeapWrite(slot, tgt, metaDataA, metaDataB);
  }

  /**
   * Attempt to atomically exchange the value in the given slot
   * with the passed replacement value. If a new reference is
   * created, we must then take appropriate write barrier actions.<p>
   *
   * In this case, we remember the address of the source of the
   * pointer if the new reference points into the nursery from
   * non-nursery space.
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param old The old reference to be swapped out
   * @param tgt The target of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occured
   * @return True if the swap was successful.
   */
  @Inline
  public boolean objectReferenceTryCompareAndSwap(ObjectReference src, Address slot, ObjectReference old, ObjectReference tgt,
      Word metaDataA, Word metaDataB, int mode) {
    boolean result = VM.barriers.objectReferenceTryCompareAndSwap(src, old, tgt, metaDataA, metaDataB, mode);
    if (result)
      fastPath(src, slot, tgt, mode);
    return result;
  }

  /**
   * A number of references are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * In this case, we remember the mutated source address range and
   * will scan that address range at GC time.
   *
   * @param src The source of the values to be copied
   * @param srcIdx The starting source index
   * @param dst The mutated object, i.e. the destination of the copy.
   * @param srcIdx The starting source index
   * @param len The number of array elements to be copied
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  @Inline
  @Override
  public final boolean objectReferenceBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    if (!Gen.inNursery(dst)) {
      Address start = dst.toAddress().plus(dstOffset);
      arrayRemset.insert(start, start.plus(bytes));
    }
    return false;
  }

  /**
   * Flush per-mutator remembered sets into the global remset pool.
   */
  public final void flushRememberedSets() {
    modbuf.flushLocal();
    remset.flushLocal();
    arrayRemset.flushLocal();
    assertRemsetsFlushed();
  }

  /**
   * Assert that the remsets have been flushed.  This is critical to
   * correctness.  We need to maintain the invariant that remset entries
   * do not accrue during GC.  If the host JVM generates barrier entires
   * it is its own responsibility to ensure that they are flushed before
   * returning to MMTk.
   */
  public final void assertRemsetsFlushed() {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(modbuf.isFlushed());
      VM.assertions._assert(remset.isFlushed());
      VM.assertions._assert(arrayRemset.isFlushed());
    }
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-mutator collection phase.
   */
  @NoInline
  public void collectionPhase(short phaseId, boolean primary) {

    if (phaseId == Gen.PREPARE) {
      nursery.reset();
      if (global().traceFullHeap()) {
        super.collectionPhase(phaseId, primary);
        modbuf.flushLocal();
        remset.resetLocal();
        arrayRemset.resetLocal();
      } else {
        flushRememberedSets();
      }
      return;
    }

    if (phaseId == Gen.RELEASE) {
      if (global().traceFullHeap()) {
        super.collectionPhase(phaseId, primary);
      }
      assertRemsetsFlushed();
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as a <code>Gen</code> instance. */
  @Inline
  private static Gen global() {
    return (Gen) VM.activePlan.global();
  }
}
