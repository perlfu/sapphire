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
package org.jikesrvm.compilers.opt.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.ir.BBend;
import org.jikesrvm.compilers.opt.ir.MIR_BinaryAcc;
import org.jikesrvm.compilers.opt.ir.MIR_Branch;
import org.jikesrvm.compilers.opt.ir.MIR_Call;
import org.jikesrvm.compilers.opt.ir.MIR_CaseLabel;
import org.jikesrvm.compilers.opt.ir.MIR_Compare;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch2;
import org.jikesrvm.compilers.opt.ir.MIR_Empty;
import org.jikesrvm.compilers.opt.ir.MIR_Lea;
import org.jikesrvm.compilers.opt.ir.MIR_LowTableSwitch;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.MIR_Nullary;
import org.jikesrvm.compilers.opt.ir.MIR_Return;
import org.jikesrvm.compilers.opt.ir.MIR_Test;
import org.jikesrvm.compilers.opt.ir.MIR_Trap;
import org.jikesrvm.compilers.opt.ir.MIR_TrapIf;
import org.jikesrvm.compilers.opt.ir.MIR_UnaryNoRes;
import org.jikesrvm.compilers.opt.ir.MIR_XChng;
import org.jikesrvm.compilers.opt.ir.NullCheck;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_IRTools;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_LocationOperand;
import org.jikesrvm.compilers.opt.ir.OPT_MemoryOperand;
import org.jikesrvm.compilers.opt.ir.OPT_MethodOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ADVISE_ESP_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CALL_SAVE_VOLATILE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CALL_SAVE_VOLATILE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DUMMY_DEF_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DUMMY_USE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_ADD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_CALL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_CMP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_CMPXCHG;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_CMPXCHG8B;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FCLEAR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FFREE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FLD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FMOV_ENDING_LIVE_RANGE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FMOV_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FST;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FSTP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FXCH;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_INT;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_JCC;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_JCC2_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_JMP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_LEA_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_LOCK;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_LOCK_CMPXCHG8B_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_LOCK_CMPXCHG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_MOV;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_MOV_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_OFFSET;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_RET;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_SHL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_TEST_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_TRAPIF;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_TRAPIF_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_XOR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.MIR_LOWTABLESWITCH_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.NULL_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REQUIRE_ESP_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.YIELDPOINT_BACKEDGE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.YIELDPOINT_EPILOGUE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.YIELDPOINT_OSR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.YIELDPOINT_PROLOGUE_opcode;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.OPT_TrapCodeOperand;
import org.jikesrvm.compilers.opt.ir.ia32.OPT_IA32ConditionOperand;
import org.jikesrvm.compilers.opt.ir.ia32.OPT_PhysicalDefUse;
import org.jikesrvm.compilers.opt.ir.ia32.OPT_PhysicalRegisterSet;
import org.jikesrvm.runtime.VM_ArchEntrypoints;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.runtime.VM_Magic;
import org.vmmagic.unboxed.Offset;

/**
 * Final acts of MIR expansion for the IA32 architecture.
 * Things that are expanded here (immediately before final assembly)
 * should only be those sequences that cannot be expanded earlier
 * due to difficulty in keeping optimizations from interfering with them.
 *
 * One job of this phase is to handle the expansion of the remains of
 * table switch.  The code looks like a mess (which it is), but there
 * is little choice for relocatable IA32 code that does this.  And the
 * details of this code are shared with the baseline compiler and
 * dependent in detail on the VM_Assembler (see {@link
 * org.jikesrvm.compilers.common.assembler.ia32.VM_Assembler#emitOFFSET_Imm_ImmOrLabel}).  If you want to mess with
 * it, you will probably need to mess with them as well.
 */
public class OPT_FinalMIRExpansion extends OPT_IRTools {

  /**
   * @param ir the IR to expand
   * @return return value is garbage for IA32
   */
  public static int expand(OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    for (OPT_Instruction next, p = ir.firstInstructionInCodeOrder(); p != null; p = next) {
      next = p.nextInstructionInCodeOrder();
      p.setmcOffset(-1);
      p.scratchObject = null;

      switch (p.getOpcode()) {
        case MIR_LOWTABLESWITCH_opcode: {
          // split the basic block after the MIR_LOWTABLESWITCH
          OPT_BasicBlock thisBlock = p.getBasicBlock();
          OPT_BasicBlock nextBlock = thisBlock.splitNodeWithLinksAt(p, ir);
          nextBlock.firstInstruction().setmcOffset(-1);

          // place offset data table after call so that call pushes
          // the base address of this table onto the stack
          int NumTargets = MIR_LowTableSwitch.getNumberOfTargets(p);
          for (int i = 0; i < NumTargets; i++) {
            thisBlock.appendInstruction(MIR_CaseLabel.create(IA32_OFFSET,
                                                             IC(i),
                                                             MIR_LowTableSwitch.getClearTarget(p, i)));
          }
          // calculate address to which to jump, and store it
          // on the top of the stack
          OPT_Register regS = MIR_LowTableSwitch.getIndex(p).getRegister();
          nextBlock.appendInstruction(MIR_BinaryAcc.create(IA32_SHL,
                                                           new OPT_RegisterOperand(regS, VM_TypeReference.Int),
                                                           IC(2)));
          nextBlock.appendInstruction(MIR_BinaryAcc.create(IA32_ADD,
                                                           new OPT_RegisterOperand(regS, VM_TypeReference.Int),
                                                           OPT_MemoryOperand.I(new OPT_RegisterOperand(phys.getESP(),
                                                                                                       VM_TypeReference.Int),
                                                                               (byte) 4,
                                                                               null,
                                                                               null)));
          nextBlock.appendInstruction(MIR_Move.create(IA32_MOV,
                                                      new OPT_RegisterOperand(regS, VM_TypeReference.Int),
                                                      OPT_MemoryOperand.I(new OPT_RegisterOperand(regS,
                                                                                                  VM_TypeReference.Int),
                                                                          (byte) 4,
                                                                          null,
                                                                          null)));
          nextBlock.appendInstruction(MIR_BinaryAcc.create(IA32_ADD,
                                                           OPT_MemoryOperand.I(new OPT_RegisterOperand(phys.getESP(),
                                                                                                       VM_TypeReference.Int),
                                                                               (byte) 4,
                                                                               null,
                                                                               null),
                                                           new OPT_RegisterOperand(regS, VM_TypeReference.Int)));
          // ``return'' to mangled return address
          nextBlock.appendInstruction(MIR_Return.create(IA32_RET, IC(0), null, null));

          // CALL next block to push pc of next ``instruction'' onto stack
          MIR_Call.mutate0(p, IA32_CALL, null, null, nextBlock.makeJumpTarget(), null);
        }
        break;

        case IA32_TEST_opcode:
          // don't bother telling rest of compiler that memory operand
          // must be first; we can just commute it here.
          if (MIR_Test.getVal2(p).isMemory()) {
            OPT_Operand tmp = MIR_Test.getClearVal1(p);
            MIR_Test.setVal1(p, MIR_Test.getClearVal2(p));
            MIR_Test.setVal2(p, tmp);
          }
          break;

        case NULL_CHECK_opcode: {
          // mutate this into a TRAPIF, and then fall through to the the
          // TRAP_IF case.
          OPT_Operand ref = NullCheck.getRef(p);
          MIR_TrapIf.mutate(p,
                            IA32_TRAPIF,
                            null,
                            ref.copy(),
                            IC(0),
                            OPT_IA32ConditionOperand.EQ(),
                            OPT_TrapCodeOperand.NullPtr());
        }
        // There is no break statement here on purpose!
        case IA32_TRAPIF_opcode: {
          // split the basic block right before the IA32_TRAPIF
          OPT_BasicBlock thisBlock = p.getBasicBlock();
          OPT_BasicBlock trap = thisBlock.createSubBlock(p.bcIndex, ir, 0f);
          thisBlock.insertOut(trap);
          OPT_BasicBlock nextBlock = thisBlock.splitNodeWithLinksAt(p, ir);
          thisBlock.insertOut(trap);
          OPT_TrapCodeOperand tc = MIR_TrapIf.getClearTrapCode(p);
          p.remove();
          nextBlock.firstInstruction().setmcOffset(-1);
          // add code to thisBlock to conditionally jump to trap
          OPT_Instruction cmp = MIR_Compare.create(IA32_CMP, MIR_TrapIf.getVal1(p), MIR_TrapIf.getVal2(p));
          if (p.isMarkedAsPEI()) {
            // The trap if was explictly marked, which means that it has
            // a memory operand into which we've folded a null check.
            // Actually need a GC map for both the compare and the INT.
            cmp.markAsPEI();
            cmp.copyPosition(p);
            ir.MIRInfo.gcIRMap.insertTwin(p, cmp);
          }
          thisBlock.appendInstruction(cmp);
          thisBlock.appendInstruction(MIR_CondBranch.create(IA32_JCC,
                                                            MIR_TrapIf.getCond(p),
                                                            trap.makeJumpTarget(),
                                                            null));

          // add block at end to hold trap instruction, and
          // insert trap sequence
          ir.cfg.addLastInCodeOrder(trap);
          if (tc.isArrayBounds()) {
            // attempt to store index expression in processor object for
            // C trap handler
            OPT_Operand index = MIR_TrapIf.getVal2(p);
            if (!(index instanceof OPT_RegisterOperand || index instanceof OPT_IntConstantOperand)) {
              index = IC(0xdeadbeef); // index was spilled, and
              // we can't get it back here.
            }
            OPT_MemoryOperand mo =
                OPT_MemoryOperand.BD(ir.regpool.makePROp(),
                                     VM_ArchEntrypoints.arrayIndexTrapParamField.getOffset(),
                                     (byte) 4,
                                     null,
                                     null);
            trap.appendInstruction(MIR_Move.create(IA32_MOV, mo, index.copy()));
          }
          // NOTE: must make p the trap instruction: it is the GC point!
          // IMPORTANT: must also inform the GCMap that the instruction has
          // been moved!!!
          trap.appendInstruction(MIR_Trap.mutate(p, IA32_INT, null, tc));
          ir.MIRInfo.gcIRMap.moveToEnd(p);

          if (tc.isStackOverflow()) {
            // only stackoverflow traps resume at next instruction.
            trap.appendInstruction(MIR_Branch.create(IA32_JMP, nextBlock.makeJumpTarget()));
          }
        }
        break;

        case IA32_FMOV_ENDING_LIVE_RANGE_opcode: {
          OPT_Operand result = MIR_Move.getResult(p);
          OPT_Operand value = MIR_Move.getValue(p);
          if (result.isRegister() && value.isRegister()) {
            if (result.similar(value)) {
              // eliminate useless move
              p.remove();
            } else {
              int i = OPT_PhysicalRegisterSet.getFPRIndex(result.asRegister().getRegister());
              int j = OPT_PhysicalRegisterSet.getFPRIndex(value.asRegister().getRegister());
              if (i == 0) {
                MIR_XChng.mutate(p, IA32_FXCH, result, value);
              } else if (j == 0) {
                MIR_XChng.mutate(p, IA32_FXCH, value, result);
              } else {
                expandFmov(p, phys);
              }
            }
          } else {
            expandFmov(p, phys);
          }
          break;
        }

        case DUMMY_DEF_opcode:
        case DUMMY_USE_opcode:
        case REQUIRE_ESP_opcode:
        case ADVISE_ESP_opcode:
          p.remove();
          break;

        case IA32_FMOV_opcode:
          expandFmov(p, phys);
          break;

        case IA32_MOV_opcode:
          // Replace result = IA32_MOV 0 with result = IA32_XOR result, result
          if (MIR_Move.getResult(p).isRegister() &&
              MIR_Move.getValue(p).isIntConstant() &&
              MIR_Move.getValue(p).asIntConstant().value == 0) {
            // Calculate what flags are defined in coming instructions before a use of a flag or BBend
            OPT_Instruction x = next;
            int futureDefs = 0;
            while(!BBend.conforms(x) && !OPT_PhysicalDefUse.usesEFLAGS(x.operator)) {
              futureDefs |= x.operator.implicitDefs;
              x = x.nextInstructionInCodeOrder();
            }
            // If the flags will be destroyed prior to use or we reached the end of the basic block
            if (BBend.conforms(x) ||
                (futureDefs & OPT_PhysicalDefUse.maskAF_CF_OF_PF_SF_ZF) == OPT_PhysicalDefUse.maskAF_CF_OF_PF_SF_ZF) {
              OPT_Operand result = MIR_Move.getClearResult(p);
              MIR_BinaryAcc.mutate(p, IA32_XOR, result, result.copy());
            }
          }
          break;

        case IA32_LEA_opcode: {
          // Sometimes we're over eager in BURS in using LEAs and after register
          // allocation we can simplify to the accumulate form
          // replace reg1 = LEA [reg1 + reg2] with reg1 = reg1 + reg2
          // replace reg1 = LEA [reg1 + c1] with reg1 = reg1 + c1
          // replace reg1 = LEA [reg1 << c1] with reg1 = reg1 << c1
          OPT_MemoryOperand value = MIR_Lea.getValue(p);
          OPT_RegisterOperand result = MIR_Lea.getResult(p);
          if ((value.base != null && value.base.getRegister() == result.getRegister()) ||
              (value.index != null && value.index.getRegister() == result.getRegister())) {
            // Calculate what flags are defined in coming instructions before a use of a flag or BBend
            OPT_Instruction x = next;
            int futureDefs = 0;
            while(!BBend.conforms(x) && !OPT_PhysicalDefUse.usesEFLAGS(x.operator)) {
              futureDefs |= x.operator.implicitDefs;
              x = x.nextInstructionInCodeOrder();
            }
            // If the flags will be destroyed prior to use or we reached the end of the basic block
            if (BBend.conforms(x) ||
                (futureDefs & OPT_PhysicalDefUse.maskAF_CF_OF_PF_SF_ZF) == OPT_PhysicalDefUse.maskAF_CF_OF_PF_SF_ZF) {
              if (value.base != null &&
                  value.index != null && value.index.getRegister() == result.getRegister() &&
                  value.disp.isZero() &&
                  value.scale == 0) {
                // reg1 = lea [base + reg1] -> add reg1, base
                MIR_BinaryAcc.mutate(p, IA32_ADD, result, value.base);
              } else if (value.base != null && value.base.getRegister() == result.getRegister() &&
                         value.index != null &&
                         value.disp.isZero() &&
                         value.scale == 0) {
                // reg1 = lea [reg1 + index] -> add reg1, index
                MIR_BinaryAcc.mutate(p, IA32_ADD, result, value.index);
              } else if (value.base != null && value.base.getRegister() == result.getRegister() &&
                         value.index == null) {
                // reg1 = lea [reg1 + disp] -> add reg1, disp
                MIR_BinaryAcc.mutate(p, IA32_ADD, result, IC(value.disp.toInt()));
              } else if (value.base == null &&
                         value.index == null && value.index.getRegister() == result.getRegister() &&
                         value.scale == 0) {
                // reg1 = lea [reg1 + disp] -> add reg1, disp
                MIR_BinaryAcc.mutate(p, IA32_ADD, result, IC(value.disp.toInt()));
              } else if (value.base == null &&
                         value.index == null && value.index.getRegister() == result.getRegister() &&
                         value.disp.isZero()) {
                // reg1 = lea [reg1 << scale] -> shl reg1, scale
                MIR_BinaryAcc.mutate(p, IA32_SHL, result, IC(value.scale));
              }
            }
          }
        }
        break;

        case IA32_FCLEAR_opcode:
          expandFClear(p, ir);
          break;

        case IA32_JCC2_opcode:
          p.insertBefore(MIR_CondBranch.create(IA32_JCC,
                                               MIR_CondBranch2.getCond1(p),
                                               MIR_CondBranch2.getTarget1(p),
                                               MIR_CondBranch2.getBranchProfile1(p)));
          MIR_CondBranch.mutate(p,
                                IA32_JCC,
                                MIR_CondBranch2.getCond2(p),
                                MIR_CondBranch2.getTarget2(p),
                                MIR_CondBranch2.getBranchProfile2(p));
          break;

        case CALL_SAVE_VOLATILE_opcode:
          p.operator = IA32_CALL;
          break;

        case IA32_LOCK_CMPXCHG_opcode:
          p.insertBefore(MIR_Empty.create(IA32_LOCK));
          p.operator = IA32_CMPXCHG;
          break;

        case IA32_LOCK_CMPXCHG8B_opcode:
          p.insertBefore(MIR_Empty.create(IA32_LOCK));
          p.operator = IA32_CMPXCHG8B;
          break;

        case YIELDPOINT_PROLOGUE_opcode:
          expandYieldpoint(p, ir, VM_Entrypoints.optThreadSwitchFromPrologueMethod, OPT_IA32ConditionOperand.NE());
          break;

        case YIELDPOINT_EPILOGUE_opcode:
          expandYieldpoint(p, ir, VM_Entrypoints.optThreadSwitchFromEpilogueMethod, OPT_IA32ConditionOperand.NE());
          break;

        case YIELDPOINT_BACKEDGE_opcode:
          expandYieldpoint(p, ir, VM_Entrypoints.optThreadSwitchFromBackedgeMethod, OPT_IA32ConditionOperand.GT());
          break;

        case YIELDPOINT_OSR_opcode:
          // must yield, does not check threadSwitch request
          expandUnconditionalYieldpoint(p, ir, VM_Entrypoints.optThreadSwitchFromOsrOptMethod);
          break;

      }
    }
    return 0;
  }

  /**
   * expand an FCLEAR pseudo-insruction using FFREEs.
   *
   * @param s the instruction to expand
   * @param ir the containing IR
   */
  private static void expandFClear(OPT_Instruction s, OPT_IR ir) {
    int nSave = MIR_UnaryNoRes.getVal(s).asIntConstant().value;
    int fpStackHeight = ir.MIRInfo.fpStackHeight;
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    for (int i = nSave; i < fpStackHeight; i++) {
      OPT_Register f = phys.getFPR(i);
      s.insertBefore(MIR_Nullary.create(IA32_FFREE, D(f)));
    }

    // Remove the FCLEAR.
    s.remove();
  }

  /**
   * expand an FMOV pseudo-insruction.
   *
   * @param s the instruction to expand
   * @param phys controlling physical register set
   */
  private static void expandFmov(OPT_Instruction s, OPT_PhysicalRegisterSet phys) {
    OPT_Operand result = MIR_Move.getResult(s);
    OPT_Operand value = MIR_Move.getValue(s);

    if (result.isRegister() && value.isRegister()) {
      if (result.similar(value)) {
        // eliminate useless move
        s.remove();
      } else {
        int i = OPT_PhysicalRegisterSet.getFPRIndex(result.asRegister().getRegister());
        int j = OPT_PhysicalRegisterSet.getFPRIndex(value.asRegister().getRegister());
        if (j == 0) {
          // We have FMOV Fi, F0
          // Expand as:
          //        FST F(i)  (copy F0 to F(i))
          MIR_Move.mutate(s, IA32_FST, D(phys.getFPR(i)), D(phys.getFPR(0)));
        } else {
          // We have FMOV Fi, Fj
          // Expand as:
          //        FLD Fj  (push Fj on FP stack).
          //        FSTP F(i+1)  (copy F0 to F(i+1) and then pop register stack)
          s.insertBefore(MIR_Move.create(IA32_FLD, D(phys.getFPR(0)), value));

          MIR_Move.mutate(s, IA32_FSTP, D(phys.getFPR(i + 1)), D(phys.getFPR(0)));
        }

      }
    } else if (value instanceof OPT_MemoryOperand) {
      if (result instanceof OPT_MemoryOperand) {
        // We have FMOV M1, M2
        // Expand as:
        //        FLD M1   (push M1 on FP stack).
        //        FSTP M2  (copy F0 to M2 and pop register stack)
        s.insertBefore(MIR_Move.create(IA32_FLD, D(phys.getFPR(0)), value));
        MIR_Move.mutate(s, IA32_FSTP, result, D(phys.getFPR(0)));
      } else {
        // We have FMOV Fi, M
        // Expand as:
        //        FLD M    (push M on FP stack).
        //        FSTP F(i+1)  (copy F0 to F(i+1) and pop register stack)
        if (VM.VerifyAssertions) VM._assert(result.isRegister());
        int i = OPT_PhysicalRegisterSet.getFPRIndex(result.asRegister().getRegister());
        s.insertBefore(MIR_Move.create(IA32_FLD, D(phys.getFPR(0)), value));
        MIR_Move.mutate(s, IA32_FSTP, D(phys.getFPR(i + 1)), D(phys.getFPR(0)));
      }
    } else {
      // We have FMOV M, Fi
      if (VM.VerifyAssertions) VM._assert(value.isRegister());
      if (VM.VerifyAssertions) {
        VM._assert(result instanceof OPT_MemoryOperand);
      }
      int i = OPT_PhysicalRegisterSet.getFPRIndex(value.asRegister().getRegister());
      if (i != 0) {
        // Expand as:
        //        FLD Fi    (push Fi on FP stack).
        //        FSTP M    (store F0 in M and pop register stack);
        s.insertBefore(MIR_Move.create(IA32_FLD, D(phys.getFPR(0)), value));
        MIR_Move.mutate(s, IA32_FSTP, result, D(phys.getFPR(0)));
      } else {
        // Expand as:
        //        FST M    (store F0 in M);
        MIR_Move.mutate(s, IA32_FST, result, value);
      }
    }
  }

  private static void expandYieldpoint(OPT_Instruction s, OPT_IR ir, VM_Method meth, OPT_IA32ConditionOperand ypCond) {
    // split the basic block after the yieldpoint, create a new
    // block at the end of the IR to hold the yieldpoint,
    // remove the yieldpoint (to prepare to out it in the new block at the end)
    OPT_BasicBlock thisBlock = s.getBasicBlock();
    OPT_BasicBlock nextBlock = thisBlock.splitNodeWithLinksAt(s, ir);
    OPT_BasicBlock yieldpoint = thisBlock.createSubBlock(s.bcIndex, ir, 0);
    thisBlock.insertOut(yieldpoint);
    yieldpoint.insertOut(nextBlock);
    ir.cfg.addLastInCodeOrder(yieldpoint);
    s.remove();

    // change thread switch instruction into call to thread switch routine
    // NOTE: must make s the call instruction: it is the GC point!
    //       must also inform the GCMap that s has been moved!!!
    Offset offset = meth.getOffset();
    OPT_LocationOperand loc = new OPT_LocationOperand(offset);
    OPT_Operand guard = TG();
    OPT_Operand target = OPT_MemoryOperand.D(VM_Magic.getTocPointer().plus(offset), (byte) 4, loc, guard);
    MIR_Call.mutate0(s, CALL_SAVE_VOLATILE, null, null, target, OPT_MethodOperand.STATIC(meth));
    yieldpoint.appendInstruction(s);
    ir.MIRInfo.gcIRMap.moveToEnd(s);

    yieldpoint.appendInstruction(MIR_Branch.create(IA32_JMP, nextBlock.makeJumpTarget()));

    // Check to see if threadSwitch requested
    Offset tsr = VM_Entrypoints.takeYieldpointField.getOffset();
    OPT_MemoryOperand M =
        OPT_MemoryOperand.BD(ir.regpool.makePROp(), tsr, (byte) 4, null, null);
    thisBlock.appendInstruction(MIR_Compare.create(IA32_CMP, M, IC(0)));
    thisBlock.appendInstruction(MIR_CondBranch.create(IA32_JCC,
                                                      ypCond,
                                                      yieldpoint.makeJumpTarget(),
                                                      OPT_BranchProfileOperand.never()));
  }

  /* generate yieldpoint without checking threadSwith request
   */
  private static void expandUnconditionalYieldpoint(OPT_Instruction s, OPT_IR ir, VM_Method meth) {
    // split the basic block after the yieldpoint, create a new
    // block at the end of the IR to hold the yieldpoint,
    // remove the yieldpoint (to prepare to out it in the new block at the end)
    OPT_BasicBlock thisBlock = s.getBasicBlock();
    OPT_BasicBlock nextBlock = thisBlock.splitNodeWithLinksAt(s, ir);
    OPT_BasicBlock yieldpoint = thisBlock.createSubBlock(s.bcIndex, ir);
    thisBlock.insertOut(yieldpoint);
    yieldpoint.insertOut(nextBlock);
    ir.cfg.addLastInCodeOrder(yieldpoint);
    s.remove();

    // change thread switch instruction into call to thread switch routine
    // NOTE: must make s the call instruction: it is the GC point!
    //       must also inform the GCMap that s has been moved!!!
    Offset offset = meth.getOffset();
    OPT_LocationOperand loc = new OPT_LocationOperand(offset);
    OPT_Operand guard = TG();
    OPT_Operand target = OPT_MemoryOperand.D(VM_Magic.getTocPointer().plus(offset), (byte) 4, loc, guard);
    MIR_Call.mutate0(s, CALL_SAVE_VOLATILE, null, null, target, OPT_MethodOperand.STATIC(meth));
    yieldpoint.appendInstruction(s);
    ir.MIRInfo.gcIRMap.moveToEnd(s);

    yieldpoint.appendInstruction(MIR_Branch.create(IA32_JMP, nextBlock.makeJumpTarget()));

    // make a jump to yield block
    thisBlock.appendInstruction(MIR_Branch.create(IA32_JMP, yieldpoint.makeJumpTarget()));
  }
}
