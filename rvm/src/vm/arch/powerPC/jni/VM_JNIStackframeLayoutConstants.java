/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
//$Id$
package com.ibm.JikesRVM.jni;

import com.ibm.JikesRVM.*;

/**
 * Define the stackframes used for JNI transition frames.
 * There are two kinds of transitions Java -> native method
 * and native method -> JNIFunction.
 * 
 * @author Dave Grove
 * @author Ton Ngo 
 * @author Steve Smith
 */
public interface VM_JNIStackframeLayoutConstants extends VM_RegisterConstants,
                                                         VM_StackframeLayoutConstants {

  /////////////////////////////////////////////////////////////
  //  Java to native function transition
  // VM_JNICompiler.compile(VM_NativeMethod)
  /////////////////////////////////////////////////////////////
  
  //-#if RVM_WITH_POWEROPEN_ABI
  static final int NATIVE_FRAME_HEADER_SIZE       =  6*BYTES_IN_ADDRESS; // fp + cr + lr + res + res + toc
  //-#elif RVM_WITH_SVR4_ABI
  // native frame header size, used for java-to-native glue frame header 
  static final int NATIVE_FRAME_HEADER_SIZE       =  2*BYTES_IN_ADDRESS;  // fp + lr
  //-#elif RVM_WITH_MACH_O_ABI
  // native frame header size, used for java-to-native glue frame header 
  static final int NATIVE_FRAME_HEADER_SIZE       =  6*BYTES_IN_ADDRESS;  // fp + cp + lr???
  //-#endif

  // number of volatile registers that may carry parameters to the
  // native code
  // GPR4-10 = 7 words  (does not include R3)
  // FPR1-6  = 12 words
  public static final int JNI_OS_PARAMETER_REGISTER_SIZE   =  
    (LAST_OS_PARAMETER_GPR - (FIRST_OS_PARAMETER_GPR + 1) + 1)*BYTES_IN_ADDRESS
        + (LAST_OS_VARARG_PARAMETER_FPR - FIRST_OS_PARAMETER_FPR + 1)*BYTES_IN_DOUBLE ;   
  
  // offset into the Java to Native glue frame, relative to the Java caller frame
  // the definitions are chained to the first one, JNI_JTOC_OFFSET
  // saved R17-R31 + JNIEnv + GCflag + affinity + saved JTOC
  public static final int JNI_RVM_NONVOLATILE_OFFSET       = BYTES_IN_ADDRESS;
  public static final int JNI_ENV_OFFSET                   = JNI_RVM_NONVOLATILE_OFFSET + 
    ((LAST_NONVOLATILE_GPR - FIRST_NONVOLATILE_GPR + 1) * BYTES_IN_ADDRESS);
  public static final int JNI_OS_PARAMETER_REGISTER_OFFSET = JNI_ENV_OFFSET + BYTES_IN_ADDRESS;

  //-#if RVM_WITH_POWEROPEN_ABI
  public static final int JNI_PROLOG_RETURN_ADDRESS_OFFSET  = JNI_OS_PARAMETER_REGISTER_OFFSET + JNI_OS_PARAMETER_REGISTER_SIZE;
  public static final int JNI_GC_FLAG_OFFSET                = JNI_PROLOG_RETURN_ADDRESS_OFFSET  + BYTES_IN_ADDRESS;          // 108
  public static final int JNI_SAVE_AREA_SIZE                = JNI_GC_FLAG_OFFSET;
  //-#endif

  //-#if RVM_WITH_SVR4_ABI || RVM_WITH_MACH_O_ABI
  // LINUX saves prologue address in lr slot of glue frame (1), see the picture
  // in VM_JNICompiler
  public static final int JNI_GC_FLAG_OFFSET                = JNI_OS_PARAMETER_REGISTER_OFFSET + JNI_OS_PARAMETER_REGISTER_SIZE;
  public static final int JNI_MINI_FRAME_POINTER_OFFSET     = 
    VM_Memory.alignUp(JNI_GC_FLAG_OFFSET + STACKFRAME_HEADER_SIZE, STACKFRAME_ALIGNMENT);

  public static final int JNI_SAVE_AREA_SIZE = JNI_MINI_FRAME_POINTER_OFFSET;
  //-#endif
  
  /////////////////////////////////////////////////////////
  // Native code to JNI Function (Java) glue frame
  // VM_JNICompiler.generateGlueCodeForJNIMethod
  /////////////////////////////////////////////////////////
  
  //   Volatile GPR 3-10 save area  -  8 * BYTES_IN_ADDRESS
  //   Volatile FPR 1-6  save area  -  6 * BYTES_IN_DOUBLE
  public static final int JNI_GLUE_SAVED_VOL_SIZE  = 
    (LAST_OS_PARAMETER_GPR - FIRST_OS_PARAMETER_GPR + 1)* BYTES_IN_ADDRESS
    +(LAST_OS_VARARG_PARAMETER_FPR - FIRST_OS_PARAMETER_FPR + 1) * BYTES_IN_DOUBLE;

  public static final int JNI_GLUE_RVM_EXTRA_GPRS_SIZE =
    (LAST_RVM_RESERVED_NV_GPR - FIRST_RVM_RESERVED_NV_GPR + 1) * BYTES_IN_ADDRESS;
  
  // offset to previous to java frame 1 (* BYTES_IN_ADDRESS)
  public static final int JNI_GLUE_FRAME_OTHERS  = 1 * BYTES_IN_ADDRESS;
  
  public static final int JNI_GLUE_FRAME_SIZE =               
    VM_Memory.alignUp(STACKFRAME_HEADER_SIZE
                      + JNI_GLUE_SAVED_VOL_SIZE                      
                      + JNI_GLUE_RVM_EXTRA_GPRS_SIZE
                      + JNI_GLUE_FRAME_OTHERS,
                      STACKFRAME_ALIGNMENT);
  
  // offset to caller, where to store offset to previous java frame 
  public static final int JNI_GLUE_OFFSET_TO_PREV_JFRAME = - JNI_GLUE_FRAME_OTHERS;
        
  // offset into the vararg save area within the native to Java glue frame
  // to saved regs GPR 6-10 & FPR 1-6, the volatile regs containing vararg arguments
  //
  public static final int VARARG_AREA_OFFSET = STACKFRAME_HEADER_SIZE + (3*BYTES_IN_ADDRESS);    // the RVM link area and saved GPR 3-5

}
