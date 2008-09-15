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
package org.mmtk.plan;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Word;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 */
@Uninterruptible public abstract class PlanConstraints {
  /** @return True if this Plan requires write barriers. */
  public boolean needsWriteBarrier() { return false; }

  /** @return True of this Plan requires read barriers on reference types. */
  public boolean needsReferenceTypeReadBarrier() { return false; }

  /** @return True of this Plan requires read barriers. */
  public boolean needsReadBarrier() { return false; }

  /** @return True if this Plan requires static write barriers. */
  public boolean needsStaticWriteBarrier() { return false;}

  /** @return True if this Plan requires static read barriers. */
  public boolean needsStaticReadBarrier() { return false; }

  /** @return True if this Plan requires linear scanning. */
  public boolean needsLinearScan() { return org.mmtk.utility.Constants.SUPPORT_CARD_SCANNING;}

  /** @return True if this Plan does not support parallel collection. */
  public boolean noParallelGC() { return false;}

  /** @return True if this Plan moves objects. */
  public boolean movesObjects() { return false;}

  /** @return True if this Plan *must* use an LOS (for example it has a size-constrained primary allocator) */
  public boolean requiresLOS() { return false;}

  /** @return True if this object forwards objects <i>after</i>
   * determining global object liveness (e.g. many compacting collectors). */
  public boolean needsForwardAfterLiveness() { return false;}

  /** @return Is this plan generational in nature. */
  public boolean generational() { return false;}

  /** @return The number of header bits that are required. */
  public abstract int gcHeaderBits();

  /** @return The number of header words that are required. */
  public abstract int gcHeaderWords();

  /** @return True if this plan contains GCspy. */
  public boolean withGCspy() { return false; }

  /** @return True if this plan contains GCTrace. */
  public boolean generateGCTrace() { return false; }

  /** @return The specialized scan methods required */
  public int numSpecializedScans() { return 0; }

  /** @return True if this plan requires concurrent worker threads */
  public boolean needsConcurrentWorkers() { return false; }

  /** @return True if this Plan requires a header bit for object logging */
  public boolean needsLogBitInHeader() { return false; }

  /** @return A bit which represents that a header is unlogged */
  public Word unloggedBit() {return Word.zero(); }

  /** @return A bit which represents that a header is unlogged */
  public Word logSetBitMask() {return Word.zero(); }
}
