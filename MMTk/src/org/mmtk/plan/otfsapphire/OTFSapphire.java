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
package org.mmtk.plan.otfsapphire;

import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.ImmortalLocal;
import org.mmtk.policy.LargeObjectLocal;
import org.mmtk.policy.MarkSweepSpace;
import org.mmtk.policy.Space;
import org.mmtk.plan.*;
import org.mmtk.plan.onthefly.OnTheFly;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.alloc.LinearScan;
import org.mmtk.utility.deque.SharedDeque;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.options.ConcurrentCopyMethod;
import org.mmtk.utility.options.ConcurrentTriggerMethod;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.options.SapphireSTWPhase;
import org.mmtk.utility.statistics.Stats;
import org.mmtk.utility.statistics.Timer;
import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

@Uninterruptible
public class OTFSapphire extends OnTheFly {
  /**
   * options
   */
  public static final boolean NO_LOS         = false;
  public static final boolean NO_NON_MOVING  = false;
  public static final boolean SHOWTRACES     = false;
  public static final boolean ENSURE_STACK_PREPARED = false;
  public static final boolean ENABLE_VERIFY_COPY    = false;
  public static final boolean POISON_DEAD_OBJECTS   = false;
  public static boolean STW_ALLOC; // set in processOptions()
  public static boolean STW_COPY;
  public static boolean STW_FLIP;
  public static boolean STW_ROOTS;
  public static final boolean VERIFY_FLIP           = false;
  public static final boolean MERGE_REPLICATE_PHASE = false;
  public static final boolean DO_MULTIPLE_PATCH     = false;
  public static final boolean STOP_THE_WORLD_GC     = false;
  public static final boolean REPORT_FIX_POINTERS   = false;
  public static final boolean REPLICATE_WITH_CAS    = false;

  public static final boolean REFERENCE_PROCESS_INFLOOP = false;
  public static final boolean REFERENCE_PROCESS_STW     = false;
  public static final boolean REFERENCE_PROCESS_YUASA   = true;
  public static final boolean REFERENCE_REPORT_STARVATION = false;

  /****************************************************************************
   *
   * Internal Classes
   */
  public static class FlipObjectScanner extends TransitiveClosure {
    /**
     * Trace an edge during GC.
     * 
     * @param source The source of the reference.
     * @param slot The location containing the object reference.
     */
    @Uninterruptible
    @Override
    @Inline
    public void processEdge(ObjectReference source, Address slot) {
      ObjectReference object = slot.prepareObjectReference();
      if (object.isNull() || !OTFSapphire.inFromSpace(object)) return; // no need to do anything
      ObjectReference toObject = ReplicatingSpace.getReplicaPointer(object);
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(OTFSapphire.inToSpace(toObject));
      slot.attempt(object, toObject); // don't care if we succeed or fail
      if (VM.VERIFY_ASSERTIONS) if (STW_FLIP) VM.assertions._assert(slot.loadObjectReference() == toObject);
    }
  }

  public static class ZeroObjectScanner extends TransitiveClosure {
    /**
     * Trace an edge during GC.
     * 
     * @param source The source of the reference.
     * @param slot The location containing the object reference.
     */
    @Uninterruptible
    @Override
    @Inline
    public void processEdge(ObjectReference source, Address slot) {
      if (VM.VERIFY_ASSERTIONS) {
        ObjectReference object = slot.prepareObjectReference(); 
        // cas in null even though this object should be dead, if this fails bad things have happened!
        if (!slot.attempt(object, ObjectReference.nullReference())) VM.assertions.fail("ZeroReferences CAS failed");
      } else {
        // non paranoid write over reference
        slot.store(ObjectReference.nullReference());
      }
    }
  }

  // generic flip linear scan
  public static class FlipNonReplicatingLinearScan extends LinearScan {
    private static final FlipObjectScanner flip = new FlipObjectScanner();

    private final Space space;
    
    FlipNonReplicatingLinearScan(Space space) {
      this.space = space;
    }
    
    @Uninterruptible
    @Override
    @Inline
    public void scan(ObjectReference object) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Space.isInSpace(space.getDescriptor(), object));
      if (space.isReachable(object)) {
        VM.scanning.scanObject(flip, object);
      }
    }
  }

  public static class FlipImmortalSpaceLinearScan extends LinearScan {
    private static final FlipObjectScanner flip = new FlipObjectScanner();
    private static final ZeroObjectScanner zero = new ZeroObjectScanner();

    @Uninterruptible
    @Override
    @Inline
    public void scan(ObjectReference object) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Space.isInSpace(IMMORTAL, object));
      if (immortalSpace.isReachable(object)) {
        VM.scanning.scanObject(flip, object);
      } else {
        VM.scanning.scanObject(zero, object);
      }
    }
  }

  public static class FlipLOSLinearScan extends LinearScan {
    private static final FlipObjectScanner flip = new FlipObjectScanner();

    @Uninterruptible
    @Override
    @Inline
    public void scan(ObjectReference object) {
      if (VM.VERIFY_ASSERTIONS) {
        if (Space.isInSpace(LOS, object)) {
          VM.assertions._assert(loSpace.isReachable(object));
        } else if (Space.isInSpace(LARGE_CODE, object)) {
          VM.assertions._assert(largeCodeSpace.isReachable(object));
        } else {
          VM.assertions.fail("flip object not in a large object space");
        }
      }
      VM.scanning.scanObject(flip, object);
    }
  }
  
  public static class FlipNonMovingLinearScan extends LinearScan {
    private static final FlipObjectScanner flip = new FlipObjectScanner();

    private final MarkSweepSpace space;
    
    FlipNonMovingLinearScan(MarkSweepSpace space) {
      this.space = space;
    }
    
    @Uninterruptible
    @Override
    @Inline
    public void scan(ObjectReference object) {
      VM.scanning.scanObject(flip, object);
    }
    
    @Uninterruptible
    @Override
    public void scan(ObjectReference object, boolean live, Address cellAddress, Extent cellExtent) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Space.isInSpace(space.getDescriptor(), object));
      // don't assert reachability here as this method receives dead objects

      if (live) {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(space.isReachable(object));
        scan(object);
      } else if (space.hasMarking(object)) {
        if (POISON_DEAD_OBJECTS) {
          Address cursor = cellAddress;
          Address cellEnd = cellAddress.plus(cellExtent);
          while (cursor.LT(cellEnd)) {
            cursor.store(0xdead0000);
            cursor = cursor.plus(BYTES_IN_WORD);
          }
        }
        space.clearMarkState(object);
      }
    }
  }

  /****************************************************************************
   *
   * Constants
   */
  
  /**
   * phases
   */
  public static final short PREPARE_ALLOC             = Phase.createSimple("prepare-alloc");
  public static final short DEREFERENCE_DELAYED_ROOTS = Phase.createSimple("dereference-delayed-roots");
  public static final short REPLICATE_PHASE           = Phase.createSimple("replicate");
  public static final short PREPARE_COPY              = Phase.createSimple("prepare-copy");
  public static final short COPY                      = Phase.createSimple("COPY");
  public static final short RELEASE_COPY              = Phase.createSimple("release-copy");
  public static final short PREPARE_FLIP              = Phase.createSimple("prepare-flip");
  public static final short RELEASE_FLIP              = Phase.createSimple("release-flip");
  public static final short FLUSH_IMMORTAL            = Phase.createSimple("flush-immortal");
  public static final short FLIP_CLOSURE              = Phase.createSimple("flip-closure");
  public static final short FLIP_NON_REPLICATING      = Phase.createSimple("flip-non-replicating");
  public static final short PRE_COPY_HOOK             = Phase.createSimple("pre-copy-hook");
  public static final short POST_COPY_HOOK            = Phase.createSimple("post-copy-hook");
  public static final short PREPARE_CLEANING_SETUP_BARRIER = Phase.createSimple("prepare-cleaning-setup-barrier");
  public static final short PREPARE_CLEANING_SETUP_BARRIER_2 = Phase.createSimple("prepare-cleaning2-setup-barrier");
  public static final short PREPARE_FLIP_SETUP_BARRIER_0 = Phase.createSimple("prepare-flip-setup-barrier-0");
  
  /**
   * root scanning
   */
  private static final short rootsAndStacksMarkPhase = Phase.createComplex("roots-and-stacks-mark-phase",
      Phase.scheduleCollector      (OTF_STACK_ROOTS),
      Phase.scheduleGlobal         (STACK_ROOTS),
      Phase.scheduleComplex        (onTheFlyRootsPhase),
      Phase.scheduleGlobal         (ROOTS));

  private static final short stwRootsAndStacksMarkPhase = Phase.createComplex("stw-roots-and-stacks-mark-phase",
      Phase.scheduleCollector      (OTF_STACK_ROOTS),
      Phase.scheduleGlobal         (STACK_ROOTS),
      Phase.scheduleCollector      (ROOTS),
      Phase.scheduleGlobal         (ROOTS));

  /**
   * reference types and IU termination loop
   */
  private static final short weakReferenceMarkIteration = Phase.createComplex("weak-reference-mark-iteration", null,
      Phase.scheduleGlobal         (CLOSURE),
      Phase.scheduleCollector      (CLOSURE),
      Phase.scheduleComplex        (rootsAndStacksMarkPhase),
      Phase.scheduleOnTheFlyMutator(FLUSH_MUTATOR),
      Phase.scheduleCollector      (FLUSH_COLLECTOR),
      Phase.scheduleGlobal         (OTF_WEAK_REFS_TERMINATION));

  private static final short weakReferenceMarkIterationFallback = Phase.createComplex("weak-reference-mark-iteration-fallback", null,
      Phase.scheduleSpecial        (STOP_MUTATORS),
      Phase.scheduleComplex        (stwRootsAndStacksMarkPhase),
      Phase.scheduleOnTheFlyMutator(FLUSH_MUTATOR),
      Phase.scheduleCollector      (FLUSH_COLLECTOR),
      Phase.scheduleGlobal         (CLOSURE),
      Phase.scheduleCollector      (CLOSURE),
      Phase.scheduleGlobal         (OTF_WEAK_REFS_TERMINATION),
      Phase.scheduleSpecial        (RESTART_MUTATORS));

  private static final short iuTerminationAndRefTypeClosurePhase = Phase.createComplex("iu-termination-and-ref-type-closure-phase",
      // 1.1. soft refs
      Phase.scheduleCollector      (OTF_PROCESS_SOFT_REFS),
      // 1.2. weak refs + IU termination if ref types are disabled
      Phase.scheduleGlobal         (OTF_PREPARE_REFERENCE),
      Phase.scheduleGlobal         (CLOSURE),
      Phase.scheduleCollector      (CLOSURE),
      Phase.scheduleComplex        (rootsAndStacksMarkPhase),
      Phase.scheduleOnTheFlyMutator(FLUSH_MUTATOR),
      Phase.scheduleCollector      (FLUSH_COLLECTOR),
      Phase.scheduleGlobal         (OTF_WEAK_REFS_TERMINATION),
      Phase.scheduleCollector      (OTF_PROCESS_WEAK_REFS),
      Phase.scheduleGlobal         (OTF_PROCESS_WEAK_REFS),
      Phase.scheduleGlobal         (OTF_RELEASE_REFERENCE),
      // 1.3. finalizer + phantom refs
      Phase.scheduleCollector      (FINALIZABLE),
      Phase.scheduleGlobal         (CLOSURE),
      Phase.scheduleCollector      (CLOSURE),
      Phase.scheduleGlobal         (TRIGGER_FINALIZE),
      Phase.schedulePlaceholder    (WEAK_TRACK_REFS),
      Phase.scheduleCollector      (PHANTOM_REFS));

  private static final short weakReferenceMarkIterationWithDeletionBarrier = Phase.createComplex("weak-reference-mark-iteration", null,
      Phase.scheduleGlobal         (CLOSURE),
      Phase.scheduleCollector      (CLOSURE),
      Phase.scheduleOnTheFlyMutator(FLUSH_MUTATOR),
      Phase.scheduleCollector      (FLUSH_COLLECTOR),
      Phase.scheduleGlobal         (OTF_WEAK_REFS_TERMINATION));

  private static final short weakReferenceMarkIterationFallbackWithDeletionBarrier = Phase.createComplex("weak-reference-mark-iteration-fallback", null,
      Phase.scheduleSpecial        (STOP_MUTATORS),
      Phase.scheduleOnTheFlyMutator(FLUSH_MUTATOR),
      Phase.scheduleCollector      (FLUSH_COLLECTOR),
      Phase.scheduleGlobal         (CLOSURE),
      Phase.scheduleCollector      (CLOSURE),
      Phase.scheduleGlobal         (OTF_WEAK_REFS_TERMINATION),
      Phase.scheduleSpecial        (RESTART_MUTATORS));

  private static final short iuTerminationAndRefTypeClosurePhaseWithDeletionBarrier = Phase.createComplex("iu-termination-and-ref-type-closure-phase-with-deletion-barrier",
      // 1.1. retain elected soft refs
      Phase.scheduleCollector      (OTF_PROCESS_SOFT_REFS),
      // 1.2. remining soft refs, weak refs, IU termination loop
      Phase.scheduleGlobal         (OTF_PREPARE_REFERENCE),
      Phase.scheduleOnTheFlyMutator(PREPARE_CLEANING_SETUP_BARRIER),
      Phase.scheduleComplex        (rootsAndStacksMarkPhase),
      Phase.scheduleOnTheFlyMutator(PREPARE_CLEANING_SETUP_BARRIER_2),
      Phase.scheduleGlobal         (CLOSURE),
      Phase.scheduleCollector      (CLOSURE),
      Phase.scheduleOnTheFlyMutator(FLUSH_MUTATOR),
      Phase.scheduleCollector      (FLUSH_COLLECTOR),
      Phase.scheduleGlobal         (OTF_WEAK_REFS_TERMINATION),
      Phase.scheduleCollector      (OTF_PROCESS_WEAK_REFS),
      Phase.scheduleGlobal         (OTF_PROCESS_WEAK_REFS),
      Phase.scheduleGlobal         (OTF_RELEASE_REFERENCE),
      // 1.3. finalizer + phantom refs
      Phase.scheduleCollector      (FINALIZABLE),
      Phase.scheduleGlobal         (CLOSURE),
      Phase.scheduleCollector      (CLOSURE),
      Phase.scheduleGlobal         (TRIGGER_FINALIZE),
      Phase.schedulePlaceholder    (WEAK_TRACK_REFS),
      Phase.scheduleCollector      (PHANTOM_REFS));

  /**
   * copy phase
   */
  public static final short copyPhase = Phase.createComplex("copy-phase", null,
      Phase.scheduleOnTheFlyMutator(PREPARE_COPY),
      Phase.scheduleGlobal         (PREPARE_COPY),
      Phase.scheduleCollector      (COPY),
      Phase.scheduleGlobal         (RELEASE_COPY),
      Phase.scheduleOnTheFlyMutator(RELEASE_COPY));

  /**
   * concurrent phases
   */
  public static final short concurrentMarkAllocPhase = Phase.createComplex("concurrent-mark-alloc",
      Phase.scheduleSpecial  (RESTART_MUTATORS),
      Phase.scheduleCollector(CLOSURE),
      Phase.scheduleSpecial  (STOP_MUTATORS));

  public static final short concurrentCopyPhase = Phase.createComplex("concurrent-copy-phase",
      Phase.scheduleSpecial(RESTART_MUTATORS),
      Phase.scheduleComplex(copyPhase),
      Phase.scheduleSpecial(STOP_MUTATORS));

  public static final short concurrentFlipPhase = Phase.createComplex("concurrent-flip-phase",
      Phase.scheduleSpecial(RESTART_MUTATORS),
      Phase.scheduleGlobal(FLIP_NON_REPLICATING),
      Phase.scheduleSpecial(STOP_MUTATORS));

  public static final short stwMarkAllocPhase = Phase.createComplex("stw-mark-alloc",
      Phase.scheduleSpecial  (STOP_MUTATORS),
      Phase.scheduleCollector(CLOSURE),
      Phase.scheduleSpecial  (RESTART_MUTATORS));

  public static final short stwCopyPhase = Phase.createComplex("stw-copy-phase",
      Phase.scheduleSpecial(STOP_MUTATORS),
      Phase.scheduleComplex (copyPhase),
      Phase.scheduleSpecial(RESTART_MUTATORS));

  public static final short stwFlipPhase = Phase.createComplex("stw-flip-phase",
      Phase.scheduleSpecial(STOP_MUTATORS),
      Phase.scheduleGlobal(FLIP_NON_REPLICATING),
      Phase.scheduleSpecial(RESTART_MUTATORS));

  /**
   * phases for debug
   */
  public static final short VERIFY_COPY = Phase.createSimple("verify-copy");
  public static final short verifyCopy  = Phase.createComplex("verify-copy-complex", null,
      Phase.scheduleGlobal (VERIFY_COPY),
      Phase.scheduleOnTheFlyMutator(VERIFY_COPY));

  /**
   * Replicate phase
   */
  public static final short allocAndCopyPhase = Phase.createComplex("alloc-and-copy-phase",
      // 1. Mark & Allocate phase

      Phase.scheduleOnTheFlyMutator(PREPARE),
      Phase.scheduleGlobal         (PREPARE),
      Phase.scheduleCollector      (PREPARE),
      Phase.scheduleOnTheFlyMutator(PREPARE_ALLOC),
      Phase.scheduleComplex        (rootsAndStacksMarkPhase),
      Phase.scheduleGlobal         (CLOSURE),
      Phase.scheduleCollector      (CLOSURE),
      REFERENCE_PROCESS_YUASA?
      Phase.scheduleComplex        (iuTerminationAndRefTypeClosurePhaseWithDeletionBarrier) :
      Phase.scheduleComplex        (iuTerminationAndRefTypeClosurePhase),
      
      Phase.scheduleOnTheFlyMutator(RELEASE),
      Phase.scheduleCollector      (RELEASE),
      Phase.scheduleGlobal         (RELEASE),

      // 2. Copy phase
      Phase.schedulePlaceholder    (PRE_COPY_HOOK),
      Phase.scheduleComplex        (copyPhase),
      Phase.schedulePlaceholder    (POST_COPY_HOOK));

  // alloc-closure and cleaning-closure phases act as replicate phase
  public static final short replicatePhase = Phase.createComplex("replicate-phase",
//      Phase.scheduleSpecial        (STOP_MUTATORS),
      
      Phase.scheduleOnTheFlyMutator(PREPARE),
      Phase.scheduleGlobal         (PREPARE),
      Phase.scheduleCollector      (PREPARE),
      Phase.scheduleOnTheFlyMutator(PREPARE_ALLOC),
      Phase.scheduleComplex        (rootsAndStacksMarkPhase),
      Phase.scheduleGlobal         (CLOSURE),
      Phase.scheduleCollector      (CLOSURE),
      REFERENCE_PROCESS_YUASA?
      Phase.scheduleComplex        (iuTerminationAndRefTypeClosurePhaseWithDeletionBarrier) :
      Phase.scheduleComplex        (iuTerminationAndRefTypeClosurePhase),
      Phase.scheduleOnTheFlyMutator(RELEASE),
      Phase.scheduleCollector      (RELEASE),
      Phase.scheduleGlobal         (RELEASE),

      // 2. Copy phase
      Phase.schedulePlaceholder    (POST_COPY_HOOK)
      );
//      ,Phase.scheduleSpecial        (RESTART_MUTATORS));

  /****************************************************************************
   *
   * Class variables
   */

  /** True if allocating into the "higher" semispace */
  public static volatile boolean low = true; // True if allocing to "lower" semispace, volatile so collector thread always reads the
                                             // right value

  /** One of the two semi spaces that alternate roles at each collection */
  public static final ReplicatingSpace repSpace0 = new ReplicatingSpace("rep-ss0", VMRequest.discontiguous());
  public static final int SS0 = repSpace0.getDescriptor();

  /** One of the two semi spaces that alternate roles at each collection */
  public static final ReplicatingSpace repSpace1 = new ReplicatingSpace("rep-ss1", VMRequest.discontiguous());
  public static final int SS1 = repSpace1.getDescriptor();

  public static CopyLocal   fromSpaceSweeper     = new CopyLocal();
  public static CopyLocal   toSpaceSweeper       = new CopyLocal();
  public static BumpPointer immortalSpaceSweeper = new ImmortalLocal(immortalSpace);
  public static LargeObjectLocal loSpaceSweeper  = new LargeObjectLocal(loSpace);
  public static LargeObjectLocal largeCodeSpaceSweeper  = new LargeObjectLocal(largeCodeSpace);
  public static Lock tackOnLock = VM.newLock("tackOnLock");

  public static final LinearScan flipImmortalSpaceLinearScan  = new FlipImmortalSpaceLinearScan();
  public static final LinearScan flipLoSpaceLinearScan        = new FlipLOSLinearScan();
  public static final LinearScan flipNonMovingSpaceLinearScan = new FlipNonMovingLinearScan(nonMovingSpace);
  public static final LinearScan flipSmallCodeSpaceLinearScan = new FlipNonMovingLinearScan(smallCodeSpace);
  public static final OTFSapphireVerifyCopyLinearScan verifyCopyLinearScan = new OTFSapphireVerifyCopyLinearScan();

  public static final int NO_TRACE                      = 0;
  public static final int ALLOC_TRACE                   = 1;
  public static final int FLIP_ROOT_TRACE               = 2;
  public static volatile int currentTrace;

  protected int iuTerminationLoopCount;
  private final int IU_TERMINATION_LOOP_LIMIT = REFERENCE_PROCESS_INFLOOP ? 0x7fffffff : 10;
  static {
    fromSpace().prepare(true);
    toSpace().prepare(false);
    fromSpaceSweeper.rebind(fromSpace());
    toSpaceSweeper.rebind(toSpace());
    nonMovingSpace.zeroAsUnmarked();
    smallCodeSpace.zeroAsUnmarked();
  }

  /**
   * Instance Variables
   */
  public final Trace allocTrace;
  public final Trace fromSpaceRemset;
  public final Trace flipRootTrace;
  public final Trace verifyTrace;
  public final SharedDeque patchQueue;
  public OTFSapphireCopyScan copyScanSTW;
  public OTFSapphireCopyScan copyScan;
  private int concurrentTriggerMethod;
  private int concurrentTrigger;
  private int lastCollectionUsedPages = 0;
  private long lastCollectionStartTime = 0;
  private long lastCollectionCompleteTime = 0;
  private long lastCollectionTriggerCumulativeCommittedPages = 0;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class variables
   */
  protected static final int ALLOC_REPLICATING = Plan.ALLOC_DEFAULT;

  public static final int FIRST_SCAN_SS = 0;
  public static final int SECOND_SCAN_SS = 0;

  public static volatile boolean mutatorsEnabled = true;

  public static boolean assert_ALL_OBJECTS_HAVE_REPLICA;
  public static boolean assert_FROM_SPACE_FORWARDED_OBJECTS_HAVE_FP;
  public static boolean assert_TO_SPLACE_LOCAL_IS_EMPTY;

  /**
   * Constructor
   */
  public OTFSapphire() {
    /*
     * Options
     */
    Options.concurrentCopyMethod = new org.mmtk.utility.options.ConcurrentCopyMethod();
    Options.concurrentCopyTransactionSize = new org.mmtk.utility.options.ConcurrentCopyTransactionSize();
    Options.sapphireSTWPhase = new org.mmtk.utility.options.SapphireSTWPhase();
    

    allocTrace = new Trace(metaDataSpace);
    fromSpaceRemset = new Trace(metaDataSpace);
    flipRootTrace = new Trace(metaDataSpace);
    if (VERIFY_FLIP)
      verifyTrace = new Trace(metaDataSpace);
    else
      verifyTrace = null;
    if (MERGE_REPLICATE_PHASE)
      patchQueue = new SharedDeque("shared patch queue", metaDataSpace, 2);
    else
      patchQueue = null;
    /**
     * This is the phase that is executed to perform a collection.
     */
    collection = Phase.createComplex("collection", null,
        // 0. initialize
        Phase.scheduleComplex  (initPhase),

        // 1. and 2. Replicate phase
        Phase.schedulePlaceholder(REPLICATE_PHASE),

        // 3. Flip
        Phase.scheduleGlobal         (PREPARE_FLIP),
        Phase.scheduleCollector      (PREPARE_FLIP),
        Phase.scheduleOnTheFlyMutator(PREPARE_FLIP_SETUP_BARRIER_0),
        Phase.scheduleOnTheFlyMutator(PREPARE_FLIP),
        Phase.scheduleOnTheFlyMutator(FLUSH_IMMORTAL),
        Phase.scheduleGlobal         (FLIP_NON_REPLICATING), // TODO: to use all collectors
        Phase.scheduleComplex        (forwardPhase),
        Phase.scheduleComplex        (onTheFlyRootsPhase),  // global root should be scanned prior to stack scanning, or stack may read gray pointers from the global root.
        Phase.scheduleCollector      (OTF_STACK_ROOTS),
        Phase.scheduleGlobal         (ROOTS),
        /* transitive closure is not needed */
        Phase.scheduleOnTheFlyMutator(RELEASE_FLIP),
        Phase.scheduleCollector      (RELEASE_FLIP),
        Phase.scheduleGlobal         (RELEASE_FLIP),

        // Z. finish
        Phase.scheduleComplex  (finishPhase));
  }
  
  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase
   */
  @Inline
  public void collectionPhase(short phaseId) {
    if (phaseId == SET_COLLECTION_KIND) {
      collectionAttempt = Allocator.determineCollectionAttempts();
      if (collectionAttempt <= 1) {
        lastCollectionStartTime = VM.statistics.nanoTime();
      }
      /* Our collector makes heap growth after mutators resume.  Mutator may fail to allocate before the heap growth.
       * If it fails, second collection before allocation succeeds.  This is normal situation, not emergency.
       */
      emergencyCollection = !Plan.isInternalTriggeredCollection() && lastCollectionWasExhaustive() && collectionAttempt > 2;
      if (emergencyCollection) {
        if (Options.verbose.getValue() >= 1) Log.write("[Emergency]");
        forceFullHeapCollection();
      }

      if (REFERENCE_REPORT_STARVATION) {
        if (!controlCollectorContext.isOnTheFlyCollection()) {
          Log.write("STWGC "); Log.writeln(org.mmtk.utility.statistics.Stats.gcCount());
        }
      }
      return;
    }

    /*
     * 1. Mark & Allocate phase
     */
    if (phaseId == PREPARE) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(toSpaceSweeper.getCursor().isZero());
      super.collectionPhase(PREPARE);
      allocTrace.prepare();
      fromSpaceRemset.prepareNonBlocking();
      if (MERGE_REPLICATE_PHASE)
        patchQueue.prepare();
      currentTrace = ALLOC_TRACE;
      iuTerminationLoopCount = 0;
      fromSpace().prepare(true);
      toSpace().prepare(false);
      return;
    }

    if (phaseId == STACK_ROOTS) {
      //TODO: VM.scanning.notifyInitialThreadScanComplete();
      setGCStatus(GC_PROPER);
      return;
    }

    if (phaseId == CLOSURE) {
      allocTrace.prepare();
      fromSpaceRemset.prepareNonBlocking();
      return;
    }

    if (phaseId == OTF_PREPARE_REFERENCE) {
      if (REFERENCE_PROCESS_STW) {
        super.unpreemptibleCollectionPhase(STOP_MUTATORS);
      }
      super.collectionPhase(phaseId);
      return;
    }

    if (phaseId == OTF_WEAK_REFS_TERMINATION) {
      super.collectionPhase(phaseId);
      return;
    }

    if (phaseId == OTF_PROCESS_WEAK_REFS) {
      super.collectionPhase(phaseId);
      return;
    }

    if (phaseId == OTF_RELEASE_REFERENCE) {
      if (REFERENCE_PROCESS_STW) {
        super.unpreemptibleCollectionPhase(RESTART_MUTATORS);
      }
      super.collectionPhase(phaseId);
      return;
    }

    if (phaseId == RELEASE) {
      allocTrace.release();
      fromSpaceRemset.release();
      if (MERGE_REPLICATE_PHASE)
        patchQueue.reset();
      /* All spaces holds garbage at this moment.
       * New objects are allocated as black in a concurrent collector setting.
       */
      return;
    }

    /*
     * 2. Copy phase
     */
    if (phaseId == PREPARE_COPY) {
      if (VM.VERIFY_ASSERTIONS) assert_ALL_OBJECTS_HAVE_REPLICA = true;
      if (VM.VERIFY_ASSERTIONS) assert_FROM_SPACE_FORWARDED_OBJECTS_HAVE_FP = true;
      tackOnLock.acquire();
      toSpaceSweeper.prepareForParallelLinearScan();
      return;
    }

    if (phaseId == RELEASE_COPY) {
      tackOnLock.release();
      return;
    }

    if (ENABLE_VERIFY_COPY) {
      if (phaseId == VERIFY_COPY) {
        tackOnLock.acquire();
        toSpaceSweeper.linearScan(verifyCopyLinearScan);
        tackOnLock.release();
        return;
      }
    }

    /*
     * 3. Flip phase
     */
    if (phaseId == PREPARE_FLIP) {
      flipRootTrace.prepareNonBlocking();
      currentTrace = FLIP_ROOT_TRACE;
      return;
    }

    /** Flip N */
    if (phaseId == FLIP_NON_REPLICATING) {
      /* flip immortal space objects */
      tackOnLock.acquire();
      immortalSpaceSweeper.linearScan(flipImmortalSpaceLinearScan);
      tackOnLock.release();
      /* flip large objects */
      loSpaceSweeper.linearScan(flipLoSpaceLinearScan);
      /* flip non-moving objects */
      nonMovingSpace.linearScan(flipNonMovingSpaceLinearScan);
      /* flip code space objects */
      if (USE_CODE_SPACE) {
        smallCodeSpace.linearScan(flipSmallCodeSpaceLinearScan);
        largeCodeSpaceSweeper.linearScan(flipLoSpaceLinearScan);
      }
      return;
    }

    /** Flip S */
    if (phaseId == FLIP_CLOSURE) {
      flipRootTrace.prepareNonBlocking();
      return;
    }

    if (phaseId == RELEASE_FLIP) {
      if (VM.VERIFY_ASSERTIONS) assert_TO_SPLACE_LOCAL_IS_EMPTY = true;
      low = !low; // flip the semi-spaces
      toSpace().release();
      OTFSapphire.tackOnLock.acquire();
      CopyLocal tmp = fromSpaceSweeper;
      fromSpaceSweeper = toSpaceSweeper;
      toSpaceSweeper = tmp;  // objects in toSpace are now in the fromSpace deadBumpPointers and will be scanned at next GC
      toSpaceSweeper.reset();  // for the next GC
      OTFSapphire.tackOnLock.release();
      flipRootTrace.release();
      currentTrace = NO_TRACE;
      super.collectionPhase(Simple.RELEASE);
      return;
    }

    if (phaseId == COMPLETE) {
      // update replicating space state
      super.collectionPhase(COMPLETE);
      lastCollectionUsedPages = getPagesUsed();
      lastCollectionCompleteTime = VM.statistics.nanoTime();
      if (lastCollectionTriggerCumulativeCommittedPages + 2 * concurrentTrigger < Space.cumulativeCommittedPages())
        lastCollectionTriggerCumulativeCommittedPages = Space.cumulativeCommittedPages() - concurrentTrigger;
      else
        lastCollectionTriggerCumulativeCommittedPages += concurrentTrigger;
      //copyScan.logStats();
    	return;
    }

    super.collectionPhase(phaseId);
  }

  @Override
  @Inline
  protected boolean collectorHasWork() {
    return allocTrace.hasWork() || fromSpaceRemset.hasWork();
  }

  @Override
  @Inline
  protected int getWeakReferenceTerminationLoopPhase() {
    if (REFERENCE_PROCESS_YUASA) {
      if (iuTerminationLoopCount++ < IU_TERMINATION_LOOP_LIMIT)
        return Phase.scheduleComplex(weakReferenceMarkIterationWithDeletionBarrier);
      else
        return Phase.scheduleComplex(weakReferenceMarkIterationFallbackWithDeletionBarrier);
    } else {
      if (iuTerminationLoopCount++ < IU_TERMINATION_LOOP_LIMIT)
        return Phase.scheduleComplex(weakReferenceMarkIteration);
      else
        return Phase.scheduleComplex(weakReferenceMarkIterationFallback);
    }
  }

  /****************************************************************************
   *
   * Accounting
   */

  /**
   * Return the number of pages reserved for copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  public final int getCollectionReserve() {
    // our copy reserve is the size of fromSpace less any copying we have done so far
    return fromSpace().reservedPages() + super.getCollectionReserve();
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  This is <i>exclusive of</i> space reserved for
   * copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public int getPagesUsed() {
    return fromSpace().reservedPages() + toSpace().reservedPages() + super.getPagesUsed();
  }

  /**
   * This method controls the triggering of a GC. It is called periodically
   * during allocation. Returns true to trigger a collection.
   *
   * @param spaceFull Space request failed, must recover pages within 'space'.
   * @param space TODO
   * @return True if a collection is requested by the plan.
   */
  protected boolean collectionRequired(boolean spaceFull, Space space) {
    if (space == toSpace()) {
      // toSpace allocation must always succeed
      // logPoll(space, "To-space collection requested - ignoring request");
      return false;
    }
    if (STOP_THE_WORLD_GC) {
      if (onTheFlyCollectionRequired(space))
        return true;
    }
    if (super.collectionRequired(spaceFull, space)) {
      lastCollectionTriggerCumulativeCommittedPages = Space.cumulativeCommittedPages();
      return true;
    }
    return false;
  }

  /**
   * This method controls the triggering of an atomic phase of a concurrent collection. It is called periodically during allocation.
   * @return True if a collection is requested by the plan.
   */
  @Override
  protected boolean onTheFlyCollectionRequired(Space space) {
    if (space == toSpace()) {
      // toSpace allocation must always succeed
      // logPoll(space, "To-space collection requested - ignoring request");
      return false;
    } else if (space == OTFSapphire.immortalSpace || space == OTFSapphire.loSpace) {
      // ignore allocation to immortal and large object spaces
      return false;
    }

    if (concurrentTriggerMethod == ConcurrentTriggerMethod.PERCENTAGE) {
      return ((getPagesReserved() * 100) / getTotalPages()) > concurrentTrigger;
    } else if (concurrentTriggerMethod == ConcurrentTriggerMethod.OOGC_ALLOCATION) {
      return ((getPagesUsed() - lastCollectionUsedPages) > concurrentTrigger);
    } else if (concurrentTriggerMethod == ConcurrentTriggerMethod.ALLOCATION) {
      if (Space.cumulativeCommittedPages() - lastCollectionTriggerCumulativeCommittedPages > concurrentTrigger) {
        return true;
      }
      return false;
    } else if (concurrentTriggerMethod == ConcurrentTriggerMethod.TIME) {
      long elapsed = VM.statistics.nanoTime() - lastCollectionCompleteTime;
      return (elapsed >= (((long) concurrentTrigger) * 1000L));
    } else if (concurrentTriggerMethod == ConcurrentTriggerMethod.PERIOD) {
      long elapsed = VM.statistics.nanoTime() - lastCollectionStartTime;
      return (elapsed >= (((long) concurrentTrigger) * 1000L));
    } else {
      return false;
    }
  }

  /**
   * @see org.mmtk.plan.Plan#willNeverMove
   *
   * @param object Object in question
   * @return True if the object will never move
   */
  @Override
  public boolean willNeverMove(ObjectReference object) {
    if (Space.isInSpace(SS0, object) || Space.isInSpace(SS1, object))
      return false;
    return super.willNeverMove(object);
  }

  /**
   * Register specialized methods.
   */
  @Interruptible
  protected void registerSpecializedMethods() {
//    TransitiveClosure.registerSpecializedScan(FIRST_SCAN_SS, STWSapphireAllocTraceLocal.class);
//    TransitiveClosure.registerSpecializedScan(SECOND_SCAN_SS, STWSapphireFlipTraceLocal.class);
    super.registerSpecializedMethods();
  }

  /****************************************************************************
   * Space checks
   */

  /**
   * @return The to space for the current collection.
   */
  @Inline
  public static ReplicatingSpace toSpace() {
    return low ? repSpace1 : repSpace0;
  }

  /**
   * @return The from space for the current collection.
   */
  @Inline
  public static ReplicatingSpace fromSpace() {
    return low ? repSpace0 : repSpace1;
  }

  public static boolean inFromSpace(ObjectReference obj) {
    return inFromSpace(VM.objectModel.refToAddress(obj));
  }

  public static boolean inToSpace(ObjectReference obj) {
    return inToSpace(VM.objectModel.refToAddress(obj));
  }

  public static ReplicatingSpace getSpace(boolean low) {
    return low ? repSpace0 : repSpace1;
  }

  /**
   * Warning only call if you know slot is actually a valid address within the object - not suitable for use when slot is actually a
   * reference
   * 
   * @param slot
   * @return
   */
  public static boolean inFromSpace(Address slot) {
    return Space.isInSpace(fromSpace().getDescriptor(), slot);
  }

  /**
   * Warning only call if you know slot is actually a valid address within the object - not suitable for use when slot is actually a
   * reference
   * 
   * @param slot
   * @return
   */
  public static boolean inToSpace(Address slot) {
    return Space.isInSpace(toSpace().getDescriptor(), slot);
  }

  /** @return the current trace object. */
  public Trace getCurrentTrace() {
    if (currentTrace == 1)
      return allocTrace;
    else if (currentTrace == 2)
      return flipRootTrace;
    else {
      VM.assertions.fail("Unknown trace count");
      return null;
    }
  }

  /**
   * The processOptions method is called by the runtime immediately after command-line arguments are available. Allocation must be
   * supported prior to this point because the runtime infrastructure may require allocation in order to parse the command line
   * arguments. For this reason all plans should operate gracefully on the default minimum heap size until the point that
   * processOptions is called.
   */
  @Interruptible
  public void processOptions() {
    // don't call Super's processOptions otherwise it will overwrite the PLACEHOLDER's
    VM.statistics.perfEventInit(Options.perfEvents.getValue());
    if (Options.verbose.getValue() > 2) Space.printVMMap();
    if (Options.verbose.getValue() > 3) VM.config.printConfig();
    if (Options.verbose.getValue() > 0) Stats.startAll();
    if (Options.eagerMmapSpaces.getValue()) Space.eagerlyMmapMMTkSpaces();

    // Replicate phase or Alloc + Copy phase
    replacePhase(Phase.schedulePlaceholder(REPLICATE_PHASE),
                 Phase.scheduleComplex(MERGE_REPLICATE_PHASE ? replicatePhase : allocAndCopyPhase));

    // replace STW phases with concurrent ones
    STW_ALLOC = Options.sapphireSTWPhase.isStopTheWorld(SapphireSTWPhase.ALLOC);
    STW_COPY = Options.sapphireSTWPhase.isStopTheWorld(SapphireSTWPhase.COPY);
    STW_FLIP = Options.sapphireSTWPhase.isStopTheWorld(SapphireSTWPhase.FLIP);
    STW_ROOTS = Options.sapphireSTWPhase.isStopTheWorld(SapphireSTWPhase.ROOT);
    if (STW_ALLOC) replacePhase(Phase.scheduleCollector(CLOSURE), Phase.scheduleComplex(stwMarkAllocPhase));
    if (STW_COPY)  replacePhase(Phase.scheduleComplex(copyPhase), Phase.scheduleComplex(stwCopyPhase));
    if (STW_FLIP)  replacePhase(Phase.scheduleGlobal(FLIP_NON_REPLICATING), Phase.scheduleComplex(stwFlipPhase));
    if (STW_ROOTS) {
      replacePhase(Phase.schedulePlaceholder(STOP_MUTATORS_BEFORE_ROOTS_HOOK), Phase.scheduleSpecial(STOP_MUTATORS));
      replacePhase(Phase.schedulePlaceholder(RESTART_MUTATORS_AFTER_ROOTS_HOOK), Phase.scheduleSpecial(RESTART_MUTATORS));
    }

    if (Options.sanityCheck.getValue()) {
      Log.writeln("Sapphire Collection sanity checking enabled.");
      replacePhase(Phase.schedulePlaceholder(PRE_SANITY_PLACEHOLDER), Phase.scheduleComplex(preSanityPhase));
      replacePhase(Phase.schedulePlaceholder(POST_SANITY_PLACEHOLDER), Phase.scheduleComplex(postSanityPhase));
    }

    if (ENSURE_STACK_PREPARED) {
      replacePhase(Phase.scheduleCollector(STACK_ROOTS), Phase.scheduleComplex(ensurePreparedAndScanStack));
    }

    if (ENABLE_VERIFY_COPY) {
      replacePhase(Phase.schedulePlaceholder(POST_COPY_HOOK), Phase.scheduleComplex(verifyCopy));
    }
    
    switch (Options.concurrentCopyMethod.method()) {
      case ConcurrentCopyMethod.HTM:    copyScan = new OTFSapphireCopyScan.CopyScanHTM(flipRootTrace);        break;
      case ConcurrentCopyMethod.HTM2:   copyScan = new OTFSapphireCopyScan.CopyScanHTM2(flipRootTrace);       break;
      case ConcurrentCopyMethod.CAS:    copyScan = new OTFSapphireCopyScan.CopyScanCAS(flipRootTrace);        break;
      case ConcurrentCopyMethod.CAS2:   copyScan = new OTFSapphireCopyScan.CopyScanCAS2(flipRootTrace);       break;
      case ConcurrentCopyMethod.UNSAFE: copyScan = new OTFSapphireCopyScan.CopyScanSTW(flipRootTrace);        break;
      case ConcurrentCopyMethod.UNSAFE2:copyScan = new OTFSapphireCopyScan.CopyScanUnsafe2(flipRootTrace);    break;
      default:
        copyScan = null; // collector threads must allocate their own scan method
        break;
    }
    copyScanSTW = new OTFSapphireCopyScan.CopyScanUnsafe2(flipRootTrace);
    concurrentTriggerMethod = Options.concurrentTriggerMethod.method();
    concurrentTrigger = Options.concurrentTrigger.getValueForMethod(concurrentTriggerMethod);
    
    if (MERGE_REPLICATE_PHASE) Log.writeln("Option: MERGE_REPLICATE_PHASE");
    if (REPLICATE_WITH_CAS)    Log.writeln("Option: REPLICATE_WITH_CAS");
    if (STOP_THE_WORLD_GC)     Log.writeln("Option: STOP_THE_WORLD_GC");
    if (DO_MULTIPLE_PATCH)     Log.writeln("Option: DO_MULTIPLE_PATCH");
    if (REPORT_FIX_POINTERS)   Log.writeln("Option: REPORT_FIX_POINTERS");
    if (ENABLE_VERIFY_COPY)    Log.writeln("Option: ENABLE_VERIFY_COPY");
    if (ENABLE_VERIFY_COPY)    Log.writeln("Option: ENABLE_VERIFY_COPY");
    if (REFERENCE_REPORT_STARVATION) Log.writeln("options REFERENCE_REPORT_STARVATION");
  }

  /**
   * Any Plan can override this to provide additional plan specific
   * timing information.
   *
   * @param totals Print totals
   */
  @Override
  protected void printDetailedTiming(boolean totals) {
    if (OTFSapphireCopyScan.PROFILE) {
      Log.write("Copy objects: ");
      Log.writeln(OTFSapphireCopyScan.getCopyCount());
      Log.write("Copy failure: ");
      Log.writeln(OTFSapphireCopyScan.getFailureCount());
      if (OTFSapphireCopyScan.getCopyCount() > 0) {
        Log.write("Copy bytes: ");
        Log.write(OTFSapphireCopyScan.getCopyBytes());
        Log.write(" avg ");
        Log.writeln(((double) OTFSapphireCopyScan.getCopyBytes()) / ((double) OTFSapphireCopyScan.getCopyCount()));
        Log.write("Copy pointers: ");
        Log.write(OTFSapphireCopyScan.getCopyPointers());
        Log.write(" avg ");
        Log.writeln(((double) OTFSapphireCopyScan.getCopyPointers()) / ((double) OTFSapphireCopyScan.getCopyCount()));
      }
    }
  }
}
