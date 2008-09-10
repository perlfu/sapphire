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
package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.plan.refcount.RCBase;
import org.mmtk.plan.refcount.RCBaseCollector;
import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.ExplicitFreeListLocal;
import org.mmtk.policy.ExplicitFreeListSpace;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements the collector context for a simple reference counting
 * collector.
 */
@Uninterruptible
public class GenRCCollector extends RCBaseCollector {
  private final GenRCFindRootSetTraceLocal rootTrace;
  private final GenRCModifiedProcessor modProcessor;
  private final ExplicitFreeListLocal rc;

  public GenRCCollector() {
    rc = new ExplicitFreeListLocal(GenRC.rcSpace);
    rootTrace = new GenRCFindRootSetTraceLocal(global().rootTrace, newRootBuffer);
    modProcessor = new GenRCModifiedProcessor(rootTrace);
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
  public final void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == RCBase.PREPARE) {
      super.collectionPhase(phaseId, primary);
      rc.prepare();
      return;
    }

    if (phaseId == RCBase.RELEASE) {
      rc.release();
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
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
   * @param align The requested alignment
   * @param offset The alignment offset
   * @return The address of the first byte of the allocated region
   */
  @Inline
  public final Address allocCopy(ObjectReference original, int bytes,
      int align, int offset, int allocator) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(allocator == GenRC.ALLOC_RC);
    }
    return rc.alloc(bytes, align, offset);
  }

  /**
   * Perform any post-copy actions.  In this case nothing is required.
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  @Inline
  public final void postCopy(ObjectReference object, ObjectReference typeRef,
                             int bytes, int allocator) {
    CopySpace.clearGCBits(object);
    RCHeader.initializeHeader(object, false);
    RCHeader.makeUnlogged(object);
    ExplicitFreeListSpace.unsyncSetLiveBit(object);
  }

  /**
   * Get the modified processor to use.
   */
  protected final TransitiveClosure getModifiedProcessor() {
    return modProcessor;
  }

  /**
   * Get the root trace to use.
   */
  protected final TraceLocal getRootTrace() {
    return rootTrace;
  }
}
