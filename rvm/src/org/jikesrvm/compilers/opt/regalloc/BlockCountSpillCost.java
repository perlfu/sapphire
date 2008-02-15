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
package org.jikesrvm.compilers.opt.regalloc;

import java.util.Enumeration;

import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.MemoryOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;

/**
 * An object that returns an estimate of the relative cost of spilling a
 * symbolic register, based on basic block frequencies.
 */
class BlockCountSpillCost extends SpillCostEstimator {

  BlockCountSpillCost(IR ir) {
    calculate(ir);
  }

  /**
   * Calculate the estimated cost for each register.
   */
  void calculate(IR ir) {
    for (Enumeration<BasicBlock> blocks = ir.getBasicBlocks(); blocks.hasMoreElements();) {
      BasicBlock bb = blocks.nextElement();
      float freq = bb.getExecutionFrequency();
      for (InstructionEnumeration e = bb.forwardInstrEnumerator(); e.hasMoreElements();) {
        Instruction s = e.nextElement();
        double factor = freq;

        if (s.isMove()) factor *= SimpleSpillCost.MOVE_FACTOR;
        double baseFactor = factor;
        if (SimpleSpillCost.hasBadSizeMemoryOperand(s)) {
          baseFactor *= SimpleSpillCost.MEMORY_OPERAND_FACTOR;
        }

        // first deal with non-memory operands
        for (OperandEnumeration e2 = s.getRootOperands(); e2.hasMoreElements();) {
          Operand op = e2.nextElement();
          if (op.isRegister()) {
            Register r = op.asRegister().getRegister();
            if (r.isSymbolic()) {
              update(r, baseFactor);
            }
          }
        }
        // now handle memory operands
        factor *= SimpleSpillCost.MEMORY_OPERAND_FACTOR;
        for (OperandEnumeration e2 = s.getMemoryOperands(); e2.hasMoreElements();) {
          MemoryOperand M = (MemoryOperand) e2.nextElement();
          if (M.base != null) {
            Register r = M.base.getRegister();
            if (r.isSymbolic()) {
              update(r, factor);
            }
          }
          if (M.index != null) {
            Register r = M.index.getRegister();
            if (r.isSymbolic()) {
              update(r, factor);
            }
          }
        }
      }
    }
  }
}
