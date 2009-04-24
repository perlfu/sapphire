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
import org.mmtk.policy.MarkSweepSpace;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Word;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 */
@Uninterruptible
public class GenConstraints extends StopTheWorldConstraints {

  /** @return True if this plan is generational. */
  @Override
  public boolean generational() { return true; }

  /** @return True if this plan moves objects. */
  @Override
  public boolean movesObjects() { return true; }

  /** @return The number of header bits that are required. */
  @Override
  public int gcHeaderBits() { return CopySpace.LOCAL_GC_BITS_REQUIRED; }

  /** @return The number of header words that are required. */
  @Override
  public int gcHeaderWords() { return CopySpace.GC_HEADER_WORDS_REQUIRED; }

  /** @return True if this plan requires a write barrier */
  @Override
  public boolean needsWriteBarrier() { return true; }

  /** @return True if this plan requires a static barrier */
  @Override
  public boolean needsStaticWriteBarrier() { return Gen.USE_STATIC_WRITE_BARRIER; }

  /** @return The specialized scan methods required */
  @Override
  public int numSpecializedScans() { return 2; }

  /** @return True if this Plan requires a header bit for object logging */
  @Override
  public boolean needsLogBitInHeader() { return Gen.USE_OBJECT_BARRIER; }

  /** @return A bit which represents that a header is unlogged */
  @Override
  public Word unloggedBit() {return MarkSweepSpace.UNLOGGED_BIT; }

  /** @return The maximum size of an object that may be allocated directly into the nursery */
  @Override
  public int maxNonLOSDefaultAllocBytes() { return Gen.MAX_NURSERY_ALLOC_BYTES; }

}
