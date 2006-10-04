/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

/**
 * Use baseline compiler to build virtual machine boot image.
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_BaselineBootImageCompiler extends VM_BootImageCompiler {

  /** 
   * Initialize boot image compiler.
   * @param args command line arguments to the bootimage compiler
   */
  protected void initCompiler(String[] args) { 
    VM_BaselineCompiler.initOptions();
    // Process arguments specified by the user.
    for (int i = 0, n = args.length; i < n; i++) {
      String arg = args[i];
      if (!VM_Compiler.options.processAsOption("-X:bc:", arg)) {
        VM.sysWrite("VM_BootImageCompiler(baseline): Unrecognized argument "+arg+"; ignoring\n");
      }
    }
  }

  /** 
   * Compile a method with bytecodes.
   * @param method the method to compile
   * @return the compiled method
   */
  protected VM_CompiledMethod compileMethod(VM_NormalMethod method) {
    VM_CompiledMethod cm;
    VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.BASELINE);
    cm = VM_BaselineCompiler.compile(method);

    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    // Must estimate compilation time by using offline ratios.
    // It is tempting to time via System.currentTimeMillis()
    // but 1 millisecond granularity isn't good enough because the 
    // the baseline compiler is just too fast.
    double compileTime = method.getBytecodeLength() / com.ibm.JikesRVM.adaptive.VM_CompilerDNA.getBaselineCompilationRate();
    cm.setCompilationTime(compileTime);
    //-#endif
    return cm;
  }
}
