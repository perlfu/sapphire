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
package org.mmtk.plan.poisoned;

import org.mmtk.plan.marksweep.MSMutator;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;

/**
 * This class implements a poisoned collector, that is essentially a test
 * case for read and write barriers in the VM.
 */
@Uninterruptible
public abstract class PoisonedMutator extends MSMutator {

  /****************************************************************************
   *
   * Write and read barriers. By default do nothing, override if
   * appropriate.
   */

  /**
   * A new reference is about to be created. Take appropriate write
   * barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @Inline
  @Override
  public void writeBarrier(ObjectReference src, Address slot, ObjectReference tgt, Offset metaDataA, int metaDataB, int mode) {
    VM.barriers.performRawWriteInBarrier(src, slot, Poisoned.poison(tgt), metaDataA, metaDataB, mode);
  }

  /**
   * Attempt to atomically exchange the value in the given slot
   * with the passed replacement value. If a new reference is
   * created, we must then take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param old The old reference to be swapped out
   * @param tgt The target of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   * @return True if the swap was successful.
   */
  @Override
  public boolean tryCompareAndSwapWriteBarrier(ObjectReference src, Address slot, ObjectReference old, ObjectReference tgt,
                                               Offset metaDataA, int metaDataB, int mode) {
    return VM.barriers.tryRawCompareAndSwapWriteInBarrier(src, slot, Poisoned.poison(old), Poisoned.poison(tgt), metaDataA, metaDataB, mode);
  }

  /**
   * A number of references are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source of the values to be copied
   * @param srcOffset The offset of the first source address, in
   * bytes, relative to <code>src</code> (in principle, this could be
   * negative).
   * @param dst The mutated object, i.e. the destination of the copy.
   * @param dstOffset The offset of the first destination address, in
   * bytes relative to <code>tgt</code> (in principle, this could be
   * negative).
   * @param bytes The size of the region being copied, in bytes.
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  @Override
  public boolean writeBarrier(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // TODO: Currently, read barriers implies that this is never used, perhaps
    //       we might want to use it sometime anyway?
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Read a reference. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object reference holding the field being read.
   * @param slot The address of the slot being read.
   * @param metaDataA A value that assists the host VM in creating a load
   * @param metaDataB A value that assists the host VM in creating a load
   * @param mode The context in which the load occurred
   * @return The reference that was read.
   */
  @Inline
  @Override
  public ObjectReference readBarrier(ObjectReference src, Address slot, Offset metaDataA, int metaDataB, int mode) {
    return Poisoned.depoison(VM.barriers.performRawReadInBarrier(src, slot, metaDataA, metaDataB, mode));
  }
}
