/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;

/**
 * An object that returns an estimate of the relative cost of spilling a 
 * symbolic register.
 *
 * @author Stephen Fink
 */
abstract class OPT_SpillCostEstimator {

  private java.util.HashMap map = new java.util.HashMap(); 

  /**
   * Return a number that represents an estimate of the relative cost of
   * spilling register r.
   */
  double getCost(OPT_Register r) {
    Double d = (Double)map.get(r);
    if (d == null) return 0;
    else return d.doubleValue();
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
    map.put(r, new Double(c));
  }
}
