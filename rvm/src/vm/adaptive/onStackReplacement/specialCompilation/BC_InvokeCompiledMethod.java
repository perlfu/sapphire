/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
/**
 * invoke a compiled method
 * 
 * @author Feng Qian
 */

public class BC_InvokeCompiledMethod extends OSR_PseudoBytecode {

  private static int bsize = 10;   
  private int cmid;

  // the bc index of referred call site
  private int origIdx;
  
  public BC_InvokeCompiledMethod(int cmethId, int origBCIndex) {
    this.cmid = cmethId;
    this.origIdx = origBCIndex;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_InvokeCompiledMethod);
    int2bytes(codes, 2, cmid);
        int2bytes(codes, 6, origIdx);
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
    VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
    VM_Method callee = cm.getMethod();

    int psize = callee.getParameterWords();
    int schanges = -psize;

    // pop receiver
    if (!callee.isStatic()) {
      schanges --;
    }
    
    VM_TypeReference rtype = callee.getReturnType();
    byte tcode = rtype.getName().parseForTypeCode();
    
    if (tcode == VoidTypeCode) {
      // do nothing
    } else {
      if ( (tcode == LongTypeCode) ||
           (tcode == DoubleTypeCode) ) {
        schanges ++;
      }
      schanges ++;
    }
    
    return schanges;
  }

  public String toString() {
    VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
    return "InvokeCompiledMethod (0x"+Integer.toHexString(cmid)+") "+"@"+origIdx;
  }
}
