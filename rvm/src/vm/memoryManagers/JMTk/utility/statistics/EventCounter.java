/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */

package org.mmtk.utility.statistics;

import org.mmtk.utility.Log;

import org.mmtk.vm.VM_Interface;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This class implements a simple event counter (counting number
 * events that occur for each phase).
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 * $Id$
 */
public class EventCounter extends Counter
  implements VM_Uninterruptible {

  /****************************************************************************
   *
   * Instance variables
   */

  private long count[];

  protected long totalCount = 0;
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
  public EventCounter(String name) {
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
  public EventCounter(String name, boolean start) {
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
  public EventCounter(String name, boolean start, boolean mergephases) {
    super(name, start, mergephases);
    count = new long[Stats.MAX_PHASES];
  }

  /****************************************************************************
   *
   * Counter-specific methods
   */

  /**
   * Increment the event counter
   */
  public void inc() {
    if (running) inc(1);
  }

  /** 
   * Increment the event counter by <code>value</code>
   *
   * @param value The amount by which the counter should be incremented.
   */
  public void inc(int value) {
    if (running) totalCount += value;
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
    if (VM_Interface.VerifyAssertions)  VM_Interface._assert(!running);
    running = true;
  }

  /**
   * Stop this counter
   */
  protected void stop() {
    if (!Stats.gatheringStats) return;
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(running);
    running = false;
  }

  /**
   * The phase has changed (from GC to mutator or mutator to GC).
   * Take action with respect to the last phase if necessary.
   * <b>Do nothing in this case.</b>
   * 
   * @param oldPhase The last phase
   */
  void phaseChange(int oldPhase) {
    if (running) {
      count[oldPhase] = totalCount;
      totalCount = 0;
    }
  }

  /**
   * Print the value of this counter for the given phase.  Print '0'
   * for false, '1' for true.
   *
   * @param phase The phase to be printed
   */
  final protected void printCount(int phase) {
    if (VM_Interface.VerifyAssertions && mergePhases()) 
      VM_Interface._assert((phase | 1) == (phase + 1));
    if (mergePhases()) 
      printValue(count[phase] + count[phase+1]);
    else
      printValue(count[phase]);
  }

  /**
   * Print the current total for this counter
   */
  final public void printTotal() {
    long total = 0;
    for (int p = 0; p <= Stats.phase; p++) {
      total += count[p];
    }
    printValue(total);
  }

  /**
   * Print the current total for either the mutator or GC phase
   *
   * @param mutator True if the total for the mutator phases is to be
   * printed (otherwise the total for the GC phases will be printed).
   */
  final protected void printTotal(boolean mutator) {
    long total = 0;
    for (int p = (mutator) ? 0 : 1; p <= Stats.phase; p += 2) {
      total += count[p];
    }
    printValue(total);
  }

  /**
   * Print the current minimum value for either the mutator or GC
   * phase.
   *
   * @param mutator True if the minimum for the mutator phase is to be
   * printed (otherwise the minimum for the GC phase will be printed).
   */
  final protected void printMin(boolean mutator) {
    int p = (mutator) ? 0 : 1;
    long min = count[p];
    for (; p < Stats.phase; p += 2) {
      if (count[p] < min) min = count[p];
    }
    printValue(min);
  }

  /**
   * Print the current maximum value for either the mutator or GC
   * phase.
   *
   * @param mutator True if the maximum for the mutator phase is to be
   * printed (otherwise the maximum for the GC phase will be printed).
   */
  final protected void printMax(boolean mutator) {
    int p = (mutator) ? 0 : 1;
    long max = count[p];
    for (; p < Stats.phase; p += 2) {
      if (count[p] > max) max = count[p];
    }
    printValue(max);
  }

  /**
   * Print the given value
   *
   * @param value The value to be printed
   */
  void printValue(long value) {
    Log.write(value);
  }

  /**
   * Print statistics for the most recent phase
   */
  public void printLast() {
    if (Stats.phase > 0) printCount(Stats.phase - 1);
  }
}
