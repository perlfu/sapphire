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
package org.jikesrvm.classloader;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * A java method's try/catch/finally information.
 */
public final class VM_ExceptionHandlerMap {
  //-----------//
  // Interface //
  //-----------//

  public int[] getStartPC() { return startPCs; }

  public int[] getEndPC() { return endPCs; }

  public int[] getHandlerPC() { return handlerPCs; }

  public VM_TypeReference[] getExceptionTypes() { return exceptionTypes; }

  public VM_TypeReference getExceptionType(int i) { return exceptionTypes[i]; }

  /* we need to adjust the exception handler map for pseudo bytecode
  * TODO: OSR redesign; make a subclass of VM_ExceptionHandlerMap with this functionality
  */
  public void setStartPC(int[] newPCs) { startPCs = newPCs; }

  public void setEndPC(int[] newPCs) { endPCs = newPCs; }

  public void setHandlerPC(int[] newPCs) { handlerPCs = newPCs; }

  //----------------//
  // Implementation //
  //----------------//

  /**
   * bytecode offset at which i-th try block begins
   * 0-indexed from start of method's bytecodes[]
   */
  private int[] startPCs;

  /**
   * bytecode offset at which i-th try block ends (exclusive)
   * 0-indexed from start of method's bytecodes[]
   */
  private int[] endPCs;

  /**
   * bytecode offset at which exception handler for i-th try block begins
   * 0-indexed from start of method's bytecodes[]
   */
  private int[] handlerPCs;

  /**
   * exception type for which i-th handler is to be invoked
   * - something like "java/lang/IOException".
   * NOTE: When constructing the VM_ExceptionHandlerMap we replace
   * 'null' entries (means a finally block that catches everything)
   * with VM_Type.JavaLangThrowableType so we don't have to do anything
   * special anywhere else in the VM.
   */
  private final VM_TypeReference[] exceptionTypes;

  /**
   * Construct the exception handler map
   *
   * @param startPCs
   * @param endPCs
   * @param handlerPCs
   * @param exceptionTypes
   */
  private VM_ExceptionHandlerMap(int[] startPCs, int[] endPCs, int[] handlerPCs, VM_TypeReference[] exceptionTypes) {
    this.startPCs = startPCs;
    this.endPCs = endPCs;
    this.handlerPCs = handlerPCs;
    this.exceptionTypes = exceptionTypes;
  }

  /**
   * Read the exception handler map
   *
   * @return an exception handler map or null if none were present
   */
  static VM_ExceptionHandlerMap readExceptionHandlerMap(DataInputStream input, int[] constantPool) throws IOException {
    int cnt = input.readUnsignedShort();
    if (cnt != 0) {
      int[] startPCs = new int[cnt];
      int[] endPCs = new int[cnt];
      int[] handlerPCs = new int[cnt];
      VM_TypeReference[] exceptionTypes = new VM_TypeReference[cnt];
      for (int i = 0; i < cnt; ++i) {
        startPCs[i] = input.readUnsignedShort();
        endPCs[i] = input.readUnsignedShort();
        handlerPCs[i] = input.readUnsignedShort();
        VM_TypeReference et = VM_Class.getTypeRef(constantPool, input.readUnsignedShort()); // possibly null
        if (et == null) {
          // A finally block...set to java.lang.Throwable to avoid
          // needing to think about this case anywhere else in the VM.
          exceptionTypes[i] = VM_TypeReference.JavaLangThrowable;
        } else {
          exceptionTypes[i] = et;
        }
      }
      return new VM_ExceptionHandlerMap(startPCs, endPCs, handlerPCs, exceptionTypes);
    } else {
      return null;
    }
  }

  VM_ExceptionHandlerMap deepCopy() {
    int n = startPCs.length;
    int[] copyStartPCs = new int[n];
    System.arraycopy(this.startPCs, 0, copyStartPCs, 0, n);
    int[] copyEndPCs = new int[n];
    System.arraycopy(this.endPCs, 0, copyEndPCs, 0, n);
    int[] copyHandlerPCs = new int[n];
    System.arraycopy(this.handlerPCs, 0, copyHandlerPCs, 0, n);
    VM_TypeReference[] copyExceptionTypes = new VM_TypeReference[n];
    System.arraycopy(this.exceptionTypes, 0, copyExceptionTypes, 0, n);

    return new VM_ExceptionHandlerMap(copyStartPCs, copyEndPCs, copyHandlerPCs, copyExceptionTypes);
  }
}
