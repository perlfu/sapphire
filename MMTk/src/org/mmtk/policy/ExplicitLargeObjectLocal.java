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
package org.mmtk.policy;

import org.mmtk.utility.alloc.LargeObjectAllocator;
import org.mmtk.utility.Constants;
import org.mmtk.utility.DoublyLinkedList;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class is intended to provide fast,
 * unsynchronized access to explicitly collected objects.  Therefore
 * instances must not be shared across truely concurrent threads
 * (CPUs).  Rather, one or more instances of this class should be
 * bound to each CPU.  The shared VMResource used by each instance is
 * the point of global synchronization, and synchronization only
 * occurs at the granularity of aquiring (and releasing) chunks of
 * memory from the VMResource.
 *
 * If there are C CPUs and T LargeObjectSpaces, there must be C X T
 * instances of this class, one for each CPU, LargeObjectSpace pair.
 */
@Uninterruptible public final class ExplicitLargeObjectLocal extends LargeObjectAllocator
  implements Constants {

  /****************************************************************************
   *
   * Class variables
   */

  /****************************************************************************
   *
   * Instance variables
   */

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param space The large object space to which this thread instance
   * is bound.
   */
  public ExplicitLargeObjectLocal(LargeObjectSpace space) {
    super(space);
    this.space = space;
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
  @Inline
  protected void postAlloc(Address cell) {
    space.getCells().add(DoublyLinkedList.payloadToNode(cell));
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Prepare for a collection. Nothing to be done here
   */
  public void prepare() {}

  /**
   * Finish up after a collection. Nothing to be done
   */
  public void release() { }

  /**
   * Free an object
   *
   * @param space The space the object is allocated in.
   * @param object The object to be freed.
   */
  @Inline
  public static void free(LargeObjectSpace space, ObjectReference object) {
    Address cell = getSuperPage(VM.objectModel.refToAddress(object));
    space.getCells().remove(cell);
    space.release(cell);
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
  @Inline
  protected int superPageHeaderSize() {
    return DoublyLinkedList.headerSize();
  }

  /**
   * Return the size of the per-cell header for cells of a given class
   * size.
   *
   * @return The size of the per-cell header for cells of a given class
   * size.
   */
  @Inline
  protected int cellHeaderSize() { return 0; }
}
