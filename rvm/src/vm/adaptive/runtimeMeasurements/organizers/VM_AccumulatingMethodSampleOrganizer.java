/*
 * (C) Copyright IBM Corp. 2003, 2004
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_CompiledMethods;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.opt.VM_OptCompiledMethod;

/**
 * An organizer for method listener information that 
 * simply accumulates the samples into a private
 * VM_MethodCountData instance.
 * 
 * This organizer is used to simply gather aggregate sample data and 
 * report it.
 * 
 * @author Dave Grove
 */
final class VM_AccumulatingMethodSampleOrganizer extends VM_Organizer {

  private VM_MethodCountData data;

  VM_AccumulatingMethodSampleOrganizer() {
    makeDaemon(true);
  }

  /**
   * Initialization: set up data structures and sampling objects.
   */
  public void initialize() {
    data = new VM_MethodCountData();
    int numSamples = VM_Controller.options.METHOD_SAMPLE_SIZE * VM_Scheduler.numProcessors;
    if (VM_Controller.options.mlCBS()) {
      numSamples *= VM.CBSMethodSamplesPerTick;
    }
    VM_MethodListener methodListener = new VM_MethodListener(numSamples);
    listener = methodListener;
    listener.setOrganizer(this);
    if (VM_Controller.options.mlTimer()) {
      VM_RuntimeMeasurements.installTimerMethodListener(methodListener);
    } else if (VM_Controller.options.mlCBS()) {
      VM_RuntimeMeasurements.installCBSMethodListener(methodListener);
    } else {
      if (VM.VerifyAssertions) VM._assert(false, "Unexpected value of method_listener_trigger");
    }
  }
  
  /**
   * Method that is called when the sampling threshold is reached
   */
  void thresholdReached() {
    if (VM.LogAOSEvents) VM_AOSLogging.organizerThresholdReached();
    int numSamples = ((VM_MethodListener)listener).getNumSamples();
    int[] samples = ((VM_MethodListener)listener).getSamples();
    data.update(samples, numSamples);
  }

  public void report() {
    VM.sysWrite("\nMethod sampler report");
    if (data != null) data.report();
  }
}
