/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.*;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Processor;

/**
 * This class implements a simple reference counting collector.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class Plan extends StopTheWorldGC implements VM_Uninterruptible { // implements Constants 
  final public static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  public static final boolean needsWriteBarrier = true;
  public static final boolean needsRefCountWriteBarrier = true;
  public static final boolean refCountCycleDetection = true;
  public static final boolean movesObjects = false;
  public static final boolean sanityTracing = false;

  // virtual memory resources
  private static FreeListVMResource rcVM;

  // RC collection space
  private static SimpleRCCollector rcSpace;

  // memory resources
  private static MemoryResource rcMR;

  // shared queues
  private static SharedQueue incPool;
  private static SharedQueue decPool;
  private static SharedQueue rootPool;
  private static SharedQueue cyclePoolA;
  private static SharedQueue cyclePoolB;
  private static SharedQueue freePool;
  private static SharedQueue tracingPool;

  // GC state
  private static boolean progress = true;  // are we making progress?
  private static int required;  // how many pages must this GC yeild?
  private static boolean cycleBufferAisOpen = true;
  private static int lastRCPages = 0; // pages at end of last GC
  
  // Allocators
  public static final byte RC_SPACE = 0;
  public static final byte DEFAULT_SPACE = RC_SPACE;

  // Miscellaneous constants
  private static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;

  // Memory layout constants
  public  static final long            AVAILABLE = VM_Interface.MAXIMUM_MAPPABLE.diff(PLAN_START).toLong();
  private static final EXTENT            RC_SIZE = (int) AVAILABLE;
  public  static final int              MAX_SIZE = RC_SIZE;

  private static final VM_Address       RC_START = PLAN_START;
  private static final VM_Address         RC_END = RC_START.add(RC_SIZE);
  private static final VM_Address       HEAP_END = RC_END;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //

  // allocator
  private SimpleRCAllocator rc;

  // counters
  private int incCounter;
  private int decCounter;
  private int rootCounter;
  private int purpleCounter;
  private int wbFastPathCounter;

  // queues (buffers)
  private AddressQueue incBuffer;
  private AddressQueue decBuffer;
  private AddressQueue rootSet;
  private AddressQueue cycleBufferA;
  private AddressQueue cycleBufferB;
  private AddressQueue freeBuffer;
  private AddressQueue tracingBuffer;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {
    // memory resources
    rcMR = new MemoryResource("rc", POLL_FREQUENCY);

    // virtual memory resources
    rcVM = new FreeListVMResource(RC_SPACE, "RC", RC_START, RC_SIZE, VMResource.IN_VM);

    // collectors
    rcSpace = new SimpleRCCollector(rcVM, rcMR);
    addSpace(RC_SPACE, "RC Space");

    // instantiate shared queues
    incPool = new SharedQueue(metaDataRPA, 1);
    incPool.newClient();
    decPool = new SharedQueue(metaDataRPA, 1);
    decPool.newClient();
    rootPool = new SharedQueue(metaDataRPA, 1);
    rootPool.newClient();
    if (refCountCycleDetection) {
      cyclePoolA = new SharedQueue(metaDataRPA, 1);
      cyclePoolA.newClient();
      cyclePoolB = new SharedQueue(metaDataRPA, 1);
      cyclePoolB.newClient();
      freePool = new SharedQueue(metaDataRPA, 1);
      freePool.newClient();
    }
    if (sanityTracing) {
      tracingPool = new SharedQueue(metaDataRPA, 1);
      tracingPool.newClient();
    }
  }

  /**
   * Constructor
   */
  public Plan() {
    rc = new SimpleRCAllocator(rcSpace);
    incBuffer = new AddressQueue("inc buf", incPool);
    decBuffer = new AddressQueue("dec buf", decPool);
    rootSet = new AddressQueue("root set", rootPool);
    if (refCountCycleDetection) {
      cycleBufferA = new AddressQueue("cycle buf A", cyclePoolA);
      cycleBufferB = new AddressQueue("cycle buf B", cyclePoolB);
      freeBuffer = new AddressQueue("free buffer", freePool);
    }
    if (sanityTracing) {
      tracingBuffer = new AddressQueue("tracing buffer", tracingPool);
    }
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public static final void boot()
    throws VM_PragmaInterruptible {
    StopTheWorldGC.boot();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //

  /**
   * Allocate space (for an object)
   *
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @param allocator The allocator number to be used for this allocation
   * @param advice Statically-generated allocation advice for this allocation
   * @return The address of the first byte of the allocated region
   */
  public final VM_Address alloc (EXTENT bytes, boolean isScalar, int allocator,
				AllocAdvice advice)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(bytes == (bytes & (~(WORD_SIZE-1))));
    VM_Address result;
    switch (allocator) {
      case       RC_SPACE: result = rc.allocOOL(isScalar, bytes); break;
      case IMMORTAL_SPACE: result = immortal.alloc(isScalar, bytes); break;
      default:             result = VM_Address.zero(); 
	                   if (VM.VerifyAssertions) VM.sysFail("No such allocator");
    }
    return result;
  }

  /**
   * Perform post-allocation actions.  For many allocators none are
   * required.
   *
   * @param ref The newly allocated object
   * @param tib The TIB of the newly allocated object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @param allocator The allocator number to be used for this allocation
   */
  public final void postAlloc(Object ref, Object[] tib, EXTENT bytes,
			      boolean isScalar, int allocator)
    throws VM_PragmaInline {
    switch (allocator) {
    case RC_SPACE: decBuffer.pushOOL(VM_Magic.objectAsAddress(ref)); return;
    case IMMORTAL_SPACE: 
      if (sanityTracing)
	SimpleRCCollector.postAllocImmortal(VM_Magic.objectAsAddress(ref));
      Immortal.postAlloc(ref); return;
    default: if (VM.VerifyAssertions) VM.sysFail("No such allocator"); return;
    }
  }

  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   *
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @return The address of the first byte of the allocated region
   */
  public final VM_Address allocCopy(VM_Address original, EXTENT bytes,
				    boolean isScalar) throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(false);
    // return VM_Address.zero();  this trips some Intel assembler bug
    return VM_Address.max();
  }

  /**  
   * Perform any post-copy actions.  In this case nothing is required.
   *
   * @param ref The newly allocated object
   * @param tib The TIB of the newly allocated object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   */
  public final void postCopy(Object ref, Object[] tib, EXTENT bytes,
			     boolean isScalar) {} // do nothing

  /**
   * Advise the compiler/runtime which allocator to use for a
   * particular allocation.  This should be called at compile time and
   * the returned value then used for the given site at runtime.
   *
   * @param type The type id of the type being allocated
   * @param bytes The size (in bytes) required for this object
   * @param callsite Information identifying the point in the code
   * where this allocation is taking place.
   * @param hint A hint from the compiler as to which allocator this
   * site should use.
   * @return The allocator number to be used for this allocation.
   */
  public final int getAllocator(Type type, EXTENT bytes, CallSite callsite,
				AllocAdvice hint) {
    return RC_SPACE;
  }

  protected final byte getSpaceFromAllocator (Allocator a) {
    if (a == rc) return DEFAULT_SPACE;
    return super.getSpaceFromAllocator(a);
  }

  protected final Allocator getAllocatorFromSpace (byte s) {
    if (s == DEFAULT_SPACE) return rc;
    return super.getAllocatorFromSpace(s);
  }

  /**
   * Give the compiler/runtime statically generated alloction advice
   * which will be passed to the allocation routine at runtime.
   *
   * @param type The type id of the type being allocated
   * @param bytes The size (in bytes) required for this object
   * @param callsite Information identifying the point in the code
   * where this allocation is taking place.
   * @param hint A hint from the compiler as to which allocator this
   * site should use.
   * @return Allocation advice to be passed to the allocation routine
   * at runtime
   */
  public final AllocAdvice getAllocAdvice(Type type, EXTENT bytes,
					  CallSite callsite,
					  AllocAdvice hint) {
    return null;
  }

  /**
   * Return the initial header value for a newly allocated LOS
   * instance.
   *
   * @param bytes The size of the newly created instance in bytes.
   * @return The inital header value for the new instance.
   */
  public static final int getInitialHeaderValue(EXTENT bytes)
    throws VM_PragmaInline {
    return rcSpace.getInitialHeaderValue(bytes);
  }

  /**
   * This method is called periodically by the allocation subsystem
   * (by default, each time a page is consumed), and provides the
   * collector with an opportunity to collect.<p>
   *
   * We trigger a collection whenever an allocation request is made
   * that would take the number of pages in use (committed for use)
   * beyond the number of pages available.  Collections are triggered
   * through the runtime, and ultimately call the
   * <code>collect()</code> method of this class or its superclass.<p>
   *
   * This method is clearly interruptible since it can lead to a GC.
   * However, the caller is typically uninterruptible and this fiat allows 
   * the interruptibility check to work.  The caveat is that the caller 
   * of this method must code as though the method is interruptible. 
   * In practice, this means that, after this call, processor-specific
   * values must be reloaded.
   *
   * @param mustCollect True if a this collection is forced.
   * @param mr The memory resource that triggered this collection.
   * @return True if a collection is triggered
   */
  public final boolean poll(boolean mustCollect, MemoryResource mr) 
    throws VM_PragmaLogicallyUninterruptible {
    if (gcInProgress) return false;
    if (mustCollect || 
	getPagesReserved() > getTotalPages() ||
	(((rcMR.committedPages() - lastRCPages) > Options.nurseryPages ||
	  metaDataMR.committedPages() > Options.metaDataPages)
	 && VM_Interface.fullyBooted())) {
      //      VM.sysWrite(getPagesReserved()); VM.sysWrite(" res, "); VM.sysWrite(lastRCPages); VM.sysWrite(" last, "); VM.sysWrite(Options.nurseryPages); VM.sysWrite(" nur, "); VM.sysWrite(metaDataMR.committedPages()); VM.sysWrite(" md, "); VM.sysWrite(Options.metaDataPages); VM.sysWrite(" omd\n");
      if (VM.VerifyAssertions) VM._assert(mr != metaDataMR);
      required = mr.reservedPages() - mr.committedPages();
      VM_Interface.triggerCollection(VM_Interface.RESOURCE_TRIGGERED_GC);
      return true;
    }
    return false;
  }

  
  ////////////////////////////////////////////////////////////////////////////
  //
  // Collection
  //
  // Important notes:
  //   . Global actions are executed by only one thread
  //   . Thread-local actions are executed by all threads
  //   . The following order is guaranteed by BasePlan, with each
  //     separated by a synchronization barrier.:
  //      1. globalPrepare()
  //      2. threadLocalPrepare()
  //      3. threadLocalRelease()
  //      4. globalRelease()
  //

  /**
   * Perform operations with <i>global</i> scope in preparation for a
   * collection.  This is called by <code>StopTheWorld</code>, which will
   * ensure that <i>only one thread</i> executes this.<p>
   *
   * In this case, it means flipping semi-spaces, resetting the
   * semi-space memory resource, and preparing each of the collectors.
   */
  protected final void globalPrepare() {
    Immortal.prepare(immortalVM, null);
    rcSpace.prepare();
  }

  /**
   * Perform operations with <i>thread-local</i> scope in preparation
   * for a collection.  This is called by <code>StopTheWorld</code>, which
   * will ensure that <i>all threads</i> execute this.<p>
   *
   * In this case, it means resetting the semi-space and large object
   * space allocators.
   */
  protected final void threadLocalPrepare(int count) {
    rc.prepare();
    // decrements from previous collection
    if (verbose == 2) processRootBufsAndCount(); else processRootBufs(); 
  }

  /**
   * We reset the state for a GC thread that is not participating in
   * this GC
   */
  public final void prepareNonParticipating() {
    threadLocalPrepare(NON_PARTICIPANT);
  }

  /**
   * Perform operations with <i>thread-local</i> scope to clean up at
   * the end of a collection.  This is called by
   * <code>StopTheWorld</code>, which will ensure that <i>all threads</i>
   * execute this.<p>
   *
   * In this case, it means releasing the large object space (which
   * triggers the sweep phase of the mark-sweep collector used by the
   * LOS).
   */
  protected final void threadLocalRelease(int count) {
    if (sanityTracing) VM.sysWrite("--------- Increment --------\n");
    if (verbose == 2) processIncBufsAndCount(); else processIncBufs();
    if (sanityTracing) VM.sysWrite("--------- Decrement --------\n");
    rcSpace.decrementPhase();
    VM_CollectorThread.gcBarrier.rendezvous();
    if (verbose == 2) processDecBufsAndCount(); else processDecBufs();
    if (refCountCycleDetection) {
      filterCycleBufs();
      processFreeBufs(false);
//       if ((getTotalPages() - getPagesReserved() - required)
// 	  < Options.cycleDetectionPages) {
	if (sanityTracing) VM.sysWrite("----------Mark Grey---------\n");
	doMarkGreyPhase();
	if (sanityTracing) VM.sysWrite("----------- Scan -----------\n");
	doScanPhase();
	if (sanityTracing) VM.sysWrite("---------- Collect ---------\n");
	doCollectPhase();
	if (sanityTracing) VM.sysWrite("------------ Free ----------\n");
	processFreeBufs(true);
//       }
    }
    if (GATHER_WRITE_BARRIER_STATS) { 
      // This is printed independantly of the verbosity so that any
      // time someone sets the GATHER_WRITE_BARRIER_STATS flags they
      // will know---it will have a noticable performance hit...
      VM.sysWrite("<GC ", Statistics.gcCount); VM.sysWrite(" "); 
      VM.sysWriteInt(wbFastPathCounter); VM.sysWrite(" wb-fast>\n");
      wbFastPathCounter = 0;
    }
    if (sanityTracing) rcSanityCheck();
  }

  /**
   * Perform operations with <i>global</i> scope to clean up at the
   * end of a collection.  This is called by <code>StopTheWorld</code>,
   * which will ensure that <i>only one</i> thread executes this.<p>
   *
   * In this case, it means releasing each of the spaces and checking
   * whether the GC made progress.
   */
  protected final void globalRelease() {
    // release each of the collected regions
    rcSpace.release();
    Immortal.release(immortalVM, null);
    if (verbose == 2) {
      VM.sysWrite("<GC ", Statistics.gcCount); VM.sysWrite(" "); 
      VM.sysWriteInt(incCounter); VM.sysWrite(" incs, ");
      VM.sysWriteInt(decCounter); VM.sysWrite(" decs, ");
      VM.sysWriteInt(rootCounter); VM.sysWrite(" roots");
      if (refCountCycleDetection) {
	VM.sysWrite(", "); 
	VM.sysWriteInt(purpleCounter); VM.sysWrite(" purple");
      }
      VM.sysWrite(">\n");
    }
    lastRCPages = rcMR.committedPages();
    if (getPagesReserved() + required >= getTotalPages()) {
      if (!progress)
	VM.sysFail("Out of memory");
      progress = false;
    } else
      progress = true;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Object processing and tracing
  //

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  public static final VM_Address traceObject (VM_Address obj) 
    throws VM_PragmaInline {
    return traceObject(obj, false);
  }
  
  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i>
   * an interior pointer.
   * @param root True if this reference to <code>obj</code> was held
   * in a root.
   * @return The possibly moved reference.
   */
  public static final VM_Address traceObject(VM_Address obj, boolean root) {
    if (obj.isZero()) return obj;
    VM_Address addr = VM_Interface.refToAddress(obj);
    if (addr.LE(HEAP_END) && addr.GE(RC_START))
      return rcSpace.traceObject(obj, root);
    else if (sanityTracing && addr.LE(HEAP_END) && addr.GE(BOOT_START))
      return rcSpace.traceBootObject(obj);
    
    // else this is not a rc heap pointer
    return obj;
  }
  public static void rootScan(VM_Address obj) {
    if (sanityTracing) {
      // this object has been explicitly scanned as part of the root scanning
      // process.  Mark it now so that it does not get re-scanned.
      if (obj.LE(RC_START) && obj.GE(BOOT_START)) {
	if (SimpleRCCollector.bootMark)
	  SimpleRCBaseHeader.setBufferedBit(obj);
	else
	  SimpleRCBaseHeader.clearBufferedBit(obj);
      }
    }
  }


  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param obj The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public static final boolean isLive(VM_Address obj) {
    VM_Address addr = VM_ObjectModel.getPointerInMemoryRegion(obj);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(RC_START))
 	return rcSpace.isLive(obj);
      else if (addr.GE(BOOT_START))
 	return true;
    } 
    return false;
  }

  /**
   * Reset the GC bits in the header word of an object that has just
   * been copied.  This may, for example, involve clearing a write
   * barrier bit.  In this case nothing is required, so the header
   * word is returned unmodified.
   *
   * @param fromObj The original (uncopied) object
   * @param forwardingPtr The forwarding pointer, which is the GC word
   * of the original object, and typically encodes some GC state as
   * well as pointing to the copied object.
   * @param bytes The size of the copied object in bytes.
   * @return The updated GC word (in this case unchanged).
   */
  public static final int resetGCBitsForCopy(VM_Address fromObj,
					     int forwardingPtr, int bytes) {
    if (VM.VerifyAssertions) VM._assert(false);  // not a copying collector!
    return forwardingPtr;
  }

  public static boolean willNotMove (VM_Address obj) {
    return true;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Write barriers. 
  //

  /**
   * A new reference is about to be created by a putfield bytecode.
   * Take appropriate write barrier actions.
   *
   * @param src The address of the object containing the source of a
   * new reference.
   * @param offset The offset into the source object where the new
   * reference resides (the offset is in bytes and with respect to the
   * object address).
   * @param tgt The target of the new reference
   */
  public final void putFieldWriteBarrier(VM_Address src, int offset,
					 VM_Address tgt)
    throws VM_PragmaInline {
    writeBarrier(src.add(offset), tgt);
  }

  /**
   * A new reference is about to be created by a aastore bytecode.
   * Take appropriate write barrier actions.
   *
   * @param src The address of the array containing the source of a
   * new reference.
   * @param index The index into the array where the new reference
   * resides (the index is the "natural" index into the array,
   * i.e. a[index]).
   * @param tgt The target of the new reference
   */
  public final void arrayStoreWriteBarrier(VM_Address src, int index,
					   VM_Address tgt)
    throws VM_PragmaInline {
    writeBarrier(src.add(index<<LOG_WORD_SIZE), tgt);
  }

  /**
   * A new reference is about to be created.  Perform appropriate
   * write barrier action.<p>
   *
   * In this case, we remember the address of the source of the
   * pointer if the new reference points into the nursery from
   * non-nursery space.
   *
   * @param src The address of the word (slot) containing the new
   * reference.
   * @param tgt The target of the new reference (about to become the
   * contents of src).
   */
  private final void writeBarrier(VM_Address src, VM_Address tgt) 
    throws VM_PragmaInline {
    if (GATHER_WRITE_BARRIER_STATS) wbFastPathCounter++;
    VM_Address old;
    do {
      old = VM_Address.fromInt(VM_Magic.prepare(src, 0));
    } while (!VM_Magic.attempt(src, 0, old.toInt(), tgt.toInt()));
    if (old.GE(RC_START))
      decBuffer.pushOOL(old);
    if (tgt.GE(RC_START))
      incBuffer.pushOOL(tgt);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Space management
  //

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  This <i>includes</i> space reserved for copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  protected static final int getPagesReserved() {
    return getPagesUsed();
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  This is <i>exclusive of</i> space reserved for
   * copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  protected static final int getPagesUsed() {
    int pages = rcMR.reservedPages();
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }


  /**
   * Return the number of pages available for allocation, <i>assuming
   * all future allocation is to the semi-space</i>.
   *
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the semi-space</i>.
   */
  protected static final int getPagesAvail() {
    return getTotalPages() - rcMR.reservedPages() - immortalMR.reservedPages() - metaDataMR.reservedPages();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //

  /**
   * Show the status of each of the allocators.
   */
  public final void show() {
    rc.show();
    immortal.show();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // RC methods (should be moved out of this class!)
  //

  public final SimpleRCAllocator getAllocator() {
    return rc;
  }
  public final void addToDecBuf(VM_Address obj)
    throws VM_PragmaInline {
    decBuffer.push(obj);
  }
  public final void addToIncBuf(VM_Address obj)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(false);
  }
  public final void addToRootSet(VM_Address root) 
    throws VM_PragmaInline {
    rootSet.push(VM_Magic.objectAsAddress(root));
  }
  public final void addToTraceBuffer(VM_Address root) 
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(sanityTracing);
    tracingBuffer.push(VM_Magic.objectAsAddress(root));
  }
  public final void addToCycleBuf(VM_Address obj)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions && !refCountCycleDetection) VM._assert(false);
    if (cycleBufferAisOpen)
      cycleBufferA.push(obj);
    else
      cycleBufferB.push(obj);
  }
  public final void addToFreeBuf(VM_Address object) 
   throws VM_PragmaInline {
    freeBuffer.push(object);
  }

  private final void processIncBufs() {
    VM_Address tgt;
    while (!(tgt = incBuffer.pop()).isZero()) {
      rcSpace.increment(tgt);
    }
  }
  private final void processIncBufsAndCount() {
    VM_Address tgt;
    incCounter = 0;
    while (!(tgt = incBuffer.pop()).isZero()) {
      rcSpace.increment(tgt);
      incCounter++;
    }
  }
  private final void rcSanityCheck() {
    if (VM.VerifyAssertions) VM._assert(sanityTracing);
    VM_Address obj;
    int checked = 0;
    while (!(obj = tracingBuffer.pop()).isZero()) {
      checked++;
      int rc = SimpleRCBaseHeader.getRC(obj);
      int sanityRC = SimpleRCBaseHeader.getTracingRC(obj);
      SimpleRCBaseHeader.clearTracingRC(obj);
      if (rc != sanityRC) {
	VM.sysWrite("---> ");
	VM.sysWrite(checked);
	VM.sysWrite(" roots checked, RC mismatch: ");
	VM.sysWrite(obj); VM.sysWrite(" -> ");
	VM.sysWrite(rc); VM.sysWrite(" (rc) != ");
	VM.sysWrite(sanityRC); VM.sysWrite(" (sanity)\n");
	if (VM.VerifyAssertions) VM._assert(false);
      }
    }
  }

  private final void processDecBufs() {
    VM_Address tgt;
    while (!(tgt = decBuffer.pop()).isZero()) {
      rcSpace.decrement(tgt, rc, this);
    }
  }
  private final void processDecBufsAndCount() {
    VM_Address tgt;
    decCounter = 0;
    while (!(tgt = decBuffer.pop()).isZero()) {
      rcSpace.decrement(tgt, rc, this);
      decCounter++;
    }
  }

  // FIXME this is inefficient!
  private final void processRootBufs() {
    VM_Address tgt;
    while (!(tgt = rootSet.pop()).isZero())
      decBuffer.push(tgt);
  }
  private final void processRootBufsAndCount() {
    VM_Address tgt;
    rootCounter = 0;
    while (!(tgt = rootSet.pop()).isZero()) {
      decBuffer.push(tgt);
      rootCounter++;
    }
  }

  private final void filterCycleBufs() {
    VM_Address obj;
    AddressQueue src = (cycleBufferAisOpen) ? cycleBufferA : cycleBufferB;
    AddressQueue tgt = (cycleBufferAisOpen) ? cycleBufferB : cycleBufferA;
    purpleCounter = 0;
    while (!(obj = src.pop()).isZero()) {
      purpleCounter++;
      if (VM.VerifyAssertions) VM._assert(!SimpleRCBaseHeader.isGreen(obj));
      if (VM.VerifyAssertions) VM._assert(SimpleRCBaseHeader.isBuffered(obj));
      if (SimpleRCBaseHeader.isLiveRC(VM_Magic.addressAsObject(obj))) {
	if (SimpleRCBaseHeader.isPurple(VM_Magic.addressAsObject(obj)))
	  tgt.push(obj);
	else {
	  SimpleRCBaseHeader.clearBufferedBit(VM_Magic.addressAsObject(obj));
	}
      } else {
	SimpleRCBaseHeader.clearBufferedBit(VM_Magic.addressAsObject(obj));
	freeBuffer.push(obj);
      }
    }
    cycleBufferAisOpen = !cycleBufferAisOpen;
  }
  private final void processFreeBufs(boolean print) {
    VM_Address obj;
    while (!(obj = freeBuffer.pop()).isZero()) {
      if (print) {
	//	VM.sysWrite(obj); VM.sysWrite(" fr\n");
      }
      rcSpace.free(obj, rc);
    }
  }
  static final int CYCLE_PROCESS_LIMIT = 1<<30;
  private final void doMarkGreyPhase() {
    VM_Address obj;
    AddressQueue src = (cycleBufferAisOpen) ? cycleBufferA : cycleBufferB;
    AddressQueue tgt = (cycleBufferAisOpen) ? cycleBufferB : cycleBufferA;
    rcSpace.markGreyPhase();
    int objsProcessed = 0;
    while (!(obj = src.pop()).isZero() && objsProcessed < CYCLE_PROCESS_LIMIT){
      if (VM.VerifyAssertions) VM._assert(!SimpleRCBaseHeader.isGreen(obj));
      if (SimpleRCBaseHeader.isPurple(obj)) {
	if (VM.VerifyAssertions) VM._assert(SimpleRCBaseHeader.isLiveRC(obj));
	rcSpace.markGrey(obj);
	objsProcessed++;
	tgt.push(obj);
      } else {
 	if (VM.VerifyAssertions) VM._assert(SimpleRCBaseHeader.isGrey(obj));
	SimpleRCBaseHeader.clearBufferedBit(obj); // FIXME Why? Why not above?
      }
    } 
    cycleBufferAisOpen = !cycleBufferAisOpen;
  }
  private final void doScanPhase() {
    VM_Address obj;
    AddressQueue src = (cycleBufferAisOpen) ? cycleBufferA : cycleBufferB;
    AddressQueue tgt = (cycleBufferAisOpen) ? cycleBufferB : cycleBufferA;
    rcSpace.scanPhase();
    while (!(obj = src.pop()).isZero()) {
      if (VM.VerifyAssertions) VM._assert(!SimpleRCBaseHeader.isGreen(obj));
      rcSpace.scan(obj);
      tgt.push(obj);
    }
    cycleBufferAisOpen = !cycleBufferAisOpen;
  }
  private final void doCollectPhase() {
    VM_Address obj;
    AddressQueue src = (cycleBufferAisOpen) ? cycleBufferA : cycleBufferB;
    rcSpace.collectPhase();
    while (!(obj = src.pop()).isZero()) {
      if (VM.VerifyAssertions) VM._assert(!SimpleRCBaseHeader.isGreen(obj));
      SimpleRCBaseHeader.clearBufferedBit(obj);
      rcSpace.collectWhite(obj, this);
    }
  }

}

