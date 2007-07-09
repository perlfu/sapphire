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
import org.jikesrvm.ArchitectureSpecific.OPT_PhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_IRTools;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;

/**
 * This abstract class provides a set of useful architecture-independent
 * methods for
 * manipulating physical registers for an IR.
 */
public abstract class OPT_GenericPhysicalRegisterTools extends OPT_IRTools {

  /**
   * Return the governing IR.
   */
  public abstract OPT_IR getIR();

  /**
   * Create an address register operand for a given physical GPR.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Load.create(INT_LOAD, I(2), A(1), IC(4)) ...
   * </pre>
   *
   * @param regnum the given GPR register number
   * @return integer register operand
   */
  protected final OPT_RegisterOperand A(int regnum) {
    OPT_PhysicalRegisterSet phys = getIR().regpool.getPhysicalRegisterSet();
    return A(phys.getGPR(regnum));
  }

  /**
   * Create an integer register operand for a given physical GPR.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Load.create(INT_LOAD, I(2), A(1), IC(4)) ...
   * </pre>
   *
   * @param regnum the given GPR register number
   * @return integer register operand
   */
  protected final OPT_RegisterOperand I(int regnum) {
    OPT_PhysicalRegisterSet phys = getIR().regpool.getPhysicalRegisterSet();
    return I(phys.getGPR(regnum));
  }

  /**
   * Create a float register operand for a given physical FPR.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Load.create(FLOAT_LOAD, F(2), A(1), IC(4)) ...
   * </pre>
   *
   * @param regnum the given DOUBLE register number
   * @return float register operand
   */
  final OPT_RegisterOperand F(int regnum) {
    OPT_PhysicalRegisterSet phys = getIR().regpool.getPhysicalRegisterSet();
    return F(phys.getFPR(regnum));
  }

  /**
   * Create a double register operand for a given physical FPR.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Load.create(DOUBLE_LOAD, D(2), A(1), IC(4)) ...
   * </pre>
   *
   * @param regnum the given double register number
   * @return double register operand
   */
  final OPT_RegisterOperand D(int regnum) {
    OPT_PhysicalRegisterSet phys = getIR().regpool.getPhysicalRegisterSet();
    return D(phys.getFPR(regnum));
  }

  /**
   * Create a long register operand for a given GPR number.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Load.create(LONG_LOAD, L(2), A(1), IC(4)) ...
   * </pre>
   *
   * @param regnum the given GPR register number
   * @return long register operand
   */
  final OPT_RegisterOperand L(int regnum) {
    OPT_PhysicalRegisterSet phys = getIR().regpool.getPhysicalRegisterSet();
    return L(phys.getGPR(regnum));
  }

  /**
   * Does instruction s have an operand that contains a physical register?
   */
  static boolean hasPhysicalOperand(OPT_Instruction s) {
    for (Enumeration<OPT_Operand> e = s.getOperands(); e.hasMoreElements();) {
      OPT_Operand op = e.nextElement();
      if (op == null) continue;
      if (op.isRegister()) {
        if (op.asRegister().getRegister().isPhysical()) {
          return true;
        }
      }
    }
    return false;
  }
}
