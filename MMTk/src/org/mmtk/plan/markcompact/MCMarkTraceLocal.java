/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.markcompact;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.MarkCompactSpace;
import org.mmtk.policy.Space;
import org.mmtk.vm.Assert;
import org.mmtk.vm.ObjectModel;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implments the thread-local functionality for a transitive
 * closure over a mark-compact space during the initial marking phase.
 * 
 * $Id$
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public final class MCMarkTraceLocal extends TraceLocal implements Uninterruptible {
  /**
   * Constructor
   */
  public MCMarkTraceLocal(Trace trace) {
    super(trace);
  }

  /****************************************************************************
   * 
   * Externally visible Object processing and tracing
   */

  /**
   * Is the specified object live?
   * 
   * @param object The object.
   * @return True if the object is live.
   */
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(MC.MARK_COMPACT, object)) {
      return MC.mcSpace.isLive(object);
    }
    return super.isLive(object);
  }

  /**
   * This method is the core method during the trace of the object graph.
   * The role of this method is to:
   * 
   * 1. Ensure the traced object is not collected.
   * 2. If this is the first visit to the object enqueue it to be scanned.
   * 3. Return the forwarded reference to the object.
   * 
   * In this instance, we refer objects in the mark-sweep space to the
   * msSpace for tracing, and defer to the superclass for all others.
   * 
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  public ObjectReference traceObject(ObjectReference object)
      throws InlinePragma {
    if (object.isNull()) return object;
    if (Space.isInSpace(MC.MARK_COMPACT, object))
      return MC.mcSpace.traceMarkObject(this, object);
    return super.traceObject(object);
  }

  /**
   * Ensure that this object will not move for the rest of the GC.
   * 
   * @param object The object that must not move
   * @return The new object, guaranteed stable for the rest of the GC.
   */
  public ObjectReference precopyObject(ObjectReference object)
      throws InlinePragma {
    if (Space.isInSpace(MC.MARK_COMPACT, object)) {
      if (MarkCompactSpace.testAndMark(object)) {
        // TODO: If precopy returns many different objects, this will cause a leak.
        // Currently, Jikes RVM does not require many objects to be precopied.
        ObjectReference newObject = ObjectModel.copy(object, MC.ALLOC_IMMORTAL);
        MarkCompactSpace.setForwardingPointer(object, newObject);
        enqueue(newObject);
        return newObject;
      }
      // Somebody else got to it first
      while (MarkCompactSpace.getForwardingPointer(object).isNull());
      return MarkCompactSpace.getForwardingPointer(object);
    }
    return super.precopyObject(object);
  }

  /**
   * Will this object move from this point on, during the current trace ?
   * 
   * @param object The object to query.
   * @return True if the object will not move.
   */
  public boolean willNotMove(ObjectReference object) {
    // All objects in the MC space may move
    return !Space.isInSpace(MC.MARK_COMPACT, object);
  }

}
