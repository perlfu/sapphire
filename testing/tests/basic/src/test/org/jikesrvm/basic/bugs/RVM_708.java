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
package test.org.jikesrvm.basic.bugs;

public class RVM_708 {
  public static void main(String[] args) {
    createThread();
    System.out.println("Sleeping for 1 seconds");
    try {
      Thread.sleep(1000);
    } catch (InterruptedException i) {
      System.err.println(i);
    }
    System.out.println("Going to call System.gc()");
    System.gc();
  }

  private static void infiniteLoop() {
    System.out.println("Waiting...");
    while (true);
  }

  private static void createThread() {
    Thread t1 = new Thread(new Runnable() {
      public void run() {
        infiniteLoop();
      }
    });
    t1.setDaemon(true);
    t1.start();
  }
}
