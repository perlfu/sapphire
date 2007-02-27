/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2004
 */
/**
 * Test to check to that NULL gets JNIID 0.
 * @author Dave Grove
 */
class NullIdentity {

  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;

  public static native void setVerboseOff();
  static native int nullFirst(Object a);
  static native int nullSecond(Object a, Object b);
  static native int nullForceSpill(Object a, Object b, Object c, Object d,
                                   Object e, Object f, Object g, Object h,
                                   Object i, Object j, Object k, Object l);
  
  /************************************************************
   * Main body of the test program
   *
   */
  public static void main(String args[]) {
    int returnValue;
    String anObj = new String("Year of the Dragon");

    System.loadLibrary("NullIdentity");
    
    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
        verbose = false;        
        setVerboseOff();
      }         
    }

    returnValue = nullFirst(null);
    checkTest(returnValue, "nullFirst");

    returnValue = nullSecond(anObj, null);
    checkTest(returnValue, "nullSecond");

    returnValue = nullForceSpill(anObj, anObj, anObj, anObj, anObj, anObj,
                                 anObj, anObj, anObj, anObj, anObj, null);
    checkTest(returnValue, "nullForceSpill");
    
    if (allTestPass)
      System.out.println("PASS: NullIdentity");
    else 
      System.out.println("FAIL: NullIdentity");
  }

  static void printVerbose(String str) {
    if (verbose) 
      System.out.println(str);
  }

  static void checkTest(int returnValue, String testName) {
    if (returnValue==0) {
      printVerbose("PASS: " + testName);
    } else {
      allTestPass = false;
      printVerbose("FAIL: " + testName);
    }
  }

}
