/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * See also: allocator/copyingGC/VM_Allocator.java
 * Note: both copying and noncopying versions of VM_Allocator
 *       provide identical "interfaces":
 *           init()
 *           boot()
 *           allocateScalar()
 *           allocateArray()
 * Selection of copying vs. noncopying allocators is a choice
 * made at boot time by specifying appropriate directory in CLASSPATH.
 *
 * @author David Bacon
 */
public class VM_Allocator
    extends VM_RCGC
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible,
               VM_Callbacks.ExitMonitor
{
    static final int      TYPE = 12; // needed for finalization or something?


  static final VM_Array BCArrayType  = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[LVM_BlockControl;"), VM_SystemClassLoader.getVMClassLoader()).asArray();
  static final VM_Array byteArrayType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[B"), VM_SystemClassLoader.getVMClassLoader()).asArray();
  private static final int byteArrayHeaderSize = VM_ObjectModel.computeArrayHeaderSize(byteArrayType);

  static Object[] byteArrayTIB;

  static final boolean RENDEZVOUS_TIMES = false;

    // OVERALL COLLECTOR CONTROL

    static boolean gc_collect_now = false;	    // flag to do a collection (new logic)
    static boolean gcInProgress = false;	    // is currently a GC happening?
    static boolean needToFreeBlocks = false;	    // out of blocks and need to free some?
    static int verbose = 0;

    // MEMORY LAYOUT
    private static VM_BootHeap bootHeap         = new VM_BootHeap();   
    private static VM_Heap smallHeap            = new VM_RCHeap("Small Object Heap");
    private static VM_ImmortalHeap immortalHeap = new VM_ImmortalHeap();
    private static VM_Heap largeHeap            = new VM_RCHeap("Large Object Heap");  // should become LargeHeap(immortalHeap);
            static VM_MallocHeap mallocHeap     = new VM_MallocHeap();

    static VM_BootRecord bootrecord;		    // copy of boot record
 

    // SMALL OBJECT ALLOCATION

    static VM_ProcessorLock   sysLockSmall;	    // Small object page lock

    static VM_BlockControl[]  blocks;		    // Partially initialized during init.  Completed during boot.
				
    static int                smallHeapSize;	    // small object heap size, in bytes
    static int    	      num_blocks;	    // number of blocks in the heap
    static int		      highest_block;	    // number of highest available block
    static int		      blocks_available;	    // number of free blocks for small obj's
    static int		      first_freeblock;	    // number of first available block
    static int                blocksCountDown;	    // when counter reaches 0, trigger GC

    static final int          OUT_OF_BLOCKS = -1;   // End-of-list indicator for freeblock list


    // LARGE OBJECT ALLOCATION

    static final int LARGE_BLOCK_SIZE = 4096;	    // Large objects are made up of 4K blocks
    static final int LOG_GC_LARGE_BLOCKSIZE = 12;

    static VM_ProcessorLock sysLockLarge;	    // Large object page lock

    static int		large_last_allocated;
    static int           largeSpacePages;
    static int           largeHeapSize;

    static short[]	largeSpaceAlloc;	    // used to allocate 
    static short[]	largeSpaceMark;		    // used to mark (debug only -- see GC_MARK_REACHABLE_OBJECTS)


    // Features

    static final boolean GC_CONTINUOUSLY = false;   // On MP, start collection on CPU 1 as soon as finished on CPU n
    static final boolean GC_FILTER_BADREFS = false;
    static final boolean GC_FILTER_MALLOC_REFS = false;
    static final boolean AGGRESSIVE_FREEING = true;
    static final boolean GC_ON_EXIT = false; // this doesn't seem to work anymore

    static final boolean COMPILE_FOR_TIMING_RUN = true;      // touch heap in boot

    // Statistics

    static final boolean GC_COUNT_ALLOC = false;

    static int allocCount;			    // updated every entry to allocate<x>
    static int fastAllocCount;			    // updated every entry to allocate<x>
    static int allocBytes;			    // total bytes allocated
    static int freedCount;			    // number of objects freed

    static final boolean RC_COUNT_EVENTS = false;

    static int green;				    // green allocated since last cycle collect (see VM_RootBuffer)
    static int black;				    // black allocated since last cycle collect (see VM_RootBuffer)

    static int nonZeroDecs;			    // decrements that didn't cause count to become zero (this epoch)
    static int internalDecs;			    // internal (implicit) decrements (this epoch)

    static int totalNonZeroDecs;		    // decrements that didn't cause count to become zero (total)
    static int totalInternalDecs;		    // internal (implicit) decrements (total)

    static int mutationIncCount;		    // total increments from mutation buffers
    static int mutationDecCount;		    // total decrements from mutation buffers
    static int stackRefCount;			    // total increment/decrements from stack buffers

    static final boolean TRACK_MEMORY_USAGE = false;

    static int bytesInUseTotal;
    static int bytesInUseMax;

    static int gcCount;				    // updated every entry to collect

    static int[] allocated_since_lastgc;	    // used to count allocations between gc
    static int[] countLive;			    // for stats - count # live objects in bin
    static int[] countLargeAlloc;		    //  - count sizes of large objects alloc'ed
    static int[] countLargeLive;		    //  - count sizes of large objects live
    static int[] countSmallFree;		    // bytes allocated by size
    static int[] countSmallBlocksAlloc;		    // blocks allocated by size

    static int largerefs_count;			    // counter of large objects marked

    // Timing

    static final int TicksPerMicrosecond = 41*4;	    // Number of system ticks per microsecond (changes by machine!)

    static long ticksPerUS;	// computed now instead of hard coding

    static double bootTime;			    // time when we booted VM

    static final boolean GC_STATISTICS = false;	    // for timing parallel GC
    static final boolean GC_TIMING = true;	    // for timing parallel GC
    static final boolean TIMING_DETAILS = false;    // break down components of pause times
    static final boolean TIME_ALLOCATES = false;     // time each allocateScalar() or allocateArray() operation
    static final boolean TIME_FREEBLOCKS = false;   // time each freeBlock call
    static final boolean PRINT_SLOW_ALLOCATES = false;

    static final long TIME_ALLOCATE_QUICK = 3000 * TicksPerMicrosecond; // report allocates slower than this (ticks)

    static int allocSlowCount;	// allocs above threshold for speed
    static int allocLargeSlowCount;	// allocs above threshold for speed

    static long allocTimeTotal;			    // in ticks
    static long allocTimeMax;			    // in ticks
    static long allocLargeTimeMax;			    // in ticks

    static double gcMinorTime;			    // for timing gc times
    static double gcMajorTime;			    // for timing gc times
    static double gcStartTime;			    // for timing gc times
    static double gcTotalTime;			    // for timing gc times

    // Tracing/Debugging

    static final boolean GC_TRIGGERGC = false;	    // for knowing why GC triggered
    static final boolean GC_TRACEALLOCATOR = false; // for tracing RCGC
    static final boolean GC_TRACEALLOCATOR_DETAIL = false; // for detailed tracing RCGC
    static final boolean GC_MARK_REACHABLE_OBJECTS = false; // check if freeing reachable obj
    static final boolean GC_MARK_REACHABLE_OBJECTS_DETAIL = false; // check if freeing reachable obj
    static final boolean GC_MARK_REACHABLE_OBJECTS_SOFT = false; // only soft warnings in MP mode?
    static final boolean DEBUG_NEXT_SLOT = false;   // verify addresses obtained from VM_SizeControl.next_slot
    static final boolean DebugLink = false;	    // debug small object free chains
    static final boolean GC_CLOBBERFREE = false;	
    static final boolean TRACE_LARGE = false;        // trace large object alloc/dealloc
    static final boolean SHOW_ALLOCATION_DELAY = false;
    static final boolean SHOW_HASH_STATS = false;

    static final boolean Report = false;

    static int OBJECT_GC_MARK_VALUE = 0;	    // changes between this and (MARK_VALUE?)

    static VM_Address refToLookFor;		    // object for tracing to use as data breakpoint
    static VM_Address refToWatch;			    // object for refcount operations to use as data breakpoint



    static  void
    init () {
	int i, ii;

	if ( ! VM.BuildForConcurrentGC ) 
	    VM.sysFail("build concurrent memory manager by setting preprocessor directive RVM_WITH_CONCURRENT_GC=1");

	VM_Processor st = VM_Scheduler.processors[VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];

	VM_CollectorThread.init();   // to alloc bootimage arrays etc

	// create synchronization objects
        sysLockLarge          = new VM_ProcessorLock();
        sysLockSmall          = new VM_ProcessorLock();

	allocated_since_lastgc = new int[GC_SIZES];
	st.sizes = new VM_SizeControl[GC_SIZES];
	blocks = new VM_BlockControl[GC_SIZES];

	// On the jdk side, we allocate an array of VM_SizeControl Blocks, 
	// one for each size.
	// We also allocate blocks array within the boot image.  
	// At runtime we allocate the rest of the BLOCK_CONTROLS, whose number 
	// depends on the heapsize, and copy the contents of init_blocks 
	// into the first GC_SIZES of them.

	for (i = 0; i < GC_SIZES; i++) {
	    st.sizes[i] = new VM_SizeControl();
	    blocks[i] = new VM_BlockControl();
	    st.sizes[i].first_block = i;	// 1 block/size initially
	    st.sizes[i].current_block = i;
	    st.sizes[i].ndx = i;

	    // make arrays 1 entry larger: AUTO-CHECK: TAKEOUT!!
	    blocks[i].Alloc1 = new byte[GC_BLOCKSIZE/GC_SIZEVALUES[i] ];
	    blocks[i].Alloc2 = new byte[GC_BLOCKSIZE/GC_SIZEVALUES[i] ];
	    blocks[i].alloc = blocks[i].Alloc1;
	    blocks[i].mark  = blocks[i].Alloc2;

	    for (ii = 0; ii < GC_BLOCKSIZE/GC_SIZEVALUES[i]; ii++) {
		blocks[i].alloc[ii] = 0;
		blocks[i].mark[ii]  = 0;
	    }

	    blocks[i].nextblock= 0;
	    blocks[i].slotsize = GC_SIZEVALUES[i];
	}

        // set up GC_INDEX_ARRAY for this Processor
        st.GC_INDEX_ARRAY = new VM_SizeControl[GC_MAX_SMALL_SIZE + 1];
        st.GC_INDEX_ARRAY[0] = st.sizes[0];   // for size = 0
        int j = 1;
        for (i = 0; i < GC_SIZES; i++) 
	    for (; j <= GC_SIZEVALUES[i]; j++) st.GC_INDEX_ARRAY[j] = st.sizes[i];

	countLive   = new int[GC_SIZES];
	countLargeAlloc = new int[GC_LARGE_SIZES];
	countLargeLive  = new int[GC_LARGE_SIZES];
        countSmallFree = new int[GC_SIZES];
        countSmallBlocksAlloc = new int[GC_SIZES];

	for (i = 0; i < GC_LARGE_SIZES; i++) {
	    countLargeAlloc[i]   = 0;
	    countLargeLive[i]    = 0;
	}

	for (i = 0; i < GC_SIZES; i++) {
	    countLive[i]   = 0;
	    allocated_since_lastgc[i]   = 0;
	}

        largeSpaceAlloc = new short[GC_INITIAL_LARGE_SPACE_PAGES];
        for (i = 0; i < GC_INITIAL_LARGE_SPACE_PAGES; i++)
	    largeSpaceAlloc[i] = 0;
        large_last_allocated = 0;
        largeSpacePages = GC_INITIAL_LARGE_SPACE_PAGES;
	
	if (VM_RCGC.acyclicVmClasses)
	  VM_RootBuffer.init();

    }			// all this done in bootimagebuilder context


    static void boot (VM_BootRecord thebootrecord) {
	int i;

        bootrecord = thebootrecord;	 // has no barrier
	verbose = bootrecord.verboseGC;
	
	// Set up processor's buffers before any use of barrier
	//
	VM_Processor st = VM_Scheduler.processors[VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
	VM_Heap.boot(bootHeap, bootrecord);
	mallocHeap.attach(bootrecord);
	setupProcessor(st);

	VM_BlockControl.boot();


	int smallHeapSize = bootrecord.smallSpaceSize;
	smallHeapSize = (smallHeapSize / GC_BLOCKALIGNMENT) * GC_BLOCKALIGNMENT;
	smallHeapSize = VM_Memory.roundUpPage(smallHeapSize);
	int immortalSize = VM_Memory.roundUpPage(4 * (bootrecord.largeSpaceSize / VM_Memory.getPagesize()) + 
						 ((int) (0.05 * smallHeapSize)) + 
						 4 * VM_Memory.getPagesize());

	immortalHeap.attach(immortalSize);
	largeHeap.attach(bootrecord.largeSpaceSize);
	smallHeap.attach(smallHeapSize);

	if (COMPILE_FOR_TIMING_RUN)
	    smallHeap.touchPages();

	// Now set the beginning address of each block into each VM_BlockControl
	// Note that init_blocks is in the boot image, but heap pages are controlled by it

	for (i =0; i < GC_SIZES; i++)  {
	    blocks[i].baseAddr = smallHeap.start.add(i * GC_BLOCKSIZE);
	    build_list_for_new_block(blocks[i], st.sizes[i]);
	}

	// Get the three arrays that control large object space
        short[] temp  = new short[bootrecord.largeSpaceSize/LARGE_BLOCK_SIZE + 1];
        largeSpaceMark  = new short[bootrecord.largeSpaceSize/LARGE_BLOCK_SIZE + 1];

        for (i = 0; i < GC_INITIAL_LARGE_SPACE_PAGES; i++)
	    temp[i] = largeSpaceAlloc[i];

	for (int iii = 0 ; iii < largeSpacePages;) {
	    if (largeSpaceAlloc[iii] == 0) {
		iii++;
	    }
	    else iii = iii + largeSpaceAlloc[iii]; // negative value in largeSpA
	}

	// At this point temp contains the up-to-now allocation information
	// for large objects; so it now becomes largeSpaceAlloc
        largeSpaceAlloc = temp;
        largeSpacePages = bootrecord.largeSpaceSize/LARGE_BLOCK_SIZE;

	// At this point it is possible to allocate 
	// (1 GC_BLOCKSIZE worth of )objects foreach size
	byteArrayTIB = byteArrayType.getTypeInformationBlock();

	if (VM_RCGC.acyclicVmClasses)
	  VM_RootBuffer.boot();	

	// Now allocate the blocks array - which will be used to allocate blocks to sizes

	num_blocks = smallHeapSize/GC_BLOCKSIZE;
	blocksCountDown = num_blocks >> 5;

	large_last_allocated = 0;

	//      GET STORAGE FOR BLOCKS ARRAY FROM OPERATING SYSTEM
	int blocks_array_size = BCArrayType.getInstanceSize(num_blocks);
        VM_Address blocks_array_storage = mallocHeap.allocateZeroedMemory(blocks_array_size);

	int blocks_storage_size = (num_blocks - GC_SIZES) * VM_BlockControl.getInstanceSize();
	VM_Address blocks_storage = mallocHeap.allocateZeroedMemory(blocks_storage_size);

	// Note: the TIB that we get should be of type int[]; if it is of type VM_BlockControl[] then things
	//   get very confused, since it is declared as int[].
	VM_BlockControl [] originalBlocks = blocks;
	Object[] BCArrayTIB = BCArrayType.getTypeInformationBlock();
	blocks = (VM_BlockControl []) (VM_ObjectModel.initializeArray(blocks_array_storage, BCArrayTIB, num_blocks, blocks_array_size));


	// index for highest page in heap
	highest_block = num_blocks -1;
	blocks_available = highest_block - GC_SIZES; 	// available to allocate
	
	// Now fill in blocks with values from blocks_init
	for (i = 0; i < GC_SIZES; i++) {
	    // NOTE: if blocks are identified by index, st.sizes[] need not be changed; if
	    // 	blocks are identified by address, then updates st.sizes[0-GC_SIZES] here
	    blocks[i]        = originalBlocks[i];
	    // make sure it survives the first collection
	    blocks[i].sticky = true;
	}

	// At this point we have assigned the first GC_SIZES blocks, 
	// 1 per, to each GC_SIZES bin
	// and are prepared to allocate from such, or from large object space:
	// large objects are allocated from the top of the heap downward; 
	// small object blocks are allocated from the bottom of the heap upward.  
	// VM_BlockControl blocks are not used to manage large objects - 
	// they are unavailable by special logic for allocation of small objs
	first_freeblock = GC_SIZES;	// next to be allocated

	// Now allocate the rest of the VM_BlockControls
	int bcSize = VM_BlockControl.getInstanceSize();
	Object[] bcTIB = VM_BlockControl.getTIB();
	
	for (i = GC_SIZES; i < num_blocks; i++) {
	    ///		blocks[i] = new VM_BlockControl();
	    Object bcTemp = makeObjectFromStorage(blocks_storage.add((i - GC_SIZES) * bcSize), bcTIB, bcSize);
	    // Avoid cast that might thread switch
	    VM_BlockControl bc = VM_Magic.addressAsBlockControl(VM_Magic.objectAsAddress(bcTemp));
	    blocks[i] = bc;
	    bc.baseAddr = smallHeap.start.add(i * GC_BLOCKSIZE); 
	    bc.nextblock = i + 1;
	}
	
	blocks[num_blocks - 1].nextblock = OUT_OF_BLOCKS;

	VM_GCUtil.boot();
	VM_Finalizer.setup();

        VM_Callbacks.addExitMonitor(new VM_Allocator());

	bootTime = VM_Time.now();

    } // boot()

    /**
     * To be called when the VM is about to exit.
     * @param value the exit value
     */
    public void notifyExit(int value) {
        cleanup();
    }

    static void setupProcessor (VM_Processor st) {

	// for the PRIMORDIAL PROCESSOR, setupProcessor is called twice,
	// once when building the bootimage (VM.runningVM==false) and again
	// from VM.boot when the VM is booting (VM.runningVM==true)
	// Allocation sturctures (size controls etc) are constructed in init().
	// IncDec buffers must be allocated in the second call when booting.
	//
	if (st.id == VM_Scheduler.PRIMORDIAL_PROCESSOR_ID) {
	  if (VM.runningVM == false)
	    return;     // nothing to do during bootimage writing
	  else {
	    VM_RCBuffers.allocateIncDecBuffer(st);
	    st.localEpoch = -1;
	  }
	  return;
	}

	VM_RCBuffers.allocateIncDecBuffer(st);
	st.localEpoch = -1;

	if (verbose >= 2)
	    VM.sysWriteln("setupProcessor ", st.id, ": allocating size control");
	// Get VM_SizeControl array 
	st.sizes =  new VM_SizeControl[GC_SIZES];
	for (int i = 0; i < GC_SIZES; i++) {
	    st.sizes[i] = new VM_SizeControl();
	    int ii = VM_Allocator.getnewblockx(i);
	    st.sizes[i].first_block = ii;    // 1 block/size initially
	    st.sizes[i].current_block = ii;
	    st.sizes[i].ndx = i;		// to fit into old code
	    build_list_for_new_block(blocks[ii], st.sizes[i]);
	}

	st.GC_INDEX_ARRAY = new VM_SizeControl[GC_MAX_SMALL_SIZE + 1];
	st.GC_INDEX_ARRAY[0] = st.sizes[0];   // for size = 0

	// set up GC_INDEX_ARRAY for this Processor
	int j = 1;
	for (int i = 0; i < GC_SIZES; i++) 
	    for (; j <= GC_SIZEVALUES[i]; j++) 
		st.GC_INDEX_ARRAY[j] = st.sizes[i];
    }


    public static void cleanup () {
	double runTime = -1.0;

	runTime = VM_Time.now() - bootTime;

	double s = VM_Time.now();
	long ts = VM_Time.cycles();
	for (double d = VM_Time.now(); d-s < 1.0; d = VM_Time.now()) {}
	long ticksPerSecond = VM_Time.cycles() - ts;
	ticksPerUS = ticksPerSecond/1000000;
	// println("Ticks/us: ", (int) ticksPerUS);

	if (GC_ON_EXIT) {
	    println("HEAP BEFORE CLEANUP");  heapInfo();

	    int e = VM_Scheduler.globalEpoch;

	    while (VM_Scheduler.globalEpoch < e+4)
		collectGarbageOrAwaitCompletion("cleanup");

	    println("HEAP AFTER CLEANUP");  heapInfo();
	}
	else if (GC_TRACEALLOCATOR) {
	    println("HEAP STATUS");  heapInfo();
	}


	// println("\nForcing final root buffer processing");
	// VM_RootBuffer.buffer.processCycles();

	boolean showAny = (GC_COUNT_ALLOC ||
			   TRACK_MEMORY_USAGE ||
			   RC_COUNT_EVENTS || 
			   TIME_ALLOCATES);

	if (showAny) {
	    print("\n\nRCGC SUMMARY\n\n");
	    println("Epochs: ", VM_Scheduler.globalEpoch);
	    println();
	}

	if (GC_COUNT_ALLOC) {
	    println("Objects allocated: ", allocCount);  
	    print("Fast allocations: ", fastAllocCount);  percentage(fastAllocCount, allocCount, "allocations");
	    print("Objects freed: ", freedCount);         percentage(freedCount, allocCount, "allocations");
	    println("Bytes allocated: ", allocBytes);
	}

	if (TRACK_MEMORY_USAGE) {
	    println("Memory high water mark: ", bytesInUseMax);
	    println("Avg memory utilization: ", bytesInUseTotal/gcCount);
	}

	if (RC_COUNT_EVENTS) {
	    totalNonZeroDecs  += nonZeroDecs;
	    totalInternalDecs += internalDecs;

	    VM_RootBuffer.printStatistics(allocCount, allocBytes, totalNonZeroDecs);

	    int totalInc = mutationIncCount + stackRefCount;
	    int totalDec = mutationDecCount + stackRefCount + totalInternalDecs;

	    print("Total increments: ", totalInc);  percentage(totalInc, allocCount, "allocations(*)");
	    print("Total decrements: ", totalDec);  percentage(totalDec, allocCount, "allocations(*)");

	    print("Mutator increments: ", mutationIncCount);  percentage(mutationIncCount, totalInc, "increments");
	    print("Mutator decrements: ", mutationDecCount);  percentage(mutationDecCount, totalDec, "decrements");
	    println("Stack inc/dec's: ", stackRefCount);

	    print("Internal Decrements: ", totalInternalDecs);  percentage(totalInternalDecs,totalDec, "decrements");
	    print("Non-0 Decrements: ",    totalNonZeroDecs);   percentage(totalNonZeroDecs, totalDec, "decrements");
	    
	    print("Max Mutation Buffers: ", VM_RCBuffers.buffersUsed);  
	    print(" - ", (VM_RCBuffers.buffersUsed * VM_RCBuffers.INCDEC_BUFFER_SIZE)/1024); println(" KB");

	    if (VM_RootBuffer.ASYNC)
		VM_CycleBuffer.printStatistics();
	    VM_RCGC.printStatistics();
	}

	if (TIME_ALLOCATES) {
	    print("Max Alloc Time:  ", (int) (allocTimeMax/ticksPerUS));  println(" usecs");
	    if (GC_COUNT_ALLOC) {
		long avgAlloc = (allocTimeTotal/((long) allocCount))/ticksPerUS;
		print("Avg Alloc Time:  ", (int) avgAlloc);  println(" usecs");
	    }
	    else
		println("Avg Alloc Time unavailable.  Turn on GC_COUNT_ALLOC");
	    
	    print("Allocs slower than ", ((int) (TIME_ALLOCATE_QUICK/TicksPerMicrosecond)));
	    println(" usec:  ", allocSlowCount);

	    print("Max Large Alloc Time:  ", (int) (allocLargeTimeMax/ticksPerUS));  println(" usecs");
	    print("Large allocs slower than ", ((int) (TIME_ALLOCATE_QUICK/TicksPerMicrosecond)));
	    println(" usec:  ", allocLargeSlowCount);

	}

	if (SHOW_HASH_STATS) 
	    dumpHashStats();

	if (VM_RCCollectorThread.TIME_PAUSES) {
	    VM_RCCollectorThread.printRCStatistics(freedCount);
	}

	if (RC_COUNT_EVENTS) 
	    println("\n * Comparative only; not a true percentage");

	if (showAny) 
	    println("\nRUN TIME: ", (int) runTime);
    }


    /////////////////////////////////////////////////////////////////////////////
    // REFERENCE COUNTING
    /////////////////////////////////////////////////////////////////////////////

    private static Object createScalar (VM_Address rawaddr, Object[] tib, int size, VM_SizeControl the_size) {
	Object object = VM_ObjectModel.initializeScalar(rawaddr, tib, size);
	refcountify(VM_Magic.objectAsAddress(object), rawaddr, tib, the_size); // do refcounting stuff
	return object;
    }

    private static Object createArray (VM_Address rawaddr, Object[] tib, int nelements, int size, VM_SizeControl the_size) {
	Object object = VM_ObjectModel.initializeArray(rawaddr, tib, nelements, size);
	refcountify(VM_Magic.objectAsAddress(object), rawaddr, tib, the_size); // do refcounting stuff
	return object;
    }

    // Encapsulate creation of refcounted object
    private static void refcountify(VM_Address objaddr, VM_Address rawaddr, Object[] tib, VM_SizeControl the_size) {

	// In case of underlying allocation failure, just return 
	if (objaddr.isZero())
	    return;

	if (VM.VerifyAssertions && objaddr.EQ(refToWatch)) {
	    VM.sysWrite("#### Refcountifying watched object; raw address ");
	    VM.sysWrite(rawaddr);  VM.sysWriteln();
	}

	// If it's a small object, mark it appropriately
	if (the_size != null) {
	    // Update alloc byte to reflect the fact that this slot has been allocated
	    VM_BlockControl the_block = blocks[the_size.current_block];
	    int slotndx = rawaddr.diff(the_block.baseAddr) / the_block.slotsize;
	    the_block.alloc[slotndx] = 1;
	    //	    the_block.allocCount++;	// should use atomic fetch and add on MP
	    VM_Synchronization.fetchAndAdd(the_block, VM_Entrypoints.allocCountField.getOffset(),1);
	}

    }


    // Allocate an object.
    // Taken:    size of object (including header), in bytes
    //           tib for object
    // Returned: zero-filled, word aligned space for an object, with header installed
    //           (ready for initializer to be run on it)
    //
    public static Object allocateScalar(int size, Object[] tib) throws OutOfMemoryError
    {

	long startTime;
	int allocType = 0;
	Object result;
	boolean mustGC = gc_collect_now;

	if (TIME_ALLOCATES) startTime = VM_Time.cycles(); 

	if (GC_COUNT_ALLOC) { allocCount++; allocBytes += size; }

	if (mustGC) 
	    gc1();

	// assumption: object blocks are always a word multiple,
	// so we don't need to worry about address alignment or rounding
	VM_Processor st = VM_Processor.getCurrentProcessor();

	if (size <= GC_MAX_SMALL_SIZE) {
	    VM_SizeControl  the_size   = st.GC_INDEX_ARRAY[size];

	    if (!the_size.next_slot.isZero()) {	// fastest path
		VM_Address rawaddr = the_size.next_slot;
		if (GC_COUNT_ALLOC) fastAllocCount++;

		if (DebugLink) checkAllocation(rawaddr, the_size);

		the_size.next_slot = VM_Magic.getMemoryAddress(rawaddr);

		if (DEBUG_NEXT_SLOT) checkNextAllocation(rawaddr, the_size);

		VM_Magic.setMemoryWord(rawaddr, 0);
		result = createScalar(rawaddr, tib, size, the_size);
	    }
	    else {
		result = allocateScalar1(the_size, tib, size, the_size.ndx);
		allocType = 1;
	    }
	}
	else {
	    result = allocateScalar1L(tib, size);
	    allocType = 2;
	}

	if (TIME_ALLOCATES) {
	    long pauseTime = VM_Time.cycles() - startTime;
	    allocTimeTotal += pauseTime;

	    VM_Thread t = VM_Thread.getCurrentThread();
	    final boolean isUser = ! (t.isGCThread || t.isIdleThread);
	    if (isUser) {
		if (pauseTime > allocTimeMax && allocType != 2) allocTimeMax = pauseTime;
		if (allocType == 2 && pauseTime > allocLargeTimeMax) allocLargeTimeMax = pauseTime;
		if (pauseTime > TIME_ALLOCATE_QUICK) {
		    if (allocType != 2) allocSlowCount++; else allocLargeSlowCount++; 
		}
	    }

	    if (PRINT_SLOW_ALLOCATES && pauseTime > TIME_ALLOCATE_QUICK) {
		print(")))) Slow allocateScalar");
		if (allocType == 1) 
		    print("1");
		else if (allocType == 2)
		    print("1L");
		if (mustGC)
		    print("[gc1]");
		print(" of ", size);
		print(" bytes: ", (int) (pauseTime/TicksPerMicrosecond));  print(" usec");
		if (! isUser) print("  [GC ALLOC]");
		println();
	    }
	}

	return result;
    }


    static Object
    allocateScalar1 (VM_SizeControl the_size, Object[] tib, int size, int ndx)
    {
	for (int i = 0; i < 20; i++) {
	    VM_Address objaddr = allocatex(the_size, tib, size, the_size.ndx);
	    if (!objaddr.isZero()) 
		return VM_Magic.addressAsObject(objaddr);

	    print("GCing for scalar of size ", GC_SIZEVALUES[ndx]);
	    print(" (iteration ", i); println(")");
	    collectGarbageOrAwaitCompletion("allocateScalar1");

	    // try fast path again
	    // if (the_size.next_slot != 0) {
	    //    return VM_Magic.addressAsObject(makeScalar(the_size, tib, size));
	    // }
	}

 	// failure
	VM_Scheduler.trace("VM_Allocator::allocateScalar1",
			   "couldn't collect enough to fill a request (bytes) for ", size);
	VM_Scheduler.traceback("VM_Allocator::allocateScalar1");
	return null;
    }


    private static final boolean INSTRUMENT_ALLOC = false;

    private static final long timeLimit = 3000 * TicksPerMicrosecond;

    // move on to next block, or get a new block, or return 0
    static VM_Address 
    allocatex (VM_SizeControl the_size, Object[] tib, int size, int ndx) 
    {
	int blocksFreedCount;
	int blocksBuiltCount;
	int blocksSkippedCount;
	long buildStartTime;
	long startTime;

	if (INSTRUMENT_ALLOC) startTime = VM_Time.cycles();

	boolean reset = recycleBlocksIfGarbageCollected(the_size);

	if (INSTRUMENT_ALLOC && VM_Time.cycles() - startTime > timeLimit) println("Slow recycling blocks");

	if (!the_size.next_slot.isZero()) {
	    return makeScalar(the_size, tib, size);
	}

	VM_Processor st = VM_Processor.getCurrentProcessor();
	VM_BlockControl the_block = blocks[the_size.current_block];

	if (INSTRUMENT_ALLOC) { blocksFreedCount = 0;  blocksBuiltCount = 0; blocksSkippedCount = 0; }

	final int slotsPerBlock = the_block.alloc.length;

	while (the_block.nextblock != 0) {
	    int blockIndex = the_block.nextblock;
	    VM_BlockControl nextBlock = blocks[blockIndex];

	    // If the block is empty, and there are more blocks on the list, free the block to reduce
	    // fragmentation and keep storage utilization low.
	    if (AGGRESSIVE_FREEING && nextBlock.allocCount == 0 && nextBlock.nextblock != 0) {
		the_block.nextblock = nextBlock.nextblock;
		freeBlock(nextBlock, blockIndex);
		if (INSTRUMENT_ALLOC) blocksFreedCount++;
		continue;
	    }

	    // Try allocating out of this block
	    the_size.current_block = blockIndex;
	    the_block = nextBlock;

	    if (the_block.allocCount == slotsPerBlock) {
		if (INSTRUMENT_ALLOC) blocksSkippedCount++;
		the_size.next_slot = VM_Address.zero(); // needed?
		continue;
	    }

	    if (INSTRUMENT_ALLOC) { blocksBuiltCount++; buildStartTime = VM_Time.cycles(); }
	    if ( build_list(the_block, the_size) ) {
		if (INSTRUMENT_ALLOC) {
		    long t = VM_Time.cycles();
		    if (t - startTime > timeLimit) {
			print("Slow searching blocks.  Freed ", blocksFreedCount);
			print("; built ", blocksBuiltCount);  print("; skipped ", blocksSkippedCount);
			if (reset) println(" [recycled list]"); else println(" [didn't recycle]");
			print("build_list() took ", (int) ((t - buildStartTime)/TicksPerMicrosecond)); println("us");
		    }
		}
		return makeScalar(the_size, tib, size);
	    }

	}	// while.... ==> need to get another block
	  
	if (getnewblock(ndx) == 0) {
	    the_block = blocks[the_size.current_block];
	    build_list_for_new_block(the_block, the_size);
	    if (INSTRUMENT_ALLOC && VM_Time.cycles() - startTime > timeLimit) {
		print("Slow allocating new block.  Freed ", blocksFreedCount);
		print("; built ", blocksBuiltCount);  print("; skipped ", blocksSkippedCount);
		if (reset) println(" [recycled list]"); else println(" [didn't recycle]");
	    }
            return makeScalar(the_size, tib, size);
	}
	else
	    return VM_Address.zero();
    }


    // make a small scalar from the free object available in next_slot
    private static VM_Address makeScalar (VM_SizeControl the_size, Object[] tib, int size) {
	if (VM.VerifyAssertions) VM.assert(!the_size.next_slot.isZero());

	VM_Address rawaddr = the_size.next_slot;

	if (DebugLink) checkAllocation(rawaddr, the_size);

	the_size.next_slot = VM_Magic.getMemoryAddress(rawaddr);

	if (DEBUG_NEXT_SLOT) checkNextAllocation(rawaddr, the_size);

	VM_Magic.setMemoryWord(rawaddr, 0);

	Object object = createScalar(rawaddr, tib, size, the_size);
	return VM_Magic.objectAsAddress(object);
    }



    // Allocate an array.
    // Taken:    number of array elements
    //           size of array object (including header), in bytes
    //           tib for array object
    // Returned: zero-filled array object with .length field set
    //
    public static Object
    allocateArray (int numElements, int size, Object[] tib) throws OutOfMemoryError {

	Object result;
	long startTime;
	int allocType = 0;
	VM_Address    objaddr;
	boolean mustGC = gc_collect_now;

	if (TIME_ALLOCATES) startTime = VM_Time.cycles();

	if (mustGC)
	    gc1();

	if (GC_COUNT_ALLOC) { allocCount++; allocBytes += size; }

	// note: array blocks need not be a word multiple,
	// so we need to round up size to preserve alignment for future allocations
	size = (size + 3) & ~3; // round up request to word multiple

	if (size <= GC_MAX_SMALL_SIZE) {
	    VM_Processor st = VM_Processor.getCurrentProcessor();
	    VM_SizeControl  the_size   = st.GC_INDEX_ARRAY[size];
	    if (!the_size.next_slot.isZero()) {  // fastest path
		if (GC_COUNT_ALLOC) fastAllocCount++;
		objaddr = the_size.next_slot;

		if (DebugLink) checkAllocation(objaddr, the_size);

		the_size.next_slot = VM_Magic.getMemoryAddress(objaddr);

		if (DEBUG_NEXT_SLOT) checkNextAllocation(objaddr, the_size);

		VM_Magic.setMemoryWord(objaddr, 0);
		result = createArray(objaddr, tib, numElements, size, the_size);
	    }
	    else {
		result = allocateArray1(the_size, tib, numElements, size, the_size.ndx);
		allocType = 1;
	    }
	}
	else {
	    result = allocateArray1L(numElements, size, tib);
	    allocType = 2;
	}

	if (TIME_ALLOCATES) {
	    long pauseTime = VM_Time.cycles() - startTime;
	    allocTimeTotal += pauseTime;

	    VM_Thread t = VM_Thread.getCurrentThread();
	    final boolean isUser = ! (t.isGCThread || t.isIdleThread);
	    if (isUser) {
		if (pauseTime > allocTimeMax && allocType != 2) allocTimeMax = pauseTime;
		if (allocType == 2 && pauseTime > allocLargeTimeMax) allocLargeTimeMax = pauseTime;
		if (pauseTime > TIME_ALLOCATE_QUICK) {
		    if (allocType != 2) allocSlowCount++; else allocLargeSlowCount++; 
		}
	    }

	    if (PRINT_SLOW_ALLOCATES && pauseTime > TIME_ALLOCATE_QUICK) {
		print(")))) Slow allocateArray");
		if (allocType == 1) 
		    print("1");
		else if (allocType == 2)
		    print("1L");
		if (mustGC)
		    print("[gc1]");
		print(" of ", size);
		print(" bytes: ", (int) (pauseTime/TicksPerMicrosecond));
		println(" usec");
		if (! isUser) print("  [GC ALLOC]");
		println();
	    }
	}

	if (VM.VerifyAssertions) {
	    VM_Type type = VM_Magic.getObjectType(result);
	    VM.assert(!type.asArray().getElementType().isAddressType());
	}
	return result;
    }


    static Object
    allocateArray1 (VM_SizeControl the_size, Object[] tib, int numElements,
			int size, int ndx)
    {
	for (int i = 0; i < 3; i++) {
	    VM_Address objaddr = allocatey(the_size, tib, numElements, size, ndx);
	    if (!objaddr.isZero()) 
		return VM_Magic.addressAsObject(objaddr);

	    VM.sysWrite("GCing for array of size "); VM.sysWrite(GC_SIZEVALUES[ndx], false);  VM.sysWrite("\n");
	    collectGarbageOrAwaitCompletion("allocateArray1");

	    // try fast path again
	    // if (the_size.next_slot != 0) {
	    //   return VM_Magic.addressAsObject(makeArray(tib, numElements, size, the_size));
	    // }
	}

	// failure
	VM_Scheduler.trace("VM_Allocator::allocateArray1",
			   "couldn't collect enough to fill a request (bytes) for ", size);
	VM_Scheduler.traceback("VM_Allocator::allocateArray1");
	return null;
    }


    // move on to next block, or get a new block, or return 0
    static VM_Address
    allocatey (VM_SizeControl the_size, Object[] tib, int numElements, int size, int ndx) {

	recycleBlocksIfGarbageCollected(the_size);
	if (!the_size.next_slot.isZero()) {
	    return makeArray(tib, numElements, size, the_size);
	}

	VM_Processor st = VM_Processor.getCurrentProcessor();
	VM_BlockControl the_block = blocks[the_size.current_block];

	final int slotsPerBlock = the_block.alloc.length;

	while (the_block.nextblock != 0) {
	    int blockIndex = the_block.nextblock;
	    VM_BlockControl nextBlock = blocks[blockIndex];

	    // If the block is empty, and there are more blocks on the list, free the block to reduce
	    // fragmentation and keep storage utilization low.
	    if (AGGRESSIVE_FREEING && nextBlock.allocCount == 0 && nextBlock.nextblock != 0) {
		the_block.nextblock = nextBlock.nextblock;
		freeBlock(nextBlock, blockIndex);
		continue;
	    }

	    // Try allocating out of this block
	    the_size.current_block = blockIndex;
	    the_block = nextBlock;

	    if (the_block.allocCount == slotsPerBlock) {
		the_size.next_slot = VM_Address.zero(); // needed?
		continue;
	    }

	    if (build_list(the_block, the_size)) {
		// VM.sysWrite("?");
		return makeArray(tib, numElements, size, the_size);
	    }
	    // VM.sysWrite("X");
	}	// while.... ==> need to get another block
	  
	if (getnewblock(ndx) == 0) {
	    // VM.sysWrite("O");

	    the_block = blocks[the_size.current_block];
	    build_list_for_new_block(the_block, the_size);

            return makeArray(tib, numElements, size, the_size);
	}
	else
	    return VM_Address.zero();		// unable to get a new block; fail
    }


    private static VM_Address makeArray (Object[] tib, int numElements, int size, VM_SizeControl the_size) {
	if (VM.VerifyAssertions) VM.assert(!the_size.next_slot.isZero());

	VM_Address rawaddr = the_size.next_slot;

	if (DebugLink) checkAllocation(rawaddr, the_size);

	the_size.next_slot = VM_Magic.getMemoryAddress(rawaddr);

	if (DEBUG_NEXT_SLOT) checkNextAllocation(rawaddr, the_size);

	VM_Magic.setMemoryWord(rawaddr, 0);

	Object object = createArray(rawaddr, tib, numElements, size, the_size);
	return VM_Magic.objectAsAddress(object);
    }


    // Bootstrap block allocator.  Not used once system is up and running.
    // Obtains a free VM_BlockControl and returns it (as an int)
    // to the caller.  First use is for the VM_Processor constructor.
    static int
    getnewblockx (int ndx) {
	sysLockSmall.lock();

	if (first_freeblock == OUT_OF_BLOCKS) {
	    needToFreeBlocks = true;

	    sysLockSmall.release();

	    collectGarbageOrAwaitCompletion("getnewblockx");

	    if (first_freeblock == OUT_OF_BLOCKS) {
		VM.sysWrite(" out of free blocks");
		VM.sysExit(1300);
	    }

	    sysLockSmall.lock();
	}
	VM_BlockControl alloc_block = blocks[first_freeblock];
	int theblock = first_freeblock;
	first_freeblock = alloc_block.nextblock;

	blocks_available--;
	if (--blocksCountDown == 0) {
	    if (VM_Scheduler.allProcessorsInitialized) {
		gc_collect_now = true;
	    }
	    blocksCountDown = num_blocks >> 5;
	}

	sysLockSmall.unlock();

	alloc_block.nextblock = 0;  // this is last block in list for thissize
	alloc_block.slotsize  = GC_SIZEVALUES[ndx];
	int size = GC_BLOCKSIZE/GC_SIZEVALUES[ndx] ;

	// get space for alloc arrays from AIX.
	int mark_array_size = getByteArrayInstanceSize(size);
	VM_Address location = mallocHeap.allocateZeroedMemory(2*mark_array_size);

	// zero the array bytes used for allocation (mark is zeroed at
	// beginning of gc)
	VM_Memory.zero(location, location.add(mark_array_size));

	alloc_block.Alloc1 = makeByteArrayFromStorage(location, size, mark_array_size);
	alloc_block.Alloc2 = makeByteArrayFromStorage(location.add(mark_array_size), size, mark_array_size);
	alloc_block.alloc  = alloc_block.Alloc1;
	alloc_block.mark   = alloc_block.Alloc2;

	return theblock;
    }

 
    // call with this_size.current_block pointing to last block in list; creates new block appended to list
    // and updates this_size.current_block.
    private static int
    getnewblock (int ndx) {
	VM_Processor st = VM_Processor.getCurrentProcessor();
	VM_SizeControl this_size = st.sizes[ndx];
	VM_BlockControl current_block = blocks[this_size.current_block];
    
	// FIX BUG: if the same size appears > 1 time in the array (see caller)
	// blocks assigned before the last time are lost 
	// if (current_block.nextblock != 0) return 0;

	// dfb: Huh?  I don't understand the above if/return statement, so for now assume it never happens.
	if (VM.VerifyAssertions) VM.assert(current_block.nextblock == 0);

	/// NEW LOGIC; trigger gc when no more blocks are available
	/// if (--blocks_available <= 2) {
	///   gc_collect_now = true;
	///   if (GC_TRIGGERGC)    VM_Scheduler.trace(" gc triggered by small object alloc \n", "XX");
	/// }

	//  NEW LOGIC; return -1 to indicate small object triggered gc.

	sysLockSmall.lock();

	if (first_freeblock == OUT_OF_BLOCKS) {
	    needToFreeBlocks = true;

	    sysLockSmall.release();

	    gc1();

	    freeBlocks(st);	// free locally and hope this tides us over

	    if (first_freeblock == OUT_OF_BLOCKS) {
		VM.sysWrite("$$$$ Out of free blocks\n");
		// VM.sysExit(1300);
	    }

	    return -1;
	}

	int newblock = first_freeblock;
	VM_BlockControl alloc_block = blocks[newblock];
	first_freeblock = alloc_block.nextblock;	// new first_freeblock

	blocks_available--;
	if (--blocksCountDown == 0) {
	    if (VM_Scheduler.allProcessorsInitialized) {
		gc_collect_now = true;
	    }
	    blocksCountDown = num_blocks >> 5;
	}

	sysLockSmall.unlock();

	alloc_block.nextblock = 0;	// this is last block in list for thissize
	alloc_block.slotsize  = GC_SIZEVALUES[ndx];

	int size = GC_BLOCKSIZE/GC_SIZEVALUES[ndx] ;	
	// Get space for alloc arrays from AIX.
	int mark_array_size = getByteArrayInstanceSize(size);
	VM_Address location = mallocHeap.allocateZeroedMemory(2*mark_array_size);

	// zero the array bytes used for allocation (mark is zeroed at
	// beginning of gc)
	VM_Memory.zero(location, location.add(mark_array_size));

	alloc_block.Alloc1 = makeByteArrayFromStorage(location, size, mark_array_size);
	alloc_block.Alloc2 = makeByteArrayFromStorage(location.add(mark_array_size), size, mark_array_size);
	alloc_block.alloc  = alloc_block.Alloc1;
	alloc_block.mark   = alloc_block.Alloc2;

	current_block.nextblock = newblock;
	this_size.current_block = newblock;

	return 0;
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


    private static final void collectGarbageOrAwaitCompletion (String originator) {
	if (GC_TRIGGERGC) 
	    VM_Scheduler.trace(originator, "Triggered garbage collection");

	double startTime = VM_Time.now();

	if (gcInProgress) {
	    int pid = VM_Processor.getCurrentProcessor().id;
	    if (VM.VerifyAssertions) 
		VM.assert(VM_Scheduler.numProcessors == 1 || pid != VM_Scheduler.numProcessors) ;
	    else if (VM_Scheduler.numProcessors != 1 && pid == VM_Scheduler.numProcessors)
		return;		// don't suspend GC while GCing

	    // This should be true; for now, make it so.
	    // VM.assert(VM_Thread.getCurrentThread().processorAffinity != null);
	    if (VM_Thread.getCurrentThread().processorAffinity == null)
		VM_Thread.getCurrentThread().processorAffinity = VM_Processor.getCurrentProcessor();

	    // heapInfo();	// see what's happening before we choke

	    VM_Scheduler.gcWaitMutex.lock();
	    VM_Thread.getCurrentThread().yield(VM_Scheduler.gcWaitQueue, VM_Scheduler.gcWaitMutex);

	    freeBlocks(VM_Processor.getCurrentProcessor());

	    // heapInfo();	// and after

	    double pauseTime = VM_Time.now() - startTime;
	    if (SHOW_ALLOCATION_DELAY) {
		VM.sysWrite("$$$$ Processor "); VM.sysWrite(pid, false);
		VM.sysWrite(" suspended from ");  VM.sysWrite(originator);
		VM.sysWrite(" for ");  VM.sysWrite((int)(pauseTime*1000000.0), false);
		VM.sysWrite(" usec\n");
	    }
	} else {
	    // VM.sysWrite("+");
	    gc1();
	}
    }


    // If a collection has happened, reset free list to beginning of block list 
    private static boolean recycleBlocksIfGarbageCollected (VM_SizeControl the_size) 
    {
	if (the_size.last_allocated != 0) {
	    // VM.sysWrite("%");

	    if (needToFreeBlocks)
		freeBlocks(the_size); // reap free blocks

	    VM_BlockControl first_block = blocks[the_size.first_block];
	    the_size.current_block = the_size.first_block;

	    if (! build_list(first_block, the_size))
		the_size.next_slot = VM_Address.zero();

	    the_size.last_allocated = 0; // remember that we've moved back to the beginning
	    return true;
	}
	else
	    return false;
    }


    // build, in the block, the list of free slot pointers, and update the
    // associated VM_SizeControl; return the address (as int) of the first
    // available slot, or 0 if there is none
    //
    static boolean
    build_list (VM_BlockControl the_block, VM_SizeControl the_size) 
    {
	byte[] the_alloc = the_block.alloc;
	int first_free = 0, i = 0, j;
	VM_Address current, next;

	for (; i < the_alloc.length ; i++) 
	    if (the_alloc[i] == 0) break;
	if ( i == the_alloc.length ) {
	    if (DebugLink) 
		VM_Scheduler.trace("build_list: ", "found a full block", the_block.slotsize);
	    return false;	// no free slots in this block
	}
	// here is the first
	else current = the_block.baseAddr.add(i * the_block.slotsize);  
	VM_Memory.zero(current.add(4), current.add(the_block.slotsize));
	the_size.next_slot = current;

	if (DebugLink && !the_size.next_slot.isZero()) {
	    if (!isValidSmallHeapPtr(the_size.next_slot)) VM.sysFail("Bad ptr");
	    if (!isPtrInBlock(the_size.next_slot, the_size)) VM.sysFail("Pointer out of block");
	}

	// now find next free slot
	i++;	 
	for (; i < the_alloc.length ; i++) 
	    if (the_alloc[i] == 0) break;
	if (i == the_alloc.length ) {	// next block has only 1 free slot
	    VM_Magic.setMemoryWord(current, 0);
	    if (DebugLink) 
		VM_Scheduler.trace("build_list: ", "found blk w 1 free slot", the_block.slotsize);
	    if (DebugLink) do_check(the_block, the_size);
	    return true;
	}

	next = the_block.baseAddr.add(i * the_block.slotsize);
	VM_Magic.setMemoryAddress(current, next);
	current = next; 
	VM_Memory.zero(current.add(4), current.add(the_block.slotsize));

	// build the rest of the list; there is at least 1 more free slot
	for (i = i + 1; i < the_alloc.length ; i++) {
	    if (the_alloc[i] == 0) {	// This slot is free
		next = the_block.baseAddr.add(i * the_block.slotsize);
		VM_Magic.setMemoryAddress(current, next);	// enter list pointer
		current = next;
		VM_Memory.zero(current.add(4), current.add(the_block.slotsize));
	    }
	}
	VM_Magic.setMemoryWord(current,0);		// set the end of the list
	if (DebugLink) do_check(the_block, the_size);
	return true;
    } 


    // A debugging routine: called to validate the result of build_list 
    // and build_list_for_new_block
    // 
    private static void
    do_check (VM_BlockControl the_block, VM_SizeControl the_size) 
    {
	int count = 0;
	if (blocks[the_size.current_block] != the_block) {
	    VM_Scheduler.trace("do_check", "BlockControls don't match");
	    VM.sysFail("BlockControl Inconsistency");
	}
	if (the_size.next_slot.isZero()) VM_Scheduler.trace("do_check", "no free slots in block");
	VM_Address temp = the_size.next_slot;
	while (!temp.isZero()) {
	    if (temp.LT(the_block.baseAddr) || temp.GT(the_block.baseAddr.add(GC_BLOCKSIZE))) {
		VM_Scheduler.trace("do_check: TILT:", "invalid slot ptr", temp.toInt());
		VM.sysFail("Bad freelist");
	    }
	    count++;
	    temp = VM_Magic.getMemoryAddress(temp);
	}

	if (count > the_block.alloc.length)  {
	    VM_Scheduler.trace("do_check: TILT:", "too many slots in block");
	    VM.sysFail("too many slots");
	}
	//  VM_Scheduler.trace("do_check", "slot_size is", the_block.slotsize);
	//  VM_Scheduler.trace("do_check", "free slots are", count);
    }


    // Input: a VM_BlockControl that was just assigned to a size; the VM_SizeControl
    // associated with the block
    // return: the address of the first slot in the block
    //
    static void
    build_list_for_new_block (VM_BlockControl the_block, VM_SizeControl the_size)
    {
	byte[] the_alloc = the_block.alloc;
	VM_Address current = the_block.baseAddr;
	VM_Memory.zero(current, current.add(GC_BLOCKSIZE));
	int delta   = the_block.slotsize;
	the_size.next_slot = current ;	// next one to allocate
	int i;

	if (DebugLink && (!the_size.next_slot.isZero())) {
	    if (!isValidSmallHeapPtr(the_size.next_slot)) VM.sysFail("Bad ptr");
	    if (!isPtrInBlock(the_size.next_slot, the_size)) VM.sysFail("Pointer out of block");
	}
	/// N.B. With AUTO-CHECK, length is 1 greater than # of slots: TAKE-OUT!!	
	for (i = 0; i < the_alloc.length -1; i++) {
	    VM_Magic.setMemoryAddress(current, current.add(delta));
	    current = current.add(delta);
	}
	// last slot does not point forward
	//   VM_Magic.setMemoryWord(current, 0);
	if (DebugLink) do_check(the_block, the_size);
	return ;
    }
	
       
    private static void checkAllocation(VM_Address objaddr, VM_SizeControl the_size) {
	if (! isValidSmallHeapPtr(objaddr)) 
	    VM.sysFail("Bad ptr");
	if (! isPtrInBlock(objaddr, the_size)) 
	    VM.sysFail("Pointer out of block");

	// if (isSetYet(blocks[the_size.current_block], objaddr))
	//    VM.sysFail("Allocating already allocated slot");
    }

    private static void checkNextAllocation(VM_Address objaddr, VM_SizeControl the_size) {
	if (!the_size.next_slot.isZero()) {
	    VM_BlockControl the_block = blocks[the_size.current_block];
	    int blockPage = the_block.baseAddr.toInt() >> LOG_GC_BLOCKSIZE;
	    int slotPage  = the_size.next_slot.toInt() >> LOG_GC_BLOCKSIZE;

	    if (blockPage != slotPage) {
		VM.sysWrite("**** Bad next free address from slot at ");
		VM.sysWrite(objaddr);
		VM.sysWrite("\n");
		VM.assert(false);
	    }

	    checkAllocation(the_size.next_slot, the_size);
	}
    }


    static void freeBlocks (VM_Processor processor) {
	if (GC_TRACEALLOCATOR)
	    VM_Scheduler.trace("VM_Allocator", "entering freeBlocks: blocks_available =",blocks_available);

	double startTime;
	if (TIME_FREEBLOCKS) startTime = VM_Time.now();

	for (int i = 0; i < GC_SIZES; i++) 
	    freeBlocks(processor.sizes[i]);

	if (TIME_FREEBLOCKS) {
	    int pause = (int) ((VM_Time.now() - startTime) * 1000000.0); // Pause in usecs
	    VM.sysWrite("|||| freeBlocks: "); VM.sysWrite(pause, false); VM.sysWrite(" usec\n");
	}

	if (GC_TRACEALLOCATOR)
	    VM_Scheduler.trace("VM_Allocator", "leaving freeBlocks: blocks_available =",blocks_available);
    }


    static void freeBlocks (VM_SizeControl size) {
	VM_BlockControl prevBlock = blocks[size.first_block];
	int currentBlockNumber = size.current_block;

	for (int blockNumber = prevBlock.nextblock; blockNumber != 0; blockNumber = prevBlock.nextblock) {

	    VM_BlockControl block = blocks[blockNumber];

	    if (block.allocCount == 0 && blockNumber != currentBlockNumber) {
		prevBlock.nextblock = block.nextblock;
		freeBlock(block, blockNumber);
	    } else {
		prevBlock = block;
	    }
	}	
    }


    private static void freeBlock (VM_BlockControl block, int blockNumber) {

	VM_Address gcArraysAddress = VM_Magic.objectAsAddress(block.Alloc1).sub(byteArrayHeaderSize);
	mallocHeap.free(gcArraysAddress);

	// null out mark & alloc array ptrs..so debugging code which marks objects will work
	block.mark   = null;  
	block.alloc  = null;
	block.Alloc1 = null;  
	block.Alloc2 = null;

	sysLockSmall.lock();
	    block.nextblock = first_freeblock;
	    first_freeblock = blockNumber;
	    blocks_available++;
	sysLockSmall.unlock();
    }

    /////////////////////////////////////////////////////////////////////////////
    // LARGE OBJECT MANAGEMENT
    /////////////////////////////////////////////////////////////////////////////

    // Allocate a large scalar
    static Object
    allocateScalar1L (Object[] tib, int size) 
    {
	VM_Address rawaddr = getlargeobj(size);

	if (rawaddr.isZero()) {
	    for (int i = 0; i < 3 && rawaddr.isZero(); i++) {
		collectGarbageOrAwaitCompletion("allocateScalar1L");
		rawaddr = getlargeobj(size);
	    }
	}

	if (rawaddr.isZero()) {
	    VM_Scheduler.trace("VM_Allocator::allocateScalar1L",
			       "couldn't collect enough to fill a request (bytes) for ", size);
	    VM.sysExit(1300);
	}

	Object object = createScalar(rawaddr, tib, size, null);
	return object;
    }
     
      
    static Object
    allocateArray1L (int numElements, int size, Object[] tib)
    {
	VM_Address memAddr = getlargeobj(size);      // address of head of new object

	if (memAddr.isZero()) {
	    for (int i = 0; i < 6 && memAddr.isZero(); i++) {
		collectGarbageOrAwaitCompletion("allocateArray1L");

		memAddr = getlargeobj(size);
	    }
	}

	if (memAddr.isZero()) {
	    VM_Scheduler.trace("VM_Allocator::allocateArray1L",
			       "couldn't collect enough to fill a request (bytes) for ", size);
	    VM.sysExit(1300);
	}

	Object object = createArray(memAddr, tib, numElements, size, null);
	return object;
    }


    // made public so it could be called from VM_WriteBuffer
    public static VM_Address
    getlargeobj (int size) {
	int num_pages = (size + LARGE_BLOCK_SIZE - 1)/LARGE_BLOCK_SIZE;		// Number of pages needed
	int last_possible = largeSpacePages - num_pages;
	long startTime;

	if (TRACE_LARGE) println("Requesting large object of page multiple ", num_pages);

	if (PRINT_SLOW_ALLOCATES) startTime = VM_Time.cycles(); 

	sysLockLarge.lock();

	while (largeSpaceAlloc[large_last_allocated] != 0)
	    large_last_allocated += largeSpaceAlloc[large_last_allocated];

        int first_free = large_last_allocated;

	VM_Address target = VM_Address.zero();	// initially, invalid memory address

	while (first_free <= last_possible) {
	    // Now find contiguous pages for this object
	    // first find the first available page
	    // i points to an available page: remember it

	    int i;

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

		if (GC_STATISTICS) {
		    // increment count of large objects alloc'd
		    if (num_pages < GC_LARGE_SIZES) countLargeAlloc[num_pages]++;	
		    else countLargeAlloc[GC_LARGE_SIZES - 1]++;
		}

		sysLockLarge.unlock();	//release lock *and synch changes*

		target = largeHeap.start.add(LARGE_BLOCK_SIZE * first_free);

		// zero space before return
		// NOTE: This takes about 1 us/100 bytes; should be moved to collector
		// VM_Memory.zero(target, target + size);

		break;
	    }
	    else {
		// found space for the new object without skipping any space		
		// first free area did not contain enough contig. pages

		first_free = i + largeSpaceAlloc[i]; 
		while (largeSpaceAlloc[first_free] != 0) 
		    first_free += largeSpaceAlloc[first_free];
	    }
	}

	if (target.isZero()) 
	    sysLockLarge.release();	//release lock: won't keep change to large_last_alloc'd

	if (TRACE_LARGE) {
	    println("Denied!");
	    freeLargeSpaceDetail();
	}

	if (PRINT_SLOW_ALLOCATES) {
	    long pauseTime = VM_Time.cycles() - startTime;

	    if (pauseTime > TIME_ALLOCATE_QUICK) {
		print(")))) Slow getlargeobj of ", size);
		print(" bytes: ", (int) (pauseTime/TicksPerMicrosecond));  
		println(" usec");
	    }
	}

	return target;		// return allocated address or invalid value
    }

    /////////////////////////////////////////////////////////////////////////////
    // GARBAGE COLLECTION ROUTINES
    /////////////////////////////////////////////////////////////////////////////

  public static void heapExhausted(VM_Heap heap, int size, int count) {
    gc1();
  }

    // To be able to be called from java/lang/runtime, or internally
    public static void
    gc ()  {
	if (GC_TRIGGERGC)
	    VM_Scheduler.trace(" gc triggered by external call to gc() \n \n", "XX");
	gc1();
    }

    public static void
    gc1 ()
    {
	double time;
	//gc_collect_now = true;

	// VM.sysWrite("#");

	if (GC_TIMING) time = VM_Time.now();

	// Tell gc thread to reclaim space, then wait for it to complete its work.
	// The gc thread will do its work by calling collect(), below.
	//
	VM_CollectorThread.collect(VM_CollectorThread.collect);

	if (GC_TIMING && GC_TRACEALLOCATOR) {
	    time = VM_Time.now() - time;
	    VM_Scheduler.trace("GC time at mutator","(millisec)",(int)(time*1000.0));
	}

    }

    static void
    collect () {
	double scanStart, scanEnd, bufferEnd;

	VM_RCCollectorThread t = VM_Magic.threadAsRCCollectorThread(VM_Thread.getCurrentThread());
	VM_Processor p = VM_Processor.getCurrentProcessor();

	if (p.id == VM_Scheduler.PRIMORDIAL_PROCESSOR_ID) {

	    if (verbose >= 1||GC_TIMING) gcStartTime = VM_Time.now();
	    gcCount++;

	    if (VM.VerifyAssertions) VM_Scheduler.assert(gcInProgress == true);

	    if (GC_TRACEALLOCATOR) {
		VM.sysWrite("\n|||| RCGC: Beginning Garbage Collection ");
		VM.sysWrite(gcCount,false);
		VM.sysWrite("\n");
	    }

	    // DEBUGGING AID - mark all objects reachable from statics
	    // later check that we are not freeing a marked object
	    // - this may not be an exact check with multiple gc threads 
	    // - executing in sequence
	    if (GC_MARK_REACHABLE_OBJECTS && ! VM_RCCollectorThread.GC_ALL_TOGETHER) {
		prepareForMarking();  // zero mark bits, invert bootimage mark value
		refToLookFor = VM_Address.zero();     // just mark, not look for specific reg
		markStatics();
	    }

	}

	if (GC_TRACEALLOCATOR) {
	    VM.sysWrite("||||  RCGC: Collection ");  VM.sysWrite(gcCount,false);  VM.sysWrite(" for Processor ");
	    VM.sysWrite(p.id, false);                VM.sysWrite(" Epoch ");      VM.sysWrite(p.localEpoch, false);
	    VM.sysWrite("\n");
	}

	if (TIMING_DETAILS) scanStart = VM_Time.now();

	VM_StackBuffer.gc_scanStacks();
	
	if (TIMING_DETAILS) scanEnd = VM_Time.now();

	saveCurrentMutationBuffer(t, p);
	      
	// allocate new inc/dec buffer for the processor - for subsequent writebarrier generated entries
	VM_RCBuffers.allocateIncDecBuffer(p);

	if (TIMING_DETAILS) bufferEnd = VM_Time.now();

	if (! VM_RCCollectorThread.GC_ALL_TOGETHER) {
	    // preprocessMutationBuffer(t, p);

	    // Mutation buffer increments
	    processMutationBufferIncrements(t, p.localEpoch);

	    // Stack buffer increments
	    VM_StackBuffer.gc_processStackBuffers(true, p);

	    if (p.localEpoch > 0) {
		// Mutation buffer decrements
		processMutationBufferDecrements(t, p.localEpoch - 1);

		// Stack buffer decrements
		VM_StackBuffer.gc_processStackBuffers(false, p);
	    }

	    // tell allocate routines to recycle blocks instead of allocating new ones
	    for (int i = 0; i < GC_SIZES; i++) {
		VM_SizeControl this_size = p.sizes[i];
		this_size.last_allocated = -1; // next time allocator needs a block, flag tells it to recycle instead
	    }
	}

	if (needToFreeBlocks || (p.localEpoch % 6) == 0) // REMOVE SECOND CONDITION!!
	    freeBlocks(p);


	if (TIMING_DETAILS) {
	    double freeTime = VM_Time.now() - bufferEnd;
	    double scanTime = scanEnd - scanStart;
	    double bufferTime = bufferEnd - scanEnd;

	    VM.sysWrite("||||  Pause breakdown: ");	     VM.sysWrite((int)(scanTime*1000000.0), false);
	    VM.sysWrite(" scan;  "); 	                     VM.sysWrite((int)(bufferTime*1000000.0), false);
	    VM.sysWrite(" buffers;  ");	                     VM.sysWrite((int)(freeTime*1000000.0), false);
	    VM.sysWrite(" freeing.\n");
	}

	if (GC_TRACEALLOCATOR) {
           VM.sysWrite("||||  Processor "); VM.sysWrite(p.id, false);
           VM.sysWrite(" finished collection "); VM.sysWrite(gcCount,false);
	   if (RC_COUNT_EVENTS && ! VM_RCCollectorThread.GC_ALL_TOGETHER) {
	       VM.sysWrite(";  "); VM.sysWrite(internalDecs,false);
	       VM.sysWrite(" interior DECs;  "); VM.sysWrite(nonZeroDecs,false);
	       VM.sysWrite(" DECs to non-zero");
	   }
	   VM.sysWrite("\n");
	}

	if (RC_COUNT_EVENTS && ! VM_RCCollectorThread.GC_ALL_TOGETHER) {
	    totalNonZeroDecs  += nonZeroDecs;    nonZeroDecs = 0;
	    totalInternalDecs += internalDecs;   internalDecs = 0;
	}

	if (p.id == VM_Scheduler.numProcessors) {	
	    if (VM_RCCollectorThread.GC_ALL_TOGETHER) {
		if (GC_MARK_REACHABLE_OBJECTS) {
		    prepareForMarking();  // zero mark bits, invert bootimage mark value
		    refToLookFor = VM_Address.zero();     // just mark, not look for specific reg
		    markStatics();
		}

		for (int i = 0; i < VM_Scheduler.threads.length; i++) {
		    VM_Thread thread = VM_Scheduler.threads[i];
		    
		    if (thread != null) {
			if (thread.isGCThread) {
			    VM_RCCollectorThread collector = VM_Magic.threadAsRCCollectorThread(thread);
			    processMutationBufferIncrements(collector, p.localEpoch);
			}
			else 
			    VM_StackBuffer.processStackBuffer(thread, true); // increment for stack buffer
		    }
		}

		if (p.localEpoch > 0) {
		    for (int i = 0; i < VM_Scheduler.threads.length; i++) {
			VM_Thread thread = VM_Scheduler.threads[i];

			if (thread != null) {
			    if (thread.isGCThread) {
				VM_RCCollectorThread collector = VM_Magic.threadAsRCCollectorThread(thread);
				processMutationBufferDecrements(collector, p.localEpoch - 1);
			    }
			    else 
				VM_StackBuffer.processStackBuffer(thread, false); // decrements for stack buffer
			}
		    }

		    // tell allocate routines to recycle blocks instead of allocating new ones
		    // (don't do this in epoch 0 because there are no decrements then)
		    for (int j = 1; j <= VM_Scheduler.numProcessors; j++) {
			VM_Processor jp = VM_Scheduler.processors[j];

			for (int i = 0; i < GC_SIZES; i++) {
			    VM_SizeControl this_size = jp.sizes[i];
			    // next time allocator needs a block, flag tells it to recycle instead
			    this_size.last_allocated = -1; 
			}
		    }
		}
	    } // VM_RCCollectorThread.GC_ALL_TOGETHER

	    if (VM_RCGC.cycleCollection && (VM_RootBuffer.ASYNC || VM_Scheduler.numProcessors == 1))
		VM_RootBuffer.buffer.processCycles(); // Perform cycle collection at end of epoch

	    if (TRACK_MEMORY_USAGE) {
		int bytesInUse = allocatedMemory();
		if (bytesInUseMax < bytesInUse) bytesInUseMax = bytesInUse;
		bytesInUseTotal += bytesInUse;
	    }

	    if (GC_TIMING) {
		gcMinorTime += VM_Time.now() - gcStartTime;
		gcTotalTime += VM_Time.now() - gcStartTime;
	    }
	    
	    needToFreeBlocks = false;

	    if (Report || GC_TRACEALLOCATOR) {
                VM.sysWrite("|||| RCGC: Collection ");
		VM.sysWrite(gcCount,false);
		VM.sysWrite(" finished.  "); 
		if (RC_COUNT_EVENTS) {
		    VM.sysWrite(internalDecs,false);
		    VM.sysWrite(" interior DECs;  "); VM.sysWrite(nonZeroDecs,false);
		    VM.sysWrite(" DECs to non-zero\n");
		}

		VM.sysWrite("|||| highest_block = ");
		VM.sysWrite(highest_block,false);
		VM.sysWrite(" first_freeblock = ");
		VM.sysWrite(first_freeblock,false);
		VM.sysWrite(" blocks_available = ");
		VM.sysWrite(blocks_available,false);
		VM.sysWrite("\n");
	    }    

	    if (RC_COUNT_EVENTS) {
		totalNonZeroDecs  += nonZeroDecs;    nonZeroDecs = 0;
		totalInternalDecs += internalDecs;   internalDecs = 0;
	    }

	    if (GC_CONTINUOUSLY && VM_Scheduler.numProcessors > 1)
		gc1();		// immediately start next epoch on processor 1
	}
    }



    // Save current mutation buffer (held in the processor) in the collector thread for this processor
    //
    static void saveCurrentMutationBuffer(VM_RCCollectorThread t, VM_Processor p) {
	int bufIndex                  = p.localEpoch % VM_RCBuffers.MAX_INCDECBUFFER_COUNT;
	t.incDecBuffers[bufIndex]     = p.incDecBuffer.toInt();
	t.incDecBuffersTops[bufIndex] = p.incDecBufferTop.toInt();
	t.incDecBuffersMaxs[bufIndex] = p.incDecBufferMax.toInt();
    }


    static void processMutationBufferIncrements(VM_RCCollectorThread t, int epoch) {
	processMutationBuffer(t, epoch, true);
    }


    static void processMutationBufferDecrements(VM_RCCollectorThread t, int epoch) {
	processMutationBuffer(t, epoch, false);
    }


    static void processMutationBuffer(VM_RCCollectorThread t, int epoch, boolean increment) {

	final int bufIndex    = epoch % VM_RCBuffers.MAX_INCDECBUFFER_COUNT;
	final VM_Address top  = VM_Address.fromInt(t.incDecBuffersTops[bufIndex]);

	int incEntries   = 0;
	int decEntries   = 0;

	if (GC_TRACEALLOCATOR) VM.sysWrite("|||| Mutation buffer...\n"); 

	for (VM_Address start = VM_Address.fromInt(t.incDecBuffers[bufIndex]), next = VM_Address.zero(); 
	     !start.isZero(); start = next) {

	    VM_Address nextAddr = start.add(VM_RCBuffers.INCDEC_BUFFER_NEXT_OFFSET);
	    next = VM_Magic.getMemoryAddress(nextAddr);

	    // if (increment && (VM_Magic.getMemoryWord(start) & 2) != 0) { // USE SYMBOLIC CONST
	    // VM.sysWrite("**** Skipping scanning for increments in preprocessed buffer\n");
	    // continue;
	    // }

	    // If this is the last buffer, stop at last filled in slot in "current" buffer.  Otherwise
	    //   process entire buffer of entries.  Note "+4" is in case of a final pair.
	    VM_Address end;
	    if (next.isZero())
		end = top;
	    else {
		end = start.add(VM_RCBuffers.INCDEC_BUFFER_LAST_OFFSET);
		if (VM_Magic.getMemoryWord(end.add(4)) != 0)
		    end = end.add(4);
	    }

	    if (GC_TRACEALLOCATOR_DETAIL) {	    
		VM.sysWrite("Processing mutation buffer ");   VM.sysWrite(increment ? "increments" : "decrements");
		VM.sysWrite(": start = "); VM.sysWrite(start);
		VM.sysWrite(" end = ");    VM.sysWrite(end);  VM.sysWrite("\n");
	    }

	    for (VM_Address bufptr = start; bufptr.LE(end); bufptr = bufptr.add(4)) {
		final int entry  = VM_Magic.getMemoryWord(bufptr);
		final VM_Address object = VM_Address.fromInt(entry & (~3));     // HACK: use VM_RCBuffers.OBJECT_MASK;
		final int lowbit = entry & VM_RCBuffers.DECREMENT_FLAG;

		if (entry == 0) break; // temporary loop end for preprocessed buffers

		if (VM.VerifyAssertions) VM.assert(!object.isZero());

		if (GC_FILTER_MALLOC_REFS && isMalloc(object)) {
		    if (VM_RCBarriers.DONT_BARRIER_BLOCK_CONTROLS) { // else too verbose
			VM.sysWrite("Ignoring malloc ref in mutation buffer: ");
			VM.sysWrite(object);
			printType(object);
		    }
		    continue;	// skip things in the malloc area (they may be gone by now)
		}

		if (increment && lowbit == 0) {
		    if (VM.VerifyAssertions) checkRef("Bad inc ref in mutation buffer", object, bufptr);
		    if (RC_COUNT_EVENTS) mutationIncCount++;
		    incrementRC(object);
		}
		else if ((! increment) && lowbit == 1) {
		    if (VM.VerifyAssertions) checkRef("Bad dec ref in mutation buffer", object, bufptr);
		    if (RC_COUNT_EVENTS) mutationDecCount++;
		    decrementRC(object);
		}

		if (GC_TRACEALLOCATOR) {
		    if (lowbit == 0)
			incEntries++;
		    else
			decEntries++;
		}
	    }

	    if (! increment)
		VM_RCBuffers.freeBuffer(start);

	    if (GC_TRACEALLOCATOR_DETAIL) VM.sysWrite("Finished buffer\n ");
	}

	if (GC_TRACEALLOCATOR) {
	    VM.sysWrite("|||| Mutation buffer: "); 
	    if (increment) {
		VM.sysWrite(incEntries, false); VM.sysWrite(" INCs processed [");
	    }
	    else {
		VM.sysWrite(decEntries, false); VM.sysWrite(" DECs processed [");
	    }
	    VM.sysWrite(incEntries+decEntries, false);  
	    VM.sysWrite(" entries; ");              
	    if (increment) 
		VM.sysWrite(decEntries, " DECs]\n");
	    else
		VM.sysWrite(incEntries, " INCs]\n");
	}
    }


    static void checkRef(String msg, VM_Address object, VM_Address bufptr) {
	if (! isPossibleRefOrMalloc(object)) {
	    VM.sysWrite("**** ");
	    VM.sysWrite(msg);
	    VM.sysWrite(": ");
	    VM.sysWrite(object);
	    VM.sysWrite("\n");

	    mallocHeap.show();
	    VM_StackBuffer.dumpBufferInfo(bufptr, object);

	    if (VM.VerifyAssertions) VM.assert(false);
	}
    }

    static void preprocessMutationBuffers (VM_RCCollectorThread t) 
    {
	for (int i = 1; i <= VM_Scheduler.numProcessors; i++) 
	    preprocessMutationBuffer(t, VM_Scheduler.processors[i]);
    }


    static void preprocessMutationBuffer (VM_RCCollectorThread t, VM_Processor p) 
    {
	VM_Address dest      = VM_Address.zero();
	VM_Address deststart = VM_Address.zero();
	VM_Address destmax   = VM_Address.zero();
	VM_Address last      = VM_Address.zero();

	int inc = 0;
	int dec = 0;
	int nop = 0;
	int freed = 0;

	final int bufIndex  = p.localEpoch % VM_RCBuffers.MAX_INCDECBUFFER_COUNT;

	for (VM_Address start = VM_Address.fromInt(t.incDecBuffers[bufIndex]), next = VM_Address.zero(); 
	     !start.isZero(); start = next) {

	    final VM_Address nextAddr = start.add(VM_RCBuffers.INCDEC_BUFFER_NEXT_OFFSET);
	    next = VM_Magic.getMemoryAddress(nextAddr);

	    if (next.isZero()) {
		last = start;
		break;		// last buffer chunk is still active; leave it alone
	    }

	    if ((VM_Magic.getMemoryWord(start) & 2) != 0) {
		VM.sysWrite("Skipping chunk already preprocessed\n");
		continue;	// buffer chunk already preprocessed; skip to next one
	    }

	    if (dest.isZero()) {
		dest      = start;
		deststart = start;
		destmax   = dest.add(VM_RCBuffers.INCDEC_BUFFER_LAST_OFFSET);
	    }

	    VM_Address end = start.add(VM_RCBuffers.INCDEC_BUFFER_LAST_OFFSET);
	    if (VM_Magic.getMemoryWord(end.add(4)) != 0)
		end = end.add(4);

	    for (VM_Address bufptr = start; bufptr.LE(end); bufptr = bufptr.add(4)) {
		final int entry  = VM_Magic.getMemoryWord(bufptr);
		final VM_Address object = VM_Address.fromInt(entry & VM_RCBuffers.OBJECT_MASK);
		final int lowbit = entry & VM_RCBuffers.DECREMENT_FLAG;

		if (VM.VerifyAssertions) VM.assert(!object.isZero());

		if (lowbit == 0) {
		    checkRef("Bad inc ref in preprocessing of mutation buffer", object, bufptr);
		    incrementRC(object);
		    if (GC_TRACEALLOCATOR) inc++;
		}
		else if (isGreaterThanOneReferenceCount(object)) {
		    checkRef("Bad dec ref in preprocessing of mutation buffer", object, bufptr);
		    decrementRC(object);
		    if (GC_TRACEALLOCATOR) dec++;
		}
		else {
		    if (GC_TRACEALLOCATOR) nop++;
		    
		    // Note: last slot will always be empty, because it would only have been used in the first
		    // place if the last entry was an inc/dec pair; but then we would have preprocessed the inc.

		    VM_Magic.setMemoryWord(dest, entry);
		    dest = dest.add(4);

		    if (dest.GT(destmax)) {
			if (VM.VerifyAssertions) VM.assert(deststart.NE(start));

			// VM_Magic.setMemoryWord(destmax+4, dest-4); // save pointer to last entry
			VM_Magic.setMemoryWord(dest, 0);
			VM_Magic.setMemoryAddress(deststart.add(VM_RCBuffers.INCDEC_BUFFER_NEXT_OFFSET), start);

			dest      = start;
			deststart = start;
			destmax   = start.add(VM_RCBuffers.INCDEC_BUFFER_LAST_OFFSET);

			if (VM.VerifyAssertions) VM.assert(!dest.isZero());
		    }
		}
	    }

	    if (start.EQ(deststart)) { // source and destination buffers are the same
		int entry = VM_Magic.getMemoryWord(start);
		VM_Magic.setMemoryWord(start, entry | 2); // indicate buffer chunk has been preprocessed
	    }
	    else {		// different buffers; free this one
		mallocHeap.free(start);
		if (GC_TRACEALLOCATOR) freed++;
	    }
	}

	if (!dest.isZero()) {
	    // VM_Magic.setMemoryWord(destmax+4, dest-4); // save pointer to last entry
	    VM_Magic.setMemoryWord(dest, 0);  // null terminate for now
	    // point to final (currently active) chunk
	    VM_Magic.setMemoryAddress(deststart.add(VM_RCBuffers.INCDEC_BUFFER_NEXT_OFFSET), last);
	}

	if (GC_TRACEALLOCATOR) {
	    VM.sysWrite("|||| Preprocessing: "); VM.sysWrite(inc, false);
	    VM.sysWrite(" INC, ");        VM.sysWrite(dec, false);
	    VM.sysWrite(" DEC, ");        VM.sysWrite(nop, false);
	    VM.sysWrite(" retained.  ");  VM.sysWrite(freed, false);
	    VM.sysWrite(" buffers freed.\n");
	}
    }


    public static boolean isPossibleRefOrMalloc(VM_Address ref) {
	return(isPossibleRef(ref, true));
    }


    public static boolean isPossibleRef (VM_Address ref) {
	return(isPossibleRef(ref, false));
    }

    public static boolean isPossibleRef (VM_Address ref, boolean allowMalloc)
    {
	if (ref.isZero()) return false; // null
	if (mallocHeap.refInHeap(ref)) return true;

	if (isPossible(ref) || (allowMalloc && isMalloc(ref))) {  // possibly a valid pointer
	    VM_Address tibptr = VM_Magic.objectAsAddress(VM_ObjectModel.getTIB(ref));
	    if (isPossible(tibptr)) {
		VM_Address classptr = VM_Magic.getMemoryAddress(tibptr);
		if (isPossible(classptr)) {
		    VM_Address tibtibptr = VM_Magic.objectAsAddress(VM_ObjectModel.getTIB(tibptr));
		    if (isPossible(tibtibptr))
			return true;
		    else  if (GC_TRACEALLOCATOR) {
			VM.sysWrite("*** isPossibleRef NOT POSSIBLE: bad tibtib ptr: ref = ");
			VM.sysWrite(ref);
			VM.sysWrite(" tibtib ptr = ");
			VM.sysWrite(tibtibptr);
			VM.sysWrite(".\n");
		    }
		}
		else  if (GC_TRACEALLOCATOR) {
		    VM.sysWrite("*** isPossibleRef NOT POSSIBLE: bad class ptr: ref = ");
		    VM.sysWrite(ref);
		    VM.sysWrite(" class ptr = ");
		    VM.sysWrite(classptr);
		    VM.sysWrite(".\n");
		}
	    }
	    else if (GC_TRACEALLOCATOR) {
		VM.sysWrite("*** isPossibleRef NOT POSSIBLE: bad TIB ptr: ref = ");
		VM.sysWrite(ref);
		VM.sysWrite(" tib ptr = ");
		VM.sysWrite(tibptr);
		VM.sysWrite(".\n");
	    }
	}
	else if (GC_TRACEALLOCATOR) {
	    VM.sysWrite("*** isPossibleRef NOT POSSIBLE: ref out of range: ref = ");
	    VM.sysWrite(ref);
	    VM.sysWrite(".\n");
	}
	return false;
    }

    // a routine to perform checks on a possible pointer: does it fall within
    // the heap or the boot Image.
    private static boolean isPossible (VM_Address ref) {
        VM_Address tref = VM_ObjectModel.getPointerInMemoryRegion(ref);
	return (bootHeap.addrInHeap(tref) ||
		smallHeap.addrInHeap(tref) ||
		largeHeap.addrInHeap(tref));
    }

    // Is it an object in the malloc area?
    static boolean isMalloc (VM_Address ref) {
	return mallocHeap.refInHeap(ref);
    }

    // a debugging routine: to make sure a pointer is into the heap
    private static boolean isValidSmallHeapPtr (VM_Address addr) {
	return smallHeap.addrInHeap(addr);
    }

    // a debugging routine: to make sure a pointer is into the heap
    private static boolean isPtrInBlock (VM_Address ptr, VM_SizeControl the_size) {
	VM_BlockControl the_block = blocks[the_size.current_block];
	VM_Address base = the_block.baseAddr;
	int offset = ptr.diff(base);
	VM_Address endofslot = ptr.add(the_block.slotsize);
	if (offset%the_block.slotsize != 0) VM.sysFail("Ptr not to beginning of slot");
	VM_Address bound = base.add(GC_BLOCKSIZE);
	return (ptr.GE(base) && endofslot.LE(bound));
    }

    
    /////////////////////////////////////////////////////////////////////////////
    // DEBUG AND STATISTICS
    /////////////////////////////////////////////////////////////////////////////

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
	    if (blocks[iii].live) VM.sysWrite("    live"); 
	    else VM.sysWrite("not live");
	    VM.sysWrite("   "); 
	    if (blocks[iii].sticky) VM.sysWrite("    sticky"); 
	    else VM.sysWrite("not sticky");
	    VM.sysWrite(" \nbaseaddr = "); VM.sysWrite(blocks[iii].baseAddr);
	    VM.sysWrite(" \nnextblock = "); VM.sysWrite(blocks[iii].nextblock);
	    VM.sysWrite("\n");
	}

    }

    static void
    clobber (VM_Address addr, int length) {
	int value = 0xdeaddead;
	int i;
	for (i = 0; i + 3 < length; i = i+4) 
	    VM_Magic.setMemoryWord(addr.add(i), value);
    }

    static void
    clobberfree () {
	VM_Processor st = VM_Processor.getCurrentProcessor();
	for (int i = 0; i < GC_SIZES; i ++) {
	    VM_BlockControl this_block = blocks[st.sizes[i].first_block];  
	    byte[] this_alloc        = this_block.mark;
	    for (int ii = 0; ii < this_alloc.length; ii ++) {
		if (this_alloc[ii] == 0)
		    clobber(this_block.baseAddr.add(ii * GC_SIZEVALUES[i]), GC_SIZEVALUES[i]);
	    }
	    int next = this_block.nextblock;
	    while (next != 0) {
		this_block = blocks[next];
		this_alloc        = this_block.mark;
		for (int ii = 0; ii < this_alloc.length; ii ++) {
		    if (this_alloc[ii] == 0)
			clobber(this_block.baseAddr.add(ii * GC_SIZEVALUES[i]), GC_SIZEVALUES[i]);
		}
		next = this_block.nextblock;
	    }
	}
    }

    public static long
    totalMemory () {
	return smallHeapSize + largeHeapSize;
    }

    // not quite right - wait for refactoring
    public static long allSmallUsableMemory() {
	return freeMemory();
    }

    // not quite right - wait for refactoring
    public static long allSmallFreeMemory() {
	return freeMemory();
    }


    public static long
    freeMemory () {
	total_blocks_in_use = 0;
	long total = 0;
	for (int i = 1; i <= VM_Scheduler.numProcessors; i++) 
	    total = total + freeSmallSpace(VM_Scheduler.processors[i]);
        return (freeLargeSpace() + total + (highest_block - total_blocks_in_use) * 
		GC_BLOCKSIZE);
    }

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
	return (total * LARGE_BLOCK_SIZE);       // number of bytes free in largespace
    }

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

    static void heapInfo () {
	VM.sysWrite("Small memory ");    VM.sysWrite((int) smallHeapSize, false);
	VM.sysWrite("; small blocks ");  VM.sysWrite(num_blocks, false);
	VM.sysWrite("; available blocks ");  VM.sysWrite(blocks_available, false);
	VM.sysWrite("\nLarge memory ");  VM.sysWrite((int) largeHeapSize, false);
	VM.sysWrite("; free large space = "); VM.sysWrite((int) freeLargeSpace(), false);
	
	int blocksUsed = 0;

	for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
	    VM_Processor p = VM_Scheduler.processors[i];
	    int free = (int) freeSmallSpace(p);
	    VM.sysWrite("\nPROCESSOR ");  VM.sysWrite(p.id, false);
	    VM.sysWrite(": Small free space ");  VM.sysWrite(free, false);  VM.sysWrite(" bytes\n");

	    for (int s = 0; s < GC_SIZES; s++) {
		VM.sysWrite("  Size ");  VM.sysWrite(GC_SIZEVALUES[s], false);  
		VM.sysWrite(": "); VM.sysWrite(countSmallFree[s], false);  VM.sysWrite(" free;  ");
		VM.sysWrite(countSmallBlocksAlloc[s], false);  VM.sysWrite(" blocks\n");
		blocksUsed += countSmallBlocksAlloc[s];
	    }
	}
	VM.sysWrite("Small blocks used ");  VM.sysWrite(blocksUsed, false);  VM.sysWrite("\n");
	println("Actual bytes in use: ", allocatedMemory());
    }

    public static long
    freeSmallSpace (VM_Processor st) {
	int total = 0;
	int next, temp;
	VM_BlockControl this_block;

	for (int i = 0; i < GC_SIZES; i++) {
	    countSmallFree[i] = 0;
	    countSmallBlocksAlloc[i] = 1;
	    this_block = blocks[st.sizes[i].first_block];
	    total_blocks_in_use++;
	    temp = (int)emptyof(i, this_block.alloc);
	    countSmallFree[i] += temp;
	    total+= temp;
	    next = this_block.nextblock;
	    while (next != 0) {
		this_block = blocks[next];
		total_blocks_in_use++;
		temp = (int)emptyof(i, this_block.alloc);
		total += temp;
		countSmallFree[i] += temp;
		countSmallBlocksAlloc[i] ++;
		next = this_block.nextblock;
	    }
	}
	return total;
    }


    static int allocatedMemory () {
	return allocatedLargeMemory() + allocatedSmallMemory();
    }

    static int allocatedLargeMemory () {
	int largeBlocksAllocated = 0;

	int total = 0;
	for (int i = 0 ; i < largeSpacePages;) {
	    if (largeSpaceAlloc[i] == 0) 
		i++;
	    else {
		int size = largeSpaceAlloc[i];
		largeBlocksAllocated += size;
		i += size;
	    }
	}

	int largeBytesAllocated = largeBlocksAllocated * LARGE_BLOCK_SIZE;
	println("Large allocated: ", largeBytesAllocated);
	return largeBytesAllocated;
    }


    static int allocatedSmallMemory () {
	int smallAllocated = 0;

	for (int p = 1; p <= VM_Scheduler.numProcessors; p++) {
	    VM_Processor st = VM_Scheduler.processors[p];

	    for (int i = 0; i < GC_SIZES; i++) {
		VM_BlockControl head = blocks[st.sizes[i].first_block];

		for (VM_BlockControl b = head, next = null; b != null; b = next) {

		    next = b.nextblock == 0 ? null : blocks[b.nextblock];

		    smallAllocated += allocatedInBlock(b, i);
		}
	    }
	}

	println("Small allocated: ", smallAllocated);
	return smallAllocated;
    }
	

    // fix later to calculate exact size (as well as internal fragmentation?)
    static int allocatedInBlock(VM_BlockControl block, int sizeIndex) {
	final byte alloc[] = block.alloc;
	final int  size    = GC_SIZEVALUES[sizeIndex];

	int total = 0;
	for (int i = 0; i < alloc.length; i++) {
	    if (alloc[i] != 0) total += size;
	}
	return total;
    }

    // calculate the number of free bytes in a block of slotsize size

    private static long
    emptyof (int size, byte[] alloc) {
	int total = 0;
	for (int i = 0; i < alloc.length; i++) {
	    if (alloc[i] == 0) total += GC_SIZEVALUES[size];
	}
	return total;
    }


    static void
    gcResetExtraSpace () {}


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


    /////////////////////////////////////////////////////////////////////////////
    // MALLOC OBJECTS
    /////////////////////////////////////////////////////////////////////////////

    private static Object
    makeObjectFromStorage (VM_Address storage, Object[] tib, int size) 
    {
      Object obj = VM_ObjectModel.initializeScalar(storage, tib, size);
      VM_Address ref = VM_Magic.objectAsAddress(obj);
      initializeMallocedRefcount(ref, VM_Magic.objectAsAddress(tib));
      return obj;
    }

   
    private static byte[]
    makeByteArrayFromStorage (VM_Address storage, int num_elements, int size)
    {
      Object tmp = VM_ObjectModel.initializeArray(storage, byteArrayTIB, num_elements, size);
      byte[] ref = VM_Magic.objectAsByteArray(tmp);
      initializeMallocedRefcount(VM_Magic.objectAsAddress(ref), VM_Magic.objectAsAddress(byteArrayTIB));
      return ref;
    }


    private static void initializeMallocedRefcount(VM_Address object, VM_Address tibptr) {
	setRefcount(object, RED | 1);
    }


    /////////////////////////////////////////////////////////////////////////////
    // REFERENCE COUNTING
    /////////////////////////////////////////////////////////////////////////////

    static void incrementRC (VM_Address object) { 
	if (VM.VerifyAssertions) VM.assert(!object.isZero());

	if (VM.VerifyAssertions && object == refToWatch)
	    VM.sysWrite("#### Incrementing RC of watched object\n");

	int color = color(object);
	if (color != RED) {
	    incReferenceCount(object);
	    if (color != GREEN) {
		VM_RootBuffer.scanBlackOnUpdate(object);
		setColor(object, BLACK);
	    }
	}
    }


    static void
    decrementRC (VM_Address object) {
	if (object.isZero())	// assert != 0?
	    return;

	if (VM.VerifyAssertions && object == refToWatch)
	    VM.sysWrite("#### Decrementing RC of watched object\n");

	int color = color(object);

	if (color == RED)
	    return;		// don't change reference counts of boot image objects

	boolean wasWhite = false;
	if (color == WHITE) {
	    dumpRefcountInfo("Decrementing RC of white object ", object);
	    wasWhite = true;
	}

	boolean isZero = decReferenceCount(object);

	if (RC_COUNT_EVENTS && ! isZero)
	    nonZeroDecs++;

	if (! isZero) {
	    VM_RootBuffer.scanBlackOnUpdate(object);
	    VM_RootBuffer.buffer.add(object); 
	}
	else 
	    removeInternalPointersAndFree(object);

	if (wasWhite)
	    dumpRefcountInfo("After decrement ", object);
    }


    static void removeInternalPointersAndFree (VM_Address ref) {
	if (VM.VerifyAssertions) VM.assert(isPossibleRef(ref));

	if (VM.VerifyAssertions && ref == refToWatch)
	    VM.sysWrite("#### Freeing watched object\n");

	VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));

	if (type.isClassType()) { 
	    int[] referenceOffsets = type.asClass().getReferenceOffsets();
	    for (int i = 0, n = referenceOffsets.length; i < n; ++i) {
		VM_Address object = VM_Magic.getMemoryAddress(ref.add(referenceOffsets[i]));
		decrementRC(object);
		if (RC_COUNT_EVENTS && !object.isZero() && color(object) != RED) internalDecs++;
	    }
	} 
	else if (type.isArrayType()) {
	    int num_elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(ref));

	    if (type.asArray().getElementType().isReferenceType()) {
		VM_Address location = ref;    // for arrays = address of [0] entry
		VM_Address end      = ref.add(num_elements * 4);
		while (location.LT(end)) {
		    VM_Address object = VM_Magic.getMemoryAddress(location);
		    decrementRC(object);
		    if (RC_COUNT_EVENTS && !object.isZero() && color(object) != RED) internalDecs++;
		    location = location.add(4);
		}
		//  XXXX USING "4" where should be using "size_of_pointer" (for 64-bits)
	    }
	} 
	else {
	    VM.sysWrite("VM_Allocator.decrementRC: type not Array or Class");
	    VM.sysExit(1000);
	}

	if (VM_RCGC.referenceCountTIBs)
	  decrementRC(VM_Magic.objectAsAddress(VM_ObjectModel.getTIB(ref)));

	if (! isBuffered(ref)) 
	    freeObject(ref);
    }


    // Once refcount drops to zero, free the object
    static void freeObject (VM_Address ref) {
	if (VM.VerifyAssertions) VM.assert(isPossibleRef(ref)); 
	if (VM.VerifyAssertions) VM.assert(! isBuffered(ref)); 
	if (GC_COUNT_ALLOC) freedCount++;

	VM_Address tref = VM_ObjectModel.getPointerInMemoryRegion(ref);

	if (smallHeap.addrInHeap(tref)) { // or refInHeap(ref)
	    // object allocated in small object runtime heap
	    int blkndx = (tref.diff(smallHeap.start)) >> LOG_GC_BLOCKSIZE;
	    VM_BlockControl this_block = blocks[blkndx];
	    int offset    = tref.diff(this_block.baseAddr);
	    int slotndx   = offset/this_block.slotsize;

	    if (GC_MARK_REACHABLE_OBJECTS && ! freeable(ref, this_block, slotndx)) return;

	    this_block.alloc[slotndx] = 0;
	    //	    this_block.allocCount--;
	    VM_Synchronization.fetchAndDecrement(this_block, VM_Entrypoints.allocCountField.getOffset(),1);

	} else if (largeHeap.addrInHeap(tref)) {
	    // object allocated in large object runtime heap
	    int page_num = tref.diff(largeHeap.start) >> LOG_GC_LARGE_BLOCKSIZE;

	    if (GC_MARK_REACHABLE_OBJECTS && ! freeableLarge(ref, page_num)) return;

	    int blocks = largeSpaceAlloc[page_num];

	    if (VM.VerifyAssertions) {
		VM.assert(blocks > 0);
		if (blocks != 1) 
		    VM.assert(blocks == (-largeSpaceAlloc[page_num + blocks - 1]));
	    }

	    // Zero large blocks here so they don't create mutator pauses
	    VM_Address blockStart = largeHeap.start.add(page_num << LOG_GC_LARGE_BLOCKSIZE);
	    VM_Address blockEnd = blockStart.add(blocks << LOG_GC_LARGE_BLOCKSIZE);
	    VM_Memory.zero(blockStart, blockEnd);

	    sysLockLarge.lock();

	    for (int i = 0; i < blocks; i++) {
		largeSpaceAlloc[page_num + i] = 0;
	    }

	    if (large_last_allocated > page_num) // SHOULD WE DO THIS???  Might actually de-optimize
		large_last_allocated = page_num; //    large object allocator

	    sysLockLarge.unlock();

	    if (TRACE_LARGE) println("Freed large object of page multiple ", blocks);
	}
	else {
	    VM.sysWrite("Object in unknown space at ");
	    VM.sysWrite(ref);
	    VM.sysWriteln(" of type ", VM_Magic.getObjectType(VM_Magic.addressAsObject(ref)).toString());
	    VM.assert(NOT_REACHED);
	}
    }


    private static boolean freeable(VM_Address ref, VM_BlockControl this_block, int slotndx) {
	if (this_block.mark[slotndx] == 0)
	    return true;

	// NOTE: with multiple CPUs, "reachable" objects may be unreachable by now due to concurrent
	//   mutator activity.
	if (GC_MARK_REACHABLE_OBJECTS_SOFT && VM_Scheduler.numProcessors > 1) {
	    VM.sysWrite("-"); // just output advisory tick mark
	}
	else {
	    VM.sysWrite("!!!!!!!!!!!!!!! TRIED TO FREE MARKED OBJECT at address ");
	    VM.sysWrite(ref);
	    VM.sysWrite(" with type ");
	    printType(ref);
	    
	    if (GC_MARK_REACHABLE_OBJECTS_DETAIL) {
		VM.sysWrite("SEARCHING from statics for ptr to this object\n");
		prepareForMarking();
		refToLookFor = ref;
		markStatics();
		VM.sysWrite("DONE SEARCHING from statics for ptr to this object\n");
	    }
	}

	return false;
    }


    private static boolean freeableLarge (VM_Address ref, int page_num) {
	if (largeSpaceMark[page_num] == 0) 
	    return true;

	if (GC_MARK_REACHABLE_OBJECTS_SOFT && VM_Scheduler.numProcessors > 1) {
	    VM.sysWrite("#"); // just output advisory tick mark
	}
	else {
	    VM.sysWrite("!!!!!!!!!!!!!!! TRIED TO FREE MARKED LARGE OBJECT at address ");
	    VM.sysWrite(ref);
	    VM.sysWrite(" with type ");
	    printType(ref);
	    
	    if (GC_MARK_REACHABLE_OBJECTS_DETAIL) {
		VM.sysWrite("SEARCHING from statics for ptr to this object\n");
		prepareForMarking();
		refToLookFor = ref;
		markStatics();
		VM.sysWrite("DONE SEARCHING from statics for ptr to this object\n");
	    }
	}

	return false;
    }


    // Debug support
    static void printType(VM_Address object) {
	if (object.isZero()) {
	    VM.sysWrite("NULL OBJECT POINTER\n");
	    return;
	}
	VM_Magic.getObjectType(VM_Magic.addressAsObject(object)).getDescriptor().sysWrite();
	VM.sysWrite("\n");
    }


    /////////////////////////////////////////////////////////////////////////////
    // LIVE OBJECT MARKING (DEBUGGING)
    /////////////////////////////////////////////////////////////////////////////

    static void
    markStatics () {
	int numslots = VM_Statics.getNumberOfSlots();
	int slot = 0;
	VM_Address ref;
	int rc;

	if (!refToLookFor.isZero()) {
	    VM.sysWrite(" * * * * * * * \n");
	    VM.sysWrite("markStatics looking for suspect ref = ");
	    VM.sysWrite(refToLookFor);
	    VM.sysWrite("\n * * * * * * * \n");
	}
	while ( slot < numslots ) {
	    if ( VM_Statics.isReference(slot)) {
		ref = VM_Address.fromInt(VM_Statics.getSlotContentsAsInt(slot));
		rc = markPtrFieldValue(ref);
		if (rc == 2) {
		    VM.sysWrite("SUSPECT REF in JTOC at slot = ");
		    VM.sysWrite(slot, false);
		    VM.sysWrite( " value = ");
		    VM.sysWrite(ref);
		    VM.sysWrite("\n * * * * * * * \n");
		    // continue looking for other suspect objects
		}
	    }
	    slot++;
	}
    }

    static boolean
    gc_setMarkLarge (VM_Address tref, VM_Address ref) { 
	int ij;
	int page_num = tref.diff(largeHeap.start) >> LOG_GC_LARGE_BLOCKSIZE;
	if (GC_STATISTICS) largerefs_count++;
	boolean result = (largeSpaceMark[page_num] != 0);
	if (result) return true;	// fast, no synch case

	sysLockLarge.lock();		// get sysLock for large objects
	result = (largeSpaceMark[page_num] != 0);
	if (result) {	// need to recheck
	    sysLockLarge.release();
	    return true;	
	}
	int blocks = largeSpaceAlloc[page_num];
	if (blocks == 1) {
	    largeSpaceMark[page_num] = 1;
	}
	else {
	    // mark entries for both ends of the range of allocated pages
	    if (blocks > 0) {
		ij = page_num + blocks -1;
		largeSpaceMark[ij] = (short)-blocks;
	    }
	    else {
	  	ij = page_num + blocks + 1;
		largeSpaceMark[ij] = (short)-blocks;
	    }
	    largeSpaceMark[page_num] = (short)blocks;
	}

	sysLockLarge.unlock();	// INCLUDES sync()
	return false;
    }
 
    // Routine for Debugging of veryfast allocation: if 1 in alloc array,
    // return true (error condition!!)  if 0 in alloc array, set to 1 (to catch
    // a subsequent allocation) and return false
    //
    static boolean
    isSetYet (VM_BlockControl the_block, VM_Address objaddr)
    {
	int slotndx, offset;
	boolean result;
	offset = objaddr.diff(the_block.baseAddr);
	slotndx = offset/the_block.slotsize;
	if (the_block.alloc[slotndx] != 0)
	    return true;	// the error!
	else {
	    the_block.alloc[slotndx] = 2;	// use to show allocated twice
	    return false;
	}
    }	//isSetYet() 

    // given an address in the small objec heap (as an int), 
    // set the corresponding mark byte on
    static boolean
    gc_setMarkSmall (VM_Address tref, VM_Address ref) {
	boolean result; 
	int blkndx, slotno, size, ij;
	blkndx = tref.diff(smallHeap.start) >> LOG_GC_BLOCKSIZE ;
	VM_BlockControl this_block = blocks[blkndx];
	int offset    = tref.diff(this_block.baseAddr); 
	//     size 	   = GC_BLOCKSIZE/this_block.mark.length;
	//     int slotndx   = offset/size;
	int slotndx   = offset/this_block.slotsize;
	result = (this_block.mark[slotndx] != 0);
	if (result) return true;		// avoid synchronization

        //  Use prepare/attempt logic to atomically update
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
	//-#if RVM_WITH_OLD_DEAD_CODE
	/// Introduce larx/stcx logic
	byte tbyte;
	int temp;
	do {
	    temp = VM_Magic.getMemoryWordWithReservation
		(VM_Magic.objectAsAddress(this_block.mark) + ((slotndx>>2)<<2));
	    if (this_block.mark[slotndx] != 0) return true;
	    tbyte = (byte)(this_block.alloc[slotndx] + 1);
	    temp = temp | (tbyte << ((3 - slotndx%4) * 8 ));
	} while (!VM_Magic.setMemoryWordConditional
		 (VM_Magic.objectAsAddress(this_block.mark) + ((slotndx>>2)<<2), temp));
	//-#endif
 
	if (GC_STATISTICS) countLive[getndx(size)]++;	// maintain count of live objects
	this_block.live = true;
	return false;
    }


    static void
    prepareForMarking() {
	// invert the mark_flag value, used for marking BootImage objects
	if ( OBJECT_GC_MARK_VALUE == 0 )
	  OBJECT_GC_MARK_VALUE = VM_AllocatorHeader.GC_MARK_BIT_MASK;
	else
	    OBJECT_GC_MARK_VALUE = 0;
	
	for (int i = 0; i < bootrecord.largeSpaceSize/LARGE_BLOCK_SIZE + 1; i++) {
	    largeSpaceMark[i] = 0;
	}
	
	for (int i = 0; i < num_blocks; i++) {
	    VM_BlockControl block = blocks[i];
	    if (block.mark != null) {
		for (int j = 0; j < block.mark.length; j++) {
		    block.mark[j] = 0;
		}
	    }
	}
    }

    static int
    markObjectOrArray (VM_Address objRef ) {
	VM_Type    type;
	int        rc = 0;

	// mark TIB
	VM_Address ptr = VM_Magic.objectAsAddress(VM_ObjectModel.getTIB(VM_Magic.addressAsObject(objRef)));
	rc = markPtrFieldValue(ptr);
	if (rc == 2) {
	    VM.sysWrite("SUSPECT REF in TIB field of object with ref = ");
	    VM.sysWrite(objRef);
	    VM.sysWrite(" ptr value = ");
	    VM.sysWrite(ptr);
	    VM.sysWrite("\n");
	    return rc;
	}

	type = VM_Magic.getObjectType(VM_Magic.addressAsObject(objRef));
	if ( ! isPossibleRef(VM_Magic.objectAsAddress(type)) ) {
	    VM.sysWrite("markObjectOrArray: BAD TYPE FIELD in object with ref = ");
	    VM.sysWrite(objRef);
	    VM.sysWrite("\n");
	    return 2;            // force print of A->B->C...
	}

	if ( type.isClassType() ) { 
	    int[] referenceOffsets = type.asClass().getReferenceOffsets();
	    for (int i = 0, n = referenceOffsets.length; i < n; ++i) {
		ptr = VM_Magic.getMemoryAddress(objRef.add(referenceOffsets[i]));
		rc = markPtrFieldValue( ptr );
		if (rc == 2) {
		    VM.sysWrite("SUSPECT REF in object with ref = ");
		    VM.sysWrite(objRef);
		    VM.sysWrite(" of class ");
		    type.toString();
		    VM.sysWrite(" at offset = ");
		    VM.sysWrite(referenceOffsets[i],false);
		    VM.sysWrite(" ptr value = ");
		    VM.sysWrite(ptr);
		    VM.sysWrite("\n");
		    return rc;
		}
	    }
	}
	else if ( type.isArrayType() ) {
	    if (type.asArray().getElementType().isReferenceType()) {
		int num_elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(objRef));
		VM_Address location = objRef;    // for arrays = address of [0] entry
		VM_Address end      = objRef.add(num_elements * 4);
		while ( location.LT(end) ) {
		    ptr = VM_Magic.getMemoryAddress(location);
		    rc = markPtrFieldValue( ptr );
		    if (rc == 2) {
			VM.sysWrite("SUSPECT REF in array with ref = ");
			VM.sysWrite(objRef);
			VM.sysWrite(" of class ");
			type.toString();
			VM.sysWrite(" at index = ");
			VM.sysWrite( location.diff(objRef)/4, false);
			VM.sysWrite(" ptr value = ");
			VM.sysWrite(ptr);
			VM.sysWrite("\n");
			return rc;
		    }
		    location = location.add(4);
		}
	    }
	}
	else {
	    VM.sysWrite("VM_Allocator.markObjectOrArray: type not Array or Class");
	    VM.sysExit(1000);
	}

	return rc;
    }

   static int
   markPtrFieldValue (VM_Address ref) {

       if (ref.isZero()) return 0;	// TEST FOR NULL POINTER

       if (ref.EQ(refToLookFor)) return 2;  // found ptr to BAD object

       // accomodate that ref might be outside space
       VM_Address tref = VM_ObjectModel.getPointerInMemoryRegion(ref);
       
       if ( smallHeap.addrInHeap(tref) ) {
	   if (gc_setMarkSmall(tref, ref)) return 0;  // already marked
	   else return  markObjectOrArray(ref);
       }
       else if ( largeHeap.addrInHeap(tref) ) {
	   if (gc_setMarkLarge(tref, ref)) return 0;  // already marked
	   else return  markObjectOrArray(ref);
       }
       else if ( bootHeap.addrInHeap(tref) ) {
	 if ( ! VM_AllocatorHeader.testAndMark(VM_Magic.addressAsObject(ref), OBJECT_GC_MARK_VALUE)) 
	       return 0;  // already marked
	   else return  markObjectOrArray(ref);
       }

       return 0;  // may inlcude "bad" refs

   }

    /////////////////////////////////////////////////////////////////////////////
    // DUMMY VARIABLES AND ROUTINES
    /////////////////////////////////////////////////////////////////////////////

    static final boolean writeBarrier = false;
    static final boolean movesObjects = false;

    static final int MARK_VALUE = 1;		    // designates "marked" objects (copying collectors)
    static final int BEING_FORWARDED_PATTERN = -5;  // "busy & marked" (copying collectors)

    static int numberOfAmbiguousRefs;		    // unused -- for compatibility
    static int numberOfStackFramesProcessed;
    static int gcMajorCount; 

    static void gcSetup (int dummy) { 
	// NO-OP
    }

    //
    // Required by new finalization code
    //

    static boolean processFinalizerListElement (VM_FinalizerListElement le) {
	VM.assert(NOT_REACHED);
	return false;
    }

    public static void printclass (VM_Address ref) {
        VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));
	VM.sysWrite(type.getDescriptor());
    }

    static void          processWriteBufferEntry (VM_Address wbref) {}

  // methods called from utility methods of VM_GCUtil
  //
  static final void    processPtrField( VM_Address location ) {}
  static final int     processPtrValue( VM_Address reference ) { return 0; }


      static int getByteArrayInstanceSize (int numelts) {
	  int bytes = VM_ObjectModel.computeArrayHeaderSize(byteArrayType) + numelts;
	  int round = (bytes + (WORDSIZE - 1)) & ~(WORDSIZE - 1);
	  return round;
      }


}   // VM_Allocator


