/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$ 
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_AllocatorHeader;
import org.vmmagic.unboxed.*;

/**
 * Constants for the JavaHeader. 
 *
 * @see VM_ObjectModel
 * 
 * @author David Bacon
 * @author Steve Blackburn
 * @author Steve Fink
 * @author Daniel Frampton
 * @author Dave Grove
 */
public interface VM_JavaHeaderConstants extends VM_SizeConstants {

  static final int TIB_BYTES         = BYTES_IN_ADDRESS;
  static final int STATUS_BYTES      = BYTES_IN_ADDRESS;

  static final int ALIGNMENT_MASK    = 0x00000001;
  static final int ALIGNMENT_VALUE   = 0xdeadbeef;
  static final int LOG_MIN_ALIGNMENT = LOG_BYTES_IN_INT;

  /* we use 64 bits for the length on a 64 bit architecture as this makes 
     the other words 8-byte aligned, and the header has to be 8-byte aligned. */
  static final int ARRAY_LENGTH_BYTES = VM.BuildFor64Addr 
                                        ? BYTES_IN_ADDRESS
                                        : BYTES_IN_INT;

  static final int JAVA_HEADER_BYTES = TIB_BYTES + STATUS_BYTES;
  static final int GC_HEADER_BYTES = VM_AllocatorHeader.NUM_BYTES_HEADER;
  static final int MISC_HEADER_BYTES = VM_MiscHeaderConstants.NUM_BYTES_HEADER;
  static final int OTHER_HEADER_BYTES = GC_HEADER_BYTES + MISC_HEADER_BYTES;

  static final Offset ARRAY_LENGTH_OFFSET = Offset.fromIntSignExtend(-ARRAY_LENGTH_BYTES);
  static final Offset JAVA_HEADER_OFFSET  = ARRAY_LENGTH_OFFSET.minus(JAVA_HEADER_BYTES);
  static final Offset MISC_HEADER_OFFSET  = JAVA_HEADER_OFFSET.minus(MISC_HEADER_BYTES);
  static final Offset GC_HEADER_OFFSET    = MISC_HEADER_OFFSET.minus(GC_HEADER_BYTES);
  static final Offset ARRAY_BASE_OFFSET   = Offset.zero();  

  /**
   * This object model supports two schemes for hashcodes:
   * (1) a 10 bit hash code in the object header
   * (2) use the address of the object as its hashcode.
   *     In a copying collector, this forces us to add a word
   *     to copied objects that have had their hashcode taken.
   */
  static final boolean ADDRESS_BASED_HASHING = 
    //-#if RVM_WITH_GCTRACE
    false;
    //-#else
    true;
    //-#endif

  /** How many bits in the header are available for the GC and MISC headers? */
  static final int NUM_AVAILABLE_BITS = ADDRESS_BASED_HASHING ? 8 : 2;

  /**
   * Does this object model use the same header word to contain
   * the TIB and a forwarding pointer?
   */
  static final boolean FORWARDING_PTR_OVERLAYS_TIB = false;

  /**
   * Does this object model place the hash for a hashed and moved object 
   * after the data (at a dynamic offset)
   */
  static final boolean DYNAMIC_HASH_OFFSET 
    = ADDRESS_BASED_HASHING && VM_AllocatorHeader.NEEDS_LINEAR_SCAN;
                                           
  /**
   * Can we perform a linear scan?
   */
  static final boolean ALLOWS_LINEAR_SCAN = true;

  /**
   * Do we need to segregate arrays and scalars to do a linear scan?
   */
  static final boolean SEGREGATE_ARRAYS_FOR_LINEAR_SCAN = false;

  /*
   * Stuff for address based hashing
   */
  static final Word HASH_STATE_UNHASHED         = Word.zero();
  static final Word HASH_STATE_HASHED           = Word.one().lsh(8); //0x00000100
  static final Word HASH_STATE_HASHED_AND_MOVED = Word.fromIntZeroExtend(3).lsh(8); //0x0000300
  static final Word HASH_STATE_MASK             = HASH_STATE_UNHASHED.or(HASH_STATE_HASHED).or(HASH_STATE_HASHED_AND_MOVED);
  
  static final int HASHCODE_BYTES              = BYTES_IN_INT;
  static final Offset HASHCODE_OFFSET = GC_HEADER_OFFSET.minus(HASHCODE_BYTES); 
  
}
