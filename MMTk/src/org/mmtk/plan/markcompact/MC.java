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
package org.mmtk.plan.markcompact;

import org.mmtk.plan.*;
import org.mmtk.policy.MarkCompactSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements the global state of a simple sliding mark-compact
 * collector.
 *
 * FIXME Need algorithmic overview and references.
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities, and divides global and local state
 * into separate class hierarchies.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  There is a single instance of Plan (or the
 * appropriate sub-class), and a 1:1 mapping of PlanLocal to "kernel
 * threads" (aka CPUs or in Jikes RVM, VM_Processors).  Thus instance
 * methods of PlanLocal allow fast, unsychronized access to functions such as
 * allocation and collection.
 *
 * The global instance defines and manages static resources
 * (such as memory and virtual memory resources).  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance properties of MMTk plans.
 */
@Uninterruptible public class MC extends StopTheWorld {

  /****************************************************************************
   * Class variables
   */

  public static final MarkCompactSpace mcSpace
    = new MarkCompactSpace("mc", DEFAULT_POLL_FREQUENCY, (float) 0.6);
  public static final int MARK_COMPACT = mcSpace.getDescriptor();

  /* Phases */
  public static final int PREPARE_FORWARD     = new SimplePhase("fw-prepare", Phase.GLOBAL_FIRST  ).getId();
  public static final int FORWARD_CLOSURE     = new SimplePhase("fw-closure", Phase.COLLECTOR_ONLY).getId();
  public static final int RELEASE_FORWARD     = new SimplePhase("fw-release", Phase.GLOBAL_LAST   ).getId();

  /* FIXME these two phases need to be made per-collector phases */
  public static final int CALCULATE_FP        = new SimplePhase("calc-fp",    Phase.MUTATOR_ONLY  ).getId();
  public static final int COMPACT             = new SimplePhase("compact",    Phase.MUTATOR_ONLY  ).getId();

  /**
   * This is the phase that is executed to perform a mark-compact collection.
   *
   * FIXME: Far too much duplication and inside knowledge of StopTheWorld
   */
  public ComplexPhase mcCollection = new ComplexPhase("collection", null, new int[] {
      initPhase,
      rootClosurePhase,
      refTypeClosurePhase,
      completeClosurePhase,
      CALCULATE_FP,
      PREPARE_FORWARD,
      PREPARE_MUTATOR,
      BOOTIMAGE_ROOTS,
      ROOTS,
      forwardPhase,
      FORWARD_CLOSURE,
      RELEASE_MUTATOR,
      RELEASE_FORWARD,
      COMPACT,
      finishPhase});

  /****************************************************************************
   * Instance variables
   */

  public final Trace markTrace;
  public final Trace forwardTrace;

  /**
   * Constructor.
 */
  public MC() {
    markTrace = new Trace(metaDataSpace);
    forwardTrace = new Trace(metaDataSpace);
    collection = mcCollection;
  }

  /*****************************************************************************
   *
   * Collection
   */


  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase to execute.
   */
  @Inline
  public final void collectionPhase(int phaseId) {
    if (phaseId == PREPARE) {
      super.collectionPhase(phaseId);
      markTrace.prepare();
      mcSpace.prepare();
      return;
    }
    if (phaseId == RELEASE) {
      markTrace.release();
      mcSpace.release();
      super.collectionPhase(phaseId);
      return;
    }

    if (phaseId == PREPARE_FORWARD) {
      super.collectionPhase(PREPARE);
      forwardTrace.prepare();
      mcSpace.prepare();
      return;
    }
    if (phaseId == RELEASE_FORWARD) {
      forwardTrace.release();
      mcSpace.release();
      super.collectionPhase(RELEASE);
      return;
    }

    super.collectionPhase(phaseId);
  }

  /*****************************************************************************
   *
   * Accounting
   */

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  The superclass accounts for its spaces, we just
   * augment this with the mark-sweep space's contribution.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public int getPagesUsed() {
    return (mcSpace.reservedPages() + super.getPagesUsed());
  }
  
  /**
   * Calculate the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   * 
   * @return the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   */
  public int getPagesRequired() {
    return super.getPagesRequired() + mcSpace.requiredPages();
  }

  /**
   * @see org.mmtk.plan.Plan#objectCanMove
   *
   * @param object Object in question
   * @return False if the object will never move
   */
  @Override
  public boolean objectCanMove(ObjectReference object) {
    if (Space.isInSpace(MARK_COMPACT, object))
      return true;
    return super.objectCanMove(object);
  }
}
