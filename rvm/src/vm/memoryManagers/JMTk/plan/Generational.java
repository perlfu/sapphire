/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.AllocAdvice;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Type;
import com.ibm.JikesRVM.memoryManagers.vmInterface.CallSite;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 * This abstract class implements the core functionality of generic
 * two-generationa copying collectors.  Nursery collections occur when
 * either the heap is full or the nursery is full.  The nursery size
 * is determined by an optional command line argument.  If undefined,
 * the nursery size is "infinite", so nursery collections only occur
 * when the heap is full (this is known as a flexible-sized nursery
 * collector).  Thus both fixed and flexible nursery sizes are
 * supported.  Full heap collections occur when the nursery size has
 * dropped to a statically defined threshold,
 * <code>NURSERY_THRESHOLD</code><p>
 * 
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  Instances of Plan map 1:1 to "kernel
 * threads" (aka CPUs or in Jikes RVM, VM_Processors).  Thus instance
 * methods allow fast, unsychronized access to Plan utilities such as
 * allocation and collection.  Each instance rests on static resources
 * (such as memory and virtual memory resources) which are "global"
 * and therefore "static" members of Plan.  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance proprties of this plan.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public abstract class Generational extends StopTheWorldGC 
  implements VM_Uninterruptible {
  public static final String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  public static final boolean needsWriteBarrier = true;
  public static final boolean needsRefCountWriteBarrier = false;
  public static final boolean refCountCycleDetection = false;
  public static final boolean movesObjects = true;

  // virtual memory resources
  protected static MonotoneVMResource nurseryVM;
  protected static FreeListVMResource losVM;

  // memory resources
  protected static MemoryResource nurseryMR;
  protected static MemoryResource matureMR;
  protected static MemoryResource losMR;

  // large object space (LOS) collector
  protected static TreadmillSpace losSpace;

  // GC state
  protected static boolean fullHeapGC = false;

  // Allocators
  protected static final byte NURSERY_SPACE = 0;
  protected static final byte MATURE_SPACE = 1;
  protected static final byte LOS_SPACE = 2;
  public static final byte DEFAULT_SPACE = NURSERY_SPACE;
  public static final byte TIB_SPACE = DEFAULT_SPACE;


  // Miscellaneous constants
  protected static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;
  protected static final int DEFAULT_MIN_NURSERY = (512*1024)>>LOG_PAGE_SIZE;
  protected static final float SURVIVAL_ESTIMATE = (float) 0.8; // est yield
  protected static final EXTENT LOS_SIZE_THRESHOLD = 8 * 1024; // largest size supported by MS

  // Memory layout constants
  public    static final long           AVAILABLE = VM_Interface.MAXIMUM_MAPPABLE.diff(PLAN_START).toLong();
  protected static final EXTENT    MATURE_SS_SIZE = Conversions.roundDownMB((int)(AVAILABLE / 3.3));
  protected static final EXTENT      NURSERY_SIZE = MATURE_SS_SIZE;
  protected static final EXTENT          LOS_SIZE = Conversions.roundDownMB((int)(AVAILABLE / 3.3 * 0.3));
  public    static final int             MAX_SIZE = 2 * MATURE_SS_SIZE;
  protected static final VM_Address     LOS_START = PLAN_START;
  protected static final VM_Address       LOS_END = LOS_START.add(LOS_SIZE);
  protected static final VM_Address  MATURE_START = LOS_END;
  protected static final EXTENT       MATURE_SIZE = MATURE_SS_SIZE<<1;
  protected static final VM_Address    MATURE_END = MATURE_START.add(MATURE_SIZE);
  protected static final VM_Address NURSERY_START = MATURE_END;
  protected static final VM_Address   NURSERY_END = NURSERY_START.add(NURSERY_SIZE);
  protected static final VM_Address      HEAP_END = NURSERY_END;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //

  // allocators
  protected BumpPointer nursery;
  protected TreadmillLocal los;

  // write buffer (remembered set)
  protected WriteBuffer remset;

  protected int wbFastPathCounter = 0;
  protected int wbSlowPathCounter = 0;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time). This is where key <i>global</i> instances
   * are allocated.  These instances will be incorporated into the
   * boot image by the build process.
   */
  static {
    nurseryMR = new MemoryResource("nur", POLL_FREQUENCY);
    matureMR = new MemoryResource("mat", POLL_FREQUENCY);
    nurseryVM  = new MonotoneVMResource(NURSERY_SPACE, "Nursery", nurseryMR,   NURSERY_START, NURSERY_SIZE, VMResource.MOVABLE);
    addSpace(NURSERY_SPACE, "Nursery");
    addSpace(MATURE_SPACE, "Mature Space");

    if (Plan.usesLOS) {
      losMR = new MemoryResource("los", POLL_FREQUENCY);
      losVM = new FreeListVMResource(LOS_SPACE, "LOS", LOS_START, LOS_SIZE, VMResource.IN_VM);
      losSpace = new TreadmillSpace(losVM, losMR);
      addSpace(LOS_SPACE, "LOS Space");
    }
  }

  /**
   * Constructor
   */
  public Generational() {
    nursery = new BumpPointer(nurseryVM);
    if (Plan.usesLOS) los = new TreadmillLocal(losSpace);
    remset = new WriteBuffer(locationPool);
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation
   */
  public static final void boot() throws VM_PragmaInterruptible {
    StopTheWorldGC.boot();
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //
  abstract VM_Address matureAlloc(boolean isScalar, EXTENT bytes);
  abstract VM_Address matureCopy(boolean isScalar, EXTENT bytes);

  /**
   * Allocate space (for an object)
   *
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @param allocator The allocator number to be used for this allocation
   * @param advice Statically-generated allocation advice for this allocation
   * @return The address of the first byte of the allocated region
   */
  public final VM_Address alloc(EXTENT bytes, boolean isScalar, int allocator,
				AllocAdvice advice)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(bytes == (bytes & (~(WORD_SIZE-1))));
    if (allocator == NURSERY_SPACE && bytes > LOS_SIZE_THRESHOLD) 
      allocator = (Plan.usesLOS) ? LOS_SPACE : MATURE_SPACE;
    if (VM.VerifyAssertions) VM._assert(Plan.usesLOS || allocator != LOS_SPACE);
    VM_Address region;
    switch (allocator) {
      case  NURSERY_SPACE: region = nursery.alloc(isScalar, bytes); break;
      case   MATURE_SPACE: region = matureAlloc(isScalar, bytes); break;
      case IMMORTAL_SPACE: region = immortal.alloc(isScalar, bytes); break;
      case      LOS_SPACE: region = los.alloc(isScalar, bytes); break;
      default:             region = VM_Address.zero();
	                   VM.sysFail("No such allocator");
    }
    if (VM.VerifyAssertions) VM._assert(Memory.assertIsZeroed(region, bytes));
    return region;
  }

  public final VM_Address alloc2(int allocator, EXTENT bytes, boolean isScalar) throws VM_PragmaNoInline {
      VM_Address region;
    switch (allocator) {
	 case  NURSERY_SPACE: region = nursery.alloc(isScalar, bytes); break;
	 case   MATURE_SPACE: region = matureAlloc(isScalar, bytes); break;
	 case IMMORTAL_SPACE: region = immortal.alloc(isScalar, bytes); break;
	 case      LOS_SPACE: region = los.alloc(isScalar, bytes); break;
      default:             region = VM_Address.zero();
	                   VM.sysFail("No such allocator");
    }
    return region;
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
    if (allocator == NURSERY_SPACE && bytes > LOS_SIZE_THRESHOLD)
      allocator = (Plan.usesLOS) ? LOS_SPACE : MATURE_SPACE;
    if (VM.VerifyAssertions) VM._assert(Plan.usesLOS || allocator != LOS_SPACE);
    switch (allocator) {
      case  NURSERY_SPACE: return;
      case   MATURE_SPACE: if (!Plan.copyMature) Header.initializeMarkSweepHeader(ref, tib, bytes, isScalar); return;
      case IMMORTAL_SPACE: Immortal.postAlloc(ref); return;
      case      LOS_SPACE: Header.initializeMarkSweepHeader(ref, tib, bytes, isScalar); return;
      default:             if (VM.VerifyAssertions) VM.sysFail("No such allocator");
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
				    boolean isScalar) 
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(bytes < LOS_SIZE_THRESHOLD);
    return matureCopy(isScalar, bytes);
  }

  /**  
   * Perform any post-copy actions.  In this case nothing is required.
   *
   * @param ref The newly allocated object
   * @param tib The TIB of the newly allocated object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   */
  public final void postCopy(Object ref, Object[] tib, int size,
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
    return (bytes >= LOS_SIZE_THRESHOLD) ? (Plan.usesLOS ? LOS_SPACE : MATURE_SPACE) : NURSERY_SPACE;
  }


  protected byte getSpaceFromAllocator (Allocator a) {
    if (a == nursery) return NURSERY_SPACE;
    if (a == los) return LOS_SPACE;
    return super.getSpaceFromAllocator(a);
  }

  protected Allocator getAllocatorFromSpace (byte s) {
    if (s == NURSERY_SPACE) return nursery;
    if (s == LOS_SPACE) return los;
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
  public final boolean poll (boolean mustCollect, MemoryResource mr) 
    throws VM_PragmaLogicallyUninterruptible {
    if (gcInProgress) return false;
    mustCollect |= stressTestGCRequired();
    boolean heapFull = getPagesReserved() > getTotalPages();
    boolean nurseryFull = nurseryMR.reservedPages() > Options.nurseryPages;
    if (mustCollect || heapFull || nurseryFull) {
      if (VM.VerifyAssertions)    VM._assert(mr != metaDataMR);
      required = mr.reservedPages() - mr.committedPages();
      if (mr == nurseryMR || (Plan.copyMature && (mr == matureMR)))
	required = required<<1;  // must account for copy reserve
      int nurseryYield = ((int)((float) nurseryMR.committedPages() * SURVIVAL_ESTIMATE))<<1;
      fullHeapGC = mustCollect || (nurseryYield < required) || fullHeapGC;
      VM_Interface.triggerCollection(VM_Interface.RESOURCE_TRIGGERED_GC);
      return true;
    }
    return false;
  }

  /**
   * Perform a collection.
   */
  public final void collect () {
    if ((verbose == 1) && (fullHeapGC)) VM.sysWrite("[Full heap]");
    super.collect();
  }

  abstract void globalMaturePrepare();
  /**
   * Perform operations with <i>global</i> scope in preparation for a
   * collection.  This is called by <code>BasePlan</code>, which will
   * ensure that <i>only one thread</i> executes this.<p>
   *
   * In this case, it means flipping semi-spaces, resetting the
   * semi-space memory resource, and preparing each of the collectors.
   */
  protected final void globalPrepare() {
    nurseryMR.reset(); // reset the nursery
    if (fullHeapGC) {
      Statistics.gcMajorCount++;
      // prepare each of the collected regions
      if (Plan.usesLOS) losSpace.prepare(losVM, losMR);
      globalMaturePrepare();
      Immortal.prepare(immortalVM, null);
    }
  }

  abstract void threadLocalMaturePrepare(int count);
  /**
   * Perform operations with <i>thread-local</i> scope in preparation
   * for a collection.  This is called by <code>BasePlan</code>, which
   * will ensure that <i>all threads</i> execute this.<p>
   *
   * In this case, it means flushing the remsets, rebinding the
   * nursery, and if a full heap collection, preparing the mature
   * space and LOS.
   */
  protected final void threadLocalPrepare(int count) {
    remset.flushLocal();
    nursery.rebind(nurseryVM);
    if (fullHeapGC) {
      threadLocalMaturePrepare(count);
      if (Plan.usesLOS) los.prepare();
    }
  }

  /**
   * We reset the state for a GC thread that is not participating in
   * this GC
   */
  final public void prepareNonParticipating() {
    threadLocalPrepare(NON_PARTICIPANT);
  }

  abstract void threadLocalMatureRelease(int count);
  /**
   * Perform operations with <i>thread-local</i> scope to clean up at
   * the end of a collection.  This is called by
   * <code>BasePlan</code>, which will ensure that <i>all threads</i>
   * execute this.<p>
   *
   * In this case, it means flushing the remsets, and if a full heap
   * GC, releasing the large object space (which triggers the sweep
   * phase of the mark-sweep collector used by the LOS), and releasing
   * the mature space.
   */
  protected final void threadLocalRelease(int count) {
    if (GATHER_WRITE_BARRIER_STATS) { 
      // This is printed independently of the verbosity so that any
      // time someone sets the GATHER_WRITE_BARRIER_STATS flags they
      // will know---it will have a noticable performance hit...
      VM.sysWrite("<GC ", Statistics.gcCount); VM.sysWrite(" "); 
      VM.sysWrite(wbFastPathCounter); VM.sysWrite(" wb-fast, ");
      VM.sysWrite(wbSlowPathCounter); VM.sysWrite(" wb-slow>\n");
      wbFastPathCounter = wbSlowPathCounter = 0;
    }
    if (fullHeapGC) { 
      if (Plan.usesLOS) los.release();
      threadLocalMatureRelease(count);
    }
    remset.flushLocal(); // flush any remset entries collected during GC
  }

  abstract void globalMatureRelease();
  /**
   * Perform operations with <i>global</i> scope to clean up at the
   * end of a collection.  This is called by <code>BasePlan</code>,
   * which will ensure that <i>only one</i> thread executes this.<p>
   *
   * In this case, it means releasing each of the spaces, determining
   * whether the next GC will be a full heap GC, and checking whether
   * the GC made progress.
   */
  protected void globalRelease() {
    // release each of the collected regions
    nurseryVM.release();
    locationPool.flushQueue(1); // flush any remset entries collected during GC
    if (fullHeapGC) {
      if (Plan.usesLOS) losSpace.release();
      globalMatureRelease();
      Immortal.release(immortalVM, null);
    }
    int minNursery = (Options.nurseryPages < MAX_INT) ? Options.nurseryPages : DEFAULT_MIN_NURSERY;
    fullHeapGC = (getPagesAvail() < minNursery);
    if (getPagesReserved() + required >= getTotalPages()) {
      if (!progress) {
	VM.sysWrite("getPagesReserved() = ", getPagesReserved());
	VM.sysWrite("required = ", required);
	VM.sysWrite("getTotalPages() = ", getTotalPages());
 	VM.sysFail("Out of memory");
      }
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
  public static final VM_Address traceObject (VM_Address obj) {
    if (obj.isZero()) return obj;
    VM_Address addr = VM_Interface.refToAddress(obj);
    byte space = VMResource.getSpace(addr);
    if (space == NURSERY_SPACE)
      return Copy.traceObject(obj);
    if (!fullHeapGC)
	return obj;
    switch (space) {
        case LOS_SPACE:         return losSpace.traceObject(obj);
        case IMMORTAL_SPACE:    return Immortal.traceObject(obj);
        case BOOT_SPACE:	return Immortal.traceObject(obj);
        case META_SPACE:	return obj;
        default:                return Plan.traceMatureObject(space, obj, addr);
    }
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
  public static final VM_Address traceObject(VM_Address obj, boolean root)
    throws VM_PragmaInline {
    return traceObject(obj);  // root or non-root is of no consequence here
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
    if (src.LT(NURSERY_START) && tgt.GE(NURSERY_START)) {
      if (GATHER_WRITE_BARRIER_STATS) wbSlowPathCounter++;
      remset.insert(src);
    }
    VM_Magic.setMemoryAddress(src, tgt);
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
    return getPagesUsed()
      + nurseryMR.reservedPages()
      + (Plan.copyMature ? matureMR.reservedPages() : 0);
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
    int pages = nurseryMR.reservedPages();
    pages += matureMR.reservedPages();
    pages += (Plan.usesLOS) ? losMR.reservedPages() : 0;
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }

  /**
   * Return the number of pages available for allocation, <i>assuming
   * all future allocation is to the nursery</i>.
   *
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the nursery</i>.
   */
  protected static final int getPagesAvail() {
    int copyReserved = nurseryMR.reservedPages();
    int nonCopyReserved = ((Plan.usesLOS) ? losMR.reservedPages() : 0) +immortalMR.reservedPages() + metaDataMR.reservedPages();
    if (Plan.copyMature)
      copyReserved += matureMR.reservedPages();
    else
      nonCopyReserved += matureMR.reservedPages();

    return ((getTotalPages() - nonCopyReserved)>>1) - copyReserved;
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //

  abstract void showMature();
  /**
   * Show the status of each of the allocators.
   */
  public final void show() {
    nursery.show();
    showMature();
    if (Plan.usesLOS) los.show();
    immortal.show();
  }


}
