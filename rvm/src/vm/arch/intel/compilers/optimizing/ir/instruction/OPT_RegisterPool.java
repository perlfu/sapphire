/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Pool of symbolic registers.
 * Intel specific implementation where JTOC is stored in the processor object
 * and accessed through the processor register.  
 * 
 * @see OPT_Register
 * 
 * @author Peter Sweeney
 * @author Stephen Fink
 */
class OPT_RegisterPool extends OPT_GenericRegisterPool implements OPT_Operators {

  /**
   * Initializes a new register pool for the method meth.
   * 
   * @param meth the VM_Method of the outermost method
   */
  OPT_RegisterPool(VM_Method meth) {
    super(meth);
  }

  /**
   * Inject an instruction to load the JTOC from
   * the processor register and return an OPT_RegisterOperand
   * that contains the result of said load.
   * 
   * @param  ir  the containing IR
   * @param s    the instruction to insert the load operand before
   * @return     a register operand that holds the JTOC
   */ 
  public OPT_RegisterOperand makeJTOCOp(OPT_IR ir, OPT_Instruction s) {
    OPT_RegisterOperand res = ir.regpool.makeTemp
      (OPT_ClassLoaderProxy.IntArrayType);
    if (VM.BuildForIA32 && ir.options.FIXED_JTOC) {
      OPT_Operator mv = OPT_IRTools.getMoveOp(OPT_ClassLoaderProxy.IntArrayType,
                                              ir.IRStage == ir.LIR);
      int jtoc = VM_Magic.objectAsAddress(VM_Magic.getJTOC());
      OPT_IntConstantOperand I = new OPT_IntConstantOperand(jtoc);
      s.insertBefore(Move.create(mv, res, I));
    } else {
      s.insertBefore(Unary.create(GET_JTOC, res, 
                                  OPT_IRTools.
                                  R(ir.regpool.getPhysicalRegisterSet().
                                    getPR())));
    }
    return res.copyD2U();
  }
}
