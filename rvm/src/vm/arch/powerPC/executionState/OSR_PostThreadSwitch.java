/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;
import com.ibm.JikesRVM.*;
/**
 * Code used for recover register value after on stack replacement.
 *
 * @author Feng Qian
 */

public class OSR_PostThreadSwitch implements VM_BaselineConstants, VM_Uninterruptible {

  /* This method must be inlined to keep the correctness 
   * This method is called at the end of threadSwitch, the caller
   * is threadSwitchFrom<...>
   */
  public static void postProcess(VM_Thread myThread) 
    throws VM_PragmaNoInline {

    /* We need to generate thread specific code and install new code.
     * We have to make sure that no GC happens from here and before 
     * the new code get executed.
     */
	// add branch instruction from CTR.
    INSTRUCTION[] bridge   = myThread.bridgeInstructions;
      
    VM_Address bridgeaddr = VM_Magic.objectAsAddress(bridge);
	
    int fooFPOffset = myThread.fooFPOffset;
    VM_Magic.setIntAtOffset(myThread.stack, 
			    fooFPOffset + STACKFRAME_NEXT_INSTRUCTION_OFFSET, 
			    bridgeaddr.toInt());
    myThread.fooFPOffset = 0;

    myThread.isWaitingForOsr = false;
    myThread.bridgeInstructions = null;
  }
}
