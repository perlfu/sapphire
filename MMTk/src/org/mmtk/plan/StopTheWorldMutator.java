/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan;

import org.mmtk.utility.Log;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class (and its sub-classes) implement <i>per-mutator thread</i>
 * behavior and state.
 *
 * MMTk assumes that the VM instantiates instances of MutatorContext
 * in thread local storage (TLS) for each application thread. Accesses
 * to this state are therefore assumed to be low-cost during mutator
 * time.<p>
 *
 * @see MutatorContext
 * @see SimplePhase#delegatePhase
 */
@Uninterruptible public abstract class StopTheWorldMutator extends MutatorContext {

  /****************************************************************************
   *
   * Collection.
   */

  /**
   * Perform a per-mutator collection phase.   This is executed by
   * one collector thread on behalf of a mutator thread.
   *
   * @see SimplePhase#delegatePhase
   *
   * @param phaseId The unique phase identifier
   * @param primary Should this thread be used to execute any single-threaded
   * local operations?
   */
  @Inline
  public void collectionPhase(int phaseId, boolean primary) {

    if (phaseId == StopTheWorld.INITIATE_MUTATOR) {
      VM.collection.prepareMutator(this);
      return;
    }

    if (phaseId == StopTheWorld.PREPARE_MUTATOR) {
      los.prepare(true);
      plos.prepare(true);
      VM.memory.collectorPrepareVMSpace();
      return;
    }

    if (phaseId == StopTheWorld.RELEASE_MUTATOR) {
      los.release(true);
      plos.release(true);
      VM.memory.collectorReleaseVMSpace();
      return;
    }

    Log.write("Per-mutator phase \""); Phase.getPhase(phaseId).logPhase();
    Log.writeln("\" not handled.");
    VM.assertions.fail("Per-mutator phase not handled!");
  }
}
