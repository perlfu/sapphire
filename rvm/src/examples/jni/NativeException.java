/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 *  Test exception handling through JNI and native code
 *
 * Ton Ngo, Steve Smith 3/24/00
 */


class NativeException {
  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;

  public static native void setVerboseOff();

  /**
   * Declare native methods that will call the JNI Array Functions
   */
  static native boolean testPassThrough(int[] sourceArray);
  static native boolean testExceptionOccured(int[] sourceArray);
  static native boolean testExceptionClear(int[] sourceArray);
  static native boolean testExceptionDescribe(int[] sourceArray);
  static native boolean testExceptionThrow(Throwable e);
  static native boolean testExceptionThrowNew(Class eclass);
  static native boolean testFatalError(boolean allTestPass, int[] sourceArray);

  /**
   * constructor
   */
  public NativeException() {

  }


  public static void main(String args[]) {

    int returnValue;
    boolean returnFlag;
    int intArray[] = new int[10];

    System.loadLibrary("NativeException");

    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
	verbose = false;	
	setVerboseOff();
      } 	
    }
    
    /****************************************************
     * Exception passing through native code without being handled
     */
    try {
      returnFlag = testPassThrough(intArray);  // shouldn't return here
      returnFlag = false;
    } catch (Exception e) {
      printVerbose("Caught exception:  expected ArrayIndexOutOfBoundsException, got " +
		   e.toString());
      returnFlag = true;
    }
    checkTest(0, returnFlag, "Exception pass through");
    
    
    /****************************************************
     * check for exception in native code
     */
    try {
      returnFlag = testExceptionOccured(intArray);  // shouldn't return here      
    } catch (Exception e) {
      printVerbose("Caught exception:  expected ArrayIndexOutOfBoundsException, got " +
		   e.toString());
    }
    
    
    /****************************************************
     * check for exception being cleared in native code
     */
    try {
      returnFlag = testExceptionClear(intArray);  
    } catch (Exception e) {
      returnFlag = false;  // shouldn't be here      
    }
    checkTest(0, returnFlag, "ExceptionClear");


    /****************************************************
     * print exception trace and clear
     */
    try {
      returnFlag = testExceptionDescribe(intArray);  
    } catch (Exception e) {
      returnFlag = false;  // shouldn't be here      
    }
    checkTest(0, returnFlag, "ExceptionDescribe");


    /****************************************************
     * give the native code an exception to throw
     */
    try {
      returnFlag = testExceptionThrow(new Exception("Test Throw in native"));  
      returnFlag = false;  // shouldn't be here  
    } catch (Exception e) {
      printVerbose("Caught exception:  got " + e.toString());      
      returnFlag = true;  
    }
    checkTest(0, returnFlag, "ExceptionThrow");


    /****************************************************
     * give the native code an exception class to throw
     */
    try {
      Class ecls = Class.forName("java.lang.Exception");
      returnFlag = testExceptionThrowNew(ecls);  
      returnFlag = false;  // shouldn't be here  
    }
    catch (ClassNotFoundException e1) {
      returnFlag = false;  // shouldn't be here        
    }
    catch (Exception e) {
      printVerbose("Caught exception:  got " + e.toString());      
      returnFlag = true;  
    }
    checkTest(0, returnFlag, "ExceptionThrowNew");


    /****************************************************
     * let the native code declare FatalError and exit the JVM
     */
    try {
      returnFlag = testFatalError(allTestPass, intArray);  
      returnFlag = false;  // shouldn't be here            
    } catch (Exception e) {
      returnFlag = false;  // shouldn't be here      
    }
    checkTest(0, returnFlag, "FatalError");


    // Summarize

    if (allTestPass)
      System.out.println("PASS: NativeException");  // won't reach here if FatalError test succeeds
    else 
      System.out.println("FAIL: NativeException");

  }



  static void printVerbose(String str) {
    if (verbose) 
      System.out.println(str);
  }

  static void checkTest(int returnValue, boolean postCheck, String testName) {
    if (returnValue==0 && postCheck) {
      printVerbose("PASS: " + testName);
    } else {
      allTestPass = false;
      printVerbose("FAIL: " + testName);
    }
  }
}
