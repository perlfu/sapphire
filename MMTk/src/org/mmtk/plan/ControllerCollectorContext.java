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
package org.mmtk.plan;

import org.mmtk.utility.Log;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.Monitor;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

@Uninterruptible
public class ControllerCollectorContext extends CollectorContext {

  /** The lock to use to manage collection */
  private Monitor lock;

  /** The set of worker threads to use */
  private ParallelCollectorGroup workers;

  /** Flag used to control the 'race to request' */
  private boolean requestFlag;

  /** The current request index */
  private int requestCount;

  /** The request index that was last completed */
  private int lastRequestCount = -1;

  /** Is there concurrent collection activity */
  private boolean concurrentCollection = false;

  /** Is arrived request for on-the-fly */
  private boolean requestOnTheFlyCollection = false;

  /** Is this collection on-the-fly */
  private boolean onTheFlyCollection = false;

  /**
   * Create a controller context.
   *
   * @param workers The worker group to use for collection.
   */
  public ControllerCollectorContext(ParallelCollectorGroup workers) {
    this.workers = workers;
  }

  @Override
  @Interruptible
  public void initCollector(int id) {
    super.initCollector(id);
    lock = VM.newHeavyCondLock("CollectorControlLock");
  }

  private void logController(String kind, String msg) {
    Log.write("[");
    Log.write(kind);
    Log.write("Controller: ");
    Log.write(msg);
    Log.writeln("]");
  }

  /**
   * Main execution loop.
   */
  @Override
  @Unpreemptible
  public void run() {
    while(true) {
      // Wait for a collection request.
      if (Options.verbose.getValue() >= 5) logController(onTheFlyCollection ? "OTF" : "STW", "Waiting for request...");
      waitForRequest();
      if (Options.verbose.getValue() >= 5) logController(onTheFlyCollection ? "OTF" : "STW", "Request recieved.");

      // This cycle is on-the-fly cycle even if another request comes afterward.
      boolean onTheFlyCollection = this.onTheFlyCollection;

      // The start time.
      long startTime = VM.statistics.nanoTime();

      if (concurrentCollection) {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!onTheFlyCollection);
        if (Options.verbose.getValue() >= 5) logController("STW", "Stopping concurrent collectors...");
        Plan.concurrentWorkers.abortCycle();
        Plan.concurrentWorkers.waitForCycle();
        Phase.clearConcurrentPhase();
        // Collector must re-request concurrent collection in this case.
        concurrentCollection = false;
      }

      if (!onTheFlyCollection) {
        // Stop all mutator threads
        if (Options.verbose.getValue() >= 5) logController("STW", "Stopping the world...");
        VM.collection.stopAllMutators();
      }

      // Was this user triggered?
      boolean userTriggeredCollection = Plan.isUserTriggeredCollection();
      boolean internalTriggeredCollection = Plan.isInternalTriggeredCollection();

      // Clear the request
      clearRequest();

      // Trigger GC.
      if (Options.verbose.getValue() >= 5) logController(onTheFlyCollection ? "OTF" : "STW", "Triggering worker threads...");
      workers.triggerCycle();

      // Wait for GC threads to complete.
      workers.waitForCycle();
      if (Options.verbose.getValue() >= 5) logController(onTheFlyCollection ? "OTF" : "STW", "Worker threads complete!");

      // Heap growth logic
      if (onTheFlyCollection) {
        /*
         * TODO: This logic is incomplete.  We have to work later.
         */
        long elapsedTime = VM.statistics.nanoTime() - startTime;
        if (!requestFlag) {
          /* This was totally on-the-fly collection.  No mutators block due to lack of memory.
           * We cannot tell how much fraction of CPU time was spent for GC.  So, we approximate
           * as 1/2.
           */
          elapsedTime >>= 1;
        }
        HeapGrowthManager.recordGCTime(VM.statistics.nanosToMillis(elapsedTime));
        if (VM.activePlan.global().lastCollectionFullHeap() && requestFlag) {
          if (Options.variableSizeHeap.getValue() && !Plan.isUserTriggeredCollection()) {
            // Don't consider changing the heap size if the application triggered the collection
            if (Options.verbose.getValue() >= 5) Log.writeln("[OTFController: Considering heap size.]");
            HeapGrowthManager.considerHeapSize();
          }
          HeapGrowthManager.reset();
        }
      } else {
        long elapsedTime = VM.statistics.nanoTime() - startTime;
        HeapGrowthManager.recordGCTime(VM.statistics.nanosToMillis(elapsedTime));
        if (VM.activePlan.global().lastCollectionFullHeap() && !internalTriggeredCollection) {
          if (Options.variableSizeHeap.getValue() && !userTriggeredCollection) {
            // Don't consider changing the heap size if the application triggered the collection
            if (Options.verbose.getValue() >= 5) Log.writeln("[STWController: Considering heap size.]");
            HeapGrowthManager.considerHeapSize();
          }
          HeapGrowthManager.reset();
        }
      }

      // Reset the triggering information.
      Plan.resetCollectionTrigger();
      clearOnTheFlyCollection();

      if (!onTheFlyCollection) {
        // Resume all mutators
        if (Options.verbose.getValue() >= 5) Log.writeln("[STWController: Resuming mutators...]");
        VM.collection.resumeAllMutators();
      }

      // Start threads that will perform concurrent collection work alongside mutators.
      if (concurrentCollection) {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!onTheFlyCollection);
        if (Options.verbose.getValue() >= 5) Log.writeln("[STWController: Triggering concurrent collectors...]");
        Plan.concurrentWorkers.triggerCycle();
      }
    }
  }

  /**
   * Request that concurrent collection is performed after this stop-the-world increment.
   */
  public void requestConcurrentCollection() {
    concurrentCollection = true;
  }

  /**
   * Request a collection.
   */
  public void request() {
    request(false);
  }

  public void requestOnTheFlyCollection() {
    request(true);
  }

  public void request(boolean onTheFly) {
    lock.lock();
    if (!onTheFly || !onTheFlyCollection) {
      if (!requestFlag) {
        requestFlag = true;
        requestOnTheFlyCollection = onTheFly;
        requestCount++;
        lock.broadcast();
      }
    }
    lock.unlock();
  }

  /**
   * Clear the collection request, making future requests incur an
   * additional collection cycle.
   */
  private void clearRequest() {
    lock.lock();
    requestFlag = false;
    requestOnTheFlyCollection = false;
    lock.unlock();
  }

  private void clearOnTheFlyCollection() {
    lock.lock(); // to make this change visible from other threads, e.g., mutators
    onTheFlyCollection = false;
    lock.unlock();
  }

  /**
   * Wait until a request is received.
   */
  private void waitForRequest() {
    lock.lock();
    lastRequestCount++;
    while (lastRequestCount == requestCount) {
      lock.await();
    }
    onTheFlyCollection = requestOnTheFlyCollection;
    lock.unlock();
  }

  @Unpreemptible
  private boolean areAllMutatorsBlocked() {
    MutatorContext m;
    boolean blocked = true;
    while ((m = VM.activePlan.getNextMutator()) != null)
      if (!VM.collection.isBlockedForGC(m)) {
        blocked = false;
        break;
      }
    VM.activePlan.resetMutatorIterator();
    return blocked;
  }

  @Unpreemptible
  public void stopAllMutators() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(onTheFlyCollection || areAllMutatorsBlocked());
    if (onTheFlyCollection) {
      if (Options.verbose.getValue() >= 5) Log.writeln("[Stopping mutators...]");
      VM.collection.stopAllMutators();
    } else {
      if (Options.verbose.getValue() >= 5) Log.writeln("[Stopping mutators; mutators are not running in STW collection]");
    }
  }

  @Unpreemptible
  public void resumeAllMutators() {
    if (onTheFlyCollection) {
      if (Options.verbose.getValue() >= 5) Log.writeln("[Resuming mutators...]");
      VM.collection.resumeAllMutators();
    } else {
      if (Options.verbose.getValue() >= 5) Log.writeln("[Resuming mutators is requested in STW collection. Keep stopped.]");
    }
  }
  
  /**
   * Is the present collection on-the-fly?
   * @return True if the present collection is on-the-fly.
   */
  @Inline
  public boolean isOnTheFlyCollection() {
    return onTheFlyCollection;
  }
}
