/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_BaselineCompiler;
import java.util.Vector;
import java.util.Enumeration;

/**
 * This class implements the controller thread.  This entity is the brains of 
 * the adaptive optimization system.  It communicates with the runtime 
 * measurements subsystem to instruct and gather profiling information.  
 * It also talks to the compilation threads to generate 
 *     a) instrumented executables;
 *     b) optimized executables; 
 *     c) static information about a method; or 
 *     d) all of the above.
 *
 *  @author Michael Hind
 *  @author David Grove
 *  @author Stephen Fink
 *  @author Peter Sweeney
 */
class VM_ControllerThread extends VM_Thread {

  public String toString() {
    return "VM_ControllerThread";
  }

  /**
   * constructor
   * @param sentinel: an object to signal when up and running
   */
  VM_ControllerThread(Object sentinel)  { 
    this.sentinel = sentinel; 
    makeDaemon(true);
  }
  private Object sentinel;


  /**
   * This method is the entry point to the controller, it is called when
   * the controllerThread is created.
   */
  public void run() {
    // save this object so others can access it, if needed
    VM_Controller.controllerThread = this;

    // Bring up the logging system
    if (VM.LogAOSEvents) VM_AOSLogging.boot();
    if (VM.LogAOSEvents) VM_AOSLogging.controllerStarted();

    // Create measurement entities that are NOT related to 
    // adaptive recompilation
    createProfilers();

    if (!VM_Controller.options.adaptive()) {
      // We're running an AOS bootimage with a non-adaptive primary strategy. 
      // We already set up any requested profiling infrastructure, so nothing
      // left to do but exit.
      controllerInitDone();
      VM.sysWrite("\nAOS: In non-adaptive mode; controller thread exiting.\n");
      return; // controller thread exits.
    }

    // Initialize the CompilerDNA class
    // This computes some internal options, must be done early in boot process
    VM_CompilerDNA.init();

    // Create the organizerThreads and schedule them
    createOrganizerThreads();

    // Create the compilationThread and schedule it
    createCompilationThread();

    // Initialize the controller "memory"
    VM_ControllerMemory.init();

    // Create our set of standard optimization plans.
    VM_Controller.recompilationStrategy.init();

    controllerInitDone();

    // Enter main controller loop.
    // Pull an event to process off of 
    // VM_Controller.controllerInputQueue and handle it.  
    // If no events are on the queue, then the deleteMin call will 
    // block until an event is available.
    // Repeat forever.
    while (true) {
      if (VM_Controller.options.EARLY_EXIT &&
	  VM_Controller.options.EARLY_EXIT_TIME < VM_Controller.controllerClock) {
	VM_Controller.stop();
      }
      Object event = VM_Controller.controllerInputQueue.deleteMin();
      ((VM_ControllerInputEvent)event).process();
    }
  }

  // Now that we're done initializing, Schedule all the organizer threads
  // and signal the sentinel object.
  private void controllerInitDone() {
    for (Enumeration e = VM_Controller.organizers.elements(); 
	 e.hasMoreElements(); ) {
      VM_Organizer o = (VM_Organizer)e.nextElement();
      o.start();
    }

    try {
      synchronized(sentinel) {
	sentinel.notify();
      }
    } catch (Exception e) {
      e.printStackTrace();
      VM.sysFail("Failed to start up controller subsystem");
    }
  }

  
  /**
   * Called when the controller thread is about to wait on 
   * VM_Controller.controllerInputQueue
   */
  public void aboutToWait() {
  }


  /**
   * Called when the controller thread is woken after waiting on 
   * VM_Controller.controllerInputQueue
   */
  public void doneWaiting() {
    if (VM.LogAOSEvents) VM_ControllerMemory.incrementNumAwoken();
  }


  ///////////////////////
  // Initialization.
  //  Create AOS threads.
  //  Initialize AOS data structures that depend on command line arguments.
  ///////////////////////

  /**
   *  Create the compilationThread and schedule it
   */
  private void createCompilationThread() {
    VM_CompilationThread ct = new VM_CompilationThread();
    VM_Controller.compilationThread = ct;
    ct.start();
  }

  /**
   * Create profiling entities that are NOT used for adaptive recompilation
   */
  private void createProfilers() {
    VM_AOSOptions opts = VM_Controller.options;

    if (opts.GATHER_PROFILE_DATA) {
      VM_MethodListener methodListener = 
	new VM_MethodListener(opts.INITIAL_SAMPLE_SIZE);
      VM_Organizer methodOrganizer = 
	new VM_AccumulatingMethodSampleOrganizer(methodListener);
      VM_Controller.organizers.addElement(methodOrganizer);
    }
  }


  /**
   *  Create the organizerThreads and schedule them
   */
  private void createOrganizerThreads() {
    VM_AOSOptions opts = VM_Controller.options;

    if (opts.sampling()) {
      // Primary backing store for method sample data
      VM_Controller.methodSamples = new VM_MethodCountData();

      // Instal organizer to drive method recompilation 
      VM_MethodListener methodListener = 
	new VM_MethodListener(opts.INITIAL_SAMPLE_SIZE);
      VM_Organizer methodOrganizer = 
	new VM_MethodSampleOrganizer(methodListener, opts.FILTER_OPT_LEVEL);
      VM_Controller.organizers.addElement(methodOrganizer);

      // Decay runtime measurement data 
      if (opts.ADAPTIVE_INLINING) {
	VM_Organizer decayOrganizer = 
	  new VM_DecayOrganizer(new VM_YieldCounterListener(opts.DECAY_FREQUENCY));
	VM_Controller.organizers.addElement(decayOrganizer);
      }
    
      if (opts.ADAPTIVE_INLINING) {
	VM_Organizer AIOrganizer = 
	  new VM_AIByEdgeOrganizer(new VM_EdgeListener());
	VM_Controller.organizers.addElement(AIOrganizer);
      }
    } else if (opts.counters()) {
      VM_InvocationCounts.createOptimizationPlan();
      VM_BaselineCompiler.options.INVOCATION_COUNTERS=true;
    }
    
    //-#if RVM_WITH_OSR
    VM_Controller.osrOrganizer = new OSR_OrganizerThread();
    VM_Controller.osrOrganizer.start();
    //-#endif
  }


  /**
   * Final report
   */
  public static void report() {
    if (VM.LogAOSEvents) VM_AOSLogging.controllerCompleted();
  }

} 
