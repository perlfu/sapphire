/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */

package org.mmtk.plan.refcount;

import org.mmtk.utility.Constants;
import org.mmtk.utility.deque.*;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements a dec-buffer for a reference counting collector
 * 
 * @see org.mmtk.plan.TraceStep
 * 
 *
 * @author Daniel Frampton
 */
@Uninterruptible public final class DecBuffer extends ObjectReferenceBuffer implements Constants {
  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   * 
   * @param queue The shared deque that is used.
   */
  public DecBuffer(SharedDeque queue) {
    super("dec", queue);
  }
  
  
  /**
   * This is the method that ensures 
   * 
   * @param object The object to process.
   */
  @Inline
  protected void process(ObjectReference object) { 
    if (RCBase.isRCObject(object)) {
      push(object);
    }
  }
}
