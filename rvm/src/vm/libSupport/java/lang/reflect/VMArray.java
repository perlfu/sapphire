/*
 * (C) Copyright The University of Manchester 2006
 */
//$Id$
package java.lang.reflect;

import com.ibm.JikesRVM.classloader.VM_Array;
import com.ibm.JikesRVM.VM_Runtime;

/**
 * VM dependent Array operations
 *
 * @author Chris Kirkham
 * @author Ian Rogers
 */
class VMArray {
  /**
   * Dynamically create an array of objects.
   *
   * @param cls guaranteed to be a valid object type
   * @param length the length of the array
   * @return the new array
   * @throws NegativeArraySizeException if dim is negative
   * @throws OutOfMemoryError if memory allocation fails
   */
  static Object createObjectArray(Class cls, int length)
    throws OutOfMemoryError, NegativeArraySizeException {
    if(cls == null)
      throw new NullPointerException();
    if(length < 0)
      throw new NegativeArraySizeException();

    VM_Array arrayType = java.lang.JikesRVMSupport.getTypeForClass(cls).getArrayTypeForElementType();
    if (!arrayType.isInitialized()) {
      arrayType.resolve();
      arrayType.instantiate();
      arrayType.initialize();
    }
    return VM_Runtime.resolvedNewArray(length, arrayType);
  }
}