/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;
import com.ibm.JikesRVM.classloader.*;
import org.vmmagic.unboxed.Offset;

/**
 * Handles the conversion from LIR to MIR of operators whose 
 * expansion requires the introduction of new control flow (new basic blocks).
 *
 * @author Dave Grove
 * @modified Peter Sweeney
 * @modified Ian Rogers
 */
abstract class OPT_ComplexLIR2MIRExpansion extends OPT_IRTools {

  /**
   * Converts the given IR to low level IA32 IR.
   *
   * @param ir IR to convert
   */
  public static void convert(OPT_IR ir) {
    OPT_Instruction nextInstr;
    for (OPT_Instruction s = ir.firstInstructionInCodeOrder();
         s != null; s = nextInstr) {
      switch (s.getOpcode()) {
      case LONG_IFCMP_opcode:
        {
          OPT_Operand val2 = IfCmp.getVal2(s);
          if (val2 instanceof OPT_RegisterOperand) {
            nextInstr = long_ifcmp(s, ir);
          } else {
            nextInstr = long_ifcmp_imm(s, ir);
          }
        }
        break;
      case FLOAT_IFCMP_opcode:
      case DOUBLE_IFCMP_opcode:
        nextInstr = fp_ifcmp(s, ir);
        break;
      default:
        nextInstr = s.nextInstructionInCodeOrder();
        break;
      }
    }
    OPT_DefUse.recomputeSpansBasicBlock(ir);
  }

  private static OPT_Instruction long_ifcmp(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    OPT_ConditionOperand cond = IfCmp.getCond(s);
    OPT_Register xh = ((OPT_RegisterOperand)IfCmp.getVal1(s)).register;
    OPT_Register xl = ir.regpool.getSecondReg(xh);
    OPT_RegisterOperand yh = (OPT_RegisterOperand)IfCmp.getClearVal2(s);
    OPT_RegisterOperand yl = new OPT_RegisterOperand(ir.regpool.getSecondReg(yh.register), VM_TypeReference.Int);
    basic_long_ifcmp(s, ir, cond, xh, xl, yh, yl);
    return nextInstr;
  }


  private static OPT_Instruction long_ifcmp_imm(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    OPT_ConditionOperand cond = IfCmp.getCond(s);
    OPT_Register xh = ((OPT_RegisterOperand)IfCmp.getVal1(s)).register;
    OPT_Register xl = ir.regpool.getSecondReg(xh);
    OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)IfCmp.getVal2(s);
    int low = rhs.lower32();
    int high = rhs.upper32();
    OPT_IntConstantOperand yh = IC(high);
    OPT_IntConstantOperand yl = IC(low);
    
    if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
      // tricky... ((xh^yh)|(xl^yl) == 0) <==> (lhll == rhrl)!!
      OPT_Register th = ir.regpool.getInteger();
      OPT_Register tl = ir.regpool.getInteger();
      if (high == 0) {
        if (low == 0) { // 0,0
          s.insertBefore(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(th, VM_TypeReference.Int),
													  new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR, new OPT_RegisterOperand(th, VM_TypeReference.Int),
															 new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
        } else if (low == -1) { // 0,-1
          s.insertBefore(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(tl, VM_TypeReference.Int),
													  new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new OPT_RegisterOperand(tl, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR, new OPT_RegisterOperand(tl, VM_TypeReference.Int),
															 new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
        } else { // 0,*
          s.insertBefore(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(tl, VM_TypeReference.Int),
													  new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new OPT_RegisterOperand(tl, VM_TypeReference.Int), yl));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR, new OPT_RegisterOperand(tl, VM_TypeReference.Int),
															 new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
        }
      } else if (high == -1) {
        if (low == 0) { // -1,0
          s.insertBefore(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(th, VM_TypeReference.Int),
													  new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new OPT_RegisterOperand(th, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR, new OPT_RegisterOperand(th, VM_TypeReference.Int),
															 new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
        } else if (low == -1) { // -1,-1
          s.insertBefore(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(th, VM_TypeReference.Int),
													  new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new OPT_RegisterOperand(th, VM_TypeReference.Int)));
          s.insertBefore(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(tl, VM_TypeReference.Int),
													  new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new OPT_RegisterOperand(tl, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR, new OPT_RegisterOperand(th, VM_TypeReference.Int),
															 new OPT_RegisterOperand(tl, VM_TypeReference.Int)));
        } else { // -1,*
          s.insertBefore(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(th, VM_TypeReference.Int),
													  new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new OPT_RegisterOperand(th, VM_TypeReference.Int)));
          s.insertBefore(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(tl, VM_TypeReference.Int),
													  new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new OPT_RegisterOperand(tl, VM_TypeReference.Int), yl));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR, new OPT_RegisterOperand(th, VM_TypeReference.Int),
															 new OPT_RegisterOperand(tl, VM_TypeReference.Int)));
        }
      } else { 
        if (low == 0) { // *,0
          s.insertBefore(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(th, VM_TypeReference.Int),
													  new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new OPT_RegisterOperand(th, VM_TypeReference.Int), yh));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR, new OPT_RegisterOperand(th, VM_TypeReference.Int),
															 new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
        } else if (low == -1) { // *,-1
          s.insertBefore(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(th, VM_TypeReference.Int),
													  new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new OPT_RegisterOperand(th, VM_TypeReference.Int), yh));
          s.insertBefore(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(tl, VM_TypeReference.Int),
													  new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new OPT_RegisterOperand(tl, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR, new OPT_RegisterOperand(th, VM_TypeReference.Int),
															 new OPT_RegisterOperand(tl, VM_TypeReference.Int)));
        } else { // neither high nor low is special
          s.insertBefore(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(th, VM_TypeReference.Int),
													  new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new OPT_RegisterOperand(th, VM_TypeReference.Int), yh));
          s.insertBefore(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(tl, VM_TypeReference.Int),
													  new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new OPT_RegisterOperand(tl, VM_TypeReference.Int), yl));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR, new OPT_RegisterOperand(th, VM_TypeReference.Int),
															 new OPT_RegisterOperand(tl, VM_TypeReference.Int)));
        }
      }
      MIR_CondBranch.mutate(s, IA32_JCC, 
                            new OPT_IA32ConditionOperand(cond),
                            IfCmp.getTarget(s),
                            IfCmp.getBranchProfile(s));
      return nextInstr;
    } else {
      // pick up a few special cases where the sign of xh is sufficient
      if (rhs.value == 0L) {
        if (cond.isLESS()) {
          // xh < 0 implies true
          s.insertBefore(MIR_Compare.create(IA32_CMP, new OPT_RegisterOperand(xh, VM_TypeReference.Int), IC(0)));
          MIR_CondBranch.mutate(s, IA32_JCC,
                                OPT_IA32ConditionOperand.LT(),
                                IfCmp.getTarget(s),
                                IfCmp.getBranchProfile(s));
          return nextInstr;
        } else if (cond.isGREATER_EQUAL()) {
          s.insertBefore(MIR_Compare.create(IA32_CMP, new OPT_RegisterOperand(xh, VM_TypeReference.Int), IC(0)));
          MIR_CondBranch.mutate(s, IA32_JCC,
                                OPT_IA32ConditionOperand.GE(),
                                IfCmp.getTarget(s),
                                IfCmp.getBranchProfile(s));
          return nextInstr;
        }
      } else if (rhs.value == -1L) {
        if (cond.isLESS_EQUAL()) {
          s.insertBefore(MIR_Compare.create(IA32_CMP, new OPT_RegisterOperand(xh, VM_TypeReference.Int), IC(-1)));
          MIR_CondBranch.mutate(s, IA32_JCC,
                                OPT_IA32ConditionOperand.LE(),
                                IfCmp.getTarget(s),
                                IfCmp.getBranchProfile(s));
          return nextInstr;
        } else if (cond.isGREATER()) {
          s.insertBefore(MIR_Compare.create(IA32_CMP, new OPT_RegisterOperand(xh, VM_TypeReference.Int), IC(0)));
          MIR_CondBranch.mutate(s, IA32_JCC,
                                OPT_IA32ConditionOperand.GE(),
                                IfCmp.getTarget(s),
                                IfCmp.getBranchProfile(s));
          return nextInstr;
        }
      }

      basic_long_ifcmp(s, ir, cond, xh, xl, yh, yl);
      return nextInstr;
    }
  }


  private static void basic_long_ifcmp(OPT_Instruction s, OPT_IR ir, 
                                       OPT_ConditionOperand cond, 
                                       OPT_Register xh, 
                                       OPT_Register xl, 
                                       OPT_Operand yh, 
                                       OPT_Operand yl) {
    if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
      OPT_RegisterOperand th = ir.regpool.makeTempInt();
      OPT_RegisterOperand tl = ir.regpool.makeTempInt();
      // tricky... ((xh^yh)|(xl^yl) == 0) <==> (lhll == rhrl)!!
      s.insertBefore(MIR_Move.create(IA32_MOV, th, new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
      s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, th.copyD2D(), yh));
      s.insertBefore(MIR_Move.create(IA32_MOV, tl, new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
      s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, tl.copyD2D(), yl));
      s.insertBefore(MIR_BinaryAcc.create(IA32_OR, th.copyD2D(), tl.copyD2U()));
      MIR_CondBranch.mutate(s, IA32_JCC, 
                            new OPT_IA32ConditionOperand(cond),
                            IfCmp.getTarget(s),
                            IfCmp.getBranchProfile(s));
    } else {
      // Do the naive thing and generate multiple compare/branch implementation.
      OPT_IA32ConditionOperand cond1;
      OPT_IA32ConditionOperand cond2;
      OPT_IA32ConditionOperand cond3;
      if (cond.isLESS()) {
        cond1 = OPT_IA32ConditionOperand.LT();
        cond2 = OPT_IA32ConditionOperand.GT();
        cond3 = OPT_IA32ConditionOperand.LLT();
      } else if (cond.isGREATER()) {
        cond1 = OPT_IA32ConditionOperand.GT();
        cond2 = OPT_IA32ConditionOperand.LT();
        cond3 = OPT_IA32ConditionOperand.LGT();
      } else if (cond.isLESS_EQUAL()) {
        cond1 = OPT_IA32ConditionOperand.LT();
        cond2 = OPT_IA32ConditionOperand.GT();
        cond3 = OPT_IA32ConditionOperand.LLE();
      } else if (cond.isGREATER_EQUAL()) {
        cond1 = OPT_IA32ConditionOperand.GT();
        cond2 = OPT_IA32ConditionOperand.LT();
        cond3 = OPT_IA32ConditionOperand.LGE();
      } else {
        // I don't think we use the unsigned compares for longs,
        // so defer actually implementing them until we find a test case. --dave
        cond1 = cond2 = cond3 = null;
        OPT_OptimizingCompilerException.TODO();
      }

      OPT_BasicBlock myBlock = s.getBasicBlock();
      OPT_BasicBlock test2Block = myBlock.createSubBlock(s.bcIndex, ir, 0.25f);
      OPT_BasicBlock falseBlock = myBlock.splitNodeAt(s, ir);
      OPT_BasicBlock trueBlock = IfCmp.getTarget(s).target.getBasicBlock();
      
      falseBlock.recomputeNormalOut(ir);
      myBlock.insertOut(test2Block);
      myBlock.insertOut(falseBlock);
      myBlock.insertOut(trueBlock);
      test2Block.insertOut(falseBlock);
      test2Block.insertOut(trueBlock);
      ir.cfg.linkInCodeOrder(myBlock, test2Block);
      ir.cfg.linkInCodeOrder(test2Block, falseBlock);
      
      s.remove();
      
      myBlock.appendInstruction(MIR_Compare.create(IA32_CMP, new OPT_RegisterOperand(xh, VM_TypeReference.Int), yh));
      myBlock.appendInstruction(MIR_CondBranch2.create(IA32_JCC2, 
                                                       cond1, trueBlock.makeJumpTarget(), new OPT_BranchProfileOperand(),
                                                       cond2, falseBlock.makeJumpTarget(), new OPT_BranchProfileOperand()));
      test2Block.appendInstruction(MIR_Compare.create(IA32_CMP, new OPT_RegisterOperand(xl, VM_TypeReference.Int), yl));
      test2Block.appendInstruction(MIR_CondBranch.create(IA32_JCC, cond3, trueBlock.makeJumpTarget(), new OPT_BranchProfileOperand()));
    }
  }


  // the fcmoi/fcmoip was generated by burs
  // we do the rest of the expansion here because in some
  // cases we must remove a trailing goto, and we 
  // can't do that in burs!
  private static OPT_Instruction fp_ifcmp(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    OPT_Operator op = s.operator();
    OPT_BranchOperand testFailed;
    OPT_BasicBlock bb = s.getBasicBlock();
    OPT_Instruction lastInstr = bb.lastRealInstruction();
    if (lastInstr.operator() == IA32_JMP) {
      // We're in trouble if there is another instruction between s and lastInstr!
      if (VM.VerifyAssertions) VM._assert(s.nextInstructionInCodeOrder() == lastInstr);
      // Set testFailed to target of GOTO
      testFailed = MIR_Branch.getTarget(lastInstr);
      nextInstr = lastInstr.nextInstructionInCodeOrder();
      lastInstr.remove();
    } else {
      // Set testFailed to label of next (fallthrough basic block)
      testFailed = bb.nextBasicBlockInCodeOrder().makeJumpTarget();
    }

    // Translate condition operand respecting IA32 FCOMI
	 OPT_Instruction fcomi = s.prevInstructionInCodeOrder();
	 OPT_Operand val1 = MIR_Compare.getVal1(fcomi);
	 OPT_Operand val2 = MIR_Compare.getVal2(fcomi);
    OPT_ConditionOperand c = IfCmp.getCond(s);
    OPT_BranchOperand target = IfCmp.getTarget(s);
    OPT_BranchProfileOperand branchProfile = IfCmp.getBranchProfile(s);

    // FCOMI sets ZF, PF, and CF as follows: 
    // Compare Results      ZF     PF      CF
    // left > right          0      0       0
    // left < right          0      0       1
    // left == right         1      0       0
    // UNORDERED             1      1       1

    // Propagate branch probabilities as follows: assume the
    // probability of unordered (first condition) is zero, and
    // propagate the original probability to the second condition.
    switch(c.value) {
      // Branches that WON'T be taken after unordered comparison
      // (i.e. UNORDERED is a goto to testFailed)
    case OPT_ConditionOperand.CMPL_EQUAL:
      if (VM.VerifyAssertions) VM._assert(!c.branchIfUnordered());
      // Check whether val1 and val2 operands are the same
      if (!val1.similar(val2)) {
        s.insertBefore(MIR_CondBranch2.create(IA32_JCC2, 
                                              OPT_IA32ConditionOperand.PE(),  // PF == 1
                                              testFailed,
                                              new OPT_BranchProfileOperand(0f),
                                              OPT_IA32ConditionOperand.EQ(),  // ZF == 1
                                              target,
                                              branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, (OPT_BranchOperand)(testFailed.copy())));
      }
      else {
        // As val1 == val2 result of compare must be == or UNORDERED
        s.insertBefore(MIR_CondBranch.create(IA32_JCC,
                                             OPT_IA32ConditionOperand.PO(),  // PF == 0
                                             target,
                                             branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
      }
      break;
    case OPT_ConditionOperand.CMPL_GREATER:
      if (VM.VerifyAssertions) VM._assert(!c.branchIfUnordered());
      s.insertBefore(MIR_CondBranch.create(IA32_JCC,
                                           OPT_IA32ConditionOperand.LGT(), // CF == 0 and ZF == 0
                                           target,
                                           branchProfile));
      s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
      break;
    case OPT_ConditionOperand.CMPG_LESS:
      if (VM.VerifyAssertions) VM._assert(!c.branchIfUnordered());
      s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                            OPT_IA32ConditionOperand.PE(),  // PF == 1
                                            testFailed,
                                            new OPT_BranchProfileOperand(0f),
                                            OPT_IA32ConditionOperand.LLT(), // CF == 1
                                            target,
                                            branchProfile));
      s.insertBefore(MIR_Branch.create(IA32_JMP, (OPT_BranchOperand)(testFailed.copy())));
      break;
    case OPT_ConditionOperand.CMPL_GREATER_EQUAL:
      if (VM.VerifyAssertions) VM._assert(!c.branchIfUnordered());
      s.insertBefore(MIR_CondBranch.create(IA32_JCC,
                                           OPT_IA32ConditionOperand.LGE(), // CF == 0
                                           target,
                                           branchProfile));
      s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
      break;
    case OPT_ConditionOperand.CMPG_LESS_EQUAL:
      s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                            OPT_IA32ConditionOperand.PE(),  // PF == 1
                                            testFailed,
                                            new OPT_BranchProfileOperand(0f),
                                            OPT_IA32ConditionOperand.LGT(), // ZF == 0 and CF == 0
                                            (OPT_BranchOperand)(testFailed.copy()),
                                            branchProfile));
      s.insertBefore(MIR_Branch.create(IA32_JMP, target));
      break;
      // Branches that WILL be taken after unordered comparison
      // (i.e. UNORDERED is a goto to target)
    case OPT_ConditionOperand.CMPL_NOT_EQUAL:
      if (VM.VerifyAssertions) VM._assert(c.branchIfUnordered());
      // Check whether val1 and val2 operands are the same
      if (!val1.similar(val2)) {
        s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                              OPT_IA32ConditionOperand.PE(),  // PF == 1
                                              target,
                                              new OPT_BranchProfileOperand(0f),
                                              OPT_IA32ConditionOperand.NE(),  // ZF == 0
                                              (OPT_BranchOperand)(target.copy()),
                                              branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
      }
      else {
        // As val1 == val2 result of compare must be == or UNORDERED
        s.insertBefore(MIR_CondBranch.create(IA32_JCC,
                                             OPT_IA32ConditionOperand.PE(),  // PF == 1
                                             target,
                                             branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
      }
      break;
    case OPT_ConditionOperand.CMPL_LESS:
      if (VM.VerifyAssertions) VM._assert(c.branchIfUnordered());
      s.insertBefore(MIR_CondBranch.create(IA32_JCC,
                                           OPT_IA32ConditionOperand.LLT(),   // CF == 1
                                           target,
                                           branchProfile));
      s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
      break;
    case OPT_ConditionOperand.CMPG_GREATER_EQUAL:
      if (VM.VerifyAssertions) VM._assert(c.branchIfUnordered());
      s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                            OPT_IA32ConditionOperand.PE(),  // PF == 1
                                            target,
                                            new OPT_BranchProfileOperand(0f),
                                            OPT_IA32ConditionOperand.LLT(), // CF == 1
                                            testFailed,
                                            branchProfile));
      s.insertBefore(MIR_Branch.create(IA32_JMP, (OPT_BranchOperand)(target.copy())));
      break;
    case OPT_ConditionOperand.CMPG_GREATER:
      if (VM.VerifyAssertions) VM._assert(c.branchIfUnordered());
      s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                            OPT_IA32ConditionOperand.PE(),  // PF == 1
                                            target,
                                            new OPT_BranchProfileOperand(0f),
                                            OPT_IA32ConditionOperand.LGT(), // ZF == 0 and CF == 0
                                            (OPT_BranchOperand)(target.copy()),
                                            branchProfile));
      s.insertBefore(MIR_Branch.create(IA32_JMP,testFailed));
      break;
    case OPT_ConditionOperand.CMPL_LESS_EQUAL:
      if (VM.VerifyAssertions) VM._assert(c.branchIfUnordered());
      s.insertBefore(MIR_CondBranch.create(IA32_JCC,
                                           OPT_IA32ConditionOperand.LLE(), // CF == 1 or ZF == 1
                                           target,
                                           branchProfile));
      s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
      break;
    default:
      OPT_OptimizingCompilerException.UNREACHABLE();
    }
    s.remove();
    return nextInstr;
  }
}

