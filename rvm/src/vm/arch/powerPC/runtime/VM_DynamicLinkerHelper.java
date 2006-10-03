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

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Machine specific helper functions for dynamic linking.
 *
 * @author Bowen Alpern 
 * @author Derek Lieber
 * @date 17 Sep 1999  
 */
class VM_DynamicLinkerHelper implements VM_Constants, Uninterruptible {

  /**
   * Reach up two stack frames into a frame that is compiled
   * with the DynamicBridge register protocol and grap 
   * the receiver object of the invoke (ie the first param).
   * NOTE: assumes that caller has disabled GC.
   */
  static Object getReceiverObject() throws NoInlinePragma {
    // reach into register save area and fetch "this" parameter
    Address callingFrame = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    callingFrame = VM_Magic.getCallerFramePointer(callingFrame);
    callingFrame = VM_Magic.getCallerFramePointer(callingFrame);
    Address location = callingFrame.minus((LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) * BYTES_IN_DOUBLE + 
                                           (LAST_NONVOLATILE_GPR - FIRST_VOLATILE_GPR + 1) * BYTES_IN_ADDRESS); 
    
    return VM_Magic.addressAsObject(location.loadAddress());
  }
}
