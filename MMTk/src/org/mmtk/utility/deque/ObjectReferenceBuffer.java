/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2006
 */
package org.mmtk.utility.deque;

import org.mmtk.plan.TraceStep;
import org.mmtk.utility.Constants;
import org.mmtk.utility.scan.Scan;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class is a combination of a Deque and a TraceStep, designed to include
 * intelligent processing of child references as objects are scanned.
 * 
 * @see org.mmtk.plan.TraceStep
 * 
 * $Id: $
 * 
 * @author Daniel Frampton
 * @version $Revision: 1.7 $
 * @date $Date: 2006/06/21 07:38:14 $
 */
public abstract class ObjectReferenceBuffer extends TraceStep implements Constants, Uninterruptible {
  /****************************************************************************
   * 
   * Instance variables
   */
  private final ObjectReferenceDeque values;

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   * 
   * @param name The name of the underlying deque.
   * @param trace The shared deque that is used.
   */
  public ObjectReferenceBuffer(String name, SharedDeque queue) {
    values = new ObjectReferenceDeque(name, queue);
    queue.newConsumer();
  }  
  
  /**
   * Transitive step.
   * 
   * @param loc The location containing the object reference to process.
   */
  public final void traceObjectLocation(Address loc) throws InlinePragma {
    ObjectReference object = loc.loadObjectReference();
    process(object);
  }
  
  /**
   * This is the method that ensures 
   * 
   * @param object The object to process.
   */
  protected abstract void process(ObjectReference object);
  
  /**
   * Process each of the child objects for the passed object. 
   * 
   * @param object The object to process the children of.
   */
  public final void processChildren(ObjectReference object) throws InlinePragma {
    Scan.scanObject(this, object);
  }
  
  /**
   * Pushes an object onto the queue, forcing an inlined sequence.
   *  
   * @param object The object to push.
   */
  public final void push(ObjectReference object) throws InlinePragma {
    values.push(object);
  }

  /**
   * Pushes an object onto the queue, forcing an out of line sequence.
   *  
   * @param object The object to push.
   */
  public final void pushOOL(ObjectReference object) throws InlinePragma {
    values.pushOOL(object);
  }
  
  /**
   * Retrives an object.
   * 
   * @return The object retrieved.
   */
  public final ObjectReference pop() throws InlinePragma {
    return values.pop();
  }
  
  /**
   * Flushes all local state back to the shared queue.
   */
  public final void flushLocal() {
    values.flushLocal();
  }
}
