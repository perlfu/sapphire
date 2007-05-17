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
package org.jikesrvm.objectmodel;

import org.jikesrvm.ArchitectureSpecific.VM_Assembler;
import org.jikesrvm.VM;
import org.jikesrvm.VM_Configuration;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.classloader.VM_Array;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.memorymanagers.mminterface.MM_Constants;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Memory;
import org.jikesrvm.scheduler.VM_Lock;
import org.jikesrvm.scheduler.VM_ThinLock;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Defines the JavaHeader portion of the object header for the
 * default JikesRVM object model.
 * The default object model uses a two word header. <p>
 *
 * One word holds a TIB pointer. <p>
 *
 * The other word ("status word") contains an inline thin lock,
 * either the hash code or hash code state, and a few unallocated
 * bits that can be used for other purposes.
 * If {@link VM_JavaHeaderConstants#ADDRESS_BASED_HASHING} is false,
 * then to implement default hashcodes, Jikes RVM uses a 10 bit hash code
 * that is completely stored in the status word, which is laid out as
 * shown below:
 * <pre>
 *      TTTT TTTT TTTT TTTT TTTT HHHH HHHH HHAA
 * T = thin lock bits
 * H = hash code
 * A = available for use by GCHeader and/or MiscHeader.
 * </pre>
 *
 * If {@link VM_JavaHeaderConstants#ADDRESS_BASED_HASHING ADDRESS_BASED_HASHING} is true,
 * then Jikes RVM uses two bits of the status word to record the hash code state in
 * a typical three state scheme ({@link #HASH_STATE_UNHASHED}, {@link #HASH_STATE_HASHED},
 * and {@link #HASH_STATE_HASHED_AND_MOVED}). In this case, the status word is laid
 * out as shown below:
 * <pre>
 *      TTTT TTTT TTTT TTTT TTTT TTHH AAAA AAAA
 * T = thin lock bits
 * H = hash code state bits
 * A = available for use by GCHeader and/or MiscHeader.
 * </pre>
 */
@Uninterruptible
public class VM_JavaHeader implements VM_JavaHeaderConstants {

  protected static final int SCALAR_HEADER_SIZE = JAVA_HEADER_BYTES + OTHER_HEADER_BYTES;
  protected static final int ARRAY_HEADER_SIZE = SCALAR_HEADER_SIZE + ARRAY_LENGTH_BYTES;

  /** offset of object reference from the lowest memory word */
  protected static final int OBJECT_REF_OFFSET = ARRAY_HEADER_SIZE;  // from start to ref
  protected static final Offset TIB_OFFSET = JAVA_HEADER_OFFSET;
  protected static final Offset STATUS_OFFSET = TIB_OFFSET.plus(STATUS_BYTES);
  protected static final Offset AVAILABLE_BITS_OFFSET =
      VM.LittleEndian ? (STATUS_OFFSET) : (STATUS_OFFSET.plus(STATUS_BYTES - 1));

  /*
   * Used for 10 bit header hash code in header (!ADDRESS_BASED_HASHING)
   */
  protected static final int HASH_CODE_SHIFT = 2;
  protected static final Word HASH_CODE_MASK = Word.one().lsh(10).minus(Word.one()).lsh(HASH_CODE_SHIFT);
  protected static Word hashCodeGenerator; // seed for generating hash codes with copying collectors.

  /** How many bits are allocated to a thin lock? */
  public static final int NUM_THIN_LOCK_BITS = ADDRESS_BASED_HASHING ? 22 : 20;
  /** How many bits to shift to get the thin lock? */
  public static final int THIN_LOCK_SHIFT = ADDRESS_BASED_HASHING ? 10 : 12;

  /** The alignment value **/
  public static final int ALIGNMENT_VALUE = VM_JavaHeaderConstants.ALIGNMENT_VALUE;
  public static final int LOG_MIN_ALIGNMENT = VM_JavaHeaderConstants.LOG_MIN_ALIGNMENT;

  static {
    if (VM.VerifyAssertions) {
      VM._assert(VM_MiscHeader.REQUESTED_BITS + MM_Constants.GC_HEADER_BITS <= NUM_AVAILABLE_BITS);
    }
  }

  /**
   * Return the TIB offset.
   */
  public static Offset getTibOffset() {
    return TIB_OFFSET;
  }

  /**
   * What is the offset of the first word after the class?
   * For use by VM_ObjectModel.layoutInstanceFields
   */
  public static Offset objectEndOffset(VM_Class klass) {
    return Offset.fromIntSignExtend(klass.getInstanceSizeInternal() - OBJECT_REF_OFFSET);
  }

  /**
   * What is the first word after the class?
   */
  public static Address getObjectEndAddress(Object obj, VM_Class type) {
    int size = type.getInstanceSize();
    if (ADDRESS_BASED_HASHING && DYNAMIC_HASH_OFFSET) {
      Word hashState = VM_Magic.objectAsAddress(obj).loadWord(STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
        size += HASHCODE_BYTES;
      }
    }
    return VM_Magic.objectAsAddress(obj).plus(VM_Memory.alignUp(size, VM_SizeConstants.BYTES_IN_INT) -
                                              OBJECT_REF_OFFSET);
  }

  /**
   * What is the first word after the array?
   */
  public static Address getObjectEndAddress(Object obj, VM_Array type, int numElements) {
    int size = type.getInstanceSize(numElements);
    if (ADDRESS_BASED_HASHING && DYNAMIC_HASH_OFFSET) {
      Word hashState = VM_Magic.getWordAtOffset(obj, STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
        size += HASHCODE_BYTES;
      }
    }
    return VM_Magic.objectAsAddress(obj).plus(VM_Memory.alignUp(size, VM_SizeConstants.BYTES_IN_INT) -
                                              OBJECT_REF_OFFSET);
  }

  /**
   * What is the offset of the first word of the class?
   */
  public static int objectStartOffset(VM_Class klass) {
    return -OBJECT_REF_OFFSET;
  }

  /**
   * What is the last word of the header from an out-to-in perspective?
   */
  public static int getHeaderEndOffset() {
    return SCALAR_HEADER_SIZE - OBJECT_REF_OFFSET;
  }

  /**
   * How small is the minimum object header size?
   * Can be used to pick chunk sizes for allocators.
   */
  public static int minimumObjectSize() {
    return SCALAR_HEADER_SIZE;
  }

  /**
   * Given a reference, return an address which is guaranteed to be inside
   * the memory region allocated to the object.
   */
  public static Address getPointerInMemoryRegion(ObjectReference ref) {
    return ref.toAddress().plus(TIB_OFFSET);
  }

  /**
   * Get the TIB for an object.
   */
  public static Object[] getTIB(Object o) {
    return VM_Magic.getObjectArrayAtOffset(o, TIB_OFFSET);
  }

  /**
   * Set the TIB for an object.
   */
  public static void setTIB(Object ref, Object[] tib) {
    VM_Magic.setObjectAtOffset(ref, TIB_OFFSET, tib);
  }

  /**
   * Set the TIB for an object.
   */
  @Interruptible
  public static void setTIB(BootImageInterface bootImage, Address refOffset, Address tibAddr, VM_Type type) {
    bootImage.setAddressWord(refOffset.plus(TIB_OFFSET), tibAddr.toWord(), false);
  }

  /**
   * how many bytes are needed when the scalar object is copied by GC?
   */
  public static int bytesRequiredWhenCopied(Object fromObj, VM_Class type) {
    int size = type.getInstanceSize();
    if (ADDRESS_BASED_HASHING) {
      Word hashState = VM_Magic.getWordAtOffset(fromObj, STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.NE(HASH_STATE_UNHASHED)) {
        size += HASHCODE_BYTES;
      }
    }
    return size;
  }

  /**
   * how many bytes are used by the scalar object?
   */
  public static int bytesUsed(Object obj, VM_Class type) {
    int size = type.getInstanceSize();
    if (MM_Constants.MOVES_OBJECTS) {
      if (ADDRESS_BASED_HASHING) {
        Word hashState = VM_Magic.getWordAtOffset(obj, STATUS_OFFSET).and(HASH_STATE_MASK);
        if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
          size += HASHCODE_BYTES;
        }
      }
    }
    return size;
  }

  /**
   * how many bytes are needed when the array object is copied by GC?
   */
  public static int bytesRequiredWhenCopied(Object fromObj, VM_Array type, int numElements) {
    int size = type.getInstanceSize(numElements);
    if (ADDRESS_BASED_HASHING) {
      Word hashState = VM_Magic.getWordAtOffset(fromObj, STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.NE(HASH_STATE_UNHASHED)) {
        size += HASHCODE_BYTES;
      }
    }
    return VM_Memory.alignUp(size, VM_SizeConstants.BYTES_IN_INT);
  }

  /**
   * how many bytes are used by the array object?
   */
  public static int bytesUsed(Object obj, VM_Array type, int numElements) {
    int size = type.getInstanceSize(numElements);
    if (MM_Constants.MOVES_OBJECTS) {
      if (ADDRESS_BASED_HASHING) {
        Word hashState = VM_Magic.getWordAtOffset(obj, STATUS_OFFSET).and(HASH_STATE_MASK);
        if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
          size += HASHCODE_BYTES;
        }
      }
    }
    return VM_Memory.alignUp(size, VM_SizeConstants.BYTES_IN_INT);
  }

  /**
   * Map from the object ref to the lowest address of the storage
   * associated with the object
   */
  @Inline
  public static Address objectStartRef(ObjectReference obj) {
    if (MM_Constants.MOVES_OBJECTS) {
      if (ADDRESS_BASED_HASHING && !DYNAMIC_HASH_OFFSET) {
        Word hashState = obj.toAddress().loadWord(STATUS_OFFSET).and(HASH_STATE_MASK);
        if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
          return obj.toAddress().minus(OBJECT_REF_OFFSET + HASHCODE_BYTES);
        }
      }
    }
    return obj.toAddress().minus(OBJECT_REF_OFFSET);
  }

  /**
   * Get an object reference from the address the lowest word of the
   * object was allocated.  In general this required that we are using
   * a dynamic hash offset or not using address based
   * hashing. However, the GC algorithm could safely do this in the
   * nursery so we can't assert DYNAMIC_HASH_OFFSET.
   */
  public static ObjectReference getObjectFromStartAddress(Address start) {
    if ((start.loadWord().toInt() & ALIGNMENT_MASK) == ALIGNMENT_MASK) {
      start = start.plus(VM_SizeConstants.BYTES_IN_WORD);
      if ((start.loadWord().toInt() & ALIGNMENT_MASK) == ALIGNMENT_MASK) {
        start = start.plus(VM_SizeConstants.BYTES_IN_WORD);
        if ((start.loadWord().toInt() & ALIGNMENT_MASK) == ALIGNMENT_MASK) {
          return ObjectReference.nullReference();
        }
      }
    }

    return start.plus(OBJECT_REF_OFFSET).toObjectReference();
  }

  /**
   * Get an object reference from the address the lowest word of the
   * object was allocated.
   */
  public static ObjectReference getScalarFromStartAddress(Address start) {
    return getObjectFromStartAddress(start);
  }

  /**
   * Get an object reference from the address the lowest word of the
   * object was allocated.
   */
  public static ObjectReference getArrayFromStartAddress(Address start) {
    return getObjectFromStartAddress(start);
  }

  /**
   * Get the next object in the heap under contiguous
   * allocation. Handles alignment issues only when there are no GC or
   * Misc header words. In the case there are we probably have to ask
   * MM_Interface to distinguish this for us.
   */
  protected static ObjectReference getNextObject(ObjectReference obj, int size) {
    if (VM.VerifyAssertions) VM._assert(OTHER_HEADER_BYTES == 0);

    return getObjectFromStartAddress(obj.toAddress().plus(size).minus(OBJECT_REF_OFFSET));
  }

  /**
   * Get the next scalar in the heap under contiguous
   * allocation. Handles alignment issues
   */
  public static ObjectReference getNextObject(ObjectReference obj, VM_Class type) {
    return getObjectFromStartAddress(getObjectEndAddress(obj.toObject(), type));
  }

  /**
   * Get the next array in the heap under contiguous
   * allocation. Handles alignment issues
   */
  public static ObjectReference getNextObject(ObjectReference obj, VM_Array type, int numElements) {
    return getObjectFromStartAddress(getObjectEndAddress(obj.toObject(), type, numElements));
  }

  /**
   * Get the reference of an array when copied to the specified region.
   */
  @Inline
  public static Object getReferenceWhenCopiedTo(Object obj, Address to, VM_Array type) {
    return getReferenceWhenCopiedTo(obj, to);
  }

  /**
   * Get the reference of a scalar when copied to the specified region.
   */
  @Inline
  public static Object getReferenceWhenCopiedTo(Object obj, Address to, VM_Class type) {
    return getReferenceWhenCopiedTo(obj, to);
  }

  @Inline
  protected static Object getReferenceWhenCopiedTo(Object obj, Address to) {
    if (ADDRESS_BASED_HASHING && !DYNAMIC_HASH_OFFSET) {
      // Read the hash state (used below)
      Word statusWord = VM_Magic.getWordAtOffset(obj, STATUS_OFFSET);
      Word hashState = statusWord.and(HASH_STATE_MASK);
      if (hashState.EQ(HASH_STATE_HASHED)) {
        to = to.plus(HASHCODE_BYTES);
      }
    }
    return VM_Magic.addressAsObject(to.plus(OBJECT_REF_OFFSET));
  }

  /**
   * Copy a scalar to the given raw storage address
   */
  @Inline
  public static Object moveObject(Address toAddress, Object fromObj, int numBytes, boolean noGCHeader, VM_Class type) {

    // We copy arrays and scalars the same way
    return moveObject(toAddress, fromObj, null, numBytes, noGCHeader);
  }

  /**
   * Copy an array to the given location.
   */
  @Inline
  public static Object moveObject(Object fromObj, Object toObj, int numBytes, boolean noGCHeader, VM_Class type) {

    // We copy arrays and scalars the same way
    return moveObject(Address.zero(), fromObj, toObj, numBytes, noGCHeader);
  }

  /**
   * Copy an array to the given raw storage address
   */
  @Inline
  public static Object moveObject(Address toAddress, Object fromObj, int numBytes, boolean noGCHeader, VM_Array type) {

    // We copy arrays and scalars the same way
    return moveObject(toAddress, fromObj, null, numBytes, noGCHeader);
  }

  /**
   * Copy an array to the given location.
   */
  @Inline
  public static Object moveObject(Object fromObj, Object toObj, int numBytes, boolean noGCHeader, VM_Array type) {

    // We copy arrays and scalars the same way
    return moveObject(Address.zero(), fromObj, toObj, numBytes, noGCHeader);
  }

  /**
   * Copy an object to the given raw storage address
   */
  @Inline
  public static Object moveObject(Address toAddress, Object fromObj, Object toObj, int numBytes, boolean noGCHeader) {

    // Default values
    int copyBytes = numBytes;
    int objRefOffset = OBJECT_REF_OFFSET;
    Word statusWord = Word.zero();
    Word hashState = HASH_STATE_UNHASHED;

    if (ADDRESS_BASED_HASHING) {
      // Read the hash state (used below)
      statusWord = VM_Magic.getWordAtOffset(fromObj, STATUS_OFFSET);
      hashState = statusWord.and(HASH_STATE_MASK);
      if (hashState.EQ(HASH_STATE_HASHED)) {
        // We do not copy the hashcode, but we do allocate it
        copyBytes -= HASHCODE_BYTES;

        if (!DYNAMIC_HASH_OFFSET) {
          // The hashcode is the first word, so we copy to object one word higher
          if (toObj == null) {
            toAddress = toAddress.plus(HASHCODE_BYTES);
          }
        }
      } else if (!DYNAMIC_HASH_OFFSET && hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
        // Simple operation (no hash state change), but one word larger header
        objRefOffset += HASHCODE_BYTES;
      }
    }

    if (toObj != null) {
      toAddress = VM_Magic.objectAsAddress(toObj).minus(OBJECT_REF_OFFSET);
    }

    // Low memory word of source object
    Address fromAddress = VM_Magic.objectAsAddress(fromObj).minus(objRefOffset);

    // Was the GC header stolen at allocation time? ok for arrays and scalars.
    if (noGCHeader) {
      if (VM.VerifyAssertions) {
        // No object can be hashed and moved unless using dynamic hash offset
        VM._assert(hashState.NE(HASH_STATE_HASHED_AND_MOVED) || DYNAMIC_HASH_OFFSET);
      }

      // We copy less but start higher in memory.
      copyBytes -= GC_HEADER_BYTES;
      objRefOffset -= GC_HEADER_BYTES;
      toAddress = toAddress.plus(GC_HEADER_BYTES);
    }

    // Do the copy
    VM_Memory.aligned32Copy(toAddress, fromAddress, copyBytes);
    toObj = VM_Magic.addressAsObject(toAddress.plus(objRefOffset));

    // Do we need to copy the hash code?
    if (hashState.EQ(HASH_STATE_HASHED)) {
      int hashCode = VM_Magic.objectAsAddress(fromObj).toWord().rshl(VM_SizeConstants.LOG_BYTES_IN_ADDRESS).toInt();
      if (DYNAMIC_HASH_OFFSET) {
        VM_Magic.setIntAtOffset(toObj, Offset.fromIntSignExtend(numBytes - objRefOffset - HASHCODE_BYTES), hashCode);
      } else {
        VM_Magic.setIntAtOffset(toObj, HASHCODE_OFFSET, (hashCode << 1) | ALIGNMENT_MASK);
      }
      VM_Magic.setWordAtOffset(toObj, STATUS_OFFSET, statusWord.or(HASH_STATE_HASHED_AND_MOVED));
      if (VM_ObjectModel.HASH_STATS) VM_ObjectModel.hashTransition2++;
    }

    return toObj;
  }

  /**
   * Get the hash code of an object.
   */
  public static int getObjectHashCode(Object o) {
    if (ADDRESS_BASED_HASHING) {
      if (MM_Constants.MOVES_OBJECTS) {
        Word hashState = VM_Magic.getWordAtOffset(o, STATUS_OFFSET).and(HASH_STATE_MASK);
        if (hashState.EQ(HASH_STATE_HASHED)) {
          // HASHED, NOT MOVED
          return VM_Magic.objectAsAddress(o).toWord().rshl(VM_SizeConstants.LOG_BYTES_IN_ADDRESS).toInt();
        } else if (hashState.EQ(HASH_STATE_HASHED_AND_MOVED)) {
          // HASHED AND MOVED
          if (DYNAMIC_HASH_OFFSET) {
            // Read the size of this object.
            VM_Type t = VM_Magic.getObjectType(o);
            int offset =
                t.isArrayType() ? t.asArray().getInstanceSize(VM_Magic.getArrayLength(o)) -
                                  OBJECT_REF_OFFSET : t.asClass().getInstanceSize() - OBJECT_REF_OFFSET;
            return VM_Magic.getIntAtOffset(o, Offset.fromIntSignExtend(offset));
          } else {
            return (VM_Magic.getIntAtOffset(o, HASHCODE_OFFSET) >>> 1);
          }
        } else {
          // UNHASHED
          Word tmp;
          do {
            tmp = VM_Magic.prepareWord(o, STATUS_OFFSET);
          } while (!VM_Magic.attemptWord(o, STATUS_OFFSET, tmp, tmp.or(HASH_STATE_HASHED)));
          if (VM_ObjectModel.HASH_STATS) VM_ObjectModel.hashTransition1++;
          return getObjectHashCode(o);
        }
      } else {
        return VM_Magic.objectAsAddress(o).toWord().rshl(VM_SizeConstants.LOG_BYTES_IN_ADDRESS).toInt();
      }
    } else { // 10 bit hash code in status word
      int hashCode = VM_Magic.getWordAtOffset(o, STATUS_OFFSET).and(HASH_CODE_MASK).rshl(HASH_CODE_SHIFT).toInt();
      if (hashCode != 0) {
        return hashCode;
      }
      return installHashCode(o);
    }
  }

  /** Install a new hashcode (only used if !ADDRESS_BASED_HASHING) */
  @NoInline
  protected static int installHashCode(Object o) {
    Word hashCode;
    do {
      hashCodeGenerator = hashCodeGenerator.plus(Word.one().lsh(HASH_CODE_SHIFT));
      hashCode = hashCodeGenerator.and(HASH_CODE_MASK);
    } while (hashCode.isZero());
    while (true) {
      Word statusWord = VM_Magic.prepareWord(o, STATUS_OFFSET);
      if (!(statusWord.and(HASH_CODE_MASK).isZero())) // some other thread installed a hashcode
      {
        return statusWord.and(HASH_CODE_MASK).rshl(HASH_CODE_SHIFT).toInt();
      }
      if (VM_Magic.attemptWord(o, STATUS_OFFSET, statusWord, statusWord.or(hashCode))) {
        return hashCode.rshl(HASH_CODE_SHIFT).toInt();  // we installed the hash code
      }
    }
  }

  /**
   * Get the offset of the thin lock word in this object
   */
  public static Offset getThinLockOffset(Object o) {
    return STATUS_OFFSET;
  }

  /**
   * what is the default offset for a thin lock?
   */
  public static Offset defaultThinLockOffset() {
    return STATUS_OFFSET;
  }

  /**
   * Allocate a thin lock word for instances of the type
   * (if they already have one, then has no effect).
   */
  public static void allocateThinLock(VM_Type t) {
    // nothing to do (all objects have thin locks in this object model);
  }

  /**
   * Generic lock
   */
  public static void genericLock(Object o) {
    VM_ThinLock.lock(o, STATUS_OFFSET);
  }

  /**
   * Generic unlock
   */
  public static void genericUnlock(Object o) {
    VM_ThinLock.unlock(o, STATUS_OFFSET);
  }

  /**
   * @param obj an object
   * @param thread a thread
   * @return <code>true</code> if the lock on obj is currently owned
   *         by thread <code>false</code> if it is not.
   */
  public static boolean holdsLock(Object obj, VM_Thread thread) {
    return VM_ThinLock.holdsLock(obj, STATUS_OFFSET, thread);
  }

  /**
   * Obtains the heavy-weight lock, if there is one, associated with the
   * indicated object.  Returns <code>null</code>, if there is no
   * heavy-weight lock associated with the object.
   *
   * @param o the object from which a lock is desired
   * @param create if true, create heavy lock if none found
   * @return the heavy-weight lock on the object (if any)
   */
  public static VM_Lock getHeavyLock(Object o, boolean create) {
    return VM_ThinLock.getHeavyLock(o, STATUS_OFFSET, create);
  }

  /**
   * Non-atomic read of word containing available bits
   */
  public static Word readAvailableBitsWord(Object o) {
    return VM_Magic.getWordAtOffset(o, STATUS_OFFSET);
  }

  /**
   * Non-atomic read of byte containing available bits
   */
  public static byte readAvailableBitsByte(Object o) {
    return VM_Magic.getByteAtOffset(o, AVAILABLE_BITS_OFFSET);
  }

  /**
   * Non-atomic write of word containing available bits
   */
  public static void writeAvailableBitsWord(Object o, Word val) {
    VM_Magic.setWordAtOffset(o, STATUS_OFFSET, val);
  }

  /**
   * Non-atomic write of word containing available bits
   */
  @Interruptible
  public static void writeAvailableBitsWord(BootImageInterface bootImage, Address ref, Word val) {
    bootImage.setAddressWord(ref.plus(STATUS_OFFSET), val, false);
  }

  /**
   * Non-atomic write of byte containing available bits
   */
  public static void writeAvailableBitsByte(Object o, byte val) {
    VM_Magic.setByteAtOffset(o, AVAILABLE_BITS_OFFSET, val);
  }

  /**
   * Return true if argument bit is 1, false if it is 0
   */
  public static boolean testAvailableBit(Object o, int idx) {
    Word mask = Word.fromIntSignExtend(1 << idx);
    Word status = VM_Magic.getWordAtOffset(o, STATUS_OFFSET);
    return mask.and(status).NE(Word.zero());
  }

  /**
   * Set argument bit to 1 if value is true, 0 if value is false
   */
  public static void setAvailableBit(Object o, int idx, boolean flag) {
    Word status = VM_Magic.getWordAtOffset(o, STATUS_OFFSET);
    if (flag) {
      Word mask = Word.fromIntSignExtend(1 << idx);
      VM_Magic.setWordAtOffset(o, STATUS_OFFSET, status.or(mask));
    } else {
      Word mask = Word.fromIntSignExtend(1 << idx).not();
      VM_Magic.setWordAtOffset(o, STATUS_OFFSET, status.and(mask));
    }
  }

  /**
   * Freeze the other bits in the byte containing the available bits
   * so that it is safe to update them using setAvailableBits.
   */
  public static void initializeAvailableByte(Object o) {
    if (!ADDRESS_BASED_HASHING) getObjectHashCode(o);
  }

  /**
   * A prepare on the word containing the available bits
   */
  public static Word prepareAvailableBits(Object o) {
    return VM_Magic.prepareWord(o, STATUS_OFFSET);
  }

  /**
   * An attempt on the word containing the available bits
   */
  public static boolean attemptAvailableBits(Object o, Word oldVal, Word newVal) {
    return VM_Magic.attemptWord(o, STATUS_OFFSET, oldVal, newVal);
  }

  /**
   * Given the smallest base address in a region, return the smallest
   * object reference that could refer to an object in the region.
   */
  public static Address minimumObjectRef(Address regionBaseAddr) {
    return regionBaseAddr.plus(OBJECT_REF_OFFSET);
  }

  /**
   * Given the largest base address in a region, return the largest
   * object reference that could refer to an object in the region.
   */
  public static Address maximumObjectRef(Address regionHighAddr) {
    return regionHighAddr.plus(OBJECT_REF_OFFSET - SCALAR_HEADER_SIZE);
  }

  /**
   * Compute the header size of an instance of the given type.
   */
  public static int computeScalarHeaderSize(VM_Class type) {
    return SCALAR_HEADER_SIZE;
  }

  /**
   * Compute the header size of an instance of the given type.
   */
  public static int computeArrayHeaderSize(VM_Array type) {
    return ARRAY_HEADER_SIZE;
  }

  /**
   * Return the desired aligment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument VM_Class.
   * @param t VM_Class instance being created
   */
  public static int getAlignment(VM_Class t) {
    return t.getAlignment();
  }

  /**
   * Return the desired aligment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument VM_Class.
   * @param t VM_Class instance being copied
   * @param obj the object being copied
   */
  public static int getAlignment(VM_Class t, Object obj) {
    return t.getAlignment();
  }

  /**
   * Return the desired aligment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument VM_Array.
   * @param t VM_Array instance being created
   */
  public static int getAlignment(VM_Array t) {
    return t.getAlignment();
  }

  /**
   * Return the desired aligment of the alignment point returned by
   * getOffsetForAlignment in instances of the argument VM_Array.
   * @param t VM_Array instance being copied
   * @param obj the object being copied
   */
  public static int getAlignment(VM_Array t, Object obj) {
    return t.getAlignment();
  }

  /**
   * Return the offset relative to physical beginning of object
   * that must be aligned.
   * @param t VM_Class instance being created
   */
  public static int getOffsetForAlignment(VM_Class t) {
    /* Align the first field - note that this is one word off from
       the reference. */
    return SCALAR_HEADER_SIZE;
  }

  /**
   * Return the offset relative to physical beginning of object
   * that must be aligned.
   * @param t VM_Class instance being copied
   * @param obj the object being copied
   */
  public static int getOffsetForAlignment(VM_Class t, ObjectReference obj) {
    if (ADDRESS_BASED_HASHING && !DYNAMIC_HASH_OFFSET) {
      Word hashState = obj.toAddress().loadWord(STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.NE(HASH_STATE_UNHASHED)) {
        return SCALAR_HEADER_SIZE + HASHCODE_BYTES;
      }
    }
    return SCALAR_HEADER_SIZE;
  }

  /**
   * Return the offset relative to physical beginning of object that must
   * be aligned.
   * @param t VM_Array instance being created
   */
  public static int getOffsetForAlignment(VM_Array t) {
    /* although array_header_size == object_ref_offset we say this
       because the whole point is to align the object ref */
    return OBJECT_REF_OFFSET;
  }

  /**
   * Return the offset relative to physical beginning of object that must
   * be aligned.
   * @param t VM_Array instance being copied
   * @param obj the object being copied
   */
  public static int getOffsetForAlignment(VM_Array t, ObjectReference obj) {
    /* although array_header_size == object_ref_offset we say this
       because the whole point is to align the object ref */
    if (ADDRESS_BASED_HASHING && !DYNAMIC_HASH_OFFSET) {
      Word hashState = obj.toAddress().loadWord(STATUS_OFFSET).and(HASH_STATE_MASK);
      if (hashState.NE(HASH_STATE_UNHASHED)) {
        return OBJECT_REF_OFFSET + HASHCODE_BYTES;
      }
    }
    return OBJECT_REF_OFFSET;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param ptr the raw storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   */
  public static Object initializeScalarHeader(Address ptr, Object[] tib, int size) {
    // (TIB set by VM_ObjectModel)
    Object ref = VM_Magic.addressAsObject(ptr.plus(OBJECT_REF_OFFSET));
    return ref;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param bootImage The bootimage being written
   * @param ptr  The object ref to the storage to be initialized
   * @param tib  The TIB of the instance being created
   * @param size The number of bytes allocated by the GC system for this object.
   */
  @Interruptible
  public static Address initializeScalarHeader(BootImageInterface bootImage, Address ptr, Object[] tib, int size) {
    Address ref = ptr.plus(OBJECT_REF_OFFSET);
    return ref;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * @param ptr the raw storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   */
  public static Object initializeArrayHeader(Address ptr, Object[] tib, int size) {
    Object ref = VM_Magic.addressAsObject(ptr.plus(OBJECT_REF_OFFSET));
    // (TIB and array length set by VM_ObjectModel)
    return ref;
  }

  /**
   * Perform any required initialization of the JAVA portion of the header.
   * XXX This documentation probably needs fixing TODO
   *
   * @param bootImage the bootimage being written
   * @param ptr  the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @return Document ME TODO XXX
   */
  @Interruptible
  public static Address initializeArrayHeader(BootImageInterface bootImage, Address ptr, Object[] tib, int size) {
    Address ref = ptr.plus(OBJECT_REF_OFFSET);
    // (TIB set by BootImageWriter; array length set by VM_ObjectModel)
    return ref;
  }

  /**
   * For low level debugging of GC subsystem.
   * Dump the header word(s) of the given object reference.
   * @param ref the object reference whose header should be dumped
   */
  public static void dumpHeader(Object ref) {
    // TIB dumped in VM_ObjectModel
    VM.sysWrite(" STATUS=");
    VM.sysWriteHex(VM_Magic.getWordAtOffset(ref, STATUS_OFFSET).toAddress());
  }

  /**
   * The following method will emit code that moves a reference to an
   * object's TIB into a destination register.
   *
   * @param asm the assembler object to emit code with
   * @param dest the number of the destination register
   * @param object the number of the register holding the object reference
   */
  @Interruptible
  public static void baselineEmitLoadTIB(VM_Assembler asm, int dest, int object) {
    VM_Configuration.archHelper.baselineEmitLoadTIB(asm, dest, object, TIB_OFFSET);
  }
}
