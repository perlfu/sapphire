/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.controlflow;

import static org.jikesrvm.compilers.opt.driver.OptConstants.INSTRUMENTATION_BCI;
import static org.jikesrvm.compilers.opt.ir.Operators.IR_PROLOGUE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_BACKEDGE;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_EPILOGUE;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_PROLOGUE;

import java.util.Enumeration;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.inlining.InlineSequence;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operator;

/**
 * This class inserts yield points in
 * <ul>
 *  <li>1) a method's prologue
 *  <li>2) loop headers
 *  <li>3) (optionally) method exits (epilogue, athrow)
 * </ul>
 */
public class YieldPoints extends CompilerPhase {

  /**
   * Return the name of this phase
   * @return "Yield Point Insertion"
   */
  @Override
  public final String getName() {
    return "Yield Point Insertion";
  }

  /**
   * This phase contains no per-compilation instance fields.
   */
  @Override
  public final CompilerPhase newExecution(IR ir) {
    return this;
  }

  /**
   * Insert yield points in method prologues, loop heads, and method exits
   *
   */
  @Override
  public final void perform(IR ir) {
    if (!ir.method.isInterruptible()) {
      return;   // don't insert yieldpoints in Uninterruptible code.
    }

    // (1) Insert prologue yieldpoint unconditionally.
    //     As part of prologue/epilogue insertion we'll remove
    //     the yieldpoints in trivial methods that otherwise wouldn't need
    //     a stackframe.
    prependYield(ir.cfg.entry(), YIELDPOINT_PROLOGUE, 0, ir.gc.inlineSequence);

    // (2) If using epilogue yieldpoints scan basic blocks, looking for returns or throws
    if (VM.UseEpilogueYieldPoints) {
      for (Enumeration<BasicBlock> e = ir.getBasicBlocks(); e.hasMoreElements();) {
        BasicBlock block = e.nextElement();
        if (block.hasReturn() || block.hasAthrowInst()) {
          prependYield(block, YIELDPOINT_EPILOGUE, INSTRUMENTATION_BCI, ir.gc.inlineSequence);
        }
      }
    }

    // (3) Insert yieldpoints in loop heads based on the LST.
    LSTGraph lst = ir.HIRInfo.loopStructureTree;
    if (lst != null) {
      for (java.util.Enumeration<LSTNode> e = lst.getRoot().getChildren(); e.hasMoreElements();) {
        processLoopNest(e.nextElement());
      }
    }
  }

  /**
   * Process all loop heads in a loop nest by inserting a backedge yieldpoint in each of them.
   */
  private void processLoopNest(LSTNode n) {
    for (java.util.Enumeration<LSTNode> e = n.getChildren(); e.hasMoreElements();) {
      processLoopNest(e.nextElement());
    }
    Instruction dest = n.header.firstInstruction();
    if (dest.position.getMethod().isInterruptible()) {
      prependYield(n.header, YIELDPOINT_BACKEDGE, dest.bcIndex, dest.position);
    }
  }

  /**
   * Add a YIELD instruction to the appropriate place for the basic
   * block passed.
   *
   * @param bb the basic block
   * @param yp the yieldpoint operator to insert
   * @param bcIndex the bcIndex of the yieldpoint
   * @param position the source position of the yieldpoint
   */
  private void prependYield(BasicBlock bb, Operator yp, int bcIndex, InlineSequence position) {
    Instruction insertionPoint = null;

    if (bb.isEmpty()) {
      insertionPoint = bb.lastInstruction();
    } else {
      insertionPoint = bb.firstRealInstruction();
    }

    if (yp == YIELDPOINT_PROLOGUE) {
      if (VM.VerifyAssertions) {
        VM._assert((insertionPoint != null) && (insertionPoint.getOpcode() == IR_PROLOGUE_opcode));
      }
      // put it after the prologue
      insertionPoint = insertionPoint.nextInstructionInCodeOrder();
    } else if (VM.UseEpilogueYieldPoints && yp == YIELDPOINT_EPILOGUE) {
      // epilogues go before the return or athrow (at end of block)
      insertionPoint = bb.lastRealInstruction();
    }

    Instruction s = Empty.create(yp);
    insertionPoint.insertBefore(s);
    s.position = position;
    s.bcIndex = bcIndex;
  }
}




