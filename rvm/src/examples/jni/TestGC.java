/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Test GC with Native frames on stack
 *
 * @author Ton Ngo, Steve Smith 
 * @date   3/29/00
 */

class TestGC {
  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;

  public static native void setVerboseOff();

  /**
   * Declare native methods that will call cause GC to occur
   */

  static native Object testgc (Object obj1, Object obj2);

  public static void main(String args[]) {

    int returnValue;
    boolean returnFlag;
    boolean runningUnderJDK = true;
    boolean copyingGC=false;

    String str1 = new String("string 1");
    String str2 = new String("string 2");
    int oldAddress1=0;
    int oldAddress2=0;
    int newAddress1=0;
    int newAddress2=0;

    System.loadLibrary("TestGC");

    if (args.length!=0) {
	for (int i=0; i<args.length; i++) {
	    if (args[i].equals("-quiet")) {
		verbose = false;	
		setVerboseOff();
	    } 	
	    if (args[i].equals("-jdk")) {
		runningUnderJDK = true;	
	    } 	
	}
    }

    printVerbose("TestGC Before native call:");
    if ( ! runningUnderJDK ) {
      oldAddress1 = VM_Magic.objectAsAddress(str1);
      oldAddress2 = VM_Magic.objectAsAddress(str2);
      printVerbose("  str1 address = " + VM.intAsHexString(oldAddress1));
      printVerbose("  str2 address = " + VM.intAsHexString(oldAddress2));
    }

    Object returnobj = testgc( str1, str2 );

    printVerbose("TestGC After native call:");
    if ( ! runningUnderJDK ) {
      newAddress1 = VM_Magic.objectAsAddress(str1);
      newAddress2 = VM_Magic.objectAsAddress(str2);
      if (oldAddress1!=newAddress1 && oldAddress2!=newAddress2) {
	copyingGC = true;
	printVerbose("Objects have been moved by GC:");
      } else {
	copyingGC = false;
	printVerbose("Objects have NOT been moved by GC:");
      }
      printVerbose("  str1 address = " + VM.intAsHexString(newAddress1));
      printVerbose("  str2 address = " + VM.intAsHexString(newAddress2));
      printVerbose("  returnobj address = " + VM.intAsHexString(VM_Magic.objectAsAddress(returnobj)));
    }

    // if (copyingGC)
    //   checkTest( 0, (str1==returnobj), "GC with copying during native code" );
    // else
    //   checkTest( 0, (str1==returnobj), "GC without copying during native code" );
    checkTest( 0, (str1==returnobj), "GC during native code" );
  }

  static void printVerbose(String str) {
    if (verbose) 
      System.out.println(str);
  }

  static void checkTest(int returnValue, boolean postCheck, String testName) {
    if (returnValue==0 && postCheck) {
      System.out.println("PASS: " + testName);
    } else {
      allTestPass = false;
      System.out.println("FAIL: " + testName);
    }
  }

}

