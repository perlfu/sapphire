/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Maintains a collection of compiler specific VM_GCMapIterators that are used 
 * by collection threads when scanning thread stacks to locate object references
 * in those stacks. Each collector thread has its own VM_GCMapIteratorGroup.
 *
 * The group contains a VM_GCMapIterator for each type of stack frame that
 * may be found while scanning a stack during garbage collection, including
 * frames for baseline compiled methods, OPT compiled methods, and frames 
 * for transitions from Java into JNI native code. These iterators are
 * repsonsible for reporting the location of references in the stack or
 * register save areas.
 *
 * @see VM_GCMapIterator
 * @see VM_CompilerInfo
 * @see VM_CollectorThread
 * @see VM_Allocator
 * 
 * @author Janice Shepherd
 * @modified by Stephen Smith
 * @modified by anyone adding a new iterator
 */
final class VM_GCMapIteratorGroup {
  
  /** current location (memory address) of each gpr register */
  private int[]                registerLocations;
  
  /** iterator for VM_BootImageCompiler stackframes */
  private VM_GCMapIterator     bootImageCompilerIterator;
  
  /** iterator for VM_RuntimeCompiler stackframes */
  private VM_GCMapIterator     runtimeCompilerIterator;
  
  /** iterator for VM_HardwareTrap stackframes */
  private VM_GCMapIterator     hardwareTrapIterator;
  
  /** iterator for fallback compiler (baseline) stackframes */
  private VM_GCMapIterator     fallbackCompilerIterator;
  
  /** iterator for test compiler (opt) stackframes */
  private VM_GCMapIterator     testOptCompilerIterator;
  
  /** iterator for JNI Java -> C  stackframes */
  private VM_GCMapIterator     jniIterator;
  
  
  VM_GCMapIteratorGroup() {
    
    registerLocations         = new int[VM_Constants.NUM_GPRS];
    
    bootImageCompilerIterator = VM_BootImageCompiler.createGCMapIterator(registerLocations);
    runtimeCompilerIterator   = VM_RuntimeCompiler.createGCMapIterator(registerLocations);
    hardwareTrapIterator      = new VM_HardwareTrapGCMapIterator(registerLocations);
    fallbackCompilerIterator  = new VM_BaselineGCMapIterator(registerLocations);
    
    // deal with the fact that testing harnesses can install additional compilers.
    // 
    //-#if RVM_WITH_OPT_COMPILER
    testOptCompilerIterator = new VM_OptGCMapIterator(registerLocations);
    //-#endif
    
    jniIterator = new VM_JNIGCMapIterator(registerLocations);
  }
  
  /**
   * Prepare to scan a thread's stack for object references.
   * Called by collector threads when beginning to scan a threads stack.
   * Calls newStackWalk for each of the contained VM_GCMapIterators.
   * <p>
   * Assumption:  the thread is currently suspended, ie. its saved gprs[]
   * contain the thread's full register state.
   * <p>
   * Side effect: registerLocations[] initialized with pointers to the
   * thread's saved gprs[] (in thread.contextRegisters.gprs)
   * <p>
   * @param thread  VM_Thread whose registers and stack are to be scanned
   */
  void
    newStackWalk(VM_Thread thread) {
    
    int registerLocation = VM_Magic.objectAsAddress(thread.contextRegisters.gprs);
    for (int i = 0; i < VM_Constants.NUM_GPRS; ++i)
      {
	registerLocations[i] = registerLocation;
	registerLocation += 4;
      }
    bootImageCompilerIterator.newStackWalk(thread);
    runtimeCompilerIterator.newStackWalk(thread);
    hardwareTrapIterator.newStackWalk(thread);
    fallbackCompilerIterator.newStackWalk(thread);
    if (testOptCompilerIterator != null) testOptCompilerIterator.newStackWalk(thread);
    if (jniIterator != null) jniIterator.newStackWalk(thread);
  }
  
  /**
   * Select iterator for scanning for object references in a stackframe.
   * Called by collector threads while scanning a threads stack.
   *
   * @param compiledMethod  VM_CompiledMethod for the method executing
   *                        in the stack frame
   *
   * @return VM_GCMapIterator to use
   */
  VM_GCMapIterator 
    selectIterator(VM_CompiledMethod compiledMethod) {
    
    int type = compiledMethod.getCompilerInfo().getCompilerType();
    
    if (type == bootImageCompilerIterator.getType())
      return bootImageCompilerIterator;
    
    if (type == runtimeCompilerIterator.getType())
      return runtimeCompilerIterator;
    
    if (type == hardwareTrapIterator.getType())
      return hardwareTrapIterator;
    
    if (type == fallbackCompilerIterator.getType())
      return fallbackCompilerIterator;
    
    if (jniIterator != null && type == jniIterator.getType())
      return jniIterator;
    
    if (testOptCompilerIterator != null && type == testOptCompilerIterator.getType())
      return testOptCompilerIterator;
    
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
    return null;
  }
  
  /**
   * get the VM_GCMapIterator used for scanning JNI native stack frames.
   *
   * @return jniIterator
   */
  VM_GCMapIterator getJniIterator() {
    if (VM.VerifyAssertions) VM.assert(jniIterator!=null);
    return jniIterator;  
  }
  
  // !!TODO: obsolete, can be deleted when callers no longer reference it [--DL]
  // ...I think its gone, if the builds aren't breaking remove this...anyone.
  // void  endStackWalk(VM_Thread thread) { }
}
