/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;
import com.ibm.JikesRVM.adaptive.*;
/**
 * OSR_CodeInstaller generates a glue code which recovers registers and 
 * from the stack frames and branch to the newly compiled method instructions.
 * The glue code is installed right before returning to the threading method
 * by OSR_PostThreadSwitch
 *
 * @author Feng Qian
 */
public class OSR_CodeInstaller implements VM_Constants, VM_BaselineConstants {

  public static boolean install(OSR_ExecutionState state,
			 VM_CompiledMethod cm) {
    VM_Thread thread = state.getThread();
    int[] stack = thread.stack;
    
    int tsfromFPOffset = state.getTSFPOffset();
    int fooFPOffset    = state.getFPOffset();

    int foomid = VM_Magic.getIntAtOffset(stack,
			   fooFPOffset + STACKFRAME_METHOD_ID_OFFSET);

    VM_CompiledMethod foo = VM_CompiledMethods.getCompiledMethod(foomid);
    int cType = foo.getCompilerType();

    int SW_WIDTH = 4;
	
    // this offset is used to adjust SP to FP right after return
    // from a call. 4 bytes for return address and 
    // 4 bytes for saved FP of tsfrom.
    int sp2fpOffset = fooFPOffset - tsfromFPOffset - 2*SW_WIDTH;

    // should given an estimated length, and print the instructions 
    // for debugging
    VM_Assembler asm = new VM_Assembler(50, VM.TraceOnStackReplacement);

    // 1. generate bridge instructions to recover saved registers
    if (cType == VM_CompiledMethod.BASELINE) {
      // unwind stack pointer, SP is FP now
      asm.emitADD_Reg_Imm(SP, sp2fpOffset);
      // restore JTOC
      asm.emitMOV_Reg_RegDisp(JTOC, SP, JTOC_SAVE_OFFSET);
      // restore saved EBX
      asm.emitMOV_Reg_RegDisp(EBX, SP, EBX_SAVE_OFFSET);
      // restore frame pointer
      asm.emitPOP_RegDisp(PR, VM_Entrypoints.framePointerField.getOffset());
      // donot pop return address and parameters, 
      // we make a faked call to newly compiled method
      asm.emitJMP_RegDisp(JTOC, cm.getOsrJTOCoffset());
    } else if (cType == VM_CompiledMethod.OPT) {
      ///////////////////////////////////////////////////
      // recover saved registers from foo's stack frame
      ///////////////////////////////////////////////////     
      VM_OptCompiledMethod fooOpt = (VM_OptCompiledMethod)foo;
      
      // foo definitely not save volatile
      boolean saveVolatile = fooOpt.isSaveVolatile();
      if (VM.VerifyAssertions) {
	VM._assert(!saveVolatile);
      }      
	  
      // assume SP is on foo's stack frame, 
      int firstNonVolatile = fooOpt.getFirstNonVolatileGPR();
      int nonVolatiles = fooOpt.getNumberOfNonvolatileGPRs();
      int nonVolatileOffset = fooOpt.getUnsignedNonVolatileOffset(); 

      for (int i = firstNonVolatile; 
	   i < firstNonVolatile + nonVolatiles; 
	   i++) {
	asm.emitMOV_Reg_RegDisp(NONVOLATILE_GPRS[i], SP, sp2fpOffset-nonVolatileOffset);
	nonVolatileOffset += SW_WIDTH;
      }
      // adjust SP to frame pointer
      asm.emitADD_Reg_Imm(SP, sp2fpOffset);
      // restore frame pointer 
      asm.emitPOP_RegDisp(PR, VM_Entrypoints.framePointerField.getOffset());

	  // we need a scratch registers here, using scratch register here
      // get JTOC content into S0 (ECX)
      asm.emitMOV_Reg_RegDisp(S0, PR, VM_Entrypoints.jtocField.getOffset());
	  // move the address to S0
	  asm.emitMOV_Reg_RegDisp(S0, S0, cm.getOsrJTOCoffset());
      // branch to the newly compiled instructions
      asm.emitJMP_Reg(S0);
    }

    if (VM.TraceOnStackReplacement) {
      VM.sysWrite("new CM instr addr ");
      VM.sysWriteHex(VM_Statics.getSlotContentsAsInt(cm.getOsrJTOCoffset() >> 2));
      VM.sysWriteln();
      VM.sysWrite("JTOC register ");
      VM.sysWriteHex(VM_Magic.getTocPointer().toInt());
      VM.sysWriteln();
      VM.sysWrite("Processor register ");
      VM.sysWriteHex(VM_Magic.objectAsAddress(VM_Magic.getProcessorRegister()).toInt());
      VM.sysWriteln();
      
      VM.sysWriteln("tsfromFPOffset "+Integer.toHexString(tsfromFPOffset));
      VM.sysWriteln("fooFPOffset " + Integer.toHexString(fooFPOffset));
      VM.sysWriteln("SP + "+ (sp2fpOffset+4));
    }
	
    // 3. set thread flags
    thread.isWaitingForOsr = true;
    thread.bridgeInstructions = asm.getMachineCodes();
    thread.fooFPOffset = fooFPOffset;
    thread.tsFPOffset = tsfromFPOffset;

    VM_Address bridgeaddr = 
      VM_Magic.objectAsAddress(thread.bridgeInstructions);

    VM_Memory.sync(bridgeaddr,
		   thread.bridgeInstructions.length << LG_INSTRUCTION_WIDTH);

    if (VM.LogAOSEvents) {
      VM_AOSLogging.logOsrEvent("OSR code installation succeeded");
    }

    return true;
  }
}
