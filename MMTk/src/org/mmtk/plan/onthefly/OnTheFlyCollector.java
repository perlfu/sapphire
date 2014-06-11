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
package org.mmtk.plan.onthefly;

import org.mmtk.plan.Plan;
import org.mmtk.plan.Simple;
import org.mmtk.plan.StopTheWorldCollector;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior
 * and state for a concurrent collector.
 */
@Uninterruptible
public abstract class OnTheFlyCollector extends StopTheWorldCollector {

  /****************************************************************************
   * Instance fields
   */

  /****************************************************************************
   * Initialization
   */

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-collector collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == OnTheFly.OTF_STACK_ROOTS) {
      VM.scanning.computeThreadRootsOnTheFly(getCurrentTrace());
      return;
    }

    if (phaseId == OnTheFly.FLUSH_COLLECTOR) {
      getCurrentTrace().processRoots();
      getCurrentTrace().flush();
      return;
    }

    if (phaseId == OnTheFly.OTF_ROOTS) {
      VM.scanning.onTheFlyComputeGlobalRoots(getCurrentTrace());
      VM.scanning.onTheFlyComputeStaticRoots(getCurrentTrace());
      if (Plan.SCAN_BOOT_IMAGE) {
        VM.scanning.onTheFlyComputeBootImageRoots(getCurrentTrace());
      }
      return;
    }

    if (phaseId == OnTheFly.OTF_PROCESS_SOFT_REFS) {
      if (primary) {
        if (!Options.noReferenceTypes.getValue()) {
          if (global().retainSoftReferences())
            VM.softReferences.scan(getCurrentTrace(),global().isCurrentGCNursery(), true);
        }
      }
      return;
    }

    if (phaseId == OnTheFly.OTF_PROCESS_WEAK_REFS) {
      /* Process soft refs as if they are weak refs. */
      if (primary) {
        if (Options.noReferenceTypes.getValue()) {
          VM.softReferences.clear();
        } else {
          VM.softReferences.scan(getCurrentTrace(),global().isCurrentGCNursery(), false);
        }
      }
      /* Process weak refs */
      super.collectionPhase(Simple.WEAK_REFS, primary);
      return;
    }

    if (phaseId == OnTheFly.TRIGGER_FINALIZE) {
      VM.finalizableProcessor.triggerFinalize();
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   *
   * Miscellaneous.
   */

  /** @return The active global plan as a <code>Concurrent</code> instance. */
  @Inline
  private static OnTheFly global() {
    return (OnTheFly) VM.activePlan.global();
  }
}
