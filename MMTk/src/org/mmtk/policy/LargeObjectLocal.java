/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.policy;

import org.mmtk.policy.LargeObjectSpace;
import org.mmtk.utility.alloc.LargeObjectAllocator;
import org.mmtk.utility.Treadmill;
import org.mmtk.utility.gcspy.drivers.TreadmillDriver;
import org.mmtk.vm.VM;
import org.mmtk.utility.Constants;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class is intended to provide fast,
 * unsynchronized access to a treadmill.  Therefore instances must not
 * be shared across truely concurrent threads (CPUs).  Rather, one or
 * more instances of this class should be bound to each CPU.  The
 * shared VMResource used by each instance is the point of global
 * synchronization, and synchronization only occurs at the granularity
 * of aquiring (and releasing) chunks of memory from the VMResource.
 * 
 * If there are C CPUs and T TreadmillSpaces, there must be C X T
 * instances of this class, one for each CPU, TreadmillSpace pair.
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @version $Revision$
 * @date $Date$
 */
public final class LargeObjectLocal extends LargeObjectAllocator
  implements Constants, Uninterruptible {

  /****************************************************************************
   * 
   * Class variables
   */

  /****************************************************************************
   * 
   * Instance variables
   */
  private final Treadmill treadmill; // per-processor

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   * 
   * @param space The treadmill space to which this thread instance is
   * bound.
   */
  public LargeObjectLocal(LargeObjectSpace space) {
    super(space);
    this.space = space;
    treadmill = new Treadmill(LOG_BYTES_IN_PAGE, true);
  }

  /****************************************************************************
   * 
   * Allocation
   */

  /**
   *  This is called each time a cell is alloced (i.e. if a cell is
   *  reused, this will be called each time it is reused in the
   *  lifetime of the cell, by contrast to initializeCell, which is
   *  called exactly once.).
   * 
   * @param cell The newly allocated cell
   */
  protected final void postAlloc (Address cell) 
    throws InlinePragma {
    treadmill.addToTreadmill(Treadmill.payloadToNode(cell));
  };

  /****************************************************************************
   * 
   * Collection
   */

  /**
   * Prepare for a collection.  Clear the treadmill to-space head and
   * prepare the collector.  If paranoid, perform a sanity check.
   */
   public final void prepare(boolean fullHeap) {                               
     if (fullHeap) {                                                           
       if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(treadmill.fromSpaceEmpty());
       treadmill.flip();                                                       
     }
  }

  /**
   * Finish up after a collection.
   */
  public void release(boolean fullHeap) {
    // sweep the large objects
    sweepLargePages(true);                // sweep the nursery
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(treadmill.nurseryEmpty());   
    if (fullHeap) sweepLargePages(false); // sweep the mature space
  }

  /**
   * Sweep through the large pages, releasing all superpages on the
   * "from space" treadmill.
   */
  private final void sweepLargePages(boolean sweepNursery) {
    while (true) {
      Address cell = treadmill.pop(sweepNursery);
      if (cell.isZero()) break;
      free(cell);
    }
    if (VM.VERIFY_ASSERTIONS) {
      if (sweepNursery)                                                      
        VM.assertions._assert(treadmill.nurseryEmpty());
      else                                                                   
        VM.assertions._assert(treadmill.fromSpaceEmpty());
    }                                                                       
  }


  /****************************************************************************
   * 
   * Miscellaneous size-related methods
   */

  /**
   * Return the size of the per-superpage header required by this
   * system.  In this case it is just the underlying superpage header
   * size.
   * 
   * @return The size of the per-superpage header required by this
   * system.
   */
  protected final int superPageHeaderSize()
    throws InlinePragma {
    return Treadmill.headerSize();
  }

  /**
   * Return the size of the per-cell header for cells of a given class
   * size.
   * 
   * @return The size of the per-cell header for cells of a given class
   * size.
   */
  protected final int cellHeaderSize()
    throws InlinePragma {
    return 0;
  }

  /**
   * Gather data for GCSpy from the nursery
   * @param event the gc event
   * @param losDriver the GCSpy space driver
   */
  public void gcspyGatherData(int event, TreadmillDriver losDriver) {
    treadmill.gcspyGatherData(event, losDriver);
  }
  
  /**
   * Gather data for GCSpy for an older space
   * @param event the gc event
   * @param losDriver the GCSpy space driver
   * @param tospace gather from tospace?
   */
  public void gcspyGatherData(int event, TreadmillDriver losDriver, boolean tospace) {
    treadmill.gcspyGatherData(event, losDriver, tospace);
  }
}
