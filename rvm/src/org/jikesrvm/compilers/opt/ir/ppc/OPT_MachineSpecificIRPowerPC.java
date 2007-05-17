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
package org.jikesrvm.compilers.opt.ir.ppc;

import org.jikesrvm.compilers.opt.OPT_OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch2;
import org.jikesrvm.compilers.opt.ir.MIR_Load;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_MachineSpecificIR;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DCBST_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DCBTST_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DCBT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DCBZL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DCBZ_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ICBI_opcode;
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
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_BCOND;

/**
 * Wrappers around PowerPC-specific IR common to both 32 & 64 bit
 */
public abstract class OPT_MachineSpecificIRPowerPC extends OPT_MachineSpecificIR {
  /**
   * Wrappers around 32-bit PowerPC-specific IR
   */
  public static final class PPC32 extends OPT_MachineSpecificIRPowerPC {
    public static final PPC32 singleton = new PPC32();

    @Override
    public boolean mayEscapeThread(OPT_Instruction instruction) {
      switch (instruction.getOpcode()) {
        case DCBST_opcode:
        case DCBT_opcode:
        case DCBTST_opcode:
        case DCBZ_opcode:
        case DCBZL_opcode:
        case ICBI_opcode:
          return false;
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
   * Wrappers around 64-bit PowerPC-specific IR
   */
  public static final class PPC64 extends OPT_MachineSpecificIRPowerPC {
    public static final PPC64 singleton = new PPC64();

    @Override
    public boolean mayEscapeThread(OPT_Instruction instruction) {
      switch (instruction.getOpcode()) {
        case DCBST_opcode:
        case DCBT_opcode:
        case DCBTST_opcode:
        case DCBZ_opcode:
        case DCBZL_opcode:
        case ICBI_opcode:
          return false;
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
  * Generic (32/64 neutral) PowerPC support
  */

  /* common to all ISAs */

  @Override
  public final boolean isConditionOperand(OPT_Operand operand) {
    return operand instanceof OPT_PowerPCConditionOperand;
  }

  @Override
  public final void mutateMIRCondBranch(OPT_Instruction cb) {
    MIR_CondBranch.mutate(cb,
                          PPC_BCOND,
                          MIR_CondBranch2.getValue(cb),
                          MIR_CondBranch2.getCond1(cb),
                          MIR_CondBranch2.getTarget1(cb),
                          MIR_CondBranch2.getBranchProfile1(cb));
  }

  @Override
  public final boolean isHandledByRegisterUnknown(char opcode) {
    switch (opcode) {
      case DCBST_opcode:
      case DCBT_opcode:
      case DCBTST_opcode:
      case DCBZ_opcode:
      case DCBZL_opcode:
      case ICBI_opcode:
        return true;
      default:
        return false;
    }
  }

  /* unique to PowerPC */
  @Override
  public final boolean isPowerPCTrapOperand(OPT_Operand operand) {
    return operand instanceof OPT_PowerPCConditionOperand;
  }

  @Override
  public final boolean canFoldNullCheckAndLoad(OPT_Instruction s) {
    if (MIR_Load.conforms(s)) {
      OPT_Operand offset = MIR_Load.getOffset(s);
      if (offset instanceof OPT_IntConstantOperand) {
        return ((OPT_IntConstantOperand) offset).value < 0;
      }
    }
    return false;
  }
}
