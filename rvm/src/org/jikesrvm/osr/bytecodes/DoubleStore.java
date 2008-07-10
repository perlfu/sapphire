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
 * BC_DoubleStore: dstore, dstore_<l>
 */

public class DoubleStore extends PseudoBytecode {
  private int bsize;
  private byte[] codes;
  private int lnum;

  public DoubleStore(int local) {
    this.lnum = local;
    if (local <= 255) {
      bsize = 2;
      codes = makeOUcode(JBC_dstore, local);
    } else {
      bsize = 4;
      codes = makeWOUUcode(JBC_dstore, local);
    }
  }

  public byte[] getBytes() {
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
    return -2;
  }

  public String toString() {
    return "dstore " + lnum;
  }
}
