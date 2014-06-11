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

import org.mmtk.utility.HeaderByte;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements unsynchronized (local) elements of an
 * immortal space. Allocation is via the bump pointer
 * (@see BumpPointer).
 *
 * @see BumpPointer
 * @see ImmortalSpace
 */
@Uninterruptible public final class ImmortalLocal extends BumpPointer {

  private byte allocColor = 0;

  /**
   * Constructor
   *
   * @param space The space to bump point into.
   */
  public ImmortalLocal(ImmortalSpace space) {
    super(space, true);
  }

  @Inline
  private void updateAllocColor() {
    allocColor = (byte) ((ImmortalSpace) space).getMarkState().toInt();
  }
  
  public void init() {
    updateAllocColor();
  }

  public void prepare() {
    updateAllocColor();
  }

  /**
   * Initialize the object header post-allocation.  We need to set the mark state
   * correctly and set the logged bit if necessary.
   *
   * @param object The newly allocated object instance whose header we are initializing
   */
  public void initializeHeader(ObjectReference object) {
    byte oldValue = VM.objectModel.readAvailableByte(object);
    byte newValue = (byte) ((oldValue & ImmortalSpace.GC_MARK_BIT_MASK) | allocColor);
    if (HeaderByte.NEEDS_UNLOGGED_BIT) newValue |= HeaderByte.UNLOGGED_BIT;
    VM.objectModel.writeAvailableByte(object, newValue);
  }
}
