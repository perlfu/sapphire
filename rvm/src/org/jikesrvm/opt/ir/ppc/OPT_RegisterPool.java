/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt.ir.ppc;

import org.jikesrvm.classloader.*;
import org.jikesrvm.opt.ir.OPT_GenericRegisterPool;
import org.jikesrvm.opt.ir.OPT_IR;
import org.jikesrvm.opt.ir.OPT_Instruction;
import org.jikesrvm.opt.ir.OPT_Register;
import org.jikesrvm.opt.ir.OPT_RegisterOperand;

/**
 * Pool of symbolic registers.
 * powerPC specific implementation where JTOC is stored in a reserved register.
 * Each IR contains has exactly one register pool object associated with it.
 * 
 * @see OPT_Register
 * 
 * @author Dave Grove
 * @author Mauricio J. Serrano
 * @author John Whaley
 * @modified Vivek Sarkar
 * @author Peter Sweeney
 */
public abstract class OPT_RegisterPool extends OPT_GenericRegisterPool {

  /**
   * Initializes a new register pool for the method meth.
   * 
   * @param meth the VM_Method of the outermost method
   */
  public OPT_RegisterPool(VM_Method meth) {
    super(meth);
  }

  /**
   * Get the JTOC register
   * 
   * @return the JTOC register
   */ 
  public OPT_Register getJTOC() {
    return physical.getJTOC();
  }

  /**
   * Get a temporary that represents the JTOC register (as an Address)
   * 
   * @param ir  
   * @param s  
   * @return the temp
   */ 
  public OPT_RegisterOperand makeJTOCOp(OPT_IR ir, OPT_Instruction s) {
    return new OPT_RegisterOperand(getJTOC(),VM_TypeReference.Address);
  }

  /**
   * Get a temporary that represents the JTOC register (as an Object)
   * 
   * @return the temp
   */ 
  public OPT_RegisterOperand makeTocOp() {
    return new OPT_RegisterOperand(getJTOC(),VM_TypeReference.JavaLangObject);
  }

}
