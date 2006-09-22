/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package com.ibm.JikesRVM.mm.mmtk;

import com.ibm.JikesRVM.VM_SizeConstants;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * $Id: Barriers.java,v 1.2 2006/06/19 06:08:15 steveb-oss Exp $ 
 *
 * @author Steve Blackburn
 * @author Perry Cheng
 *
 * @version $Revision: 1.2 $
 * @date $Date: 2006/06/19 06:08:15 $
 */
public class Barriers extends org.mmtk.vm.Barriers implements VM_SizeConstants, Uninterruptible {
  /**
   * Perform the actual write of the write barrier.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param target The value that the slot will be updated to
   * @param offset The offset from the ref (metaDataA)
   * @param locationMetadata An index of the FieldReference (metaDataB)
   * @param mode The context in which the write is occuring
   */
  public final void performWriteInBarrier(ObjectReference ref, Address slot, 
                                           ObjectReference target, Offset offset, 
                                           int locationMetadata, int mode) 
    throws InlinePragma {
    Object obj = ref.toObject();
    VM_Magic.setObjectAtOffset(obj, offset, target.toObject(), locationMetadata);  
  }

  /**
   * Atomically write a reference field of an object or array and return 
   * the old value of the reference field.
   * 
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param target The value that the slot will be updated to
   * @param offset The offset from the ref (metaDataA)
   * @param locationMetadata An index of the FieldReference (metaDataB)
   * @param mode The context in which the write is occuring
   * @return The value that was replaced by the write.
   */
  public final ObjectReference performWriteInBarrierAtomic(
                                           ObjectReference ref, Address slot,
                                           ObjectReference target, Offset offset,
                                           int locationMetadata, int mode)
    throws InlinePragma {                                
    Object obj = ref.toObject();
    Object newObject = target.toObject();
    Object oldObject;
    do {
      oldObject = VM_Magic.prepareObject(obj, offset);
    } while (!VM_Magic.attemptObject(obj, offset, oldObject, newObject));
    return ObjectReference.fromObject(oldObject); 
  }

  /**
   * Sets an element of a char array without invoking any write
   * barrier.  This method is called by the Log method, as it will be
   * used during garbage collection and needs to manipulate character
   * arrays without causing a write barrier operation.
   *
   * @param dst the destination array
   * @param index the index of the element to set
   * @param value the new value for the element
   */
  public final void setArrayNoBarrier(char [] dst, int index, char value) {
    setArrayNoBarrierStatic(dst, index, value);
  }
  public static final void setArrayNoBarrierStatic(char [] dst, int index, char value) {
    if (VM.runningVM)
      VM_Magic.setCharAtOffset(dst, Offset.fromIntZeroExtend(index << LOG_BYTES_IN_CHAR), value);
    else
      dst[index] = value;
  }

  /**
   * Gets an element of a char array without invoking any read barrier
   * or performing bounds check.
   *
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public final char getArrayNoBarrier(char [] src, int index) {
    return getArrayNoBarrierStatic(src, index);
  }
  public static final char getArrayNoBarrierStatic(char [] src, int index) {
    if (VM.runningVM)
      return VM_Magic.getCharAtOffset(src, Offset.fromIntZeroExtend(index << LOG_BYTES_IN_CHAR));
    else
      return src[index];
  }

  /**
   * Gets an element of a byte array without invoking any read barrier
   * or bounds check.
   *
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public final byte getArrayNoBarrier(byte [] src, int index) {
    return getArrayNoBarrierStatic(src, index);
  }
  public static final byte getArrayNoBarrierStatic(byte [] src, int index) {
    if (VM.runningVM)
      return VM_Magic.getByteAtOffset(src, Offset.fromIntZeroExtend(index));
    else
      return src[index];
  }

  /**
   * Gets an element of an int array without invoking any read barrier
   * or performing bounds checks.
   *
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public final int getArrayNoBarrier(int [] src, int index) {
    if (VM.runningVM)
      return VM_Magic.getIntAtOffset(src, Offset.fromIntZeroExtend(index<<LOG_BYTES_IN_INT));
    else
      return src[index];
  }

  /**
   * Gets an element of an Object array without invoking any read
   * barrier or performing bounds checks.
   *
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public final Object getArrayNoBarrier(Object [] src, int index) {
    if (VM.runningVM)
      return VM_Magic.getObjectAtOffset(src, Offset.fromIntZeroExtend(index<<LOG_BYTES_IN_ADDRESS));
    else
      return src[index];
  }
  

  /**
   * Gets an element of an array of byte arrays without causing the potential
   * thread switch point that array accesses normally cause.
   *
   * @param src the source array
   * @param index the index of the element to get
   * @return the new value of element
   */
  public final byte[] getArrayNoBarrier(byte[][] src, int index) {
    return getArrayNoBarrierStatic(src, index);
  }
  public static final byte[] getArrayNoBarrierStatic(byte[][] src, int index) {
    if (VM.runningVM)
      return VM_Magic.addressAsByteArray(VM_Magic.objectAsAddress(VM_Magic.getObjectAtOffset(src, Offset.fromIntZeroExtend(index << LOG_BYTES_IN_ADDRESS))));
    else
      return src[index];
  }
}
