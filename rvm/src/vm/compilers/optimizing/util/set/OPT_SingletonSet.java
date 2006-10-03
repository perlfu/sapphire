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
class OPT_SingletonSet extends java.util.AbstractSet {
  Object o;

  OPT_SingletonSet (Object o) {
    this.o = o;
  }

  public boolean contains (Object o) {
    return  this.o == o;
  }

  public int hashCode () {
    return  this.o.hashCode();
  }

  public java.util.Iterator iterator () {
    return  new OPT_SingletonIterator(o);
  }

  public int size () {
    return  1;
  }

  public Object[] toArray() {
    Object[] a = new Object[1];
    a[0] = o;
    return  a;
  }
}
