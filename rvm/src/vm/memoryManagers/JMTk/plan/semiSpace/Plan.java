/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.AllocAdvice;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Type;
import com.ibm.JikesRVM.memoryManagers.vmInterface.CallSite;


import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 * This class implements a simple semi-space collector. See the Jones
 * & Lins GC book, section 2.2 for an overview of the basic
 * algorithm. This implementation also includes a large object space
 * (LOS), and an uncollected "immortal" space.<p>
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
 * @author Perry Cheng
 *
 * @version $Revision$
 * @date $Date$
 */
public class Plan extends StopTheWorldGC implements VM_Uninterruptible {
  public static final String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  public static final boolean needsWriteBarrier = false;
  public static final boolean needsPutStaticWriteBarrier = false;
  public static final boolean needsTIBStoreWriteBarrier = false;
  public static final boolean refCountCycleDetection = false;
  public static final boolean movesObjects = true;

  // virtual memory resources
  private static FreeListVMResource losVM;
  private static MonotoneVMResource ss0VM;
  private static MonotoneVMResource ss1VM;

  // memory resources
  private static MemoryResource ssMR;
  private static MemoryResource losMR;

  // large object space (LOS) collector
  private static TreadmillSpace losSpace;

  // GC state
  private static boolean hi = false; // True if allocing to "higher" semispace

  // Allocators
  private static final byte LOW_SS_SPACE = 0;
  private static final byte HIGH_SS_SPACE = 1;
  private static final byte LOS_SPACE = 2;
  public static final byte DEFAULT_SPACE = 3; // logical space that maps to either LOW_SS_SPACE or HIGH_SS_SPACE

  // Miscellaneous constants
  private static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;
  private static final int LOS_SIZE_THRESHOLD = DEFAULT_LOS_SIZE_THRESHOLD;
  
  // Memory layout constants
  public  static final long            AVAILABLE = VM_Interface.MAXIMUM_MAPPABLE.diff(PLAN_START).toLong();
  private static final VM_Extent         SS_SIZE = Conversions.roundDownMB(VM_Extent.fromInt((int)(AVAILABLE / 2.3)));
  private static final VM_Extent        LOS_SIZE = Conversions.roundDownMB(VM_Extent.fromInt((int)(AVAILABLE / 2.3 * 0.3)));
  public  static final VM_Extent        MAX_SIZE = SS_SIZE.add(SS_SIZE);

  private static final VM_Address      LOS_START = PLAN_START;
  private static final VM_Address        LOS_END = LOS_START.add(LOS_SIZE);
  private static final VM_Address       SS_START = LOS_END;
  private static final VM_Address   LOW_SS_START = SS_START;
  private static final VM_Address  HIGH_SS_START = SS_START.add(SS_SIZE);
  private static final VM_Address         SS_END = HIGH_SS_START.add(SS_SIZE);
  private static final VM_Address       HEAP_END = SS_END;


  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //

  // allocators
  public BumpPointer ss;
  private TreadmillLocal los;

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
    ssMR = new MemoryResource("ss", POLL_FREQUENCY);
    losMR = new MemoryResource("los", POLL_FREQUENCY);
    ss0VM = new MonotoneVMResource(LOW_SS_SPACE, "Lower SS", ssMR, LOW_SS_START, SS_SIZE, VMResource.MOVABLE);
    ss1VM = new MonotoneVMResource(HIGH_SS_SPACE, "Upper SS", ssMR, HIGH_SS_START, SS_SIZE, VMResource.MOVABLE);
    losVM = new FreeListVMResource(LOS_SPACE, "LOS", LOS_START, LOS_SIZE, VMResource.IN_VM);
    losSpace = new TreadmillSpace(losVM, losMR);

    addSpace(LOW_SS_SPACE, "Lower Semi-Space");
    addSpace(HIGH_SS_SPACE, "Upper Semi-Space");
    addSpace(LOS_SPACE, "LOS Space");
    // DEFAULT_SPACE is logical and does not actually exist
  }

  /**
   * Constructor
   */
  public Plan() {
    ss = new BumpPointer(ss0VM);
    los = new TreadmillLocal(losSpace);
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
  public final VM_Address alloc (int bytes, boolean isScalar, int allocator,
				AllocAdvice advice)
    throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(bytes == (bytes & (~(WORD_SIZE-1))));
    if (allocator == DEFAULT_SPACE && bytes > LOS_SIZE_THRESHOLD) {
      return los.alloc(isScalar, bytes);
    } else {
      switch (allocator) {
      case  DEFAULT_SPACE:  return ss.alloc(isScalar, bytes);
      case IMMORTAL_SPACE:  return immortal.alloc(isScalar, bytes);
      case      LOS_SPACE:  return los.alloc(isScalar, bytes);
      default: 
	if (VM_Interface.VerifyAssertions) VM_Interface.sysFail("No such allocator");
	return VM_Address.zero();
      }
    }
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
  public final void postAlloc(Object ref, Object[] tib, int bytes,
			      boolean isScalar, int allocator)
    throws VM_PragmaInline {
    if (allocator == DEFAULT_SPACE && bytes > LOS_SIZE_THRESHOLD) {
      Header.initializeLOSHeader(ref, tib, bytes, isScalar);
    } else {
      switch (allocator) {
      case  DEFAULT_SPACE: return;
      case IMMORTAL_SPACE: ImmortalSpace.postAlloc(ref); return;
      case      LOS_SPACE: Header.initializeLOSHeader(ref, tib, bytes, isScalar); return;
      default:             if (VM_Interface.VerifyAssertions) VM_Interface.sysFail("No such allocator");
      }
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
  public final VM_Address allocCopy(VM_Address original, int bytes, 
				    boolean isScalar) 
    throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(bytes < LOS_SIZE_THRESHOLD);
    VM_Address result = ss.alloc(isScalar, bytes);
    return result;
  }

  /**  
   * Perform any post-copy actions.  In this case nothing is required.
   *
   * @param ref The newly allocated object
   * @param tib The TIB of the newly allocated object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   */
  public final void postCopy(Object ref, Object[] tib, int bytes,
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
  public final int getAllocator(Type type, int bytes, CallSite callsite, 
				AllocAdvice hint) {
    return (bytes >= LOS_SIZE_THRESHOLD) ? LOS_SPACE : DEFAULT_SPACE;
  }

  protected final byte getSpaceFromAllocator (Allocator a) {
    if (a == ss) return DEFAULT_SPACE;
    if (a == los) return LOS_SPACE;
    return super.getSpaceFromAllocator(a);
  }

  protected final Allocator getAllocatorFromSpace (byte s) {
    if (s == DEFAULT_SPACE) return ss;
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
  public final AllocAdvice getAllocAdvice(Type type, int bytes,
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
  public static final int getInitialHeaderValue(int bytes)
    throws VM_PragmaInline {
    return losSpace.getInitialHeaderValue(bytes);
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
    if (mustCollect || getPagesReserved() > getTotalPages()) {
      required = mr.reservedPages() - mr.committedPages();
      if (mr == ssMR)
	required = required<<1;  // must account for copy reserve
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
    hi = !hi;        // flip the semi-spaces
    ssMR.reset();    // reset the semispace memory resource, and
    // prepare each of the collected regions
    CopySpace.prepare(((hi) ? ss0VM : ss1VM), ssMR);
    ImmortalSpace.prepare(immortalVM, null);
    losSpace.prepare(losVM, losMR);
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
    // rebind the semispace bump pointer to the appropriate semispace.
    ss.rebind(((hi) ? ss1VM : ss0VM)); 
    los.prepare();
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
    los.release();
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
    losSpace.release();
    ((hi) ? ss0VM : ss1VM).release();
    CopySpace.release(((hi) ? ss0VM : ss1VM), ssMR);
    ImmortalSpace.release(immortalVM, null);
    if (getPagesReserved() + required >= getTotalPages()) {
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
  public static final VM_Address traceObject (VM_Address obj) throws VM_PragmaInline {
    if (obj.isZero()) return obj;
    VM_Address addr = VM_Interface.refToAddress(obj);
    byte space = VMResource.getSpace(addr);
    switch (space) {
      case LOW_SS_SPACE:    return   hi  ? CopySpace.traceObject(obj) : obj;
      case HIGH_SS_SPACE:   return (!hi) ? CopySpace.traceObject(obj) : obj;
      case LOS_SPACE:       return losSpace.traceObject(obj);
      case IMMORTAL_SPACE:  return ImmortalSpace.traceObject(obj);
      case BOOT_SPACE:	    return ImmortalSpace.traceObject(obj);
      case META_SPACE:	    return obj;
      default:              if (VM_Interface.VerifyAssertions) {
	                      VM_Interface.sysWrite("Plan.traceObject: obj ",obj);
	                      VM_Interface.sysWrite(" or addr ",addr);
	                      VM_Interface.sysWrite(" of page ",Conversions.addressToPagesDown(addr));
	                      VM_Interface.sysWriteln(" is in unknown space",space);
	                      VM_Interface.sysWrite("Type = ");
			      VM_Interface.sysWrite(VM_Magic.getObjectType(obj).getDescriptor());
			      VM_Interface.sysWriteln();
			      VM_Interface.sysFail("Plan.traceObject: unknown space");
                            }
			    return obj;
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
  public static final VM_Address traceObject(VM_Address obj, boolean root) {
    return traceObject(obj);  // root or non-root is of no consequence here
  }


  /**
   * Return true if the given reference is to an object that is within
   * one of the semi-spaces.
   *
   * @param ref The object in question
   * @return True if the given reference is to an object that is within
   * one of the semi-spaces.
   */
  public static final boolean isSemiSpaceObject(Object ref) {
    VM_Address addr = VM_Interface.refToAddress(VM_Magic.objectAsAddress(ref));
    return (addr.GE(SS_START) && addr.LE(SS_END));
  }

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param obj The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public static final boolean isLive(VM_Address obj) {
    if (obj.isZero()) return false;
    VM_Address addr = VM_Interface.refToAddress(obj);
    byte space = VMResource.getSpace(addr);
    switch (space) {
      case LOW_SS_SPACE:    return CopySpace.isLive(obj);
      case HIGH_SS_SPACE:   return CopySpace.isLive(obj);
      case LOS_SPACE:       return losSpace.isLive(obj);
      case IMMORTAL_SPACE:  return true;
      case BOOT_SPACE:	    return true;
      case META_SPACE:	    return true;
      default:              if (VM_Interface.VerifyAssertions) {
	                      VM_Interface.sysWriteln("Plan.isLive: unknown space",space);
			      VM_Interface.sysFail("Plan.isLive: unknown space");
                            }
			    return false;
    }
  }


  /**
   * Reset the GC bits in the header word of an object that has just
   * been copied.  This may, for example, involve clearing a write
   * barrier bit.  In this case nothing is required, so the header
   * word is returned unmodified.
   *
   * @param fromObj The original (uncopied) object
   * @param forwardingWord The integer containing the GC bits, which is the GC word
   * of the original object, and typically encodes some GC state as
   * well as pointing to the copied object.
   * @param bytes The size of the copied object in bytes.
   * @return The updated GC word (in this case unchanged).
   */
  public static final int resetGCBitsForCopy(VM_Address fromObj,
					     int forwardingWord, int bytes) {
    return forwardingWord; // a no-op for this collector
  }

  public static boolean willNotMove (VM_Address obj) {
   boolean movable = VMResource.refIsMovable(obj);
   if (!movable) return true;
   VM_Address addr = VM_Interface.refToAddress(obj);
   return (hi ? ss1VM : ss0VM).inRange(addr);
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
    // we must account for the number of pages required for copying,
    // which equals the number of semi-space pages reserved
    return ssMR.reservedPages() + getPagesUsed();
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
    int pages = ssMR.reservedPages();
    pages += losMR.reservedPages();
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
    int semispaceTotal = getTotalPages() - losMR.reservedPages() 
      - immortalMR.reservedPages();
    return (semispaceTotal>>1) - ssMR.reservedPages();
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //

  /**
   * Show the status of each of the allocators.
   */
  public final void show() {
    ss.show();
    los.show();
    immortal.show();
  }


}
