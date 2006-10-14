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
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
//-#if RVM_WITH_OPT_COMPILER
import com.ibm.JikesRVM.opt.*;
//-#endif
//-#if RVM_WITH_QUICK_COMPILER
import com.ibm.JikesRVM.quick.*;
//-#endif

import org.vmmagic.pragma.*; 
import org.vmmagic.unboxed.*; 

/**
 * Manage pool of compiled methods. <p>
 * Original extracted from VM_ClassLoader. <p>
 * 
 * @author Bowen Alpern
 * @author Derek Lieber
 * @author Arvin Shepherd
 */
public class VM_CompiledMethods implements VM_SizeConstants {

  /**
   * Create a VM_CompiledMethod appropriate for the given compilerType
   */
  public synchronized static VM_CompiledMethod createCompiledMethod(VM_Method m, int compilerType) {
    int id = ++currentCompiledMethodId;
    if (id == compiledMethods.length) {
      compiledMethods = growArray(compiledMethods, 2 * compiledMethods.length); 
    }
    VM_CompiledMethod cm = null;
    if (compilerType == VM_CompiledMethod.BASELINE) {
      cm = new VM_BaselineCompiledMethod(id, m);
    } else if (compilerType == VM_CompiledMethod.OPT) {
      //-#if RVM_WITH_OPT_COMPILER
      cm = new VM_OptCompiledMethod(id, m);
      //-#endif
      //-#if RVM_WITH_QUICK_COMPILER
    } else if (compilerType == VM_CompiledMethod.QUICK) {
      cm = new VM_QuickCompiledMethod(id, m);
      //-#endif
    } else if (compilerType == VM_CompiledMethod.JNI) {
      cm = new VM_JNICompiledMethod(id, m);
    } else {
      if (VM.VerifyAssertions) VM._assert(false, "Unexpected compiler type!");
    }
    compiledMethods[id] = cm;
    return cm;
  }

  /**
   * Create a VM_CompiledMethod for the synthetic hardware trap frame
   */
  static synchronized VM_CompiledMethod createHardwareTrapCompiledMethod() {
    int id = ++currentCompiledMethodId;
    if (id == compiledMethods.length) {
      compiledMethods = growArray(compiledMethods, 2 * compiledMethods.length); 
    }
    VM_CompiledMethod cm = new VM_HardwareTrapCompiledMethod(id, null);
    compiledMethods[id] = cm;
    return cm;
  }


  // Fetch a previously compiled method.
  //
  public static VM_CompiledMethod getCompiledMethod(int compiledMethodId) throws UninterruptiblePragma {
    VM_Magic.isync();  // see potential update from other procs

    if (VM.VerifyAssertions) {
        if (!(0 < compiledMethodId && compiledMethodId <= currentCompiledMethodId)) {
            VM.sysWriteln(compiledMethodId);
            VM._assert(false);
        }
    }

    return compiledMethods[compiledMethodId];
  }

  // Get number of methods compiled so far.
  //
  public static int numCompiledMethods() throws UninterruptiblePragma {
    return currentCompiledMethodId + 1;
  }

  // Getter method for the debugger, interpreter.
  //
  public static VM_CompiledMethod[] getCompiledMethods() throws UninterruptiblePragma {
    return compiledMethods;
  }

  /**
   * Find the method whose machine code contains the specified instruction.
   * 
   * Assumption: caller has disabled gc (otherwise collector could move
   *                objects without fixing up the raw <code>ip</code> pointer)
   * 
   * Note: this method is highly inefficient. Normally you should use the
   * following instead: 
   * 
   * <code>
   * VM_ClassLoader.getCompiledMethod(VM_Magic.getCompiledMethodID(fp))
   * </code>
   * 
   * @param ip  instruction address
   * 
   * Usage note: <code>ip</code> must point to the instruction *following* the
   * actual instruction whose method is sought. This allows us to properly
   * handle the case where the only address we have to work with is a return
   * address (ie. from a stackframe) or an exception address (ie. from a null
   * pointer dereference, array bounds check, or divide by zero) on a machine
   * architecture with variable length instructions.  In such situations we'd
   * have no idea how far to back up the instruction pointer to point to the
   * "call site" or "exception site".
   * 
   * @return method (<code>null</code> --> not found)
   */
  public static VM_CompiledMethod findMethodForInstruction(Address ip) throws UninterruptiblePragma {
    for (int i = 0, n = numCompiledMethods(); i < n; ++i) {
      VM_CompiledMethod compiledMethod = compiledMethods[i];
      if (compiledMethod == null || !compiledMethod.isCompiled())
        continue; // empty slot

      if (compiledMethod.containsReturnAddress(ip))
	return compiledMethod;
    }

    return null;
  }

  // We keep track of compiled methods that become obsolete because they have
  // been replaced by another version. These are candidates for GC. But, they
  // can only be collected once we are certain that they are no longer being
  // executed. Here, we keep track of them until we know they are no longer
  // in use.
  public static void setCompiledMethodObsolete(VM_CompiledMethod compiledMethod) {
    // Currently, we avoid setting methods of java.lang.Object obsolete.
    // This is because the TIBs for arrays point to the original version
    // and are not updated on recompilation.
    // !!TODO: When replacing a java.lang.Object method, find arrays in JTOC
    //  and update TIB to use newly recompiled method.
    if (compiledMethod.getMethod().getDeclaringClass().isJavaLangObjectType())
      return;

    compiledMethod.setObsolete();
    VM_Magic.sync();
    scanForObsoleteMethods = true;
  }      

  // Snip reference to CompiledMethod so that we can reclaim code space. If
  // the code is currently being executed, stack scanning is responsible for
  // marking it NOT obsolete. Keep such reference until a future GC.
  // NOTE: It's expected that this is processed during GC, after scanning
  //    stacks to determine which methods are currently executing.
  public static void snipObsoleteCompiledMethods() {
    VM_Magic.isync();
    if (!scanForObsoleteMethods) return;
    scanForObsoleteMethods = false;
    VM_Magic.sync();
    
    int max = numCompiledMethods(); 
    for (int i=0; i<numCompiledMethods(); i++) {
      VM_CompiledMethod cm = compiledMethods[i];
      if (cm != null) {
        if (cm.isActiveOnStack()) {
          if (cm.isObsolete()) {
            // can't get it this time; force us to look again next GC
            scanForObsoleteMethods = true;
            VM_Magic.sync();
          }
          cm.clearActiveOnStack();
        } else {
          if (cm.isObsolete()) {
            // obsolete and not active on a thread stack: it's garbage!
            compiledMethods[i] = null;
          }
        }
      }
    }
  }

  /**
   * Report on the space used by compiled code and associated mapping information
   */
  public static void spaceReport() {
    //-#if RVM_WITH_QUICK_COMPILER
    int[] codeCount = new int[VM_CompiledMethod.NUM_COMPILER_TYPES+1];
    int[] codeBytes = new int[VM_CompiledMethod.NUM_COMPILER_TYPES+1];
    int[] mapBytes = new int[VM_CompiledMethod.NUM_COMPILER_TYPES+1];
    //-#else
    int[] codeCount = new int[5];
    int[] codeBytes = new int[5];
    int[] mapBytes = new int[5];
    //-#endif

    VM_Array codeArray = VM_Type.CodeArrayType.asArray();
    for (int i=0; i<numCompiledMethods(); i++) {
      VM_CompiledMethod cm = compiledMethods[i];
      if (cm == null || !cm.isCompiled()) continue;
      int ct = cm.getCompilerType();
      codeCount[ct]++;
      int size = codeArray.getInstanceSize(cm.numberOfInstructions());
      codeBytes[ct] += VM_Memory.alignUp(size, BYTES_IN_ADDRESS);
      mapBytes[ct] += cm.size();
    }
    VM.sysWriteln("Compiled code space report\n");

    VM.sysWriteln("  Baseline Compiler");
    VM.sysWriteln("\tNumber of compiled methods = " + codeCount[VM_CompiledMethod.BASELINE]);
    VM.sysWriteln("\tTotal size of code (bytes) =         " + codeBytes[VM_CompiledMethod.BASELINE]);
    VM.sysWriteln("\tTotal size of mapping data (bytes) = " + mapBytes[VM_CompiledMethod.BASELINE]);

    if (codeCount[VM_CompiledMethod.OPT] > 0) {
      VM.sysWriteln("  Optimizing Compiler");
      VM.sysWriteln("\tNumber of compiled methods = " + codeCount[VM_CompiledMethod.OPT]);
      VM.sysWriteln("\tTotal size of code (bytes) =         " + codeBytes[VM_CompiledMethod.OPT]);
      VM.sysWriteln("\tTotal size of mapping data (bytes) = " +mapBytes[VM_CompiledMethod.OPT]);
    }

    //-#if RVM_WITH_QUICK_COMPILER
    if (codeCount[VM_CompiledMethod.QUICK] > 0) {
      VM.sysWriteln("  Quick Compiler");
      VM.sysWriteln("\tNumber of compiled methods = " + codeCount[VM_CompiledMethod.QUICK]);
      VM.sysWriteln("\tTotal size of code (bytes) =         " + codeBytes[VM_CompiledMethod.QUICK]);
      VM.sysWriteln("\tTotal size of mapping data (bytes) = " +mapBytes[VM_CompiledMethod.QUICK]);
    }
    //-#endif

    if (codeCount[VM_CompiledMethod.JNI] > 0) {
      VM.sysWriteln("  JNI Stub Compiler (Java->C stubs for native methods)");
      VM.sysWriteln("\tNumber of compiled methods = " + codeCount[VM_CompiledMethod.JNI]);
      VM.sysWriteln("\tTotal size of code (bytes) =         " + codeBytes[VM_CompiledMethod.JNI]);
      VM.sysWriteln("\tTotal size of mapping data (bytes) = " + mapBytes[VM_CompiledMethod.JNI]);
    }
 }


  //----------------//
  // implementation //
  //----------------//

  // Java methods that have been compiled into machine code.
  // Note that there may be more than one compiled versions of the same method
  // (ie. at different levels of optimization).
  //
  private static VM_CompiledMethod[] compiledMethods = new VM_CompiledMethod[16000];

  // Index of most recently allocated slot in compiledMethods[].
  //
  private static int currentCompiledMethodId;

  // See usage above
  private static boolean scanForObsoleteMethods = false;

  // Expand an array.
  //
  private static VM_CompiledMethod[] growArray(VM_CompiledMethod[] array, 
                                               int newLength) {
    VM_CompiledMethod[] newarray = MM_Interface.newContiguousCompiledMethodArray(newLength);
    System.arraycopy(array, 0, newarray, 0, array.length);
    VM_Magic.sync();
    return newarray;
  }

}
