/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.utility.deque;

import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of
 * object references
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @version $Revision$
 * @date $Date$
 */
public class ObjectReferenceDeque extends LocalDeque 
  implements Constants, Uninterruptible {

  /****************************************************************************
   * 
   * Public instance methods
   */
  public final String name;

  /**
   * Constructor
   * 
   * @param queue The shared queue to which this queue will append
   * its buffers (when full or flushed) and from which it will aquire new
   * buffers when it has exhausted its own.
   */
  public ObjectReferenceDeque(String n, SharedDeque queue) {
    super(queue);
    name = n;
  }

  /**
   * Insert an object into the object queue.
   * 
   * @param object the object to be inserted into the object queue
   */
  public final void insert(ObjectReference object) throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!object.isNull());
    checkTailInsert(1);
    uncheckedTailInsert(object.toAddress());
  }

  /**
   * Push an object onto the object queue.
   * 
   * @param object the object to be pushed onto the object queue
   */
  public final void push(ObjectReference object) throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!object.isNull());
    checkHeadInsert(1);
    uncheckedHeadInsert(object.toAddress());
  }

  /**
   * Push an object onto the object queue, force this out of line
   * ("OOL"), in some circumstnaces it is too expensive to have the
   * push inlined, so this call is made.
   * 
   * @param object the object to be pushed onto the object queue
   */
  public final void pushOOL(ObjectReference object) throws NoInlinePragma {
    push(object);
  }

  /**
   * Pop an object from the object queue, return zero if the queue
   * is empty.
   * 
   * @return The next object in the object queue, or zero if the
   * queue is empty
   */
  public final ObjectReference pop() throws InlinePragma {
    if (checkDequeue(1)) {
      return uncheckedDequeue().toObjectReference();
    } else {
      return ObjectReference.nullReference();
    }
  }

  public final boolean isEmpty() throws InlinePragma {
    return !checkDequeue(1);
  }

  public final boolean isNonEmpty() throws InlinePragma {
    return checkDequeue(1);
  }

}
