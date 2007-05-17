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
package org.jikesrvm.compilers.opt;

import java.util.Enumeration;
import java.util.NoSuchElementException;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;

/**
 * An enumeration over live set lists
 */
public class OPT_LiveSetEnumerator implements Enumeration<OPT_RegisterOperand> {

  /**
   *  the current element on this list
   */
  private OPT_LiveSetElement current;

  /**
   * The constructor
   * @param   list  The {@link OPT_LiveSetElement} at the head of the list.
   */
  public OPT_LiveSetEnumerator(OPT_LiveSetElement list) {
    current = list;
  }

  /**
   * Are there any more elements?
   * @return whether there are any more elements?
   */
  public boolean hasMoreElements() {
    return current != null;
  }

  /**
   * Returns the next element, if one exists, otherwise throws an exception
   * @return the next element, if one exists, otherwise throws an exception
   */
  public OPT_RegisterOperand nextElement() {
    if (current != null) {
      OPT_LiveSetElement ret = current;
      current = current.getNext();
      return ret.getRegisterOperand();
    } else {
      throw new NoSuchElementException("OPT_LiveSetEnumerator");
    }
  }
}



