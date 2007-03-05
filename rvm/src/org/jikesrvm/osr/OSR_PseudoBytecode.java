/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package org.jikesrvm.osr;
import org.jikesrvm.classloader.*;
import org.jikesrvm.VM_SizeConstants;

import org.vmmagic.unboxed.*;
/**
 * OSR_PseudoBytecode is super class of all pseudo instructions.
 *
 * @author Feng Qian
 */

public abstract class OSR_PseudoBytecode 
  implements VM_BytecodeConstants, OSR_Constants , VM_SizeConstants{

  public OSR_PseudoBytecode next;

  public abstract byte[] getBytes();
  public abstract int getSize();
  public abstract int stackChanges();

  public static byte[] initBytes(int size, int instruction) {
    byte[] code = new byte[size];
    code[0] = (byte)JBC_impdep1;
    code[1] = (byte)instruction;
    return code;
  }

  public static void int2bytes(byte[] to, int p, int value) {

    for (int i=3; i>=0; i--) {
      to[p++] = (byte)((value >>> (i<<LOG_BITS_IN_BYTE)) & 0x0FF);
    }
  }

  public static void long2bytes(byte[] to, int p, long value) {
    
    for (int i=7; i>=0; i--) {
      to[p++] = (byte)((value >>> (i<<LOG_BITS_IN_BYTE)) & 0x0FF);
    }
  }

  public static void word2bytes(byte[] to, int p, Word value) {
    
    for (int i=BYTES_IN_ADDRESS-1; i>=0; i--) {
      to[p++] = (byte)(value.rshl(i<<LOG_BITS_IN_BYTE).toInt() & 0x0FF);
    }
  }

  public static void float2bytes(byte[] to, int p, float value) {
    
    int v = Float.floatToIntBits(value);
    int2bytes(to, p, v);
  }

  public static void double2bytes(byte[] to, int p, double value) {
    
    long v = Double.doubleToLongBits(value);
    long2bytes(to, p, v);
  }

  public static byte[] makeOUUcode(int op, int idx) {
    
    byte[] codes = new byte[3];
    codes[0] = (byte)op;
    codes[1] = (byte)((idx>>8)&0x0FF);
    codes[2] = (byte)(idx&0x0FF);
    
    return codes;  
  }

  public static byte[] makeWOUUcode(int op, int idx) {
    byte[] codes = new byte[4];
    codes[0] = (byte)JBC_wide;
    codes[1] = (byte)op;
    codes[2] = (byte)((idx>>8)&0x0FF);
    codes[3] = (byte)(idx&0x0FF);
    return codes;
  }

  public static byte[] makeOUcode(int op, int idx) { 
    byte[] codes = new byte[2];
    codes[0] = (byte)op;
    codes[1] = (byte)(idx & 0x0FF);
    return codes;
  }
}
