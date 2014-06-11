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
package org.jikesrvm.mm.mmtk;

import org.vmmagic.pragma.Uninterruptible;
import org.jikesrvm.scheduler.TestThread;

/**
 * Debugger support for the MMTk harness
 */
@Uninterruptible
public final class Debug extends org.mmtk.vm.Debug {
  private TestThread thread = null;
  private boolean useThread = false;
  
  /**
   * Enable/disable MMTk debugger support
   */
  @Override
  public boolean isEnabled() {
    return true;
  }

  public void setTestThread(TestThread testThread) {
    thread = testThread;
    useThread = (thread != null);
  }
  
  /**
   * A global GC collection phase
   * @param phaseId The phase ID
   * @param before true at the start of the phase, false at the end
   */
  public void globalPhase(short phaseId, boolean before) {
    if (useThread) thread.globalPhase(phaseId, before);
  }

  /**
   * A special global GC collection phase
   * @param phaseId The phase ID
   * @param before true at the start of the phase, false at the end
   */
  public void specialGlobalPhase(short phaseId, boolean before) {
    if (useThread) thread.specialGlobalPhase(phaseId, before);
  }

  /**
   * A per-collector GC collection phase
   * @param phaseId The phase ID
   * @param ordinal The collector ID (within this collection)
   * @param before true at the start of the phase, false at the end
   */
  public void collectorPhase(short phaseId, int ordinal, boolean before) {
    if (useThread) thread.collectorPhase(phaseId, ordinal, before);
  }

  /**
   * A per-mutator GC collection phase
   * @param phaseId The phase ID
   * @param ordinal The mutator ID
   * @param before true at the start of the phase, false at the end
   */
  public void mutatorPhase(short phaseId, int ordinal, boolean before) {
    if (useThread) thread.mutatorPhase(phaseId, ordinal, before);
  }
  
  /**
   * A global request for on-the-fly per-mutator GC collection phase
   * @param phaseId The phase ID
   * @param before true at the start of the phase, false at the end
   */
  public void requestedOnTheFlyMutatorPhase(short phaseId, boolean before) {
    if (useThread) thread.requestedOnTheFlyMutatorPhase(phaseId, before);
  }
  
  /**
   * An on-the-fly per-mutator GC collection phase
   * @param phaseId The phase ID
   * @param ordinal The mutator ID
   * @param before true at the start of the phase, false at the end
   */
  public void onTheFlyMutatorPhase(short phaseId, int ordinal, boolean before) { 
    if (useThread) thread.onTheFlyMutatorPhase(phaseId, ordinal, before);
  }
}
