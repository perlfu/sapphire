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
package org.mmtk.plan.generational.marksweep;

import org.mmtk.plan.generational.Gen;
import org.mmtk.plan.Trace;
import org.mmtk.policy.MarkSweepSpace;
import org.mmtk.policy.Space;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the functionality of a two-generation copying
 * collector where <b>the higher generation is a mark-sweep space</b>
 * (free list allocation, mark-sweep collection).  Nursery collections
 * occur when either the heap is full or the nursery is full.  The
 * nursery size is determined by an optional command line argument.
 * If undefined, the nursery size is "infinite", so nursery
 * collections only occur when the heap is full (this is known as a
 * flexible-sized nursery collector).  Thus both fixed and flexible
 * nursery sizes are supported.  Full heap collections occur when the
 * nursery size has dropped to a statically defined threshold,
 * <code>NURSERY_THRESHOLD</code><p>
 *
 * See the Jones & Lins GC book, chapter 7 for a detailed discussion
 * of generational collection and section 7.3 for an overview of the
 * flexible nursery behavior ("The Standard ML of New Jersey
 * collector"), or go to Appel's paper "Simple generational garbage
 * collection and fast allocation." SP&E 19(2):171--183, 1989.<p>
 *
 *
 * For general comments about the global/local distinction among classes refer
 * to Plan.java and PlanLocal.java.
 */
@Uninterruptible
public class GenMS extends Gen {

  /*****************************************************************************
   *
   * Class fields
   */

  /** The mature space, which for GenMS uses a mark sweep collection policy. */
  public static final MarkSweepSpace msSpace = USE_DISCONTIGUOUS_SPACES ? new MarkSweepSpace("ms", DEFAULT_POLL_FREQUENCY) : new MarkSweepSpace("ms", DEFAULT_POLL_FREQUENCY, MATURE_FRACTION);

  public static final int MS = msSpace.getDescriptor();

  /****************************************************************************
   *
   * Instance fields
   */

  /* The trace class for a full-heap collection */
  public final Trace matureTrace = new Trace(metaDataSpace);

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a (global) collection phase.
   */
  @Inline
  @Override
  public final void collectionPhase(short phaseId) {
    if (traceFullHeap()) {
      if (phaseId == PREPARE) {
        super.collectionPhase(phaseId);
        matureTrace.prepare();
        msSpace.prepare();
        return;
      }

      if (phaseId == CLOSURE) {
        matureTrace.prepare();
        return;
      }
      if (phaseId == RELEASE) {
        matureTrace.release();
        msSpace.release();
        super.collectionPhase(phaseId);
        return;
      }
    }
    super.collectionPhase(phaseId);
  }

  /*****************************************************************************
   *
   * Accounting
   */

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  @Inline
  @Override
  public int getPagesUsed() {
    return msSpace.reservedPages() + super.getPagesUsed();
  }

  /**
   * Calculate the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   *
   * @return the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   */
  public int getPagesRequired() {
    return super.getPagesRequired() + msSpace.requiredPages();
  }


  /*****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * Accessor method to allow the generic generational code in Gen.java
   * to access the mature space.
   *
   * @return The active mature space
   */
  @Inline
  protected final Space activeMatureSpace() {
    return msSpace;
  }

  /**
   * @see org.mmtk.plan.Plan#willNeverMove
   *
   * @param object Object in question
   * @return True if the object will never move
   */
  @Override
  public boolean willNeverMove(ObjectReference object) {
    if (Space.isInSpace(MS, object))
      return true;
    return super.willNeverMove(object);
  }
}
