/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.opt.*;

/**
 * Wrapper class around IR info that is valid on the MIR
 *
 * @author Dave Grove
 * @author Mauricio Serrano
 */
public final class OPT_MIRInfo {
  
  /**
   * The generated machinecodes produced by this compilation of 'method'
   */
  public INSTRUCTION[] machinecode;

  /**
   * The IRMap for the method (symbolic GCMapping info)
   */
  public OPT_GCIRMap  gcIRMap;

  /**
   * The frame size of the current method
   */
  public int FrameSize;

  /**
   * The number of floating point stack slots allocated.
   * (Only used on IA32)
   */
  public int fpStackHeight;

  /**
   * A basic block holding the call to VM_Thread.threadSwitch for a
   * prologue.
   */
  public OPT_BasicBlock prologueYieldpointBlock = null;

  /**
   * A basic block holding the call to VM_Thread.threadSwitch for an
   * epilogue.
   */
  public OPT_BasicBlock epilogueYieldpointBlock = null;

  /**
   * A basic block holding the call to VM_Thread.threadSwitch for a
   * backedge.
   */
  public OPT_BasicBlock backedgeYieldpointBlock = null;

  /**
   * Information needed for linear scan. 
   */
  public OPT_LinearScan.LinearScanState linearScanState = null;

  public OPT_Instruction instAfterPrologue;
  
  public OPT_MIRInfo(OPT_IR ir) {
    ir.compiledMethod.setSaveVolatile(ir.method.getDeclaringClass().isSaveVolatile());
    ir.compiledMethod.setOptLevel(ir.options.getOptLevel());
  }

}
