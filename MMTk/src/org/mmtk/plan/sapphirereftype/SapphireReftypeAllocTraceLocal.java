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
package org.mmtk.plan.sapphirereftype;

import org.mmtk.plan.Trace;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

@Uninterruptible
public class SapphireReftypeAllocTraceLocal extends org.mmtk.plan.otfsapphire.OTFSapphireAllocTraceLocal {
  /**
   * Constructor
   */
  public SapphireReftypeAllocTraceLocal(Trace trace) {
    super(trace, false);
  }

  /**
   * This method is the core method during the trace of the object graph.
   * The role of this method is to:
   *
   * 1. Ensure the traced object is not collected.
   * 2. If this is the first visit to the object enqueue it to be scanned.
   * 3. Return the forwarded reference to the object.
   *
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    if (SapphireReftype.REFERENCE_REPORT_TRACE_OBJS) {
      if (!isLive(object)) SapphireReftype.traceCount.increment();
    }
    return super.traceObject(object);
  }
}
