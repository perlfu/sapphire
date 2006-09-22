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
package org.mmtk.vm;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;

/**
 * $Id: Barriers.java,v 1.5 2006/06/21 07:38:13 steveb-oss Exp $
 * 
 * @author Steve Blackburn
 * @author Perry Cheng
 * 
 * @version $Revision: 1.5 $
 * @date $Date: 2006/06/21 07:38:13 $
 */
public abstract class Barriers implements Uninterruptible {
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
  public abstract void setArrayNoBarrier(char [] dst, int index, char value);

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
  public abstract void performWriteInBarrier(ObjectReference ref, Address slot,
                                           ObjectReference target, Offset offset, 
                                           int locationMetadata, int mode);

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
  public abstract ObjectReference performWriteInBarrierAtomic(
                                           ObjectReference ref, Address slot,
                                           ObjectReference target, Offset offset,
      int locationMetadata, int mode);

  /**
   * Gets an element of a char array without invoking any read barrier
   * or performing bounds check.
   * 
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public abstract char getArrayNoBarrier(char[] src, int index);

  /**
   * Gets an element of a byte array without invoking any read barrier
   * or bounds check.
   * 
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public abstract byte getArrayNoBarrier(byte[] src, int index);

  /**
   * Gets an element of an int array without invoking any read barrier
   * or performing bounds checks.
   * 
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public abstract int getArrayNoBarrier(int[] src, int index);

  /**
   * Gets an element of an Object array without invoking any read
   * barrier or performing bounds checks.
   * 
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public abstract Object getArrayNoBarrier(Object[] src, int index);

  /**
   * Gets an element of an array of byte arrays without causing the potential
   * thread switch point that array accesses normally cause.
   * 
   * @param src the source array
   * @param index the index of the element to get
   * @return the new value of element
   */
  public abstract byte[] getArrayNoBarrier(byte[][] src, int index);
}
