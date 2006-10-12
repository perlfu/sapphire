/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Richard Jones, 2002-5
 * Computing Laboratory, University of Kent at Canterbury
 * All rights reserved.
 */
package org.mmtk.utility.gcspy;

import org.mmtk.utility.Log;
import org.mmtk.vm.gcspy.Util;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;



/**
 * This class is an abstraction of a contiguous region of a Space.  For
 * example, a semispace collector might choose to model the heap as a
 * single Space, but within that Space it could model each semispace by
 * a Subspace.<p>
 * Subspace provides a number of useful facilities to many drivers, and
 * is useful even if the Space comprises just a single contiguous region.<p>
 *
 * A subspace keeps track of the start and end address of the region,
 * the index of its first block, the size of the blocks in this space,
 * and the number of blocks in this subspace.
 *
 * $Id$
 * 
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class Subspace implements Uninterruptible {

  private Address start_;       // The Subspace spans the address range [start_, end_)
  private Address end_;
  private int firstIndex_;      // The index of the block in which start_ lies
  private int blockSize_;       // The block size
  private int blockNum_;        // The number of blocks in this space

  private static final boolean DEBUG = false;

  /**
   * Create a new subspace
   * 
   * @param start The address of the start of the subspace
   * @param end The address of the end of the subspace
   * @param firstIndex The index of the first block of the subspace
   * @param blockSize The size of blocks in this space
   * @param blockNum The number of blocks in this subspace
   */
  public Subspace (Address start,
                   Address end,
                   int firstIndex,
                   int blockSize,
                   int blockNum) {
    reset(start, end, firstIndex, blockSize, blockNum);
  }


  //------------------Methods to reset a subspace----------------------

  /**
   * Reset a subspace.
   *
   * @param start The address of the start of the subspace
   * @param end The address of the end of the subspace
   * @param firstIndex The index of the first block of the subspace
   * @param blockSize The size of blocks in this subspace
   * @param blockNum The number of blocks in this subspace
   */
  private void reset (Address start,
                      Address end,
                      int firstIndex,
                      int blockSize,
                      int blockNum) {
    //TODO sanity check on addresses and block size and number
    reset(start, end, firstIndex, blockNum);
    blockSize_ = blockSize;

    if (DEBUG) dump();
  }

  /**
   * Reset a new subspace
   * 
   * @param start The address of the start of the subspace
   * @param end The address of the end of the subspace
   * @param firstIndex The index of the first block of the subspace
   * @param blockNum The number of blocks in this subspace
   */
  public void reset (Address start,
                     Address end,
                     int firstIndex,
                     int blockNum) {
    start_ = start;
    end_ = end;
    firstIndex_ = firstIndex;
    blockNum_ = blockNum;
  }

  /**
   * Reset a new subspace.
   *
   * @param start The address of the start of the subspace
   * @param end The address of the end of the subspace
   * @param blockNum The number of blocks in this subspace
   */
  public void reset(Address start, Address end, int blockNum) {
    start_ = start;
    end_ = end;
    blockNum_ = blockNum;
  }

  /**
   * Reset a new subspace.
   *
   * @param firstIndex The index of the first block of the subspace
   * @param blockNum The number of blocks in this subspace
   */
  public void reset(int firstIndex, int blockNum) {
    firstIndex_ = firstIndex;
    blockNum_ = blockNum;
  }


  //----------------Facilities to query a subspace-----------------

  /**
   * Is an index in the range of this subspace?
   *
   * @param index The index of the block
   * @return true if this block lies in this subspace
   */
  public boolean indexInRange(int index) {
    return index >= firstIndex_ &&
           index < firstIndex_ + blockNum_;
  }

  /**
   * Is address in the range of this subspace?
   *
   * @param addr An address
   * @return true if this address is in a block in this subspace
   */
  public boolean addressInRange(Address addr) {
    return addr.GE(start_) && addr.LT(end_);
  }


  /**
   * Get the block index from an address
   *
   * @param addr The address
   * @return The index of the block holding this address
   */
  public int getIndex(Address addr) {
    if (DEBUG) {
      Log.write("start_ ", start_);
      Log.write(" end_ ", end_);
      Log.write(" blockSize_ ", blockSize_);
      Log.write(" firstIndex_ ", firstIndex_);
      Log.write(" + ", addr.diff(start_).toInt() / blockSize_); 
      Log.writeln(" addr ", addr);
    }
    return firstIndex_ + addr.diff(start_).toInt() / blockSize_;
  }

  /**
   * Get the address of start of block from its index
   *
   * @param index The index of the block
   * @return The address of the start of the block
   */
  public Address getAddress(int index) {
    return start_.plus(index - firstIndex_ * blockSize_);
  }

  //--------------Accessors-------------------------
  
  /**
   * Get the start of the subspace
   * @return The start of this subspace
   */
  public Address getStart() { return start_; }

  /**
   * Get the end of this subspace
   * @return The address of the end of this subspace
   */
  public Address getEnd() { return end_; }

  /**
   * Get the first index of subspace
   * @return the firstIndex of this subspace
   */
  public int getFirstIndex() { return firstIndex_; }

  /**
   * Get the blocksize for this subspace
   * @return The size of a tile
   */
  public int getBlockSize() { return blockSize_; }

  /**
   * Get the number of tiles in this subspace
   * @return The number of tiles in this subspace
   */
  public int getBlockNum() { return blockNum_; }

  /**
   * Calculate the space remaining in a block after this address
   *
   * @param addr the Address
   * @return the remainder
   */
  public int spaceRemaining(Address addr) {
    int nextIndex = getIndex(addr) + 1;
    Address nextTile = start_.plus(blockSize_ * (nextIndex - firstIndex_));
    return nextTile.diff(addr).toInt();
  }

  /**
   * Dump a representation of the subspace
   */
  private void dump() {
    Log.write("GCspy Subspace: ");
    Util.dumpRange(start_, end_);
    Log.writeln("\n  -- firstIndex=", firstIndex_);
    Log.writeln("  -- blockNum=", blockNum_);
  }
}


