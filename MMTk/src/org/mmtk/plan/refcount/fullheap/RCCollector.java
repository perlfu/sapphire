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
package org.mmtk.plan.refcount.fullheap;

import org.mmtk.plan.*;
import org.mmtk.plan.refcount.RCBaseCollector;
import org.mmtk.utility.Constants;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior
 * and state for the <i>RC</i> plan, which implements a full-heap
 * reference counting collector.<p>
 *
 * Specifically, this class defines <i>RC</i> collection behavior
 * (through <code>trace</code> and the <code>collectionPhase</code>
 * method).<p>
 *
 * @see RC for an overview of the reference counting algorithm.<p>
 *
 * FIXME The SegregatedFreeList class (and its decendents such as
 * MarkSweepLocal) does not properly separate mutator and collector
 * behaviors, so the ms field below should really not exist in
 * this class as there is no collection-time allocation in this
 * collector.
 *
 * @see RC
 * @see RCMutator
 * @see StopTheWorldCollector
 * @see CollectorContext
 */
@Uninterruptible public abstract class RCCollector extends RCBaseCollector
  implements Constants {
  /****************************************************************************
   * Instance fields
   */
  public final RCTraceLocal trace;
  public final RCModifiedProcessor modProcessor;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public RCCollector() {
    trace = new RCTraceLocal(global().rcTrace);
    // We use the modified object processor for full heap RC
    modProcessor = new RCModifiedProcessor();
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>RC</code> instance. */
  @Inline
  private static RC global() {
    return (RC) VM.activePlan.global();
  }

  /** @return The current trace instance. */
  public final TraceLocal getCurrentTrace() {
    return trace;
  }

  /** @return The current modified object processor. */
  public final TransitiveClosure getModifiedProcessor() {
    return modProcessor;
  }
}
