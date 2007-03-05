/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.vmmagic.unboxed;

import org.vmmagic.pragma.*;
import org.jikesrvm.VM;

/**
 * The offset type is used by the runtime system and collector to denote 
 * the directed distance between two machine addresses. 
 * We use a separate type instead of the Java int type for coding clarity.
 * machine-portability (it can map to 32 bit and 64 bit integral types), 
 * and access to unsigned operations (Java does not have unsigned int types).
 * <p>
 * For efficiency and to avoid meta-circularity, the Offset class is intercepted like
 * magic and converted into the base type so no Offset object is created run-time.
 *
 * @author Perry Cheng
 * @see Address Word
 */
@Uninterruptible public final class Offset extends ArchitecturalWord {
  Offset(int value) {
    super(value, false);
  }
  Offset(int value, boolean zeroExtend) {
    super(value, zeroExtend);
  }
  Offset(long value) {
    super(value);
  }
  public boolean equals(Object o) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED); 
    return (o instanceof Offset) && ((Offset) o).value == value;
  }

  @UninterruptibleNoWarn
  public static Offset fromIntSignExtend(int address) { 
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(address);
  }

  @UninterruptibleNoWarn
  public static Offset fromIntZeroExtend(int address) { 
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(address, true);
  }

  @UninterruptibleNoWarn
  public static Offset fromLong(long offset) { 
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(offset);
  }

  @UninterruptibleNoWarn
  public static Offset zero() { 
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(0);
  }

  @UninterruptibleNoWarn
  public static Offset max() { 
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return fromIntSignExtend(-1);
  }

  public int toInt() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (int) value;
  }

  public long toLong() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (VM.BuildFor64Addr) {
      return value;
    } else {
      return 0x00000000ffffffffL & ((long) value);
    }
  }

  @UninterruptibleNoWarn
  public Word toWord() { 
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value);
  }

  @UninterruptibleNoWarn
  public Offset plus(int byteSize) { 
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(value + byteSize);
  }

  @UninterruptibleNoWarn
  public Offset plus(Offset off2) { 
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(value + off2.value);
  }

  @UninterruptibleNoWarn
  public Offset minus(int byteSize) { 
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(value - byteSize);
  }

  @UninterruptibleNoWarn
  public Offset minus(Offset off2) { 
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(value - off2.value);
  }

  public boolean EQ(Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value == off2.value;
  }

  public boolean NE(Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value != off2.value;
  }

  public boolean sLT(Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value < off2.value;
  }

  public boolean sLE(Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value <= off2.value;
  }

  public boolean sGT(Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value > off2.value;
  }

  public boolean sGE(Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value >= off2.value;
  }

  public boolean isZero() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return EQ(zero());
  }

  public boolean isMax() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return EQ(max());
  }
}

