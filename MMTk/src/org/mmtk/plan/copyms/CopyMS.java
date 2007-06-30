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
package org.mmtk.plan.copyms;

import org.mmtk.plan.*;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.MarkSweepSpace;
import org.mmtk.utility.options.Options;

import org.vmmagic.pragma.*;

/**
 * This class implements the global state of a full-heap collector
 * with a copying nursery and mark-sweep mature space.  Unlike a full
 * generational collector, there is no write barrier, no remembered set, and
 * every collection is full-heap.
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
@Uninterruptible public class CopyMS extends StopTheWorld {

  /****************************************************************************
   * Constants
   */

  /****************************************************************************
   * Class variables
   */
  public static final CopySpace nurserySpace = new CopySpace("nursery", DEFAULT_POLL_FREQUENCY, (float) 0.15, true, false);
  public static final MarkSweepSpace msSpace = new MarkSweepSpace("ms", DEFAULT_POLL_FREQUENCY, (float) 0.5);

  public static final int NURSERY = nurserySpace.getDescriptor();
  public static final int MARK_SWEEP = msSpace.getDescriptor();

  public static final int ALLOC_NURSERY = ALLOC_DEFAULT;
  public static final int ALLOC_MS = StopTheWorld.ALLOCATORS + 1;


  /****************************************************************************
   * Instance variables
   */

  public final Trace trace;

  /**
   * Constructor.
 */
  public CopyMS() {
    trace = new Trace(metaDataSpace);
  }

  /**
   * Boot-time initialization
   */
  @Interruptible
  public void boot() {
    super.boot();
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
      trace.prepare();
      msSpace.prepare();
      nurserySpace.prepare(true);
      return;
    }
    if (phaseId == RELEASE) {
      trace.release();
      msSpace.release();
      nurserySpace.release();
      super.collectionPhase(phaseId);
      return;
    }

    super.collectionPhase(phaseId);
  }
  
  /**
   * This method controls the triggering of a GC. It is called periodically
   * during allocation. Returns true to trigger a collection.
   *
   * @param spaceFull Space request failed, must recover pages within 'space'.
   * @return True if a collection is requested by the plan.
   */
  public final boolean collectionRequired(boolean spaceFull) {
    boolean nurseryFull = nurserySpace.reservedPages() > Options.nurserySize.getMaxNursery();
    
    return super.collectionRequired(spaceFull) || nurseryFull; 
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
  public int getPagesUsed() {
    return super.getPagesUsed() +
      msSpace.reservedPages() +
      nurserySpace.reservedPages();
  }

  /**
   * Return the number of pages reserved for collection.
   * For mark sweep this is a fixed fraction of total pages.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for collection.
   */
  public int getCollectionReserve() {
    return nurserySpace.reservedPages() + super.getCollectionReserve();
  }

  /**
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the nursery</i>.
   */
  public final int getPagesAvail() {
    return (getTotalPages() - getPagesReserved()) >> 1;
  }

  /**
   * Calculate the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   * 
   * @return the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   */
  public int getPagesRequired() {
    return super.getPagesRequired() + msSpace.requiredPages() + 
      (nurserySpace.requiredPages() << 1);
  }
}
