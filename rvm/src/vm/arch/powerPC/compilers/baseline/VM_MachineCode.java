/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;
import java.util.Vector;

/*
 * A block of machine code in the running virtual machine image.
 *
 * Machine code is an array of "instructions", declared formally as integers,
 * produced by VM_Compiler, typically by translating the bytecodes
 * of a VM_Method. The code entrypoint is the first word in the array.
 *
 * @author Bowen Alpern
 * @author Tony Cocchi 
 * @author Derek Lieber
 */
public final class VM_MachineCode {

  /**
   * Get the instructions comprising this block of machine code.
   */ 
  public INSTRUCTION[] getInstructions() {
    if (VM.VerifyAssertions) VM._assert(instructions != null); // must call "finish" first
    return instructions;
  }

  /** 
   * Get the bytecode-to-instruction map for this block of machine code.
   * @return an array co-indexed with bytecode array. Each entry is an offset
   * into the array of machine codes giving the first instruction that the
   * bytecode compiled to.
   * @see #getInstructions
   */
  public int[] getBytecodeMap() {
    return bytecode_2_machine;
  }

  /**
   * Finish generation of assembler code.
   */ 
  void finish () {
    if (VM.VerifyAssertions) VM._assert(instructions == null); // finish must only be called once

    int n = (next_bundle-1)*size+next;
    instructions = MM_Interface.newInstructions(n);
    int k = 0;
    for (int i=0; i<next_bundle; i++){
      INSTRUCTION[] b = (INSTRUCTION[]) bundles.elementAt(i);
      int m = (i == next_bundle-1 ? next : size);
      for (int j=0; j<m; j++) {
        instructions[k++] = b[j];
      }
    }

    // synchronize icache with generated machine code that was written through dcache
    //
    if (VM.runningVM)
      VM_Memory.sync(VM_Magic.objectAsAddress(instructions), instructions.length << VM.LG_INSTRUCTION_WIDTH);

    // release work buffers
    //
    bundles = null;
    current_bundle = null;
  }

  void addInstruction (INSTRUCTION instr) {
    if (next < current_bundle.length) {
      current_bundle[next++] = instr;
    } else {
      current_bundle = new INSTRUCTION[size];
      bundles.addElement(current_bundle);
      next_bundle++;
      next = 0;
      current_bundle[next++] = instr;
    }
  }

  INSTRUCTION getInstruction (int k) {
    int i = k >> shift;
    int j = k & mask;
    INSTRUCTION[] b = (INSTRUCTION[]) bundles.elementAt(i);
    return b[j];
  }

  void putInstruction(int k, INSTRUCTION instr) {
    int i = k >> shift;
    int j = k & mask;
    INSTRUCTION[] b = (INSTRUCTION[]) bundles.elementAt(i);
    b[j] = instr;
  }

  void setBytecodeMap(int b2m[]) {
    bytecode_2_machine = b2m;
    return;
  }


  private int bytecode_2_machine[];  // See setBytecodeMap/getBytecodeMap

  /* Unfortunately, the number of instructions is not known in advance.
     This class implements a vector of instructions (ints).  It uses a
     vector -- bundles -- whose elements are each int[size] arrays of 
     instructions.  size is assumed to be a power of two.
   */

  private static final int  mask = 0xFF;
  private static final int  size = mask+1;
  private static final int shift = 8;

  private INSTRUCTION[] instructions;
  private Vector        bundles;
  private INSTRUCTION[] current_bundle;
  private int           next;
  private int           next_bundle;

  VM_MachineCode () {
    bundles = new Vector();
    current_bundle = new INSTRUCTION[size];
    bundles.addElement(current_bundle);
    next_bundle++;
  }
}
