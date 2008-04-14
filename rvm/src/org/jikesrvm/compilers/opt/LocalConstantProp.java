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

import java.util.HashMap;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.controlflow.BranchOptimizations;
import org.jikesrvm.compilers.opt.controlflow.BranchSimplifier;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.ConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 * Perform local constant propagation for a factored basic block.
 * Orthogonal to the constant propagation performed in Simple
 * since here we use flow-sensitive analysis within a basic block.
 */
public class LocalConstantProp extends CompilerPhase {

  public final boolean shouldPerform(OptOptions options) {
    return options.LOCAL_CONSTANT_PROP;
  }

  public final String getName() {
    return "Local ConstantProp";
  }

  public void reportAdditionalStats() {
    VM.sysWrite("  ");
    VM.sysWrite(container.counter1 / container.counter2 * 100, 2);
    VM.sysWrite("% Infrequent BBs");
  }

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  /**
   * Perform Local Constant propagation for a method.
   *
   * @param ir the IR to optimize
   */
  public void perform(IR ir) {
    // info is a mapping from Register to ConstantOperand.
    HashMap<Register, ConstantOperand> info = new HashMap<Register, ConstantOperand>();
    boolean runBranchOpts = false;
    for (BasicBlock bb = ir.firstBasicBlockInCodeOrder(); bb != null; bb = bb.nextBasicBlockInCodeOrder()) {
      if (bb.isEmpty()) continue;
      container.counter2++;
      if (bb.getInfrequent()) {
        container.counter1++;
        if (ir.options.FREQ_FOCUS_EFFORT) continue;
      }
      // iterate over all instructions in the basic block
      for (Instruction s = bb.firstRealInstruction(), next,
          sentinel = bb.lastInstruction(); s != sentinel; s = next) {
        next = s.nextInstructionInCodeOrder();
        if (!info.isEmpty()) {
          // PROPAGATE CONSTANTS
          int numUses = s.getNumberOfUses();
          if (numUses > 0) {
            boolean didSomething = false;
            int numDefs = s.getNumberOfDefs();
            for (int idx = numDefs; idx < numUses + numDefs; idx++) {
              Operand use = s.getOperand(idx);
              if (use instanceof RegisterOperand) {
                RegisterOperand rUse = (RegisterOperand) use;
                Operand value = info.get(rUse.getRegister());
                if (value != null) {
                  didSomething = true;
                  s.putOperand(idx, value.copy());
                }
              }
            }
            if (didSomething) Simplifier.simplify(ir.IRStage == IR.HIR, ir.regpool, s);
          }
          // KILL
          for (OperandEnumeration e = s.getDefs(); e.hasMoreElements();) {
            Operand def = e.next();
            if (def != null) {
              // we have a definition, check to see if it kills a local move of a constant
              Register defReg = ((RegisterOperand) def).getRegister();
              ConstantOperand cOp = info.get(defReg);
              if (cOp != null) {
                // move was overwritten
                if (Move.conforms(s) && Move.getVal(s).isConstant()) {
                  ConstantOperand cOp2 = (ConstantOperand) Move.getVal(s);
                  if (cOp2.similar(cOp)) {
                    // Redundant move of same value, remove instruction and do nothing
                    s.remove();
                  } else {
                    // Overwriting move
                    info.put(defReg, cOp2);
                  }
                } else {
                  // Overwritten by non-move
                  info.remove(defReg);
                }
              }
            }
          }
        } else if (Move.conforms(s) && Move.getVal(s).isConstant()) {
          // GEN
          info.put(Move.getResult(s).getRegister(), (ConstantOperand) Move.getVal(s));
        }
      }
      info.clear();
      runBranchOpts |= BranchSimplifier.simplify(bb, ir);
    }
    if (runBranchOpts) {
      new BranchOptimizations(0, true, false, false).perform(ir);
    }
  }
}
