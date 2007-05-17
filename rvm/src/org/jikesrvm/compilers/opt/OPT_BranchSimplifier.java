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

import org.jikesrvm.compilers.opt.ir.Goto;
import org.jikesrvm.compilers.opt.ir.GuardResultCarrier;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.IfCmp2;
import org.jikesrvm.compilers.opt.ir.InlineGuard;
import org.jikesrvm.compilers.opt.ir.LookupSwitch;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BranchOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ConditionOperand;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_IREnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GOTO;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GUARD_MOVE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_IFCMP;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.OPT_TrueGuardOperand;
import org.jikesrvm.compilers.opt.ir.TableSwitch;

/**
 * Simplify and canonicalize conditional branches with constant operands.
 *
 * <p> This module performs no analysis, it simply attempts to
 * simplify any branching instructions of a basic block that have constant
 * operands. The intent is that analysis modules can call this
 * transformation engine, allowing us to share the
 * simplification code among multiple analysis modules.
 */
abstract class OPT_BranchSimplifier {

  /**
   * Given a basic block, attempt to simplify any conditional branch
   * instructions with constant operands.
   * The instruction will be mutated in place.
   * The control flow graph will be updated, but the caller is responsible
   * for calling OPT_BranchOptmizations after simplify has been called on
   * all basic blocks in the IR to remove unreachable code.
   *
   * @param bb the basic block to simplify
   * @return true if we do something, false otherwise.
   */
  public static boolean simplify(OPT_BasicBlock bb, OPT_IR ir) {
    boolean didSomething = false;

    for (OPT_InstructionEnumeration branches = bb.enumerateBranchInstructions(); branches.hasMoreElements();) {
      OPT_Instruction s = branches.next();
      if (Goto.conforms(s)) {
        // nothing to do, but a common case so test first
      } else if (IfCmp.conforms(s)) {
        if (processIfCmp(ir, bb, s)) {
          // hack. Just start over since Enumeration has changed.
          branches = bb.enumerateBranchInstructions();
          bb.recomputeNormalOut(ir);
          didSomething = true;
        }
      } else if (IfCmp2.conforms(s)) {
        if (processIfCmp2(ir, bb, s)) {
          // hack. Just start over since Enumeration has changed.
          branches = bb.enumerateBranchInstructions();
          bb.recomputeNormalOut(ir);
          didSomething = true;
        }
      } else if (LookupSwitch.conforms(s)) {
        if (processLookupSwitch(ir, bb, s)) {
          // hack. Just start over since Enumeration has changed.
          branches = bb.enumerateBranchInstructions();
          bb.recomputeNormalOut(ir);
          didSomething = true;
        }
      } else if (TableSwitch.conforms(s)) {
        if (processTableSwitch(ir, bb, s)) {
          // hack. Just start over since Enumeration has changed.
          branches = bb.enumerateBranchInstructions();
          bb.recomputeNormalOut(ir);
          didSomething = true;
        }
      } else if (InlineGuard.conforms(s)) {
        if (processInlineGuard(ir, bb, s)) {
          // hack. Just start over since Enumeration has changed.
          branches = bb.enumerateBranchInstructions();
          bb.recomputeNormalOut(ir);
          didSomething = true;
        }
      }
    }
    return didSomething;
  }

  /** Process IfCmp branch instruction */
  static boolean processIfCmp(OPT_IR ir, OPT_BasicBlock bb, OPT_Instruction s) {
    OPT_RegisterOperand guard = IfCmp.getGuardResult(s);
    OPT_Operand val1 = IfCmp.getVal1(s);
    OPT_Operand val2 = IfCmp.getVal2(s);
    {
      int cond = IfCmp.getCond(s).evaluate(val1, val2);
      if (cond != OPT_ConditionOperand.UNKNOWN) {
        // constant fold
        if (cond == OPT_ConditionOperand.TRUE) {  // branch taken
          insertTrueGuard(s, guard);
          Goto.mutate(s, GOTO, IfCmp.getTarget(s));
          removeBranchesAfterGotos(bb);
        } else {
          // branch not taken
          insertTrueGuard(s, guard);
          s.remove();
        }
        return true;
      }
    }
    if (val1.isConstant() && !val2.isConstant()) {
      // Canonicalize by making second argument the constant
      IfCmp.setVal1(s, val2);
      IfCmp.setVal2(s, val1);
      IfCmp.setCond(s, IfCmp.getCond(s).flipOperands());
    }

    if (val2.isIntConstant()) {
      // Tricks to get compare against zero.
      int value = ((OPT_IntConstantOperand) val2).value;
      OPT_ConditionOperand cond = IfCmp.getCond(s);
      if (value == 1) {
        if (cond.isLESS()) {
          IfCmp.setCond(s, OPT_ConditionOperand.LESS_EQUAL());
          IfCmp.setVal2(s, new OPT_IntConstantOperand(0));
        } else if (cond.isGREATER_EQUAL()) {
          IfCmp.setCond(s, OPT_ConditionOperand.GREATER());
          IfCmp.setVal2(s, new OPT_IntConstantOperand(0));
        }
      } else if (value == -1) {
        if (cond.isGREATER()) {
          IfCmp.setCond(s, OPT_ConditionOperand.GREATER_EQUAL());
          IfCmp.setVal2(s, new OPT_IntConstantOperand(0));
        } else if (cond.isLESS_EQUAL()) {
          IfCmp.setCond(s, OPT_ConditionOperand.LESS());
          IfCmp.setVal2(s, new OPT_IntConstantOperand(0));
        }
      }
    }
    return false;
  }

  /** Process IfCmp2 branch instruction */
  static boolean processIfCmp2(OPT_IR ir, OPT_BasicBlock bb, OPT_Instruction s) {
    OPT_RegisterOperand guard = IfCmp2.getGuardResult(s);
    OPT_Operand val1 = IfCmp2.getVal1(s);
    OPT_Operand val2 = IfCmp2.getVal2(s);
    int cond1 = IfCmp2.getCond1(s).evaluate(val1, val2);
    int cond2 = IfCmp2.getCond2(s).evaluate(val1, val2);
    if (cond1 == OPT_ConditionOperand.TRUE) {
      // target 1 taken
      insertTrueGuard(s, guard);
      Goto.mutate(s, GOTO, IfCmp2.getTarget1(s));
      removeBranchesAfterGotos(bb);
    } else if ((cond1 == OPT_ConditionOperand.FALSE) && (cond2 == OPT_ConditionOperand.TRUE)) {
      // target 2 taken
      insertTrueGuard(s, guard);
      Goto.mutate(s, GOTO, IfCmp2.getTarget2(s));
      removeBranchesAfterGotos(bb);
    } else if ((cond1 == OPT_ConditionOperand.FALSE) && (cond2 == OPT_ConditionOperand.FALSE)) {
      // not taken
      insertTrueGuard(s, guard);
      s.remove();
    } else if ((cond1 == OPT_ConditionOperand.FALSE) && (cond2 == OPT_ConditionOperand.UNKNOWN)) {
      // target 1 not taken, simplify test to ifcmp
      IfCmp.mutate(s,
                   INT_IFCMP,
                   guard,
                   val1,
                   val2,
                   IfCmp2.getCond2(s),
                   IfCmp2.getTarget2(s),
                   IfCmp2.getBranchProfile2(s));
    } else if ((cond1 == OPT_ConditionOperand.UNKNOWN) && (cond2 == OPT_ConditionOperand.FALSE)) {
      // target 1 taken possibly, simplify test to ifcmp
      IfCmp.mutate(s,
                   INT_IFCMP,
                   guard,
                   val1,
                   val2,
                   IfCmp2.getCond1(s),
                   IfCmp2.getTarget1(s),
                   IfCmp2.getBranchProfile1(s));
    } else if ((cond1 == OPT_ConditionOperand.UNKNOWN) && (cond2 == OPT_ConditionOperand.TRUE)) {
      // target 1 taken possibly, simplify first test to ifcmp and
      // insert goto after
      s.insertAfter(Goto.create(GOTO, IfCmp2.getTarget2(s)));
      IfCmp.mutate(s,
                   INT_IFCMP,
                   guard,
                   val1,
                   val2,
                   IfCmp2.getCond1(s),
                   IfCmp2.getTarget1(s),
                   IfCmp2.getBranchProfile1(s));
      removeBranchesAfterGotos(bb);
    } else {
      if (val1.isConstant() && !val2.isConstant()) {
        // Canonicalize by making second argument the constant
        IfCmp2.setVal1(s, val2);
        IfCmp2.setVal2(s, val1);
        IfCmp2.setCond1(s, IfCmp2.getCond1(s).flipOperands());
        IfCmp2.setCond2(s, IfCmp2.getCond2(s).flipOperands());
      }
      // we did no optimisation, return false
      return false;
    }
    return true;
  }

  /** Process LookupSwitch branch instruction */
  static boolean processLookupSwitch(OPT_IR ir, OPT_BasicBlock bb, OPT_Instruction s) {
    OPT_Operand val = LookupSwitch.getValue(s);
    int numMatches = LookupSwitch.getNumberOfMatches(s);
    if (numMatches == 0) {
      // Can only goto default
      Goto.mutate(s, GOTO, LookupSwitch.getDefault(s));
    } else if (val.isConstant()) {
      // lookup value is constant
      int value = ((OPT_IntConstantOperand) val).value;
      OPT_BranchOperand target = LookupSwitch.getDefault(s);
      for (int i = 0; i < numMatches; i++) {
        if (value == LookupSwitch.getMatch(s, i).value) {
          target = LookupSwitch.getTarget(s, i);
          break;
        }
      }
      Goto.mutate(s, GOTO, target);
    } else if (numMatches == 1) {
      // only 1 match, simplify to ifcmp
      OPT_BranchOperand defaultTarget = LookupSwitch.getDefault(s);
      IfCmp.mutate(s,
                   INT_IFCMP,
                   ir.regpool.makeTempValidation(),
                   val,
                   LookupSwitch.getMatch(s, 0),
                   OPT_ConditionOperand.EQUAL(),
                   LookupSwitch.getTarget(s, 0),
                   LookupSwitch.getBranchProfile(s, 0));
      s.insertAfter(Goto.create(GOTO, defaultTarget));
    } else {
      // no optimisation, just continue
      return false;
    }
    return true;
  }

  /** Process TableSwitch branch instruction */
  static boolean processTableSwitch(OPT_IR ir, OPT_BasicBlock bb, OPT_Instruction s) {
    OPT_Operand val = TableSwitch.getValue(s);
    int low = TableSwitch.getLow(s).value;
    int high = TableSwitch.getHigh(s).value;
    if (val.isConstant()) {
      int value = ((OPT_IntConstantOperand) val).value;
      OPT_BranchOperand target = TableSwitch.getDefault(s);
      if (value >= low && value <= high) {
        target = TableSwitch.getTarget(s, value - low);
      }
      Goto.mutate(s, GOTO, target);
    } else if (low == high) {
      // only 1 match, simplify to ifcmp
      OPT_BranchOperand defaultTarget = TableSwitch.getDefault(s);
      IfCmp.mutate(s,
                   INT_IFCMP,
                   ir.regpool.makeTempValidation(),
                   val,
                   new OPT_IntConstantOperand(low),
                   OPT_ConditionOperand.EQUAL(),
                   TableSwitch.getTarget(s, 0),
                   TableSwitch.getBranchProfile(s, 0));
      s.insertAfter(Goto.create(GOTO, defaultTarget));
    } else {
      // no optimisation, just continue
      return false;
    }
    return true;
  }

  /** Process InlineGuard branch instruction */
  static boolean processInlineGuard(OPT_IR ir, OPT_BasicBlock bb, OPT_Instruction s) {
    OPT_Operand val = InlineGuard.getValue(s);
    if (val.isNullConstant()) {
      // branch not taken
      s.remove();
      return true;
    } else if (val.isObjectConstant()) {
      // TODO:
      // VM.sysWrite("TODO: should constant fold MethodIfCmp on ObjectConstant");
    }
    return false;
  }

  /**
   * To maintain IR integrity, remove any branches that are after the
   * first GOTO in the basic block.
   */
  private static void removeBranchesAfterGotos(OPT_BasicBlock bb) {
    // identify the first GOTO instruction in the basic block
    OPT_Instruction firstGoto = null;
    OPT_Instruction end = bb.lastRealInstruction();
    for (OPT_InstructionEnumeration branches = bb.enumerateBranchInstructions(); branches.hasMoreElements();) {
      OPT_Instruction s = branches.next();
      if (Goto.conforms(s)) {
        firstGoto = s;
        break;
      }
    }
    // remove all instructions after the first GOTO instruction
    if (firstGoto != null) {
      OPT_InstructionEnumeration ie = OPT_IREnumeration.forwardIntraBlockIE(firstGoto, end);
      ie.next();
      while (ie.hasMoreElements()) {
        OPT_Instruction s = ie.next();
        if (GuardResultCarrier.conforms(s)) {
          insertTrueGuard(s, GuardResultCarrier.getGuardResult(s));
        }
        s.remove();
      }
    }
  }

  private static void insertTrueGuard(OPT_Instruction inst, OPT_RegisterOperand guard) {
    if (guard == null) return;  // Probably bad style but correct IR
    OPT_Instruction trueGuard = Move.create(GUARD_MOVE, guard.copyD2D(), new OPT_TrueGuardOperand());
    trueGuard.position = inst.position;
    trueGuard.bcIndex = inst.bcIndex;
    inst.insertBefore(trueGuard);
    OPT_DefUse.updateDUForNewInstruction(trueGuard);
  }
}
