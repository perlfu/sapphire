/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import com.ibm.JikesRVM.memoryManagers.JMTk.AddressQueue;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Statics;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * Class that determines all JTOC slots (statics) that hold references
 *
 * @author Perry Cheng
 */  
public class ScanStatics
  implements Constants, VM_Constants {

  /**
   * Scan static variables (JTOC) for object references.
   * Executed by all GC threads in parallel, with each doing a portion of the JTOC.
   */
  public static void scanStatics (AddressQueue rootLocations) throws VM_PragmaUninterruptible {

    int numSlots = VM_Statics.getNumberOfSlots();
    VM_Address slots    = VM_Magic.objectAsAddress(VM_Statics.getSlots());
    int chunkSize = 512;
    int slot, start, end, stride, slotAddress;
    VM_CollectorThread ct;

    stride = chunkSize * VM_CollectorThread.numCollectors();
    ct = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    start = (ct.getGCOrdinal() - 1) * chunkSize;

    while ( start < numSlots ) {
      end = start + chunkSize;
      if (end > numSlots)
	end = numSlots;  // doing last segment of JTOC

      for ( slot=start; slot<end; slot++ ) {

	if ( ! VM_Statics.isReference(slot) ) continue;

	// slot contains a ref of some kind.  call collector specific
	// processPointerField, passing address of reference
	//
	rootLocations.push(slots.add(slot << LOG_WORD_SIZE));

      }  // end of for loop

      start = start + stride;

    }  // end of while loop

  }  // scanStatics

  /*
  static boolean validateRefs () throws VM_PragmaUninterruptible {
    int numSlots = VM_Statics.getNumberOfSlots();
    VM_Address slots    = VM_Magic.objectAsAddress(VM_Statics.getSlots());
    boolean result = true;
    for ( int slot=0; slot<numSlots; slot++ ) {
      if ( ! VM_Statics.isReference(slot) ) continue;
      VM_Address ref = VM_Magic.getMemoryAddress(slots.add(slot << LOG_WORD_SIZE));
      if ( (!ref.isZero()) && !VM_GCUtil.validRef(ref) ) {
	VM.sysWrite("\nScanStatics.validateRefs:bad ref in slot "); VM.sysWrite(slot,false); VM.sysWrite("\n");
	VM.sysWriteHex(slot); VM.sysWrite(" ");
	VM_GCUtil.dumpRef(ref);
	result = false;
      }
    }  // end of for loop
    return result;
  }  // validateRefs


  static boolean validateRefs ( int depth ) throws VM_PragmaUninterruptible {
    int numSlots = VM_Statics.getNumberOfSlots();
    VM_Address slots    = VM_Magic.objectAsAddress(VM_Statics.getSlots());
    boolean result = true;
    for ( int slot=0; slot<numSlots; slot++ ) {
      if ( ! VM_Statics.isReference(slot) ) continue;
      VM_Address ref = VM_Address.fromInt(VM_Magic.getMemoryWord(slots.add(slot << LOG_WORD_SIZE)));
      if ( ! VM_ScanObject.validateRefs( ref, depth ) ) {
	VM.sysWrite("ScanStatics.validateRefs: Bad Ref reached from JTOC slot ");
	VM.sysWrite(slot,false);
	VM.sysWrite("\n");
	result = false;
      }
    }
    return result;
  }

  static void dumpRefs ( int start, int count ) throws VM_PragmaUninterruptible {
    int numSlots = VM_Statics.getNumberOfSlots();
    VM_Address slots    = VM_Magic.objectAsAddress(VM_Statics.getSlots());
    int last     = start + count;
    if (last > numSlots) last = numSlots;
    VM.sysWrite("Dumping Static References...\n");
      for ( int slot=start; slot<last; slot++ ) {
	if ( ! VM_Statics.isReference(slot) ) continue;
	VM_Address ref = VM_Address.fromInt(VM_Magic.getMemoryWord(slots.add(slot << LOG_WORD_SIZE)));
	if (!ref.isZero()) {
	  VM.sysWrite(slot,false); VM.sysWrite(" "); VM_GCUtil.dumpRef(ref);
	}
      }  // end of for loop
    VM.sysWrite("Done\n");
  }  // dumpRefs
*/

}   // VM_ScanStatics
