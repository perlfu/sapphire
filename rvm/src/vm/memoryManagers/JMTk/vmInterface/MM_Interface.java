/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import java.util.Date;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.ref.PhantomReference;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.memoryManagers.JMTk.AllocAdvice;
import com.ibm.JikesRVM.memoryManagers.JMTk.Barrier;
import com.ibm.JikesRVM.memoryManagers.JMTk.VMResource;
import com.ibm.JikesRVM.memoryManagers.JMTk.Plan;
import com.ibm.JikesRVM.memoryManagers.JMTk.Options;
import com.ibm.JikesRVM.memoryManagers.JMTk.Memory;
import com.ibm.JikesRVM.memoryManagers.JMTk.SynchronizedCounter;
import com.ibm.JikesRVM.memoryManagers.JMTk.Finalizer;
import com.ibm.JikesRVM.memoryManagers.JMTk.ReferenceProcessor;
import com.ibm.JikesRVM.memoryManagers.JMTk.HeapGrowthManager;

import com.ibm.JikesRVM.classloader.VM_Atom;
import com.ibm.JikesRVM.classloader.VM_Type;
import com.ibm.JikesRVM.classloader.VM_Array;
import com.ibm.JikesRVM.classloader.VM_Class;
import com.ibm.JikesRVM.classloader.VM_Method;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_CodeArray;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_DynamicLibrary;
import com.ibm.JikesRVM.VM_JavaHeader;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * The interface that the JMTk memory manager presents to the Jikes
 * research virtual machine.
 * 
 * @author Perry Cheng
 * @version $Revision$
 * @date $Date$
 */  
public class MM_Interface implements Constants, VM_Uninterruptible {

  /***********************************************************************
   *
   * Class variables
   */

  /**
   * <code>true</code> if a write barrier is required.  Specifically,
   * if the memory manger requires that the virtual machine calls
   * putfieldWriteBarrier, arrayStoreWriteBarrier or modifyCheck when
   * the corresponding writes are done.
   * AJG: Is this correct?
   */
  public static final boolean NEEDS_WRITE_BARRIER = Plan.NEEDS_WRITE_BARRIER;

  /**
   * <code>true</code> if a write barrier for putstatic operations is
   * required.  Specifically, if the memory manger requires that the
   * virtual machine calls putstaticWriteBarrier, when a putstatic
   * operation is needed.
   * AJG: Is this correct?
   */
  public static final boolean NEEDS_PUTSTATIC_WRITE_BARRIER
    = Plan.NEEDS_PUTSTATIC_WRITE_BARRIER;

  /* AJG: Not used. But will be. needed */
  /**
   * <code>true</code> if a write barrier for TIB store operations is
   * requried.  Note: This field is not used.
   */
  public static final boolean NEEDS_TIB_STORE_WRITE_BARRIER
    = Plan.NEEDS_TIB_STORE_WRITE_BARRIER;

  /**
   * <code>true</code> if the memory manager moves objects. For
   * example, a copying collector will move objects.
   */
  public static final boolean MOVES_OBJECTS = Plan.MOVES_OBJECTS;

  /**
   * <code>true</code> if the memory manager moves type information
   * blocks (TIBs).
   */
  public static final boolean MOVES_TIBS = Plan.MOVES_TIBS;

  /**
   * <code>true</code> if checking of allocated memory to ensure it is
   * zeroed is desired.
   */
  private static final boolean CHECK_MEMORY_IS_ZEROED = false;

  /* AJG: Not used */
//   public static final boolean RC_CYCLE_DETECTION = Plan.REF_COUNT_CYCLE_DETECTION;

  /***********************************************************************
   *
   * Initialization
   */

  /**
   * Suppress default constructor to enforce noninstantiability.
   */
  private MM_Interface() {} // This constructor will never be invoked.

  /**
   * Initialization that occurs at <i>build</i> time.  The value of
   * statics as at the completion of this routine will be reflected in
   * the boot image.  Any objects referenced by those statics will be
   * transitively included in the boot image.
   *
   * This is the entry point for all build-time activity in the collector.
   */
  public static final void init() throws VM_PragmaInterruptible {
    VM_CollectorThread.init();
    VM_Interface.init();
  }

  /**
   * Initialization that occurs at <i>boot</i> time (runtime
   * initialization).  This is only executed by one processor (the
   * primordial thread).
   * @param theBootRecord the boot record. Contains information about
   * the heap size.
   */
  public static final void boot (VM_BootRecord theBootRecord)
    throws VM_PragmaInterruptible {
    int pageSize = VM_Memory.getPagesize();  // Cannot be determined at init-time
    HeapGrowthManager.boot(theBootRecord.initialHeapSize, theBootRecord.maximumHeapSize);
    Util.boot(theBootRecord);
    Plan.boot();
    VMResource.boot();
    Statistics.boot();
    SynchronizedCounter.boot();
    Monitor.boot();
  }

  /**
   * Sets up the fields of a <code>VM_Processor</code> object to
   * accommodate allocation and garbage collection running on that processor.
   * This may involve creating a remset array or a buffer for GC tracing.
   * 
   * This method is called from the constructor of VM_Processor. For the
   * PRIMORDIAL processor, which is allocated while the bootimage is being
   * built, this method is called a second time, from VM.boot, when the 
   * VM is starting.
   *
   * @param p The <code>VM_Processor</code> object.
   */
  public static final void setupProcessor(VM_Processor proc)
    throws VM_PragmaInterruptible {
    // if (proc.mmPlan == null) proc.mmPlan = new Plan();
    // if (VM.VerifyAssertions) VM._assert(proc.mmPlan != null);
  }

  /**
   * Perform postBoot operations such as dealing with command line
   * options (this is called as soon as options have been parsed,
   * which is necessarily after the basic allocator boot).
   */
  public static void postBoot() throws VM_PragmaInterruptible {
    Plan.postBoot();
  }

  /**
   * Notify the MM that the host VM is now fully booted.
   */
  public static void fullyBootedVM() throws VM_PragmaInterruptible {
    Plan.fullyBooted();
    Lock.fullyBooted();
    Barrier.fullyBooted();
  }

  /** 
   *  Process GC parameters.
   */
  public static void processCommandLineArg(String arg)
    throws VM_PragmaInterruptible {
    Options.process(arg);
  }

  /***********************************************************************
   *
   * Write barriers
   */

  /**
   * Write barrier for putfield operations.
   *
   * @param ref the object which is the subject of the putfield
   * @param offset the offset of the field to be modified
   * @param value the new value for the field
   */
  public static void putfieldWriteBarrier(Object ref, int offset, Object value)
    throws VM_PragmaInline {
    VM_Interface.getPlan().putFieldWriteBarrier(
                                   VM_Magic.objectAsAddress(ref), offset,
				   VM_Magic.objectAsAddress(value));
  }
  
  /**
   * Write barrier for putstatic operations.
   *
   * @param ref the object which is the subject of the putfield
   * @param offset the offset of the field to be modified
   * @param value the new value for the field
   */
  public static void putstaticWriteBarrier(int offset, Object value)
    throws VM_PragmaInline { 
    // putstatic barrier currently unimplemented
    if (VM.VerifyAssertions) VM._assert(false);
    //     VM_Address jtocSlot = VM_Magic.objectAsAddress(VM_Magic.getJTOC()).add(offset);
    //     VM_Interface.getPlan().putStaticWriteBarrier(jtocSlot, VM_Magic.objectAsAddress(value));
  }

  /**
   * Take appropriate write barrier actions for the creation of a new
   * reference by an aastore bytecode.
   *
   * @param ref the array containing the source of the new reference.
   * @param index the index into the array were the new referece
   * resides.  The index is the "natural" index into the array, for
   * example a[index].
   * @param value the object that is the target of the new reference.
   */
  public static void arrayStoreWriteBarrier(Object ref, int index,
					    Object value)
    throws VM_PragmaInline {
    VM_Interface.getPlan().arrayStoreWriteBarrier(
				     VM_Magic.objectAsAddress(ref), index,
				     VM_Magic.objectAsAddress(value));
  }

  /**
   * Checks that if a garbage collection is in progress then the given
   * object is not movable.  If it is movable error messages are
   * logged and the system exits.
   *
   * @param obj the object to check
   */
  public static void modifyCheck(Object obj) {
    /* Make sure we don't GC does not putField on a possible moving
     * object. */
    if (Plan.gcInProgress()) {
	VM_Address ref = VM_Magic.objectAsAddress(obj);
	if (VMResource.refIsMovable(ref)) {
	    VM.sysWriteln("GC modifying a potetentially moving object via Java (i.e. not magic)");
	    VM.sysWriteln("  obj = ", ref);
	    VM_Type t = VM_Magic.getObjectType(obj);
	    VM.sysWrite(" type = "); VM.sysWriteln(t.getDescriptor());
	    VM.sysFail("GC modifying a potetentially moving object via Java (i.e. not magic)");
	}
    }
  }

  /***********************************************************************
   *
   * Statistics
   */
  
  /**
   * Returns the number of collections that have occured.
   *
   * @return The number of collections that have occured.
   */
  public static final int getCollectionCount()
    throws VM_PragmaUninterruptible {
    return VM_CollectorThread.collectionCount;
  }
  

  /***********************************************************************
   *
   * Application interface to memory manager
   */
  
  /**
   * Returns the amount of free memory.
   *
   * @return The amount of free memory.
   */
  public static final long freeMemory() {
    return Plan.freeMemory();
  }

  /**
   * Returns the amount of total memory.
   *
   * @return The amount of total memory.
   */
  public static final long totalMemory() {
    return Plan.totalMemory();
  }

  /**
   * External call to force a garbage collection.
   */
  public static final void gc() throws VM_PragmaInterruptible {
    Statistics.gcExternalCount++;
    if (!Options.ignoreSystemGC)
	VM_Interface.triggerCollection(VM_Interface.EXTERNAL_GC_TRIGGER);
  }

  /****************************************************************************
   *
   * Check references, log information about references
   */

  /**
   * Logs information about a reference to the error output.
   *
   * @param ref the address to log information about
   */
  public static void dumpRef(VM_Address ref) throws VM_PragmaUninterruptible {
    Util.dumpRef(ref);
  }

  /**
   * Checks if a reference is valid.
   *
   * @param ref the address to be checked
   * @return <code>true</code> if the reference is valid
   */
  public static boolean validRef(VM_Address ref)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return Util.validRef(ref);
  }

  /**
   * Checks if an address refers to an in-use area of memory.
   * 
   * @param address the address to be checked
   * @return <code>true</code> if the address refers to an in use area
   */
  public static boolean addrInVM(VM_Address address)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VMResource.addrInVM(address);
  }

  /**
   * Checks if a reference refers to an object in an in-use area of
   * memory.
   *
   * <p>References may be addresses just outside the memory region
   * allocated to the object.
   *
   * @param ref the reference to be checked
   * @return <code>true</code> if the object refered to is in an
   * in-use area
   */
  public static boolean refInVM(VM_Address ref)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VMResource.refInVM(ref);
  }

  /***********************************************************************
   *
   * Allocation
   */

  /**
   * Returns the appropriate allocation scheme/area for the given
   * type.  This form is deprecated.  Without the VM_Method argument,
   * it is possible that the wrong allocator is chosen which may
   * affect correctness. The prototypical example is that JMTk
   * meta-data must generally be in immortal or at least non-moving
   * space.
   *
   *
   * @param type the type of the object to be allocated
   * @return the identifier of the appropriate allocator
   */
  public static int pickAllocator(VM_Type type) throws VM_PragmaInterruptible {
      return pickAllocator(type, null);
  }

 /**
   * Is string <code>a</code> a prefix of string
   * <code>b</code>. String <code>b</code> is encoded as an ASCII byte
   * array.
   *
   * @param a prefix string
   * @param b string which may contain prefix, encoded as an ASCII
   * byte array.
   * @return <code>true</code> if <code>a</code> is a prefix of
   * <code>b</code>
   */
  private static boolean isPrefix(String a, byte [] b)
    throws VM_PragmaInterruptible {
    int aLen = a.length();
    if (aLen > b.length)
      return false;
    for (int i = 0; i<aLen; i++) {
      if (a.charAt(i) != ((char) b[i]))
	return false;
    }
    return true;
  }

  /**
   * Returns the appropriate allocation scheme/area for the given type
   * and given method requesting the allocation.
   * 
   * @param type the type of the object to be allocated
   * @param method the method requesting the allocation
   * @return the identifier of the appropriate allocator
   */
  public static int pickAllocator(VM_Type type, VM_Method method)
    throws VM_PragmaInterruptible {
    /*
     * First we chcek the calling method so GC-data can go into
     * special places.  Then we cache the result of the allocator
     * assuming that it is type-based only.  A better implementation
     * would be call-site specific which is strictly more refined.
     */
    if (method != null) {
     // We should strive to be allocation-free here.
      VM_Class cls = method.getDeclaringClass();
      byte[] clsBA = cls.getDescriptor().toByteArray();
      if (isPrefix("Lcom/ibm/JikesRVM/memoryManagers/JMTk/", clsBA)) {
	return Plan.IMMORTAL_SPACE;
      }
    }
    Type t = type.JMTKtype;
    if (t.initialized)
      return t.allocator;
    int allocator = Plan.DEFAULT_SPACE;
    byte[] typeBA = type.getDescriptor().toByteArray();
    if (isPrefix("Lcom/ibm/JikesRVM/memoryManagers/", typeBA) ||
	isPrefix("Lcom/ibm/JikesRVM/VM_Processor;", typeBA))
      allocator = Plan.IMMORTAL_SPACE;
    t.initialized = true;
    t.allocator = allocator;
    return allocator;
  }

  /*
   * These methods allocate memory.  Specialized versions are available for
   * particular object types.
   */

  /**
   * Allocate a scalar object.
   *
   * @param size Size in bytes of the object, including any headers
   * that need space.
   * @param tib  Type of the object (pointer to TIB).
   * @param allocator Specify which allocation scheme/area JMTk should
   * allocate the memory from.
   * @return the initialized Object
   */
  public static Object allocateScalar(int size, Object [] tib, int allocator) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    Plan plan = VM_Interface.getPlan();
    AllocAdvice advice = plan.getAllocAdvice(null, size, null, null);
    VM_Address region = plan.alloc(size, true, allocator, advice);
    if (CHECK_MEMORY_IS_ZEROED) Memory.assertIsZeroed(region, size);
    Object result = VM_ObjectModel.initializeScalar(region, tib, size);
    plan.postAlloc(VM_Magic.objectAsAddress(result), tib, size, true,
		   allocator);
    return result;
  }

  /**
   * Allocate an array object.
   * 
   * @param numElements number of array elements
   * @param logElementSize size in bytes of an array element, log base 2.
   * @param headerSize size in bytes of array header
   * @param tib type information block for array object
   * @param allocator int that encodes which allocator should be used
   * @return array object with header installed and all elements set 
   *         to zero/null
   * See also: bytecode 0xbc ("newarray") and 0xbd ("anewarray")
   */ 
  public static Object allocateArray(int numElements, int logElementSize, 
				     int headerSize, Object [] tib,
				     int allocator) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int elemBytes = numElements << logElementSize;
    if ((elemBytes >>> logElementSize) != numElements) {
      // asked to allocate more than Integer.MAX_VALUE bytes
      failWithOutOfMemoryError();
    }
    int size = VM_Memory.alignUp(elemBytes + headerSize, BYTES_IN_ADDRESS);
    Plan plan = VM_Interface.getPlan();
    AllocAdvice advice = plan.getAllocAdvice(null, size, null, null);
    VM_Address region = plan.alloc(size, false, allocator, advice);
    if (CHECK_MEMORY_IS_ZEROED) Memory.assertIsZeroed(region, size);
    Object result = VM_ObjectModel.initializeArray(region, tib, numElements,
						   size);
    plan.postAlloc(VM_Magic.objectAsAddress(result), tib, size, false,
		   allocator);
    return result;
  }

  /**
   * Throw an out of memory exception.
   */
  public static void failWithOutOfMemoryError()
    throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline {
    throw new OutOfMemoryError();
  }

  /**
   * Allocate an array object, using the given array as an example of
   * the required type.
   *
   * @param array an array of the type to be allocated
   * @param allocator which allocation scheme/area JMTk should
   * allocation the memory from.
   * @param length the number of elements in the array to be allocated
   * @return the initialzed array object
   */
  public static Object cloneArray(Object [] array, int allocator, int length)
      throws VM_PragmaUninterruptible {
    VM_Array type = VM_Magic.getObjectType(array).asArray();
    Object [] tib = type.getTypeInformationBlock();
    return allocateArray(length, type.getLogElementSize(), 
			 VM_ObjectModel.computeArrayHeaderSize(type),
			 tib, allocator);
  }


  /***********************************************************************
   *
   * Specialized allocation routines
   */

  /**
   * Allocate a VM_CodeArray
   * NOTE: We don't use this at all for Jikes RVM right now.
   *       It might be useful to think about expanding the interface
   *       to take hints on the likely hot/coldness of the code and
   *       other code placement hints.
   * @param n The number of instructions to allocate
   * @return The  array
   */
  public static VM_CodeArray newInstructions(int n)
    throws VM_PragmaInline, VM_PragmaInterruptible {
    return VM_CodeArray.create(n);
  }

  /**
   * Allocate a stack array
   * @param n The number of stack slots to allocate
   * @return The stack array
   */ 
  public static int[] newStack(int n)
    throws VM_PragmaInline, VM_PragmaInterruptible {
    return new int[n];
  }

  /**
   * Allocate an aligned stack array that will live forever and does not move
   * @param n The number of stack slots to allocate
   * @return The stack array
   */ 
  public static int[] newImmortalStack(int n) throws VM_PragmaInterruptible {

    if (VM.runningVM) {
      int logAlignment = 12;
      int alignment = 1 << logAlignment; // 4096
      VM_Array stackType = VM_Array.IntArray;
      Object [] stackTib = stackType.getTypeInformationBlock();
      int offset = VM_JavaHeader.computeArrayHeaderSize(stackType);
      int arraySize = stackType.getInstanceSize(n);
      int fullSize = arraySize + alignment;  // somewhat wasteful
      if (VM.VerifyAssertions) VM._assert(alignment > offset);
      AllocAdvice advice = VM_Interface.getPlan().getAllocAdvice(null, fullSize, null, null);
      VM_Address fullRegion = VM_Interface.getPlan().alloc(fullSize, false, Plan.IMMORTAL_SPACE, advice);
      VM_Address tmp = fullRegion.add(alignment);
      VM_Word mask = VM_Word.one().lsh(logAlignment).sub(VM_Word.one()).not();
      VM_Address region = tmp.toWord().and(mask).sub(VM_Word.fromIntSignExtend(offset)).toAddress();
      Object result = VM_ObjectModel.initializeArray(region, stackTib, n, arraySize);
      VM_Interface.getPlan().postAlloc(VM_Magic.objectAsAddress(result), stackTib, arraySize, false, Plan.IMMORTAL_SPACE);
      return (int []) result;
    }

    return new int[n];
  }

  /**
   * Allocate a contiguous VM_CompiledMethod array
   * @param n The number of objects
   * @return The contiguous object array
   */ 
  public static VM_CompiledMethod[] newContiguousCompiledMethodArray(int n)
    throws VM_PragmaInline, VM_PragmaInterruptible {
    return new VM_CompiledMethod[n];
  }

  /**
   * Allocate a contiguous VM_DynamicLibrary array
   * @param n The number of objects
   * @return The contiguous object array
   */ 
  public static VM_DynamicLibrary[] newContiguousDynamicLibraryArray(int n)
    throws VM_PragmaInline, VM_PragmaInterruptible {
    return new VM_DynamicLibrary[n];
  }

  /**
   * Allocate a new type information block (TIB).
   *
   * @param n the number of slots in the TIB to be allocated
   * @return the new TIB
   */
  public static Object[] newTIB (int n)
    throws VM_PragmaInline, VM_PragmaInterruptible {
    if (VM.runningVM) {
      VM_Array objectArrayType = VM_Type.JavaLangObjectArrayType;
      Object [] objectArrayTib = objectArrayType.getTypeInformationBlock();
      Object result = allocateArray(n, objectArrayType.getLogElementSize(),
				    VM_ObjectModel.computeArrayHeaderSize(objectArrayType),
				    objectArrayTib, Plan.IMMORTAL_SPACE);
      return (Object []) result;
    } else
      return new Object[n];
  }


  /***********************************************************************
   *
   * Finalizers
   */
  
  /**
   * Adds an object to the list of objects to have their
   * <code>finalize</code> method called when they are reclaimed.
   *
   * @param obj the object to be added to the finalizer's list
   */
  public static void addFinalizer(Object obj) throws VM_PragmaInterruptible {
    Finalizer.addCandidate(obj);
  }

  /**
   * Gets an object from the list of objects that are to be reclaimed
   * and need to have their <code>finalize</code> method called.
   *
   * @return the object needing to be finialized
   */
  public static Object getFinalizedObject() throws VM_PragmaInterruptible {
    return Finalizer.get();
  }


  /***********************************************************************
   *
   * References
   */

  /**
   * Add a soft reference to the list of soft references.
   *
   * @param obj the soft reference to be added to the list
   */
   public static void addSoftReference(SoftReference obj)
     throws VM_PragmaInterruptible {
     ReferenceProcessor.addSoftCandidate(obj);
   }
 
  /**
   * Add a weak reference to the list of weak references.
   *
   * @param obj the weak reference to be added to the list
   */
   public static void addWeakReference(WeakReference obj)
     throws VM_PragmaInterruptible {
     ReferenceProcessor.addWeakCandidate(obj);
   }
 
  /**
   * Add a phantom reference to the list of phantom references.
   *
   * @param obj the phantom reference to be added to the list
   */
   public static void addPhantomReference(PhantomReference obj)
     throws VM_PragmaInterruptible {
     ReferenceProcessor.addPhantomCandidate(obj);
   }


  /***********************************************************************
   *
   * Tracing
   */

  /**
   * Processes an object during the tracing phase of a collection.
   *
   * @param obj the address of the object to be processed
   */
  public static VM_Address processPtrValue(VM_Address obj)
    throws VM_PragmaUninterruptible, VM_PragmaInline { 
    return Plan.traceObject(obj);
  }

  /**
   * Processes an object during the tracing phase of a collection.
   *
   * @param location the address of a reference to the object to be
   * processed
   * @param root <code>true</code> if the location is within a root
   */
  public static void processPtrLocation(VM_Address location, boolean root)
    throws VM_PragmaUninterruptible, VM_PragmaInline { 
    Plan.traceObjectLocation(location, root);
  }

  /***********************************************************************
   *
   * Heap size and heap growth
   */

  /**
   * Return the max heap size in bytes (as set by -Xmx).
   *
   * @return The max heap size in bytes (as set by -Xmx).
   */
  public static int getMaxHeapSize() {
    return HeapGrowthManager.getMaxHeapSize();
  }

  /**
   * Return the initial heap size in bytes (as set by -Xms).
   *
   * @return The initial heap size in bytes (as set by -Xms).
   */
  public static int getInitialHeapSize() {
    return HeapGrowthManager.getInitialHeapSize();
  }

  /**
   * Increase heap size for an emergency, such as processing an out of
   * memory exception.
   * 
   * @param growSize number of bytes to increase the heap size
   */
  public static void emergencyGrowHeap(int growSize) { // in bytes
    // This can be undoable if the current thread doesn't cause 'em
    // all to exit.
    // if (VM.VerifyAssertions && growSize < 0) VM._assert(false);
    HeapGrowthManager.overrideGrowHeapSize(growSize);
  }

  
 /***********************************************************************
  *
  * Miscellaneous
  */

  /**
   * Check if object might be a TIB.
   *
   * @param obj address of object to check
   * @return <code>false</code> if the object is in the wrong
   * allocation scheme/area for a TIB, <code>true</code> otherwise
   */
  public static boolean mightBeTIB (VM_Address obj)
    throws VM_PragmaInline, VM_PragmaUninterruptible {
    return VMResource.refIsImmortal(obj);
  }

  /**
   * Returns true if GC is in progress.
   *
   * @return True if GC is in progress.
   */
  public static final boolean gcInProgress() throws VM_PragmaUninterruptible {
    return Plan.gcInProgress();
  }


 /***********************************************************************
  *
  * Deprecated and/or broken.  The following need to be expunged.
  */

  /**
   * Returns the maximum number of heaps that can be managed.
   *
   * @return the maximum number of heaps
   */
  public static int getMaxHeaps() {
    /*
     *  The boot record has a table of address ranges of the heaps,
     *  the maximum number of heaps is used to size the table.
     */
    return VMResource.getMaxVMResource();
  }

   /**
    * Allocate an immortal short array that will live forever and does not move
    * @param n The number of elements
    * @return The short array
    */ 
   public static short[] newImmortalShortArray (int n)
     throws VM_PragmaInterruptible {

     if (VM.runningVM) {
       VM_Array shortArrayType = VM_Array.ShortArray;
       Object [] shortArrayTib = shortArrayType.getTypeInformationBlock();
       Object result = allocateArray(n, shortArrayType.getLogElementSize(),
				     VM_ObjectModel.computeArrayHeaderSize(shortArrayType),
				     shortArrayTib, Plan.IMMORTAL_SPACE);
       return (short []) result;
     }
     return new short[n];
   }

  /**
   * Allocate a contiguous int array
   * @param n The number of ints
   * @return The contiguous int array
   */ 
  public static int[] newContiguousIntArray(int n)
    throws VM_PragmaInline, VM_PragmaInterruptible {
    return new int[n];
  }

}

