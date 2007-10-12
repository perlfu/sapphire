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
package org.jikesrvm.memorymanagers.mminterface;

import org.jikesrvm.VM;
import org.jikesrvm.mm.mmtk.Collection;
import org.jikesrvm.mm.mmtk.Lock;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.greenthreads.VM_GreenScheduler;
import org.jikesrvm.scheduler.greenthreads.VM_GreenThread;
import org.vmmagic.pragma.LogicallyUninterruptible;
import org.vmmagic.pragma.Uninterruptible;

/**
 * VM_Handshake handles mutator requests to initiate a collection, and
 * wait for a collection to complete.  It implements the process of
 * suspending all mutator threads executing in Java and starting all
 * the GC threads (VM_CollectorThreads) for the processors that will
 * be participating in a collection.  This may not be all processors,
 * if we exclude those executing in native code.
 *
 * Because the threading strategy within RVM is currently under
 * revision, the logic here is also changing and somewhat "messy".
 *
 * @see VM_CollectorThread
 */
public class VM_Handshake {

  /***********************************************************************
   *
   * Class variables
   */
  public static final int verbose = 0;
  static final int LOCKOUT_GC_WORD = 0x0CCCCCCC;

  /***********************************************************************
   *
   * Instance variables
   */
  private Lock lock = new Lock("handshake");
  protected boolean requestFlag;
  protected boolean completionFlag;
  public int gcTrigger;  // reason for this GC

  public VM_Handshake() {
    reset();
  }

  /**
   * Called by mutators to request a garbage collection and wait for
   * it to complete.
   *
   * Waiting is actually just yielding the processor to schedule the
   * collector thread, which will disable further thread switching on
   * the processor until it has completed the collection.
   */
  @LogicallyUninterruptible
  @Uninterruptible
  public void requestAndAwaitCompletion(int why) {
    if (request(why)) {
      if (verbose >= 1) VM.sysWriteln("GC Message: VM_Handshake.requestAndAwaitCompletion - yielding");
      /* allow a gc thread to run */
      VM_Scheduler.yield();
      complete();
      if (verbose >= 1) VM.sysWriteln("GC Message: VM_Handshake.requestAndAwaitCompletion - mutator running");
    }
  }

  /**
   * Called by mutators to request an asynchronous garbage collection.
   * After initiating a GC (if one is not already initiated), the
   * caller continues until it yields to the GC.  It may thus make
   * this call at an otherwise unsafe point.
   */
  @Uninterruptible
  public void requestAndContinue(int why) {
    request(why);
  }

  @Uninterruptible
  public void reset() {
    gcTrigger = Collection.UNKNOWN_GC_TRIGGER;
    requestFlag = false;
    completionFlag = false;
  }

  /**
   * Initiates a garbage collection.  Called from requestAndAwaitCompletion
   * by the first mutator thread to request a collection using the
   * current VM_Handshake object.
   *
   * The sequence of events that start a collection is initiated by the
   * calling mutator, and it then yields, waiting for the collection
   * to complete.
   *
   * While mutators are executing, all the GC threads (VM_CollectorThreads)
   * reside on a single system queue, VM_Scheduler.collectorQueue.  This
   * method determines which processors will participate in the collection,
   * dequeues the GC threads associated with those processors, and
   * schedules them for executing on their respective processors.
   * (Most of the time, all processors and thus all GC threads participate,
   * but this is changing as the RVM thread startegy changes.)
   *
   * The collection actually starts when all participating GC threads
   * arrive at the first rendezvous in VM_CollectorThreads run method,
   * and suspend thread switching on their processors.
   *
   * While collection is in progress, mutators are not explicitly waiting
   * for the collection. They reside in the thread dispatch queues of their
   * processors, until the collector threads re-enable thread switching.
   */
  @Uninterruptible
  private void initiateCollection() {

    /* check that scheduler initialization is complete */
    if (!VM_GreenScheduler.allProcessorsInitialized) {
      VM.sysWrite("GC required before system fully initialized");
      VM.sysWriteln("Specify larger than default heapsize on command line");
      VM_Scheduler.dumpStack();
      VM.shutdown(VM.EXIT_STATUS_MISC_TROUBLE);
    }

    /* wait for preceding GC to complete */
    if (verbose >= 2) {
      VM.sysWrite("GC Message: VM_Handshake.initiateCollection before waiting");
      VM_GreenScheduler.collectorQueue.dump();
    }
    int maxCollectorThreads = waitForPrecedingGC();

    /* Acquire global lockout field inside the boot record.  Will be
     * released when gc completes. */
    VM_CollectorThread.gcThreadRunning = true;

    /* reset counter for collector threads arriving to participate in
     * the collection */
    VM_CollectorThread.participantCount[0] = 0;

    /* reset rendezvous counters to 0, the decision about which
     * collector threads will participate has moved to the run method
     * of CollectorThread */
    VM_CollectorThread.gcBarrier.resetRendezvous();

    /* Deque and schedule collector threads on ALL RVM Processors.
     */
    if (verbose >= 1) {
      VM.sysWriteln("GC Message: VM_Handshake.initiateCollection: scheduling collector threads");
    }
    VM_GreenScheduler.collectorMutex.lock("handshake collector mutex");
    if (VM_GreenScheduler.collectorQueue.length() != maxCollectorThreads) {
      VM.sysWriteln("GC Error: Expected ",
                    maxCollectorThreads,
                    " GC threads.   Found ",
                    VM_GreenScheduler.collectorQueue.length());
    }
    while (VM_GreenScheduler.collectorQueue.length() > 0) {
      VM_GreenThread t = VM_GreenScheduler.collectorQueue.dequeue();
      t.schedule();
    }
    VM_GreenScheduler.collectorMutex.unlock();
  }

  /**
   * Wait for all GC threads to complete previous collection cycle.
   *
   * @return The number of GC threads.
   */
  @Uninterruptible
  private int waitForPrecedingGC() {
    /*
     * Get the number of GC threads.  Include NativeDaemonProcessor
     * collector thread in the count.  If it exists, check for null to
     * allow builds without a NativeDaemon (see VM_Scheduler)
     */
    int maxCollectorThreads = VM_GreenScheduler.numProcessors;

    /* Wait for all gc threads to finish preceeding collection cycle */
    if (verbose >= 1) {
      VM.sysWrite("GC Message: VM_Handshake.initiateCollection ");
      VM.sysWriteln("checking if previous collection is finished");
    }
    int count = 0;
    while (true) {
      VM_GreenScheduler.collectorMutex.lock("waiting for preceeding GC");
      int len = VM_GreenScheduler.collectorQueue.length();
      if (count++ == 100000) {
        VM.sysWriteln("GC Warning: WAITED LONG TIME FOR PRECEEDING GC TO FINISH");
        VM.sysWriteln("GC Warning:          len = ", len);
        VM.sysWriteln("GC Warning:    maxCollTh = ", maxCollectorThreads);
        // VM_Scheduler.collectorQueue.dump();
      }
      VM_GreenScheduler.collectorMutex.unlock();
      if (len < maxCollectorThreads) {
        if (verbose >= 1) {
          VM.sysWrite("GC Message: VM_Handshake.initiateCollection waiting for previous collection to finish");
        }
        lock.release();   // release lock so other threads can make progress
        VM_Scheduler.yield();
        lock.acquire();   // acquire lock to make progress
      } else {
        break;
      }
    }
    return maxCollectorThreads;
  }

  @Uninterruptible
  private void complete() {
    for (int i = 1; i <= VM_GreenScheduler.numProcessors; i++) {
      VM_GreenScheduler.processors[i].unblockIfBlockedInC();
    }
  }

  /**
   * Called by mutators to request a garbage collection.  If the
   * completionFlag is already set, return false.  Else, if the
   * requestFlag is not yet set (ie this is the first mutator to
   * request this collection) then initiate the collection sequence
   *
   * @return true if the completion flag is not already set.
   */
  @Uninterruptible
  private boolean request(int why) {
    lock.acquire();
    if (completionFlag) {
      if (verbose >= 1) {
        VM.sysWriteln("GC Message: mutator: already completed");
      }
      lock.release();
      return false;
    }
    if (why > gcTrigger) gcTrigger = why;
    if (requestFlag) {
      if (verbose >= 1) {
        VM.sysWriteln("GC Message: mutator: already in progress");
      }
    } else {
      // first mutator initiates collection by making all gc threads
      // runnable at high priority
      if (verbose >= 1) VM.sysWriteln("GC Message: VM_Handshake - mutator: initiating collection");
      requestFlag = true;
      initiateCollection();
    }
    lock.release();
    return true;
  }

  /**
   * Set the completion flag that indicates the collection has
   * completed.  Called by a collector thread after the collection has
   * completed.  It currently does not do a "notify" on waiting
   * mutator threads, since they are in VM_Processor thread queues,
   * waiting for the collector thread to re-enable thread switching.
   *
   * @see VM_CollectorThread
   */
  @Uninterruptible
  void notifyCompletion() {
    lock.acquire();
    if (verbose >= 1) {
      VM.sysWriteln("GC Message: VM_Handshake.notifyCompletion");
    }
    complete();
    completionFlag = true;
    lock.release();
  }
}


