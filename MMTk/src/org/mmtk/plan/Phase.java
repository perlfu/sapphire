/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan;

import org.mmtk.utility.Constants;
import org.mmtk.utility.statistics.Timer;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * A garbage collection proceeds as a sequence of phases. Each
 * phase is either simple (singular) or complex (an array).
 * 
 * The context an individual phase executes in may be global, local,
 * or an (ordered) combination of global and local.
 * 
 * @see CollectorContext#collectionPhase
 * @see Plan#collectionPhase
 * 
 * Urgent TODO: Assess cost of rendezvous when running in parallel.
 * It should be possible to remove some by thinking about phases more 
 * carefully
 * 
 *
 * @author Daniel Frampton
 * @author Robin Garner
 */
@Uninterruptible public abstract class Phase implements Constants {
  private static final int MAX_PHASES = 64;
  private static final Phase[] phases = new Phase[MAX_PHASES];
  private static short phaseId = 0;

  /** If this bit is set then global work should happen first. */
  protected static final int GLOBAL_FIRST_MASK = 1;

  /** If this bit is set then local collector work should happen. */
  protected static final int COLLECTOR_MASK = 4;

  /** If this bit is set then local mutator work should happen. */
  protected static final int MUTATOR_MASK = 8;

  /** If this bit is set then global work should happen last. */
  protected static final int GLOBAL_LAST_MASK = 2;

  /** A phase that only executes global actions (1. Plan). */
  public static final int GLOBAL_ONLY = GLOBAL_FIRST_MASK;

  /** A phase that executes global actions first (1. Plan, 2. PlanLocal). */
  public static final int GLOBAL_FIRST = GLOBAL_FIRST_MASK | COLLECTOR_MASK;

  /** A phase that executes global actions last (1. PlanLocal, 2. Plan). */
  public static final int GLOBAL_LAST = GLOBAL_LAST_MASK | COLLECTOR_MASK;

  /** A phase that only executes local actions (1. PlanLocal). */
  public static final int COLLECTOR_ONLY = COLLECTOR_MASK;

  /** A phase that only executes local actions (1. PlanLocal). */
  public static final int MUTATOR_ONLY = MUTATOR_MASK;

  /**
   * A phase that currently does not execute. Is either designed to be 
   * replaced by collectors if required, or is a reminded or a future 
   * work item.
   */
  public static final int PLACEHOLDER = 0;

  /**
   * The unique phase identifier.
   */
  protected final short id;

  /**
   * The name of the phase.
   */
  protected final String name;

  /**
   * The Timer that is started and stopped around the excecution of this 
   * phase.
   */
  protected final Timer timer;

  /**
   * Create a new Phase. This involves creating a corresponding Timer
   * instance, allocating a unique identifier, and registering the 
   * Phase.
   * 
   * @param name The name for the phase.
   */
  protected Phase(String name) {
    this(name, new Timer(name, false, true));
  }

  /**
   * Create a new phase. This involves setting the corresponding Timer
   * instance, allocating a unique identifier, and registering the Phase.
   * 
   * @param name The name of the phase.
   * @param timer The timer, or null if this is an untimed phase.
   */
  protected Phase(String name, Timer timer) {
    this.name = name;
    this.timer = timer;
    this.id = phaseId++;
    phases[this.id] = this;
  }

  /**
   * @return The unique identifier for this phase.
   */
  public final int getId() {
    return this.id;
  }

  /**
   * @param phaseId The unique phase identifier.
   * @return The name of the phase.
   */
  public static String getName(int phaseId) {
    return phases[phaseId].name;
  }

  /**
   * Delegate the execution of a specified phase. This causes any
   * necessary synchronization to be executed, and the appropriate
   * collectionPhase calls to be made to Plan and PlanLocal
   * 
   * @see Plan#collectionPhase
   * @see CollectorContext#collectionPhase
   * 
   * @param phaseId The identifier of the phase to execute.
   */
  public static void delegatePhase(int phaseId) {
    delegatePhase(phases[phaseId]);
  }

  /**
   * Delegate the execution of a specified phase. This causes any
   * necessary synchronization to be executed, and the appropriate
   * collectionPhase calls to be made to Plan and PlanLocal
   * 
   * @see Plan
   * @see CollectorContext
   * 
   * @param phase The phase to execute.
   */
  protected static void delegatePhase(Phase phase) {
    phase.delegatePhase();
  }

  /**
   * Call appropriate methods on Plan and PlanLocal for a phase.
   */
  protected abstract void delegatePhase();

  /**
   * Print out phase information for debugging purposes.
   */
  protected abstract void logPhase();

  /**
   * Retrieve a phase by the unique phase identifier.
   * 
   * @param id The phase identifier.
   * @return The Phase instance.
   */
  public static Phase getPhase(int id) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(id < phaseId, "Phase ID unknown");
      VM.assertions._assert(phases[id] != null, "Uninitialised phase");
    }
    return phases[id];
  }
}
