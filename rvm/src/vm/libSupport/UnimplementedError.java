/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


package com.ibm.JikesRVM.librarySupport;
import com.ibm.JikesRVM.VM_UnimplementedError;

/**
 * This class provides an entrypoint and type used in the
 * implementation of standard libraries to indicate unimplemented
 * functionality.
 *
 * @author Stephen Fink
 */
public final class UnimplementedError extends VM_UnimplementedError {

  /**
   * Create a runtime (unchecked) exception indicating that the VM does not
   * implement some operation.  
   *
   * @param msg An error message
   */
  UnimplementedError(String msg) {
    super(msg);
  }

  /**
   * Throw a runtime (unchecked) exception indicating that the VM does not
   * implement some operation.  
   *
   * @param msg An error message
   * @exception VM_UnimplementedError is always thrown
   */
  public static void unimplemented(String msg) {
    throw new UnimplementedError(msg);
  }
}
