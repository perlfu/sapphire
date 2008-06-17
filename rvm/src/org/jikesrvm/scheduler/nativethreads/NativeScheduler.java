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
package org.jikesrvm.scheduler.nativethreads;

import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.scheduler.Lock;
import org.jikesrvm.scheduler.Processor;
import org.jikesrvm.scheduler.Scheduler;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Interruptible;

public class NativeScheduler extends Scheduler {

  @Override
  protected int availableProcessorsInternal() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  protected void dumpVirtualMachineInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean gcEnabledInternal() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  protected void initInternal() {
    // TODO Auto-generated method stub
  }

  @Override
  protected void bootInternal() {
    // TODO Auto-generated method stub
  }

  @Override
  protected void lockOutputInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void requestMutatorFlushInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void scheduleConcurrentCollectorThreadsInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void scheduleFinalizerInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void startDebuggerThreadInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void suspendDebuggerThreadInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void suspendConcurrentCollectorThreadInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void suspendFinalizerThreadInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void sysExitInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void unlockOutputInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void yieldToOtherThreadWaitingOnLockInternal(Lock l) {
    // TODO Auto-generated method stub
  }

  /**
   *  Number of Processors
   */
  @Override
  protected int getNumberOfProcessorsInternal() {
    // TODO Auto-generated method stub
    return 0;
  }

  /**
   *  First Processor
   */
  protected int getFirstProcessorIdInternal() {
    // TODO Auto-generated method stub
    return 0;
  }

  /**
   *  Last Processor
   */
  protected int getLastProcessorIdInternal() {
    // TODO Auto-generated method stub
    return 0;
  }

  /**
   * Get a Processor
   */
  @Override
  protected Processor getProcessorInternal(int id) {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * Is it safe to start forcing garbage collects for stress testing?
   */
  @Override
  protected boolean safeToForceGCsInternal() {
    // TODO Auto-generated method stub
    return false;
  }

  /**
   * Schedule another thread
   */
  @Override
  protected void yieldInternal() {
    // TODO Auto-generated method stub
  }

  /**
   * Set up the initial thread and processors as part of boot image writing
   * @return the boot thread
   */
  @Interruptible
  @Override
  protected RVMThread setupBootThreadInternal() {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * Get the type of the thread (to avoid guarded inlining..)
   */
  @Override
  @Interruptible
  protected TypeReference getThreadTypeInternal() {
    return TypeReference.findOrCreate(NativeThread.class);
  }

  /**
   * Get the type of the processor (to avoid guarded inlining..)
   */
  @Override
  @Interruptible
  protected TypeReference getProcessorTypeInternal() {
    return TypeReference.findOrCreate(NativeProcessor.class);
  }
}
