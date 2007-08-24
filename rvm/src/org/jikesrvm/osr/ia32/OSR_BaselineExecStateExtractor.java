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
package org.jikesrvm.osr.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.baseline.VM_BaselineCompiledMethod;
import org.jikesrvm.compilers.baseline.ia32.VM_Compiler;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.compilers.opt.ia32.OPT_PhysicalRegisterConstants;
import org.jikesrvm.ia32.VM_ArchConstants;
import org.jikesrvm.osr.OSR_BytecodeTraverser;
import org.jikesrvm.osr.OSR_Constants;
import org.jikesrvm.osr.OSR_ExecStateExtractor;
import org.jikesrvm.osr.OSR_ExecutionState;
import org.jikesrvm.osr.OSR_VariableElement;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * OSR_BaselineExecStateExtractor retrieves the VM scope descriptor
 * from a suspended thread whose top method was compiled by the
 * baseline compiler.
 */

public abstract class OSR_BaselineExecStateExtractor extends OSR_ExecStateExtractor
    implements VM_Constants, VM_ArchConstants, OSR_Constants, OPT_PhysicalRegisterConstants {

  /**
   * Implements OSR_ExecStateExtractor.extractState.
   *
   * @param thread : the suspended thread, the registers and stack frames are used.
   * @param osrFPoff : the osr method's stack frame offset
   * @param methFPoff : the real method's stack frame offset
   * @param cmid   : the top application method ( system calls are unwounded ).
   *
   * return a OSR_ExecStateExtractor object.
   */
  public OSR_ExecutionState extractState(VM_Thread thread, Offset osrFPoff, Offset methFPoff, int cmid) {

    /* performs architecture and compiler dependent operations here
    *
    * When a thread is hung called from baseline compiled code,
    * the hierarchy of calls on stack looks like follows
    * ( starting from FP in the FP register ):
    *
    *           morph
    *           yield
    *           threadSwitch
    *           threadSwitchFrom[Prologue|Backedge|Epilong]
    *           foo ( real method ).
    *
    * The returned OSR_ExecutionState should have following
    *
    *     current thread
    *     compiled method ID of "foo"
    *     fp of foo's stack frame
    *     bytecode index of foo's next instruction
    *     the list of variable,value of foo at that point
    *     which method (foo)
    */

    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("BASE execStateExtractor starting ...");
    }

    byte[] stack = thread.getStack();

    if (VM.VerifyAssertions) {
      int fooCmid = VM_Magic.getIntAtOffset(stack, methFPoff.plus(STACKFRAME_METHOD_ID_OFFSET));

      if (VM.TraceOnStackReplacement) {
        VM.sysWriteln("fooCmid = " + fooCmid);
        VM.sysWriteln("   cmid = " + cmid);
      }

      VM._assert(fooCmid == cmid);
    }

    VM_BaselineCompiledMethod fooCM = (VM_BaselineCompiledMethod) VM_CompiledMethods.getCompiledMethod(cmid);

    VM_NormalMethod fooM = (VM_NormalMethod) fooCM.getMethod();

    VM.disableGC();
    Address rowIP = VM_Magic.objectAsAddress(stack).loadAddress(osrFPoff.plus(STACKFRAME_RETURN_ADDRESS_OFFSET));
    Offset ipOffset = fooCM.getInstructionOffset(rowIP);
    VM.enableGC();

    // CAUTION: IP Offset should point to next instruction
    int bcIndex = fooCM.findBytecodeIndexForInstruction(ipOffset.plus(INSTRUCTION_WIDTH));

    // assertions
    if (VM.VerifyAssertions) {
      if (bcIndex == -1) {

        VM.sysWriteln("osrFPoff = ", osrFPoff);
        VM.sysWriteln("instr_beg = ", VM_Magic.objectAsAddress(fooCM.getEntryCodeArray()));

        for (int i = (osrFPoff.toInt()) - 10; i < (osrFPoff.toInt()) + 10; i++) {
          VM.sysWriteln("  stack[" + i + "] = " + stack[i]);
        }

        Offset ipIndex = ipOffset.toWord().rsha(LG_INSTRUCTION_WIDTH).toOffset();
        VM.sysWriteln("ipIndex : ", ipIndex);
        VM.sysWriteln("bcIndex : " + bcIndex);
      }
      VM._assert(bcIndex != -1);
    }

    // create execution state object
    OSR_ExecutionState state = new OSR_ExecutionState(thread, methFPoff, cmid, bcIndex, osrFPoff);

    /* extract values for local and stack, but first of all
     * we need to get type information for current PC.
     */
    OSR_BytecodeTraverser typer = new OSR_BytecodeTraverser();
    typer.computeLocalStackTypes(fooM, bcIndex);
    byte[] localTypes = typer.getLocalTypes();
    byte[] stackTypes = typer.getStackTypes();

    if (VM.TraceOnStackReplacement) {
      VM.sysWrite("BC Index : " + bcIndex + "\n");
      VM.sysWrite("Local Types :");
      for (byte localType : localTypes) {
        VM.sysWrite(" " + (char) localType);
      }
      VM.sysWrite("\nStack Types :");
      for (byte stackType : stackTypes) {
        VM.sysWrite(" " + (char) stackType);
      }
      VM.sysWrite("\n");
    }

    // consult GC reference map again since the type matcher does not complete
    // the flow analysis, it can not distinguish reference or non-reference
    // type. We should remove non-reference type
    for (int i = 0, n = localTypes.length; i < n; i++) {
      // if typer reports a local is reference type, but the GC map says no
      // then set the localType to uninitialized, see VM spec, bytecode verifier
      if (localTypes[i] == ClassTypeCode) {
        if (!fooCM.referenceMaps.isLocalRefType(fooM, ipOffset.plus(1 << LG_INSTRUCTION_WIDTH), i)) {
          localTypes[i] = VoidTypeCode;
          if (VM.TraceOnStackReplacement) {
            VM.sysWriteln("GC maps disagrees with type matcher at " + i + "th local\n");
          }
        }
      }
    }

    // go through the stack frame and extract values
    // In the variable value list, we keep the order as follows:
    // L0, L1, ..., S0, S1, ....

    // adjust local offset and stack offset
    // NOTE: do not call VM_Compiler.getFirstLocalOffset(method)
    Offset startLocalOffset = methFPoff.plus(VM_Compiler.locationToOffset(fooCM.getGeneralLocalLocation(0)));

    Offset stackOffset = methFPoff.plus(fooCM.getEmptyStackOffset());

    // for locals
    getVariableValue(stack, startLocalOffset, localTypes, fooCM, LOCAL, state);

    // for stacks
    getVariableValue(stack, stackOffset, stackTypes, fooCM, STACK, state);

    if (VM.TraceOnStackReplacement) {
      state.printState();
    }

    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("BASE executionStateExtractor done ");
    }
    return state;
  }

  /* go over local/stack array, and build OSR_VariableElement. */
  private static void getVariableValue(byte[] stack, Offset offset, byte[] types,
                                       VM_BaselineCompiledMethod compiledMethod, boolean kind, OSR_ExecutionState state) {
    int size = types.length;
    Offset vOffset = offset;
    for (int i = 0; i < size; i++) {
      if (VM.TraceOnStackReplacement) {
        Word content = VM_Magic.getWordAtOffset(stack, vOffset.minus(BYTES_IN_ADDRESS));
        VM.sysWrite("0x", vOffset.minus(BYTES_IN_ADDRESS), "    0x");
        VM.sysWriteln(content);
      }

      switch (types[i]) {
        case VoidTypeCode:
          vOffset = vOffset.minus(BYTES_IN_STACKSLOT);
          break;

        case BooleanTypeCode:
        case ByteTypeCode:
        case ShortTypeCode:
        case CharTypeCode:
        case IntTypeCode:
        case FloatTypeCode: {
          int value = VM_Magic.getIntAtOffset(stack, vOffset.minus(BYTES_IN_INT));
          vOffset = vOffset.minus(BYTES_IN_STACKSLOT);

          byte tcode = (types[i] == FloatTypeCode) ? FLOAT : INT;

          state.add(new OSR_VariableElement(kind, i, tcode, value));
          break;
        }
        case LongTypeCode:
        case DoubleTypeCode: {
          //KV: this code would be nicer if VoidTypeCode would always follow a 64-bit value. Rigth now for LOCAL it follows, for STACK it proceeds
          Offset memoff =
              (kind == LOCAL) ? vOffset.minus(BYTES_IN_DOUBLE) : VM.BuildFor64Addr ? vOffset : vOffset.minus(
                  BYTES_IN_STACKSLOT);
          long value = VM_Magic.getLongAtOffset(stack, memoff);

          byte tcode = (types[i] == LongTypeCode) ? LONG : DOUBLE;

          state.add(new OSR_VariableElement(kind, i, tcode, value));

          if (kind == LOCAL) { //KV:VoidTypeCode is next
            vOffset = vOffset.minus(2 * BYTES_IN_STACKSLOT);
            i++;
          } else {
            vOffset = vOffset.minus(BYTES_IN_STACKSLOT); //KV:VoidTypeCode was already in front
          }

          break;
        }
        case ReturnAddressTypeCode: {
          VM.disableGC();
          Address rowIP = VM_Magic.objectAsAddress(stack).loadAddress(vOffset);
          Offset ipOffset = compiledMethod.getInstructionOffset(rowIP);
          VM.enableGC();

          vOffset = vOffset.minus(BYTES_IN_STACKSLOT);

          if (VM.TraceOnStackReplacement) {
            Offset ipIndex = ipOffset.toWord().rsha(LG_INSTRUCTION_WIDTH).toOffset();
            VM.sysWrite("baseline ret_addr ip ", ipIndex, " --> ");
          }

          int bcIndex = compiledMethod.findBytecodeIndexForInstruction(ipOffset.plus(INSTRUCTION_WIDTH));

          if (VM.TraceOnStackReplacement) {
            VM.sysWrite(" bc " + bcIndex + "\n");
          }

          state.add(new OSR_VariableElement(kind, i, RET_ADDR, bcIndex));
          break;
        }

        case ClassTypeCode:
        case ArrayTypeCode: {
          VM.disableGC();
          Object ref = VM_Magic.getObjectAtOffset(stack, vOffset.minus(BYTES_IN_ADDRESS));
          VM.enableGC();

          vOffset = vOffset.minus(BYTES_IN_STACKSLOT);

          state.add(new OSR_VariableElement(kind, i, REF, ref));
          break;
        }
        case WordTypeCode: {
          Word value = VM_Magic.getWordAtOffset(stack, vOffset.minus(BYTES_IN_ADDRESS));
          vOffset = vOffset.minus(BYTES_IN_STACKSLOT);

          state.add(new OSR_VariableElement(kind, i, WORD, value));
          break;
        }
        default:
          if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
          break;
      } // switch
    } // for loop
  }
}
