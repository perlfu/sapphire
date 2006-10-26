/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm;

/**
 * Generate a "trampoline" that jumps to the shared lazy compilation stub.
 * We do this to enable the optimizing compiler to use ptr equality of
 * target instructions to imply logical (source) equality of target methods.
 * This is used to perform guarded inlining using the "method test."
 * Without per-method lazy compilation trampolines, ptr equality of target
 * instructions does not imply source equality, since both targets may in fact
 * be the globally shared lazy compilation stub.
 * 
 * @author Dave Grove
 */
public class VM_LazyCompilationTrampolineGenerator implements VM_BaselineConstants {

  /** Generate a new lazy compilation trampoline. */
  public static VM_CodeArray getTrampoline (){
    VM_Assembler asm = new VM_Assembler(0); 
    // get JTOC into ECX
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, ECX,
                                              VM_Entrypoints.jtocField.getOffset());
    // jmp to real lazy mathod invoker
    asm.emitJMP_RegDisp(ECX, VM_Entrypoints.lazyMethodInvokerMethod.getOffset()); 
    return asm.getMachineCodes();
  }
}
