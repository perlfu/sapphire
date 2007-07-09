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
import org.jikesrvm.compilers.opt.OPT_GenericPhysicalRegisterTools;
import org.jikesrvm.compilers.opt.OPT_OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FMOV;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_MOV;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_MOVSD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_MOVSS;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.ia32.VM_ArchConstants;

/**
 * This abstract class provides a set of useful methods for
 * manipulating physical registers for an IR.
 */
public abstract class OPT_PhysicalRegisterTools extends OPT_GenericPhysicalRegisterTools {

  /**
   * Return the governing IR.
   */
  public abstract OPT_IR getIR();

  /**
   * Create an MIR instruction to move rhs into lhs
   */
  public static OPT_Instruction makeMoveInstruction(OPT_RegisterOperand lhs, OPT_RegisterOperand rhs) {
    if (rhs.getRegister().isInteger() || rhs.getRegister().isLong() || rhs.getRegister().isAddress()) {
      if (VM.VerifyAssertions) {
        VM._assert(lhs.getRegister().isInteger() || lhs.getRegister().isLong() || lhs.getRegister().isAddress());
      }
      return MIR_Move.create(IA32_MOV, lhs, rhs);
    } else if (rhs.getRegister().isFloatingPoint()) {
      if (VM.VerifyAssertions) {
        VM._assert(lhs.getRegister().isFloatingPoint());
      }
      if (VM_ArchConstants.SSE2_FULL) {
        if (rhs.getRegister().isFloat()) {
          return MIR_Move.create(IA32_MOVSS, lhs, rhs);
        } else {
          return MIR_Move.create(IA32_MOVSD, lhs, rhs);
        }
      } else {
        return MIR_Move.create(IA32_FMOV, lhs, rhs);
      }
    } else {
      OPT_OptimizingCompilerException.TODO("OPT_PhysicalRegisterTools.makeMoveInstruction");
      return null;
    }
  }
}
