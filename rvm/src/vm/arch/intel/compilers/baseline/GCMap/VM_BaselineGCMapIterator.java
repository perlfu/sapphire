/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Iterator for stack frame  built by the Baseline compiler
 * An Instance of this class will iterate through a particular 
 * reference map of a method returning the offsets of any refereces
 * that are part of the input parameters, local variables, and 
 * java stack for the stack frame.
 *
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 */
final class VM_BaselineGCMapIterator extends VM_GCMapIterator 
  implements VM_BaselineConstants {
  private static final boolean TRACE_ALL = false;
  private static final boolean TRACE_DL  = false; // dynamic link frames

  //-------------//
  // Constructor //
  //-------------//

  // 
  // Remember the location array for registers. This array needs to be updated
  // with the location of any saved registers.
  // This information is not used by this iterator but must be updated for the
  // other types of iterators (ones for the quick and opt compiler built frames)
  // The locations are kept as addresses within the stack.
  //
  
  VM_BaselineGCMapIterator(int registerLocations[]) {
    this.registerLocations = registerLocations; // (in superclass)
    dynamicLink  = new VM_DynamicLink();
  }

  //-----------//
  // Interface //
  //-----------//

  //
  // Set the iterator to scan the map at the machine instruction offset provided.
  // The iterator is positioned to the beginning of the map
  //
  //   method - identifies the method and class
  //   instruction offset - identifies the map to be scanned.
  //   fp  - identifies a specific occurrance of this method and
  //         allows for processing instance specific information
  //         i.e JSR return address values
  //
  //  NOTE: An iterator may be reused to scan a different method and map.
  //
  void setupIterator(VM_CompiledMethod compiledMethod, int instructionOffset, int fp) {
    currentMethod = compiledMethod.getMethod();
      
    // setup superclass
    //
    framePtr = fp;
      
    // setup stackframe mapping
    //
    maps      = ((VM_BaselineCompilerInfo)compiledMethod.getCompilerInfo()).referenceMaps;
    mapId     = maps.locateGCPoint(instructionOffset, currentMethod);
    mapOffset = 0;
    if (mapId < 0) {
      // lock the jsr lock to serialize jsr processing
      VM_ReferenceMaps.jsrLock.lock();
      maps.setupJSRSubroutineMap(framePtr, mapId, compiledMethod);
    }
    if (VM.TraceStkMaps || TRACE_ALL ) {
      VM.sysWrite("VM_BaselineGCMapIterator setupIterator mapId = ");
      VM.sysWrite(mapId);
      VM.sysWrite(".\n");
    }
      
    // setup dynamic bridge mapping
    //
    bridgeTarget                   = null;
    bridgeParameterTypes           = null;
    bridgeParameterMappingRequired = false;
    bridgeRegistersLocationUpdated = false;
    bridgeParameterIndex           = 0;
    bridgeRegisterIndex            = 0;
    bridgeRegisterLocation         = 0;
    bridgeSpilledParamLocation     = 0;
    
    if (currentMethod.getDeclaringClass().isDynamicBridge()) {
      int               ip                       = VM_Magic.getReturnAddress(fp);
                        fp                       = VM_Magic.getCallerFramePointer(fp);
      int               callingCompiledMethodId  = VM_Magic.getCompiledMethodID(fp);
      VM_CompiledMethod callingCompiledMethod    = VM_CompiledMethods.getCompiledMethod(callingCompiledMethodId);
      VM_CompilerInfo   callingCompilerInfo      = callingCompiledMethod.getCompilerInfo();
      int               callingInstructionOffset = ip - VM_Magic.objectAsAddress(callingCompiledMethod.getInstructions());

      callingCompilerInfo.getDynamicLink(dynamicLink, callingInstructionOffset);
      bridgeTarget                    = dynamicLink.methodRef();
      bridgeParameterTypes            = bridgeTarget.getParameterTypes();
      if (dynamicLink.isInvokedWithImplicitThisParameter()) {
	bridgeParameterInitialIndex     = -1;
	bridgeSpilledParamInitialOffset =  8; // this + return addr
      } else {	
	bridgeParameterInitialIndex     =  0;
	bridgeSpilledParamInitialOffset =  4; // return addr
      }
      bridgeSpilledParamInitialOffset  += (4 * bridgeTarget.getParameterWords());
      if (callingCompilerInfo.getCompilerType() == VM_CompilerInfo.BASELINE) {
	bridgeSpilledParameterMappingRequired = false;
      } else {
	bridgeSpilledParameterMappingRequired = true;
      }
    }
        
    reset();
  }
  
  // Reset iteration to initial state.
  // This allows a map to be scanned multiple times
  //
  void reset() {
    mapOffset = 0;

    if (bridgeTarget != null) {
      bridgeParameterMappingRequired = true;
      bridgeParameterIndex           = bridgeParameterInitialIndex;
      bridgeRegisterIndex            = 0;
      bridgeRegisterLocation         = framePtr + STACKFRAME_FIRST_PARAMETER_OFFSET; // top of frame
      bridgeSpilledParamLocation     = framePtr + bridgeSpilledParamInitialOffset;
    }
  }

  // Get location of next reference.
  // A zero return indicates that no more references exist.
  //
  int getNextReferenceAddress() {
    if (mapId < 0) {
      mapOffset = maps.getNextJSRRef(mapOffset);
    } else {
      mapOffset = maps.getNextRef(mapOffset, mapId);
    }
    if (VM.TraceStkMaps || TRACE_ALL) {
      VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset = ");
      VM.sysWriteHex(mapOffset);
      VM.sysWrite(".\n");
      VM.sysWrite("Reference is ");
      VM.sysWriteHex ( VM_Magic.getMemoryWord ( framePtr+mapOffset ) );
      VM.sysWrite(".\n");
      if (mapId < 0) 
	VM.sysWrite("Offset is a JSR return address ie internal pointer.\n");
    }

    if (mapOffset != 0) {
      if (bridgeParameterMappingRequired)
	// TODO  clean this
	return (framePtr + mapOffset - BRIDGE_FRAME_EXTRA_SIZE );
      else
	return (framePtr + mapOffset );
    } else if (bridgeParameterMappingRequired) {
      if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
	VM.sysWrite("getNextReferenceAddress: bridgeTarget="); VM.sysWrite(bridgeTarget); VM.sysWrite("\n");
      }         

      if (!bridgeRegistersLocationUpdated) {
	// point registerLocations[] to our callers stackframe
	//
	registerLocations[JTOC] = framePtr + JTOC_SAVE_OFFSET;
	registerLocations[T0]   = framePtr + T0_SAVE_OFFSET;
	registerLocations[T1]   = framePtr + T1_SAVE_OFFSET;
	registerLocations[EBX]  = framePtr + EBX_SAVE_OFFSET;
	
	bridgeRegistersLocationUpdated = true;
      }

      // handle implicit "this" parameter, if any
      //
      if (bridgeParameterIndex == -1) {
	bridgeParameterIndex       += 1;
	bridgeRegisterIndex        += 1;
	bridgeRegisterLocation     -= 4;
	bridgeSpilledParamLocation -= 4;
	
	if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
	  VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset = dynamic link GPR this ");
	  VM.sysWriteHex(bridgeRegisterLocation + 4);
	  VM.sysWrite(".\n");
	}

	return bridgeRegisterLocation + 4;
      }
         
      // now the remaining parameters
      //
      while(bridgeParameterIndex < bridgeParameterTypes.length) {
	VM_Type bridgeParameterType = bridgeParameterTypes[bridgeParameterIndex++];
	
	if (bridgeParameterType.isReferenceType()) {
	  bridgeRegisterIndex        += 1;
	  bridgeRegisterLocation     -= 4;
	  bridgeSpilledParamLocation -= 4;
	  
	  if (bridgeRegisterIndex <= NUM_PARAMETER_GPRS) {
	    if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
	      VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset = dynamic link GPR parameter ");
	      VM.sysWriteHex(bridgeRegisterLocation + 4);
	      VM.sysWrite(".\n");
	    }
	    return bridgeRegisterLocation + 4;
	  } else {
	    if (bridgeSpilledParameterMappingRequired) {
	      if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
		VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset = dynamic link spilled parameter ");
		VM.sysWriteHex(bridgeSpilledParamLocation + 4);
		VM.sysWrite(".\n");
	      }
	      return bridgeSpilledParamLocation + 4;
	    } else {
	      break;
	    }
	  }
	} else if (bridgeParameterType.isLongType()) {
	  bridgeRegisterIndex        += 2;
	  bridgeRegisterLocation     -= 8;
	  bridgeSpilledParamLocation -= 8;
	} else if (bridgeParameterType.isDoubleType()) {
	  bridgeSpilledParamLocation -= 8;
	} else if (bridgeParameterType.isFloatType()) {
	  bridgeSpilledParamLocation -= 4;
	} else { 
	  // boolean, byte, char, short, int
	  bridgeRegisterIndex        += 1;
	  bridgeRegisterLocation     -= 4;
	  bridgeSpilledParamLocation -= 4;
	}
      }
    } else {
      // point registerLocations[] to our callers stackframe
      //
      registerLocations[JTOC] = framePtr + JTOC_SAVE_OFFSET;
    }
    
    return 0;
  }

  //
  // Gets the location of the next return address
  // after the current position.
  //  a zero return indicates that no more references exist
  //
  int getNextReturnAddressAddress() {
    if (mapId >= 0) {
      if (VM.TraceStkMaps || TRACE_ALL) {
	VM.sysWrite("VM_BaselineGCMapIterator getNextReturnAddressOffset mapId = ");
	VM.sysWrite(mapId);
	VM.sysWrite(".\n");
      }
      return 0;
    }
    mapOffset = maps.getNextJSRReturnAddr(mapOffset);
    if (VM.TraceStkMaps || TRACE_ALL) {
      VM.sysWrite("VM_BaselineGCMapIterator getNextReturnAddressOffset = ");
      VM.sysWrite(mapOffset);
      VM.sysWrite(".\n");
    }
    return (mapOffset == 0) ? 0 : (framePtr + mapOffset);
  }

  // cleanup pointers - used with method maps to release data structures
  //    early ... they may be in temporary storage ie storage only used
  //    during garbage collection
  //
  void cleanupPointers() {
    maps.cleanupPointers();
    maps = null;
    if (mapId < 0)   
      VM_ReferenceMaps.jsrLock.unlock();
    bridgeTarget         = null;
    bridgeParameterTypes = null;
  }

  int getType() {
    return VM_GCMapIterator.BASELINE;
  }

  // For debugging (used with checkRefMap)
  //
  int getStackDepth() {
    return maps.getStackDepth(mapId);
  }

  // Iterator state for mapping any stackframe.
  //
  private   int              mapOffset; // current offset in current map
  private   int              mapId;     // id of current map out of all maps
  private   VM_ReferenceMaps maps;      // set of maps for this method

  // Additional iterator state for mapping dynamic bridge stackframes.
  //
  private VM_DynamicLink dynamicLink;                    // place to keep info returned by VM_CompilerInfo.getDynamicLink
  private VM_Method      bridgeTarget;                   // method to be invoked via dynamic bridge (null: current frame is not a dynamic bridge)
  private VM_Method      currentMethod;                  // method for the frame
  private VM_Type[]      bridgeParameterTypes;           // parameter types passed by that method
  private boolean        bridgeParameterMappingRequired; // have all bridge parameters been mapped yet?
  private boolean        bridgeSpilledParameterMappingRequired; // do we need to map spilled params (baseline compiler = no, opt = yes)
  private boolean        bridgeRegistersLocationUpdated; // have the register location been updated
  private int            bridgeParameterInitialIndex;    // first parameter to be mapped (-1 == "this")
  private int            bridgeParameterIndex;           // current parameter being mapped (-1 == "this")
  private int            bridgeRegisterIndex;            // gpr register it lives in
  private int            bridgeRegisterLocation;         // memory address at which that register was saved
  private int            bridgeSpilledParamLocation;     // current spilled param location
  private int            bridgeSpilledParamInitialOffset;// starting offset to stack location for param0
}
