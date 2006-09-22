/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.utility.alloc;

import org.mmtk.plan.Plan;
import org.mmtk.policy.Space;
import org.mmtk.utility.*;
import org.mmtk.utility.statistics.*;
import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This abstract base class provides the basis for processor-local
 * allocation.  The key functionality provided is the retry mechanism
 * that is necessary to correctly handle the fact that a "slow-path"
 * allocation can cause a GC which violate the uninterruptability assumption.
 * This results in the thread being moved to a different processor so that
 * the allocator object it is using is not actually the one for the processor
 * it is running on.
 *
 * This class also includes functionality to assist allocators with
 * ensuring that requests are aligned according to requests.
 *
 * Failing to handle this properly will lead to very hard to trace bugs
 * where the allocation that caused a GC or allocations immediately following
 * GC are run incorrectly.
 * 
 * $Id$
 * 
 * @author Perry Cheng
 * @modified Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */

public abstract class Allocator implements Constants, Uninterruptible {
  /**
   * Maximum number of retries on consecutive allocation failure.
   * 
   */
  private static final int MAX_RETRY = 5;

  /**
   * Constructor
   * 
   */
  Allocator() {
  }

  /**
   * Aligns up an allocation request. The allocation request accepts a
   * region, that must be at least particle aligned, an alignment
   * request (some power of two number of particles) and an offset (a
   * number of particles). There is also a knownAlignment parameter to
   * allow a more optimised check when the particular allocator in use
   * always aligns at a coarser grain than individual particles, such
   * as some free lists.
   *
   * @param region The region to align up.
   * @param alignment The requested alignment
   * @param offset The offset from the alignment 
   * @param knownAlignment The statically known minimum alignment.
   * @return The aligned up address.
   */
  final public static Address alignAllocation(Address region, int alignment,
                                             int offset, int knownAlignment, 
                                             boolean fillAlignmentGap)
      throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(knownAlignment >= MIN_ALIGNMENT);
      VM.assertions._assert(MIN_ALIGNMENT >= BYTES_IN_INT);
      VM.assertions._assert(!(fillAlignmentGap && region.isZero()));
      VM.assertions._assert(alignment <= MAX_ALIGNMENT);
      VM.assertions._assert(offset >= 0);
      VM.assertions._assert(region.toWord().and(Word.fromIntSignExtend(MIN_ALIGNMENT-1)).isZero());
      VM.assertions._assert((alignment & (MIN_ALIGNMENT - 1)) == 0);
      VM.assertions._assert((offset & (MIN_ALIGNMENT - 1)) == 0);
    }

    // No alignment ever required.
    if (alignment <= knownAlignment || MAX_ALIGNMENT <= MIN_ALIGNMENT)
      return region;

    // May require an alignment
    Word mask = Word.fromIntSignExtend(alignment - 1);
    Word negOff = Word.fromIntSignExtend(-offset);
    Offset delta = negOff.minus(region.toWord()).and(mask).toOffset();

    if (fillAlignmentGap && ALIGNMENT_VALUE != 0) {
      if ((MAX_ALIGNMENT - MIN_ALIGNMENT) == BYTES_IN_WORD) {
        // At most a single hole
        if (delta.toInt() == (BYTES_IN_WORD)) {
          region.store(Word.fromInt(ALIGNMENT_VALUE));
          region = region.plus(delta);
        return region;
        }
      } else {
        while (delta.toInt() >= (BYTES_IN_WORD)) {
          region.store(Word.fromInt(ALIGNMENT_VALUE));
          region = region.plus(BYTES_IN_WORD);
          delta = delta.minus(BYTES_IN_WORD);
        }
      }
    }

    return region.plus(delta);
  }

  /**
   * Fill the specified region with the alignment value.
   * 
   * @param start The start of the region.
   * @param end A pointer past the end of the region.
   */
  final public static void fillAlignmentGap(Address start, Address end)
      throws InlinePragma {
    if ((MAX_ALIGNMENT - MIN_ALIGNMENT) == BYTES_IN_INT) {
      // At most a single hole
      if (!end.diff(start).isZero()) {
        start.store(ALIGNMENT_VALUE);
      }
    } else {
      while (start.LT(end)) {
        start.store(ALIGNMENT_VALUE);
        start = start.plus(BYTES_IN_INT);
      }
    }
  }

  /**
   * Aligns up an allocation request. The allocation request accepts a
   * region, that must be at least particle aligned, an alignment
   * request (some power of two number of particles) and an offset (a
   * number of particles).
   *
   * @param region The region to align up.
   * @param alignment The requested alignment
   * @param offset The offset from the alignment 
   * @return The aligned up address.
   */
  final public static Address alignAllocation(Address region, int alignment,
                                             int offset) 
    throws InlinePragma {
    return alignAllocation(region, alignment, offset, MIN_ALIGNMENT, true);
  }

  /**
   * Aligns up an allocation request. The allocation request accepts a
   * region, that must be at least particle aligned, an alignment
   * request (some power of two number of particles) and an offset (a
   * number of particles).
   *
   * @param region The region to align up.
   * @param alignment The requested alignment
   * @param offset The offset from the alignment 
   * @return The aligned up address.
   */
  final public static Address alignAllocationNoFill(Address region, int alignment, 
                                             int offset) 
    throws InlinePragma {
    return alignAllocation(region, alignment, offset, MIN_ALIGNMENT, false);
  }

  /**
   * This method calculates the minimum size that will guarantee the allocation
   * of a specified number of bytes at the specified alignment.
   * 
   * @param size The number of bytes (not aligned).
   * @param alignment The requested alignment (some factor of 2).
   */
  final public static int getMaximumAlignedSize(int size, int alignment)
      throws InlinePragma {
    return getMaximumAlignedSize(size, alignment, MIN_ALIGNMENT);
  }

  /**
   * This method calculates the minimum size that will guarantee the allocation
   * of a specified number of bytes at the specified alignment.
   * 
   * @param size The number of bytes (not aligned).
   * @param alignment The requested alignment (some factor of 2).
   * @param knownAlignment The known minimum alignment. Specifically for use in
   * allocators that enforce greater than particle alignment.
   */
  final public static int getMaximumAlignedSize(int size, int alignment,
                                                int knownAlignment) 
    throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(knownAlignment >= MIN_ALIGNMENT);
    if (MAX_ALIGNMENT <= MIN_ALIGNMENT || alignment <= knownAlignment) {
      return size;
    } else {
      return size + alignment - knownAlignment;
    }
  }

  /**
   * Single slow path allocation attempt. This is called by allocSlow.
   * 
   * @param bytes The size of the allocation request
   * @param alignment The required alignment
   * @param offset The alignment offset
   * @param inGC Is this request occuring during GC
   * @return The start address of the region, or zero if allocation fails
   */
  abstract protected Address allocSlowOnce(int bytes, int alignment,
      int offset, boolean inGC);

  /**
   * <b>Out-of-line</b> slow path allocation. This method forces slow path
   * allocation to be out of line (typically desirable, but not when the
   * calling context is already explicitly out-of-line).
   * 
   * @param bytes The size of the allocation request
   * @param alignment The required alignment
   * @param offset The alignment offset
   * @param inGC Is this request occuring during GC
   * @return The start address of the region, or zero if allocation fails
   */
  final public Address allocSlow(int bytes, int alignment, int offset, boolean inGC) throws NoInlinePragma {
    return allocSlowInline(bytes, alignment, offset, inGC);
  }
  
  /**
   * <b>Inline</b> slow path allocation. This method attempts allocSlowOnce
   * several times, and allows collection to occur, and ensures that execution
   * safely resumes by taking care of potential thread/mutator context affinity 
   * changes. All allocators should use this as the trampoline for slow 
   * path allocation.
   * 
   * @param bytes The size of the allocation request
   * @param alignment The required alignment
   * @param offset The alignment offset
   * @param inGC Is this request occuring during GC
   * @return The start address of the region, or zero if allocation fails
   */
  final public Address allocSlowInline(int bytes, int alignment, int offset,
      boolean inGC) throws InlinePragma {
    int gcCountStart = Stats.gcCount();
    Allocator current = this;
    for (int i = 0; i < MAX_RETRY; i++) {
      Address result = current.allocSlowOnce(bytes, alignment, offset, inGC);
      if (!result.isZero())
        return result;
      if (!inGC) {
        /* This is in case a GC occurs, and our mutator context is stale.
         * In some VMs the scheduler can change the affinity between the
         * current thread and the mutator context. This is possible for
         * VMs that dynamically multiplex Java threads onto multiple mutator 
         * contexts, */
	current = VM.activePlan.mutator().getOwnAllocator(current);
    }
    }
    Log.write("GC Warning: Possible VM range imbalance - Allocator.allocSlow failed on request of ");
    Log.write(bytes);
    Log.write(" on space ");
    Log.writeln(Plan.getSpaceNameFromAllocatorAnyLocal(this));
    Log.write("gcCountStart = ");
    Log.writeln(gcCountStart);
    Log.write("gcCount (now) = ");
    Log.writeln(Stats.gcCount());
    Space.printUsageMB();
    VM.assertions.dumpStack();
    VM.assertions.failWithOutOfMemoryError();
    /* NOTREACHED */
    return Address.zero();
  }
}
