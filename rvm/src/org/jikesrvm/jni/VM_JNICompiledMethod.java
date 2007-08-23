/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.jni;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.runtime.VM_DynamicLink;
import org.jikesrvm.runtime.VM_ExceptionDeliverer;
import org.jikesrvm.runtime.VM_StackBrowser;
import org.vmmagic.pragma.SynchronizedObject;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

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
 */
@SynchronizedObject
public final class VM_JNICompiledMethod extends VM_CompiledMethod {

  public VM_JNICompiledMethod(int id, VM_Method m) {
    super(id, m);
  }

  @Uninterruptible
  public int getCompilerType() {
    return JNI;
  }

  public String getCompilerName() {
    return "JNI compiler";
  }

  @Uninterruptible
  public VM_ExceptionDeliverer getExceptionDeliverer() {
    // this method should never get called.
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  @Uninterruptible
  public void getDynamicLink(VM_DynamicLink dynamicLink, Offset instructionOffset) {
    // this method should never get called.
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  public boolean isWithinUninterruptibleCode(Offset instructionOffset) {
    return false;
  }

  public int findCatchBlockForInstruction(Offset instructionOffset, VM_Type exceptionType) {
    return -1;
  }

  public void printStackTrace(Offset instructionOffset, org.jikesrvm.VM_PrintLN out) {
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

  public void set(VM_StackBrowser browser, Offset instr) {
    browser.setBytecodeIndex(-1);
    browser.setCompiledMethod(this);
    browser.setMethod(method);
  }
}
