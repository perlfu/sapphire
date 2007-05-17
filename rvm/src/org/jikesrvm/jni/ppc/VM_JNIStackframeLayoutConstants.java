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
package org.jikesrvm.jni.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.ppc.VM_RegisterConstants;
import org.jikesrvm.ppc.VM_StackframeLayoutConstants;
import org.jikesrvm.runtime.VM_Memory;

/**
 * Define the stackframes used for JNI transition frames.
 * There are two kinds of transitions Java -> native method
 * and native method -> JNIFunction.
 */
public interface VM_JNIStackframeLayoutConstants extends VM_RegisterConstants, VM_StackframeLayoutConstants {

  /////////////////////////////////////////////////////////////
  //  Java to native function transition
  // VM_JNICompiler.compile(VM_NativeMethod)
  /////////////////////////////////////////////////////////////

  int NATIVE_FRAME_HEADER_SIZE =
      VM.BuildForPowerOpenABI ? 6 * BYTES_IN_ADDRESS /* fp + cr + lr + res + res + toc */ : (VM.BuildForSVR4ABI ? 2 *
                                                                                                                  BYTES_IN_ADDRESS /* fp + lr */ : /* BuildForMachOABI */
                                                                                                                                   6 *
                                                                                                                                   BYTES_IN_ADDRESS /* fp + cp + lr + ??? */);

  // number of volatile registers that may carry parameters to the
  // native code
  // GPR4-10 = 7 words  (does not include R3)
  // FPR1-6  = 12 words
  int JNI_OS_PARAMETER_REGISTER_SIZE =
      (LAST_OS_PARAMETER_GPR - (FIRST_OS_PARAMETER_GPR + 1) + 1) * BYTES_IN_ADDRESS +
      (LAST_OS_VARARG_PARAMETER_FPR - FIRST_OS_PARAMETER_FPR + 1) * BYTES_IN_DOUBLE;

  // offset into the Java to Native glue frame, relative to the Java caller frame
  // the definitions are chained to the first one, JNI_JTOC_OFFSET
  // saved R17-R31 + JNIEnv + GCflag + affinity + saved JTOC
  int JNI_RVM_NONVOLATILE_OFFSET = BYTES_IN_ADDRESS;
  int JNI_ENV_OFFSET =
      JNI_RVM_NONVOLATILE_OFFSET + ((LAST_NONVOLATILE_GPR - FIRST_NONVOLATILE_GPR + 1) * BYTES_IN_ADDRESS);
  int JNI_OS_PARAMETER_REGISTER_OFFSET = JNI_ENV_OFFSET + BYTES_IN_ADDRESS;

  int JNI_PROLOG_RETURN_ADDRESS_OFFSET =
      VM.BuildForPowerOpenABI ? (JNI_OS_PARAMETER_REGISTER_OFFSET + JNI_OS_PARAMETER_REGISTER_SIZE) : -1;  /* UNUSED */
  int JNI_GC_FLAG_OFFSET =
      VM.BuildForPowerOpenABI ? (JNI_PROLOG_RETURN_ADDRESS_OFFSET + BYTES_IN_ADDRESS) : (
          JNI_OS_PARAMETER_REGISTER_OFFSET +
          JNI_OS_PARAMETER_REGISTER_SIZE);

  // SRV4 and MachO save prologue address in lr slot of glue frame (1), see the picture in VM_JNICompiler
  int JNI_MINI_FRAME_POINTER_OFFSET =
      VM.BuildForPowerOpenABI ? -1 /* UNUSED */ : VM_Memory.alignUp(JNI_GC_FLAG_OFFSET + STACKFRAME_HEADER_SIZE,
                                                                    STACKFRAME_ALIGNMENT);

  int JNI_SAVE_AREA_SIZE = VM.BuildForPowerOpenABI ? JNI_GC_FLAG_OFFSET : JNI_MINI_FRAME_POINTER_OFFSET;

  /////////////////////////////////////////////////////////
  // Native code to JNI Function (Java) glue frame
  // VM_JNICompiler.generateGlueCodeForJNIMethod
  /////////////////////////////////////////////////////////

  //   Volatile GPR 3-10 save area  -  8 * BYTES_IN_ADDRESS
  //   Volatile FPR 1-6  save area  -  6 * BYTES_IN_DOUBLE
  int JNI_GLUE_SAVED_VOL_SIZE =
      (LAST_OS_PARAMETER_GPR - FIRST_OS_PARAMETER_GPR + 1) * BYTES_IN_ADDRESS +
      (LAST_OS_VARARG_PARAMETER_FPR - FIRST_OS_PARAMETER_FPR + 1) * BYTES_IN_DOUBLE;

  int JNI_GLUE_RVM_EXTRA_GPRS_SIZE = (LAST_RVM_RESERVED_NV_GPR - FIRST_RVM_RESERVED_NV_GPR + 1) * BYTES_IN_ADDRESS;

  // offset to previous to java frame 1 (* BYTES_IN_ADDRESS)
  int JNI_GLUE_FRAME_OTHERS = 1 * BYTES_IN_ADDRESS;

  int JNI_GLUE_FRAME_SIZE =
      VM_Memory.alignUp(STACKFRAME_HEADER_SIZE +
                        JNI_GLUE_SAVED_VOL_SIZE +
                        JNI_GLUE_RVM_EXTRA_GPRS_SIZE +
                        JNI_GLUE_FRAME_OTHERS, STACKFRAME_ALIGNMENT);

  // offset to caller, where to store offset to previous java frame
  int JNI_GLUE_OFFSET_TO_PREV_JFRAME = -JNI_GLUE_FRAME_OTHERS;

  // offset into the vararg save area within the native to Java glue frame
  // to saved regs GPR 6-10 & FPR 1-6, the volatile regs containing vararg arguments
  //
  int VARARG_AREA_OFFSET = STACKFRAME_HEADER_SIZE + (3 * BYTES_IN_ADDRESS);    // the RVM link area and saved GPR 3-5

}
