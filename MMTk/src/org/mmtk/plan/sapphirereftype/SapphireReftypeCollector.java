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
package org.mmtk.plan.sapphirereftype;

import org.mmtk.plan.onthefly.OnTheFly;
import org.mmtk.plan.otfsapphire.OTFSapphire;
import org.mmtk.plan.otfsapphire.OTFSapphireFlipTraceLocal;
import org.mmtk.plan.otfsapphire.OTFSapphireVerifyTraceLocal;
import org.mmtk.utility.Log;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.*;

@Uninterruptible
public class SapphireReftypeCollector extends org.mmtk.plan.otfsapphire.OTFSapphireCollector {
  /**
   * Constructor
   */
  public SapphireReftypeCollector() {
    super(new SapphireReftypeAllocTraceLocal(global().allocTrace),
         new OTFSapphireFlipTraceLocal(global().flipRootTrace),
         OTFSapphire.VERIFY_FLIP ? new OTFSapphireVerifyTraceLocal(global().verifyTrace) : null,
         OTFSapphire.metaDataSpace);
  }

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
    if (phaseId == OnTheFly.OTF_PROCESS_WEAK_REFS) {
      /* Process soft refs as if they are weak refs. */
      if (SapphireReftype.REFERENCE_REPORT_NUM_REFS) {
      	if (primary) {
    			Log.write("refs-before-process soft: ");
    			Log.write(VM.softReferences.countWaitingReferences());
    			Log.write(" weak: ");
    			Log.write(VM.weakReferences.countWaitingReferences());
    			Log.writeln(VM.weakReferences.countWaitingReferences());
      	}
      }
      super.collectionPhase(phaseId, primary);
      if (SapphireReftype.REFERENCE_REPORT_NUM_REFS) {
      	if (primary) {
    			Log.write("refs-after-process  soft: ");
    			Log.write(VM.softReferences.countWaitingReferences());
    			Log.write(" weak: ");
    			Log.write(VM.weakReferences.countWaitingReferences());
    			Log.writeln(VM.weakReferences.countWaitingReferences());
      	}
      }
      return;
    }
    super.collectionPhase(phaseId, primary);
  }
  /****************************************************************************
  *
  * Miscellaneous
  */

 /** @return The active global plan as an <code>SS</code> instance. */
 @Inline
 private static SapphireReftype global() {
   return (SapphireReftype) VM.activePlan.global();
 }
}
