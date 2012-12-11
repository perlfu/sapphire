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
package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.plan.refcount.RCBase;
import org.mmtk.plan.refcount.RCBaseCollector;
import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.policy.ExplicitFreeListLocal;
import org.mmtk.policy.ExplicitFreeListSpace;
import org.mmtk.utility.ForwardingWord;
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
   * {@inheritDoc}
   */
  @Override
  public final void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == RCBase.PREPARE) {
      super.collectionPhase(phaseId, primary);
      rc.prepare();
      return;
    }

    if (phaseId == RCBase.CLOSURE) {
      super.collectionPhase(phaseId, primary);
      rc.flush();
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
   * {@inheritDoc}
   */
  @Override
  @Inline
  public final Address allocCopy(ObjectReference original, int bytes,
      int align, int offset, int allocator) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(allocator == GenRC.ALLOC_RC);
    }
    return rc.alloc(bytes, align, offset);
  }

  /**
   * {@inheritDoc}<p>
   *
   * In this case nothing is required.
   */
  @Override
  @Inline
  public final void postCopy(ObjectReference object, ObjectReference typeRef,
                             int bytes, int allocator) {
    ForwardingWord.clearForwardingBits(object);
    RCHeader.initializeHeader(object, false);
    RCHeader.makeUnlogged(object);
    ExplicitFreeListSpace.unsyncSetLiveBit(object);
  }

  @Override
  protected final TransitiveClosure getModifiedProcessor() {
    return modProcessor;
  }

  @Override
  protected final TraceLocal getRootTrace() {
    return rootTrace;
  }
}
