/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;
import com.ibm.JikesRVM.BootImageInterface;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Memory;
/**
 * Chooses the appropriate collector-specific header model.
 *
 * @see VM_ObjectModel
 * 
 * @author Perry Cheng
 */
public class Header extends HybridHeader {

  // Merges all the headers together.  In this case, we have only one.

  public final static int GC_BARRIER_BIT_MASK = -1;  // must be defined even though unused
  public static boolean isBeingForwarded(Object base) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(false);
    return false;
  }

  public static boolean isForwarded(Object base) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(false);
    return false;
  }

  static void setBarrierBit(Object ref)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(false);
  }
}
