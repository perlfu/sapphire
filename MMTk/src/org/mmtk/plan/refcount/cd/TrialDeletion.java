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

import org.mmtk.plan.ComplexPhase;
import org.mmtk.plan.Phase;
import org.mmtk.plan.SimplePhase;
import org.mmtk.plan.refcount.RCBase;
import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.utility.Log;
import org.mmtk.utility.deque.SharedDeque;
import org.mmtk.utility.options.Options;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the global state of a trial deletion cycle detector.

 */
@Uninterruptible public final class TrialDeletion extends CD {

  /****************************************************************************
   *
   * Class variables
   */
 // Collection phases
  public static final int CD_PREPARE_FILTER   = new SimplePhase("td.prepare-filter",     Phase.GLOBAL_ONLY     ).getId();
  public static final int CD_PREPARE_COLLECT  = new SimplePhase("td.prepare-collect",    Phase.GLOBAL_ONLY     ).getId();
  public static final int CD_FILTER_PURPLE    = new SimplePhase("td.filter-purple",      Phase.COLLECTOR_ONLY  ).getId();
  public static final int CD_FREE_FILTERED    = new SimplePhase("td.free-filtered",      Phase.COLLECTOR_ONLY  ).getId();
  public static final int CD_FILTER_MATURE    = new SimplePhase("td.filter-mature",      Phase.COLLECTOR_ONLY  ).getId();
  public static final int CD_MARK_GREY        = new SimplePhase("td.mark-grey",          Phase.COLLECTOR_ONLY  ).getId();
  public static final int CD_SCAN             = new SimplePhase("td.scan",               Phase.COLLECTOR_ONLY  ).getId();
  public static final int CD_COLLECT          = new SimplePhase("td.collect",            Phase.COLLECTOR_ONLY  ).getId();
  public static final int CD_FREE             = new SimplePhase("td.free",               Phase.COLLECTOR_ONLY  ).getId();
  public static final int CD_FLUSH_FILTERED   = new SimplePhase("td.flush-filtered",     Phase.COLLECTOR_ONLY  ).getId();
  public static final int CD_PROCESS_DECS     = new SimplePhase("td.process-decs",       Phase.COLLECTOR_ONLY  ).getId();
  public static final int CD_RELEASE          = new SimplePhase("td.release",            Phase.GLOBAL_ONLY     ).getId();

  /* Cycle detection */
  private static final int cdPhase = new ComplexPhase("trial deletion", new int[] {
      CD_PREPARE_FILTER,
      CD_FILTER_PURPLE,
      CD_FREE_FILTERED,
      CD_PREPARE_COLLECT,
      CD_FILTER_MATURE,
      CD_MARK_GREY,
      CD_SCAN,
      CD_COLLECT,
      CD_FREE,
      CD_FLUSH_FILTERED,
      CD_PROCESS_DECS,
      CD_RELEASE
  }).getId();

  public static final int NO_PROCESSING   = 0;
  public static final int FILTER_PURPLE   = 1;
  public static final int FULL_COLLECTION = 2;

  /****************************************************************************
   *
   * Instance variables
   */
  public final SharedDeque workPool;
  public final SharedDeque blackPool;
  public final SharedDeque unfilteredPurplePool;
  public final SharedDeque maturePurplePool;
  public final SharedDeque filteredPurplePool;
  public final SharedDeque cyclePoolA;
  public final SharedDeque cyclePoolB;
  public final SharedDeque freePool;
  public int cdMode;
  private long startCycles;

  /****************************************************************************
   *
   * Initialization
   */


  public TrialDeletion(RCBase global) {
    workPool = new SharedDeque(RCBase.metaDataSpace, 1);
    blackPool = new SharedDeque(RCBase.metaDataSpace, 1);
    unfilteredPurplePool = new SharedDeque(RCBase.metaDataSpace, 1);
    maturePurplePool = new SharedDeque(RCBase.metaDataSpace, 1);
    filteredPurplePool = new SharedDeque(RCBase.metaDataSpace, 1);
    cyclePoolA = new SharedDeque(RCBase.metaDataSpace, 1);
    cyclePoolB = new SharedDeque(RCBase.metaDataSpace, 1);
    freePool = new SharedDeque(RCBase.metaDataSpace, 1);
    cdMode = NO_PROCESSING;
    global.insertPhaseAfter(RCBase.RELEASE, cdPhase);
  }

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase to execute.
   */
  @Inline
  public boolean collectionPhase(int phaseId) {

    if (phaseId == CD_PREPARE_FILTER) {
      if (shouldFilterPurple()) {
        cdMode = FILTER_PURPLE;
      }
      return true;
    }

    if (phaseId == CD_PREPARE_COLLECT) {
      if (cdMode == FILTER_PURPLE) {
        if (shouldCollectCycles()) {
          cdMode = FULL_COLLECTION;
          startCycles = VM.statistics.cycles();
          if (Options.verbose.getValue() > 0) {
            Log.write("(CD ");
            Log.flush();
          }
        }
      }
      return true;
    }

    if (phaseId == CD_RELEASE) {
      if (cdMode == FULL_COLLECTION) {
        if (Options.verbose.getValue() > 0) {
          Log.write(VM.statistics.cyclesToMillis(VM.statistics.cycles() - startCycles));
          Log.write(" ms)");
        }
      }
      return true;
    }

    return false;
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Update the CD section of the RC word when an increment is performed
   *
   * @param rcWord The refcount word after the increment.
   * @return The updated status after CD modification
   */
  public int notifyIncRC(int rcWord) {
    return (rcWord & ~RCHeader.PURPLE);
  }

  /**
   * If the reported decrement succeeds, should we buffer the object?
   *
   * @param rcWord The refcount work post decrement.
   * @return The updated status after CD modification
   */
  public boolean shouldBufferOnDecRC(int rcWord) {
    return ((rcWord & RCHeader.COLOR_MASK) < RCHeader.PURPLE) &&
           ((rcWord & RCHeader.BUFFERED_MASK) == 0);
  }


  /**
   * Allow a free of this object, or is it in a CD data structure
   *
   * @param object The object to check
   * @return True if free is safe
   */
  public boolean allowFree(ObjectReference object) {
    return !RCHeader.isBuffered(object);
  }

  /**
   * Update the header on a buffered dec to non-zero RC
   *
   * @param rcWord The refcount work post decrement.
   * @return The updated status after CD modification
   */
  public int updateHeaderOnBufferedDec(int rcWord) {
    return (rcWord & ~RCHeader.COLOR_MASK) | RCHeader.PURPLE | RCHeader.BUFFERED_MASK;
  }

  /**
   * Update the header on a non-buffered dec to non-zero RC
   *
   * @param rcWord The refcount work post decrement.
   * @return The updated status after CD modification
   */
  public int updateHeaderOnUnbufferedDec(int rcWord) {
    if ((rcWord & RCHeader.GREEN) != 0) {
      return rcWord;
    }
    return (rcWord & ~RCHeader.COLOR_MASK) | RCHeader.PURPLE;
  }

  /**
   * Perform any cycle detector header initialization.
   *
   * @param typeRef Type information for the object.
   * @param rcWord The refcount work post decrement.
   * @return The updated status after CD modification
   */
  public int initializeHeader(ObjectReference typeRef, int rcWord) {
    if (VM.objectModel.isAcyclic(typeRef)) {
      rcWord |= RCHeader.GREEN;
    }
    return rcWord;
  }
}
