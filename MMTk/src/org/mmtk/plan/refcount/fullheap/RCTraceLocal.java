/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.refcount.fullheap;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implments the thread-local functionality for a transitive
 * closure over a mark-sweep space.
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public final class RCTraceLocal extends TraceLocal implements Uninterruptible {
  /**
   * Constructor
   */
  public RCTraceLocal(Trace trace) {
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
    if (RC.isRCObject(object)) {
      return RCHeader.isLiveRC(object);
    }
    return super.isLive(object);
  }

  /**
   * This method is the core method during the trace of the object graph.
   * The role of this method is to:
   * 
   * For reference counting, we simply try to enumerate all 'roots' 
   * into the reference counted spaces. 
   * 
   * @param object The object to be traced.
   * @param root is this object a root
   * @return The new reference to the same object instance.
   */
  public ObjectReference traceObject(ObjectReference object, boolean root)
      throws InlinePragma {
    if (root && RC.isRCObject(object)) {
      collector().reportRoot(object);
    }
    return object;
  }
  
  /**
   * This method is the core method during the trace of the object graph.
   * The role of this method is to:
   * 
   * For reference counting, we never do anything with non-root objects.
   * 
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  public ObjectReference traceObject(ObjectReference object)
      throws InlinePragma {
    return object;
  }
  
  
  /**
   * Miscellaneous
   */

  /**
   * Called during the trace to process any remsets. As there is a bug
   * in JikesRVM where write barriers occur during GC, this is 
   * necessary.
   */
  public void flushRememberedSets() {
    if (RC.WITH_COALESCING_RC) {
      collector().processModBuffer();
    }
  }

  /**
   * @return The current RC collector instace.
   */
  private static final RCCollector collector() throws InlinePragma {
    return (RCCollector)VM.activePlan.collector();
  }
  
  /**
   * Return true if an object is ready to move to the finalizable
   * queue, i.e. it has no regular references to it.
   *
   * @param object The object being queried.
   * @return <code>true</code> if the object has no regular references
   * to it.
   */
  public boolean readyToFinalize(ObjectReference object) {
    if (RC.isRCObject(object))
      return RCHeader.isFinalizable(object);
    return false;
  }
}
