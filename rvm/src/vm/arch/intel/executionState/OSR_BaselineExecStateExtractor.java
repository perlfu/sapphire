/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;
/**
 * OSR_BaselineExecStateExtractor retrieves the JVM scope descriptor
 * from a suspended thread whose top method was compiled by the
 * baseline compiler.
 *
 * @author Feng Qian
 */

public final class OSR_BaselineExecStateExtractor 
  extends OSR_ExecStateExtractor implements VM_Constants, 
					    OSR_Constants,
					    OPT_PhysicalRegisterConstants {

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
  public OSR_ExecutionState extractState(VM_Thread thread,
				  int osrFPoff,
				  int methFPoff, 
				  int cmid) {

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

    VM_Registers contextRegisters = thread.contextRegisters;
    int[] stack = thread.stack;

    if (VM.VerifyAssertions) {
      int fooCmid     = VM_Magic.getIntAtOffset(stack, 
			      methFPoff + STACKFRAME_METHOD_ID_OFFSET);

      if (VM.TraceOnStackReplacement) {
	VM.sysWriteln("fooCmid = " + fooCmid);
	VM.sysWriteln("   cmid = " + cmid);
      }

      VM._assert(fooCmid == cmid);
    }

    VM_BaselineCompiledMethod fooCM = 
      (VM_BaselineCompiledMethod)VM_CompiledMethods.getCompiledMethod(cmid);

    VM_NormalMethod fooM = (VM_NormalMethod)fooCM.getMethod();

    // get the next bc index 
    INSTRUCTION[] instructions = fooCM.getInstructions();

    VM.disableGC();
    int instr_beg = VM_Magic.objectAsAddress(instructions).toInt();
    int rowIP     = VM_Magic.getIntAtOffset(stack, 
		       osrFPoff + STACKFRAME_RETURN_ADDRESS_OFFSET);
    int ipIndex   = (rowIP - instr_beg) >> LG_INSTRUCTION_WIDTH;
    VM.enableGC();
    
    // CAUTION: IP Offset should point to next instruction
    int bcIndex = fooCM.findBytecodeIndexForInstruction(ipIndex + 1);

    // assertions
    if (VM.VerifyAssertions) {
      if (bcIndex == -1) {      

	VM.sysWriteln("osrFPoff = " + (osrFPoff>>2));
	VM.sysWriteln("instr_beg = " + instr_beg);

	for (int i=(osrFPoff>>2)-10; i<(osrFPoff>>2)+10; i++)
	  VM.sysWriteln("  stack["+i+"] = "+stack[i]);

	VM.sysWriteln("ipIndex : " + ipIndex);
	VM.sysWriteln("bcIndex : " + bcIndex);
      }
      VM._assert(bcIndex != -1);
    }

    // create execution state object
    OSR_ExecutionState state = new OSR_ExecutionState(thread,
						      methFPoff,
						      cmid,
						      bcIndex,
						      osrFPoff);

    /* extract values for local and stack, but first of all
     * we need to get type information for current PC.
     */    
    OSR_BytecodeTraverser typer = new OSR_BytecodeTraverser();
	typer.computeLocalStackTypes(fooM, bcIndex);
    byte[] localTypes = typer.getLocalTypes();
    byte[] stackTypes = typer.getStackTypes();

    if (VM.TraceOnStackReplacement) {
      VM.sysWrite("BC Index : "+bcIndex+"\n");
      VM.sysWrite("Local Types :");
      for (int i=0; i<localTypes.length; i++) {
	VM.sysWrite(" "+(char)localTypes[i]);
      }
      VM.sysWrite("\nStack Types :");
      for (int i=0; i<stackTypes.length; i++) {
	VM.sysWrite(" "+(char)stackTypes[i]);
      }
      VM.sysWrite("\n");
    }
    
    // consult GC reference map again since the type matcher does not complete
    // the flow analysis, it can not distinguish reference or non-reference 
    // type. We should remove non-reference type
    for (int i=0, n=localTypes.length; i<n; i++) {
      // if typer reports a local is reference type, but the GC map says no
      // then set the localType to uninitialized, see VM spec, bytecode verifier
      if (localTypes[i] == ClassTypeCode) {
	if (!fooCM.referenceMaps.isLocalRefType(fooM, ipIndex + 1, i)) {
	  localTypes[i] = VoidTypeCode;
	  if (VM.TraceOnStackReplacement) {
	    VM.sysWriteln("GC maps disagrees with type matcher at "+i+"th local\n");
	  }
	}
      }
    }

    // go through the stack frame and extract values
    // In the variable value list, we keep the order as follows:
    // L0, L1, ..., S0, S1, ....
    
    // adjust local offset and stack offset
    // NOTE: donot call VM_Compiler.getFirstLocalOffset(method)     
    int localOffset = fooCM.getFirstLocalOffset();
    localOffset += methFPoff;

    int stackOffset = fooCM.getEmptyStackOffset();
    stackOffset += (methFPoff- ( 1 << LG_STACKWORD_WIDTH));

    // for locals
    getVariableValue(stack, 
		     localOffset, 
		     localTypes,
		     fooCM,
		     instructions,
		     LOCAL,
		     state);

    // for stacks
    getVariableValue(stack,
		     stackOffset,
		     stackTypes,
		     fooCM,
		     instructions,
		     STACK,
		     state);

    if (VM.TraceOnStackReplacement) {
      state.printState();
    }
		 
    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("BASE executionStateExtractor done ");    
    }
    return state;
  }
  
  /* go over local/stack array, and build OSR_VariableElement. */
  private static void getVariableValue(int[] stack,
				       int   offset,
				       byte[] types,
			            VM_BaselineCompiledMethod compiledMethod,
				       INSTRUCTION[] instructions,
				       int   kind,
				       OSR_ExecutionState state) {
    int size = types.length;
    int vOffset = offset;
    for (int i=0; i<size; i++) {
      if (VM.TraceOnStackReplacement) {
	int content = VM_Magic.getIntAtOffset(stack, vOffset);
	VM.sysWrite("0x"+Integer.toHexString(vOffset)+"    0x"+Integer.toHexString(content)+"\n");
      }
      
      switch (types[i]) {
      case VoidTypeCode:
	vOffset -= 4;
	break;

      case BooleanTypeCode:
      case ByteTypeCode:
      case ShortTypeCode:
      case CharTypeCode:
      case IntTypeCode:
      case FloatTypeCode:{
	int value = VM_Magic.getIntAtOffset(stack, vOffset);
	vOffset -= 4;
          
        int tcode = (types[i] == FloatTypeCode) ? FLOAT : INT;

	state.add(new OSR_VariableElement(kind,
					 i,
					 tcode,
					 value));
	break;
      }
      case LongTypeCode: 
      case DoubleTypeCode: {
	int memoff = 
	  (kind == LOCAL) ? (vOffset-4) : vOffset;
	long value = VM_Magic.getLongAtOffset(stack, memoff);
	
	vOffset -= 8;

        int tcode = (types[i] == LongTypeCode) ? LONG : DOUBLE;

	state.add(new OSR_VariableElement(kind,
					 i,
					 tcode,
					 value));

	i++;
	break;
      }
      case AddressTypeCode: {
	VM.disableGC();
	int rowIP = VM_Magic.getIntAtOffset(stack, vOffset);
	int instr_beg = VM_Magic.objectAsAddress(instructions).toInt();	
	VM.enableGC();

	vOffset -= 4;

	int ipIndex = (rowIP - instr_beg) >> LG_INSTRUCTION_WIDTH;

        if (VM.TraceOnStackReplacement) {
	  VM.sysWrite("baseline addr ip "+ipIndex+" --> ");
	}
        
	int bcIndex = 
	  compiledMethod.findBytecodeIndexForInstruction(ipIndex+1);

        if (VM.TraceOnStackReplacement) {
	  VM.sysWrite(" bc "+ bcIndex+"\n");
        }
        
	state.add(new OSR_VariableElement(kind,
					 i,
					 ADDR,
					 bcIndex));
	break;
      }

      case ClassTypeCode: 
      case ArrayTypeCode: {
	VM.disableGC();
	Object ref = VM_Magic.getObjectAtOffset(stack, vOffset);
	VM.enableGC();

	vOffset -= 4;

	state.add(new OSR_VariableElement(kind,
					 i,
					 REF,
					 ref));
	break;
      }
      default:
	if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
	break;
      } // switch 
    } // for loop
  }  
}
