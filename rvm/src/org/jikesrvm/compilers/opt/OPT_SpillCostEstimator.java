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
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Register;

/**
 * An object that returns an estimate of the relative cost of spilling a
 * symbolic register.
 */
abstract class OPT_SpillCostEstimator {

  private final HashMap<OPT_Register, Double> map = new HashMap<OPT_Register, Double>();

  /**
   * Return a number that represents an estimate of the relative cost of
   * spilling register r.
   */
  double getCost(OPT_Register r) {
    Double d = map.get(r);
    if (d == null) {
      return 0;
    } else {
      return d;
    }
  }

  /**
   * Calculate the estimated cost for each register.
   */
  abstract void calculate(OPT_IR ir);

  /**
   * Update the cost for a particular register.
   */
  protected void update(OPT_Register r, double delta) {
    double c = getCost(r);
    c += delta;
    map.put(r, c);
  }
}
