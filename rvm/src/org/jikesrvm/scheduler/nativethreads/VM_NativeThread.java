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
package org.jikesrvm.scheduler.nativethreads;

import static org.jikesrvm.ArchitectureSpecific.VM_StackframeLayoutConstants.STACK_SIZE_NORMAL;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.scheduler.VM_Lock;
import org.jikesrvm.scheduler.VM_Thread;

public class VM_NativeThread extends VM_Thread {

  /**
   * Create a thread with default stack and with the given name.
   */
  public VM_NativeThread(String name) {
    this(MM_Interface.newStack(STACK_SIZE_NORMAL, false),
        null, // java.lang.Thread
        name,
        true, // daemon
        true, // system
        Thread.NORM_PRIORITY);
  }

  /**
   * Create a thread with the given stack and name. Used by
   * {@link org.jikesrvm.memorymanagers.mminterface.VM_CollectorThread} and the
   * boot image writer for the boot thread.
   */
  public VM_NativeThread(byte[] stack, String name) {
    this(stack,
        null, // java.lang.Thread
        name,
        true, // daemon
        true, // system
        Thread.NORM_PRIORITY);
  }

  /**
   * Create a thread with ... called by java.lang.VMThread.create. System thread
   * isn't set.
   */
  public VM_NativeThread(Thread thread, long stacksize, String name, boolean daemon, int priority) {
    this(MM_Interface.newStack((stacksize <= 0) ? STACK_SIZE_NORMAL : (int)stacksize, false),
        thread, name, daemon, false, priority);
  }

  /**
   * Create a thread.
   */
  protected VM_NativeThread(byte[] stack, Thread thread, String name, boolean daemon, boolean system, int priority) {
    super(stack, thread, name, daemon, system, priority);
  }

  @Override
  public String getThreadState() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  protected void killInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void notifyAllInternal(Object o, VM_Lock l) {
    // TODO Auto-generated method stub

  }

  @Override
  protected void notifyInternal(Object o, VM_Lock l) {
    // TODO Auto-generated method stub

  }

  @Override
  protected void registerThreadInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void resumeInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  public void schedule() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void sleepInternal(long millis, int ns) throws InterruptedException {
    // TODO Auto-generated method stub

  }

  @Override
  protected void suspendInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected Throwable waitInternal(Object o, long millis) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  protected Throwable waitInternal(Object o) {
    // TODO Auto-generated method stub
    return null;
  }

}
