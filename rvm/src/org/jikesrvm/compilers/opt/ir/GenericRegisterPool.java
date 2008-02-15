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
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.ArchitectureSpecific.PhysicalRegisterSet;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.scheduler.VM_Scheduler;

/**
 * Pool of symbolic registers.
 * Each IR contains has exactly one register pool object associated with it.
 *
 * @see Register
 */
public class GenericRegisterPool extends AbstractRegisterPool {

  protected final PhysicalRegisterSet physical = new PhysicalRegisterSet();

  public PhysicalRegisterSet getPhysicalRegisterSet() {
    return physical;
  }

  /**
   * Initializes a new register pool for the method meth.
   *
   * @param meth the VM_Method of the outermost method
   */
  protected GenericRegisterPool(VM_Method meth) {
    // currentNum is assigned an initial value to avoid overlap of
    // physical and symbolic registers.
    currentNum = PhysicalRegisterSet.getSize();
  }

  /**
   * Return the number of symbolic registers (doesn't count physical ones)
   * @return the number of symbolic registers allocated by the pool
   */
  public int getNumberOfSymbolicRegisters() {
    int start = PhysicalRegisterSet.getSize();
    return currentNum - start;
  }

  /**
   * Get the Framepointer (FP)
   *
   * @return the FP register
   */
  public Register getFP() {
    return physical.getFP();
  }

  /**
   * Get a temporary that represents the FP register
   *
   * @return the temp
   */
  public RegisterOperand makeFPOp() {
    return new RegisterOperand(getFP(), VM_TypeReference.Address);
  }

  /**
   * Get a temporary that represents the PR register
   *
   * @return the temp
   */
  public RegisterOperand makePROp() {
    RegisterOperand prOp = new RegisterOperand(physical.getPR(), VM_Scheduler.getProcessorType());
    prOp.setPreciseType();
    return prOp;
  }

}
