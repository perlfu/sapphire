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
 * This class implements the functionality of a standard
 * two-generation copying collector.  Nursery collections occur when
 * either the heap is full or the nursery is full.  The nursery size
 * is determined by an optional command line argument.  If undefined,
 * the nursery size is "infinite", so nursery collections only occur
 * when the heap is full (this is known as a flexible-sized nursery
 * collector).  Thus both fixed and flexible nursery sizes are
 * supported.  Full heap collections occur when the nursery size has
 * dropped to a statically defined threshold,
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
  protected static final boolean usesLOS = true;
  protected static final boolean copyMature = true;

  // virtual memory resources
  private static MonotoneVMResource mature0VM;
  private static MonotoneVMResource mature1VM;

  // GC state
  private static boolean hi = false; // True if copying to "higher" semispace 

  // Memory layout constants
  private static final VM_Address MATURE_LO_START = MATURE_START;
  private static final VM_Address MATURE_HI_START = MATURE_START.add(MATURE_SS_SIZE);

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //

  // allocators
  private BumpPointer mature;

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
    mature0VM  = new MonotoneVMResource("Higher gen lo", matureMR, MATURE_LO_START, MATURE_SS_SIZE, VMResource.MOVABLE);
    mature1VM  = new MonotoneVMResource("Higher gen hi", matureMR, MATURE_HI_START, MATURE_SS_SIZE, VMResource.MOVABLE);
  }

  /**
   * Constructor
   */
  public Plan() {
    super();
    mature = new BumpPointer(mature0VM);
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
    return mature.alloc(isScalar, bytes);
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
    return losCollector.getInitialHeaderValue(bytes);
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
    matureMR.reset(); // reset the nursery semispace memory resource
    hi = !hi;         // flip the semi-spaces
  }

  /**
   * Perform operations pertaining to the mature space with
   * <i>thread-local</i> scope in preparation for a collection.  This
   * is called by <code>Generational</code>, which will ensure that
   * <i>all threads</i> execute this.
   */
  protected final void threadLocalMaturePrepare(int count) {
    mature.rebind(((hi) ? mature1VM : mature0VM)); 
  }

  /**
   * Perform operations pertaining to the mature space with
   * <i>thread-local</i> scope to clean up at the end of a collection.
   * This is called by <code>Generational</code>, which will ensure
   * that <i>all threads</i> execute this.<p>
   */
  protected final void threadLocalMatureRelease(int count) {
  } // do nothing

  /**
   * Perform operations pertaining to the mature space with
   * <i>global</i> scope to clean up at the end of a collection.  This
   * is called by <code>Generational</code>, which will ensure that
   * <i>only one</i> thread executes this.<p>
   */
  protected final void globalMatureRelease() {
    ((hi) ? mature0VM : mature1VM).release();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Object processing and tracing
  //

  /**
   * Trace a reference into the mature space during GC.  This involves
   * determining whether the instance is in from space, and if so,
   * calling the <code>traceObject</code> method of the Copy
   * collector.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  protected static final VM_Address traceMatureObject(VM_Address obj,
						      VM_Address addr) {
    if ((hi && addr.LT(MATURE_HI_START)) ||
	(!hi && addr.GE(MATURE_HI_START)))
      return Copy.traceObject(obj);
    else
      return obj;
  }

  /**
   * Return true if the given reference to a mature object will not
   * move in this GC (if in from space it will move, if in to space it
   * has already moved).
   *
   * @param obj The object in question
   * @return True if the given reference will not move in this GC.
   */
  protected final boolean willNotMoveMature(VM_Address addr) 
    throws VM_PragmaInline {
    return (hi ? mature1VM : mature0VM).inRange(addr);
  }

  /**
   * Return true if the object resides in a copying space (in this
   * case mature and nursery objects are in a copying space).
   *
   * @param obj The object in question
   * @return True if the object resides in a copying space.
   */
  public static final boolean isCopyObject(Object base) {
    VM_Address addr =VM_Interface.refToAddress(VM_Magic.objectAsAddress(base));
    return (addr.GE(MATURE_START) && addr.LE(HEAP_END));
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
      if (addr.GE(MATURE_START))
	return Copy.isLive(obj);
      else if (addr.GE(LOS_START))
	return losCollector.isLive(obj);
      else if (addr.GE(IMMORTAL_START))
	return true;
    } 
    return false;
  }

  /**
   * Reset the GC bits in the header word of an object that has just
   * been copied.  This may, for example, involve clearing a write
   * barrier bit.  In this case nothing is required, so the header word
   * is returned unmodified.
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
    return forwardingPtr; // a no-op for this collector
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
