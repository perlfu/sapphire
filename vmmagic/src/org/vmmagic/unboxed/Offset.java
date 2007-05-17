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
package org.vmmagic.unboxed;

import org.vmmagic.Unboxed;

/**
 * To be commented
 */
@Unboxed
public final class Offset {

  public static Offset fromIntSignExtend(int address) {
    return null;
  }

  public static Offset fromIntZeroExtend(int address) {
    return null;
  }

  public static Offset zero() {
    return null;
  }

  public static Offset max() {
    return null;
  }

  public int toInt() {
    return 0;
  }

  public long toLong() {
    return 0L;
  }

  public Word toWord() {
    return null;
  }

  public Offset plus(int byteSize) {
    return null;
  }

  public Offset minus(int byteSize) {
    return null;
  }

  public Offset minus(Offset off2) {
    return null;
  }

  public boolean EQ(Offset off2) {
    return false;
  }

  public boolean NE(Offset off2) {
    return false;
  }

  public boolean sLT(Offset off2) {
    return false;
  }

  public boolean sLE(Offset off2) {
    return false;
  }

  public boolean sGT(Offset off2) {
    return false;
  }

  public boolean sGE(Offset off2) {
    return false;
  }

  public boolean isZero() {
    return false;
  }

  public boolean isMax() {
    return false;
  }
}

