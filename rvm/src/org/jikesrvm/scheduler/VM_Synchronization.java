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
package org.jikesrvm.scheduler;

import org.jikesrvm.VM;
import org.jikesrvm.memorymanagers.mminterface.MM_Constants;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.runtime.VM_Magic;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Class to provide synchronization methods where java language
 * synchronization is insufficient and VM_Magic.prepare and VM_Magic.attempt
 * are at too low a level
 */
@Uninterruptible
public class VM_Synchronization {

  /**
   * Atomically swap test value to new value in the specified object and the specified field
   * @param base object containing field
   * @param offset position of field
   * @param testValue expected value of field
   * @param newValue new value of field
   * @return true => successful swap, false => field not equal to testValue
   */
  @Inline
  public static boolean tryCompareAndSwap(Object base, Offset offset, int testValue, int newValue) {
    int oldValue;
    do {
      oldValue = VM_Magic.prepareInt(base, offset);
      if (oldValue != testValue) return false;
    } while (!VM_Magic.attemptInt(base, offset, oldValue, newValue));
    return true;
  }

  /**
   * Atomically swap test value to new value in the specified object and the specified field
   * @param base object containing field
   * @param offset position of field
   * @param testValue expected value of field
   * @param newValue new value of field
   * @return true => successful swap, false => field not equal to testValue
   */
  @Inline
  public static boolean tryCompareAndSwap(Object base, Offset offset, long testValue, long newValue) {
    long oldValue;
    do {
      oldValue = VM_Magic.prepareLong(base, offset);
      if (oldValue != testValue) return false;
    } while (!VM_Magic.attemptLong(base, offset, oldValue, newValue));
    return true;
  }

  /**
   * Atomically swap test value to new value in the specified object and the specified field
   * @param base object containing field
   * @param offset position of field
   * @param testValue expected value of field
   * @param newValue new value of field
   * @return true => successful swap, false => field not equal to testValue
   */
  @Inline
  public static boolean tryCompareAndSwap(Object base, Offset offset, Object testValue, Object newValue) {
    if (MM_Constants.NEEDS_WRITE_BARRIER) {
      return MM_Interface.tryCompareAndSwapWriteBarrier(base, offset, testValue, newValue);
    } else {
      Object oldValue;
      do {
        oldValue = VM_Magic.prepareObject(base, offset);
        if (oldValue != testValue) return false;
      } while (!VM_Magic.attemptObject(base, offset, oldValue, newValue));
      return true;
    }
  }

  @Inline
  public static boolean testAndSet(Object base, Offset offset, int newValue) {
    int oldValue;
    do {
      oldValue = VM_Magic.prepareInt(base, offset);
      if (oldValue != 0) return false;
    } while (!VM_Magic.attemptInt(base, offset, oldValue, newValue));
    return true;
  }

  @Inline
  public static int fetchAndStore(Object base, Offset offset, int newValue) {
    int oldValue;
    do {
      oldValue = VM_Magic.prepareInt(base, offset);
    } while (!VM_Magic.attemptInt(base, offset, oldValue, newValue));
    return oldValue;
  }

  @Inline
  public static Address fetchAndStoreAddress(Object base, Offset offset, Address newValue) {
    Address oldValue;
    do {
      oldValue = VM_Magic.prepareAddress(base, offset);
    } while (!VM_Magic.attemptAddress(base, offset, oldValue, newValue));
    return oldValue;
  }

  @Inline
  public static int fetchAndAdd(Object base, Offset offset, int increment) {
    int oldValue;
    do {
      oldValue = VM_Magic.prepareInt(base, offset);
    } while (!VM_Magic.attemptInt(base, offset, oldValue, oldValue + increment));
    return oldValue;
  }

  @Inline
  public static int fetchAndDecrement(Object base, Offset offset, int decrement) {
    int oldValue;
    do {
      oldValue = VM_Magic.prepareInt(base, offset);
    } while (!VM_Magic.attemptInt(base, offset, oldValue, oldValue - decrement));
    return oldValue;
  }

  @Inline
  public static Address fetchAndAddAddress(Address addr, int increment) {
    Address oldValue;
    do {
      oldValue = VM_Magic.prepareAddress(VM_Magic.addressAsObject(addr), Offset.zero());
    } while (!VM_Magic.attemptAddress(VM_Magic.addressAsObject(addr),
                                      Offset.zero(),
                                      oldValue,
                                      oldValue.plus(increment)));
    return oldValue;
  }

  @Inline
  public static Address fetchAndAddAddressWithBound(Address addr, int increment, Address bound) {
    Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(increment > 0);
    do {
      oldValue = VM_Magic.prepareAddress(VM_Magic.addressAsObject(addr), Offset.zero());
      newValue = oldValue.plus(increment);
      if (newValue.GT(bound)) return Address.max();
    } while (!VM_Magic.attemptAddress(VM_Magic.addressAsObject(addr), Offset.zero(), oldValue, newValue));
    return oldValue;
  }

  @Inline
  public static Address fetchAndSubAddressWithBound(Address addr, int decrement, Address bound) {
    Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(decrement > 0);
    do {
      oldValue = VM_Magic.prepareAddress(VM_Magic.addressAsObject(addr), Offset.zero());
      newValue = oldValue.minus(decrement);
      if (newValue.LT(bound)) return Address.max();
    } while (!VM_Magic.attemptAddress(VM_Magic.addressAsObject(addr), Offset.zero(), oldValue, newValue));
    return oldValue;
  }

  @Inline
  public static Address fetchAndAddAddressWithBound(Object base, Offset offset, int increment, Address bound) {
    Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(increment > 0);
    do {
      oldValue = VM_Magic.prepareAddress(base, offset);
      newValue = oldValue.plus(increment);
      if (newValue.GT(bound)) return Address.max();
    } while (!VM_Magic.attemptAddress(base, offset, oldValue, newValue));
    return oldValue;
  }

  @Inline
  public static Address fetchAndSubAddressWithBound(Object base, Offset offset, int decrement, Address bound) {
    Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(decrement > 0);
    do {
      oldValue = VM_Magic.prepareAddress(base, offset);
      newValue = oldValue.minus(decrement);
      if (newValue.LT(bound)) return Address.max();
    } while (!VM_Magic.attemptAddress(base, offset, oldValue, newValue));
    return oldValue;
  }
}
