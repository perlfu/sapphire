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
package org.mmtk.plan.generational;

import org.mmtk.plan.StopTheWorldConstraints;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.Space;

import org.vmmagic.pragma.*;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 */
@Uninterruptible
public class GenConstraints extends StopTheWorldConstraints {

  @Override
  public boolean generational() { return true; }

  @Override
  public boolean movesObjects() { return true; }

  @Override
  public int gcHeaderBits() { return CopySpace.LOCAL_GC_BITS_REQUIRED; }

  @Override
  public int gcHeaderWords() { return CopySpace.GC_HEADER_WORDS_REQUIRED; }

  @Override
  public boolean needsObjectReferenceWriteBarrier() { return true; }

  @Override
  public boolean needsObjectReferenceNonHeapWriteBarrier() { return Gen.USE_NON_HEAP_OBJECT_REFERENCE_WRITE_BARRIER; }

  @Override
  public boolean objectReferenceBulkCopySupported() { return true; }

  @Override
  public int numSpecializedScans() { return 2; }

  @Override
  public boolean needsLogBitInHeader() { return Gen.USE_OBJECT_BARRIER; }

  /**
   * @return The maximum size of an object that may be allocated directly into the nursery
   */
  @Override
  public int maxNonLOSDefaultAllocBytes() {
    /*
     * If the nursery is discontiguous, the maximum object is essentially unbounded.  In
     * a contiguous nursery, we can't attempt to nursery-allocate objects larger than the
     * available nursery virtual memory.
     */
    return  Gen.USE_DISCONTIGUOUS_NURSERY ?
        org.mmtk.utility.Constants.MAX_INT :
        Space.getFracAvailable(Gen.NURSERY_VM_FRACTION).toInt();
  }

}
