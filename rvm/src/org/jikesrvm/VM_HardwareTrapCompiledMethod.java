/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package org.jikesrvm;

import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.classloader.VM_Method;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Offset;

/**
 * Information associated with artifical stackframe inserted by hardware 
 * trap handler.
 *
 * @author Derek Lieber
 * @date 02 Jun 1999 
 */
@SynchronizedObject
final class VM_HardwareTrapCompiledMethod extends VM_CompiledMethod {

  public VM_HardwareTrapCompiledMethod(int id, VM_Method m) {
    super(id,m);    
  }

  @Uninterruptible
  public int getCompilerType() { 
    return TRAP; 
  }

  public String getCompilerName() {
    return "<hardware trap>";
  }

  @Uninterruptible
  public VM_ExceptionDeliverer getExceptionDeliverer() { 
    // this method should never get called, because exception delivery begins
    // at site of exception, which is one frame above artificial "trap" frame
    // corresponding to this compiler-info object
    //
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }
      
  public int findCatchBlockForInstruction(Offset instructionOffset, VM_Type exceptionType) {
    return -1;
  }
   
  @Uninterruptible
  public void getDynamicLink(VM_DynamicLink dynamicLink, Offset instructionOffset) { 
    // this method should never get called, because exception delivery begins
    // at site of exception, which is one frame above artificial "trap" frame
    // corresponding to this compiler-info object
    //
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  public void printStackTrace(Offset instructionOffset, org.jikesrvm.PrintLN out) {
    out.println("\tat <hardware trap>");
  }

  /**
   * Set the stack browser to the innermost logical stack frame of this method
   */
  public void set(VM_StackBrowser browser, Offset instr) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }
       
  /**
   * Advance the VM_StackBrowser up one internal stack frame, if possible
   */
  public boolean up(VM_StackBrowser browser) {
    return false;
  }

}
