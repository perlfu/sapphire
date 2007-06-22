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
package org.mmtk.utility.statistics;

import org.mmtk.utility.Log;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements a simple boolean counter (counting number of
 * phases where some boolean event is true).
 */
@Uninterruptible public class BooleanCounter extends Counter {

  /****************************************************************************
   *
   * Instance variables
   */

  private final boolean[] state;

  protected int total = 0;
  private boolean running = false;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   */
  public BooleanCounter(String name) {
    this(name, true, false);
  }

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   * @param start True if this counter is to be implicitly started
   * when <code>startAll()</code> is called (otherwise the counter
   * must be explicitly started).
   */
  public BooleanCounter(String name, boolean start) {
    this(name, start, false);
  }

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   * @param start True if this counter is to be implicitly started
   * when <code>startAll()</code> is called (otherwise the counter
   * must be explicitly started).
   * @param mergephases True if this counter does not separately
   * report GC and Mutator phases.
   */
  public BooleanCounter(String name, boolean start, boolean mergephases) {
    super(name, start, mergephases);
    state = new boolean[Stats.MAX_PHASES];
    for (int i = 0; i < Stats.MAX_PHASES; i++)
      state[i] = false;
  }

  /****************************************************************************
   *
   * Counter-specific methods
   */

  /**
   * Set the boolean to true for this phase, increment the total.
   */
  public void set() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!state[Stats.phase]);
    state[Stats.phase] = true;
    total++;
  }

  /****************************************************************************
   *
   * Generic counter control methods: start, stop, print etc
   */

  /**
   * Start this counter
   */
  protected void start() {
    if (!Stats.gatheringStats) return;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!running);
    running = true;
  }

  /**
   * Stop this counter
   */
  protected void stop() {
    if (!Stats.gatheringStats) return;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(running);
    running = false;
  }

  /**
   * The phase has changed (from GC to mutator or mutator to GC).
   * Take action with respect to the last phase if necessary.
   * <b>Do nothing in this case.</b>
   *
   * @param oldPhase The last phase
   */
  void phaseChange(int oldPhase) {}

  /**
   * Print the value of this counter for the given phase.  Print '0'
   * for false, '1' for true.
   *
   * @param phase The phase to be printed
   */
  protected final void printCount(int phase) {
    if (VM.VERIFY_ASSERTIONS && mergePhases())
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert((phase | 1) == (phase + 1));
    if (mergePhases())
      printValue((state[phase] || state[phase + 1]) ? 1 : 0);
    else
      printValue((state[phase]) ? 1 : 0);
  }

  /**
   * Print the current total number of 'true' phases for this counter
   */
  protected final void printTotal() {
    int total = 0;
    for (int p = 0; p <= Stats.phase; p++) {
      total += (state[p]) ? 1 : 0;
    }
    printValue(total);
  }

  /**
   * Print the current total number of 'true' phases for either the
   * mutator or GC phase
   *
   * @param mutator True if the total for the mutator phases is to be
   * printed (otherwise the total for the GC phases will be printed).
   */
  protected final void printTotal(boolean mutator) {
    int total = 0;
    for (int p = (mutator) ? 0 : 1; p <= Stats.phase; p += 2) {
      total += (state[p]) ? 1 : 0;
    }
    printValue(total);
  }

  /**
   * Print the current minimum value for either the mutator or GC
   * phase. <b>Do nothing in this case.</b>
   *
   * @param mutator True if the minimum for the mutator phase is to be
   * printed (otherwise the minimum for the GC phase will be printed).
   */
  protected final void printMin(boolean mutator) {}

  /**
   * Print the current maximum value for either the mutator or GC
   * phase. <b>Do nothing in this case.</b>
   *
   * @param mutator True if the maximum for the mutator phase is to be
   * printed (otherwise the maximum for the GC phase will be printed).
   */
  protected final void printMax(boolean mutator) {}

  /**
   * Print the given value
   *
   * @param value The value to be printed
   */
  void printValue(int value) {
    Log.write(value);
  }

  /**
   * Print statistics for the most recent phase
   */
  public void printLast() {
    if (Stats.phase > 0) printCount(Stats.phase - 1);
  }
}
