/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.heap;

import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.EmbeddedMetaData;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.GenericFreeList;
import org.mmtk.vm.VM;
import org.mmtk.utility.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class manages the allocation of pages for a space.  When a
 * page is requested by the space both a page budget and the use of
 * virtual address space are checked.  If the request for space can't
 * be satisfied (for either reason) a GC may be triggered.<p>
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @version $Revision$
 * @date $Date$
 */
public final class FreeListPageResource extends PageResource 
  implements Constants, Uninterruptible {

  private GenericFreeList freeList;
  private int highWaterMark = 0;
  private int metaDataPagesPerRegion = 0;

  /**
   * Constructor
   * 
   * Contiguous monotone resource. The address range is pre-defined at
   * initializtion time and is immutable.
   * 
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   * @param space The space to which this resource is attached
   * @param start The start of the address range allocated to this resource
   * @param bytes The size of the address rage allocated to this resource
   */
  public FreeListPageResource(int pageBudget, Space space, Address start,
      Extent bytes) {
    super(pageBudget, space, start);
    freeList = new GenericFreeList(Conversions.bytesToPages(bytes));
  }

  /**
   * Constructor
   * 
   * Contiguous monotone resource. The address range is pre-defined at
   * initializtion time and is immutable.
   * 
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   * @param space The space to which this resource is attached
   * @param start The start of the address range allocated to this resource
   * @param bytes The size of the address rage allocated to this resource
   * @param metaDataPagesPerRegion The number of pages of meta data
   * that are embedded in each region.
   */
  public FreeListPageResource(int pageBudget, Space space, Address start,
      Extent bytes, int metaDataPagesPerRegion) {
    super(pageBudget, space, start);
    this.metaDataPagesPerRegion = metaDataPagesPerRegion;
    freeList = new GenericFreeList(Conversions.bytesToPages(bytes), EmbeddedMetaData.PAGES_IN_REGION);
    reserveMetaData(space.getExtent());
  }

  /**
   * Constructor
   * 
   * Discontiguous monotone resource. The address range is <i>not</i>
   * pre-defined at initializtion time and is dynamically defined to
   * be some set of pages, according to demand and availability.
   * 
   * CURRENTLY UNIMPLEMENTED
   * 
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   * @param space The space to which this resource is attached
   */
  public FreeListPageResource(int pageBudget, Space space) {
    super(pageBudget, space);
    /* unimplemented */
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false); 
  }

  /**
   * Allocate <code>pages</code> pages from this resource.<p>
   * 
   * If the request can be satisfied, then ensure the pages are
   * mmpapped and zeroed before returning the address of the start of
   * the region.  If the request cannot be satisified, return zero.
   * 
   * @param pages The number of pages to be allocated.
   * @return The start of the first page if successful, zero on
   * failure.
   */
  protected final Address allocPages(int pages) throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(contiguous);
    lock();
    int pageOffset = freeList.alloc(pages);
    if (pageOffset == -1) {
      unlock();
      return Address.zero();
    } else {
      if (pageOffset > highWaterMark) {
        if ((pageOffset ^ highWaterMark) > EmbeddedMetaData.PAGES_IN_REGION) {
          int regions = 1 + ((pageOffset - highWaterMark) >> EmbeddedMetaData.LOG_PAGES_IN_REGION);
          int metapages = regions * metaDataPagesPerRegion;
          reserved += metapages;
          committed += metapages;
          highWaterMark = pageOffset;
        }
      }
      Address rtn = start.plus(Conversions.pagesToBytes(pageOffset));
      Mmapper.ensureMapped(rtn, pages);
      VM.memory.zero(rtn, Conversions.pagesToBytes(pages));
      unlock();
      return rtn;
    }
  }

  /**
   * Release a group of pages, associated with this page resource,
   * that were allocated together, optionally zeroing on release and
   * optionally memory protecting on release.
   * 
   * @param first The first page in the group of pages that were
   * allocated together.
   */
  public final void releasePages(Address first)
    throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(Conversions.isPageAligned(first));

    int pageOffset = Conversions.bytesToPages(first.diff(start));

    int pages = freeList.size(pageOffset);
    if (ZERO_ON_RELEASE)
      VM.memory.zero(first, Conversions.pagesToBytes(pages));
    /* Can't use protect here because of the chunk sizes involved!
    if (protectOnRelease.getValue())
      LazyMmapper.protect(first, pages);
     */
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(pages <= committed);

    lock();
    reserved -= pages;
    committed -= pages;
    freeList.free(pageOffset);
    unlock();
  }

  /**
   * Reserve virtual address space for meta-data.
   * 
   * @param extent The size of this space
   */
  private final void reserveMetaData(Extent extent) {
    highWaterMark = 0;
    if (metaDataPagesPerRegion > 0) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(start.toWord().rshl(EmbeddedMetaData.LOG_BYTES_IN_REGION).lsh(EmbeddedMetaData.LOG_BYTES_IN_REGION).toAddress().EQ(start));
      Extent size = extent.toWord().rshl(EmbeddedMetaData.LOG_BYTES_IN_REGION).lsh(EmbeddedMetaData.LOG_BYTES_IN_REGION).toExtent();
      Address cursor = start.plus(size);
      while (cursor.GT(start)) {
        cursor = cursor.minus(EmbeddedMetaData.BYTES_IN_REGION);
        int unit = cursor.diff(start).toWord().rshl(LOG_BYTES_IN_PAGE).toInt();
        int tmp = freeList.alloc(metaDataPagesPerRegion, unit);
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(tmp == unit);
      }
    }
  }

  /**
   * Adjust a page request to include metadata requirements, if any.  In the
   * case of a free-list allocator, meta-data is pre-allocated, so simply
   * return the un-adjusted request size.
   * 
   * @param pages The size of the pending allocation in pages
   * @return The (unadjusted) request size, since metadata is pre-allocated
   */
  public final int adjustForMetaData(int pages) { return pages; }
  
  /**
   * Adjust a page request to include metadata requirements, if any.  In the
   * case of a free-list allocator, meta-data is pre-allocated, so simply
   * return the un-adjusted request size.
   * 
   * @param pages The size of the pending allocation in pages
   * @param begin The start of the region of memory assigned to this pending
   * allocation
   * @return The (unadjusted) request size, since metadata is pre-allocated
   */
  public final int adjustForMetaData(int pages, Address begin) { return pages; }
  
  final int pages(Address first) throws InlinePragma {
    return freeList.size(Conversions.bytesToPages(first.diff(start)));
  }

  public Address getHighWater() {
    return start.plus(Extent.fromIntSignExtend(highWaterMark<<LOG_BYTES_IN_PAGE));
  }


  /**
   * Return the size of the super page
   * 
   * @param first the Address of the first word in the superpage
   * @return the size in bytes
   */
  public final Extent getSize(Address first)
    throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(Conversions.isPageAligned(first));

    int pageOffset = Conversions.bytesToPages(first.diff(start));
    int pages = freeList.size(pageOffset);
    return Conversions.pagesToBytes(pages);
  }
}
