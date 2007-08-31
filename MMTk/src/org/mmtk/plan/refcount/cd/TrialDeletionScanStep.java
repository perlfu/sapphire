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
package org.mmtk.plan.refcount.cd;

import org.mmtk.plan.TransitiveClosure;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This trace step is used during trial deletion processing.
 */
@Uninterruptible
public final class TrialDeletionScanStep extends TransitiveClosure {

  /**
   * Trace an edge during GC.
   *
   * @param source The source of the reference.
   * @param slot The location containing the object reference.
   */
  @Inline
  public void processEdge(ObjectReference source, Address slot) {
    ObjectReference object = slot.loadObjectReference();
    ((TrialDeletionCollector)CDCollector.current()).enumerateScan(object);
  }
}
