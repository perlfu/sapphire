/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/** 
 * Handle exception delivery and stack unwinding for 
 * opt compiled methods.
 * 
 * @author Dave Grove
 * @author Mauricio J. Serrano 
 */
final class VM_OptExceptionDeliverer extends VM_ExceptionDeliverer
  implements VM_Constants, VM_BytecodeConstants {

  /** 
   * Pass control to a catch block.
   */
  void deliverException(VM_CompiledMethod compiledMethod, 
			int catchBlockInstructionAddress, 
			Throwable exceptionObject, 
			VM_Registers registers) {

    // store exception object for later retrieval by catch block
    VM_OptCompilerInfo info = (VM_OptCompilerInfo)compiledMethod.getCompilerInfo();
    int offset = info.getUnsignedExceptionOffset();
    if (offset != 0) {
      // only put the exception object in the stackframe if the catch block is expecting it.
      // (if the method hasn't allocated a stack slot for caught exceptions, then we can safely
      //  drop the exceptionObject on the floor).
      int fp = registers.getInnermostFramePointer();
      VM_Magic.setObjectAtOffset(VM_Magic.addressAsObject(fp), offset, exceptionObject);
    }

    // set address at which to resume executing frame
    registers.ip = catchBlockInstructionAddress;
    VM.enableGC(); // disabled right before VM_Runtime.deliverException was called

    if (VM.VerifyAssertions) VM.assert(registers.inuse == true);
    registers.inuse = false;

    // "branches" to catchBlockInstructionAddress
    VM_Magic.restoreHardwareExceptionState(registers);
    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
  }

  /**
   * Unwind a stackframe.
   */ 
  void unwindStackFrame(VM_CompiledMethod compiledMethod, 
		       VM_Registers registers) {
    int fp = registers.getInnermostFramePointer();
    VM_Method method = compiledMethod.getMethod();
    VM_OptCompilerInfo info = (VM_OptCompilerInfo)compiledMethod.getCompilerInfo();

    // restore non-volatile registers
    int frameOffset = info.getUnsignedNonVolatileOffset();
    int firstInteger = info.getFirstNonVolatileGPR();
    if (firstInteger >= 0) {
      for (int i = firstInteger; i < 32; i++) {
	registers.gprs[i] = VM_Magic.getMemoryWord(fp + frameOffset);
	frameOffset += 4;
      }
    }
    int firstFloat = info.getFirstNonVolatileFPR();
    if (firstFloat >= 0) {
      frameOffset = (frameOffset + 7) & ~7;  // align pointer for doubles
      for (int i = firstFloat; i < 32; i++) {
	long temp = VM_Magic.getLongAtOffset(VM_Magic.addressAsObject(fp), frameOffset);
	registers.fprs[i] = VM_Magic.longBitsAsDouble(temp);
	frameOffset += 8;
      }
    }

    registers.unwindStackFrame();
  }
}



