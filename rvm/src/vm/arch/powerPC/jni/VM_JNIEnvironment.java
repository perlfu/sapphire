/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 *   This class implements the JNI environment, it includes:
 * -The array of JNI function pointers accessible from C
 * -Implementation of all the JNI functions
 *
 * @author Ton Ngo 
 * @author Steve Smith
 */
import java.lang.reflect.*;


public class VM_JNIEnvironment implements VM_JNIConstants, VM_RegisterConstants
{
  private static boolean initialized = false;
  private static String[] names;
  static VM_CompilerInfo   javaToCCompilerInfo;  // shared VM_CompilerInfo for all native method glue code

  /**
   * This is the JNI function table, the address of this array will be
   * passed to the native code
   */
  private static INSTRUCTION[][][] JNIFunctions;

  /**
   * This is a table of pointers to the shared JNI function table.  All entries 
   * point to the same function table.  Each thread uses the pointer at its thread id
   * offset to allow us to determine a threads id from the pointer it is using.
   * Needed when native calls Java (JNIFunctions) and passes its JNIEnv pointer.
   * Its offset into the JNIFunctionPts array is the same as the threads offset
   * in the Scheduler.threads array.
   */
    //  private static int[] JNIFunctionPointers;
    static int[] JNIFunctionPointers;        // made public so vpStatus could be set 11/16/00 SES
                                             // maybe need set & get functions ??

  /**
   * These are thread specific information, such as:
   *  -the list of references passed to native code, for GC purpose
   *  -saved RVM system registers
   */
  int JNIEnvAddress;      // contain a pointer to the JNIFunctions array
  int savedTIreg;         // for saving thread index register on entry to native, to be restored on JNI call from native
  VM_Processor savedPRreg; // for saving processor register on entry to native, to be restored on JNI call from native
  boolean alwaysHasNativeFrame;  // true if the bottom stack frame is native, such as thread for CreateJVM or AttachCurrentThread

  int[] JNIRefs;          // references passed to native code
  int   JNIRefsTop;       // -> address of current top ref in JNIRefs array 
  int   JNIRefsMax;       // -> address of end (last entry) of JNIRefs array
  int   JNIRefsSavedFP;   // -> previous frame boundary in JNIRefs array
  int   JNITopJavaFP;     // -> Top java frame when in C frames on top of the stack

  Throwable pendingException = null;

  // Saved context for thread attached to external pthread.  This context is
  // saved by the JNIService thread and points to the point in JNIStartUp thread
  // where it yields to the queue in the native VM_Processor.
  // When DetachCurrentThread is called, the JNIService thread restores this context 
  // to allow the thread to run VM_Thread.terminate on its original stack.
  VM_Registers savedContextForTermination;

  // temporarily use a fixed size array for JNI refs, later grow as needed
  static final int JNIREFS_ARRAY_LENGTH = 100;

  // allocate the first dimension of the function array in the boot image so that
  // we have an address pointing to it.  This is necessary for thread creation
  // since the VM_JNIEnvironment object will contain a field pointing to this array
  public static void init() {
    JNIFunctions = new int[FUNCTIONCOUNT][][];

//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
// alternate implementation of jni
    JNIFunctionPointers = new int[VM_Scheduler.MAX_THREADS];
//-#else
// default implementation of jni
    // this build uses 2 words for each thread
    JNIFunctionPointers = new int[VM_Scheduler.MAX_THREADS * 2];
//-#endif
  }

  /**
   *  Initialize the array of JNI functions
   *  To be called from VM_DynamicLibrary.java when a library is loaded,
   *  expecting native calls to be made
   *
   */
  public static void boot() {

    if (initialized)
      return;

    // fill an array of JNI names
    setNames();

    // create method ID for JNI transition stackframes
    //javaToCCompilerInfo = new VM_JNICompilerInfo();   //XXX now alloc method specific infos ses 073101

    // fill in the TOC entries for each AIX linkage triplet
    for (int i=0; i<JNIFunctions.length; i++) {
      JNIFunctions[i] = new int[3][];
      JNIFunctions[i][TOC] = VM_Statics.getSlots();   // the JTOC value: address of TOC
    }

    // fill in the IP entries for each AIX linkage triplet
    try {
      VM_Class cls = VM_Class.forName("VM_JNIFunctions");
      VM_Method[] mths = cls.getDeclaredMethods();
      // VM.sysWrite("VM_JNIEnvironment:  scanning " + mths.length + " methods\n");
      for (int i=0; i<mths.length; i++) {
	String methodName = mths[i].getName().toString();
	int jniIndex = indexOf(methodName);
	if (jniIndex!=-1) {
	  JNIFunctions[jniIndex][IP] = mths[i].getMostRecentlyGeneratedInstructions();
	  // VM.sysWrite("   " + methodName + "=" + VM.intAsHexString(JNIFunctions[jniIndex][IP]));
	} 
	// else {
	//   VM.sysWrite("   " + methodName + " skipped\n");
	// }
      }

      int functionAddress = VM_Magic.objectAsAddress(JNIFunctions[NEWINTARRAY][IP]);
      // VM.sysWrite("   NewIntArray is at " + VM.intAsHexString(functionAddress) + "\n");
      functionAddress = VM_Magic.objectAsAddress(JNIFunctions[NEWINTARRAY][TOC]);
      // VM.sysWrite("   TOC is stored at " + VM.intAsHexString(functionAddress) + "\n");

    } catch (VM_ResolutionException e) {
      throw new InternalError("VM_JNIEnvironment fails to initialize, has the class been renamed\n");
    }

    initialized = true;

  }

  // Instance:  create a thread specific JNI environment.  threadSlot = creating threads
  // thread id == index of its entry in Scheduler.threads array
  //
  public VM_JNIEnvironment(int threadSlot) {
    // VM_Field functions = (VM_Field) VM.getMember("VM_JNIEnvironment", "JNIFunctions", "[[I");
    // int addr = VM_Magic.getTocPointer() + functions.getOffset();

    // as of 8/22 SES - let JNIEnvAddress be the address of the JNIFunctionPtr to be
    // used by the creating thread.  Passed as first arg (JNIEnv) to native C functions.

//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
// alternate implementation of jni
    // set the jni function pointer for the creating thread, to be used when
    // it makes native calls
    JNIFunctionPointers[threadSlot] = VM_Magic.objectAsAddress(JNIFunctions);
    JNIEnvAddress = VM_Magic.objectAsAddress(JNIFunctionPointers) + threadSlot*4;
//-#else
// default implementation of jni
    // this build uses 2 words for each thread, the first is the function pointer
    // to be used when making native calls
    JNIFunctionPointers[threadSlot * 2] = VM_Magic.objectAsAddress(JNIFunctions);
    JNIFunctionPointers[(threadSlot * 2)+1] = 0;  // later contains addr of processor vpStatus word
    JNIEnvAddress = VM_Magic.objectAsAddress(JNIFunctionPointers) + threadSlot*8;
//-#endif

    JNIRefs = new int[JNIREFS_ARRAY_LENGTH];
    JNIRefs[0] = 0;                                        // 0 entry for bottom of stack
    JNIRefsTop = 0;
    JNIRefsSavedFP = 0;
    JNIRefsMax = (JNIRefs.length - 1) * 4;   // byte offset to last entry

    // initially TOP and SavedFP -> entry 0 containing 0

    alwaysHasNativeFrame = false;

  }

  // push a reference onto thread local JNIRefs stack.  To be used by JNI
  // Functions when returning a reference back to JNI native C code
  // Taken:    Object to put on stack
  // Returned: offset of entry in JNIRefs stack
  // 
  public int pushJNIRef( Object ref ) {
    JNIRefsTop += 4;
    JNIRefs[ JNIRefsTop >> 2 ] = VM_Magic.objectAsAddress(ref);
    return JNIRefsTop;
  }

  // get a reference from the JNIRefs stack
  // Taken:    offset in JNIRefs stack
  // Returned: reference at that offset
  public Object getJNIRef( int offset ) {
    if (offset > JNIRefsTop) {
      VM.sysWrite("JNI ERROR: getJNIRef for illegal offset > TOP\n");
      return null;
    }
    return VM_Magic.addressAsObject( JNIRefs[ offset>>2 ] );
  }
	

  // record an exception as pending so that it will be delivered on the return
  // to the Java caller;  clear the exception by recording null
  // Taken:  an exception or error
  // Returned:  nothing
  //
  public void recordException(Throwable e) {
    // don't overwrite the first exception except to clear it
    if (pendingException==null || e==null)
      pendingException = e;
  }

  // return the pending exception
  // Taken:  nothing
  // Returned:  an exception or error
  //
  public Throwable getException() {
    return pendingException;
  }

  //
  // get the address of the JNIFunctions array, which should be in the JTOC
  // Taken:    nothing 
  // Returned: the address of the JNIFunctions array 
  // 
  public int getJNIenvAddress() {
    return JNIEnvAddress;
  }

  public INSTRUCTION[] getInstructions(int id) {    
    return JNIFunctions[id][IP];
  }

  //
  // get the JNI index for a function name
  // Taken:    a JNI function name
  // Returned: the index for this function, -1 if not found
  //
  private static int indexOf(String functionName) {
    for (int i=0; i<FUNCTIONCOUNT; i++) {
      if (names[i].equals(functionName))
	return i;
    }
    return -1;
  }





  private static String[] setNames() {
    names = new String[FUNCTIONCOUNT];
    names[0]                             = new String("undefined");
    names[RESERVED0]                     = new String("reserved0")                     ;	  
    names[RESERVED1]                     = new String("reserved1")                     ;	  
    names[RESERVED2]                     = new String("reserved2")                     ;	  
    names[RESERVED3]                     = new String("reserved3")                     ;	  
    names[GETVERSION]                    = new String("GetVersion")                    ;	  
    names[DEFINECLASS]                   = new String("DefineClass")                   ;	  
    names[FINDCLASS]                     = new String("FindClass")                     ;	  
    names[FROMREFLECTEDMETHOD]         	 = new String("FromReflectedMethod"); //  JDK1.2, #7      
    names[FROMREFLECTEDFIELD]          	 = new String("FromReflectedField");  //  JDK1.2, #8      
    names[TOREFLECTEDMETHOD]           	 = new String("ToReflectedMethod");   //  JDK1.2, #9      
    names[GETSUPERCLASS]                 = new String("GetSuperclass")                 ;	  
    names[ISASSIGNABLEFROM]              = new String("IsAssignableFrom")              ;	  
    names[TOREFLECTEDFIELD]            	 = new String("ToReflectedField");    //  JDK1.2, #12      
    names[THROW]                         = new String("Throw")                         ;	  
    names[THROWNEW]                      = new String("ThrowNew")                      ;	  
    names[EXCEPTIONOCCURRED]             = new String("ExceptionOccurred")             ;	  
    names[EXCEPTIONDESCRIBE]             = new String("ExceptionDescribe")             ;	  
    names[EXCEPTIONCLEAR]                = new String("ExceptionClear")                ;	  
    names[FATALERROR]                    = new String("FatalError")                    ;	  
    names[PUSHLOCALFRAME]              	 = new String("PushLocalFrame");      //  JDK1.2, #19      
    names[POPLOCALFRAME]               	 = new String("PopLocalFrame");       //  JDK1.2, #20      
    names[NEWGLOBALREF]                  = new String("NewGlobalRef")                  ;	  
    names[DELETEGLOBALREF]               = new String("DeleteGlobalRef")               ;	  
    names[DELETELOCALREF]                = new String("DeleteLocalRef")                ;	  
    names[ISSAMEOBJECT]                  = new String("IsSameObject")                  ;	  
    names[NEWLOCALREF]                 	 = new String("NewLocalRef");         //  JDK1.2, #25      
    names[ENSURELOCALCAPACITY]         	 = new String("EnsureLocalCapacity"); //  JDK1.2, #26   
    names[ALLOCOBJECT]                   = new String("AllocObject")                   ;	  
    names[NEWOBJECT]                     = new String("NewObject")                     ;	  
    names[NEWOBJECTV]                    = new String("NewObjectV")                    ;	  
    names[NEWOBJECTA]                    = new String("NewObjectA")                    ;	  
    names[GETOBJECTCLASS]                = new String("GetObjectClass")                ;	  
    names[ISINSTANCEOF]                  = new String("IsInstanceOf")                  ;	  
    names[GETMETHODID]                   = new String("GetMethodID")                   ;	  
    names[CALLOBJECTMETHOD]              = new String("CallObjectMethod")              ;	  
    names[CALLOBJECTMETHODV]             = new String("CallObjectMethodV")             ;	  
    names[CALLOBJECTMETHODA]             = new String("CallObjectMethodA")             ;	  
    names[CALLBOOLEANMETHOD]             = new String("CallBooleanMethod")             ;	  
    names[CALLBOOLEANMETHODV]            = new String("CallBooleanMethodV")            ;	  
    names[CALLBOOLEANMETHODA]            = new String("CallBooleanMethodA")            ;	  
    names[CALLBYTEMETHOD]                = new String("CallByteMethod")                ;	  
    names[CALLBYTEMETHODV]               = new String("CallByteMethodV")               ;	  
    names[CALLBYTEMETHODA]               = new String("CallByteMethodA")               ;	  
    names[CALLCHARMETHOD]                = new String("CallCharMethod")                ;	  
    names[CALLCHARMETHODV]               = new String("CallCharMethodV")               ;	  
    names[CALLCHARMETHODA]               = new String("CallCharMethodA")               ;	  
    names[CALLSHORTMETHOD]               = new String("CallShortMethod")               ;	  
    names[CALLSHORTMETHODV]              = new String("CallShortMethodV")              ;	  
    names[CALLSHORTMETHODA]              = new String("CallShortMethodA")              ;	  
    names[CALLINTMETHOD]                 = new String("CallIntMethod")                 ;	  
    names[CALLINTMETHODV]                = new String("CallIntMethodV")                ;	  
    names[CALLINTMETHODA]                = new String("CallIntMethodA")                ;	  
    names[CALLLONGMETHOD]                = new String("CallLongMethod")                ;	  
    names[CALLLONGMETHODV]               = new String("CallLongMethodV")               ;	  
    names[CALLLONGMETHODA]               = new String("CallLongMethodA")               ;	  
    names[CALLFLOATMETHOD]               = new String("CallFloatMethod")               ;	  
    names[CALLFLOATMETHODV]              = new String("CallFloatMethodV")              ;	  
    names[CALLFLOATMETHODA]              = new String("CallFloatMethodA")              ;	  
    names[CALLDOUBLEMETHOD]              = new String("CallDoubleMethod")              ;	  
    names[CALLDOUBLEMETHODV]             = new String("CallDoubleMethodV")             ;	  
    names[CALLDOUBLEMETHODA]             = new String("CallDoubleMethodA")             ;	  
    names[CALLVOIDMETHOD]                = new String("CallVoidMethod")                ;	  
    names[CALLVOIDMETHODV]               = new String("CallVoidMethodV")               ;	  
    names[CALLVOIDMETHODA]               = new String("CallVoidMethodA")               ;	  
    names[CALLNONVIRTUALOBJECTMETHOD]    = new String("CallNonvirtualObjectMethod")    ;	  
    names[CALLNONVIRTUALOBJECTMETHODV]   = new String("CallNonvirtualObjectMethodV")   ;	  
    names[CALLNONVIRTUALOBJECTMETHODA]   = new String("CallNonvirtualObjectMethodA")   ;	  
    names[CALLNONVIRTUALBOOLEANMETHOD]   = new String("CallNonvirtualBooleanMethod")   ;	  
    names[CALLNONVIRTUALBOOLEANMETHODV]  = new String("CallNonvirtualBooleanMethodV")  ;	  
    names[CALLNONVIRTUALBOOLEANMETHODA]  = new String("CallNonvirtualBooleanMethodA")  ;	  
    names[CALLNONVIRTUALBYTEMETHOD]      = new String("CallNonvirtualByteMethod")      ;	  
    names[CALLNONVIRTUALBYTEMETHODV]     = new String("CallNonvirtualByteMethodV")     ;	  
    names[CALLNONVIRTUALBYTEMETHODA]     = new String("CallNonvirtualByteMethodA")     ;	  
    names[CALLNONVIRTUALCHARMETHOD]      = new String("CallNonvirtualCharMethod")      ;	  
    names[CALLNONVIRTUALCHARMETHODV]     = new String("CallNonvirtualCharMethodV")     ;	  
    names[CALLNONVIRTUALCHARMETHODA]     = new String("CallNonvirtualCharMethodA")     ;	  
    names[CALLNONVIRTUALSHORTMETHOD]     = new String("CallNonvirtualShortMethod")     ;	  
    names[CALLNONVIRTUALSHORTMETHODV]    = new String("CallNonvirtualShortMethodV")    ;	  
    names[CALLNONVIRTUALSHORTMETHODA]    = new String("CallNonvirtualShortMethodA")    ;	  
    names[CALLNONVIRTUALINTMETHOD]       = new String("CallNonvirtualIntMethod")       ;	  
    names[CALLNONVIRTUALINTMETHODV]      = new String("CallNonvirtualIntMethodV")      ;	  
    names[CALLNONVIRTUALINTMETHODA]      = new String("CallNonvirtualIntMethodA")      ;	  
    names[CALLNONVIRTUALLONGMETHOD]      = new String("CallNonvirtualLongMethod")      ;	  
    names[CALLNONVIRTUALLONGMETHODV]     = new String("CallNonvirtualLongMethodV")     ;	  
    names[CALLNONVIRTUALLONGMETHODA]     = new String("CallNonvirtualLongMethodA")     ;	  
    names[CALLNONVIRTUALFLOATMETHOD]     = new String("CallNonvirtualFloatMethod")     ;	  
    names[CALLNONVIRTUALFLOATMETHODV]    = new String("CallNonvirtualFloatMethodV")    ;	  
    names[CALLNONVIRTUALFLOATMETHODA]    = new String("CallNonvirtualFloatMethodA")    ;	  
    names[CALLNONVIRTUALDOUBLEMETHOD]    = new String("CallNonvirtualDoubleMethod")    ;	  
    names[CALLNONVIRTUALDOUBLEMETHODV]   = new String("CallNonvirtualDoubleMethodV")   ;	  
    names[CALLNONVIRTUALDOUBLEMETHODA]   = new String("CallNonvirtualDoubleMethodA")   ;	  
    names[CALLNONVIRTUALVOIDMETHOD]      = new String("CallNonvirtualVoidMethod")      ;	  
    names[CALLNONVIRTUALVOIDMETHODV]     = new String("CallNonvirtualVoidMethodV")     ;	  
    names[CALLNONVIRTUALVOIDMETHODA]     = new String("CallNonvirtualVoidMethodA")     ;	  
    names[GETFIELDID]                    = new String("GetFieldID")                    ;	  
    names[GETOBJECTFIELD]                = new String("GetObjectField")                ;	  
    names[GETBOOLEANFIELD]               = new String("GetBooleanField")               ;	  
    names[GETBYTEFIELD]                  = new String("GetByteField")                  ;	  
    names[GETCHARFIELD]                  = new String("GetCharField")                  ;	  
    names[GETSHORTFIELD]                 = new String("GetShortField")                 ;	  
    names[GETINTFIELD]                   = new String("GetIntField")                   ;	  
    names[GETLONGFIELD]                  = new String("GetLongField")                  ;	  
    names[GETFLOATFIELD]                 = new String("GetFloatField")                 ;	  
    names[GETDOUBLEFIELD]                = new String("GetDoubleField")                ;	  
    names[SETOBJECTFIELD]                = new String("SetObjectField")                ;	  
    names[SETBOOLEANFIELD]               = new String("SetBooleanField")               ;	  
    names[SETBYTEFIELD]                  = new String("SetByteField")                  ;	  
    names[SETCHARFIELD]                  = new String("SetCharField")                  ;	  
    names[SETSHORTFIELD]                 = new String("SetShortField")                 ;	  
    names[SETINTFIELD]                   = new String("SetIntField")                   ;	  
    names[SETLONGFIELD]                  = new String("SetLongField")                  ;	  
    names[SETFLOATFIELD]                 = new String("SetFloatField")                 ;	  
    names[SETDOUBLEFIELD]                = new String("SetDoubleField")                ;	  
    names[GETSTATICMETHODID]             = new String("GetStaticMethodID")             ;	  
    names[CALLSTATICOBJECTMETHOD]        = new String("CallStaticObjectMethod")        ;	  
    names[CALLSTATICOBJECTMETHODV]       = new String("CallStaticObjectMethodV")       ;	  
    names[CALLSTATICOBJECTMETHODA]       = new String("CallStaticObjectMethodA")       ;	  
    names[CALLSTATICBOOLEANMETHOD]       = new String("CallStaticBooleanMethod")       ;	  
    names[CALLSTATICBOOLEANMETHODV]      = new String("CallStaticBooleanMethodV")      ;	  
    names[CALLSTATICBOOLEANMETHODA]      = new String("CallStaticBooleanMethodA")      ;	  
    names[CALLSTATICBYTEMETHOD]          = new String("CallStaticByteMethod")          ;	  
    names[CALLSTATICBYTEMETHODV]         = new String("CallStaticByteMethodV")         ;	  
    names[CALLSTATICBYTEMETHODA]         = new String("CallStaticByteMethodA")         ;	  
    names[CALLSTATICCHARMETHOD]          = new String("CallStaticCharMethod")          ;	  
    names[CALLSTATICCHARMETHODV]         = new String("CallStaticCharMethodV")         ;	  
    names[CALLSTATICCHARMETHODA]         = new String("CallStaticCharMethodA")         ;	  
    names[CALLSTATICSHORTMETHOD]         = new String("CallStaticShortMethod")         ;	  
    names[CALLSTATICSHORTMETHODV]        = new String("CallStaticShortMethodV")        ;	  
    names[CALLSTATICSHORTMETHODA]        = new String("CallStaticShortMethodA")        ;	  
    names[CALLSTATICINTMETHOD]           = new String("CallStaticIntMethod")           ;	  
    names[CALLSTATICINTMETHODV]          = new String("CallStaticIntMethodV")          ;	  
    names[CALLSTATICINTMETHODA]          = new String("CallStaticIntMethodA")          ;	  
    names[CALLSTATICLONGMETHOD]          = new String("CallStaticLongMethod")          ;	  
    names[CALLSTATICLONGMETHODV]         = new String("CallStaticLongMethodV")         ;	  
    names[CALLSTATICLONGMETHODA]         = new String("CallStaticLongMethodA")         ;	  
    names[CALLSTATICFLOATMETHOD]         = new String("CallStaticFloatMethod")         ;	  
    names[CALLSTATICFLOATMETHODV]        = new String("CallStaticFloatMethodV")        ;	  
    names[CALLSTATICFLOATMETHODA]        = new String("CallStaticFloatMethodA")        ;	  
    names[CALLSTATICDOUBLEMETHOD]        = new String("CallStaticDoubleMethod")        ;	  
    names[CALLSTATICDOUBLEMETHODV]       = new String("CallStaticDoubleMethodV")       ;	  
    names[CALLSTATICDOUBLEMETHODA]       = new String("CallStaticDoubleMethodA")       ;	  
    names[CALLSTATICVOIDMETHOD]          = new String("CallStaticVoidMethod")          ;	  
    names[CALLSTATICVOIDMETHODV]         = new String("CallStaticVoidMethodV")         ;	  
    names[CALLSTATICVOIDMETHODA]         = new String("CallStaticVoidMethodA")         ;	  
    names[GETSTATICFIELDID]              = new String("GetStaticFieldID")              ;	  
    names[GETSTATICOBJECTFIELD]          = new String("GetStaticObjectField")          ;	  
    names[GETSTATICBOOLEANFIELD]         = new String("GetStaticBooleanField")         ;	  
    names[GETSTATICBYTEFIELD]            = new String("GetStaticByteField")            ;	  
    names[GETSTATICCHARFIELD]            = new String("GetStaticCharField")            ;	  
    names[GETSTATICSHORTFIELD]           = new String("GetStaticShortField")           ;	  
    names[GETSTATICINTFIELD]             = new String("GetStaticIntField")             ;	  
    names[GETSTATICLONGFIELD]            = new String("GetStaticLongField")            ;	  
    names[GETSTATICFLOATFIELD]           = new String("GetStaticFloatField")           ;	  
    names[GETSTATICDOUBLEFIELD]          = new String("GetStaticDoubleField")          ;	  
    names[SETSTATICOBJECTFIELD]          = new String("SetStaticObjectField")          ;	  
    names[SETSTATICBOOLEANFIELD]         = new String("SetStaticBooleanField")         ;	  
    names[SETSTATICBYTEFIELD]            = new String("SetStaticByteField")            ;	  
    names[SETSTATICCHARFIELD]            = new String("SetStaticCharField")            ;	  
    names[SETSTATICSHORTFIELD]           = new String("SetStaticShortField")           ;	  
    names[SETSTATICINTFIELD]             = new String("SetStaticIntField")             ;	  
    names[SETSTATICLONGFIELD]            = new String("SetStaticLongField")            ;	  
    names[SETSTATICFLOATFIELD]           = new String("SetStaticFloatField")           ;	  
    names[SETSTATICDOUBLEFIELD]          = new String("SetStaticDoubleField")          ;	  
    names[NEWSTRING]                     = new String("NewString")                     ;	  
    names[GETSTRINGLENGTH]               = new String("GetStringLength")               ;	  
    names[GETSTRINGCHARS]                = new String("GetStringChars")                ;	  
    names[RELEASESTRINGCHARS]            = new String("ReleaseStringChars")            ;	  
    names[NEWSTRINGUTF]                  = new String("NewStringUTF")                  ;	  
    names[GETSTRINGUTFLENGTH]            = new String("GetStringUTFLength")            ;	  
    names[GETSTRINGUTFCHARS]             = new String("GetStringUTFChars")             ;	  
    names[RELEASESTRINGUTFCHARS]         = new String("ReleaseStringUTFChars")         ;	  
    names[GETARRAYLENGTH]                = new String("GetArrayLength")                ;	  
    names[NEWOBJECTARRAY]                = new String("NewObjectArray")                ;	  
    names[GETOBJECTARRAYELEMENT]         = new String("GetObjectArrayElement")         ;	  
    names[SETOBJECTARRAYELEMENT]         = new String("SetObjectArrayElement")         ;	  
    names[NEWBOOLEANARRAY]               = new String("NewBooleanArray")               ;	  
    names[NEWBYTEARRAY]                  = new String("NewByteArray")                  ;	  
    names[NEWCHARARRAY]                  = new String("NewCharArray")                  ;	  
    names[NEWSHORTARRAY]                 = new String("NewShortArray")                 ;	  
    names[NEWINTARRAY]                   = new String("NewIntArray")                   ;	  
    names[NEWLONGARRAY]                  = new String("NewLongArray")                  ;	  
    names[NEWFLOATARRAY]                 = new String("NewFloatArray")                 ;	  
    names[NEWDOUBLEARRAY]                = new String("NewDoubleArray")                ;	  
    names[GETBOOLEANARRAYELEMENTS]       = new String("GetBooleanArrayElements")       ;	  
    names[GETBYTEARRAYELEMENTS]          = new String("GetByteArrayElements")          ;	  
    names[GETCHARARRAYELEMENTS]          = new String("GetCharArrayElements")          ;	  
    names[GETSHORTARRAYELEMENTS]         = new String("GetShortArrayElements")         ;	  
    names[GETINTARRAYELEMENTS]           = new String("GetIntArrayElements")           ;	  
    names[GETLONGARRAYELEMENTS]          = new String("GetLongArrayElements")          ;	  
    names[GETFLOATARRAYELEMENTS]         = new String("GetFloatArrayElements")         ;	  
    names[GETDOUBLEARRAYELEMENTS]        = new String("GetDoubleArrayElements")        ;	  
    names[RELEASEBOOLEANARRAYELEMENTS]   = new String("ReleaseBooleanArrayElements")   ;	  
    names[RELEASEBYTEARRAYELEMENTS]      = new String("ReleaseByteArrayElements")      ;	  
    names[RELEASECHARARRAYELEMENTS]      = new String("ReleaseCharArrayElements")      ;	  
    names[RELEASESHORTARRAYELEMENTS]     = new String("ReleaseShortArrayElements")     ;	  
    names[RELEASEINTARRAYELEMENTS]       = new String("ReleaseIntArrayElements")       ;	  
    names[RELEASELONGARRAYELEMENTS]      = new String("ReleaseLongArrayElements")      ;	  
    names[RELEASEFLOATARRAYELEMENTS]     = new String("ReleaseFloatArrayElements")     ;	  
    names[RELEASEDOUBLEARRAYELEMENTS]    = new String("ReleaseDoubleArrayElements")    ;	  
    names[GETBOOLEANARRAYREGION]         = new String("GetBooleanArrayRegion")         ;	  
    names[GETBYTEARRAYREGION]            = new String("GetByteArrayRegion")            ;	  
    names[GETCHARARRAYREGION]            = new String("GetCharArrayRegion")            ;	  
    names[GETSHORTARRAYREGION]           = new String("GetShortArrayRegion")           ;	  
    names[GETINTARRAYREGION]             = new String("GetIntArrayRegion")             ;	  
    names[GETLONGARRAYREGION]            = new String("GetLongArrayRegion")            ;	  
    names[GETFLOATARRAYREGION]           = new String("GetFloatArrayRegion")           ;	  
    names[GETDOUBLEARRAYREGION]          = new String("GetDoubleArrayRegion")          ;	  
    names[SETBOOLEANARRAYREGION]         = new String("SetBooleanArrayRegion")         ;	  
    names[SETBYTEARRAYREGION]            = new String("SetByteArrayRegion")            ;	  
    names[SETCHARARRAYREGION]            = new String("SetCharArrayRegion")            ;	  
    names[SETSHORTARRAYREGION]           = new String("SetShortArrayRegion")           ;	  
    names[SETINTARRAYREGION]             = new String("SetIntArrayRegion")             ;	  
    names[SETLONGARRAYREGION]            = new String("SetLongArrayRegion")            ;	  
    names[SETFLOATARRAYREGION]           = new String("SetFloatArrayRegion")           ;	  
    names[SETDOUBLEARRAYREGION]          = new String("SetDoubleArrayRegion")          ;	  
    names[REGISTERNATIVES]               = new String("RegisterNatives")               ;	  
    names[UNREGISTERNATIVES]             = new String("UnregisterNatives")             ;	  
    names[MONITORENTER]                  = new String("MonitorEnter")                  ;	  
    names[MONITOREXIT]                   = new String("MonitorExit")                   ;	  
    names[GETJAVAVM]                     = new String("GetJavaVM")                     ;	  
    names[GETSTRINGREGION]             	 = new String("GetStringRegion");           // JDK 1.2, #220
    names[GETSTRINGUTFREGION]         	 = new String("GetStringUTFRegion");        // JDK 1.2, #221
    names[GETPRIMITIVEARRAYCRITICAL]   	 = new String("GetPrimitiveArrayCritical"); // JDK 1.2, #222
    names[RELEASEPRIMITIVEARRAYCRITICAL] = new String("ReleasePrimitiveArrayCritical"); // JDK 1.2, #223
    names[GETSTRINGCRITICAL]           	 = new String("GetStringCritical");         // JDK 1.2, # 224
    names[RELEASESTRINGCRITICAL]       	 = new String("ReleaseStringCritical");     // JDK 1.2, #225
    names[NEWWEAKGLOBALREF]            	 = new String("NewWeakGlobalRef");    	    // JDK 1.2, #226
    names[DELETEWEAKGLOBALREF]         	 = new String("DeleteWeakGlobalRef"); 	    // JDK 1.2, #227
    names[EXCEPTIONCHECK]              	 = new String("ExceptionCheck");      	    // JDK 1.2, #228

    return names;

  }


  /*****************************************************************************
   * Utility function called from VM_JNIFunction
   * (cannot be placed in VM_JNIFunction because methods there are specially compiled
   * to be called from native)
   *****************************************************************************/


  /**
   * Get a VM_Field of an object given the index for this field
   * @param obj an Object
   * @param fieldIndex an index into the VM_Field array that describes the fields of this object
   * @return the VM_Field pointed to by the index, or null if the index or the object is invalid
   *
   */
  public static VM_Field getFieldAtIndex (Object obj, int fieldIndex) {   
    // VM.sysWrite("GetObjectField: field at index " + fieldIndex + "\n");

    VM_Type objType = VM_Magic.getObjectType(obj);
    if (objType.isClassType()) {
      VM_Field[] fields = objType.asClass().getInstanceFields();
      if (fieldIndex>=fields.length) {
	return null;                                      // invalid field index
      } else {
	return fields[fieldIndex];
      } 
    } else {
      // object is not a class type, probably an array or an invalid reference
      return null;
    }
  }



  /**
   * Common code shared by the JNI functions NewObjectA, NewObjectV, NewObject
   * (object creation)
   * @param methodID the method ID for a constructor
   * @return a new object created by the specified constructor
   * @see VM_JNIFunctions.CallStaticIntMethod, VM_JNIFunctions.CallStaticLongMethod, etc.
   */
  public static Object invokeInitializer(Class cls, int methodID, int argAddress, 
					 boolean isJvalue, boolean isDotDotStyle) 
    throws Exception {

    // get the parameter list as Java class
    VM_Method mth = VM_MethodDictionary.getValue(methodID);
    VM_Type[] argTypes = mth.getParameterTypes();
    Class[]   argClasses = new Class[argTypes.length];
    for (int i=0; i<argClasses.length; i++) {
      argClasses[i] = argTypes[i].getClassForType();
    }

    Constructor constMethod = cls.getConstructor(argClasses);
    if (constMethod==null)
      throw new Exception("Constructor not found");


    // Package the parameters for the constructor
    int varargAddress;
    if (isDotDotStyle) 
      // flag is false because this JNI function has 3 args before the var args
      varargAddress = pushVarArgToSpillArea(methodID, false);    
    else
      varargAddress = argAddress;

    Object argObjs[];
    if (isJvalue)
      argObjs = packageParameterFromJValue(mth, argAddress);
    else
      argObjs = packageParameterFromVarArg(mth, varargAddress);

    // construct the new object
    Object newobj = constMethod.newInstance(argObjs);
    
    return newobj;

  }


  /**
   * Common code shared by the JNI functions CallStatic<type>Method
   * (static method invocation)
   * @param methodID the method ID
   * @param expectReturnType the return type of the method to be invoked
   * @return an object that may be the return object or a wrapper for the primitive return value 
   * @see VM_JNIFunctions.CallStaticIntMethod, VM_JNIFunctions.CallStaticLongMethod, etc.
   */
  public static Object invokeWithDotDotVarArg(int methodID, VM_Type expectReturnType)
    throws Exception {
    
    int varargAddress = pushVarArgToSpillArea(methodID, false);    
    return packageAndInvoke(null, methodID, varargAddress, expectReturnType, false, true);

  }

  /**
   * Common code shared by the JNI functions Call<type>Method
   * (virtual method invocation)
   * @param obj the object instance 
   * @param methodID the method ID
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args  true if the calling JNI Function takes 4 args before the vararg
   *                   false if the calling JNI Function takes 3 args before the vararg
   * @return an object that may be the return object or a wrapper for the primitive return value 
   * @see VM_JNIFunctions.CallIntMethod, VM_JNIFunctions.CallLongMethod, etc.
   */
  public static Object invokeWithDotDotVarArg(Object obj, int methodID, 
					      VM_Type expectReturnType, boolean skip4Args)
    throws Exception {

    int varargAddress = pushVarArgToSpillArea(methodID, skip4Args);    
    return packageAndInvoke(obj, methodID, varargAddress, expectReturnType, skip4Args, true);

  }


  /**
   * This method supports var args passed from C
   *
   * In the AIX C convention, the caller keeps the first 8 words in registers and 
   * the rest in the spill area in the caller frame.  The callee will push the values
   * in registers out to the spill area of the caller frame and use the beginning 
   * address of this spill area as the var arg address
   *
   * For the JNI functions that takes var args, their prolog code will save the
   * var arg in the glue frame because the values in the register may be lost by 
   * subsequent calls.
   *
   * This method copies the var arg values that were saved earlier in glue frame into
   * the spill area of the original caller, thereby doing the work that the callee
   * normally performs in the AIX C convention.
   *
   * NOTE: This method contains internal stack pointer.
   * For now we assume that the stack will not be relocatable while native code is running
   * because native code can hold an address into the stack, so this code is OK,
   * but this is an issue to be resolved later
   *
   * NOTE:  this method assumes that it is immediately above the 
   * invokeWithDotDotVarArg frame, the JNI frame, the glue frame and 
   * the C caller frame in the respective order.  
   * Therefore, this method will not work if called from anywhere else
   *
   *
   *
   *   |  fp  | <- VM_JNIEnvironment.pushVarArgToSpillArea
   *   | mid  |
   *   | xxx  |
   *   |      |
   *   |      |
   *   |------|   
   *   |  fp  | <- VM_JNIEnvironment.invokeWithDotDotVarArg frame
   *   | mid  |
   *   | xxx  |
   *   |      |
   *   |      |
   *   |      |
   *   |------|   
   *   |  fp  | <- JNI method frame
   *   | mid  |
   *   | xxx  |
   *   |      |
   *   |      |
   *   |      |
   *   |------|
   *   |  fp  | <- glue frame
   *   | mid  |
   *   + xxx  +
   *   | r3   |   volatile save area
   *   | r4   |
   *   | r5   |
   *   | r6   |   vararg GPR[6-10]save area   <- VARARG_AREA_OFFSET
   *   | r7   |
   *   | r8   |
   *   | r9   |
   *   | r10  |
   *   | fpr1 |   vararg FPR[1-3] save area (also used as volatile FPR[1-6] save area)
   *   | fpr2 |
   *   | fpr3 |
   *   | fpr4 |
   *   | fpr5 |
   *   + fpr6 +
   *   | r13  |   nonvolatile GPR[13-31] save area
   *   | ...  |
   *   + r31  +
   *   | fpr14|   nonvolatile FPR[14-31] save area
   *   | ...  |
   *   | fpr31|
   *   |topjav|   offset to preceding Java to C glue frame
   *   |------|  
   *   | fp   | <- Native C caller frame
   *   | cr   |
   *   | lr   |
   *   | resv |
   *   | resv |
   *   + toc  +
   *   |   0  |    spill area initially not filled
   *   |   1  |    to be filled by this method
   *   |   2  |
   *   |   3  |
   *   |   4  |
   *   |   5  |
   *   |   6  |
   *   |   7  |
   *   |   8  |    spill area already filled by caller
   *   |   9  |
   *   |      |
   *   |      |
   *   |      |
   *
   * @param methodID an index into VM_MethodDictionary
   * @param skip4Args if true, the calling JNI function has 4 args before the vararg
   *                  if false, the calling JNI function has 3 args before the vararg
   * @return the starting address of the vararg in the caller stack frame
   */
  private static int pushVarArgToSpillArea(int methodID, boolean skip4Args) {

//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
// alternate implementation of jni
int glueFrameSize = NATIVE_TO_JAVA_GLUE_FRAME_SIZE;
//-#else
// default implementation of jni
int glueFrameSize = JNI_GLUE_FRAME_SIZE;
//-#endif

    // get the FP for this stack frame and traverse 2 frames to get to the glue frame
    int fp = VM_Magic.getMemoryWord(VM_Magic.getFramePointer() + VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET);
    fp = VM_Magic.getMemoryWord(fp + VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET);
    int gluefp = VM_Magic.getMemoryWord(fp + VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET);

    // compute the offset into the area where the vararg GPR[6-10] and FPR[1-3] are saved
    // skipping the args which are not part of the arguments for the target method
    // For Call<type>Method functions and NewObject, skip 3 args
    // For CallNonvirtual<type>Method functions, skip 4 args
    int varargGPROffset = VARARG_AREA_OFFSET + (skip4Args ? 4 : 0);
    int varargFPROffset = varargGPROffset + 5*4 ;

    // compute the offset into the spill area of the native caller frame, 
    // skipping the args which are not part of the arguments for the target method
    // For Call<type>Method functions, skip 3 args
    // For CallNonvirtual<type>Method functions, skip 4 args
    int spillAreaLimit  = glueFrameSize + AIX_FRAME_HEADER_SIZE + 8*4;
    int spillAreaOffset = glueFrameSize + AIX_FRAME_HEADER_SIZE + 
                          (skip4Args ? 4*4 : 3*4);

    // address to return pointing to the var arg list
    int varargAddress = gluefp + spillAreaOffset;   

    // VM.sysWrite("pushVarArgToSpillArea:  var arg at " + 
    // 		   VM.intAsHexString(varargAddress) + "\n");
 
    VM_Method targetMethod = VM_MethodDictionary.getValue(methodID);
    VM_Type[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;

    for (int i=0; i<argCount && spillAreaOffset<spillAreaLimit ; i++) {
      int hiword, loword;

      if (argTypes[i].isFloatType() || argTypes[i].isDoubleType()) {
	// move 2 words from the vararg FPR save area into the spill area of the caller
	hiword = VM_Magic.getMemoryWord(gluefp + varargFPROffset);
	varargFPROffset+=4;
	loword = VM_Magic.getMemoryWord(gluefp + varargFPROffset);
	varargFPROffset+=4;
	VM_Magic.setMemoryWord(gluefp + spillAreaOffset, hiword);
	spillAreaOffset+=4;
	VM_Magic.setMemoryWord(gluefp + spillAreaOffset, loword);
	spillAreaOffset+=4;
      } 

      else if (argTypes[i].isLongType()) {
	// move 2 words from the vararg GPR save area into the spill area of the caller
	hiword = VM_Magic.getMemoryWord(gluefp + varargGPROffset);
	varargGPROffset+=4;
	VM_Magic.setMemoryWord(gluefp + spillAreaOffset, hiword);
	spillAreaOffset+=4;
	// this covers the case when the long value straddles the spill boundary
	if (spillAreaOffset<spillAreaLimit) {
	  loword = VM_Magic.getMemoryWord(gluefp + varargGPROffset);
	  varargGPROffset+=4;
	  VM_Magic.setMemoryWord(gluefp + spillAreaOffset, loword);
	  spillAreaOffset+=4;
	}
      }

      else {
	hiword = VM_Magic.getMemoryWord(gluefp + varargGPROffset);
	varargGPROffset+=4;
	VM_Magic.setMemoryWord(gluefp + spillAreaOffset, hiword);
	spillAreaOffset+=4;
      }

    }

    // At this point, all the vararg values should be in the spill area in the caller frame
    // return the address of the beginning of the vararg to use in invoking the target method
    return varargAddress;

  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodV
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @return an object that may be the return object or a wrapper for the primitive return value 
   * @see VM_JNIFunctions.CallStaticIntMethodV, VM_JNIFunctions.CallStaticLongMethodV, etc.
   */
  public static Object invokeWithVarArg(int methodID, int argAddress, VM_Type expectReturnType) 
    throws Exception {

    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, true);

  }

  /**
   * Common code shared by the JNI functions Call<type>MethodV
   * @param obj the object instance 
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args received from the JNI function, passed on to VM_Reflection.invoke()
   * @return an object that may be the return object or a wrapper for the primitive return value 
   * @see VM_JNIFunctions.CallStaticIntMethodV, VM_JNIFunctions.CallStaticLongMethodV, etc.
   */
  public static Object invokeWithVarArg(Object obj, int methodID, int argAddress, 
					VM_Type expectReturnType, boolean skip4Args) 
    throws Exception {

    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, true);

  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodA
   * @param methodID an index into the VM_MethodDictionary
   * @param argAddress a raw address for the argument array
   * @return an object that may be the return object or a wrapper for the primitive return value 
   * @see VM_JNIFunctions.CallStaticIntMethodA, VM_JNIFunctions.CallStaticLongMethodA, etc.
   */
  public static Object invokeWithJValue(int methodID, int argAddress, VM_Type expectReturnType) 
    throws Exception {
    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, false);
  }

  /**
   * Common code shared by the JNI functions Call<type>MethodA
   * @param obj the object instance 
   * @param methodID an index into the VM_MethodDictionary
   * @param argAddress a raw address for the argument array
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args received from the JNI function, passed on to VM_Reflection.invoke()
   * @return an object that may be the return object or a wrapper for the primitive return value 
   * @see VM_JNIFunctions.CallStaticIntMethodA, VM_JNIFunctions.CallStaticLongMethodA, etc.
   */
  public static Object invokeWithJValue(Object obj, int methodID, int argAddress, 
					VM_Type expectReturnType, boolean skip4Args) 
    throws Exception {
    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, false);
  }


  /**
   * Common code shared by invokeWithJValue, invokeWithVarArg and invokeWithDotDotVarArg
   * @param obj the object instance 
   * @param methodID an index into the VM_MethodDictionary
   * @param argAddress a raw address for the argument array
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args This flag is received from the JNI function and passed directly to 
   *                     VM_Reflection.invoke().  
   *                     It is true if the actual method is to be invoked, which could be
   *                     from the superclass.
   *                     It is false if the method from the real class of the object 
   *                     is to be invoked, which may not be the actual method specified by methodID
   * @param isVarArg  This flag describes whether the array of parameters is in var arg format or
   *                  jvalue format
   * @return an object that may be the return object or a wrapper for the primitive return value 
   * @see invokeWithJValue, invokeWithVarArg, invokeWithDotDotVarArg
   */
  public static Object packageAndInvoke(Object obj, int methodID, int argAddress, 
					VM_Type expectReturnType, boolean skip4Args, 
					boolean isVarArg) 
    throws Exception {
  
    VM_Method targetMethod;
    int returnValue;

    // VM.sysWrite("JNI CallXXXMethod:  method ID " + methodID + " with args at " + 
    // 		   VM.intAsHexString(argAddress) + "\n");
    
    targetMethod = VM_MethodDictionary.getValue(methodID);
    VM_Type returnType = targetMethod.getReturnType();

    // VM.sysWrite("JNI CallXXXMethod:  " + targetMethod.getDeclaringClass().toString() +
    //		"." + targetMethod.getName().toString() + "\n");

    if (expectReturnType==null) {   // for reference return type 
      if (!returnType.isReferenceType())
	throw new Exception("Wrong return type for method: expect reference type instead of " + returnType);      
    } 
    else {    // for primitive return type
      if (returnType!=expectReturnType) 
	throw new Exception("Wrong return type for method: expect " + expectReturnType + 
			    " instead of " + returnType);
    }  

    // Repackage the arguments into an array of objects based on the signature of this method
    Object[] argObjectArray;
    if (isVarArg)
      argObjectArray = packageParameterFromVarArg(targetMethod, argAddress);
    else
      argObjectArray = packageParameterFromJValue(targetMethod, argAddress);

    // now invoke the method
    Object returnObj = VM_Reflection.invoke(targetMethod, obj, argObjectArray, skip4Args);
    
    return returnObj;
  }


  /**
   * Repackage the arguments passed as a variable argument list into an array of Object,
   * used by the JNI functions CallStatic<type>MethodV
   * @param mth the target VM_Method
   * @param argAddress an address into the C space for the array of jvalue unions;  
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   * @see packageAndInvoke()
   */
  static Object[] packageParameterFromVarArg(VM_Method targetMethod, int argAddress) {
    VM_Type[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    // get the VM_JNIEnvironment for this thread in case we need to dereference any object arg
    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();

    // VM.sysWrite("JNI packageParameterFromVarArg: packaging " + argCount + " arguments\n");

    for (int i=0, addr=argAddress; i<argCount; i++) {
      int loword, hiword;
      hiword = VM_Magic.getMemoryWord(addr);

      // VM.sysWrite("JNI packageParameterFromVarArg:  arg " + i + " = " + hiword + 
      // " or " + VM.intAsHexString(hiword) + "\n");

      addr+=4;

      // convert and wrap the argument according to the expected type

      if (argTypes[i].isFloatType()) {
	// NOTE:  in VarArg convention, C compiler will expand a float to a double that occupy 2 words
	// so we have to extract it as a double and convert it back to a float
	loword = VM_Magic.getMemoryWord(addr);
	addr+=4;                       
	long doubleBits = (((long) hiword) << 32) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapFloat((float) (Double.longBitsToDouble(doubleBits)));
	
      } else if (argTypes[i].isDoubleType()) {
	loword = VM_Magic.getMemoryWord(addr);
	addr+=4;
	long doubleBits = (((long) hiword) << 32) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));

      } else if (argTypes[i].isLongType()) { 
	loword = VM_Magic.getMemoryWord(addr);
	addr+=4;
	long longValue = (((long) hiword) << 32) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapLong(longValue);

      } else if (argTypes[i].isBooleanType()) {
	// the 0/1 bit is stored in the high byte	
	argObjectArray[i] = VM_Reflection.wrapBoolean(hiword);

      } else if (argTypes[i].isByteType()) {
	// the target byte is stored in the high byte
	argObjectArray[i] = VM_Reflection.wrapByte((byte) hiword);

      } else if (argTypes[i].isCharType()) {
	// char is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapChar((char) hiword);

      } else if (argTypes[i].isShortType()) {
	// short is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapShort((short) hiword);

      } else if (argTypes[i].isReferenceType()) {
	// for object, the arg is a JREF index, dereference to get the real object
	argObjectArray[i] =  env.getJNIRef(hiword);   

      } else if (argTypes[i].isIntType()) {
	argObjectArray[i] = VM_Reflection.wrapInt(hiword);

      } else {
	return null;
      }

    }

    return argObjectArray;
    

  }

  /**
   * Repackage the arguments passed as an array of jvalue into an array of Object,
   * used by the JNI functions CallStatic<type>MethodA
   * @param mth the target VM_Method
   * @param argAddress an address into the C space for the array of jvalue unions;  
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParameterFromJValue(VM_Method targetMethod, int argAddress) {
    VM_Type[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    // get the VM_JNIEnvironment for this thread in case we need to dereference any object arg
    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();

    // VM.sysWrite("JNI packageParameterFromJValue: packaging " + argCount + " arguments\n");

    for (int i=0, addr=argAddress; i<argCount; i++, addr+=8) {
      int loword, hiword;
      hiword = VM_Magic.getMemoryWord(addr);

      // VM.sysWrite("JNI packageParameterFromJValue:  arg " + i + " = " + hiword + 
      //	  " or " + VM.intAsHexString(hiword) + "\n");

      // convert and wrap the argument according to the expected type

      if (argTypes[i].isFloatType()) {
	argObjectArray[i] = VM_Reflection.wrapFloat(Float.intBitsToFloat(hiword));

      } else if (argTypes[i].isDoubleType()) {
	loword = VM_Magic.getMemoryWord(addr+4);
	long doubleBits = (((long) hiword) << 32) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));

      } else if (argTypes[i].isLongType()) { 
	loword = VM_Magic.getMemoryWord(addr+4);
	long longValue = (((long) hiword) << 32) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapLong(longValue);

      } else if (argTypes[i].isBooleanType()) {
	// the 0/1 bit is stored in the high byte	
	argObjectArray[i] = VM_Reflection.wrapBoolean((hiword & 0xFF000000) >>> 24);

      } else if (argTypes[i].isByteType()) {
	// the target byte is stored in the high byte
	argObjectArray[i] = VM_Reflection.wrapByte((byte) ((hiword & 0xFF000000) >>> 24));

      } else if (argTypes[i].isCharType()) {
	// char is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapChar((char) ((hiword & 0xFFFF0000) >>> 16));

      } else if (argTypes[i].isShortType()) {
	// short is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapShort((short) ((hiword & 0xFFFF0000) >>> 16));

      } else if (argTypes[i].isReferenceType()) {
	// for object, the arg is a JREF index, dereference to get the real object
	argObjectArray[i] =  env.getJNIRef(hiword);   

      } else if (argTypes[i].isIntType()) {
	argObjectArray[i] = VM_Reflection.wrapInt(hiword);

      } else {
	return null;
      }

    }

    return argObjectArray;
    
  }


  /**
   * Given an address in C that points to a null-terminated string,
   * create a new Java byte[] with a copy of the string
   * @param stringAddress an address in C space for a string
   * @return a new Java byte[]
   */
  static byte[] createByteArrayFromC(int stringAddress) {
    int word;
    int length = 0;
    int addr = stringAddress;

    // scan the memory for the null termination of the string
    while (true) {
      word = VM_Magic.getMemoryWord(addr);
      int byte0 = ((word >> 24) & 0xFF);
      int byte1 = ((word >> 16) & 0xFF);
      int byte2 = ((word >> 8) & 0xFF);
      int byte3 = (word & 0xFF);
      if (byte0==0)
	break;
      length++;
      if (byte1==0) 
	break;
      length++;
      if (byte2==0)
	break;
      length++;
      if (byte3==0)
	break;
      length++;
      addr += 4;
    }

   byte[] contents = new byte[length];
   VM_Memory.memcopy(VM_Magic.objectAsAddress(contents), stringAddress, length);
   
   return contents;
  }


  /**
   * Given an address in C that points to a null-terminated string,
   * create a new Java String with a copy of the string
   * @param stringAddress an address in C space for a string
   * @return a new Java String
   */
  static String createStringFromC(int stringAddress) {

    byte[] contents = createByteArrayFromC( stringAddress );
    return new String(contents);

  }

  public void dumpJniRefsStack () {
    int jniRefOffset = JNIRefsTop;
    VM.sysWrite("\n* * dump of JNIEnvironment JniRefs Stack * *\n");
    VM.sysWrite("* JNIRefs = ");
    VM.sysWriteHex(VM_Magic.objectAsAddress(JNIRefs));
    VM.sysWrite(" * JNIRefsTop = ");
    VM.sysWrite(JNIRefsTop,false);
    VM.sysWrite(" * JNIRefsSavedFP = ");
    VM.sysWrite(JNIRefsSavedFP,false);
    VM.sysWrite(".\n*\n");
    while ( jniRefOffset >= 0 ) {
      VM.sysWrite(jniRefOffset,false);
      VM.sysWrite(" ");
      VM.sysWriteHex(VM_Magic.objectAsAddress(JNIRefs)+jniRefOffset);
      VM.sysWrite(" ");
      VM_GCUtil.dumpRef(JNIRefs[ jniRefOffset >> 2 ]);
      jniRefOffset -= 4;
    }
    VM.sysWrite("\n* * end of dump * *\n");
  }

}
