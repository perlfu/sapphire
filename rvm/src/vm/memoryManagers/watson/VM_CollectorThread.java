/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * System thread used to preform garbage collections.
 *
 * These threads are created by VM.boot() at runtime startup.  One is created
 * for each VM_Processor that will (potentially) participate in garbage collection.
 * <pre>
 * Its "run" method does the following:
 *    1. wait for a collection request
 *    2. synchronize with other collector threads (stop mutation)
 *    3. reclaim space
 *    4. synchronize with other collector threads (resume mutation)
 *    5. goto 1
 * </pre>
 * Between collections, the collector threads reside on the VM_Scheduler
 * collectorQueue.  A collection in initiated by a call to the static collect()
 * method, which calls VM_Handshake requestAndAwaitCompletion() to dequeue
 * the collector threads and schedule them for execution.  The collection 
 * commences when all scheduled collector threads arrive at the first
 * "rendezvous" in the run methods run loop.
 *
 * An instance of VM_Handshake contains state information for the "current"
 * collection.  When a collection is finished, a new VM_Handshake is allocated
 * for the next garbage collection.
 *
 * @see VM_Handshake
 *
 * @author Derek Lieber
 * @author Bowen Alpern 
 * @author Stephen Smith
 */ 
class VM_CollectorThread extends VM_Thread
  implements VM_Uninterruptible, VM_GCConstants {

  private final static boolean debug_native = false;
  
  private final static boolean trace = false; // emit trace messages?

  /** When true, causes RVM collectors to display heap configuration at startup */
  static final boolean DISPLAY_OPTIONS_AT_BOOT = false;
  
  /**
   * When true, causes RVM collectors to measure time spent in each phase of
   * collection. Will also force summary statistics to be generated.
   */
  static final boolean TIME_GC_PHASES  = false;

  /**
   * When true, collector threads measure time spent waiting for buffers while
   * processing the Work Queue, and time spent waiting in Rendezvous during
   * the collection process. Will also force summary statistics to be generated.
   */
  final static boolean MEASURE_WAIT_TIMES = false;
  
  /** Measure & print entry & exit times for rendezvous */
  final static boolean RENDEZVOUS_TIMES = false;

  /** for measuring times in GC - set in VM_Handshake.initiateCollection */
  static double startTime;

  static int[]  participantCount;    // array of size 1 to count arriving collector threads

  static VM_CollectorThread[] collectorThreads;   // maps processor id to assoicated collector thread
  
  // following used when RENDEZVOUS_TIMES is on
  static int rendezvous1in[] = null;
  static int rendezvous1out[] = null;
  static int rendezvous2in[] = null;
  static int rendezvous2out[] = null;
  static int rendezvous3in[] = null;
  static int rendezvous3out[] = null;
  
  static int collectionCount;             // number of collections
  
  /**
   * The VM_Handshake object that contains the state of the next or current
   * (in progress) collection.  Read by mutators when detecting a need for
   * a collection, and passed to the collect method when requesting a
   * collection.
   */
  static VM_Handshake                       collect;

  /** Use by collector threads to rendezvous during collection */
  static VM_GCSynchronizationBarrier gcBarrier;
  
  static {
    //-#if RVM_WITH_CONCURRENT_GC
    collect = new VM_RCHandshake();
    //-#else
    collect = new VM_Handshake();
    //-#endif

    participantCount = new int[1];  // counter for threads starting a collection
  } 
  
  /**
   * Initialize for boot image. Should be called from VM_Allocator.init() 
   */
  static void  init() {
    gcBarrier = new VM_GCSynchronizationBarrier();

    collectorThreads = new VM_CollectorThread[ 1 + VM_Scheduler.MAX_PROCESSORS ];

    if (RENDEZVOUS_TIMES) {
      rendezvous1in =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
      rendezvous1out =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
      rendezvous2in =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
      rendezvous2out =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
      rendezvous3in =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
      rendezvous3out =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
    }
  }
  
  /**
   * Initiate a garbage collection. 
   * Called by a mutator thread when its allocator runs out of space.
   * The caller should pass the VM_Handshake that was referenced by the
   * static variable "collect" at the time space was unavailable.
   *
   * @param handshake VM_Handshake for the requested collection
   */
  static void  collect(VM_Handshake handshake) {
    
    if (trace) {
      double start = VM_Time.now();
      handshake.requestAndAwaitCompletion();
      double stop  = VM_Time.now();
      VM.sysWrite("VM_CollectorThread.collect: " + (stop - start) + " seconds\n");
    }
    else
      handshake.requestAndAwaitCompletion();
  }
  
  // FOLLOWING NO LONGER TRUE... we now scan the stack frame for the run method,
  // so references will get updated, and "getThis" should not be necessary.
  // 
  // The gc algorithm currently doesn't scan collector stacks, so we cannot use
  // local variables (eg. "this"). This method is a temporary workaround
  // until gc scans gc stacks too. Eventually we should be able to
  // replace "getThis()" with "this" and everything should work ok.
  //
  // Use this to access all instance variables:
  //
  private static VM_CollectorThread  getThis() {
    return VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
  }
  
  // overrides VM_Thread.toString
  public String toString() {
    return "VM_CollectorThread";
  }

  // returns number of collector threads participating in a collection
  //
  static int numCollectors() {
    return( participantCount[0] );
  }

  /**
   * Return the GC ordinal for this collector thread. A integer, 1,2,...
   * assigned to each collector thread participating in the current
   * collection.  Only valid while GC is "InProgress".
   *
   * @return The GC ordinal
   */
  public final int getGCOrdinal() {
    return gcOrdinal;
  }

  /**
   * Run method for collector thread (one per VM_Processor).
   * Enters an infinite loop, waiting for collections to be requested,
   * performing those collections, and then waiting again.
   * Calls VM_Allocator.collect to perform the collection, which
   * will be different for the different allocators/collectors
   * that the RVM can be configured to use.
   */
  public void run() {
    int mypid;   // id of processor thread is running on - constant for the duration
    // of each collection - actually should always be id of associated processor
    
    //  make sure Opt compiler does not compile this method
    //  references stored in registers by the opt compiler will not be relocated by GC
    VM_Magic.pragmaNoOptCompile();
    
    while (true) {
      
      // suspend this thread: it will resume when scheduled by VM_Handshake 
      // initiateCollection().  while suspended, collector threads reside on
      // the schedulers collectorQueue
      //
      VM_Scheduler.collectorMutex.lock();
      VM_Thread.getCurrentThread().yield(VM_Scheduler.collectorQueue,
					 VM_Scheduler.collectorMutex);
      
      // block mutators from running on the current processor
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      
      if (trace) VM_Scheduler.trace("VM_CollectorThread", "waking up");

      // record time it took to stop mutators on this processor and get this
      // collector thread dispatched
      //
      if (MEASURE_WAIT_TIMES) stoppingTime = VM_Time.now() - startTime;
      
      gcOrdinal = VM_Synchronization.fetchAndAdd(participantCount, 0, 1) + 1;
      
      if (trace)
	VM_Scheduler.trace("VM_CollectorThread", "entering first rendezvous - gcOrdinal =",
			 gcOrdinal);

      //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
      //-#else
      // the first RVM VP will wait for all the native VPs not blocked in native
      // to reach a SIGWAIT state
      //
      if( gcOrdinal == 1) {
        if (debug_native) VM_Scheduler.trace("VM_CollectorThread", "quiescing native VPs");
        for (int i = 1; i <= VM_Processor.numberNativeProcessors; i++) {
	  if (debug_native) VM_Scheduler.trace("VM_CollectorThread", "doing a nativeVP");
          if ( VM_Processor.vpStatus[VM_Processor.nativeProcessors[i].vpStatusIndex] !=
	       VM_Processor.BLOCKED_IN_NATIVE) {
	    if (debug_native) VM_Scheduler.trace("VM_CollectorThread", "found one not blocked in native");
					
	    while ( VM_Processor.vpStatus[VM_Processor.nativeProcessors[i].vpStatusIndex] !=
		    VM_Processor.IN_SIGWAIT) {
	      if (debug_native) {
		VM_Scheduler.trace("VM_CollectorThread", "WAITING FOR NATIVE PROCESSOR", i);
		VM_Scheduler.trace("VM_CollectorThread", "vpstatus =",
				   VM_Processor.vpStatus[VM_Processor.nativeProcessors[i].vpStatusIndex]);
	      }
	      VM.sysVirtualProcessorYield();
	    }
	    // Note: threads contextRegisters (ip & fp) are set by thread before
	    // entering SIGWAIT, so nothing needs to be done here
	  }
	  else {
	    // BLOCKED_IN_NATIVE - set context regs so that scan of threads stack
	    // will start at the java to C transition frame. ip is not used for
	    // these frames, so can be set to 0.
	    //
	    VM_Thread t = VM_Processor.nativeProcessors[i].activeThread;
	    //	    t.contextRegisters.gprs[FRAME_POINTER] = t.jniEnv.JNITopJavaFP;
	    t.contextRegisters.setInnermost( 0 /*ip*/, t.jniEnv.JNITopJavaFP );
	  }
        }

	// quiesce any attached processors, blocking then IN_NATIVE or IN_SIGWAIT
	//
	quiesceAttachedProcessors();

      }  // gcOrdinal==1
      //-#endif

      /***
      if (RENDEZVOUS_TIMES) {
	mypid = VM_Processor.getCurrentProcessorId();
	rendezvous1in[mypid] = (int)((VM_Time.now() - startTime)*1000000);
	gcBarrier.rendezvous();     // wait for other collector threads to arrive here
	rendezvous1out[mypid] = (int)((VM_Time.now() - startTime)*1000000);
      }
      else
	gcBarrier.rendezvous();     // wait for other collector threads to arrive here
      ***/

      // wait for other collector threads to arrive or be made non-participants
      gcBarrier.startupRendezvous();

      // record time it took for running collector thread to start GC
      //
      if (MEASURE_WAIT_TIMES) startingTime = VM_Time.now() - startTime;

      // MOVE THIS INTO collect
      //
      // setup common workqueue for num VPs participating, used to be called once.
      // now count varies for each GC, so call for each GC
      if ( gcOrdinal == 1 )
	VM_GCWorkQueue.workQueue.initialSetup(participantCount[0]);
    
      if (trace) VM_Scheduler.trace("VM_CollectorThread", "starting collection");
      if (getThis().isActive) {
	VM_Allocator.collect();     // gc
      }
      if (trace) VM_Scheduler.trace("VM_CollectorThread", "finished collection");
      
      if (RENDEZVOUS_TIMES) {
	rendezvous2in[mypid] = (int)((VM_Time.now() - startTime)*1000000);
	gcBarrier.rendezvous();              // wait for other collector threads to arrive here
	rendezvous2out[mypid] = (int)((VM_Time.now() - startTime)*1000000);
      }
      else
	gcBarrier.rendezvous();              // wait for other collector threads to arrive here
      
      // Wake up mutators waiting for this gc cycle and create new collection
      // handshake object to be used for next gc cycle.
      // Note that mutators will not run until after thread switching is enabled,
      // so no mutators can possibly arrive at old handshake object: it's safe to
      // replace it with a new one.
      //
      if ( gcOrdinal == 1 ) {
	collectionCount += 1;

	// notify mutators waiting on previous handshake object - actually we
	// don't notify anymore, mutators are simply in processor ready queues
	// waiting to be dispatched.
	collect.notifyCompletion();
	collect =  new VM_Handshake();  // replace handshake with new one for next collection

	// schedule the FinalizerThread, if there is work to do & it is idle
	// THIS NOW HAPPENS DURING GC - SWITCH TO DOING IT HERE 
	// VM_Finalizer.schedule();
      } 
      
      if (RENDEZVOUS_TIMES) {
	rendezvous3in[mypid] = (int)((VM_Time.now() - startTime)*1000000);
	gcBarrier.rendezvous();     // wait for other collector threads to arrive here
	rendezvous3out[mypid] = (int)((VM_Time.now() - startTime)*1000000);
      }
      else
	gcBarrier.rendezvous();     // wait for other collector threads to arrive here
      
      if (RENDEZVOUS_TIMES) {
	// need extra barrier call to let all processors set rendezvouz3out time
	gcBarrier.rendezvous();         
	if (VM_Processor.getCurrentProcessorId() == 1)
	  printRendezvousTimes();
      }
      
      // final cleanup for initial collector thread
      if (gcOrdinal == 1) {

	//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
	//-#else

	// unblock any native processors executing in native that were blocked
	// in native at the start of GC
	//
	for (int i = 1; i <= VM_Processor.numberNativeProcessors; i++) {
	  VM_Processor vp = VM_Processor.nativeProcessors[i];
	  if (VM.VerifyAssertions) VM.assert(vp != null);
	  if ( VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.BLOCKED_IN_NATIVE ) {
	    VM_Processor.vpStatus[vp.vpStatusIndex] = VM_Processor.IN_NATIVE;
	    if (debug_native)
	      VM_Scheduler.trace("VM_CollectorThread:", "unblocking Native Processor", vp.id);
	  }
	}

	// It is VERY unlikely, but possible that some RVM processors were
	// found in C, and were BLOCKED_IN_NATIVE, during the collection, and now
	// need to be unblocked.
	//
	for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
	  VM_Processor vp = VM_Scheduler.processors[i];
	  if (VM.VerifyAssertions) VM.assert(vp != null);
	  if ( VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.BLOCKED_IN_NATIVE ) {
	    VM_Processor.vpStatus[vp.vpStatusIndex] = VM_Processor.IN_NATIVE;
	    if (debug_native)
	      VM_Scheduler.trace("VM_CollectorThread:", "unblocking RVM Processor", vp.id);
	  }
	}

	if ( !VM.BuildForSingleVirtualProcessor ) {
	  // if NativeDaemonProcessor was BLOCKED_IN_SIGWAIT, unblock it
	  //
	  VM_Processor ndvp = VM_Scheduler.processors[VM_Scheduler.nativeDPndx];
	  if ( ndvp!=null && VM_Processor.vpStatus[ndvp.vpStatusIndex] == VM_Processor.BLOCKED_IN_SIGWAIT ) {
	    VM_Processor.vpStatus[ndvp.vpStatusIndex] = VM_Processor.IN_SIGWAIT;
	    if (debug_native)
	      VM_Scheduler.trace("VM_CollectorThread:", "unblocking Native Daemon Processor");
	  }

	  // resume any attached Processors blocked prior to Collection
	  //
	  resumeAttachedProcessors();
	}
	//-#endif

	int lockoutAddr = VM_Magic.objectAsAddress(VM_BootRecord.the_boot_record)
	  + VM_Entrypoints.lockoutProcessorOffset;
	VM_Magic.setMemoryWord(lockoutAddr, 0);      // clear the GC flag
      }

      if (MEASURE_WAIT_TIMES) resetWaitTimers();  // reset for next GC

      // all collector threads enable thread switching on their processors
      // allowing waiting mutators to be scheduled and run.  The collector
      // threads go back to the top of the run loop, to place themselves
      // back on the collectorQueue, to wait for the next collection.
      //
      VM_Processor.getCurrentProcessor().enableThreadSwitching();  // resume normal scheduling
      
    }  // end of while(true) loop
    
  }  // run


  static void
  quiesceAttachedProcessors() {

    //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
    // not invoked in this case

    //-#else
    // if there are any attached processors (for user pthreads that entered the VM
    // via an AttachJVM) we may be briefly IN_JAVA during transitions to a RVM
    // processor. Ususally they are either IN_NATIVE (having returned to the users 
    // native C code - or invoked a native method) or IN_SIGWAIT (the native code
    // invoked a JNI Function, migrated to a RVM processor, leaving the attached
    // processor running its IdleThread, which enters IN_SIGWAIT and wait for a signal.
    //
    // We wait for the processor to be in either IN_NATIVE or IN_SIGWAIT and then 
    // block it there for the duration of the Collection

    if (VM_Processor.numberAttachedProcessors == 0) return;

    if (debug_native) VM_Scheduler.trace("VM_CollectorThread", "quiescing attached VPs");
    
    for (int i = 1; i < VM_Processor.attachedProcessors.length; i++) {
      VM_Processor vp = VM_Processor.attachedProcessors[i];
      if (vp==null) continue;   // must have detached
      if (debug_native) VM_Scheduler.trace("VM_CollectorThread", "quiescing attached VP", i);
      
      int loopCount = 0;
      while ( true ) {
	
	while ( VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.IN_JAVA )
	  VM.sysVirtualProcessorYield();
	
	if ( vp.blockInWaitIfInWait() ) {
	  if (debug_native)
	    VM_Scheduler.trace("VM_CollectorThread", "Attached Processor BLOCKED_IN_SIGWAIT", i);
	  // Note: threads contextRegisters (ip & fp) are set by thread before
	  // entering SIGWAIT, so nothing needs to be done here
	  break;
	}
	if ( vp.lockInCIfInC() ) {
	  if (debug_native)
	    VM_Scheduler.trace("VM_CollectorThread", "Attached Processor BLOCKED_IN_NATIVE", i);
	  
	  // XXX SES TON XXX
	  // TON !! what is in jniEnv.JNITopJavaFP when thread returns to user C code.
	  // AND what will happen when we scan its stack with that fp
	  
	  // set running threads context regs ip & fp to where scan of threads 
	  // stack should start.
	  VM_Thread at = vp.activeThread;
	  at.contextRegisters.setInnermost( 0 /*ip*/, at.jniEnv.JNITopJavaFP );
	  break;
	}
	
	loopCount++;
	if (debug_native && (loopCount%10 == 0)) {
	  VM_Scheduler.trace("VM_CollectorThread", "Waiting for Attached Processor", i);
	  VM_Scheduler.trace("                  ", "vpstatus =",
			     VM_Processor.vpStatus[vp.vpStatusIndex]);
	}
	if (loopCount%1000 == 0) {
	  VM_Scheduler.trace("VM_CollectorThread", "STUCK Waiting for Attached Processor", i);
	  VM.sysFail("VM_CollectorThread - STUCK quiescing attached processors");
	}
      }  // while (true)
    }  // end loop over attachedProcessors[]
    //-#endif
  }  // quiesceAttachedProcessors


  static void
  resumeAttachedProcessors() {

    //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
    // not invoked in this case

    //-#else
    // any attached processors were quiesced in either BLOCKED_IN_SIGWAIT
    // or BLOCKED_IN_NATIVE.  Unblock them.

    if (VM_Processor.numberAttachedProcessors == 0) return;

    if (debug_native) VM_Scheduler.trace("VM_CollectorThread", "resuming attached VPs");
    
    for (int i = 1; i < VM_Processor.attachedProcessors.length; i++) {
      VM_Processor vp = VM_Processor.attachedProcessors[i];
      if (vp==null) continue;   // must have detached
      if (debug_native) VM_Scheduler.trace("VM_CollectorThread", "resuming attached VP", i);

      if ( VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.BLOCKED_IN_NATIVE ) {
	VM_Processor.vpStatus[vp.vpStatusIndex] = VM_Processor.IN_NATIVE;
	if (debug_native)
	  VM_Scheduler.trace("VM_CollectorThread:", "resuming Processor IN_NATIVE", i);
	continue;
      }

      if ( VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.BLOCKED_IN_SIGWAIT ) {
	// first unblock the processor
	vp.vpStatus[vp.vpStatusIndex] = VM_Processor.IN_SIGWAIT;
	// then send signal
	VM.sysCall1(VM_BootRecord.the_boot_record.sysPthreadSignalIP,vp.pthread_id);
	continue;
      }

      // should not reach here: system error:
      VM_Scheduler.trace("ERROR", "resumeAttachedProcessors: VP not BLOCKED", i);
      VM_Scheduler.trace("   ", "vpstatus =",VM_Processor.vpStatus[vp.vpStatusIndex]);
      if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);

    }  // end loop over attachedProcessors[]
    //-#endif
  }  // resumeAttachedProcessors
  
  
  // Make a collector thread that will participate in gc.
  // Taken:    stack to run on
  //           processor to run on
  // Returned: collector
  // Note: "stack" must be in pinned memory: currently done by allocating it in the boot image.
  //
  static VM_CollectorThread 
    createActiveCollectorThread(int[] stack, VM_Processor processorAffinity) {
    //-#if RVM_WITH_CONCURRENT_GC
    return new VM_RCCollectorThread(stack, true, processorAffinity);
    //-#else
    return new VM_CollectorThread(stack, true, processorAffinity);
    //-#endif
  }
  
  // Make a collector thread that will not participate in gc.
  // It will serve only to lock out mutators from the current processor.
  // Taken:    stack to run on
  //           processor to run on
  // Returned: collector
  // Note: "stack" must be in pinned memory: currently done by allocating it in the boot image.
  //
  static VM_CollectorThread 
    createPassiveCollectorThread(int[] stack, VM_Processor processorAffinity) {
    return new VM_CollectorThread(stack, false,  processorAffinity);
  }
  
  //-----------------//
  // Instance fields //
  //-----------------//
  
  boolean           isActive;      // are we an "active participant" in gc?
  
  /** arrival order of collectorThreads participating in a collection */
  int               gcOrdinal;

  /** used by each CollectorThread when scanning thread stacks for references */
  VM_GCMapIteratorGroup iteratorGroup;
  
  // pointers into work queue put and get buffers, used with loadbalanced 
  // workqueues where per thread buffers when filled are given to a common shared
  // queue of buffers of work to be done, and buffers for scanning are obtained
  // from that common queue (see VM_GCWorkQueue)
  //
  // Put Buffer is filled from right (end) to left (start)
  //    | next ptr | ......      <--- | 00000 | entry | entry | entry |
  //      |                             |   
  //    putStart                      putTop
  //
  // Get Buffer is emptied from left (start) to right (end)
  //    | next ptr | xxxxx | xxxxx | entry | entry | entry | entry |
  //      |                   |                                     |   
  //    getStart             getTop ---->                         getEnd
  //
  
  /** start of current work queue put buffer */
  int putBufferStart;
  /** current position in current work queue put buffer */
  int putBufferTop;
  /** start of current work queue get buffer */
  int getBufferStart;
  /** current position in current work queue get buffer */
  int getBufferTop;
  /** end of current work queue get buffer */
  int getBufferEnd;
  /** extra Work Queue Buffer */
  int extraBuffer;
  /** second extra Work Queue Buffer */
  int extraBuffer2;
  
  // for statistics
  //
  int localcount1;            // copyCount
  int localcount2;            // biggestByteCountCopied
  int localcount3;            // markBootCount

  int timeInRendezvous;       // time waiting in rendezvous (milliseconds)
  
  double stoppingTime;        // mutator stopping time - until enter Rendezvous 1
  double startingTime;        // time leaving Rendezvous 1
  double rendezvousWaitTime;  // accumulated wait time in GC Rendezvous's

  // for measuring load balancing of work queues
  //
  int copyCount;              // for saving count of objects copied
  int rootWorkCount;          // initial count of entries == number of roots
  int putWorkCount;           // workqueue entries found and put into buffers
  int getWorkCount;           // workqueue entries got from buffers & scanned
  int swapBufferCount;        // times get & put buffers swapped
  int putBufferCount;         // number of buffers put to common workqueue
  int getBufferCount;         // number of buffers got from common workqueue
  int bufferWaitCount;        // number of times had to wait for a get buffer
  double bufferWaitTime;      // accumulated time waiting for get buffers
  double finishWaitTime;      // time waiting for all buffers to be processed;

  int copyCount1;              // for saving count of objects copied
  int rootWorkCount1;          // initial count of entries == number of roots
  int putWorkCount1;           // workqueue entries found and put into buffers
  int getWorkCount1;           // workqueue entries got from buffers & scanned
  int swapBufferCount1;        // times get & put buffers swapped
  int putBufferCount1;         // number of buffers put to common workqueue
  int getBufferCount1;         // number of buffers got from common workqueue
  int bufferWaitCount1;        // number of times had to wait for a get buffer
  double bufferWaitTime1;      // accumulated time waiting for get buffers
  double finishWaitTime1;      // time waiting for all buffers to be processed;

  double totalBufferWait;      // total time waiting for get buffers
  double totalFinishWait;      // total time waiting for no more buffers
  double totalRendezvousWait;  // total time waiting for no more buffers

  // constructor
  //
  VM_CollectorThread(int[] stack, boolean isActive, VM_Processor processorAffinity) {
    super(stack);
    makeDaemon(true); // this is redundant, but harmless
    this.isActive          = isActive;
    this.isGCThread        = true;
    this.processorAffinity = processorAffinity;
    this.iteratorGroup     = new VM_GCMapIteratorGroup();

    // associate this collector thread with its affinity processor
    collectorThreads[processorAffinity.id] = this;
  }
  
  // Record number of processors that will be participating in gc synchronization.
  //
  static void 
    boot(int numProcessors) {
    VM_Allocator.gcSetup(numProcessors);
  }
  
  static void
    printRendezvousTimes() {
    VM.sysWrite("* * * * * * * * * * * * * *\n");
    VM.sysWrite("COLLECTOR THREAD RENDEZVOUS entrance & exit times (microsecs) rendevous 1, 2 & 3\n");
    
    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
      VM.sysWrite(i,false);
      VM.sysWrite(" R1 in ");
      VM.sysWrite(rendezvous1in[i],false);
      VM.sysWrite(" out ");
      VM.sysWrite(rendezvous1out[i],false);
      VM.sysWrite(" R2 in ");
      VM.sysWrite(rendezvous2in[i],false);
      VM.sysWrite(" out ");
      VM.sysWrite(rendezvous2out[i],false);
      VM.sysWrite(" R3 in ");
      VM.sysWrite(rendezvous3in[i],false);
      VM.sysWrite(" out ");
      VM.sysWrite(rendezvous3out[i],false);
      VM.sysWrite("\n");
    }
    VM.sysWrite("* * * * * * * * * * * * * *\n");
  }

  void
  incrementWaitTimeTotals() {
    totalBufferWait += bufferWaitTime + bufferWaitTime1;
    totalFinishWait += finishWaitTime + finishWaitTime1;
    totalRendezvousWait += rendezvousWaitTime;
  }

  void
  resetWaitTimers() {
    bufferWaitTime = 0.0;
    bufferWaitTime1 = 0.0;
    finishWaitTime = 0.0;
    finishWaitTime1 = 0.0;
    rendezvousWaitTime = 0.0;
  }

  static void
  printThreadWaitTimes() {
    VM_CollectorThread ct;

    VM.sysWrite("*** Collector Thread Wait Times (in micro-secs)\n");
    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
      ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.processors[i].activeThread );
      VM.sysWrite(i,false);
      VM.sysWrite(" stop ");
      VM.sysWrite( (int)((ct.stoppingTime)*1000000.0), false);
      VM.sysWrite(" start ");
      VM.sysWrite( (int)((ct.startingTime)*1000000.0), false);
      VM.sysWrite(" SBW ");
      if (ct.bufferWaitCount1 > 0)
	VM.sysWrite(ct.bufferWaitCount1-1,false);  // subtract finish wait
      else
	VM.sysWrite(0,false);
      VM.sysWrite(" SBWT ");
      VM.sysWrite( (int)((ct.bufferWaitTime1)*1000000.0), false);
      VM.sysWrite(" SFWT ");
      VM.sysWrite( (int)((ct.finishWaitTime1)*1000000.0), false);
      VM.sysWrite(" FBW ");
      if (ct.bufferWaitCount > 0)
	VM.sysWrite(ct.bufferWaitCount-1,false);  // subtract finish wait
      else
	VM.sysWrite(0,false);
      VM.sysWrite(" FBWT ");
      VM.sysWrite( (int)((ct.bufferWaitTime)*1000000.0), false);
      VM.sysWrite(" FFWT ");
      VM.sysWrite( (int)((ct.finishWaitTime)*1000000.0), false);
      VM.sysWrite(" RWT ");
      VM.sysWrite( (int)((ct.rendezvousWaitTime)*1000000.0), false);
      VM.sysWrite("\n");

      ct.stoppingTime = 0.0;
      ct.startingTime = 0.0;
      ct.rendezvousWaitTime = 0.0;
      VM_GCWorkQueue.resetWaitTimes(ct);
    }
  }
}
