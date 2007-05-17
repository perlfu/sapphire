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
package org.jikesrvm.adaptive.recompilation.instrumentation;

import java.util.ArrayList;
import org.jikesrvm.adaptive.controller.VM_Controller;
import org.jikesrvm.adaptive.database.VM_AOSDatabase;
import org.jikesrvm.adaptive.measurements.instrumentation.VM_Instrumentation;
import org.jikesrvm.adaptive.measurements.instrumentation.VM_StringEventCounterData;
import org.jikesrvm.compilers.opt.OPT_CompilerPhase;
import org.jikesrvm.compilers.opt.OPT_Options;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LABEL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.RETURN;
import org.jikesrvm.compilers.opt.ir.Prologue;

/**
 * The following OPT phase inserts counters on all instructions in the
 * IR.  It maintians one counter for each operand type, so it output
 * how many loads were executed, how many int_add's etc.  This is
 * useful for debugging and assessing the accuracy of optimizations.
 *
 * Note: The counters are added at the end of HIR, so the counts will
 * NOT reflect any changes to the code that occur after HIR.
 */
public class OPT_InsertInstructionCounters extends OPT_CompilerPhase {

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  public OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }

  public final boolean shouldPerform(OPT_Options options) {
    return VM_Controller.options.INSERT_INSTRUCTION_COUNTERS;
  }

  public final String getName() { return "InsertInstructionCounters"; }

  /**
   * Insert a counter on every instruction, and group counts by
   * opcode type.
   *
   * @param ir the governing IR
   */
  public final void perform(OPT_IR ir) {

    // Don't insert counters in uninterruptible methods,
    // the boot image, or when instrumentation is disabled
    if (!ir.method.isInterruptible() ||
        ir.method.getDeclaringClass().isInBootImage() ||
        !VM_Instrumentation.instrumentationEnabled()) {
      return;
    }

    // Get the data object that handles the counters
    VM_StringEventCounterData data = VM_AOSDatabase.instructionCounterData;

    // Create a vector of basic blocks up front because the blocks
    // are modified as we iterate below.
    ArrayList<OPT_BasicBlock> bbList = new ArrayList<OPT_BasicBlock>();
    for (OPT_BasicBlockEnumeration bbe = ir.getBasicBlocks(); bbe.hasMoreElements();) {
      OPT_BasicBlock bb = bbe.next();
      bbList.add(bb);
    }

    // Iterate through the basic blocks
    for (OPT_BasicBlock bb : bbList) {
      // Add instructions to vector so enumeration doesn't mess
      // things up.  There is probably a better way to do this, but
      // it doesn't matter because this is a debugging phase.
      ArrayList<OPT_Instruction> iList = new ArrayList<OPT_Instruction>();
      OPT_Instruction inst = bb.firstInstruction();
      while (inst != null && inst != bb.lastInstruction()) {
        iList.add(inst);
        inst = inst.nextInstructionInCodeOrder();
      }

      // Iterate through all the instructions in this block.
      for (OPT_Instruction i : iList) {

        // Skip dangerous instructions
        if (i.operator() == LABEL || Prologue.conforms(i)) {
          continue;
        }

        if (i.isBranch() || i.operator() == RETURN) {

          // It's a branch, so you need to be careful how you insert the
          // counter.
          OPT_Instruction prev = i.prevInstructionInCodeOrder();

          // If the instruction above this branch is also a branch,
          // then we can't instrument as-is because a basic block
          // must end with branches only.  Solve by splitting block.
          if (prev.isBranch()) {
            // OPT_BasicBlock newBlock =
            bb.splitNodeWithLinksAt(prev, ir);
            bb.recomputeNormalOut(ir);
          }

          // Use the name of the operator as the name of the event
          OPT_Instruction counterInst = data.
              getCounterInstructionForEvent(i.operator().toString());

          // Insert the new instruction into the code order
          i.insertBefore(counterInst);
        } else {
          // It's a non-branching instruction.  Insert counter before
          // the instruction.

          // Use the name of the operator as the name of the event
          OPT_Instruction counterInst = data.
              getCounterInstructionForEvent(i.operator().toString());

          i.insertBefore(counterInst);
        }
      }
    }
  }
}
