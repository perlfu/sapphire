/*
 * (C) Copyright IBM Corp. 2001, 2003
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

/**
 * Information associated with artifical stackframe inserted at the
 * transition from Jave to JNI Native C.  
 *
 * Exception delivery should never see Native C frames, or the Java to C 
 * transition frame.  Native C code is redispatched during exception
 * handling to either process/handle and clear the exception or to return
 * to Java leaving the exception pending.  If it returns to the transition
 * frame with a pending exception. JNI causes an athrow to happen as if it
 * was called at the call site of the call to the native method.
 *
 * @author Ton Ngo
 */
public final class VM_JNICompiledMethod extends VM_CompiledMethod {

  public VM_JNICompiledMethod(int id, VM_Method m) {
    super(id,m);    
  }

  public final int getCompilerType() throws VM_PragmaUninterruptible { 
    return JNI; 
  }

  public final String getCompilerName() {
    return "JNI compiler";
  }

  public final VM_ExceptionDeliverer getExceptionDeliverer() throws VM_PragmaUninterruptible { 
    // this method should never get called.
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }
      
  public final void getDynamicLink(VM_DynamicLink dynamicLink, int instructionOffset) throws VM_PragmaUninterruptible { 
    // this method should never get called.
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  public final int findCatchBlockForInstruction(int instructionOffset, VM_Type exceptionType) {
    return -1;
  }
   
  public final void printStackTrace(int instructionOffset, com.ibm.JikesRVM.PrintLN out) {
    if (method != null) {
      // print name of native method
      out.print("\tat ");
      out.print(method.getDeclaringClass());
      out.print(".");
      out.print(method.getName());
      out.println(" (native method)");
    } else {
      out.println("\tat <native method>");
    }
  }

  public final void set(VM_StackBrowser browser, int instr) {
    browser.setBytecodeIndex(-1);
    browser.setCompiledMethod(this);
    browser.setMethod(method);
  }
}
