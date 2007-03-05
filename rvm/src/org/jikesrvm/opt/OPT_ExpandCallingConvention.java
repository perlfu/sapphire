/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt;

import org.jikesrvm.ArchitectureSpecific.OPT_CallingConvention;
import org.jikesrvm.opt.ir.OPT_IR;

/**
 *  Phase for expanding the calling convention
 *  @author Michael Hind
 */
public final class OPT_ExpandCallingConvention extends OPT_CompilerPhase {

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  public OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }

  public boolean printingEnabled (OPT_Options options, boolean before) {
    return  options.PRINT_CALLING_CONVENTIONS && !before;
  }

  public String getName() { 
    return "Expand Calling Convention"; 
  }

  public void perform(org.jikesrvm.opt.ir.OPT_IR ir)  {
    OPT_CallingConvention.expandCallingConventions(ir);
  }
}
