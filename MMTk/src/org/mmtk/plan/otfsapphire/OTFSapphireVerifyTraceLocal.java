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
package org.mmtk.plan.otfsapphire;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.utility.Log;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

@Uninterruptible
public class OTFSapphireVerifyTraceLocal extends TraceLocal {
  /**
   * Constructor
   */
  public OTFSapphireVerifyTraceLocal(Trace trace, boolean specialized) {
    super(-1, trace); // no specialized scan
  }

  /**
   * Constructor
   */
  public OTFSapphireVerifyTraceLocal(Trace trace) {
    this(trace, false);
  }

  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param object The object in question
   * @return True if <code>obj</code> is a live object.
   */
  @Override
  @Inline
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (OTFSapphire.inFromSpace(object)) {
      return OTFSapphire.fromSpace().isLive(object);
    } else if (OTFSapphire.inToSpace(object)) {
      return true;
    } else
      return super.isLive(object);
  }

  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) {
      if (!object.isNull()) {
        VM.assertions._assert(!OTFSapphire.inFromSpace(object));
        if (!OTFSapphire.inToSpace(object))
          VM.assertions._assert(super.isLive(object));
      }
    }
    return object;
  }

  /**
   * Will this object move from this point on, during the current trace ?
   *
   * @param object The object to query.
   * @return True if the object will not move.
   */
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    return true;
  }
}
