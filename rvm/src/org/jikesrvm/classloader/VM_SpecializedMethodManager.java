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

import org.jikesrvm.memorymanagers.mminterface.MM_Interface;

import org.jikesrvm.VM;

/**
 * The manager of specialized methods.
 */
public final class VM_SpecializedMethodManager {
  /** The number of specialized methods. Currently the MM is the only consumer. */
  private static final int numSpecializedMethods = MM_Interface.numSpecializedMethods();

  /** All the specialized methods */
  private static final VM_SpecializedMethod[] methods = new VM_SpecializedMethod[numSpecializedMethods];

  /** The number of specialized methods */
  public static int numSpecializedMethods() { return numSpecializedMethods; }

  /** Set up the specialized methods for the given type */
  public static void notifyTypeInstantiated(VM_Type type) {
    for(int i=0; i < numSpecializedMethods; i++) {
      if (methods[i] == null) {
        initializeSpecializedMethod(i);
      }
      type.setSpecializedMethod(i, methods[i].specializeMethod(type));
    }
  }

  /** Ensure that a specific specialized method now exists. */
  private static void initializeSpecializedMethod(int id) {
    if (VM.VerifyAssertions) VM._assert(id >= 0);
    if (VM.VerifyAssertions) VM._assert(id < numSpecializedMethods);
    if (VM.VerifyAssertions) VM._assert(methods[id] == null);
    methods[id] = MM_Interface.createSpecializedMethod(id);
  }

  /** Can not create an instance of the manager */
  private VM_SpecializedMethodManager() {}
}
