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
 * This class implements the functionality of a two-generation copying
 * collector where <b>the higher generation is a mark-sweep space</b>
 * (free list allocation, mark-sweep collection).  Nursery collections
 * occur when either the heap is full or the nursery is full.  The
 * nursery size is determined by an optional command line argument.
 * If undefined, the nursery size is "infinite", so nursery
 * collections only occur when the heap is full (this is known as a
 * flexible-sized nursery collector).  Thus both fixed and flexible
 * nursery sizes are supported.  Full heap collections occur when the
 * nursery size has dropped to a statically defined threshold,
 * <code>NURSERY_THRESHOLD</code><p>
 *
 * See the Jones & Lins GC book, chapter 7 for a detailed discussion
 * of generational collection and section 7.3 for an overview of the
 * flexible nursery behavior ("The Standard ML of New Jersey
 * collector"), or go to Appel's paper "Simple generational garbage
 * collection and fast allocation." SP&E 19(2):171--183, 1989.<p>
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
 * @version $Revision$
 * @date $Date$
 */
public class Plan extends Generational implements VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  protected static final boolean usesLOS = false;
  protected static final boolean copyMature = false;
  
  // virtual memory resources
  private static FreeListVMResource matureVM;

  // mature space collector
  private static MarkSweepCollector matureCollector;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //

  // allocators
  private MarkSweepAllocator mature;

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
    matureVM = new FreeListVMResource("Mature", MATURE_START, MATURE_SIZE, VMResource.MOVABLE);
    matureCollector = new MarkSweepCollector(matureVM, matureMR);
  }

  /**
   * Constructor
   */
  public Plan() {
    super();
    mature = new MarkSweepAllocator(matureCollector);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //

  /**
   * Allocate space (for an object) in the mature space
   *
   * @param isScalar True if the object occupying this space will be a scalar
   * @param bytes The size of the space to be allocated (in bytes)
   * @return The address of the first byte of the allocated region
   */
  protected final VM_Address matureAlloc(boolean isScalar, EXTENT bytes) 
    throws VM_PragmaInline {
    return mature.alloc(isScalar, bytes);
  }

  /**
   * Allocate space for copying an object in the mature space (this
   * method <i>does not</i> copy the object, it only allocates space)
   *
   * @param isScalar True if the object occupying this space will be a scalar
   * @param bytes The size of the space to be allocated (in bytes)
   * @return The address of the first byte of the allocated region
   */
  protected final VM_Address matureCopy(boolean isScalar, EXTENT bytes) 
    throws VM_PragmaInline {
    return mature.allocCopy(isScalar, bytes);
  }

  /**
   * Return the initial header value for a newly allocated mature
   * instance (either large object or surviving nursery object).
   *
   * @param bytes The size of the newly created instance in bytes.
   * @return The inital header value for the new instance.
   */
  public final static int getInitialHeaderValue(EXTENT bytes)
    throws VM_PragmaInline {
    return matureCollector.getInitialHeaderValue(bytes);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Collection
  //

  /**
   * Perform operations pertaining to the mature space with
   * <i>global</i> scope in preparation for a collection.  This is
   * called by <code>Generational</code>, which will ensure that
   * <i>only one thread</i> executes this.
   */
  protected final void globalMaturePrepare() {
    matureCollector.prepare(matureVM, matureMR);
  }

  /**
   * Perform operations pertaining to the mature space with
   * <i>thread-local</i> scope in preparation for a collection.  This
   * is called by <code>Generational</code>, which will ensure that
   * <i>all threads</i> execute this.
   */
  protected final void threadLocalMaturePrepare(int count) {
    mature.prepare();
  }

  /**
   * Perform operations pertaining to the mature space with
   * <i>thread-local</i> scope to clean up at the end of a collection.
   * This is called by <code>Generational</code>, which will ensure
   * that <i>all threads</i> execute this.<p>
   */
  protected final void threadLocalMatureRelease(int count) {
    mature.release();
  }

  /**
   * Perform operations pertaining to the mature space with
   * <i>global</i> scope to clean up at the end of a collection.  This
   * is called by <code>Generational</code>, which will ensure that
   * <i>only one</i> thread executes this.<p>
   */
  protected final void globalMatureRelease() {
    matureCollector.release();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Object processing and tracing
  //

  /**
   * Trace a reference into the mature space during GC.  This simply
   * involves the <code>traceObject</code> method of the mature
   * collector.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  protected static final VM_Address traceMatureObject(VM_Address obj,
						      VM_Address addr) {
    return matureCollector.traceObject(obj);
  }

  /**
   * Return true if the given reference to a mature object will not
   * move in this GC (always true for this non-moving mature space).
   *
   * @param obj The object in question
   * @return True
   */
  protected final boolean willNotMoveMature(VM_Address addr) 
    throws VM_PragmaInline {
    return true;
  }

  /**
   * Return true if the object resides in a copying space (in this
   * case only nursery objects are in a copying space).
   *
   * @param obj The object in question
   * @return True if the object resides in a copying space.
   */
  public final static boolean isCopyObject(Object obj) {
    VM_Address addr = VM_Interface.refToAddress(VM_Magic.objectAsAddress(obj));
    return (addr.GE(NURSERY_START) && addr.LE(HEAP_END));
  }

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param obj The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public final static boolean isLive(VM_Address obj) {
    VM_Address addr = VM_ObjectModel.getPointerInMemoryRegion(obj);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(NURSERY_START))
	return Copy.isLive(obj);
      else if (addr.GE(MATURE_START))
	return matureCollector.isLive(obj);
      else if (addr.GE(IMMORTAL_START))
	return true;
    } 
    return false;
  }

  /**
   * Reset the GC bits in the header word of an object that has just
   * been copied.  This may, for example, involve clearing a write
   * barrier bit.  In this case the word has to be initialized for the
   * mark-sweep collector.
   *
   * @param fromObj The original (uncopied) object
   * @param forwardingPtr The forwarding pointer, which is the GC word
   * of the original object, and typically encodes some GC state as
   * well as pointing to the copied object.
   * @param bytes The size of the copied object in bytes.
   * @return The updated GC word (in this case unchanged).
   */
  public final static int resetGCBitsForCopy(VM_Address fromObj,
					     int forwardingPtr, int bytes) {
    return (forwardingPtr & ~HybridHeader.GC_BITS_MASK) | matureCollector.getInitialHeaderValue(bytes);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //

  /**
   * Show the status of the mature allocator.
   */
  protected final void showMature() {
    mature.show();
  }
}
