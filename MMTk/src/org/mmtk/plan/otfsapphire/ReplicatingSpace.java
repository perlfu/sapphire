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

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.policy.CopySpace;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;

import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements tracing functionality for a simple copying
 * space.  Since no state needs to be held globally or locally, all
 * methods are static.
 */
@Uninterruptible
public final class ReplicatingSpace extends CopySpace
implements Constants {

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean TRACEATOMIC = false;
  
  public static final int LOCAL_GC_BITS_REQUIRED = 2; // BUSY and FORWARDED
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  public static final int GC_HEADER_WORDS_REQUIRED = 1; // Whole word for forwarding pointer

  public static final int BUSY        = 0x1;
  public static final int FORWARDED   = 0x2;
  public static final int STATUS_MASK = 0x3;

  static final Offset REPLICA_POINTER_OFFSET = VM.objectModel.GC_HEADER_OFFSET();

  public static final int GC_INACTIVE = 0;
  public static final int IN_GC_ALLOC_PHASE = 1;
  public static final int IN_GC_COPY_PHASE = 2;
  public static final int IN_GC_FLIP_PHASE = 3;

  public static final boolean FOR_NEW_OBJECT = true;
  public static final boolean FOR_EXISTING_OBJECT = false;

  public static final boolean CHECK_FORWARD = true;
  public static final boolean CHECK_BACKWARD = false;

  /* region list */
  private Address regionList;
  private static final Lock lock = VM.newLock("replicating space");

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * The caller specifies the region of virtual memory to be used for
   * this space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param fromSpace The does this instance start life as from-space
   * (or to-space)?
   * @param vmRequest An object describing the virtual memory requested.
   */
  public ReplicatingSpace(String name, VMRequest vmRequest) {
    super(name, true, true, vmRequest);
  }

  /****************************************************************************
   *
   * Prepare and release
   */

  /**
   * Release this copy space after a collection.  This means releasing
   * all pages associated with this (now empty) space.
   */
  @Override
  public void release() {
    super.release();
    regionList = Address.zero();
  }

  /****************************************************************************
   *
   * Tracing and forwarding
   */

  @Inline
  public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions.fail("traceObject cannot be called without allocator");
    return null;
  }

  /**
   * Return true if this object is live in this GC
   * This method is valid after mark phase and before releasing flip.
   *
   * @param object The object in question
   * @return True if this object is live in this GC (has it been forwarded?)
   */
  @Inline
  public boolean isLive(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(fromSpace); // if the object is in toSpace, it should be live
    return isForwarded(object);
  }

  /**
   * Has the object in this space been reached during the current collection.
   * This is used for GC Tracing.
   *
   * @param object The object reference.
   * @return True if the object is reachable.
   */
  @Inline
  public boolean isReachable(ObjectReference object) {
    return !fromSpace || isForwarded(object);
  }

  /****************************************************************************
   *
   * Region list manipulation
   */

  /**
   * Append a region or list of regions to the global list
   * @param region
   */
  public void append_unused(Address region) {
    lock.acquire();
    if (Options.verbose.getValue() >= 8) {
      Log.write("Appending region "); Log.write(region);
      Log.writeln(" to global list");
    }
    if (regionList.isZero()) {
      regionList = region;
    } else {
      appendRegion(regionList,region);
    }
    lock.release();
  }

  @Inline
  public static void appendRegion(Address listHead, Address region) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!listHead.isZero());
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!region.isZero());
    Address cursor = listHead;
    while (!BumpPointer.getNextRegion(cursor).isZero()) {
      cursor = BumpPointer.getNextRegion(cursor);
    }
    BumpPointer.setNextRegion(cursor, region);
  }

  /****************************************************************************
   *
   * Header manipulation
   */

  /**
   * Object Header
   *   New object              -> available bits: 00  repl ptr: null
   *   Not copied yet          -> available bits: 00  repl ptr: junk
   *   From space being copied -> available bits: 01  repl ptr: junk
   *   From space copied       -> available bits: 10  repl ptr: valid
   *   From space, some thread attempt to copy already-copied
   *                           -> available bits: 11  repl ptr: valid
   *   To space replica        -> available bits: 00  repl ptr: valid
   */
  @Inline
  public static void initializeHeader(ObjectReference object, boolean alloc) {
    if (alloc) {
      /* nothing to do because all bits are zero */
      return;
    }
    /* clear status bits */
    Word value = VM.objectModel.readAvailableBitsWord(object);
    VM.objectModel.writeAvailableBitsWord(object, value.and(Word.fromIntZeroExtend(STATUS_MASK).not()));
    /* We do not have to clear RP word because it will be overwritten soon */
  }

  @Inline
  public static boolean isBusy(ObjectReference object) {
    return (VM.objectModel.readAvailableByte(object) & BUSY) == BUSY;
  }

  @Inline
  public static Word atomicMarkBusy(ObjectReference object, OTFSapphireMutator mutator) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(mutator == null ? OTFSapphire.inFromSpace(object) : mutator.inFromSpace(object));
    Word oldValue;
    if (TRACEATOMIC) {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      if ((oldValue.toInt() & BUSY) == BUSY || !VM.objectModel.attemptAvailableBits(object, oldValue, oldValue.or(Word.fromIntZeroExtend(BUSY)))) {
        do {
          oldValue = VM.objectModel.prepareAvailableBits(object);
        } while ((oldValue.toInt() & BUSY) == BUSY || !VM.objectModel.attemptAvailableBits(object, oldValue, oldValue.or(Word.fromIntZeroExtend(BUSY))));
      }
    } else {
      do {
        oldValue = VM.objectModel.prepareAvailableBits(object);
      } while ((oldValue.toInt() & BUSY) == BUSY || !VM.objectModel.attemptAvailableBits(object, oldValue, oldValue.or(Word.fromIntZeroExtend(BUSY))));
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isBusy(object));
    return oldValue.or(Word.fromIntZeroExtend(BUSY));
  }

  @Inline
  public static void clearBusy(ObjectReference object, OTFSapphireMutator mutator) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(mutator == null ? OTFSapphire.inFromSpace(object) : mutator.inFromSpace(object));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isBusy(object));
    Word oldValue = VM.objectModel.readAvailableBitsWord(object);
    VM.objectModel.writeAvailableBitsWord(object, oldValue.and(Word.fromIntZeroExtend(BUSY).not()));
  }

  @Inline
  public static boolean isForwarded(ObjectReference object) {
    return (VM.objectModel.readAvailableByte(object) & FORWARDED) == FORWARDED;
  }

  /** status = BUSY, repl ptr = valid -> status = FORWARDED, repl ptr = valid */
  @Inline
  public static void setForwarded(ObjectReference object, OTFSapphireMutator mutator, boolean newObj) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(mutator == null ? OTFSapphire.inFromSpace(object) : mutator.inFromSpace(object));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(newObj || isBusy(object));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!getReplicaPointer(object).isNull());
    Word value = VM.objectModel.readAvailableBitsWord(object);
    value = value.and(Word.fromIntZeroExtend(STATUS_MASK).not());
    value = value.or(Word.fromIntZeroExtend(FORWARDED));
    VM.objectModel.writeAvailableBitsWord(object, value);
  }

  @Inline
  public static ObjectReference getReplicaPointer(ObjectReference object) {
    return object.toAddress().loadObjectReference(REPLICA_POINTER_OFFSET);
  }

  /**
   * Install a replica pointer to object.  If object can be seen by mutators, it should be locked by using busy bit.
   * @param object The object to which the replica pointer is installed.
   * @param ptr The replica pointer.
   * @param forward True if object is a from-space object.  For verification only.
   * @param newObj True if this call is for double allocation, and so mutators cannot see object even the object is in the from-space.  For verification only.
   */
  @Inline
  public static void setReplicaPointer(ObjectReference object, ObjectReference ptr, OTFSapphireMutator mutator, boolean forward, boolean newObj) {
    if (forward) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(mutator == null ? OTFSapphire.inFromSpace(object) : mutator.inFromSpace(object));
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(mutator == null ? OTFSapphire.inToSpace(ptr) : mutator.inToSpace(ptr));
      if (newObj) {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!isBusy(object));
      } else {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isBusy(object));
      }
    } else {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(mutator == null ? OTFSapphire.inFromSpace(ptr) : mutator.inFromSpace(ptr));
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(mutator == null ? OTFSapphire.inToSpace(object) : mutator.inToSpace(object));
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!isBusy(object));
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!isForwarded(object));
    object.toAddress().store(ptr, REPLICA_POINTER_OFFSET);
  }

  @Inline
  public static Word wordMarkBusy(Word old) {
    return old.or(Word.fromIntZeroExtend(BUSY));
  }

  @Inline
  public static boolean wordIsForwarded(Word old) {
    return (old.toInt() & FORWARDED) == FORWARDED;
  }

  @Inline
  public static ObjectReference metaLockObject(ObjectReference obj, OTFSapphireMutator mutator) {
    Word s;
    do {
      s = VM.objectModel.prepareAvailableBits(obj);
      if ((s.toInt() & FORWARDED) == FORWARDED) return getReplicaPointer(obj);
    } while ((s.toInt() & BUSY) == BUSY || !VM.objectModel.attemptAvailableBits(obj, s, s.or(Word.fromIntZeroExtend(BUSY))));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(mutator == null ? OTFSapphire.inFromSpace(obj) : mutator.inFromSpace(obj));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isBusy(obj));
    return obj;
  }

  @Inline
  public static void metaUnlockObject(ObjectReference obj, OTFSapphireMutator mutator) {
    Word s;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(mutator == null ? OTFSapphire.inFromSpace(obj) : mutator.inFromSpace(obj));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isBusy(obj));
    do {
      s = VM.objectModel.prepareAvailableBits(obj);
    } while (!VM.objectModel.attemptAvailableBits(obj, s, s.and(Word.fromIntZeroExtend(~BUSY))));
  }
}
