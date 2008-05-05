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
package org.mmtk.plan.stickyimmix;

import org.mmtk.plan.*;
import org.mmtk.plan.immix.ImmixMutator;

import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior
 * and state for the <i>StickyImmix</i> plan, which implements a
 * generational mark-sweep collector.<p>
 *
 * Specifically, this class defines <i>MS</i> mutator-time allocation
 * and per-mutator thread collection semantics (flushing and restoring
 * per-mutator allocator state).<p>
 * *
 * @see StickyImmix
 * @see StickyImmixCollector
 * @see MutatorContext
 * @see SimplePhase#delegatePhase
 */
@Uninterruptible
public abstract class StickyImmixMutator extends ImmixMutator {

  /****************************************************************************
   * Instance fields
   */

  private ObjectReferenceDeque modBuffer;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public StickyImmixMutator() {
    super();
    modBuffer = new ObjectReferenceDeque("mod buf", global().modPool);
  }

  /****************************************************************************
   *
   * Barriers
   */

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
   * @param metaDataA A field used by the VM to create a correct store.
   * @param metaDataB A field used by the VM to create a correct store.
   * @param mode The mode of the store (eg putfield, putstatic etc)
   */
  @Inline
  public final void writeBarrier(ObjectReference src, Address slot,
      ObjectReference tgt, Offset metaDataA, int metaDataB, int mode) {
    if (Plan.logRequired(src))
      logSource(src);
    VM.barriers.performWriteInBarrier(src, slot, tgt, metaDataA, metaDataB, mode);
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
   * @param src The source of the values to copied
   * @param srcOffset The offset of the first source address, in
   * bytes, relative to <code>src</code> (in principle, this could be
   * negative).
   * @param dst The mutated object, i.e. the destination of the copy.
   * @param dstOffset The offset of the first destination address, in
   * bytes relative to <code>tgt</code> (in principle, this could be
   * negative).
   * @param bytes The size of the region being copied, in bytes.
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  @Inline
  public final boolean writeBarrier(ObjectReference src, Offset srcOffset,
      ObjectReference dst, Offset dstOffset, int bytes) {
    if (Plan.logRequired(src))
      logSource(src);
    return false;
  }

  /**
   * Add an object to the modified objects buffer and mark the
   * object has having been logged.  Since duplicate entries do
   * not raise any correctness issues, we do <i>not</i> worry
   * about synchronization and allow threads to race to log the
   * object, potentially including it twice (unlike reference
   * counting where duplicates would lead to incorrect reference
   * counts).
   *
   * @param src The object to be logged
   */
  private void logSource(ObjectReference src) {
   Plan.markAsLogged(src);
   modBuffer.push(src);
  }

  /**
   * Flush per-mutator remembered sets into the global remset pool.
   */
  public final void flushRememberedSets() {
    modBuffer.flushLocal();
    assertRemsetFlushed();
  }

  /**
   * Assert that the remsets have been flushed.  This is critical to
   * correctness.  We need to maintain the invariant that remset entries
   * do not accrue during GC.  If the host JVM generates barrier entires
   * it is its own responsibility to ensure that they are flushed before
   * returning to MMTk.
   */
  public final void assertRemsetFlushed() {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(modBuffer.isFlushed());
    }
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
  public final void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == StickyImmix.PREPARE) {
      flushRememberedSets();
    }
    if (phaseId == StickyImmix.RELEASE) {
      assertRemsetFlushed();
    }

    if (!global().collectWholeHeap) {
      if (phaseId == StickyImmix.PREPARE) {
        if (StickyImmix.NURSERY_COLLECT_PLOS)
          plos.prepare(false);
        immix.prepare();
        return;
      }

      if (phaseId == StickyImmix.RELEASE) {
        immix.release();
        if (StickyImmix.NURSERY_COLLECT_PLOS)
          plos.release(false);
        return;
      }
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>MSGen</code> instance. */
  @Inline
  private static StickyImmix global() {
    return (StickyImmix) VM.activePlan.global();
  }
}
