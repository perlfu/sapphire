/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Register Usage Conventions
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
interface VM_RegisterConstants {
  //--------------------------------------------------------------------------------------------//
  //                              Register usage conventions.                                   //
  //--------------------------------------------------------------------------------------------//

   // Machine instructions.
   //
  static final int    LG_INSTRUCTION_WIDTH = 2;           // log2 of instruction width in bytes, powerPC
  static final String INSTRUCTION_ARRAY_SIGNATURE = "[I"; // for powerPC

   // Condition register thread switch bit (must be a field of a non-volatile condition register).
   //
  static final int THREAD_SWITCH_BIT = 8; // field 0 of condition register 2 [ I think. Bowen, is this right? --DL ]
  // TODO: change to define THREAD_SWITCH_BIT in terms of the following
  static final int THREAD_SWITCH_REGISTER = THREAD_SWITCH_BIT >> 2;
  static final int THREAD_SWITCH_FIELD = THREAD_SWITCH_BIT & 3;

   // General purpose register usage. (GPR's are 32 bits wide).
   //
  static final int REGISTER_ZERO              =  0; // special instruction semantics on this register
  static final int FRAME_POINTER              =  1; // AIX is 1
  static final int JTOC_POINTER               =  2; // AIX is 2
  static final int FIRST_VOLATILE_GPR         =  3; // AIX is 3
  //                                            ...
  static final int LAST_VOLATILE_GPR          = 12; // AIX is 10
  static final int FIRST_SCRATCH_GPR          = 13; // AIX is 11
  static final int LAST_SCRATCH_GPR           = 14; // AIX is 12
  static final int THREAD_ID_REGISTER         = 15;
  static final int PROCESSOR_REGISTER         = 16;
  static final int FIRST_NONVOLATILE_GPR      = 17; // AIX is 14
  //                                            ...
  static final int LAST_NONVOLATILE_GPR       = 31; // AIX is 31
  static final int NUM_GPRS                   = 32;

   // Floating point register usage. (FPR's are 64 bits wide).
   //
  static final int FIRST_SCRATCH_FPR          =  0; // AIX is 0
  static final int LAST_SCRATCH_FPR           =  0; // AIX is 0
  static final int FIRST_VOLATILE_FPR         =  1; // AIX is 1
  //                                            ...
  static final int LAST_VOLATILE_FPR          = 15; // AIX is 13
  static final int FIRST_NONVOLATILE_FPR      = 16; // AIX is 14
  //                                            ...
  static final int LAST_NONVOLATILE_FPR       = 31; // AIX is 31
  static final int NUM_FPRS                   = 32;


  // condition registers
  // TODO: fill table
  static final int NUM_CRS                    = 8;
   
   // special   registers (user visible)
  static final int NUM_SPECIALS               = 8;


  // AIX register convention (for mapping parameters in JNI calls)
  static final int FIRST_AIX_VOLATILE_GPR         =  3; 
  static final int LAST_AIX_VOLATILE_GPR          = 10; 
  static final int FIRST_AIX_VOLATILE_FPR         =  1; 
  static final int LAST_AIX_VOLATILE_FPR          = 13; 
  static final int AIX_FRAME_HEADER_SIZE          = 24;  // fp + cr + lr + res + res + toc = 6 * 4

  //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
  // alternate implementation of jni

  // saved offset to top java frame = 1 word
  // RVM link area = STACKFRAME_HEADER_SIZE
  // volatile GPR3-5 save area = 3 words
  // vararg save area = 11 words      (partially shared for saving volatiles GPR6-10 and FPR1-3)
  // volatile FPR4-6 save area = 12 words
  // GPR save area = 19 words
  // FPR save area = 36 words
  static final int NATIVE_TO_JAVA_GLUE_FRAME_SIZE = 
    VM_StackframeLayoutConstants.STACKFRAME_HEADER_SIZE + ((1+3+11+12+19+36) * 4);

   // offset into the vararg save area within the native to Java glue frame
   // right after the RVM link area and saved volatile R3-R5
  static final int VARARG_AREA_OFFSET = VM_StackframeLayoutConstants.STACKFRAME_HEADER_SIZE + (3*4);

  // use 3 words in the header
  // TODO:  change to 4 words later to match normal convention
  static final int GLUE_FRAME_HEADER_SIZE = 12;

//-#else
// default implementation of jni

     // Native code to JNI Function (Java) glue frame
     //
     //   RVM link area    -  STACKFRAME_HEADER_SIZE
     //   Volatile GPR 3-10 save area  -  8 words
     //   Volatile FPR 1-6  save area  - 12 words
     //   Non-Volatile GPR 13-16 save area  4 words   for AIX non-vol GPR not restored by RVM
     //   Non-Volatile FPR 14-15 save area  4 words   for AIX non-vol FPR not restored by RVM
     //   padding                           1 word
     //   offset to previous to java frame  1 word    the preceeding java to native transition frame
     //
  static final int JNI_GLUE_FRAME_SIZE = 
    VM_StackframeLayoutConstants.STACKFRAME_HEADER_SIZE + ((8+12+4+4+1+1)*4);

     // offset into the vararg save area within the native to Java glue frame
     // to saved regs GPR 6-10 & FPR 1-6, the volatile regs containing vararg arguments
     //
  static final int VARARG_AREA_OFFSET = 
    VM_StackframeLayoutConstants.STACKFRAME_HEADER_SIZE + (3*4);    // the RVM link area and saved GPR 3-5

//-#endif

     // number of volatile registers that may carry parameters and that need to be saved
     // and restored for the thread reschedule from Java VM_Processor to native VM_Processor
     // GPR4-10 = 7 words  (does not include R3)
     // FPR1-6  = 12 words
  static final int JNI_AIX_VOLATILE_REGISTER_SIZE   =  
    ((LAST_AIX_VOLATILE_GPR - (FIRST_AIX_VOLATILE_GPR + 1) + 1 + 12) * 4) ;   


     // offset into the Java to Native glue frame, relative to the Java caller frame
     // the definitions are chained to the first one, JNI_JTOC_OFFSET
     // saved R17-R31 + R16 + GCflag + affinity + saved JTOC + saved SP

  static final int JNI_JTOC_OFFSET                  = 4;
  static final int JNI_SP_OFFSET                    = JNI_JTOC_OFFSET + 4;  // at 8
  static final int JNI_RVM_NONVOLATILE_OFFSET       = JNI_SP_OFFSET + 4;    // at 12
  static final int JNI_PR_OFFSET                    = JNI_RVM_NONVOLATILE_OFFSET + 
    ((LAST_NONVOLATILE_GPR - FIRST_NONVOLATILE_GPR + 1) * 4);             // at 72
  static final int JNI_AIX_VOLATILE_REGISTER_OFFSET = JNI_PR_OFFSET + 4;    // at 76: save 7 register 4-10
  static final int JNI_AFFINITY_OFFSET = JNI_AIX_VOLATILE_REGISTER_OFFSET + JNI_AIX_VOLATILE_REGISTER_SIZE; // at 104
  static final int JNI_PROLOG_RETURN_ADDRESS_OFFSET  = JNI_AFFINITY_OFFSET + 4;          // 108
  static final int JNI_GC_FLAG_OFFSET  = JNI_PROLOG_RETURN_ADDRESS_OFFSET  + 4;          // 112

  // size in byte of the whole save area for the Java to C glue frame
  // static final int JNI_SAVE_AREA_OFFSET = (4*(LAST_NONVOLATILE_GPR-FIRST_NONVOLATILE_GPR+6)
  //                                             +JNI_AIX_VOLATILE_REGISTER_SIZE);
  static final int JNI_SAVE_AREA_SIZE = JNI_GC_FLAG_OFFSET;

  // Register mnemonics (for use by debugger).
  //
  static final String [] GPR_NAMES = {
    "00", "FP", "JT", "T0", "T1", "T2", "T3", "V4",
    "V5", "V6", "V7", "V8", "V9", "S0", "SP", "TI",
    "PR", "NE", "ND", "NC", "NB", "NA", "N9", "N8",
    "N7", "N6", "N5", "N4", "N3", "N2", "N1", "N0"
  };

  static final String [] FPR_NAMES = {
    "F00",  "FV0",  "FV1",  "FV2",  "FV3",  "FV4",  "FN19", "FN18",
    "FN17", "FN16", "FN15", "FN14", "FN13", "FN12", "FN11", "FN10",
    "FNF",  "FNE",  "FND",  "FNC",  "FNB",  "FNA",  "FN9",  "FN8",
    "FN7",  "FN6",  "FN5",  "FN4",  "FN3",  "FN2",  "FN1",  "FN0"
  };

}

