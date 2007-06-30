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
package org.jikesrvm.scheduler;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.compilers.opt.VM_OptCompiledMethod;
import org.jikesrvm.compilers.opt.VM_OptEncodedCallSiteTree;
import org.jikesrvm.compilers.opt.VM_OptMachineCodeMap;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.memorymanagers.mminterface.VM_CollectorThread;
import org.jikesrvm.osr.OSR_ObjectHolder;
import org.jikesrvm.runtime.VM_BootRecord;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.runtime.VM_Magic;
import static org.jikesrvm.runtime.VM_SysCall.sysCall;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.LogicallyUninterruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Global variables used to implement virtual machine thread scheduler.
 *    - virtual cpus
 *    - threads
 *    - queues
 *    - locks
 */
@Uninterruptible
public class VM_Scheduler {

  /** Toggle display of frame pointer address in stack dump */
  private static final boolean SHOW_FP_IN_STACK_DUMP = false;

  /** Index of initial processor in which "VM.boot()" runs. */
  public static final int PRIMORDIAL_PROCESSOR_ID = 1;

  /** Index of thread in which "VM.boot()" runs */
  public static final int PRIMORDIAL_THREAD_INDEX = 1;

  // A processors id is its index in the processors array & a threads
  // id is its index in the threads array.  id's start at 1, so that
  // id 0 can be used in locking to represent an unheld lock

  /**
   * Maximum number of VM_Processor's that we can support.
   */
  public static final int MAX_PROCESSORS = 12;   // allow processors = 1 to 12

  /** Maximum number of VM_Thread's that we can support. */
  public static final int LOG_MAX_THREADS = 14;
  public static final int MAX_THREADS = 1 << LOG_MAX_THREADS;

  /** Flag for controlling virtual-to-physical processor binding. */
  public static final int NO_CPU_AFFINITY = -1;

  /** Scheduling quantum in milliseconds: interruptQuantum * interruptQuantumMultiplier */
  public static int schedulingQuantum = 10;

  // Virtual cpu's.
  //
  /**
   * Physical cpu to which first virtual processor is bound (remainder are bound
   * sequentially)
   */
  public static int cpuAffinity = NO_CPU_AFFINITY;
  /** Total number of virtual processors to be used */
  public static int numProcessors = 1;
  /** Array of all virtual processors (slot 0 always empty) */
  public static VM_Processor[] processors;
  /** Have all processors completed initialization? */
  public static boolean allProcessorsInitialized;
  /** VM is terminated, clean up and exit */
  public static boolean terminated;

  // Thread creation and deletion.
  //
  /** list of threads that have been created (slot 0 always empty) */
  public static final VM_Thread[] threads = new VM_Thread[MAX_THREADS];

  /** place to start searching threads[] for next free slot */
  static int threadAllocationIndex;
  /** highest thread index allocated */
  public static int threadHighWatermark;
  /** number of threads running or waiting to run */
  static int numActiveThreads;
  /** number of "daemon" threads, in the java sense */
  static int numDaemons;
  /** guard for serializing access to fields above */
  static final VM_ProcessorLock threadCreationMutex = new VM_ProcessorLock();

  // Thread execution.
  //
  /** threads waiting to wake up from a sleep() */
  static final VM_ProxyWakeupQueue wakeupQueue = new VM_ProxyWakeupQueue();
  static final VM_ProcessorLock wakeupMutex = new VM_ProcessorLock();

  /** thread waiting to service debugging requests */
  public static final VM_ThreadQueue debuggerQueue = new VM_ThreadQueue();
  static final VM_ProcessorLock debuggerMutex = new VM_ProcessorLock();

  /** collector threads waiting to be resumed */
  public static final VM_ThreadQueue collectorQueue = new VM_ThreadQueue();
  public static final VM_ProcessorLock collectorMutex = new VM_ProcessorLock();

  /** Finalizer thread waits here when idle */
  public static final VM_ThreadQueue finalizerQueue = new VM_ThreadQueue();
  public static final VM_ProcessorLock finalizerMutex = new VM_ProcessorLock();

  /** Thick locks. */
  public static VM_Lock[] locks;

  /**
   * Flag set by external signal to request debugger activation at next thread switch.
   * See also: RunBootImage.C
   */
  public static boolean debugRequested;

  /** Number of times dump stack has been called recursively */
  private static int inDumpStack = 0;

  /** In dump stack and dying */
  private static boolean exitInProgress = false;

  /** How many extra procs (not counting primordial) ? */
  private static int NUM_EXTRA_PROCS = 0;

  /** Extra debug from traces */
  private static final boolean traceDetails = false;

  /** Int controlling output. 0 => output can be used, otherwise ID of processor */
  @SuppressWarnings({"unused", "UnusedDeclaration"})
  private static int outputLock;

  ////////////////////////////////////////////////
  // fields for synchronizing code patching
  ////////////////////////////////////////////////

  /**
   * How may processors to be synchronized for code patching, the last one (0)
   * will notify the blocked thread. Used only if RVM_FOR_POWERPC is true
   */
  public static int toSyncProcessors;

  /**
   * Synchronize object. Used only if RVM_FOR_POWERPC is true
   */
  public static Object syncObj = null;

  /**
   * Initialize boot image.
   */
  @Interruptible
  public static void init() {
    threadAllocationIndex = PRIMORDIAL_THREAD_INDEX;

    // Enable us to dump a Java Stack from the C trap handler to aid in debugging things that
    // show up as recursive use of hardware exception registers (eg the long-standing lisp bug)
    VM_BootRecord.the_boot_record.dumpStackAndDieOffset = VM_Entrypoints.dumpStackAndDieMethod.getOffset();

    // allocate initial processor list
    //
    processors = new VM_Processor[2 + NUM_EXTRA_PROCS];  // first slot unused, then primordial, then extra
    processors[PRIMORDIAL_PROCESSOR_ID] = new VM_Processor(PRIMORDIAL_PROCESSOR_ID);
    for (int i = 1; i <= NUM_EXTRA_PROCS; i++) {
      processors[PRIMORDIAL_PROCESSOR_ID + i] = new VM_Processor(PRIMORDIAL_PROCESSOR_ID + i);
    }

    // allocate lock structures
    //
    VM_Lock.init();
  }

  /** This is run from VM.boot() */
  @Interruptible
  public static void giveBootVM_ThreadAJavaLangThread() {
    VM_Thread vt = threads[PRIMORDIAL_THREAD_INDEX];

    vt.setJavaLangThread(java.lang.JikesRVMSupport.createThread(vt, "Jikes_RVM_Boot_Thread"));
  }

  /**
   * Begin multi-threaded vm operation.
   */
  @Interruptible
  public static void boot() {
    if (VM.VerifyAssertions) VM._assert(1 <= numProcessors && numProcessors <= MAX_PROCESSORS);

    if (VM.TraceThreads) {
      trace("VM_Scheduler.boot", "numProcessors =", numProcessors);
    }

    // Create a VM_Processor object for each virtual cpu that we'll be running.
    // Note that the VM_Processor object for the primordial processor
    // (the virtual cpu in whose context we are currently running)
    // was already created in the boot image by init(), above.
    //
    VM_Processor[] origProcs = processors;
    processors = new VM_Processor[1 + numProcessors];

    for (int i = PRIMORDIAL_PROCESSOR_ID; i <= numProcessors; i++) {
      VM_Processor p = (i < origProcs.length) ? origProcs[i] : null;
      if (p == null) {
        processors[i] = new VM_Processor(i);
      } else {
        processors[i] = p;
        if (VM.BuildForIA32) {
          p.jtoc = VM_Magic.getJTOC();  // only needed for EXTRA_PROCS
        }
      }
    }

    // Create one one idle thread per processor.
    //
    for (int i = 0; i < numProcessors; ++i) {
      int pid = i + 1;
      VM_Thread t = new VM_IdleThread(processors[pid], pid != PRIMORDIAL_PROCESSOR_ID);
      processors[pid].idleQueue.enqueue(t);
    }

    // JNI support
    terminated = false;

    // the one we're running on
    processors[PRIMORDIAL_PROCESSOR_ID].isInitialized = true;

    // Create virtual cpu's.
    //

    sysCall.sysCreateThreadSpecificDataKeys();
    if (!VM.withoutInterceptBlockingSystemCalls) {
      /// We now insist on this happening, by using LD_PRELOAD on platforms
      /// that support it.  Do it here for backup.
      // Enable spoofing of blocking native select calls
      System.loadLibrary("syswrap");
    }

    sysCall.sysInitializeStartupLocks(numProcessors);

    if (cpuAffinity != NO_CPU_AFFINITY) {
      sysCall.sysVirtualProcessorBind(cpuAffinity + PRIMORDIAL_PROCESSOR_ID - 1); // bind it to a physical cpu
    }

    for (int i = PRIMORDIAL_PROCESSOR_ID; ++i <= numProcessors;) {
      // create VM_Thread for virtual cpu to execute
      //
      VM_Thread target = processors[i].idleQueue.dequeue();

      // Create a virtual cpu and wait for execution to enter the target's
      // code/stack.
      // This is done with GC disabled to ensure that the garbage collector
      // doesn't move code or stack before the C startoff function has a
      // chance to transfer control into the VM image.
      //
      if (VM.TraceThreads) {
        trace("VM_Scheduler.boot", "starting processor id", i);
      }

      processors[i].activeThread = target;
      processors[i].activeThreadStackLimit = target.stackLimit;
      target.registerThread(); // let scheduler know that thread is active.
      if (VM.BuildForPowerPC) {
        // NOTE: It is critical that we acquire the tocPointer explicitly
        //       before we start the SysCall sequence. This prevents
        //       the opt compiler from generating code that passes the AIX
        //       sys toc instead of the RVM jtoc. --dave
        Address toc = VM_Magic.getTocPointer();
        sysCall.sysVirtualProcessorCreate(toc,
                                          VM_Magic.objectAsAddress(processors[i]),
                                          target.contextRegisters.ip,
                                          target.contextRegisters.getInnermostFramePointer());
        if (cpuAffinity != NO_CPU_AFFINITY) {
          sysCall.sysVirtualProcessorBind(cpuAffinity + i - 1); // bind it to a physical cpu
        }
      } else if (VM.BuildForIA32) {
        sysCall.sysVirtualProcessorCreate(VM_Magic.getTocPointer(),
                                          VM_Magic.objectAsAddress(processors[i]),
                                          target.contextRegisters.ip,
                                          target.contextRegisters.getInnermostFramePointer());
      } else if (VM.VerifyAssertions) {
        VM._assert(VM.NOT_REACHED);
      }
    }

    // wait for everybody to start up
    //
    sysCall.sysWaitForVirtualProcessorInitialization();

    allProcessorsInitialized = true;

    //    for (int i = PRIMORDIAL_PROCESSOR_ID; i <= numProcessors; ++i)
    //      processors[i].enableThreadSwitching();
    VM_Processor.getCurrentProcessor().enableThreadSwitching();

    // Start interrupt driven timeslicer to improve threading fairness and responsiveness.
    //
    schedulingQuantum = VM.interruptQuantum * VM.schedulingMultiplier;
    if (VM.TraceThreads) {
      VM.sysWrite("  schedulingQuantum " + schedulingQuantum);
      VM.sysWrite(" = VM.interruptQuantum " + VM.interruptQuantum);
      VM.sysWrite(" * VM.schedulingMultiplier " + VM.schedulingMultiplier);
      VM.sysWriteln();
    }
    sysCall.sysVirtualProcessorEnableTimeSlicing(VM.interruptQuantum);

    // Allow virtual cpus to commence feeding off the work queues.
    //
    sysCall.sysWaitForMultithreadingStart();

    if (VM.BuildForAdaptiveSystem) {
      OSR_ObjectHolder.boot();
    }

    // Start collector threads on each VM_Processor.
    for (int i = 0; i < numProcessors; ++i) {
      VM_Thread t = VM_CollectorThread.createActiveCollectorThread(processors[1 + i]);
      t.start(processors[1 + i].readyQueue);
    }

    // Start the G.C. system.

    // Create the VM_FinalizerThread
    VM_FinalizerThread tt = new VM_FinalizerThread();
    tt.makeDaemon(true);
    tt.start();

    // Store VM_Processor in pthread
    sysCall.sysStashVmProcessorInPthread(VM_Processor.getCurrentProcessor());
  }

  /**
   * Terminate all the pthreads that belong to the VM
   * This path is used when the VM is taken down by an external pthread via
   * the JNI call DestroyJavaVM.  All pthreads in the VM must eventually reach this
   * method from VM_Thread.terminate() for the termination to proceed and for control
   * to return to the pthread that calls DestroyJavaVM
   * Going by the order in processor[], the pthread for each processor will join with
   * the next one, and the external pthread calling DestroyJavaVM will join with the
   * main pthread of the VM (see libjni.C)
   *
   * Note:  the NativeIdleThread's don't need to be terminated since they don't have
   * their own pthread;  they run on the external pthreads that had called CreateJavaVM
   * or AttachCurrentThread.
   */
  public static void processorExit(int rc) {
    // trace("VM_Scheduler", ("Exiting with " + numProcessors + " pthreads."));

    // set flag to get all idle threads to exit to VM_Thread.terminate()
    terminated = true;

    // TODO:
    // Get the collector to free system memory:  no more allocation beyond this point

    // Terminate the pthread: each processor waits for the next one
    // find the pthread to wait for
    VM_Processor myVP = VM_Processor.getCurrentProcessor();
    VM_Processor VPtoWaitFor = null;
    for (int i = 1; i < numProcessors; i++) {
      if (processors[i] == myVP) {
        VPtoWaitFor = processors[i + 1];
        break;
      }
    }

    // each join with the expected pthread
    if (VPtoWaitFor != null) {
      sysCall.sysPthreadJoin(VPtoWaitFor.pthread_id);
    }

    // then exit myself with pthread_exit
    sysCall.sysPthreadExit();

    // does not return
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);

  }

  /**
   * Print out message in format "p[j] (cez#td) who: what", where:
   *    p  = processor id
   *    j  = java thread id
   *    c* = ava thread id of the owner of threadCreationMutex (if any)
   *    e* = java thread id of the owner of threadExecutionMutex (if any)
   *    z* = VM_Processor.getCurrentProcessor().threadSwitchingEnabledCount
   *         (0 means thread switching is enabled outside of the call to debug)
   *    t* = numActiveThreads
   *    d* = numDaemons
   *
   * * parenthetical values, printed only if traceDetails = true)
   *
   * We serialize against a mutex to avoid intermingling debug output from multiple threads.
   */
  public static void trace(String who, String what) {
    lockOutput();
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    VM.sysWriteInt(VM_Processor.getCurrentProcessorId());
    VM.sysWrite("[");
    VM_Thread t = VM_Thread.getCurrentThread();
    t.dump();
    VM.sysWrite("] ");
    if (traceDetails) {
      VM.sysWrite("(");
      // VM.sysWriteInt(threadCreationMutex.owner);
      // VM.sysWrite("-");
      // VM.sysWriteInt(-VM_Processor.getCurrentProcessor().threadSwitchingEnabledCount);
      // VM.sysWrite("#");
      VM.sysWriteInt(numDaemons);
      VM.sysWrite("/");
      VM.sysWriteInt(numActiveThreads);
      VM.sysWrite(") ");
    }
    VM.sysWrite(who);
    VM.sysWrite(": ");
    VM.sysWrite(what);
    VM.sysWrite("\n");
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
    unlockOutput();
  }

  /**
   * Print out message in format "p[j] (cez#td) who: what howmany", where:
   *    p  = processor id
   *    j  = java thread id
   *    c* = java thread id of the owner of threadCreationMutex (if any)
   *    e* = java thread id of the owner of threadExecutionMutex (if any)
   *    z* = VM_Processor.getCurrentProcessor().threadSwitchingEnabledCount
   *         (0 means thread switching is enabled outside of the call to debug)
   *    t* = numActiveThreads
   *    d* = numDaemons
   *
   * * parenthetical values, printed only if traceDetails = true)
   *
   * We serialize against a mutex to avoid intermingling debug output from multiple threads.
   */
  public static void trace(String who, String what, int howmany) {
    _trace(who, what, howmany, false);
  }

  // same as trace, but prints integer value in hex
  //
  public static void traceHex(String who, String what, int howmany) {
    _trace(who, what, howmany, true);
  }

  public static void trace(String who, String what, Address addr) {
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    lockOutput();
    VM.sysWriteInt(VM_Processor.getCurrentProcessorId());
    VM.sysWrite("[");
    VM_Thread.getCurrentThread().dump();
    VM.sysWrite("] ");
    if (traceDetails) {
      VM.sysWrite("(");
      VM.sysWriteInt(numDaemons);
      VM.sysWrite("/");
      VM.sysWriteInt(numActiveThreads);
      VM.sysWrite(") ");
    }
    VM.sysWrite(who);
    VM.sysWrite(": ");
    VM.sysWrite(what);
    VM.sysWrite(" ");
    VM.sysWriteHex(addr);
    VM.sysWrite("\n");
    unlockOutput();
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }

  private static void _trace(String who, String what, int howmany, boolean hex) {
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    lockOutput();
    VM.sysWriteInt(VM_Processor.getCurrentProcessorId());
    VM.sysWrite("[");
    //VM.sysWriteInt(VM_Thread.getCurrentThread().getIndex());
    VM_Thread.getCurrentThread().dump();
    VM.sysWrite("] ");
    if (traceDetails) {
      VM.sysWrite("(");
      // VM.sysWriteInt(threadCreationMutex.owner);
      // VM.sysWrite("-");
      // VM.sysWriteInt(-VM_Processor.getCurrentProcessor().threadSwitchingEnabledCount);
      // VM.sysWrite("#");
      VM.sysWriteInt(numDaemons);
      VM.sysWrite("/");
      VM.sysWriteInt(numActiveThreads);
      VM.sysWrite(") ");
    }
    VM.sysWrite(who);
    VM.sysWrite(": ");
    VM.sysWrite(what);
    VM.sysWrite(" ");
    if (hex) {
      VM.sysWriteHex(howmany);
    } else {
      VM.sysWriteInt(howmany);
    }
    VM.sysWrite("\n");
    unlockOutput();
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }

  /**
   * Print interesting scheduler information, starting with a stack traceback.
   * Note: the system could be in a fragile state when this method
   * is called, so we try to rely on as little runtime functionality
   * as possible (eg. use no bytecodes that require VM_Runtime support).
   */
  public static void traceback(String message) {
    if (VM.runningVM) {
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      lockOutput();
    }
    VM.sysWriteln(message);
    tracebackWithoutLock();
    if (VM.runningVM) {
      unlockOutput();
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
    }
  }

  public static void traceback(String message, int number) {
    if (VM.runningVM) {
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      lockOutput();
    }
    VM.sysWriteln(message, number);
    tracebackWithoutLock();
    if (VM.runningVM) {
      unlockOutput();
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
    }
  }

  static void tracebackWithoutLock() {
    if (VM.runningVM) {
      dumpStack(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));
    } else {
      dumpStack();
    }
  }

  /**
   * Dump stack of calling thread, starting at callers frame
   */
  @LogicallyUninterruptible
  public static void dumpStack() {
    if (VM.runningVM) {
      dumpStack(VM_Magic.getFramePointer());
    } else {
      StackTraceElement[] elements =
        (new Throwable("--traceback from Jikes RVM's VM_Scheduler class--")).getStackTrace();
      for (StackTraceElement element: elements) {
        System.err.println(element.toString());
      }
    }
  }

  /**
   * Dump state of a (stopped) thread's stack.
   * @param fp address of starting frame. first frame output
   *           is the calling frame of passed frame
   */
  static void dumpStack(Address fp) {
    if (VM.VerifyAssertions) {
      VM._assert(VM.runningVM);
    }

    Address ip = VM_Magic.getReturnAddress(fp);
    fp = VM_Magic.getCallerFramePointer(fp);
    dumpStack(ip, fp);

  }

  /**
   * Dump state of a (stopped) thread's stack.
   * @param ip instruction pointer for first frame to dump
   * @param fp frame pointer for first frame to dump
   */
  public static void dumpStack(Address ip, Address fp) {
    ++inDumpStack;
    if (inDumpStack > 1 &&
        inDumpStack <= VM.maxSystemTroubleRecursionDepth + VM.maxSystemTroubleRecursionDepthBeforeWeStopVMSysWrite) {
      VM.sysWrite("VM_Scheduler.dumpStack(): in a recursive call, ");
      VM.sysWrite(inDumpStack);
      VM.sysWriteln(" deep.");
    }
    if (inDumpStack > VM.maxSystemTroubleRecursionDepth) {
      VM.dieAbruptlyRecursiveSystemTrouble();
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }

    VM.sysWrite("-- Stack --\n");
    while (VM_Magic.getCallerFramePointer(fp).NE(ArchitectureSpecific.VM_StackframeLayoutConstants.STACKFRAME_SENTINEL_FP)) {

      // if code is outside of RVM heap, assume it to be native code,
      // skip to next frame
      if (!MM_Interface.addressInVM(ip)) {
        showMethod("native frame", fp);
        ip = VM_Magic.getReturnAddress(fp);
        fp = VM_Magic.getCallerFramePointer(fp);
        continue; // done printing this stack frame
      }

      int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
      if (compiledMethodId == ArchitectureSpecific.VM_StackframeLayoutConstants.INVISIBLE_METHOD_ID) {
        showMethod("invisible method", fp);
      } else {
        // normal java frame(s)
        VM_CompiledMethod compiledMethod = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
        if (compiledMethod == null) {
          showMethod(compiledMethodId, fp);
        } else if (compiledMethod.getCompilerType() == VM_CompiledMethod.TRAP) {
          showMethod("hardware trap", fp);
        } else {
          VM_Method method = compiledMethod.getMethod();
          Offset instructionOffset = compiledMethod.getInstructionOffset(ip);
          int lineNumber = compiledMethod.findLineNumberForInstruction(instructionOffset);

          if (VM.BuildForOptCompiler && compiledMethod.getCompilerType() == VM_CompiledMethod.OPT) {
            VM_OptCompiledMethod optInfo = (VM_OptCompiledMethod) compiledMethod;
            // Opt stack frames may contain multiple inlined methods.
            VM_OptMachineCodeMap map = optInfo.getMCMap();
            int iei = map.getInlineEncodingForMCOffset(instructionOffset);
            if (iei >= 0) {
              int[] inlineEncoding = map.inlineEncoding;
              int bci = map.getBytecodeIndexForMCOffset(instructionOffset);
              for (; iei >= 0; iei = VM_OptEncodedCallSiteTree.getParent(iei, inlineEncoding)) {
                int mid = VM_OptEncodedCallSiteTree.getMethodID(iei, inlineEncoding);
                method = VM_MemberReference.getMemberRef(mid).asMethodReference().getResolvedMember();
                lineNumber = ((VM_NormalMethod)method).getLineNumberForBCIndex(bci);
                showMethod(method, lineNumber, fp);
                if (iei > 0) {
                  bci = VM_OptEncodedCallSiteTree.getByteCodeOffset(iei, inlineEncoding);
                }
              }
            } else {
              showMethod(method, lineNumber, fp);
            }
            ip = VM_Magic.getReturnAddress(fp);
            fp = VM_Magic.getCallerFramePointer(fp);
            continue; // done printing this stack frame
          }

          showMethod(method, lineNumber, fp);
        }
      }
      ip = VM_Magic.getReturnAddress(fp);
      fp = VM_Magic.getCallerFramePointer(fp);
    }
    --inDumpStack;
  }

  private static void showPrologue(Address fp) {
    VM.sysWrite("   at ");
    if (SHOW_FP_IN_STACK_DUMP) {
      VM.sysWrite("[");
      VM.sysWrite(fp);
      VM.sysWrite("] ");
    }
  }

  /**
   * Show a method where getCompiledMethod returns null
   *
   * @param compiledMethodId
   * @param fp
   */
  private static void showMethod(int compiledMethodId, Address fp) {
    showPrologue(fp);
    VM.sysWrite("<unprintable normal Java frame: VM_CompiledMethods.getCompiledMethod(",
                compiledMethodId,
                ") returned null>\n");
  }

  /**
   * Show a method that we can't show (ie just a text description of the
   * stack frame
   *
   * @param name
   * @param fp
   */
  private static void showMethod(String name, Address fp) {
    showPrologue(fp);
    VM.sysWrite("<");
    VM.sysWrite(name);
    VM.sysWrite(">\n");
  }

  /** Helper function for {@link #dumpStack(Address,Address)}.  Print a
   * stack frame showing the method.  */
  private static void showMethod(VM_Method method, int lineNumber, Address fp) {
    showPrologue(fp);
    if (method == null) {
      VM.sysWrite("<unknown method>");
    } else {
      VM.sysWrite(method.getDeclaringClass().getDescriptor());
      VM.sysWrite(" ");
      VM.sysWrite(method.getName());
      VM.sysWrite(method.getDescriptor());
    }
    if (lineNumber > 0) {
      VM.sysWrite(" at line ");
      VM.sysWriteInt(lineNumber);
    }
    VM.sysWrite("\n");
  }

  /**
   * Dump state of a (stopped) thread's stack and exit the virtual machine.
   * @param fp address of starting frame
   * Returned: doesn't return.
   * This method is called from RunBootImage.C when something goes horrifically
   * wrong with exception handling and we want to die with useful diagnostics.
   */
  public static void dumpStackAndDie(Address fp) {
    if (!exitInProgress) {
      // This is the first time I've been called, attempt to exit "cleanly"
      exitInProgress = true;
      dumpStack(fp);
      VM.sysExit(VM.EXIT_STATUS_DUMP_STACK_AND_DIE);
    } else {
      // Another failure occured while attempting to exit cleanly.
      // Get out quick and dirty to avoid hanging.
      sysCall.sysExit(VM.EXIT_STATUS_RECURSIVELY_SHUTTING_DOWN);
    }
  }

  /**
   * Dump state of virtual machine.
   */
  public static void dumpVirtualMachine() {
    VM_Processor processor;
    VM.sysWrite("\n-- Processors --\n");
    for (int i = 1; i <= numProcessors; ++i) {
      processor = processors[i];
      processor.dumpProcessorState();
    }

    // system queues
    VM.sysWrite("\n-- System Queues -- \n");
    VM.sysWrite(" wakeupQueue: ");
    wakeupQueue.dump();
    VM.sysWrite(" debuggerQueue: ");
    debuggerQueue.dump();
    VM.sysWrite(" collectorQueue: ");
    collectorQueue.dump();
    VM.sysWrite(" finalizerQueue: ");
    finalizerQueue.dump();

    VM.sysWrite("\n-- Threads --\n");
    for (int i = 1; i < threads.length; ++i) {
      if (threads[i] != null) {
        threads[i].dumpWithPadding(30);
        VM.sysWrite(threads[i].getCPUTimeMillis());
        VM.sysWrite("\n");
      }
    }
    VM.sysWrite("\n");

    VM.sysWrite("\n-- Locks available --\n");
    for (int i = PRIMORDIAL_PROCESSOR_ID; i <= numProcessors; ++i) {
      processor = processors[i];
      int unallocated = processor.lastLockIndex - processor.nextLockIndex + 1;
      VM.sysWrite(" processor ");
      VM.sysWriteInt(i);
      VM.sysWrite(": ");
      VM.sysWriteInt(processor.locksAllocated);
      VM.sysWrite(" locks allocated, ");
      VM.sysWriteInt(processor.locksFreed);
      VM.sysWrite(" locks freed, ");
      VM.sysWriteInt(processor.freeLocks);
      VM.sysWrite(" free looks, ");
      VM.sysWriteInt(unallocated);
      VM.sysWrite(" unallocated slots\n");
    }
    VM.sysWrite("\n");

    VM.sysWrite("\n-- Locks in use --\n");
    for (VM_Lock lock : locks) {
      if (lock != null) {
        lock.dump();
      }
    }
    VM.sysWrite("\n");

    VM.sysWriteln("Dumping stack of active thread\n");
    dumpStack();

    VM.sysWriteln("Attempting to dump the stack of all other live threads");
    VM.sysWriteln("This is somewhat risky since if the thread is running we're going to be quite confused");
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    for (int i = 1; i < threads.length; ++i) {
      VM_Thread thr = threads[i];
      if (thr != null && thr != VM_Thread.getCurrentThread() && thr.isAlive) {
        thr.dump();
        dumpStack(thr.contextRegisters.getInnermostFramePointer());
      }
    }
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }

  //---------------------------//
  // Low level output locking. //
  //---------------------------//

  static void lockOutput() {
    if (VM_Scheduler.numProcessors == 1) return;
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    do {
      int processorId = VM_Magic.prepareInt(VM_Magic.getJTOC(), VM_Entrypoints.outputLockField.getOffset());
      if (processorId != 0) {
        // expect 0 but got another processor's ID
        continue;
      }
      // Attempt atomic swap of outputLock to out processor ID
      if(!VM_Magic.attemptInt(VM_Magic.getJTOC(),
          VM_Entrypoints.outputLockField.getOffset(),
          0, VM_Processor.getCurrentProcessorId())) {
        continue;
      }
    } while (false);
    VM_Magic.isync(); // TODO!! is this really necessary?
  }

  static void unlockOutput() {
    if (VM_Scheduler.numProcessors == 1) return;
    VM_Magic.sync(); // TODO!! is this really necessary?
    if (true) {
      outputLock = 0; // TODO!! this ought to work, but doesn't?
    } else {
      do {
        int processorId = VM_Magic.prepareInt(VM_Magic.getJTOC(), VM_Entrypoints.outputLockField.getOffset());
        if (VM.VerifyAssertions && processorId != VM_Processor.getCurrentProcessorId()) {
          VM.sysExit(VM.EXIT_STATUS_SYSFAIL);
        }
        if (VM_Magic.attemptInt(VM_Magic.getJTOC(), VM_Entrypoints.outputLockField.getOffset(), processorId, 0)) {
          break;
        }
      } while (true);
    }
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }
}
