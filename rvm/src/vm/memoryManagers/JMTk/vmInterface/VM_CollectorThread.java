/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import com.ibm.JikesRVM.memoryManagers.JMTk.Options;
import com.ibm.JikesRVM.memoryManagers.JMTk.Plan;
import com.ibm.JikesRVM.memoryManagers.JMTk.HeapGrowthManager;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_CompiledMethods;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaNoOptCompile;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_SysCall;
import com.ibm.JikesRVM.VM_Registers;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Synchronization;

/**
 * System thread used to preform garbage collections.
 *
 * These threads are created by VM.boot() at runtime startup.  One is
 * created for each VM_Processor that will (potentially) participate
 * in garbage collection.
 *
 * <pre>
 * Its "run" method does the following:
 *    1. wait for a collection request
 *    2. synchronize with other collector threads (stop mutation)
 *    3. reclaim space
 *    4. synchronize with other collector threads (resume mutation)
 *    5. goto 1
 * </pre>
 *
 * Between collections, the collector threads reside on the
 * VM_Scheduler collectorQueue.  A collection in initiated by a call
 * to the static collect() method, which calls VM_Handshake
 * requestAndAwaitCompletion() to dequeue the collector threads and
 * schedule them for execution.  The collection commences when all
 * scheduled collector threads arrive at the first "rendezvous" in the
 * run methods run loop.
 *
 * An instance of VM_Handshake contains state information for the
 * "current" collection.  When a collection is finished, a new
 * VM_Handshake is allocated for the next garbage collection.
 *
 * @see VM_Handshake
 *
 * @author Derek Lieber
 * @author Bowen Alpern 
 * @author Stephen Smith
 * @modified Perry Cheng
 *
 * @version $Revision$
 * @date $Date$
 */ 
public class VM_CollectorThread extends VM_Thread {
  
  /***********************************************************************
   *
   * Class variables
   */
  private final static int verbose = 0;

  /** When true, causes RVM collectors to display heap configuration
   * at startup */
  static final boolean DISPLAY_OPTIONS_AT_BOOT = false;
  
  /**
   * When true, causes RVM collectors to measure time spent in each
   * phase of collection. Will also force summary statistics to be
   * generated.
   */
  public static final boolean TIME_GC_PHASES  = false;

  /**
   * When true, collector threads measure time spent waiting for
   * buffers while processing the Work Queue, and time spent waiting
   * in Rendezvous during the collection process. Will also force
   * summary statistics to be generated.
   */
  public final static boolean MEASURE_WAIT_TIMES = false;
  
  /** array of size 1 to count arriving collector threads */
  public static int[]  participantCount;

  /** maps processor id to assoicated collector thread */
  public static VM_CollectorThread[] collectorThreads; 
  
  /** number of collections */
  public static int collectionCount;
  
  /**
   * The VM_Handshake object that contains the state of the next or
   * current (in progress) collection.  Read by mutators when
   * detecting a need for a collection, and passed to the collect
   * method when requesting a collection.
   */
  public static VM_Handshake handshake;

  /** Use by collector threads to rendezvous during collection */
  public static SynchronizationBarrier gcBarrier;
  

  /***********************************************************************
   *
   * Instance variables
   */
  /** are we an "active participant" in gc? */
  boolean           isActive; 
  /** arrival order of collectorThreads participating in a collection */
  private int       gcOrdinal;

  /** used by each CollectorThread when scanning stacks for references */
  VM_GCMapIteratorGroup iteratorGroup;
  
  /** time waiting in rendezvous (milliseconds) */
  int timeInRendezvous;
  

  /***********************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {
    handshake = new VM_Handshake();
    participantCount = new int[1]; // counter for threads starting a collection
  } 

  /**
   * Constructor
   *
   * @param stack The stack this thread will run on
   * @param isActive Whether or not this thread will participate in GC
   * @param processorAffinity The processor with which this thread is
   * associated.
   */
  VM_CollectorThread(int[] stack, boolean isActive, 
		     VM_Processor processorAffinity)
    throws VM_PragmaInterruptible {
    super(stack);
    makeDaemon(true); // this is redundant, but harmless
    this.isActive          = isActive;
    this.isGCThread        = true;
    this.processorAffinity = processorAffinity;
    this.iteratorGroup     = new VM_GCMapIteratorGroup();

    /* associate this collector thread with its affinity processor */
    collectorThreads[processorAffinity.id] = this;
  }
  
  /**
   * Initialize for boot image.
   */
  public static void  init() throws VM_PragmaInterruptible {
    gcBarrier = new SynchronizationBarrier();
    collectorThreads = new VM_CollectorThread[1 + VM_Scheduler.MAX_PROCESSORS];
  }
  
  /**
   * Record number of processors that will be participating in gc
   * synchronization.  
   * XXX SB: This comment seems bogus
   * 
   * @param numProcessors Unused
   */
  public static void boot(int numProcessors) throws VM_PragmaInterruptible {
    VM_Processor proc = VM_Processor.getCurrentProcessor();
    MM_Interface.setupProcessor(proc);
  }
  
  /**
   * Make a collector thread that will participate in gc.<p>
   *
   * Note: the new thread's stack must be in pinned memory: currently
   * done by allocating it in immortal memory.
   *
   * @param processorAffinity processor to run on
   * @return a new collector thread
   */
  public static VM_CollectorThread createActiveCollectorThread(VM_Processor processorAffinity) 
    throws VM_PragmaInterruptible {
    int[] stack =  MM_Interface.newImmortalStack(STACK_SIZE_COLLECTOR>>2);  /* XXX SB: what is this >>2?  word, address?? */
    return new VM_CollectorThread(stack, true, processorAffinity);
  }
  
  /**
   * Make a collector thread that will not participate in gc. It will
   * serve only to lock out mutators from the current processor.
   *
   * Note: the new thread's stack must be in pinned memory: currently
   * done by allocating it in immortal memory.
   *
   * @param stack stack to run on
   * @param processorAffinity processor to run on
   * @return a new non-particpating collector thread
   */
  static VM_CollectorThread createPassiveCollectorThread(int[] stack,
							 VM_Processor processorAffinity) 
    throws VM_PragmaInterruptible {
    return new VM_CollectorThread(stack, false, processorAffinity);
  }

  /**
   * Initiate a garbage collection.  Called by a mutator thread when
   * its allocator runs out of space.  The caller should pass the
   * VM_Handshake that was referenced by the static variable "collect"
   * at the time space was unavailable.
   *
   * @param handshake VM_Handshake for the requested collection
   */
  public static void collect (VM_Handshake handshake, int why) {
    handshake.requestAndAwaitCompletion(why);
  }
  
  /**
   * Initiate a garbage collection at next GC safe point.  Called by a
   * mutator thread at any time.  The caller should pass the
   * VM_Handshake that was referenced by the static variable
   * "collect".
   *
   * @param handshake VM_Handshake for the requested collection
   */
  public static void asyncCollect(VM_Handshake handshake) 
    throws VM_PragmaUninterruptible {
    handshake.requestAndContinue();
  }

  /** 
   * Override VM_Thread.toString
   *
   * @return A string describing this thread.
   */
  public String toString() throws VM_PragmaUninterruptible {
    return "VM_CollectorThread";
  }

  /**
   * Returns number of collector threads participating in a collection
   *
   * @return The number of collector threads participating in a collection
   */
  public static int numCollectors() throws VM_PragmaUninterruptible {
    return(participantCount[0]);
  }
  
  /**
   * Return the GC ordinal for this collector thread. An integer,
   * 1,2,...  assigned to each collector thread participating in the
   * current collection.  Only valid while GC is "InProgress".
   *
   * @return The GC ordinal
   */
  public final int getGCOrdinal() throws VM_PragmaUninterruptible {
    return gcOrdinal;
  }

  /**
   * Set the GC ordinal for this collector thread.  An integer,
   * 1,2,...  assigned to each collector thread participating in the
   * current collection.
   *
   * @param ord The new GC ordinal for this thread
   */
  public final void setGCOrdinal(int ord) throws VM_PragmaUninterruptible {
    gcOrdinal = ord;
  }

  /**
   * Run method for collector thread (one per VM_Processor).  Enters
   * an infinite loop, waiting for collections to be requested,
   * performing those collections, and then waiting again.  Calls
   * VM_Interface.collect to perform the collection, which will be
   * different for the different allocators/collectors that the RVM
   * can be configured to use.
   */
   public void run()
       throws VM_PragmaNoOptCompile, // refs stored in registers by opt compiler will not be relocated by GC 
	      VM_PragmaLogicallyUninterruptible,  // due to call to snipObsoleteCompiledMethods
	      VM_PragmaUninterruptible {

    for (int count = 0; ; count++) {
      /* suspend this thread: it will resume when scheduled by
       * VM_Handshake initiateCollection().  while suspended,
       * collector threads reside on the schedulers collectorQueue */
      VM_Scheduler.collectorMutex.lock();
      if (verbose >= 1) VM.sysWriteln("GC Message: VM_CT.run yielding");
      if (count > 0) { // resume normal scheduling
	VM_Processor.getCurrentProcessor().enableThreadSwitching();
      }
      VM_Thread.getCurrentThread().yield(VM_Scheduler.collectorQueue,
					 VM_Scheduler.collectorMutex);
      
      /* block mutators from running on the current processor */
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      
      if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run waking up");

      gcOrdinal = VM_Synchronization.fetchAndAdd(participantCount, 0, 1) + 1;
      long startCycles = VM_Time.cycles();
      
      if (verbose > 2) VM.sysWriteln("GC Message: VM_CT.run entering first rendezvous - gcOrdinal =", gcOrdinal);

      /* the first RVM VP will wait for all the native VPs not blocked
       * in native to reach a SIGWAIT state */
      if (gcOrdinal == 1) {
        if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run quiescing native VPs");
        for (int i = 1; i <= VM_Processor.numberNativeProcessors; i++) {
          if (VM_Processor.vpStatus[VM_Processor.nativeProcessors[i].vpStatusIndex] != VM_Processor.BLOCKED_IN_NATIVE) {
	    if (verbose >= 2) VM.sysWriteln("GC Mesage: VM_CollectorThread.run found one not blocked in native");
	    
	    while (VM_Processor.vpStatus[VM_Processor.nativeProcessors[i].vpStatusIndex] != VM_Processor.IN_SIGWAIT) {
	      if (verbose >= 2)
		VM.sysWriteln("GC Message: VM_CT.run  WAITING FOR NATIVE PROCESSOR", i,
			      " with vpstatus = ",
			      VM_Processor.vpStatus[VM_Processor.nativeProcessors[i].vpStatusIndex]);
	      VM.sysVirtualProcessorYield();
	    }
	    /* Note: threads contextRegisters (ip & fp) are set by
	     * thread before entering SIGWAIT, so nothing needs to be
	     * done here */
	  } else {
	    /* BLOCKED_IN_NATIVE - set context regs so that scan of
	     * threads stack will start at the java to C transition
	     * frame. ip is not used for these frames, so can be set
	     * to 0. */
	    VM_Thread t = VM_Processor.nativeProcessors[i].activeThread;
	    t.contextRegisters.setInnermost( VM_Address.zero() /*ip*/, t.jniEnv.topJavaFP() );
	  }
        }

	/* quiesce any attached processors, blocking then IN_NATIVE or
	 * IN_SIGWAIT */
	quiesceAttachedProcessors();
      }  // gcOrdinal==1

      /* wait for other collector threads to arrive or be made
       * non-participants */
      if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run  initializing rendezvous");
      gcBarrier.startupRendezvous();

      /* actually perform the GC... */
      if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run  starting collection");
      if (isActive) VM_Interface.getPlan().collect(); // gc
      if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run  finished collection");
      
      gcBarrier.rendezvous(5200);

      if (gcOrdinal == 1) {
	long elapsedCycles = VM_Time.cycles() - startCycles;
	HeapGrowthManager.recordGCTime(VM_Time.cyclesToMillis(elapsedCycles));
      }
      if (gcOrdinal == 1 && Plan.isLastGCFull()) {
        boolean heapSizeChanged = false;
	if (Options.variableSizeHeap && handshake.gcTrigger != VM_Interface.EXTERNAL_GC_TRIGGER) {
	  // Don't consider changing the heap size if gc was forced by System.gc()
	  heapSizeChanged = HeapGrowthManager.considerHeapSize();
	}
	HeapGrowthManager.reset();
      } 

      /* Wake up mutators waiting for this gc cycle and reset
       * the handshake object to be used for next gc cycle.
       * Note that mutators will not run until after thread switching
       * is enabled, so no mutators can possibly arrive at old
       * handshake object: it's safe to replace it with a new one. */
      if (gcOrdinal == 1) {
	/* Snip reference to any methods that are still marked
	 * obsolete after we've done stack scans. This allows
	 * reclaiming them on next GC. */
	VM_CompiledMethods.snipObsoleteCompiledMethods();

	collectionCount += 1;

	/* notify mutators waiting on previous handshake object -
	 * actually we don't notify anymore, mutators are simply in
	 * processor ready queues waiting to be dispatched. */
	handshake.notifyCompletion();
	handshake.reset();

	/* schedule the FinalizerThread, if there is work to do & it is idle */
	VM_Interface.scheduleFinalizerThread();
      } 
      
      /* wait for other collector threads to arrive here */
      gcBarrier.rendezvous(5210);
      if (verbose > 2) VM.sysWriteln("VM_CollectorThread: past rendezvous 1 after collection");

      /* final cleanup for initial collector thread */
      if (gcOrdinal == 1) {
	/* unblock any native processors executing in native that were
	 * blocked in native at the start of GC */
	if (verbose > 3) VM.sysWriteln("VM_CollectorThread: unblocking native procs");
	for (int i = 1; i <= VM_Processor.numberNativeProcessors; i++) {
	  VM_Processor vp = VM_Processor.nativeProcessors[i];
	  if (VM.VerifyAssertions) VM._assert(vp != null);
	  if (VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.BLOCKED_IN_NATIVE ) {
	    VM_Processor.vpStatus[vp.vpStatusIndex] = VM_Processor.IN_NATIVE;
	    if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run unblocking Native Processor", vp.id);
	  }
	}

	/* It is VERY unlikely, but possible that some RVM processors
	 * were found in C, and were BLOCKED_IN_NATIVE, during the
	 * collection, and now need to be unblocked. */
	if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run unblocking native procs blocked during GC");
	for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
	  VM_Processor vp = VM_Scheduler.processors[i];
	  if (VM.VerifyAssertions) VM._assert(vp != null);
	  if (VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.BLOCKED_IN_NATIVE ) {
	    VM_Processor.vpStatus[vp.vpStatusIndex] = VM_Processor.IN_NATIVE;
	    if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run unblocking RVM Processor", vp.id);
	  }
	}
	
	if (!VM.BuildForSingleVirtualProcessor) {
	  /* if NativeDaemonProcessor was BLOCKED_IN_SIGWAIT, unblock it */
	  VM_Processor ndvp = VM_Scheduler.processors[VM_Scheduler.nativeDPndx];
	  if (ndvp!=null && VM_Processor.vpStatus[ndvp.vpStatusIndex] == VM_Processor.BLOCKED_IN_SIGWAIT) {
	    VM_Processor.vpStatus[ndvp.vpStatusIndex] = VM_Processor.IN_SIGWAIT;
	    if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run unblocking Native Daemon Processor");
	  }
	  /* resume any attached Processors blocked prior to Collection */
	  resumeAttachedProcessors();
	}

	/* clear the GC flag */
	if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run clearing lock out field");
	VM_Magic.setIntAtOffset(VM_BootRecord.the_boot_record, VM_Entrypoints.lockoutProcessorField.getOffset(), 0);
      }

      if (gcOrdinal == 1)
	Plan.collectionComplete();

    }  // end of while(true) loop
    
  }  // run
  
  /**
   * Return true if no threads are still in GC.  We do this by
   * checking whether the GC lockout field has been cleared.
   *
   * @return <code>true</code> if no threads are still in GC.
   */
  static boolean noThreadsInGC() throws VM_PragmaUninterruptible {
    return VM_Magic.getIntAtOffset(VM_BootRecord.the_boot_record, VM_Entrypoints.lockoutProcessorField.getOffset()) == 0;
  }

  /**
   * If there are any attached processors (for user pthreads that
   * entered the VM via an AttachJVM) we may be briefly
   * <code>IN_JAVA</code> during transitions to a RVM
   * processor. Usually they are either <code>IN_NATIVE</code> (having
   * returned to the users native C code - or invoked a native method)
   * or <code>IN_SIGWAIT</code> (the native code invoked a JNI
   * Function, migrated to a RVM processor, leaving the attached
   * processor running its IdleThread, which enters
   * <code>IN_SIGWAIT</code> and wait for a signal. <p>
   *
   * We wait for the processor to be in either <code>IN_NATIVE</code>
   * or <code>IN_SIGWAIT<code> and then block it there for the
   * duration of the collection
   */
  static void quiesceAttachedProcessors() throws VM_PragmaUninterruptible {

    if (VM_Processor.numberAttachedProcessors == 0) return;

    if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.quiesceAttachedProcessors  quiescing attached VPs");
    
    for (int i = 1; i < VM_Processor.attachedProcessors.length; i++) {
      VM_Processor vp = VM_Processor.attachedProcessors[i];
      if (vp==null) continue;   // must have detached
      if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.quiesceAttachedProcessors  quiescing attached VP", i);
      
      int loopCount = 0;
      while (true) {
	while (VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.IN_JAVA)
	  VM.sysVirtualProcessorYield();
	
	if (vp.blockInWaitIfInWait()) {
	  if (verbose >= 2)
	    VM.sysWriteln("GC Message: VM_CT.quiesceAttachedProcessors  Attached Processor BLOCKED_IN_SIGWAIT", i);
	  /* Note: threads contextRegisters (ip & fp) are set by
	   * thread before entering SIGWAIT, so nothing needs to be
	   * done here */
	  break;
	}
	if (vp.lockInCIfInC()) {
	  if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.quiesceAttachedProcessors Attached Processor BLOCKED_IN_NATIVE", i);
	  
	  /* XXX SES TON XXX */
	  /* TON !! what is in jniEnv.JNITopJavaFP when thread returns
	  to user C code.  AND what will happen when we scan its stack
	  with that fp set running threads context regs ip & fp to
	  where scan of threads stack should start. */
	  VM_Thread at = vp.activeThread;
	  at.contextRegisters.setInnermost(VM_Address.zero() /*ip*/, at.jniEnv.topJavaFP());
	  break;
	}
	
	loopCount++;
	if (verbose >= 2 && (loopCount%10 == 0)) {
	  VM.sysWriteln("GC Message: VM_CollectorThread Waiting for Attached Processor", i, " with vpstatus ", VM_Processor.vpStatus[vp.vpStatusIndex]);
	}
	if (loopCount%1000 == 0) {
	  VM.sysWriteln("GC Message: VM_CT.quiesceAttachedProcessors STUCK Waiting for Attached Processor", i);
	  VM.sysFail("VM_CollectorThread - STUCK quiescing attached processors");
	}
      }  // while (true)
    }  // end loop over attachedProcessors[]
  }  // quiesceAttachedProcessors

  /**
   * Any attached processors were quiesced in either
   * <code>BLOCKED_IN_SIGWAIT</code> or
   * <code>BLOCKED_IN_NATIVE</code>.  Unblock them.
   */
  static void resumeAttachedProcessors() throws VM_PragmaUninterruptible {
    if (VM_Processor.numberAttachedProcessors == 0) return;

    if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.resumeAttachedProcessors  resuming attached VPs");
    
    for (int i = 1; i < VM_Processor.attachedProcessors.length; i++) {
      VM_Processor vp = VM_Processor.attachedProcessors[i];
      if (vp==null) continue;   // must have detached
      if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.resumeAttachedProcessors  resuming attached VP", i);

      if (VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.BLOCKED_IN_NATIVE ) {
	VM_Processor.vpStatus[vp.vpStatusIndex] = VM_Processor.IN_NATIVE;
	if (verbose >= 2) VM.sysWriteln("GC Message:  VM_CollectorThread.resumeAttachedProcessors resuming Processor IN_NATIVE", i);
	continue;
      }
      
      if (VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.BLOCKED_IN_SIGWAIT ) {
	/* first unblock the processor */
	vp.vpStatus[vp.vpStatusIndex] = VM_Processor.IN_SIGWAIT;
	/* then send signal */
	VM_SysCall.sysPthreadSignal(vp.pthread_id);
	continue;
      }
      
      /* should not reach here: system error: */
      VM.sysWriteln("GC Message: VM_CT.resumeAttachedProcessors  ERROR VP not BLOCKED", i, " vpstatus ", VM_Processor.vpStatus[vp.vpStatusIndex]);
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }  // end loop over attachedProcessors[]
  }  // resumeAttachedProcessors
  
  
  public int rendezvous(int where) throws VM_PragmaUninterruptible {
    return gcBarrier.rendezvous(where);
  }
  
  /*
  public static void printThreadWaitTimes() throws VM_PragmaUninterruptible {
    VM.sysWrite("*** Collector Thread Wait Times (in micro-secs)\n");
    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
      VM_CollectorThread ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.processors[i].activeThread );
      VM.sysWrite(i);
      VM.sysWrite(" SBW ");
      if (ct.bufferWaitCount1 > 0)
	VM.sysWrite(ct.bufferWaitCount1-1);  // subtract finish wait
      else
	VM.sysWrite(0);
      VM.sysWrite(" SBWT ");
      VM.sysWrite(ct.bufferWaitTime1*1000000.0);
      VM.sysWrite(" SFWT ");
      VM.sysWrite(ct.finishWaitTime1*1000000.0);
      VM.sysWrite(" FBW ");
      if (ct.bufferWaitCount > 0)
	VM.sysWrite(ct.bufferWaitCount-1);  // subtract finish wait
      else
	VM.sysWrite(0);
      VM.sysWrite(" FBWT ");
      VM.sysWrite(ct.bufferWaitTime*1000000.0);
      VM.sysWrite(" FFWT ");
      VM.sysWrite(ct.finishWaitTime*1000000.0);
      VM.sysWriteln();

    }
  }
  */
}
