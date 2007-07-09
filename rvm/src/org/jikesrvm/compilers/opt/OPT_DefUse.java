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

import java.util.Enumeration;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_OperandEnumeration;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PHI;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperandEnumeration;
import org.vmmagic.pragma.NoInline;

/**
 * This class computes du-lists and associated information.
 *
 * <P> Note: DU operands are stored on the USE lists, but not the DEF
 * lists.
 */
public final class OPT_DefUse {
  static final boolean DEBUG = false;
  static final boolean TRACE_DU_ACTIONS = false;
  static final boolean SUPRESS_DU_FOR_PHYSICALS = true;

  /**
   * Clear defList, useList for an IR.
   *
   * @param ir the IR in question
   */
  public static void clearDU(OPT_IR ir) {
    for (OPT_Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = reg.getNext()) {
      reg.defList = null;
      reg.useList = null;
      reg.scratch = -1;
      reg.clearSeenUse();
    }
    for (Enumeration<OPT_Register> e = ir.regpool.getPhysicalRegisterSet().enumerateAll(); e.hasMoreElements();) {
      OPT_Register reg = e.nextElement();
      reg.defList = null;
      reg.useList = null;
      reg.scratch = -1;
      reg.clearSeenUse();
    }

    if (TRACE_DU_ACTIONS || DEBUG) {
      VM.sysWrite("Cleared DU\n");
    }
  }

  /**
   * Compute the register list and def-use lists for a method.
   *
   * @param ir the IR in question
   */
  @NoInline
  public static void computeDU(OPT_IR ir) {
    // Clear old register list (if any)
    clearDU(ir);
    // Create register defList and useList
    for (OPT_Instruction instr = ir.firstInstructionInCodeOrder(); instr != null; instr =
        instr.nextInstructionInCodeOrder()) {

      OPT_OperandEnumeration defs = instr.getPureDefs();
      OPT_OperandEnumeration uses = instr.getUses();

      while (defs.hasMoreElements()) {
        OPT_Operand op = defs.next();
        if (op instanceof OPT_RegisterOperand) {
          OPT_RegisterOperand rop = (OPT_RegisterOperand) op;
          recordDef(rop);
        }
      }         // for ( defs = ... )

      while (uses.hasMoreElements()) {
        OPT_Operand op = uses.next();
        if (op instanceof OPT_RegisterOperand) {
          OPT_RegisterOperand rop = (OPT_RegisterOperand) op;
          recordUse(rop);
        }
      }         // for ( uses = ... )
    }           // for ( instr = ... )
    // Remove any symbloic registers with no uses/defs from
    // the register pool.  We'll waste analysis time keeping them around.
    OPT_Register next;
    for (OPT_Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = next) {
      next = reg.getNext();
      if (reg.defList == null && reg.useList == null) {
        if (DEBUG) {
          VM.sysWrite("Removing " + reg + " from the register pool\n");
        }
        ir.regpool.removeRegister(reg);
      }
    }
  }

  /**
   * Record a use of a register
   * @param regOp the operand that uses the register
   */
  static void recordUse(OPT_RegisterOperand regOp) {
    OPT_Register reg = regOp.getRegister();
    regOp.append(reg.useList);
    reg.useList = regOp;
    reg.useCount++;
  }

  /**
   * Record a def/use of a register
   * TODO: For now we just pretend this is a use!!!!
   *
   * @param regOp the operand that uses the register
   */
  public static void recordDefUse(OPT_RegisterOperand regOp) {
    OPT_Register reg = regOp.getRegister();
    if (SUPRESS_DU_FOR_PHYSICALS && reg.isPhysical()) return;
    regOp.append(reg.useList);
    reg.useList = regOp;
  }

  /**
   * Record a def of a register
   * @param regOp the operand that uses the register
   */
  static void recordDef(OPT_RegisterOperand regOp) {
    OPT_Register reg = regOp.getRegister();
    if (SUPRESS_DU_FOR_PHYSICALS && reg.isPhysical()) return;
    regOp.append(reg.defList);
    reg.defList = regOp;
  }

  /**
   * Record that a use of a register no longer applies
   * @param regOp the operand that uses the register
   */
  public static void removeUse(OPT_RegisterOperand regOp) {
    OPT_Register reg = regOp.getRegister();
    if (SUPRESS_DU_FOR_PHYSICALS && reg.isPhysical()) return;
    if (regOp == reg.useList) {
      reg.useList = reg.useList.getNext();
    } else {
      OPT_RegisterOperand prev = reg.useList;
      OPT_RegisterOperand curr = prev.getNext();
      while (curr != regOp) {
        prev = curr;
        curr = curr.getNext();
      }
      prev.setNext(curr.getNext());
    }
    reg.useCount--;
    if (DEBUG) {
      VM.sysWrite("removed a use " + regOp.instruction + "\n");
      printUses(reg);
    }
  }

  /**
   * Record that a def of a register no longer applies
   * @param regOp the operand that uses the register
   */
  public static void removeDef(OPT_RegisterOperand regOp) {
    OPT_Register reg = regOp.getRegister();
    if (SUPRESS_DU_FOR_PHYSICALS && reg.isPhysical()) return;
    if (regOp == reg.defList) {
      reg.defList = reg.defList.getNext();
    } else {
      OPT_RegisterOperand prev = reg.defList;
      OPT_RegisterOperand curr = prev.getNext();
      while (curr != regOp) {
        prev = curr;
        curr = curr.getNext();
      }
      prev.setNext(curr.getNext());
    }
    if (DEBUG) {
      VM.sysWrite("removed a def " + regOp.instruction + "\n");
      printDefs(reg);
    }
  }

  /**
   *  This code changes the use in <code>origRegOp</code> to use
   *    the use in <code>newRegOp</code>.
   *
   *  <p> If the type of <code>origRegOp</code> is not a reference, but the
   *  type of <code>newRegOp</code> is a reference, we need to update
   *  <code>origRegOp</code> to be a reference.
   *  Otherwise, the GC map code will be incorrect.   -- Mike Hind
   *  @param origRegOp the register operand to change
   *  @param newRegOp the register operand to use for the change
   */
  static void transferUse(OPT_RegisterOperand origRegOp, OPT_RegisterOperand newRegOp) {
    if (VM.VerifyAssertions) {
      VM._assert(origRegOp.getRegister().getType() == newRegOp.getRegister().getType());
    }
    OPT_Instruction inst = origRegOp.instruction;
    if (DEBUG) {
      VM.sysWrite("Transfering a use of " + origRegOp + " in " + inst + " to " + newRegOp + "\n");
    }
    removeUse(origRegOp);
    // check to see if the regOp type is NOT a ref, but the newRegOp type
    // is a reference.   This can occur because of magic calls.
    if (!origRegOp.getType().isReferenceType() && newRegOp.getType().isReferenceType()) {
      // clone the newRegOp object and use it to replace the regOp object
      OPT_RegisterOperand copiedRegOp = (OPT_RegisterOperand) newRegOp.copy();
      inst.replaceOperand(origRegOp, copiedRegOp);
      recordUse(copiedRegOp);
    } else {
      // just copy the register
      origRegOp.setRegister(newRegOp.getRegister());
      recordUse(origRegOp);
    }
    if (DEBUG) {
      printUses(origRegOp.getRegister());
      printUses(newRegOp.getRegister());
    }
  }

  /**
   * Remove an instruction and update register lists.
   */
  static void removeInstructionAndUpdateDU(OPT_Instruction s) {
    for (OPT_OperandEnumeration e = s.getPureDefs(); e.hasMoreElements();) {
      OPT_Operand op = e.next();
      if (op instanceof OPT_RegisterOperand) {
        removeDef((OPT_RegisterOperand) op);
      }
    }
    for (OPT_OperandEnumeration e = s.getUses(); e.hasMoreElements();) {
      OPT_Operand op = e.next();
      if (op instanceof OPT_RegisterOperand) {
        removeUse((OPT_RegisterOperand) op);
      }
    }
    s.remove();
  }

  /**
   * Update register lists to account for the effect of a new
   * instruction s
   */
  public static void updateDUForNewInstruction(OPT_Instruction s) {
    for (OPT_OperandEnumeration e = s.getPureDefs(); e.hasMoreElements();) {
      OPT_Operand op = e.next();
      if (op instanceof OPT_RegisterOperand) {
        recordDef((OPT_RegisterOperand) op);
      }
    }
    for (OPT_OperandEnumeration e = s.getUses(); e.hasMoreElements();) {
      OPT_Operand op = e.next();
      if (op instanceof OPT_RegisterOperand) {
        recordUse((OPT_RegisterOperand) op);
      }
    }
  }

  /**
   * Replace an instruction and update register lists.
   */
  static void replaceInstructionAndUpdateDU(OPT_Instruction oldI, OPT_Instruction newI) {
    oldI.insertBefore(newI);
    removeInstructionAndUpdateDU(oldI);
    updateDUForNewInstruction(newI);
  }

  /**
   * Enumerate all operands that use a given register.
   */
  static OPT_RegisterOperandEnumeration uses(OPT_Register reg) {
    return new RegOpListWalker(reg.useList);
  }

  /**
   * Enumerate all operands that def a given register.
   */
  public static OPT_RegisterOperandEnumeration defs(OPT_Register reg) {
    return new RegOpListWalker(reg.defList);
  }

  /**
   * Does a given register have exactly one use?
   */
  static boolean exactlyOneUse(OPT_Register reg) {
    return (reg.useList != null) && (reg.useList.getNext() == null);
  }

  /**
   * Print all the instructions that def a register.
   * @param reg
   */
  static void printDefs(OPT_Register reg) {
    VM.sysWrite("Definitions of " + reg + '\n');
    for (OPT_RegisterOperandEnumeration e = defs(reg); e.hasMoreElements();) {
      VM.sysWrite("\t" + e.next().instruction + "\n");
    }
  }

  /**
   * Print all the instructions that usea register.
   * @param reg
   */
  static void printUses(OPT_Register reg) {
    VM.sysWrite("Uses of " + reg + '\n');
    for (OPT_RegisterOperandEnumeration e = uses(reg); e.hasMoreElements();) {
      VM.sysWrite("\t" + e.next().instruction + "\n");
    }
  }

  /**
   * Recompute <code> isSSA </code> for all registers by traversing register
   * list.
   * NOTE: the DU MUST be computed BEFORE calling this function
   *
   * @param ir the IR in question
   */
  public static void recomputeSSA(OPT_IR ir) {
    // Use register /ist to enumerate register objects (FAST)
    for (OPT_Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = reg.getNext()) {
      // Set isSSA = true iff reg has exactly one static definition.
      reg.putSSA((reg.defList != null && reg.defList.getNext() == null));
    }
  }

  /**
   * Merge register reg2 into register reg1.
   * Remove reg2 from the DU information
   */
  public static void mergeRegisters(OPT_IR ir, OPT_Register reg1, OPT_Register reg2) {
    OPT_RegisterOperand lastOperand;
    if (reg1 == reg2) {
      return;
    }
    if (DEBUG) {
      VM.sysWrite("Merging " + reg2 + " into " + reg1 + "\n");
      printDefs(reg2);
      printUses(reg2);
      printDefs(reg1);
      printUses(reg1);
    }
    // first loop through defs of reg2 (currently, there will only be one def)
    lastOperand = null;
    for (OPT_RegisterOperand def = reg2.defList; def != null; lastOperand = def, def = def.getNext()) {
      // Change def to refer to reg1 instead
      def.setRegister(reg1);
      // Track lastOperand
      lastOperand = def;
    }
    if (lastOperand != null) {
      // Set reg1.defList = concat(reg2.defList, reg1.deflist)
      lastOperand.setNext(reg1.defList);
      reg1.defList = reg2.defList;
    }
    // now loop through uses
    lastOperand = null;
    for (OPT_RegisterOperand use = reg2.useList; use != null; use = use.getNext()) {
      // Change use to refer to reg1 instead
      use.setRegister(reg1);
      // Track lastOperand
      lastOperand = use;
    }
    if (lastOperand != null) {
      // Set reg1.useList = concat(reg2.useList, reg1.uselist)
      lastOperand.setNext(reg1.useList);
      reg1.useList = reg2.useList;
    }
    // Remove reg2 from RegisterPool
    ir.regpool.removeRegister(reg2);
    if (DEBUG) {
      VM.sysWrite("Merge complete\n");
      printDefs(reg1);
      printUses(reg1);
    }
  }

  /**
   * Recompute spansBasicBlock flags for all registers.
   *
   * @param ir the IR in question
   */
  public static void recomputeSpansBasicBlock(OPT_IR ir) {
    // clear fields
    for (OPT_Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = reg.getNext()) {
      reg.scratch = -1;
      reg.clearSpansBasicBlock();
    }
    // iterate over the basic blocks
    for (OPT_BasicBlock bb = ir.firstBasicBlockInCodeOrder(); bb != null; bb = bb.nextBasicBlockInCodeOrder()) {
      int bbNum = bb.getNumber();
      // enumerate the instructions in the basic block
      for (OPT_InstructionEnumeration e = bb.forwardRealInstrEnumerator(); e.hasMoreElements();) {
        OPT_Instruction inst = e.next();
        // check each Operand in the instruction
        for (OPT_OperandEnumeration ops = inst.getOperands(); ops.hasMoreElements();) {
          OPT_Operand op = ops.next();
          if (op instanceof OPT_RegisterOperand) {
            OPT_Register reg = ((OPT_RegisterOperand) op).getRegister();
            if (reg.isPhysical()) {
              continue;
            }
            if (reg.spansBasicBlock()) {
              continue;
            }
            if (seenInDifferentBlock(reg, bbNum)) {
              reg.setSpansBasicBlock();
              continue;
            }
            if (inst.operator() == PHI) {
              reg.setSpansBasicBlock();
              continue;
            }
            logAppearance(reg, bbNum);
          }
        }
      }
    }
  }

  /**
   * Mark that we have seen a register in a particular
   * basic block, and whether we saw a use
   *
   * @param reg the register
   * @param bbNum the number of the basic block
   */
  private static void logAppearance(OPT_Register reg, int bbNum) {
    reg.scratch = bbNum;
  }

  /**
   * Have we seen this register in a different basic block?
   *
   * @param reg the register
   * @param bbNum the number of the basic block
   */
  private static boolean seenInDifferentBlock(OPT_Register reg, int bbNum) {
    int bb = reg.scratch;
    return (bb != -1) && (bb != bbNum);
  }

  /**
   * Utility class to encapsulate walking a use/def list.
   */
  private static final class RegOpListWalker implements OPT_RegisterOperandEnumeration {

    private OPT_RegisterOperand current;

    RegOpListWalker(OPT_RegisterOperand start) {
      current = start;
    }

    public boolean hasMoreElements() {
      return current != null;
    }

    public OPT_RegisterOperand nextElement() {
      return next();
    }

    public OPT_RegisterOperand next() {
      if (current == null) raiseNoSuchElementException();
      OPT_RegisterOperand tmp = current;
      current = current.getNext();
      return tmp;
    }

    @NoInline
    private static void raiseNoSuchElementException() {
      throw new java.util.NoSuchElementException("RegOpListWalker");
    }
  }
}
