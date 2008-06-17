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

import org.jikesrvm.scheduler.Lock;
import org.jikesrvm.scheduler.RVMThread;

public class NativeLock extends Lock {

  @Override
  public boolean lockHeavy(Object o) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void unlockHeavy(Object o) {
    // TODO Auto-generated method stub
  }

  @Override
  public boolean isBlocked(RVMThread t) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isWaiting(RVMThread t) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void dumpWaitingThreads() {
    // TODO Auto-generated method stub
  }

  @Override
  public void dumpBlockedThreads() {
    // TODO Auto-generated method stub
  }
}
