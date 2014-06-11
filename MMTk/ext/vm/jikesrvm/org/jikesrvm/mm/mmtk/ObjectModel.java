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
package org.jikesrvm.mm.mmtk;

import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.VM;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.objectmodel.JavaHeader;
import org.jikesrvm.objectmodel.JavaHeaderConstants;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.mm.mminterface.DebugUtil;
import org.jikesrvm.mm.mminterface.MemoryManager;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

@Uninterruptible public final class ObjectModel extends org.mmtk.vm.ObjectModel implements org.mmtk.utility.Constants,
                                                                                           org.jikesrvm.Constants,
                                                                                           org.jikesrvm.SizeConstants {

  @Override
  protected Offset getArrayBaseOffset() { return JavaHeaderConstants.ARRAY_BASE_OFFSET; }

  @Override
  @Inline
  public ObjectReference copy(ObjectReference from, int allocator) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(from);
    RVMType type = Magic.objectAsType(tib.getType());

    if (type.isClassType())
      return copyScalar(from, tib, type.asClass(), allocator);
    else
      return copyArray(from, tib, type.asArray(), allocator);
  }

  @Inline
  private ObjectReference copyScalar(ObjectReference from, TIB tib, RVMClass type, int allocator) {
    int bytes = org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(from.toObject(), type);
    int align = org.jikesrvm.objectmodel.ObjectModel.getAlignment(type, from.toObject());
    int offset = org.jikesrvm.objectmodel.ObjectModel.getOffsetForAlignment(type, from);
    CollectorContext context = VM.activePlan.collector();
    allocator = context.copyCheckAllocator(from, bytes, align, allocator);
    Address region = MemoryManager.allocateSpace(context, bytes, align, offset,
                                                allocator, from);
    Object toObj = org.jikesrvm.objectmodel.ObjectModel.moveObject(region, from.toObject(), bytes, type);
    ObjectReference to = ObjectReference.fromObject(toObj);
    context.postCopy(from, to, ObjectReference.fromObject(tib), bytes, allocator);
    return to;
  }

  @Inline
  private ObjectReference copyArray(ObjectReference from, TIB tib, RVMArray type, int allocator) {
    int elements = Magic.getArrayLength(from.toObject());
    int bytes = org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(from.toObject(), type, elements);
    int align = org.jikesrvm.objectmodel.ObjectModel.getAlignment(type, from.toObject());
    int offset = org.jikesrvm.objectmodel.ObjectModel.getOffsetForAlignment(type, from);
    CollectorContext context = VM.activePlan.collector();
    allocator = context.copyCheckAllocator(from, bytes, align, allocator);
    Address region = MemoryManager.allocateSpace(context, bytes, align, offset,
                                                allocator, from);
    Object toObj = org.jikesrvm.objectmodel.ObjectModel.moveObject(region, from.toObject(), bytes, type);
    ObjectReference to = ObjectReference.fromObject(toObj);
    context.postCopy(from, to, ObjectReference.fromObject(tib), bytes, allocator);
    if (type == RVMType.CodeArrayType) {
      // sync all moved code arrays to get icache and dcache in sync
      // immediately.
      int dataSize = bytes - org.jikesrvm.objectmodel.ObjectModel.computeHeaderSize(Magic.getObjectType(toObj));
      org.jikesrvm.runtime.Memory.sync(to.toAddress(), dataSize);
    }
    return to;
  }

  public boolean validRef(ObjectReference obj) {
    return MemoryManager.validRef(obj);
  }

  public ObjectReference fillInBlankDoubleRelica(ObjectReference from, Address toSpace, int bytes) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(from);
    RVMType type = Magic.objectAsType(tib.getType());
    ObjectReference toObj;
    if (type.isClassType())
      toObj = ObjectReference.fromObject(org.jikesrvm.objectmodel.ObjectModel.copyScalarHeader(toSpace, from.toObject(), tib, bytes));
    else {
      int elements = Magic.getArrayLength(from.toObject());
      toObj = ObjectReference.fromObject(org.jikesrvm.objectmodel.ObjectModel.copyArrayHeader(toSpace, from.toObject(), tib, elements, bytes));
    }
    return toObj;
  }
  
  public ObjectReference createBlankReplica(ObjectReference from, int allocator) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(from);
    RVMType type = Magic.objectAsType(tib.getType());
    ObjectReference toObj;
    if (type.isClassType())
      toObj = createBlankReplicaScalar(from, tib, type.asClass(), allocator);
    else
      toObj = createBlankReplicaArray(from, tib, type.asArray(), allocator);

    return toObj;
  }

  @Inline
  private ObjectReference createBlankReplicaScalar(ObjectReference from, TIB tib, RVMClass type, int allocator) {
    int bytes = org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(from.toObject(), type);
    int align = org.jikesrvm.objectmodel.ObjectModel.getAlignment(type, from.toObject());
    int offset = org.jikesrvm.objectmodel.ObjectModel.getOffsetForAlignment(type, from);
    CollectorContext context = VM.activePlan.collector();
    allocator = context.copyCheckAllocator(from, bytes, align, allocator);
    Address region = MemoryManager.allocateSpace(context, bytes, align, offset, allocator, from);
    Object toObj = org.jikesrvm.objectmodel.ObjectModel.copyScalarHeader(region, from.toObject(), tib, bytes);
    ObjectReference to = ObjectReference.fromObject(toObj);
    context.postCopy(from, to, ObjectReference.fromObject(tib), bytes, allocator);
    return to;
  }

  @Inline
  private ObjectReference createBlankReplicaArray(ObjectReference from, TIB tib, RVMArray type, int allocator) {
    int elements = Magic.getArrayLength(from.toObject());
    int bytes = org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(from.toObject(), type, elements);
    int align = org.jikesrvm.objectmodel.ObjectModel.getAlignment(type, from.toObject());
    int offset = org.jikesrvm.objectmodel.ObjectModel.getOffsetForAlignment(type, from);
    CollectorContext context = VM.activePlan.collector();
    allocator = context.copyCheckAllocator(from, bytes, align, allocator);
    Address region = MemoryManager.allocateSpace(context, bytes, align, offset, allocator, from);
    Object toObj = org.jikesrvm.objectmodel.ObjectModel.copyArrayHeader(region, from.toObject(), tib, elements, bytes);
    ObjectReference to = ObjectReference.fromObject(toObj);
    context.postCopy(from, to, ObjectReference.fromObject(tib), bytes, allocator);
    return to;
  }

  /**
   * Return the size of a given object, in bytes
   *
   * @param object The object whose size is being queried
   * @return The size (in bytes) of the given object.
   */
  static int getObjectSize(ObjectReference object) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(object);
    RVMType type = Magic.objectAsType(tib.getType());

    if (type.isClassType())
      return org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(object.toObject(), type.asClass());
    else
      return org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(object.toObject(), type.asArray(), Magic.getArrayLength(object.toObject()));
  }

  /**
   * @param region The start (or an address less than) the region that was reserved for this object.
   */
  @Override
  @Inline
  public Address copyTo(ObjectReference from, ObjectReference to, Address region) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(from);
    RVMType type = tib.getType();
    int bytes;

    boolean copy = (from != to);

    if (copy) {
      if (type.isClassType()) {
        RVMClass classType = type.asClass();
        bytes = org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(from.toObject(), classType);
        org.jikesrvm.objectmodel.ObjectModel.moveObject(from.toObject(), to.toObject(), bytes, classType);
      } else {
      RVMArray arrayType = type.asArray();
        int elements = Magic.getArrayLength(from.toObject());
        bytes = org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(from.toObject(), arrayType, elements);
        org.jikesrvm.objectmodel.ObjectModel.moveObject(from.toObject(), to.toObject(), bytes, arrayType);
      }
    } else {
      bytes = getCurrentSize(to);
    }

    Address start = org.jikesrvm.objectmodel.ObjectModel.objectStartRef(to);
    Allocator.fillAlignmentGap(region, start);

    return start.plus(bytes);
  }

  @Override
  public ObjectReference getReferenceWhenCopiedTo(ObjectReference from, Address to) {
    return ObjectReference.fromObject(org.jikesrvm.objectmodel.ObjectModel.getReferenceWhenCopiedTo(from.toObject(), to));
  }

  @Override
  public Address getObjectEndAddress(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.getObjectEndAddress(object.toObject());
  }

  @Override
  public int getSizeWhenCopied(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(object.toObject());
  }

  @Override
  public int getAlignWhenCopied(ObjectReference object) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(object);
    RVMType type = tib.getType();
    if (type.isArrayType()) {
      return org.jikesrvm.objectmodel.ObjectModel.getAlignment(type.asArray(), object.toObject());
    } else {
      return org.jikesrvm.objectmodel.ObjectModel.getAlignment(type.asClass(), object.toObject());
    }
  }

  @Override
  public int getAlignOffsetWhenCopied(ObjectReference object) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(object);
    RVMType type = tib.getType();
    if (type.isArrayType()) {
      return org.jikesrvm.objectmodel.ObjectModel.getOffsetForAlignment(type.asArray(), object);
    } else {
      return org.jikesrvm.objectmodel.ObjectModel.getOffsetForAlignment(type.asClass(), object);
    }
  }

  @Override
  public int getCurrentSize(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.bytesUsed(object.toObject());
  }

  @Override
  public ObjectReference getNextObject(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.getNextObject(object);
  }

  @Override
  public ObjectReference getObjectFromStartAddress(Address start) {
    return org.jikesrvm.objectmodel.ObjectModel.getObjectFromStartAddress(start);
  }

  @Override
  public byte [] getTypeDescriptor(ObjectReference ref) {
    Atom descriptor = Magic.getObjectType(ref).getDescriptor();
    return descriptor.toByteArray();
  }

  @Override
  @Inline
  public int getArrayLength(ObjectReference object) {
    return Magic.getArrayLength(object.toObject());
  }

  @Override
  public boolean isArray(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.getObjectType(object.toObject()).isArrayType();
  }

  @Override
  public boolean isPrimitiveArray(ObjectReference object) {
    Object obj = object.toObject();
    return (obj instanceof long[]   ||
            obj instanceof int[]    ||
            obj instanceof short[]  ||
            obj instanceof byte[]   ||
            obj instanceof double[] ||
            obj instanceof float[]);
  }

  /**
   * Tests a bit available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param idx the index of the bit
   */
  public boolean testAvailableBit(ObjectReference object, int idx) {
    return org.jikesrvm.objectmodel.ObjectModel.testAvailableBit(object.toObject(), idx);
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
    org.jikesrvm.objectmodel.ObjectModel.setAvailableBit(object.toObject(), idx, flag);
  }

  @Override
  public boolean attemptAvailableBits(ObjectReference object,
                                             Word oldVal, Word newVal) {
    return org.jikesrvm.objectmodel.ObjectModel.attemptAvailableBits(object.toObject(), oldVal,
                                               newVal);
  }

  @Override
  public Word prepareAvailableBits(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.prepareAvailableBits(object.toObject());
  }

  @Override
  public void writeAvailableByte(ObjectReference object, byte val) {
    org.jikesrvm.objectmodel.ObjectModel.writeAvailableByte(object.toObject(), val);
  }

  @Override
  public byte readAvailableByte(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.readAvailableByte(object.toObject());
  }

  @Override
  public void writeAvailableBitsWord(ObjectReference object, Word val) {
    org.jikesrvm.objectmodel.ObjectModel.writeAvailableBitsWord(object.toObject(), val);
  }

  public void writeReplicaPointers(ObjectReference fromSpace, ObjectReference toSpace) {
    org.jikesrvm.objectmodel.JavaHeader.writeReplicaPointers(fromSpace, toSpace);
  }

  public ObjectReference getReplicaPointer(ObjectReference obj) {
    return ObjectReference.fromObject(org.jikesrvm.objectmodel.JavaHeader.getReplicaPointer(obj));
  }

  @Override
  public Word readAvailableBitsWord(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.readAvailableBitsWord(object.toObject());
  }

  /* AJG: Should this be a variable rather than method? */
  @Override
  public Offset GC_HEADER_OFFSET() {
    return org.jikesrvm.objectmodel.ObjectModel.GC_HEADER_OFFSET;
  }

  @Override
  @Inline
  public Address objectStartRef(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.objectStartRef(object);
  }

  @Override
  public Address refToAddress(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.getPointerInMemoryRegion(object);
  }

  @Override
  @Inline
  public boolean isAcyclic(ObjectReference typeRef) {
    TIB tib = Magic.addressAsTIB(typeRef.toAddress());
    RVMType type = tib.getType();
    return type.isAcyclicReference();
  }

  @Override
  public void dumpObject(ObjectReference object) {
    DebugUtil.dumpRef(object);
  }
  
  public void concurrentCopy(TraceLocal trace, ObjectReference fromSpace, ObjectReference toSpace) {
    RVMType type = Magic.getObjectType(fromSpace.toObject());
    if (type.isClassType()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!type.getTypeRef().isRuntimeTable());
      // It's an object instance, copy all the fields, this assumes that reference fields are word aligned
      for (RVMField field : type.getInstanceFields()) {
        Offset offset = field.getOffset();
        while (true) { // potentially loop forever copying field
          if (field.isTraced()) {
            // prepare toSpace
            ObjectReference currentToSpaceVal = ObjectReference.fromObject(Magic.prepareObject(toSpace.toObject(), offset));
            // it's a reference field
            // read fromSpace and convert to toSpace if needed
            ObjectReference oldFromSpaceVal = ObjectReference.fromObject(Magic.getObjectAtOffset(fromSpace.toObject(), offset));
            ObjectReference fromSpaceEquivalent = trace.traceObject(oldFromSpaceVal, false);
            if (fromSpaceEquivalent.toAddress().EQ(currentToSpaceVal.toAddress()))
              break; // fromSpace and toSpace both contain same value
            else {
              // fromSpace and toSpace don't match must attempt to update toSpace
              if (!Magic.attemptObject(toSpace, offset, currentToSpaceVal.toObject(), fromSpaceEquivalent.toObject()))
                break; // CAS failed mutator updated the slot so we don't need to copy
              else {
                // succeed in updating toSpace slot but must loop back round and reread fromSpace value
              }
            }
          } else {
            // not a reference field
            byte size = (byte) field.getSize();
            if (size == BYTES_IN_LONG) {
              // prepare toSpace
              long currentToSpaceVal = Magic.prepareLong(toSpace.toObject(), offset);
              // copy as a double / long
              // read fromSpace
              long oldFromSpaceVal = Magic.getLongAtOffset(fromSpace.toObject(), offset);
              if (oldFromSpaceVal == currentToSpaceVal)
                break; // fromSpace and toSpace both contain same value
              else {
                // fromSpace and toSpace don't match must attempt to update toSpace
                if (!Magic.attemptLong(toSpace, offset, currentToSpaceVal, oldFromSpaceVal))
                  break; // CAS failed mutator updated the slot so we don't need to copy
                else {
                  // succeed in updating toSpace slot but must loop back round and reread fromSpace value
                }
              }
            } else {
              // not a double or long
              // LPJH: this could be optimsied more to use 64 bit writes until we got to the end of the object
              offset = Offset.fromIntZeroExtend((offset.toWord().and(Word.fromIntZeroExtend(3).not())).toInt()); // round down the offset to a word boundary
              // prepare toSpace
              int currentToSpaceVal = Magic.prepareInt(toSpace.toObject(), offset);
              int oldFromSpaceVal = Magic.getIntAtOffset(fromSpace.toObject(), offset);
              if (oldFromSpaceVal == currentToSpaceVal)
                break; // fromSpace and toSpace both contain same value
              else {
                // fromSpace and toSpace don't match must attempt to update toSpace
                Magic.attemptInt(toSpace, offset, currentToSpaceVal, oldFromSpaceVal);
                // if the CAS fails we will still need to reread the whole word as mutator may just write a byte and the rest of
                // toSpace may be wrong
              }
            }
          }
        }
      }
    } else if (type.isArrayType()) {
      RVMType elementType = type.asArray().getElementType();
      int fromSpaceLength = getArrayLength(fromSpace);
      if (elementType.isReferenceType()) {
        // replication barrier might or might not be on whilst updating this reference array
        for (int i = 0; i < fromSpaceLength; i++) {
          Offset offset = Offset.fromIntZeroExtend(i << 2);
          while (true) {
            // might loop forever copying a single element
            ObjectReference oldFromSpaceVal = ObjectReference.fromObject(Magic.getObjectAtOffset(fromSpace.toObject(), offset));
            ObjectReference fromSpaceEquivalent = trace.traceObject(oldFromSpaceVal, false);
            // prepare toSpace
            ObjectReference currentToSpaceVal = ObjectReference.fromObject(Magic.prepareObject(toSpace.toObject(), offset));
            if (fromSpaceEquivalent.toAddress().EQ(currentToSpaceVal.toAddress()))
              break; // fromSpace and toSpace both contain same value
            else {
              // fromSpace and toSpace don't match must attempt to update toSpace
              if (!Magic.attemptObject(toSpace, offset, currentToSpaceVal.toObject(), fromSpaceEquivalent.toObject()))
                break; // CAS failed mutator updated the slot so we don't need to copy
              else {
                // succeed in updating toSpace slot but must loop back round and reread fromSpace value
              }
            }
          }
        }
      } else if (elementType.isDoubleType() || elementType.isLongType()) {
        // a long / double array
        for (int i = 0; i < fromSpaceLength; i++) {
          Offset offset = Offset.fromIntZeroExtend(i << 3);
          while (true) {
            long oldFromSpaceVal = Magic.getLongAtOffset(fromSpace.toObject(), offset);
            // prepare toSpace
            long currentToSpaceVal = Magic.prepareLong(toSpace.toObject(), offset);
            if (oldFromSpaceVal == currentToSpaceVal)
              break; // fromSpace and toSpace both contain same value
            else {
              // fromSpace and toSpace don't match must attempt to update toSpace
              if (!Magic.attemptLong(toSpace, offset, currentToSpaceVal, oldFromSpaceVal))
                break; // CAS failed mutator updated the slot so we don't need to copy
              else {
                // succeed in updating toSpace slot but must loop back round and reread fromSpace value
              }
            }
          }
        }
      } else {
        // not a reference or double / long
        int numBytes = elementType.getMemoryBytes() * fromSpaceLength;
        for (int i = 0; i < numBytes; i = i + 4) {
          Offset offset = Offset.fromIntZeroExtend(i);
          while (true) {
            // LPJH: this could be optimsied more to use 64 bit writes until we got to the end of the array
            int oldFromSpaceVal = Magic.getIntAtOffset(fromSpace.toObject(), offset);
            // prepare toSpace
            int currentToSpaceVal = Magic.prepareInt(toSpace.toObject(), offset);
            if (oldFromSpaceVal == currentToSpaceVal)
              break; // fromSpace and toSpace both contain same value
            else {
              // fromSpace and toSpace don't match must attempt to update toSpace
              Magic.attemptInt(toSpace, offset, currentToSpaceVal, oldFromSpaceVal);
              // if the CAS fails we will still need to reread the whole word as mutator may just write a byte and the rest of
              // toSpace may be wrong
            }
          }
        }
      }
    } else {
      VM.assertions.fail("unknown type in concurrent copy");
    }
  }

  @Override
  @Inline
  public void concurrentCopyCAS2(TraceLocal trace, ObjectReference fromCopy, ObjectReference toCopy) {
    final int BYTES_IN_INT = org.jikesrvm.SizeConstants.BYTES_IN_INT;
    final RVMType type = Magic.getObjectType(fromCopy.toObject());
    if (type.isClassType()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!type.getTypeRef().isRuntimeTable());
      
      final int size = Memory.alignUp(type.asClass().getInstanceSize(), BYTES_IN_INT) - (JavaHeader.OBJECT_REF_OFFSET - BYTES_IN_INT);
      final Offset baseOffset = Offset.zero().minus(BYTES_IN_INT);
      
      final Offset endOffset = baseOffset.plus(size);
      // reference offset is sorted in address order.
      // In current implementation, references are clustered and put at the first of the object.
      // But this fact is private in org.jikesrvm.objectmodel.ObjectModel.
      final int[] refoffs = type.getReferenceOffsets();
      int refoffoff = 0;
      Offset offset = baseOffset;
      
      if (refoffs.length > 0) {
        if (VM.VERIFY_ASSERTIONS) {
          // Verify order of offset array
          for (int i = 0; i < (refoffs.length - 1); ++i) {
            VM.assertions._assert(refoffs[i] < refoffs[i+1]);
          }
        }
        for (; offset.sLT(endOffset); offset = offset.plus(BYTES_IN_INT)) {
          if (refoffs[refoffoff] == offset.toInt()) {
            while (true) {
              ObjectReference toVal = ObjectReference.fromObject(Magic.prepareObject(toCopy.toObject(), offset));
              ObjectReference fromVal = ObjectReference.fromObject(Magic.getObjectAtOffset(fromCopy.toObject(), offset));
              ObjectReference fromValEquiv = trace.traceObject(fromVal, false);
              if (fromValEquiv.toAddress().EQ(toVal.toAddress()))
                break;
              if (!Magic.attemptObject(toCopy.toObject(), offset, toVal.toObject(), fromValEquiv.toObject()))
                break;
            }
            if (++refoffoff >= refoffs.length) {
              offset = offset.plus(BYTES_IN_INT);
              break;
            }
          } else {
            while (true) {
              int toVal = Magic.prepareInt(toCopy.toObject(), offset);
              int fromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
              if (fromVal == toVal)
                break;
              if (!Magic.attemptInt(toCopy.toObject(), offset, toVal, fromVal))
                break;
            }
          }
        }
      }
      for (; offset.sLT(endOffset); offset = offset.plus(BYTES_IN_INT)) {
        while (true) {
          int toVal = Magic.prepareInt(toCopy.toObject(), offset);
          int fromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          if (fromVal == toVal)
            break;
          if (!Magic.attemptInt(toCopy.toObject(), offset, toVal, fromVal))
            break;
        }
      }
    } else {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(type.isArrayType());
      RVMType elementType = type.asArray().getElementType();
      int fromCopyLength = getArrayLength(fromCopy);
      if (elementType.isReferenceType()) {
        Offset end = Offset.fromIntSignExtend(fromCopyLength << org.jikesrvm.SizeConstants.LOG_BYTES_IN_ADDRESS);
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS)) {
          while (true) {
            ObjectReference toVal = ObjectReference.fromObject(Magic.prepareObject(toCopy.toObject(), offset));
            ObjectReference fromVal = ObjectReference.fromObject(Magic.getObjectAtOffset(fromCopy.toObject(), offset));
            ObjectReference fromValEquiv = trace.traceObject(fromVal, false);
            if (fromValEquiv.toAddress().EQ(toVal.toAddress()))
              break;
            if (!Magic.attemptObject(toCopy.toObject(), offset, toVal.toObject(), fromValEquiv.toObject()))
              break;
          }
        }
      } else {
        int numBytes = Memory.alignUp(elementType.getMemoryBytes() * fromCopyLength, org.jikesrvm.SizeConstants.BYTES_IN_INT);
        Offset end = Offset.fromIntSignExtend(numBytes);
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS)) {
          while (true) {
            int toVal = Magic.prepareInt(toCopy.toObject(), offset);
            int fromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
            if (fromVal == toVal)
              break;
            if (!Magic.attemptInt(toCopy.toObject(), offset, toVal, fromVal))
              break;
          }
        }
      }
    }
  }
  
  @Override
  @Inline
  public void concurrentCopyUnsafe(TraceLocal trace, ObjectReference fromSpace, ObjectReference toSpace) {
    final Word wordMask = Word.fromIntSignExtend(~0).rshl(org.jikesrvm.SizeConstants.BITS_IN_WORD - org.jikesrvm.SizeConstants.LOG_BYTES_IN_WORD).not();
    RVMType type = Magic.getObjectType(fromSpace.toObject());
    
    if (type.isClassType()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!type.getTypeRef().isRuntimeTable());
      // It's an object instance, copy all the fields, this assumes that reference fields are word aligned
      for (RVMField field : type.getInstanceFields()) {
        Offset offset = field.getOffset();
        if (field.isTraced()) {
          // it's a reference field
          // read fromSpace and convert to toSpace if needed
          ObjectReference fromSpaceVal = ObjectReference.fromObject(Magic.getObjectAtOffset(fromSpace.toObject(), offset));
          ObjectReference fromSpaceEquivalent = trace.traceObject(fromSpaceVal, false);
          Magic.setObjectAtOffset(toSpace, offset, fromSpaceEquivalent.toObject());
        } else {
          // not a reference field
          byte size = (byte) field.getSize();
          if (size == BYTES_IN_LONG) {
            // copy as a double / long
            // read fromSpace
            long oldFromSpaceVal = Magic.getLongAtOffset(fromSpace.toObject(), offset);
            Magic.setLongAtOffset(toSpace, offset, oldFromSpaceVal);
          } else {
            // not a double or long
            offset = Offset.fromIntZeroExtend(offset.toWord().and(wordMask).toInt()); // round down the offset to a word boundary
            Word fromSpaceVal = Magic.getWordAtOffset(fromSpace.toObject(), offset);
            Magic.setWordAtOffset(toSpace, offset, fromSpaceVal);
          }
        }
      }
    } else if (type.isArrayType()) {
      RVMType elementType = type.asArray().getElementType();
      int fromSpaceLength = getArrayLength(fromSpace);
      if (elementType.isReferenceType()) {
        Offset offset = Offset.zero();
        for (int i = 0; i < fromSpaceLength; i++) {
          ObjectReference fromSpaceVal = ObjectReference.fromObject(Magic.getObjectAtOffset(fromSpace.toObject(), offset));
          ObjectReference fromSpaceEquivalent = trace.traceObject(fromSpaceVal, false);
          Magic.setObjectAtOffset(toSpace, offset, fromSpaceEquivalent.toObject());
          offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS);
        }
      } else {
        int numBytes = elementType.getMemoryBytes() * fromSpaceLength;
        if ((numBytes & wordMask.not().toInt()) != 0)
          numBytes += 4 - (numBytes & wordMask.not().toInt());
        Memory.aligned32Copy(Magic.objectAsAddress(toSpace), Magic.objectAsAddress(fromSpace), numBytes);
      }
    } else {
      VM.assertions.fail("unknown type in concurrent copy");
    }
  }

  @Override
  @Inline
  public void concurrentCopyUnsafe2(TraceLocal trace, ObjectReference fromCopy, ObjectReference toCopy) {
    final int BYTES_IN_INT = org.jikesrvm.SizeConstants.BYTES_IN_INT;
    final RVMType type = Magic.getObjectType(fromCopy.toObject());
    if (type.isClassType()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!type.getTypeRef().isRuntimeTable());
      final int size = Memory.alignUp(type.asClass().getInstanceSize(), BYTES_IN_INT) - (JavaHeader.OBJECT_REF_OFFSET - BYTES_IN_INT);
      final Offset baseOffset = Offset.zero().minus(BYTES_IN_INT);
      final Offset endOffset = baseOffset.plus(size);
      // reference offset is sorted in address order.
      // In current implementation, references are clustered and put at the first of the object.
      // But this fact is private in org.jikesrvm.objectmodel.ObjectModel.
      final int[] refoffs = type.getReferenceOffsets();
      int refoffoff = 0;
      Offset offset = baseOffset;
      if (refoffs.length > 0) {
        for (; offset.sLT(endOffset); offset = offset.plus(BYTES_IN_INT)) {
          int fromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          if (refoffs[refoffoff] == offset.toInt()) {
            ObjectReference fromValEquiv = trace.traceObject(Address.fromIntSignExtend(fromVal).toObjectReference(), false);
            Magic.setObjectAtOffset(toCopy.toObject(), offset, fromValEquiv.toObject());
            if (++refoffoff >= refoffs.length) {
              offset = offset.plus(BYTES_IN_INT);
              break;
            }
          } else {
            Magic.setIntAtOffset(toCopy.toObject(), offset, fromVal);
          }
        }
      }
      for (; offset.sLT(endOffset); offset = offset.plus(BYTES_IN_INT)) {
        int fromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
        Magic.setIntAtOffset(toCopy.toObject(), offset, fromVal);
      }
    } else {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(type.isArrayType());
      RVMType elementType = type.asArray().getElementType();
      int fromCopyLength = getArrayLength(fromCopy);
      if (elementType.isReferenceType()) {
        Offset end = Offset.fromIntSignExtend(fromCopyLength << org.jikesrvm.SizeConstants.LOG_BYTES_IN_ADDRESS);
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS)) {
          ObjectReference fromSpaceVal = ObjectReference.fromObject(Magic.getObjectAtOffset(fromCopy.toObject(), offset));
          ObjectReference fromSpaceEquivalent = trace.traceObject(fromSpaceVal, false);
          Magic.setObjectAtOffset(toCopy.toObject(), offset, fromSpaceEquivalent.toObject());
        }
      } else {
        // Memory.aligned32Copy(Magic.objectAsAddress(toCopy.toObject()), Magic.objectAsAddress(fromCopy.toObject()), numBytes);
        int numBytes = Memory.alignUp(elementType.getMemoryBytes() * fromCopyLength, BYTES_IN_INT);
        Offset end = Offset.fromIntSignExtend(numBytes);
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(BYTES_IN_INT)) {
          int fromSpaceVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          Magic.setIntAtOffset(toCopy.toObject(), offset, fromSpaceVal);
        }
      }
    }
  }
  
  @Override
  @Inline
  public int concurrentCopyHTM(TraceLocal trace, ObjectReference fromSpace, ObjectReference toSpace, int attempts) {
    while (attempts > 0) {
      int result = Magic.htmBegin();
      if (result == (~0)) {
        concurrentCopyUnsafe(trace, fromSpace, toSpace);
        Magic.htmEnd();
        return 0;
      } else {
        attempts--;
      }
    }
    return CONCURRENT_COPY_FAILED;
  }
  
  @Override
  @Inline
  public int concurrentCopyHTM2(TraceLocal trace, ObjectReference fromSpace, ObjectReference toSpace, int attempts) {
    while (attempts > 0) {
      int result = Magic.htmBegin();
      if (result == (~0)) {
        concurrentCopyUnsafe2(trace, fromSpace, toSpace);
        Magic.htmEnd();
        return 0;
      } else {
        attempts--;
      }
    }
    return CONCURRENT_COPY_FAILED;
  }

  @Override
  @Inline
  public int concurrentCopyHTMBegin() {
    int result = Magic.htmBegin();
    return (result == (~0) ? 0 : CONCURRENT_COPY_FAILED);
  }
  
  @Override
  @Inline
  public int concurrentCopyHTMEnd() {
    Magic.htmEnd();
    return 0;
  }
  
  @Override
  @Inline
  public boolean concurrentCopySTM(TraceLocal trace, ObjectReference fromCopy, ObjectReference toCopy, Address buf, Extent buflen) {
    RVMType type = Magic.getObjectType(fromCopy.toObject());
    if (type.isClassType()) {
      if (type.asClass().getInstanceSize() > buflen.toInt())
        return false;
      buf = buf.minus(JavaHeader.getHeaderEndOffset());
      for (RVMField field : type.getInstanceFields()) {
        Offset offset = field.getOffset();
        if (field.isTraced()) {
          ObjectReference oldFromSpaceValue = ObjectReference.fromObject(Magic.getObjectAtOffset(fromCopy.toObject(), offset));
          buf.store(oldFromSpaceValue, offset);
          ObjectReference fromSpaceEquivalent = trace.traceObject(oldFromSpaceValue, false);
          Magic.setObjectAtOffset(toCopy.toObject(), offset, fromSpaceEquivalent.toObject());
        } else if (field.getSize() == 8) {
          long oldFromSpaceValue = Magic.getLongAtOffset(fromCopy.toObject(), offset);
          buf.store(oldFromSpaceValue, offset);
          Magic.setLongAtOffset(toCopy.toObject(), offset, oldFromSpaceValue);
        } else {
          offset = Offset.fromIntZeroExtend((offset.toWord().and(Word.fromIntZeroExtend(3).not())).toInt()); // round down the offset to a word boundary
          int oldFromSpaceValue = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          buf.store(oldFromSpaceValue, offset);
          Magic.setIntAtOffset(toCopy.toObject(), offset, oldFromSpaceValue);
        }
      }        
      //      Magic.sync();
      Magic.fence();
      for (RVMField field : type.getInstanceFields()) {
        Offset offset = field.getOffset();
        if (field.getSize() == 8) {
          long currentFromSpaceValue = Magic.getLongAtOffset(fromCopy.toObject(), offset);
          long oldFromSpaceValue = buf.loadLong(offset);
          if (currentFromSpaceValue != oldFromSpaceValue)
            return false;
        } else {
          offset = Offset.fromIntZeroExtend((offset.toWord().and(Word.fromIntZeroExtend(3).not())).toInt()); // round down the offset to a word boundary
          int currentFromSpaceValue = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          int oldFromSpaceValue = buf.loadInt(offset);
          if (currentFromSpaceValue != oldFromSpaceValue)
            return false;
        }
      }        
      return true; // success
    } else if (type.isArrayType()) {
      RVMType elementType = type.asArray().getElementType();
      int fromCopyLength = getArrayLength(fromCopy);
      int numBytes = elementType.getMemoryBytes() * fromCopyLength;
      if (numBytes > buflen.toInt())
        return false;
      if (elementType.isReferenceType()) {
        for (int i = 0; i < fromCopyLength; i++) {
          Offset offset = Offset.fromIntSignExtend(i << org.jikesrvm.SizeConstants.LOG_BYTES_IN_ADDRESS);
          ObjectReference oldFromSpaceValue = ObjectReference.fromObject(Magic.getObjectAtOffset(fromCopy.toObject(), offset));
          buf.store(oldFromSpaceValue, offset);
          ObjectReference fromSpaceEquivalent = trace.traceObject(oldFromSpaceValue, false);
          Magic.setObjectAtOffset(toCopy.toObject(), offset, fromSpaceEquivalent);
        }
      } else {
        for (int i = 0; i < numBytes; i += 4) {
          Offset offset = Offset.fromIntSignExtend(i);
          int oldFromSpaceVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          buf.store(oldFromSpaceVal, offset);
          Magic.setIntAtOffset(toCopy.toObject(), offset, oldFromSpaceVal);
        }
      }
      //      Magic.sync();
      Magic.fence();
      for (int i = 0; i < numBytes; i += 4) {
        Offset offset = Offset.fromIntSignExtend(i);
        int currentFromSpaceValue = Magic.getIntAtOffset(fromCopy.toObject(), offset);
        int oldFromSpaceValue = buf.loadInt(offset);
        if (currentFromSpaceValue != oldFromSpaceValue)
          return false;
      }
      return true;
    } else {
      VM.assertions.fail("Unknown type");
      return false;
    }
  }

  @Override
  @Inline
  public boolean concurrentCopySTMSeq(TraceLocal trace, ObjectReference fromCopy, ObjectReference toCopy, Address buf, Extent buflen) {
    RVMType type = Magic.getObjectType(fromCopy.toObject());
    if (type.isClassType()) {
      int size = type.asClass().getInstanceSize() - (JavaHeader.OBJECT_REF_OFFSET - org.jikesrvm.SizeConstants.BYTES_IN_INT);
      size = Memory.alignUp(size, org.jikesrvm.SizeConstants.BYTES_IN_INT);
      if (size > buflen.toInt())
        return false;
      buf = buf.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
      /* copy */
      Offset offset = Offset.zero().minus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
      for (int i = 0; i < size; i += org.jikesrvm.SizeConstants.BYTES_IN_INT) {
        int fromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
        buf.store(fromVal, offset);
        Magic.setIntAtOffset(toCopy.toObject(), offset, fromVal);
        offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
      }
      /* patch */
      for (int refoff : type.getReferenceOffsets()) {
        Offset refOffset = Offset.fromIntSignExtend(refoff);
        ObjectReference fromVal = buf.loadObjectReference(refOffset);
        ObjectReference fromEquiv = trace.traceObject(fromVal, false);
        Magic.setObjectAtOffset(toCopy.toObject(), refOffset, fromEquiv.toObject());
      }
      Magic.fence();
      /* verify */
      offset = Offset.zero().minus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
      for (int i = 0; i < size; i += org.jikesrvm.SizeConstants.BYTES_IN_INT) {
        int currentFromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
        int oldFromVal = buf.loadInt(offset);
        if (currentFromVal != oldFromVal)
          return false;
        offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
      }
      return true;
    } else { // array type
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(type.isArrayType());
      RVMType elementType = type.asArray().getElementType();
      int length = getArrayLength(fromCopy);
      int numBytes = elementType.getMemoryBytes() * length;
      if (numBytes > buflen.toInt())
        return false;
      if (elementType.isReferenceType()) {
        for (int i = 0; i < length; i++) {
          Offset offset = Offset.fromIntSignExtend(i << org.jikesrvm.SizeConstants.LOG_BYTES_IN_ADDRESS);
          ObjectReference fromVal = ObjectReference.fromObject(Magic.getObjectAtOffset(fromCopy.toObject(), offset));
          buf.store(fromVal, offset);
          ObjectReference fromEquiv = trace.traceObject(fromVal, false);
          Magic.setObjectAtOffset(toCopy.toObject(), offset, fromEquiv.toObject());
        }
        Magic.fence();
        for (int i = 0; i < length; i ++) {
          Offset offset = Offset.fromIntSignExtend(i << org.jikesrvm.SizeConstants.LOG_BYTES_IN_ADDRESS);
          int currentFromSpaceValue = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          int oldFromSpaceValue = buf.loadInt(offset);
          if (currentFromSpaceValue != oldFromSpaceValue)
            return false;
        }
      } else {
        for (int i = 0; i < numBytes; i += 4) {
          Offset offset = Offset.fromIntSignExtend(i);
          int oldFromSpaceVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          buf.store(oldFromSpaceVal, offset);
          Magic.setIntAtOffset(toCopy.toObject(), offset, oldFromSpaceVal);
        }
        Magic.fence();
        for (int i = 0; i < numBytes; i += 4) {
          Offset offset = Offset.fromIntSignExtend(i);
          int currentFromSpaceValue = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          int currentToSpaceValue = Magic.getIntAtOffset(toCopy.toObject(), offset);
          if (currentFromSpaceValue != currentToSpaceValue)
            return false;
        }
      }
      return true;
    }
  }

  @Override
  @Inline
  public boolean concurrentCopySTMSeq2(TraceLocal trace, ObjectReference fromCopy, ObjectReference toCopy, Address buf, Extent buflen) {
    RVMType type = Magic.getObjectType(fromCopy.toObject());
    if (type.isClassType()) {
      int size = type.asClass().getInstanceSize() - (JavaHeader.OBJECT_REF_OFFSET - org.jikesrvm.SizeConstants.BYTES_IN_INT);
      size = Memory.alignUp(size, org.jikesrvm.SizeConstants.BYTES_IN_INT);
      if (size > buflen.toInt())
        return false;
      buf = buf.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
      Offset baseOffset = Offset.zero().minus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
      /* copy */
      // reference offset is sorted in address order.
      // In current implementation, references are clustered and put at the first of the obecjt.
      // But this fact is private in org.jikesrvm.objectmodel.ObjectModel.
      int[] refoffs = type.getReferenceOffsets();
      int refoffoff = 0;
      Offset offset = baseOffset;
      if (refoffs.length > 0) {
        for (; offset.sLT(baseOffset.plus(size)); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
          int fromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          buf.store(fromVal, offset);
          if (refoffs[refoffoff] == offset.toInt()) {
            ObjectReference fromValEquiv = trace.traceObject(Address.fromIntSignExtend(fromVal).toObjectReference(), false);
            Magic.setObjectAtOffset(toCopy.toObject(), offset, fromValEquiv.toObject());
            if (++refoffoff >= refoffs.length) {
              offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
              break;
            }
          } else
            Magic.setIntAtOffset(toCopy.toObject(), offset, fromVal);
        }
      }
      for (; offset.sLT(baseOffset.plus(size)); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
        int fromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
        buf.store(fromVal, offset);
        Magic.setIntAtOffset(toCopy.toObject(), offset, fromVal);
      }
      Magic.fence();
      /* verify */
      for (offset = baseOffset; offset.sLT(baseOffset.plus(size)); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
        int currentFromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
        int oldFromVal = buf.loadInt(offset);
        if (currentFromVal != oldFromVal)
          return false;
      }
      return true;
    } else { // array type
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(type.isArrayType());
      RVMType elementType = type.asArray().getElementType();
      int length = getArrayLength(fromCopy);
      if (elementType.isReferenceType()) {
        int numBytes = length << org.jikesrvm.SizeConstants.LOG_BYTES_IN_ADDRESS;
        if (numBytes > buflen.toInt())
          return false;
        Offset end = Offset.fromIntSignExtend(numBytes);
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS)) {
          ObjectReference fromVal = ObjectReference.fromObject(Magic.getObjectAtOffset(fromCopy.toObject(), offset));
          buf.store(fromVal, offset);
          ObjectReference fromEquiv = trace.traceObject(fromVal, false);
          Magic.setObjectAtOffset(toCopy.toObject(), offset, fromEquiv.toObject());
        }
        Magic.fence();
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS)) {
          int currentFromSpaceValue = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          int oldFromSpaceValue = buf.loadInt(offset);
          if (currentFromSpaceValue != oldFromSpaceValue)
            return false;
        }
      } else {
        int numBytes = Memory.alignUp(elementType.getMemoryBytes() * length, org.jikesrvm.SizeConstants.BYTES_IN_INT);
        if (numBytes > buflen.toInt())
          return false;
        Offset end = Offset.fromIntSignExtend(numBytes);
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
          int oldFromSpaceVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          buf.store(oldFromSpaceVal, offset);
          Magic.setIntAtOffset(toCopy.toObject(), offset, oldFromSpaceVal);
        }
        Magic.fence();
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
          int currentFromSpaceValue = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          int oldToSpaceVal = buf.loadInt(offset);
          if (currentFromSpaceValue != oldToSpaceVal)
            return false;
        }
      }
      return true;
    }
  }

  @Override
  @Inline
  public boolean concurrentCopySTMSeq2P(TraceLocal trace, ObjectReference fromCopy, ObjectReference toCopy, Address buf, Extent buflen) {
    RVMType type = Magic.getObjectType(fromCopy.toObject());
    if (type.isClassType()) {
      int size = type.asClass().getInstanceSize() - (JavaHeader.OBJECT_REF_OFFSET - org.jikesrvm.SizeConstants.BYTES_IN_INT);
      size = Memory.alignUp(size, org.jikesrvm.SizeConstants.BYTES_IN_INT);
      if (size > buflen.toInt())
        return false;
      buf = buf.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
      Offset baseOffset = Offset.zero().minus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
      int[] refoffs = type.getReferenceOffsets();
      int refoffoff = 0;
      Offset offset = baseOffset;
      if (refoffs.length > 0) {
        for (; offset.sLT(baseOffset.plus(size)); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
          int fromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          if (refoffs[refoffoff] == offset.toInt()) {
            buf.store(fromVal, offset);
            ObjectReference fromValEquiv = trace.traceObject(Address.fromIntSignExtend(fromVal).toObjectReference(), false);
            Magic.setObjectAtOffset(toCopy.toObject(), offset, fromValEquiv.toObject());
            if (++refoffoff >= refoffs.length) {
              offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
              break;
            }
          } else
            Magic.setIntAtOffset(toCopy.toObject(), offset, fromVal);
        }
      }
      for (; offset.sLT(baseOffset.plus(size)); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
        int fromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
        Magic.setIntAtOffset(toCopy.toObject(), offset, fromVal);
      }
      Magic.fence();
      /* verify */
      refoffoff = 0;
      offset = baseOffset;
      if (refoffs.length > 0) {
        for (; offset.sLT(baseOffset.plus(size)); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
          int currentFromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          if (refoffs[refoffoff] == offset.toInt()) {
            int oldFromVal = buf.loadInt(offset);
            if (currentFromVal != oldFromVal)
              return false;
            if (++refoffoff >= refoffs.length) {
              offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
              break;
            }
          } else {
            int currentToVal = Magic.getIntAtOffset(toCopy.toObject(), offset);
            if (currentFromVal != currentToVal)
              return false;
          }
        }
      }
      for (; offset.sLT(baseOffset.plus(size)); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
        int currentFromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
        int currentToVal = Magic.getIntAtOffset(toCopy.toObject(), offset);
        if (currentFromVal != currentToVal)
          return false;
      }
      return true;
    } else { // array type
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(type.isArrayType());
      RVMType elementType = type.asArray().getElementType();
      int length = getArrayLength(fromCopy);
      if (elementType.isReferenceType()) {
        int numBytes = length << org.jikesrvm.SizeConstants.LOG_BYTES_IN_ADDRESS;
        if (numBytes > buflen.toInt())
          return false;
        Offset end = Offset.fromIntSignExtend(numBytes);
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS)) {
          ObjectReference fromVal = ObjectReference.fromObject(Magic.getObjectAtOffset(fromCopy.toObject(), offset));
          buf.store(fromVal, offset);
          ObjectReference fromEquiv = trace.traceObject(fromVal, false);
          Magic.setObjectAtOffset(toCopy.toObject(), offset, fromEquiv.toObject());
        }
        Magic.fence();
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS)) {
          int currentFromSpaceValue = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          int oldFromSpaceValue = buf.loadInt(offset);
          if (currentFromSpaceValue != oldFromSpaceValue)
            return false;
        }
      } else {
        int numBytes = Memory.alignUp(elementType.getMemoryBytes() * length, org.jikesrvm.SizeConstants.BYTES_IN_INT);
        Offset end = Offset.fromIntSignExtend(numBytes);
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
          int oldFromSpaceVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          Magic.setIntAtOffset(toCopy.toObject(), offset, oldFromSpaceVal);
        }
        Magic.fence();
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
          int currentFromSpaceValue = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          int currentToSpaceValue = Magic.getIntAtOffset(toCopy.toObject(), offset);
          if (currentFromSpaceValue != currentToSpaceValue)
            return false;
        }
      }
      return true;
    }
  }

  @Override
  @Inline
  public boolean concurrentCopySTMSeq2N(TraceLocal trace, ObjectReference fromCopy, ObjectReference toCopy, Address buf, Extent buflen) {
    RVMType type = Magic.getObjectType(fromCopy.toObject());
    if (type.isClassType()) {
      int size = type.asClass().getInstanceSize() - (JavaHeader.OBJECT_REF_OFFSET - org.jikesrvm.SizeConstants.BYTES_IN_INT);
      size = Memory.alignUp(size, org.jikesrvm.SizeConstants.BYTES_IN_INT);
      Offset baseOffset = Offset.zero().minus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
      /* copy */
      // reference offset is sorted in address order.
      // In current implementation, references are clustered and put at the first of the obecjt.
      // But this fact is private in org.jikesrvm.objectmodel.ObjectModel.
      int[] refoffs = type.getReferenceOffsets();
      int refoffoff = 0;
      Offset offset = baseOffset;
      if (refoffs.length > 0) {
        for (; offset.sLT(baseOffset.plus(size)); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
          int fromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          if (refoffs[refoffoff] == offset.toInt()) {
            ObjectReference fromValEquiv = trace.traceObject(Address.fromIntSignExtend(fromVal).toObjectReference(), false);
            Magic.setObjectAtOffset(toCopy.toObject(), offset, fromValEquiv.toObject());
            if (++refoffoff >= refoffs.length) {
              offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
              break;
            }
          } else
            Magic.setIntAtOffset(toCopy.toObject(), offset, fromVal);
        }
      }
      for (; offset.sLT(baseOffset.plus(size)); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
        int fromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
        Magic.setIntAtOffset(toCopy.toObject(), offset, fromVal);
      }
      Magic.fence();
      /* verify */
      refoffoff = 0;
      offset = baseOffset;
      if (refoffs.length > 0) {
        for (; offset.sLT(baseOffset.plus(size)); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
          int fromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          if (refoffs[refoffoff] == offset.toInt()) {
            ObjectReference fromValEquiv = trace.traceObject(Address.fromIntSignExtend(fromVal).toObjectReference(), false);
            ObjectReference toVal = ObjectReference.fromObject(Magic.getObjectAtOffset(toCopy.toObject(), offset));
            if (fromValEquiv.toAddress().NE(toVal.toAddress()))
              return false;
            if (++refoffoff >= refoffs.length) {
              offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
              break;
            }
          } else {
            int toVal = Magic.getIntAtOffset(toCopy.toObject(), offset);
            if (fromVal != toVal)
              return false;
          }
        }
      }
      for (; offset.sLT(baseOffset.plus(size)); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
        int currentFromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
        int currentToVal = Magic.getIntAtOffset(toCopy.toObject(), offset);
        if (currentFromVal != currentToVal)
          return false;
      }
      return true;
    } else { // array type
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(type.isArrayType());
      RVMType elementType = type.asArray().getElementType();
      int length = getArrayLength(fromCopy);
      if (elementType.isReferenceType()) {
        int numBytes = length << org.jikesrvm.SizeConstants.LOG_BYTES_IN_ADDRESS;
        Offset end = Offset.fromIntSignExtend(numBytes);
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS)) {
          ObjectReference fromVal = ObjectReference.fromObject(Magic.getObjectAtOffset(fromCopy.toObject(), offset));
          ObjectReference fromEquiv = trace.traceObject(fromVal, false);
          Magic.setObjectAtOffset(toCopy.toObject(), offset, fromEquiv.toObject());
        }
        Magic.fence();
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS)) {
          ObjectReference fromVal = ObjectReference.fromObject(Magic.getObjectAtOffset(fromCopy.toObject(), offset));
          ObjectReference toVal = ObjectReference.fromObject(Magic.getObjectAtOffset(toCopy, offset));
          if (fromVal.toAddress().NE(toVal.toAddress()))
            return false;
        }
      } else {
        int numBytes = Memory.alignUp(elementType.getMemoryBytes() * length, org.jikesrvm.SizeConstants.BYTES_IN_INT);
        Offset end = Offset.fromIntSignExtend(numBytes);
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
          int oldFromSpaceVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          Magic.setIntAtOffset(toCopy.toObject(), offset, oldFromSpaceVal);
        }
        Magic.fence();
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
          int currentFromSpaceValue = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          int currentToSpaceValue = Magic.getIntAtOffset(toCopy.toObject(), offset);
          if (currentFromSpaceValue != currentToSpaceValue)
            return false;
        }
      }
      return true;
    }
  }

  @Override
  @Inline
  public int concurrentCopySTMCopy(TraceLocal trace, ObjectReference fromCopy, ObjectReference toCopy, Address buf, Extent buflen) {
    RVMType type = Magic.getObjectType(fromCopy.toObject());
    if (type.isClassType()) {
      if (type.asClass().getInstanceSize() > buflen.toInt())
        return CONCURRENT_COPY_FAILED;
      buf = buf.minus(JavaHeader.getHeaderEndOffset());
      for (RVMField field : type.getInstanceFields()) {
        Offset offset = field.getOffset();
        if (field.isTraced()) {
          ObjectReference oldFromSpaceValue = ObjectReference.fromObject(Magic.getObjectAtOffset(fromCopy.toObject(), offset));
          buf.store(oldFromSpaceValue, offset);
          ObjectReference fromSpaceEquivalent = trace.traceObject(oldFromSpaceValue, false);
          Magic.setObjectAtOffset(toCopy.toObject(), offset, fromSpaceEquivalent.toObject());
        } else if (field.getSize() == 8) {
          long oldFromSpaceValue = Magic.getLongAtOffset(fromCopy.toObject(), offset);
          buf.store(oldFromSpaceValue, offset);
          Magic.setLongAtOffset(toCopy.toObject(), offset, oldFromSpaceValue);
        } else {
          offset = Offset.fromIntZeroExtend((offset.toWord().and(Word.fromIntZeroExtend(3).not())).toInt()); // round down the offset to a word boundary
          int oldFromSpaceValue = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          buf.store(oldFromSpaceValue, offset);
          Magic.setIntAtOffset(toCopy.toObject(), offset, oldFromSpaceValue);
        }
      }        
      return type.asClass().getInstanceSize(); // success
    } else if (type.isArrayType()) {
      RVMType elementType = type.asArray().getElementType();
      int fromCopyLength = getArrayLength(fromCopy);
      int numBytes = elementType.getMemoryBytes() * fromCopyLength;
      if (numBytes > buflen.toInt())
        return CONCURRENT_COPY_FAILED;
      if (elementType.isReferenceType()) {
        int copiedBytes = 0;
        for (; copiedBytes < numBytes; copiedBytes += SizeConstants.BYTES_IN_ADDRESS) {
          Offset offset = Offset.fromIntSignExtend(copiedBytes);
          ObjectReference oldFromSpaceValue = ObjectReference.fromObject(Magic.getObjectAtOffset(fromCopy.toObject(), offset));
          buf.store(oldFromSpaceValue, offset);
          ObjectReference fromSpaceEquivalent = trace.traceObject(oldFromSpaceValue, false);
          Magic.setObjectAtOffset(toCopy.toObject(), offset, fromSpaceEquivalent);
        }
        return copiedBytes;
      } else {
        int copiedBytes = 0;
        for (; copiedBytes < numBytes; copiedBytes += SizeConstants.BYTES_IN_INT) {
          Offset offset = Offset.fromIntSignExtend(copiedBytes);
          int oldFromSpaceVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          buf.store(oldFromSpaceVal, offset);
          Magic.setIntAtOffset(toCopy.toObject(), offset, oldFromSpaceVal);
        }
        return copiedBytes;
      }
    } else {
      VM.assertions.fail("Unknown type");
      return CONCURRENT_COPY_FAILED;
    }
  }

  @Override
  @Inline
  public boolean concurrentCopySTMVerify(ObjectReference fromCopy, ObjectReference toCopy, Address buf) {
    RVMType type = Magic.getObjectType(fromCopy.toObject());
    if (type.isClassType()) {
      buf = buf.minus(JavaHeader.getHeaderEndOffset());
      for (RVMField field : type.getInstanceFields()) {
        Offset offset = field.getOffset();
        if (field.getSize() == 8) {
          long currentFromSpaceValue = Magic.getLongAtOffset(fromCopy.toObject(), offset);
          long oldFromSpaceValue = buf.loadLong(offset);
          if (currentFromSpaceValue != oldFromSpaceValue)
            return false;
        } else {
          offset = Offset.fromIntZeroExtend((offset.toWord().and(Word.fromIntZeroExtend(3).not())).toInt()); // round down the offset to a word boundary
          int currentFromSpaceValue = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          int oldFromSpaceValue = buf.loadInt(offset);
          if (currentFromSpaceValue != oldFromSpaceValue)
            return false;
        }
      }        
      return true;
    } else if (type.isArrayType()) {
      RVMType elementType = type.asArray().getElementType();
      int fromCopyLength = getArrayLength(fromCopy);
      int numBytes = elementType.getMemoryBytes() * fromCopyLength;
      for (int i = 0; i < numBytes; i += 4) {
        Offset offset = Offset.fromIntSignExtend(i);
        int currentFromSpaceValue = Magic.getIntAtOffset(fromCopy.toObject(), offset);
        int oldFromSpaceValue = buf.loadInt(offset);
        if (currentFromSpaceValue != oldFromSpaceValue)
          return false;
      }
      return true;
    } else {
      VM.assertions.fail("Unknown type");
      return false;
    }
  }

  @Override
  @Inline
  public int concurrentCopySTMSeqCopy(TraceLocal trace, ObjectReference fromCopy, ObjectReference toCopy, Address buf, Extent buflen) {
    RVMType type = Magic.getObjectType(fromCopy.toObject());
    if (type.isClassType()) {
      int size = type.asClass().getInstanceSize() - (JavaHeader.OBJECT_REF_OFFSET - org.jikesrvm.SizeConstants.BYTES_IN_INT);
      size = Memory.alignUp(size, org.jikesrvm.SizeConstants.BYTES_IN_INT);
      if (size > buflen.toInt())
        return CONCURRENT_COPY_FAILED;
      buf = buf.minus(JavaHeader.getHeaderEndOffset());
      Offset baseOffset = Offset.zero().minus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
      int[] refoffs = type.getReferenceOffsets();
      int refoffoff = 0;
      Offset offset = baseOffset;
      if (refoffs.length > 0) {
        for (; offset.sLT(baseOffset.plus(size)); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
          int fromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          if (refoffs[refoffoff] == offset.toInt()) {
            buf.store(fromVal, offset);
            ObjectReference fromValEquiv = trace.traceObject(Address.fromIntSignExtend(fromVal).toObjectReference(), false);
            Magic.setObjectAtOffset(toCopy.toObject(), offset, fromValEquiv.toObject());
            if (++refoffoff >= refoffs.length) {
              offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
              break;
            }
          } else
            Magic.setIntAtOffset(toCopy.toObject(), offset, fromVal);
        }
      }
      for (; offset.sLT(baseOffset.plus(size)); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
        int fromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
        Magic.setIntAtOffset(toCopy.toObject(), offset, fromVal);
      }
      return size;
    } else {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(type.isArrayType());
      RVMType elementType = type.asArray().getElementType();
      int length = getArrayLength(fromCopy);
      if (elementType.isReferenceType()) {
        int numBytes = length << org.jikesrvm.SizeConstants.LOG_BYTES_IN_ADDRESS;
        if (numBytes > buflen.toInt())
          return CONCURRENT_COPY_FAILED;
        Offset end = Offset.fromIntSignExtend(numBytes);
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS)) {
          ObjectReference fromVal = ObjectReference.fromObject(Magic.getObjectAtOffset(fromCopy.toObject(), offset));
          buf.store(fromVal, offset);
          ObjectReference fromEquiv = trace.traceObject(fromVal, false);
          Magic.setObjectAtOffset(toCopy.toObject(), offset, fromEquiv.toObject());
        }
        return numBytes;
      } else {
        int numBytes = Memory.alignUp(elementType.getMemoryBytes() * length, org.jikesrvm.SizeConstants.BYTES_IN_INT);
        Offset end = Offset.fromIntSignExtend(numBytes);
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
          int oldFromSpaceVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          Magic.setIntAtOffset(toCopy.toObject(), offset, oldFromSpaceVal);
        }
        return 0;
      }
    }
  }

  @Override
  @Inline
  public boolean concurrentCopySTMSeqVerify(ObjectReference fromCopy, ObjectReference toCopy, Address buf) {
    RVMType type = Magic.getObjectType(fromCopy.toObject());
    if (type.isClassType()) {
      int size = type.asClass().getInstanceSize() - (JavaHeader.OBJECT_REF_OFFSET - org.jikesrvm.SizeConstants.BYTES_IN_INT);
      size = Memory.alignUp(size, org.jikesrvm.SizeConstants.BYTES_IN_INT);
      buf = buf.minus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
      Offset baseOffset = Offset.zero().minus(JavaHeader.getHeaderEndOffset());
      int[] refoffs = type.getReferenceOffsets();
      int refoffoff = 0;
      Offset offset = baseOffset;
      if (refoffs.length > 0) {
        for (; offset.sLT(baseOffset.plus(size)); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
          int currentFromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          if (refoffs[refoffoff] == offset.toInt()) {
            int oldFromVal = buf.loadInt(offset);
            if (currentFromVal != oldFromVal)
              return false;
            if (++refoffoff >= refoffs.length) {
              offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT);
              break;
            }
          } else {
            int currentToVal = Magic.getIntAtOffset(toCopy.toObject(), offset);
            if (currentFromVal != currentToVal)
              return false;
          }
        }
      }
      for (; offset.sLT(baseOffset.plus(size)); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
        int currentFromVal = Magic.getIntAtOffset(fromCopy.toObject(), offset);
        int currentToVal = Magic.getIntAtOffset(toCopy.toObject(), offset);
        if (currentFromVal != currentToVal)
          return false;
      }
    } else {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(type.isArrayType());
      RVMType elementType = type.asArray().getElementType();
      int length = getArrayLength(fromCopy);
      if (elementType.isReferenceType()) {
        int numBytes = length << org.jikesrvm.SizeConstants.LOG_BYTES_IN_ADDRESS;
        Offset end = Offset.fromIntSignExtend(numBytes);
        Magic.fence();
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS)) {
          int currentFromSpaceValue = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          int oldFromSpaceValue = buf.loadInt(offset);
          if (currentFromSpaceValue != oldFromSpaceValue)
            return false;
        }
      } else {
        int numBytes = Memory.alignUp(elementType.getMemoryBytes() * length, org.jikesrvm.SizeConstants.BYTES_IN_INT);
        Offset end = Offset.fromIntSignExtend(numBytes);
        Magic.fence();
        for (Offset offset = Offset.zero(); offset.sLT(end); offset = offset.plus(org.jikesrvm.SizeConstants.BYTES_IN_INT)) {
          int currentFromSpaceValue = Magic.getIntAtOffset(fromCopy.toObject(), offset);
          int currentToSpaceValue = Magic.getIntAtOffset(toCopy.toObject(), offset);
          if (currentFromSpaceValue != currentToSpaceValue)
            return false;
        }
      }
    }
    return true;
  }

  @Override
  public int getBytesWhenCopied(ObjectReference object) {
    RVMType type = Magic.getObjectType(object.toObject());
    if (type.isClassType()) {
      return type.asClass().getInstanceSize() - (JavaHeader.OBJECT_REF_OFFSET - org.jikesrvm.SizeConstants.BYTES_IN_INT);
    } else {
      RVMType elementType = type.asArray().getElementType();
      return elementType.getMemoryBytes() * getArrayLength(object);
    }
  }

  @Override
  public int getNumberOfPointerFields(ObjectReference object) {
    RVMType type = Magic.getObjectType(object.toObject());
    if (type.isClassType())
      return type.asClass().getReferenceOffsets().length;
    else if (type.asArray().getElementType().isReferenceType())
      return getArrayLength(object);
    else
      return 0;
  }

  public Offset getThinLockOffset(ObjectReference o) {
    return org.jikesrvm.objectmodel.ObjectModel.getThinLockOffset(o.toObject());
  }
  
  /**
   * Tell if o is hashed or not.
   * @param o
   * @return True if o is not hashed.  False if o is hashed regardless of whether it is moved or not.
   */
  @Override
  public boolean isUnhashed(ObjectReference o) {
    return org.jikesrvm.objectmodel.ObjectModel.isUnhashed(o);
  }

  /**
   * Make the object in the hashed state.  If it has already been hashed, do nothing.
   * @param o
   */
  @Override
  public void setHashed(ObjectReference o) {
    org.jikesrvm.objectmodel.ObjectModel.setHashed(o);
  }
}

