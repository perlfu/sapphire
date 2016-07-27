/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.semispace;

import org.mmtk.plan.*;
//import org.mmtk.plan.otfsapphire.OTFSapphire;
//import org.mmtk.plan.otfsapphire.ReplicatingSpace;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-mutator thread</i> behavior
 * and state for the <i>SS</i> plan, which implements a full-heap
 * semi-space collector.<p>
 *
 * Specifically, this class defines <i>SS</i> mutator-time allocation
 * and per-mutator thread collection semantics (flushing and restoring
 * per-mutator allocator state).<p>
 *
 * See {@link SS} for an overview of the semi-space algorithm.<p>
 *
 * @see SS
 * @see SSCollector
 * @see StopTheWorldMutator
 * @see MutatorContext
 */
@Uninterruptible
public class SSMutator extends StopTheWorldMutator {
  /****************************************************************************
   * Instance fields
   */
  protected final CopyLocal ss;
  
  //protected boolean barrierEnable = false;
  private final boolean barrierEnable = false;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public SSMutator() {
    ss = new CopyLocal();
  }

  /**
   * Called before the MutatorContext is used, but after the context has been
   * fully registered and is visible to collection.
   */
  @Override
  public void initMutator(int id) {
    super.initMutator(id);
    ss.rebind(SS.toSpace());
  }

  /****************************************************************************
   *
   * Mutator-time allocation
   */

  /**
   * {@inheritDoc}
   */
  @Override
  @Inline
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == SS.ALLOC_SS)
      return ss.alloc(bytes, align, offset);
    else
      return super.alloc(bytes, align, offset, allocator, site);
  }

  @Override
  @Inline
  public void postAlloc(ObjectReference object, ObjectReference typeRef,
      int bytes, int allocator) {
    if (allocator == SS.ALLOC_SS) return;
    super.postAlloc(object, typeRef, bytes, allocator);
  }

  @Override
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == SS.copySpace0 || space == SS.copySpace1) return ss;
    return super.getAllocatorFromSpace(space);
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * {@inheritDoc}
   */
  @Override
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == SS.PREPARE) {
      super.collectionPhase(phaseId, primary);
      return;
    }

    if (phaseId == SS.RELEASE) {
      super.collectionPhase(phaseId, primary);
      // rebind the allocation bump pointer to the appropriate semispace.
      ss.rebind(SS.toSpace());
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
  *
  * Write barrier
  */

  @NoInline
  private boolean doNothing(Object src, Address slot, Word metaDataA, Word metaDataB, int mode) {
    int i;
    for (i = mode; i < (metaDataA.toInt() + metaDataB.toInt()); ++i) {
      slot.store(ObjectReference.fromObject(src));
    }
    return (i < (metaDataA.toInt() * metaDataB.toInt()));
  }
  
 /**
  * Write a boolean. Take appropriate write barrier actions.
  * <p>
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param value The value of the new boolean
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  */
 @Override
 @Inline
 public void booleanWrite(ObjectReference src, Address slot, boolean value, Word metaDataA, Word metaDataB, int mode) {
   if (barrierEnable) {
     doNothing(src, slot, metaDataA, metaDataB, mode);
   } else {
     VM.barriers.booleanWrite(src, value, metaDataA, metaDataB, mode);
   }
 }
 
 /**
  * A number of booleans are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy).
  * Thus, <code>dst</code> is the mutated object. Take appropriate write barrier actions.
  * <p>
  * @param src The source array
  * @param srcOffset The starting source offset
  * @param dst The destination array
  * @param dstOffset The starting destination offset
  * @param bytes The number of bytes to be copied
  * @return True if the update was performed by the barrier, false if left to the caller
  */
 public boolean booleanBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
   // Not actually called yet - something to optimise later
   return false;
 }

 /**
  * Write a byte. Take appropriate write barrier actions.
  * <p>
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param value The value of the new byte
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  */
 @Override
 @Inline
 public void byteWrite(ObjectReference src, Address slot, byte value, Word metaDataA, Word metaDataB, int mode) {
   if (barrierEnable) {
     doNothing(src, slot, metaDataA, metaDataB, mode);
   } else {
     VM.barriers.byteWrite(src, value, metaDataA, metaDataB, mode);
   }
 }

 /**
  * A number of bytes are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy). Thus,
  * <code>dst</code> is the mutated object. Take appropriate write barrier actions.
  * <p>
  * @param src The source array
  * @param srcOffset The starting source offset
  * @param dst The destination array
  * @param dstOffset The starting destination offset
  * @param bytes The number of bytes to be copied
  * @return True if the update was performed by the barrier, false if left to the caller
  */
 public boolean byteBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
   // Not actually called yet - something to optimise later
   return false;
 }

 /**
  * Write a char. Take appropriate write barrier actions.
  * <p>
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param value The value of the new char
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  */
 @Override
 @Inline
 public void charWrite(ObjectReference src, Address slot, char value, Word metaDataA, Word metaDataB, int mode) {
   if (barrierEnable) {
     doNothing(src, slot, metaDataA, metaDataB, mode);
   } else {
     VM.barriers.charWrite(src, value, metaDataA, metaDataB, mode);
   }
 }

 /**
  * A number of chars are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy). Thus,
  * <code>dst</code> is the mutated object. Take appropriate write barrier actions.
  * <p>
  * @param src The source array
  * @param srcOffset The starting source offset
  * @param dst The destination array
  * @param dstOffset The starting destination offset
  * @param bytes The number of bytes to be copied
  * @return True if the update was performed by the barrier, false if left to the caller
  */
 public boolean charBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
   // Not actually called yet - something to optimise later
   return false;
 }

 /**
  * Write a double. Take appropriate write barrier actions.
  * <p>
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param value The value of the new double
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  */ 
 @Override
 @Inline
 public void doubleWrite(ObjectReference src, Address slot, double value, Word metaDataA, Word metaDataB, int mode) {
   if (barrierEnable) {
     doNothing(src, slot, metaDataA, metaDataB, mode);
   } else {
     VM.barriers.doubleWrite(src, value, metaDataA, metaDataB, mode);
   }
 }  
 
 /**
  * A number of doubles are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy). Thus,
  * <code>dst</code> is the mutated object. Take appropriate write barrier actions.
  * <p>
  * @param src The source array
  * @param srcOffset The starting source offset
  * @param dst The destination array
  * @param dstOffset The starting destination offset
  * @param bytes The number of bytes to be copied
  * @return True if the update was performed by the barrier, false if left to the caller
  */
 public boolean doubleBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
   // Not actually called yet - something to optimise later
   return false;
 }

 /**
  * Write a float. Take appropriate write barrier actions.
  * <p>
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param value The value of the new float
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  */ 
 @Override
 @Inline
 public void floatWrite(ObjectReference src, Address slot, float value, Word metaDataA, Word metaDataB, int mode) {
   if (barrierEnable) {
     doNothing(src, slot, metaDataA, metaDataB, mode);
   } else {
     VM.barriers.floatWrite(src, value, metaDataA, metaDataB, mode);
   }
 }

 /**
  * A number of floats are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy). Thus,
  * <code>dst</code> is the mutated object. Take appropriate write barrier actions.
  * <p>
  * @param src The source array
  * @param srcOffset The starting source offset
  * @param dst The destination array
  * @param dstOffset The starting destination offset
  * @param bytes The number of bytes to be copied
  * @return True if the update was performed by the barrier, false if left to the caller
  */
 public boolean floatBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
   // Not actually called yet - something to optimise later
   return false;
 }

 /**
  * Write a int. Take appropriate write barrier actions.
  * <p>
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param value The value of the new int
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  */ 
 @Override
 @Inline
 public void intWrite(ObjectReference src, Address slot, int value, Word metaDataA, Word metaDataB, int mode) {
   if (barrierEnable) {
     doNothing(src, slot, metaDataA, metaDataB, mode);
   } else {
     VM.barriers.intWrite(src, value, metaDataA, metaDataB, mode);
   }
 }

 /**
  * A number of ints are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy). Thus,
  * <code>dst</code> is the mutated object. Take appropriate write barrier actions.
  * <p>
  * @param src The source array
  * @param srcOffset The starting source offset
  * @param dst The destination array
  * @param dstOffset The starting destination offset
  * @param bytes The number of bytes to be copied
  * @return True if the update was performed by the barrier, false if left to the caller
  */
 public boolean intBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
   // Not actually called yet - something to optimise later
   return false;
 }

 /**
  * Attempt to atomically exchange the value in the given slot with the passed replacement value.
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param old The old int to be swapped out
  * @param value The new int
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  * @return True if the swap was successful.
  */
 public boolean intTryCompareAndSwap(ObjectReference src, Address slot, int old, int value, Word metaDataA, Word metaDataB,
                                     int mode) {
   return VM.barriers.intTryCompareAndSwap(src, old, value, metaDataA, metaDataB, mode);
 }

 /**
  * Write a long. Take appropriate write barrier actions.
  * <p>
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param value The value of the new long
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  */
 @Override
 @Inline
 public void longWrite(ObjectReference src, Address slot, long value, Word metaDataA, Word metaDataB, int mode) {
   if (barrierEnable) {
     doNothing(src, slot, metaDataA, metaDataB, mode);
   } else {
     VM.barriers.longWrite(src, value, metaDataA, metaDataB, mode);
   }
 }
 
 /**
  * A number of longs are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy). Thus,
  * <code>dst</code> is the mutated object. Take appropriate write barrier actions.
  * <p>
  * @param src The source array
  * @param srcOffset The starting source offset
  * @param dst The destination array
  * @param dstOffset The starting destination offset
  * @param bytes The number of bytes to be copied
  * @return True if the update was performed by the barrier, false if left to the caller
  */
 public boolean longBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
   // Not actually called yet - something to optimise later
   return false;
 }

 /**
  * Attempt to atomically exchange the value in the given slot with the passed replacement value.
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param old The old long to be swapped out
  * @param value The new long
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  * @return True if the swap was successful.
  */
 public boolean longTryCompareAndSwap(ObjectReference src, Address slot, long old, long value, Word metaDataA, Word metaDataB,
                                      int mode) {
   return VM.barriers.longTryCompareAndSwap(src, old, value, metaDataA, metaDataB, mode);
 }

 /**
  * Write a short. Take appropriate write barrier actions.
  * <p>
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param value The value of the new short
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  */
 @Override
 @Inline
 public void shortWrite(ObjectReference src, Address slot, short value, Word metaDataA, Word metaDataB, int mode) {
   if (barrierEnable) {
     doNothing(src, slot, metaDataA, metaDataB, mode);
   } else {
     VM.barriers.shortWrite(src, value, metaDataA, metaDataB, mode);
   }
 }
 
 /**
  * A number of shorts are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy). Thus,
  * <code>dst</code> is the mutated object. Take appropriate write barrier actions.
  * <p>
  * @param src The source array
  * @param srcOffset The starting source offset
  * @param dst The destination array
  * @param dstOffset The starting destination offset
  * @param bytes The number of bytes to be copied
  * @return True if the update was performed by the barrier, false if left to the caller
  */
 public boolean shortBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
   // Not actually called yet - something to optimise later
   return false;
 }

 /**
  * Write a Word. Take appropriate write barrier actions.
  * <p>
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param value The value of the new Word
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  */
 @Override
 @Inline
 public void wordWrite(ObjectReference src, Address slot, Word value, Word metaDataA, Word metaDataB, int mode) {
   if (barrierEnable) {
     doNothing(src, slot, metaDataA, metaDataB, mode);
   } else {
     VM.barriers.wordWrite(src, value, metaDataA, metaDataB, mode);
   }
 }

 /**
  * Write a Address during GC into toSpace. Take appropriate write barrier actions.
  * <p>
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param value The value of the new Address
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  */
 public void addressWriteDuringGC(ObjectReference src, Address slot, Address value, Word metaDataA, Word metaDataB, int mode) {
   VM.barriers.addressWrite(src, value, metaDataA, metaDataB, mode);
 }

 /**
  * Attempt to atomically exchange the value in the given slot with the passed replacement value.
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param old The old long to be swapped out
  * @param value The new long
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  * @return True if the swap was successful.
  */
 public boolean wordTryCompareAndSwap(ObjectReference src, Address slot, Word old, Word value, Word metaDataA, Word metaDataB,
                                      int mode) {
   return VM.barriers.wordTryCompareAndSwap(src, old, value, metaDataA, metaDataB, mode);
 }

 /**
  * Write a Address. Take appropriate write barrier actions.
  * <p>
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param value The value of the new Address
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  */
 @Override
 @Inline
 public void addressWrite(ObjectReference src, Address slot, Address value, Word metaDataA, Word metaDataB, int mode) {
   if (barrierEnable) {
     doNothing(src, slot, metaDataA, metaDataB, mode);
   } else {
     VM.barriers.addressWrite(src, value, metaDataA, metaDataB, mode);
   }
 }

 /**
  * Write a Extent. Take appropriate write barrier actions.
  * <p>
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param value The value of the new Extent
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  */
 @Override
 @Inline
 public void extentWrite(ObjectReference src, Address slot, Extent value, Word metaDataA, Word metaDataB, int mode) {
   if (barrierEnable) {
     doNothing(src, slot, metaDataA, metaDataB, mode);
   } else {
     VM.barriers.extentWrite(src, value, metaDataA, metaDataB, mode);
   }
 }
 
 /**
  * Write a Offset. Take appropriate write barrier actions.
  * <p>
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param value The value of the new Offset
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  */ 
 @Override
 @Inline
 public void offsetWrite(ObjectReference src, Address slot, Offset value, Word metaDataA, Word metaDataB, int mode) {
   if (barrierEnable) {
     doNothing(src, slot, metaDataA, metaDataB, mode);
   } else {
     VM.barriers.offsetWrite(src, value, metaDataA, metaDataB, mode);
   }
 }

 /**
  * Write an object reference. Take appropriate write barrier actions.
  * <p>
  * <b>By default do nothing, override if appropriate.</b>
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param value The value of the new reference
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  */
 @Override
 @Inline
 public void objectReferenceWrite(ObjectReference src, Address slot, ObjectReference value, Word metaDataA, Word metaDataB,
     int mode) {
   if (barrierEnable) {
     doNothing(src, slot, metaDataA, metaDataB, mode);
   } else {
     VM.barriers.objectReferenceWrite(src, value, metaDataA, metaDataB, mode);
   }
 }

 /**
  * A number of references are about to be copied from object <code>src</code> to object <code>dst</code> (as in an array copy).
  * Thus, <code>dst</code> is the mutated object. Take appropriate write barrier actions.
  * <p>
  * @param src The source array
  * @param srcOffset The starting source offset
  * @param dst The destination array
  * @param dstOffset The starting destination offset
  * @param bytes The number of bytes to be copied
  * @return True if the update was performed by the barrier, false if left to the caller (always false in this case).
  */
 public boolean objectReferenceBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
   // Not actually called yet - something to optimise later
   return false;
 }

 /**
  * Attempt to atomically exchange the value in the given slot with the passed replacement value. If a new reference is created, we
  * must then take appropriate write barrier actions.
  * <p>
  * <b>By default do nothing, override if appropriate.</b>
  * @param src The object into which the new reference will be stored
  * @param slot The address into which the new reference will be stored.
  * @param old The old reference to be swapped out
  * @param tgt The target of the new reference
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  * @param mode The context in which the store occurred
  * @return True if the swap was successful.
  */

 @Override
 @Inline
 public boolean objectReferenceTryCompareAndSwap(ObjectReference src, Address slot, ObjectReference old, ObjectReference tgt,
     Word metaDataA, Word metaDataB, int mode) {
   if (barrierEnable) {
     return doNothing(src, slot, metaDataA, metaDataB, mode);
   } else {
     return VM.barriers.objectReferenceTryCompareAndSwap(src, old, tgt, metaDataA, metaDataB, mode);
   }
 }

 /**
  * A new reference is about to be created in a location that is not
  * a regular heap object.  Take appropriate write barrier actions.<p>
  *
  * In this case, we remember the address of the source of the
  * pointer if the new reference points into the nursery from
  * non-nursery space.
  *
  * @param slot The address into which the new reference will be stored.
  * @param tgt The target of the new reference
  * @param metaDataA A value that assists the host VM in creating a store
  * @param metaDataB A value that assists the host VM in creating a store
  */
 @Inline
 public final void objectReferenceNonHeapWrite(Address slot, ObjectReference tgt, Word metaDataA, Word metaDataB) {
   VM.barriers.objectReferenceNonHeapWrite(slot, tgt, metaDataA, metaDataB);
 }

 private void writeBarrierAssertions(Address slot, ObjectReference src) {
 }

 private void writeBarrierAssertionsObjectReferenceValue(ObjectReference value, Address slot) {
   if (value.isNull()) return;

   if (!Space.isMappedObject(value)) {
     Log.write("attempt to write ");
     Log.write(value);
     Log.write(" to ");
     Log.writeln(slot);
     VM.objectModel.dumpObject(value);
   }
   if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Space.isMappedObject(value));
 }
  
 @Inline
 @Override
 public void addressWriteToReferenceTable(ObjectReference src, Address slot, Address value, Word metaDataA, Word metaDataB, int mode) {
   if (barrierEnable) {
     doNothing(src, slot, metaDataA, metaDataB, mode);
   } else {
     VM.barriers.addressWriteToReferenceTable(src, value, metaDataA, metaDataB, mode);
   }
 }
 
 /**
  * Read a reference type. In a concurrent collector this may
  * involve adding the referent to the marking queue.
  *
  * @param ref The referent being read.
  * @return The new referent.
  */
 @Inline
 @Override
 public ObjectReference javaLangReferenceReadBarrier(ObjectReference ref) {
   // don't need this because we are running an insertion barrier not a deletion barrier
   if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
   return ObjectReference.nullReference();
 }
 
 @Override
 @Inline
 public boolean objectReferenceCompare(ObjectReference refA, ObjectReference refB) {
   if (barrierEnable) {
     return doNothing(refA.toObject(), refB.toAddress(), refA.toAddress().toWord(), refB.toAddress().toWord(), 0);
   } else {
     return (refA.toAddress().EQ(refB.toAddress()));
   }
 }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * Show the status of each of the allocators.
   */
  public final void show() {
    ss.show();
    los.show();
    immortal.show();
  }

}
