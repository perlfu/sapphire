/* -*-coding: iso-8859-1 -*-
 * 
 * (C) Copyright IBM Corp. 2001
 *
 * $Id$
 */
package org.mmtk.vm;

import org.mmtk.utility.ReferenceProcessor;
import org.mmtk.vm.Lock;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Entrypoints;

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
 * @author Chris Hoffmann
 * @modified Andrew Gray
 */
public class ReferenceGlue implements Uninterruptible {
  /**
   * <code>true</code> if the references are implemented as heap
   * objects (rather than in a table, for example).  In this context
   * references are soft, weak or phantom references.
   */
  public static final boolean REFERENCES_ARE_OBJECTS = true;

  private static boolean clearSoftReferences = false;

  private static Lock lock = new Lock("ReferenceProcessor");

  private static ReferenceGlue softReferenceProcessor =
    new ReferenceGlue(ReferenceProcessor.SOFT_SEMANTICS);
  private static ReferenceGlue weakReferenceProcessor =
    new ReferenceGlue(ReferenceProcessor.WEAK_SEMANTICS);
  private static ReferenceGlue phantomReferenceProcessor =
    new ReferenceGlue(ReferenceProcessor.PHANTOM_SEMANTICS);

  // Debug flags
  private static final boolean TRACE = false;

  private Address waitingListHead = Address.zero();
  private int countOnWaitingList = 0;
  private int semantics;

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
  private void addCandidate(Reference ref)
    throws NoInlinePragma, InterruptiblePragma {
    if (TRACE) {
        Address referenceAsAddress = VM_Magic.objectAsAddress(ref);
        ObjectReference referent = getReferent(referenceAsAddress);
        VM.sysWriteln("Adding Reference: ", referenceAsAddress);
        VM.sysWriteln("       Referent:  ", referent);
    }
    
    lock.acquire();
    setNextReferenceAsAddress(VM_Magic.objectAsAddress(ref), waitingListHead);
    waitingListHead = VM_Magic.objectAsAddress(ref);
    countOnWaitingList += 1;    
    lock.release();
  }

  /**
   * Scan through the list of references. Calls ReferenceProcessor's
   * processReference method for each reference and builds a new
   * list of those references still active.
   */
  private void scanReferences()
    throws LogicallyUninterruptiblePragma {
    Address reference = waitingListHead;
    Address prevReference = Address.zero();
    Address newHead = Address.zero();
    int waiting = 0;
      
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
    countOnWaitingList = waiting;
    waitingListHead = newHead;
  }

  /**
   * Scan through the list of references with the specified semantics.
   * @param semantics the number representing the semantics
   */
  public static void scanReferences(int semantics) {
    if (VM.VerifyAssertions)
      VM._assert(ReferenceProcessor.SOFT_SEMANTICS <= semantics
                           &&
                           semantics <= ReferenceProcessor.PHANTOM_SEMANTICS);
    if (TRACE) {
      VM.sysWriteln("Starting ReferenceGlue.scanReferences(",
                    ReferenceProcessor.semanticStrings[semantics], ")");
    }
    switch (semantics) {
    case ReferenceProcessor.SOFT_SEMANTICS:
      softReferenceProcessor.scanReferences();
      break;
    case ReferenceProcessor.WEAK_SEMANTICS:
      weakReferenceProcessor.scanReferences();
      break;
    case ReferenceProcessor.PHANTOM_SEMANTICS:
      phantomReferenceProcessor.scanReferences();
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
  public static final boolean enqueueReference(Address addr,
                                               boolean onlyOnce) {
    Reference reference = (Reference)VM_Magic.addressAsObject(addr);
    if (!onlyOnce || !reference.wasEverEnqueued())
      return reference.enqueue();
    else
      return false;
  }

  /**
   * Add a reference to the list of soft references.
   * @param ref the SoftReference to add
   */
  public static void addSoftCandidate(SoftReference ref)
    throws InterruptiblePragma {
    softReferenceProcessor.addCandidate(ref);
  }

  /**
   * Add a reference to the list of weak references.
   * @param ref the WeakReference to add
   */
  public static void addWeakCandidate(WeakReference ref)
    throws InterruptiblePragma {
    weakReferenceProcessor.addCandidate(ref);
  }
  
  /**
   * Add a reference to the list of phantom references.
   * @param ref the PhantomReference to add
   */
  public static void addPhantomCandidate(PhantomReference ref)
    throws InterruptiblePragma {
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
  public static ObjectReference getReferent(Address addr) {
    return addr.loadObjectReference(VM_Entrypoints.referenceReferentField.getOffset());
  }
  
  /**
   * Set the referent in a reference.  For Java the reference is
   * a Reference object.
   * @param addr the address of the reference
   * @param referent the referent address
   */
  public static void setReferent(Address addr, ObjectReference referent) {
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

  public static int countWaitingSoftReferences() {
    return softReferenceProcessor.countOnWaitingList;
  }

  public static int countWaitingWeakReferences() {
    return weakReferenceProcessor.countOnWaitingList;
  }
}
