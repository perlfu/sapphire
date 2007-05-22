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
package org.jikesrvm.compilers.opt;

import java.util.ArrayList;
import org.jikesrvm.ArchitectureSpecific.OPT_MIROptimizationPlanner;
import org.jikesrvm.VM;
import org.jikesrvm.adaptive.recompilation.instrumentation.OPT_InsertInstructionCounters;
import org.jikesrvm.adaptive.recompilation.instrumentation.OPT_InsertMethodInvocationCounter;
import org.jikesrvm.adaptive.recompilation.instrumentation.OPT_InsertYieldpointCounters;
import org.jikesrvm.adaptive.recompilation.instrumentation.OPT_InstrumentationSamplingFramework;
import org.jikesrvm.adaptive.recompilation.instrumentation.OPT_LowerInstrumentation;
import org.jikesrvm.compilers.opt.ir.OPT_ConvertBCtoHIR;
import org.jikesrvm.osr.OSR_AdjustBCIndexes;

/**
 * This class specifies the order in which OPT_CompilerPhases are
 * executed during the HIR and LIR phase of the opt compilation of a method.
 *
 * @see OPT_MIROptimizationPlanner
 */
public class OPT_OptimizationPlanner {

  /**
   * The master optimization plan.
   * All plans that are actually executed should be subsets of this plan
   * constructed by calling createOptimizationPlan.
   */
  private static OPT_OptimizationPlanElement[] masterPlan;

  /**
   * Generate a report of time spent in various phases of the opt compiler.
   * <p> NB: This method may be called in a context where classloading and/or
   * GC cannot be allowed.
   * Therefore we must use primitive sysWrites for output and avoid string
   * appends and other allocations.
   *
   * @param explain Should an explanation of the metrics be generated?
   */
  public static void generateOptimizingCompilerSubsystemReport(boolean explain) {
    if (!VM.MeasureCompilation) {
      return;
    }

    VM.sysWrite("\t\tOptimizing Compiler SubSystem\n");
    VM.sysWrite("\tPhase\t\t\t\t\tTime\n");
    VM.sysWrite("\t\t\t\t\t   (ms)    (%ofTotal)\n");
    double total = 0.0;

    for (OPT_OptimizationPlanElement element : masterPlan) {
      total += element.elapsedTime();
    }

    for (OPT_OptimizationPlanElement element : masterPlan) {
      element.reportStats(8, 40, total);
    }

    VM.sysWrite("\n\tTOTAL COMPILATION TIME\t\t");
    int t = (int) total, places = t;
    while (places < 1000000) { // Right-align 't'
      VM.sysWrite(" ");
      places *= 10;
    }
    VM.sysWrite(t);
    VM.sysWrite("\n");
  }

  /**
   * Using the passed options create an optimization plan
   * by selecting a subset of the elements in the masterPlan.
   *
   * @param options the OPT_Options to use
   * @return an OPT_OptimizationPlanElement[] selected from
   * the masterPlan based on options.
   */
  public static OPT_OptimizationPlanElement[] createOptimizationPlan(OPT_Options options) {
    if (masterPlan == null) {
      initializeMasterPlan();
    }

    ArrayList<OPT_OptimizationPlanElement> temp = new ArrayList<OPT_OptimizationPlanElement>();
    for (OPT_OptimizationPlanElement element : masterPlan) {
      if (element.shouldPerform(options)) {
        temp.add(element);
      }
    }
    if (VM.writingBootImage) {
      masterPlan = null;  // avoid problems with classes not being in bootimage.
    }
    return toArray(temp);
  }

  /**
   * This method is called to initialize all phases to support
   *  measuring compilation.
   */
  public static void initializeMeasureCompilation() {
    for (OPT_OptimizationPlanElement element : masterPlan) {
      element.initializeForMeasureCompilation();
    }
  }

  /**
   * Initialize the "master plan", which holds all optimization elements
   * that will normally execute.
   */
  private static void initializeMasterPlan() {
    ArrayList<OPT_OptimizationPlanElement> temp = new ArrayList<OPT_OptimizationPlanElement>();
    BC2HIR(temp);
    HIROptimizations(temp);
    HIR2LIR(temp);
    LIROptimizations(temp);
    OPT_MIROptimizationPlanner.intializeMasterPlan(temp);
    masterPlan = toArray(temp);
  }

  /**
   * Convert the ArrayList to an array of elements.
   * TODO: this is a bad name (finalize), isn't it?
   */
  private static OPT_OptimizationPlanElement[] toArray(ArrayList<OPT_OptimizationPlanElement> planElementList) {
    OPT_OptimizationPlanElement[] p = new OPT_OptimizationPlanElement[planElementList.size()];
    planElementList.toArray(p);
    return p;
  }

  /**
   * This method defines the optimization plan elements required to
   * generate HIR from bytecodes.
   *
   * @param p the plan under construction
   */
  private static void BC2HIR(ArrayList<OPT_OptimizationPlanElement> p) {
    composeComponents(p, "Convert Bytecodes to HIR", new Object[]{
        // Generate HIR from bytecodes
        new OPT_ConvertBCtoHIR(),

        new OSR_AdjustBCIndexes(), new OSR_OsrPointConstructor(),

        // Always do initial wave of peephole branch optimizations
        new OPT_BranchOptimizations(0, true, false),

        // Adjust static branch probabilites to account for infrequent blocks
        new OPT_AdjustBranchProbabilities(),

        // Optional printing of initial HIR
        // Do this after branch optmization, since without merging
        // FallThroughOuts, the IR is quite ugly.
        new OPT_IRPrinter("Initial HIR") {
          public boolean shouldPerform(OPT_Options options) {
            return options.PRINT_HIGH;
          }
        }});
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed on the HIR.
   *
   * @param p the plan under construction
   */
  private static void HIROptimizations(ArrayList<OPT_OptimizationPlanElement> p) {
    // Various large-scale CFG transformations.
    // Do these very early in the pipe so that all HIR opts can benefit.
    composeComponents(p, "CFG Transformations", new Object[]{
        // tail recursion elimination
        new OPT_TailRecursionElimination(),
        // Estimate block frequencies if doing any of
        // static splitting, cfg transformations, or loop unrolling.
        // Assumption: none of these are active at O0.
        new OPT_OptimizationPlanCompositeElement("Basic Block Frequency Estimation",
                                                 new Object[]{new OPT_BuildLST(), new OPT_EstimateBlockFrequencies()}) {
          public boolean shouldPerform(OPT_Options options) {
            return options.getOptLevel() >= 1;
          }
        },
        // CFG spliting
        new OPT_StaticSplitting(),
        // restructure loops
        new OPT_CFGTransformations(),
        // Loop unrolling
        new OPT_LoopUnrolling(), new OPT_BranchOptimizations(1, true, true),});

    // Use the LST to insert yieldpoints and estimate
    // basic block frequency from branch probabilities
    composeComponents(p,
                      "CFG Structural Analysis",
                      new Object[]{new OPT_BuildLST(), new OPT_YieldPoints(), new OPT_EstimateBlockFrequencies()});

    // Simple flow-insensitive optimizations
    addComponent(p, new OPT_Simple(1, true, true));

    // Simple escape analysis and related transformations
    addComponent(p, new OPT_EscapeTransformations());

    // Perform peephole branch optimizations to clean-up before SSA stuff
    addComponent(p, new OPT_BranchOptimizations(1, true, true));

    // SSA meta-phase
    SSAinHIR(p);

    // Perform local copy propagation for a factored basic block.
    addComponent(p, new OPT_LocalCopyProp());
    // Perform local constant propagation for a factored basic block.
    addComponent(p, new OPT_LocalConstantProp());
    // Perform local common-subexpression elimination for a
    // factored basic block.
    addComponent(p, new OPT_LocalCSE(true));
    // Flow-insensitive field analysis
    addComponent(p, new OPT_FieldAnalysis());
    if (VM.BuildForAdaptiveSystem) {
      // Insert counter on each method prologue
      // Insert yieldpoint counters
      addComponent(p, new OPT_InsertYieldpointCounters());
      // Insert counter on each HIR instruction
      addComponent(p, new OPT_InsertInstructionCounters());
      // Insert method invocation counters
      addComponent(p, new OPT_InsertMethodInvocationCounter());
    }
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed with SSA form on HIR.
   *
   * @param p the plan under construction
   */
  private static void SSAinHIR(ArrayList<OPT_OptimizationPlanElement> p) {
    composeComponents(p, "SSA", new Object[]{
        // Use the LST to estimate basic block frequency from branch probabilities
        new OPT_OptimizationPlanCompositeElement("Basic Block Frequency Estimation",
                                                 new Object[]{new OPT_BuildLST(), new OPT_EstimateBlockFrequencies()}) {
          public boolean shouldPerform(OPT_Options options) {
            return options.getOptLevel() >= 3;
          }
        },

        new OPT_OptimizationPlanCompositeElement("HIR SSA transformations", new Object[]{
            // Local copy propagation
            new OPT_LocalCopyProp(),
            // Local constant propagation
            new OPT_LocalConstantProp(),
            // Insert PI Nodes
            new OPT_PiNodes(true),
            // branch optimization
            new OPT_BranchOptimizations(3, true, true),
            // Compute dominators
            new OPT_DominatorsPhase(true),
            // compute dominance frontier
            new OPT_DominanceFrontier(),
            // load elimination
            new OPT_LoadElimination(1),
            // load elimination
            new OPT_LoadElimination(2),
            // load elimination
            new OPT_LoadElimination(3),
            // load elimination
            new OPT_LoadElimination(4),
            // load elimination
            new OPT_LoadElimination(5),
            // eliminate redundant conditional branches
            new OPT_RedundantBranchElimination(),
            // path sensitive constant propagation
            new OPT_SSATuneUp(),
            // clean up Pi Nodes
            new OPT_PiNodes(false),
            // Simple SSA optimizations,
            new OPT_SSATuneUp(),
            // Global Code Placement,
            new OPT_GCP(),
            // Loop versioning
            new OPT_LoopVersioning(),
            // Leave SSA
            new OPT_LeaveSSA()}) {
          public boolean shouldPerform(OPT_Options options) {
            return options.getOptLevel() >= 3;
          }
        },
        // Coalesce moves
        new OPT_CoalesceMoves(),

        // SSA reveals new opportunites for the following
        new OPT_OptimizationPlanCompositeElement("Post SSA cleanup",
                                                 new Object[]{new OPT_LocalCopyProp(),
                                                              new OPT_LocalConstantProp(),
                                                              new OPT_Simple(3, true, true),
                                                              new OPT_EscapeTransformations(),
                                                              new OPT_BranchOptimizations(3, true, true)}) {
          public boolean shouldPerform(OPT_Options options) {
            return options.getOptLevel() >= 3;
          }
        }});
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed with SSA form on LIR.
   *
   * @param p the plan under construction
   */
  private static void SSAinLIR(ArrayList<OPT_OptimizationPlanElement> p) {
    composeComponents(p, "SSA", new Object[]{
        // Use the LST to estimate basic block frequency from branch probabilities
        new OPT_OptimizationPlanCompositeElement("Basic Block Frequency Estimation",
                                                 new Object[]{new OPT_BuildLST(), new OPT_EstimateBlockFrequencies()}) {
          public boolean shouldPerform(OPT_Options options) {
            return options.getOptLevel() >= 3;
          }
        },

        new OPT_OptimizationPlanCompositeElement("LIR SSA transformations", new Object[]{
            // restructure loops
            new OPT_CFGTransformations(),
            // Compute dominators
            new OPT_DominatorsPhase(true),
            // compute dominance frontier
            new OPT_DominanceFrontier(),
            // Global Code Placement,
            new OPT_GCP(),
            // Leave SSA
            new OPT_LeaveSSA()}) {
          public boolean shouldPerform(OPT_Options options) {
            return options.getOptLevel() >= 3;
          }
        },
        // Live range splitting
        new OPT_LiveRangeSplitting(),

        // Coalesce moves
        new OPT_CoalesceMoves(),

        // SSA reveals new opportunites for the following
        new OPT_OptimizationPlanCompositeElement("Post SSA cleanup",
                                                 new Object[]{new OPT_LocalCopyProp(),
                                                              new OPT_LocalConstantProp(),
                                                              new OPT_Simple(3, true, true),
                                                              new OPT_BranchOptimizations(3, true, true)}) {
          public boolean shouldPerform(OPT_Options options) {
            return options.getOptLevel() >= 3;
          }
        }});
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed to lower HIR to LIR.
   *
   * @param p the plan under construction
   */
  private static void HIR2LIR(ArrayList<OPT_OptimizationPlanElement> p) {
    composeComponents(p, "Convert HIR to LIR", new Object[]{
        // Optional printing of final HIR
        new OPT_IRPrinter("Final HIR") {
          public boolean shouldPerform(OPT_Options options) {
            return options.PRINT_FINAL_HIR;
          }
        },

        // Inlining "runtime service" methods
        new OPT_ExpandRuntimeServices(),
        // Peephole branch optimizations
        new OPT_BranchOptimizations(1, true, true),
        // Local optimizations of checkcasts
        new OPT_LocalCastOptimization(),
        // Massive operator expansion
        new OPT_ConvertHIRtoLIR(),
        // Peephole branch optimizations
        new OPT_BranchOptimizations(0, true, true),
        // Adjust static branch probabilites to account for infrequent blocks
        // introduced by the inlining of runtime services.
        new OPT_AdjustBranchProbabilities(),
        // Optional printing of initial LIR
        new OPT_IRPrinter("Initial LIR") {
          public boolean shouldPerform(OPT_Options options) {
            return options.PRINT_LOW;
          }
        }});
  }

  /**
   * This method defines the optimization plan elements that
   * are to be performed on the LIR.
   *
   * @param p the plan under construction
   */
  private static void LIROptimizations(ArrayList<OPT_OptimizationPlanElement> p) {
    // SSA meta-phase
    SSAinLIR(p);
    // Perform local copy propagation for a factored basic block.
    addComponent(p, new OPT_LocalCopyProp());
    // Perform local constant propagation for a factored basic block.
    addComponent(p, new OPT_LocalConstantProp());
    // Perform local common-subexpression elimination for a factored basic block.
    addComponent(p, new OPT_LocalCSE(false));
    // Simple flow-insensitive optimizations
    addComponent(p, new OPT_Simple(0, false, false));

    // Use the LST to estimate basic block frequency
    addComponent(p,
                 new OPT_OptimizationPlanCompositeElement("Basic Block Frequency Estimation",
                                                          new Object[]{new OPT_BuildLST(),
                                                                       new OPT_EstimateBlockFrequencies()}));

    // Perform basic block reordering
    addComponent(p, new OPT_ReorderingPhase());
    // Perform peephole branch optimizations
    addComponent(p, new OPT_BranchOptimizations(1, false, true));

    if (VM.BuildForAdaptiveSystem) {
      // Arnold & Ryder instrumentation sampling framework
      addComponent(p, new OPT_InstrumentationSamplingFramework());

      // Convert high level place holder instructions into actual instrumenation
      addComponent(p, new OPT_LowerInstrumentation());
    }
  }

  // Helper functions for constructing the masterPlan.
  protected static void addComponent(ArrayList<OPT_OptimizationPlanElement> p, OPT_CompilerPhase e) {
    addComponent(p, new OPT_OptimizationPlanAtomicElement(e));
  }

  /**
   * Add an optimization plan element to a vector.
   */
  protected static void addComponent(ArrayList<OPT_OptimizationPlanElement> p, OPT_OptimizationPlanElement e) {
    p.add(e);
  }

  /**
   * Add a set of optimization plan elements to a vector.
   * @param p    the vector to add to
   * @param name the name for this composition
   * @param es   the array of composed elements
   */
  protected static void composeComponents(ArrayList<OPT_OptimizationPlanElement> p, String name, Object[] es) {
    p.add(OPT_OptimizationPlanCompositeElement.compose(name, es));
  }
}
