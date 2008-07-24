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
package org.jikesrvm.mm.mmtk;

import org.jikesrvm.VM;
import org.jikesrvm.Services;
import org.jikesrvm.scheduler.Processor;

import org.vmmagic.pragma.*;

@Uninterruptible
public final class Strings extends org.mmtk.vm.Strings {
  /**
   * Log a message.
   *
   * @param c character array with message starting at index 0
   * @param len number of characters in message
   */
  public void write(char [] c, int len) {
    VM.sysWrite(c, len);
  }

  /**
   * Log a thread identifier and a message.
   *
   * @param c character array with message starting at index 0
   * @param len number of characters in message
   */
  public void writeThreadId(char [] c, int len) {
    VM.psysWrite(c, len);
  }

  /**
   * Copies characters from the string into the character array.
   * Thread switching is disabled during this method's execution.
   * <p>
   * <b>TODO:</b> There are special memory management semantics here that
   * someone should document.
   *
   * @param str the source string
   * @param dst the destination array
   * @param dstBegin the start offset in the desination array
   * @param dstEnd the index after the last character in the
   * destination to copy to
   * @return the number of characters copied.
   */
  public int copyStringToChars(String str, char [] dst,
                                     int dstBegin, int dstEnd) {
    if (VM.runningVM) {
      Processor.getCurrentProcessor().disableThreadSwitching("Disabled for MMTk string copy");
    }
    char[] str_backing = java.lang.JikesRVMSupport.getBackingCharArray(str);
    int str_length = java.lang.JikesRVMSupport.getStringLength(str);
    int str_offset = java.lang.JikesRVMSupport.getStringOffset(str);
    int n = (dstBegin + str_length <= dstEnd) ? str_length : (dstEnd - dstBegin);
    for (int i = 0; i < n; i++) {
      Services.setArrayNoBarrier(dst, dstBegin + i, str_backing[str_offset+i]);
    }
    if (VM.runningVM) {
      Processor.getCurrentProcessor().enableThreadSwitching();
    }
    return n;
  }
}
