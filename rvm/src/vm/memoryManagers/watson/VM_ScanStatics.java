/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Class that supports scanning statics (the JTOC) for references
 * and processing those references
 *
 * @author Stephen Smith
 */  
public class VM_ScanStatics
  implements VM_Constants, VM_GCConstants, VM_Uninterruptible {

  /**
   * Scan static variables (JTOC) for object references.
   * Executed by all GC threads in parallel, with each doing a portion of the JTOC.
   */
  static void
  scanStatics () {
    int numSlots = VM_Statics.getNumberOfSlots();
    int slots    = VM_Magic.objectAsAddress(VM_Statics.getSlots());
    int chunkSize = 512;
    int slot, start, end, stride, slotAddress;
    VM_CollectorThread ct;

    stride = chunkSize * VM_CollectorThread.numCollectors();
    ct = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    start = (ct.gcOrdinal - 1) * chunkSize;

    while ( start < numSlots ) {
      end = start + chunkSize;
      if (end > numSlots)
	end = numSlots;  // doing last segment of JTOC

      for ( slot=start; slot<end; slot++ ) {

	if ( ! VM_Statics.isReference(slot) ) continue;

	// slot contains a ref of some kind.  call collector specific
	// processPointerField, passing address of reference
	//
	VM_Allocator.processPtrField(slots + (slot << LG_WORDSIZE));

      }  // end of for loop

      start = start + stride;

    }  // end of while loop

  }  // scanStatics


  static boolean
  validateRefs () {
    int numSlots = VM_Statics.getNumberOfSlots();
    int slots    = VM_Magic.objectAsAddress(VM_Statics.getSlots());
    boolean result = true;
    for ( int slot=0; slot<numSlots; slot++ ) {
      if ( ! VM_Statics.isReference(slot) ) continue;
      int ref = VM_Magic.getMemoryWord(slots + (slot << LG_WORDSIZE));
      if ( (ref!=VM_NULL) && !VM_GCUtil.validRef(ref) ) {
	VM.sysWrite("\nScanStatics.validateRefs:bad ref in slot "); VM.sysWrite(slot,false); VM.sysWrite("\n");
	VM.sysWriteHex(slot); VM.sysWrite(" ");
	VM_GCUtil.dumpRef(ref);
	result = false;
      }
    }  // end of for loop
    return result;
  }  // validateRefs


  static boolean
  validateRefs ( int depth ) {
    int numSlots = VM_Statics.getNumberOfSlots();
    int slots    = VM_Magic.objectAsAddress(VM_Statics.getSlots());
    boolean result = true;
    for ( int slot=0; slot<numSlots; slot++ ) {
      if ( ! VM_Statics.isReference(slot) ) continue;
      int ref = VM_Magic.getMemoryWord(slots + (slot << LG_WORDSIZE));
      if ( ! VM_ScanObject.validateRefs( ref, depth ) ) {
	VM.sysWrite("ScanStatics.validateRefs: Bad Ref reached from JTOC slot ");
	VM.sysWrite(slot,false);
	VM.sysWrite("\n");
	result = false;
      }
    }
    return result;
  }

  static void
  dumpRefs ( int start, int count ) {
    int numSlots = VM_Statics.getNumberOfSlots();
    int slots    = VM_Magic.objectAsAddress(VM_Statics.getSlots());
    int last     = start + count;
    if (last > numSlots) last = numSlots;
    VM.sysWrite("Dumping Static References...\n");
      for ( int slot=start; slot<last; slot++ ) {
	if ( ! VM_Statics.isReference(slot) ) continue;
	int ref = VM_Magic.getMemoryWord(slots + (slot << LG_WORDSIZE));
	if (ref!=VM_NULL) {
	  VM.sysWrite(slot,false); VM.sysWrite(" "); VM_GCUtil.dumpRef(ref);
	}
      }  // end of for loop
    VM.sysWrite("Done\n");
  }  // dumpRefs



}   // VM_ScanStatics
