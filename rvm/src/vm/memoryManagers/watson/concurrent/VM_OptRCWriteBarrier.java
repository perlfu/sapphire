/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * This class contains the Java code that the opt compiler will
 * inline at every (1) ref_astore, (2) putfield of reference type 
 * and (3) putstatic of reference type to implement the store.
 * This methods must 
 *   (a) perform any necessary dynamic linking
 *   (b) perform the store
 *   (c) perform all other operations that RCGC needs to do at the store.
 *
 * NOTE: If the code defined here contains any aastores, putfields, or putstatics
 *       of references, they will NOT have write barrier code inserted (infinite loop).
 *
 * @author Dave Grove
 * @author David F. Bacon
 * 
 * @see OPT_SpecialInline (logic to inline this code to replace the normal implementation)
 * @see VM_RCBarriers (baseline compiler implementation of RC write barriers)
 *
 */
class VM_OptRCWriteBarrier implements VM_Constants, VM_Uninterruptible {

  /**
   * This method is inlined to emit the increment and decrement values to the buffer
   *
   * @param oldval The old value to be decremented.
   * @param newval The new value to be incremented.
   */
    static final void emitBufferStores(VM_Address oldval, VM_Address newval) throws VM_PragmaInline {

	VM_Processor p = VM_Processor.getCurrentProcessor();
	VM_Address bufferPointer = p.incDecBufferTop;

	// emit address of object whose refcount is to be incremented
	if (newval.NE(VM_Magic.objectAsAddress(null))) {
	    bufferPointer = bufferPointer.add(4);
	    VM_Magic.setMemoryAddress(bufferPointer, newval);
	}

	// emit address of object whose refcount is to be decremented
	if (oldval.NE(VM_Magic.objectAsAddress(null))) {
	    oldval = VM_Address.fromInt(oldval.toInt() | VM_RCBuffers.DECREMENT_FLAG);
	    bufferPointer = bufferPointer.add(4);
	    VM_Magic.setMemoryAddress(bufferPointer, oldval);
	}	    

	// store updated buffer pointer
	p.incDecBufferTop = bufferPointer;

	// check for overflow
	if (bufferPointer.GT(p.incDecBufferMax))
	    VM_RCBuffers.processIncDecBuffer();
    }

  /**
   * This method is inlined to implement an aastore.
   *
   * @param ref   The base pointer of the array
   * @param index The array index being stored into.  NOTE: This is the "natural" index; a[3] will pass 3.
   * @param value The value being stored
   */
  static void aastore(Object ref, int index, Object value) {
      // atomically store value at ref[index]
      VM_Address oldval;
      VM_Address newval = VM_Magic.objectAsAddress(value);
      do {
	oldval = VM_Address.fromInt(VM_Magic.prepare(ref, index<<2));
      } while (! VM_Magic.attempt(ref, index<<2, oldval.toInt(), newval.toInt()));

      // enqueue oldval and newval values in buffer
      emitBufferStores(oldval, newval);
  }

  /**
   * This method is inlined to implement a resolved putfield of a reference. 
   *
   * @param ref    The base pointer of the array
   * @param offset The offset being stored into.  NOTE: This is in bytes.
   * @param value  The value being stored
   */
  static void resolvedPutfield(Object ref, int offset, Object value) {
      VM_Address oldval;
      VM_Address newval = VM_Magic.objectAsAddress(value);
      do {
	  oldval = VM_Address.fromInt(VM_Magic.prepare(ref, offset));
      } while (! VM_Magic.attempt(ref, offset, oldval.toInt(), newval.toInt()));

      // enqueue oldval and newval values in buffer
      emitBufferStores(oldval, newval);
  }


  /**
   * This method is inlined to implement an unresolved putfield of a reference.
   *
   * @param ref   The base pointer of the array
   * @param fid   The field id that is being stored into.
   * @param value The value being stored
   */
  static void unresolvedPutfield(Object ref, int fid, Object value) {
    int offset = VM_TableBasedDynamicLinker.getFieldOffset(fid);
    // if we're doing a putfield, we've instantiated ref already, therefore
    // the offset can't possibly still be unresolved.
    if (VM.VerifyAssertions) VM.assert(offset != NEEDS_DYNAMIC_LINK);
    resolvedPutfield(ref, offset, value);
  }


  /**
   * This method is inlined to implement a resolved putstatic of a reference.
   *
   * @param ref    The base pointer of the array
   * @param offset The offset being stored into.  NOTE: This is in bytes.
   * @param value  The value being stored
   */
  static void resolvedPutstatic(int offset, Object value) {
      VM_Address oldval;
      VM_Address newval = VM_Magic.objectAsAddress(value);
      do {
	  oldval = VM_Address.fromInt(VM_Magic.prepare(VM_Magic.getJTOC(), offset));
      } while (! VM_Magic.attempt(VM_Magic.getJTOC(), offset, oldval.toInt(), newval.toInt()));

      // enqueue oldval and newval values in buffer
      emitBufferStores(oldval, newval);
  }


  /**
   * This method is inlined to implement an unresolved putstatic of a reference.
   *
   * @param ref   The base pointer of the array
   * @param fid   The field id that is being stored into.
   * @param value The value being stored
   */
  static void unresolvedPutstatic(int fid, Object value) throws Throwable {
    int offset = VM_TableBasedDynamicLinker.getFieldOffset(fid);
    if (offset == NEEDS_DYNAMIC_LINK) {
      VM_Field target = VM_FieldDictionary.getValue(fid);
      VM_Runtime.initializeClassForDynamicLink(target.getDeclaringClass());
      offset = VM_TableBasedDynamicLinker.getFieldOffset(fid);
      if (VM.VerifyAssertions) VM.assert(offset != NEEDS_DYNAMIC_LINK);
    }
    resolvedPutstatic(offset, value);
  }
}
