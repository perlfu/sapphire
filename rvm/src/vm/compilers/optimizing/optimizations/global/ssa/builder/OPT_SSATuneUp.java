/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.*;
import instructionFormats.*;

/**
 * This phase puts the IR in SSA form and performs a set of simple
 * optimizations to clean up.  
 *
 * @author Stephen Fink
 */
final class OPT_SSATuneUp extends OPT_OptimizationPlanCompositeElement {

  /**
   * Build this phase as a composite of others.
   */
  OPT_SSATuneUp () {
    super("SSA Tune Up", new OPT_OptimizationPlanElement[] {
      // 1. Set up IR state to control SSA translation as needed
      new OPT_OptimizationPlanAtomicElement(new TuneUpPreparation()), 
      // 2. Get the desired SSA form
      new OPT_OptimizationPlanAtomicElement(new OPT_EnterSSA()), 
      // 3. Perform simple optimizations
      new OPT_OptimizationPlanAtomicElement(new OPT_Simple(true,true,false))
    });
  }

  boolean shouldPerform (OPT_Options options) {
    return  options.SSA;
  }

  /**
   * This class sets up the IR state prior to entering SSA.
   */
  private static class TuneUpPreparation extends OPT_CompilerPhase {

    final boolean shouldPerform (OPT_Options options) {
      return  options.SSA;
    }

    final String getName () {
      return  "SSA Tune UpPreparation";
    }

    final boolean printingEnabled (OPT_Options options, boolean before) {
      return false;
    }

    /**
     * register in the IR the SSA properties we need for simple scalar
     * optimizations
     */
    final public void perform (OPT_IR ir) {
      ir.desiredSSAOptions = new OPT_SSAOptions();
      ir.desiredSSAOptions.setScalarsOnly(true);
      ir.desiredSSAOptions.setBackwards(false);
      ir.desiredSSAOptions.setInsertUsePhis(false);
    }
  }
}



