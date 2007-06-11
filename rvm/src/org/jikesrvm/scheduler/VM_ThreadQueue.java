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
package org.jikesrvm.scheduler;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A queue of VM_Threads
 */
@Uninterruptible
public class VM_ThreadQueue extends VM_AbstractThreadQueue {

  /**
   * First thread on list.
   */
  protected VM_Thread head;

  /**
   * Last thread on the list.
   */
  protected VM_Thread tail;

  /**
   * Are any threads on the queue?
   */
  public boolean isEmpty() {
    return head == null;
  }

  /**
   * Atomic test to determine if any threads are on the queue.
   *    Note: The test is required for native idle threads
   */
  boolean atomicIsEmpty(VM_ProcessorLock lock) {
    boolean r;

    lock.lock();
    r = (head == null);
    lock.unlock();
    return r;
  }

  /** Add a thread to head of queue. */
  public void enqueueHighPriority(VM_Thread t) {
    if (VM.VerifyAssertions) VM._assert(t.next == null); // not currently on any other queue
    t.next = head;
    head = t;
    if (tail == null) {
      tail = t;
    }
  }

  /** Add a thread to tail of queue. */
  public void enqueue(VM_Thread t) {
    if (VM.VerifyAssertions) VM._assert(t.next == null); // not currently on any other queue
    if (head == null) {
      head = t;
    } else {
      tail.next = t;
    }
    tail = t;
  }

  /** Remove a thread from the head of the queue.
   @return the thread (null --> queue is empty) */
  public VM_Thread dequeue() {
    VM_Thread t = head;
    if (t == null) {
      return null;
    }
    head = t.next;
    t.next = null;
    if (head == null) {
      tail = null;
    }

    return t;
  }

  /** Dequeue the CollectorThread, if any, from this queue.
   If qlock != null protect by lock.
   @return The garbage collector thread.  If no thread found, return null. */
  VM_Thread dequeueGCThread(VM_ProcessorLock qlock) {

    if (qlock != null) qlock.lock();
    VM_Thread currentThread = head;
    if (head == null) {
      if (qlock != null) qlock.unlock();
      return null;
    }
    VM_Thread nextThread = head.next;

    if (currentThread.isGCThread()) {
      head = nextThread;
      if (head == null) {
        tail = null;
      }
      currentThread.next = null;
      if (qlock != null) qlock.unlock();
      return currentThread;
    }

    while (nextThread != null) {
      if (nextThread.isGCThread()) {
        currentThread.next = nextThread.next;
        if (nextThread == tail) {
          tail = currentThread;
        }
        nextThread.next = null;
        if (qlock != null) qlock.unlock();
        return nextThread;
      }
      currentThread = nextThread;
      nextThread = nextThread.next;
    }

    return null;
  }

  // Number of items on queue (an estimate only: we do not lock the queue
  // during this scan.)
  //
  public int length() {
    int length = 0;
    for (VM_Thread t = head; t != null; t = t.next) {
      length += 1;
    }
    return length;
  }

  /** Debugging. */
  public boolean contains(VM_Thread x) {
    for (VM_Thread t = head; t != null; t = t.next) {
      if (t == x) return true;
    }
    return false;
  }

  public void dump() {
    // We shall space-separate them, for compactness.
    // I hope this is a good decision.
    boolean pastFirst = false;
    for (VM_Thread t = head; t != null; t = t.next) {
      if (pastFirst) {
        VM.sysWrite(" ");
      }
      t.dump();
      pastFirst = true;
    }
    VM.sysWrite("\n");
  }
}
