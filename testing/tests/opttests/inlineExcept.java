/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * simple test of inlining & exception handling
 *
 * @author unascribed
 */

final class inlineExcept {
  public static void main(String[] args) {
    run();
  }

  public static boolean run() {
    try {
      foo();
    } catch (IndexOutOfBoundsException e) {
      System.out.println("Caught IOOBE in foo");
    }
    return true;
  }

  static void foo() {
    bar();
  }

  static void bar() {
    throw new IndexOutOfBoundsException();
  }
}
