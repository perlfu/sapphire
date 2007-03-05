/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package org.jikesrvm.osr;

import org.vmmagic.unboxed.*;

/**
 * load a word constant on the stack
 *
 * @author Kris Venstermans
 */
public class BC_LoadWordConst extends OSR_PseudoBytecode {
  private static final int bsize = 2+BYTES_IN_ADDRESS;
  private final Word wbits;
  
  public BC_LoadWordConst(Word bits) {
    this.wbits = bits;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_LoadWordConst);
    word2bytes(codes, 2, wbits);
    return codes;
  }

  public int getSize() {
    return bsize; 
  }
 
  public int stackChanges() {
        return +1;
  }

  public String toString() {
    return "LoadWord "+wbits;  
  }
}
