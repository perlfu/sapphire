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
class t3GTWorker2 extends Thread {

  int        arg1;
  boolean    isReady;
  boolean    isFinished;

  t3GTWorker2(int arg1)
  {
    this.arg1 = arg1;
    this.isFinished = false;
    this.isReady    = false;
  }

  public void start() //- overrides Thread
  {
    super.start();
  }

  public void run()  //- overrides Thread
  {

                isReady = true;
                while (isReady) {
    try {
      Thread.sleep(arg1);
    }
    catch (InterruptedException e) {
                        System.out.println(" GC thread returning");
                        isFinished = true;
    }
                        if (isFinished) return;
    System.gc();
    }

  }
}

