/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility.alloc;

import org.mmtk.policy.Space;
import org.mmtk.utility.*;
import org.mmtk.utility.gcspy.drivers.LinearSpaceDriver;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements a bump pointer allocator that allows linearly
 * scanning through the allocated objects. In order to achieve this in the
 * face of parallelism it maintains a header at a region (1 or more chunks)
 * granularity.
 *
 * Intra-block allocation is fast, requiring only a load, addition comparison
 * and store.  If a block boundary is encountered the allocator will
 * request more memory (virtual and actual).
 *
 * In the current implementation the scanned objects maintain affinity
 * with the thread that allocated the objects in the region. In the future
 * it is anticipated that subclasses should be allowed to choose to improve
 * load balancing during the parallel scan.
 *
 * Each region is laid out as follows:
 *
 *  +-------------+-------------+-------------+---------------
 *  | Region  End | Next Region |  Data  End  | Data -->
 * +-------------+-------------+-------------+---------------
 *
 * The minimum region size is 32768 bytes, so the 3 or 4 word overhead is
 * less than 0.05% of all space.
 *
 * An intended enhancement is to facilitate a reallocation operation
 * where a second cursor is maintained over earlier regions (and at the
 * limit a lower location in the same region). This would be accompianied
 * with an alternative slow path that would allow reuse of empty regions.
 *
 * This class relies on the supporting virtual machine implementing the
 * getNextObject and related operations.
 */
@Uninterruptible public class BumpPointer extends Allocator
  implements Constants {

  /****************************************************************************
   *
   * Class variables
   */

  // Chunk size defines slow path periodicity.
  private static final int LOG_DEFAULT_STEP_SIZE = 20; // 1M: let the external slow path dominate
  private static final int STEP_SIZE = 1<<(SUPPORT_CARD_SCANNING ? LOG_CARD_BYTES : LOG_DEFAULT_STEP_SIZE);
  protected static final int LOG_CHUNK_SIZE = LOG_BYTES_IN_PAGE + 3;
  protected static final Word CHUNK_MASK = Word.one().lsh(LOG_CHUNK_SIZE).minus(Word.one());

  // Offsets into header
  protected static final Offset REGION_LIMIT_OFFSET = Offset.zero();
  protected static final Offset NEXT_REGION_OFFSET = REGION_LIMIT_OFFSET.plus(BYTES_IN_ADDRESS);
  protected static final Offset DATA_END_OFFSET = NEXT_REGION_OFFSET.plus(BYTES_IN_ADDRESS);

  // Data must start particle-aligned.
  protected static final Offset DATA_START_OFFSET = alignAllocationNoFill(
      Address.zero().plus(DATA_END_OFFSET.plus(BYTES_IN_ADDRESS)),
      MIN_ALIGNMENT, 0).toWord().toOffset();
  protected static final Offset MAX_DATA_START_OFFSET = alignAllocationNoFill(
      Address.zero().plus(DATA_END_OFFSET.plus(BYTES_IN_ADDRESS)),
      MAX_ALIGNMENT, 0).toWord().toOffset();

  /****************************************************************************
   *
   * Instance variables
   */
  protected Address cursor; // insertion point
  private Address internalLimit; // current internal slow-path sentinal for bump pointer
  private Address limit; // current external slow-path sentinal for bump pointer
  protected Space space; // space this bump pointer is associated with
  protected Address initialRegion; // first contiguous region
  protected final boolean allowScanning; // linear scanning is permitted if true
  protected Address region; // current contigious region


  /**
   * Constructor.
   *
   * @param space The space to bump point into.
   * @param allowScanning Allow linear scanning of this region of memory.
   */
  protected BumpPointer(Space space, boolean allowScanning) {
    this.space = space;
    this.allowScanning = allowScanning;
    reset();
  }

  /**
   * Reset the allocator. Note that this does not reset the space.
   * This is must be done by the caller.
   */
  public final void reset() {
    cursor = Address.zero();
    limit = Address.zero();
    internalLimit = Address.zero();
    initialRegion = Address.zero();
    region = Address.zero();
  }

  /**
   * Re-associate this bump pointer with a different space. Also
   * reset the bump pointer so that it will use the new space
   * on the next call to <code>alloc</code>.
   *
   * @param space The space to associate the bump pointer with.
   */
  public final void rebind(Space space) {
    reset();
    this.space = space;
  }

  /**
   * Allocate space for a new object.  This is frequently executed code and
   * the coding is deliberaetly sensitive to the optimizing compiler.
   * After changing this, always check the IR/MC that is generated.
   *
   * @param bytes The number of bytes allocated
   * @param align The requested alignment
   * @param offset The offset from the alignment
   * @param inGC Is the allocation request occuring during GC.
   * @return The address of the first byte of the allocated region
   */
  @Inline
  public final Address alloc(int bytes, int align, int offset, boolean inGC) {
    Address start = alignAllocationNoFill(cursor, align, offset);
    Address end = start.plus(bytes);
    if (end.GT(internalLimit))
      return allocSlow(start, end, align, offset, inGC);
    fillAlignmentGap(cursor, start);
    cursor = end;
    return start;
  }

 /**
  * Internal allocation slow path.  This is called whenever the bump
  * pointer reaches the internal limit.  The code is forced out of
  * line.  If required we perform an external slow path take, which
  * we inline into this method since this is already out of line.
  *
  * @param start The start address for the pending allocation
  * @param end The end address for the pending allocation
  * @param align The requested alignment
  * @param offset The offset from the alignment
  * @param inGC Is the allocation request occuring during GC.
  * @return The address of the first byte of the allocated region
  */
  @NoInline
  private Address allocSlow(Address start, Address end, int align,
      int offset, boolean inGC) {
    Address rtn = null;
    Address card = null;
    if (SUPPORT_CARD_SCANNING)
      card = getCard(start.plus(CARD_MASK)); // round up
    if (end.GT(limit)) { /* external slow path */
      rtn = allocSlowInline(end.diff(start).toInt(), align, offset,
          inGC);
      if (SUPPORT_CARD_SCANNING && card.NE(getCard(rtn.plus(CARD_MASK))))
        card = getCard(rtn); // round down
    } else {             /* internal slow path */
      while (internalLimit.LE(end))
        internalLimit = internalLimit.plus(STEP_SIZE);
      if (internalLimit.GT(limit))
        internalLimit = limit;
      fillAlignmentGap(cursor, start);
      cursor = end;
      rtn = start;
    }
    if (SUPPORT_CARD_SCANNING && !rtn.isZero())
      createCardAnchor(card, rtn, end.diff(start).toInt());
    return rtn;
  }

  /**
   * Given an allocation which starts a new card, create a record of
   * where the start of the object is relative to the start of the
   * card.
   *
   * @param card An address that lies within the card to be marked
   * @param start The address of an object which creates a new card.
   * @param bytes The size of the pending allocation in bytes (used for debugging)
   */
  private void createCardAnchor(Address card, Address start, int bytes) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(allowScanning);
      VM.assertions._assert(card.EQ(getCard(card)));
      VM.assertions._assert(start.diff(card).sLE(MAX_DATA_START_OFFSET));
      VM.assertions._assert(start.diff(card).toInt() >= -CARD_MASK);
    }
    while (bytes > 0) {
      int offset = start.diff(card).toInt();
      getCardMetaData(card).store(offset);
      card = card.plus(1 << LOG_CARD_BYTES);
      bytes -= (1 << LOG_CARD_BYTES);
    }
  }

  /**
   * Return the start of the card corresponding to a given address.
   *
   * @param address The address for which the card start is required
   * @return The start of the card containing the address
   */
  private static Address getCard(Address address) {
    return address.toWord().and(Word.fromIntSignExtend(CARD_MASK).not()).toAddress();
  }

  /**
   * Return the address of the metadata for a card, given the address of the card.
   * @param card The address of some card
   * @return The address of the metadata associated with that card
   */
  private static Address getCardMetaData(Address card) {
    Address metadata = EmbeddedMetaData.getMetaDataBase(card);
    return metadata.plus(EmbeddedMetaData.getMetaDataOffset(card, LOG_CARD_BYTES-LOG_CARD_META_SIZE, LOG_CARD_META_SIZE));
  }

  /**
   * External allocation slow path (called by superclass when slow path is
   * actually taken.  This is necessary (rather than a direct call
   * from the fast path) because of the possibility of a thread switch
   * and corresponding re-association of bump pointers to kernel
   * threads.
   *
   * @param bytes The number of bytes allocated
   * @param align The requested alignment
   * @param offset The offset from the alignment
   * @param inGC Was the request made from within GC?
   * @return The address of the first byte of the allocated region or
   * zero on failure
   */
  protected final Address allocSlowOnce(int bytes, int align, int offset,
      boolean inGC) {
    /* Check if we already have a chunk to use */
    if (allowScanning && !region.isZero()) {
      Address nextRegion = region.loadAddress(NEXT_REGION_OFFSET);
      if (!nextRegion.isZero()) {
        return consumeNextRegion(nextRegion, bytes, align, offset, inGC);
      }
    }

    /* Aquire space, chunk aligned, that can accomodate the request */
    Extent chunkSize = Word.fromIntZeroExtend(bytes).plus(CHUNK_MASK)
                       .and(CHUNK_MASK.not()).toExtent();
    Address start = space.acquire(Conversions.bytesToPages(chunkSize));

    if (start.isZero()) return start; // failed allocation

    if (!allowScanning) { // simple allocator
      if (start.NE(limit)) cursor = start;  // discontigious
      updateLimit(start.plus(chunkSize), start, bytes);
    } else                // scannable allocator
      updateMetaData(start, chunkSize, bytes);
    return alloc(bytes, align, offset, inGC);
  }

  /**
   * Update the limit pointer.  As a side effect update the internal limit
   * pointer appropriately.
   *
   * @param newLimit The new value for the limit pointer
   * @param start The start of the region to be allocated into
   * @param bytes The size of the pending allocation (if any).
   */
  @Inline
  protected final void updateLimit(Address newLimit, Address start, int bytes) {
    limit = newLimit;
    internalLimit = start.plus(STEP_SIZE);
    if (internalLimit.GT(limit))
      internalLimit = limit;
    else {
      while (internalLimit.LT(cursor.plus(bytes)))
        internalLimit = internalLimit.plus(STEP_SIZE);
      if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(internalLimit.LE(limit));
    }
  }

  /**
   * A bump pointer chuck/region has been consumed but the contigious region
   * is available, so consume it and then return the address of the start
   * of a memory region satisfying the outstanding allocation request.  This
   * is relevant when re-using memory, as in a mark-compact collector.
   *
   * @param nextRegion The region to be consumed
   * @param bytes The number of bytes allocated
   * @param align The requested alignment
   * @param offset The offset from the alignment
   * @param inGC Was the request made from within GC?
   * @return The address of the first byte of the allocated region or
   * zero on failure
   */
  private Address consumeNextRegion(Address nextRegion, int bytes, int align,
        int offset, boolean inGC) {
    region.plus(DATA_END_OFFSET).store(cursor);
    region = nextRegion;
    cursor = nextRegion.plus(DATA_START_OFFSET);
    updateLimit(nextRegion.loadAddress(REGION_LIMIT_OFFSET), nextRegion, bytes);
    nextRegion.store(Address.zero(), DATA_END_OFFSET);
    VM.memory.zero(cursor, limit.diff(cursor).toWord().toExtent().plus(BYTES_IN_ADDRESS));
    reusePages(Conversions.bytesToPages(limit.diff(region).plus(BYTES_IN_ADDRESS)));

    return alloc(bytes, align, offset, inGC);
  }

  /**
   * Update the metadata to reflect the addition of a new region.
   *
   * @param start The start of the new region
   * @param size The size of the new region (rounded up to chunk-alignment)
   */
  @Inline
  private void updateMetaData(Address start, Extent size, int bytes) {
    if (initialRegion.isZero()) {
      /* this is the first allocation */
      initialRegion = start;
      region = start;
      cursor = region.plus(DATA_START_OFFSET);
    } else if (limit.plus(BYTES_IN_ADDRESS).NE(start)
        || region.diff(start.plus(size)).toWord().toExtent()
        .GT(maximumRegionSize())) {
      /* non contiguous or over-size, initialize new region */
      region.plus(NEXT_REGION_OFFSET).store(start);
      region.plus(DATA_END_OFFSET).store(cursor);
      region = start;
      cursor = start.plus(DATA_START_OFFSET);
    }
    updateLimit(start.plus(size.minus(BYTES_IN_ADDRESS)), start, bytes); // skip over region limit
    region.plus(REGION_LIMIT_OFFSET).store(limit);
  }

  /**
   * Gather data for GCspy. <p>
   * This method calls the drivers linear scanner to scan through
   * the objects allocated by this bump pointer.
   *
   * @param driver The GCspy driver for this space.
   */
  public void gcspyGatherData(LinearSpaceDriver driver) {
	//driver.setRange(space.getStart(), cursor);
    driver.setRange(space.getStart(), limit);
    this.linearScan(driver.getScanner());
  }

  /**
   * Gather data for GCspy. <p>
   * This method calls the drivers linear scanner to scan through
   * the objects allocated by this bump pointer.
   *
   * @param driver The GCspy driver for this space.
   * @param scanSpace The space to scan
   */
  public void gcspyGatherData(LinearSpaceDriver driver, Space scanSpace) {
	//TODO can scanSpace ever be different to this.space?
    if (VM.VERIFY_ASSERTIONS)
	  VM.assertions._assert(scanSpace == space, "scanSpace != space");

	//driver.setRange(scanSpace.getStart(), cursor);
    Address start = scanSpace.getStart();
    driver.setRange(start, limit);

    if (false) {
      Log.write("\nBumpPointer.gcspyGatherData set Range "); Log.write(scanSpace.getStart());
      Log.write(" to "); Log.writeln(limit);
      Log.write("BumpPointergcspyGatherData scan from "); Log.writeln(initialRegion);
    }

    linearScan(driver.getScanner());
  }


  /**
   * Perform a linear scan through the objects allocated by this bump pointer.
   *
   * @param scanner The scan object to delegate scanning to.
   */
  @Inline
  public final void linearScan(LinearScan scanner) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(allowScanning);
    /* Has this allocator ever allocated anything? */
    if (initialRegion.isZero()) return;

    /* Loop through active regions or until the last region */
    Address start = initialRegion;
    while (!start.isZero()) {
      scanRegion(scanner, start); // Scan this region
      start = start.plus(NEXT_REGION_OFFSET).loadAddress(); // Move on to next
    }
  }

  /**
   * Perform a linear scan through a single contigious region
   *
   * @param scanner The scan object to delegate to.
   * @param start The start of this region
   */
  @Inline
  private void scanRegion(LinearScan scanner, Address start) {
    /* Get the end of this region */
    Address dataEnd = start.plus(DATA_END_OFFSET).loadAddress();

    /* dataEnd = zero represents the current region. */
    Address currentLimit = (dataEnd.isZero() ? cursor : dataEnd);
    ObjectReference current =
      VM.objectModel.getObjectFromStartAddress(start.plus(DATA_START_OFFSET));

    while (VM.objectModel.refToAddress(current).LT(currentLimit) && !current.isNull()) {
      ObjectReference next = VM.objectModel.getNextObject(current);
      scanner.scan(current); // Scan this object.
      current = next;
    }
  }

  /**
   * Some pages are about to be re-used to satisfy a slow path request.
   * @param pages The number of pages.
   */
  protected void reusePages(int pages) {
    VM.assertions.fail("Subclasses that reuse regions must override this method.");
  }

  /**
   * Maximum size of a single region. Important for children that implement
   * load balancing or increments based on region size.
   * @return the maximum region size
   */
  protected Extent maximumRegionSize() { return Extent.max(); }

  /** @return the current cursor value */
  public final Address getCursor() { return cursor; }
  /** @return the space associated with this bump pointer */
  public final Space getSpace() { return space; }

  /**
   * Print out the status of the allocator (for debugging)
   */
  public final void show() {
    Log.write("cursor = "); Log.write(cursor);
    if (allowScanning) {
      Log.write(" region = "); Log.write(region);
    }
    Log.write(" limit = "); Log.writeln(limit);
  }
}
