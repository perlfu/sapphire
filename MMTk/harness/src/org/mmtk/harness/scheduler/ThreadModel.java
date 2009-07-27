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
package org.mmtk.harness.scheduler;

import org.mmtk.harness.Collector;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.utility.Log;

/**
 * Abstract implementation of a threading model.
 */
public abstract class ThreadModel {

  /**
   * Distinguish between setup/initialization and proper running of the harness
   */
  private boolean running = false;

  /** The global state of the scheduler */
  public enum State {
    /** Mutator threads are running */
    MUTATOR,
    /** GC requested, GC will start once all mutators have yielded */
    BEGIN_GC,
    /** GC in progress */
    GC,
    /** Waiting on all GC threads to hibernate */
    END_GC }

  private static volatile State state = State.MUTATOR;

  /** The trigger for this GC */
  protected int triggerReason;

  protected void initCollectors() { }

  protected abstract void yield();

  protected abstract void scheduleMutator(Schedulable method);

  protected abstract void scheduleCollector();

  protected abstract Thread scheduleCollector(Schedulable item);

  protected abstract Log currentLog();

  protected abstract Mutator currentMutator();

  /* schedule GC */

  protected abstract void triggerGC(int why);

  protected abstract void exitGC();

  protected abstract void waitForGCStart();

  protected abstract boolean noThreadsInGC();

  protected abstract boolean gcTriggered();

  protected abstract int rendezvous(int where);

  protected abstract int mutatorRendezvous(String where, int expected);

  protected abstract Collector currentCollector();

  protected abstract void waitForGC();

  protected int getTriggerReason() {
    return triggerReason;
  }

  protected abstract void schedule();

  protected abstract void scheduleGcThreads();

  /**
   * An MMTk lock
   */
  protected abstract Lock newLock(String name);

  protected void setState(State state) {
    Trace.trace(Item.SCHEDULER,"State changing from %s to %s",ThreadModel.state,state);
    ThreadModel.state = state;
  }

  protected State getState() {
    return state;
  }

  protected boolean isState(State s) {
    return state == s;
  }

  protected boolean isRunning() {
    return running;
  }

  private void setRunning(boolean state) {
    running = state;
  }

  protected void startRunning() {
    setRunning(true);
  }

  protected void stopRunning() {
    setRunning(false);
  }
}
