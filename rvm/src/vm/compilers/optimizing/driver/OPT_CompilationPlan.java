/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/*
 * OPT_CompilationPlan.java
 *
 * @author Stephen Fink
 * @author Dave Grove
 * @author Michael Hind
 *
 * An instance of this class acts instructs the optimizing
 * compiler how to compile the specified method.
 *
 */
final class OPT_CompilationPlan {
  /**
   * The method to be compiled.
   */
  public VM_Method method;

  VM_Method getMethod () {
    return method;
  }
  /**
   * The OPT_OptimizationPlanElements to be invoked during compilation.
   */
  public OPT_OptimizationPlanElement[] optimizationPlan;
  /**
   * The instrumentation plan for the method.
   */
  public OPT_InstrumentationPlan instrumentationPlan;
  /**
   * Used to make edge counts available to all opt phases.
   */
  public OPT_EdgeCounts edgeCounts;
  /**
   * The oracle to be consulted for all inlining decisions.
   */
  public OPT_InlineOracle inlinePlan;
  /**
   * The OPT_Options object that contains misc compilation control data
   */
  public OPT_Options options;

  //-#if RVM_WITH_SPECIALIZATION
  /**
   * pointer to context when compilation caused by specialization
   */
  public OPT_SpecializationHandler special;
  //-#endif

  /** 
   * Whether this compilation is for analysis only?
   */
  public boolean analyzeOnly;

  public boolean irGeneration;

  /**
   * Construct a compilation plan
   *
   * @param m    The VM_Method representing the source method to be compiled
   * @param op   The optimization plan to be executed on m
   * @param mp   The instrumentation plan to be executed on m
   * @param opts The OPT_Options to be used for compiling m
   */
  public OPT_CompilationPlan (VM_Method m, OPT_OptimizationPlanElement[] op, 
      OPT_InstrumentationPlan mp, OPT_Options opts) {
    method = m;
    optimizationPlan = op;
    instrumentationPlan = mp;
    inlinePlan = OPT_InlineOracleDictionary.getOracle(m);
    options = opts;
  }

  //-#if RVM_WITH_SPECIALIZATION
  /**
   * Construct a compilation plan
   * @param m    The VM_Method representing the source method to be compiled
   * @param op   The optimization plan to be executed on m
   * @param mp   The instrumentation plan to be executed on m
   * @param opts The OPT_Options to be used for compiling m
   * @param c    The specialization context in which to compile m
   */
  public OPT_CompilationPlan (VM_Method m, OPT_OptimizationPlanElement[] op, 
      OPT_InstrumentationPlan mp, OPT_Options opts, 
      OPT_SpecializationHandler special) {
    method = m;
    optimizationPlan = op;
    instrumentationPlan = mp;
    inlinePlan = OPT_InlineOracleDictionary.getOracle(m);
    options = opts;
    this.special = special;
  }
  //-#endif
    
  /**
   * Construct a compilation plan
   * @param m    The VM_Method representing the source method to be compiled
   * @param op   A single optimization pass to execute on m
   * @param mp   The instrumentation plan to be executed on m
   * @param opts The OPT_Options to be used for compiling m
   */
  public OPT_CompilationPlan (VM_Method m, OPT_OptimizationPlanElement op, 
      OPT_InstrumentationPlan mp, OPT_Options opts) {
    method = m;
    optimizationPlan = new OPT_OptimizationPlanElement[] {
      op
    };
    instrumentationPlan = mp;
    inlinePlan = OPT_InlineOracleDictionary.getOracle(m);
    options = opts;
  }

  //-#if RVM_WITH_SPECIALIZATION
  /**
   * Construct a compilation plan
   * @param m    The VM_Method representing the source method to be compiled
   * @param op   A single optimization pass to execute on m
   * @param mp   The instrumentation plan to be executed on m
   * @param opts The OPT_Options to be used for compiling m
   * @param c    The specialization context in which to compile m
   */
  public OPT_CompilationPlan (VM_Method m, OPT_OptimizationPlanElement op, 
			      OPT_InstrumentationPlan mp, OPT_Options opts,
			      OPT_SpecializationHandler special)
  {
    method = m;
    optimizationPlan = new OPT_OptimizationPlanElement[] { op };
    instrumentationPlan = mp;
    inlinePlan = OPT_InlineOracleDictionary.getOracle(m);
    options = opts;
    this.special = special;
  }
  //-#endif

  /**
   * Set the inline oracle
   */
  public void setInlineOracle (OPT_InlineOracle o) {
    inlinePlan = o;
  }

  /**
   * Execute a compilation plan by executing each element
   * in the optimization plan.
   */
  public OPT_IR execute () {
    OPT_IR ir = new OPT_IR(method, this);

    ir.compiledMethod = (VM_OptCompiledMethod)VM_CompiledMethods.createCompiledMethod(method, VM_CompiledMethod.OPT);

    // If there is instrumentation to perform, do some initialization
    if (instrumentationPlan != null) {
      instrumentationPlan.initInstrumentation(method);
    }
    for (int i = 0; i < optimizationPlan.length; i++) {
      optimizationPlan[i].perform(ir);
    }
    // If instrumentation has occured, perform some
    // cleanup/finalization.  NOTE: This code won't execute when
    // compilation fails with an exception.  TODO: Figure out
    // whether this matters.
    if (instrumentationPlan != null) {
      instrumentationPlan.finalizeInstrumentation(method);
    }

    // Give the edge counts a chance to clear their data structures to
    // prevent memory leaks.  This would be unnecessary if we had weak
    // references.
    if (ir.edgeCounts != null)
      ir.edgeCounts.compilationFinished();

    return ir;
  }
}
