/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * A queue of VM_Proxys prioritized by their thread wakeup times.
 * based on VM_WakeupQueue (14 October 1998 Bowen Alpern)
 *
 * @author Bowen Alpern
 */
final class VM_ProxyWakeupQueue extends VM_AbstractThreadQueue implements VM_Uninterruptible {
  
  private VM_Proxy head; // first thread on list
  private int       id;   // id of this queue, for event logging

  VM_ProxyWakeupQueue(int id) {
    this.id = id;
  }

  boolean isEmpty () {
    return head == null;
  }

  boolean isReady () {
    VM_Proxy temp = head;
    return ((temp != null) && (VM_Time.now() >= temp.wakeupTime));
  }

  void enqueue (VM_Thread t) {
    enqueue(t.proxy);
  }

  void enqueue (VM_Proxy p) {
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logEnqueue(p.patron, id);
    VM_Proxy previous = null;
    VM_Proxy current  = head;
    while (current != null && current.wakeupTime <= p.wakeupTime) { // skip proxies with earlier wakeupTimes
      previous = current;
      current = current.wakeupNext;
      }
    // insert p
    if (previous == null) {
      head = p;
    } else {
      previous.wakeupNext = p;
    }
    p.wakeupNext = current;
  }

  // Remove a thread from the queue if there's one ready to wake up "now".
  // Returned: the thread (null --> nobody ready to wake up)
  //
  VM_Thread dequeue () {
    double currentTime = VM_Time.now();
    while (head != null) {
      if (currentTime < head.wakeupTime) return null;
      VM_Proxy p = head;
      head = head.wakeupNext;
      p.wakeupNext = null;
      VM_Thread t = p.unproxy();
      if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logDequeue(t, id);
      if (t != null) return t;
    }
    return null;
  }

  // Number of items on queue (an estimate: queue is not locked during the scan).
  //
  int length() {
    if (head == null) return 0;
    int length = 1;
    for (VM_Proxy  p = head; p != null; p = p.wakeupNext)
      length += 1;
    return length;
  }

  // Debugging.
  //
  boolean contains(VM_Thread t) {
    for (VM_Proxy p = head; p != null; p = p.wakeupNext)
      if (p.patron == t) return true;
    return false;
  }

  void dump() {
    for (VM_Proxy p = head; p != null; p = p.wakeupNext) {
      if (p.patron != null) p.patron.dump();
    }
    VM.sysWrite("\n");
  }

}
