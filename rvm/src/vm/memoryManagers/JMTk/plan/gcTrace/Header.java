/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package org.mmtk.plan;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;

/**
 * Chooses the appropriate collector-specific header model.
 *
 * @see VM_ObjectModel
 * 
 * @author Perry Cheng
 */
import org.mmtk.vm.VM_Interface;
public class Header extends HybridHeader {

  // Merges all the headers together.  In this case, we have only one.

  public final static VM_Word GC_BARRIER_BIT_MASK = VM_Word.fromIntSignExtend(-1); // must be defined even though unused
  public static boolean isBeingForwarded(VM_Address base) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (Plan.isSemiSpaceObject(base))
      return CopyingHeader.isBeingForwarded(base);
    else
      return false;
  }

  public static boolean isForwarded(VM_Address base) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (Plan.isSemiSpaceObject(base))
      return CopyingHeader.isForwarded(base);
    else
      return false;
  }

  static void setBarrierBit(VM_Address ref)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Interface._assert(false);
  }
}
