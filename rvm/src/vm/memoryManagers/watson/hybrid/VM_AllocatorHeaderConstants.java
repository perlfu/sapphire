/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


package com.ibm.JikesRVM.memoryManagers.watson;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Atom;
import com.ibm.JikesRVM.VM_Type;
import com.ibm.JikesRVM.VM_Class;
import com.ibm.JikesRVM.VM_Array;
import com.ibm.JikesRVM.VM_JavaHeaderConstants;

/**
 * Define the constants manipulated by VM_CommonAllocatorHeader. <p>
 * 
 * This collector uses two or three header bits in the object.
 * One bit is a barrier bit used by the write buffer.
 * The other bit(s) are used in the following manner.
 * For bootimage objects, the second bit is a mark bit.
 * For all other objects, we need a forwarding bit to 
 * indicate that the object is/is being forwarded. <p>
 * 
 * If the forwarding pointer is not overlayed in the TIB word,
 * then we can use the mark bit as the forwarding bit because the 
 * meaning of the bit can be determined by the heap segment the object
 * is located in. <p>
 * 
 * If the forwarding pointer does overlay the TIB word,
 * then it becomes too expensive to rely on address ranges to 
 * disambiguate whether or not the mark bit indicates a mark or a forwarding 
 * state.  There are three ways to handle this situations:
 * <ul>
 * <li> use three header bits
 * <li> use a side mark vector for bootimage objects
 * <li> require that the generated code for loading a TIB for classes used 
 *      by the collector use the address range test to disambiguate
 *      forwarding from marking.
 * </ul>
 * We currently just choose between the first two options at build time.
 * The third is believed to be quite bad since it imposes a large overhead
 * for accessing the TIBs of some commonly used runtime classes.
 *
 * @see VM_ObjectModel
 * @see VM_AllocatorHeader
 * @see VM_CommonAllocatorHeader
 * 
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 */
public interface VM_AllocatorHeaderConstants extends VM_JavaHeaderConstants {
  
  /**
   * How many bytes are used by all GC header fields?
   */
  static final int NUM_BYTES_HEADER = 0;

  /**
   * How many bits does this GC system require?
   */
  static final int REQUESTED_BITS = FORWARDING_PTR_OVERLAYS_TIB && NUM_AVAILABLE_BITS >= 3 ? 3 : 2;

  /**
   * Only use a side mark vector if forced to.
   */
  static final boolean USE_SIDE_MARK_VECTOR = FORWARDING_PTR_OVERLAYS_TIB && REQUESTED_BITS == 2;

  /*
   * Values that VM_CommonAllocatorHeader requires that we define.
   */
  static final int GC_BARRIER_BIT_IDX  = 1; 
  static final int GC_FORWARDED        = REQUESTED_BITS == 2 ? 1 : 4; // (01 or 1x0)
  static final int GC_BEING_FORWARDED  = REQUESTED_BITS == 2 ? 3 : 5; // (11 or 1x1)
}
