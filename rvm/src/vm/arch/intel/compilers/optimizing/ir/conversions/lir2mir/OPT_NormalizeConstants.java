/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Normalize the use of constants in the LIR
 * to match the patterns supported in LIR2MIR.rules
 *
 * @author Dave Grove
 */
abstract class OPT_NormalizeConstants implements OPT_Operators {

  /**
   * Only thing we do for IA32 is to restrict the usage of 
   * String, Float, and Double constants.  The rules are prepared 
   * to deal with everything else.
   * 
   * @param ir IR to normalize
   */
  static void perform(OPT_IR ir) { 
    for (OPT_Instruction s = ir.firstInstructionInCodeOrder(); 
	 s != null; 
	 s = s.nextInstructionInCodeOrder()) {

      // Get 'large' constants into a form the the BURS rules are 
      // prepared to deal with.
      // Constants can't appear as defs, so only scan the uses.
      //
      int numUses = s.getNumberOfUses();
      if (numUses > 0) {
        int numDefs = s.getNumberOfDefs();
        for (int idx = numDefs; idx < numUses + numDefs; idx++) {
          OPT_Operand use = s.getOperand(idx);
          if (use != null) {
            if (use instanceof OPT_StringConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_Type.JavaLangStringType);
	      OPT_Operand jtoc = ir.regpool.makeJTOCOp(ir,s);
              OPT_StringConstantOperand sc = (OPT_StringConstantOperand)use;
              int offset = sc.value.offset();
              if (offset == 0)
                throw  new OPT_OptimizingCompilerException("String constant w/o valid JTOC offset");
              offset = offset << 2;
              OPT_LocationOperand loc = new OPT_LocationOperand(offset);
	      s.insertBefore(Load.create(INT_LOAD, rop, jtoc, new OPT_IntConstantOperand(offset), loc));
	      s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_DoubleConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_Type.DoubleType);
	      OPT_Operand jtoc = ir.regpool.makeJTOCOp(ir,s);
              OPT_DoubleConstantOperand dc = (OPT_DoubleConstantOperand)use.copy();
              int offset = dc.offset;
              if (offset == 0) {
                offset = VM_Statics.findOrCreateDoubleLiteral(VM_Magic.doubleAsLongBits(dc.value));
              }
	      dc.offset = offset << 2;
	      s.insertBefore(Binary.create(MATERIALIZE_FP_CONSTANT, rop, jtoc, dc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_FloatConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_Type.FloatType);
	      OPT_Operand jtoc = ir.regpool.makeJTOCOp(ir,s);
              OPT_FloatConstantOperand fc = (OPT_FloatConstantOperand)use.copy();
              int offset = fc.offset;
              if (offset == 0) {
                offset = VM_Statics.findOrCreateFloatLiteral(VM_Magic.floatAsIntBits(fc.value));
              }
	      fc.offset = offset << 2;
	      s.insertBefore(Binary.create(MATERIALIZE_FP_CONSTANT, rop, jtoc, fc));
              s.putOperand(idx, rop.copyD2U());
	    } else if (use instanceof OPT_NullConstantOperand) {
	      s.putOperand(idx, new OPT_IntConstantOperand(0));
	    }
	  }
        }
      }
    }
  }

  /**
   * IA32 supports 32 bit int immediates, so nothing to do.
   */
  static OPT_Operand asImmediateOrReg (OPT_Operand addr, 
				       OPT_Instruction s, 
				       OPT_IR ir) {
    return addr;
  }

}
