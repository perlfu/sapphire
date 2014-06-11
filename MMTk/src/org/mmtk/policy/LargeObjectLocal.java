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
package org.mmtk.policy;

import org.mmtk.utility.alloc.LargeObjectAllocator;
import org.mmtk.utility.alloc.LinearScan;
import org.mmtk.utility.gcspy.drivers.TreadmillDriver;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Treadmill;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Each instance of this class is intended to provide fast,
 * unsynchronized access to a treadmill.  Therefore instances must not
 * be shared across truly concurrent threads (CPUs).  Rather, one or
 * more instances of this class should be bound to each CPU.  The
 * shared VMResource used by each instance is the point of global
 * synchronization, and synchronization only occurs at the granularity
 * of acquiring (and releasing) chunks of memory from the VMResource.<p>
 *
 * If there are C CPUs and T TreadmillSpaces, there must be C X T
 * instances of this class, one for each CPU, TreadmillSpace pair.
 */
@Uninterruptible
public final class LargeObjectLocal extends LargeObjectAllocator implements Constants {

  /****************************************************************************
   *
   * Class variables
   */

  /****************************************************************************
   *
   * Instance variables
   */
  private byte allocColor;

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
  public LargeObjectLocal(BaseLargeObjectSpace space) {
    super(space);
  }

  /****************************************************************************
   *
   * Allocation
   */
  /**
   * Perform any required initialization of the GC portion of the header.
   *
   * @param object the object ref to the storage to be initialized
   */
  @Inline
  public void initializeHeader(ObjectReference object) {
    ((LargeObjectSpace) space).initializeHeader(object, allocColor, true);
  }

  /****************************************************************************
   *
   * Collection
   */
  
  public void initThreadForOTFCollection() {
    prepareOTFCollection();
  }

  public void prepareOTFCollection() {
    allocColor = (byte) ((LargeObjectSpace) space).getMarkState().toInt();
  }
  
  /**
   * Prepare for a collection.  Clear the treadmill to-space head and
   * prepare the collector.  If paranoid, perform a sanity check.
   */
  public void prepare(boolean fullHeap) {
  }

  /**
   * Finish up after a collection.
   */
  public void release(boolean fullHeap) {
  }
  
  /****************************************************************************
   * 
   * Access methods
   */
  
  /**
   * Perform a linear scan through the objects in this large object space.
   *
   * @param scanner The scan object to delegate scanning to.
   */
  @Inline
  public void linearScan(LinearScan scanner) {
    Treadmill treadmill = ((LargeObjectSpace)space).getTreadmill();
    treadmill.linearScan(scanner);
  }

  /****************************************************************************
   *
   * Miscellaneous size-related methods
   */

  /**
   * Gather data for GCSpy from the nursery
   * @param event the gc event
   * @param losDriver the GCSpy space driver
   */
  public void gcspyGatherData(int event, TreadmillDriver losDriver) {
    // TODO: assumes single threaded
    // TODO: assumes non-explit LOS
    ((LargeObjectSpace)space).getTreadmill().gcspyGatherData(event, losDriver);
  }

  /**
   * Gather data for GCSpy for an older space
   * @param event the gc event
   * @param losDriver the GCSpy space driver
   * @param tospace gather from tospace?
   */
  public void gcspyGatherData(int event, TreadmillDriver losDriver, boolean tospace) {
    // TODO: assumes single threaded
    ((LargeObjectSpace)space).getTreadmill().gcspyGatherData(event, losDriver, tospace);
  }
}
