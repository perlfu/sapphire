/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.memorymanagers.mminterface;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.ArchitectureSpecific.VM_BaselineGCMapIterator;
import org.jikesrvm.ArchitectureSpecific.VM_JNIGCMapIterator;
import org.jikesrvm.ArchitectureSpecific.VM_OptGCMapIterator;
import org.jikesrvm.VM;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_HardwareTrapGCMapIterator;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.WordArray;

/**
 * Maintains a collection of compiler specific VM_GCMapIterators that are used
 * by collection threads when scanning thread stacks to locate object references
 * in those stacks. Each collector thread has its own VM_GCMapIteratorGroup.
 *
 * The group contains a VM_GCMapIterator for each type of stack frame that
 * may be found while scanning a stack during garbage collection, including
 * frames for baseline compiled methods, OPT compiled methods, and frames
 * for transitions from Java into JNI native code. These iterators are
 * repsonsible for reporting the location of references in the stack or
 * register save areas.
 *
 * @see VM_GCMapIterator
 * @see VM_CompiledMethod
 * @see VM_CollectorThread
 */
public final class VM_GCMapIteratorGroup implements VM_SizeConstants {

  /** current location (memory address) of each gpr register */
  private final WordArray registerLocations;

  /** iterator for baseline compiled frames */
  private final VM_GCMapIterator baselineIterator;

  /** iterator for opt compiled frames */
  private final VM_GCMapIterator optIterator;

  /** iterator for VM_HardwareTrap stackframes */
  private final VM_GCMapIterator hardwareTrapIterator;

  /** iterator for JNI Java -> C  stackframes */
  private final VM_GCMapIterator jniIterator;

  public VM_GCMapIteratorGroup() {
    registerLocations = WordArray.create(ArchitectureSpecific.VM_ArchConstants.NUM_GPRS);

    baselineIterator = new VM_BaselineGCMapIterator(registerLocations);
    if (VM.BuildForOptCompiler) {
      optIterator = new VM_OptGCMapIterator(registerLocations);
    } else {
      optIterator = null;
    }
    jniIterator = new VM_JNIGCMapIterator(registerLocations);
    hardwareTrapIterator = new VM_HardwareTrapGCMapIterator(registerLocations);
  }

  /**
   * Prepare to scan a thread's stack for object references.
   * Called by collector threads when beginning to scan a threads stack.
   * Calls newStackWalk for each of the contained VM_GCMapIterators.
   * <p>
   * Assumption:  the thread is currently suspended, ie. its saved gprs[]
   * contain the thread's full register state.
   * <p>
   * Side effect: registerLocations[] initialized with pointers to the
   * thread's saved gprs[] (in thread.contextRegisters.gprs)
   * <p>
   * @param thread  VM_Thread whose registers and stack are to be scanned
   */
  @Uninterruptible
  public void newStackWalk(VM_Thread thread, Address registerLocation) {
    for (int i = 0; i < ArchitectureSpecific.VM_ArchConstants.NUM_GPRS; ++i) {
      registerLocations.set(i, registerLocation.toWord());
      registerLocation = registerLocation.plus(BYTES_IN_ADDRESS);
    }
    baselineIterator.newStackWalk(thread);
    if (VM.BuildForOptCompiler) {
      optIterator.newStackWalk(thread);
    }
    hardwareTrapIterator.newStackWalk(thread);
    jniIterator.newStackWalk(thread);
  }

  /**
   * Select iterator for scanning for object references in a stackframe.
   * Called by collector threads while scanning a threads stack.
   *
   * @param compiledMethod  VM_CompiledMethod for the method executing
   *                        in the stack frame
   *
   * @return VM_GCMapIterator to use
   */
  @Uninterruptible
  public VM_GCMapIterator selectIterator(VM_CompiledMethod compiledMethod) {
    switch (compiledMethod.getCompilerType()) {
      case VM_CompiledMethod.TRAP:
        return hardwareTrapIterator;
      case VM_CompiledMethod.BASELINE:
        return baselineIterator;
      case VM_CompiledMethod.OPT:
        return optIterator;
      case VM_CompiledMethod.JNI:
        return jniIterator;
    }
    if (VM.VerifyAssertions) {
      VM._assert(false, "VM_GCMapIteratorGroup.selectIterator: Unknown type of compiled method");
    }
    return null;
  }

  /**
   * get the VM_GCMapIterator used for scanning JNI native stack frames.
   *
   * @return jniIterator
   */
  @Uninterruptible
  public VM_GCMapIterator getJniIterator() {
    if (VM.VerifyAssertions) VM._assert(jniIterator != null);
    return jniIterator;
  }
}
