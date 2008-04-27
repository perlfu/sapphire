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
package org.mmtk.policy.immix;

import org.mmtk.plan.Plan;
import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;

@Uninterruptible
public final class ChunkList implements Constants {
  private static final int LOG_CHUNK_MAP_PAGES = 0;
  private static final int CHUNK_MAP_ENTRIES = (BYTES_IN_PAGE<<LOG_CHUNK_MAP_PAGES)>>LOG_BYTES_IN_ADDRESS;
  private static final int CHUNK_MAP_ARRAY_SIZE = 16;
  private AddressArray chunkMap =  AddressArray.create(CHUNK_MAP_ARRAY_SIZE);
  private int chunkMapLimit = -1;
  private int chunkMapCursor = -1;

  void reset() {
    chunkMapLimit = chunkMapCursor;
  }

  public Address getHeadChunk() {
    if (chunkMapLimit < 0)
      return Address.zero();
    else
      return getMapAddress(0).loadAddress();
  }

  public Address getTailChunk() {
    if (chunkMapLimit < 0)
      return Address.zero();
    else
      return getMapAddress(chunkMapLimit).loadAddress();
  }

  void addNewChunkToMap(Address chunk) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Chunk.isAligned(chunk));
    chunkMapCursor++;
    int index = getChunkIndex(chunkMapCursor);
    int map = getChunkMap(chunkMapCursor);
    if (map >= CHUNK_MAP_ARRAY_SIZE) {
      Space.printUsageMB();
      VM.assertions.fail("Overflow of chunk map!");
    }
    if (chunkMap.get(map).isZero()) {
      Address tmp = Plan.metaDataSpace.acquire(1<<LOG_CHUNK_MAP_PAGES);
      if (tmp.isZero()) {
        Space.printUsageMB();
        VM.assertions.fail("Failed to allocate space for chunk map.  Is metadata virtual memory exhausted?");
      }
      chunkMap.set(map, tmp);
    }
    Address entry = chunkMap.get(map).plus(index<<LOG_BYTES_IN_ADDRESS);
    entry.store(chunk);
    Chunk.setMap(chunk, chunkMapCursor);
    if (VM.VERIFY_ASSERTIONS) checkMap();
    }

  void removeChunkFromMap(Address chunk) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Chunk.isAligned(chunk));
    int entry = Chunk.getMap(chunk);
    getMapAddress(entry).store(Address.zero());  // zero it it
    Chunk.setMap(chunk, -entry);
    if (VM.VERIFY_ASSERTIONS) checkMap();
  }

  private int getChunkIndex(int entry) { return entry & (CHUNK_MAP_ENTRIES - 1);}
  private int getChunkMap(int entry) {return entry & ~(CHUNK_MAP_ENTRIES - 1);}

  private Address getMapAddress(int entry) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(entry >= 0);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(entry <= chunkMapCursor);
    int index = getChunkIndex(entry);
    int map = getChunkMap(entry);
    return chunkMap.get(map).plus(index<<LOG_BYTES_IN_ADDRESS);
  }

  public Address nextChunk(Address chunk) {
    return nextChunk(chunk, chunk);
  }

  public Address nextChunk(Address chunk, Address limit) {
    return nextChunk(chunk, Chunk.getMap(limit), 1);
  }

  public Address nextChunk(Address chunk, int limit) {
    return nextChunk(chunk, limit, 1);
  }

  public Address nextChunk(Address chunk, int limit, int stride) {
    if (VM.VERIFY_ASSERTIONS) checkMap();
    return nextChunk(Chunk.getMap(chunk), limit, stride);
  }

  public Address nextChunk(int entry, int limit, int stride) {
    if (VM.VERIFY_ASSERTIONS) checkMap();
    Address chunk;
    do {
      entry += stride;
      if (entry > chunkMapLimit) { entry = entry % stride; }
      chunk = getMapAddress(entry).loadAddress();
    } while (chunk.isZero() && entry != limit);
    return entry == limit ? Address.zero() : chunk;
  }

  public Address firstChunk(int ordinal, int stride) {
    if (ordinal > chunkMapCursor) return Address.zero();
    if (VM.VERIFY_ASSERTIONS) checkMap();
    Address chunk = getMapAddress(ordinal).loadAddress();
    return chunk.isZero() ? nextChunk(ordinal, ordinal, stride) : chunk;
  }

  private void checkMap() {
    VM.assertions._assert(chunkMapLimit <= chunkMapCursor);
    for (int entry = 0; entry <= chunkMapCursor; entry++) {
      Address chunk = getMapAddress(entry).loadAddress();
      if (!chunk.isZero())
        VM.assertions._assert(Chunk.getMap(chunk) == entry);
    }
  }

  public void consolidateMap() {
    int oldCursor = 0;
    int newCursor = -1;
    while (oldCursor <= chunkMapCursor) {
      Address chunk = getMapAddress(oldCursor).loadAddress();
      if (!chunk.isZero()) {
        getMapAddress(++newCursor).store(chunk);
        Chunk.setMap(chunk, newCursor);
      }
      oldCursor++;
    }
    chunkMapCursor = newCursor;
    chunkMapLimit = newCursor;
    if (VM.VERIFY_ASSERTIONS) checkMap();
  }
}
