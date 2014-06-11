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
package org.jikesrvm.mm.mmtk;

import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.utility.options.Options;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.mm.mminterface.Selected;
import org.jikesrvm.mm.mminterface.CollectorThread;
import org.jikesrvm.runtime.SysCall;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.scheduler.FinalizerThread;

import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Address;

@Uninterruptible
public class Collection extends org.mmtk.vm.Collection implements org.mmtk.utility.Constants,
                                                                  org.jikesrvm.Constants {

  /****************************************************************************
   *
   * Class variables
   */
  protected static short onTheFlyPhase = 0;

  @Inline
  public static short getOnTheFlyPhase() {
    return onTheFlyPhase;
  }
  
  /**
   * {@inheritDoc}
   */
  @Override
  @Interruptible
  public void spawnCollectorContext(CollectorContext context) {
    byte[] stack = MemoryManager.newStack(ArchitectureSpecific.StackframeLayoutConstants.STACK_SIZE_COLLECTOR);
    CollectorThread t = new CollectorThread(stack, context);
    t.start();
  }

  @Override
  public int getDefaultThreads() {
    return SysCall.sysCall.sysNumProcessors();
  }

  @Override
  public int getActiveThreads() {
    return RVMThread.getNumActiveThreads() - RVMThread.getNumActiveDaemons();
  }

  @Override
  @Unpreemptible
  public void blockForGC() {
    // poll has advised that a GC is required, block the current thread
    // a concurrent GC may resume this thread later before the GC is actually complete
    RVMThread t = RVMThread.getCurrentThread();
    t.assertAcceptableStates(RVMThread.IN_JAVA, RVMThread.IN_JAVA_TO_BLOCK);
    RVMThread.observeExecStatusAtSTW(t.getExecStatus());
    if (Options.verbose.getValue() >= 8) VM.sysWriteln("Thread # about to block for GC ", t.threadSlot);
    RVMThread.getCurrentThread().block(RVMThread.gcBlockAdapter);
    if (Options.verbose.getValue() >= 8) VM.sysWriteln("Thread # back from blocking for GC ", t.threadSlot);
  }

  public boolean isBlockedForGC(MutatorContext m) {
    RVMThread t = ((Selected.Mutator) m).getThread();
    if (RVMThread.gcBlockAdapter.isBlocked(t)) {
      if (Options.verbose.getValue() >= 8) VM.sysWriteln("STW Mutator phase iterating over mutator thread ", t.threadSlot);
      return true;
    }
    if (t.isTimerThread()) {
      if (Options.verbose.getValue() >= 8) VM.sysWriteln("STW Mutator phase iterating over timer thread ", t.threadSlot);
      return true;
    } else if (t.isCollectorThread()) {
      if (Options.verbose.getValue() >= 8) VM.sysWriteln("STW Mutator phase iterating over collector thread ", t.threadSlot);
      return true;
    } else if (RVMThread.notRunning(t.getExecStatus())) {
      if (Options.verbose.getValue() >= 8) VM.sysWriteln("STW Mutator phase iterating over NEW thread");
      return true;
    } else {
      if (Options.verbose.getValue() >= 8) VM.sysWriteln("STW Mutator phase found a thread of unknown type or in wrong state ", t.threadSlot);
    }
    return false;
  }

  /***********************************************************************
   *
   * Initialization
   */

  /**
   * {@inheritDoc}
   */
  @Override
  @UninterruptibleNoWarn
  public void outOfMemory() {
    throw RVMThread.getOutOfMemoryError();
  }

  @Override
  public final void prepareMutator(MutatorContext m) {
    /*
     * The collector threads of processors currently running threads
     * off in JNI-land cannot run.
     */
    RVMThread t = ((Selected.Mutator) m).getThread();
    t.monitor().lockNoHandshake();
    // are these the only unexpected states?
    t.assertUnacceptableStates(RVMThread.IN_JNI,RVMThread.IN_NATIVE);
    int execStatus = t.getExecStatus();
    // these next assertions are not redundant given the ability of the
    // states to change asynchronously, even when we're holding the lock, since
    // the thread may change its own state.  of course that shouldn't happen,
    // but having more assertions never hurts...
    if (VM.VerifyAssertions) VM._assert(execStatus != RVMThread.IN_JNI);
    if (VM.VerifyAssertions) VM._assert(execStatus != RVMThread.IN_NATIVE);
    if (execStatus == RVMThread.BLOCKED_IN_JNI) {
      if (Options.verbose.getValue() >= 8) {
        VM.sysWriteln("prepareMutator for thread #", t.getThreadSlot(), " setting up JNI stack scan");
        VM.sysWriteln("thread #",t.getThreadSlot()," has top java fp = ",t.getJNIEnv().topJavaFP());
      }

      /* thread is blocked in C for this GC.
       Its stack needs to be scanned, starting from the "top" java
       frame, which has been saved in the running threads JNIEnv.  Put
       the saved frame pointer into the threads saved context regs,
       which is where the stack scan starts. */
      t.contextRegisters.setInnermost(Address.zero(), t.getJNIEnv().topJavaFP());
    }
    t.contextRegistersLastPreparation.copyFrom(t.contextRegisters);
    t.monitor().unlock();
  }

  @Override
  @Unpreemptible
  public void stopAllMutators() {
    RVMThread.blockAllMutatorsForGC();
  }

  @Override
  @Unpreemptible
  public void resumeAllMutators() {
    RVMThread.unblockAllMutatorsForGC();
  }

  private static RVMThread.SoftHandshakeVisitor mutatorFlushVisitor =
    new RVMThread.SoftHandshakeVisitor() {
      @Override
      @Uninterruptible
      public boolean checkAndSignal(RVMThread t) {
        t.flushRequested = true;
        return true;
      }
      @Override
      @Uninterruptible
      public void notifyStuckInNative(RVMThread t) {
        t.flush();
        t.flushRequested = false;
      }
    };

  /**
   * Wait for running mutator threads to go past GC safe point
   * Nested handshake is not allowed
   */
  @UninterruptibleNoWarn
  public void requestSoftHandshake(org.mmtk.vm.Collection.SoftHandshakeVisitor v) {
    softHandshakeVisitorAdapter.set(v);
    RVMThread.softHandshake(softHandshakeVisitorAdapter);
    softHandshakeVisitorAdapter.clear();
  }

  private static class SoftHandshakeVisitorAdapter extends RVMThread.SoftHandshakeVisitor {
    private org.mmtk.vm.Collection.SoftHandshakeVisitor v;

    @Uninterruptible
    public void set(org.mmtk.vm.Collection.SoftHandshakeVisitor v) {
      if (VM.VerifyAssertions) VM._assert(this.v == null);
      this.v = v;
    }

    @Uninterruptible
    public void clear() {
      if (VM.VerifyAssertions) this.v = null;
    }

    @Uninterruptible
    public boolean checkAndSignal(RVMThread t) {
      return v.checkAndSignal(t);
    }

    @Uninterruptible
    public void notifyStuckInNative(RVMThread t) {
      v.notifyStuckInNative(t);
    }

    @Uninterruptible
    public boolean includeThread(RVMThread t) {
      if (t.isCollectorThread()) return false;
      return v.includeThread(t);
    }
  }

  private static SoftHandshakeVisitorAdapter softHandshakeVisitorAdapter = new SoftHandshakeVisitorAdapter();

  @UninterruptibleNoWarn
  public void requestMutatorOnTheFlyProcessPhase(short phaseId) {
    if (VM.VerifyAssertions)
      VM._assert(RVMThread.getCurrentThread().isCollectorThread(), "Designed to be called by a collector thread");
    onTheFlyPhase = phaseId;
    RVMThread.softHandshake(mutatorOnTheFlyProcessVisitor);
    onTheFlyPhase = 0;
  }

  private static RVMThread.SoftHandshakeVisitor mutatorOnTheFlyProcessVisitor = new RVMThread.SoftHandshakeVisitor() {
    @UninterruptibleNoWarn
    public boolean checkAndSignal(RVMThread t) {
      if (Options.verbose.getValue() >= 8) VM.sysWriteln("Requesting async process of on-the-fly mutator phase");
      t.mutatorProcessPhase = true;
      return true;
    }

    @Uninterruptible
    public void notifyStuckInNative(RVMThread t) {
      if (Options.verbose.getValue() >= 8) VM.sysWriteln("Performing process collectionPhase on behalf of blocked thread");
      t.collectionPhase(getOnTheFlyPhase(), false);
      t.mutatorProcessPhase = false;
    }

    @Uninterruptible
    public boolean includeThread(RVMThread t) {
      return !t.isCollectorThread();
    }
  };

  @Override
  @UninterruptibleNoWarn("This method is really unpreemptible, since it involves blocking")
  public void requestMutatorFlush() {
    Selected.Mutator.get().flush();
    RVMThread.softHandshake(mutatorFlushVisitor);
  }

  /***********************************************************************
   *
   * Finalizers
   */

  /**
   * Schedule the finalizerThread, if there are objects to be
   * finalized and the finalizerThread is on its queue (ie. currently
   * idle).  Should be called at the end of GC after moveToFinalizable
   * has been called, and before mutators are allowed to run.
   */
  @Uninterruptible
  public static void scheduleFinalizerThread() {
    int finalizedCount = FinalizableProcessor.countReadyForFinalize();
    if (finalizedCount > 0) {
      FinalizerThread.schedule();
    }
  }
}

