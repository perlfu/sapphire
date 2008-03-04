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
package org.jikesrvm.compilers.opt.bc2ir;

import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.HIRInfo;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * Translate from bytecodes to HIR
 */
public final class ConvertBCtoHIR extends CompilerPhase {

  public String getName() {
    return "Generate HIR";
  }

  /**
   * Generate HIR for ir.method into ir
   *
   * @param ir The IR to generate HIR into
   */
  public void perform(IR ir) {
    // Generate the cfg into gc
    GenerationContext gc = new GenerationContext(ir.method, ir.params, ir.compiledMethod, ir.options, ir.inlinePlan);
    BC2IR.generateHIR(gc);
    // Transfer HIR and misc state from gc to the ir object
    ir.gc = gc;
    ir.cfg = gc.cfg;
    ir.regpool = gc.temps;
    if (gc.allocFrame) {
      ir.stackManager.forceFrameAllocation();
    }
    // ir now contains well formed HIR.
    ir.IRStage = IR.HIR;
    ir.HIRInfo = new HIRInfo(ir);
    if (IR.SANITY_CHECK) {
      ir.verify("Initial HIR", true);
    }
  }

  // This phase contains no instance fields.
  public CompilerPhase newExecution(IR ir) {
    return this;
  }
}
