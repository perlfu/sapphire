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
package org.mmtk.plan.refcount.backuptrace;

import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.utility.alloc.LinearScan;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class is used in backup tracing to sweep and zero any dead 'immortal' objects.
 */
@Uninterruptible
public final class BTSweepImmortalScanner extends LinearScan {

  private final BTDecMarkedAndZero sdmAZ = new BTDecMarkedAndZero();

  /**
   * Scan an object during sweep.
   *
   * @param object The source of the reference.
   */
  @Inline
  public void scan(ObjectReference object) {
    if (!RCHeader.isMarked(object)) {
      VM.scanning.scanObject(sdmAZ, object);
    } else {
      RCHeader.clearMarked(object);
    }
  }
}
