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

package org.jikesrvm.tools.bootImageWriter;

import java.util.concurrent.atomic.AtomicLong;
import org.jikesrvm.classloader.VM_Type;

/**
 * Worker for parallel compilation during bootimage writing.
 */
public class BootImageWorker implements Runnable {

  public static final boolean verbose = false;
  public static boolean instantiationFailed = false;
  private static final AtomicLong count = new AtomicLong();
  private final VM_Type type;

  BootImageWorker(VM_Type type) {
    this.type = type;
  }

  public void run() {
    if (type == null)
      return;
    try {
      long startTime = 0;
      long myCount = 0;
      if (verbose) {
        startTime = System.currentTimeMillis();
        myCount = count.incrementAndGet();
        BootImageWriterMessages.say(startTime + ": "+ myCount +" starting " + type);
      }
      type.instantiate();
      if (verbose) {
        long stopTime = System.currentTimeMillis();
        BootImageWriterMessages.say(stopTime + ": "+ myCount +" finish " + type +
            " duration: " + (stopTime - startTime));
      }
    } catch (Throwable t) {
      instantiationFailed = true;
      t.printStackTrace();
      BootImageWriterMessages.fail("Failure during instantiation of " + type);
    }
  }
}

