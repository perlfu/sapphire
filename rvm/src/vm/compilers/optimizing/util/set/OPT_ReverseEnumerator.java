/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * Reverse the order of an enumeration.
 * 
 * @author Stephen Fink
 */
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.NoSuchElementException;

public final class OPT_ReverseEnumerator implements Enumeration {

  private ArrayList vec = new ArrayList();
  private int index;

  public boolean hasMoreElements () {
    return index > 0;
  }

  public Object nextElement () {
    index--;
    if (index >= 0) {
      return vec.get(index);
    } else {
      throw new NoSuchElementException();
    }
  }

  public OPT_ReverseEnumerator(Enumeration e) {
    while(e.hasMoreElements()) {
      vec.add(e.nextElement());
    }
    index = vec.size();
  }
}



