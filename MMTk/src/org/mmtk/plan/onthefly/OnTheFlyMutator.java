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
package org.mmtk.plan.onthefly;

import org.mmtk.plan.*;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements <i>per-mutator thread</i> behavior
 * and state for a simple whole-heap concurrent collector.
 *
 * @see OnTheFly
 * @see OnTheFlyCollector
 * @see StopTheWorldMutator
 * @see MutatorContext
 */
@Uninterruptible
public class OnTheFlyMutator extends StopTheWorldMutator {

  /****************************************************************************
   * Instance fields
   */

  protected OnTheFlyMutator() {
  }

  @Override
  public void initMutator(int id) {
    super.initMutator(id);
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-mutator collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == OnTheFly.FLUSH_MUTATOR) {
      flush();
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   *
   * Barriers
   */

  /**
   * Read a reference type. In a concurrent collector this may
   * involve adding the referent to the marking queue.
   *
   * @param ref The referent being read.
   * @return The new referent.
   */
  /*
  inline getReferent(i, ref)
  {
    do
      ::(soft == NORMAL) ->
         if
           ::(reference[i] == true) ->
              ref = REFERENT(i)
           ::else ->
              ref = -1
         fi;
         break
      ::(soft == ABORT) ->
         if
           ::(reference[i] == true) ->
              if
                ::(mark[REFERENT(i)] == WHITE) ->
                   mark[REFERENT(i)] = GRAY;
                ::else -> skip
              fi;
              ref = REFERENT(i)
           ::else ->
              ref = -1
         fi;
         break
      ::(soft == TRACING) ->
         if
           ::(reference[i] == true) ->
              if
                ::(mark[REFERENT(i)] == WHITE) ->
  #ifdef GET_REFERENT_MARKS_REFERENT
                   CAS(soft, TRACING, ABORT, skip, skip)
  #else
                   CAS(soft, TRACING, NORMAL, skip, skip)
  #endif
                ::else ->
                   ref = REFERENT(i);
                   break
              fi
           ::else ->
              ref = -1;
              break
         fi
      ::(soft == CLEANING) ->
         if
           ::(reference[i] == true) ->
              assert(mark[REFERENT(i)] != RECLAIMED);
              assert(mark[REFERENT(i)] != GRAY);
              if
                ::(mark[REFERENT(i)] == WHITE) ->
                   ref = -1
                ::else ->
                   ref = REFERENT(i)
              fi;
           ::else ->
              ref = -1
         fi;
         break
    od;
    skip;
    d_step{
      getReferent_arg = i;
      getReferent_ret = ref
    };
    d_step{
      getReferent_arg = -1;
      getReferent_ret = -1
    }
  }
  */
  protected boolean isLive(ObjectReference obj) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }
  
  @Inline
  @Override
  public ObjectReference javaLangReferenceReadBarrier(ObjectReference ref, boolean isSoft) {
    if (isSoft) {
      while (true) {
        switch (OnTheFly.getSoftState()) {
        case OnTheFly.REF_TYPE_NORMAL:
          return ref;
        case OnTheFly.REF_TYPE_TRACING:
          if (ref.isNull())
            return ObjectReference.nullReference();
          if (isLive(ref))
            return ref;
          OnTheFly.testAndSetSoftState(OnTheFly.REF_TYPE_TRACING, OnTheFly.REF_TYPE_NORMAL);
          break;
        case OnTheFly.REF_TYPE_CLEARING:
          if (ref.isNull() || !isLive(ref))
            return ObjectReference.nullReference();
          return ref;
        }
      }
    } else { // weak reference
      while (true) {
        switch (OnTheFly.getWeakState()) {
        case OnTheFly.REF_TYPE_NORMAL:
          return ref;
        case OnTheFly.REF_TYPE_TRACING:
          if (ref.isNull())
            return ObjectReference.nullReference();
          if (isLive(ref))
            return ref;
          OnTheFly.testAndSetWeakState(OnTheFly.REF_TYPE_TRACING, OnTheFly.REF_TYPE_NORMAL);
          break;
        case OnTheFly.REF_TYPE_CLEARING:
          if (ref.isNull() || !isLive(ref))
            return ObjectReference.nullReference();
          return ref;
        }
      }
    }
    
  }
}
