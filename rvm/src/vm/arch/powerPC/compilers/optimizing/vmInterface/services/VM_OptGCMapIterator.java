/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

/**
 * An instance of this class provides iteration across the references 
 * represented by a frame built by the OPT compiler.
 *
 * The architecture-specific version of the GC Map iterator.  It inherits
 * its architecture-independent code from VM_OptGenericGCMapIterator.
 * This version is for the PowerPC
 *
 * @author Michael Hind
 */
public final class VM_OptGCMapIterator extends VM_OptGenericGCMapIterator 
  implements VM_Uninterruptible {


  private final static boolean DEBUG = false;

  public VM_OptGCMapIterator(int[] registerLocations) {
    super(registerLocations);
  }

  /** 
   * If any non-volatile gprs were saved by the method being processed
   * then update the registerLocations array with the locations where the
   * registers were saved.
   *
   */
  void updateLocateRegisters() {

    //  HIGH MEMORY
    //
    //    +------------------+                                           
    //    |  NVolGPR[n]      |
    //    |     ...          |   k == info.getFirstNonVolatileGPR()      
    //    |  NVolGPR[k]      |  <-- info.getUnsignedNonVolatileOffset()      
    //    +------------------+                                           
    //    |  ScratchGPR[LAST]|                                           
    //    |     ...          |     only SaveVolatile Frames              
    //    | ScratchGPR[FIRST]|                                           
    //    +------------------+                                           
    //    |  VolGPR[LAST]    |                                           
    //    |     ...          |     only SaveVolatile Frames              
    //    |  VolGPR[FIRST]   |                                           
    //    +------------------+                                           
    //
    //  LOW MEMORY
    

    int frameOffset = compiledMethod.getUnsignedNonVolatileOffset();
    if (frameOffset >= 0) {

      // get to the nonVol area
      VM_Address nonVolArea = framePtr.add(frameOffset);
      
      // update non-volatiles that were saved
      int first = compiledMethod.getFirstNonVolatileGPR();
      if (first >= 0) {
	// move to the beginning of the save area for nonvolatiles
	VM_Address location = nonVolArea;
	for (int i = first; i <= LAST_GCMAP_REG; i++) {
	  registerLocations[i] = location.toInt();
	  location = location.add(4);
	}
      }
      
      // update volatiles if needed
      if (compiledMethod.isSaveVolatile()) {
	// move to the beginning of the save area for volatiles
	int numSlotsToSkip = FIRST_NONVOLATILE_GPR - FIRST_VOLATILE_GPR;
	VM_Address location = nonVolArea.sub(4 * numSlotsToSkip);
	
	// Walk the saved volatiles, updating registerLocations array
	for (int i = FIRST_VOLATILE_GPR; i <= LAST_VOLATILE_GPR; i++) {
	  registerLocations[i] = location.toInt();
	  location = location.add(4);
	}
	
	// Walk the saved scratch, updating registerLocations array
	for (int i = FIRST_SCRATCH_GPR; i <= LAST_SCRATCH_GPR; i++) {
	  registerLocations[i] = location.toInt();
	  location = location.add(4);
	}
      }
    }
  }

  /** 
   *  Determine the stack location given the frame ptr and spill offset.
   *  (The offset direction varies among architectures.)
   *  @param framePtr the frame pointer
   *  @param offset  the offset 
   *  @return the resulting stack location
   */
  VM_Address getStackLocation(VM_Address framePtr, int offset) {
    return framePtr.add(offset);
  }

  /** 
   *  Get address of the first spill location for the given frame ptr
   *  @param the frame pointer
   *  @return the first spill location
   */
  VM_Address getFirstSpillLoc() {
    return framePtr.add(SPILL_DISTANCE_FROM_FP);
  }

  /** 
   *  Get address of the last spill location for the given frame ptr
   *
   *
   *  @param the frame pointer
   *  @return the last spill location, if no spills occur, we return the
   *      first spill location
   */
  VM_Address getLastSpillLoc() {
    if (DEBUG) {
      VM.sysWrite("\n unsigendNVOffset: ");
      VM.sysWrite(compiledMethod.getUnsignedNonVolatileOffset());
      VM.sysWrite("\t isSaveVolatile: ");
      if (compiledMethod.isSaveVolatile()) {
	VM.sysWrite("true");
      } else {
	VM.sysWrite("false");
      }
      VM.sysWrite("\nLAST_VOLATILE_GPR: ");
      VM.sysWrite(LAST_VOLATILE_GPR);
      VM.sysWrite("\tFIRST_VOLATILE_GPR: ");
      VM.sysWrite(LAST_VOLATILE_GPR);
      VM.sysWrite("\nLAST_SCRATCH_GPR: ");
      VM.sysWrite(LAST_SCRATCH_GPR);
      VM.sysWrite("\tFIRST_SCRATCH_GPR: ");
      VM.sysWrite(LAST_SCRATCH_GPR);
      VM.sysWrite("\nSAVE_VOL_SIZE: ");
      VM.sysWrite(SAVE_VOL_SIZE);
      VM.sysWrite("\n");
    }

    // This computation will include some locations that are not technically
    // spill locations.  We do this because we currently do not record 
    // enough info in the VM_OptCompiledMethod object (the one that is available
    // at GC time) to distinguish the lower part of the spill.

    VM_Address firstSpill = getFirstSpillLoc();
    VM_Address lastSpill;
    int nonVolOffset = compiledMethod.getUnsignedNonVolatileOffset();
    if (nonVolOffset != 0) {
      if (compiledMethod.isSaveVolatile()) {
	lastSpill = framePtr.add(nonVolOffset - 4 - SAVE_VOL_SIZE);
      } else {
	lastSpill = framePtr.add(nonVolOffset - 4);
      }
      // If the above computation is less than firstSpill, there are no spills
      if (lastSpill.LT(firstSpill)) {
	lastSpill = firstSpill;
      }
    } else {
      // If nonVolOffset = 0, there are no spills
      lastSpill = firstSpill;
    }
    return lastSpill;
  }

  final static int SPILL_DISTANCE_FROM_FP = 12;
  final static int SAVE_VOL_SIZE = 4 *
    ((LAST_VOLATILE_GPR - FIRST_VOLATILE_GPR + 1) + 
     (LAST_SCRATCH_GPR - FIRST_SCRATCH_GPR + 1)); 

}
