/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_GCMapIterator;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;

/**
 * A place to put code common to all runtime compilers that use the OPT
 * compiler, such as the adaptive and optimizing-only runtime compilers.
 *
 * @author Michael Hind
 * @author Dave Grove
 */
public class VM_RuntimeOptCompilerInfrastructure 
  extends VM_RuntimeCompilerInfrastructure {
  
  // is the opt compiler usable?
  protected static boolean compilerEnabled;  
  
  // is opt compiler currently in use?
  // This flag is used to detect/avoid recursive opt compilation.
  // (ie when opt compilation causes a method to be compiled).
  // We also make all public entrypoints static synchronized methods 
  // because the opt compiler is not reentrant. 
  // When we actually fix defect 2912, we'll have to implement a different
  // scheme that can distinguish between recursive opt compilation by the same
  // thread (always bad) and parallel opt compilation (currently bad, future ok).
  // NOTE: This code can be quite subtle, so please be absolutely sure
  // you know what you're doing before modifying it!!!
  protected static boolean compilationInProgress; 
  
  // One time check to optionally preload and compile a specified class
  protected static boolean preloadChecked = false;

  // Cache objects needed to cons up compilation plans
  public static OPT_Options options;
  public static OPT_OptimizationPlanElement[] optimizationPlan;

  /**
   * Perform intitialization for OPT compiler
   */
  public static void boot() throws OPT_OptimizingCompilerException {
    options = new OPT_Options();
    setNoCacheFlush(options);

    optimizationPlan = OPT_OptimizationPlanner.createOptimizationPlan(options);
    if (VM.MeasureCompilation) {
      VM_Callbacks.addExitMonitor(new VM_RuntimeCompilerInfrastructure());
      OPT_OptimizationPlanner.initializeMeasureCompilation();
    }

    OPT_Compiler.init(options);

    // when we reach here the OPT compiler is enabled.
    compilerEnabled = true;
  }

  /**
   * attempt to compile the passed method with the OPT_Compiler.
   * Don't handle OPT_OptimizingCompilerExceptions 
   *   (leave it up to caller to decide what to do)
   * Precondition: compilationInProgress "lock" has been acquired
   * @param method the method to compile
   * @param plan the plan to use for compiling the method
   */
  private static VM_CompiledMethod optCompile(VM_NormalMethod method, 
					      OPT_CompilationPlan plan) 
    throws OPT_OptimizingCompilerException {
    if (VM.VerifyAssertions) {
      VM._assert(compilationInProgress, "Failed to acquire compilationInProgress \"lock\"");
    }
    
    VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.JNI);
    double start = 0;
    if (VM.MeasureCompilation || VM.BuildForAdaptiveSystem) {
      double now = VM_Time.now();
      start = updateStartAndTotalTimes(now);
    }
    
    VM_CompiledMethod cm = OPT_Compiler.compile(plan);

    if (VM.MeasureCompilation || VM.BuildForAdaptiveSystem) {
      double now = VM_Time.now();
      double end = updateStartAndTotalTimes(now);
      double compileTime = (end - start) * 1000; // convert to milliseconds
      cm.setCompilationTime(compileTime);
      record(OPT_COMPILER, method, cm);
    }
    
    return cm;
  }
  

  // These methods are safe to invoke from VM_RuntimeCompiler.compile

  /**
   * This method tries to compile the passed method with the OPT_Compiler, 
   * using the default compilation plan.  If
   * this fails we will use the quicker compiler (baseline for now)
   * The following is carefully crafted to avoid (infinte) recursive opt
   * compilation for all combinations of bootimages & lazy/eager compilation.
   * Be absolutely sure you know what you're doing before changing it !!!
   * @param method the method to compile
   */
  public static synchronized VM_CompiledMethod optCompileWithFallBack(VM_NormalMethod method) {
    if (compilationInProgress) {
      return fallback(method);
    } else {
      try {
	compilationInProgress = true;
	OPT_CompilationPlan plan = new OPT_CompilationPlan(method, optimizationPlan, null, options);
	return optCompileWithFallBackInternal(method, plan);
      } finally {
	compilationInProgress = false;
      }
    }
  }

  /**
   * This method tries to compile the passed method with the OPT_Compiler
   * with the passed compilation plan.  If
   * this fails we will use the quicker compiler (baseline for now)
   * The following is carefully crafted to avoid (infinte) recursive opt
   * compilation for all combinations of bootimages & lazy/eager compilation.
   * Be absolutely sure you know what you're doing before changing it !!!
   * @param method the method to compile
   * @param plan the compilation plan to use for the compile
   */
  public static synchronized VM_CompiledMethod optCompileWithFallBack(VM_NormalMethod method, 
								      OPT_CompilationPlan plan) {
    if (compilationInProgress) {
      return fallback(method);
    } else {
      try {
	compilationInProgress = true;
	return optCompileWithFallBackInternal(method, plan);
      } finally {
	compilationInProgress = false;
      }
    }
  }

  /**
   * This real method that performs the opt compilation.  
   * @param method the method to compile
   * @param plan the compilation plan to use
   */
  private static VM_CompiledMethod optCompileWithFallBackInternal(VM_NormalMethod method, 
								  OPT_CompilationPlan plan) {
    if (method.hasNoOptCompilePragma()) return fallback(method);
    try {
      return optCompile(method, plan);
    } catch (OPT_OptimizingCompilerException e) {
      String msg = "VM_RuntimeCompiler: can't optimize \"" + method + "\" (error was: " + e + "): reverting to baseline compiler\n"; 
      if (e.isFatal && options.ERRORS_FATAL) {
	e.printStackTrace();
	VM.sysFail(msg);
      } else {
	boolean printMsg = true;
	if (e instanceof OPT_MagicNotImplementedException) {
	  printMsg = !((OPT_MagicNotImplementedException)e).isExpected;
	}
	if (printMsg) VM.sysWrite(msg);
      }
      return fallback(method);
    } 
  }


  //-#if RVM_WITH_OSR
  /* recompile the specialized method with OPT_Compiler. */ 
  public static VM_CompiledMethod
    recompileWithOptOnStackSpecialization(OPT_CompilationPlan plan) {

    if (VM.VerifyAssertions) { VM._assert(plan.method.isForOsrSpecialization());}

    if (compilationInProgress) {
      return null;
    }

    try {
      compilationInProgress = true;

      // the compiler will check if isForOsrSpecialization of the method
      VM_CompiledMethod cm = optCompile(plan.method, plan);

      // we donot replace the compiledMethod of original method, 
      // because it is temporary method
      return cm;

    } catch (OPT_OptimizingCompilerException e) {
		e.printStackTrace();
      String msg = "Optimizing compiler " 
	+"(via recompileWithOptOnStackSpecialization): "
	+"can't optimize \"" + plan.method + "\" (error was: " + e + ")\n"; 

      if (e.isFatal && plan.options.ERRORS_FATAL) {
	VM.sysFail(msg);
      } else {
	VM.sysWrite(msg);
      }
      return null;	
    } finally {
      compilationInProgress = false;
    }
  }
  //-#endif 


  /**
   * This method tries to compile the passed method with the OPT_Compiler.
   * It will install the new compiled method in the VM, if sucessful.
   * NOTE: the recompile method should never be invoked via 
   *      VM_RuntimeCompiler.compile;
   *   it does not have sufficient guards against recursive recompilation.
   * @param plan the compilation plan to use
   * @return the CMID of the new method if successful, -1 if the 
   *    recompilation failed.
   *
  **/
  public static synchronized int recompileWithOpt(OPT_CompilationPlan plan) {
    if (compilationInProgress) {
      return -1;
    } else {
      try {
	compilationInProgress = true;
	VM_CompiledMethod cm = optCompile(plan.method, plan);
	try {
	  plan.method.replaceCompiledMethod(cm);
	} catch (Throwable e) {
	  String msg = "Failure in VM_Method.replaceCompiledMethod (via recompileWithOpt): while replacing \"" + plan.method + "\" (error was: " + e + ")\n"; 
	  if (plan.options.ERRORS_FATAL) {
	    e.printStackTrace();
	    VM.sysFail(msg);
	  } else {
	    VM.sysWrite(msg);
	  }
	  return -1;
	}
	return cm.getId();
      } catch (OPT_OptimizingCompilerException e) {
	String msg = "Optimizing compiler (via recompileWithOpt): can't optimize \"" + plan.method + "\" (error was: " + e + ")\n"; 
	if (e.isFatal && plan.options.ERRORS_FATAL) {
	  e.printStackTrace();
	  VM.sysFail(msg);
	} else {
	  // VM.sysWrite(msg);
	}
	return -1;
      } finally {
	compilationInProgress = false;
      }
    }
  }

  /**
   * A wrapper method for those callers who don't want to make
   * optimization plans
   * @param method the method to recompile
   */
  public static int recompileWithOpt(VM_NormalMethod method) {
    OPT_CompilationPlan plan = new OPT_CompilationPlan(method, 
						       optimizationPlan, 
						       null, 
						       options);
    return recompileWithOpt(plan);
  }

  /**
   * This method uses the default compiler (baseline) to compile a method
   * It is typically called when a more aggressive compilation fails.
   * This method is safe to invoke from VM_RuntimeCompiler.compile
   */
  protected static VM_CompiledMethod fallback(VM_NormalMethod method) {
    // call the inherited method "baselineCompile"
    return baselineCompile(method);
  }

  /**
   * This method creates a opt-compiler GC Map iterator object that will be
   * used to report the stack GC roots.
   */
  public static VM_GCMapIterator createGCMapIterator(int[] registerLocations) {
    return new VM_OptGCMapIterator(registerLocations);
  }

  /**
   * This method detect if we're running on a uniprocessor and optimizes
   * accordingly.
   * One needs to call this method each time a command line argument is changed
   * and each time an OPT_Options object is created.
   * @param options the command line options for the opt compiler
  */
  public static void setNoCacheFlush(OPT_Options options) {
    if (options.DETECT_UNIPROCESSOR) {
      if (VM.sysCall0(VM_BootRecord.the_boot_record.sysNumProcessorsIP) == 1) {
	options.NO_CACHE_FLUSH = true;
      }
    }
  }

  /**
   * This method provides a hook to allow in depth compiler subsystem reports.
   * It was originally (and still?) called from VM_CompilationProfiler.
   * @param explain should we provide more details
   */
  public static void detailedCompilationReport(boolean explain) {
    // If/when the baseline compiler gets these, invoke them here.

    // Get the opt's report
    VM_TypeReference theTypeRef = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(),
								VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/opt/OPT_OptimizationPlanner;"));
    VM_Type theType = theTypeRef.peekResolvedType();
    if (theType != null && theType.asClass().isInitialized()) {
      OPT_OptimizationPlanner.generateOptimizingCompilerSubsystemReport(explain);
    } else {
      VM.sysWrite("\n\tNot generating Optimizing Compiler SubSystem Report because \n");
      VM.sysWrite("\tthe opt compiler was never invoked.\n\n");
    }
  }
}
