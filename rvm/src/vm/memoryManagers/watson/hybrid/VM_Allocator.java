/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Hybrid Generational Collector/Allocator with Fixed Size Nursery
 * <p>
 * The hybrid collector divides the heap into 2 spaces, a small Nursery
 * region where objects are allocated, and a Mature Space for objects
 * that survive one or more collections. The Nursery is managed using the
 * code from the copying generational collectors. The Mature Space is
 * managed using the code from the non-copying mark-sweep collector.
 * <p>
 * Minor collections of the Nursery are performed like Minor collections
 * in the copying generational collectors, except that space for objects
 * being copied into the Mature Space is obtained using the allocate
 * routines of the mark-sweep allocator.  Major collections are performed
 * using the mark-sweep code.  Major collections are triggerred when
 * the number of free blocks in Mature Space falls below a threshold.
 * <p>
 * The nursery size can be set on the command line by specifying
 * "-X:nh=xxx" where xxx is the nursery size in megabytes.  The nursery size
 * is subtracted from the small object heap size (-X:h=xxx) and the remainder
 * becomes the Mature Space.
 * <pre>
 * Small Object Heap Layout: 
 *  + ------------+----------------------------------------------+---------+
 *  | BootImage   |     (non-copying) Mature Space               | Nursery |
 *  +-------------+----------------------------------------------+---------+
 *        heapStart^                                                 heapEnd^
 * </pre>
 * The Hybrid collector uses the default RVM writebarrier
 * which puts references to objects, which had internal references
 * modified, into processor local writebuffers.  For minor collections, objects in
 * the writebuffers become part of the root set for the collection.
 * (The RVM compilers generate the barrier code when the static final
 * constant "writeBarrier" is set to true.)
 * 
 * @see vm/allocator/copyGCgenSmallN/VM_Allocator
 * @see vm/allocator/noncopyingGC/VM_Allocator
 * @see VM_GCWorkQueue
 * @see VM_WriteBuffer
 * @see VM_CollectorThread
 *
 * @author Dick Attanasio
 * @author Tony Cocchi
 * @author Stephen Smith
 */  
public class VM_Allocator
  implements VM_Constants, VM_GCConstants, VM_Uninterruptible, VM_Callbacks.ExitMonitor {

  static final boolean GCDEBUG_PARTIAL = false;
  static final boolean DEBUG_FREEBLOCKS = false;

  /** When true, print heap configuration when starting */
  static final boolean DISPLAY_OPTIONS_AT_BOOT = VM_CollectorThread.DISPLAY_OPTIONS_AT_BOOT;
  
  /**
   * When true, causes time spent in each phase of collection to be measured.
   * Forces summary statistics to be generated. See VM_CollectorThread.TIME_GC_PHASES.
   */
  static final boolean TIME_GC_PHASES  = VM_CollectorThread.TIME_GC_PHASES;

  /**
   * When true, causes each gc thread to measure accumulated wait times
   * during collection. Forces summary statistics to be generated.
   * See VM_CollectorThread.MEASURE_WAIT_TIMES.
   */
  static final boolean RENDEZVOUS_WAIT_TIME = VM_CollectorThread.MEASURE_WAIT_TIMES;

  /** count times parallel GC threads attempt to mark the same object */
  private static final boolean COUNT_COLLISIONS = true;

  /**
   * Initialize for boot image - executed when bootimage is being build
   */
  static  void
    init () {
    int i, ii;

    if ( writeBarrier == false ) {
      VM.sysWrite("VM_Allocator: VM MUST BE COMPILED WITH writeBarrier=true\n");
      VM.shutdown(-5);
    }

    VM_GCLocks.init();	
    VM_GCWorkQueue.init();       // to alloc shared work queue0
    VM_CollectorThread.init();    // to alloc its rendezvous arrays, if necessary

    partialBlockList = new int[GC_SIZES];          // GSC

		for (i = 0; i < GC_SIZES; i++) partialBlockList[i] = OUT_OF_BLOCKS;

    VM_Processor st = VM_Scheduler.processors[VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];

    sysLockLarge          = new VM_ProcessorLock();
    sysLockBlock	      = new VM_ProcessorLock();

    st.sizes = new VM_SizeControl[GC_SIZES];
    init_blocks = new VM_BlockControl[GC_SIZES];

    // On the jdk side, we allocate an array of VM_SizeControl Blocks, 
    // one for each size.
    // We also allocate init_blocks array within the boot image.  
    // At runtime we allocate the rest of the BLOCK_CONTROLS, whose number 
    // depends on the heapsize, and copy the contents of init_blocks 
    // into the first GC_SIZES of them.

    for (i = 0; i < GC_SIZES; i++) {
      st.sizes[i] = new VM_SizeControl();
      init_blocks[i] = new VM_BlockControl();
      st.sizes[i].first_block = i;	// 1 block/size initially
      st.sizes[i].current_block = i;
      st.sizes[i].ndx = i;
      init_blocks[i].mark = new byte[GC_BLOCKSIZE/GC_SIZEVALUES[i] ];
      for (ii = 0; ii < GC_BLOCKSIZE/GC_SIZEVALUES[i]; ii++) {
	init_blocks[i].mark[ii]  = 0;
      }
      init_blocks[i].nextblock = OUT_OF_BLOCKS;
      init_blocks[i].slotsize = GC_SIZEVALUES[i];
    }

    // set up GC_INDEX_ARRAY for this Processor
    st.GC_INDEX_ARRAY = new VM_SizeControl[GC_MAX_SMALL_SIZE + 1];
    st.GC_INDEX_ARRAY[0] = st.sizes[0];   // for size = 0
    int j = 1;
    for (i = 0; i < GC_SIZES; i++) 
      for (; j <= GC_SIZEVALUES[i]; j++) st.GC_INDEX_ARRAY[j] = st.sizes[i];

    countLargeAlloc = new int[GC_LARGE_SIZES];
    countLargeLive  = new int[GC_LARGE_SIZES];
    countSmallFree = new int[GC_SIZES];
    countSmallBlocksAlloc = new int[GC_SIZES];

    for (i = 0; i < GC_LARGE_SIZES; i++) {
      countLargeAlloc[i]   = 0;
      countLargeLive[i]    = 0;
    }

    largeSpaceAlloc = new short[GC_INITIAL_LARGE_SPACE_PAGES];
    for (i = 0; i < GC_INITIAL_LARGE_SPACE_PAGES; i++)
      largeSpaceAlloc[i] = 0;
    large_last_allocated = 0;
    largeSpacePages = GC_INITIAL_LARGE_SPACE_PAGES;
    largeSpaceHiWater = 0;
	
  }   // init

  /**
   * Initialize for execution - executed when VM starts up.
   */
  static void
    boot (VM_BootRecord thebootrecord) {
    int i;
    int blocks_storage, blocks_array_storage;

    VM_Processor st = VM_Scheduler.processors[VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
    blocks = VM_Magic.addressAsIntArray(VM_Magic.objectAsAddress(init_blocks));

    bootrecord = thebootrecord;	
    // now in bootrecord, set by -nh command line arg
    nurserySize = bootrecord.nurserySize;
    nurseryEndAddress = bootrecord.endAddress;
    nurseryStartAddress = nurseryEndAddress - nurserySize;   // should be page boundary
    if (VM.VerifyAssertions) VM.assert( (nurseryStartAddress & 4095) == 0);

    // set bounds of possible FromSpace refs (of objects to be copied)
    minNurseryRef = nurseryStartAddress - OBJECT_HEADER_OFFSET;
    maxNurseryRef = nurseryEndAddress + 4;

    fromStartAddress = nurseryStartAddress;
    fromEndAddress = nurseryEndAddress;

    // set pointers used for atomic allocate of Chunks from Nursery
    areaCurrentAddress = nurseryStartAddress;
    areaEndAddress     = nurseryEndAddress;

    // need address of areaCurrentAddress (in JTOC) for atomic fetchAndAdd()
    // when JTOC moves, this must be reset
    // offset of areaCurrentAddress in JTOC is set (in JDK side) in VM_EntryPoints
    addrAreaCurrentAddress = VM_Magic.getTocPointer() + VM_Entrypoints.areaCurrentAddressOffset;

    // first ref in bootimage
    minBootRef = bootrecord.startAddress-OBJECT_HEADER_OFFSET;   
    maxBootRef = bootrecord.freeAddress+4;      // last ref in bootimage

    bootStartAddress = bootrecord.startAddress;   // start of boot image
    bootEndAddress = bootrecord.freeAddress;      // end of boot image
    smallHeapStartAddress = ((bootEndAddress + GC_BLOCKALIGNMENT - 1)/
			     GC_BLOCKALIGNMENT)*GC_BLOCKALIGNMENT;
    smallHeapEndAddress = (nurseryStartAddress/GC_BLOCKALIGNMENT)*
      GC_BLOCKALIGNMENT;
    smallHeapSize = smallHeapEndAddress - smallHeapStartAddress;
    minSmallHeapRef = smallHeapStartAddress - OBJECT_HEADER_OFFSET;
    maxSmallHeapRef = smallHeapEndAddress + 4;

    // when preparing for a timing run touch all the pages in the Nursery
    // and Small Object Heap, to avoid overhead of page fault
    // during the timeing run
    if (COMPILE_FOR_TIMING_RUN) 
      for (i = nurseryEndAddress - 4096; i >= smallHeapStartAddress; i = i - 4096)
	VM_Magic.setMemoryWord(i, 0);

    largeHeapStartAddress = bootrecord.largeStart;
    largeHeapEndAddress = bootrecord.largeStart + bootrecord.largeSize;
    largeHeapSize = largeHeapEndAddress - largeHeapStartAddress;
    minLargeRef = largeHeapStartAddress-OBJECT_HEADER_OFFSET;   // first ref in large space
        
    // check for inconsistent heap & nursery sizes
    if (smallHeapSize <= nurserySize) {
      VM.sysWrite("\nNursery size is too large for the specified Heap size:\n");
      VM.sysWrite("  Small Object Heap Size = ");
      VM.sysWrite(smallHeapSize,false); VM.sysWrite("\n");
      VM.sysWrite("  Nursery Size = ");
      VM.sysWrite(nurserySize,false); VM.sysWrite("\n");
      VM.sysWrite("Use -X:h=nnn & -X:nh=nnn to specify a heap size at least twice as big as the nursery\n");
      VM.sysWrite("Remember, the nursery is subtracted from the specified heap size\n");
      VM.shutdown(-5);
    }

    // detect if Large Heap Size has made largeHeapStartAddress go negative
    // ie. extends into segment 8. (Temporarily we allow the "end" address
    // to go negative...by removing all compares of refs to largeHeapEndAddress)
    if (VM.VerifyAssertions) VM.assert(largeHeapStartAddress > 0);

    // Now set the beginning address of each block into each VM_BlockControl
    // Note that init_blocks is in the boot image, but heap pages are controlled by it

    for (i =0; i < GC_SIZES; i++)  {
      init_blocks[i].baseAddr = smallHeapStartAddress + i * GC_BLOCKSIZE;
      build_list_for_new_block(init_blocks[i], st.sizes[i]);
    }

    // Get the three arrays that control large object space
    short[] temp    = new short[bootrecord.largeSize/4096 + 1];
    largeSpaceMark  = new short[bootrecord.largeSize/4096 + 1];
    largeSpaceGen   = new byte[bootrecord.largeSize/4096 + 1];

    for (i = 0; i < GC_INITIAL_LARGE_SPACE_PAGES; i++)
      temp[i] = largeSpaceAlloc[i];

    // At this point temp contains the up-to-now allocation information
    // for large objects; so it now becomes largeSpaceAlloc
    largeSpaceAlloc = temp;
    largeSpacePages = bootrecord.largeSize/4096;


    // At this point it is possible to allocate 
    // (1 GC_BLOCKSIZE worth of )objects foreach size

    // Now allocate the blocks array - which will be used to allocate blocks to sizes

    num_blocks = smallHeapSize/GC_BLOCKSIZE;
    large_last_allocated = 0;
    //	blocks     = new VM_BlockControl[num_blocks];

    // set free block count for triggering major collection
    majorCollectionThreshold = nurserySize/GC_BLOCKSIZE;

    //      GET STORAGE FOR BLOCKS ARRAY FROM OPERATING SYSTEM
    if ((blocks_array_storage = VM.sysCall1(bootrecord.sysMallocIP,
					    //      storage for entries in blocks array: 4 bytes/ ref
					    num_blocks * 4 + ARRAY_HEADER_SIZE)) == 0) {
      VM.sysWrite(" In boot, call to sysMalloc returned 0 \n");
      VM.shutdown(1800);
    }

    if ((blocks_storage = VM.sysCall1(bootrecord.sysMallocIP,
				      (num_blocks-GC_SIZES) * VM_BlockControl.Size)) == 0) {
      VM.sysWrite(" In boot, call to sysMalloc returned 0 \n");
      VM.shutdown(1900);
    }

    blocks = makeArrayFromStorage(blocks_array_storage,
				  VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(blocks) + OBJECT_TIB_OFFSET),
				  num_blocks);

    // index for highest page in heap
    highest_block = num_blocks -1;
    blocks_available = highest_block - GC_SIZES; 	// available to allocate
	
    // Now fill in blocks with values from blocks_init
    for (i = 0; i < GC_SIZES; i++) {
      // NOTE: if blocks are identified by index, st.sizes[] need not be changed; if
      // 	blocks are identified by address, then updates st.sizes[0-GC_SIZES] here
      blocks[i]        = VM_Magic.objectAsAddress(init_blocks[i]);
      // make sure it survives the first collection
    }

    // At this point we have assigned the first GC_SIZES blocks, 
    // 1 per, to each GC_SIZES bin
    // and are prepared to allocate from such, or from large object space:
    // large objects are allocated from the top of the heap downward; 
    // small object blocks are allocated from the bottom of the heap upward.  
    // VM_BlockControl blocks are not used to manage large objects - 
    // they are unavailable by special logic for allocation of small objs
    first_freeblock = GC_SIZES;	// next to be allocated
    init_blocks 	= null;		// these are currently live through blocks

    // Now allocate the rest of the VM_BlockControls
    for (i = GC_SIZES; i < num_blocks; i++) {
      blocks[i] = makeObjectFromStorage(blocks_storage + 
					(i - GC_SIZES) * VM_BlockControl.Size,
					VM_Magic.getMemoryWord(blocks[0]
							       + OBJECT_TIB_OFFSET), VM_BlockControl.Size);
      VM_Magic.addressAsBlockControl(blocks[i]).baseAddr = 
	smallHeapStartAddress + i * GC_BLOCKSIZE; 
      VM_Magic.addressAsBlockControl(blocks[i]).nextblock = i + 1;
      // New logic: set alloc pointer = 0 here
      VM_Magic.addressAsBlockControl(blocks[i]).mark = null;
		
    }
	
    VM_Magic.addressAsBlockControl(blocks[num_blocks -1]).nextblock = OUT_OF_BLOCKS;
	
    // create synchronization objects
    sysLockFree	      = new VM_ProcessorLock();

    arrayOfIntType = VM_Array.getPrimitiveArrayType( 10 /*code for INT*/ ); // XXX

    VM_GCUtil.boot();

    // create the finalizer object
    VM_Finalizer.setup();

    total = new int[GC_SIZES];   // for reportBlocks
    accum = new int[GC_SIZES];   // for reportBlocks

    VM_Callbacks.addExitMonitor(new VM_Allocator());

    if (DISPLAY_OPTIONS_AT_BOOT) {
      VM.sysWrite("\nGenerational Hybrid Collector/Allocator\n");
      VM.sysWrite("\n   Nursery Size           = "); VM.sysWrite(nurserySize);
      VM.sysWrite("\n   Fixed/Old Heap Size    = "); VM.sysWrite(smallHeapSize);
      VM.sysWrite("\n   Large Object Heap Size = "); VM.sysWrite(largeHeapSize);
      VM.sysWrite("\n\n");
      VM.sysWrite(bootStartAddress);
      VM.sysWrite("  is the bootStartAddress \n");
      VM.sysWrite(bootEndAddress);
      VM.sysWrite("  is the bootEndAddress \n");
      VM.sysWrite(smallHeapStartAddress);
      VM.sysWrite("  is smallHeapStartAddress \n");
      VM.sysWrite(smallHeapEndAddress);
      VM.sysWrite("  is the smallHeapEndAddress \n");
      VM.sysWrite(nurseryStartAddress);
      VM.sysWrite("  is nurseryStartAddress \n");
      VM.sysWrite(nurseryEndAddress);
      VM.sysWrite("  is the nurseryEndAddress \n");
      VM.sysWrite(largeHeapStartAddress);
      VM.sysWrite("  is the largeHeapStartAddress \n");
      VM.sysWrite(largeHeapEndAddress);
      VM.sysWrite("  is the largeHeapEndAddress \n\n");
      VM.sysWrite(VM_GCWorkQueue.WORK_BUFFER_SIZE);
      VM.sysWrite("  is the WORK QUEUE buffer size\n");
    }

  }  // boot()

  /**
   * To be called when the VM is about to exit.
   * @param value the exit value
   */
  public void notifyExit(int value) {
    printSummaryStatistics();
  }

  /**
   * Force a garbage collection. Supports System.gc() called from
   * application programs.
   */
  public static void
    gc ()  {
    if (GC_TRIGGERGC)
      VM_Scheduler.trace(" gc triggered by external call to gc()", "EXTERNAL GC()");
    gc1();
  }

  /**
   * VM internal method to initiate a collection
   */
  public static void
    gc1 () {

    double time;

    // if here and in a GC thread doing GC then it is a system error, insufficient
    // extra space for allocations during GC
    if ( VM_Thread.getCurrentThread().isGCThread ) {
      VM.sysFail("VM_Allocator: Garbage Collection Failure: GC Thread attempting to allocate during GC");
    }

    // notify GC threads to initiate collection, wait until done
    VM_CollectorThread.collect(VM_CollectorThread.collect);

  }  // gc1

  /**
   * Get total amount of memory.  Includes both full size of the
   * small object heap and the size of the large object heap.
   *
   * @return the number of bytes
   */
  public static long
    totalMemory () {
    return smallHeapSize + largeHeapSize + nurserySize;
  }

  /**
   * Get the number of bytes currently available for object allocation.
   * In this collector, returns bytes available in the current semi-space.
   * (Does include space available in large object space.)
   *
   * @return number of bytes available
   */
  public static long
    freeMemory () {

    total_blocks_in_use = 0;
    long total = 0;
    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) 
      total = total + freeSmallSpace(VM_Scheduler.processors[i]);
      
    return (freeLargeSpace() + total + (highest_block - total_blocks_in_use) * 
	    GC_BLOCKSIZE + (areaEndAddress - areaCurrentAddress));
  }  // freeMemory

  // START NURSERY ALLOCATION ROUTINES HERE 

  /**
   * Print OutOfMemoryError message and exit.
   * TODO: make it possible to throw an exception, but this will have
   * to be done without doing further allocations (or by using temp space)
   */
  private static void
    outOfMemory () {

    // First thread to be out of memory will write out the message,
    // and issue the shutdown. Others just spinwait until the end.

    sysLockLarge.lock();
    if (!outOfMemoryReported) {
      outOfMemoryReported = true;
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      VM.sysWrite("\nOutOfMemoryError\n");
      VM.sysWrite("Insufficient heap size for hybrid collector\n");
      VM.sysWrite("Could not complete a minor collection\n");
      VM.sysWrite("Current heap size = ");
      VM.sysWrite(smallHeapSize, false);
      VM.sysWrite("\nSpecify a larger heap using -X:h=nnn command line argument\n");
      // call shutdown while holding the processor lock
      VM.shutdown(-5);
    }
    else {
      sysLockLarge.release();
      while( outOfMemoryReported == true );  // spin until VM shuts down
    }
  }

  /**
   * Print OutOfMemoryError message and exit.
   * TODO: make it possible to throw an exception, but this will have
   * to be done without doing further allocations (or by using temp space)
   */
  private static void
    outOfLargeSpace ( int size ) {

    // First thread to be out of memory will write out the message,
    // and issue the shutdown. Others just spinwait until the end.

    sysLockLarge.lock();
    if (!outOfMemoryReported) {
      outOfMemoryReported = true;
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      VM.sysWrite("\nOutOfMemoryError - Insufficient Large Object Space\n");
      VM.sysWrite("Unable to allocate large object of size = ");
      VM.sysWrite(size, false);
      VM.sysWrite("\nCurrent Large Space Size = ");
      VM.sysWrite(largeHeapSize, false);
      VM.sysWrite("\nSpecify a bigger large object heap using -X:lh=nnn command line argument\n");
      // call shutdown while holding the processor lock
      VM.shutdown(-5);
    }
    else {
      sysLockLarge.release();
      while( outOfMemoryReported == true );  // spin until VM shuts down
    }
  }

  /**
   * Get space for new object or array from heap (Nursery or Large Space).  invoke GC if necessary.
   * Will get Chunks from the Nursery for the executing processor,if necessary,
   * and will request GC if no Nursery Chunks are available.
   *
   * @param size  size of space needed in bytes
   *
   * @return      the address of first byte of allocated area
   */
  public static int
    getHeapSpace ( int size ) {

    int addr;
    VM_Thread t;

    if (VM.VerifyAssertions) {
      t = VM_Thread.getCurrentThread();
      VM.assert( gcInProgress == false );
      VM.assert( (t.disallowAllocationsByThisThread == false) && ((size & 3) == 0) );
    }

    // if large, allocate from large object space
    if (size > SMALL_SPACE_MAX) {
      addr = getlargeobj(size);
      if (addr == -2) {  // insufficient large space, try a GC
	if (GC_TRIGGERGC) VM_Scheduler.trace("VM_Allocator","GC triggered by large object request",size);
	outOfLargeSpaceFlag = true;  // forces a major collection to reclaim more large space
	gc1();
	addr = getlargeobj(size);     // try again after GC
	if ( addr == -2 ) {
	  // out of space...REALLY...or maybe NOT ?
	  // maybe other user threads got the free space first, after the GC
	  //
	  outOfLargeSpace( size );
	}
      }
      return addr;
    }  // end of - (size > SMALL_SPACE_MAX)

    // now handle normal allocation of small objects in heap

    VM_Processor st = VM_Processor.getCurrentProcessor();
    if ( (st.localCurrentAddress + size ) <= st.localEndAddress ) {
      addr = st.localCurrentAddress;
      st.localCurrentAddress = st.localCurrentAddress + size;
    }
    else { // not enough space in local chunk, get the next chunk for allocation
      addr = VM_Synchronization.fetchAndAddWithBound(VM_Magic.addressAsObject(addrAreaCurrentAddress), 0, CHUNK_SIZE, areaEndAddress );
      if ( addr != -1 ){
	st.localEndAddress = addr + CHUNK_SIZE;
	st.localCurrentAddress = addr + size;
	VM_Memory.zeroPages(addr,CHUNK_SIZE);
      }
      else { // no space in system thread and no more chunks, do garbage collection
	if (GC_TRIGGERGC) VM_Scheduler.trace("VM_Allocator","GC triggered by request for CHUNK");
	gc1();
	
	// retry request for space
	// NOTE! may now be running on a DIFFERENT SYSTEM THREAD than before GC
	//
	st = VM_Processor.getCurrentProcessor();
	if ( (st.localCurrentAddress + size ) <= st.localEndAddress ) {
	  addr = st.localCurrentAddress;
	  st.localCurrentAddress = st.localCurrentAddress + size;
	}
	else {
	  // not enough space in local chunk, get the next chunk for allocation
	  //
	  addr = VM_Synchronization.fetchAndAddWithBound(VM_Magic.addressAsObject(addrAreaCurrentAddress), 0, CHUNK_SIZE, areaEndAddress );
	  if ( addr != -1 ){
	    st.localEndAddress = addr + CHUNK_SIZE;
	    st.localCurrentAddress = addr + size;
	    VM_Memory.zeroPages(addr,CHUNK_SIZE);
	  }
	  else {  // unable to get chunk, after GC, so throw outOfMemoryError
	    // maybe should retyr GC again, some number of times
	    VM_Scheduler.trace("VM_Allocator.getHeapSpace:","Could Not Get Allocation Buffer After GC\n");
	    VM.shutdown(-5);
	  } 
	}
      }  // else do gc
    }  // else get new chunk from global heap

    // addr -> beginning of allocated region
    return addr;
  }  // getHeapSpace

  /**
   * Allocate a scalar object. Fills in the header for the object,
   * and set all data fields to zero.
   *
   * @param size         size of object (including header), in bytes
   * @param tib          type information block for object
   * @param hasFinalizer hasFinalizer flag
   *
   * @return the reference for the allocated object
   */
  public static Object
    allocateScalar (int size, Object[] tib, boolean hasFinalizer)
    throws OutOfMemoryError {
    
    Object new_ref;
    
    VM_Magic.pragmaInline();	// make sure this method is inlined
    
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
      VM_EventLogger.logObjectAllocationEvent();
    
    // assumption: collector has previously zero-filled the space
    // assumption: object sizes are always a word multiple,
    // so we don't need to worry about address alignment or rounding
    //
    //  |<--------------------size---------------->|
    //  .                            |<--hdr size->|
    //  .                            |<--- hdr offset--->|
    //  +-------------------+--------+------+------+-----+-----+
    //  |         ...field1 | field0 | tib  |status| free| free|
    //  +-------------------+--------+------+------+-----+-----+
    //                      (new) areaCurrentAddress^     ^new_ref
    //   ^(prevoius) areaCurrentAddress
    
    // always use processor local "chunks", assume size is "small" and attempt to
    // allocate locally, if the local allocation fails, call the heavyweight allocate

    VM_Processor st = VM_Processor.getCurrentProcessor();
    int new_current = st.localCurrentAddress + size;
      
    if ( new_current <= st.localEndAddress ) {
      st.localCurrentAddress = new_current;   // increment allocation pointer
      // note - ref for an object is 4 bytes beyond the object
      new_ref = VM_Magic.addressAsObject(new_current - (SCALAR_HEADER_SIZE + OBJECT_HEADER_OFFSET));
      VM_Magic.setObjectAtOffset(new_ref, OBJECT_TIB_OFFSET, tib);
      // initial value of status word is 0 (unmarked)
      if( hasFinalizer )  VM_Finalizer.addElement(new_ref);
      return new_ref;
    }
    else
      return cloneScalar( size, tib, null );

  }   // end of allocateScalar() with finalizer flag
  
  /**
   * Allocate a scalar object & optionally clone another object.
   * Fills in the header for the object.  If a clone is specified,
   * then the data fields of the clone are copied into the new
   * object.  Otherwise, the data fields are set to 0.
   *
   * @param size     size of object (including header), in bytes
   * @param tib      type information block for object
   * @param cloneSrc object from which to copy field values
   *                 (null --> set all fields to 0/null)
   *
   * @return the reference for the allocated object
   */
  public static Object
    cloneScalar (int size, Object[] tib, Object cloneSrc)
    throws OutOfMemoryError {
    
    boolean hasFinalizer;

    VM_Magic.pragmaNoInline();	// prevent inlining - this is the infrequent slow allocate
    
    hasFinalizer = VM_Magic.addressAsType(VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(tib))).hasFinalizer();
    
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logObjectAllocationEvent();
    
    int firstByte = getHeapSpace(size);
    
    Object objRef = VM_Magic.addressAsObject(firstByte + size - SCALAR_HEADER_SIZE - OBJECT_HEADER_OFFSET);
    
    VM_Magic.setObjectAtOffset(objRef, OBJECT_TIB_OFFSET, tib);
    
    // initial value of status work is 0 (unmarked)
    
    // initialize object fields with data from passed in object to clone
    //
    if (cloneSrc != null) {
      int cnt = size - SCALAR_HEADER_SIZE;
      int src = VM_Magic.objectAsAddress(cloneSrc) + OBJECT_HEADER_OFFSET - cnt;
      int dst = VM_Magic.objectAsAddress(objRef) + OBJECT_HEADER_OFFSET - cnt;
      VM_Memory.aligned32Copy(dst, src, cnt);
    }
    
    if( hasFinalizer )  VM_Finalizer.addElement(objRef);
    
    return objRef; // return object reference
  }  // cloneScalar
  

  /**
   * Allocate an array object. Fills in the header for the object,
   * sets the array length to the specified length, and sets
   * all data fields to zero.
   *
   * @param numElements  number of array elements
   * @param size         size of array object (including header), in bytes
   * @param tib          type information block for array object
   *
   * @return the reference for the allocated array object 
   */
  public static Object
    allocateArray (int numElements, int size, Object[] tib)
    throws OutOfMemoryError {
    
    VM_Magic.pragmaInline();	// make sure this method is inlined
    
    Object objAddress;
    
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
      VM_EventLogger.logObjectAllocationEvent();
    
    // assumption: collector has previously zero-filled the space
    //
    //  |<--------------------size---------------->|
    //  |<-----hdr size---->|                      .
    //  |<-----hdr offset-->|                      .
    //  +------+------+-----+------+---------------+----+
    //  | tib  |status| len | elt0 |     ...       |free|
    //  +------+------+-----+------+---------------+----+
    //   ^memAddr             ^objAddress           ^areaCurrentAddress
    //
    
    // note: array size might not be a word multiple,
    // so we must round up size to preserve alignment for future allocations
    
    size = (size + 3) & ~3;     // round up request to word multiple
    
    // always use processor local "chunks", and size is "small", attempt to
    // allocate locally, if the local allocation fails, call the heavyweight allocate

    if (size <= SMALL_SPACE_MAX) {
      VM_Processor st = VM_Processor.getCurrentProcessor();
      int new_current = st.localCurrentAddress + size;
      if ( new_current <= st.localEndAddress ) {
	objAddress = VM_Magic.addressAsObject(st.localCurrentAddress - OBJECT_HEADER_OFFSET);  // ref for new array
	st.localCurrentAddress = new_current;            // increment processor allocation pointer
	// set tib field in header
	VM_Magic.setObjectAtOffset(objAddress, OBJECT_TIB_OFFSET, tib);
	// initial value of status word is 0 (unmarked)
	// set .length field
	VM_Magic.setIntAtOffset(objAddress, ARRAY_LENGTH_OFFSET, numElements);
	return objAddress;
      }
    }
    // if size too large, or not space in current chunk, call heavyweight allocate
    return cloneArray( numElements, size, tib, null );

  }  // allocateArray
  
  /**
   * Allocate an array object and optionally clone another array.
   * Fills in the header for the object and sets the array length
   * to the specified length.  If an object to clone is specified,
   * then the data elements of the clone are copied into the new
   * array.  Otherwise, the elements are set to zero.
   *
   * @param numElements  number of array elements
   * @param size         size of array object (including header), in bytes
   * @param tib          type information block for array object
   * @param cloneSrc     object from which to copy field values
   *                     (null --> set all fields to 0/null)
   *
   * @return the reference for the allocated array object 
   */
  public static Object
    cloneArray (int numElements, int size, Object[] tib, Object cloneSrc)
    throws OutOfMemoryError {

    VM_Magic.pragmaNoInline();	// prevent inlining - this is the infrequent slow allocate
    
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
      VM_EventLogger.logObjectAllocationEvent();
    
    size = (size + 3) & ~3;            // round up request to word multiple
    
    int firstByte = getHeapSpace(size);
    
    Object objRef = VM_Magic.addressAsObject(firstByte - OBJECT_HEADER_OFFSET);
    
    VM_Magic.setObjectAtOffset(objRef, OBJECT_TIB_OFFSET, tib);
    
    // initial value of status word is 0 (unmarked)
    
    VM_Magic.setIntAtOffset(objRef, ARRAY_LENGTH_OFFSET, numElements);
    
    // initialize array elements
    //
    if (cloneSrc != null) {
      int cnt = size - ARRAY_HEADER_SIZE;
      int src = VM_Magic.objectAsAddress(cloneSrc);
      int dst = VM_Magic.objectAsAddress(objRef);
      VM_Memory.aligned32Copy(dst, src, cnt);
    }
    
    return objRef;  // return reference for allocated array
  }  // cloneArray
  
  /**
   * Allocate space for a "large" object in the Large Object Space
   *
   * @param size  size in bytes needed for the large object
   * @return  address of first byte of the region allocated or 
   *          -2 if not enough space.
   */
  public static int
    getlargeobj (int size) {
    int i, num_pages, num_blocks, first_free, start, temp, result;
    int last_possible;
    num_pages = (size + 4095)/4096;    // Number of pages needed
    last_possible = largeSpacePages - num_pages;
    sysLockLarge.lock();

    while (largeSpaceAlloc[large_last_allocated] != 0)
      large_last_allocated += largeSpaceAlloc[large_last_allocated];

    first_free = large_last_allocated;

    while (first_free <= last_possible) {
      // Now find contiguous pages for this object
      // first find the first available page
      // i points to an available page: remember it
      for (i = first_free + 1; i < first_free + num_pages ; i++) 
	if (largeSpaceAlloc[i] != 0) break;
      if (i == (first_free + num_pages )) {  
	// successful: found num_pages contiguous pages
	// mark the newly allocated pages
	// mark the beginning of the range with num_pages
	// mark the end of the range with -num_pages
	// so that when marking (ref is input) will know which extreme 
	// of the range the ref identifies, and then can find the other

	largeSpaceAlloc[first_free + num_pages - 1] = (short)(-num_pages);
	largeSpaceAlloc[first_free] = (short)(num_pages);
	       
	if (first_free > largeSpaceHiWater) 
	  largeSpaceHiWater = first_free;

	sysLockLarge.unlock();  //release lock *and synch changes*
	int target = largeHeapStartAddress + 4096 * first_free;
	VM_Memory.zero(target, target + size);  // zero space before return
	return target;
      }  // found space for the new object without skipping any space    

      else {  // free area did not contain enough contig. pages
	first_free = i + largeSpaceAlloc[i]; 
	while (largeSpaceAlloc[first_free] != 0) 
	  first_free += largeSpaceAlloc[first_free];
      }
    }    // go to top and try again

    // fall through if reached the end of large space without finding 
    // enough space
    sysLockLarge.release();  //release lock: won't keep change to large_last_alloc'd
    return -2;  // reached end of largeHeap w/o finding numpages
  }  // getLargeObj

  // END OF NURSERY ALLOCATION ROUTINES HERE

  // **************************
  // Implementation
  // **************************

  static final int      TYPE = 1;	// IDENTIFIES HYBRID
  static final boolean  writeBarrier = true;      // MUST BE TRUE FOR THIS STORAGE MANAGER
  static final boolean  movesObjects = true;

  static VM_Type arrayOfIntType;   // VM_Type of int[], to detect code objects

  final static int  OUT_OF_BLOCKS = -1;

  static final int  MARK_VALUE = 1;               // designates "marked" objects in Nursery
  static final int  BEING_FORWARDED_PATTERN = -5; // "busy & marked" (being copied)

  static final int  SMALL_SPACE_MAX = 2048;       // largest object in small heap

  static final int  CHUNK_SIZE = 64 * 1024;       // chunk size = 64K, pre-alloca ted to sysThreads

  static final int  CRASH_BUFFER_SIZE = 1024 * 1024;  // alloc buf to get before sysFail

  static final boolean GC_USE_LARX_STCX = true;  // update mark bytes with syncronized ops

  static final boolean COMPILE_FOR_TIMING_RUN = true;      // touch heap in boot

  static final boolean TRACE                       = false; 

  static final boolean GCDEBUG_PARALLEL            = false;   // for debugging parallel collection
  static final boolean GCDEBUG_FREESPACE           = false;   // for debugging parallel collection
  static final boolean GCDEBUG_TRACE_STACKS        = false;   // for debugging scanStack

  static final boolean GC_CHECKWRITEBUFFER	    = false;   // buffer entries during gc
  static final boolean GC_TRIGGERGC                = false;   // for knowing why GC triggered

  static final boolean debugNative = false;

  static final boolean  Debug = false;	

  static final boolean  DebugLink = false;

  static int[] accum;   // for reportBlocks
  static int[] total;   // for reportBlocks    

  static double gcMinorTime;             // for timing gc times
  static double gcMajorTime;             // for timing gc times
  static double gcStartTime;             // for timing gc times
  static double gcEndTime;               // for timing gc times
  static double gcTotalTime = 0.0;         // for timing gc times
  static double maxMajorTime = 0.0;         // for timing gc times
  static double maxMinorTime = 0.0;         // for timing gc times

  private static double totalStartTime = 0.0;    // accumulated stopping time
  private static double totalMinorTime = 0.0;    // accumulated minor gc time
  private static double totalMajorTime = 0.0;    // accumulated major gc time
  private static int    collisionCount = 0;      // counts attempts to mark same object

  // timestamps and accumulators for TIME_GC_PHASES output
  private static double totalInitTime;
  private static double totalStacksAndStaticsTime;
  private static double totalScanningTime;
  private static double totalFinalizeTime;
  private static double totalFinishTime;
  private static double totalInitTimeMajor;
  private static double totalStacksAndStaticsTimeMajor;
  private static double totalScanningTimeMajor;
  private static double totalFinalizeTimeMajor;
  private static double totalFinishTimeMajor;
  
  private static double gcInitDoneTime = 0;
  private static double gcStacksAndStaticsDoneTime = 0;    
  private static double gcScanningDoneTime = 0;
  private static double gcFinalizeDoneTime = 0;
  
  // Data Fields that control the allocation of memory

  static VM_ProcessorLock  sysLockLarge;    // serialization in setMarkLarge
  static VM_ProcessorLock  sysLockFree;     // for parallel freeing of blocks
  static VM_ProcessorLock  sysLockBlock;    // for getting free blocks
  static volatile boolean           initGCDone = false;

  // 1 VM_BlockControl per GC-SIZES for initial use, before heap setup
  static VM_BlockControl[]  init_blocks;	
  static int[]              blocks;	// 1 per BLKSIZE block of the heap
				
  static int     gcCount      = 0;  // updated every entry to collect
  static int     gcMajorCount = 0;  // major collections
  static int     majorCollectionThreshold;   // minimum # blocks before major collection
  static boolean gcInProgress = false;
  static boolean outOfSmallHeapSpace = false;
  static boolean majorCollection = false;
  static boolean outOfLargeSpaceFlag = false;
  static boolean outOfMemoryReported = false;  // to make only 1 thread report OutOfMemory

  static int     smallHeapStartAddress;
  static int     smallHeapEndAddress;
  static int     minSmallHeapRef;
  static int     maxSmallHeapRef;
  static int	  num_blocks;		// number of blocks in the heap
  static int	  first_freeblock;	// number of first available block
  static int	  highest_block;		// number of highest available block
  static int	  blocks_available;	// number of free blocks for small obj's

  // nursery area for new allocations
  static int     nurseryStartAddress;     
  static int     nurseryEndAddress;
  static int     nurserySize;
  static int     fromStartAddress;
  static int     fromEndAddress;
  static int     minNurseryRef;
  static int     maxNurseryRef;

  // set pointers used by the allocateScalar and allocateArray
  static int     areaCurrentAddress;
  static int     areaEndAddress;
  static int     matureCurrentAddress;
  static int     addrAreaCurrentAddress;

  static int     largeHeapStartAddress;
  static int     largeHeapEndAddress;
  static int     largeSpacePages;
  static int     largeSpaceHiWater;
  static int     minLargeRef;
  static int     smallHeapSize;
  static int     largeHeapSize;
  static int     large_last_allocated;
             
  static short[]	largeSpaceAlloc;	// used to allocate 
  static short[]	largeSpaceMark;		// used to mark
  static byte[]	largeSpaceGen;		// used to remember generation number

  static int[]	countLargeAlloc;	//  - count sizes of large objects alloc'ed
  static int[]	countLargeLive;		//  - count sizes of large objects live
  static int[] countSmallFree;	        // bytes allocated by size
  static int[] countSmallBlocksAlloc;  // blocks allocated by size

  static VM_BootRecord	 bootrecord;
 
  static int bootStartAddress;
  static int bootEndAddress;
  static int minBootRef;
  static int maxBootRef;

  static int OBJECT_GC_MARK_VALUE = 0;   // changes between this and 0

  static int[]       partialBlockList;         // GSC
  static int         numBlocksToKeep = 10;     // GSC
  static final boolean GSC_TRACE = false;			 // GSC

  /**
  * getter function for gcInProgress
  */

  static boolean
  gcInProgress() {
    return gcInProgress;
  }

  /**
   * Setup for Collection. Sets number of collector threads participating
   * in collection (currently All participate).
   * Called from CollectorThread.boot().
   *
   * @param numThreads   number of collector threads participating
   */
  static void
    gcSetup (int numThreads ) {
    VM_GCWorkQueue.workQueue.initialSetup(numThreads);

    // increase free block threshold for triggering major collection if running
    // with very small nursery (small threshold) and many processors
    //    if (majorCollectionThreshold < numThreads * 24) 
    //      majorCollectionThreshold = numThreads * 24;
    majorCollectionThreshold = nurserySize/GC_BLOCKSIZE + numThreads*12;
  }

  /**
   * gc_getMatureSpace is called during Minor (Nursery) collections to get space
   * for live Nursery objects being copied to Mature Space.  This is basically
   * the allocate method of the non-copying mark-sweep allocator.
   * return value = 0 ==> new chunk could not be obtained. This means we could not
   * complete a minor collection & will cause an OutOfMemory error shutdown.
   * <p>
   * We Could Be Smarter Here, note that other collector threads may have space!!!
   */
  public static int
    gc_getMatureSpace (int size) throws OutOfMemoryError {

    int objaddr;

    // assumption: object blocks are always a word multiple,
    // so we don't need to worry about address alignment or rounding
    VM_Processor st = VM_Processor.getCurrentProcessor();

    // N.B. - if used only internally, no need for validity check on size
    //
    VM_SizeControl  the_size   = st.GC_INDEX_ARRAY[size];
    if (the_size.next_slot != 0) {	// fastest path
      objaddr = the_size.next_slot;
      if (DebugLink) {
	if (!isValidSmallHeapPtr(objaddr)) VM.sysFail("Bad ptr");
	if (!isPtrInBlock(objaddr, the_size)) VM.sysFail("Pointer out of block");
      }
      the_size.next_slot = VM_Magic.getMemoryWord(objaddr);
      if (DebugLink && (the_size.next_slot != 0)) {
	if (!isValidSmallHeapPtr(the_size.next_slot)) VM.sysFail("Bad ptr");
	if (!isPtrInBlock(the_size.next_slot, the_size)) VM.sysFail("Pointer out of block");
      }
	  
      return objaddr;
    }
    else return getSpacex(the_size, size);
      
  }  // getMatureSpace

  /**
   * move on to next block for given slot size, or get a new block, or return 0
   */
  static int getSpacex (VM_SizeControl the_size, int size) {
    int objaddr;
    VM_BlockControl the_block = 
      VM_Magic.addressAsBlockControl(blocks[the_size.current_block]);
    while (the_block.nextblock != OUT_OF_BLOCKS) {
      the_size.current_block = the_block.nextblock;
      the_block = VM_Magic.addressAsBlockControl(blocks[the_block.nextblock]);
      if ( build_list(the_block, the_size) ) {
	objaddr = the_size.next_slot;
	if (DebugLink) {
	  if (!isValidSmallHeapPtr(objaddr)) VM.sysFail("Bad ptr");
	  if (!isPtrInBlock(objaddr, the_size)) VM.sysFail("Pointer out of block");
	}
	the_size.next_slot = VM_Magic.getMemoryWord(objaddr);
	if (DebugLink && (the_size.next_slot != 0)) {
	  if (!isValidSmallHeapPtr(the_size.next_slot)) VM.sysFail("Bad ptr");
	  if (!isPtrInBlock(the_size.next_slot, the_size)) VM.sysFail("Pointer out of block");
	}
	return (objaddr);
      }
    }	// while nextBlock != OUT_OF_BLOCKS
       
    // the_block -> current_block for the_size
    while ( getPartialBlock(the_size.ndx) == 0 ) {
      if (GSC_TRACE) {
	VM_Processor.getCurrentProcessor().disableThreadSwitching();
	VM.sysWrite("allocatex: adding partial block: ndx "); VM.sysWrite(the_size.ndx,false);
	VM.sysWrite(" current was "); VM.sysWrite(the_size.current_block,false);
	VM.sysWrite(" new current is "); VM.sysWrite(the_block.nextblock,false);
	VM.sysWrite("\n");
	VM_Processor.getCurrentProcessor().enableThreadSwitching();
      }
      the_size.current_block = the_block.nextblock;
      the_block = VM_Magic.addressAsBlockControl(blocks[the_block.nextblock]);
      if ( build_list(the_block, the_size) ) {
	// take next slot from list of free slots for this allocation
	objaddr = the_size.next_slot;
	the_size.next_slot = VM_Magic.getMemoryWord(objaddr);
	return (objaddr);      // return addr of first byte
      }
      else {
	if (GSC_TRACE) {
	  VM_Processor.getCurrentProcessor().disableThreadSwitching();
	  VM.sysWrite("allocatey: partial block was full\n");
	  VM_Processor.getCurrentProcessor().enableThreadSwitching();
	}
      }
    }
    
    if (getnewblock(the_size.ndx) == 0) {
      the_size.current_block = the_block.nextblock;
      build_list_for_new_block
	(VM_Magic.addressAsBlockControl(blocks[the_size.current_block]), the_size);
      objaddr = the_size.next_slot;
      if (DebugLink) {
	if (!isValidSmallHeapPtr(objaddr)) VM.sysFail("Bad ptr");
	if (!isPtrInBlock(objaddr, the_size)) VM.sysFail("Pointer out of block");
      }
      the_size.next_slot = VM_Magic.getMemoryWord(objaddr);
      if (DebugLink && (the_size.next_slot != 0)) {
	if (!isValidSmallHeapPtr(the_size.next_slot)) VM.sysFail("Bad ptr");
	if (!isPtrInBlock(the_size.next_slot, the_size)) VM.sysFail("Pointer out of block");
      }
      return (objaddr);      // return addr of first byte
    }
    else return 0;
  }  // getSpacex

  /**
   * build, in the block, the list of free slot pointers, and update the
   * associated VM_SizeControl; return the address (as int) of the first
   * available slot, or 0 if there is none
   */
  static boolean
    build_list (VM_BlockControl the_block, VM_SizeControl the_size) {

    byte[] the_mark = the_block.mark;
    int first_free = 0, i = 0, j, current, next;
     
    for (; i < the_mark.length ; i++) 
      if (the_mark[i] == 0) break;
    if ( i == the_mark.length ) {
      if (DebugLink) 
	VM_Scheduler.trace("build_list: ", "found a full block", the_block.slotsize);
      // Reset control info for this block, for next collection 
      VM_Memory.zero(VM_Magic.objectAsAddress(the_mark),
		     VM_Magic.objectAsAddress(the_mark) + the_mark.length);
      the_block.live = false;
      return false;	// no free slots in this block
    }
    // here is the first
    else current = the_block.baseAddr + i * the_block.slotsize;  
    //    VM_Memory.zero(current + 4, current + the_block.slotsize);
    the_size.next_slot = current;
    if (DebugLink && (the_size.next_slot != 0)) {
      if (!isValidSmallHeapPtr(the_size.next_slot)) VM.sysFail("Bad ptr");
      if (!isPtrInBlock(the_size.next_slot, the_size)) VM.sysFail("Pointer out of block");
    }
      
    // now find next free slot
    i++;	 
    for (; i < the_mark.length ; i++) 
      if (the_mark[i] == 0) break;
    if (i == the_mark.length ) {	// next block has only 1 free slot
      VM_Magic.setMemoryWord(current, 0);
      if (DebugLink) 
	VM_Scheduler.trace("build_list: ", "found blk w 1 free slot", the_block.slotsize);
      if (DebugLink) do_check(the_block, the_size);
      // Reset control info for this block, for next collection 
      VM_Memory.zero(VM_Magic.objectAsAddress(the_mark),
		     VM_Magic.objectAsAddress(the_mark) + the_mark.length);
      the_block.live = false;
      return true;
    }
      
    next = the_block.baseAddr + i * the_block.slotsize;
    VM_Magic.setMemoryWord(current, next);
    current = next; 
    //    VM_Memory.zero(current + 4, current + the_block.slotsize);
      
    // build the rest of the list; there is at least 1 more free slot
    for (i = i + 1; i < the_mark.length ; i++) {
      if (the_mark[i] == 0) {	// This slot is free
	next = the_block.baseAddr + i * the_block.slotsize;
	VM_Magic.setMemoryWord(current, next);	// enter list pointer
	current = next;
	//     VM_Memory.zero(current + 4, current + the_block.slotsize);
      }
    }
    VM_Magic.setMemoryWord(current,0);		// set the end of the list
    if (DebugLink) do_check(the_block, the_size);
    // Reset control info for this block, for next collection 
    VM_Memory.zero(VM_Magic.objectAsAddress(the_mark),
		   VM_Magic.objectAsAddress(the_mark) + the_mark.length);
    the_block.live = false;
    return true;
  }  // build_list 
       
  // A debugging routine: called to validate the result of build_list 
  // and build_list_for_new_block
  // 
  private static void
    do_check (VM_BlockControl the_block, VM_SizeControl the_size) {

    int count = 0;
    if (VM_Magic.addressAsBlockControl(blocks[the_size.current_block]) 
	!= the_block) {
      VM_Scheduler.trace("do_check", "BlockControls don't match");
      VM.sysFail("BlockControl Inconsistency");
    }
    if (the_size.next_slot == 0) VM_Scheduler.trace("do_check", "no free slots in block");
    int temp = the_size.next_slot;
    while (temp != 0) {
      if ((temp < the_block.baseAddr) || (temp > the_block.baseAddr + GC_BLOCKSIZE))  {
	VM_Scheduler.trace("do_check: TILT:", "invalid slot ptr", temp);
	VM.sysFail("Bad freelist");
      }
      count++;
      temp = VM_Magic.getMemoryWord(temp);
    }
      
    if (count > the_block.mark.length)  {
      VM_Scheduler.trace("do_check: TILT:", "too many slots in block");
      VM.sysFail("too many slots");
    }
    //  VM_Scheduler.trace("do_check", "slot_size is", the_block.slotsize);
    //  VM_Scheduler.trace("do_check", "free slots are", count);
  }

  /**
   * Build list of free slots of a given size in an empty block.
   *
   * @param the_block   VM_BlockControl for the empty block
   * @param the_size    VM_SizeControl the block will be assigned to
   */
  static void
    build_list_for_new_block (VM_BlockControl the_block, VM_SizeControl the_size) {

    byte[] the_mark = the_block.mark;
    int i, current, delta;
    current = the_block.baseAddr;
    //    VM_Memory.zero(current, current + GC_BLOCKSIZE);
    delta   = the_block.slotsize;
    the_size.next_slot = current ;	// next one to allocate
    if (DebugLink && (the_size.next_slot != 0)) {
      if (!isValidSmallHeapPtr(the_size.next_slot)) VM.sysFail("Bad ptr");
      if (!isPtrInBlock(the_size.next_slot, the_size)) VM.sysFail("Pointer out of block");
    }
    for (i = 0; i < the_mark.length -1; i++) {
      VM_Magic.setMemoryWord(current, current + delta);
      current += delta;
    }
    // last slot does not point forward
    VM_Magic.setMemoryWord(current, 0);
    if (DebugLink) do_check(the_block, the_size);
    // Reset control info for this block, for next collection 
    VM_Memory.zero(VM_Magic.objectAsAddress(the_mark),
		   VM_Magic.objectAsAddress(the_mark) + the_mark.length);
    the_block.live = false;
    return ;
  }  // build_list_for_new_block
       
  /**
   * get a partially full block for a given slot size from the shard
   * pool of partially full blocks.
   */
  private static int
    getPartialBlock (int ndx) {

    VM_Processor st = VM_Processor.getCurrentProcessor();
    VM_SizeControl this_size = st.sizes[ndx];
    VM_BlockControl currentBlock =
      VM_Magic.addressAsBlockControl(blocks[this_size.current_block]);

    sysLockBlock.lock();

    if (partialBlockList[ndx] == OUT_OF_BLOCKS) {
      //      if (GSC_TRACE) {
      //  VM_Processor.getCurrentProcessor().disableThreadSwitching();
      //  VM.sysWrite("getPartialBlock: ndx = "); VM.sysWrite(ndx,false);
      //  VM.sysWrite(" returning -1\n");
      //  VM_Processor.getCurrentProcessor().enableThreadSwitching();
      //      }
      sysLockBlock.release();
      return -1;
    }

    // get first partial block of same slot size
    //
    currentBlock.nextblock = partialBlockList[ndx];
    VM_BlockControl allocBlock =
      VM_Magic.addressAsBlockControl(blocks[partialBlockList[ndx]]);
    partialBlockList[ndx] = allocBlock.nextblock;
    allocBlock.nextblock = OUT_OF_BLOCKS;

    if (GSC_TRACE) {
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      VM.sysWrite("getPartialBlock: ndx = "); VM.sysWrite(ndx,false);
      VM.sysWrite(" allocating "); VM.sysWrite(currentBlock.nextblock,false);
      VM.sysWrite(" baseAddr "); VM.sysWriteHex(allocBlock.baseAddr);
      VM.sysWrite("\n");
      if (VM.VerifyAssertions) VM.assert(allocBlock.slotsize==GC_SIZEVALUES[ndx]);
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
    }

    sysLockBlock.unlock();
    return 0;
  }  // getPartialBlock

  /**
   * Get an empty block, from the shared list of empty blocks, and allocate
   * if necessary, a mark array.
   */
  private static int
    getnewblock (int ndx) {
    int i, save, size, location;
    VM_Processor st = VM_Processor.getCurrentProcessor();
    VM_SizeControl this_size = st.sizes[ndx];
    VM_BlockControl alloc_block = VM_Magic.addressAsBlockControl(blocks[this_size.current_block]);
    // some debugging code in generational collector available if needed 
       
    /// if (alloc_block.nextblock != OUT_OF_BLOCKS) return 0;
       
       
    //  return -1 to indicate small object triggered gc.
    sysLockBlock.lock();
    if (first_freeblock == OUT_OF_BLOCKS) {
      sysLockBlock.release();
      return -1;
    }

    alloc_block.nextblock = first_freeblock;
    alloc_block = VM_Magic.addressAsBlockControl(blocks[first_freeblock]);
    first_freeblock = alloc_block.nextblock;	// new first_freeblock
    blocks_available--;
    sysLockBlock.unlock();
    alloc_block.nextblock = OUT_OF_BLOCKS;	// this is last block in list for thissize
    alloc_block.slotsize  = GC_SIZEVALUES[ndx];
    size = GC_BLOCKSIZE/GC_SIZEVALUES[ndx] ;	
       
    // on first assignment of this block, get space from AIX
    // for mark array, for the size requested.  
    // If not first assignment, if the existing arrays are large enough for 
    // the new size, use them; else free the existing ones, and get space 
    // for new ones.  Keep the size for the currently allocated arrays in
    // alloc_block.alloc_size.  This value only goes up during the running
    // of the VM.
       
    int temp;
    if (alloc_block.mark != null) {
      if (size <= alloc_block.alloc_size) {
	VM_Magic.setMemoryWord(VM_Magic.objectAsAddress(alloc_block.mark) +
			       ARRAY_LENGTH_OFFSET, size);
	return 0;
      }
      else {		// free the existing array space
	VM.sysCall1(bootrecord.sysFreeIP,
		    VM_Magic.objectAsAddress(alloc_block.mark) - ARRAY_HEADER_SIZE);
      }
    }
    temp = (size + ARRAY_HEADER_SIZE + 3) & ~3;
    if ((location = VM.sysCall1(bootrecord.sysMallocIP,
				temp)) == 0) {
      VM.sysWrite(" In getnewblock, call to sysMalloc returned 0 \n");
      VM.shutdown(1800);
    }
    if (VM.VerifyAssertions) VM.assert((location & 3) == 0);// check full wd
    // zero the array bytes used for allocation (mark is zeroed at
    // beginning of gc)
    alloc_block.alloc_size = size;	// remember allocated size
    alloc_block.mark = VM_Magic.addressAsByteArray(location  
						   + ARRAY_HEADER_SIZE);
    int byte_array_tib = VM_Magic.getMemoryWord(
						VM_Magic.objectAsAddress(VM_Magic.addressAsBlockControl(blocks[0]).mark) + OBJECT_TIB_OFFSET);
    VM_Magic.setMemoryWord(VM_Magic.objectAsAddress(alloc_block.mark) +
			   OBJECT_TIB_OFFSET, byte_array_tib);
    VM_Magic.setMemoryWord(VM_Magic.objectAsAddress(alloc_block.mark) +
			   ARRAY_LENGTH_OFFSET, size);
    return 0;
  }
       
  // Like getnewblock, used for VM_Processor constructor
  //
  static int
    getnewblockx (int ndx) {
    int location;
    sysLockBlock.lock();
    if (first_freeblock == OUT_OF_BLOCKS) {
      if (GC_TRIGGERGC) 
	VM_Scheduler.trace(" gc collection triggered by getnewblockx call ", "XX");
      gc1();
    }	
    VM_BlockControl alloc_block = VM_Magic.addressAsBlockControl(blocks[first_freeblock]);
    int theblock = first_freeblock;
    first_freeblock = alloc_block.nextblock;
    blocks_available--;
    sysLockBlock.unlock();
    alloc_block.nextblock = OUT_OF_BLOCKS;  // this is last block in list for thissize
    alloc_block.slotsize  = GC_SIZEVALUES[ndx];
    int size = GC_BLOCKSIZE/GC_SIZEVALUES[ndx] ;     
    if (alloc_block.mark != null)  {
      if (size <= alloc_block.alloc_size) {
	VM_Magic.setMemoryWord(VM_Magic.objectAsAddress(alloc_block.mark) +
			       ARRAY_LENGTH_OFFSET, size);
	return 0;
      }
      else {		// free the existing array space
	VM.sysCall1(bootrecord.sysFreeIP,
		    VM_Magic.objectAsAddress(alloc_block.mark) - ARRAY_HEADER_SIZE);
      }
    }
    // get space for alloc arrays from AIX.
    int temp = (size + ARRAY_HEADER_SIZE + 3) & ~3;
    if ((location = VM.sysCall1(bootrecord.sysMallocIP,
				temp)) == 0) {
      VM.sysWrite(" In getnewblockx, call to sysMalloc returned 0 \n");
      VM.shutdown(1800);
    }
    if (VM.VerifyAssertions) VM.assert((location & 3) == 0);// check full wd
    // zero the array bytes used for allocation (mark is zeroed at
    // beginning of gc)
    alloc_block.mark = VM_Magic.addressAsByteArray(location
						   + ARRAY_HEADER_SIZE);
    // GET TIB POINTER FOR byte[] from VM_Magic.addressAsBlockControl(blocks[0]).mark
    int byte_array_tib = VM_Magic.getMemoryWord(
						VM_Magic.objectAsAddress(VM_Magic.addressAsBlockControl(blocks[0]).mark) + OBJECT_TIB_OFFSET);
    VM_Magic.setMemoryWord(VM_Magic.objectAsAddress(alloc_block.mark) +
			   OBJECT_TIB_OFFSET, byte_array_tib);
    VM_Magic.setMemoryWord(VM_Magic.objectAsAddress(alloc_block.mark) +
			   ARRAY_LENGTH_OFFSET, size);
    // hashcode now set in Object.hashCode() on first use
       
    return theblock;
  }

  private static int  
    getndx (int size) {
    if (size <= GC_SIZEVALUES[0]) return 0;	// special case most common
    if (size <= GC_SIZEVALUES[1]) return 1;	// special case most common
    if (size <= GC_SIZEVALUES[2]) return 2;	// special case most common
    if (size <= GC_SIZEVALUES[3]) return 3;	// special case most common
    if (size <= GC_SIZEVALUES[4]) return 4;	// special case most common
    if (size <= GC_SIZEVALUES[5]) return 5;	// special case most common
    if (size <= GC_SIZEVALUES[6]) return 6;	// special case most common
    if (size <= GC_SIZEVALUES[7]) return 7;	// special case most common
    //	if (size <= GC_SIZEVALUES[8]) return 8;	// special case most common
    for (int i =8; i < GC_SIZES; i++) 
      if (size <= GC_SIZEVALUES[i]) return i;
    return -1;
  }

  /**
   * Do Minor collection of Nursery, copying live nursery objects
   * into Mature/Fixed Space.  All collector threads execute in parallel.
   * If any attempt to get space (see getMatureSpace) fails,
   * outOfSmallHeapSpace is set and the Minor collection will be
   * incomplete.  In this simple version of hybrid, this will cause
   * an Out_Of_Memory failure.
   */
  static void
    gcCollectMinor () {
    int       i,temp,bytes;

    // ASSUMPTIONS:
    // initGCDone flag is false before first GC thread enter gcCollectMinor
    // InitLock is reset before first GC thread enter gcCollectMinor
    //

    // following just for timing GC time
    double tempTime;

    if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logGarbageCollectionEvent();

    int mypid = VM_Processor.getCurrentProcessorId();  // id of processor running on

    // BEGIN SINGLE GC THREAD SECTION - GC INITIALIZATION

    if ( VM_GCLocks.testAndSetInitLock() ) {
       
      gcStartTime = VM_Time.now();         // start time for GC
      totalStartTime += gcStartTime - VM_CollectorThread.startTime; //time since GC requested

      if (VM.VerifyAssertions) VM.assert( initGCDone == false );  

      gcCount++;

      // setup common workqueue for num VPs participating, used to be called once.
      // now count varies for each GC, so call for each GC   SES 050201
      //
      VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());
      
      gcInProgress = true;
      majorCollection = false;
      outOfSmallHeapSpace = false;

      // Now initialize the large object space mark array
      VM_Memory.zero(VM_Magic.objectAsAddress(largeSpaceMark), 
		     VM_Magic.objectAsAddress(largeSpaceMark) + 2*largeSpaceMark.length);
             
      // this gc thread copies own VM_Processor, resets processor register & processor
      // local allocation pointers (before copying first object to ToSpace)
      gc_initProcessor();
   
      // with the default jni implementation some RVM VM_Processors may
      // be blocked in native C and not participating in a collection.
      prepareNonParticipatingVPsForGC( false /*minor*/);
      
      // precopy new VM_Thread objects, updating schedulers threads array
      // here done by one thread. could divide among multiple collector threads
      gc_copyThreads();

      VM_GCLocks.resetFinishLock();  // for singlethread'ing end of minor collections

      // must sync memory changes so GC threads on other processors see above changes
      // sync before setting initGCDone flag to allow other GC threads to proceed
      VM_Magic.sync();

      if (TIME_GC_PHASES)  gcInitDoneTime = VM_Time.now();

      // set Done flag to allow other GC threads to begin processing
      initGCDone = true;

    } // END SINGLE GC THREAD SECTION - GC INITIALIZATION

    else {
      // Each GC thread must wait here until initialization is complete
      // this should be short, if necessary at all, so we spin instead of sysYiel
      //
      // It is NOT required that all GC threads reach here before any can proceed
      //
      while( initGCDone == false ); // spin until initialization finished
      VM_Magic.isync();             // prevent following inst. from moving infront of waitloop

      // each gc thread copies own VM_Processor, resets processor register & processor
      // local allocation pointers
      gc_initProcessor();
    }

    if (outOfSmallHeapSpace) {
      VM_Scheduler.trace(" gcCollectMinor:", "outOfSmallHeapSpace after initProcessor, gcCount = ", gcCount);
      return;
    }

    // ALL GC THREADS IN PARALLEL

    // each GC threads acquires ptr to its thread object, for accessing thread local counters
    // and workqueue pointers.  If the thread object needs to be moved, it has been, in copyThreads
    // above, and its ref in the threads array (in copyThreads) and the activeThread field of the
    // current processors VM_Processor (in initProcessor) have been updated  This means using either
    // of those fields to get "currentThread" get the copied thread object.
    //
    VM_CollectorThread mylocal = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    // workqueue should have been left empty, with top == start
    if (VM.VerifyAssertions) VM.assert( mylocal.workQueueTop == mylocal.workQStartAddress);

    // This rendezvous appears to be required, else pBOB fails
    // See copyingGC.VM_Allocator...
    //
    if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
    VM_CollectorThread.gcBarrier.rendezvous();
    if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
          
    // Begin finding roots for this collection.
    // roots are (fromSpace) object refs in the jtoc or on the stack or in writebuffer
    // objects.  For each unmarked root object, it is marked, copied to mature space, and
    // added to thread local work queue buffer for later scanning. The root refs are updated.
     
    // scan VM_Processor object, causing referenced object to be copied.  When write buffers are
    // implemented as objects it is thus copied, and special code updates interior pointers 
    // (declared as ints) into the writebuffers.
    //
    gc_scanProcessor();        // each gc threads scans its own processor object

    gc_scanStaticsMinor();    // ALL GC threads process JTOC in parallel
 
    gc_scanThreads();          // ALL GC threads compete to scan threads & stacks

    if (outOfSmallHeapSpace) {
      VM_Scheduler.trace("gcCollectMinor:", "out of memory after scanning statics & threads");
      return;
    }

    // This synchronization is necessary to ensure all stacks have been scanned
    // and all internal save ip values have been updated before we scan copied
    // objects.  Because if we scan a VM_Method, and then update its code pointer
    // we can no longer compute old ip offsets for updating saved ip values
    //
    if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
    VM_CollectorThread.gcBarrier.rendezvous();
    if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;

    // have processor 1 record timestame for end of scanning stacks & statics
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcStacksAndStaticsDoneTime = VM_Time.now(); // for time scanning stacks & statics

    // scan modified old objects for refs->nursery objects
    gc_processWriteBuffers();

    if (outOfSmallHeapSpace) {
      VM_Scheduler.trace("gcCollectMinor:", "out of memory after processWriteBuffers");
      return;
    }

    // each GC thread processes work queue buffers until empty
    gc_emptyWorkQueue();

    // have processor 1 record timestame for end of scan/mark/copy phase
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcScanningDoneTime = VM_Time.now();

    if (outOfSmallHeapSpace) {
      VM_Scheduler.trace("gcCollectMinor:", "out of memory after emptyWorkQueue");
      return;
    }

    // If counting or timing in VM_GCWorkQueue, save current counter values
    //
    if (VM_GCWorkQueue.WORKQUEUE_COUNTS)   VM_GCWorkQueue.saveCounters(mylocal);
    if (VM_GCWorkQueue.MEASURE_WAIT_TIMES || VM_CollectorThread.MEASURE_WAIT_TIMES)
      VM_GCWorkQueue.saveWaitTimes(mylocal);
     
    // all write buffers were reset to empty earlier, check that still empty
    if (GC_CHECKWRITEBUFFER) gc_checkWriteBuffers();

    // If there are not any objects with finalizers skip finalization phases
    //
    if (VM_Finalizer.existObjectsWithFinalizers()) {

      // Now handle finalization

      if (mylocal.gcOrdinal == 1) {

	VM_GCWorkQueue.workQueue.reset();   // reset work queue shared control variables
	
	// one thread scans the hasFinalizer list for dead objects.  They are made live
	// again, and put into that threads work queue buffers.
	//
	VM_Finalizer.moveToFinalizable();

	// following resets barrier bits in objects modified by moveToFinalizable
	// write buffer entries generated during GC will be discarded, and these
	// object may not get scanned in the next collection (hard to find bug) 
	//
	VM_WriteBuffer.resetBarrierBits(VM_Processor.getCurrentProcessor());
      }
      
      // ALL threads have to wait to see if any finalizable objects are found
      if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
      VM_CollectorThread.gcBarrier.rendezvous();
      if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
     
      if (VM_Finalizer.foundFinalizableObject) {

	// Some were found. Now ALL threads execute emptyWorkQueue again, this time
	// to mark and keep live all objects reachable from the new finalizable objects.
	//
	gc_emptyWorkQueue();

      }

      if (outOfSmallHeapSpace) {
	VM_Scheduler.trace("gcCollectMinor:", "out of memory after finalization");
	return;
      }

    }  //  end of Finalization Processing

    // Each GC thread increments adds its wait times for this collection
    // into its total wait time - for printSummaryStatistics output
    //
    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.incrementWaitTimeTotals();

    //
    if ( VM_GCLocks.testAndSetFinishLock() ) {

      // BEGIN SINGLE GC THREAD SECTION - MINOR END

      // set ending times for preceeding finalization or scanning phase
      // do here where a sync (below) will push value to memory
      if (TIME_GC_PHASES)  gcFinalizeDoneTime = VM_Time.now();

      // reset allocation pointers to the empty nursery area
      areaCurrentAddress = nurseryStartAddress;

      // for this collector "zapFromSpace" means zap the nursery
      // fill from space with 0x01010101, then zero on each allocation
      if (VM.AllocatorZapFromSpace)
	VM_Memory.fill( nurseryStartAddress, (byte)1, nurserySize );

      // in minor collections mark OLD large objects, to keep until next major collection
      // in major collections following just resets largeSpaceGen numbers of free pages
      gc_markOldLargeObjects();

      // exchange largeSpaceAlloc and largeSpaceMark
      short[] shorttemp = largeSpaceAlloc;
      largeSpaceAlloc = largeSpaceMark;
      largeSpaceMark  = shorttemp;
      large_last_allocated = 0;

      prepareNonParticipatingVPsForAllocation( false /*minor*/);

      gcInProgress = false;

      // reset lock for next GC before starting mutators
      VM_GCLocks.reset();

      // reset the flag used during GC initialization, for next GC
      initGCDone = false;

      gcEndTime = VM_Time.now();
      gcMinorTime = gcEndTime - gcStartTime;
      gcTotalTime += gcMinorTime;
      if (gcMinorTime > maxMinorTime) maxMinorTime = gcMinorTime;
      totalMinorTime += gcMinorTime;

      if ( VM.verboseGC ) {
	VM.sysWrite("\n<GC ");
	VM.sysWrite(gcCount,false);
	VM.sysWrite(" (MINOR) time ");
	VM.sysWrite( (int)(gcMinorTime*1000.0), false );
	VM.sysWrite(" (ms)  blocks available = ");
	VM.sysWrite(blocks_available,false);
	VM.sysWrite("  found finalizable = ");
	VM.sysWrite(VM_Finalizer.foundFinalizableCount,false);
	VM.sysWrite("\n");
      }

      // add current GC phase times into totals, print if verbose on
      if (TIME_GC_PHASES) accumulateGCPhaseTimes();  	

      if ( VM.verboseGC ) printWaitTimesAndCounts();

      //       if (VM.verboseGC && VM_CollectorThread.MEASURE_WAIT_TIMES)
      //	 VM_CollectorThread.printThreadWaitTimes();

      // must sync memory changes so GC threads on other processors see above changes
      VM_Magic.sync();

    }  // END OF SINGLE THREAD SECTION

    // all GC threads return to collect
    return;

  }  // gcCollectMinor

  /**
   * Update Large Space Mark and Generation numbers after Major & Minor collections.
   * For Minor Collections - remarks old large objects not otherwise marked
   * For Major Collections - resets gen numbers of unmarked (now free) large objects
   */
  static void
    gc_markOldLargeObjects () {
    int i,j,ii;

    for (i =  0; i <= largeSpaceHiWater;) {
      ii = largeSpaceMark[i];

      if (VM.VerifyAssertions) VM.assert( ii >= 0 );

      if (ii == 0) {           // no live object found here
	j = largeSpaceGen[i]; // now check for old object
	if (j == 0) {
	  i++;
	  continue; // was not live before this collection
	}
	else { // was live; either new object became garbage, or old
	  ii = largeSpaceAlloc[i];     // tells us size
	  if (j >= GC_OLD) {// an old object
	    if (!majorCollection) {    // this is not a full collection
	      largeSpaceMark[i + ii -1] = (short)(-ii);
	      largeSpaceMark[i] = (short)ii;
	      i = i + ii ;
	      continue;
	    }
	  }
	  largeSpaceGen[i] = 0;   // an old (if a full collection)
	  // or middle-aged object became garbage
	  largeSpaceGen[i + ii - 1] = 0;   // and the other end
	  i = i + ii ;         // do correct increment of loop
	}
      }
      else i = i + ii ;
    }
  }  // gc_markOldLargeObjects

  /**
   * initProcessor is called by each GC thread to copy the processor object of the
   * processor it is running on, and reset it processor register, and update its
   * entry in the scheduler processors array and reset its local allocation pointers
   */
  static void 
    gc_initProcessor ()  {
    int            sta;
    VM_Processor   st;
    VM_Thread      activeThread;
    int            tid;   // id of active thread
	
    st = VM_Processor.getCurrentProcessor();
    sta = VM_Magic.objectAsAddress(st);
    activeThread = st.activeThread;
    tid = activeThread.getIndex();

    if (VM.VerifyAssertions) VM.assert(tid == VM_Thread.getCurrentThread().getIndex());

    // if Processor is in fromSpace, copy and update array entry
    if ( sta >= minNurseryRef && sta <= maxNurseryRef ) {
      sta = gc_copyObject(sta);   // copy thread object, do not queue for scanning
      // change entry in system threads array to point to copied sys thread
      VM_Magic.setMemoryWord( VM_Magic.objectAsAddress(VM_Scheduler.processors)+(st.id*4), sta);
      // should have Magic to recast addressAsProcessor, instead 
      // reload st from just modified array entry
      st = VM_Scheduler.processors[st.id];
    }

    // each gc thread updates its PROCESSOR_REGISTER, after copying its VM_Processor object
    VM_Magic.setProcessorRegister(st);

    //  reset local heap pointers. first mutator allocate to will cause the 
    //  processor to get new CHUNK from the shared Nursery
    //
    st.localCurrentAddress = 0;
    st.localEndAddress     = 0;

    // if Processors activethread (should be current, gc, thread) is in fromSpace, copy and
    // update activeThread field and threads array entry to make sure BOTH ways of computing
    // getCurrentThread return the new copy of the thread
    int ata = VM_Magic.objectAsAddress(activeThread);
    if ( ata >= minNurseryRef && ata <= maxNurseryRef ) {
      // copy thread object, do not queue for scanning
      ata = gc_copyObject(ata);
      st.activeThread = VM_Magic.addressAsThread(ata);
      // change entry in system threads array to point to copied sys thread
      VM_Magic.setMemoryWord( VM_Magic.objectAsAddress(VM_Scheduler.threads)+(tid*4), ata);
    }

    // setup the work queue buffers for this gc thread
    VM_GCWorkQueue.resetWorkQBuffers();
  }  // initProcessor

  // called by ONE gc/collector thread to copy any "new" thread objects
  // copies but does NOT enqueue for scanning
  static void
    gc_copyThreads ()  {
    int          i, ta, vpa, thread_count;
    VM_Thread    t;
    VM_Processor vp;

    for ( i=0; i<VM_Scheduler.threads.length; i++ ) {
      t = VM_Scheduler.threads[i];
      ta = VM_Magic.objectAsAddress(t);
      if ( ta >= minNurseryRef && ta <= maxNurseryRef ) {
	ta = gc_copyObject(ta);
	// change entry in threads array to point to new copy of thread
	VM_Magic.setMemoryWord( VM_Magic.objectAsAddress(VM_Scheduler.threads)+(i*4), ta);
      }
    }  // end of loop over threads[]
  } // gc_copyThreads

  /**
   * Do a Major Collection.  Does a full scan in which all objects reachable from
   * roots (stacks & statics) are marked.  Triggered after a Minor collection
   * when the count of available free blocks falls below a specified threshold.
   * Because Minor collection must complete successfully, this threshold is
   * conservatively set to the number of blocks in the Nursery.
   */
  static void
    gcCollectMajor () {
    int i, ii;
    short[]	shorttemp;		// used to exchange Alloc and Mark
    double    tempTime;

    int start, end;
    VM_BlockControl this_block;
    VM_BlockControl next_block;
    VM_SizeControl this_size;

    if (TRACE) VM_Scheduler.trace("gcCollectMajor:", "Entering");

    VM_CollectorThread mylocal = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    int mypid = VM_Processor.getCurrentProcessorId();  // id of processor running on

    VM_Processor st = VM_Processor.getCurrentProcessor();

    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.resetWaitTimers();         // reset for measuring major GC wait times

    // Begin single thread initialization
    //
    if ( VM_GCLocks.testAndSetInitLock()) {
      gcStartTime = VM_Time.now();    // reset for measuring major collections
       
      gcMajorCount++;
      majorCollection = true;
       
      if (TRACE) VM_Scheduler.trace("gcCollectMajor:","initialization for gcMajorCount =",gcMajorCount);

      // setup common workqueue for num VPs participating, used to be called once.
      // now count varies for each GC, so call for each GC
      //
      VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());

      // set number of partial heap blocks per processor slot size to retain
      // choose to keep half of the blocks
      //
      //	numBlocksToKeep = num_blocks/(VM_Scheduler.numProcessors * GC_SIZES * 2);
      numBlocksToKeep = 1;    // was 4, have to keep 1!!!

      // invert the mark_flag value, used for marking BootImage objects
      if ( OBJECT_GC_MARK_VALUE == 0 )
	OBJECT_GC_MARK_VALUE = OBJECT_GC_MARK_MASK;
      else
	OBJECT_GC_MARK_VALUE = 0;

      // Now initialize the large object space mark array
      VM_Memory.zero(VM_Magic.objectAsAddress(largeSpaceMark), 
		     VM_Magic.objectAsAddress(largeSpaceMark) + 2*largeSpaceMark.length);

      // zero mark arrays in global partial blocks list
      //
      if (GSC_TRACE) VM_Scheduler.trace("Zeroing partial block mark arrays"," ");
      for (i = 0; i < GC_SIZES; i++) {
				int counter = 0;
				int index = partialBlockList[i];
				while ( index != OUT_OF_BLOCKS ) {
	  			counter++;
	  			this_block = VM_Magic.addressAsBlockControl(blocks[index]);
	  			VM_Memory.zero(VM_Magic.objectAsAddress(this_block.mark),
			    VM_Magic.objectAsAddress(this_block.mark) + this_block.mark.length);
	  			this_block.live = false;
	  			index = this_block.nextblock;
				}
	if (GSC_TRACE) {
	  VM.sysWrite(" size = "); VM.sysWrite(i,false);
	  VM.sysWrite(" first = "); VM.sysWrite(partialBlockList[i],false);
	  VM.sysWrite(" count = "); VM.sysWrite(counter,false); VM.sysWrite("\n");
	}
      }

      // with the default jni implementation some RVM VM_Processors may
      // be blocked in native C and not participating in a collection.
      prepareNonParticipatingVPsForGC( true /*major*/);

    }

    // ALL COLLECTOR THREADS IN PARALLEL

    if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
    VM_CollectorThread.gcBarrier.rendezvous();
    if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;

    VM_GCWorkQueue.resetWorkQBuffers();  // reset thread local work queue buffers

    // For each slot size list, need to initialize (zero mark array) all blocks 
    // after the current block since this was not done during mutator execution
    //
    for (i = 0; i < GC_SIZES; i++) {
      this_block = VM_Magic.addressAsBlockControl(blocks[st.sizes[i].current_block]);
      int next = this_block.nextblock;
      while (next != OUT_OF_BLOCKS) {
			this_block = VM_Magic.addressAsBlockControl(blocks[next]);
			if (VM.VerifyAssertions) VM.assert(this_block.mark != null);
			VM_Memory.zero(VM_Magic.objectAsAddress(this_block.mark),
		       VM_Magic.objectAsAddress(this_block.mark) + this_block.mark.length);
			this_block.live = false;
			next = this_block.nextblock;
      }
    }

    if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
    VM_CollectorThread.gcBarrier.rendezvous();
    if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;

    // have processor 1 record timestame for end of scanning stacks & statics
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcInitDoneTime = VM_Time.now(); // for time scanning stacks & statics

    gc_scanStaticsMajor();

    gc_scanStacksMajor();

    // have processor 1 record timestame for end of scanning stacks & statics
    // ...this will be approx. because there is not a rendezvous after scanning thread stacks.
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcStacksAndStaticsDoneTime = VM_Time.now(); // for time scanning stacks & statics

    gc_emptyWorkQueueMajor();

    // have processor 1 record timestame for end of scan/mark/copy phase
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcScanningDoneTime = VM_Time.now();

    // If counting or timing in VM_GCWorkQueue, save current counter values
    //
    if (VM_GCWorkQueue.WORKQUEUE_COUNTS)   VM_GCWorkQueue.saveCounters(mylocal);
    if (VM_GCWorkQueue.MEASURE_WAIT_TIMES || VM_CollectorThread.MEASURE_WAIT_TIMES)
      VM_GCWorkQueue.saveWaitTimes(mylocal);

    // If there are not any objects with finalizers skip finalization phases
    //
    if (VM_Finalizer.existObjectsWithFinalizers()) {

      // Now handle finalization

      if (mylocal.gcOrdinal == 1) {

	VM_GCWorkQueue.workQueue.reset();   // reset work queue shared control variables
	
	// one thread scans the hasFinalizer list for dead objects.  They are made live
	// again, and put into that threads work queue buffers.
	//
	VM_Finalizer.moveToFinalizable();

	// following resets barrier bits in objects modified by moveToFinalizable
	// write buffer entries generated during GC will be discarded, and these
	// object may not get scanned in the next collection (hard to find bug) 
	//
	VM_WriteBuffer.resetBarrierBits(VM_Processor.getCurrentProcessor());
      }
      
      // ALL threads have to wait to see if any finalizable objects are found
      if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
      VM_CollectorThread.gcBarrier.rendezvous();
      if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
     
      if (VM_Finalizer.foundFinalizableObject) {

	// Some were found. Now ALL threads execute emptyWorkQueue again, this time
	// to mark and keep live all objects reachable from the new finalizable objects.
	//
	gc_emptyWorkQueueMajor();

      }
    }

    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcFinalizeDoneTime = VM_Time.now();

    // done marking all reachable objects

    int local_first_free_ndx = OUT_OF_BLOCKS; 
    int local_blocks_available = 0; 
    VM_BlockControl local_first_free_block = VM_Magic.addressAsBlockControl(VM_NULL);

    for (i = 0; i < GC_SIZES; i++) {
      this_block = VM_Magic.addressAsBlockControl(blocks[st.sizes[i].first_block]);
      this_size  = st.sizes[i];
      // begin scan in 1st block again
      this_size.current_block = this_size.first_block; 
      if (!build_list(this_block, this_size)) this_size.next_slot = 0;
      int next = this_block.nextblock;

      this_size.lastBlockToKeep = -1;     // GSC
      int blockCounter = 0;               // GSC

      while (next != OUT_OF_BLOCKS) {
				next_block = VM_Magic.addressAsBlockControl(blocks[next]);
				if (!next_block.live) {
	  			if (local_first_free_block == VM_Magic.addressAsBlockControl(VM_NULL)) 
	    			local_first_free_block = next_block;
	  // In this stanza, we make the next's next the next of this_block, and put
	  // original next on the freelist
	  			this_block.nextblock = next_block.nextblock;	// out of live list
	  			next_block.nextblock = local_first_free_ndx;
	  			local_first_free_ndx = next;
	  			local_blocks_available++;
				}
				else  {  // found that next block is live
	  			if (++blockCounter == numBlocksToKeep)            // GSC
	    			// this_size.lastBlockToKeep = next;   // used to record next block
	    			this_size.lastBlockToKeep = 
	      			(this_block.baseAddr - smallHeapStartAddress)/GC_BLOCKSIZE;
	    			this_size.lastBlockToKeep = next;             // GSC
	  			  this_block = next_block;
				}
				next = this_block.nextblock; 
      }
      // this_block -> last block in list, with next==0. remember its
      // index for possible moving of partial blocks to global lists below
      //
      this_size.last_allocated =
				(this_block.baseAddr - smallHeapStartAddress)/GC_BLOCKSIZE;
    }

    if (DEBUG_FREEBLOCKS)
      VM_Scheduler.trace(" Found assigned", " freeblocks", local_blocks_available);

    for (i = mylocal.gcOrdinal - 1; i < GC_SIZES; 
			i+= VM_CollectorThread.numCollectors()) {
      if (GCDEBUG_PARTIAL) {
        VM_Scheduler.trace("Allocator: doing partials"," index", i);
        VM_Scheduler.trace("   value in ", "list = ", partialBlockList[i]);
    	}
      if (partialBlockList[i] == OUT_OF_BLOCKS) continue;
      this_block = VM_Magic.addressAsBlockControl(blocks[partialBlockList[i]]);
			int id = 0;
			int temp;
			temp = this_block.nextblock;
			while (!this_block.live) {
        local_blocks_available++;
				if (GCDEBUG_PARTIAL) VM_Scheduler.trace(" Found an empty block at",
					" head of partial list", partialBlockList[i]);
        if (Debug) if (id++ == 500000) 
          VM.sysFail(" Loop in block controls in first of partial list");
        if (local_first_free_block == VM_Magic.addressAsBlockControl(VM_NULL))  
					{
     			  if (VM.VerifyAssertions) VM.assert(local_first_free_ndx == OUT_OF_BLOCKS);
						local_first_free_block = this_block;
					}
				temp = this_block.nextblock;
				this_block.nextblock = local_first_free_ndx;
				local_first_free_ndx = (this_block.baseAddr - smallHeapStartAddress)/GC_BLOCKSIZE;
				partialBlockList[i] = temp;
				if (temp == OUT_OF_BLOCKS) break;
  		  this_block = VM_Magic.addressAsBlockControl(blocks[temp]);
			}

			if (temp == OUT_OF_BLOCKS) continue;
			int next = this_block.nextblock;
			id = 0;
      while (next != OUT_OF_BLOCKS) {
        if (Debug) if (id++ == 500000) {
          VM.sysFail(" Loop in block controls in partial list");
        }
        next_block = VM_Magic.addressAsBlockControl(blocks[next]);
        if (!next_block.live) {
				  if (GCDEBUG_PARTIAL) VM_Scheduler.trace(" Found an empty block ",
					" in partial list", next);
        // In this stanza, we make the next's next the next of this_block, and put
        // original next on the freelist
          if (local_first_free_block == VM_Magic.addressAsBlockControl(VM_NULL))
					  {
     			    if (VM.VerifyAssertions) VM.assert(local_first_free_ndx == OUT_OF_BLOCKS);
              local_first_free_block = next_block;
						}
          this_block.nextblock = next_block.nextblock; // out of live list

          next_block.nextblock = local_first_free_ndx;
          local_first_free_ndx = next;
          local_blocks_available++;

        }
        else this_block = next_block;  // live block done
        next = this_block.nextblock;
      }
		}

    if (DEBUG_FREEBLOCKS)
      VM_Scheduler.trace(" Found partial ", " freeblocks", local_blocks_available);

    // Rendezvous here because below and above, partialBlocklist can be modified
    VM_CollectorThread.gcBarrier.rendezvous();

    sysLockFree.lock();   // serialize access to global block data

    // If this processor found empty blocks, add them to global free list
    //
    if  (local_first_free_block != VM_Magic.addressAsBlockControl(VM_NULL)) {
    if (DEBUG_FREEBLOCKS) if (local_first_free_ndx == OUT_OF_BLOCKS)
      VM_Scheduler.trace(" LFFB not NULL", "LFFI = out_of_Blocks");
      local_first_free_block.nextblock = first_freeblock;
      first_freeblock = local_first_free_ndx;
      blocks_available += local_blocks_available;
    }

    // Add excess partially full blocks (maybe full ???) blocks
    // of each size to the global list for that size
    //
    if (GSC_TRACE) VM.sysWrite("\nAdding to global partial block lists\n");

    for (i = 0; i < GC_SIZES; i++) {

      this_size = st.sizes[i];
      if (this_size.lastBlockToKeep != OUT_OF_BLOCKS ) {
				VM_BlockControl lastToKeep = VM_Magic.addressAsBlockControl(blocks[this_size.lastBlockToKeep]);
				int firstToGiveUp = lastToKeep.nextblock;
				if (firstToGiveUp != OUT_OF_BLOCKS) {
	  			VM_Magic.addressAsBlockControl(blocks[ this_size.last_allocated ]).nextblock =
	    			partialBlockList[i];
	  			partialBlockList[i] = firstToGiveUp;
	  			lastToKeep.nextblock = OUT_OF_BLOCKS;
	}
      }
      if (GSC_TRACE) {
				VM.sysWrite(" size = "); VM.sysWrite(i,false);
				VM.sysWrite(" new first = "); VM.sysWrite(partialBlockList[i],false);
        VM.sysWrite("\n");
      }
    }

    sysLockFree.unlock();  // release lock on global block data


    // Added this Rendezvous to prevent mypid==1 from proceeding before all others
    // have completed the above, especially if mypid=1 did NOT free any blocks
    if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
    VM_CollectorThread.gcBarrier.rendezvous();
    if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;

    // Each GC thread increments adds its wait times for this collection
    // into its total wait time - for printSummaryStatistics output
    //
    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.incrementWaitTimeTotals();

    // Do single-threaded cleanup
    if (mylocal.gcOrdinal == 1) {
	  
      // for major collection, reset gen numbers for old garbage
      // and reclaim space for all unmarked large objects.
      gc_markOldLargeObjects();

      shorttemp       = largeSpaceAlloc;
      largeSpaceAlloc = largeSpaceMark;
      largeSpaceMark  = shorttemp;
      large_last_allocated = 0;  // first large alloc will search from beginning

      prepareNonParticipatingVPsForAllocation( true /*major*/);

      VM_GCLocks.reset();

      // DONE except for verbose output, measurements etc..

      gcEndTime = VM_Time.now();
      gcMajorTime = gcEndTime - gcStartTime;
      gcTotalTime += gcMajorTime;
      totalMajorTime += gcMajorTime;
      if (gcMajorTime > maxMajorTime) maxMajorTime = gcMajorTime;
	 
      if (VM.verboseGC) {
				VM.sysWrite("\n<GC ");
				VM.sysWrite(gcCount,false);
				VM.sysWrite(" (MAJOR) time ");
				VM.sysWrite( (int)(gcMajorTime * 1000), false );
				VM.sysWrite(" (ms)  blocks available = ");
				VM.sysWrite(blocks_available,false);
				VM.sysWrite("  found finalizable = ");
				VM.sysWrite(VM_Finalizer.foundFinalizableCount,false);
				VM.sysWrite("\n");
      }

      // add current GC phase times into totals, print if verbose on
      if (TIME_GC_PHASES) accumulateGCPhaseTimes();  	

      if ( VM.verboseGC ) printWaitTimesAndCounts();

      //	      if (VM.verboseGC && VM_CollectorThread.MEASURE_WAIT_TIMES)
      //		VM_CollectorThread.printThreadWaitTimes();

    }	// if mylocal.gcOrdinal == 1

    // ALL collector threads return to caller (without a rendezvous)
      
  }  // gcCollectMajor

  /**
   * Scans threads stacks during Major Collections
   */
  static void
    gc_scanStacksMajor () {
    VM_Thread t;
    int fp;
      
    int myThreadId = VM_Thread.getCurrentThread().getIndex(); // ID of running GC thread

    for (int i = 0; i < VM_Scheduler.threads.length; i++) {
      t = VM_Scheduler.threads[i];

      if (t == null) continue;

      if ( i == myThreadId ) {  // at thread object for running gc thread
	VM_ScanStack.scanStack(t, VM_NULL, false /*relocate_code*/ );
        continue;
      }

      //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
      // alternate implementation of jni

      // skip other GC threads, on RVM VP, each will do its own
      if ( t.isGCThread && t.processorAffinity.processorMode == VM_Processor.RVM)
        continue;
      
      // attempt to get control of this thread
      if ( VM_GCLocks.testAndSetThreadLock(i)) {

	// find if thread is in native vproc: either it's
	// the NativeIdleThread in sysWait (if in yield, 
	// no special processing needed) or it's a mutator
	// thread running C-code; in both cases, need to
	// do the scan from the last Java frame to stacktop
	//
	if ((t.isNativeIdleThread && ((VM_NativeIdleThread)t).inSysWait)  ||
	    ((t.nativeAffinity  != null) && (t.nativeAffinity.activeThread == t)))
	  fp  = t.jniEnv.JNITopJavaFP;
	else
	  fp = t.contextRegisters.gprs[FRAME_POINTER];  // normal mutator thread
	
	gc_scanStackMajor(t, fp);
      }
      else continue;  // some other gc thread has seized this thread

      //-#else
      // default implementation of jni

      // skip other collector threads participating (have ordinal number) in this GC
      if ( t.isGCThread && (VM_Magic.threadAsCollectorThread(t).gcOrdinal > 0) )
	continue;

      // attempt to get control of this thread
      if ( VM_GCLocks.testAndSetThreadLock(i)) {

	// all threads blocked in sigwait or native should have the ip & fp
	// in their saved context regs set to start the stack scan at the
	// the proper ("top java") frame.

	VM_ScanStack.scanStack(t, VM_NULL, false /*relocate_code*/);
      }
      else continue;  // some other gc thread has seized this thread
      //-#endif

    }
  }  // gc_scanStacksMajor

  /**
   * Scan static variables (JTOC) for object references during Major collections.
   * Executed by all GC threads in parallel, with each doing a portion of the JTOC.
   */
  static void
    gc_scanStaticsMajor () {
    int numSlots = VM_Statics.getNumberOfSlots();
    int slot, ref;  
    int segmentSize = 512;
    int stride, start, end;

    stride = segmentSize * VM_Scheduler.numProcessors;

    start = (VM_Processor.getCurrentProcessorId() - 1) * segmentSize;
    
    while ( start < numSlots ) {
      end = start + segmentSize;
      if (end > numSlots)
	end = numSlots;  // doing last segment of JTOC

      for ( slot=start; slot<end; slot++ ) {
	if  ( VM_Statics.isReference(slot)) {
	  ref  = VM_Statics.getSlotContentsAsInt(slot);
	  gc_processPtrFieldValue(ref);
	}
      }
      start = start + stride;
    }  // end of while loop
  }  // scanStaticsMajor


  private static boolean
    isPossibleRef (int ref, VM_Thread t)
  {
    if (isPossible(ref, t)) {  // possibly a valid pointer
      int tibptr = VM_Magic.getMemoryWord(ref + OBJECT_TIB_OFFSET);
      if (isPossible(tibptr, t)) {
	int classptr = VM_Magic.getMemoryWord(tibptr);
	if (isPossible(classptr, t)) {
	  int tibtibptr = VM_Magic.getMemoryWord(tibptr + OBJECT_TIB_OFFSET);
	  if (isPossible(tibtibptr,t))
	    return true;
	}
      }
    }
    return false;
  }

  // a routine to perform checks on a possible pointer: does it fall within
  // the heap or the boot Image; is it *not* a pointer into the current stack
  private static boolean
    isPossible (int ref, VM_Thread t) {
    int tref = ref + OBJECT_HEADER_OFFSET;
    if (((tref >= bootStartAddress) && (tref <= bootEndAddress)) ||
	((tref >= smallHeapStartAddress) && (tref <= smallHeapEndAddress)) ||
	((tref >= largeHeapStartAddress) && (tref <= largeHeapEndAddress)) );
    else return false;

    if (t == VM_Magic.addressAsThread(VM_NULL)) return true;
    int[] stack = t.stack;
    if ((ref < VM_Magic.objectAsAddress(stack)) ||
	(ref > VM_Magic.objectAsAddress(stack) + 4*stack.length))
      return true;
    else return false;
  }

  // a debugging routine: to make sure a pointer is into the heap
  private static boolean
    isValidSmallHeapPtr (int ptr) {
    if (((ptr >= smallHeapStartAddress) && (ptr <= smallHeapEndAddress)))
      return true;
    else return false;
  }

  // a debugging routine: to make sure a pointer is into the heap
  private static boolean
    isPtrInBlock (int ptr, VM_SizeControl the_size) {
    VM_BlockControl the_block =  VM_Magic.addressAsBlockControl(blocks[the_size.current_block]);
    int base = the_block.baseAddr;
    int offset = ptr - base;
    int endofslot = ptr + the_block.slotsize;
    if (offset%the_block.slotsize != 0) VM.sysFail("Ptr not to beginning of slot");
    int bound = base + GC_BLOCKSIZE;
    if ((ptr >= base) && (endofslot <= bound)) return true;
    else return false;
  }

  /**
   * Scan an object or array for references during Major Collection
   * (Major and Minor version of this are basically identical)
   */
  static  void
    gc_scanObjectOrArrayMajor  (int objRef ) {
    VM_Type    type;

    //  First process TIB in the header - NOT NEEDED - always found in JTOC
    // gc_processPtrFieldValue(VM_Magic.getMemoryWord(objRef  + OBJECT_TIB_OFFSET));

    type  = VM_Magic.getObjectType(VM_Magic.addressAsObject(objRef));
    if  ( type.isClassType() ) { 
      int[]  referenceOffsets = type.asClass().getReferenceOffsets();
      for  (int i = 0, n = referenceOffsets.length; i < n; ++i) {
        gc_processPtrFieldValue( VM_Magic.getMemoryWord(objRef + 
							referenceOffsets[i])  );
      }
    }
    else  if ( type.isArrayType() ) {
      if  (type.asArray().getElementType().isReferenceType()) {
        int  num_elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(objRef));
        int  location = objRef;   // for arrays = address of [0] entry
        int  end    = objRef + num_elements * 4;
        while  ( location < end ) {
          gc_processPtrFieldValue(VM_Magic.getMemoryWord(location));
          //  USING  "4" where should be using "size_of_pointer" (for 64-bits)
          location  = location + 4;
        }
      }
    }
    else  {
      VM.sysWrite("VM_Allocator.gc_scanObjectOrArray: type not Array or Class");
      VM.shutdown(1000);
    }
  }  //  gc_scanObjectOrArrayMajor

  /**
   * Mark a large space object, if not already marked
   *
   * @return  true if already marked, false if not marked & this invocation marked it.
   */
  static boolean
    gc_setMarkLarge (int tref) { 
    int ij, temp, temp1;
    int page_num = (tref - largeHeapStartAddress ) >> 12;
    boolean result = (largeSpaceMark[page_num] != 0);
    if (result) return true;	// fast, no synch case
       
    sysLockLarge.lock();		// get sysLock for large objects
    result = (largeSpaceMark[page_num] != 0);
    if (result) {	// need to recheck
      sysLockLarge.release();
      return true;	
    }
    temp = largeSpaceAlloc[page_num];
    if (temp == 1) {
      if (largeSpaceGen[page_num] <= GC_OLD )
	largeSpaceGen[page_num]++;
      largeSpaceMark[page_num] = 1;
    }
    else {
      // mark entries for both ends of the range of allocated pages
      if (temp > 0) {
	ij = page_num + temp -1;
	largeSpaceMark[ij] = (short)-temp;
      }
      else {
	ij = page_num + temp + 1;
	largeSpaceMark[ij] = (short)-temp;
      }
      largeSpaceMark[page_num] = (short)temp;
	   
      // increment Gen number of live Large Space object
      if (largeSpaceGen[ij] <= GC_OLD) {
	largeSpaceGen[ij]++;              // Gen number is stored at both 
	largeSpaceGen[page_num]++;        // ends of hte allocated interval
      }
    }
       
    // Need to turn back on barrier bit *always*
    do {
      temp1 = VM_Magic.prepare(VM_Magic.addressAsObject(tref),
			       - (OBJECT_HEADER_OFFSET - OBJECT_STATUS_OFFSET));
      temp = temp1 | OBJECT_BARRIER_MASK;
    } while (!VM_Magic.attempt(VM_Magic.addressAsObject(tref),
			       -(OBJECT_HEADER_OFFSET - OBJECT_STATUS_OFFSET), temp1, temp));
       
    sysLockLarge.unlock();	// INCLUDES sync()

    return false;
  }  // gc_setMarkLarge

  /**  given an address in the small objec heap (as an int), 
  *  set the corresponding mark byte on
  */
  static  boolean
  gc_setMarkSmall (int tref) {
    int  blkndx, slotno, size, ij;
    blkndx  = (tref - smallHeapStartAddress) >> LOG_GC_BLOCKSIZE ;
    VM_BlockControl  this_block = VM_Magic.addressAsBlockControl(blocks[blkndx]);
    int  offset   = tref - this_block.baseAddr; 
    int  slotndx  = offset/this_block.slotsize;

    if (this_block.mark[slotndx] != 0) return true;   // avoid synchronization
    
    if (!GC_USE_LARX_STCX) {
      // store byte into byte array
      this_block.mark[slotndx] = 1;
    }
    else {
      // atomically update word contain mark byte
      byte  tbyte;
      int  temp, temp1;
      do  {
	// get word with proper byte from map
	temp1 = VM_Magic.prepare(this_block.mark, ((slotndx>>2)<<2));
	if (this_block.mark[slotndx] != 0) return true;
	tbyte  = (byte)( 1);         // create mask bit
//-#if RVM_FOR_IA32    
	int index = slotndx%4; // get byte in word - little Endian
//-#else 
	int index = 3 - (slotndx%4); // get byte in word - big Endian
//-#endif
	int mask = tbyte << (index * 8); // convert to bit in word
	temp  = temp1 | mask;        // merge into existing word
      }  while (!VM_Magic.attempt(this_block.mark, ((slotndx>>2)<<2), temp1, temp));
    }  // USE_LARX_STCX

    this_block.live  = true;
    return  false;
  }  //  gc_setMarkSmall


  /**
   * process pointer fields during Major Collections
   */
  static void
    gc_processPtrFieldValue (int ref) {

    if (ref == 0) return;    

    // accomodate that ref might be outside space
    int tref = ref + OBJECT_HEADER_OFFSET;	

    if ( tref >= smallHeapStartAddress && tref <  smallHeapEndAddress) {
      // object allocated in small object runtime heap
      if (!gc_setMarkSmall(tref))
	VM_GCWorkQueue.putToWorkBuffer(ref);
      return;
    }

    if ( tref >= largeHeapStartAddress && tref < largeHeapEndAddress) {
      if (!gc_setMarkLarge(tref)) 
	VM_GCWorkQueue.putToWorkBuffer(ref);
      return;
    }

    if ( (tref >= bootStartAddress) && (tref <= bootEndAddress) ) {
      if (VM_Synchronization.testAndMark(VM_Magic.addressAsObject(ref), OBJECT_STATUS_OFFSET, OBJECT_GC_MARK_VALUE))
	VM_GCWorkQueue.putToWorkBuffer(ref);
      return;
    }

    // the nursery should be empty for major collections & we should not
    // encounter any pointers to objects in the nursery area

    else if ( (tref >= nurseryStartAddress ) && (tref <= nurseryEndAddress ) ) {
      VM_Scheduler.traceHex("processPtrFieldValue:","Ptr into Nursery =",tref);
      VM_Scheduler.dumpStack(VM_Magic.getFramePointer());
      VM.shutdown(8080);
    }

    else if (Debug) {
      VM.sysWrite(tref);
      VM.sysWrite(" processPtrFieldValue ptr:  not in heap or boot image \n");
      return;
    }
  }  // gc_processPtrFieldValue

  /**
   * process objects in the work queue buffers until no more buffers to process
   * Minor collections
   */
  static void
    gc_emptyWorkQueue () {
    int ref = VM_GCWorkQueue.getFromWorkBuffer();

    while ( ref != 0 ) {
      gc_scanObjectOrArray( ref );
      ref = VM_GCWorkQueue.getFromWorkBuffer();
    }
  }

  /**
   * process objects in the work queue buffers until no more buffers to process
   * Major collections
   */
  static void
    gc_emptyWorkQueueMajor () {
    int ref = VM_GCWorkQueue.getFromWorkBuffer();

    while ( ref != 0 ) {
      gc_scanObjectOrArrayMajor( ref );
      ref = VM_GCWorkQueue.getFromWorkBuffer();
    }
  }

  /**
   * Mark objects in the BootImage. They are marked using a bit field
   * in the object header. The meaning of "marked" is inverted for
   * each collection (to avoid resetting) Only for Major Collections.
   */
  static boolean
    gc_markBootObject (int ref) {
    // ref should be for an object in BootImage !!!
    boolean result;
    int statusword, old_statusword;

    // test mark bit in lock word to see if already marked, if so done.
    statusword = VM_Magic.getMemoryWord(ref + OBJECT_STATUS_OFFSET);
    if ( (statusword & OBJECT_GC_MARK_MASK) == OBJECT_GC_MARK_VALUE ) {
      return true;        // object already marked, should be on workqueue
    }

    // set mark bit in object statusword
    do  {
      old_statusword = VM_Magic.prepare(VM_Magic.addressAsObject(ref), OBJECT_STATUS_OFFSET);
      statusword = (statusword & ~OBJECT_GC_MARK_MASK) | OBJECT_GC_MARK_VALUE;
    }  while (!VM_Magic.attempt(VM_Magic.addressAsObject(ref), OBJECT_STATUS_OFFSET, old_statusword, statusword));

    return  false;
  }  // gc_markBootObject


  private static void
  prepareNonParticipatingVPsForGC(boolean major) {

    //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
    // alternate implementation of jni
    // all RVM VM_Processors participate in every collection
    return;
    //-#endif

    // include NativeDaemonProcessor in following loop over processors
    for (int i = 1; i <= VM_Scheduler.numProcessors+1; i++) {
      VM_Processor vp = VM_Scheduler.processors[i];
      if (vp == null) continue;   // the last VP (nativeDeamonProcessor) may be null
      int vpStatus = VM_Processor.vpStatus[vp.vpStatusIndex];
      if ((vpStatus == VM_Processor.BLOCKED_IN_NATIVE) || (vpStatus == VM_Processor.BLOCKED_IN_SIGWAIT)) {
	if (vpStatus == VM_Processor.BLOCKED_IN_NATIVE) { 
	  // processor & its running thread are block in C for this GC.  Its stack
	  // needs to be scanned, starting from the "top" java frame, which has
	  // been saved in the running threads JNIEnv.  Put the saved frame pointer
	  // into the threads saved context regs, which is where the stack scan starts.
	  //
	  VM_Thread t = vp.activeThread;
	  t.contextRegisters.setInnermost( 0 /*ip*/, t.jniEnv.JNITopJavaFP );
	}

	if (major) 
	  zeromarks(vp);		// reset mark bits for nonparticipating vps
	else {
	  // for minor collections:
	  // move the processors writebuffer entries into the executing collector
	  // threads work buffers so the referenced objects will be scanned.
	  VM_WriteBuffer.moveToWorkQueue(vp);
	}
      }
    }
  
    if ( !major ) {
      // in case (actually doubtful) native processors have writebuffer
      // entries, move them also.
      for (int i = 1; i <= VM_Processor.numberNativeProcessors; i++) {
	VM_Processor vp = VM_Processor.nativeProcessors[i];
	VM_WriteBuffer.moveToWorkQueue(vp);
	// check that native processors have not done allocations
	if (VM.VerifyAssertions) {
	  if (vp.localCurrentAddress != 0) {
	    VM_Scheduler.trace("prepareNonParticipatingVPsForGC:",
			       "native processor with non-zero allocation ptr, id =",vp.id);
	    vp.dumpProcessorState();
	    VM.assert(vp.localCurrentAddress == 0);
	  }
	}
      }
    }
  }  // prepareNonParticipatingVPsForGC

  private static void
  prepareNonParticipatingVPsForAllocation(boolean major) {

    //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
    // alternate implementation of jni
    // all RVM VM_Processors participate in every collection
    return;
    //-#endif

    // include NativeDaemonProcessor in following loop over processors
    for (int i = 1; i <= VM_Scheduler.numProcessors+1; i++) {
      VM_Processor vp = VM_Scheduler.processors[i];
      if (vp == null) continue;   // the last VP (nativeDeamonProcessor) may be null
      int vpStatus = VM_Processor.vpStatus[vp.vpStatusIndex];
      if ((vpStatus == VM_Processor.BLOCKED_IN_NATIVE) || (vpStatus == VM_Processor.IN_SIGWAIT)) {
	if ( major )
	  setupAllocation(vp);
	else {
	  // After minor collections, reset VPs allocation pointers so subsequent
	  // allocations will acquire a new local block from the new nursery
	  vp.localCurrentAddress = 0;
	  vp.localEndAddress     = 0;
	}

      }
    }
  }

  private static void
  setupAllocation(VM_Processor st) {
    for (int i = 0; i < GC_SIZES; i++) {
      VM_BlockControl this_block = VM_Magic.addressAsBlockControl(blocks[st.sizes[i].first_block]);
      VM_SizeControl this_size  = st.sizes[i];
      // begin scan in 1st block again
      this_size.current_block = this_size.first_block;
      if (!build_list(this_block, this_size)) this_size.next_slot = 0;
    }
  }

  private static void
  zeromarks(VM_Processor st) 
  {
    for (int i = 0; i < GC_SIZES; i++) {

      //  NEED TO INITIALIZE THE BLOCK AFTER CURRENT_BLOCK, FOR
      //  EACH SIZE, SINCE THIS WAS NOT DONE DURING MUTATOR EXECUTION
      VM_BlockControl this_block = VM_Magic.addressAsBlockControl(blocks[st.sizes[i].current_block]);

      int next = this_block.nextblock;
      while (next != OUT_OF_BLOCKS) {
        this_block = VM_Magic.addressAsBlockControl(blocks[next]);
        if (Debug && (this_block.mark == null))
          VM.sysWrite(" In collect, found block with no mark \n");
        VM_Memory.zero(VM_Magic.objectAsAddress(this_block.mark),
           VM_Magic.objectAsAddress(this_block.mark) + this_block.mark.length);
        this_block.live = false;
        next = this_block.nextblock;
      }
    }
  }


  /**
   * called from CollectorThread.run() to perform a collection.
   * Does a Minor collection followed by a Major collection if the
   * count of available blocks goes too low.
   * All GC threads execute in parallel.
   */
  public static void
    collect () {

    double tempTime;
    int blocksBefore = blocks_available;
    int mypid = VM_Processor.getCurrentProcessorId();// id of processor running on
    VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    // set running threads context regs so that a scan of its stack
    // will start at the caller of collect (ie. VM_CollectorThread.run)
    //
    int fp = VM_Magic.getFramePointer();
    int caller_ip = VM_Magic.getReturnAddress(fp);
    int caller_fp = VM_Magic.getCallerFramePointer(fp);
    VM_Thread.getCurrentThread().contextRegisters.setInnermost( caller_ip, caller_fp );

    gcCollectMinor();

    if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
    VM_CollectorThread.gcBarrier.rendezvous();
    if (RENDEZVOUS_WAIT_TIME) myThread.rendezvousWaitTime += VM_Time.now() - tempTime;

    if (TRACE && (myThread.gcOrdinal == 1))
      VM_Scheduler.trace("collect: after Minor Collection","blocks_available =",blocks_available);

    if (outOfSmallHeapSpace) {
      if (myThread.gcOrdinal == 1) {
	VM_Scheduler.trace("collect:","Out Of Memory - could not complete Minor Collection");
	VM_Scheduler.trace("collect:","blocks_available (before) =",blocksBefore);
	VM_Scheduler.trace("collect:","blocks_available (after)  =",blocks_available);
	reportBlocks();
	outOfMemory();
      }
      else return;   // quit - error
    }

    if (outOfLargeSpaceFlag || (blocks_available < majorCollectionThreshold)) {
      if (VM.verboseGC && myThread.gcOrdinal == 1) {
	if (outOfLargeSpaceFlag)
	  VM_Scheduler.trace("Major Collection Necessory:", "To reclaim Large Space");
	else
	  VM_Scheduler.trace("Major Collection Necessory:", "blocks available =",blocks_available);
      }

      gcCollectMajor();	

      if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
      VM_CollectorThread.gcBarrier.rendezvous();
      if (RENDEZVOUS_WAIT_TIME) myThread.rendezvousWaitTime += VM_Time.now() - tempTime;

      if (myThread.gcOrdinal == 1) {
	if (TRACE)
	  VM_Scheduler.trace("collect: after Major Collection","blocks_available =",blocks_available);
	if (VM.verboseGC && (blocks_available < majorCollectionThreshold))
	  VM_Scheduler.trace("WARNING","after collection low blocks available =",blocks_available);

	majorCollection = false;
	outOfSmallHeapSpace = false;
	outOfLargeSpaceFlag = false;
	gcInProgress    = false;
	initGCDone      = false;
      }
      if (TRACE) VM_Scheduler.trace("collect:","returning");
    }
  }  // collect

  static void
    dumpblocks () {
    VM_Processor st = VM_Processor.getCurrentProcessor();
    VM.sysWrite(first_freeblock);
    VM.sysWrite(" is the first freeblock index \n");
    for (int iii = 0; iii < GC_SIZES; iii++) {
      VM.sysWrite(iii);
      VM.sysWrite("th VM_SizeControl first_block = " );
      VM.sysWrite( st.sizes[iii].first_block);
      VM.sysWrite(" current_block = "); 
      VM.sysWrite(st.sizes[iii].current_block);
      VM.sysWrite("\n\n");
    }
       
    for (int iii = 0; iii < num_blocks; iii++) {
      VM.sysWrite(iii);
      VM.sysWrite("th VM_BlockControl    ");
      if (VM_Magic.addressAsBlockControl(blocks[iii]).live) VM.sysWrite("    live"); 
      else VM.sysWrite("not live");
      VM.sysWrite("   "); 
      VM.sysWrite(" baseaddr = "); VM.sysWrite(VM_Magic.addressAsBlockControl(blocks[iii]).baseAddr);
      VM.sysWrite(" \nnextblock = "); VM.sysWrite(VM_Magic.addressAsBlockControl(blocks[iii]).nextblock);
      VM.sysWrite("\n");
    }
  }  // dumpblocks

  public static long
    freeLargeSpace () {

    int total = 0;
    for (int i = 0 ; i < largeSpacePages;) {
      if (largeSpaceAlloc[i] == 0) {
	total++;
	i++;
      }
      else i = i + largeSpaceAlloc[i]; // negative value in largeSpA
    }
      
    return (total * 4096);       // number of bytes free in largespace
  }  // freeLargeSpace


  public static void
    freeLargeSpaceDetail () {

    int total = 0;
    int largelarge = 0;
    int largesize = 0;
    int i,templarge = 0;
    VM.sysWrite(largeHeapSize);
    VM.sysWrite(" is the large object heap size in bytes \n");
    for (i = 0 ; i < largeSpacePages;) {
      if (largeSpaceAlloc[i] == 0) {
	templarge++;
	if (templarge > largesize) largesize = templarge;
	total++;
	i++;
      }
      else {
	templarge = 0;
	int temp = largeSpaceAlloc[i];
	if (temp < GC_LARGE_SIZES) countLargeLive[temp]++;
	else {
	  VM.sysWrite(temp);
	  VM.sysWrite(" pages of a very large object \n");
	  largelarge++;
	}
	i = i + largeSpaceAlloc[i]; // negative value in largeSpA
      }
    }
      
    VM.sysWrite(total);
    VM.sysWrite(" pages free in large space \n ");
    VM.sysWrite(largesize);
    VM.sysWrite(" is largest block in pages available \n");
      
    for (i = 0; i < GC_LARGE_SIZES; i++) {
      if (countLargeLive[i] > 0) {
	VM.sysWrite(countLargeLive[i]);
	VM.sysWrite(" large objects of size ");
	VM.sysWrite(i);
	VM.sysWrite(" live \n");
	countLargeLive[i] = 0;	// for next time
      }
    }
    VM.sysWrite(largelarge);
    VM.sysWrite(" very large objects live \n ");
      
  }  // freeLargeSpaceDetail


  static int total_blocks_in_use; // count blocks in use during this calculation

  public static long
    freeSmallSpace (VM_Processor st) {

    int total = 0;
    int i, next, temp;
    VM_BlockControl this_block;
      
    for (i = 0; i < GC_SIZES; i++) {
      countSmallFree[i] = 0;
      countSmallBlocksAlloc[i] = 1;
      this_block = VM_Magic.addressAsBlockControl(blocks[st.sizes[i].current_block]);
      total_blocks_in_use++;
      temp = (int)emptyof(i, this_block.mark);
      countSmallFree[i] += temp;
      total+= temp;
      next = this_block.nextblock;
      while (next != OUT_OF_BLOCKS) {
	this_block = VM_Magic.addressAsBlockControl(blocks[next]);
	total_blocks_in_use++;
	temp = (int)emptyof(i, this_block.mark);
	total += temp;
	countSmallFree[i] += temp;
	countSmallBlocksAlloc[i] ++;
	next = this_block.nextblock;
      }
    }
     
    return total;
  }  // freeSmallSpace


  // START NURSERY GARBAGE COLLECTION ROUTINES HERE

  /**
   * Process write buffers for the current processor. called by
   * each collector thread during Minor collections.
   */
  static void
    gc_processWriteBuffers () {
    VM_WriteBuffer.processWriteBuffer(VM_Processor.getCurrentProcessor());
  }

  /**
   * check that write buffers still empty, if not print diagnostics & reset
   * ...we seem to get some entries after major collections ????
   */
  static void
    gc_checkWriteBuffers () {
    VM_WriteBuffer.checkForEmpty(VM_Processor.getCurrentProcessor());
  }

  /**
   * Process an object reference during Minor collections.
   * If it points to an object in the Nursery, check if already
   * marked & forwarded. If not, mark it, copy the object
   * to Mature space, set its forwarding address, and add its reference
   * to the work queue for later scanning. Update the reference
   * to point to the ToSpace copy of the object.
   * <p>
   * If the reference points to a Large Space object, check its "age".
   * If "old" skip. If not (allocated this mutator cycle), check if
   * already marked (visited), and if not, mark it and add to the
   * work queue for scanning.
   * <p>
   * BootImage and Mature Space objects are skipped during Minor Collections
   *
   * @param location  address of the reference to process
   */
  static void
    gc_processPtrField ( int location ) {

    int objRef = VM_Magic.getMemoryWord( location );

    if (objRef == VM_NULL) return;

    // always process objects in the Nursery (forward if not already forwarded)
    if ( objRef >= minNurseryRef && objRef <= maxNurseryRef ) {
      VM_Magic.setMemoryWord( location, gc_copyAndScanObject( objRef ) );
      return;
    }

    // for minor collections skip old objects.  that leaves NEW Large objects
    // to be marked and scanned.  If we later support direct allocations into
    // the non moving Old space this will have to change!
    //
    // ...no longer test if <= maxLargeRef since end of LargeSpace may be
    // beyond 0x80000000 and java signed integer compares will be wrong
    //
    if ( objRef >= minLargeRef ) {
      int tref = objRef + OBJECT_HEADER_OFFSET;
      int page_num = (tref - largeHeapStartAddress  ) >> 12;
      if ( largeSpaceGen[page_num] == 0 ) {  // new large object
	if (!gc_setMarkLarge(tref)) {
	  // we marked it, so put to workqueue
	  VM_GCWorkQueue.putToWorkBuffer( objRef );
	}
      }
    }
    return;  // skip Bootimage, OldSpace, & Old Large objects
  }  // processPtrField

  /**
   * Processes live objects in Nursery (FromSpace) that need to be marked,
   * copied and forwarded during Minor collection.  Returns the new address
   * of the object Mature Space (ToSpace).  If the object was not previously
   * marked, then the invoking collector thread will do the copying and
   * enqueue the object on the work queue of objects to be scanned.
   *
   * @param fromRef Reference to object in Nursery
   *
   * @return the address of the Object in Mature Space
   */
  static int
    gc_copyAndScanObject ( int fromRef ) {
    VM_Type type;
    int     full_size;
    int     statusWord;   // original status word from header of object to be copied
    int     toRef;        // address/ref of object in MatureSpace (as int)
    int     toAddress;    // address of header of object in MatureSpace (as int)
    int     fromAddress;  // address of header of object in FromSpace (as int)
    boolean assertion;

    if (GCDEBUG_PARALLEL && !validFromRef( fromRef ))
      VM_Scheduler.trace("VM_Allocator","copyAndScanObject, invalid fromref =",fromRef);

    while (true) {
      toRef = VM_Synchronization.fetchAndMarkBusy(VM_Magic.addressAsObject(fromRef), OBJECT_STATUS_OFFSET);
      VM_Magic.isync();   // prevent instructions moving infront of fetchAndMark

      // if toRef is "marked" then object has been or is being copied
      if ( (toRef & OBJECT_GC_MARK_MASK) != MARK_VALUE ) break;
      else {
	// if forwarding ptr == "busy pattern" object is being copied by another
	// GC thread, and wait (should be very short) for valid ptr to be set
	if (COUNT_COLLISIONS && (toRef == BEING_FORWARDED_PATTERN ))
	  collisionCount++;
	while ( toRef == BEING_FORWARDED_PATTERN ) {
	  toRef = VM_Magic.getMemoryWord(fromRef+OBJECT_STATUS_OFFSET);
	}
	// prevent following instructions from being moved in front of waitloop
	VM_Magic.isync();
	if (VM.VerifyAssertions) {
	  if (MARK_VALUE == 0)
	    assertion = ((toRef & 3)==0) && validRef(toRef);
	  else
	    assertion = ((toRef & 3)==1) && validRef(toRef & ~3);
	  if (!assertion) {
	    VM_Scheduler.trace("   copyAndScanObject", "loser - invalid toRef =",toRef);
	    VM_Scheduler.trace("   copyAndScanObject", "loser - invalid fromref =",fromRef);
	  }
	  VM.assert(assertion);  
	}

	// at this point, either a valid forward pointer is in toRef, or else
	// the original status word, ==> that copy into old space failed and
	// major collection is required.
	//
	if ((toRef & OBJECT_GC_MARK_MASK) == MARK_VALUE) {
	  // return forwarding pointer to caller
	  if (MARK_VALUE == 0)
	    return toRef;
	  else
	    return toRef & ~3;   // mask out markbit
	}
      }
    }

    // copy object or array, set forwarding pointer and add to buffer for scanning

    // fetchAndMarkBusy returned a word NOT marked busy, then it has returned
    // the original status word (ie lock bits, thread id etc) and replaced it
    // with the the BEING_FORWARDED_PATTERN (which has the mark bit set).
    //
    statusWord = toRef;
    type = VM_Magic.getObjectType(VM_Magic.addressAsObject(fromRef));
    if (VM.VerifyAssertions) {
      boolean foo = validRef(VM_Magic.objectAsAddress(type));
      if (!foo) {
	VM.sysWrite(" Bad type for ref found  -  ref is ");
	VM.sysWrite(fromRef);
	VM.sysWrite(" type is ");
	VM.sysWrite(VM_Magic.objectAsAddress(type));
	VM.sysWrite(" tib  is ");
	VM.sysWrite(VM_Magic.getMemoryWord(fromRef + OBJECT_TIB_OFFSET));
	VM.sysWrite("\n");
	VM_Magic.setMemoryWord(fromRef + OBJECT_STATUS_OFFSET, statusWord);
	return fromRef;
      }
    }

    if ( type.isClassType() ) {
      full_size = type.asClass().getInstanceSize();

      if ((toAddress = gc_getMatureSpace(full_size)) == 0) {
	// reach here means that no space was available for this thread
        // in mature, noncopying space, therefore 1. turn on outOfSmallHeapSpace
        // 2. reset status to original value, which will be unmarked, and 
        // 3. return original toRef value - so calling code is unchanged.
        // XXXX Might need sync here, but we don't think so.
	VM_Magic.setMemoryWord(fromRef+OBJECT_STATUS_OFFSET, statusWord);
	outOfSmallHeapSpace = true;
	return fromRef;
      }

      // position Toref to 4 beyond end of object
      toRef = toAddress + full_size - SCALAR_HEADER_SIZE - OBJECT_HEADER_OFFSET;
      // position from to start of object data in FromSpace
      // remember, header is to right, ref is 4 bytes beyond header
      fromAddress = fromRef + OBJECT_HEADER_OFFSET + SCALAR_HEADER_SIZE - full_size;
      // now copy object (including the overwritten status word)
      VM_Memory.aligned32Copy( toAddress, fromAddress, full_size );

    }
    else {
      if (VM.VerifyAssertions) VM.assert(type.isArrayType());
      int num_elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(fromRef));
      full_size = type.asArray().getInstanceSize(num_elements);
      full_size = (full_size + 3) & ~3;;  //need Magic to roundup

      if ((toAddress = gc_getMatureSpace(full_size)) == 0) {
	// reach here means that no space was available for this thread
        // in mature, noncopying space, therefore 1. turn on outOfSmallHeapSpace
        // 2. reset status to original value, which will be unmarked, and 
        // 3. return original toRef value - so calling code is unchanged.
        // XXXX Might need sync here, but we don't think so.
	VM_Magic.setMemoryWord(fromRef+OBJECT_STATUS_OFFSET, statusWord);
	outOfSmallHeapSpace = true;
	return fromRef;
      }

      toRef = toAddress - OBJECT_HEADER_OFFSET;
      fromAddress = fromRef+OBJECT_HEADER_OFFSET;

      // now copy object (including the overwritten status word)
      VM_Memory.aligned32Copy( toAddress, fromAddress, full_size );

      // sync all arrays of ints - must sync moved code
      if (type == arrayOfIntType) {
	VM_Memory.sync(toAddress, full_size);
      }
    }

    // replace status word in copied object, forcing writebarrier bit on (bit 30)
    // set mark bit to "unmarked" state
    if (MARK_VALUE == 0)
      // "marked" = 0, set markbit on to designate "unmarked"
      VM_Magic.setMemoryWord(toRef + OBJECT_STATUS_OFFSET,
			     statusWord | (OBJECT_BARRIER_MASK | OBJECT_GC_MARK_MASK) );
    else 
      // "marked" = 1, markbit in orig. statusword should be 0
      VM_Magic.setMemoryWord(toRef + OBJECT_STATUS_OFFSET,
			     statusWord | OBJECT_BARRIER_MASK );

    VM_Magic.sync(); // make changes viewable to other processors 

    // set status word in old/from object header to forwarding address with
    // the low order markbit set to "marked", if MARK_VALUE=0, storing an aligned
    // pointer will make it "marked".  If multiple GC threads, this store will overwrite
    // the BEING_FORWARDED_PATTERN and let other waiting/spinning GC threads proceed.
    if ( MARK_VALUE == 0 )
      VM_Magic.setMemoryWord(fromRef+OBJECT_STATUS_OFFSET, toRef);
    else
      VM_Magic.setMemoryWord(fromRef+OBJECT_STATUS_OFFSET, toRef | OBJECT_GC_MARK_MASK);

    // following sync is optional, not needed for correctness
    // for not let changes go out whenever...
    // VM_Magic.sync(); // make changes viewable to other processors 

    // add copied object to GC work queue, so it will be scanned later
    VM_GCWorkQueue.putToWorkBuffer( toRef );

    // return ref for copied object or array
    return toRef;
  } // gc_copyAndScanObject

  /**
   * Scan static variables (JTOC) for object references during Minor collections.
   * Executed by all GC threads in parallel, with each doing a portion of the JTOC.
   * <p>
   * This version is for Minor collections.  It copies & scans live nursry objects
   * and marks and scans NEW live large space objects.  Does NOT process objects
   * in mature space (old) or the bootimage (assumed old).
   */
  static void
    gc_scanStaticsMinor () {
    int numSlots = VM_Statics.getNumberOfSlots();
    int slot, ref, tref, page_num;  
    int segmentSize = 512;
    int stride, start, end;

    stride = segmentSize * VM_Scheduler.numProcessors;

    start = (VM_Processor.getCurrentProcessorId() - 1) * segmentSize;
    
    while ( start < numSlots ) {
      end = start + segmentSize;
      if (end > numSlots)
	end = numSlots;  // doing last segment of JTOC

      for ( slot=start; slot<end; slot++ ) {

	if ( ! VM_Statics.isReference(slot) ) continue;
      
	// slot contains a ref of some kind
	ref = VM_Statics.getSlotContentsAsInt(slot);
	if ( ref == VM_NULL ) continue;
      
	if ( ref >= minNurseryRef && ref <= maxNurseryRef ) {
	  VM_Statics.setSlotContents( slot, gc_copyAndScanObject(ref) );
	  continue;
	}
      
	// a minor collection: mark and scan (and age) only NEW large objects
	if ( ref >= minLargeRef ) {
	  tref = ref + OBJECT_HEADER_OFFSET;
	  page_num = (tref - largeHeapStartAddress  ) >> 12;
	  if ( largeSpaceGen[page_num] == 0 ) {  // new large object
	    if (!gc_setMarkLarge(tref)) {
	      // we marked it, so put to workqueue for later scanning
	      VM_GCWorkQueue.putToWorkBuffer( ref );
	    }
	  }
	}
      }  // end of for loop
      start = start + stride;
    }  // end of while loop
  }  // gc_scanStaticsMinor


  /**
   * scan object or array for references - Minor Collections
   */
  static void
    gc_scanObjectOrArray ( int objRef ) {
    VM_Type    type;

    // First process the header
    //   The header has one pointer in it - namely the pointer to the TIB (type info block).
    // 
    gc_processPtrField(objRef + OBJECT_TIB_OFFSET);   

    type = VM_Magic.getObjectType(VM_Magic.addressAsObject(objRef));
    if ( type.isClassType() ) {
      int[] referenceOffsets = type.asClass().getReferenceOffsets();
      for(int i = 0, n=referenceOffsets.length; i < n; i++) {
	gc_processPtrField( objRef + referenceOffsets[i] );
      }
    }
    else {
      if (VM.VerifyAssertions) VM.assert(type.isArrayType());
      VM_Type elementType = type.asArray().getElementType();
      if (elementType.isReferenceType()) {
	int num_elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(objRef));
	int location = objRef;    // for arrays = address of [0] entry
	int end      = objRef + num_elements * 4;
	while ( location < end ) {
	  gc_processPtrField( location );
	  location = location + 4;  // should use "size_of_pointer" (for 64-bits)
	}
      }
    }
  }

  // scan a VM_Processor object. Called by each collector thread during Minor
  // collections to scan the VM_Processor is running on. Special things
  // must be done, sometimes.  Looks like we allow the write buffer to move,
  // but this should never happen (it is always in non-moving large space now)
  //
  static void
    gc_scanProcessor ()  {
    int               sta, oldbuffer, newbuffer;
    VM_Processor   st;

    st = VM_Processor.getCurrentProcessor();
    sta = VM_Magic.objectAsAddress(st);


    // scan system thread object to force "interior" objects to be copied, marked, and
    // queued for later scanning.
    oldbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    gc_scanThread(sta);      // scan Processor with thread routine (its OK)

    // if writebuffer moved, adjust interior pointers
    newbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    if (oldbuffer != newbuffer) {
      VM_Scheduler.trace("VM_Allocator","write buffer cop ied",st.id);
      st.modifiedOldObjectsMax = newbuffer + (st.modifiedOldObjectsMax - oldbuffer);
      st.modifiedOldObjectsTop = newbuffer + (st.modifiedOldObjectsTop - oldbuffer);
    }

  }  // scanProcessor

  // input:  object ref of VM_Thread or object derived from VM_Thread (like java/lang/Thread)
  // process pointer fields, skipping interior pointers
  // ...VM_Thread no longer (11/1/98) has interior pointers, so a special scan routine
  // is not necessary, the general scanObjectOrArray could be used
  //
  static void
    gc_scanThread ( int objRef ) {
    VM_Type    type;
    int        offset;

    // First process the header
    gc_processPtrField(objRef + OBJECT_TIB_OFFSET);   

    type = VM_Magic.getObjectType(VM_Magic.addressAsObject(objRef));

    int[] referenceOffsets = type.asClass().getReferenceOffsets();
    for(int i = 0, n=referenceOffsets.length; i < n; i++) {
      offset = referenceOffsets[i];
      gc_processPtrField( objRef + offset );
    }
  }

  // For copying system objects like VM_Thread & VM_Processor objects that
  // do not have to be queued for scanning (they are explicitly scanned)
  //
  static int
    gc_copyObject ( int fromRef ) {
    VM_Type type;
    int     full_size;
    int     statusWord;   // original status word from header of object to be copied
    int     toRef;        // address/ref of object in MatureSpace (as int)
    int     toAddress;    // address of header of object in MatureSpace (as int)
    int     fromAddress;  // address of header of object in FromSpace (as int)
    boolean assertion;

    // attempt to mark object, get back original status word
    statusWord = VM_Synchronization.fetchAndMarkBusy(VM_Magic.addressAsObject(fromRef), OBJECT_STATUS_OFFSET);
    VM_Magic.isync();   // prevent instructions moving infront of fetchAndMark

    // if statusWord is "marked" then object has been or is being copied
    if ( (statusWord & OBJECT_GC_MARK_MASK) == MARK_VALUE ) {

      // if forwarding ptr == "busy pattern" object is being copied by another
      // GC thread, and wait (should be very short) for valid ptr to be set
      if (COUNT_COLLISIONS && (statusWord == BEING_FORWARDED_PATTERN ))
	collisionCount++;
      while ( statusWord == BEING_FORWARDED_PATTERN ) {
	statusWord = VM_Magic.getMemoryWord(fromRef+OBJECT_STATUS_OFFSET);
      }
      // prevent following instructions from being moved in front of waitloop
      VM_Magic.isync();

      if (VM.VerifyAssertions) {
	if (MARK_VALUE==0)
	  assertion = ((statusWord & 3)==0) && validRef(statusWord);
	else
	  assertion = ((statusWord & 3)==1) && validRef(statusWord & ~3);
	if (!assertion) {
	  VM_Scheduler.trace("   copyObject", "looser - invalid statusWord(toRef) =",statusWord);
	  VM_Scheduler.trace("   copyObject", "looser - invalid fromref =",fromRef);
	}
	VM.assert(assertion);  
      }

      // return forwarding pointer to caller
      if (MARK_VALUE==0)
	return statusWord;
      else
	return statusWord & ~3;   // mask off markbit & busy bit
    }

    // copy object & set forwarding ptr in status word in FromSpace object.

    // fetchAndMarkBusy returned a word NOT marked busy, then it has returned
    // the original status word (ie lock bits, thread id etc) and replaced it
    // with the the BEING_FORWARDED_PATTERN (which has the mark bit set).

    type = VM_Magic.getObjectType(VM_Magic.addressAsObject(fromRef));
    if (VM.VerifyAssertions) VM.assert(validRef(VM_Magic.objectAsAddress(type)));
    if (VM.VerifyAssertions) VM.assert(type.isClassType());
    full_size = type.asClass().getInstanceSize();

    if ((toAddress = gc_getMatureSpace(full_size)) == 0) {
      // reach here means that no space was available for this thread
      // in mature, noncopying space, therefore 1. turn on outOfSmallHeapSpace
      // 2. reset status to original value, which will be unmarked, and 
      // 3. return original toRef value - so calling code is unchanged.
      // XXXX Might need sync here, but we don't think so.

      // need to fix - like copyObjectOrArray
      VM.sysWrite("XXX gc_copyObject: getMatureSpace returns 0: BROKEN\n");
      VM.sysWrite("XXX gc_copyObject: getMatureSpace returns 0: BROKEN\n");

      VM_Magic.setMemoryWord(fromRef+OBJECT_STATUS_OFFSET, statusWord);
      outOfSmallHeapSpace = true;
      return fromRef;
    }

    // position Roref to 4 beyond end of object
    toRef = toAddress + full_size - SCALAR_HEADER_SIZE - OBJECT_HEADER_OFFSET;
    // position from to start of object data in FromSpace
    // remember, header is to right, ref is 4 bytes beyond header
    fromAddress = fromRef + OBJECT_HEADER_OFFSET + SCALAR_HEADER_SIZE - full_size;

    // copy object...before status word modified
    VM_Memory.aligned32Copy( toAddress, fromAddress, full_size );

    // replace status word in copied object, forcing writebarrier bit on (bit 30)
    // set mark bit to "unmarked" state
    if (MARK_VALUE == 0)
      // "marked" = 0, set markbit on to designate "unmarked"
      VM_Magic.setMemoryWord(toRef + OBJECT_STATUS_OFFSET,
			     statusWord | (OBJECT_BARRIER_MASK | OBJECT_GC_MARK_MASK) );
    else 
      // "marked" = 1, markbit in orig. statusword should be 0
      VM_Magic.setMemoryWord(toRef + OBJECT_STATUS_OFFSET,
			     statusWord | OBJECT_BARRIER_MASK );
     
    // sync here to ensure copied object is intact, before setting forwarding ptr
    VM_Magic.sync(); // make changes viewable to other processors 

    // set status word in old/from object header to forwarding address with
    // the low order markbit set to "marked", if MARK_VALUE=0, storing an aligned
    // pointer will make it "marked".  If multiple GC threads, this store will overwrite
    // the BEING_FORWARDED_PATTERN and let other waiting/spinning GC threads proceed.
    if ( MARK_VALUE == 0 )
      VM_Magic.setMemoryWord(fromRef+OBJECT_STATUS_OFFSET, toRef);
    else
      VM_Magic.setMemoryWord(fromRef+OBJECT_STATUS_OFFSET, toRef | OBJECT_GC_MARK_MASK);
     
    // following sync is optional, not needed for correctness
    // for not let changes go out whenever...
    // VM_Magic.sync(); // make changes viewable to other processors 
      
    return toRef;
  }  // copyObject

  // Scans all threads in the VM_Scheduler threads array.  A threads stack
  // will be copied if necessary and any interior addresses relocated.
  // Each threads stack is scanned for object references, which will
  // becomes Roots for a collection.
  //
  // All collector threads execute here in parallel, and compete for
  // individual threads to process.  Each collector thread processes
  // its own thread object and stack.
  //
  static void 
    gc_scanThreads ()  {
    int        i, ta, myThreadId, fp;
    VM_Thread  t;
    int[]      oldstack;
    
    // get ID of running GC thread
    myThreadId = VM_Thread.getCurrentThread().getIndex();
    
    for ( i=0; i<VM_Scheduler.threads.length; i++ ) {
      t = VM_Scheduler.threads[i];
      ta = VM_Magic.objectAsAddress(t);
      
      if ( t == null )
	continue;
      
      // let each GC thread scan its own thread object to force updating
      // of the header TIB pointer, and possible copying of register arrays
      // stacks are supposed to be in the bootimage (for now)
      
      if ( i == myThreadId ) {  // at thread object for running gc thread
	
	// GC threads are assumed not to have native processors.  if this proves
	// false, then we will have to deal with its write buffers
	//
	if (VM.VerifyAssertions) VM.assert(t.nativeAffinity == null);
	
	// all threads should have been copied out of fromspace(Nursery) earlier
	if (VM.VerifyAssertions) VM.assert( !(ta >= minNurseryRef && ta <= maxNurseryRef) );
	
	if (VM.VerifyAssertions) oldstack = t.stack;    // for verifying  gc stacks not moved
	gc_scanThread(ta);     // will copy copy stacks, reg arrays, etc.
	if (VM.VerifyAssertions) VM.assert(oldstack == t.stack);
	
	if (t.jniEnv != null) gc_scanObjectOrArray(VM_Magic.objectAsAddress(t.jniEnv));
	
	gc_scanObjectOrArray(VM_Magic.objectAsAddress(t.contextRegisters));
	gc_scanObjectOrArray(VM_Magic.objectAsAddress(t.hardwareExceptionRegisters));
	
	if (debugNative) VM_Scheduler.trace("VM_Allocator","Collector Thread scanning own stack",i);
	VM_ScanStack.scanStack(t,VM_NULL, true /*relocate_code*/);

	
	continue;
      }

      if ( debugNative && t.isGCThread ) {
	VM_Scheduler.trace("scanThreads:","at GC thread for processor id =",
			   t.processorAffinity.id);
	VM_Scheduler.trace("scanThreads:","                    gcOrdinal =",
			   VM_Magic.threadAsCollectorThread(t).gcOrdinal);
      }

      // skip other collector threads participating (have ordinal number) in this GC
      if ( t.isGCThread && (VM_Magic.threadAsCollectorThread(t).gcOrdinal > 0) )
	continue;
      
      // have mutator thread, compete for it with other GC threads
      if ( VM_GCLocks.testAndSetThreadLock(i) ) {
	
	if (debugNative) VM_Scheduler.trace("VM_Allocator","processing mutator thread",i);
	
	// all threads should have been copied out of fromspace earlier
	if (VM.VerifyAssertions) VM.assert( !(ta >= minNurseryRef && ta <= maxNurseryRef) );
	
	// scan thread object to force "interior" objects to be copied, marked, and
	// queued for later scanning.
	oldstack = t.stack;    // remember old stack address before scanThread
	gc_scanThread(ta);
	
	// if stack moved, adjust interior stack pointers
	if ( oldstack != t.stack ) {
	  t.fixupMovedStack(VM_Magic.objectAsAddress(t.stack) - VM_Magic.objectAsAddress(oldstack));
	}
	
	// the above scanThread(t) will have marked and copied the threads JNIEnvironment object,
	// but not have scanned it (likely queued for later scanning).  We force a scan of it now,
	// to force copying of the JNI Refs array, which the following scanStack call will update,
	// and we want to ensure that the updates go into the "new" copy of the array.
	//
	if (t.jniEnv != null) gc_scanObjectOrArray(VM_Magic.objectAsAddress(t.jniEnv));
	
	// Likewise we force scanning of the threads contextRegisters, to copy 
	// contextRegisters.gprs where the threads registers were saved when it yielded.
	// Any saved object references in the gprs will be updated during the scan
	// of its stack.
	//
	gc_scanObjectOrArray(VM_Magic.objectAsAddress(t.contextRegisters));
	gc_scanObjectOrArray(VM_Magic.objectAsAddress(t.hardwareExceptionRegisters));

	// all threads in "unusual" states, such as running threads in
	// SIGWAIT (nativeIdleThreads, nativeDaemonThreads, passiveCollectorThreads),
	// set their ContextRegisters before calling SIGWAIT so that scans of
	// their stacks will start at the caller of SIGWAIT
	//
	// fp = -1 case, which we need to add support for again
	// this is for "attached" threads that have returned to C, but
	// have been given references which now reside in the JNIEnv sidestack
	//


	if (TRACE) VM_Scheduler.trace("VM_Allocator","scanning stack for thread",i);
	//gc_scanStack(t,fp);
	VM_ScanStack.scanStack(t,VM_NULL, true /*relocate_code*/);

	//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
	// alternate implementation of jni
	// if this thread has an associated native VP, then move its writebuffer entries 
	// in the workqueue for later scanning
	//
	if ( t.nativeAffinity != null ) 
	  VM_WriteBuffer.moveToWorkQueue(t.nativeAffinity);
	//-#else
	// default implementation of jni
	//  do nothing here, write buffer entries moved in prepare...ForGC()
	//-#endif

      }  // (if true) we seized got the thread to process

      else continue;  // some other gc thread has seized this thread
      
    }  // end of loop over threads[]
    
  }  // gc_scanThreads

  // END OF NURSERY GARBAGE COLLECTION ROUTINES HERE

  private static void
    countLargeObjects () {
    int i,num_pages,countLargeOld;
    int contiguousFreePages,maxContiguousFreePages;

    for (i =  0; i < GC_LARGE_SIZES; i++) countLargeAlloc[i] = 0;
    countLargeOld = contiguousFreePages = maxContiguousFreePages = 0;

    for (i =  0; i < largeSpacePages;) {
      num_pages = largeSpaceAlloc[i];
      if (num_pages == 0) {     // no large object found here
        countLargeAlloc[0]++;   // count free pages in entry[0]
        contiguousFreePages++;
        i++;
      }
      else {    // at beginning of a large object
        if (num_pages < GC_LARGE_SIZES-1) countLargeAlloc[num_pages]++;
        else countLargeAlloc[GC_LARGE_SIZES - 1]++;
        if ( contiguousFreePages > maxContiguousFreePages )
          maxContiguousFreePages = contiguousFreePages;
        contiguousFreePages = 0;
        i = i + num_pages;       // skip to next object or free page
      }
    }
    if ( contiguousFreePages > maxContiguousFreePages )
      maxContiguousFreePages = contiguousFreePages;

    VM.sysWrite("Large Objects Allocated - by num pages\n");
    for (i = 0; i < GC_LARGE_SIZES-1; i++) {
      VM.sysWrite("pages ");
      VM.sysWrite(i);
      VM.sysWrite(" count ");
      VM.sysWrite(countLargeAlloc[i]);
      VM.sysWrite("\n");
    }
    VM.sysWrite(countLargeAlloc[GC_LARGE_SIZES-1]);
    VM.sysWrite(" large objects ");
    VM.sysWrite(countLargeAlloc[GC_LARGE_SIZES-1]);
    VM.sysWrite(" large objects ");
    VM.sysWrite(GC_LARGE_SIZES-1);
    VM.sysWrite(" pages or more.\n");
    VM.sysWrite(countLargeAlloc[0]);
    VM.sysWrite(" Large Object Space pages are free.\n");
    VM.sysWrite(maxContiguousFreePages);
    VM.sysWrite(" is largest block of contiguous free pages.\n");
    VM.sysWrite(countLargeOld);
    VM.sysWrite(" large objects are old.\n");

  }  // countLargeObjects()

  static boolean
    gc_isOldObject (int dummy) {
    VM.assert(NOT_REACHED);
    return false;
  } 
  
  static boolean
    gc_isLive (int ref) {
    VM.assert(NOT_REACHED);
    return false;
  }  // isLive
  
  static int
    gc_makeLive(int ref) {
    VM.assert(NOT_REACHED);
    return 0;
  }  // makeLive
  
  static void
    gc_markLive (int ref) {
    VM.assert(NOT_REACHED);
  }  // gc_markLive
  
  static boolean
    validRef ( int ref ) {
    if (ref >= bootStartAddress && ref <= largeHeapEndAddress) return true;
    else return false;
  }
  
  private static int
    makeObjectFromStorage (int storage, int tibptr, int size) {
    int ref = storage + size - (SCALAR_HEADER_SIZE + OBJECT_HEADER_OFFSET);
    VM_Magic.setMemoryWord(ref + OBJECT_TIB_OFFSET, tibptr);
    return ref;
  }
  
  private static int[]
    makeArrayFromStorage (int storage, int tibptr, int num_elements) {
    int ref = storage - OBJECT_HEADER_OFFSET;
    VM_Magic.setMemoryWord(ref + OBJECT_TIB_OFFSET, tibptr);
    VM_Magic.setMemoryWord(ref + ARRAY_LENGTH_OFFSET, num_elements);
    return VM_Magic.addressAsIntArray(ref);
  }
   
  // setupProcessor is called from the constructor of VM_Processor
  // to alloc allocation structs and collection write buffers
  // for the PRIMORDIAL processor, allocation structs are built
  // in init, and setupProcessor is called a second time from VM.boot.
  // this second call must cause writebuffer pointers to be initialized
  // see VM_WriteBuffer.setupProcessor().
  //
  static void
    setupProcessor (VM_Processor st) {
    int sizes_array_storage, sizes_storage;

    VM_WriteBuffer.setupProcessor( st );

    if (st.id == VM_Scheduler.PRIMORDIAL_PROCESSOR_ID)
      return;  // sizes, etc built in init()

    // Get VM_SizeControl array 
    //      GET STORAGE FOR sizes ARRAY FROM OPERATING SYSTEM
    if ((sizes_array_storage = VM.sysCall1(bootrecord.sysMallocIP,
					   GC_SIZES * 4 + ARRAY_HEADER_SIZE)) == 0) {
      VM.sysWrite(" In setupProcessor, call to sysMalloc returned 0 \n");
      VM.shutdown(1800);
    }

    if ((sizes_storage = VM.sysCall1(bootrecord.sysMallocIP,
				     (GC_SIZES * VM_SizeControl.Size)))  == 0) {
      VM.sysWrite(" In setupProcessor, call to sysMalloc returned 0 \n");
      VM.shutdown(1900);
    }

    //    The following line does THIS:  st.sizes =  new VM_SizeControl[GC_SIZES];
    //    for storage obtained from AIX rather than from our heap
    st.sizes = VM_Magic.addressAsSizeControlArray(
                 VM_Magic.objectAsAddress(makeArrayFromStorage(sizes_array_storage,
                   VM_Magic.getMemoryWord(VM_Magic.objectAsAddress
                     (VM_Scheduler.processors[VM_Scheduler.PRIMORDIAL_PROCESSOR_ID].sizes) 
                           + OBJECT_TIB_OFFSET), GC_SIZES)));

    for (int i = 0; i < GC_SIZES; i++) {
      //    The following line does THIS:  st.sizes[i] =  new VM_SizeControl;
      //    for storage obtained from AIX rather than from our heap
      st.sizes[i] = VM_Magic.addressAsSizeControl(
                      makeObjectFromStorage(sizes_storage + i * VM_SizeControl.Size, 
                        VM_Magic.getMemoryWord(VM_Magic.objectAsAddress
			(VM_Scheduler.processors[VM_Scheduler.PRIMORDIAL_PROCESSOR_ID]
			 .sizes[0]) + OBJECT_TIB_OFFSET), VM_SizeControl.Size));
      int ii = VM_Allocator.getnewblockx(i);
      st.sizes[i].first_block = ii;    // 1 block/size initially
      st.sizes[i].current_block = ii;
      st.sizes[i].ndx = i;		// to fit into old code
      build_list_for_new_block(VM_Magic.addressAsBlockControl(blocks[ii]), st.sizes[i]);
    }
    
    st.GC_INDEX_ARRAY = new VM_SizeControl[GC_MAX_SMALL_SIZE + 1];
    st.GC_INDEX_ARRAY[0] = st.sizes[0];   // for size = 0
    // set up GC_INDEX_ARRAY for this Processor
    int j = 1;
    for (int i = 0; i < GC_SIZES; i++) 
      for (; j <= GC_SIZEVALUES[i]; j++) st.GC_INDEX_ARRAY[j] = st.sizes[i];
  }

  static boolean
    validFromRef ( int ref ) {
    if ( ref >= minNurseryRef && ref <= maxNurseryRef ) return true;
    else return false;
  }

  // allocate buffer for allocates during traceback & call sysFail (gets stacktrace)
  // or sysWrite the message and sysExit (no traceback possible)
  //
  private static void
    crash (String err_msg) {
    int tempbuffer;
    VM.sysWrite("VM_Allocator.crash:\n");
    
    if ((tempbuffer = VM.sysCall1(bootrecord.sysMallocIP,
				  VM_Allocator.CRASH_BUFFER_SIZE)) == 0) {
      VM.sysWrite("VM_ALLOCATOR.crash() sysMalloc returned 0 \n");
      VM.shutdown(1800);
    }
    VM_Processor p = VM_Processor.getCurrentProcessor();
    p.localCurrentAddress = tempbuffer;
    p.localEndAddress = tempbuffer + VM_Allocator.CRASH_BUFFER_SIZE;
    VM_Memory.zero(tempbuffer, tempbuffer + VM_Allocator.CRASH_BUFFER_SIZE);
    VM.sysFail(err_msg);
  }

  public static void
    printclass (int ref) {
    VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));
    VM.sysWrite(type.getDescriptor());
  }

  /**
   * processFinalizerListElement is called from VM_Finalizer.moveToFinalizable
   * to process a FinalizerListElement (FLE) on the hasFinalizer list.  
   * <pre> 
   *   -if the FLE interger pointer -> to a marked/live object return true.
   *        For minor collections, if the object is in the Nursery, use
   *        its forwarding pointer to update the FLE integer pointer to 
   *        point to its new mature space location. For minor collections
   *        all "old" objects are considered "live"
   *   -if the FLE integer pointer -> to an unmarked/dead object:
   *        1. make it live again, copying to mature space if necessary
   *        2. set the FLE reference pointer to point to the object (to keep it live)
   *        3. enqueue the object for scanning, so that the finalization
   *           scan phase fill make live all objects reachable from the object
   *        return false.
   * </pre> 
   * Executed by ONE GC collector thread at the end of collection, after
   * all reachable object are marked and forwarded.
   *  
   * @param le  VM_FinalizerListElement to be processed
   */
  static boolean
    processFinalizerListElement (VM_FinalizerListElement le) {
    int ref = le.value;
    
    // For Minor Collections look for entries pointing to unreached Nursery objects,
    // copy the objects to mature space & and 
    // For minor GCs, FromSpace is the Nursery.
    //

    if ( ! majorCollection ) {

      if ( ref >= minNurseryRef && ref <= maxNurseryRef ) {
	int statusword = VM_Magic.getMemoryWord(ref + OBJECT_STATUS_OFFSET);
	if ( (statusword & OBJECT_GC_MARK_MASK) == MARK_VALUE ) {
	  // live, set le.value to forwarding address
	  le.value = statusword & ~3;
	  return true;
	}
	else {
	  // dead, mark, copy, and enque for scanning, and set le.pointer
	  le.pointer = VM_Magic.addressAsObject(gc_copyAndScanObject(ref));
	  le.value = -1;
	  return false;
	}
      }
    
      // for minor collections, objects in mature space are assumed live.
      // they are not moved, and le.value is OK
      
      if ( ref > smallHeapStartAddress && ref <= smallHeapEndAddress+4 ) return true;

    }   // end of Minor Collection procsssing of Nursery & Mature Space

    else {  // Major Collection procsssing of Nursery & Mature Space

      // should never see an object in the Nursery during Major Collections
      if ( ref >= minNurseryRef && ref <= maxNurseryRef )
	VM.assert(NOT_REACHED);


      if ( ref > smallHeapStartAddress && ref <= smallHeapEndAddress+4 ) {
	//  locate mark array entry for the object
	int  tref = ref + OBJECT_HEADER_OFFSET;
	int blkndx  = (tref - smallHeapStartAddress) >> LOG_GC_BLOCKSIZE ;
	VM_BlockControl  this_block = VM_Magic.addressAsBlockControl(blocks[blkndx]);
	int  offset   = tref - this_block.baseAddr;
	int  slotndx  = offset/this_block.slotsize;

	// if marked (ie live) return true, FLE is OK
	if (this_block.mark[slotndx] != 0)
	  return true;

	// is not live, ie now finalizable, so mark it live, set the pointer (ref)
	// field in the FLE (to ekeep live), enqueue for scanning, return false;
	this_block.mark[slotndx]  = 1;
	le.pointer = VM_Magic.addressAsObject(le.value);
	le.value = -1;
	VM_GCWorkQueue.putToWorkBuffer( ref );
	return false;
      }
    }  // end of Major Collection procsssing of Nursery & heap

    // if here FLE object should be in large space.  We have only see arrays,
    // which do not have finalizers, in large space. But for completeness,
    // we include code for the possibility of a large space object that
    // becomes finalizable.
    //
    if (VM.VerifyAssertions) VM.assert(ref >= minLargeRef);
    int tref = ref + OBJECT_HEADER_OFFSET;
    int page_num = (tref - largeHeapStartAddress ) >> 12;
    if (largeSpaceMark[page_num] != 0)
      return true;   // marked, still live, le.value is OK

    // have a large space object NOT marked during the preceeding collection

    // for minor collections, old large objects are considered live
    if (!majorCollection && (largeSpaceGen[page_num] >= GC_OLD))
      return true;   // not marked, but old, le.value is OK
    
    // if here, have garbage large object, mark live, and enqueue for scanning
    gc_setMarkLarge(tref);
    VM_GCWorkQueue.putToWorkBuffer(ref);
    le.pointer = VM_Magic.addressAsObject(ref);
    le.value = -1;
    return false;
  }  // processFinalizerListElement
       
  // Called from WriteBuffer code for generational collector
  // (ONLY USED WHEN GC_OLD > 1, so NOT called in this collector !!
  // gc_scanObjectOrArray is called instead
  //
  // static void
  //    gc_processWriteBufferEntry (VM_RememberedSet rs, int wbref) {}

  // Called from WriteBuffer code for generational collectors.
  // Argument is a modified old object which needs to be scanned
  //
  static void
  processWriteBufferEntry (int ref) {
    VM_ScanObject.scanObjectOrArray(ref);
  }
        
  static void printFreeSmallSpaceDetail()
  {
    int total_blocks = 0;
    VM.sysWrite("\n  Details of Free Space \n \n ");
    for (int i = 0; i < GC_SIZES; i++) {
      VM.sysWrite("Slotsize = ");
      VM.sysWrite(GC_SIZEVALUES[i], false);
      VM.sysWrite(" freespace = ");
      VM.sysWrite(countSmallFree[i], false);
      VM.sysWrite(" alloc'd blocks = ");
      VM.sysWrite(countSmallBlocksAlloc[i], false);
      total_blocks += countSmallBlocksAlloc[i];
      VM.sysWrite(" \n \n");
    }
    VM.sysWrite("Total Blocks allocated = ");
    VM.sysWrite(total_blocks, false);
     
    VM.sysWrite(" \n ");
  }

  /**
   * update times used when TIME_GC_PHASES is on
   */
  private static void
    accumulateGCPhaseTimes () {
    double start = 0.0;
    if (!majorCollection) 
      start    = gcStartTime - VM_CollectorThread.startTime;
    double init     = gcInitDoneTime - gcStartTime;
    double stacksAndStatics = gcStacksAndStaticsDoneTime - gcInitDoneTime;
    double scanning = gcScanningDoneTime - gcStacksAndStaticsDoneTime;
    double finalize = gcFinalizeDoneTime - gcScanningDoneTime;
    double finish   = gcEndTime - gcFinalizeDoneTime;

    // add current GC times into totals for summary output
    //    totalStartTime += start;   // always measured in ge initialization
    if (!majorCollection) {
      totalInitTime += init;
      totalStacksAndStaticsTime += stacksAndStatics;
      totalScanningTime += scanning;
      totalFinalizeTime += finalize;
      totalFinishTime += finish;
    }
    else {
      totalInitTimeMajor += init;
      totalStacksAndStaticsTimeMajor += stacksAndStatics;
      totalScanningTimeMajor += scanning;
      totalFinalizeTimeMajor += finalize;
      totalFinishTimeMajor += finish;
    }

    // if invoked with -verbose:gc print output line for this last GC
    if (VM.verboseGC) {
      VM.sysWrite("<GC ");
      VM.sysWrite(gcCount,false);
      if (!majorCollection) {
	VM.sysWrite(" startTime ");
	VM.sysWrite( (int)(start*1000000.0), false);
	VM.sysWrite("(us)");
      }
      VM.sysWrite(" init ");
      VM.sysWrite( (int)(init*1000000.0), false);
      VM.sysWrite("(us) stacks & statics ");
      VM.sysWrite( (int)(stacksAndStatics*1000000.0), false);
      VM.sysWrite("(us) scanning ");
      VM.sysWrite( (int)(scanning*1000.0), false );
      VM.sysWrite("(ms) finalize ");
      VM.sysWrite( (int)(finalize*1000000.0), false);
      VM.sysWrite("(us) finish ");
      VM.sysWrite( (int)(finish*1000000.0), false);
      VM.sysWrite("(us)>\n");
    }
  }

  /**
   * Generate summary statistics when VM exits. invoked via sysExit callback.
   */
  static void
    printSummaryStatistics () {
    int np = VM_Scheduler.numProcessors;

    // produce summary system exit output if -verbose:gc was specified of if
    // compiled with measurement flags turned on
    //
    if ( ! (TIME_GC_PHASES || VM_CollectorThread.MEASURE_WAIT_TIMES || VM.verboseGC) )
      return;     // not verbose, no flags on, so don't produce output

    VM.sysWrite("\nGC stats: Hybrid Collector (");
    VM.sysWrite(np,false);
    VM.sysWrite(" Collector Threads ):\n");
    VM.sysWrite("          Heap Size ");
    VM.sysWrite(smallHeapSize,false);
    VM.sysWrite("  Nursery Size ");
    VM.sysWrite(nurserySize,false);
    VM.sysWrite("  Large Object Heap Size ");
    VM.sysWrite(largeHeapSize,false);
    VM.sysWrite("\n");

    VM.sysWrite("  ");
    if (gcCount == 0)
      VM.sysWrite("0 MinorCollections");
    else {
      VM.sysWrite(gcCount,false);
      VM.sysWrite(" MinorCollections: avgTime ");
      VM.sysWrite( (int)( ((totalMinorTime/(double)gcCount)*1000.0) ),false);
      VM.sysWrite(" (ms) maxTime ");
      VM.sysWrite( (int)(maxMinorTime*1000.0),false);
      VM.sysWrite(" (ms) AvgStartTime ");
      VM.sysWrite( (int)( ((totalStartTime/(double)gcCount)*1000000.0) ),false);
      VM.sysWrite(" (us)\n");
      VM.sysWrite("  ");
    }
    if (gcMajorCount == 0)
      VM.sysWrite("0 MajorCollections\n");
    else {
      VM.sysWrite(gcMajorCount,false);
      VM.sysWrite(" MajorCollections: avgTime ");
      VM.sysWrite( (int)( ((totalMajorTime/(double)gcMajorCount)*1000.0) ),false);
      VM.sysWrite(" (ms) maxTime ");
      VM.sysWrite( (int)(maxMajorTime*1000.0),false);
      VM.sysWrite(" (ms)\n");
      VM.sysWrite("  Total Collection Time ");
      VM.sysWrite( (int)(gcTotalTime*1000.0),false);
      VM.sysWrite(" (ms)\n\n");
    }

    if (COUNT_COLLISIONS && (gcCount>0) && (np>1)) {
      VM.sysWrite("  avg number of collisions per collection = ");
      VM.sysWrite(collisionCount/gcCount,false);
      VM.sysWrite("\n\n");
    }

    if (TIME_GC_PHASES && (gcCount>0)) {
      int avgStart=0, avgInit=0, avgStacks=0, avgScan=0, avgFinalize=0, avgFinish=0;

      avgStart = (int)((totalStartTime/(double)gcCount)*1000000.0);
      avgInit = (int)((totalInitTime/(double)gcCount)*1000000.0);
      avgStacks = (int)((totalStacksAndStaticsTime/(double)gcCount)*1000000.0);
      avgScan = (int)((totalScanningTime/(double)gcCount)*1000.0);
      avgFinalize = (int)((totalFinalizeTime/(double)gcCount)*1000000.0);
      avgFinish = (int)((totalFinishTime/(double)gcCount)*1000000.0);

      VM.sysWrite("Average Time in Phases of Collection:\n");
      VM.sysWrite("Minor: startTime ");
      VM.sysWrite( avgStart, false);
      VM.sysWrite("(us) init ");
      VM.sysWrite( avgInit, false);
      VM.sysWrite("(us) stacks & statics ");
      VM.sysWrite( avgStacks, false);
      VM.sysWrite("(us) scanning ");
      VM.sysWrite( avgScan, false );
      VM.sysWrite("(ms) finalize ");
      VM.sysWrite( avgFinalize, false);
      VM.sysWrite("(us) finish ");
      VM.sysWrite( avgFinish, false);
      VM.sysWrite("(us)\n");

      if (gcMajorCount>0) {
	avgInit = (int)((totalInitTimeMajor/(double)gcMajorCount)*1000000.0);
	avgStacks = (int)((totalStacksAndStaticsTimeMajor/(double)gcMajorCount)*1000000.0);
	avgScan = (int)((totalScanningTimeMajor/(double)gcMajorCount)*1000.0);
	avgFinalize = (int)((totalFinalizeTimeMajor/(double)gcMajorCount)*1000000.0);
	avgFinish = (int)((totalFinishTimeMajor/(double)gcMajorCount)*1000000.0);

	VM.sysWrite("Major: (no startTime) init ");
	VM.sysWrite( avgInit, false);
	VM.sysWrite("(us) stacks & statics ");
	VM.sysWrite( avgStacks, false);
	VM.sysWrite("(us) scanning ");
	VM.sysWrite( avgScan, false );
	VM.sysWrite("(ms) finalize ");
	VM.sysWrite( avgFinalize, false);
	VM.sysWrite("(us) finish ");
	VM.sysWrite( avgFinish, false);
	VM.sysWrite("(us)\n\n");
      }
    }

    if (VM_CollectorThread.MEASURE_WAIT_TIMES && (gcCount>0)) {
      double totalBufferWait = 0.0;
      double totalFinishWait = 0.0;
      double totalRendezvousWait = 0.0;
      int avgBufferWait=0, avgFinishWait=0, avgRendezvousWait=0;
      double collections = (double)(gcCount + gcMajorCount);

      VM_CollectorThread ct;
      for (int i=1; i <= np; i++ ) {
	ct = VM_CollectorThread.collectorThreads[VM_Scheduler.processors[i].id];
	totalBufferWait += ct.totalBufferWait;
	totalFinishWait += ct.totalFinishWait;
	totalRendezvousWait += ct.totalRendezvousWait;
      }

      avgBufferWait = ((int)((totalBufferWait/collections)*1000000.0))/np;
      avgFinishWait = ((int)((totalFinishWait/collections)*1000000.0))/np;
      avgRendezvousWait = ((int)((totalRendezvousWait/collections)*1000000.0))/np;

      VM.sysWrite("Average Wait Times For Each Collector Thread In A Collection:\n");
      VM.sysWrite("Buffer Wait ");
      VM.sysWrite( avgBufferWait, false);
      VM.sysWrite(" (us) Finish Wait ");
      VM.sysWrite( avgFinishWait, false);
      VM.sysWrite(" (us) Rendezvous Wait ");
      VM.sysWrite( avgRendezvousWait, false);
      VM.sysWrite(" (us)\n\n");
    }

  }  // printSummaryStatistics

  /**
   * Generate output for measurement flags turned on in VM_GCWorkQueue
   */
  private static void
    printWaitTimesAndCounts () {

    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      VM_CollectorThread.printThreadWaitTimes();
    else {
      if (VM_GCWorkQueue.MEASURE_WAIT_TIMES) {
	VM.sysWrite("*** Wait Times for Scanning \n");
	VM_GCWorkQueue.printAllWaitTimes();
	VM_GCWorkQueue.saveAllWaitTimes();
	VM.sysWrite("*** Wait Times for Finalization \n");
	VM_GCWorkQueue.printAllWaitTimes();
	VM_GCWorkQueue.resetAllWaitTimes();
      }
    }
    
    if (VM_GCWorkQueue.WORKQUEUE_COUNTS) {
      VM.sysWrite("*** Work Queue Counts for Scanning \n");
      VM_GCWorkQueue.printAllCounters();
      VM_GCWorkQueue.saveAllCounters();
      VM.sysWrite("*** WorkQueue Counts for Finalization \n");
      VM_GCWorkQueue.printAllCounters();
      VM_GCWorkQueue.resetAllCounters();
    }
  }  // printWaitTimesAndCounts

  private static void
  reportBlocks() {
    int i, j, next, sum = 0;
    VM_Processor st;
    for (j = 0; j < GC_SIZES; j++) total[j] = 0;  
    for (i = 1; i <= VM_Scheduler.numProcessors; i++) {
      VM.sysWrite(" Processor ");
      VM.sysWrite(i);
      VM.sysWrite("\n");
      st = VM_Scheduler.processors[i];
      for (j = 0; j < GC_SIZES; j++) {
	VM_SizeControl the_size = st.sizes[j];
	accum[j] = 1;		// count blocks allocated to this size
	VM_BlockControl the_block = VM_Magic.addressAsBlockControl(blocks[the_size.first_block]);
	next = the_block.nextblock;
	while (next != OUT_OF_BLOCKS) {
	  accum[j]++;
	  the_block = VM_Magic.addressAsBlockControl(blocks[next]);	
	  next = the_block.nextblock;
	}
	total[j] += accum[j];
	VM.sysWrite(" blocksize = ");
	VM.sysWrite(GC_SIZEVALUES[j]);
	VM.sysWrite(" allocated blocks = ");
	VM.sysWrite(accum[j]);
	VM.sysWrite("\n");
	accum[j] = 0;
      }
    }	// all processors
    VM.sysWrite("\n");
    for (j = 0; j < GC_SIZES; j++) {
      VM.sysWrite(" blocksize = ");
      VM.sysWrite(GC_SIZEVALUES[j]);
      VM.sysWrite(" total allocated blocks = ");
      VM.sysWrite(total[j]);
      VM.sysWrite("\n");
      sum += total[j];
    }
    VM.sysWrite(" Total blocks allocated = ");
    VM.sysWrite(sum);
    VM.sysWrite(" total blocks in system = ");
    VM.sysWrite(num_blocks);
    VM.sysWrite(" available blocks = ");
    VM.sysWrite(blocks_available);
    VM.sysWrite("\n");
  }  // reportBlocks				

  /**
   * return the number of blocks not assigned to a vp
   * or to the partial block list
   */

  static int
  freeBlocks () {
    if (first_freeblock == OUT_OF_BLOCKS) return 0;
    VM_BlockControl the_block = VM_Magic.addressAsBlockControl(blocks[first_freeblock]);
    int i = 1;
    int next = the_block.nextblock;
    while (next != OUT_OF_BLOCKS) {
      the_block = VM_Magic.addressAsBlockControl(blocks[next]);
      i++;
      next = the_block.nextblock;
    }
    return i;
  }

  private static void
  freeSmallSpaceDetails (boolean details) {
    int i, next;
    int blocks_in_use = 0, blocks_in_partial = 0;
    VM_Processor st;
    for (i = 1; i <= VM_Scheduler.numProcessors; i++) {
      st = VM_Scheduler.processors[i];
      VM.sysWrite(" \n Details of block usage \n Processor");
      VM.sysWrite(i, false);
      VM.sysWrite(" \n");
      blocks_in_use = blocks_in_use + freeSmallSpaceDetail(st, details);
    }
    VM.sysWrite("\n Blocks alloc'd to procs = ");
    VM.sysWrite(blocks_in_use, false);
    for (i = 0; i < GC_SIZES; i++) {
      if (partialBlockList[i] == OUT_OF_BLOCKS) continue;
      VM_BlockControl this_block = VM_Magic.addressAsBlockControl(blocks[partialBlockList[i]]
);
      if (this_block == null) continue;
      blocks_in_partial++;
      next = this_block.nextblock;
      while (next != OUT_OF_BLOCKS) {
        blocks_in_partial++;
        this_block = VM_Magic.addressAsBlockControl(blocks[next]);
        next = this_block.nextblock;
      }
    }
    VM.sysWrite("\n Blocks part'l lists = ");
    VM.sysWrite(blocks_in_partial, false);
    VM.sysWrite("\n Total blocks not free = ");
    VM.sysWrite(blocks_in_use + blocks_in_partial, false);
    VM.sysWrite(" Total blocks in sys = ");
    VM.sysWrite(num_blocks, false);
    VM.sysWrite("\n");
    VM.sysWrite("Number of Free Blocks is ");
    VM.sysWrite(freeBlocks(), false);
    VM.sysWrite("\n");
  }

  private static int
  freeSmallSpaceDetail (VM_Processor st, boolean details) {
    int blocks_in_use = 0;
		int temp = 0;
    for (int i = 0; i < GC_SIZES; i++) {
      VM_BlockControl this_block = VM_Magic.addressAsBlockControl(blocks[st.sizes[i].first_block]);
      blocks_in_use++;
      if (details) temp = emptyOfCurrentBlock(this_block, st.sizes[i].next_slot);
      int next = this_block.nextblock;
      while (next != OUT_OF_BLOCKS) {
        this_block = VM_Magic.addressAsBlockControl(blocks[next]);
        blocks_in_use++;
        if (details) temp += emptyof(i, this_block.mark);
        next = this_block.nextblock;
      }
			if (details) {
        VM.sysWrite(GC_SIZEVALUES[i], false);
        VM.sysWrite(" sized slots have ");
        VM.sysWrite(temp/GC_SIZEVALUES[i], false);
        VM.sysWrite(" slots free in ");
        VM.sysWrite(blocksInChain(VM_Magic.addressAsBlockControl(blocks[st.sizes[i].first_block])), false);
        VM.sysWrite(" alloc'd blocks\n");
			}
    }
    return blocks_in_use;
  }

  private static int
  emptyOfCurrentBlock(VM_BlockControl the_block, int current_pointer) {
    int i = current_pointer;
    int sum = 0;
    while (i != 0) {
      sum += the_block.slotsize;
      i = VM_Magic.getMemoryWord(i);
    }
    return sum;
  }


  //  calculate the number of free bytes in a block of slotsize size
  private  static int
  emptyof (int size, byte[] alloc) {
  int  total = 0;
  int  i;
  for (i = 0; i < alloc.length; i++) {
    if (alloc[i] == 0) total += GC_SIZEVALUES[size];
  }
  return  total;
  }

  // Count all VM_blocks in the chain from the input to the end
  //
  private static int
  blocksInChain(VM_BlockControl the_block) {
    int next = the_block.nextblock;
    int count = 1;
    while (next != OUT_OF_BLOCKS) {
      if (GCDEBUG_FREESPACE) VM_Scheduler.trace(" In blocksinChain", "next = ", next);
      count++;
      the_block = VM_Magic.addressAsBlockControl(blocks[next]);
      next = the_block.nextblock;
    }
    return count;
  }


  // added for VM_GCUtil 080101 SES

  /**
   * Process an object reference field during collection.
   *
   * @param location  address of a reference field
   */
  static void
  processPtrField ( int location ) {
    if (majorCollection)
      gc_processPtrFieldValue( VM_Magic.getMemoryWord( location) );
    else
      gc_processPtrField( location );
  }

  /**
   * Process an object reference (value) during collection.
   *
   * @param location  address of a reference field
   */
  static int
  processPtrValue ( int ref ) {
    int tref, page_num;

    if (majorCollection) {
      gc_processPtrFieldValue(ref);
      return ref;
    }

    // minor collection...

    if (ref == VM_NULL) return ref;

    // always process objects in the Nursery (forward if not already forwarded)
    if ( ref >= minNurseryRef && ref <= maxNurseryRef ) {
      return gc_copyAndScanObject(ref);  // return new reference
    }

    // for minor collection: mark and scan (and age) only NEW large objects
    // ...no longer test if <= maxLargeRef since end of LargeSpace may be
    // beyond 0x80000000 and java signed integer compares will be wrong
    //
    if ( ref >= minLargeRef ) {
      tref = ref + OBJECT_HEADER_OFFSET;
      page_num = (tref - largeHeapStartAddress  ) >> 12;
      if ( largeSpaceGen[page_num] == 0 ) {  // new large object
	if (!gc_setMarkLarge(tref))
	  // we marked it, so put to workqueue
	  VM_GCWorkQueue.putToWorkBuffer( ref );
      }
    }
    return ref;
  }

}   // VM_Allocator


