/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 * (C) Copyright IBM Corp. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.classloader.VM_Array;

/**
 * This class implements a virtual memory resource.  The unit of
 * managment for virtual memory resources is the <code>PAGE</code><p>
 *
 * Instances of this class each manage a contigious region of virtual
 * memory.  The class's static methods and fields coordinate to ensure
 * coherencey among VM resource requests (i.e. that they do not
 * overlap). 
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */

public abstract class VMResource implements Constants, VM_Uninterruptible {

  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public static variables and methods
  //
  public static final byte NOT_IN_VM = 0;   // 00000000
  public static final byte IN_VM     = 1;   // 00000001
  public static final byte IMMORTAL  = 2;   // 00000010
  public static final byte MOVABLE   = 4;   // 00000100
  public static final byte META_DATA = -128; // 10000000


  public static void showAll () {
    for (int vmr = 0; vmr < count; vmr++) {
      VM.sysWrite("VMResource ");
      VM.sysWrite(vmr); VM.sysWrite(" ");
      VM.sysWrite(resources[vmr].start); VM.sysWrite(" ");
      VM.sysWrite(resources[vmr].end); VM.sysWrite(" ");
      VM.sysWriteln(resources[vmr].name);
    }
  }

  public static boolean refIsMovable (VM_Address obj) {
    VM_Address addr = VM_Interface.refToAddress(VM_Magic.objectAsAddress(obj));
    return (getPageStatus(addr) & MOVABLE) == MOVABLE;
  }

  public static boolean refInVM(VM_Address ref) throws VM_PragmaUninterruptible {
    return addrInVM(VM_Interface.refToAddress(ref));
  }

  public static boolean addrInVM(VM_Address addr) throws VM_PragmaUninterruptible {
    return (getPageStatus(addr) & IN_VM) == IN_VM;
  }

  public static boolean refIsImmortal(VM_Address ref) throws VM_PragmaUninterruptible {
    return addrIsImmortal(VM_Interface.refToAddress(ref));
  }

  public static boolean addrIsImmortal(VM_Address addr) throws VM_PragmaUninterruptible {
    return (getPageStatus(addr) & IMMORTAL) == IMMORTAL;
  }

  public static int getMaxVMResource() {
    return MAX_VMRESOURCE;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private static methods and variables
  //
  private static VMResource resourceTable[]; // Points to corresponding VM resource.  null if no corresponding VM resource.
  private static byte spaceTable[];          // Status of each page
  private static byte tagTable[];            // Space-specific information of each page
  private static int count;                  // How many VMResources exist now?
  private static VMResource resources[];     // List of all VMResources.
  final private static int MAX_VMRESOURCE = 20;
  final private static int NUM_PAGES = 1 << (LOG_ADDRESS_SPACE - LOG_PAGE_SIZE);

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {
    resources = new VMResource[MAX_VMRESOURCE];
    spaceTable = new byte[NUM_PAGES];
    tagTable = new byte[NUM_PAGES];
    for (int blk = 0; blk < NUM_PAGES; blk++) 
      spaceTable[blk] = Plan.UNUSED_SPACE;
  }

  public static void boot() throws VM_PragmaInterruptible {
    // resourceTable = new VMResource[NUM_PAGES];
    VM_Array type = VM_Magic.getObjectType(resources).asArray();
    Object [] tib = type.getTypeInformationBlock();
    int size = type.getInstanceSize(NUM_PAGES);
    resourceTable = (VMResource []) VM_Interface.allocateArray(NUM_PAGES, size, tib, Plan.IMMORTAL_SPACE);
    for (int i=0; i<resources.length; i++) {
      VMResource vm = resources[i];
      if (vm == null) continue;
      int startPage = Conversions.addressToPagesDown(vm.start);
      for (int p = startPage; p < (startPage + vm.pages); p++) {
	if (resourceTable[p] != null) {
	  VM.sysWrite("Conflicting VMResource: ", vm.name);
	  VM.sysWriteln(" and ", resourceTable[p].name);
	  VM.sysFail("Conflicting VMResource");
	}
	resourceTable[p] = vm;
      }
    }
    int bootSize = VM_Interface.bootImageEnd().diff(VM_Interface.bootImageStart()).toInt();
    Plan.bootVM.acquireHelp(BasePlan.BOOT_START, Conversions.bytesToPagesUp(bootSize));
  }

  public static VMResource resourceForPage(VM_Address addr) {
    if (resourceTable == null)
      VM.sysFail("resourceForBlock called when resourceTable is null");
    return resourceTable[addr.toInt() >>> LOG_PAGE_SIZE];
  }

  public static byte getPageStatus(VM_Address addr) {
    VMResource vm = resourceForPage(addr);
    if (vm == null) return NOT_IN_VM;
    return vm.status;
  }

  final public static byte getSpace(VM_Address addr) throws VM_PragmaInline {
    if (VM.VerifyAssertions) {
	if (spaceTable == null)
	  VM.sysFail("getSpace called when spaceTable is null");
	return spaceTable[addr.toInt() >>> LOG_PAGE_SIZE];
    }
    return VM_Magic.getByteAtOffset(VM_Magic.objectAsAddress(spaceTable), 
				    addr.toInt() >>> LOG_PAGE_SIZE);
  }

  public static byte getTag (VM_Address addr) {
    int page =  addr.toInt() >>> LOG_PAGE_SIZE;
    return tagTable[page];
  }

  public static void setTag (VM_Address addr, int pages, byte v) {
    int start =  addr.toInt() >>> LOG_PAGE_SIZE;
    for (int i=0; i<pages; i++)
	tagTable[start+i] = v;
  }

  public static void clearTag (VM_Address addr, int pages, byte v) {
    int start =  addr.toInt() >>> LOG_PAGE_SIZE;
    for (int i=0; i<pages; i++) {
	if (tagTable[start+i] != v)
	    VM.sysFail("VMResource.clearTag: current tag does not match expected value");
	tagTable[start+i] = (byte) 0;
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //
  /**
   * Constructor
   */
  VMResource(byte space_, String vmName, VM_Address vmStart, EXTENT bytes, byte status_) {
    space = space_;
    start = vmStart;
    pages = Conversions.bytesToPages(bytes);
    end = start.add(bytes);
    name = vmName;
    index = count++;
    resources[index] = this;
    status = status_;
    VM_Interface.setHeapRange(index, start, end);
    if (end.GT(VM_Interface.MAXIMUM_MAPPABLE)) {
      VM.sysWrite("\nError creating VMResrouce ", vmName);
      VM.sysWriteln(" with range ", start, " to ", end);
      VM.sysWriteln("Exceeds the maximum mappable address for this OS of ", VM_Interface.MAXIMUM_MAPPABLE);
      VM._assert(false);
    }
  }

  /**
   * Acquire a number of contigious blocks from the virtual memory resource.
   *
   * @param request The number of pages requested
   * @return The address of the start of the virtual memory region, or
   * zero on failure.
   */
  public abstract VM_Address acquire(int request);
  public abstract VM_Address acquire(int request, MemoryResource mr);
  
  protected void acquireHelp (VM_Address start, int pageRequest) {
    if (!VM.runningVM) VM.sysFail("VMResource.acquireHelp called before VM is running");
    if (spaceTable == null) 
	VM.sysFail("VMResource.acquireHelp called when spaceTable is still empty");
    int pageStart = Conversions.addressToPages(start);
    // VM.sysWrite("Acquiring pages ", pageStart);
    // VM.sysWrite(" to ", pageStart + pageRequest - 1);
    // VM.sysWriteln(" for space ", space);
    for (int i=0; i<pageRequest; i++) {
      if (VM.VerifyAssertions) 
	  VM._assert(spaceTable[pageStart+i] == Plan.UNUSED_SPACE ||
		     spaceTable[pageStart+i] == space);  // Suspect - FreeListVM
      spaceTable[pageStart+i] = space;
    }
  }

  protected void releaseHelp (VM_Address start, int pageRequest) {
    if (!VM.runningVM) VM.sysFail("VMResource.releaseHelp called before VM is running");
    int pageStart = Conversions.addressToPages(start);
    // VM.sysWrite("Releasing pages ", pageStart);
    // VM.sysWrite(" to ", pageStart + pageRequest - 1);
    // VM.sysWriteln(" for space ", space);
    for (int i=0; i<pageRequest; i++) {
      if (VM.VerifyAssertions) 
	  VM._assert(spaceTable[pageStart+i] == space ||
		     spaceTable[pageStart+i] == Plan.UNUSED_SPACE); // Suspect - FreeListVM
      spaceTable[pageStart+i] = Plan.UNUSED_SPACE;
    }
  }

  public final int getPages() { return pages; }

  public final VM_Address getStart() { return start; }
  public final VM_Address getEnd() { return end; }
  public final boolean inRange(VM_Address s) { return (start.LE(s) && s.LT(end)); }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private fields and methods
  //
  final private int index;
  final private byte space;
  final protected String name;
  private byte status;
  protected VM_Address start;
  protected VM_Address end;
  private int pages;
}
