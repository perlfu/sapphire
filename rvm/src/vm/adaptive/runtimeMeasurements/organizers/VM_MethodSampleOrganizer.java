/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * An organizer for method listener information. 
 * <p>
 * This organizer is designed to work well with non-decayed 
 * cumulative method samples.  The basic idea is that each time 
 * the sampling threshold is reached we update the accumulated method 
 * sample data with the new data and then notify the controller of all 
 * methods that were sampled in the current window.
 * 
 * @author Dave Grove
 */
final class VM_MethodSampleOrganizer extends VM_Organizer {

  /**
   *  Filter out all opt-compiled methods that were compiled 
   * at this level or higher.
   */
  private int filterOptLevel;

  /**
   *  The listener
   */
  private VM_BasicMethodListener listener;

  /**
   * @param listener         the associated listener
   * @param filterOptLevel   filter out all opt-compiled methods that 
   *                         were compiled at this level or higher
   */
  VM_MethodSampleOrganizer(VM_BasicMethodListener listener, 
			   int filterOptLevel) {
    this.listener         = listener;
    this.filterOptLevel   = filterOptLevel;
    listener.setOrganizer(this);
  }

  /**
   * Initialization: set up data structures and sampling objects.
   */
  public void initialize() {
    if (VM.LogAOSEvents) 
      VM_AOSLogging.methodSampleOrganizerThreadStarted(filterOptLevel);

    // Install and activate my listener
    VM_RuntimeMeasurements.installMethodListener(listener);
    listener.activate();
  }

  /**
   * Method that is called when the sampling threshold is reached
   */
  void thresholdReached() {
    if (VM.LogAOSEvents) VM_AOSLogging.organizerThresholdReached();

    int numSamples = listener.getNumSamples();
    int[] samples = listener.getSamples();

    // (1) Update the global (cumulative) sample data
    VM_Controller.methodSamples.update(samples, numSamples);
    
    // (2) Remove duplicates from samples buffer.
    //     NOTE: This is a dirty trick and may be ill-advised.
    //     Rather than copying the unique samples into a different buffer
    //     we treat samples as if it was a scratch buffer.
    //     NOTE: This is worse case O(numSamples^2) but we expect a 
    //     significant number of duplicates, so it's probably better than
    //     the other obvious alternative (sorting samples).
    int uniqueIdx = 1;
  outer:
    for (int i=1; i<numSamples; i++) {
      int cur = samples[i];
      for (int j=0; j<uniqueIdx; j++) {
	if (cur == samples[j]) continue outer;
      }
      samples[uniqueIdx++] = cur;
    }

    // (3) For all samples in 0...uniqueIdx, if the method represented by
    //     the sample is compiled at an opt level below filterOptLevel
    //     and the total (cumulative) number of samples attributed to the
    //     method is above our absolute minimum, then report it to the
    //     controller. We have an absolute minimum value to avoid
    //     considering methods that haven't been sampled enough times to
    //     give us at least some reason to think that the fact that they
    //     were sampled wasn't just random bad luck.
    //     NOTE: this minimum is since the beginning of time, not
    //           just the current window.
    for (int i=0; i<uniqueIdx; i++) {
      int cmid = samples[i];
      double ns = VM_Controller.methodSamples.getData(cmid);
      if (ns > 3.0) {
	VM_CompilerInfo info = 
	  VM_ClassLoader.getCompiledMethod(cmid).getCompilerInfo();
	int compilerType = info.getCompilerType();

	// Enqueue it unless it's either a trap method or already opt compiled
	// at filterOptLevel or higher.
	if (!(compilerType == VM_CompilerInfo.TRAP ||
	      (compilerType == VM_CompilerInfo.OPT && 
	       (((VM_OptCompilerInfo)info).getOptLevel() >= filterOptLevel)))) {
	  VM_HotMethodRecompilationEvent event = 
	    new VM_HotMethodRecompilationEvent(cmid, ns);
	  if (VM_Controller.controllerInputQueue.prioritizedInsert(ns, event)){
	    if (VM.LogAOSEvents) {
	      VM_CompiledMethod m = VM_ClassLoader.getCompiledMethod(cmid);
	      VM_AOSLogging.controllerNotifiedForHotness(m, ns);
	    }
	  } else {
	    if (VM.LogAOSEvents) VM_AOSLogging.controllerInputQueueFull(event);
	  }
	}
      }
    }
    
    // (4) Get the listener ready to go and activate it for the next 
    //     sampling window.
    listener.reset();
    listener.activate();
  }
}
