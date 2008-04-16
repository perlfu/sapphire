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
package org.mmtk.plan.stickyms;

import org.mmtk.plan.marksweep.MSConstraints;

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
public class StickyMSConstraints extends MSConstraints {
  /** @return The number of specialized scans.  We need nursery & full heap. */
  @Override
  public int numSpecializedScans() { return 2; }

  /** @return True if this plan requires a write barrier */
  public boolean needsWriteBarrier() { return true; }

  /** @return True if this Plan requires a header bit for object logging */
  public boolean needsLogBitInHeader() { return true; }

  /** @return A bit which represents that a header is unlogged */
  public Word unloggedBit() {return MarkSweepSpace.UNLOGGED_BIT; }
}
