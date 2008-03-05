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
package org.jikesrvm.compilers.opt.driver;

import java.util.Vector;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Callbacks;
import org.jikesrvm.adaptive.recompilation.VM_CompilerDNA;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.baseline.VM_BaselineCompiler;
import org.jikesrvm.compilers.baseline.VM_EdgeCounts;
import org.jikesrvm.compilers.common.VM_BootImageCompiler;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.opt.MagicNotImplementedException;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;

/**
 * Use optimizing compiler to build virtual machine boot image.
 */
public final class VM_OptimizingBootImageCompiler extends VM_BootImageCompiler {

  // Cache objects needed to cons up compilation plans
  private final Vector<OptimizationPlanElement[]> optimizationPlans = new Vector<OptimizationPlanElement[]>();
  private final Vector<Boolean> optimizationPlanLocks = new Vector<Boolean>();
  private final Vector<OptOptions> options = new Vector<OptOptions>();
  private final OptOptions masterOptions = new OptOptions();

  // If excludePattern is null, all methods are opt-compiled (or attempted).
  // Otherwise, methods that match the pattern are not opt-compiled.
  // In any case, the class VM_OptSaveVolatile is always opt-compiled.
  //
  private String excludePattern;

  private boolean match(VM_Method method) {
    if (excludePattern == null) return true;
    VM_Class cls = method.getDeclaringClass();
    String clsName = cls.toString();
    if (clsName.compareTo("org.jikesrvm.compilers.opt.runtimesupport.VM_OptSaveVolatile") == 0) return true;
    String methodName = method.getName().toString();
    String fullName = clsName + "." + methodName;
    return (fullName.indexOf(excludePattern)) < 0;
  }

  /**
   * Initialize boot image compiler.
   * @param args command line arguments to the bootimage compiler
   */
  protected void initCompiler(String[] args) {
    try {
      VM_BaselineCompiler.initOptions();
      VM.sysWrite("VM_BootImageCompiler: init (opt compiler)\n");

      // Writing a boot image is a little bit special.  We're not really
      // concerned about compile time, but we do care a lot about the quality
      // and stability of the generated code.  Set the options accordingly.
      OptimizingCompiler.setBootOptions(masterOptions);

      // Allow further customization by the user.
      for (int i = 0, n = args.length; i < n; i++) {
        String arg = args[i];
        if (!masterOptions.processAsOption("-X:bc:", arg)) {
          if (arg.startsWith("exclude=")) {
            excludePattern = arg.substring(8);
          } else {
            VM.sysWrite("VM_BootImageCompiler: Unrecognized argument " + arg + "; ignoring\n");
          }
        }
      }
      VM_EdgeCounts.boot(masterOptions.EDGE_COUNT_INPUT_FILE);
      OptimizingCompiler.init(masterOptions);
    } catch (OptimizingCompilerException e) {
      String msg = "VM_BootImageCompiler: Compiler failed during initialization: " + e + "\n";
      if (e.isFatal) {
        // An unexpected error when building the opt boot image should be fatal
        e.printStackTrace();
        System.exit(VM.EXIT_STATUS_OPT_COMPILER_FAILED);
      } else {
        VM.sysWrite(msg);
      }
    }
  }

  /**
   * Compile a method with bytecodes.
   * @param method the method to compile
   * @return the compiled method
   */
  protected VM_CompiledMethod compileMethod(VM_NormalMethod method, VM_TypeReference[] params) {
    if (method.hasNoOptCompileAnnotation()) {
      return baselineCompile(method);
    } else {
      VM_CompiledMethod cm = null;
      OptimizingCompilerException escape = new OptimizingCompilerException(false);
      try {
        VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.OPT);
        boolean include = match(method);
        if (!include) {
          throw escape;
        }
        int freeOptimizationPlan = getFreeOptimizationPlan();
        OptimizationPlanElement[] optimizationPlan = optimizationPlans.get(freeOptimizationPlan);
        CompilationPlan cp =
          new CompilationPlan(method, params, optimizationPlan, null, options.get(freeOptimizationPlan));
        cm = OptimizingCompiler.compile(cp);
        if (VM.BuildForAdaptiveSystem) {
          /* We can't accurately measure compilation time on Host JVM, so just approximate with DNA */
          int compilerId = VM_CompilerDNA.getCompilerConstant(cp.options.getOptLevel());
          cm.setCompilationTime((float)VM_CompilerDNA.estimateCompileTime(compilerId, method));
        }
        releaseOptimizationPlan(freeOptimizationPlan);
        return cm;
      } catch (OptimizingCompilerException e) {
        if (e.isFatal) {
          // An unexpected error when building the opt boot image should be fatal
          VM.sysWriteln("Error compiling method: "+method);
          e.printStackTrace();
          System.exit(VM.EXIT_STATUS_OPT_COMPILER_FAILED);
        } else {
          boolean printMsg = true;
          if (e instanceof MagicNotImplementedException) {
            printMsg = !((MagicNotImplementedException) e).isExpected;
          }
          if (e == escape) {
            printMsg = false;
          }
          if (printMsg) {
            if (e.toString().indexOf("method excluded") >= 0) {
              String msg = "VM_BootImageCompiler: " + method + " excluded from opt-compilation\n";
              VM.sysWrite(msg);
            } else {
              String msg = "VM_BootImageCompiler: can't optimize \"" + method + "\" (error was: " + e + ")\n";
              VM.sysWrite(msg);
            }
          }
        }
        return baselineCompile(method);
      }
    }
  }

  private VM_CompiledMethod baselineCompile(VM_NormalMethod method) {
    VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.BASELINE);
    VM_CompiledMethod cm = VM_BaselineCompiler.compile(method);
    /* We can't accurately measure compilation time on Host JVM, so just approximate with DNA */
    cm.setCompilationTime((float)VM_CompilerDNA.estimateCompileTime(VM_CompilerDNA.BASELINE, method));
    return cm;
  }

  /**
   * Return an optimization plan that isn't in use
   * @return optimization plan
   */
  private int getFreeOptimizationPlan() {
    // Find plan
    synchronized (optimizationPlanLocks) {
      for (int i = 0; i < optimizationPlanLocks.size(); i++) {
        if (!optimizationPlanLocks.get(i)) {
          optimizationPlanLocks.set(i, Boolean.TRUE);
          return i;
        }
      }
      // Find failed, so create new plan
      OptimizationPlanElement[] optimizationPlan;
      OptOptions cloneOptions = masterOptions.dup();
      optimizationPlan = OptimizationPlanner.createOptimizationPlan(cloneOptions);
      optimizationPlans.addElement(optimizationPlan);
      optimizationPlanLocks.addElement(Boolean.TRUE);
      options.addElement(cloneOptions);
      return optimizationPlanLocks.size() - 1;
    }
  }

  /**
   * Release an optimization plan
   * @param plan an optimization plan
   */
  private void releaseOptimizationPlan(int plan) {
    synchronized (optimizationPlanLocks) {
      optimizationPlanLocks.set(plan, Boolean.FALSE);
    }
  }
}
