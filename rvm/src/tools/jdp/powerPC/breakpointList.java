/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Maintain a list of breakpoints:
 * If used for thread stepping breakpoint, the index is the thread index
 * If used for random breakpoint, there is no specific ordering
 * @author Ton Ngo
 */
import java.util.*;

class breakpointList extends Vector implements jdpConstants {

  /**
   * pointer back to the process that owns this breakpoint list
   */
  OsProcess owner;                  	
  

  public breakpointList(OsProcess proc) {
    super(5,5);
    owner = proc;
  }

  public breakpointList(OsProcess proc, int numThreads) {
    super(numThreads,5);
    owner = proc;
    for (int i=0; i<numThreads; i++) {
      addElement( new breakpoint(0,0,0) );
    }
  }
  
  /**
   * Setting breakpoint for stepping by Java source line
   *
   */
  public boolean setStepLineBreakpoint(int thread) {
    int linenum;
    breakpoint bp = (breakpoint) elementAt(thread);  // get the step breakpoint for this thread

    if (bp==null) {
      System.out.println("setStepLineBreakpoint: null breakpoint for thread " + thread + "?");
      return false;
    }

    // first find out where we are
    int fp = owner.reg.currentFP();
    int addr = owner.reg.hardwareIP();
    int compiledMethodID = owner.bmap.getCompiledMethodID(fp, addr);

    if (compiledMethodID==NATIVE_METHOD_ID) {
      System.out.println("in native procedure, cannot step by Java source line.");
      return false;
    }

    VM_CompilerInfo compInfo = VM_ClassLoader.getCompiledMethod(compiledMethodID).getCompilerInfo();

    // find the current source line 
    try {
      linenum = owner.bmap.findLineNumber(compiledMethodID, addr);
    } catch (Exception e) {
      System.out.println("setStepLineBreakpoint: cannot find current line number");
      return false;
    }

    // look for the next valid code line to set the step breakpoint
    int offset = compInfo.findInstructionForNextLineNumber(linenum);
    if (offset==-1) {
      // end of source code for this method, return to caller
      // System.out.println("setStepLineBreakpoint:  end of method, returning to caller.");
      setLinkBreakpoint(thread);
    } else {
      // set a step breakpoint at the next source line
      bp.next_addr = owner.bmap.instructionAddress(compiledMethodID) + offset;
      bp.next_I = owner.mem.read(bp.next_addr);
      bp.methodID = compiledMethodID;
      Platform.setbp(bp.next_addr);
      // System.out.println("setStepLineBreakpoint: next line breakpoint at " + Integer.toHexString(bp.next_addr));
    }
    return true;

  }

  /**
   * Check if the address matches the breakpoint
   */
  public boolean isAtStepLineBreakpoint(int thread, int address) {
    breakpoint bp = (breakpoint) elementAt(thread);  // get the step breakpoint for this thread

    if (bp==null) {
      System.out.println("isAtStepLineBreakpoint: null breakpoint for thread " + thread + "?");
      return false;
    }
    return (bp.next_addr==address);
  }




  /**
   * Setting breakpoint for stepping by machine instruction 
   * The input is the address of the current instruction
   * This requires two breakpoints, next address and branch target
   * <ul>
   * <li> If over_brl is true and this is a brl instruction,
   *      don't set a breakpoint at the branch target
   * <li> If over_brl is false, we set a breakpoint at the branch target 
   *      to follow any branch 
   * </ul>
   * @param thread the target thread being stepped
   * @param over_brl flag indicating whether to step over or into a branch-link
   * @param skip_prolog flag indicating whether to skip over the prolog code
   * @return
   * @exception
   * @see   
   */
  public void setStepBreakpoint(int thread, boolean over_brl, boolean skip_prolog) {
    breakpoint bp;
    int current_I, address;

    // System.out.println("setStepBreakpoint: thread " + thread + ", list set up for " +
    //		       size() + " threads" ); 
    
    bp = (breakpoint) elementAt(thread);  // get the step breakpoint for this thread

    if (bp==null) {
      System.out.println("setStepBreakpoint: null breakpoint for thread " + thread + "?");
      return;
    }

    address = owner.reg.hardwareIP();    
    bp.next_addr = address + 4;            
    bp.next_I = owner.mem.read(bp.next_addr);
    bp.methodID = owner.bmap.getCompiledMethodID(owner.reg.currentFP(), bp.next_addr);

    // if jumping over method invocation, don't worry about the branch target
    current_I = owner.mem.read(address);
    boolean is_brl = PPC_Disassembler.isBranchAndLink(current_I);
    if (over_brl && is_brl) {      
      // System.out.println("setStepBreakpoint: next " + Integer.toHexString(bp.next_addr));
      Platform.setbp(bp.next_addr);
      return;
    }

    // if it's other types of branch or we are stepping into call on a brl
    // then we also need to set a breakpoint at the branch target
    int branch_addr = owner.mem.branchTarget(current_I, address);

    // is the branch address valid?       
    // do we have breakpoint here already?
    if (branch_addr!=-1 && branch_addr!=bp.next_addr) {     

      if (skip_prolog) {     // should we skip the prolog in the callee?
	bp.branch_offset = owner.bmap.scanPrologSize(branch_addr);
      }

      if (is_brl) { 
	// save the new methodID to be used during the prolog section that
        // will initialize the method ID in the new stack frame
	bp.methodID = owner.bmap.getCompiledMethodIDForInstruction(branch_addr);	
	bp.next_addr = branch_addr + bp.branch_offset;
	bp.next_I = owner.mem.read(bp.next_addr);
      } else {
	bp.branch_addr = branch_addr + bp.branch_offset;
	bp.branch_I = owner.mem.read(bp.branch_addr);
	// System.out.println("setStepBreakpoint: btarget " + 
	// 		   Integer.toHexString(bp.branch_addr));
	Platform.setbp(bp.branch_addr);
      }

    }

    // System.out.println("setStepBreakpoint: next " + Integer.toHexString(bp.next_addr));
    Platform.setbp(bp.next_addr);


  }

  /**
   * Clear the step breakpoint for a thread
   * (but don't clear the method ID since jdp will need to refer
   * to it during the prolog code that initializes the new method ID)
   * @param thread the target thread
   * @return
   * @exception
   * @see
   */
  public void clearStepBreakpoint(int thread) {
    breakpoint bp;
    int current_I, address;

    // System.out.println("clearStepBreakpoint: thread " + thread);

    bp = (breakpoint) elementAt(thread); // get breakpoint object for this thread

    if (bp.next_addr!=-1) {
      // System.out.println("Clearing step " + bp.toString());
      owner.mem.write(bp.next_addr, bp.next_I);
      if (bp.branch_addr!=-1) {
	owner.mem.write(bp.branch_addr, bp.branch_I);
      }
      bp.next_addr = -1;
      bp.branch_addr = -1;
    }
  }

  /**
   * Clear the step breakpoint for a thread but keep the address to restore later
   */
  public void temporaryClearStepBreakpoint(int thread) {
    breakpoint bp = (breakpoint) elementAt(thread); // get breakpoint object for this thread
    if (bp.next_addr!=-1) {
       owner.mem.write(bp.next_addr, bp.next_I);
      if (bp.branch_addr!=-1) {
	owner.mem.write(bp.branch_addr, bp.branch_I);
      }
    }    
  }

  /**
   * Put the step breakpoint back in the program,
   * intended to work in pair with temporaryClearStepBreakpoint()
   */
  public void restoreStepBreakpoint(int thread) {
    breakpoint bp = (breakpoint) elementAt(thread); // get breakpoint object for this thread
    if (bp.next_addr!=-1) {
      bp.next_I = owner.mem.read(bp.next_addr);
      Platform.setbp(bp.next_addr);
      if (bp.branch_addr!=-1) {
	bp.branch_I = owner.mem.read(bp.branch_addr);
	Platform.setbp(bp.branch_addr);
      }
    }    
  }

  /**
   * Find the return address from this stack frame and set a breakpoint there
   * This allows execution to continue through the end of the current method
   * and stop at the return to the caller
   * @param thread the target thread
   * @return
   * @exception
   * @see
   */
  public void setLinkBreakpoint(int thread) {
    breakpoint bp = (breakpoint) elementAt(thread);
    int fp = owner.reg.currentFP();
    fp = owner.mem.read(fp);  // go up one frame to get the address to return from this frame
    bp.next_addr = owner.mem.read(fp + VM_Constants.STACKFRAME_NEXT_INSTRUCTION_OFFSET);
    bp.next_I = owner.mem.read(bp.next_addr);
    bp.branch_addr = -1;
    if (bp.next_addr != 0)
      Platform.setbp(bp.next_addr);
    else 
      bp.next_addr = -1;
  }


  /**
   * Set a breakpoint given the methodID, offset and address
   * @param newbp  a candidate breakpoint object
   * @return
   * @exception
   * @see
   */
  public void setBreakpoint(breakpoint newbp) {
    breakpoint bp;
    int address = newbp.address();
    bp = lookup(address);
    if (bp!=null) 
      return;     // already have a breakpoint there

    newbp.next_I = owner.mem.read(newbp.next_addr);
    addElement(newbp);
    Platform.setbp(address);
    // System.out.println("setBreakpoint: setting " + bp.toString());

  }

  /**
   * Clear a breakpoint at the current IP
   * @return
   * @exception
   * @see
   */
  public void clearBreakpoint() {
    breakpoint bp = lookup(owner.reg.hardwareIP());
    clearBreakpoint(bp);   
  }

  /**
   * Clear a breakpoint in random memory
   * @param address  address of breakpoint to clear
   * @return
   * @exception
   * @see
   */
  public void clearBreakpoint(breakpoint bp) {
    if (bp!=null) {
      // restore the instruction that was there , but only if the trap instr.
      // is still there
      int memdata = owner.mem.read(bp.address());
      if (memdata==BKPT_INSTRUCTION)
        owner.mem.write(bp.next_addr, bp.next_I);    
      removeElement(bp);
    } 
  }

  /**
   * Clear all breakpoints in memory
   * @return
   * @exception
   * @see
   */
  public void clearAllBreakpoint() {
    breakpoint bp;
    for (int i=0; i<size(); i++) {
      bp = (breakpoint) elementAt(i);
      if (bp.next_addr!=-1) {
        int memdata = owner.mem.read(bp.next_addr);
        if (memdata==BKPT_INSTRUCTION)
	  owner.mem.write(bp.next_addr, bp.next_I);
      }      
    }
    removeAllElements();
  }

  /**
   * This should be used each time the debugger regains control of the JVM.
   * GC may have moved some instruction blocks, so the addresses saved in
   * the breakpoint list may be stale.
   * For each breakpoint, check the address using the methodID and offset
   * to see if the instruction block has been moved.  If so, verify that the
   * instruction at this address is the breakpoint trap instruction, then
   * update the address saved in the debugger.
   *
   */
  public boolean relocateBreakpoint() {
    breakpoint bp;
    BootMap bmap = owner.bootmap();
    boolean relocated = false;

    for (int i=0; i<size(); i++) {
      bp = (breakpoint) elementAt(i);
      // System.out.println("relocateBreakpoint: checking " + bp);
      if (bp.methodID == 0 || bp.methodID==NATIVE_METHOD_ID) 
	continue;
      int actualAddress = bmap.instructionAddress(bp.methodID);
      if (bp.next_addr != (actualAddress + bp.next_offset)) {
	System.out.println("relocateBreakpoint: from " + 
			   Integer.toHexString(bp.next_addr) + " to " +
			   Integer.toHexString(actualAddress + bp.next_offset));
	relocated = true;
	bp.next_addr   = actualAddress + bp.next_offset;
	if (bp.branch_addr != -1)
	  bp.branch_addr = actualAddress + bp.branch_offset;
      }	
    }
    
    return relocated;

  }


  /**
   * Some breakpoints may have no methodID saved because they were
   * specified as hex address, so the only way to find the methodID
   * is to scan the dictionary.  If the scan is performed, we save
   * the methodID so that it can be referenced the next time.
   * This method looks for the breakpoint and update the methodID
   * @param address an instruction address
   * @param methodID  the method ID for this instruction
   */
  // public void updateMethodId(int address, int methodID) {
  //   System.out.println("updateMethodId: updating methodID " + methodID +
  // 			  " for address " + Integer.toHexString(address));
  //   breakpoint bp = lookup(address);
  //   if (bp==null) {
  // 	 System.out.println("updateMethodId: none found.");
  // 	 return;
  //   }
  //   if (bp.methodID==0) {
  // 	 bp.methodID = methodID;
  // 	 int startAddress = owner.bmap.instructionAddress(methodID);
  // 	 bp.next_offset = address - startAddress;
  // 	 System.out.println("updateMethodId: updated.");
  //   }
  // }


  /** 
   * Check if any breakpoint is currently set
   */
  public boolean anyBreakpointExist() {
    if (size() != 0)
      return true;
    else
      return false;
  }

  /**
   * Check if a breakpoint is set for this address
   * @param address a random address
   * @return true if a breakpoint is set at this address, false otherwise
   * @exception
   * @see
   */
  public boolean doesBreakpointExist(int address) {
    breakpoint bp = lookup(address);  

    if (bp==null) 
      return false;
    else 
      return true;        
  }


  /**
   * Look up the array of breakpoint, return match.
   * if no match, return null
   * @param address  a random address to look up
   * @return a breakpoint object
   * @exception
   * @see
   */
  public breakpoint lookup(int address) {
    breakpoint bp;
    for (int i=0; i<size(); i++) {
      bp = (breakpoint) elementAt(i);
      if (bp.next_addr == address || bp.branch_addr == address) 
	return bp;
    }
    return null;
  }

  /**
   * Given an address, look up the method ID in the list of breakpoints 
   * to see if the address falls in the start/end range of the instruction block 
   * @param address a random instruction address
   * @return a breakpoint that is in the same instruction block as the address
   */
  public breakpoint lookupByRange(int address) {
    breakpoint bp;
    for (int i=0; i<size(); i++) {
      bp = (breakpoint) elementAt(i);
      if (bp.methodID!=0 && bp.methodID!=NATIVE_METHOD_ID) {
        int startAddress = owner.bmap.instructionAddress(bp.methodID);
	if (owner.bmap.isInstructionAddress(startAddress, address))
	  return bp;
      }

    }
    return null;
  }

  /**
   * Print the list of current breakpoints
   * @param
   * @return
   * @exception
   * @see
   */
  public String list() {
    int count=0;
    breakpoint bp;
    BootMap bmap = owner.bootmap();
    String result = "Current breakpoints:\n";
    for (int i=0; i<size(); i++) {
      bp = (breakpoint) elementAt(i);
      if (bp.next_addr != -1) {
	count++;
	result += ("bp " + i + ": " + bp.toString(bmap) + "\n");
	// bmap.findClassMethodName(methodID, bp.next_addr) + ":" +
	// bmap.findLineNumberAsString(methodID, bp.next_addr));
      }
    }
    
    return result;

  }
  


  
}
