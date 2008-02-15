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

import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.compilers.opt.ir.GenericRegisterPool;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.runtime.VM_Magic;
import org.vmmagic.unboxed.Address;

/**
 * Pool of symbolic registers.
 * Intel specific implementation where JTOC is stored in the processor object
 * and accessed through the processor register.
 *
 * @see Register
 */
public abstract class RegisterPool extends GenericRegisterPool implements Operators {

  /**
   * Initializes a new register pool for the method meth.
   *
   * @param meth the VM_Method of the outermost method
   */
  protected RegisterPool(VM_Method meth) {
    super(meth);
  }

  /**
   * Return a constant operand that is the base address of the JTOC.
   * TODO: This really should be returning an AddressConstantOperand,
   *       but that causes rippling changes in BURS that are larger
   *       than I want to deal with right now. --dave 12/20/2005.
   *
   * @param  ir  the containing IR
   * @param s    the instruction to insert the load operand before
   * @return a register operand that holds the JTOC
   */
  public Operand makeJTOCOp(IR ir, Instruction s) {
    Address jtoc = VM_Magic.getTocPointer();
    return new IntConstantOperand(jtoc.toInt());
  }
}
