/*
 * (C) Copyright IBM Corp 2001, 2002, 2003
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import java.lang.ref.Reference;

/**
 * A virtual machine.
 * Implements VM_Uninterruptible to suppress thread switching in boot().
 *
 * @author Derek Lieber (project start).
 * @date 21 Nov 1997 
 *
 * @modified Steven Augart (to catch recursive shutdowns, 
 *                          such as when out of memory)
 * @date 10 July 2003
 */
public class VM extends VM_Properties implements VM_Constants, 
                                                 VM_Uninterruptible { 

  //----------------------------------------------------------------------//
  //                          Initialization.                             //
  //----------------------------------------------------------------------//

  /** 
   * Prepare vm classes for use by boot image writer.
   * @param classPath class path to be used by VM_ClassLoader
   * @param bootCompilerArgs command line arguments for the bootimage compiler
   */ 
  public static void initForBootImageWriter(String classPath, 
                                            String[] bootCompilerArgs) 
    throws VM_PragmaInterruptible {
    writingBootImage = true;
    init(classPath, bootCompilerArgs);
  }

  /**
   * Prepare vm classes for use by tools.
   */
  public static void initForTool() 
    throws VM_PragmaInterruptible {
    runningTool = true;
    init(System.getProperty("java.class.path"), null);
  }

  /**
   * Prepare vm classes for use by tools.
   * @param classpath class path to be used by VM_ClassLoader
   */
  public static void initForTool(String classpath) 
    throws VM_PragmaInterruptible {
    runningTool = true;
    init(classpath, null);
  }

  /**
   * Begin vm execution.
   * The following machine registers are set by "C" bootstrap program 
   * before calling this method:
   *    JTOC_POINTER        - required for accessing globals
   *    FRAME_POINTER       - required for accessing locals
   *    THREAD_ID_REGISTER  - required for method prolog (stack overflow check)
   * @exception Exception
   */
  public static void boot() throws Exception, 
                                   VM_PragmaLogicallyUninterruptible {
    writingBootImage = false;
    runningVM        = true;
    runningAsSubsystem = false;
    verboseBoot = VM_BootRecord.the_boot_record.verboseBoot;
    sysWriteLockOffset = VM_Entrypoints.sysWriteLockField.getOffset();
    if (verboseBoot >= 1) VM.sysWriteln("Booting");

    // Set up the current VM_Processor object.  The bootstrap program
    // has placed a pointer to the current VM_Processor in a special
    // register.
    if (verboseBoot >= 1) VM.sysWriteln("Setting up current VM_Processor");
    VM_ProcessorLocalState.boot();

    // Finish thread initialization that couldn't be done in boot image.
    // The "stackLimit" must be set before any interruptible methods are called
    // because it's accessed by compiler-generated stack overflow checks.
    //
    if (verboseBoot >= 1) VM.sysWriteln("Doing thread initialization");
    VM_Thread currentThread = VM_Processor.getCurrentProcessor().activeThread;
    currentThread.stackLimit = VM_Magic.objectAsAddress(currentThread.stack).add(STACK_SIZE_GUARD);
    VM_Processor.getCurrentProcessor().activeThreadStackLimit = currentThread.stackLimit;

    // get pthread_id from OS and store into vm_processor field
    // 
    if (!BuildForSingleVirtualProcessor)
      VM_Processor.getCurrentProcessor().pthread_id = VM_SysCall.sysPthreadSelf();

    // Initialize memory manager's virtual processor local state.
    // This must happen before any putfield or arraystore of object refs
    // because the buffer is accessed by compiler-generated write barrier code.
    //
    if (verboseBoot >= 1) VM.sysWriteln("Setting up write barrier");
    MM_Interface.setupProcessor(VM_Processor.getCurrentProcessor());
    
    // Initialize memory manager.
    //    This must happen before any uses of "new".
    //
    if (verboseBoot >= 1) VM.sysWriteln("Setting up memory manager: bootrecord = ", VM_Magic.objectAsAddress(VM_BootRecord.the_boot_record));
    MM_Interface.boot(VM_BootRecord.the_boot_record);
    
    // Start calculation of cycles to millsecond conversion factor
    if (verboseBoot >= 1) VM.sysWriteln("Stage one of booting VM_Time");
    VM_Time.bootStageOne();

    // Reset the options for the baseline compiler to avoid carrying 
    // them over from bootimage writing time.
    // 
    if (verboseBoot >= 1) VM.sysWriteln("Initializing baseline compiler options to defaults");
    VM_BaselineCompiler.initOptions();

    // Create class objects for static synchronized methods in the bootimage.
    // This must happen before any static synchronized methods of bootimage 
    // classes can be invoked.
    if (verboseBoot >= 1) VM.sysWriteln("Creating class objects for static synchronized methods");
    createClassObjects();

    // Fetch arguments from program command line.
    //
    if (verboseBoot >= 1) VM.sysWriteln("Fetching command-line arguments");
    VM_CommandLineArgs.fetchCommandLineArguments();

    // Process most virtual machine command line arguments.
    //
    if (verboseBoot >= 1) VM.sysWriteln("Early stage processing of command line");
    VM_CommandLineArgs.earlyProcessCommandLineArguments();

    // Allow Memory Manager to respond to its command line arguments
    //
    if (verboseBoot >= 1) VM.sysWriteln("Collector processing rest of boot options");
    MM_Interface.postBoot();

    // Initialize class loader.
    //
    if (verboseBoot >= 1) VM.sysWriteln("Initializing class loader");
    String vmClasses = VM_CommandLineArgs.getVMClasses();
    VM_ClassLoader.boot(vmClasses);
    VM_SystemClassLoader.boot();

    // Complete calculation of cycles to millsecond conversion factor
    // Must be done before any dynamic compilation occurs.
    if (verboseBoot >= 1) VM.sysWriteln("Stage two of booting VM_Time");
    VM_Time.bootStageTwo();

    // Initialize statics that couldn't be placed in bootimage, either 
    // because they refer to external state (open files), or because they 
    // appear in fields that are unique to Jikes RVM implementation of 
    // standard class library (not part of standard jdk).
    // We discover the latter by observing "host has no field" and 
    // "object not part of bootimage" messages printed out by bootimage 
    // writer.
    //
    if (verboseBoot >= 1) VM.sysWriteln("Running various class initializers");
    runClassInitializer("java.lang.Runtime");
    runClassInitializer("java.lang.System");
    runClassInitializer("java.io.File");
    runClassInitializer("java.lang.Boolean");
    runClassInitializer("java.lang.Byte");
    runClassInitializer("java.lang.Short");
    runClassInitializer("java.lang.Number");
    runClassInitializer("java.lang.Integer");
    runClassInitializer("java.lang.Long");
    runClassInitializer("java.lang.Float");
    runClassInitializer("java.lang.Character");
    runClassInitializer("gnu.java.io.EncodingManager");
    runClassInitializer("java.lang.Thread");
    runClassInitializer("java.lang.ThreadGroup");
    runClassInitializer("java.io.PrintWriter");
    runClassInitializer("gnu.java.lang.SystemClassLoader");
    runClassInitializer("java.lang.String");
    runClassInitializer("java.lang.VMString");
    runClassInitializer("gnu.java.security.provider.DefaultPolicy");
    runClassInitializer("java.security.Policy");
    runClassInitializer("java.util.WeakHashMap");
    runClassInitializer("java.lang.ClassLoader");
    runClassInitializer("java.lang.Math");
    runClassInitializer("java.util.TimeZone");
    runClassInitializer("java.util.Locale");
    runClassInitializer("java.util.Calendar");
    runClassInitializer("java.util.GregorianCalendar");
    runClassInitializer("java.util.ResourceBundle");
    runClassInitializer("java.util.zip.ZipEntry");
    runClassInitializer("java.util.zip.Inflater");
    runClassInitializer("java.util.zip.DeflaterHuffman");
    runClassInitializer("java.util.zip.InflaterDynHeader");
    runClassInitializer("java.util.zip.InflaterHuffmanTree");
    runClassInitializer("gnu.java.locale.Calendar");
    runClassInitializer("java.util.Date");
    //-#if RVM_WITH_ALL_CLASSES
    runClassInitializer("java.util.jar.Attributes$Name");
    //-#endif

    if (verboseBoot >= 1) VM.sysWriteln("Booting VM_Lock");
    VM_Lock.boot();
    
    // set up HPM
    //-#if RVM_WITH_HPM
    if (BuildForHPM) {
      if (VM_HardwarePerformanceMonitors.enabled()) {
        // assume only one Java thread is executing!
        if(VM_HardwarePerformanceMonitors.verbose>=2)
          VM.sysWriteln("VM.boot() call VM_HardwarePerformanceMonitors.boot()");
        VM_HardwarePerformanceMonitors.boot();
      }
    }
    //-#endif

    // Enable multiprocessing.
    // Among other things, after this returns, GC and dynamic class loading are enabled.
    // 
    if (verboseBoot >= 1) VM.sysWriteln("Booting scheduler");
    VM_Scheduler.boot();

    // Create JNI Environment for boot thread.  
    // After this point the boot thread can invoke native methods.
    com.ibm.JikesRVM.jni.VM_JNIEnvironment.boot();
    if (verboseBoot >= 1) VM.sysWriteln("Initializing JNI for boot thread");
    VM_Thread.getCurrentThread().initializeJNIEnv();

    //-#if RVM_WITH_HPM
    runClassInitializer("com.ibm.JikesRVM.Java2HPM");
    VM_HardwarePerformanceMonitors.setUpHPMinfo();
    //-#endif

    // Run class intializers that require JNI
    if (verboseBoot >= 1) VM.sysWriteln("Running late class initializers");
    runClassInitializer("java.io.FileDescriptor");
    runClassInitializer("java.lang.Double");
    runClassInitializer("java.util.PropertyPermission");
    runClassInitializer("com.ibm.JikesRVM.VM_Process");

    // Initialize java.lang.System.out, java.lang.System.err, java.lang.System.in
    VM_FileSystem.initializeStandardStreams();

    ///////////////////////////////////////////////////////////////
    // The VM is now fully booted.                               //
    // By this we mean that we can execute arbitrary Java code.  //
    ///////////////////////////////////////////////////////////////
    if (verboseBoot >= 1) VM.sysWriteln("VM is now fully booted");
    
    // Inform interested subsystems that VM is fully booted.
    VM.fullyBooted = true;
    MM_Interface.fullyBootedVM();
    VM_BaselineCompiler.fullyBootedVM();

    // Allow profile information to be read in from a file
    // 
    VM_EdgeCounts.boot();

    // Initialize compiler that compiles dynamically loaded classes.
    //
    if (verboseBoot >= 1) VM.sysWriteln("Initializing runtime compiler");
    VM_RuntimeCompiler.boot();

    // Process remainder of the VM's command line arguments.
    if (verboseBoot >= 1) VM.sysWriteln("Late stage processing of command line");
    String[] applicationArguments = VM_CommandLineArgs.lateProcessCommandLineArguments();

    if (VM.verboseClassLoading || verboseBoot >= 1) VM.sysWrite("[VM booted]\n");

    // set up JikesRVM socket I/O
    if (verboseBoot >= 1) VM.sysWriteln("Initializing socket factories");
    JikesRVMSocketImpl.boot();

    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    if (verboseBoot >= 1) VM.sysWriteln("Initializing adaptive system");
    com.ibm.JikesRVM.adaptive.VM_Controller.boot();
    //-#endif

    // The first argument must be a class name.
    if (verboseBoot >= 1) VM.sysWriteln("Extracting name of class to execute");
    if (applicationArguments.length == 0) {
      pleaseSpecifyAClass();
    }
    if (applicationArguments.length > 0 && 
        ! VM_TypeDescriptorParsing.isJavaClassName(applicationArguments[0])) {
      VM.sysWrite("vm: \"");
      VM.sysWrite(applicationArguments[0]);
      VM.sysWrite("\" is not a legal Java class name.\n");
      pleaseSpecifyAClass();
    }

    // Create main thread.
    // Work around class incompatibilities in boot image writer
    // (JDK's java.lang.Thread does not extend VM_Thread) [--IP].
    // Junk this when we do feature 3601.
    if (verboseBoot >= 1) VM.sysWriteln("Constructing mainThread");
    Thread      xx         = new MainThread(applicationArguments);
    VM_Address  yy         = VM_Magic.objectAsAddress(xx);
    VM_Thread   mainThread = (VM_Thread)VM_Magic.addressAsObject(yy);

    //-#if RVM_WITH_OSR
    // Notify application run start
    VM_Callbacks.notifyAppRunStart("VM", 0);
    //-#endif

    // Schedule "main" thread for execution.
    if (verboseBoot >= 1) VM.sysWriteln("Starting main thread");
    mainThread.start();

    // Create one debugger thread.
    VM_Thread t = new DebuggerThread();
    t.start(VM_Scheduler.debuggerQueue);

    //-#if RVM_WITH_HPM
    if (VM_HardwarePerformanceMonitors.enabled()) {
      // IS THIS NEEDED?
      if (!VM_HardwarePerformanceMonitors.thread_group) {
        if(VM_HardwarePerformanceMonitors.verbose>=2)
          VM.sysWrite(" VM.boot() call sysHPMresetMyThread()\n");
        VM_SysCall.sysHPMresetMyThread();
      }
    }
    //-#endif

    // End of boot thread.
    //
    if (VM.TraceThreads) VM_Scheduler.trace("VM.boot", "completed - terminating");
    VM_Thread.terminate();
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  private static void pleaseSpecifyAClass() throws VM_PragmaInterruptible {
    VM.sysWrite("vm: Please specify a class to execute.\n");
    VM.sysWrite("vm:   You can invoke the VM with the \"-help\" flag for usage information.\n");
    VM.sysExit(VM.exitStatusBogusCommandLineArg);
  }


  private static VM_Class[] classObjects = new VM_Class[0];
  /**
   * Called by the compilers when compiling a static synchronized method
   * during bootimage writing.
   */
  public static void deferClassObjectCreation(VM_Class c) throws VM_PragmaInterruptible {
    for (int i=0; i<classObjects.length; i++) {
      if (classObjects[i] == c) return; // already recorded
    }
    VM_Class[] tmp = new VM_Class[classObjects.length+1];
    System.arraycopy(classObjects, 0, tmp, 0, classObjects.length);
    tmp[classObjects.length] = c;
    classObjects = tmp;
  }

  /**
   * Create the java.lang.Class objects needed for 
   * static synchronized methods in the bootimage.
   */
  private static void createClassObjects() throws VM_PragmaInterruptible {
    for (int i=0; i<classObjects.length; i++) {
      if (verboseBoot >= 2) {
        VM.sysWriteln(classObjects[i].toString()); 
      }
      classObjects[i].getClassForType();
    }
  }

  /**
   * Run <clinit> method of specified class, if that class appears 
   * in bootimage and actually has a clinit method (we are flexible to
   * allow one list of classes to work with different bootimages and 
   * different version of classpath (eg 0.05 vs. cvs head).
   * 
   * This method is called only while the VM boots.
   * 
   * @param className
   */
  static void runClassInitializer(String className) throws VM_PragmaInterruptible {
    if (verboseBoot >= 2) {
      sysWrite("running class intializer for ");
      sysWriteln(className);
    }
    VM_Atom  classDescriptor = 
      VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();
    VM_TypeReference tRef = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), classDescriptor);
    VM_Class cls = (VM_Class)tRef.peekResolvedType();
    if (cls != null && cls.isInBootImage()) {
      VM_Method clinit = cls.getClassInitializerMethod();
      if (clinit != null) {
        clinit.compile();
        if (verboseBoot >= 10) VM.sysWriteln("invoking method " + clinit);
        try {
          VM_Magic.invokeClassInitializer(clinit.getCurrentInstructions());
        } catch (Error e) {
          throw e;
        } catch (Throwable t) {
          ExceptionInInitializerError eieio
            = new ExceptionInInitializerError("Caught exception while invoking the class initializer for "
                                              +  className);
          eieio.initCause(t);
          throw eieio;
        }
      } else {
        if (verboseBoot >= 10) VM.sysWriteln("has no clinit method ");
      } 
      cls.setAllFinalStaticJTOCEntries();
    }
  }

  //----------------------------------------------------------------------//
  //                         Execution environment.                       //
  //----------------------------------------------------------------------//

  /**
   * Verify a runtime assertion (die w/traceback if assertion fails).
   * Note: code your assertion checks as 
   * "if (VM.VerifyAssertions) VM._assert(xxx);"
   * @param b the assertion to verify
   */
  public static void _assert(boolean b) {
    _assert(b, null, null);
  }

  /**
   * Verify a runtime assertion (die w/message and traceback if 
   * assertion fails).   Note: code your assertion checks as 
   * "if (VM.VerifyAssertions) VM._assert(xxx,yyy);"
   * @param b the assertion to verify
   * @param message the message to print if the assertion is false
   */
  public static void _assert(boolean b, String message) {
    _assert(b, message, null);
  }

  public static void _assert(boolean b, String msg1, String msg2) {
    if (!VM.VerifyAssertions) {
      sysWriteln("vm: somebody forgot to conditionalize their call to assert with");
      sysWriteln("vm: if (VM.VerifyAssertions)");
      _assertionFailure("vm internal error: assert called when !VM.VerifyAssertions", null);
    }
    if (!b) _assertionFailure(msg1, msg2);
  }




  private static void _assertionFailure(String msg1, String msg2) 
    throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline 
  {
    if (msg1 == null && msg2 == null)
      msg1 = "vm internal error at:";
    if (msg2 == null) {
      msg2 = msg1;
      msg1 = null;
    }
    if (VM.runningVM) {
      if (msg1 != null) {
        sysWrite(msg1);
        //      sysWrite(": ");
      }
      sysFail(msg2);
    }
    throw new RuntimeException((msg1 != null ? msg1 : "") 
                               // + ( (msg1 != null) ? ": " : "") 
                               + msg2);
  }


  /**
   * Format a 32 bit number as "0x" followed by 8 hex digits.
   * Do this without referencing Integer or Character classes, 
   * in order to avoid dynamic linking.
   * TODO: move this method to VM_Services.
   * @param number
   * @return a String with the hex representation of the integer
   */
  public static String intAsHexString(int number) throws VM_PragmaInterruptible {
    char[] buf   = new char[10];
    int    index = 10;
    while (--index > 1) {
      int digit = number & 0x0000000f;
      buf[index] = digit <= 9 ? (char)('0' + digit) : (char)('a' + digit - 10);
      number >>= 4;
    }
    buf[index--] = 'x';
    buf[index]   = '0';
    return new String(buf);
  }


  /**
   * Format a 32/64 bit number as "0x" followed by 8/16 hex digits.
   * Do this without referencing Integer or Character classes, 
   * in order to avoid dynamic linking.
   * TODO: move this method to VM_Services.
   * @param number
   * @return a String with the hex representation of an Address
   */
  public static String addressAsHexString(VM_Address addr) throws VM_PragmaInterruptible {
    int len = 2 + (BITS_IN_ADDRESS>>2);
    char[] buf   = new char[len];
    while (--len > 1) {
      int digit = addr.toInt() & 0x0F;
      buf[len] = digit <= 9 ? (char)('0' + digit) : (char)('a' + digit - 10);
      addr = addr.toWord().rshl(4).toAddress();
    }
    buf[len--] = 'x';
    buf[len]   = '0';
    return new String(buf);
  }

  private static int sysWriteLock = 0;
  private static int sysWriteLockOffset = -1;

  private static void swLock() {
    if (sysWriteLockOffset == -1) return;
    while (!VM_Synchronization.testAndSet(VM_Magic.getJTOC(), sysWriteLockOffset, 1)) 
      ;
  }

  private static void swUnlock() {
    if (sysWriteLockOffset == -1) return;
    VM_Synchronization.fetchAndStore(VM_Magic.getJTOC(), sysWriteLockOffset, 0);
  }

  /**
   * Low level print to console.
   * @param value  what is printed
   */
  public static void write(VM_Atom value) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    value.sysWrite();
  }

  /**
   * Low level print to console.
   * @param value  what is printed
   */
  public static void write(VM_Member value) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    write(value.getMemberRef());
  }

  /**
   * Low level print to console.
   * @param value  what is printed
   */
  public static void write(VM_MemberReference value) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    write(value.getType().getName());
    write(".");
    write(value.getName());
    write(" ");
    write(value.getDescriptor());
  }

  /**
   * Low level print to console.
   * @param value   what is printed
   */
  public static void write(String value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (value == null) {
      write("null");
    } else {
      if (runningVM) {
        char[] chars = java.lang.JikesRVMSupport.getBackingCharArray(value);
        int numChars = java.lang.JikesRVMSupport.getStringLength(value);
        int offset = java.lang.JikesRVMSupport.getStringOffset(value);
        for (int i = 0; i<numChars; i++) 
          write(chars[offset+i]);
      } else {
        System.err.print(value);
      }
    }
  }

  /**
   * Low level print to console.
   * @param value character array that is printed
   * @param len number of characters printed
   */
  public static void write(char[] value, int len) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    for (int i = 0, n = len; i < n; ++i) {
      if (runningVM)
        write(VM_Magic.getCharAtOffset(value, i << LOG_BYTES_IN_CHAR));
      else
        write(value[i]);
    }
  }

  /**
    * Low level print to console.
   * @param value       what is printed
   */
  public static void write(char value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM)
      VM_SysCall.sysWriteChar(value);
    else
      System.err.print(value);
  }


  /**
   * Low level print of double to console.  Can't pass doubles so printing code in Java.
   *
   * @param value   double to be printed
   * @param int     number of decimal places
   */
  public static void write(double value, int postDecimalDigits) 
    throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline 
    /* don't waste code space inlining these --dave */ {
    if (runningVM) {
      boolean negative = (value < 0.0);
      value = (value < 0.0) ? (-value) : value;
      int ones = (int) value;
      int multiplier = 1;
      while (postDecimalDigits-- > 0)
        multiplier *= 10;
      int remainder = (int) (multiplier * (value - ones));
      if (negative) write('-');
      write(ones, false); 
      write('.');
      while (multiplier > 1) {
        multiplier /= 10;
        write(remainder / multiplier);
        remainder %= multiplier;
      }
    }
    else
      System.err.print(value);
  }

  /**
   * Low level print to console.
   * @param value       what is printed
   */
  public static void write(int value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM) {
      int mode = (value < -(1<<20) || value > (1<<20)) ? 2 : 0; // hex only or decimal only
      VM_SysCall.sysWrite(value, mode);
    } else {
      System.err.print(value);
    }
  }

  /**
   * Low level print to console.
   * @param value       what is printed, as hex only
   */
  public static void writeHex(int value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM)
      VM_SysCall.sysWrite(value, 2 /*just hex*/);
    else {
      System.err.print(Integer.toHexString(value));
    }
  }

  /**
   * Low level print to console.
   * @param value       what is printed, as hex only
   */
  public static void writeHex(long value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM){
      VM_SysCall.sysWriteLong(value, 2);
    } else {
      System.err.print(Long.toHexString(value));
    }
  }

  public static void writeHex(VM_Word value) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    //-#if RVM_FOR_64_ADDR
    writeHex(value.toLong()); 
    //-#else
    writeHex(value.toInt()); 
    //-#endif
  }

  public static void writeHex(VM_Address value) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    //-#if RVM_FOR_64_ADDR
    writeHex(value.toLong()); 
    //-#else
    writeHex(value.toInt()); 
    //-#endif
  }

  public static void writeHex(VM_Extent value) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    //-#if RVM_FOR_64_ADDR
    writeHex(value.toLong()); 
    //-#else
    writeHex(value.toInt()); 
    //-#endif
  }

  public static void writeHex(VM_Offset value) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    //-#if RVM_FOR_64_ADDR
    writeHex(value.toLong()); 
    //-#else
    writeHex(value.toInt()); 
    //-#endif
  }

  /**
   * Low level print to console.
   * @param value       what is printed, as int only
   */
  public static void writeInt(int value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM)
      VM_SysCall.sysWrite(value, 0 /*just decimal*/);
    else {
      System.err.print(value);
    }
  }

  /**
   * Low level print to console.
   * @param value   what is printed
   * @param hexToo  how to print: true  - print as decimal followed by hex
   *                              false - print as decimal only
   */
  public static void write(int value, boolean hexToo) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM)
      VM_SysCall.sysWrite(value, hexToo?1:0);
    else
      System.err.print(value);
  }

  /**
   * Low level print to console.
   * @param value   what is printed
   */
  public static void write(long value) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    write(value, true);
  }
  
  /**
   * Low level print to console.
   * @param value   what is printed
   * @param hexToo  how to print: true  - print as decimal followed by hex
   *                              false - print as decimal only
   */
  public static void write(long value, boolean hexToo) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    if (runningVM) 
      VM_SysCall.sysWriteLong(value, hexToo?1:0);
    else
      System.err.print(value);
  }


  public static void writeField(int fieldWidth, String s) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    write(s);
    int len = s.length();
    while (fieldWidth > len++) write(" ");
  }

  /**
   * Low level print to console.
   * @param value       print value and left-fill with enough spaces to print at least fieldWidth characters
   */
  public static void writeField(int fieldWidth, int value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    int len = 1, temp = value;
    if (temp < 0) { len++; temp = -temp; }
    while (temp >= 10) { len++; temp /= 10; }
    while (fieldWidth > len++) write(" ");
    if (runningVM) 
      VM_SysCall.sysWrite(value, 0);
    else 
      System.err.print(value);
  }

  /**
   * Low level print to console.
   * @param value       print value and left-fill with enough spaces to print at least fieldWidth characters
   */
  public static void writeField(int fieldWidth, VM_Atom s) throws VM_PragmaNoInline /* don't waste code space inlining these --dave */ {
    int len = s.length();
    while (fieldWidth > len++) write(" ");
    write(s);
  }

  public static void writeln () {
    write('\n');
  }

  public static void write (double d) {
    write(d, 2);
  }

  public static void write (VM_Word addr) { 
    writeHex(addr);
  }

  public static void write (VM_Address addr) { 
    writeHex(addr);
  }

  public static void write (VM_Offset addr) { 
    writeHex(addr);
  }

  public static void write (VM_Extent addr) { 
    writeHex(addr);
  }

  public static void write (boolean b) {
    write(b ? "true" : "false");
  }

  /**
   * A group of multi-argument sysWrites with optional newline.  Externally visible methods.
   */
  public static void sysWrite(VM_Atom a)               throws VM_PragmaNoInline { swLock(); write(a); swUnlock(); }
  public static void sysWriteln(VM_Atom a)             throws VM_PragmaNoInline { swLock(); write(a); write("\n"); swUnlock(); }
  public static void sysWrite(VM_Member m)             throws VM_PragmaNoInline { swLock(); write(m); swUnlock(); }
  public static void sysWrite(VM_MemberReference mr)   throws VM_PragmaNoInline { swLock(); write(mr); swUnlock(); }
  public static void sysWriteln ()                     throws VM_PragmaNoInline { swLock(); write("\n"); swUnlock(); }
  public static void sysWrite(char c)                  throws VM_PragmaNoInline { write(c); }
  public static void sysWriteField (int w, int v)      throws VM_PragmaNoInline { swLock(); writeField(w, v); swUnlock(); }
  public static void sysWriteField (int w, String s)   throws VM_PragmaNoInline { swLock(); writeField(w, s); swUnlock(); }
  public static void sysWriteHex(int v)                throws VM_PragmaNoInline { swLock(); writeHex(v); swUnlock(); }
  public static void sysWriteHex(VM_Address v)         throws VM_PragmaNoInline { swLock(); writeHex(v); swUnlock(); }
  public static void sysWriteInt(int v)                throws VM_PragmaNoInline { swLock(); writeInt(v); swUnlock(); }
  public static void sysWriteLong(long v)              throws VM_PragmaNoInline { swLock(); write(v,false); swUnlock(); }
  public static void sysWrite   (double d, int p)      throws VM_PragmaNoInline { swLock(); write(d, p); swUnlock(); }
  public static void sysWrite   (double d)             throws VM_PragmaNoInline { swLock(); write(d); swUnlock(); }
  public static void sysWrite   (String s)             throws VM_PragmaNoInline { swLock(); write(s); swUnlock(); }
  public static void sysWrite   (char [] c, int l)     throws VM_PragmaNoInline { swLock(); write(c, l); swUnlock(); }
  public static void sysWrite   (VM_Address a)         throws VM_PragmaNoInline { swLock(); write(a); swUnlock(); }
  public static void sysWriteln (VM_Address a)         throws VM_PragmaNoInline { swLock(); write(a); writeln(); swUnlock(); }
  public static void sysWrite   (VM_Offset o)          throws VM_PragmaNoInline { swLock(); write(o); swUnlock(); }
  public static void sysWriteln (VM_Offset o)          throws VM_PragmaNoInline { swLock(); write(o); writeln(); swUnlock(); }
  public static void sysWrite   (VM_Word w)            throws VM_PragmaNoInline { swLock(); write(w); swUnlock(); }
  public static void sysWriteln (VM_Word w)            throws VM_PragmaNoInline { swLock(); write(w); writeln(); swUnlock(); }
  public static void sysWrite   (VM_Extent e)          throws VM_PragmaNoInline { swLock(); write(e); swUnlock(); }
  public static void sysWriteln (VM_Extent e)          throws VM_PragmaNoInline { swLock(); write(e); writeln(); swUnlock(); }
  public static void sysWrite   (boolean b)            throws VM_PragmaNoInline { swLock(); write(b); swUnlock(); }
  public static void sysWrite   (int i)                throws VM_PragmaNoInline { swLock(); write(i); swUnlock(); }
  public static void sysWriteln (int i)                throws VM_PragmaNoInline { swLock(); write(i);   writeln(); swUnlock(); }
  public static void sysWriteln (double d)             throws VM_PragmaNoInline { swLock(); write(d);   writeln(); swUnlock(); }
  public static void sysWriteln (long l)               throws VM_PragmaNoInline { swLock(); write(l);   writeln(); swUnlock(); }
  public static void sysWriteln (boolean b)            throws VM_PragmaNoInline { swLock(); write(b);   writeln(); swUnlock(); }
  public static void sysWriteln (String s)             throws VM_PragmaNoInline { swLock(); write(s);   writeln(); swUnlock(); }
  public static void sysWrite   (String s, int i)           throws VM_PragmaNoInline { swLock(); write(s);   write(i); swUnlock(); }
  public static void sysWriteln (String s, int i)           throws VM_PragmaNoInline { swLock(); write(s);   write(i); writeln(); swUnlock(); }
  public static void sysWrite   (String s, boolean b)       throws VM_PragmaNoInline { swLock(); write(s);   write(b); swUnlock(); }
  public static void sysWriteln (String s, boolean b)       throws VM_PragmaNoInline { swLock(); write(s);   write(b); writeln(); swUnlock(); }
  public static void sysWrite   (String s, double d)        throws VM_PragmaNoInline { swLock(); write(s);   write(d); swUnlock(); }
  public static void sysWriteln (String s, double d)        throws VM_PragmaNoInline { swLock(); write(s);   write(d); writeln(); swUnlock(); }
  public static void sysWrite   (double d, String s)        throws VM_PragmaNoInline { swLock(); write(d);   write(s); swUnlock(); }
  public static void sysWriteln (double d, String s)        throws VM_PragmaNoInline { swLock(); write(d);   write(s); writeln(); swUnlock(); }
  public static void sysWrite   (String s, long i)           throws VM_PragmaNoInline { swLock(); write(s);   write(i); swUnlock(); }
  public static void sysWriteln (String s, long i)           throws VM_PragmaNoInline { swLock(); write(s);   write(i); writeln(); swUnlock(); }
  public static void sysWrite   (int i, String s)           throws VM_PragmaNoInline { swLock(); write(i);   write(s); swUnlock(); }
  public static void sysWriteln (int i, String s)           throws VM_PragmaNoInline { swLock(); write(i);   write(s); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2)      throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); swUnlock(); }
  public static void sysWriteln (String s1, String s2)      throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s, VM_Address a)    throws VM_PragmaNoInline { swLock(); write(s);   write(a); swUnlock(); }
  public static void sysWriteln (String s, VM_Address a)    throws VM_PragmaNoInline { swLock(); write(s);   write(a); writeln(); swUnlock(); }
  public static void sysWrite   (String s, VM_Offset o)    throws VM_PragmaNoInline { swLock(); write(s);   write(o); swUnlock(); }
  public static void sysWriteln (String s, VM_Offset o)    throws VM_PragmaNoInline { swLock(); write(s);   write(o); writeln(); swUnlock(); }
  public static void sysWrite   (String s, VM_Word w)       throws VM_PragmaNoInline { swLock(); write(s);   write(w); swUnlock(); }
  public static void sysWriteln (String s, VM_Word w)       throws VM_PragmaNoInline { swLock(); write(s);   write(w); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, VM_Address a)  throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(a); swUnlock(); }
  public static void sysWriteln (String s1, String s2, VM_Address a)  throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(a); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, int i)  throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(i); swUnlock(); }
  public static void sysWriteln (String s1, String s2, int i)  throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(i); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, int i, String s2)  throws VM_PragmaNoInline { swLock(); write(s1);  write(i);  write(s2); swUnlock(); }
  public static void sysWriteln (String s1, int i, String s2)  throws VM_PragmaNoInline { swLock(); write(s1);  write(i);  write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, String s3)  throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(s3); swUnlock(); }
  public static void sysWriteln (String s1, String s2, String s3)  throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(s3); writeln(); swUnlock(); }
  public static void sysWrite   (int i1, String s, int i2)     throws VM_PragmaNoInline { swLock(); write(i1);  write(s);  write(i2); swUnlock(); }
  public static void sysWriteln (int i1, String s, int i2)     throws VM_PragmaNoInline { swLock(); write(i1);  write(s);  write(i2); writeln(); swUnlock(); }
  public static void sysWrite   (int i1, String s1, String s2) throws VM_PragmaNoInline { swLock(); write(i1);  write(s1); write(s2); swUnlock(); }
  public static void sysWriteln (int i1, String s1, String s2) throws VM_PragmaNoInline { swLock(); write(i1);  write(s1); write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, int i1, String s2, int i2) throws VM_PragmaNoInline { swLock(); write(s1);  write(i1); write(s2); write(i2); swUnlock(); }
  public static void sysWriteln (String s1, int i1, String s2, int i2) throws VM_PragmaNoInline { swLock(); write(s1);  write(i1); write(s2); write(i2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, int i1, String s2, long l1) throws VM_PragmaNoInline { swLock(); write(s1);  write(i1); write(s2); write(  l1); swUnlock(); }
  public static void sysWriteln (String s1, int i1, String s2, long l1) throws VM_PragmaNoInline { swLock(); write(s1);  write(i1); write(s2); write(l1); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, double d, String s2)        throws VM_PragmaNoInline { swLock(); write(s1);   write(d); write(s2); swUnlock(); }
  public static void sysWriteln (String s1, double d, String s2)        throws VM_PragmaNoInline { swLock(); write(s1);   write(d); write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, int i1, String s3) throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(i1); write(  s3); swUnlock(); }
  public static void sysWriteln (String s1, String s2, int i1, String s3) throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(i1); write(s3); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, String s3, int i1) throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(s3); write(  i1); swUnlock(); }
  public static void sysWriteln (String s1, String s2, String s3, int i1) throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(s3); write(i1); writeln(); swUnlock(); }
  public static void sysWrite   (int i, String s1, double d, String s2) throws VM_PragmaNoInline { swLock(); write(i); write(s1);  write(d); write(s2); swUnlock(); }
  public static void sysWriteln (int i, String s1, double d, String s2) throws VM_PragmaNoInline { swLock(); write(i); write(s1);  write(d); write(s2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, String s2, String s3, int i1, String s4) throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(s3); write(i1); write(  s4); swUnlock(); }
  public static void sysWriteln (String s1, String s2, String s3, int i1, String s4) throws VM_PragmaNoInline { swLock(); write(s1);  write(s2); write(s3); write(i1); write(s4); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, VM_Address a1, String s2, VM_Address a2) throws VM_PragmaNoInline { swLock(); write(s1);  write(a1); write(s2); write(a2); swUnlock(); }
  public static void sysWriteln   (String s1, VM_Address a1, String s2, VM_Address a2) throws VM_PragmaNoInline { swLock(); write(s1);  write(a1); write(s2); write(a2); writeln(); swUnlock(); }
  public static void sysWrite   (String s1, VM_Address a, String s2, int i) throws VM_PragmaNoInline { swLock(); write(s1);  write(a); write(s2); write(i); swUnlock(); }
  public static void sysWriteln   (String s1, VM_Address a, String s2, int i) throws VM_PragmaNoInline { swLock(); write(s1);  write(a); write(s2); write(i); writeln(); swUnlock(); }

  private static void showProc() { 
    VM_Processor p = VM_Processor.getCurrentProcessor();
    write("Proc "); 
    write(p.id);
    write(": ");
  }

  private static void showThread() { 
    write("Thread "); 
    write(VM_Thread.getCurrentThread().getIndex());
    write(": ");
  }

  public static void ptsysWriteln (String s)             throws VM_PragmaNoInline { swLock(); showProc(); showThread(); write(s); writeln(); swUnlock(); }

  public static void psysWrite    (char [] c, int l)     throws VM_PragmaNoInline { swLock(); showProc(); write(c, l); swUnlock(); }

  public static void psysWriteln (VM_Address a)         throws VM_PragmaNoInline { swLock(); showProc(); write(a); writeln(); swUnlock(); }
  public static void psysWriteln (String s)             throws VM_PragmaNoInline { swLock(); showProc(); write(s); writeln(); swUnlock(); }
  public static void psysWriteln (String s, int i)             throws VM_PragmaNoInline { swLock(); showProc(); write(s); write(i); writeln(); swUnlock(); }
  public static void psysWriteln (String s, VM_Address a)      throws VM_PragmaNoInline { swLock(); showProc(); write(s); write(a); writeln(); swUnlock(); }
  public static void psysWriteln   (String s1, VM_Address a1, String s2, VM_Address a2) throws VM_PragmaNoInline { swLock(); showProc(); write(s1);  write(a1); write(s2); write(a2); writeln(); swUnlock(); }
  public static void psysWriteln   (String s1, VM_Address a1, String s2, VM_Address a2, String s3, VM_Address a3) throws VM_PragmaNoInline { swLock(); showProc(); write(s1);  write(a1); write(s2); write(a2); write(s3); write(a3); writeln(); swUnlock(); }
  public static void psysWriteln   (String s1, VM_Address a1, String s2, VM_Address a2, String s3, VM_Address a3, String s4, VM_Address a4) throws VM_PragmaNoInline { swLock(); showProc(); write(s1);  write(a1); write(s2); write(a2); write(s3); write(a3); write (s4); write(a4); writeln(); swUnlock(); }
  public static void psysWriteln   (String s1, VM_Address a1, String s2, VM_Address a2, String s3, VM_Address a3, String s4, VM_Address a4, String s5, VM_Address a5) throws VM_PragmaNoInline { swLock(); showProc(); write(s1);  write(a1); write(s2); write(a2); write(s3); write(a3); write (s4); write(a4); write(s5); write(a5); writeln(); swUnlock(); }
  
  // Exit statuses, pending a better location.
  // We also use the explicit constant -1 as an exit status (that gets mapped
  // to 255).  -1 is for things that are particularly bad.
  public static int exitStatusRecursivelyShuttingDown = 128;
  public static int exitStatusDumpStackAndDie = 124;
  public static int exitStatusMainThreadCouldNotLaunch = 123;
  public static int exitStatusMiscTrouble = 122;
  public static int exitStatusSysFail = exitStatusDumpStackAndDie;

  /* See sys.C; if you change one of the following macros here,
     change them there too! */

  // EXIT_STATUS_SYSCALL_TROUBLE
  final public static int exitStatusSyscallTrouble = 121;
  // EXIT_STATUS_TIMER_TROUBLE
  final public static int exitStatusTimerTrouble = exitStatusSyscallTrouble;

  // EXIT_STATUS_UNEXPECTED_CALL_TO_SYS
  final public static int exitStatusUnexpectedCallToSys = 120;
  // EXIT_STATUS_UNSUPPORTED_INTERNAL_OP
  final public static int exitStatusUnsupportedInternalOp = exitStatusUnexpectedCallToSys;

  public static int exitStatusDyingWithUncaughtException = 113;
  /** Trouble with the Hardware Performance Monitors */
  public static int exitStatusHPMTrouble = 110;
  public static int exitStatusOptCompilerFailed = 101;
  /* See EXIT_STATUS_BOGUS_COMMAND_LINE_ARG in RunBootImage.C.  If you change
   * this value, change it there too. */
  public static int exitStatusBogusCommandLineArg = 100;
  public static int exitStatusTooManyThrowableErrors = 99;
  public static int exitStatusTooManyOutOfMemoryErrors = exitStatusTooManyThrowableErrors;
  /* See EXIT_STATUS_BAD_WORKING_DIR, in VM_0005fProcess.C.  If you change
     this value, change it there too.  */
  public static int exitStatusJNITrouble = 98;
  

  /**
   * Exit virtual machine due to internal failure of some sort.
   * @param message  error message describing the problem
   */
  public static void sysFail(String message) throws VM_PragmaNoInline {
    handlePossibleRecursiveCallToSysFail(message);

    // print a traceback and die
    VM_Scheduler.traceback(message);
    VM.shutdown(exitStatusSysFail);
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

//   /* This could be made public. */
//   private static boolean alreadyShuttingDown() {
//     return (inSysExit != 0) || (inShutdown != 0);
//   }

  public static boolean debugOOM = false; // debug out-of-memory exception. DEBUG
  public static boolean doEmergencyGrowHeap = !debugOOM; // DEBUG
  
  /**
   * Exit virtual machine.
   * @param value  value to pass to host o/s
   */
  public static void sysExit(int value) throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline {
    handlePossibleRecursiveCallToSysExit();
    if (debugOOM) {
      sysWrite("entered VM.sysExit(");
      sysWrite(value);
      sysWriteln(")");
    }
    if (runningVM) {
      VM_Wait.disableIoWait(); // we can't depend on thread switching being enabled
      VM_Callbacks.notifyExit(value);
      VM.shutdown(value);
    } else {
      System.exit(value);
    }
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Shut down the virtual machine.
   * Should only be called if the VM is running.
   * @param value  exit value
   */
  public static void shutdown(int value) {
    handlePossibleRecursiveShutdown();
    
    if (VM.VerifyAssertions) VM._assert(VM.runningVM);
    if (VM.runningAsSubsystem) {
      // Terminate only the system threads that belong to the VM
      VM_Scheduler.processorExit(value);
    } else {
      VM_SysCall.sysExit(value);
    }
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  private static int inSysFail = 0;
  private static void handlePossibleRecursiveCallToSysFail(
                                                           String message) 
  {
    ++inSysFail;
    if (inSysFail > 1 && inSysFail <= maxSystemTroubleRecursionDepth + VM.maxSystemTroubleRecursionDepthBeforeWeStopVMSysWrite) {
      sysWrite("VM.sysFail(): We're in a recursive call to VM.sysFail(), ");
      sysWrite(inSysFail);
      sysWriteln(" deep.");
      sysWrite("sysFail was called with the message: ");
      sysWriteln(message);
     }
    if (inSysFail > maxSystemTroubleRecursionDepth) {
      dieAbruptlyRecursiveSystemTrouble();
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
  }


  private static int inSysExit = 0;
  private static void handlePossibleRecursiveCallToSysExit() {
    ++inSysExit;
    /* Message if we've been here before. */
    if (inSysExit > 1 && inSysExit <= maxSystemTroubleRecursionDepth + VM.maxSystemTroubleRecursionDepthBeforeWeStopVMSysWrite) {
      sysWrite("In a recursive call to VM.sysExit(), ");
      sysWrite(inSysExit);
      sysWriteln(" deep.");
    }
    
    if (inSysExit > maxSystemTroubleRecursionDepth ) {
      dieAbruptlyRecursiveSystemTrouble();
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
  }

  private static int inShutdown = 0;
  /** Used only by VM.shutdown() */
  private static void handlePossibleRecursiveShutdown() {
    ++inShutdown;
    /* If we've been here only a few times, print message. */
    if (   inShutdown > 1 
        && inShutdown <= maxSystemTroubleRecursionDepth + VM.maxSystemTroubleRecursionDepthBeforeWeStopVMSysWrite) {
      sysWriteln("We're in a recursive call to VM.shutdown()");
      sysWrite(inShutdown);
      sysWriteln(" deep!");
    }
    if (inShutdown > maxSystemTroubleRecursionDepth ) {
      dieAbruptlyRecursiveSystemTrouble();
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
  }
  
  /** Have we already called dieAbruptlyRecursiveSystemTrouble()?  
      Only for use if we're recursively shutting down!  Used by
      dieAbruptlyRecursiveSystemTrouble() only.  */

  private static boolean inDieAbruptlyRecursiveSystemTrouble = false;

  public static void dieAbruptlyRecursiveSystemTrouble() {
    if (! inDieAbruptlyRecursiveSystemTrouble) {
      inDieAbruptlyRecursiveSystemTrouble = true;
      VM.sysWrite("VM.dieAbruptlyRecursiveSystemTrouble(): Dying abruptly");
      VM.sysWriteln("; we're stuck in a recursive shutdown/exit.");
    }
    /* Emergency death. */
    VM_SysCall.sysExit(exitStatusRecursivelyShuttingDown);
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }


  /**
   * Yield execution of current virtual processor back to o/s.
   */
  public static void sysVirtualProcessorYield() {
    //-#if !RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    VM_SysCall.sysVirtualProcessorYield();
    //-#endif
  }

  //----------------//
  // implementation //
  //----------------//

  /**
   * Create class instances needed for boot image or initialize classes 
   * needed by tools.
   * @param vmClassPath places where vm implemention class reside
   * @param bootCompilerArgs command line arguments to pass along to the 
   *                         boot compiler's init routine.
   */
  private static void init(String vmClassPath, String[] bootCompilerArgs) 
    throws VM_PragmaInterruptible {
    // create dummy boot record
    //
    VM_BootRecord.the_boot_record = new VM_BootRecord();

    // initialize type subsystem and classloader
    VM_ClassLoader.init(vmClassPath);

    // initialize remaining subsystems needed for compilation
    //
    VM_OutOfLineMachineCode.init();
    if (writingBootImage) // initialize compiler that builds boot image
      VM_BootImageCompiler.init(bootCompilerArgs);
    VM_Runtime.init();
    VM_Scheduler.init();
    MM_Interface.init();
  }

  /**
   * The following two methods are for use as guards to protect code that 
   * must deal with raw object addresses in a collection-safe manner 
   * (ie. code that holds raw pointers across "gc-sites").
   *
   * Authors of code running while gc is disabled must be certain not to 
   * allocate objects explicitly via "new", or implicitly via methods that, 
   * in turn, call "new" (such as string concatenation expressions that are 
   * translated by the java compiler into String() and StringBuffer() 
   * operations). Furthermore, to prevent deadlocks, code running with gc 
   * disabled must not lock any objects. This means the code must not execute 
   * any bytecodes that require runtime support (eg. via VM_Runtime) 
   * such as:
   *   - calling methods or accessing fields of classes that haven't yet 
   *     been loaded/resolved/instantiated
   *   - calling synchronized methods
   *   - entering synchronized blocks
   *   - allocating objects with "new"
   *   - throwing exceptions 
   *   - executing trap instructions (including stack-growing traps)
   *   - storing into object arrays, except when runtime types of lhs & rhs 
   *     match exactly
   *   - typecasting objects, except when runtime types of lhs & rhs 
   *     match exactly
   *
   * Recommendation: as a debugging aid, VM_Allocator implementations 
   * should test "VM_Thread.disallowAllocationsByThisThread" to verify that 
   * they are never called while gc is disabled.
   */
  public static void disableGC() throws VM_PragmaInline, VM_PragmaInterruptible  { 
    // current (non-gc) thread is going to be holding raw addresses, therefore we must:
    //
    // 1. make sure we have enough stack space to run until gc is re-enabled
    //    (otherwise we might trigger a stack reallocation)
    //    (We can't resize the stack if there's a native frame, so don't
    //     do it and hope for the best)
    //
    // 2. force all other threads that need gc to wait until this thread
    //    is done with the raw addresses
    //
    // 3. ensure that this thread doesn't try to allocate any objects
    //    (because an allocation attempt might trigger a collection that
    //    would invalidate the addresses we're holding)
    //

    VM_Thread myThread = VM_Thread.getCurrentThread();

    // 1.
    //
    if (VM_Magic.getFramePointer().sub(STACK_SIZE_GCDISABLED).LT(myThread.stackLimit) && !myThread.hasNativeStackFrame()) {
      VM_Thread.resizeCurrentStack(myThread.stack.length + STACK_SIZE_GCDISABLED, null);
    }

    // 2.
    //
    VM_Processor.getCurrentProcessor().disableThreadSwitching();

    // 3.
    //
    if (VM.VerifyAssertions) {
      VM._assert(myThread.disallowAllocationsByThisThread == false); // recursion not allowed
      myThread.disallowAllocationsByThisThread = true;
    }
  }

  /**
   * enable GC
   */
  public static void enableGC() throws VM_PragmaInline { 
    if (VM.VerifyAssertions) {
      VM_Thread myThread = VM_Thread.getCurrentThread();
      // recursion not allowed
      VM._assert(myThread.disallowAllocationsByThisThread == true); 
      myThread.disallowAllocationsByThisThread = false;
    }
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }

  /**
   * Place to set breakpoints (called by compiled code).
   */
  public static void debugBreakpoint() throws VM_PragmaNoInline, VM_PragmaNoOptCompile {
    // no inline to make sure it doesn't disappear from callee
    // no opt compile to force a full prologue sequence (easier for debugger to grok)
  }
}
