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
package org.jikesrvm.osr.bytecodes;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.AosEntrypoints;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;

/**
 * Special invokestatic, with only two possible target
 * ObjectHolder.getRefAt and ObjectHolder.cleanRefs
 * indiced by GETREFAT and CLEANREFS.
 */

public class InvokeStatic extends PseudoBytecode {

  private static final int bsize = 6;
  private final int tid;  // target INDEX

  public InvokeStatic(int targetId) {
    this.tid = targetId;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_InvokeStatic);
    int2bytes(codes, 2, tid);
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
    RVMMethod callee = null;
    switch (tid) {
      case GETREFAT:
        callee = AosEntrypoints.osrGetRefAtMethod;
        break;
      case CLEANREFS:
        callee = AosEntrypoints.osrCleanRefsMethod;
        break;
      default:
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        break;
    }

    int psize = callee.getParameterWords();
    int schanges = -psize;

    TypeReference rtype = callee.getReturnType();
    byte tcode = rtype.getName().parseForTypeCode();

    if (tcode == VoidTypeCode) {
      // do nothing
    } else {
      if ((tcode == LongTypeCode) || (tcode == DoubleTypeCode)) {
        schanges++;
      }
      schanges++;
    }

    return schanges;
  }

  public String toString() {
    return "InvokeStatic " + tid;
  }
}
