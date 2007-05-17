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

import java.util.HashMap;

/**
 * OPT_DF_Solution.java
 *
 * Represents the solution to a system of Data Flow equations.
 * Namely, a function mapping Objects to OPT_DF_LatticeCells
 */
public class OPT_DF_Solution extends HashMap<Object, OPT_DF_LatticeCell> {
  /** Support for serialization */
  static final long serialVersionUID = -335649266901802532L;

  /**
   * Return a string representation of the dataflow solution
   * @return a string representation of the dataflow solution
   */
  public String toString() {
    String result = "";
    for (OPT_DF_LatticeCell cell : values()) {
      result = result + cell + "\n";
    }
    return result;
  }

  /**
   * Return the lattice cell corresponding to an object
   * @param k the object to look up
   * @return its lattice cell
   */
  public Object lookup(Object k) {
    return get(k);
  }
}



