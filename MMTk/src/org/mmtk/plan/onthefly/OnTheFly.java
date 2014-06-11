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

import org.mmtk.plan.Phase;
import org.mmtk.plan.Plan;
import org.mmtk.plan.StopTheWorld;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.ConcurrentTrigger;
import org.mmtk.utility.options.ConcurrentTriggerMethod;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements the global state of a concurrent collector.
 */
@Uninterruptible
public abstract class OnTheFly extends StopTheWorld {

  /****************************************************************************
   * Constants
   */
  public static final int DEBUG_WEAK_REF_TERMINATION_LOOP_COUNT = 0;  // 0: disable
  
  /* root scanning */
  public static final short OTF_STACK_ROOTS             = Phase.createSimple("otf-stack-roots");
  public static final short OTF_ROOTS_SNAPSHOT          = Phase.createSimple("otf-roots-snapshot");
  public static final short OTF_ROOTS                   = Phase.createSimple("otf-roots");
  /* work queue flush */
  public static final short FLUSH_MUTATOR               = Phase.createSimple("flush-mutator", null);
  public static final short FLUSH_COLLECTOR             = Phase.createSimple("flush-collector", null);
  /* reference types */
  public static final short OTF_PREPARE_REFERENCE       = Phase.createSimple("otf-prepare-reference", null);
  public static final short OTF_RELEASE_REFERENCE       = Phase.createSimple("otf-release-reference", null);
  public static final short OTF_PROCESS_SOFT_REFS       = Phase.createSimple("otf-process-soft-refs", null);
  public static final short OTF_WEAK_REFS_TERMINATION   = Phase.createSimple("otf-weak-refs-termination", null);
  public static final short OTF_PROCESS_WEAK_REFS       = Phase.createSimple("otf-process-weak-refs", null);
  public static final short TRIGGER_FINALIZE            = Phase.createSimple("trigger-finalize", null);
  /* controlling mutator threads */
  public static final short STOP_MUTATORS               = Phase.createSimple("stop-mutators", null);
  public static final short RESTART_MUTATORS            = Phase.createSimple("restart-mutators", null);
  public static final short DISABLE_MUTATORS            = Phase.createSimple("disable-mutators", null);
  public static final short ENABLE_MUTATORS             = Phase.createSimple("enable-mutators", null);
  /* misc */
  public static final short UPDATE_GC_IN_PROGRESS       = Phase.createSimple("update-gc-in-progress", null);
  /* debug */
  public static final short STOP_MUTATORS_BEFORE_ROOTS_HOOK   = Phase.createSimple("stop-mutators-before-roots-hook");
  public static final short RESTART_MUTATORS_AFTER_ROOTS_HOOK = Phase.createSimple("restart-mutators-after-roots-hook");
  public static final short ASSERT_STACK_PREPARED      = Phase.createSimple("assert-stack-prepared");

  /** Global root scanning for OTF collectors */
  protected static final short onTheFlyRootsPhase = Phase.createComplex("otf-roots-phase",
      Phase.schedulePlaceholder(STOP_MUTATORS_BEFORE_ROOTS_HOOK),
      Phase.scheduleGlobal     (OTF_ROOTS_SNAPSHOT),
      Phase.scheduleCollector  (OTF_ROOTS),
      Phase.schedulePlaceholder(RESTART_MUTATORS_AFTER_ROOTS_HOOK));

  /** Start the collection, including preparation for any collected spaces. */
  protected static final short initPhase = Phase.createComplex("init",
      Phase.scheduleGlobal          (SET_COLLECTION_KIND),
      Phase.scheduleGlobal          (INITIATE),
      Phase.schedulePlaceholder     (PRE_SANITY_PLACEHOLDER));
  
  /** Check if the stack is prepared and scan the stack */
  public static final short ensurePreparedAndScanStack = Phase.createComplex("ensure-prepared-and-scan-stack",
      Phase.scheduleGlobal   (ASSERT_STACK_PREPARED),
      Phase.scheduleCollector(STACK_ROOTS)
      );

  /** Build and validate a sanity table */
  protected static final short preSanityPhase = Phase.createComplex("pre-sanity", null,
      Phase.scheduleSpecial    (STOP_MUTATORS),
      Phase.scheduleGlobal     (SANITY_SET_PREGC),
      Phase.scheduleComplex    (sanityBuildPhase),
      Phase.scheduleComplex    (sanityCheckPhase),
      Phase.scheduleSpecial    (RESTART_MUTATORS));

  /** Build and validate a sanity table */
  protected static final short postSanityPhase = Phase.createComplex("post-sanity", null,
      Phase.scheduleSpecial    (STOP_MUTATORS),
      Phase.scheduleGlobal     (SANITY_SET_POSTGC),
      Phase.scheduleComplex    (sanityBuildPhase),
      Phase.scheduleComplex    (sanityCheckPhase),
      Phase.scheduleSpecial    (RESTART_MUTATORS));

  /**
   * The collection scheme - this is a small tree of complex phases.
   */
  protected static final short finishPhase = Phase.createComplex("finish",
      Phase.schedulePlaceholder    (POST_SANITY_PLACEHOLDER),
      Phase.scheduleCollector      (COMPLETE),
      Phase.scheduleGlobal         (COMPLETE));
  
  /****************************************************************************
   * Class variables
   */
  private static boolean mutatorsEnabled = true;

  public static final int REF_TYPE_NORMAL      = 0;
  public static final int REF_TYPE_TRACING     = 1;
  public static final int REF_TYPE_CLEARING    = 2;
  private static volatile int softState;
  private static volatile int weakState;
  private static final org.mmtk.vm.Lock refTypeLock = VM.newLock("refTpeLock");
  /****************************************************************************
   * Instance variables
   */

  /****************************************************************************
   * Constructor.
   */
  public OnTheFly() {
    Options.concurrentTrigger = new ConcurrentTrigger();
    Options.concurrentTriggerMethod = new ConcurrentTriggerMethod();
  }

  /*****************************************************************************
   *
   * Collection
   */

  public static String stateName(int state) {
    switch (state) {
    case REF_TYPE_NORMAL:   return "NORMAL";
    case REF_TYPE_TRACING:  return "TRACING";
    case REF_TYPE_CLEARING: return "CLEANING";
    }
    VM.assertions.fail("unknown reference state");
    return null;
  }
  
  /**
   * The processOptions method is called by the runtime immediately after
   * command-line arguments are available. Allocation must be supported
   * prior to this point because the runtime infrastructure may require
   * allocation in order to parse the command line arguments.  For this
   * reason all plans should operate gracefully on the default minimum
   * heap size until the point that processOptions is called.
   */
  @Interruptible
  public void processOptions() {
    super.processOptions();
  }

  @Inline
  static int getSoftState() {
    return softState;
  }

  @Inline
  private static void setSoftState(int state) {
    if (Options.verbose.getValue() >= 6) {
      Log.write("soft reference state ");
      Log.write(stateName(softState));
      Log.write(" -> ");
      Log.writeln(stateName(state));
    }
    softState = state;
  }

  @Inline
  static boolean testAndSetSoftState(int old, int state) {
    refTypeLock.acquire();
    if (softState == old) {
      int prev = softState;
      softState = state;
      refTypeLock.release();
      if (Options.verbose.getValue() >= 6) {
        Log.write("soft reference state ");
        Log.write(stateName(prev));
        Log.write(" -atomic-> ");
        Log.writeln(stateName(state));
      }
      return true;
    }
    refTypeLock.release();
    return false;
  }

  @Inline
  public static int getWeakState() {
    return weakState;
  }

  @Inline
  private static void setWeakState(int state) {
    if (Options.verbose.getValue() >= 6) {
      Log.write("weak reference state ");
      Log.write(stateName(weakState));
      Log.write(" -> ");
      Log.writeln(stateName(state));
    }
    weakState = state;
  }

  @Inline
  static boolean testAndSetWeakState(int old, int state) {
    refTypeLock.acquire();
    if (weakState == old) {
      int prev = weakState;
      weakState = state;
      refTypeLock.release();
      if (Options.verbose.getValue() >= 6) {
        Log.write("weak reference state ");
        Log.write(stateName(prev));
        Log.write(" -atomic-> ");
        Log.writeln(stateName(state));
      }
      return true;
    }
    refTypeLock.release();
    return false;
  }
  /****************************************************************************
   *
   * Collection
   */

  /** debug */
  private int otfWeakRefsLoopCount;
  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase to execute.
   */
  @Inline
  public void collectionPhase(short phaseId) {
    if (phaseId == ASSERT_STACK_PREPARED) {
      if (VM.VERIFY_ASSERTIONS) VM.scanning.assertMutatorPrepared();
      return;
    }

    /*
     * Root scan
     */
    if (phaseId == OnTheFly.OTF_ROOTS_SNAPSHOT) {
      VM.scanning.onTheFlyRootsSnapshot();
      return;
    }

    /*
     * Reference procession
     */
    if (phaseId == OnTheFly.OTF_PREPARE_REFERENCE) {
      if (!Options.noReferenceTypes.getValue())
        setWeakState(REF_TYPE_TRACING);
      otfWeakRefsLoopCount = 0;
      return;
    }

    if (phaseId == OTF_PREPARE_REFERENCE) {
      return;
    }

    if (phaseId == OnTheFly.OTF_WEAK_REFS_TERMINATION) {
      if (Options.noReferenceTypes.getValue()) {
        /* We need this termination loop regardless of presence of reference types
         * in order to help collectors with insertion barrier
         */
        if (!collectorHasWork())
          return;
      } else {
        if (!collectorHasWork() && otfWeakRefsLoopCount++ >= DEBUG_WEAK_REF_TERMINATION_LOOP_COUNT) {
          if (testAndSetWeakState(REF_TYPE_TRACING, REF_TYPE_CLEARING))
            return;
        }
        setWeakState(REF_TYPE_TRACING);
      }
      Phase.pushScheduledPhase(getWeakReferenceTerminationLoopPhase());
      return;
    }

    if (phaseId == OnTheFly.OTF_PROCESS_WEAK_REFS) {
      /* this phase can be merged with following phases, but not with previous phases
       * as we need a handshake between clearing weak refs and changing state.
       */
      setWeakState(REF_TYPE_NORMAL);
      return;
    }

    if (phaseId == OTF_RELEASE_REFERENCE) {
    	return;
    }

    if (phaseId == OnTheFly.TRIGGER_FINALIZE) {
      VM.finalizableProcessor.triggerFinalize();
      return;
    }

    super.collectionPhase(phaseId);
  }

  /**
   * Perform a (global) unpreemptible collection phase.
   */
  @Override
  @Unpreemptible
  public void unpreemptibleCollectionPhase(short phaseId) {
    if (phaseId == DISABLE_MUTATORS) {
      if (Options.verbose.getValue() >= 5) Log.writeln("[Disabling mutators...]");
      mutatorsEnabled = false;
      phaseId = STOP_MUTATORS;
      /* fall through */
    }

    if (phaseId == ENABLE_MUTATORS) {
      if (Options.verbose.getValue() >= 5) Log.writeln("[Enabling mutators...]");
      mutatorsEnabled = true;
      phaseId = RESTART_MUTATORS;
      /* fall through */
    }

    if (phaseId == STOP_MUTATORS) {
      // Stop all mutator threads
    	if (Plan.controlCollectorContext.isOnTheFlyCollection())
    		controlCollectorContext.stopAllMutators();
    	return;
    }

    if (phaseId == RESTART_MUTATORS) {
      if (mutatorsEnabled) {
      	if (Plan.controlCollectorContext.isOnTheFlyCollection()) {
	        stacksPrepared = false;
	        controlCollectorContext.resumeAllMutators();
      	}
        return;
      } else if (Options.verbose.getValue() >= 5) {
        Log.writeln("[Restart Mutators called but mutators are currently disabled");
      }
      return;
    }

    super.unpreemptibleCollectionPhase(phaseId);
  }

  /**
   * Are mutators enabled?
   * @return True if mutators are enabled for this collection.
   */
  @Inline
  public boolean mutatorsAreEnabled() {
    return mutatorsEnabled;
  }
  
  /**
   * Does the collector has work on queues?  This method is used for IU
   * termination loop.
   * @return True if any queue or remset has work.
   */
  @Inline
  protected boolean collectorHasWork() {
    return false;
  }

  /**
   * Determine if all soft references should be retained or not in this
   * collection cycle.  The decision must not be changed during a GC cycle.
   * @return True if the collector intend to retain all soft references.
   */
  @Inline
  protected boolean retainSoftReferences() {
    return !isEmergencyCollection();
  }

  /**
   * This method is called when the collector cannot guarantee
   * all reachable objects are coloured black before processing
   * weak references.
   * When we decide not to use reference types, this phase
   * is used for IU termination loop for collectors with an
   * insertion barrier.
   * @return a phase to be done to guarantee all reachable objects
   * are coloured black.  This phase should be a complex phase
   * ending with OTF_WEAK_REF_TERMINATION phase. This phase should
   * not have a timer.
   */
  @Inline
  protected int getWeakReferenceTerminationLoopPhase() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return -1;
  }

  /**
   * This method controls the triggering of a GC. It is called periodically
   * during allocation. Returns <code>true</code> to trigger a collection.
   *
   * @param spaceFull Space request failed, must recover pages within 'space'.
   * @param space TODO
   * @return <code>true</code> if a collection is requested by the plan.
   */
  protected boolean collectionRequired(boolean spaceFull, Space space) {
    /* Stress test does not work for on-the-fly collectors as it triggers
     * only stop-the-world GC.
     */
    boolean heapFull = getPagesReserved() > getTotalPages();

    return spaceFull  || heapFull;
  }

  /**
   * This method controls the triggering of an atomic phase of a concurrent
   * collection. It is called periodically during allocation.
   *
   * @return True if a collection is requested by the plan.
   */
  @Override
  protected final boolean concurrentCollectionRequired() {
    return false;
  }

  /*****************************************************************************
   *
   * Accounting
   */
}
