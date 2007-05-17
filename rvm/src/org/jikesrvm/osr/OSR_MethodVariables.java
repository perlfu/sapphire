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
package org.jikesrvm.osr;

import java.util.LinkedList;
import org.jikesrvm.classloader.VM_MemberReference;

/**
 * A class to hold variables for a method at one program point.
 */
public final class OSR_MethodVariables {

  /* which method */
  public int methId;

  /* which program point */
  public int bcIndex;

  /* a list of variables */
  public LinkedList<OSR_LocalRegPair> tupleList;

  public OSR_MethodVariables(int mid, int pc, LinkedList<OSR_LocalRegPair> tupleList) {
    this.methId = mid;
    this.bcIndex = pc;
    this.tupleList = tupleList;
  }

  public LinkedList<OSR_LocalRegPair> getTupleList() {
    return tupleList;
  }

  public String toString() {
    StringBuilder buf = new StringBuilder("");

    buf.append(" pc@").append(bcIndex).append(VM_MemberReference.getMemberRef(methId).getName());
    buf.append("\n");
    for (int i = 0, n = tupleList.size(); i < n; i++) {
      buf.append(tupleList.get(i).toString());
      buf.append("\n");
    }
    return buf.toString();
  }
}



