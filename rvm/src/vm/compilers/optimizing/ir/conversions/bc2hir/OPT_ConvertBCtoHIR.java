/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Translate from bytecodes to HIR
 *
 * @author Dave Grove
 */
final class OPT_ConvertBCtoHIR extends OPT_CompilerPhase {

  final String getName () { 
    return  "Generate HIR";
  }

  /**
   * Generate HIR for ir.method into ir
   * 
   * @param ir The IR to generate HIR into
   */
  final void perform (OPT_IR ir) {
    // Generate the cfg into gc
    OPT_GenerationContext gc = 
      new OPT_GenerationContext(ir.method, ir.compiledMethodId, 
				ir.options, ir.inlinePlan);
    OPT_BC2IR.generateHIR(gc);
    // Transfer HIR and misc state from gc to the ir object
    ir.gc = gc;
    ir.cfg = gc.cfg;
    ir.regpool = gc.temps;
    if (gc.allocFrame) {
      ir.stackManager.forceFrameAllocation();
    }
    // ir now contains well formed HIR.
    ir.IRStage = OPT_IR.HIR;
    ir.HIRInfo = new OPT_HIRInfo(ir);
    if (OPT_IR.SANITY_CHECK) {
      ir.verify("Initial HIR", true);
    }
  }

  // This phase contains no instance fields.
  OPT_CompilerPhase newExecution (OPT_IR ir) {
    return  this;
  }
}
