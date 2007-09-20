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
package org.mmtk.plan.concurrent;

import org.mmtk.plan.Phase;
import org.mmtk.plan.Simple;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.ConcurrentTrigger;
import org.mmtk.utility.options.Options;

import org.vmmagic.pragma.*;

/**
 * This class implements the global state of a concurrent collector.
 */
@Uninterruptible
public abstract class Concurrent extends Simple {

  /****************************************************************************
   * Constants
   */

  /****************************************************************************
   * Class variables
   */
  public static final short FLUSH_MUTATOR               = Phase.createSimple("flush-mutator", null);
  public static final short SET_BARRIER_ACTIVE          = Phase.createSimple("set-barrier", null);
  public static final short FLUSH_COLLECTOR             = Phase.createSimple("flush-collector", null);
  public static final short CLEAR_BARRIER_ACTIVE        = Phase.createSimple("clear-barrier", null);

  // CHECKSTYLE:OFF

  /**
   * When we preempt a concurrent marking phase we must flush mutators and then continue the closure.
   */
  protected static final short preemptConcurrentClosure = Phase.createComplex("preeempt-concurrent-trace", null,
      Phase.scheduleMutator  (FLUSH_MUTATOR),
      Phase.scheduleCollector(CLOSURE));

  public static final short CONCURRENT_CLOSURE = Phase.createConcurrent("concurrent-closure",
                                                                        Phase.scheduleComplex(preemptConcurrentClosure));

  /**
   * Perform the initial determination of liveness from the roots.
   */
  protected static final short concurrentClosure = Phase.createComplex("concurrent-mark", null,
      Phase.scheduleMutator   (SET_BARRIER_ACTIVE),
      Phase.scheduleCollector (FLUSH_COLLECTOR),
      Phase.scheduleConcurrent(CONCURRENT_CLOSURE),
      Phase.scheduleMutator   (CLEAR_BARRIER_ACTIVE));

  /** Build, validate and then build another sanity table */
  protected static final short preSanityPhase = Phase.createComplex("sanity", null,
      Phase.scheduleComplex    (sanityBuildPhase),
      Phase.scheduleGlobal     (SANITY_SET_PREGC),
      Phase.scheduleComplex    (sanityCheckPhase),
      Phase.scheduleComplex    (sanityBuildPhase));

  /** Validate, build and validate the second sanity table */
  protected static final short postSanityPhase = Phase.createComplex("sanity", null,
      Phase.scheduleGlobal     (SANITY_SET_POSTGC),
      Phase.scheduleComplex    (sanityCheckPhase),
      Phase.scheduleComplex    (sanityBuildPhase),
      Phase.scheduleGlobal     (SANITY_SET_PREGC),
      Phase.scheduleComplex    (sanityCheckPhase));

  // CHECKSTYLE:OFF

  /****************************************************************************
   * Instance variables
   */

  /****************************************************************************
   * Constructor.
   */
  public Concurrent() {
    Options.concurrentTrigger = new ConcurrentTrigger();
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  @Interruptible
  public void postBoot() {
    super.postBoot();

    /* Set up the concurrent marking phase */
    replacePhase(Phase.scheduleCollector(CLOSURE), Phase.scheduleComplex(concurrentClosure));

    if (Options.sanityCheck.getValue()) {
      Log.writeln("Collection sanity checking enabled.");
      replacePhase(Phase.schedulePlaceholder(PRE_SANITY_PLACEHOLDER), Phase.scheduleComplex(preSanityPhase));
      replacePhase(Phase.schedulePlaceholder(POST_SANITY_PLACEHOLDER), Phase.scheduleComplex(postSanityPhase));
    }
  }

  /****************************************************************************
  *
  * Collection
  */

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase to execute.
   */
  @Inline
  public void collectionPhase(short phaseId) {
    if (phaseId == CLOSURE) {  // No-op for a concurrent collector
      return;
    }
    super.collectionPhase(phaseId);
  }

  /**
   * This method controls the triggering of an atomic phase of a concurrent
   * collection. It is called periodically during allocation.
   *
   * @return True if a collection is requested by the plan.
   */
  protected boolean concurrentCollectionRequired() {
    return !Phase.concurrentPhaseActive() &&
      ((getPagesReserved() * 100) / getTotalPages()) > Options.concurrentTrigger.getValue();
  }

  /*****************************************************************************
   *
   * Accounting
   */
}
