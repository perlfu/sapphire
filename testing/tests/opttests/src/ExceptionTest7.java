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
class ExceptionTest7 {


  static int[] testa = null; //new int[3];
  public static void main(String[] args) {

    run();
  }

 public static boolean run() {
    try {
      foo();
    } catch (IndexOutOfBoundsException e5) {
      System.out.println(" IndexOutOfBoundsException caught");
    } catch (NullPointerException e) {
      System.out.println(" NullPointerException");
    }
    System.out.println(" At End");

    return true;
  }

  public static void foo() {
      testa[4] = 0;
  }

}
