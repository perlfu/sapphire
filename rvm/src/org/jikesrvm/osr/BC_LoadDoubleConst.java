/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package org.jikesrvm.osr;
/**
 * BC_LoadDoubleConst: ldc2_w 
 * 
 * @author Feng Qian
 */
public class BC_LoadDoubleConst extends OSR_PseudoBytecode {
  private static final int bsize = 10;
  private final long dbits;

  public BC_LoadDoubleConst(long bits) {
    this.dbits = bits;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_LoadDoubleConst);
    long2bytes(codes, 2, dbits);
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
        return +2;
  }

  public String toString() {
    return "LoadDouble 0x"+ Long.toHexString(dbits) + " : "+Double.longBitsToDouble(dbits);
  }
}

