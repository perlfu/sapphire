/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$ 
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_AllocatorHeader;

/**
 * Constants for the JavaHeader. 
 *
 * @see VM_ObjectModel
 * 
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 */
public interface VM_JavaHeaderConstants extends VM_SizeConstants {

  static final int JAVA_HEADER_END = -BYTES_IN_ADDRESS - 2*BYTES_IN_INT;

  static final int ARRAY_LENGTH_OFFSET = -BYTES_IN_INT;

  /**
   * This object model supports two schemes for hashcodes:
   * (1) a 10 bit hash code in the object header
   * (2) use the address of the object as its hashcode.
   *     In a copying collector, this forces us to add a word
   *     to copied objects that have had their hashcode taken.
   */
  static final boolean ADDRESS_BASED_HASHING = true;

  /** How many bits in the header are available for the GC and MISC headers? */
  static final int NUM_AVAILABLE_BITS = ADDRESS_BASED_HASHING ? 8 : 2;

  /**
   * Does this object model use the same header word to contain
   * the TIB and a forwarding pointer?
   */
  static final boolean FORWARDING_PTR_OVERLAYS_TIB = false;

  static final int OTHER_HEADER_BYTES = VM_AllocatorHeader.NUM_BYTES_HEADER + VM_MiscHeader.NUM_BYTES_HEADER;

  /*
   * Stuff for address based hashing
   */
  static final int HASH_STATE_UNHASHED         = 0x00000000;
  static final int HASH_STATE_HASHED           = 0x00000100;
  static final int HASH_STATE_HASHED_AND_MOVED = 0x00000300;
  static final int HASH_STATE_MASK             = HASH_STATE_UNHASHED | HASH_STATE_HASHED | HASH_STATE_HASHED_AND_MOVED;
  static final int HASHCODE_SCALAR_OFFSET      = -BYTES_IN_INT; // in "phantom word"
  static final int HASHCODE_ARRAY_OFFSET       = JAVA_HEADER_END - OTHER_HEADER_BYTES - BYTES_IN_INT; // to left of header
  static final int HASHCODE_BYTES              = BYTES_IN_INT;
  
}
