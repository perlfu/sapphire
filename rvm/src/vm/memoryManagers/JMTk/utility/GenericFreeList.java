/*
 * (C) Copyright Architecture and Language Implementation Laboratory,
 *     Department of Computer Science,
 *     University of Massachusetts at Amherst. 2001
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 * (C) Copyright IBM Corp. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
/**
 * This is a very simple, generic malloc-free allocator.  It works
 * abstractly, in "units", which the user may associate with some
 * other allocatable resource (e.g. heap blocks).  The user issues
 * requests for N units and the allocator returns the index of the
 * first of a contigious set of N units or fails, returning -1.  The
 * user frees the block of N units by calling <code>free()</code> with
 * the index of the first unit as the argument.<p>
 *
 * Properties/Constraints:<ul>
 *   <li> The allocator consumes one word per allocatable unit (plus
 *   a fixed overhead of about 128 words).</li>
 *   <li> The allocator can only deal with MAX_UNITS units (see below for
 *   the value).</li>
 * </ul>
 *
 * The basic data structure used by the algorithm is a large table,
 * with one word per allocatable unit.  Each word is used in a
 * number of different ways, some combination of "undefined" (32),
 * "free/used" (1), "multi/single" (1), "prev" (15), "next" (15) &
 * "size" (15) where field sizes in bits are in parenthesis.
 * <pre>
 *                      +-+-+-----------+-----------+
 *                      |f|m|    prev   | next/size |
 *                      +-+-+-----------+-----------+
 *
 *   - single free unit: "free", "single", "prev", "next"
 *   - single used unit: "used", "single"
 *   - contigious free units
 *     . first unit: "free", "multi", "prev", "next"
 *     . second unit: "free", "multi", "size"
 *     . last unit: "free", "multi", "size"
 *   - contigious used units
 *     . first unit: "used", "multi", "prev", "next"
 *     . second unit: "used", "multi", "size"
 *     . last unit: "used", "multi", "size"
 *   - any other unit: undefined
 *    
 *                      +-+-+-----------+-----------+
 *  top sentinel        |0|0|    tail   |   head    |  [-1]
 *                      +-+-+-----------+-----------+ 
 *                                    ....
 *           /--------  +-+-+-----------+-----------+
 *           |          |1|1|   prev    |   next    |  [j]
 *           |          +-+-+-----------+-----------+
 *           |          |1|1|           |   size    |  [j+1]
 *        free multi    +-+-+-----------+-----------+
 *        unit block    |              ...          |  ...
 *           |          +-+-+-----------+-----------+
 *           |          |1|1|           |   size    |
 *           >--------  +-+-+-----------+-----------+
 *  single free unit    |1|0|   prev    |   next    |
 *           >--------  +-+-+-----------+-----------+
 *  single used unit    |0|0|                       |
 *           >--------  +-+-+-----------------------+
 *           |          |0|1|                       |
 *           |          +-+-+-----------+-----------+
 *           |          |0|1|           |   size    |
 *        used multi    +-+-+-----------+-----------+
 *        unit block    |              ...          |
 *           |          +-+-+-----------+-----------+
 *           |          |0|1|           |   size    |
 *           \--------  +-+-+-----------+-----------+
 *                                    ....
 *                      +-+-+-----------------------+
 *  bottom sentinel     |0|0|                       |  [N]
 *                      +-+-+-----------------------+ 
 * </pre>
 * The sentinels serve as guards against out of range coalescing
 * because they both appear as "used" blocks and so will never
 * coalesce.  The top sentinel also serves as the head and tail of
 * the doubly linked list of free blocks.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 *
 */


final class GenericFreeList extends BaseGenericFreeList implements Constants, VM_Uninterruptible {
   public final static String Id = "$Id$";
 
  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //

  /**
   * Constructor
   */
  GenericFreeList(int units) {
    if (VM.VerifyAssertions) VM._assert(units <= MAX_UNITS);

    // allocate the data structure, including space for top & bottom sentinels
    table = new int[(units + 2)<<1];

    initializeHeap(units);
  }

  /**
   * Initialize a unit as a sentinel
   *
   * @param unit The unit to be initilized
   */
  protected void setSentinel(int unit) {
    setLoEntry(unit, SENTINEL_LO_INIT);
    setHiEntry(unit, SENTINEL_HI_INIT);
  }

  /**
   * Get the size of a lump of units
   *
   * @param unit The first unit in the lump of units
   * @return The size of the lump of units
   */
  protected int getSize(int unit)  {
    if ((getHiEntry(unit) & MULTI_MASK) == MULTI_MASK) 
      return (getHiEntry(unit + 1) & SIZE_MASK);
    else
      return 1;
  }

  /**
   * Set the size of lump of units
   *
   * @param unit The first unit in the lump of units
   * @param size The size of the lump of units
   */
  protected void setSize(int unit, int size) {
    if (size > 1) {
      setHiEntry(unit, getHiEntry(unit) | MULTI_MASK);
      setHiEntry(unit + 1, MULTI_MASK | size);
      setHiEntry(unit + size - 1, MULTI_MASK | size);
    } else 
      setHiEntry(unit, getHiEntry(unit) & ~MULTI_MASK);
  }

  /**
   * Establish whether a lump of units is free
   *
   * @param unit The first or last unit in the lump
   * @return True if the lump is free
   */
  protected boolean getFree(int unit) {
    return ((getLoEntry(unit) & FREE_MASK) == FREE_MASK);
  }
  
  /**
   * Set the "free" flag for a lump of units (both the first and last
   * units in the lump are set.
   *
   * @param unit The first unit in the lump
   * @param isFree True if the lump is to be marked as free
   */
  protected void setFree(int unit, boolean isFree) {
    int size;
    if (isFree) {
      setLoEntry(unit, getLoEntry(unit) | FREE_MASK);
      if ((size = getSize(unit)) > 1)
	setLoEntry(unit + size - 1, getLoEntry(unit + size - 1) | FREE_MASK);
    } else {
      setLoEntry(unit, getLoEntry(unit) & ~FREE_MASK);
      if ((size = getSize(unit)) > 1)
	setLoEntry(unit + size - 1, getLoEntry(unit + size - 1) & ~FREE_MASK);
    }
  }
  
  /**
   * Get the next lump in the doubly linked free list
   *
   * @param unit The index of the first unit in the current lump
   * @return The index of the first unit of the next lump of units in the list
   */
  protected int getNext(int unit) {
    int next = getHiEntry(unit) & NEXT_MASK;
    return (next <= MAX_UNITS) ? next : HEAD;
  }

  /**
   * Set the next lump in the doubly linked free list
   *
   * @param unit The index of the first unit in the lump to be set
   * @param next The value to be set.
   */
  protected void setNext(int unit, int next) {
    if (VM.VerifyAssertions) VM._assert((next >= HEAD) && (next <= MAX_UNITS));
    int oldValue = getHiEntry(unit);
    int newValue = (next == HEAD) ? (oldValue | NEXT_MASK) : ((oldValue & ~NEXT_MASK) | next);
    setHiEntry(unit, newValue);
  }

  /**
   * Get the previous lump in the doubly linked free list
   *
   * @param unit The index of the first unit in the current lump
   * @return The index of the first unit of the previous lump of units
   * in the list
   */
  protected int getPrev(int unit) {
    int prev = getLoEntry(unit) & PREV_MASK;
    return (prev <= MAX_UNITS) ? prev : HEAD;
  }

  /**
   * Set the previous lump in the doubly linked free list
   *
   * @param unit The index of the first unit in the lump to be set
   * @param prev The value to be set.
   */
  protected void setPrev(int unit, int prev) {
    if (VM.VerifyAssertions) VM._assert((prev >= HEAD) && (prev <= MAX_UNITS));
    if (prev == HEAD)
      setLoEntry(unit, (getLoEntry(unit) | PREV_MASK));
    else
      setLoEntry(unit, (getLoEntry(unit) & ~PREV_MASK) | prev);
  }

  /**
   * Get the lump to the "left" of the current lump (i.e. "above" it)
   *
   * @param unit The index of the first unit in the lump in question
   * @return The index of the first unit in the lump to the
   * "left"/"above" the lump in question.
   */
  protected int getLeft(int unit) {
    if ((getHiEntry(unit - 1) & MULTI_MASK) == MULTI_MASK)
      return unit - (getHiEntry(unit - 1) & SIZE_MASK);
    else
      return unit - 1;
  }
  
  /**
   * Get the contents of an entry
   * 
   * @param unit The index of the unit
   * @return The contents of the unit
   */
  private int getLoEntry(int unit) {
    return table[(unit + 1)<<1];
  }
  private int getHiEntry(int unit) {
    return table[((unit + 1)<<1) + 1];
  }

  /**
   * Set the contents of an entry
   * 
   * @param unit The index of the unit
   * @param value The contents of the unit
   */
  private void setLoEntry(int unit, int value) {
    table[(unit + 1)<<1] = value;
  }
  private void setHiEntry(int unit, int value) {
    table[((unit + 1)<<1)+1] = value;
  }

  private static final int TOTAL_BITS = 32;
  private static final int UNIT_BITS = (TOTAL_BITS - 1);
  private static final int MAX_UNITS = ((1<<UNIT_BITS) - 1) - 1;
  private static final int NEXT_MASK = (1<<UNIT_BITS) - 1;
  private static final int PREV_MASK = (1<<UNIT_BITS) - 1;
  private static final int FREE_MASK = 1<<(TOTAL_BITS-1);
  private static final int MULTI_MASK = 1<<(TOTAL_BITS-1);
  private static final int SIZE_MASK = (1<<UNIT_BITS) - 1;
  
  // want the sentinels to be "used" & "single", and want first
  // sentinel to initially point to itself.
  private static final int SENTINEL_LO_INIT = PREV_MASK;
  private static final int SENTINEL_HI_INIT = NEXT_MASK;

  private int[] table;
}
