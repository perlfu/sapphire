/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Memory;


/*
 * @author Perry Cheng  
 */  

public class Memory implements VM_Uninterruptible {

  // Inlining this loop into the uninterruptible code can cause/encourage the GCP 
  // into moving a get_obj_tib into the interruptible region where the tib is being
  // installed via an int_store
  //
  private static boolean isZeroedHelper(VM_Address start, EXTENT size, boolean verbose) throws VM_PragmaNoInline {
    if (VM.VerifyAssertions) VM._assert(size == (size & (~3)));
    for (int i=0; i<size; i+=4) 
      if (!VM_Magic.getMemoryAddress(start.add(i)).isZero()) {
	if (verbose) {
	  VM.sysWrite("Memory range is not zeroed: ", start);
	  VM.sysWriteln(" .. ", start.add(size));
	  dumpMemory(start, 0, size);
	}
	return false;
      }
    return true;
  }

  public static boolean IsZeroed(VM_Address start, EXTENT size) throws VM_PragmaInline {
    return isZeroedHelper(start, size, false);
  }

  public static boolean assertIsZeroed(VM_Address start, EXTENT size) throws VM_PragmaInline {
    return isZeroedHelper(start, size, true);
  }

  // start and len must both be 4-byte aligned
  //
  public static void zero(VM_Address start, int len) throws VM_PragmaInline {
    if (len > 256)
      VM_Memory.zero(start, len);
    else
      zeroSmall(start, len);
  }

  public static void zeroSmall(VM_Address start, int len) throws VM_PragmaInline {
    for (int i=0; i<len; i+=4) 
      VM_Magic.setMemoryWord(start.add(i), 0);
  }

  // start and len must both be OS-page aligned
  //
  public static void zeroPages(VM_Address start, int len) throws VM_PragmaInline {
    VM_Memory.zeroPages(start, len);
  }

  // Derived forms
  public static void zeroSmall(VM_Address start, VM_Address end) throws VM_PragmaInline {
    zeroSmall(start, end.diff(start).toInt());
  }

  public static void zero(VM_Address start, VM_Address end) throws VM_PragmaInline {
    zero(start, end.diff(start).toInt());
  }

  public static void dumpMemory(VM_Address addr, int before, int after) {
    VM_Memory.dumpMemory(addr, before, after);
  }

}
