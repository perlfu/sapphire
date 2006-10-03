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
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
class OPT_EmptyIterator implements java.util.Iterator {

  public boolean hasNext () {
    return  false;
  }

  public Object next () {
    throw  new java.util.NoSuchElementException();
  }

  public void remove () {
    throw new OPT_OptimizingCompilerException("EmptyIterator.remove called");
  }

  public static OPT_EmptyIterator INSTANCE = new OPT_EmptyIterator();
}



