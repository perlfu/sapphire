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
import org.mmtk.policy.LargeObjectLocal;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-collector thread</i> behavior
 * and state for the <i>CopyMS</i> plan.<p>
 *
 * Specifically, this class defines <i>CopyMS</i>
 * collection behavior (through <code>trace</code> and
 * the <code>collectionPhase</code> method), and
 * collection-time allocation into the mature space.
 *
 * @see CopyMS
 * @see CopyMSMutator
 * @see StopTheWorldCollector
 * @see CollectorContext
 */
@Uninterruptible
public class CopyMSCollector extends StopTheWorldCollector {

  /****************************************************************************
   * Instance fields
   */

  private MarkSweepLocal mature;
  private CopyMSTraceLocal trace;

  protected final LargeObjectLocal los;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Create a new (local) instance.
   */
  public CopyMSCollector() {
    los = new LargeObjectLocal(Plan.loSpace);
    mature = new MarkSweepLocal(CopyMS.msSpace);
    trace = new CopyMSTraceLocal(global().trace);
 }

  /****************************************************************************
   *
   * Collection-time allocation
   */

  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   *
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @return The address of the first byte of the allocated region
   */
  @Inline
  public final Address allocCopy(ObjectReference original, int bytes,
      int align, int offset, int allocator) {
    if (allocator == Plan.ALLOC_LOS) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Allocator.getMaximumAlignedSize(bytes, align) > Plan.LOS_SIZE_THRESHOLD);
      return los.alloc(bytes, align, offset);
    } else {
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(bytes <= Plan.LOS_SIZE_THRESHOLD);
        VM.assertions._assert(allocator == CopyMS.ALLOC_MS);
      }
      return mature.alloc(bytes, align, offset);
    }
  }

  /**
   * Perform any post-copy actions.
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  @Inline
  public final void postCopy(ObjectReference object, ObjectReference typeRef,
      int bytes, int allocator) {
    if (allocator == Plan.ALLOC_LOS)
      Plan.loSpace.initializeHeader(object, false);
    else
      CopyMS.msSpace.postCopy(object, true);
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-collector collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary Use this thread for single-threaded local activities.
   */
  @Inline
  public final void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == CopyMS.PREPARE) {
      super.collectionPhase(phaseId, primary);
      mature.prepare();
      trace.prepare();
      return;
    }

    if (phaseId == CopyMS.CLOSURE) {
      trace.completeTrace();
      return;
    }

    if (phaseId == CopyMS.RELEASE) {
      mature.release();
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

  /** @return the active global plan as an <code>MS</code> instance. */
  @Inline
  private static CopyMS global() {
    return (CopyMS) VM.activePlan.global();
  }

  /** @return The current trace instance. */
  public final TraceLocal getCurrentTrace() { return trace; }

}
