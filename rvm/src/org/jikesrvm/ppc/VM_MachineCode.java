/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Magic;
import org.jikesrvm.VM_Memory;
import org.jikesrvm.ArchitectureSpecific;
import java.util.ArrayList;

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
public abstract class VM_MachineCode {

  /**
   * Get the instructions comprising this block of machine code.
   */ 
  public ArchitectureSpecific.VM_CodeArray getInstructions() {
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
  public void finish () {
    if (VM.VerifyAssertions) VM._assert(instructions == null); // finish must only be called once

    /* NOTE: MM_Interface.pickAllocator() depends on the name of this
       class and method to identify code allocation */
    int n = (next_bundle-1)*size+next;
    instructions = ArchitectureSpecific.VM_CodeArray.Factory.create(n, false);
    int k = 0;
    for (int i=0; i<next_bundle; i++){
      int[] b = bundles.get(i);
      int m = (i == next_bundle-1 ? next : size);
      for (int j=0; j<m; j++) {
        instructions.set(k++, b[j]);
      }
    }

    // synchronize icache with generated machine code that was written through dcache
    //
    if (VM.runningVM)
      VM_Memory.sync(VM_Magic.objectAsAddress(instructions), instructions.length() << VM_RegisterConstants.LG_INSTRUCTION_WIDTH);

    // release work buffers
    //
    bundles = null;
    current_bundle = null;
  }

  public void addInstruction (int instr) {
    if (next < current_bundle.length) {
      current_bundle[next++] = instr;
    } else {
      current_bundle = new int[size];
      bundles.add(current_bundle);
      next_bundle++;
      next = 0;
      current_bundle[next++] = instr;
    }
  }

  public int getInstruction (int k) {
    int i = k >> shift;
    int j = k & mask;
    int[] b = bundles.get(i);
    return b[j];
  }

  public void putInstruction(int k, int instr) {
    int i = k >> shift;
    int j = k & mask;
    int[] b = bundles.get(i);
    b[j] = instr;
  }
  
  public void setBytecodeMap(int[] b2m) {
    bytecode_2_machine = b2m;
  }


  private int[] bytecode_2_machine;  // See setBytecodeMap/getBytecodeMap

  /* Unfortunately, the number of instructions is not known in advance.
     This class implements a vector of instructions (ints).  It uses a
     vector -- bundles -- whose elements are each int[size] arrays of 
     instructions.  size is assumed to be a power of two.
   */

  private static final int  mask = 0xFF;
  private static final int  size = mask+1;
  private static final int shift = 8;

  private ArchitectureSpecific.VM_CodeArray instructions;
  private ArrayList<int[]> bundles;
  private int[]         current_bundle;
  private int           next;
  private int           next_bundle;

  public VM_MachineCode () {
    bundles = new ArrayList<int[]>();
    current_bundle = new int[size];
    bundles.add(current_bundle);
    next_bundle++;
  }
}
