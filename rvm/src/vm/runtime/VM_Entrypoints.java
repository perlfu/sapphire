/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Fields and methods of the virtual machine that are needed by 
 * compiler-generated machine code.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
class VM_Entrypoints implements VM_Constants {
  static final VM_Method debugBreakpointMethod = getMethod("LVM;", "debugBreakpoint", "()V");
 
  static final VM_Method instanceOfMethod         = getMethod("LVM_Runtime;", "instanceOf", "(Ljava/lang/Object;I)Z");
  static final VM_Method instanceOfFinalMethod    = getMethod("LVM_Runtime;", "instanceOfFinal", "(Ljava/lang/Object;I)Z");
  static final VM_Method checkcastMethod          = getMethod("LVM_Runtime;", "checkcast", "(Ljava/lang/Object;I)V");
  static final VM_Method checkstoreMethod         = getMethod("LVM_Runtime;", "checkstore", "(Ljava/lang/Object;Ljava/lang/Object;)V");
  static final VM_Method athrowMethod             = getMethod("LVM_Runtime;", "athrow", "(Ljava/lang/Throwable;)V");
  static final VM_Method newScalarMethod          = getMethod("LVM_Runtime;", "newScalar", "(I)Ljava/lang/Object;");
  static final VM_Method quickNewArrayMethod      = getMethod("LVM_Runtime;", "quickNewArray", "(II[Ljava/lang/Object;)Ljava/lang/Object;");
  static final VM_Method quickNewScalarMethod     = getMethod("LVM_Runtime;", "quickNewScalar", "(I[Ljava/lang/Object;Z)Ljava/lang/Object;");
  static final VM_Method unimplementedBytecodeMethod = getMethod("LVM_Runtime;", "unimplementedBytecode", "(I)V");
  static final VM_Method invokeInterfaceMethod    = getMethod("LVM_Runtime;", "invokeInterface", "(Ljava/lang/Object;I)"+INSTRUCTION_ARRAY_SIGNATURE);
  static final VM_Method findItableMethod         = getMethod("LVM_Runtime;", "findITable", "([Ljava/lang/Object;I)[Ljava/lang/Object;");
  static final VM_Method raiseArrayBoundsError    = getMethod("LVM_Runtime;", "raiseArrayIndexOutOfBoundsException", "(I)V");
  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
  static final VM_Method allocAdviceNewScalarMethod = getMethod("LVM_Runtime;", "newScalar", "(II)Ljava/lang/Object;");
  static final VM_Method allocAdviceQuickNewArrayMethod = getMethod("LVM_Runtime;", "quickNewArray", "(II[Ljava/lang/Object;I)Ljava/lang/Object;");
  static final VM_Method allocAdviceQuickNewScalarMethod = getMethod("LVM_Runtime;", "quickNewScalar", "(I[Ljava/lang/Object;I)Ljava/lang/Object;");
  static final VM_Method allocAdviceQuickNewScalarMethodNEW = getMethod("LVM_Runtime;", "quickNewScalar", "(I[Ljava/lang/Object;ZI)Ljava/lang/Object;");
  //-#endif

  static final VM_Method mandatoryInstanceOfInterfaceMethod = getMethod("LVM_DynamicTypeCheck;", "mandatoryInstanceOfInterface", "(LVM_Class;[Ljava/lang/Object;)V");
  static final VM_Method unresolvedInterfaceMethodMethod    = getMethod("LVM_DynamicTypeCheck;", "unresolvedInterfaceMethod", "(I[Ljava/lang/Object;)V");

  static final VM_Method lockMethod = getMethod("LVM_Lock;", "lock", "(Ljava/lang/Object;)V");
  static final VM_Method unlockMethod = getMethod("LVM_Lock;", "unlock", "(Ljava/lang/Object;)V");

  static final VM_Method newArrayArrayMethod   = getMethod("LVM_MultianewarrayHelper;", "newArrayArray", "(III)Ljava/lang/Object;");

  static final VM_Method resolveMethodMethod     = getMethod("LVM_TableBasedDynamicLinker;", "resolveMethod", "(I)V");
  static final VM_Field  methodOffsetsField      = getField("LVM_TableBasedDynamicLinker;", "methodOffsets", "[I");   
  static final VM_Method resolveFieldMethod      = getMethod("LVM_TableBasedDynamicLinker;", "resolveField", "(I)V");
  static final VM_Field  fieldOffsetsField       = getField("LVM_TableBasedDynamicLinker;", "fieldOffsets", "[I");    

  static final VM_Method longMultiplyMethod = getMethod("LVM_Math;", "longMultiply", "(JJ)J");
  static final VM_Method longDivideMethod   = getMethod("LVM_Math;", "longDivide", "(JJ)J");
  static final VM_Method longRemainderMethod= getMethod("LVM_Math;", "longRemainder", "(JJ)J");
  static final VM_Method longToDoubleMethod = getMethod("LVM_Math;", "longToDouble", "(J)D");
  static final VM_Method doubleToIntMethod  = getMethod("LVM_Math;", "doubleToInt", "(D)I");
  static final VM_Method doubleToLongMethod = getMethod("LVM_Math;", "doubleToLong", "(D)J");
  static final VM_Field longOneField        = getField("LVM_Math;", "longOne", "J");  // 1L
  static final VM_Field minusOneField       = getField("LVM_Math;", "minusOne", "F"); // -1.0F
  static final VM_Field zeroFloatField      = getField("LVM_Math;", "zero", "F");     // 0.0F
  static final VM_Field halfFloatField      = getField("LVM_Math;", "half", "F");     // 0.5F
  static final VM_Field oneFloatField       = getField("LVM_Math;", "one", "F");      // 1.0F
  static final VM_Field twoFloatField       = getField("LVM_Math;", "two", "F");      // 2.0F
  static final VM_Field two32Field          = getField("LVM_Math;", "two32", "F");    // 2.0F^32
  static final VM_Field half32Field         = getField("LVM_Math;", "half32", "F");   // 0.5F^32
  static final VM_Field billionthField      = getField("LVM_Math;", "billionth", "D");// 1e-9
  static final VM_Field zeroDoubleField     = getField("LVM_Math;", "zeroD", "D");    // 0.0
  static final VM_Field oneDoubleField      = getField("LVM_Math;", "oneD", "D");     // 1.0
  static final VM_Field maxintField         = getField("LVM_Math;", "maxint", "D");   //  largest double that can be rounded to an int
  static final VM_Field minintField         = getField("LVM_Math;", "minint", "D");   //  smallest double that can be rounded to an int
  static final VM_Field IEEEmagicField      = getField("LVM_Math;", "IEEEmagic", "D");//  IEEEmagic constant
  static final VM_Field I2DconstantField    = getField("LVM_Math;", "I2Dconstant", "D");//  special double value for use in int <--> double conversions
  //-#if RVM_FOR_IA32  
  static final VM_Field FPUControlWordField = getField("LVM_Math;", "FPUControlWord", "I");
  //-#endif
   
  static final VM_Field reflectiveMethodInvokerInstructionsField       = getField("LVM_OutOfLineMachineCode;", "reflectiveMethodInvokerInstructions", INSTRUCTION_ARRAY_SIGNATURE);
  static final VM_Field saveThreadStateInstructionsField               = getField("LVM_OutOfLineMachineCode;", "saveThreadStateInstructions", INSTRUCTION_ARRAY_SIGNATURE);
  static final VM_Field threadSwitchInstructionsField                  = getField("LVM_OutOfLineMachineCode;", "threadSwitchInstructions", INSTRUCTION_ARRAY_SIGNATURE);
  static final VM_Field restoreHardwareExceptionStateInstructionsField = getField("LVM_OutOfLineMachineCode;", "restoreHardwareExceptionStateInstructions", INSTRUCTION_ARRAY_SIGNATURE);
  //-#if RVM_FOR_POWERPC
  static final VM_Field getTimeInstructionsField                       = getField("LVM_OutOfLineMachineCode;", "getTimeInstructions", INSTRUCTION_ARRAY_SIGNATURE);
  //-#endif
  static final VM_Field invokeNativeFunctionInstructionsField          = getField("LVM_OutOfLineMachineCode;", "invokeNativeFunctionInstructions", INSTRUCTION_ARRAY_SIGNATURE);

  static final VM_Field outputLockField  = getField("LVM_Scheduler;", "outputLock", "I");
  //-#if RVM_WITH_STRONG_VOLATILE_SEMANTICS
  static final VM_Field doublewordVolatileMutexField = getField("LVM_Scheduler;", "doublewordVolatileMutex", "LVM_ProcessorLock;");
  //-#else
  static final VM_Field doublewordVolatileMutexField = null; // GACK!
  //-#endif
   
  static final VM_Field deterministicThreadSwitchCountField = getField("LVM_Processor;", "deterministicThreadSwitchCount", "I");
  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  static final VM_Field modifiedOldObjectsTopField = getField("LVM_Processor;", "modifiedOldObjectsTop", "I");
  static final VM_Field modifiedOldObjectsMaxField = getField("LVM_Processor;", "modifiedOldObjectsMax", "I");
  static final VM_Field incDecBufferTopField       = getField("LVM_Processor;", "incDecBufferTop", "I");
  static final VM_Field incDecBufferMaxField       = getField("LVM_Processor;", "incDecBufferMax", "I");
  //-#endif
  static final VM_Field scratchSecondsField        = getField("LVM_Processor;", "scratchSeconds", "D");
  static final VM_Field scratchNanosecondsField    = getField("LVM_Processor;", "scratchNanoseconds", "D");
  static final VM_Field threadSwitchRequestedField = getField("LVM_Processor;", "threadSwitchRequested", "I");
  static final VM_Field activeThreadField          = getField("LVM_Processor;", "activeThread", "LVM_Thread;");
  static final VM_Field activeThreadStackLimitField= getField("LVM_Processor;", "activeThreadStackLimit", "I");
  //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
  static final VM_Field vpStateField               = getField("LVM_Processor;", "vpState", "I");
  //-#else
  // default implementation of jni
  static final VM_Field processorModeField         = getField("LVM_Processor;", "processorMode", "I");
  static final VM_Field vpStatusAddressField       = getField("LVM_Processor;", "vpStatusAddress", "I");
  //-#endif
  //-#if RVM_FOR_IA32  
  static final VM_Field jtocField               = getField("LVM_Processor;", "jtoc", "Ljava/lang/Object;");
  static final VM_Field threadIdField           = getField("LVM_Processor;", "threadId", "I");
  static final VM_Field framePointerField       = getField("LVM_Processor;", "framePointer", "I");
  static final VM_Field hiddenSignatureIdField  = getField("LVM_Processor;", "hiddenSignatureId", "I");
  static final VM_Field arrayIndexTrapParamField= getField("LVM_Processor;", "arrayIndexTrapParam", "I");
  //-#endif
   
  static final VM_Method threadSwitchFromPrologueMethod = getMethod("LVM_Thread;", "threadSwitchFromPrologue", "()V");
  static final VM_Method threadSwitchFromBackedgeMethod = getMethod("LVM_Thread;", "threadSwitchFromBackedge", "()V");
  static final VM_Method threadSwitchFromEpilogueMethod = getMethod("LVM_Thread;", "threadSwitchFromEpilogue", "()V");
  static final VM_Method threadYieldMethod              = getMethod("LVM_Thread;", "yield", "()V");
  static final VM_Method becomeNativeThreadMethod       = getMethod("LVM_Thread;", "becomeNativeThread", "()V");
  static final VM_Method becomeRVMThreadMethod          = getMethod("LVM_Thread;", "becomeRVMThread", "()V");
  static final VM_Field stackLimitField                 = getField("LVM_Thread;", "stackLimit", "I");
  static final VM_Field beingDispatchedField            = getField("LVM_Thread;", "beingDispatched", "Z");
  static final VM_Field threadSlotField                 = getField("LVM_Thread;", "threadSlot", "I");
  static final VM_Field jniEnvField                     = getField("LVM_Thread;", "jniEnv", "LVM_JNIEnvironment;");
  static final VM_Field processorAffinityField          = getField("LVM_Thread;", "processorAffinity", "LVM_Processor;");
  static final VM_Field nativeAffinityField             = getField("LVM_Thread;", "nativeAffinity", "LVM_Processor;");

  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  static final VM_Field areaCurrentAddressField        = getField("LVM_Allocator;", "areaCurrentAddress", "I");
  static final VM_Field matureCurrentAddressField      = getField("LVM_Allocator;", "matureCurrentAddress", "I");
  //-#endif

  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  static final VM_Field finalizerListElementValueField   = getField("LVM_FinalizerListElement;", "value", "I");
  static final VM_Field finalizerListElementPointerField = getField("LVM_FinalizerListElement;", "pointer", "Ljava/lang/Object;");
  //-#endif

  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  static final VM_Field allocCountField                = getField("LVM_BlockControl;", "allocCount", "I");
  //-#endif

  //-#if RVM_WITH_CONCURRENT_GC
  static final VM_Method processIncDecBufferMethod      = getMethod("LVM_RCBuffers;", "processIncDecBuffer", "()V");
  //-#endif

  static final VM_Field processorsField                = getField("LVM_Scheduler;", "processors", "[LVM_Processor;");
  static final VM_Field threadsField                   = getField("LVM_Scheduler;", "threads", "[LVM_Thread;");

  static final VM_Field latestContenderField            = getField("LVM_ProcessorLock;", "latestContender", "LVM_Processor;");
  static final VM_Method processorLockMethod            = getMethod("LVM_ProcessorLock;", "lock", "()V");
  static final VM_Method processorUnlockMethod          = getMethod("LVM_ProcessorLock;", "unlock", "()V");

  static final VM_Field classForTypeField              = getField("LVM_Type;", "classForType", "Ljava/lang/Class;");

  //-#if RVM_WITH_PREMATURE_CLASS_RESOLUTION
  static final VM_Method initializeClassIfNecessaryMethod = getMethod("LVM_Class;", "initializeClassIfNecessary", "(I)V");
  //-#else
  static final VM_Method initializeClassIfNecessaryMethod = null; // GACK
  //-#endif

  static final VM_Field JNIEnvAddressField         = getField("LVM_JNIEnvironment;", "JNIEnvAddress", "I");
  static final VM_Field JNIEnvSavedTIField         = getField("LVM_JNIEnvironment;", "savedTIreg", "I");
  static final VM_Field JNIEnvSavedPRField         = getField("LVM_JNIEnvironment;", "savedPRreg", "LVM_Processor;");
  static final VM_Field JNIRefsField               = getField("LVM_JNIEnvironment;", "JNIRefs", "[I");
  static final VM_Field JNIRefsTopField            = getField("LVM_JNIEnvironment;", "JNIRefsTop", "I");
  static final VM_Field JNIRefsMaxField            = getField("LVM_JNIEnvironment;", "JNIRefsMax", "I");
  static final VM_Field JNIRefsSavedFPField        = getField("LVM_JNIEnvironment;", "JNIRefsSavedFP", "I");
  static final VM_Field JNITopJavaFPField          = getField("LVM_JNIEnvironment;", "JNITopJavaFP", "I");
  static final VM_Field JNIPendingExceptionField   = getField("LVM_JNIEnvironment;", "pendingException", "Ljava/lang/Throwable;");
  static final VM_Field JNIFunctionPointersField   = getField("LVM_JNIEnvironment;", "JNIFunctionPointers", "[I");

  static final VM_Field the_boot_recordField = getField("LVM_BootRecord;", "the_boot_record", "LVM_BootRecord;");
  static final VM_Field globalGCInProgressFlagField     = getField("LVM_BootRecord;", "globalGCInProgressFlag", "I");
  static final VM_Field lockoutProcessorField           = getField("LVM_BootRecord;", "lockoutProcessor", "I");
  static final VM_Field sysVirtualProcessorYieldIPField = getField("LVM_BootRecord;", "sysVirtualProcessorYieldIP", "I");
  static final VM_Field externalSignalFlagField         = getField("LVM_BootRecord;", "externalSignalFlag", "I");
  //-#if RVM_FOR_POWERPC
  static final VM_Field sysTOCField                     = getField("LVM_BootRecord;", "sysTOC", "I");
  //-#endif

  static final VM_Method arrayStoreWriteBarrierMethod = getMethod("LVM_WriteBarrier;", "arrayStoreWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method resolvedPutfieldWriteBarrierMethod = getMethod("LVM_WriteBarrier;", "resolvedPutfieldWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method unresolvedPutfieldWriteBarrierMethod = getMethod("LVM_WriteBarrier;", "unresolvedPutfieldWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method resolvedPutStaticWriteBarrierMethod = getMethod("LVM_WriteBarrier;", "resolvedPutStaticWriteBarrier", "(ILjava/lang/Object;)V");
  static final VM_Method unresolvedPutStaticWriteBarrierMethod = getMethod("LVM_WriteBarrier;", "unresolvedPutStaticWriteBarrier", "(ILjava/lang/Object;)V");

  //-#if RVM_WITH_READ_BARRIER2
  static final VM_Method arrayLoadReadBarrierMethod = getMethod("LVM_ReadBarrier;", "arrayLoadReadBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method resolvedGetfieldReadBarrierMethod = getMethod("LVM_ReadBarrier;", "resolvedGetfieldReadBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method unresolvedGetfieldReadBarrierMethod = getMethod("LVM_ReadBarrier;", "unresolvedGetfieldReadBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method resolvedGetStaticReadBarrierMethod = getMethodVM.getMember("LVM_ReadBarrier;", "resolvedGetStaticReadBarrier", "(ILjava/lang/Object;)V");
  static final VM_Method unresolvedGetStaticReadBarrierMethod = getMethod("LVM_ReadBarrier;", "unresolvedGetStaticReadBarrier", "(ILjava/lang/Object;)V");
  //-#endif RVM_WITH_READ_BARRIER2

  //-#if RVM_WITH_GCTk
  static ADDRESS GCTk_WriteBufferBase;
  static ADDRESS GCTk_BumpPointerBase;
  static ADDRESS GCTk_SyncPointerBase;
  static ADDRESS GCTk_ChunkAllocatorBase;
  static ADDRESS GCTk_TraceBufferBase;
  //-#endif

  private static VM_Method getMethod(String klass, String member, String descriptor) {
    return (VM_Method)VM.getMember(klass, member, descriptor);
  }

  private static VM_Field getField(String klass, String member, String descriptor) {
    return (VM_Field)VM.getMember(klass, member, descriptor);
  }

  static void init() {
    //-#if RVM_WITH_GCTk
    ADDRESS top = VM.getMember("LVM_Processor;", "writeBuffer0", "I").getOffset();
    ADDRESS bot = VM.getMember("LVM_Processor;", "writeBuffer1", "I").getOffset();
    GCTk_WriteBufferBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean discontigious = (((top > bot) && ((top - bot) != 4))
			       || ((top < bot) && ((bot - top) != 4)));
      if (discontigious)
	VM.sysWrite("\n---->"+top+","+bot+"->"+GCTk_WriteBufferBase+"<----\n");
      VM.assert(!discontigious);
    }
    GCTk_TraceBufferBase        = VM.getMember("LGCTk_TraceBuffer;", "bumpPtr_", "I").getOffset();

    top = VM.getMember("LVM_Processor;", "allocBump0", "I").getOffset();
    bot = VM.getMember("LVM_Processor;", "allocBump7", "I").getOffset();
    GCTk_BumpPointerBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean unaligned = (((top > bot) && ((top - bot) != 28)) 
			   || ((top < bot) && ((bot - top) != 28)));
      if (unaligned)
	VM.sysWrite("\n---->"+top+","+bot+"->"+GCTk_BumpPointerBase+"<----\n");
      VM.assert(!unaligned);
    }
  
    top = VM.getMember("LVM_Processor;", "allocSync0", "I").getOffset();
    bot = VM.getMember("LVM_Processor;", "allocSync7", "I").getOffset();
    GCTk_SyncPointerBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean unaligned = (((top > bot) && ((top - bot) != 28)) 
			   || ((top < bot) && ((bot - top) != 28)));
      if (unaligned)
	VM.sysWrite("---->"+top+","+bot+"->"+GCTk_SyncPointerBase+"<----\n");
      VM.assert(!unaligned);
    }
	  
    top = VM.getMember("LGCTk_ChunkAllocator;", "allocChunkStart0", "I").getOffset();
    bot = VM.getMember("LGCTk_ChunkAllocator;", "allocChunkEnd7", "I").getOffset();
    GCTk_ChunkAllocatorBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean unaligned = (((top > bot) && ((top - bot) != 60)) 
			   || ((top < bot) && ((bot - top) != 60)));
      if (unaligned) 
	VM.sysWrite("---->"+top+","+bot+"->"+GCTk_ChunkAllocatorBase+"<----\n");
      VM.assert(!unaligned);
    }
    //-#endif
  }
}
