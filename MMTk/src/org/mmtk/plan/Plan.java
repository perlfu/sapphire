/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan;

import org.mmtk.policy.MarkSweepSpace;
import org.mmtk.policy.Space;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.RawPageSpace;
import org.mmtk.policy.LargeObjectSpace;
import org.mmtk.utility.alloc.LinearScan;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.heap.Map;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.*;
import org.mmtk.utility.sanitychecker.SanityChecker;
import org.mmtk.utility.statistics.Timer;
import org.mmtk.utility.statistics.Stats;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implements the global core functionality for all
 * memory management schemes.  All global MMTk plans should inherit from
 * this class.<p>
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities, and divides global and local state
 * into separate class hierarchies.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  There is a single instance of Plan (or the
 * appropriate sub-class), and a 1:1 mapping of PlanLocal to "kernel
 * threads" (aka CPUs).  Thus instance
 * methods of PlanLocal allow fast, unsynchronized access to functions such as
 * allocation and collection.
 *
 * The global instance defines and manages static resources
 * (such as memory and virtual memory resources).  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance properties of MMTk plans.
 */
@Uninterruptible
public abstract class Plan implements Constants {
  /****************************************************************************
   * Constants
   */

  /**
   *
   */

  /* GC State */
  public static final int NOT_IN_GC = 0; // this must be zero for C code
  public static final int GC_PREPARE = 1; // before setup and obtaining root
  public static final int GC_PROPER = 2;

  /* Space Size Constants. */
  public static final boolean USE_CODE_SPACE = true;

  /* Allocator Constants */
  public static final int ALLOC_DEFAULT = 0;
  public static final int ALLOC_NON_REFERENCE = 1;
  public static final int ALLOC_NON_MOVING = 2;
  public static final int ALLOC_IMMORTAL = 3;
  public static final int ALLOC_LOS = 4;
  public static final int ALLOC_PRIMITIVE_LOS = 5;
  public static final int ALLOC_GCSPY = 6;
  public static final int ALLOC_CODE = 7;
  public static final int ALLOC_LARGE_CODE = 8;
  public static final int ALLOC_HOT_CODE = USE_CODE_SPACE ? ALLOC_CODE : ALLOC_DEFAULT;
  public static final int ALLOC_COLD_CODE = USE_CODE_SPACE ? ALLOC_CODE : ALLOC_DEFAULT;
  public static final int ALLOC_STACK = ALLOC_LOS;
  public static final int ALLOCATORS = 9;
  public static final int DEFAULT_SITE = -1;

  /* Miscellaneous Constants */
//  public static final int LOS_SIZE_THRESHOLD = SegregatedFreeListSpace.MAX_CELL_SIZE;
  public static final int NON_PARTICIPANT = 0;
  public static final boolean GATHER_WRITE_BARRIER_STATS = false;
  public static final int DEFAULT_MIN_NURSERY =  (2 << 20) >> LOG_BYTES_IN_PAGE;
  public static final int DEFAULT_MAX_NURSERY = (32 << 20) >> LOG_BYTES_IN_PAGE;
  public static final boolean SCAN_BOOT_IMAGE = true;  // scan it for roots rather than trace it
 // public static final boolean REQUIRES_LOS = VM.activePlan.constraints().requiresLOS();
  public static final int MAX_NON_LOS_DEFAULT_ALLOC_BYTES = VM.activePlan.constraints().maxNonLOSDefaultAllocBytes();
  public static final int MAX_NON_LOS_NONMOVING_ALLOC_BYTES = VM.activePlan.constraints().maxNonLOSNonMovingAllocBytes();
  public static final int MAX_NON_LOS_COPY_BYTES = VM.activePlan.constraints().maxNonLOSCopyBytes();

  /* Do we support a log bit in the object header?  Some write barriers may use it */
  public static final boolean NEEDS_LOG_BIT_IN_HEADER = VM.activePlan.constraints().needsLogBitInHeader();

  /****************************************************************************
   * Class variables
   */

  /** The space that holds any VM specific objects (e.g. a boot image) */
  public static final Space vmSpace = VM.memory.getVMSpace();

  /** Any immortal objects allocated after booting are allocated here. */
  public static final ImmortalSpace immortalSpace = new ImmortalSpace("immortal", VMRequest.create());

  /** All meta data that is used by MMTk is allocated (and accounted for) in the meta data space. */
  public static final RawPageSpace metaDataSpace = new RawPageSpace("meta", VMRequest.create());

  /** Large objects are allocated into a special large object space. */
  public static final LargeObjectSpace loSpace = new LargeObjectSpace("los", VMRequest.create());

  /** Space used by the sanity checker (used at runtime only if sanity checking enabled */
  public static final RawPageSpace sanitySpace = new RawPageSpace("sanity", VMRequest.create());

  /** Space used to allocate objects that cannot be moved. we do not need a large space as the LOS is non-moving. */
  public static final MarkSweepSpace nonMovingSpace = new MarkSweepSpace("non-moving", VMRequest.create());

  public static final MarkSweepSpace smallCodeSpace = USE_CODE_SPACE ? new MarkSweepSpace("sm-code", VMRequest.create()) : null;
  public static final LargeObjectSpace largeCodeSpace = USE_CODE_SPACE ? new LargeObjectSpace("lg-code", VMRequest.create()) : null;

  public static int pretenureThreshold = Integer.MAX_VALUE;

  /* Space descriptors */
  public static final int IMMORTAL = immortalSpace.getDescriptor();
  public static final int VM_SPACE = vmSpace.getDescriptor();
  public static final int META = metaDataSpace.getDescriptor();
  public static final int LOS = loSpace.getDescriptor();
  public static final int SANITY = sanitySpace.getDescriptor();
  public static final int NON_MOVING = nonMovingSpace.getDescriptor();
  public static final int SMALL_CODE = USE_CODE_SPACE ? smallCodeSpace.getDescriptor() : 0;
  public static final int LARGE_CODE = USE_CODE_SPACE ? largeCodeSpace.getDescriptor() : 0;

  /** Timer that counts total time */
  public static final Timer totalTime = new Timer("time");

  /** Support for allocation-site identification */
  protected static int allocationSiteCount = 0;

  /** Global sanity checking state **/
  public static final SanityChecker sanityChecker = new SanityChecker();

  /** Default collector context */
  protected final Class<? extends ParallelCollector> defaultCollectorContext;

  /****************************************************************************
   * Constructor.
   */
  public Plan() {
    /* Create base option instances */
    Options.verbose = new Verbose();
    Options.verboseTiming = new VerboseTiming();
    Options.stressFactor = new StressFactor();
    Options.noFinalizer = new NoFinalizer();
    Options.noReferenceTypes = new NoReferenceTypes();
    Options.fullHeapSystemGC = new FullHeapSystemGC();
    Options.harnessAll = new HarnessAll();
    Options.ignoreSystemGC = new IgnoreSystemGC();
    Options.metaDataLimit = new MetaDataLimit();
    Options.nurserySize = new NurserySize();
    Options.nurseryZeroing = new NurseryZeroing();
    Options.pretenureThresholdFraction = new PretenureThresholdFraction();
    Options.variableSizeHeap = new VariableSizeHeap();
    Options.eagerMmapSpaces = new EagerMmapSpaces();
    Options.sanityCheck = new SanityCheck();
    Options.debugAddress = new DebugAddress();
    Options.perfEvents = new PerfEvents();
    Options.useReturnBarrier = new UseReturnBarrier();
    Options.threads = new Threads();
    Options.cycleTriggerThreshold = new CycleTriggerThreshold();
    Map.finalizeStaticSpaceMap();
    registerSpecializedMethods();

    // Determine the default collector context.
    Class<? extends Plan> mmtkPlanClass = this.getClass().asSubclass(Plan.class);
    while(!mmtkPlanClass.getName().startsWith("org.mmtk.plan")) {
      mmtkPlanClass = mmtkPlanClass.getSuperclass().asSubclass(Plan.class);
    }
    String contextClassName = mmtkPlanClass.getName() + "Collector";
    Class<? extends ParallelCollector> mmtkCollectorClass = null;
    try {
      mmtkCollectorClass = Class.forName(contextClassName).asSubclass(ParallelCollector.class);
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(-1);
    }
    defaultCollectorContext = mmtkCollectorClass;
  }

  /****************************************************************************
   * The complete boot Sequence is:
   *
   *  1. enableAllocation: allow allocation (but not collection).
   *  2. processOptions  : the VM has parsed/prepared options for MMTk to react to.
   *  3. enableCollection: the VM can support the spawning of MMTk collector contexts.
   *  4. fullyBooted     : control is just about to be given to application code.
   */

  /**
   * The enableAllocation method is called early in the boot process to allow
   * allocation.
   */
  @Interruptible
  public void enableAllocation() {
  }

  /**
   * The processOptions method is called by the runtime immediately after
   * command-line arguments are available. Allocation must be supported
   * prior to this point because the runtime infrastructure may require
   * allocation in order to parse the command line arguments.  For this
   * reason all plans should operate gracefully on the default minimum
   * heap size until the point that processOptions is called.
   */
  @Interruptible
  public void processOptions() {
    VM.statistics.perfEventInit(Options.perfEvents.getValue());
    if (Options.verbose.getValue() > 2) Space.printVMMap();
    if (Options.verbose.getValue() > 3) VM.config.printConfig();
    if (Options.verbose.getValue() > 0) Stats.startAll();
    if (Options.eagerMmapSpaces.getValue()) Space.eagerlyMmapMMTkSpaces();
    pretenureThreshold = (int) ((Options.nurserySize.getMaxNursery()<<LOG_BYTES_IN_PAGE) * Options.pretenureThresholdFraction.getValue());
  }

  /**
   * The enableCollection method is called by the runtime after it is
   * safe to spawn collector contexts and allow garbage collection.
   */
  @Interruptible
  public void enableCollection() {
    // Make sure that if we have not explicitly set threads, then we use the right default.
    Options.threads.updateDefaultValue(VM.collection.getDefaultThreads());

    // Create our parallel workers
    parallelWorkers.initGroup(Options.threads.getValue(), defaultCollectorContext);

    // Create the concurrent worker threads.
    if (VM.activePlan.constraints().needsConcurrentWorkers()) {
      concurrentWorkers.initGroup(Options.threads.getValue(), defaultCollectorContext);
    }

    // Create our control thread.
    VM.collection.spawnCollectorContext(controlCollectorContext);

    // Allow mutators to trigger collection.
    initialized = true;
  }

  @Interruptible
  public void fullyBooted() {
    if (Options.harnessAll.getValue()) harnessBegin();
  }

  public static final ParallelCollectorGroup parallelWorkers = new ParallelCollectorGroup("ParallelWorkers");
  public static final ParallelCollectorGroup concurrentWorkers = new ParallelCollectorGroup("ConcurrentWorkers");
  public static final ControllerCollectorContext controlCollectorContext = new ControllerCollectorContext(parallelWorkers);

  /**
   * The VM is about to exit. Perform any clean up operations.
   *
   * @param value The exit value
   */
  @Interruptible
  public void notifyExit(int value) {
    if (Options.harnessAll.getValue()) harnessEnd();
    if (Options.verbose.getValue() == 1) {
      Log.write("[End ");
      totalTime.printTotalSecs();
      Log.writeln(" s]");
    } else if (Options.verbose.getValue() == 2) {
      Log.write("[End ");
      totalTime.printTotalMillis();
      Log.writeln(" ms]");
    }
    if (Options.verboseTiming.getValue()) printDetailedTiming(true);
  }

  /**
   * Any Plan can override this to provide additional plan specific
   * timing information.
   *
   * @param totals Print totals
   */
  protected void printDetailedTiming(boolean totals) {}

  /**
   * Perform any required write barrier action when installing an object reference
   * a boot time.
   *
   * @param reference the reference value that is to be stored
   * @return The raw value to be
   */
  public Word bootTimeWriteBarrier(Word reference) {
    return reference;
  }

  /****************************************************************************
   * Allocation
   */

  /**
   * @param compileTime is this a call by the compiler?
   * @return an allocation site
   *
   */
  public static int getAllocationSite(boolean compileTime) {
    if (compileTime) // a new allocation site is being compiled
      return allocationSiteCount++;
    else             // an anonymous site
      return DEFAULT_SITE;
  }

  /****************************************************************************
   * Collection.
   */

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId The unique id of the phase to perform.
   */
  public abstract void collectionPhase(short phaseId);

  /**
   * Replace a phase.
   *
   * @param oldScheduledPhase The scheduled phase to insert after
   * @param scheduledPhase The scheduled phase to insert
   */
  @Interruptible
  public void replacePhase(int oldScheduledPhase, int scheduledPhase) {
    VM.assertions.fail("replacePhase not implemented for this plan");
  }

  /**
   * Insert a phase.
   *
   * @param markerScheduledPhase The scheduled phase to insert after
   * @param scheduledPhase The scheduled phase to insert
   */
  @Interruptible
  public void insertPhaseAfter(int markerScheduledPhase, int scheduledPhase) {
    short tempPhase = Phase.createComplex("auto-gen", null, markerScheduledPhase, scheduledPhase);
    replacePhase(markerScheduledPhase, Phase.scheduleComplex(tempPhase));
  }

  /**
   * @return Whether last GC was an exhaustive attempt to collect the heap.  For many collectors this is the same as asking whether the last GC was a full heap collection.
   */
  public boolean lastCollectionWasExhaustive() {
    return lastCollectionFullHeap();
  }

  /**
   * @return Whether last GC is a full GC.
   */
  public boolean lastCollectionFullHeap() {
    return true;
  }

  /**
   * @return Is last GC a full collection?
   */
  public static boolean isEmergencyCollection() {
    return emergencyCollection;
  }

  /**
   * Force the next collection to be full heap.
   */
  public void forceFullHeapCollection() {}

  /**
   * @return Is current GC only collecting objects allocated since last GC.
   */
  public boolean isCurrentGCNursery() {
    return false;
  }

  private long lastStressPages = 0;

  /**
   * Return the expected reference count. For non-reference counting
   * collectors this becomes a {@code true/false} relationship.
   *
   * @param object The object to check.
   * @param sanityRootRC The number of root references to the object.
   * @return The expected (root excluded) reference count.
   */
  public int sanityExpectedRC(ObjectReference object, int sanityRootRC) {
    Space space = Space.getSpaceForObject(object);
    return space.isReachable(object) ? SanityChecker.ALIVE : SanityChecker.DEAD;
  }

  /**
   * Perform a linear scan of all spaces to check for possible leaks.
   * This is only called after a full-heap GC.
   *
   * @param scanner The scanner callback to use.
   */
  public void sanityLinearScan(LinearScan scanner) {
  }

  /**
   * @return {@code true} is a stress test GC is required
   */
  @Inline
  public final boolean stressTestGCRequired() {
    long pages = Space.cumulativeCommittedPages();
    if (initialized &&
        ((pages ^ lastStressPages) > Options.stressFactor.getPages())) {
      lastStressPages = pages;
      return true;
    } else
      return false;
  }

  /****************************************************************************
   * GC State
   */

  /**
   *
   */
  protected static boolean userTriggeredCollection;
  protected static boolean internalTriggeredCollection;
  protected static boolean lastInternalTriggeredCollection;
  protected static boolean emergencyCollection;
  protected static boolean stacksPrepared;

  private static boolean initialized = false;

  @Entrypoint
  private static int gcStatus = NOT_IN_GC; // shared variable

  /** @return Is the memory management system initialized? */
  public static boolean isInitialized() {
    return initialized;
  }

  /**
   * Return {@code true} if stacks have been prepared in this collection cycle.
   *
   * @return {@code true} if stacks have been prepared in this collection cycle.
   */
  public static boolean stacksPrepared() {
    return stacksPrepared;
  }
  /**
   * Return {@code true} if a collection is in progress.
   *
   * @return {@code true} if a collection is in progress.
   */
  public static boolean gcInProgress() {
    return gcStatus != NOT_IN_GC;
  }

  /**
   * Return {@code true} if a collection is in progress and past the preparatory stage.
   *
   * @return {@code true} if a collection is in progress and past the preparatory stage.
   */
  public static boolean gcInProgressProper() {
    return gcStatus == GC_PROPER;
  }

  /**
   * Sets the GC status.
   *
   * @param s The new GC status.
   */
  public static void setGCStatus(int s) {
    if (gcStatus == NOT_IN_GC) {
      /* From NOT_IN_GC to any phase */
      stacksPrepared = false;
      if (Stats.gatheringStats()) {
        Stats.startGC();
        VM.activePlan.global().printPreStats();
      }
    }
    VM.memory.isync();
    gcStatus = s;
    VM.memory.sync();
    if (gcStatus == NOT_IN_GC) {
      /* From any phase to NOT_IN_GC */
      if (Stats.gatheringStats()) {
        Stats.endGC();
        VM.activePlan.global().printPostStats();
      }
    }
  }

  /**
   * Print pre-collection statistics.
   */
  public void printPreStats() {
    if ((Options.verbose.getValue() == 1) ||
        (Options.verbose.getValue() == 2)) {
      Log.write("[GC "); Log.write(Stats.gcCount());
      if (Options.verbose.getValue() == 1) {
        Log.write(" Start ");
        Plan.totalTime.printTotalSecs();
        Log.write(" s");
      } else {
        Log.write(" Start ");
        Plan.totalTime.printTotalMillis();
        Log.write(" ms");
      }
      Log.write("   ");
      Log.write(Conversions.pagesToKBytes(getPagesUsed()));
      Log.write("KB ");
      Log.flush();
    }
    if (Options.verbose.getValue() > 2) {
      Log.write("Collection "); Log.write(Stats.gcCount());
      Log.write(":        ");
      printUsedPages();
      Log.write("  Before Collection: ");
      Space.printUsageMB();
      if (Options.verbose.getValue() >= 4) {
        Log.write("                     ");
        Space.printUsagePages();
      }
      if (Options.verbose.getValue() >= 5) {
        Space.printVMMap();
      }
    }
  }

  /**
   * Print out statistics at the end of a GC
   */
  public final void printPostStats() {
    if ((Options.verbose.getValue() == 1) ||
        (Options.verbose.getValue() == 2)) {
      Log.write("-> ");
      Log.writeDec(Conversions.pagesToBytes(getPagesUsed()).toWord().rshl(10));
      Log.write("KB   ");
      if (Options.verbose.getValue() == 1) {
        totalTime.printLast();
        Log.writeln(" ms]");
      } else {
        Log.write("End ");
        totalTime.printTotal();
        Log.writeln(" ms]");
      }
    }
    if (Options.verbose.getValue() > 2) {
      Log.write("   After Collection: ");
      Space.printUsageMB();
      if (Options.verbose.getValue() >= 4) {
        Log.write("                     ");
        Space.printUsagePages();
      }
      if (Options.verbose.getValue() >= 5) {
        Space.printVMMap();
      }
      Log.write("                     ");
      printUsedPages();
      Log.write("    Collection time: ");
      totalTime.printLast();
      Log.writeln(" ms");
    }
  }

  public final void printUsedPages() {
    Log.write("reserved = ");
    Log.write(Conversions.pagesToMBytes(getPagesReserved()));
    Log.write(" MB (");
    Log.write(getPagesReserved());
    Log.write(" pgs)");
    Log.write("      used = ");
    Log.write(Conversions.pagesToMBytes(getPagesUsed()));
    Log.write(" MB (");
    Log.write(getPagesUsed());
    Log.write(" pgs)");
    Log.write("      total = ");
    Log.write(Conversions.pagesToMBytes(getTotalPages()));
    Log.write(" MB (");
    Log.write(getTotalPages());
    Log.write(" pgs)");
    Log.writeln();
  }

  /**
   * The application code has requested a collection.
   */
  @Unpreemptible
  public static void handleUserCollectionRequest() {
    if (Options.ignoreSystemGC.getValue()) {
      // Ignore the user GC request.
      return;
    }
    // Mark this as a user triggered collection
    userTriggeredCollection = true;
    // Request the collection
    controlCollectorContext.request();
    // Wait for the collection to complete
    VM.collection.blockForGC();
  }

  /**
   * MMTK has requested stop-the-world activity (e.g., stw within a concurrent gc).
   */
  public static void triggerInternalCollectionRequest() {
    // Mark this as a user triggered collection
    internalTriggeredCollection = lastInternalTriggeredCollection = true;
    // Request the collection
    controlCollectorContext.request();
  }

  /**
   * Reset collection state information.
   */
  public static void resetCollectionTrigger() {
    lastInternalTriggeredCollection = internalTriggeredCollection;
    internalTriggeredCollection = false;
    userTriggeredCollection = false;
  }

  /**
   * @return {@code true} if this collection was triggered by application code.
   */
  public static boolean isUserTriggeredCollection() {
    return userTriggeredCollection;
  }

  /**
   * @return {@code true} if this collection was triggered internally.
   */
  public static boolean isInternalTriggeredCollection() {
    return lastInternalTriggeredCollection;
  }

  /****************************************************************************
   * Harness
   */

  /**
   *
   */
  protected static boolean insideHarness = false;

  /**
   * Generic hook to allow benchmarks to be harnessed.  A plan may use
   * this to perform certain actions prior to the commencement of a
   * benchmark, such as a full heap collection, turning on
   * instrumentation, etc.  By default we do a full heap GC,
   * and then start stats collection.
   */
  @Interruptible
  public static void harnessBegin() {
    // Save old values.
    boolean oldFullHeap = Options.fullHeapSystemGC.getValue();
    boolean oldIgnore = Options.ignoreSystemGC.getValue();

    // Set desired values.
    Options.fullHeapSystemGC.setValue(true);
    Options.ignoreSystemGC.setValue(false);

    // Trigger a full heap GC.
    System.gc();

    // Restore old values.
    Options.ignoreSystemGC.setValue(oldIgnore);
    Options.fullHeapSystemGC.setValue(oldFullHeap);

    // Start statistics
    insideHarness = true;
    Stats.startAll();
  }

  /**
   * Generic hook to allow benchmarks to be harnessed.  A plan may use
   * this to perform certain actions after the completion of a
   * benchmark, such as a full heap collection, turning off
   * instrumentation, etc.  By default we stop all statistics objects
   * and print their values.
   */
  @Interruptible
  public static void harnessEnd()  {
    Stats.stopAll();
    insideHarness = false;
  }

  /****************************************************************************
   * VM Accounting
   */

  /* Global accounting and static access */

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
  public static Extent freeMemory() {
    return totalMemory().minus(usedMemory());
  }

  /**
   * Return the amount of <i>available memory</i>, in bytes.  Note
   * that this accounts for unused memory that is held in reserve
   * for copying, and therefore unavailable for allocation.
   *
   * @return The amount of <i>available memory</i>, in bytes.
   */
  public static Extent availableMemory() {
    return totalMemory().minus(reservedMemory());
  }

  /**
   * Return the amount of <i>memory in use</i>, in bytes.  Note that
   * this excludes unused memory that is held in reserve for copying,
   * and therefore unavailable for allocation.
   *
   * @return The amount of <i>memory in use</i>, in bytes.
   */
  public static Extent usedMemory() {
    return Conversions.pagesToBytes(VM.activePlan.global().getPagesUsed());
  }

  /**
   * Return the amount of <i>memory in use</i>, in bytes.  Note that
   * this includes unused memory that is held in reserve for copying,
   * and therefore unavailable for allocation.
   *
   * @return The amount of <i>memory in use</i>, in bytes.
   */
  public static Extent reservedMemory() {
    return Conversions.pagesToBytes(VM.activePlan.global().getPagesReserved());
  }

  /**
   * Return the total amount of memory managed to the memory
   * management system, in bytes.
   *
   * @return The total amount of memory managed to the memory
   * management system, in bytes.
   */
  public static Extent totalMemory() {
    return HeapGrowthManager.getCurrentHeapSize();
  }

  /* Instance methods */

  /**
   * Return the total amount of memory managed to the memory
   * management system, in pages.
   *
   * @return The total amount of memory managed to the memory
   * management system, in pages.
   */
  public final int getTotalPages() {
    return totalMemory().toWord().rshl(LOG_BYTES_IN_PAGE).toInt();
  }

  /**
   * Return the number of pages available for allocation.
   *
   * @return The number of pages available for allocation.
   */
  public int getPagesAvail() {
    return getTotalPages() - getPagesReserved();
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  Sub-classes must override the getCopyReserve method,
   * as the arithmetic here is fixed.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  public final int getPagesReserved() {
    return getPagesUsed() + getCollectionReserve();
  }

  /**
   * Return the number of pages reserved for collection.
   * In most cases this is a copy reserve, all subclasses that
   * manage a copying space must add the copying contribution.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for collection.
   */
  public int getCollectionReserve() {
    return 0;
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public int getPagesUsed() {
    return loSpace.reservedPages() + immortalSpace.reservedPages() +
      metaDataSpace.reservedPages() + nonMovingSpace.reservedPages();
  }

  /****************************************************************************
   * Internal read/write barriers.
   */

  /**
   * Store an object reference
   *
   * @param slot The location of the reference
   * @param value The value to store
   */
  @Inline
  public void storeObjectReference(Address slot, ObjectReference value) {
    slot.store(value);
  }

  /**
   * Load an object reference
   *
   * @param slot The location of the reference
   * @return the object reference loaded from slot
   */
  @Inline
  public ObjectReference loadObjectReference(Address slot) {
    return slot.loadObjectReference();
  }

  /****************************************************************************
   * Collection.
   */

  /**
   * This method is called periodically by the allocation subsystem
   * (by default, each time a page is consumed), and provides the
   * collector with an opportunity to collect.
   *
   * @param spaceFull Space request failed, must recover pages within 'space'.
   * @param space The space that triggered the poll.
   * @return <code>true</code> if a collection is required.
   */
  public final boolean poll(boolean spaceFull, Space space) {
    if (collectionRequired(spaceFull, space)) {
      if (space == metaDataSpace) {
        /* In general we must not trigger a GC on metadata allocation since
         * this is not, in general, in a GC safe point.  Instead we initiate
         * an asynchronous GC, which will occur at the next safe point.
         */
        logPoll(space, "Asynchronous collection requested");
        controlCollectorContext.request();
        return false;
      }
      logPoll(space, "Triggering collection");
      controlCollectorContext.request();
      return true;
    }

    if (concurrentCollectionRequired()) {
      if (space == metaDataSpace) {
        logPoll(space, "Triggering async concurrent collection");
        triggerInternalCollectionRequest();
        return false;
      } else {
        logPoll(space, "Triggering concurrent collection");
        triggerInternalCollectionRequest();
        return true;
      }
    }

    return false;
  }

  /**
   * Log a message from within 'poll'
   * @param space
   * @param message
   */
  protected void logPoll(Space space, String message) {
    if (Options.verbose.getValue() >= 5) {
      Log.write("  [POLL] ");
      Log.write(space.getName());
      Log.write(": ");
      Log.writeln(message);
    }
  }

  /**
   * This method controls the triggering of a GC. It is called periodically
   * during allocation. Returns <code>true</code> to trigger a collection.
   *
   * @param spaceFull Space request failed, must recover pages within 'space'.
   * @param space TODO
   * @return <code>true</code> if a collection is requested by the plan.
   */
  protected boolean collectionRequired(boolean spaceFull, Space space) {
    boolean stressForceGC = stressTestGCRequired();
    boolean heapFull = getPagesReserved() > getTotalPages();

    return spaceFull || stressForceGC || heapFull;
  }

  /**
   * This method controls the triggering of an atomic phase of a concurrent
   * collection. It is called periodically during allocation.
   *
   * @return <code>true</code> if a collection is requested by the plan.
   */
  protected boolean concurrentCollectionRequired() {
    return false;
  }

  /**
   * Start GCspy server.
   *
   * @param port The port to listen on,
   * @param wait Should we wait for a client to connect?
   */
  @Interruptible
  public void startGCspyServer(int port, boolean wait) {
    VM.assertions.fail("startGCspyServer called on non GCspy plan");
  }

  /**
   * Can this object ever move.  Used by the VM to make decisions about
   * whether it needs to copy IO buffers etc.
   *
   * @param object The object in question
   * @return <code>true</code> if it is not possible that the object will ever move.
   */
  public boolean willNeverMove(ObjectReference object) {
    if (!VM.activePlan.constraints().movesObjects())
      return true;
    if (Space.isInSpace(LOS, object))
      return true;
    if (Space.isInSpace(IMMORTAL, object))
      return true;
    if (Space.isInSpace(VM_SPACE, object))
      return true;
    if (Space.isInSpace(NON_MOVING, object))
      return true;
    if (USE_CODE_SPACE && Space.isInSpace(SMALL_CODE, object))
      return true;
    if (USE_CODE_SPACE && Space.isInSpace(LARGE_CODE, object))
      return true;
    /*
     * Default to false- this preserves correctness over efficiency.
     * Individual plans should override for non-moving spaces they define.
     */
    return false;
  }

  /****************************************************************************
   * Specialized Methods
   */

  /**
   * Register specialized methods.
   */
  @Interruptible
  protected void registerSpecializedMethods() {}

  /**
   * Get the specialized scan with the given id.
   */
  public final Class<?> getSpecializedScanClass(int id) {
    return TransitiveClosure.getSpecializedScanClass(id);
  }
}
