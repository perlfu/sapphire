/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Fields and methods of the virtual machine that are needed by 
 * compiler-generated machine code or C runtime code.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public class VM_Entrypoints implements VM_Constants {
  public static final VM_Method debugBreakpointMethod = getMethod("Lcom/ibm/JikesRVM/VM;", "debugBreakpoint", "()V");
  public static final VM_Method bootMethod            = getMethod("Lcom/ibm/JikesRVM/VM;", "boot", "()V");

  public static final VM_Field magicObjectRemapperField = getField("Lcom/ibm/JikesRVM/VM_Magic;", "objectAddressRemapper","Lcom/ibm/JikesRVM/VM_ObjectAddressRemapper;");
 
  public static final VM_Method instanceOfMethod         = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "instanceOf", "(Ljava/lang/Object;I)Z");
  public static final VM_Method instanceOfFinalMethod    = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "instanceOfFinal", "(Ljava/lang/Object;I)Z");
  public static final VM_Method checkcastMethod          = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "checkcast", "(Ljava/lang/Object;I)V");
  public static final VM_Method checkcastFinalMethod     = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "checkcastFinal", "(Ljava/lang/Object;I)V");
  public static final VM_Method checkstoreMethod         = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "checkstore", "(Ljava/lang/Object;Ljava/lang/Object;)V");
  public static final VM_Method athrowMethod             = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "athrow", "(Ljava/lang/Throwable;)V");
  public static final VM_Method newScalarMethod          = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "newScalar", "(I)Ljava/lang/Object;");
  public static final VM_Method quickNewArrayMethod      = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "quickNewArray", "(II[Ljava/lang/Object;)Ljava/lang/Object;");
  public static final VM_Method quickNewScalarMethod     = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "quickNewScalar", "(I[Ljava/lang/Object;Z)Ljava/lang/Object;");
  public static final VM_Method unimplementedBytecodeMethod = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "unimplementedBytecode", "(I)V");
  public static final VM_Method unexpectedAbstractMethodCallMethod = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "unexpectedAbstractMethodCall", "()V");
  public static final VM_Method raiseNullPointerException= getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "raiseNullPointerException", "()V");
  public static final VM_Method raiseArrayBoundsException= getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "raiseArrayIndexOutOfBoundsException", "(I)V");
  public static final VM_Method raiseArithmeticException = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "raiseArithmeticException", "()V");
  public static final VM_Method raiseAbstractMethodError = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "raiseAbstractMethodError", "()V");
  public static final VM_Method raiseIllegalAccessError  = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "raiseIllegalAccessError", "()V");
  public static final VM_Method deliverHardwareExceptionMethod = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "deliverHardwareException", "(II)V");
  public static final VM_Method unlockAndThrowMethod      = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "unlockAndThrow", "(Ljava/lang/Object;Ljava/lang/Throwable;)V");

  public static final VM_Method invokeInterfaceMethod                          = getMethod("Lcom/ibm/JikesRVM/VM_InterfaceInvocation;", "invokeInterface", "(Ljava/lang/Object;I)"+INSTRUCTION_ARRAY_SIGNATURE);
  public static final VM_Method findItableMethod                               = getMethod("Lcom/ibm/JikesRVM/VM_InterfaceInvocation;", "findITable", "([Ljava/lang/Object;I)[Ljava/lang/Object;");
  public static final VM_Method invokeinterfaceImplementsTestMethod            = getMethod("Lcom/ibm/JikesRVM/VM_InterfaceInvocation;", "invokeinterfaceImplementsTest", "(Lcom/ibm/JikesRVM/VM_Class;[Ljava/lang/Object;)V");
  public static final VM_Method unresolvedInvokeinterfaceImplementsTestMethod  = getMethod("Lcom/ibm/JikesRVM/VM_InterfaceInvocation;", "unresolvedInvokeinterfaceImplementsTest", "(I[Ljava/lang/Object;)V");

  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
  static final VM_Method allocAdviceNewScalarMethod = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "newScalar", "(II)Ljava/lang/Object;");
  static final VM_Method allocAdviceQuickNewArrayMethod = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "quickNewArray", "(II[Ljava/lang/Object;I)Ljava/lang/Object;");
  static final VM_Method allocAdviceQuickNewScalarMethod = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "quickNewScalar", "(I[Ljava/lang/Object;I)Ljava/lang/Object;");
  static final VM_Method allocAdviceQuickNewScalarMethodNEW = getMethod("Lcom/ibm/JikesRVM/VM_Runtime;", "quickNewScalar", "(I[Ljava/lang/Object;ZI)Ljava/lang/Object;");
  //-#endif

  public static final VM_Method instanceOfUnresolvedMethod         = getMethod("Lcom/ibm/JikesRVM/VM_DynamicTypeCheck;", "instanceOfUnresolved", "(Lcom/ibm/JikesRVM/VM_Class;[Ljava/lang/Object;)Z");
  public static final VM_Method instanceOfArrayMethod              = getMethod("Lcom/ibm/JikesRVM/VM_DynamicTypeCheck;", "instanceOfArray", "(Lcom/ibm/JikesRVM/VM_Class;ILcom/ibm/JikesRVM/VM_Type;)Z");
  public static final VM_Method instanceOfUnresolvedArrayMethod    = getMethod("Lcom/ibm/JikesRVM/VM_DynamicTypeCheck;", "instanceOfUnresolvedArray", "(Lcom/ibm/JikesRVM/VM_Class;ILcom/ibm/JikesRVM/VM_Type;)Z");

  public static final VM_Method lockMethod          = getMethod("Lcom/ibm/JikesRVM/VM_ObjectModel;", "genericLock", "(Ljava/lang/Object;)V");
  public static final VM_Method unlockMethod        = getMethod("Lcom/ibm/JikesRVM/VM_ObjectModel;", "genericUnlock", "(Ljava/lang/Object;)V");

  public static final VM_Method inlineLockMethod    = getMethod("Lcom/ibm/JikesRVM/VM_ThinLock;", "inlineLock", "(Ljava/lang/Object;I)V");
  public static final VM_Method inlineUnlockMethod  = getMethod("Lcom/ibm/JikesRVM/VM_ThinLock;", "inlineUnlock", "(Ljava/lang/Object;I)V");

  public static final VM_Method newArrayArrayMethod   = getMethod("Lcom/ibm/JikesRVM/VM_MultianewarrayHelper;", "newArrayArray", "(III)Ljava/lang/Object;");

  public static final VM_Method lazyMethodInvokerMethod         = getMethod("Lcom/ibm/JikesRVM/VM_DynamicLinker;", "lazyMethodInvoker", "()V");
  public static final VM_Method unimplementedNativeMethodMethod = getMethod("Lcom/ibm/JikesRVM/VM_DynamicLinker;", "unimplementedNativeMethod", "()V");

  public static final VM_Method resolveMethodMethod     = getMethod("Lcom/ibm/JikesRVM/VM_TableBasedDynamicLinker;", "resolveMethod", "(I)V");
  public static final VM_Field  methodOffsetsField      = getField("Lcom/ibm/JikesRVM/VM_TableBasedDynamicLinker;", "methodOffsets", "[I");   
  public static final VM_Method resolveFieldMethod      = getMethod("Lcom/ibm/JikesRVM/VM_TableBasedDynamicLinker;", "resolveField", "(I)V");
  public static final VM_Field  fieldOffsetsField       = getField("Lcom/ibm/JikesRVM/VM_TableBasedDynamicLinker;", "fieldOffsets", "[I");    

  public static final VM_Field longOneField        = getField("Lcom/ibm/JikesRVM/VM_Math;", "longOne", "J");  // 1L
  public static final VM_Field minusOneField       = getField("Lcom/ibm/JikesRVM/VM_Math;", "minusOne", "F"); // -1.0F
  public static final VM_Field zeroFloatField      = getField("Lcom/ibm/JikesRVM/VM_Math;", "zero", "F");     // 0.0F
  public static final VM_Field halfFloatField      = getField("Lcom/ibm/JikesRVM/VM_Math;", "half", "F");     // 0.5F
  public static final VM_Field oneFloatField       = getField("Lcom/ibm/JikesRVM/VM_Math;", "one", "F");      // 1.0F
  public static final VM_Field twoFloatField       = getField("Lcom/ibm/JikesRVM/VM_Math;", "two", "F");      // 2.0F
  public static final VM_Field two32Field          = getField("Lcom/ibm/JikesRVM/VM_Math;", "two32", "F");    // 2.0F^32
  public static final VM_Field half32Field         = getField("Lcom/ibm/JikesRVM/VM_Math;", "half32", "F");   // 0.5F^32
  public static final VM_Field billionthField      = getField("Lcom/ibm/JikesRVM/VM_Math;", "billionth", "D");// 1e-9
  public static final VM_Field zeroDoubleField     = getField("Lcom/ibm/JikesRVM/VM_Math;", "zeroD", "D");    // 0.0
  public static final VM_Field oneDoubleField      = getField("Lcom/ibm/JikesRVM/VM_Math;", "oneD", "D");     // 1.0
  public static final VM_Field maxintField         = getField("Lcom/ibm/JikesRVM/VM_Math;", "maxint", "D");   //  largest double that can be rounded to an int
  public static final VM_Field minintField         = getField("Lcom/ibm/JikesRVM/VM_Math;", "minint", "D");   //  smallest double that can be rounded to an int
  public static final VM_Field IEEEmagicField      = getField("Lcom/ibm/JikesRVM/VM_Math;", "IEEEmagic", "D");//  IEEEmagic constant
  public static final VM_Field I2DconstantField    = getField("Lcom/ibm/JikesRVM/VM_Math;", "I2Dconstant", "D");//  special double value for use in int <--> double conversions
  //-#if RVM_FOR_IA32  
  public static final VM_Field FPUControlWordField = getField("Lcom/ibm/JikesRVM/VM_Math;", "FPUControlWord", "I");
  //-#endif
   
  public static final VM_Field reflectiveMethodInvokerInstructionsField       = getField("Lcom/ibm/JikesRVM/VM_OutOfLineMachineCode;", "reflectiveMethodInvokerInstructions", INSTRUCTION_ARRAY_SIGNATURE);
  public static final VM_Field saveThreadStateInstructionsField               = getField("Lcom/ibm/JikesRVM/VM_OutOfLineMachineCode;", "saveThreadStateInstructions", INSTRUCTION_ARRAY_SIGNATURE);
  public static final VM_Field threadSwitchInstructionsField                  = getField("Lcom/ibm/JikesRVM/VM_OutOfLineMachineCode;", "threadSwitchInstructions", INSTRUCTION_ARRAY_SIGNATURE);
  public static final VM_Field restoreHardwareExceptionStateInstructionsField = getField("Lcom/ibm/JikesRVM/VM_OutOfLineMachineCode;", "restoreHardwareExceptionStateInstructions", INSTRUCTION_ARRAY_SIGNATURE);
  //-#if RVM_FOR_POWERPC
  public static final VM_Field getTimeInstructionsField                       = getField("Lcom/ibm/JikesRVM/VM_OutOfLineMachineCode;", "getTimeInstructions", INSTRUCTION_ARRAY_SIGNATURE);
  //-#endif
  public static final VM_Field invokeNativeFunctionInstructionsField          = getField("Lcom/ibm/JikesRVM/VM_OutOfLineMachineCode;", "invokeNativeFunctionInstructions", INSTRUCTION_ARRAY_SIGNATURE);

  public static final VM_Field deterministicThreadSwitchCountField = getField("Lcom/ibm/JikesRVM/VM_Processor;", "deterministicThreadSwitchCount", "I");
  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  public static final VM_Field modifiedOldObjectsTopField = getField("Lcom/ibm/JikesRVM/VM_Processor;", "modifiedOldObjectsTop", "Lcom/ibm/JikesRVM/VM_Address;");
  public static final VM_Field modifiedOldObjectsMaxField = getField("Lcom/ibm/JikesRVM/VM_Processor;", "modifiedOldObjectsMax", "Lcom/ibm/JikesRVM/VM_Address;");
  //-#endif
  public static final VM_Field scratchSecondsField        = getField("Lcom/ibm/JikesRVM/VM_Processor;", "scratchSeconds", "D");
  public static final VM_Field scratchNanosecondsField    = getField("Lcom/ibm/JikesRVM/VM_Processor;", "scratchNanoseconds", "D");
  public static final VM_Field threadSwitchRequestedField = getField("Lcom/ibm/JikesRVM/VM_Processor;", "threadSwitchRequested", "I");
  public static final VM_Field activeThreadField          = getField("Lcom/ibm/JikesRVM/VM_Processor;", "activeThread", "Lcom/ibm/JikesRVM/VM_Thread;");
  public static final VM_Field activeThreadStackLimitField= getField("Lcom/ibm/JikesRVM/VM_Processor;", "activeThreadStackLimit", "Lcom/ibm/JikesRVM/VM_Address;");
  public static final VM_Field pthreadIDField             = getField("Lcom/ibm/JikesRVM/VM_Processor;", "pthread_id", "I");
  public static final VM_Field epochField                 = getField("Lcom/ibm/JikesRVM/VM_Processor;", "epoch", "I");
  public static final VM_Field processorModeField         = getField("Lcom/ibm/JikesRVM/VM_Processor;", "processorMode", "I");
  public static final VM_Field vpStatusAddressField       = getField("Lcom/ibm/JikesRVM/VM_Processor;", "vpStatusAddress", "Lcom/ibm/JikesRVM/VM_Address;");
  //-#if RVM_FOR_IA32
  public static final VM_Field processorThreadIdField     = getField("Lcom/ibm/JikesRVM/VM_Processor;", "threadId", "I");
  public static final VM_Field processorFPField           = getField("Lcom/ibm/JikesRVM/VM_Processor;", "framePointer", "Lcom/ibm/JikesRVM/VM_Address;");
  public static final VM_Field processorJTOCField         = getField("Lcom/ibm/JikesRVM/VM_Processor;", "jtoc", "Ljava/lang/Object;");
  public static final VM_Field processorTrapParamField    = getField("Lcom/ibm/JikesRVM/VM_Processor;", "arrayIndexTrapParam", "I");
  public static final VM_Field jtocField               = getField("Lcom/ibm/JikesRVM/VM_Processor;", "jtoc", "Ljava/lang/Object;");
  public static final VM_Field threadIdField           = getField("Lcom/ibm/JikesRVM/VM_Processor;", "threadId", "I");
  public static final VM_Field framePointerField       = getField("Lcom/ibm/JikesRVM/VM_Processor;", "framePointer", "Lcom/ibm/JikesRVM/VM_Address;");
  public static final VM_Field hiddenSignatureIdField  = getField("Lcom/ibm/JikesRVM/VM_Processor;", "hiddenSignatureId", "I");
  public static final VM_Field arrayIndexTrapParamField= getField("Lcom/ibm/JikesRVM/VM_Processor;", "arrayIndexTrapParam", "I");
  //-#endif
   
  public static final VM_Method threadSwitchFromPrologueMethod = getMethod("Lcom/ibm/JikesRVM/VM_Thread;", "threadSwitchFromPrologue", "()V");
  public static final VM_Method threadSwitchFromBackedgeMethod = getMethod("Lcom/ibm/JikesRVM/VM_Thread;", "threadSwitchFromBackedge", "()V");
  public static final VM_Method threadSwitchFromEpilogueMethod = getMethod("Lcom/ibm/JikesRVM/VM_Thread;", "threadSwitchFromEpilogue", "()V");
  public static final VM_Method threadYieldMethod              = getMethod("Lcom/ibm/JikesRVM/VM_Thread;", "yield", "()V");
  public static final VM_Method becomeNativeThreadMethod       = getMethod("Lcom/ibm/JikesRVM/VM_Thread;", "becomeNativeThread", "()V");
  public static final VM_Method becomeRVMThreadMethod          = getMethod("Lcom/ibm/JikesRVM/VM_Thread;", "becomeRVMThread", "()V");

  public static final VM_Method threadStartoffMethod           = getMethod("Lcom/ibm/JikesRVM/VM_Thread;", "startoff", "()V");
  public static final VM_Field threadStackField                = getField("Lcom/ibm/JikesRVM/VM_Thread;", "stack", "[I");
  public static final VM_Field stackLimitField                 = getField("Lcom/ibm/JikesRVM/VM_Thread;", "stackLimit", "Lcom/ibm/JikesRVM/VM_Address;");

  public static final VM_Field beingDispatchedField            = getField("Lcom/ibm/JikesRVM/VM_Thread;", "beingDispatched", "Z");
  public static final VM_Field threadSlotField                 = getField("Lcom/ibm/JikesRVM/VM_Thread;", "threadSlot", "I");
  public static final VM_Field jniEnvField                     = getField("Lcom/ibm/JikesRVM/VM_Thread;", "jniEnv", "Lcom/ibm/JikesRVM/VM_JNIEnvironment;");
  public static final VM_Field processorAffinityField          = getField("Lcom/ibm/JikesRVM/VM_Thread;", "processorAffinity", "Lcom/ibm/JikesRVM/VM_Processor;");
  public static final VM_Field nativeAffinityField             = getField("Lcom/ibm/JikesRVM/VM_Thread;", "nativeAffinity", "Lcom/ibm/JikesRVM/VM_Processor;");
  public static final VM_Field threadContextRegistersField     = getField("Lcom/ibm/JikesRVM/VM_Thread;", "contextRegisters", "Lcom/ibm/JikesRVM/VM_Registers;");
  public static final VM_Field threadHardwareExceptionRegistersField = getField("Lcom/ibm/JikesRVM/VM_Thread;", "hardwareExceptionRegisters", "Lcom/ibm/JikesRVM/VM_Registers;");

  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  public static final VM_Field contiguousHeapCurrentField      = getField("Lcom/ibm/JikesRVM/memoryManagers/VM_ContiguousHeap;", "current", "Lcom/ibm/JikesRVM/VM_Address;");
  //-#endif

  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  public static final VM_Field finalizerListElementValueField   = getField("Lcom/ibm/JikesRVM/memoryManagers/VM_FinalizerListElement;", "value", "Lcom/ibm/JikesRVM/VM_Address;");
  public static final VM_Field finalizerListElementPointerField = getField("Lcom/ibm/JikesRVM/memoryManagers/VM_FinalizerListElement;", "pointer", "Ljava/lang/Object;");
  //-#endif

  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  public static final VM_Field allocCountField                = getField("Lcom/ibm/JikesRVM/memoryManagers/VM_BlockControl;", "allocCount", "I");
  //-#endif

  public static final VM_Field registersIPField   = getField("Lcom/ibm/JikesRVM/VM_Registers;",   "ip",  "Lcom/ibm/JikesRVM/VM_Address;");
  public static final VM_Field registersFPRsField = getField("Lcom/ibm/JikesRVM/VM_Registers;", "fprs", "[D");
  public static final VM_Field registersGPRsField = getField("Lcom/ibm/JikesRVM/VM_Registers;", "gprs", "[I");
  public static final VM_Field registersInUseField= getField("Lcom/ibm/JikesRVM/VM_Registers;", "inuse", "Z");
  //-#if RVM_FOR_POWERPC
  public static final VM_Field registersLRField   = getField("Lcom/ibm/JikesRVM/VM_Registers;", "lr", "Lcom/ibm/JikesRVM/VM_Address;");
  //-#endif
  //-#if RVM_FOR_IA32
  public static final VM_Field registersFPField   = getField("Lcom/ibm/JikesRVM/VM_Registers;",   "fp",  "Lcom/ibm/JikesRVM/VM_Address;");
  //-#endif

  //-#if RVM_WITH_CONCURRENT_GC
  static final VM_Field incDecBufferTopField            = getField("Lcom/ibm/JikesRVM/VM_Processor;", "incDecBufferTop", "Lcom/ibm/JikesRVM/VM_Address;");
  static final VM_Field incDecBufferMaxField            = getField("Lcom/ibm/JikesRVM/VM_Processor;", "incDecBufferMax", "Lcom/ibm/JikesRVM/VM_Address;");
  static final VM_Method processIncDecBufferMethod      = getMethod("Lcom/ibm/JikesRVM/VM_RCBuffers;", "processIncDecBuffer", "()V");
  static final VM_Method RCGC_aastoreMethod             = getMethod("Lcom/ibm/JikesRVM/VM_OptRCWriteBarrier;", "aastore", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method RCGC_resolvedPutfieldMethod    = getMethod("Lcom/ibm/JikesRVM/VM_OptRCWriteBarrier;", "resolvedPutfield", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method RCGC_unresolvedPutfieldMethod  = getMethod("Lcom/ibm/JikesRVM/VM_OptRCWriteBarrier;", "unresolvedPutfield", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method RCGC_resolvedPutstaticMethod   = getMethod("Lcom/ibm/JikesRVM/VM_OptRCWriteBarrier;", "resolvedPutstatic", "(ILjava/lang/Object;)V");
  static final VM_Method RCGC_unresolvedPutstaticMethod = getMethod("Lcom/ibm/JikesRVM/VM_OptRCWriteBarrier;", "unresolvedPutstatic", "(ILjava/lang/Object;)V");
  //-#endif

  static final VM_Field outputLockField                = getField("Lcom/ibm/JikesRVM/VM_Scheduler;", "outputLock", "I");
  //-#if RVM_WITH_STRONG_VOLATILE_SEMANTICS
  static final VM_Field doublewordVolatileMutexField   = getField("Lcom/ibm/JikesRVM/VM_Scheduler;", "doublewordVolatileMutex", "Lcom/ibm/JikesRVM/VM_ProcessorLock;");
  //-#else
  static final VM_Field doublewordVolatileMutexField   = null; // GACK!
  //-#endif
  public static final VM_Field processorsField                = getField("Lcom/ibm/JikesRVM/VM_Scheduler;", "processors", "[Lcom/ibm/JikesRVM/VM_Processor;");
  public static final VM_Field threadsField                   = getField("Lcom/ibm/JikesRVM/VM_Scheduler;", "threads", "[Lcom/ibm/JikesRVM/VM_Thread;");
  public static final VM_Field debugRequestedField            = getField("Lcom/ibm/JikesRVM/VM_Scheduler;", "debugRequested", "Z");
  public static final VM_Field attachThreadRequestedField     = getField("Lcom/ibm/JikesRVM/VM_Scheduler;", "attachThreadRequested", "Lcom/ibm/JikesRVM/VM_Address;");
  public static final VM_Method dumpStackAndDieMethod         = getMethod("Lcom/ibm/JikesRVM/VM_Scheduler;", "dumpStackAndDie", "(Lcom/ibm/JikesRVM/VM_Address;)V");

  public static final VM_Field latestContenderField            = getField("Lcom/ibm/JikesRVM/VM_ProcessorLock;", "latestContender", "Lcom/ibm/JikesRVM/VM_Processor;");
  public static final VM_Method processorLockMethod            = getMethod("Lcom/ibm/JikesRVM/VM_ProcessorLock;", "lock", "()V");
  public static final VM_Method processorUnlockMethod          = getMethod("Lcom/ibm/JikesRVM/VM_ProcessorLock;", "unlock", "()V");

  public static final VM_Field classForTypeField              = getField("Lcom/ibm/JikesRVM/VM_Type;", "classForType", "Ljava/lang/Class;");
  public static final VM_Field depthField                     = getField("Lcom/ibm/JikesRVM/VM_Type;", "depth", "I");
  public static final VM_Field idField                        = getField("Lcom/ibm/JikesRVM/VM_Type;", "dictionaryId", "I");
  public static final VM_Field dimensionField                 = getField("Lcom/ibm/JikesRVM/VM_Type;", "dimension", "I");

  public static final VM_Field innermostElementTypeField      = getField("Lcom/ibm/JikesRVM/VM_Array;", "innermostElementType", "Lcom/ibm/JikesRVM/VM_Type;");


  //-#if RVM_WITH_PREMATURE_CLASS_RESOLUTION
  static final VM_Method initializeClassIfNecessaryMethod = getMethod("Lcom/ibm/JikesRVM/VM_Class;", "initializeClassIfNecessary", "(I)V");
  //-#else
  public static final VM_Method initializeClassIfNecessaryMethod = null; // GACK
  //-#endif

  static final VM_Field JNIEnvAddressField         = getField("Lcom/ibm/JikesRVM/VM_JNIEnvironment;", "JNIEnvAddress", "Lcom/ibm/JikesRVM/VM_Address;");
  static final VM_Field JNIEnvSavedTIField         = getField("Lcom/ibm/JikesRVM/VM_JNIEnvironment;", "savedTIreg", "I");
  static final VM_Field JNIEnvSavedPRField         = getField("Lcom/ibm/JikesRVM/VM_JNIEnvironment;", "savedPRreg", "Lcom/ibm/JikesRVM/VM_Processor;");
  static final VM_Field JNIRefsField               = getField("Lcom/ibm/JikesRVM/VM_JNIEnvironment;", "JNIRefs", "[I");
  static final VM_Field JNIRefsTopField            = getField("Lcom/ibm/JikesRVM/VM_JNIEnvironment;", "JNIRefsTop", "I");
  static final VM_Field JNIRefsMaxField            = getField("Lcom/ibm/JikesRVM/VM_JNIEnvironment;", "JNIRefsMax", "I");
  static final VM_Field JNIRefsSavedFPField        = getField("Lcom/ibm/JikesRVM/VM_JNIEnvironment;", "JNIRefsSavedFP", "I");
  static final VM_Field JNITopJavaFPField          = getField("Lcom/ibm/JikesRVM/VM_JNIEnvironment;", "JNITopJavaFP", "Lcom/ibm/JikesRVM/VM_Address;");
  static final VM_Field JNIPendingExceptionField   = getField("Lcom/ibm/JikesRVM/VM_JNIEnvironment;", "pendingException", "Ljava/lang/Throwable;");
  static final VM_Field JNIFunctionPointersField   = getField("Lcom/ibm/JikesRVM/VM_JNIEnvironment;", "JNIFunctionPointers", "[I");

  public static final VM_Field the_boot_recordField            = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "the_boot_record", "Lcom/ibm/JikesRVM/VM_BootRecord;");
  static final VM_Field tiRegisterField                 = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "tiRegister", "I");
  static final VM_Field spRegisterField                 = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "spRegister", "I");
  static final VM_Field ipRegisterField                 = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "ipRegister", "I");
  static final VM_Field tocRegisterField                = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "tocRegister", "I");
  public static final VM_Field processorsOffsetField           = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "processorsOffset", "I");
  public static final VM_Field threadsOffsetField              = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "threadsOffset", "I");
  static final VM_Field globalGCInProgressFlagField     = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "globalGCInProgressFlag", "I");
  public static final VM_Field lockoutProcessorField           = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "lockoutProcessor", "I");
  public static final VM_Field sysVirtualProcessorYieldIPField = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysVirtualProcessorYieldIP", "I");
  public static final VM_Field externalSignalFlagField         = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "externalSignalFlag", "I");
  public static final VM_Field sysLongDivideIPField            = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysLongDivideIP", "I");
  public static final VM_Field sysLongRemainderIPField         = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysLongRemainderIP", "I");
  public static final VM_Field sysLongToFloatIPField           = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysLongToFloatIP", "I");
  public static final VM_Field sysLongToDoubleIPField          = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysLongToDoubleIP", "I");
  public static final VM_Field sysFloatToIntIPField            = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysFloatToIntIP", "I");
  public static final VM_Field sysDoubleToIntIPField           = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysDoubleToIntIP", "I");
  public static final VM_Field sysFloatToLongIPField           = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysFloatToLongIP", "I");
  public static final VM_Field sysDoubleToLongIPField          = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysDoubleToLongIP", "I");
  //-#if RVM_FOR_POWERPC
  public static final VM_Field sysDoubleRemainderIPField       = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysDoubleRemainderIP", "I");
  public static final VM_Field sysTOCField                     = getField("Lcom/ibm/JikesRVM/VM_BootRecord;", "sysTOC", "I");
  //-#endif

  public static final VM_Field edgeCountersField               = getField("Lcom/ibm/JikesRVM/VM_EdgeCounterDictionary;", "values", "[[I");

  public static final VM_Method arrayStoreWriteBarrierMethod = getMethod("Lcom/ibm/JikesRVM/memoryManagers/VM_WriteBarrier;", "arrayStoreWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  public static final VM_Method resolvedPutfieldWriteBarrierMethod = getMethod("Lcom/ibm/JikesRVM/memoryManagers/VM_WriteBarrier;", "resolvedPutfieldWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  public static final VM_Method unresolvedPutfieldWriteBarrierMethod = getMethod("Lcom/ibm/JikesRVM/memoryManagers/VM_WriteBarrier;", "unresolvedPutfieldWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  public static final VM_Method resolvedPutStaticWriteBarrierMethod = getMethod("Lcom/ibm/JikesRVM/memoryManagers/VM_WriteBarrier;", "resolvedPutStaticWriteBarrier", "(ILjava/lang/Object;)V");
  public static final VM_Method unresolvedPutStaticWriteBarrierMethod = getMethod("Lcom/ibm/JikesRVM/memoryManagers/VM_WriteBarrier;", "unresolvedPutStaticWriteBarrier", "(ILjava/lang/Object;)V");


  public static final VM_Field inetAddressAddressField = getField("Ljava/net/InetAddress;", "address", "I");
  public static final VM_Field inetAddressFamilyField  = getField("Ljava/net/InetAddress;", "family", "I");
  
  public static final VM_Field socketImplAddressField  = getField("Ljava/net/SocketImpl;", "address", "Ljava/net/InetAddress;");
  public static final VM_Field socketImplPortField     = getField("Ljava/net/SocketImpl;", "port", "I");

  //-#if RVM_WITH_READ_BARRIER2
  static final VM_Method arrayLoadReadBarrierMethod = getMethod("Lcom/ibm/JikesRVM/VM_ReadBarrier;", "arrayLoadReadBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method resolvedGetfieldReadBarrierMethod = getMethod("Lcom/ibm/JikesRVM/VM_ReadBarrier;", "resolvedGetfieldReadBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method unresolvedGetfieldReadBarrierMethod = getMethod("Lcom/ibm/JikesRVM/VM_ReadBarrier;", "unresolvedGetfieldReadBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  static final VM_Method resolvedGetStaticReadBarrierMethod = getMethod("Lcom/ibm/JikesRVM/VM_ReadBarrier;", "resolvedGetStaticReadBarrier", "(ILjava/lang/Object;)V");
  static final VM_Method unresolvedGetStaticReadBarrierMethod = getMethod("Lcom/ibm/JikesRVM/VM_ReadBarrier;", "unresolvedGetStaticReadBarrier", "(ILjava/lang/Object;)V");
  //-#endif RVM_WITH_READ_BARRIER2

  //-#if RVM_WITH_GCTk
  static ADDRESS GCTk_WriteBufferBase;
  static ADDRESS GCTk_BumpPointerBase;
  static ADDRESS GCTk_SyncPointerBase;
  static ADDRESS GCTk_ChunkAllocatorBase;
  static ADDRESS GCTk_TraceBufferBase;
  //-#endif


  //-#if RVM_WITH_OPT_COMPILER
  ////////////////// 
  // Entrypoints that are valid only when the opt compiler is included in the build
  //////////////////
  public static final VM_Field specializedMethodsField = getField("Lcom/ibm/JikesRVM/opt/OPT_SpecializedMethodPool;", "specializedMethods", "["+INSTRUCTION_ARRAY_SIGNATURE);

  public static final VM_Method optThreadSwitchFromPrologueMethod = getMethod("Lcom/ibm/JikesRVM/opt/VM_OptSaveVolatile;", "OPT_threadSwitchFromPrologue", "()V");
  public static final VM_Method optThreadSwitchFromBackedgeMethod = getMethod("Lcom/ibm/JikesRVM/opt/VM_OptSaveVolatile;", "OPT_threadSwitchFromBackedge", "()V");
  public static final VM_Method optThreadSwitchFromEpilogueMethod = getMethod("Lcom/ibm/JikesRVM/opt/VM_OptSaveVolatile;", "OPT_threadSwitchFromEpilogue", "()V");
  public static final VM_Method optResolveMethod                  = getMethod("Lcom/ibm/JikesRVM/opt/VM_OptSaveVolatile;", "OPT_resolve", "()V");

  public static final VM_Method optNewArrayArrayMethod            = getMethod("Lcom/ibm/JikesRVM/opt/VM_OptLinker;", "newArrayArray", "([II)Ljava/lang/Object;");
  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
  static final VM_Method optAllocAdviceNewArrayArrayMethod = getMethod("Lcom/ibm/JikesRVM/VM_OptLinker;", "allocAdviceNewArrayArray", "([III)Ljava/lang/Object;");
  //-#endif

  public static final VM_Method sysArrayCopy = getMethod("Lcom/ibm/JikesRVM/librarySupport/SystemSupport;", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V");
  //-#endif



  //-#if RVM_WITH_ADAPTIVE_SYSTEM
  ////////////////// 
  // Entrypoints that are valid only when the opt compiler is included in the build
  //////////////////
  static final VM_Field methodListenerNextIndexField      = getField("Lcom/ibm/JikesRVM/VM_MethodListener;", "nextIndex", "I");
  static final VM_Field methodListenerNumSamplesField     = getField("Lcom/ibm/JikesRVM/VM_MethodListener;", "numSamples", "I");

  static final VM_Field edgeListenerNextIndexField        = getField("Lcom/ibm/JikesRVM/VM_EdgeListener;", "nextIndex", "I");
  static final VM_Field edgeListenerSamplesTakenField     = getField("Lcom/ibm/JikesRVM/VM_EdgeListener;", "samplesTaken", "I");
  static final VM_Field counterArrayManagerCounterArraysField = getField("Lcom/ibm/JikesRVM/VM_CounterArrayManager;","counterArrays","[[D");
  //-#endif


  static void init() {
    //-#if RVM_WITH_GCTk
    ADDRESS top = getMember("Lcom/ibm/JikesRVM/VM_Processor;", "writeBuffer0", "I").getOffset();
    ADDRESS bot = getMember("Lcom/ibm/JikesRVM/VM_Processor;", "writeBuffer1", "I").getOffset();
    GCTk_WriteBufferBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean discontigious = (((top > bot) && ((top - bot) != 4))
			       || ((top < bot) && ((bot - top) != 4)));
      if (discontigious)
	VM.sysWrite("\n---->"+top+","+bot+"->"+GCTk_WriteBufferBase+"<----\n");
      VM._assert(!discontigious);
    }
    GCTk_TraceBufferBase        = getMember("LGCTk_TraceBuffer;", "bumpPtr_", "I").getOffset();

    top = getMember("Lcom/ibm/JikesRVM/VM_Processor;", "allocBump0", "I").getOffset();
    bot = getMember("Lcom/ibm/JikesRVM/VM_Processor;", "allocBump7", "I").getOffset();
    GCTk_BumpPointerBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean unaligned = (((top > bot) && ((top - bot) != 28)) 
			   || ((top < bot) && ((bot - top) != 28)));
      if (unaligned)
	VM.sysWrite("\n---->"+top+","+bot+"->"+GCTk_BumpPointerBase+"<----\n");
      VM._assert(!unaligned);
    }
  
    top = getMember("Lcom/ibm/JikesRVM/VM_Processor;", "allocSync0", "I").getOffset();
    bot = getMember("Lcom/ibm/JikesRVM/VM_Processor;", "allocSync7", "I").getOffset();
    GCTk_SyncPointerBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean unaligned = (((top > bot) && ((top - bot) != 28)) 
			   || ((top < bot) && ((bot - top) != 28)));
      if (unaligned)
	VM.sysWrite("---->"+top+","+bot+"->"+GCTk_SyncPointerBase+"<----\n");
      VM._assert(!unaligned);
    }
	  
    top = getMember("LGCTk_ChunkAllocator;", "allocChunkStart0", "I").getOffset();
    bot = getMember("LGCTk_ChunkAllocator;", "allocChunkEnd7", "I").getOffset();
    GCTk_ChunkAllocatorBase = (top > bot) ? bot : top;
    if (VM.VerifyAssertions) {
      boolean unaligned = (((top > bot) && ((top - bot) != 60)) 
			   || ((top < bot) && ((bot - top) != 60)));
      if (unaligned) 
	VM.sysWrite("---->"+top+","+bot+"->"+GCTk_ChunkAllocatorBase+"<----\n");
      VM._assert(!unaligned);
    }
    //-#endif
  }


  /**
   * Get description of virtual machine component (field or method).
   * Note: This is method is intended for use only by VM classes that need 
   * to address their own fields and methods in the runtime virtual machine 
   * image.  It should not be used for general purpose class loading.
   * @param classDescriptor  class  descriptor - something like "Lcom/ibm/JikesRVM/VM_Runtime;"
   * @param memberName       member name       - something like "invokestatic"
   * @param memberDescriptor member descriptor - something like "()V"
   * @return corresponding VM_Member object
   */
  private static VM_Member getMember(String classDescriptor, String memberName, 
				     String memberDescriptor) {
    VM_Atom clsDescriptor = VM_Atom.findOrCreateAsciiAtom(classDescriptor);
    VM_Atom memName       = VM_Atom.findOrCreateAsciiAtom(memberName);
    VM_Atom memDescriptor = VM_Atom.findOrCreateAsciiAtom(memberDescriptor);
    try {
      VM_Class cls = VM_ClassLoader.findOrCreateType(clsDescriptor, VM_SystemClassLoader.getVMClassLoader()).asClass();
      cls.load();
      cls.resolve();
         
      VM_Member member;
      if ((member = cls.findDeclaredField(memName, memDescriptor)) != null)
        return member;
      if ((member = cls.findDeclaredMethod(memName, memDescriptor)) != null)
        return member;

      // The usual causes for getMember() to fail are:
      //  1. you mispelled the class name, member name, or member signature
      //  2. the class containing the specified member didn't get compiled
      //
      VM.sysWrite("VM_Entrypoints.getMember: can't find class="+classDescriptor+" member="+memberName+" desc="+memberDescriptor+"\n");
      VM._assert(NOT_REACHED);
    } catch (VM_ResolutionException e) {
      VM.sysWrite("VM_Entrypoints.getMember: can't resolve class=" + classDescriptor+
		  " member=" + memberName + " desc=" + memberDescriptor + "\n");
      VM._assert(NOT_REACHED);
    }
    return null; // placate jikes
  }

  private static VM_Method getMethod(String klass, String member, String descriptor) {
    return (VM_Method)getMember(klass, member, descriptor);
  }

  private static VM_Field getField(String klass, String member, String descriptor) {
    return (VM_Field)getMember(klass, member, descriptor);
  }
}
