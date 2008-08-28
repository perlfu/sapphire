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
package org.mmtk.harness.lang.compiler;

import java.util.ArrayList;
import java.util.List;

public class Temporary {

  private List<Register> freePool = new ArrayList<Register>();

  private int nextIndex = 0;

  /**
   * Get a free temporary from the pool, or create a new one.
   * @return
   */
  public Register acquire() {
    if (freePool.isEmpty()) {
      Register tmp = Register.createTemporary(nextIndex++);
      //System.err.printf("Acquire *new* temporary, %s%n", tmp);
      return tmp;
    } else {
      Register result = freePool.remove(freePool.size()-1);
      //System.err.printf("Acquire temporary, %s%n", result);
      result.setUsed();
      return result;
    }
  }

  public void release(Register...temp) {
    for (Register t : temp) {
      if (t.isTemporary()) {
        t.setFree();
        freePool.add(t);
      }
    }
  }

  /**
   * Allocate a new temporary register pool
   *
   * @param firstIndex
   */
  public Temporary(int firstIndex) {
    nextIndex = firstIndex;
  }

  public int size() {
    return nextIndex;
  }
}
