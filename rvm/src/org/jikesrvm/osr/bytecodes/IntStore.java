/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.osr.bytecodes;


/**
 * BC_IntStore : {@code istore_<?>}, {@code istore}
 * <pre>
 *      Local number            Instruction
 *      [0, 3]                  istore_&lt;i&gt;
 *      other                   istore, wide istore
 * </pre>
 */
public class IntStore extends PseudoBytecode {
  private int bsize;
  private byte[] codes;
  private int lnum;

  public IntStore(int local) {
    this.lnum = local;
    if (local <= 255) {
      bsize = 2;
      codes = makeOUcode(JBC_istore, local);
    } else {
      bsize = 4;
      codes = makeWOUUcode(JBC_istore, local);
    }
  }

  @Override
  public byte[] getBytes() {
    return codes;
  }

  @Override
  public int getSize() {
    return bsize;
  }

  @Override
  public int stackChanges() {
    return -1;
  }

  @Override
  public String toString() {
    return "istore " + lnum;
  }
}
