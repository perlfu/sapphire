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
package org.jikesrvm.mm.mmtk;

import org.mmtk.utility.ReferenceProcessor;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Entrypoints;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.ref.PhantomReference;


/**
 * This class manages SoftReferences, WeakReferences, and
 * PhantomReferences. When a java/lang/ref/Reference object is created,
 * its address is added to a list of pending reference objects of the
 * appropriate type. An address is used so the reference will not stay
 * alive during gc if it isn't in use elsewhere the mutator. During
 * gc, the various lists are processed in the proper order to
 * determine if any Reference objects are ready to be enqueued or
 * whether referents that have died should be kept alive until the
 * Reference is explicitly cleared. The ReferenceProcessor class drives
 * this processing and uses this class, via VM_Interface, to scan
 * the lists of pending reference objects.
 *
 * Elsewhere, there is a distinguished Finalizer thread which enqueues
 * itself on the VM_Scheduler finalizerQueue.  At the end of gc, if
 * needed and if any Reference queue is not empty, the finalizer
 * thread is scheduled to be run when gc is completed. This thread
 * calls Reference.enqueue() to make the actual notifcation to the
 * user program that the object state has changed.
 *
 * Based on previous ReferenceProcessor.java, which was loosely based
 * on Finalizer.java
 *
 * As an optimization for generational collectors, each reference type
 * maintains two queues: a nursery queue and the main queue.
 */
@Uninterruptible public final class ReferenceGlue extends org.mmtk.vm.ReferenceGlue {
  /********************************************************************
   * Class fields
   */

  /**
   * <code>true</code> if the references are implemented as heap
   * objects (rather than in a table, for example).  In this context
   * references are soft, weak or phantom references.
   */
  protected boolean getReferencesAreObjects() { return true; }

 private static Lock lock = new Lock("ReferenceProcessor");

  private static final ReferenceGlue softReferenceProcessor =
    new ReferenceGlue(ReferenceProcessor.SOFT_SEMANTICS);
  private static final ReferenceGlue weakReferenceProcessor =
    new ReferenceGlue(ReferenceProcessor.WEAK_SEMANTICS);
  private static final ReferenceGlue phantomReferenceProcessor =
    new ReferenceGlue(ReferenceProcessor.PHANTOM_SEMANTICS);

  // Debug flags
  private static final boolean TRACE = false;

  /*************************************************************************
   * Instance fields
   */
  private Address waitingListHead = Address.zero();
  private Address nurseryListHead = Address.zero();
  private int countOnMatureList = 0;
  private int countOnWaitingList = 0;
  private int semantics;


  /**
   * Constructor
   */
  public ReferenceGlue() {}

  /**
   * Constructor
   * @param semantics Soft, phantom or weak
   */
  private ReferenceGlue(int semantics) {
    this.semantics = semantics;
  }

  /**
   * Add a reference to the list of references.
   *
   * (SJF: This method must NOT be inlined into an inlined allocation
   * sequence, since it contains a lock!)
   *
   * @param ref the reference to add
   */
  @Interruptible
  @NoInline
  private void addCandidate(Reference<?> ref) {
    if (TRACE) {
        Address referenceAsAddress = VM_Magic.objectAsAddress(ref);
        ObjectReference referent = getReferent(referenceAsAddress);
        VM.sysWriteln("Adding Reference: ", referenceAsAddress);
        VM.sysWriteln("       Referent:  ", referent);
    }

    lock.acquire();
    setNextReferenceAsAddress(VM_Magic.objectAsAddress(ref), nurseryListHead);
    nurseryListHead = VM_Magic.objectAsAddress(ref);
    countOnWaitingList += 1;
    lock.release();
  }

  /**
   * Scan through all references and forward. Only called when references
   * are objects.
   */
  public void forward() {

    if (TRACE) {
      VM.sysWrite("Starting ReferenceProcessor.traverse(");
      VM.sysWrite(ReferenceProcessor.semanticStrings[semantics]);
      VM.sysWriteln(")");
    }

    Address reference = waitingListHead;
    Address prevReference = Address.zero();
    if (!waitingListHead.isZero()) {
      waitingListHead = ReferenceProcessor.forwardReference(reference);
      prevReference = reference;
      reference = getNextReferenceAsAddress(reference);
    }
    while (!reference.isZero()) {
      Address newReference = ReferenceProcessor.forwardReference(reference);
      if (!prevReference.isZero()) {
        setNextReferenceAsAddress(prevReference, newReference);
      }
      prevReference = reference;
      reference = getNextReferenceAsAddress(reference);
    }
    if (!prevReference.isZero()) {
      setNextReferenceAsAddress(prevReference, Address.zero());
    }

    if (TRACE) {
      VM.sysWrite("Ending ReferenceProcessor.traverse(");
      VM.sysWrite(ReferenceProcessor.semanticStrings[semantics]);
      VM.sysWriteln(")");
    }
  }

  /**
   * Scan through the list of references. Calls ReferenceProcessor's
   * processReference method for each reference and builds a new
   * list of those references still active.
   *
   * Depending on the value of <code>nursery</code>, we will either
   * scan all references, or just those created since the last scan.
   *
   * @param nursery Scan only the newly created references
   */
  @LogicallyUninterruptible
  private void scanReferences(boolean nursery) {
    Address reference;
    Address prevReference = Address.zero();
    Address newHead = Address.zero();
    int waiting = 0;

    if (!nursery) {
      /* Process the mature reference list */

      reference = waitingListHead;
      while (!reference.isZero()) {
        Address newReference =
          ReferenceProcessor.processReference(reference, semantics);
        if (!newReference.isZero()) {
          /*
           * Update 'next' pointer of the previous reference in the
           * linked list of waiting references.
           */
          if (!prevReference.isZero()) {
            setNextReferenceAsAddress(prevReference, newReference);
          }
          waiting += 1;
          prevReference = newReference;
          if (newHead.isZero())
            newHead = newReference;
        }
        reference = getNextReferenceAsAddress(reference);
      }
      if (!prevReference.isZero()) {
        setNextReferenceAsAddress(prevReference, Address.zero());
      }
      waitingListHead = newHead;
      countOnMatureList = waiting;
    }

    /* Process the reference nursery, putting survivors on the main list */
    reference = nurseryListHead;
    waiting = countOnMatureList;
    while (!reference.isZero()) {
      Address newReference =
        ReferenceProcessor.processReference(reference, semantics);
      reference = getNextReferenceAsAddress(reference);
      if (!newReference.isZero()) {
        /*
         * Put the reference onto the main 'tenured' queue
         */
        setNextReferenceAsAddress(newReference, waitingListHead);
        waitingListHead = VM_Magic.objectAsAddress(newReference);
        waiting += 1;
      }
    }
    nurseryListHead = Address.zero();

    countOnMatureList = countOnWaitingList = waiting;
  }

  /**
   * Scan through the list of references with the specified semantics.
   *
   * @param semantics the number representing the semantics
   * @param nursery Scan only the newly created references
   */
  public void scanReferences(int semantics, boolean nursery) {
    if (VM.VerifyAssertions) {
      VM._assert(ReferenceProcessor.SOFT_SEMANTICS <= semantics &&
                 semantics <= ReferenceProcessor.PHANTOM_SEMANTICS);
    }
    if (TRACE) {
      VM.sysWriteln("Starting ReferenceGlue.scanReferences(",
                    ReferenceProcessor.semanticStrings[semantics], ")");
    }
    switch (semantics) {
    case ReferenceProcessor.SOFT_SEMANTICS:
      //VM.sysWriteln("Scanning soft references, nursery = ",nursery);
      softReferenceProcessor.scanReferences(nursery);
      break;
    case ReferenceProcessor.WEAK_SEMANTICS:
      //VM.sysWriteln("Scanning weak references, nursery = ",nursery);
      weakReferenceProcessor.scanReferences(nursery);
      break;
    case ReferenceProcessor.PHANTOM_SEMANTICS:
      //VM.sysWriteln("Scanning phantom references, nursery = ",nursery);
      phantomReferenceProcessor.scanReferences(nursery);
      break;
    }
    if (TRACE) {
      VM.sysWriteln("Ending ReferenceGlue.scanReferences()");
    }
  }

  /**
   * Put this Reference object on its ReferenceQueue (if it has one)
   * when its referent is no longer sufficiently reachable. The
   * definition of "reachable" is defined by the semantics of the
   * particular subclass of Reference. The implementation of this
   * routine is determined by the the implementation of
   * java.lang.ref.ReferenceQueue in GNU classpath. It is in this
   * class rather than the public Reference class to ensure that Jikes
   * has a safe way of enqueueing the object, one that cannot be
   * overridden by the application program.
   *
   * @see java.lang.ref.ReferenceQueue
   * @param addr the address of the Reference object
   * @param onlyOnce <code>true</code> if the reference has ever
   * been enqueued previously it will not be enqueued
   * @return <code>true</code> if the reference was enqueued
   */
  public boolean enqueueReference(Address addr,
                                               boolean onlyOnce) {
    Reference<?> reference = (Reference<?>)VM_Magic.addressAsObject(addr);
    if (!onlyOnce || !reference.wasEverEnqueued())
      return reference.enqueue();
    else
      return false;
  }

  /**
   * Add a reference to the list of soft references.
   * @param ref the SoftReference to add
   */
  @Interruptible
  public static void addSoftCandidate(SoftReference<?> ref) {
    softReferenceProcessor.addCandidate(ref);
  }

  /**
   * Add a reference to the list of weak references.
   * @param ref the WeakReference to add
   */
  @Interruptible
  public static void addWeakCandidate(WeakReference<?> ref) {
    weakReferenceProcessor.addCandidate(ref);
  }

  /**
   * Add a reference to the list of phantom references.
   * @param ref the PhantomReference to add
   */
  @Interruptible
  public static void addPhantomCandidate(PhantomReference<?> ref) {
    phantomReferenceProcessor.addCandidate(ref);
  }

  /***********************************************************************
   *
   * Reference object field accesors
   */

  /**
   * Get the referent from a reference.  For Java the reference
   * is a Reference object.
   * @param addr the address of the reference
   * @return the referent address
   */
  public ObjectReference getReferent(Address addr) {
    return addr.loadObjectReference(VM_Entrypoints.referenceReferentField.getOffset());
  }

  /**
   * Set the referent in a reference.  For Java the reference is
   * a Reference object.
   * @param addr the address of the reference
   * @param referent the referent address
   */
  public void setReferent(Address addr, ObjectReference referent) {
    addr.store(referent, VM_Entrypoints.referenceReferentField.getOffset());
  }

  private static Address getNextReferenceAsAddress(Address ref) {
    return ref.loadAddress(VM_Entrypoints.referenceNextAsAddressField.getOffset());
  }

  private static void setNextReferenceAsAddress(Address ref,
                                                Address next) {
    ref.store(next, VM_Entrypoints.referenceNextAsAddressField.getOffset());
  }

  /***********************************************************************
   *
   * Statistics and debugging
   */

  public static int countWaitingReferences(int semantics) {
    switch (semantics) {
    case ReferenceProcessor.SOFT_SEMANTICS:
      return softReferenceProcessor.countOnWaitingList;
    case ReferenceProcessor.WEAK_SEMANTICS:
      return weakReferenceProcessor.countOnWaitingList;
    case ReferenceProcessor.PHANTOM_SEMANTICS:
      return phantomReferenceProcessor.countOnWaitingList;
    }

    return -1;
  }

  /**
   * Scan through all references and forward. Only called when references
   * are objects.
   */
  public void forwardReferences() {
    softReferenceProcessor.forward();
    weakReferenceProcessor.forward();
    phantomReferenceProcessor.forward();
  }
}
