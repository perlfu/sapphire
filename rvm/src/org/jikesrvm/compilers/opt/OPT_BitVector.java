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
package org.jikesrvm.compilers.opt;

import java.io.Serializable;

/**
 * OPT_BitVector.java
 *
 * implements a bit vector
 */
public final class OPT_BitVector implements Serializable {
  /** Support for serialization */
  static final long serialVersionUID = 6961578653974090041L;

  private static final int LOG_BITS_PER_UNIT = 5;
  private static final int MASK = 0xffffffff;
  private static final int LOW_MASK = 0x1f;
  private final int[] bits;
  private final int nbits;

  /**
   * Convert bitIndex to a subscript into the bits[] array.
   */
  private static int subscript(int bitIndex) {
    return bitIndex >> LOG_BITS_PER_UNIT;
  }

  /**
   * Creates an empty string with the specified size.
   * @param nbits the size of the string
   */
  public OPT_BitVector(int nbits) {
    // subscript(nbits) is the length of the array needed to
    // hold nbits
    bits = new int[subscript(nbits) + 1];
    this.nbits = nbits;
  }

  /**
   * Creates a copy of a Bit String
   * @param s the string to copy
   */
  public OPT_BitVector(OPT_BitVector s) {
    bits = new int[s.bits.length];
    this.nbits = s.nbits;
    System.arraycopy(s.bits, 0, this.bits, 0, s.bits.length);
  }

  /**
   * Sets all bits.
   */
  public void setAll() {
    for (int i = 0; i < bits.length; i++) {
      bits[i] = MASK;
    }
  }

  /**
   * Sets a bit.
   * @param bit the bit to be set
   */
  public void set(int bit) {
    int shiftBits = bit & LOW_MASK;
    bits[subscript(bit)] |= (1 << shiftBits);
  }

  /**
   * Clears all bits.
   */
  public void clearAll() {
    for (int i = 0; i < bits.length; i++) {
      bits[i] = 0;
    }
  }

  /**
   * Clears a bit.
   * @param bit the bit to be cleared
   */
  public void clear(int bit) {
    int shiftBits = bit & LOW_MASK;
    bits[subscript(bit)] &= ~(1 << shiftBits);
  }

  /**
   * Gets a bit.
   * @param bit the bit to be gotten
   */
  public boolean get(int bit) {
    int shiftBits = bit & LOW_MASK;
    int n = subscript(bit);
    return ((bits[n] & (1 << shiftBits)) != 0);
  }

  /**
   * Logically NOT this bit string
   */
  public void not() {
    for (int i = 0; i < bits.length; i++) {
      bits[i] ^= MASK;
    }
  }

  /**
   * Return the NOT of a bit string
   */
  public static OPT_BitVector not(OPT_BitVector s) {
    OPT_BitVector b = new OPT_BitVector(s);
    b.not();
    return b;
  }

  /**
   * Logically ANDs this bit set with the specified set of bits.
   * @param set the bit set to be ANDed with
   */
  public void and(OPT_BitVector set) {
    if (this == set) {
      return;
    }
    int n = bits.length;
    for (int i = n; i-- > 0;) {
      bits[i] &= set.bits[i];
    }
  }

  /**
   * Return a new bit string as the AND of two others.
   */
  public static OPT_BitVector and(OPT_BitVector b1, OPT_BitVector b2) {
    OPT_BitVector b = new OPT_BitVector(b1);
    b.and(b2);
    return b;
  }

  /**
   * Logically ORs this bit set with the specified set of bits.
   * @param set the bit set to be ORed with
   */
  public void or(OPT_BitVector set) {
    if (this == set) { // should help alias analysis
      return;
    }
    int setLength = set.bits.length;
    for (int i = setLength; i-- > 0;) {
      bits[i] |= set.bits[i];
    }
  }

  /**
   * Return a new OPT_BitVector as the OR of two others
   */
  public static OPT_BitVector or(OPT_BitVector b1, OPT_BitVector b2) {
    OPT_BitVector b = new OPT_BitVector(b1);
    b.or(b2);
    return b;
  }

  /**
   * Logically XORs this bit set with the specified set of bits.
   * @param set the bit set to be XORed with
   */
  public void xor(OPT_BitVector set) {
    int setLength = set.bits.length;
    for (int i = setLength; i-- > 0;) {
      bits[i] ^= set.bits[i];
    }
  }

  /**
   * Check if the intersection of the two sets is empty
   * @param other the set to check intersection with
   */
  public boolean intersectionEmpty(OPT_BitVector other) {
    int n = bits.length;
    for (int i = n; i-- > 0;) {
      if ((bits[i] & other.bits[i]) != 0) return false;
    }
    return true;
  }

  /**
   * Copies the values of the bits in the specified set into this set.
   * @param set the bit set to copy the bits from
   */
  public void copyBits(OPT_BitVector set) {
    System.arraycopy(set.bits, 0, this.bits, 0, set.bits.length);
  }

  /**
   * Gets the hashcode.
   */
  public int hashCode() {
    int h = 1234;
    for (int i = bits.length; --i >= 0;) {
      h ^= bits[i] * (i + 1);
    }
    return h;
  }

  /**
   * How many bits are set?
   */
  public int populationCount() {
    int count = 0;
    for (int bit : bits) {
      count += OPT_Bits.populationCount(bit);
    }
    return count;
  }

  /**
   * Calculates and returns the set's size in bits.
   * The maximum element in the set is the size - 1st element.
   */
  public int length() {
    return bits.length << LOG_BITS_PER_UNIT;
  }

  /**
   * Compares this object against the specified object.
   * @param obj the object to compare with
   * @return true if the objects are the same; false otherwise.
   */
  public boolean equals(Object obj) {
    if ((obj != null) && (obj instanceof OPT_BitVector)) {
      if (this == obj) { // should help alias analysis
        return true;
      }
      OPT_BitVector set = (OPT_BitVector) obj;
      int n = bits.length;
      if (n != set.bits.length) return false;
      for (int i = n; i-- > 0;) {
        if (bits[i] != set.bits[i]) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public boolean isZero() {
    int setLength = bits.length;
    for (int i = setLength; i-- > 0;) {
      if (bits[i] != 0) return false;
    }
    return true;
  }

  public OPT_BitVector dup() {
    return new OPT_BitVector(this);
  }

  /**
   * Converts the OPT_BitVector to a String.
   */
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    boolean needSeparator = false;
    buffer.append('{');
//    int limit = length();
    int limit = this.nbits;
    for (int i = 0; i < limit; i++) {
      if (get(i)) {
        if (needSeparator) {
          buffer.append(", ");
        } else {
          needSeparator = true;
        }
        buffer.append(i);
      }
    }
    buffer.append('}');
    return buffer.toString();
  }
}
