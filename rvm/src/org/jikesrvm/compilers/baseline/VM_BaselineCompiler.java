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
package org.jikesrvm.compilers.baseline;

import org.jikesrvm.ArchitectureSpecific.VM_Assembler;
import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;
import org.jikesrvm.ArchitectureSpecific.VM_Compiler;
import org.jikesrvm.ArchitectureSpecific.VM_MachineCode;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.osr.OSR_BytecodeTraverser;
import org.jikesrvm.runtime.VM_Time;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.vmmagic.unboxed.Offset;

/**
 * Baseline compiler - platform independent code.
 * Platform dependent versions extend this class and define
 * the host of abstract methods defined by VM_CompilerFramework to complete
 * the implementation of a baseline compiler for a particular target,
 */
public abstract class VM_BaselineCompiler extends VM_CompilerFramework {

  private static long gcMapNanos;
  private static long osrSetupNanos;
  private static long codeGenNanos;
  private static long encodingNanos;

  /**
   * Options used during base compiler execution
   */
  public static VM_BaselineOptions options;

  /**
   * Next edge counter entry to allocate
   */
  protected int edgeCounterIdx;

  protected final Offset getEdgeCounterOffset() {
    return Offset.fromIntZeroExtend(method.getId() << LOG_BYTES_IN_ADDRESS);
  }

  /**
   * The types that locals can take.
   * There are two types of locals. First the parameters of the method, they only have one type
   * Second, the other locals, numbers get reused when stack shrinks and grows again.
   * Therefore, these can have more than one type assigned.
   * The compiler can use this information to assign registers to locals
   * See the VM_Compiler constructor.
   */
  protected final byte[] localTypes;

  /**
   * Construct a VM_Compiler
   */
  protected VM_BaselineCompiler(VM_BaselineCompiledMethod cm) {
    super(cm);
    shouldPrint =
        (!VM.runningTool &&
         (options.PRINT_MACHINECODE) &&
         (!options.hasMETHOD_TO_PRINT() || options.fuzzyMatchMETHOD_TO_PRINT(method.toString())));
    if (!VM.runningTool && options.PRINT_METHOD) printMethodMessage();
    if (shouldPrint && VM.runningVM && !fullyBootedVM) {
      shouldPrint = false;
      if (options.PRINT_METHOD) {
        VM.sysWriteln("\ttoo early in VM.boot() to print machine code");
      }
    }
    asm = new VM_Assembler(bcodes.length(), shouldPrint, (VM_Compiler) this);
    localTypes = new byte[method.getLocalWords()];
  }

  /**
   * Clear out crud from bootimage writing
   */
  public static void initOptions() {
    options = new VM_BaselineOptions();
  }

  /**
   * Now that VM is fully booted, enable options
   * such as PRINT_MACHINE_CODE that require a fully booted VM.
   */
  public static void fullyBootedVM() {
    // If the user has requested machine code dumps, then force a test
    // of method to print option so extra classes needed to process
    // matching will be loaded and compiled upfront. Thus avoiding getting
    // stuck looping by just asking if we have a match in the middle of
    // compilation. Pick an obsure string for the check.
    if (options.hasMETHOD_TO_PRINT() && options.fuzzyMatchMETHOD_TO_PRINT("???")) {
      VM.sysWrite("??? is not a sensible string to specify for method name");
    }
    if (!VM.BuildForAdaptiveSystem && options.PRELOAD_CLASS != null) {
      VM.sysWrite("Option preload_class should only be used when the optimizing compiler is the runtime");
      VM.sysWrite(" compiler or in an adaptive system\n");
      VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    fullyBootedVM = true;
  }

  /**
   * Process a command line argument
   * @param prefix
   * @param arg     Command line argument with prefix stripped off
   */
  public static void processCommandLineArg(String prefix, String arg) {
    if (!options.processAsOption(prefix, arg)) {
      VM.sysWrite("VM_BaselineCompiler: Unrecognized argument \"" + arg + "\"\n");
      VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
  }

  /**
   * Generate a report of time spent in various phases of the baseline compiler.
   * <p> NB: This method may be called in a context where classloading and/or
   * GC cannot be allowed.
   * Therefore we must use primitive sysWrites for output and avoid string
   * appends and other allocations.
   *
   * @param explain Should an explanation of the metrics be generated?
   */
  public static void generateBaselineCompilerSubsystemReport(boolean explain) {
    if (!VM.MeasureCompilationPhases) return;

    VM.sysWriteln("\n\t\tBaseline Compiler SubSystem");
    VM.sysWriteln("\tPhase\t\t\t    Time");
    VM.sysWriteln("\t\t\t\t(ms)    (%ofTotal)");

    double gcMapTime = VM_Time.nanosToMillis(gcMapNanos);
    double osrSetupTime = VM_Time.nanosToMillis(osrSetupNanos);
    double codeGenTime = VM_Time.nanosToMillis(codeGenNanos);
    double encodingTime = VM_Time.nanosToMillis(encodingNanos);
    double total = gcMapTime + osrSetupTime + codeGenTime + encodingTime;

    VM.sysWrite("\tCompute GC Maps\t\t", gcMapTime);
    VM.sysWriteln("\t", 100 * gcMapTime / total);

    if (osrSetupTime > 0) {
      VM.sysWrite("\tOSR setup \t\t", osrSetupTime);
      VM.sysWriteln("\t", 100 * osrSetupTime / total);
    }

    VM.sysWrite("\tCode generation\t\t", codeGenTime);
    VM.sysWriteln("\t", 100 * codeGenTime / total);

    VM.sysWrite("\tEncode GC/MC maps\t", encodingTime);
    VM.sysWriteln("\t", 100 * encodingTime / total);

    VM.sysWriteln("\tTOTAL\t\t\t", total);
  }

  /**
   * Compile the given method with the baseline compiler.
   *
   * @param method the VM_NormalMethod to compile.
   * @return the generated VM_CompiledMethod for said VM_NormalMethod.
   */
  public static VM_CompiledMethod compile(VM_NormalMethod method) {
    VM_BaselineCompiledMethod cm =
        (VM_BaselineCompiledMethod) VM_CompiledMethods.createCompiledMethod(method, VM_CompiledMethod.BASELINE);
    cm.compile();
    return cm;
  }

  protected abstract void initializeCompiler();

  /**
   * Top level driver for baseline compilation of a method.
   */
  protected void compile() {
    if (shouldPrint) printStartHeader(method);

    // Phase 1: GC map computation
    long start = 0;
    VM_ReferenceMaps refMaps;
    try {
      if (VM.MeasureCompilationPhases) {
        start = VM_Scheduler.getCurrentThread().startTimedInterval();
      }
      refMaps = new VM_ReferenceMaps((VM_BaselineCompiledMethod) compiledMethod, stackHeights, localTypes);
    } finally {
      if (VM.MeasureCompilationPhases) {
        long end = VM_Scheduler.getCurrentThread().endTimedInterval();
        gcMapNanos += end - start;
      }
    }

    /* reference map and stackheights were computed using original bytecodes
     * and possibly new operand words
     * recompute the stack height, but keep the operand words of the code
     * generation consistant with reference map
     * TODO: revist this code as part of OSR redesign
     */
    // Phase 2: OSR setup\
    boolean edge_counters = options.EDGE_COUNTERS;
    try {
      if (VM.MeasureCompilationPhases) {
        start = VM_Scheduler.getCurrentThread().startTimedInterval();
      }
      if (VM.BuildForAdaptiveSystem && method.isForOsrSpecialization()) {
        options.EDGE_COUNTERS = false;
        // we already allocated enough space for stackHeights, shift it back first
        System.arraycopy(stackHeights,
                         0,
                         stackHeights,
                         method.getOsrPrologueLength(),
                         method.getBytecodeLength());   // NB: getBytecodeLength returns back the length of original bytecodes

        // compute stack height for prologue
        new OSR_BytecodeTraverser().prologueStackHeights(method, method.getOsrPrologue(), stackHeights);
      }
    } finally {
      if (VM.MeasureCompilationPhases) {
        long end = VM_Scheduler.getCurrentThread().endTimedInterval();
        osrSetupNanos += end - start;
      }
    }

    // Phase 3: Code gen
    int[] bcMap;
    VM_MachineCode machineCode;
    VM_CodeArray instructions;
    try {
      if (VM.MeasureCompilationPhases) {
        start = VM_Scheduler.getCurrentThread().startTimedInterval();
      }

      // determine if we are going to insert edge counters for this method
      if (options
          .EDGE_COUNTERS &&
          !method.getDeclaringClass().hasBridgeFromNativeAnnotation() &&
          (method.hasCondBranch() || method.hasSwitch())) {
        ((VM_BaselineCompiledMethod) compiledMethod).setHasCounterArray(); // yes, we will inject counters for this method.
      }

      //do platform specific tasks before generating code;
      initializeCompiler();

      machineCode = genCode();
      instructions = machineCode.getInstructions();
      bcMap = machineCode.getBytecodeMap();
    } finally {
      if (VM.MeasureCompilationPhases) {
        long end = VM_Scheduler.getCurrentThread().endTimedInterval();
        codeGenNanos += end - start;
      }
    }

    /* adjust machine code map, and restore original bytecode
     * for building reference map later.
     * TODO: revisit this code as part of OSR redesign
     */
    // Phase 4: OSR part 2
    try {
      if (VM.MeasureCompilationPhases) {
        start = VM_Scheduler.getCurrentThread().startTimedInterval();
      }
      if (VM.BuildForAdaptiveSystem && method.isForOsrSpecialization()) {
        int[] newmap = new int[bcMap.length - method.getOsrPrologueLength()];
        System.arraycopy(bcMap, method.getOsrPrologueLength(), newmap, 0, newmap.length);
        machineCode.setBytecodeMap(newmap);
        bcMap = newmap;
        // switch back to original state
        method.finalizeOsrSpecialization();
        // restore options
        options.EDGE_COUNTERS = edge_counters;
      }
    } finally {
      if (VM.MeasureCompilationPhases) {
        long end = VM_Scheduler.getCurrentThread().endTimedInterval();
        osrSetupNanos += end - start;
      }
    }

    // Phase 5: Encode machine code maps
    try {
      if (VM.MeasureCompilationPhases) {
        start = VM_Scheduler.getCurrentThread().startTimedInterval();
      }
      if (method.isSynchronized()) {
        ((VM_BaselineCompiledMethod) compiledMethod).setLockAcquisitionOffset(lockOffset);
      }
      ((VM_BaselineCompiledMethod) compiledMethod).encodeMappingInfo(refMaps, bcMap, instructions.length());
      compiledMethod.compileComplete(instructions);
      if (edgeCounterIdx > 0) {
        VM_EdgeCounts.allocateCounters(method, edgeCounterIdx);
      }
      if (shouldPrint) {
        ((VM_BaselineCompiledMethod) compiledMethod).printExceptionTable();
        printEndHeader(method);
      }
    } finally {
      if (VM.MeasureCompilationPhases) {
        long end = VM_Scheduler.getCurrentThread().endTimedInterval();
        encodingNanos += end - start;
      }
    }
  }

  protected String getCompilerName() {
    return "baseline";
  }
}
