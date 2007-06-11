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
package org.jikesrvm.adaptive.measurements.organizers;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.measurements.listeners.VM_Listener;
import org.jikesrvm.scheduler.VM_Thread;
import org.jikesrvm.scheduler.VM_ThreadQueue;
import org.vmmagic.pragma.Uninterruptible;

/**
 * An VM_Organizer acts an an intermediary between the low level
 * online measurements and the controller.  An organizer may perform
 * simple or complex tasks, but it is always simply following the
 * instructions given by the controller.
 */
public abstract class VM_Organizer extends VM_Thread {

  /** Constructor */
  public VM_Organizer() {
    super(null, "VM_Organizer");
  }

  /**
   * The listener associated with this organizer.
   * May be null if the organizer has no associated listener.
   */
  protected VM_Listener listener;

  /**
   * A queue to hold the organizer thread when it isn't executing
   */
  private final VM_ThreadQueue tq = new VM_ThreadQueue();

  /**
   * Called when thread is scheduled.
   */
  public void run() {
    initialize();
    while (true) {
      passivate(); // wait until externally scheduled to run
      try {
        thresholdReached();       // we've been scheduled; do our job!
        if (listener != null) listener.reset();
      } catch (Exception e) {
        e.printStackTrace();
        if (VM.ErrorsFatal) VM.sysFail("Exception in organizer " + this);
      }
    }
  }

  /**
   * Last opportunity to say something.
   */
  public void report() {}

  /**
   * Method that is called when the sampling threshold is reached
   */
  abstract void thresholdReached();

  /**
   * Organizer specific setup.
   * A good place to install and activate any listeners.
   */
  protected abstract void initialize();

  /*
   * Can access the thread queue without locking because
   * Only listener and organizer operate on the thread queue and the
   * listener uses its own protocol to ensure that exactly 1
   * thread will attempt to activate the organizer.
   */
  @Uninterruptible
  private void passivate() {
    if (listener != null) {
      if (VM.VerifyAssertions) VM._assert(!listener.isActive());
      listener.activate();
    }
    VM_Thread.yield(tq);
  }

  /**
   * Called to activate the organizer thread (ie schedule it for execution).
   */
  @Uninterruptible
  public void activate() {
    if (listener != null) {
      if (VM.VerifyAssertions) VM._assert(listener.isActive());
      listener.passivate();
    }
    VM_Thread org = tq.dequeue();
    if (VM.VerifyAssertions) VM._assert(org != null);
    org.schedule();
  }
}
