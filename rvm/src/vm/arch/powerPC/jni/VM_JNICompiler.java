/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Ton Ngo 
 * @author Steve Smith
 */
public class VM_JNICompiler implements VM_BaselineConstants {

  /**
   * This method creates the stub to link native method.  It will be called
   * from the lazy linker the first time a native method is invoked.  The stub
   * generated will be patched by the lazy linker to link to the native method
   * for all future calls. 
   *
   * The stub performs the following tasks in the prologue:
   *   -Allocate the glue frame
   *   -Save the TI and PR registers in the JNI Environment for reentering Java later
   *   -Shuffle the parameters in the registers to conform to the AIX convention
   *   -Save the nonvolatile registers in a known space in the frame to be used 
   *    for the GC stack map
   *   -Push a new JREF frame on the JNIRefs stack
   *   -Set the VM_Processor affinity so that this thread won't migrate while in native
   *   -Supply the first JNI argument:  the JNI environment pointer
   *   -Supply the second JNI argument:  class object if static, "this" if virtual
   *   -Hardcode the TOC and IP to the corresponding native code
   *
   * The stub performs the following tasks in the epilogue:
   *   -TI and PR registers are AIX nonvolatile, so they should be restored already
   *   -Restore the nonvolatile registers if GC has occurred
   *   -Pop the JREF frame off the JNIRefs stack
   *   -Check for pending exception and deliver to Java caller if present
   *   -Process the return value from native:  push onto caller's Java stack  
   *  
   * The stack frame created by this stub conforms to the AIX convention:
   *   -6-word frame header
   *   -parameter save/spill area
   *   -one word flag to indicate whether GC has occurred during the native execution
   *   -16-word save area for nonvolatile registers
   *  
   *   | fp       | <- native frame
   *   | cr       |
   *   | lr       |
   *   | resv     |
   *   | resv     |
   *   + toc      +
   *   |          |
   *   |          |
   *   |----------|  
   *   | fp       | <- Java to C glue frame
   *   | cr/mid   |
   *   | lr       |
   *   | resv     |
   *   | resv     |
   *   + toc      +
   *   |   0      | spill area (at least 8 words reserved)
   *   |   1      | (also used for saving volatile regs during calls in prolog)
   *   |   2      |
   *   |   3      |
   *   |   4      |
   *   |   5      |
   *   |   6      |
   *   |   7      |
   *   |  ...     | 
   *   |          |
   *   |GC flag   | offset = JNI_SAVE_AREA_OFFSET           <- JNI_GC_FLAG_OFFSET
   *   |affinity  | saved VM_Thread.processorAffinity       <- JNI_AFFINITY_OFFSET
   *   |vol fpr1  | saved AIX volatile fpr during becomeNativeThread
   *   | ...      | 
   *   |vol fpr6  | saved AIX volatile fpr during becomeNativeThread
   *   |vol r4    | saved AIX volatile regs during Yield (to be removed when code moved to Java)   
   *   | ...      | 
   *   |vol r10   | saved AIX volatile regs during Yield    <- JNI_AIX_VOLATILE_REGISTER_OFFSET
   *   |Proc reg  | processor register R16                  <- JNI_PR_OFFSET
   *   |nonvol 17 | save 15 nonvolatile registers for stack mapper
   *   | ...      |
   *   |nonvol 31 |                                         <- JNI_RVM_NONVOLATILE_OFFSET
   *   |savedSP   | SP is no longer saved & restored (11/21/00)  <- JNI_SP_OFFSET XXX
   *   |savedJTOC | save RVM JTOC for return                 <- JNI_JTOC_OFFSET
   *   |----------|   
   *   |  fp   	  | <- Java caller frame
   *   | mid   	  |
   *   | xxx   	  |
   *   |       	  |
   *   |       	  |
   *   |       	  |
   *   |----------|
   *   |       	  |
   */
  static VM_MachineCode generateGlueCodeForNative (VM_CompiledMethod cm) {
    int compiledMethodId = cm.getId();
    VM_Method method     = cm.getMethod();
      VM_Assembler asm	= new VM_Assembler(0);
      int frameSize	= VM_Compiler.getFrameSize(method);
      VM_Class klass	= method.getDeclaringClass();

      /* initialization */
      { 
	if (VM.VerifyAssertions) VM.assert(T3 <= LAST_VOLATILE_GPR);           // need 4 gp temps
	if (VM.VerifyAssertions) VM.assert(F3 <= LAST_VOLATILE_FPR);           // need 4 fp temps
	if (VM.VerifyAssertions) VM.assert(S0 < SP && SP <= LAST_SCRATCH_GPR); // need 2 scratch
      }      

      VM_Address bootRecordAddress = VM_Magic.objectAsAddress(VM_BootRecord.the_boot_record);
      int lockoutLockOffset = VM_Entrypoints.lockoutProcessorField.getOffset();
      VM_Address lockoutLockAddress = bootRecordAddress.add(lockoutLockOffset);
      int sysTOCOffset      = VM_Entrypoints.sysTOCField.getOffset();
      int sysYieldIPOffset  = VM_Entrypoints.sysVirtualProcessorYieldIPField.getOffset();

      VM_Address gCFlagAddress = VM_Magic.objectAsAddress(VM_BootRecord.the_boot_record).add(VM_Entrypoints.globalGCInProgressFlagField.getOffset());

      int nativeIP  = method.getNativeIP();
      int nativeTOC = method.getNativeTOC();

      // NOTE:  this must be done before the condition VM_Thread.hasNativeStackFrame() become true
      // so that the first Java to C transition will be allowed to resize the stack
      // (currently, this is true when the JNIRefsTop index has been incremented from 0)
      asm.emitNativeStackOverflowCheck(frameSize + 14);   // add at least 14 for C frame (header + spill)

      int numValues = method.getParameterWords();     // number of arguments for this method
      int parameterAreaSize = (numValues) * 4;

      asm.emitMFLR(0);                                // save return address in caller frame
      asm.emitST(0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);	// this was set up the first time by DynamicBridgeTo()

      asm.emitSTU (FP,  -frameSize, FP);                      // get transition frame on stack
      asm.emitST  (JTOC, frameSize - JNI_JTOC_OFFSET, FP);    // save RVM JTOC in frame

      asm.emitST  (PROCESSOR_REGISTER, frameSize - JNI_PR_OFFSET, FP);  // save PR in frame

      // store method ID for JNI frame, occupies AIX saved CR slot, which we don't use
      // hardcoded in the glue routine
      asm.emitLVAL (S0, compiledMethodId); 
      asm.emitST   (S0, STACKFRAME_METHOD_ID_OFFSET, FP);

      // establish SP -> VM_Thread, S0 -> threads JNIEnv structure      
      asm.emitL(SP, VM_Entrypoints.activeThreadField.getOffset(), PROCESSOR_REGISTER);
      asm.emitL(S0, VM_Entrypoints.jniEnvField.getOffset(), SP);  

      // save the TI & PR registers in the JNIEnvironment object for possible calls back into Java
      asm.emitST(TI, VM_Entrypoints.JNIEnvSavedTIField.getOffset(), S0);           
      asm.emitST(PROCESSOR_REGISTER, VM_Entrypoints.JNIEnvSavedPRField.getOffset(), S0);   

      // save current frame pointer in JNIEnv, JNITopJavaFP, which will be the frame
      // to start scanning this stack during GC, if top of stack is still executing in C
      asm.emitST(FP, VM_Entrypoints.JNITopJavaFPField.getOffset(), S0);           

      // save the RVM nonvolatile registers, to be scanned by GC stack mapper
      // remember to skip past the saved JTOC and SP by starting with offset=-12
      //
      for (int i = LAST_NONVOLATILE_GPR, offset = JNI_RVM_NONVOLATILE_OFFSET;
	   i >= FIRST_NONVOLATILE_GPR; --i, offset+=4) {
	asm.emitST (i, frameSize - offset, FP);
      }

      // clear the GC flag on entry to native code
      asm.emitCAL(PROCESSOR_REGISTER,0,0);          // use PR as scratch
      asm.emitST(PROCESSOR_REGISTER, frameSize-JNI_GC_FLAG_OFFSET, FP);

      // generate the code to map the parameters to AIX convention and add the
      // second parameter 2 (either the "this" ptr or class if a static method).
      // The JNI Function ptr first parameter is set before making the call.
      // Opens a new frame in the JNIRefs table to register the references
      // Assumes S0 set to JNIEnv, kills TI, SP & PROCESSOR_REGISTER
      // On return, S0 is still valid.
      //
      storeParametersForAIX(asm, frameSize, method, klass);

     // Get address of out_of_line prolog into SP, before setting TOC reg.
     asm.emitL   (SP, VM_Entrypoints.invokeNativeFunctionInstructionsField.getOffset(), JTOC);
     asm.emitMTLR(SP);

     // set the TOC and IP for branch to out_of_line code
     asm.emitLVAL (JTOC,  nativeTOC);         // load TOC for native function into TOC reg
     asm.emitLVAL (TI,    nativeIP);	      // load TI with address of native code

     // go to part of prologue that is in the boot image. It will change the Processor
     // status to "in_native" and transfer to the native code.  On return it will
     // change the state back to "in_java" (waiting if blocked).
     //
     // The native address entrypoint is in register TI
     // The native TOC has been loaded into the TOC register
     // S0 still points to threads JNIEnvironment
     //
     asm.emitBLRL  ();

     // restore PR, saved in this glue frame before the call. If GC occurred, this saved
     // VM_Processor reference was relocated while scanning this stack. If while in native,
     // the thread became "stuck" and was moved to a Native Processor, then this saved
     // PR was reset to point to that native processor
     //
     asm.emitL     (PROCESSOR_REGISTER, frameSize - JNI_PR_OFFSET, FP);  

     // at this point, test if in blue processor: if yes, return to
     // Java caller; by picking up at "check if GC..." below
     // if not, must transfer to blue processor by using
     // become RVM thread.

     asm.emitL     (S0, VM_Entrypoints.processorModeField.getOffset(), PROCESSOR_REGISTER); // get processorMode
     asm.emitCMPI  (S0, VM_Processor.RVM)     ;           // are we still on blue processor
     asm.emitBEQ   (+8);                                  // br if equal; i.e., still on blue processor

      asm.emitL(T0, VM_Entrypoints.activeThreadField.getOffset(), PROCESSOR_REGISTER);  // get activeThread from Processor
      asm.emitL(S0, VM_Entrypoints.jniEnvField.getOffset(), T0);             // get JNIEnvironment from activeThread
      asm.emitL (TI, VM_Entrypoints.JNIEnvSavedTIField.getOffset(), S0);     // and restore TI from JNIEnvironment  

      // restore RVM JTOC
      asm.emitL   (JTOC, frameSize - 4, FP);          

      // Branch to becomeRVMThread:  we will yield and get rescheduled to execute 
      // on a regular VM_Processor for Java code
      asm.emitL   (S0, VM_Entrypoints.becomeRVMThreadMethod.getOffset(), JTOC);
      asm.emitMTLR(S0);
      asm.emitBLRL  ();
     
      // At this point, we have resumed execution in the regular Java VM_Processor
      // so the PR contains the correct values for this VM_Processor.  TI is also correct

      // branch to here if on blue processor	
      //
      // check if GC has occurred, If GC did not occur, then 
      // VM NON_VOLATILE regs were restored by AIX and are valid.  If GC did occur
      // objects referenced by these restored regs may have moved, in this case we
      // restore the nonvolatile registers from our savearea,
      // where any object references would have been relocated during GC.
      // use T2 as scratch (not needed any more on return from call)
      //
      asm.emitL(T2, frameSize - JNI_GC_FLAG_OFFSET, FP);
      asm.emitCMPI(T2,0);
      asm.emitBEQ(16);   // NOTE:  count of 16 takes branch around the next 15 instructions (register loads)
      for (int i = LAST_NONVOLATILE_GPR, offset = JNI_RVM_NONVOLATILE_OFFSET;
	   i >= FIRST_NONVOLATILE_GPR; --i, offset+=4) {
	asm.emitL (i, frameSize - offset, FP);
      }

      // BEQ(16) branch reaches here (next emitted instruction) if taken
      asm.emitL(S0, VM_Entrypoints.activeThreadField.getOffset(), PROCESSOR_REGISTER);  // S0 holds thread pointer

      // reset threads processor affinity if it was previously zero, before the call.
      // ...again, rethink if the setting & resetting of processor affinity is still necessary
      //
      asm.emitL(T2, frameSize - JNI_AFFINITY_OFFSET, FP);          // saved affinity in glue frame
      asm.emitCMPI(T2,0);
      asm.emitBNE(2); 
      asm.emitST(T2, VM_Entrypoints.processorAffinityField.getOffset(), S0);  // store 0 into affinity field
		 
      // reestablish S0 to hold jniEnv pointer
      asm.emitL(S0, VM_Entrypoints.jniEnvField.getOffset(), S0);       

      // pop jrefs frame off the JNIRefs stack, "reopen" the previous top jref frame
      // use SP as scratch before it's restored, also use T2, T3 for scratch which are no longer needed
      asm.emitL  (SP, VM_Entrypoints.JNIRefsField.getOffset(), S0);          // load base of JNIRefs array
      asm.emitL  (T2, VM_Entrypoints.JNIRefsSavedFPField.getOffset(), S0);   // get saved offset for JNIRefs frame ptr previously pushed onto JNIRefs array
      asm.emitCAL(T3, -4, T2);                                    // compute offset for new TOP
      asm.emitST (T3, VM_Entrypoints.JNIRefsTopField.getOffset(), S0);       // store new offset for TOP into JNIEnv
      asm.emitLX (T2, SP, T2);                                    // retrieve the previous frame ptr
      asm.emitST (T2, VM_Entrypoints.JNIRefsSavedFPField.getOffset(), S0);   // store new offset for JNIRefs frame ptr into JNIEnv

      // Restore the return value R3-R4 saved in the glue frame spill area before the migration
      asm.emitL (T0, AIX_FRAME_HEADER_SIZE, FP);
      asm.emitL (T1, AIX_FRAME_HEADER_SIZE+4, FP);
      
      // if the the return type is a reference, the native C is returning a jref
      // which is a byte offset from the beginning of the threads JNIRefs stack/array
      // of the corresponding ref.  In this case, emit code to replace the returned
      // offset (in R3) with the ref from the JNIRefs array

      VM_Type returnType = method.getReturnType();
      if ( returnType.isReferenceType() ) {
	// use returned offset to load ref from JNIRefs into R3
	asm.emitLX (3, SP, 3);         // SP is still the base of the JNIRefs array
      }

      // reload TI ref saved in JNIEnv
      asm.emitL (TI, VM_Entrypoints.JNIEnvSavedTIField.getOffset(), S0);           


      // pop the glue stack frame, restore the Java caller frame
      asm.emitCAL (FP,  +frameSize, FP);              // remove linkage area

      // C return value is already where caller expected it (R3, R4 or F0)
      // and SP is a scratch register, so we actually don't have to 
      // restore it and/or pop arguments off it.
      // So, just restore the return address to the link register.

      asm.emitL(0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); 
      asm.emitMTLR(0);                           // restore return address

      // CHECK EXCEPTION AND BRANCH TO ATHROW CODE OR RETURN NORMALLY

      asm.emitL  (T2, VM_Entrypoints.JNIPendingExceptionField.getOffset(), S0);   // get pending exception from JNIEnv
      asm.emitCAL (T3,  0, 0);             // get a null value to compare
      asm.emitST  (T3, VM_Entrypoints.JNIPendingExceptionField.getOffset(), S0); // clear the current pending exception
      asm.emitCMP (T2, T3);                      // check for pending exception on return from native
      asm.emitBNE(+2);                           // if pending exception, handle it
      asm.emitBLR();                             // if no pending exception, proceed to return to caller

      // An exception is pending, deliver the exception to the caller as if executing an athrow in the caller
      // at the location of the call to the native method
      asm.emitLtoc(T3, VM_Entrypoints.athrowMethod.getOffset());
      asm.emitMTCTR(T3);                         // point LR to the exception delivery code
      asm.emitCAL (T0, 0, T2);                   // copy the saved exception to T0

      asm.emitBCTR();                            // then branch to the exception delivery code, does not return

      return asm.makeMachineCode();

     } // generateGlueCodeForNative 


  // Map the arguments from RVM convention to AIX convention,
  // and replace all references with indexes into JNIRefs array.
  // Assumption on entry:
  // -TI, PROCESSOR_REGISTER and SP are available for use as scratch register
  // -the frame has been created, FP points to the new callee frame
  // Also update the JNIRefs arra
  private static void 
    storeParametersForAIX(VM_Assembler asm, int frameSize, VM_Method method, VM_Class klass) {

    int nextAIXArgReg, nextAIXArgFloatReg, nextVMArgReg, nextVMArgFloatReg; 
    
    // offset to the spill area in the callee (AIX frame):
    // skip past the 2 arguments to be added in front:  JNIenv and class or object pointer
    int spillOffsetAIX = AIX_FRAME_HEADER_SIZE + 8;

    // offset to the spill area in the caller (RVM frame), relative to the callee's FP
    int spillOffsetVM = frameSize + STACKFRAME_HEADER_SIZE;

    VM_Type[] types = method.getParameterTypes();   // does NOT include implicit this or class ptr
    int numArguments = types.length;                // number of arguments for this method
    
    // Set up the Reference table for GC
    // PR <- JREFS array base
    asm.emitL(PROCESSOR_REGISTER, VM_Entrypoints.JNIRefsField.getOffset(), S0);
    // TI <- JREFS current top 
    asm.emitL(TI, VM_Entrypoints.JNIRefsTopField.getOffset(), S0);   // JREFS offset for current TOP 
    asm.emitA(TI, PROCESSOR_REGISTER, TI);                // convert into address

    // TODO - count number of refs
    // TODO - emit overflow check for JNIRefs array
    
    // start a new JNIRefs frame on each transition from Java to native C
    // push current SavedFP ptr onto top of JNIRefs stack (use available SP reg as a temp)
    // and make current TOP the new savedFP
    //
    asm.emitL   ( SP, VM_Entrypoints.JNIRefsSavedFPField.getOffset(), S0);
    asm.emitSTU ( SP, 4, TI );                           // push prev frame ptr onto JNIRefs array	
    asm.emitSF  ( SP, PROCESSOR_REGISTER, TI);           // compute offset for new TOP
    asm.emitST  ( SP, VM_Entrypoints.JNIRefsSavedFPField.getOffset(), S0);  // save new TOP as new frame ptr in JNIEnv


    // for static methods: caller has placed args in r3,r4,... 
    // for non-static methods:"this" ptr is in r3, and args start in r4,r5,...
    // 
    // for static methods:                for nonstatic methods:       
    //  Java caller     AIX callee         Java caller     AIX callee    
    //  -----------     ----------	    -----------     ----------  
    //  spill = arg11 -> new spill	    spill = arg11 -> new spill  
    //  spill = arg10 -> new spill	    spill = arg10 -> new spill  
    // 				            spill = arg9  -> new spill  
    //    R12 = arg9  -> new spill	                                
    //    R11 = arg8  -> new spill	      R12 = arg8  -> new spill  
    //    R10 = arg7  -> new spill	      R11 = arg7  -> new spill  
    //    R9  = arg6  -> new spill	      R10 = arg6  -> new spill  
    // 								   
    //    R8  = arg5  -> R10                  R9  = arg5  -> R10         
    //    R7  = arg4  -> R9		      R8  = arg4  -> R9          
    //    R6  = arg3  -> R8		      R7  = arg3  -> R8          
    //    R5  = arg2  -> R7		      R6  = arg2  -> R7          
    //    R4  = arg1  -> R6		      R5  = arg1  -> R6          
    //    R3  = arg0  -> R5		      R4  = arg0  -> R5          
    //                   R4 = class           R3  = this  -> R4         
    // 	                 R3 = JNIenv                         R3 = JNIenv
    //
    // if the number of args in GPR does not exceed R11, then we can use R12 as scratch 
    //   to move the args
    // if the number of args in GPR exceed R12, then we need to save R12 first to make 
    //   room for a scratch register
    // if the number of args in FPR does not exceed F12, then we can use F13 as scratch

    nextAIXArgFloatReg = FIRST_AIX_VOLATILE_FPR;
    nextVMArgFloatReg = FIRST_VOLATILE_FPR;
    nextAIXArgReg      = FIRST_AIX_VOLATILE_GPR + 2;   // 1st reg = JNIEnv, 2nd reg = class
    if ( method.isStatic() ) {
      nextVMArgReg = FIRST_VOLATILE_GPR;              
    } else {
      nextVMArgReg = FIRST_VOLATILE_GPR+1;            // 1st reg = this, to be processed separately
    }

    // The loop below assumes the following relationship:
    if (VM.VerifyAssertions) VM.assert(FIRST_AIX_VOLATILE_FPR==FIRST_VOLATILE_FPR);
    if (VM.VerifyAssertions) VM.assert(LAST_AIX_VOLATILE_FPR<=LAST_VOLATILE_FPR);
    if (VM.VerifyAssertions) VM.assert(FIRST_AIX_VOLATILE_GPR==FIRST_VOLATILE_GPR);
    if (VM.VerifyAssertions) VM.assert(LAST_AIX_VOLATILE_GPR<=LAST_VOLATILE_GPR);


    // create one VM_Assembler object for each argument
    // This is needed for the following reason:
    //   -2 new arguments are added in front for native methods, so the normal arguments
    //    need to be shifted down in addition to being moved
    //   -to avoid overwriting each other, the arguments must be copied in reverse order
    //   -the analysis for mapping however must be done in forward order
    //   -the moving/mapping for each argument may involve a sequence of 1-3 instructions 
    //    which must be kept in the normal order
    // To solve this problem, the instructions for each argument is generated in its
    // own VM_Assembler in the forward pass, then in the reverse pass, each VM_Assembler
    // emist the instruction sequence and copies it into the main VM_Assembler
    VM_Assembler[] asmForArgs = new VM_Assembler[numArguments];

    for (int arg = 0; arg < numArguments; arg++) {
      
      boolean mustSaveFloatToSpill;
      asmForArgs[arg] = new VM_Assembler(0);
      VM_Assembler asmArg = asmForArgs[arg];

      // For 32-bit float arguments
      //
      if (types[arg].isFloatType()) {

	// Side effect of float arguments on the GPR's
	// (1a) reserve one GPR for each float if it is available
	if (nextAIXArgReg<=LAST_AIX_VOLATILE_GPR) {
	  nextAIXArgReg++;
	  mustSaveFloatToSpill = false;
	}
	// (1b) if GPR has spilled, store the float argument in the callee spill area
	// regardless of whether the FPR has spilled or not
	else {
	  mustSaveFloatToSpill = true;
	}

	// Check if the args need to be moved
	// (2a) leave those in FPR[1:13] as is unless the GPR has spilled
	if (nextVMArgFloatReg<=LAST_AIX_VOLATILE_FPR) {
	  if (mustSaveFloatToSpill) 
	    asmArg.emitSTFS(nextVMArgFloatReg, spillOffsetAIX, FP); 
	  spillOffsetAIX+=4;
	  nextAIXArgFloatReg++;
	  nextVMArgFloatReg++;  
	} 
	// (2b) run out of FPR in AIX, but still have 2 more FPR in VM,
	// so FPR[14:15] goes to the callee spill area
	else if (nextVMArgFloatReg<=LAST_VOLATILE_FPR) {
	  asmArg.emitSTFS(nextVMArgFloatReg, spillOffsetAIX, FP);
	  nextVMArgFloatReg++;
	  spillOffsetAIX+=4;
	} 
	// (2c) run out of FPR in VM, now get the remaining args from the caller spill area 
	// and move them into the callee spill area
	else {
	  asmArg.emitLFS(0, spillOffsetVM, FP);
	  asmArg.emitSTFS(0, spillOffsetAIX, FP);
	  spillOffsetVM+=4;
	  spillOffsetAIX+=4;
	}
      } 



      // For 64-bit float arguments 
      //
      else if (types[arg].isDoubleType()) {

	// Side effect of float arguments on the GPR's
	// (1a) reserve two GPR's for double
	if (nextAIXArgReg<=LAST_AIX_VOLATILE_GPR-1) {
	  nextAIXArgReg+=2;
	  mustSaveFloatToSpill = false;
	}
	// if only one GPR is left, reserve it anyway although it won't be used
	else {
	  if (nextAIXArgReg<=LAST_AIX_VOLATILE_GPR)
	    nextAIXArgReg++;
	  mustSaveFloatToSpill = true;
	}

	// Check if the args need to be moved
	// (2a) leave those in FPR[1:13] as is unless the GPR has spilled
	if (nextVMArgFloatReg<=LAST_AIX_VOLATILE_FPR) {
	  if (mustSaveFloatToSpill) 
	    asmArg.emitSTFD(nextVMArgFloatReg, spillOffsetAIX, FP); 
	  spillOffsetAIX+=8;
	  nextAIXArgFloatReg++;
	  nextVMArgFloatReg++;  
	} 
	// (2b) run out of FPR in AIX, but still have 2 more FPR in VM,
	// so FPR[14:15] goes to the callee spill area
	else if (nextVMArgFloatReg<=LAST_VOLATILE_FPR) {
	  asmArg.emitSTFD(nextVMArgFloatReg, spillOffsetAIX, FP);
	  nextVMArgFloatReg++;
	  spillOffsetAIX+=8;

	} 
	// (2c) run out of FPR in VM, now get the remaining args from the caller spill area 
	// and move them into the callee spill area
	else {
	  asmArg.emitLFD(0, spillOffsetVM, FP);
	  asmArg.emitSTFD(0, spillOffsetAIX, FP);
	  spillOffsetVM+=8;
	  spillOffsetAIX+=8;
	}
      }


      // For 64-bit int arguments
      //
      else if (types[arg].isLongType()) {
	// (1a) fit in AIX register, move the pair
	if (nextAIXArgReg<=LAST_AIX_VOLATILE_GPR-1) {
	  asmArg.emitCAU(nextAIXArgReg+1, nextVMArgReg+1, 0);  // move lo-word first
	  asmArg.emitCAU(nextAIXArgReg, nextVMArgReg, 0);      // so it doesn't overwritten
	  nextAIXArgReg+=2;
	  nextVMArgReg+=2;
	  spillOffsetAIX+=8;
	}

	// (1b) fit in VM register but straddle across AIX register/spill
	else if (nextAIXArgReg==LAST_AIX_VOLATILE_GPR &&
		 nextVMArgReg<=LAST_VOLATILE_GPR-1) {
	  spillOffsetAIX+=4;
	  asmArg.emitST(nextVMArgReg+1, spillOffsetAIX, FP);   // move lo-word first
	  spillOffsetAIX+=4;                                    // so it doesn't overwritten
	  asmArg.emitCAU(nextAIXArgReg, nextVMArgReg, 0);
	  nextAIXArgReg+=2;
	  nextVMArgReg+=2;	  
	}

	// (1c) fit in VM register, spill in AIX without straddling register/spill
	else if (nextAIXArgReg>LAST_AIX_VOLATILE_GPR &&
		 nextVMArgReg<=LAST_VOLATILE_GPR-1) {
	  asmArg.emitST(nextVMArgReg++, spillOffsetAIX, FP);
	  spillOffsetAIX+=4;
	  asmArg.emitST(nextVMArgReg++, spillOffsetAIX, FP);
	  spillOffsetAIX+=4;
	}

	// (1d) split across VM/spill, spill in AIX
	else if (nextVMArgReg==LAST_VOLATILE_GPR) {
	  asmArg.emitST(nextVMArgReg++, spillOffsetAIX, FP);
	  spillOffsetAIX+=4;
	  asmArg.emitL(0, spillOffsetVM, FP);
	  asmArg.emitST(0, spillOffsetAIX, FP);
	  spillOffsetAIX+=4;
	  spillOffsetVM+=4;
	}

	// (1e) spill both in VM and AIX
	else {
	  asmArg.emitLFD(0, spillOffsetVM, FP);
	  asmArg.emitSTFD(0, spillOffsetAIX, FP);
	  spillOffsetAIX+=8;
	  spillOffsetVM+=8;	  
	}
      }


      // For reference type, replace with handlers before passing to AIX
      //
      else if (types[arg].isReferenceType() ) {	
	// (1a) fit in AIX register, move the register
	if (nextAIXArgReg<=LAST_AIX_VOLATILE_GPR) {
	  asmArg.emitSTU(nextVMArgReg++, 4, TI );          // append ref to end of JNIRefs array
	  asmArg.emitSF(nextAIXArgReg++, PROCESSOR_REGISTER, TI );  // pass offset in bytes of jref
	  spillOffsetAIX+=4;
	}

	// (1b) spill AIX register, but still fit in VM register
	else if (nextVMArgReg<=LAST_VOLATILE_GPR) {
	  asmArg.emitSTU(nextVMArgReg++, 4, TI );    // append ref to end of JNIRefs array
	  asmArg.emitSF(0, PROCESSOR_REGISTER, TI );  // compute offset in bytes for jref
	  asmArg.emitST(0, spillOffsetAIX, FP);       // spill into AIX frame
	  spillOffsetAIX+=4;
	}

	// (1c) spill VM register
	else {
	  asmArg.emitL(0, spillOffsetVM, FP);        // retrieve arg from VM spill area
	  asmArg.emitSTU(0, 4, TI );                  // append ref to end of JNIRefs array
	  asmArg.emitSF(0, PROCESSOR_REGISTER, TI );  // compute offset in bytes for jref
	  asmArg.emitST(0, spillOffsetAIX, FP);       // spill into AIX frame
	  spillOffsetVM+=4;
	  spillOffsetAIX+=4;
	}
      }


      // For all other types: int, short, char, byte, boolean
      else {
	// (1a) fit in AIX register, move the register
	if (nextAIXArgReg<=LAST_AIX_VOLATILE_GPR) {
	  asmArg.emitCAU(nextAIXArgReg++, nextVMArgReg++, 0);
	  spillOffsetAIX+=4;
	}

	// (1b) spill AIX register, but still fit in VM register
	else if (nextVMArgReg<=LAST_VOLATILE_GPR) {
	  asmArg.emitST(nextVMArgReg++, spillOffsetAIX, FP);
	  spillOffsetAIX+=4;
	}

	// (1c) spill VM register
	else {
	  asmArg.emitL(0,spillOffsetVM, FP);        // retrieve arg from VM spill area
	  asmArg.emitST(0, spillOffsetAIX, FP);
	  spillOffsetVM+=4;
	  spillOffsetAIX+=4;
	}

      }
    }
    

    // Append the code sequences for parameter mapping 
    // to the current machine code in reverse order
    // so that the move does not overwrite the parameters
    for (int arg = numArguments-1; arg >= 0; arg--) {
      VM_MachineCode codeForArg = asmForArgs[arg].makeMachineCode();
      asm.appendInstructions(codeForArg.getInstructions());
    }


    // Now add the 2 new JNI parameters:  JNI environment and Class or "this" object

    // if static method, append ref for class, else append ref for "this"
    // and pass offset in JNIRefs array in r4 (as second arg to called native code)
    if ( method.isStatic() ) {
       klass.getClassForType();     // ensure the Java class object is created
       // JTOC saved above in JNIEnv is still valid, used by following emitLtoc
       asm.emitLtoc( 4, klass.getTibOffset() ); // r4 <- class TIB ptr from jtoc
       asm.emitL   ( 4, 0, 4 );                  // r4 <- first TIB entry == -> class object
       asm.emitL   ( 4, VM_Entrypoints.classForTypeField.getOffset(), 4 ); // r4 <- java Class for this VM_Class
       asm.emitSTU ( 4, 4, TI );                 // append class ptr to end of JNIRefs array
       asm.emitSF( 4, PROCESSOR_REGISTER, TI );  // pass offset in bytes
    }
    else {
       asm.emitSTU ( 3, 4, TI );                 // append this ptr to end of JNIRefs array
       asm.emitSF( 4, PROCESSOR_REGISTER, TI );  // pass offset in bytes
    }
    
    // store the new JNIRefs array TOP back into JNIEnv	
    asm.emitSF(TI, PROCESSOR_REGISTER, TI );     // compute offset for the current TOP
    asm.emitST(TI, VM_Entrypoints.JNIRefsTopField.getOffset(), S0);

  }  // end storeParametersforAIX


  // Emit code to interface with a call from native code that uses the AIX convention
  // for register and stack to a JNI Function that uses Japapeno conventions.
  //
  static void generateGlueCodeForJNIMethod(VM_Assembler asm, VM_Method mth) {

    int offset;

    asm.emitSTU(FP,-JNI_GLUE_FRAME_SIZE,FP);     // buy the glue frame

    // we may need to save CR in the previous frame also if CR will be used
    // CR is to be saved at FP+4 in the previous frame

    // Here we check if this is a JNI function that takes the vararg in the ... style
    // This includes CallStatic<type>Method, Call<type>Method, CallNonVirtual<type>Method
    // For these calls, the vararg starts at the 4th or 5th argument (GPR 6 or 7)
    // So, we save the GPR 6-10 and FPR 1-3 in a volatile register save area 
    // in the glue stack frame so that the JNI function can later repackage the arguments
    // based on the parameter types of target method to be invoked.
    // (For long argument lists, the additional arguments, have been saved in
    // the spill area of the AIX caller, and will be retrieved from there.)
    //
    // If we are compiling such a JNI Function, then emit the code to store
    // GPR 4-10 and FPR 1-6 into the volatile save area.

    String mthName = mth.getName().toString();
    if ((mthName.startsWith("Call") && mthName.endsWith("Method")) ||
	mthName.equals("NewObject")) {

	offset = STACKFRAME_HEADER_SIZE + 12;   // skip over slots for GPR 3-5
	for (int i = 6; i <= 10; i++ ) {
	    asm.emitST (i, offset, FP);
	    offset+=4;
	}
	// store FPRs 1-3 in first 3 slots of volatile FPR save area
	for (int i = 1; i <= 3; i++) {
	    asm.emitSTFD (i, offset, FP);
	    offset+=8;
	}
    }

    // Save AIX non-volatile GRPs and FPRs that will not be saved and restored
    // by RVM. These are GPR 13-16 & FPR 14-15.
    //
    offset = STACKFRAME_HEADER_SIZE + 80;   // skip 20 word volatile reg save area
    for (int i = 13; i <= 16; i++) {
      asm.emitST (i, offset, FP);
      offset += 4;
    }
    for (int i = 14; i <= 15; i++) {
      asm.emitSTFD (i, offset, FP);
      offset +=8;
    }
 
    // set the method ID for the glue frame
    // and save the return address in the previous frame
    //
    asm.emitLVAL(S0, INVISIBLE_METHOD_ID);
    asm.emitMFLR(0);
    asm.emitST  (S0, STACKFRAME_METHOD_ID_OFFSET, FP);
    asm.emitST  (0, JNI_GLUE_FRAME_SIZE + STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);
    int CR_OFFSET = 4; // Save CR in caller's frame; see page 162 of PPC Compiler Writer's Guide
    asm.emitMFCR (S0);
    asm.emitST (S0, JNI_GLUE_FRAME_SIZE + CR_OFFSET, FP);

    // change the vpStatus of the current Processor to "in Java", if GC has started 
    // and we are "blocked_in_native" then loop doing sysYields until GC done and the
    // processor is unblocked.  With default jni, we can also be "blocked_in_native"
    // if the running thread has been detected as "stuck" and is being moved to a
    // native processor.
    //
    // on entry T0 = JNI Function Ptr (Handle) which should point into the array of function ptrs
    // the next word in that array contains the address of the current processors vpStatus word
    //
    // AIX non volatile gprs 13-16 have been saved & are available (also gprs 11-13 can be used).
    // S0=13, SP=14, TI=15, PROCESSOR_REGISTER=16 are available (&have labels) for changing state.
    // ...it would be nice to leave the passed arguments untouched, unless we are blocked
    // and have to call sysVirtualProcessorYield

    int label0    = asm.currentInstructionOffset();	                    // inst index of the following instr
    //
    asm.emitL     (TI, 4, T0);      // TI <- addr of processors vpStatus, from JNI function Ptr array
    asm.emitLWARX (S0, 0, TI);      // get status for processor
    asm.emitCMPI  (S0, VM_Processor.BLOCKED_IN_NATIVE);       // check if GC in progress, blocked in native mode
    asm.emitBEQ   (+5);                                       // if blocked, br to code to sysYield

    asm.emitCAL   (S0,  VM_Processor.IN_JAVA, 0 );            // S0  <- new state value
    asm.emitSTWCXr(S0,  0, TI);                               // attempt to change state to native
    asm.emitBNE   ( label0 - asm.currentInstructionOffset() );// br if failure -retry lwarx
    asm.emitB     ( 8 + 6 + 7 + 8 + 6 + 1 + 1 );              // branch around code to call sysYield
                                                              // 1 + #instructions to NOW_IN_JAVA

    // branch to here if blocked in native, call sysVirtulaProcessorYield (AIX pthread yield)
    // must save AIX volatile gprs & fprs before the call and restore after
    //
    offset = STACKFRAME_HEADER_SIZE;

    // save volatile GPRS 3-10   ( 8 instructions )
    for (int i = FIRST_AIX_VOLATILE_GPR; i <= LAST_AIX_VOLATILE_GPR; i++) {
      asm.emitST (i, offset, FP);
      offset+=4;
    }

    // save volatile FPRS 1-6   ( 6 instructions )
    for (int i = FIRST_AIX_VOLATILE_FPR; i <= 6; i++) {
      asm.emitSTFD (i, offset, FP);
      offset+=8;
    }

    // note JTOC is the RVM JTOC, set by native code when it branched thru the
    // JNI function pointer to this code
    asm.emitL     (SP, VM_Entrypoints.the_boot_recordField.getOffset(), JTOC); // get boot record address
    asm.emitCAL   (PROCESSOR_REGISTER, 0, JTOC);                    // save JTOC for later
    asm.emitL     (JTOC, VM_Entrypoints.sysTOCField.getOffset(), SP);          // load TOC for syscalls from bootrecord
    asm.emitL     (TI,   VM_Entrypoints.sysVirtualProcessorYieldIPField.getOffset(), SP);  // load addr of function
    asm.emitMTLR  (TI);
    asm.emitBLRL();                                                 // call sysVirtualProcessorYield in sys.C
    asm.emitCAL   (JTOC, 0,PROCESSOR_REGISTER);                     // restore RVM JTOC

    // restore the saved volatile GPRs 3-10 and FPRs 1-6
    offset = STACKFRAME_HEADER_SIZE;

    // restore volatile GPRS 3-10   ( 8 instructions )
    for (int i = FIRST_AIX_VOLATILE_GPR; i <= LAST_AIX_VOLATILE_GPR; i++) {
      asm.emitL  (i, offset, FP);
      offset+=4;
    }

    // restore volatile FPRS 1-6   ( 6 instructions )
    for (int i = FIRST_AIX_VOLATILE_FPR; i <= 6; i++) {
      asm.emitLFD (i, offset, FP);
      offset+=8;
    }

    asm.emitB   ( label0 - asm.currentInstructionOffset() );  // br back to try lwarx again

    // NOW_IN_JAVA:
    // branch to here, after setting status to IN_JAVA
    //
    // GC is not in progress - so we can now reference moveable objects in the heap,
    // such as JNIEnvironment, VM_Thread, VM_Processor.
    //
    // T0 = the address of running threads entry in the jnifunctionpointers array
    // (as of 11/16/00 each thread gets 2 words in this array)
    // compute offset into this array, use this divieded by 2 as offset in threads array,
    // load VM_Thread, and from it load the jniEnv pointer.
    // ...use TI and PROCESSOR_REGISTER as temps for a while
    // ...JTOC is now the RVM JTOC
    //
    asm.emitL  (TI, VM_Entrypoints.JNIFunctionPointersField.getOffset(), JTOC);
    asm.emitSF (TI, TI, T0);                                         // TI <- byte offset into funcPtrs array
    asm.emitSRAI (TI, TI, 1);                                        // divide by 2
    asm.emitL  (PROCESSOR_REGISTER, VM_Entrypoints.threadsField.getOffset(), JTOC);
    asm.emitA  (TI, PROCESSOR_REGISTER, TI);
    asm.emitL  (TI, 0, TI);                                                  // TI <- address of VM_Thread 
    asm.emitL  (T0, VM_Entrypoints.jniEnvField.getOffset(), TI);                        // T0 <- JNIEnv ref (a REAL ref)

    // get pointer to top java frame from JNIEnv, compute offset from current
    // frame pointer (offset to avoid more interior pointers) and save offset
    // in this glue frame
    //
    asm.emitL  (TI, VM_Entrypoints.JNITopJavaFPField.getOffset(), T0);     // get addr of top java frame from JNIEnv
    asm.emitSF (TI, FP, TI);                                    // TI <- offset from current FP
    asm.emitST (TI, JNI_GLUE_FRAME_SIZE-4, FP);                 // store offset at end of glue frame

    // load TI & PR registers from JNIEnv before calling Java JNI Function
    asm.emitL  (TI, VM_Entrypoints.JNIEnvSavedTIField.getOffset(), T0);  
    asm.emitL  (PROCESSOR_REGISTER, VM_Entrypoints.JNIEnvSavedPRField.getOffset(), T0);  

    // check if now running on a native processor, if so we have to
    // transfer back to a blue/RVM processor (via call to becomeRVM)
    //
    asm.emitL     (S0, VM_Entrypoints.processorModeField.getOffset(), PROCESSOR_REGISTER); // get processorMode
    asm.emitCMPI  (S0, VM_Processor.RVM);                // are we still on blue processor
    asm.emitBEQ   (+(6+8+4+6+8+1));                      // skip transfer to blue if on blue
                                                         // 1 + # instructions to ON_RVM_VP

    // save volatile GPRs 3-10 and FPRs 1-6 before calling becomeRVMThread
    offset = STACKFRAME_HEADER_SIZE;

    // save volatile GPRS 3-10   ( 8 instructions )
    for (int i = FIRST_AIX_VOLATILE_GPR; i <= LAST_AIX_VOLATILE_GPR; i++) {
      asm.emitST (i, offset, FP);
      offset+=4;
    }

    // save volatile FPRS 1-6   ( 6 instructions )
    for (int i = FIRST_AIX_VOLATILE_FPR; i <= 6; i++) {
      asm.emitSTFD (i, offset, FP);
      offset+=8;
    }

    // Branch to becomeRVMThread:  we will yield and get rescheduled to execute 
    // on a regular VM_Processor for Java code.  At this point PR, TI & JTOC
    // are valid for executing Java
    //
    asm.emitLtoc2 (S0, VM_Entrypoints.becomeRVMThreadMethod.getOffset());  // always 2 instructions
    asm.emitMTLR  (S0);
    asm.emitBLRL  ();
     
    // At this point, we have resumed execution in a RVM VM_Processor
    // The PR regirster now points to this VM_Processor.  TI & JTOC are still valid.

    // restore the saved volatile GPRs 3-10 and FPRs 1-6
    offset = STACKFRAME_HEADER_SIZE;

    // restore volatile GPRS 3-10   ( 8 instructions )
    for (int i = FIRST_AIX_VOLATILE_GPR; i <= LAST_AIX_VOLATILE_GPR; i++) {
      asm.emitL  (i, offset, FP);
      offset+=4;
    }

    // restore volatile FPRS 1-6   ( 6 instructions )
    for (int i = FIRST_AIX_VOLATILE_FPR; i <= 6; i++) {
      asm.emitLFD (i, offset, FP);
      offset+=8;
    }

    // ON_RVM_VP:
    // branch to here if making jni call on RVM/blue processor	

    // BRANCH TO THE PROLOG FOR THE JNI FUNCTION

    // relative branch and link past the following epilog, to the normal prolog of the method
    // the normal epilog of the method will return to the epilog here to pop the glue stack frame

    // branch count = number of instructions/words that follow + 1
    asm.emitBL( 11 + 4 + 2 + 4 + 1 + 24);      // with restore of only NECESSARY volatile regs

    // RETURN TO HERE FROM EPILOG OF JNI FUNCTION
    // CAUTION:  START OF EPILOG OF GLUE CODE
    // The section of code from here to "END OF EPILOG OF GLUE CODE" is nestled between
    // the glue code prolog and the real JNI code, so any change in the number of instructions
    // is this section must be reflected in the BL instruction above.
    // T0 & T1 (R3 & R4) or F1 contain the return value from the function - DO NOT USE

    // assume: JTOC, TI, and PROCESSOR_REG are valid, and all RVM non-volatile 
    // GPRs and FPRs have been restored.  Our processor state should be ...IN_JAVA so 
    // it is OK to use saved refs to moveable objects (ie. PROCESSOR_REG)

    // establish T2 -> current threads JNIEnv structure, from activeThread field
    // of current processor      
    asm.emitL (T2, VM_Entrypoints.activeThreadField.getOffset(), PROCESSOR_REGISTER);   // T2 <- activeThread of PR
    asm.emitL (T2, VM_Entrypoints.jniEnvField.getOffset(), T2);                         // T2 <- JNIEnvironment 

    // before returning to C, set pointer to top java frame in JNIEnv, using offset
    // saved in this glue frame during transition from C to Java.  GC will use this saved
    // frame pointer if it is necessary to do GC with a processors active thread
    // stuck (and blocked) in native C, ie. GC starts scanning the threads stack at that frame.
    //
    asm.emitL  (T3, JNI_GLUE_FRAME_SIZE-4, FP);                 // load offset from FP to top java frame
    asm.emitA  (T3, FP, T3);                                    // T3 <- address of top java frame
    asm.emitST (T3, VM_Entrypoints.JNITopJavaFPField.getOffset(), T2);     // store TopJavaFP back into JNIEnv



    // Check if nativeAffinity is set:  
    // -if yes, the caller is via CreateJavaVM or AttachCurrentThread
    //  from an external pthread and control must be returned to the C program there
    // -If no, return to C can be done on the current processor
    asm.emitL (S0, VM_Entrypoints.activeThreadField.getOffset(), PROCESSOR_REGISTER);   // T2 <- activeThread of PR
    asm.emitL (S0, VM_Entrypoints.nativeAffinityField.getOffset(), S0);                 // T2 <- nativeAffinity 
    asm.emitCMPI (S0, 0);
    asm.emitBEQ (+17);           // skip if no native affinity

    // check to see if this frame address is the sentinal since there may be no further Java frame below
    asm.emitCMPI (T3, VM_Constants.STACKFRAME_SENTINAL_FP);
    asm.emitBNE(+15);            // normal frame, don't need to move thread back to native VP
    
    // save result GPRS 3-4  and FPRS 1
    offset = STACKFRAME_HEADER_SIZE;
    asm.emitST (T0, offset, FP);
    asm.emitST (T1, offset+4, FP);
    asm.emitST (T2, offset+8, FP);
    asm.emitST (T3, offset+12, FP);
    asm.emitSTFD (FIRST_AIX_VOLATILE_FPR, offset+16, FP);

    // Branch to becomeNativeThread:  we will yield and get rescheduled to execute 
    // on a the VM_Processor for the external pthread code.
    //
    asm.emitLtoc2 (S0, VM_Entrypoints.becomeNativeThreadMethod.getOffset());  // always 2 instructions
    asm.emitMTLR  (S0);
    asm.emitBLRL  ();

    // restore result GPRS 3-4  and FPRS 1
    offset = STACKFRAME_HEADER_SIZE;
    asm.emitL (T0, offset, FP);
    asm.emitL (T1, offset+4, FP);
    asm.emitL (T2, offset+8, FP);
    asm.emitL (T3, offset+12, FP);
    asm.emitLFD (FIRST_AIX_VOLATILE_FPR, offset+16, FP);

    // While in Java (JNI Function), on a RVM Processor, we allow the thread to be migrated
    // to a different Processor. Thus the current processor when returning from Java back
    // may be different from the processor when calling the Java JNI Function. We therefore
    // must update the saved processor registers, in the threads JNIEnvironment and in the
    // preceeding "Java to C" transition frame.
    // Note: must be done after the possible call to becomeNativeThread to pick up the right PR value
    //
    // update the saved PR of the frame if TopJavaFP is valid (branch here from check above)
    
    // check to see if this frame address is the sentinal since there may be no further Java frame below
    asm.emitCMPI (T3, VM_Constants.STACKFRAME_SENTINAL_FP);
    asm.emitBEQ(+3);
    asm.emitL  (S0, 0, T3);                   // get fp for caller of prev J to C transition frame
    asm.emitST (PROCESSOR_REGISTER, -JNI_PR_OFFSET, S0);  // store PR back into transition frame


    asm.emitST (PROCESSOR_REGISTER, VM_Entrypoints.JNIEnvSavedPRField.getOffset(), T2);  // store PR back into JNIEnv


    // change the state of the VP to "in Native". With default jni transitions to
    // native C from Java ALWAYS start on a blue/RVM Processor (status = IN_JAVA)
    // and are never "BLOCKED_IN_JAVA"
    //
    asm.emitL     (T3, VM_Entrypoints.vpStatusAddressField.getOffset(), PROCESSOR_REGISTER); // T3 gets addr vpStatus word
    asm.emitCAL   (S0,  VM_Processor.IN_NATIVE, 0 );              // S0  <- new status value
    asm.emitST    (S0,  0, T3);                                   // change state to native

    asm.emitL     (S0, JNI_GLUE_FRAME_SIZE + CR_OFFSET, FP);
    asm.emitMTCRF (0xff, S0);

    // Restore those AIX nonvolatile registers saved in the prolog above
    // Here we only save & restore ONLY those registers not restored by RVM
    //
    offset = STACKFRAME_HEADER_SIZE + 80;   // skip 20 word volatile reg save area
    for (int i = 13; i <= 16; i++) {
	asm.emitL  (i, offset, FP);                     // 4 instructions
	offset += 4;
    }
    for (int i = 14; i <= 15; i++) {
	asm.emitLFD  (i, offset, FP);                   // 2 instructions
	offset +=8;
    }

    // pop frame
    asm.emitCAL(FP, JNI_GLUE_FRAME_SIZE, FP);

    // load return address & return to caller
    // T0 & T1 (or F1) should still contain the return value
    //
    asm.emitL(T2, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);
    asm.emitMTLR(T2);
    asm.emitBLR (); // branch always, through link register

    //END OF EPILOG OF GLUE CODE

  }  // generateGlueCodeForJNIMethod 
}
