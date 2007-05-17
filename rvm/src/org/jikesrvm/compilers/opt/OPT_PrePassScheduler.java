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

/**
 * Pre-pass Instruction Scheduling Phase
 *
 * This class is declared as "final" which implies that all its methods
 * are "final" too.
 */
public final class OPT_PrePassScheduler extends OPT_CompilerPhase {

  public boolean shouldPerform(OPT_Options options) {
    return options.SCHEDULE_PREPASS;
  }

  public String getName() {
    return "InstrSched (pre-pass)";
  }

  public boolean printingEnabled(OPT_Options options, boolean before) {
    return !before &&          // old interface only printed afterwards
           options.PRINT_SCHEDULE_PRE;
  }

  /**
   * Perform instruction scheduling for a method.
   * This is an MIR to MIR transformation.
   *
   * @param ir the IR in question
   */
  public void perform(org.jikesrvm.compilers.opt.ir.OPT_IR ir) {
    new OPT_Scheduler(OPT_Scheduler.PREPASS).perform(ir);
  }

}
