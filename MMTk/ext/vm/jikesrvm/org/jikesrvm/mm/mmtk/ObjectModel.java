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
package org.jikesrvm.mm.mmtk;

import org.mmtk.utility.scan.MMType;
import org.mmtk.utility.Constants;
import org.mmtk.utility.alloc.Allocator;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.VM_Magic;
import org.jikesrvm.VM_Memory;
import org.jikesrvm.VM_ObjectModel;
import org.jikesrvm.VM_JavaHeaderConstants;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_Array;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.memorymanagers.mminterface.Selected;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 *
 * @author Steve Blackburn
 * @author Perry Cheng
 *
 */
@Uninterruptible public final class ObjectModel extends org.mmtk.vm.ObjectModel implements Constants, VM_Constants {

  protected Offset getArrayBaseOffset() { return VM_JavaHeaderConstants.ARRAY_BASE_OFFSET; }

  /**
   * Copy an object using a plan's allocCopy to get space and install
   * the forwarding pointer.  On entry, <code>from</code> must have
   * been reserved for copying by the caller.  This method calls the
   * plan's <code>getStatusForCopy()</code> method to establish a new
   * status word for the copied object and <code>postCopy()</code> to
   * allow the plan to perform any post copy actions.
   *
   * @param from the address of the object to be copied
   * @return the address of the new object
   */
  @Inline
  public ObjectReference copy(ObjectReference from, int allocator) { 
    Object[] tib = VM_ObjectModel.getTIB(from);
    VM_Type type = VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]);
    
    if (type.isClassType())
      return copyScalar(from, tib, type.asClass(), allocator);
    else
      return copyArray(from, tib, type.asArray(), allocator);
  }

  @Inline
  private ObjectReference copyScalar(ObjectReference from, Object[] tib,
                                       VM_Class type, int allocator) { 
    int bytes = VM_ObjectModel.bytesRequiredWhenCopied(from, type);
    int align = VM_ObjectModel.getAlignment(type, from);
    int offset = VM_ObjectModel.getOffsetForAlignment(type, from);
    Selected.Collector plan = Selected.Collector.get();
    allocator = plan.copyCheckAllocator(from, bytes, align, allocator);
    Address region = MM_Interface.allocateSpace(plan, bytes, align, offset,
                                                allocator, from);
    Object toObj = VM_ObjectModel.moveObject(region, from, bytes, false, type);
    ObjectReference to = ObjectReference.fromObject(toObj);
    plan.postCopy(to, ObjectReference.fromObject(tib), bytes, allocator);
    MMType mmType = (MMType) type.getMMType();
    mmType.profileCopy(bytes);
    return to;
  }

  @Inline
  private ObjectReference copyArray(ObjectReference from, Object[] tib,
                                      VM_Array type, int allocator) { 
    int elements = VM_Magic.getArrayLength(from);
    int bytes = VM_ObjectModel.bytesRequiredWhenCopied(from, type, elements);
    int align = VM_ObjectModel.getAlignment(type, from);
    int offset = VM_ObjectModel.getOffsetForAlignment(type, from);
    Selected.Collector plan = Selected.Collector.get();
    allocator = plan.copyCheckAllocator(from, bytes, align, allocator);
    Address region = MM_Interface.allocateSpace(plan, bytes, align, offset,
                                                allocator, from);
    Object toObj = VM_ObjectModel.moveObject(region, from, bytes, false, type);
    ObjectReference to = ObjectReference.fromObject(toObj);
    plan.postCopy(to, ObjectReference.fromObject(tib), bytes, allocator);
    if (type == VM_Type.CodeArrayType) {
      // sync all moved code arrays to get icache and dcache in sync
      // immediately.
      int dataSize = bytes - VM_ObjectModel.computeHeaderSize(VM_Magic.getObjectType(toObj));
      VM_Memory.sync(to.toAddress(), dataSize);
    }
    MMType mmType = (MMType) type.getMMType();
    mmType.profileCopy(bytes);
    return to;
  }
  
  /**
   * Return the size of a given object, in bytes
   * 
   * @param object The object whose size is being queried
   * @return The size (in bytes) of the given object.
   */
  static int getObjectSize(ObjectReference object) {
    Object[] tib = VM_ObjectModel.getTIB(object);
    VM_Type type = VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]);
    
    if (type.isClassType())
      return VM_ObjectModel.bytesRequiredWhenCopied(object, type.asClass());
    else
      return VM_ObjectModel.bytesRequiredWhenCopied(object, type.asArray(), VM_Magic.getArrayLength(object));
  }

  /**
   * Copy an object to be pointer to by the to address. This is required 
   * for delayed-copy collectors such as compacting collectors. During the 
   * collection, MMTk reserves a region in the heap for an object as per
   * requirements found from ObjectModel and then asks ObjectModel to 
   * determine what the object's reference will be post-copy.
   * 
   * @param from the address of the object to be copied
   * @param to The target location.
   * @param region The start (or an address less than) the region that was reserved for this object.
   * @return Address The address past the end of the copied object
   */
  @Inline
  public Address copyTo(ObjectReference from, ObjectReference to, Address region) { 
    Object[] tib = VM_ObjectModel.getTIB(from);
    VM_Type type = VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]);
    int bytes;
    
    boolean copy = (from != to);
    
    if (copy) {
      if (type.isClassType()) {
        VM_Class classType = type.asClass();
        bytes = VM_ObjectModel.bytesRequiredWhenCopied(from, classType);
        VM_ObjectModel.moveObject(from, to, bytes, false, classType);
      } else {
      VM_Array arrayType = type.asArray();
        int elements = VM_Magic.getArrayLength(from);
        bytes = VM_ObjectModel.bytesRequiredWhenCopied(from, arrayType, elements);
        VM_ObjectModel.moveObject(from, to, bytes, false, arrayType);
      }
    } else {
      bytes = getCurrentSize(to);
    }
    
    Address start = VM_ObjectModel.objectStartRef(to);
    Allocator.fillAlignmentGap(region, start);
    
    return start.plus(bytes);
  }

  /**
   * Return the reference that an object will be refered to after it is copied
   * to the specified region. Used in delayed-copy collectors such as compacting
   * collectors.
   *
   * @param from The object to be copied.
   * @param to The region to be copied to.
   * @return The resulting reference.
   */
  public ObjectReference getReferenceWhenCopiedTo(ObjectReference from, Address to) {
    return ObjectReference.fromObject(VM_ObjectModel.getReferenceWhenCopiedTo(from, to));
  }
  
  /**
   * Gets a pointer to the address just past the end of the object.
   * 
   * @param object The objecty.
   */
  public Address getObjectEndAddress(ObjectReference object) {
    return VM_ObjectModel.getObjectEndAddress(object);
  }
  
  /**
   * Return the size required to copy an object
   *
   * @param object The object whose size is to be queried
   * @return The size required to copy <code>obj</code>
   */
  public int getSizeWhenCopied(ObjectReference object) {
    return VM_ObjectModel.bytesRequiredWhenCopied(object);
  }
  
  /**
   * Return the alignment requirement for a copy of this object
   *
   * @param object The object whose size is to be queried
   * @return The alignment required for a copy of <code>obj</code>
   */
  public int getAlignWhenCopied(ObjectReference object) {
    Object[] tib = VM_ObjectModel.getTIB(object);
    VM_Type type = VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]);
    if (type.isArrayType()) {
      return VM_ObjectModel.getAlignment(type.asArray(), object);
    } else {
      return VM_ObjectModel.getAlignment(type.asClass(), object);
    }
  }
  
  /**
   * Return the alignment offset requirements for a copy of this object
   *
   * @param object The object whose size is to be queried
   * @return The alignment offset required for a copy of <code>obj</code>
   */
  public int getAlignOffsetWhenCopied(ObjectReference object) {
    Object[] tib = VM_ObjectModel.getTIB(object);
    VM_Type type = VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]);
    if (type.isArrayType()) {
      return VM_ObjectModel.getOffsetForAlignment(type.asArray(), object);
    } else {
      return VM_ObjectModel.getOffsetForAlignment(type.asClass(), object);
    }
  }
  
  /**
   * Return the size used by an object
   *
   * @param object The object whose size is to be queried
   * @return The size of <code>obj</code>
   */
  public int getCurrentSize(ObjectReference object) {
    return VM_ObjectModel.bytesUsed(object);
  }

  /**
   * Return the next object in the heap under contiguous allocation
   */
  public ObjectReference getNextObject(ObjectReference object) {
    return VM_ObjectModel.getNextObject(object);
  }

  /**
   * Return an object reference from knowledge of the low order word
   */
  public ObjectReference getObjectFromStartAddress(Address start) {
    return VM_ObjectModel.getObjectFromStartAddress(start);
  }
  
  /**
   * Get the type descriptor for an object.
   *
   * @param ref address of the object
   * @return byte array with the type descriptor
   */
  public byte [] getTypeDescriptor(ObjectReference ref) {
    VM_Atom descriptor = VM_Magic.getObjectType(ref).getDescriptor();
    return descriptor.toByteArray();
  }

  @Inline
  public int getArrayLength(ObjectReference object) { 
    return VM_Magic.getArrayLength(object.toObject());
  }
  /**
   * Tests a bit available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param idx the index of the bit
   */
  public boolean testAvailableBit(ObjectReference object, int idx) {
    return VM_ObjectModel.testAvailableBit(object.toObject(), idx);
  }

  /**
   * Sets a bit available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param idx the index of the bit
   * @param flag <code>true</code> to set the bit to 1,
   * <code>false</code> to set it to 0
   */
  public void setAvailableBit(ObjectReference object, int idx,
                                     boolean flag) {
    VM_ObjectModel.setAvailableBit(object.toObject(), idx, flag);
  }

  /**
   * Attempts to set the bits available for memory manager use in an
   * object.  The attempt will only be successful if the current value
   * of the bits matches <code>oldVal</code>.  The comparison with the
   * current value and setting are atomic with respect to other
   * allocators.
   *
   * @param object the address of the object
   * @param oldVal the required current value of the bits
   * @param newVal the desired new value of the bits
   * @return <code>true</code> if the bits were set,
   * <code>false</code> otherwise
   */
  public boolean attemptAvailableBits(ObjectReference object,
                                             Word oldVal, Word newVal) {
    return VM_ObjectModel.attemptAvailableBits(object.toObject(), oldVal,
                                               newVal);
  }

  /**
   * Gets the value of bits available for memory manager use in an
   * object, in preparation for setting those bits.
   *
   * @param object the address of the object
   * @return the value of the bits
   */
  public Word prepareAvailableBits(ObjectReference object) {
    return VM_ObjectModel.prepareAvailableBits(object.toObject());
  }

  /**
   * Sets the bits available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param val the new value of the bits
   */
  public void writeAvailableBitsWord(ObjectReference object, Word val) {
    VM_ObjectModel.writeAvailableBitsWord(object.toObject(), val);
  }

  /**
   * Read the bits available for memory manager use in an object.
   *
   * @param object the address of the object
   * @return the value of the bits
   */
  public Word readAvailableBitsWord(ObjectReference object) {
    return VM_ObjectModel.readAvailableBitsWord(object);
  }

  /**
   * Gets the offset of the memory management header from the object
   * reference address.  XXX The object model / memory manager
   * interface should be improved so that the memory manager does not
   * need to know this.
   *
   * @return the offset, relative the object reference address
   */
  /* AJG: Should this be a variable rather than method? */
  public Offset GC_HEADER_OFFSET() {
    return VM_ObjectModel.GC_HEADER_OFFSET;
  }

  /**
   * Returns the lowest address of the storage associated with an object.
   *
   * @param object the reference address of the object
   * @return the lowest address of the object
   */
  @Inline
  public Address objectStartRef(ObjectReference object) { 
    return VM_ObjectModel.objectStartRef(object);
  }

  /**
   * Returns an address guaranteed to be inside the storage assocatied
   * with and object.
   *
   * @param object the reference address of the object
   * @return an address inside the object
   */
  public Address refToAddress(ObjectReference object) {
    return VM_ObjectModel.getPointerInMemoryRegion(object);
  }

  /**
   * Checks if a reference of the given type in another object is
   * inherently acyclic.  The type is given as a TIB.
   *
   * @return <code>true</code> if a reference of the type is
   * inherently acyclic
   */
  @Inline
  public boolean isAcyclic(ObjectReference typeRef) { 
    Object type;
    Object[] tib = VM_Magic.addressAsObjectArray(typeRef.toAddress());
    if (true) {  // necessary to avoid an odd compiler bug
      type = VM_Magic.getObjectAtOffset(tib, Offset.fromIntZeroExtend(TIB_TYPE_INDEX));
    } else {
      type = tib[TIB_TYPE_INDEX];
    }
    return VM_Magic.objectAsType(type).isAcyclicReference();
  }

  /**
   * Return the type object for a give object
   *
   * @param object The object whose type is required
   * @return The type object for <code>object</code>
   */
  @Inline
  public MMType getObjectType(ObjectReference object) { 
    Object obj = object.toObject();
    Object[] tib = VM_ObjectModel.getTIB(obj);
    if (VM.VerifyAssertions) {
      if (tib == null || VM_ObjectModel.getObjectType(tib) != VM_Type.JavaLangObjectArrayType) {
        VM.sysWriteln("getObjectType: objRef = ", object.toAddress(), "   tib = ", VM_Magic.objectAsAddress(tib));
        VM.sysWriteln("               tib's type is not Object[]");
        VM._assert(false);
      }
    }
    VM_Type vmType = VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]);
    if (VM.VerifyAssertions) {
      if (vmType == null) {
        VM.sysWriteln("getObjectType: null type for object = ", object);
        VM._assert(false);
      }
    }
    if (VM.VerifyAssertions) VM._assert(vmType.getMMType() != null);
    return (MMType) vmType.getMMType();
  }
}

