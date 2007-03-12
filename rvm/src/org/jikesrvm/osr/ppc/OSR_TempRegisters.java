/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package org.jikesrvm.osr.ppc;

import org.jikesrvm.ppc.VM_Registers;
import org.jikesrvm.ppc.VM_RegisterConstants;

import org.vmmagic.unboxed.*;

/** 
 * Temporary resiter set.
 * see: VM_Registers
 *
 * @author Feng Qian
 */
public class OSR_TempRegisters implements VM_RegisterConstants {

  Address ip;        // next instruction address
  WordArray gprs;
  double[] fprs;

  /* hold CR, XER, CTR */
  int    cr;
  int    xer;
  Word    ctr;

  /* if a GPR hold a reference to an object, we convert the raw memory
   * address to a reference. When objs[i] is null, the GPR[i] is not
   * holding a reference.
   */
  Object[] objs;

  public OSR_TempRegisters(VM_Registers contextRegisters) {
    gprs = WordArray.create(NUM_GPRS);
    fprs = new double[NUM_FPRS];
    objs = new Object[NUM_GPRS];

    for (int i=0; i<NUM_GPRS; i++) {
      gprs.set(i, contextRegisters.gprs.get(i));
    }
    System.arraycopy(contextRegisters.fprs,
                     0,
                     fprs,
                     0,
                     NUM_FPRS);
    ip = contextRegisters.ip;
  }

  public void dumpContents() {
    System.err.println("OSR_TempRegister: @"+ip.toInt());
    System.err.println("  GPRS: ");
    for (int i=0; i<NUM_GPRS; i++) {
      System.err.println("    ("+i+","+gprs.get(i).toInt()+")");
    }

    System.err.println();
    System.err.println("  OBJS: ");
    for (int i=0; i<NUM_GPRS; i++) {
      if (objs[i] != null) {
        System.err.println("    ("+i+","+objs[i]+")");
      }
    }

    System.err.println();
    System.err.println("  FPRS  ");
    for (int i=0; i<NUM_FPRS; i++) {
      System.err.println("    ("+i+","+fprs[i]+")");
    }
  }
}
