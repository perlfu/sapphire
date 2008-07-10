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


/**
 * BC_LoadFloatConst: ldc, ldc_w
 */
public class LoadFloatConst extends PseudoBytecode {
  private static final int bsize = 6;
  private final int fbits;

  public LoadFloatConst(int bits) {
    this.fbits = bits;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_LoadFloatConst);
    int2bytes(codes, 2, fbits);
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
    return +1;
  }

  public String toString() {
    return "LoadFloat " + Float.intBitsToFloat(fbits);
  }
}
