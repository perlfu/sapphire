/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$

package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.classloader.VM_Method;
import com.ibm.JikesRVM.classloader.VM_NormalMethod;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_RuntimeCompiler;
import com.ibm.JikesRVM.opt.*;
import java.io.*;

/**
 * This class provides logging functionality for the Adaptive Optimization System
 *
 * Right now this is fairly primitive, an evolving number of events are
 * defined and log entries are quite unsophisticated.
 * Some obvious TODO items:
 *  -- compact encoding of log entries
 *  -- some notion of log format versions
 *  -- ...
 *
 * NOTE: All code that writes to the log is synchronized on the PrintStream
 *      object to avoid interspersed messages, which can happen when the
 *      compilation thread and the controller thread try to log a message
 *      "at the same time".
 * 
 * ***When is the log file flushed and closed?
 * ***Do we want to put report() information in the log?
 *
 * The current logging levels are:
 *   0  Do no logging
 *   1  Do minimal logging at startup and VM exit.  
 *      If at all possible, do not log anything during program execution.
 *      This logging level is supposed to produce minimal performance pertubation.
 *   2  Log interesting AOS events and controller actions
 *   3  Exhaustively log pretty much everything that is going on
 *
 * @author Dave Grove
 * @author Michael Hind
 * @modified Peter Sweeney
 */
public class VM_AOSLogging {

  /*
   * The output file stream, where all log messages will go
   */
  private static PrintStream log;
  
  /*
   * Record that the AOS logging has been booted.
   * Needed to allow fast exit from reporting to ensure
   * that when no class is specified to be run but "-help" is specified, 
   * don't want null pointer exception to occur!
   */
  private static boolean booted = false;

  /**
   * Return whether AOS logging has booted.
   * @return whether AOS logging has booted
   */
  public static boolean booted() {
    return booted;
  }
  
  /**
   * Called from VM_ControllerThread.run to initialize the logging subsystem
   */
  public static void boot() {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      try {
        log = new PrintStream(new FileOutputStream(VM_Controller.options.LOGFILE_NAME));

        // This statement will force the compilation of println, so it
        // is needed regardless of the particular content of the message!
        synchronized (log) {
          log.println(VM_Controller.controllerClock +" Logging enabled\n");
          log.println(VM_Controller.options);
        }
      }
      catch (IOException e) {
        VM.sysWrite("IOException caught in VM_AOSLogging.java while trying to create and start log file.\n");
        VM.sysWrite("Please check for file permission problems\n");
      }
    }
    booted = true;
  }

  ////////////////////////////////////////////////////////////////
  // Logging level 1
  ////////////////////////////////////////////////////////////////

  /**
   * Called from VM_Controller.report to allow a last message to the logging
   *  system
   */
  public static void systemExiting() {
    if (!booted) return; // fast exit
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock +" System Exiting\n");
      }
    }
  }

  /**
   * Called from VM_RuntimeMeasurements when the argument thread is terminating
   * to allow us to record the time spent in the thread.
   * @param t the thread of interest
   */
  public static void threadExiting(VM_Thread t) {
    if (!booted) return; // fast exit
    try {
      if (VM_Controller.options.LOGGING_LEVEL >= 1) {
        synchronized (log) {
          log.println(VM_Controller.controllerClock +
                      " ThreadIndex: " + t.getIndex() + " "
                      + t.getClass().getName() + " "
                      + " Time: " 
                      + (t.getCPUTimeMillis() / 1000)
                      + " status("
                      + (  t.isIdleThread() ?     "i"         // idle daemon
                           : t.isGCThread() ?     "g"       // gc daemon
                           : t.isDaemonThread()  ?     "d"       // user daemon
                           :                      "" )
                      + (!t.isAlive()     ?     "!" : "")     // dead/alive
                      + ")"
                      );
        }
      }
    } catch (NullPointerException e) {
      // ignore.  A thread exited before the AOS Logging system was
      // initialized.  It can't be interesting.
    }
  }

  /**
   * Call this method when the controller thread initially begins executing
   */
  public static void controllerStarted() {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Controller thread started");
      }
    }
  }

  /**
   * Call this method to dump statistics on how often listeners are invoked.
   * @param method method listener info
   * @param context context listener info
   * @param nll null listener info
   */
  public static void listenerStatistics(int method, int context, int nll) {
    if (!booted) return; // fast exit
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Listeners called:"+
                    "\n\t method "+method+"\n\t context "+context
                    +"\n\t null "+nll);
      }
    }
  }

  /**
   * Call this method to dump statistics related to decaying
   * @param decayCount the number of decay events
   */
  public static void decayStatistics(int decayCount) {
    if (!booted) return; // fast exit
    if (VM_Controller.options.LOGGING_LEVEL >=  1) {
      synchronized (log) {
        log.print(VM_Controller.controllerClock 
                  +" Decay Organizer Statistics: \n\t"+
                  " Num of Decay events: "+
                  decayCount+"\n");
      }
    }
  }

  /**
   * Call this method when one run of the application is completed
   */
  public static void appRunStart(String prog, int run) {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Application "+prog+" starting run "+run);
      }
    }
  }

  /**
   * Call this method when one run of the application is completed
   */
  public static void appRunComplete(String prog, int run) {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Application "+prog+" completed run "+run);
      }
    }
  }


  /**
   * Call this method when the controller thread is exiting.  This can 
   * cause us lots and lots of trouble if we are exiting as part of handling
   * an OutOfMemoryError.  We resolve *that* problem by means of a test in
   * VM_Runtime.deliverException(). 
   */
  public static void controllerCompleted() {
    if (!booted) return; // fast exit
  
    int awoken = VM_ControllerMemory.getNumAwoken(); 
    int didNothing = VM_ControllerMemory.getNumDidNothing();
    int numMethodsConsidered = VM_ControllerMemory.getNumMethodsConsidered();
    int numMethodsScheduledForRecomp = 
                    VM_ControllerMemory.getNumMethodsScheduledForRecomp(); 
    int numOpt0 = VM_ControllerMemory.getNumOpt0();
    int numOpt1 = VM_ControllerMemory.getNumOpt1();
    int numOpt2 = VM_ControllerMemory.getNumOpt2();
    int numOpt3 = VM_ControllerMemory.getNumOpt3();

    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.print(VM_Controller.controllerClock 
                  +" Controller thread exiting ... "
                  +"\n  Num times Controller thread is awoken: "+
                  awoken
                  +"\n  Num times did nothing: "+ didNothing +" ("+
                  ((int)((float)didNothing/(float)awoken * 100))
                  +"%)\n  Num methods baseline compiled: "+
                  VM_ControllerMemory.getNumBase()
                  +"\n  Num methods considered for recompilation: "+
                  numMethodsConsidered
                  +"\n  Num methods chosen to recompile: "+ 
                  numMethodsScheduledForRecomp +" ("+
                  ((int) ((float) numMethodsScheduledForRecomp
                                         /  numMethodsConsidered * 100))
                  +"%)\n  Opt Levels Chosen: "
                  +"\n\t Opt Level 0: "+ numOpt0 +" ("+
                  ((int) ((float) numOpt0/numMethodsScheduledForRecomp * 100))

                  +"%)\n\t Opt Level 1: "+ numOpt1 +" ("+
                  ((int) ((float) numOpt1/numMethodsScheduledForRecomp * 100))
                  +"%)\n"

                  +"\t Opt Level 2: "+ numOpt2 +" ("+
                  ((int) ((float) numOpt2/numMethodsScheduledForRecomp * 100))
                  +"%)\n"

                  +"\t Opt Level 3: "+ numOpt3 +" ("+
                  ((int) ((float) numOpt3/numMethodsScheduledForRecomp * 100))
                  +"%)\n\n");

        // Let the controller memory summarize itself to the log file
        VM_ControllerMemory.printFinalMethodStats(log);
      }
    }
  }


  /**
   * Call this method when the compilation thread initially begins executing
   */
  public static void compilationThreadStarted() {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Compilation thread started");
      }
    }
  }

  /**
   * Call this method when the compilation thread is exiting
   */
  public static void compilationThreadCompleted() {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Compilation thread exiting");
      }
    }
  }

  /**
   * Call this method when the organizer thread initially begins executing
   * @param filterOptLevel the opt level that we are filtering
   */
  public static void methodSampleOrganizerThreadStarted(int filterOptLevel) {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Method Sample Organizer thread started");
        log.println("  filterOptLevel: "+ filterOptLevel);
      }
    }
  }

  /**
   * Call this method when the organizer thread initially begins executing
   */
  public static void AIByEdgeOrganizerThreadStarted() {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Adaptive Inlining (AI) by Edge Organizer thread started");
      }
    }
  }

  /**
   * This method reports the basic speedup rate for a compiler
   * @param compiler the compiler you are reporting about
   * @param rate the speedup rate
   */
  public static void reportSpeedupRate(int compiler, double rate) {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" SpeedupRate for "+ 
                    VM_CompilerDNA.getCompilerString(compiler)
                    +" compiler: "+ rate);
      }
    }
  }

  /**
   * This method reports the basic compilation rate for a compiler
   * @param compiler the compiler you are reporting about
   * @param rate the compilation rate (bytecodes per millisecond)
   */
  public static void reportCompilationRate(int compiler, double rate) {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Compilation Rate (bytecode/msec) for "+ 
                    VM_CompilerDNA.getCompilerString(compiler)
                    +" compiler: "+ rate);
      }
    }
  }

  /**
   *  This method reports the benefit ratio from one compiler to the other
   *  @param compiler1 the first compiler
   *  @param compiler2 the second compiler
   *  @param rate the improvement from going from a compiler1-compiled method
   *                   to a compiler2-compiled method
   */
  public static void reportBenefitRatio(int compiler1, 
                                        int compiler2, 
                                        double rate) {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Benefit Ratio from "+
                    VM_CompilerDNA.getCompilerString(compiler1)
                    +" compiler to "+
                    VM_CompilerDNA.getCompilerString(compiler2)
                    +" compiler: "+ rate);
      }
    }
  }

  /**
   *  This method reports the compile time ratio from one compiler to
   *  the other
   *  @param compiler1 the first compiler
   *  @param compiler2 the second compiler
   *  @param rate the ratio of compiler1 compilation rate to 
   *                compiler2 compilation rate
   */
  public static void reportCompileTimeRatio(int compiler1, 
                                            int compiler2, 
                                            double rate) {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Compile Time Ratio of "+
                    VM_CompilerDNA.getCompilerString(compiler1)
                    +" compiler to "+
                    VM_CompilerDNA.getCompilerString(compiler2)
                    +" compiler: "+ rate);
      }
    }
  }

  ////////////////////////////////////////////////////////////////
  // Logging level 2
  ////////////////////////////////////////////////////////////////

  /**
   * This method logs the scheduling of a recompilation,
   * i.e., it being inserted in the compilation queue.
   * @param plan the OPT_Compilation plan being executed.
   * @param priority a number from 0.0 to 1.0 encoding the plan's priority.
   */
  public static void recompilationScheduled(OPT_CompilationPlan plan, 
                                            double priority) {
    if (VM_Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Scheduling level "+
                    plan.options.getOptLevel() +" recompilation of "+
                    plan.method+" (plan has priority "+priority+")");
      }
    }
  }

  /**
   * This method logs when a recompilation could not occur
   * because the queue is full
   * @param plan the OPT_Compilation plan that would have been executed
   */
  public static void recompilationQueueFull(OPT_CompilationPlan plan) {
    if (VM_Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Level "+ plan.options.getOptLevel()
                    +" recompilation postponed of "+plan.method);
      }
    }
  }

  /**
   * This method logs when the controller could not be notified 
   * of an event because the controllerInputQueue was full.
   * @param event the event the controller would have been told about
   */
  public static void controllerInputQueueFull(Object event) {
    if (VM_Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock + 
                    " Unable to inform controller of "+event+ " due to full input queue");
      }
    }
  }

  /**
   * This method logs the beginning of an adaptively selected recompilation
   * @param plan the OPT_Compilation plan being executed.
   */
  public static void recompilationStarted(OPT_CompilationPlan plan) {
    if (VM_Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock +" Recompiling (at level "+
                    plan.options.getOptLevel() +") "+ plan.method);
      }
    }
  }

  /**
   * This method logs the successful completion of an adaptively 
   * selected recompilation
   * @param plan the OPT_Compilation plan being executed.
   */
  public static void recompilationCompleted(OPT_CompilationPlan plan) {
    if (VM_Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock +"  Recompiled (at level "+
                    plan.options.getOptLevel() +") " +plan.method);
      }
    }
  }

  /**
   * This method logs the abortion of an adaptively selected recompilation
   * @param plan the OPT_Compilation plan being executed.
   */
  public static void recompilationAborted(OPT_CompilationPlan plan) {
    if (VM_Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Failed recompiling (at level "+ 
                    plan.options.getOptLevel()+" "+ plan.method);
      }
    }
  }

  /**
   * This method logs the actual compilation time for the given compiled method.
   * @param cm the compiled method
   * @param expectedCompilationTime the model-derived expected compilation time
   */
  public static void recordCompileTime(VM_CompiledMethod cm, double expectedCompilationTime) {
    if (log != null && VM_Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        double compTime = cm.getCompilationTime();
        log.println(VM_Controller.controllerClock 
                    +" Compiled "+cm.getMethod() + " with "+ cm.getCompilerName()
                    +" in "+ compTime+ " ms"+
                    ", model estimated: "+ expectedCompilationTime +" ms"
                    +", rate: "+ 
                    (((VM_NormalMethod) cm.getMethod()).getBytecodeLength() / compTime));
      }
    }
  }

  /**
   * this method logs the event when the controller discovers a method that has 
   * been recompiled and the previous version is still regarded as hot, 
   * i.e., still on the stack and signficant.
   */
  public static void oldVersionStillHot(VM_HotMethodEvent hme) {
    if (VM_Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Found a method with an old version still hot "+ hme);
      }
    }
  }

  /**
   * This method logs when the decay organizer runs.
   */
  public static void decayingCounters() {
    if (VM_Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Decaying clock and decayable objects");
      }
    }
  }

  /**
   * This Method logs when the organizer thread has reached its
   * sampling threshold
   */
  public static void organizerThresholdReached() {
    if (VM_Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" OrganizerThread reached sample size threshold\n");
      }
    }
  }

  /**
   * This method reports a bulk detection of hot max-opt-level methods to 
   * have their call edges inspected.
   *
   * @param numMethods the total number of max opt level methods found to be hot
   *                     and will have their call edges inspected
   */
  public static void AIorganizerFoundHotMethods(int numMethods) {
    if (VM_Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" AI organizer found "+numMethods
                    +" hot max-opt-level methods, will inspect their call edges.");
      }
    }
  }

  /**
   * This method logs that the a hot call edge from an max-opt-level
   * method has been identified.
   * 
   * @param hotMethod   method to be recompiled,
   * @param numSamples  number of samples attributed to the method 
   * @param boost       expected boost factor
   */
  public static void inliningOpportunityDetected(VM_CompiledMethod hotMethod,
                                                 double numSamples, 
                                                 VM_CallSiteTriple triple) {
    if (VM_Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" AI organizer found method "+hotMethod.getMethod()+
                    " with "+numSamples+" samples that has an edge "+
                    triple+" that can be inlined");
      }
    }
  }

  /**
   * This method logs that the controller is notified of a 
   * candidate to be recompiled due to inlining opportunities;
   * i.e., the method has been inserted in the controller queue.
   * @param hotMethod   method to be recompiled,
   * @param numSamples  number of samples attributed to the method
   * @param triple      edge that should be inlined.
   */
  public static void controllerNotifiedForInlining(VM_CompiledMethod hotMethod,
                                                   double numSamples, 
                                                   double boost) {
    if (VM_Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" AI organizer notified controller that method "+
                    hotMethod.getMethod()+" with "+numSamples+
                    " samples could be recompiled with a boost of "+boost);
      }
    }
  }

  /**
   * This method logs that the controller is notified of a 
   * candidate to be recompiled due to hotness;
   * i.e., the method has been inserted in the controller queue.
   * @param hotMethod   method to be recompiled, and
   * @param numSamples  number of samples attributed to the method
   */
  public static void controllerNotifiedForHotness(VM_CompiledMethod hotMethod,
                                 double numSamples) {
    if (VM_Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +" Controller notified that method "+hotMethod.getMethod()
                    + "(" + hotMethod.getId() + ")" + 
                    " has "+numSamples+" samples");
      }
    }
  }

  ////////////////////////////////////////////////////////////////
  // Logging level 3
  ////////////////////////////////////////////////////////////////

  /**
   * This method logs a controller cost estimate for doing nothing.
   * @param method the method of interest
   * @param optLevel the opt level being estimated, -1 = baseline
   * @param cost  the computed cost for this method and level
   */
  public static void recordControllerEstimateCostDoNothing(VM_Method method, 
                                                           int optLevel,
                                                           double cost) {
    if (VM_Controller.options.LOGGING_LEVEL >= 3) {
      synchronized (log) {
        log.print(VM_Controller.controllerClock 
                    +"  Estimated cost of doing nothing (leaving at ");
        if (optLevel == -1) {
          log.print("baseline");
        }
        else {
          log.print("O"+ optLevel);
        }
        log.println(") to "+ method +" is "+ cost);
      }
    }
  }

  /**
   * This method logs a controller cost estimate.
   * @param method the method of interest
   * @param choiceDesc a String describing the choice point
   * @param compilationTime the computed compilation cost for this method and level
   * @param futureTime the computed future time, including cost and execution
   */
  public static void recordControllerEstimateCostOpt(VM_Method method, 
                                                     String choiceDesc,
                                                     double compilationTime,
                                                     double futureTime) {
    if (VM_Controller.options.LOGGING_LEVEL >= 3) {
      synchronized (log) {
        log.println(VM_Controller.controllerClock 
                    +"  Estimated cost of OPT compiling "+
                    method + " at " + choiceDesc +
                    " is "+ compilationTime +
                    ", total future time is "+ futureTime);
      }
    }
  }

  /**
   * Records lots of details about the online computation of a compilation rate
   * @param compiler compiler of interest
   * @param method the method
   * @param BCLength the number of bytecodes
   * @param totalBCLength cumulative number of bytecodes
   * @param MCLength size of machine code
   * @param totalMCLength cumulative size of machine code
   * @param compTime compilation time for this method
   * @param totalComptime cumulative compilation time for this method
   * @param totalLogOfRates running sum of the natural logs of the rates
   * @param totalLogValueMethods number of methods used in the log of rates
   * @param totalMethods total number of methods
   */
  public static void recordUpdatedCompilationRates(byte compiler,
                                                   VM_Method method,
                                                   int BCLength,
                                                   int totalBCLength,
                                                   int MCLength,
                                                   int totalMCLength,
                                                   double compTime,
                                                   double totalCompTime,
                                                   double totalLogOfRates,
                                                   int totalLogValueMethods,
                                                   int totalMethods) {

    if (VM_Controller.options.LOGGING_LEVEL >= 3) {
      synchronized (log) {
        boolean backBranch = false;
        if (method instanceof VM_NormalMethod) {
          backBranch = ((VM_NormalMethod)method).hasBackwardsBranch();
        }
        log.println(VM_Controller.controllerClock 
                    +"  Updated compilation rates for "+ VM_RuntimeCompiler.getCompilerName(compiler) +"compiler");
        log.println("\tmethod compiled: "+ method);
        log.println("\tbyte code length: "+ BCLength +", Total: "+ totalBCLength);
        log.println("\tmachine code length: "+ MCLength +", Total: "+ totalMCLength);
        log.println("\tbackwards branch: " + (backBranch ? "yes" : "no"));
        log.println("\tcompilation time: "+ compTime +", Total: "+ totalCompTime);
        log.println("\tRate for this method: "+ BCLength / compTime
                    +", Total of Logs: "+ totalLogOfRates);
        log.println("\tTotal Methods: "+ totalMethods);
        log.println("\tNew Rate: "+ Math.exp(totalLogOfRates / totalLogValueMethods));
      }
    }
  }

  ////////////////////////////////////////////////////////////////
  // OSR-related code
  ////////////////////////////////////////////////////////////////

  //-#if RVM_WITH_OSR
  public static void recordOSRRecompilationDecision(VM_ControllerPlan plan) {
    OPT_CompilationPlan cplan = plan.getCompPlan();
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized(log) {
        log.println(VM_Controller.controllerClock +" recompile with OSR " +
                    "( at level " + cplan.options.getOptLevel() +" ) " + cplan.method);
      }
    }
  }
  public static void onStackReplacementStarted(OPT_CompilationPlan plan) {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized(log) {
        log.println(VM_Controller.controllerClock +" OSR starts " +
                    "( at level " + plan.options.getOptLevel() +" ) " + plan.method);
      }
    }
  }

  public static void onStackReplacementCompleted(OPT_CompilationPlan plan) {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized(log) {
        log.println(VM_Controller.controllerClock +" OSR ends " +
                    "( at level " + plan.options.getOptLevel() +" ) " + plan.method);
      }
    }
  }

  public static void onStackReplacementAborted(OPT_CompilationPlan plan) {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized(log) {
        log.println(VM_Controller.controllerClock +" OSR failed "+
                    "( at level " + plan.options.getOptLevel() +" ) " + plan.method);
      }
    }
  }

  public static void logOsrEvent(String s) {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized(log) {
        log.println(VM_Controller.controllerClock + " " + s);
      }
    }
  }

  public static void deOptimizationStarted() {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized(log) {
        log.println(VM_Controller.controllerClock +" Deoptimization starts ");
      }
    }
  }

  public static void deOptimizationCompleted() {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized(log) {
        log.println(VM_Controller.controllerClock +" Deoptimization ends.");
      }
    }
  }

  public static void deOptimizationAborted() {
    if (VM_Controller.options.LOGGING_LEVEL >= 1) {
      synchronized(log) {
        log.println(VM_Controller.controllerClock +" Deoptimization aborted.");
      }
    }
  }
  //-#endif
  public static void debug(String s) {
    if (VM_Controller.options.LOGGING_LEVEL >= 2) {
      synchronized(log) {
        log.println(VM_Controller.controllerClock + s);
      }
    }
  }

  //-#if RVM_WITH_OSR
  public static void onstackreplacementStarted(OPT_CompilationPlan plan) {
    synchronized(log) {
      log.println(VM_Controller.controllerClock +" OSR starts " +
         "( at level " + plan.options.getOptLevel() +" ) " + plan.method);
    }
  }
 
  public static void onstackreplacementCompleted(OPT_CompilationPlan plan) {
    synchronized(log) {
      log.println(VM_Controller.controllerClock +" OSR ends " +
        "( at level " + plan.options.getOptLevel() +" ) " + plan.method);
    }
  }
 
  public static void onstackreplacementAborted(OPT_CompilationPlan plan) {
    synchronized(log) {
      log.println(VM_Controller.controllerClock +" OSR failed "+
         "( at level " + plan.options.getOptLevel() +" ) " + plan.method);
    }
  }
  //-#endif
}
