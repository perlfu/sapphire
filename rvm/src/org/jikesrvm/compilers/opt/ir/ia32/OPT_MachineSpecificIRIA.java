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
package org.jikesrvm.compilers.opt.ir.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.OPT_LiveIntervalElement;
import org.jikesrvm.compilers.opt.OPT_OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch2;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_MachineSpecificIR;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_Operator;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ADVISE_ESP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DUMMY_DEF;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DUMMY_USE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GET_CURRENT_PROCESSOR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GET_JTOC_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FCLEAR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FMOV;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FMOV_ENDING_LIVE_RANGE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FNINIT;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_JCC;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_2ADDR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_ADD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_AND_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_NEG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_OR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SHL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SUB_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_USHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_XOR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.NOP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PREFETCH_opcode;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;

/**
 * Wrappers around IA32-specific IR common to both 32 & 64 bit
 */
public abstract class OPT_MachineSpecificIRIA extends OPT_MachineSpecificIR {

  /**
   * Wrappers around IA32-specific IR (32-bit specific)
   */
  public static final class IA32 extends OPT_MachineSpecificIRIA {
    public static final IA32 singleton = new IA32();

    /* common to all ISAs */
    @Override
    public boolean mayEscapeThread(OPT_Instruction instruction) {
      switch (instruction.getOpcode()) {
        case PREFETCH_opcode:
          return false;
        case GET_JTOC_opcode:
        case GET_CURRENT_PROCESSOR_opcode:
          return true;
        default:
          throw new OPT_OptimizingCompilerException("OPT_SimpleEscapge: Unexpected " + instruction);
      }
    }

    @Override
    public boolean mayEscapeMethod(OPT_Instruction instruction) {
      return mayEscapeThread(instruction); // at this stage we're no more specific
    }
  }

  /**
   * Wrappers around EMT64-specific IR (64-bit specific)
   */
  public static final class EM64T extends OPT_MachineSpecificIRIA {
    public static final EM64T singleton = new EM64T();

    /* common to all ISAs */
    @Override
    public boolean mayEscapeThread(OPT_Instruction instruction) {
      switch (instruction.getOpcode()) {
        case PREFETCH_opcode:
          return false;
        case GET_JTOC_opcode:
        case GET_CURRENT_PROCESSOR_opcode:
        case LONG_OR_opcode:
        case LONG_AND_opcode:
        case LONG_XOR_opcode:
        case LONG_SUB_opcode:
        case LONG_SHL_opcode:
        case LONG_ADD_opcode:
        case LONG_SHR_opcode:
        case LONG_USHR_opcode:
        case LONG_NEG_opcode:
        case LONG_MOVE_opcode:
        case LONG_2ADDR_opcode:
          return true;
        default:
          throw new OPT_OptimizingCompilerException("OPT_SimpleEscapge: Unexpected " + instruction);
      }
    }

    @Override
    public boolean mayEscapeMethod(OPT_Instruction instruction) {
      return mayEscapeThread(instruction); // at this stage we're no more specific
    }
  }

  /*
  * Generic (32/64 neutral) IA support
  */

  /* common to all ISAs */

  @Override
  public boolean isConditionOperand(OPT_Operand operand) {
    return operand instanceof OPT_IA32ConditionOperand;
  }

  @Override
  public void mutateMIRCondBranch(OPT_Instruction cb) {
    MIR_CondBranch.mutate(cb,
                          IA32_JCC,
                          MIR_CondBranch2.getCond1(cb),
                          MIR_CondBranch2.getTarget1(cb),
                          MIR_CondBranch2.getBranchProfile1(cb));
  }

  @Override
  public boolean isHandledByRegisterUnknown(char opcode) {
    return (opcode == PREFETCH_opcode);
  }

  /* unique to IA */
  @Override
  public boolean isAdviseESP(OPT_Operator operator) {
    return operator == ADVISE_ESP;
  }

  @Override
  public boolean isFClear(OPT_Operator operator) {
    return operator == IA32_FCLEAR;
  }

  @Override
  public boolean isFNInit(OPT_Operator operator) {
    return operator == IA32_FNINIT;
  }

  @Override
  public boolean isBURSManagedFPROperand(OPT_Operand operand) {
    return operand instanceof OPT_BURSManagedFPROperand;
  }

  @Override
  public int getBURSManagedFPRValue(OPT_Operand operand) {
    return ((OPT_BURSManagedFPROperand) operand).regNum;
  }

  /**
   * Mutate FMOVs that end live ranges
   *
   * @param live The live interval for a basic block/reg pair
   * @param register The register for this live interval
   * @param dfnbegin The (adjusted) begin for this interval
   * @param dfnend The (adjusted) end for this interval
   */
  @Override
  public boolean mutateFMOVs(OPT_LiveIntervalElement live, OPT_Register register, int dfnbegin, int dfnend) {
    OPT_Instruction end = live.getEnd();
    if (end != null && end.operator == IA32_FMOV) {
      if (dfnend == dfnbegin) {
        // if end, an FMOV, both begins and ends the live range,
        // then end is dead.  Change it to a NOP and return null.
        Empty.mutate(end, NOP);
        return false;
      } else {
        if (!end.isPEI()) {
          if (VM.VerifyAssertions) {
            OPT_Operand value = MIR_Move.getValue(end);
            VM._assert(value.isRegister());
            VM._assert(MIR_Move.getValue(end).asRegister().register == register);
          }
          end.operator = IA32_FMOV_ENDING_LIVE_RANGE;
        }
      }
    }
    return true;
  }

  /**
   *  Rewrite floating point registers to reflect changes in stack
   *  height induced by BURS.
   *
   *  Side effect: update the fpStackHeight in MIRInfo
   */
  public void rewriteFPStack(OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    for (OPT_BasicBlockEnumeration b = ir.getBasicBlocks(); b.hasMoreElements();) {
      OPT_BasicBlock bb = b.nextElement();

      // The following holds the floating point stack offset from its
      // 'normal' position.
      int fpStackOffset = 0;

      for (OPT_InstructionEnumeration inst = bb.forwardInstrEnumerator(); inst.hasMoreElements();) {
        OPT_Instruction s = inst.next();
        for (OPT_OperandEnumeration ops = s.getOperands(); ops.hasMoreElements();) {
          OPT_Operand op = ops.next();
          if (op.isRegister()) {
            OPT_RegisterOperand rop = op.asRegister();
            OPT_Register r = rop.register;

            // Update MIR state for every phyiscal FPR we see
            if (r.isPhysical() && r.isFloatingPoint() && s.operator() != DUMMY_DEF && s.operator() != DUMMY_USE) {
              int n = OPT_PhysicalRegisterSet.getFPRIndex(r);
              if (fpStackOffset != 0) {
                n += fpStackOffset;
                rop.register = phys.getFPR(n);
              }
              ir.MIRInfo.fpStackHeight = Math.max(ir.MIRInfo.fpStackHeight, n + 1);
            }
          } else if (op instanceof OPT_BURSManagedFPROperand) {
            int regNum = ((OPT_BURSManagedFPROperand) op).regNum;
            s.replaceOperand(op, new OPT_RegisterOperand(phys.getFPR(regNum), VM_TypeReference.Double));
          }
        }
        // account for any effect s has on the floating point stack
        // position.
        if (s.operator().isFpPop()) {
          fpStackOffset--;
        } else if (s.operator().isFpPush()) {
          fpStackOffset++;
        }
        if (VM.VerifyAssertions) VM._assert(fpStackOffset >= 0);
      }
    }
  }
}
