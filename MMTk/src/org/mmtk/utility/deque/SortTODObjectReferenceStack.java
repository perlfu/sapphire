/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 *     University of Massachusetts, Amherst. 2003
 */
package org.mmtk.utility.deque;

import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This supports <i>unsynchronized</i> pushing and popping of object
 * references.  In addition, this can sort the entries currently on
 * the shared stack.
 * 
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 */
@Uninterruptible public class SortTODObjectReferenceStack extends LocalDeque 
  implements Constants {

  /****************************************************************************
   * 
   * Public instance methods
   */

  /**
   * Constructor
   * 
   * @param queue The shared stack to which this stack will append
   * its buffers (when full or flushed) and from which it will aquire new
   * buffers when it has exhausted its own.
   */
  public SortTODObjectReferenceStack(SortTODSharedDeque queue) {
    super(queue);
  }

  /**
   * Sort the address on the shared stack.
   */
  public final void sort() {
    flushLocal();
    ((SortTODSharedDeque) queue).sort();
  }

  /**
   * Push an address onto the address stack.
   * 
   * @param object the object to be pushed onto the object queue
   */
  @Inline
  public final void push(ObjectReference object) { 
    Address addr = object.toAddress();
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!addr.isZero());
    checkHeadInsert(1);
    uncheckedHeadInsert(addr);
  }

  /**
   * Pop an address from the address stack, return zero if the stack
   * is empty.
   * 
   * @return The next address in the address stack, or zero if the
   * stack is empty
   */
  @Inline
  public final ObjectReference pop() { 
    if (checkDequeue(1)) {
      return uncheckedDequeue().toObjectReference();
    } else {
      return ObjectReference.nullReference();
    }
  }

  /**
   * Check if the (local and shared) stacks are empty.
   * 
   * @return True if there are no more entries on the local & shared stack,
   *         false otherwise.
   */
  @Inline
  public final boolean isEmpty() { 
    return !checkDequeue(1);
  }
}
