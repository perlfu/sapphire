/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package com.ibm.JikesRVM.mm.mmtk;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_CommandLineArgs;
import com.ibm.JikesRVM.VM_Processor;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * $Id: Strings.java,v 1.2 2006/06/19 06:08:16 steveb-oss Exp $ 
 *
 * @author Steve Blackburn
 * @author Perry Cheng
 *
 * @version $Revision: 1.2 $
 * @date $Date: 2006/06/19 06:08:16 $
 */
public final class Strings extends org.mmtk.vm.Strings implements Uninterruptible {
  /**
   * Log a message.
   *
   * @param c character array with message starting at index 0
   * @param len number of characters in message
   */
  public final void write(char [] c, int len) {
    VM.sysWrite(c, len);
  }

  /**
   * Log a thread identifier and a message.
   *
   * @param c character array with message starting at index 0
   * @param len number of characters in message
   */
  public final void writeThreadId(char [] c, int len) {
    VM.psysWrite(c, len);
  }

  /**
   * Copies characters from the string into the character array.
   * Thread switching is disabled during this method's execution.
   * <p>
   * <b>TODO:</b> There are special memory management semantics here that
   * someone should document.
   *
   * @param src the source string
   * @param dst the destination array
   * @param dstBegin the start offset in the desination array
   * @param dstEnd the index after the last character in the
   * destination to copy to
   * @return the number of characters copied.
   */
  public final int copyStringToChars(String src, char [] dst,
                                     int dstBegin, int dstEnd)
    throws LogicallyUninterruptiblePragma {
    if (VM.runningVM)
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
    int len = src.length();
    int n = (dstBegin + len <= dstEnd) ? len : (dstEnd - dstBegin);
    for (int i = 0; i < n; i++) 
      Barriers.setArrayNoBarrierStatic(dst, dstBegin + i, src.charAt(i));
    if (VM.runningVM)
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
    return n;
  }
}
