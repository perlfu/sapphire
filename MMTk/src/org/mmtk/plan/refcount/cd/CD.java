/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.plan.refcount.cd;

import org.mmtk.plan.refcount.RCBase;
import org.mmtk.utility.options.CycleFilterThreshold;
import org.mmtk.utility.options.CycleMetaDataLimit;
import org.mmtk.utility.options.CycleTriggerThreshold;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.statistics.Stats;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>global</i> behavior 
 * and state for a cycle detector.
 *  
 * $Id: MS.java,v 1.4 2006/06/21 07:38:15 steveb-oss Exp $
 * 
 * @author Daniel Frampton
 * @version $Revision: 1.4 $
 * @date $Date: 2006/06/21 07:38:15 $
 */
public abstract class CD implements Uninterruptible {

  /****************************************************************************
   * Constants
   */

  /****************************************************************************
   * Class variables
   */

  /****************************************************************************
   * Instance variables
   */

  /**
   * Constructor.
   * 
   */
  public CD() {
    Options.cycleFilterThreshold = new CycleFilterThreshold();
    Options.cycleTriggerThreshold = new CycleTriggerThreshold();
    Options.cycleMetaDataLimit = new CycleMetaDataLimit();
  }

  /*****************************************************************************
   * 
   * Collection
   */

  /**
   * Decide whether cycle collection should be invoked.  This uses
   * a probabalisitic heuristic based on heap fullness.
   * 
   * @return True if cycle collection should be invoked
   */
  protected final boolean shouldCollectCycles() {
    return shouldAct(Options.cycleTriggerThreshold.getPages());
  }

  /**
   * Decide whether the purple buffer should be filtered.  This will
   * happen if the heap is close to full or if the number of purple
   * objects enqued has reached a user-defined threashold.
   * 
   * @return True if the unfiltered purple buffer should be filtered
   */
  protected final boolean shouldFilterPurple() {
    return shouldAct(Options.cycleFilterThreshold.getPages());
  }

  /**
   * Decide whether to act on cycle collection or purple filtering.
   * This uses a probabalisitic heuristic based on heap fullness.
   * 
   * @return True if we should act
   */
  private final boolean shouldAct(int thresholdPages) {
    if (RCBase.FORCE_FULL_CD) return true;
    final int LOG_WRIGGLE = 2;
    int slack = log2((int) VM.activePlan.global().getPagesAvail() / thresholdPages);
    int mask = (1 << slack) - 1;
    boolean rtn = (slack <= LOG_WRIGGLE) && ((Stats.gcCount() & mask) == mask);
    return rtn;
  }
  
  private final int log2(int value) {
    int rtn = 0;
    while (value > 1<<rtn) rtn++;
    return rtn;
  }

  /**
   * Perform a (global) collection phase.
   * 
   * @param phaseId Collection phase to execute.
   */
  public boolean collectionPhase(int phaseId) throws InlinePragma {
    return false;
  }
  
  /**
   * Update the CD section of the RC word when an increment is performed
   * 
   * @param rcWord The refcount word after the increment.
   * @return The updated status after CD modification
   */
  public abstract int notifyIncRC(int rcWord);

  /**
   * If the reported decrement succeeds, should we buffer the object?
   * 
   * @param rcWord The refcount work post decrement.
   * @return True if DEC_BUFFER should be returned
   */
  public abstract boolean shouldBufferOnDecRC(int rcWord);

  /**
   * Allow a free of this object, or is it in a CD data structure
   * 
   * @param object The object to check
   * @return True if free is safe
   */
  public abstract boolean allowFree(ObjectReference object);
  
  /**
   * Update the header on a buffered dec to non-zero RC
   * 
   * @param rcWord The refcount work post decrement.
   * @return The updated status after CD modification
   */
  public abstract int updateHeaderOnBufferedDec(int rcWord);

  /**
   * Update the header on a non-buffered dec to non-zero RC
   * 
   * @param rcWord The refcount work post decrement.
   * @return The updated status after CD modification
   */
  public abstract int updateHeaderOnUnbufferedDec(int rcWord);
  
  /**
   * Perform any cycle detector header initialization.
   * 
   * @param typeRef Type information for the object.
   * @param rcWord The refcount work post decrement.
   * @return The updated status after CD modification
   */
  public abstract int initializeHeader(ObjectReference typeRef, int rcWord);

  /****************************************************************************
   * 
   * Miscellaneous
   */

  /** @return The active cycle detector global instance */
  public static final CD current() throws InlinePragma {
    return ((RCBase)VM.activePlan.global()).cycleDetector();
  }
}
