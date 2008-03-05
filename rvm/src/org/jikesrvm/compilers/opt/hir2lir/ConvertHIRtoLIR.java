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
package org.jikesrvm.compilers.opt.hir2lir;

import org.jikesrvm.compilers.opt.depgraph.DepGraphStats;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.LIRInfo;

/**
 * Convert an IR object from HIR to LIR
 */
public final class ConvertHIRtoLIR extends CompilerPhase {

  public String getName() {
    return "HIR Operator Expansion";
  }

  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  public void perform(IR ir) {
    if (IR.SANITY_CHECK) {
      ir.verify("before conversion to LIR", true);
    }
    if (ir.options.STATIC_STATS) {
      // Print summary statistics (critpath, etc.) for all basic blocks
      DepGraphStats.printBasicBlockStatistics(ir);
    }
    // Do the conversion from HIR to LIR.
    ir.IRStage = IR.LIR;
    ir.LIRInfo = new LIRInfo(ir);
    ConvertToLowLevelIR.convert(ir, ir.options);
  }
}
