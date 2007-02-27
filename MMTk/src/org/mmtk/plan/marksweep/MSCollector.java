/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.marksweep;

import org.mmtk.plan.*;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior 
 * and state for the <i>MS</i> plan, which implements a full-heap
 * mark-sweep collector.<p>
 * 
 * Specifically, this class defines <i>MS</i> collection behavior
 * (through <code>trace</code> and the <code>collectionPhase</code>
 * method).<p>
 * 
 * @see MS for an overview of the mark-sweep algorithm.<p>
 * 
 * FIXME The SegregatedFreeList class (and its decendents such as
 * MarkSweepLocal) does not properly separate mutator and collector
 * behaviors, so the ms field below should really not exist in
 * this class as there is no collection-time allocation in this
 * collector.
 * 
 * @see MS
 * @see MSMutator
 * @see StopTheWorldCollector
 * @see CollectorContext
 * @see SimplePhase#delegatePhase
 * 
 *
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 */
@Uninterruptible public abstract class MSCollector extends StopTheWorldCollector {

  /****************************************************************************
   * Instance fields
   */
  private MSTraceLocal trace;
  private MarkSweepLocal ms; // see FIXME at top of this class

  /****************************************************************************
   * Initialization
   */

  /**
   * Constructor
   */
  public MSCollector() {
    trace = new MSTraceLocal(global().msTrace);
    ms = new MarkSweepLocal(MS.msSpace);
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
  public final void collectionPhase(int phaseId, boolean primary) { 
    if (phaseId == MS.PREPARE) {
      super.collectionPhase(phaseId, primary);
      ms.prepare();
      trace.prepare();
      return;
    }

    if (phaseId == MS.START_CLOSURE) {
      trace.startTrace();
      return;
    }

    if (phaseId == MS.COMPLETE_CLOSURE) {
      trace.completeTrace();
      return;
    }

    if (phaseId == MS.RELEASE) {
      trace.release();
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   * 
   * Miscellaneous
   */

  /** @return The active global plan as an <code>MS</code> instance. */
  @Inline
  private static MS global() {
    return (MS) VM.activePlan.global();
  }

  /** @return The current trace instance. */
  public final TraceLocal getCurrentTrace() {
    return trace;
  }
}
