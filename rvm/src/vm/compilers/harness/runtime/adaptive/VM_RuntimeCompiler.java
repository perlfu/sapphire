/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$

/**
 *
 * The adaptive version of the runtime compiler.
 * 
 * @author Michael Hind
 * @modified by Matthew Arnold
 */
class VM_RuntimeCompiler extends VM_RuntimeOptCompilerInfrastructure {
  static OPT_InlineOracle offlineInlineOracle;

  static void boot() { 
    VM.sysWrite("VM_RuntimeCompiler: boot (adaptive compilation)\n");

    // initialize the OPT compiler, if any operation throws
    // an exception, we'll let it propagate to our caller because we
    // need both the baseline and OPT compilers to work
    // to perform adaptive compilation.
    // Currently, the baseline compiler does not have a boot method
    VM_RuntimeOptCompilerInfrastructure.boot(); 

  }
  
  static void initializeMeasureCompilation() {
    VM_RuntimeOptCompilerInfrastructure.initializeMeasureCompilation(); 
  }
  
  static void processCommandLineArg(String arg) {
    if (compilerEnabled) {
      if (options.processAsOption("-X:aos:irc", arg)) {
	// update the optimization plan to reflect the new command line argument
	setNoCacheFlush(options);
	optimizationPlan = OPT_OptimizationPlanner.createOptimizationPlan(options);
      } else {
	VM.sysWrite("VM_RuntimeCompiler: Unrecognized argument \""+arg+"\" with prefix -X:aos:irc:\n");
	VM.sysExit(-1);
      }
    } else {
      VM.sysWrite("VM_RuntimeCompiler: Compiler not enabled; unable to process command line argument: "+arg+"\n");
      VM.sysExit(-1);
    }
  }
  
  // This will be called by the classLoader when we need to compile a method
  // for the first time.
  static VM_CompiledMethod compile(VM_Method method) {
    VM_CompiledMethod cm;

    if (!VM_Controller.enabled) {
      // System still early in boot process; compile with baseline compiler
      cm = baselineCompile(method);
      VM_ControllerMemory.incrementNumBase();
    } else {
      if (VM_Controller.options.optOnly()) {
	if (// will only run once: don't bother optimizing
	    method.isClassInitializer() || 
	    // exception in progress. can't use opt compiler: 
            // it uses exceptions and runtime doesn't support 
            // multiple pending (undelivered) exceptions [--DL]
	    VM_Thread.getCurrentThread().hardwareExceptionRegisters.inuse ||
	    // opt compiler doesn't support compiling the JNI 
            // stub needed to invoke native methods
	    method.isNative()) {
	  // compile with baseline compiler
	  cm = baselineCompile(method);
          VM_ControllerMemory.incrementNumBase();
	} else { // compile with opt compiler
	  // Initialize an instrumentation plan.
	  VM_AOSInstrumentationPlan instrumentationPlan = 
	    new VM_AOSInstrumentationPlan(VM_Controller.options,
					  method);

	  // create a compilation plan
	  OPT_CompilationPlan compPlan = 
	    new OPT_CompilationPlan(method, optimizationPlan, 
				    instrumentationPlan, options);

	  // If we have an inline oracle, use it!
	  if (offlineInlineOracle != null) {
	    compPlan.setInlineOracle(offlineInlineOracle);
	  }

	  cm = optCompileWithFallBack(method, compPlan);
	}
      } else {
	  // compile with baseline compiler
	  cm = baselineCompile(method);
          VM_ControllerMemory.incrementNumBase();
      }
    }
    return cm;
  }
}
