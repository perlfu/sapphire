/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 * This abstract class implments the core functionality for all memory
 * management schemes.  All JMTk plans should inherit from this
 * class.<p>
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
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public abstract class BasePlan 
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  public  static int verbose = 0;
  private static final int MAX_PLANS = 100;
  protected static Plan [] plans = new Plan[MAX_PLANS];
  protected static int planCount = 0;        // Number of plan instances in existence

  // GC state and control variables
  protected static boolean gcInProgress = false;  // Controlled by subclasses

  // Timing variables
  protected static double bootTime;

  // Meta data resources
  private static MonotoneVMResource metaDataVM;
  protected static MemoryResource metaDataMR;
  protected static RawPageAllocator metaDataRPA;
  public static MonotoneVMResource bootVM;
  public static MemoryResource bootMR;
  public static MonotoneVMResource immortalVM;
  protected static MemoryResource immortalMR;

  // Space constants
  private static final String[] spaceNames = new String[128];
  public static final byte UNUSED_SPACE = 127;
  public static final byte BOOT_SPACE = 126;
  public static final byte META_SPACE = 125;
  public static final byte IMMORTAL_SPACE = 124;

  // Miscellaneous constants
  private static final int META_DATA_POLL_FREQUENCY = (1<<31) - 1; // never
  protected static final int DEFAULT_POLL_FREQUENCY = (128<<10)>>LOG_PAGE_SIZE;
  protected static final int DEFAULT_LOS_SIZE_THRESHOLD = 16 * 1024;
  public    static final int NON_PARTICIPANT = 0;
  protected static final boolean GATHER_WRITE_BARRIER_STATS = false;

  // Memory layout constants
  protected static final EXTENT        SEGMENT_SIZE = 0x10000000;
  protected static final int           SEGMENT_MASK = SEGMENT_SIZE - 1;
  public    static final VM_Address      BOOT_START = VM_Address.fromInt(VM_Interface.bootImageAddress);
  protected static final EXTENT           BOOT_SIZE = SEGMENT_SIZE;
  protected static final VM_Address  IMMORTAL_START = BOOT_START.add(BOOT_SIZE);
  protected static final EXTENT       IMMORTAL_SIZE = 32 * 1024 * 1024;
  protected static final VM_Address    IMMORTAL_END = IMMORTAL_START.add(IMMORTAL_SIZE);
  protected static final VM_Address META_DATA_START = IMMORTAL_END;
  protected static final EXTENT     META_DATA_SIZE  = 32 * 1024 * 1024;
  protected static final VM_Address   META_DATA_END = META_DATA_START.add(META_DATA_SIZE);  
  protected static final VM_Address      PLAN_START = META_DATA_END;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private int id = 0;                     // Zero-based id of plan instance
  public BumpPointer immortal;

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
    metaDataMR = new MemoryResource("meta", META_DATA_POLL_FREQUENCY);
    metaDataVM = new MonotoneVMResource(META_SPACE, "Meta data", metaDataMR, META_DATA_START, META_DATA_SIZE, VMResource.META_DATA);
    metaDataRPA = new RawPageAllocator(metaDataVM, metaDataMR);

    bootMR = new MemoryResource("boot", META_DATA_POLL_FREQUENCY);
    bootVM = new ImmortalVMResource(BOOT_SPACE, "Boot", bootMR, BOOT_START, BOOT_SIZE);

    immortalMR = new MemoryResource("imm", DEFAULT_POLL_FREQUENCY);
    immortalVM = new ImmortalVMResource(IMMORTAL_SPACE, "Immortal", bootMR, IMMORTAL_START, IMMORTAL_SIZE);

    addSpace(UNUSED_SPACE, "Unused");
    addSpace(BOOT_SPACE, "Boot");
    addSpace(META_SPACE, "Meta");
    addSpace(IMMORTAL_SPACE, "Immortal");
  }

  /**
   * Constructor
   */
  BasePlan() {
    id = planCount++;
    plans[id] = (Plan) this;
    immortal = new BumpPointer(immortalVM);
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public static void boot() throws VM_PragmaInterruptible {
    bootTime = VM_Interface.now();
  }

  /**
   * The boot method is called by the runtime immediately after
   * command-line arguments are available.  Note that allocation must
   * be supported prior to this point because the runtime
   * infrastructure may require allocation in order to parse the
   * command line arguments.  For this reason all plans should operate
   * gracefully on the default minimum heap size until the point that
   * boot is called.
   */
  public static void postBoot() {
    if (verbose > 2) VMResource.showAll();
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //

  protected byte getSpaceFromAllocator (Allocator a) {
    if (a == immortal) return IMMORTAL_SPACE;
    return UNUSED_SPACE;
  }

  protected Allocator getAllocatorFromSpace (byte s) {
    if (s == BOOT_SPACE) VM.sysFail("BasePlan.getAllocatorFromSpace given boot space");
    if (s == META_SPACE) VM.sysFail("BasePlan.getAllocatorFromSpace given meta space");
    if (s == IMMORTAL_SPACE) return immortal;
    VM.sysFail("BasePlan.getAllocatorFromSpace given unknown space");
    return null;
  }

  static Allocator getOwnAllocator (Allocator a) {
    byte space = UNUSED_SPACE;
    for (int i=0; i<plans.length && space == UNUSED_SPACE; i++)
      space = plans[i].getSpaceFromAllocator(a);
    if (space == UNUSED_SPACE)
      VM.sysFail("BasePlan.getOwnAllocator could not obtain space");
    Plan plan = VM_Interface.getPlan();
    return plan.getAllocatorFromSpace(space);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Object processing and tracing
  //

  /**
   * Add a gray object
   *
   * @param obj The object to be enqueued
   */
  public static final void enqueue(VM_Address obj)
    throws VM_PragmaInline {
    VM_Interface.getPlan().values.push(obj);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param objLoc The location containing the object reference to be
   * traced.  The object reference is <i>NOT</i> an interior pointer.
   * @param root True if <code>objLoc</code> is within a root.
   */
  public static final void traceObjectLocation(VM_Address objLoc, boolean root)
    throws VM_PragmaInline {
    VM_Address obj = VM_Magic.getMemoryAddress(objLoc);
    VM_Address newObj = Plan.traceObject(obj, root);
    VM_Magic.setMemoryAddress(objLoc, newObj);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.  This reference is presumed <i>not</i>
   * to be from a root.
   *
   * @param objLoc The location containing the object reference to be
   * traced.  The object reference is <i>NOT</i> an interior pointer.
   */
  public static final void traceObjectLocation(VM_Address objLoc)
    throws VM_PragmaInline {
    traceObjectLocation(objLoc, false);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @param interiorRef The interior reference inside obj that must be traced.
   * @param root True if the reference to <code>obj</code> was held in a root.
   * @return The possibly moved interior reference.
   */
  public static final VM_Address traceInteriorReference(VM_Address obj,
							VM_Address interiorRef,
							boolean root) {
    VM_Offset offset = interiorRef.diff(obj);
    VM_Address newObj = Plan.traceObject(obj, root);
    if (VM.VerifyAssertions) {
      if (offset.toInt() > (1<<24)) {  // There is probably no object this large
	VM.sysWriteln("ERROR: Suspiciously large delta of interior pointer from object base");
	VM.sysWriteln("       object base = ", obj);
	VM.sysWriteln("       interior reference = ", interiorRef);
	VM.sysWriteln("       delta = ", offset.toInt());
	VM._assert(false);
      }
    }
    return newObj.add(offset);
  }

  public static boolean willNotMove (VM_Address obj) {
      return !VMResource.refIsMovable(obj);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Read and write barriers.  By default do nothing, override if
  // appropriate.
  //

  /**
   * A new reference is about to be created by a putfield bytecode.
   * Take appropriate write barrier actions.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The address of the object containing the source of a
   * new reference.
   * @param offset The offset into the source object where the new
   * reference resides (the offset is in bytes and with respect to the
   * object address).
   * @param tgt The target of the new reference
   */
  public void putFieldWriteBarrier(VM_Address src, int offset,
				   VM_Address tgt){}

  /**
   * A new reference is about to be created by a aastore bytecode.
   * Take appropriate write barrier actions.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The address of the array containing the source of a
   * new reference.
   * @param index The index into the array where the new reference
   * resides (the index is the "natural" index into the array,
   * i.e. a[index]).
   * @param tgt The target of the new reference
   */
  public void arrayStoreWriteBarrier(VM_Address ref, int index, 
				     VM_Address value) {}

  /**
   * A new reference is about to be created by a putStatic bytecode.
   * Take appropriate write barrier actions.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param slot The location into which the new reference will be
   * stored (the address of the static field being stored into).
   * @param tgt The target of the new reference
   */
  public final void putStaticWriteBarrier(VM_Address slot, VM_Address tgt) {
    // putstatic barrier currently unimplemented
    if (VM.VerifyAssertions) VM._assert(false);
  }

  /**
   * A reference is about to be read by a getField bytecode.  Take
   * appropriate read barrier action.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param tgt The address of the object containing the pointer
   * about to be read
   * @param offset The offset from tgt of the field to be read from
   */
  public final void getFieldReadBarrier(VM_Address tgt, int offset) {
    // getfield barrier currently unimplemented
    if (VM.VerifyAssertions) VM._assert(false);
  }

  /**
   * A reference is about to be read by a getStatic bytecode. Take
   * appropriate read barrier action.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param slot The location from which the reference will be read
   * (the address of the static field being read).
   */
  public final void getStaticReadBarrier(VM_Address slot) {
    // getstatic barrier currently unimplemented
    if (VM.VerifyAssertions) VM._assert(false);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Space management
  //

  static public void addSpace (byte sp, String name) throws VM_PragmaInterruptible {
    if (spaceNames[sp] != null) VM.sysFail("addSpace called on already registed space");
    spaceNames[sp] = name;
  }

  static public String getSpaceName (byte sp) {
    if (spaceNames[sp] == null) VM.sysFail("getSpace called on unregisted space");
    return spaceNames[sp];
  }

  /**
   * Return the amount of <i>free memory</i>, in bytes (where free is
   * defined as not in use).  Note that this may overstate the amount
   * of <i>available memory</i>, which must account for unused memory
   * that is held in reserve for copying, and therefore unavailable
   * for allocation.
   *
   * @return The amount of <i>free memory</i>, in bytes (where free is
   * defined as not in use).
   */
  public static long freeMemory() throws VM_PragmaUninterruptible {
    return totalMemory() - usedMemory();
  }

  /**
   * Return the amount of <i>memory in use</i>, in bytes.  Note that
   * this includes unused memory that is held in reserve for copying,
   * and therefore unavailable for allocation.
   *
   * @return The amount of <i>memory in use</i>, in bytes.
   */
  public static long usedMemory() throws VM_PragmaUninterruptible {
    return Conversions.pagesToBytes(Plan.getPagesUsed());
  }

  /**
   * Return the total amount of memory managed to the memory
   * management system, in bytes.
   *
   * @return The total amount of memory managed to the memory
   * management system, in bytes.
   */
  public static long totalMemory() throws VM_PragmaUninterruptible {
    return Options.initialHeapSize;
  }

  /**
   * Return the total amount of memory managed to the memory
   * management system, in pages.
   *
   * @return The total amount of memory managed to the memory
   * management system, in pages.
   */
  public static int getTotalPages() throws VM_PragmaUninterruptible { 
    int heapPages = Conversions.bytesToPages(Options.initialHeapSize);
    return heapPages; 
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //

  /**
   * This method should be called whenever an error is encountered.
   *
   * @param str A string describing the error condition.
   */
  public void error(String str) {
    MemoryResource.showUsage(PAGES);
    MemoryResource.showUsage(MB);
    VM.sysFail(str);
  }

  /**
   * Return true if a collection is in progress.
   *
   * @return True if a collection is in progress.
   */
  static public boolean gcInProgress() {
    return gcInProgress;
  }

  /**
   * Return the GC count (the count is incremented at the start of
   * each GC).
   *
   * @return The GC count (the count is incremented at the start of
   * each GC).
   */
  public static int gcCount() { 
    return Statistics.gcCount;
  }

  /**
   * Return the <code>RawPageAllocator</code> being used.
   *
   * @return The <code>RawPageAllocator</code> being used.
   */
  public static RawPageAllocator getMetaDataRPA() {
    return metaDataRPA;
  }

  /**
   * The VM is about to exit.  Perform any clean up operations.
   *
   * @param value The exit value
   */
  public void notifyExit(int value) {
    if (verbose == 1) {
      // VM.sysWrite("[End ", (VM_Interface.now() - bootTime));
      // VM.sysWrite(" s]\n");
    }
  }



  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //

  final static int PAGES = 0;
  final static int MB = 1;
  final static int PAGES_MB = 2;
  final static int MB_PAGES = 3;

  /**
   * Print out the number of pages and or megabytes, depending on the mode.
   * A prefix string is outputted first.
   *
   * @param prefix A prefix string
   * @param pages The number of pages
   */
  public static void writePages(int pages, int mode) {
    double mb = Conversions.pagesToBytes(pages) / (1024.0 * 1024.0);
    switch (mode) {
      case PAGES: VM.sysWrite(pages, " pgs"); break; 
      case MB:    VM.sysWrite(mb, " Mb"); break;
      case PAGES_MB: VM.sysWrite(pages, " pgs ("); VM.sysWrite(mb, " Mb)"); break;
      case MB_PAGES: VM.sysWrite(mb, " Mb ("); VM.sysWrite(pages, " pgs)"); break;
      default: VM.sysFail("writePages passed illegal printing mode");
    }
  }




}
