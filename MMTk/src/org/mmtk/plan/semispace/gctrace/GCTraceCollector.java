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
package org.mmtk.plan.semispace.gctrace;

import org.mmtk.plan.*;
import org.mmtk.plan.semispace.*;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;


/**
 * This class implements <i>per-collector thread</i> behavior and state for the
 * <i>GCTrace</i> plan, which implements a GC tracing algorithm.<p>
 *
 * Specifically, this class defines <i>SS</i> collection behavior
 * (through <code>inducedTrace</code> and the <code>collectionPhase</code>
 * method), and collection-time allocation (copying of objects).<p>
 *
 * See {@link GCTrace} for an overview of the GC trace algorithm.<p>
 *
 * @see SSCollector
 * @see GCTrace
 * @see GCTraceMutator
 * @see org.mmtk.plan.StopTheWorldCollector
 * @see org.mmtk.plan.CollectorContext
 */
@Uninterruptible public class GCTraceCollector extends SSCollector {
  /****************************************************************************
   * Instance fields
   */
  protected final GCTraceTraceLocal inducedTrace;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public GCTraceCollector() {
    inducedTrace = new GCTraceTraceLocal(global().ssTrace);
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-collector collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary perform any single-threaded local activities.
   */
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == GCTrace.CLOSURE) {
      inducedTrace.completeTrace();
      return;
    }

    if (phaseId == GCTrace.RELEASE) {
      inducedTrace.release();
      if (!GCTrace.traceInducedGC) {
        super.collectionPhase(phaseId, primary);
      }
      return;
    }

    /* fall through case */
    if (!GCTrace.traceInducedGC ||
        ((phaseId != StopTheWorld.SOFT_REFS) &&
         (phaseId != StopTheWorld.WEAK_REFS) &&
         (phaseId != StopTheWorld.PHANTOM_REFS) &&
         (phaseId != StopTheWorld.FINALIZABLE) &&
         (phaseId != SS.PREPARE))) {
      // Delegate up.
      super.collectionPhase(phaseId, primary);
    }
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as a <code>GCTrace</code> instance. */
  @Inline
  private static GCTrace global() {
    return (GCTrace) VM.activePlan.global();
  }

  /** @return The current trace instance */
  public TraceLocal getCurrentTrace() {
    return inducedTrace;
  }
}
