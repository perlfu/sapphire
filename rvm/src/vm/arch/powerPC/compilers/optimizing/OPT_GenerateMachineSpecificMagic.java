/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;

/**
 * This class implements the machine-specific magics for the opt compiler. 
 *
 * @see OPT_GenerateMagic for the machine-independent magics.
 * 
 * @author Dave Grove
 * @author Mauricio Serrano
 */
class OPT_GenerateMachineSpecificMagic 
  implements OPT_Operators, VM_StackframeLayoutConstants {

  /**
   * "Semantic inlining" of methods of the VM_Magic class
   * Based on the methodName, generate a sequence of opt instructions
   * that implement the magic, updating the stack as neccessary
   *
   * @param bc2ir the bc2ir object that is generating the 
   *              ir containing this magic
   * @param gc == bc2ir.gc
   * @param meth the VM_Method that is the magic method
   */
  static boolean generateMagic (OPT_BC2IR bc2ir, 
				OPT_GenerationContext gc, 
				VM_MethodReference meth) 
    throws OPT_MagicNotImplementedException {
    VM_Atom methodName = meth.getName();
    if (methodName == VM_MagicNames.getFramePointer) {
      bc2ir.push(gc.temps.makeFPOp());
      gc.allocFrame = true;
    } else if (methodName == VM_MagicNames.getTocPointer) {
      bc2ir.push(gc.temps.makeJTOCOp(null,null));
    } else if (methodName == VM_MagicNames.getJTOC) {
      bc2ir.push(gc.temps.makeTocOp());
    } else if (methodName == VM_MagicNames.getThreadId) {
      OPT_PhysicalRegisterSet phys = gc.temps.getPhysicalRegisterSet();
      OPT_RegisterOperand TIOp = 
	new OPT_RegisterOperand(phys.getTI(),VM_TypeReference.Int);
      bc2ir.push(TIOp);
    } else if (methodName == VM_MagicNames.getCallerFramePointer) {
      OPT_Operand fp = bc2ir.popAddress();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Address);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, 
					  fp,
					  new OPT_IntConstantOperand(STACKFRAME_FRAME_POINTER_OFFSET),
					  null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setCallerFramePointer) {
      OPT_Operand val = bc2ir.popAddress();
      OPT_Operand fp = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(REF_STORE, val, 
					   fp, 
					   new OPT_IntConstantOperand(STACKFRAME_FRAME_POINTER_OFFSET),
					   null));
    } else if (methodName == VM_MagicNames.getCompiledMethodID) {
      OPT_Operand fp = bc2ir.popAddress();
      OPT_RegisterOperand val = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD, val, 
					  fp,
					  new OPT_IntConstantOperand(STACKFRAME_METHOD_ID_OFFSET),
					  null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setCompiledMethodID) {
      OPT_Operand val = bc2ir.popInt();
      OPT_Operand fp = bc2ir.popInt();
      bc2ir.appendInstruction(Store.create(INT_STORE, val, 
					   fp, 
					   new OPT_IntConstantOperand(STACKFRAME_METHOD_ID_OFFSET),
					   null));
    } else if (methodName == VM_MagicNames.getNextInstructionAddress) {
      OPT_Operand fp = bc2ir.popAddress();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Address);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, 
					  fp,
					  new OPT_IntConstantOperand(STACKFRAME_NEXT_INSTRUCTION_OFFSET),
					  null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setNextInstructionAddress) {
      OPT_Operand val = bc2ir.popAddress();
      OPT_Operand fp = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(REF_STORE, val, 
					   fp, 
					   new OPT_IntConstantOperand(STACKFRAME_NEXT_INSTRUCTION_OFFSET),
					   null));
    } else if (methodName == VM_MagicNames.getReturnAddressLocation) {
      OPT_Operand fp = bc2ir.popAddress();
      OPT_RegisterOperand callerFP = gc.temps.makeTemp(VM_TypeReference.Address);
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Address);
      bc2ir.appendInstruction(Load.create(REF_LOAD, callerFP, 
					  fp,
					  new OPT_IntConstantOperand(STACKFRAME_FRAME_POINTER_OFFSET),
					  null));
      bc2ir.appendInstruction(Binary.create(INT_ADD, val, 
					    callerFP,
					    new OPT_IntConstantOperand(STACKFRAME_NEXT_INSTRUCTION_OFFSET)));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.getTime) {
      OPT_RegisterOperand val = gc.temps.makeTempDouble();
      VM_Field target = VM_Entrypoints.getTimeInstructionsField;
      OPT_MethodOperand mo = OPT_MethodOperand.STATIC(target);
      bc2ir.appendInstruction(Call.create1(CALL, val, new OPT_IntConstantOperand(target.getOffset()), mo, bc2ir.popRef()));
      bc2ir.push(val.copyD2U(), VM_TypeReference.Double);
    } else if (methodName == VM_MagicNames.sysCall0) {
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create0(SYSCALL, op0, ip, toc));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall_L_0) {
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempLong();
      bc2ir.appendInstruction(CallSpecial.create0(SYSCALL, op0, ip, toc));
      bc2ir.pushDual(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall_L_I) {
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempLong();
      bc2ir.appendInstruction(CallSpecial.create1(SYSCALL, op0, ip, 
						  toc, p1));
      bc2ir.pushDual(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall1) {
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create1(SYSCALL, op0, ip, 
                                                  toc, p1));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall2) {
      OPT_Operand p2 = bc2ir.popInt();
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create2(SYSCALL, op0, ip, 
						  toc, p1, p2));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCallAD) {
      OPT_Operand p2 = bc2ir.popDouble();
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create2(SYSCALL, op0, ip, 
                                                  toc, p1, p2));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall3) {
      OPT_Operand p3 = bc2ir.popInt();
      OPT_Operand p2 = bc2ir.popInt();
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create3(SYSCALL, op0, ip, 
						  toc, p1, p2, p3));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall4) {
      OPT_Operand p4 = bc2ir.popInt();
      OPT_Operand p3 = bc2ir.popInt();
      OPT_Operand p2 = bc2ir.popInt();
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create4(SYSCALL, op0, ip, 
						  toc, p1, p2, p3, p4));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.isync) {
      if (!gc.options.NO_CACHE_FLUSH)
        bc2ir.appendInstruction(Empty.create(READ_CEILING));
    } else if (methodName == VM_MagicNames.sync) {
      if (!gc.options.NO_CACHE_FLUSH)
        bc2ir.appendInstruction(Empty.create(WRITE_FLOOR));
    } else if (methodName == VM_MagicNames.dcbst) {
      bc2ir.appendInstruction(CacheOp.create(DCBST, bc2ir.popInt()));
    } else if (methodName == VM_MagicNames.icbi) {
      bc2ir.appendInstruction(CacheOp.create(ICBI, bc2ir.popInt()));
    } else {
      // Distinguish between magics that we know we don't implement
      // (and never plan to implement) and those (usually new ones) 
      // that we want to be warned that we don't implement.
      String msg = "Magic method not implemented: " + meth;
      if (methodName == VM_MagicNames.returnToNewStack) {
	throw OPT_MagicNotImplementedException.EXPECTED(msg);
      } else {
	return false;
	// throw OPT_MagicNotImplementedException.UNEXPECTED(msg);
      }
    }
    return true;
  }
}



