/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt.ir;

import org.jikesrvm.ArchitectureSpecific.OPT_PhysicalRegisterSet;
import org.jikesrvm.classloader.*;

/**
 * Pool of symbolic registers.
 * Each IR contains has exactly one register pool object associated with it.
 * 
 * @see OPT_Register
 * 
 * @author Dave Grove
 * @author Mauricio J. Serrano
 * @author John Whaley
 * @modified Vivek Sarkar
 * @modified Peter Sweeney
 */
public class OPT_GenericRegisterPool extends OPT_AbstractRegisterPool {

  protected OPT_PhysicalRegisterSet physical = new OPT_PhysicalRegisterSet(); 

  public OPT_PhysicalRegisterSet getPhysicalRegisterSet() {
    return physical;
  }

  /**
   * Initializes a new register pool for the method meth.
   * 
   * @param meth the VM_Method of the outermost method
   */
  protected OPT_GenericRegisterPool(VM_Method meth) {
    // currentNum is assigned an initial value to avoid overlap of
    // physical and symbolic registers.
    currentNum = OPT_PhysicalRegisterSet.getSize();
  }

  /**
   * Return the number of symbolic registers (doesn't count physical ones)
   * @return the number of synbloic registers allocated by the pool
   */
  public int getNumberOfSymbolicRegisters() {
    int start = OPT_PhysicalRegisterSet.getSize();
    return currentNum - start;
  }

  /**
   * Get the Framepointer (FP)
   * 
   * @return the FP register
   */ 
  public OPT_Register getFP() {
    return physical.getFP();
  }

  /**
   * Get a temporary that represents the FP register
   * 
   * @return the temp
   */ 
  public OPT_RegisterOperand makeFPOp() {
    return new OPT_RegisterOperand(getFP(), VM_TypeReference.Address);
  }

  /**
   * Get a temporary that represents the PR register
   * 
   * @return the temp
   */ 
  public OPT_RegisterOperand makePROp() {
    return new OPT_RegisterOperand(physical.getPR(), VM_TypeReference.VM_Processor);
  }

}
