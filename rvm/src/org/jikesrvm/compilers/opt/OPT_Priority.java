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

import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_InstructionEnumeration;

/**
 * Instruction priority representation
 * Used by the scheduler to enumerate over instructions
 *
 * @see OPT_Scheduler
 */
abstract class OPT_Priority implements OPT_InstructionEnumeration {

  /**
   * Resets the enumeration to the first instruction in sequence
   */
  public abstract void reset();

  /**
   * Returns true if there are more instructions, false otherwise
   *
   * @return true if there are more instructions, false otherwise
   */
  public abstract boolean hasMoreElements();

  /**
   * Returns the next instruction in sequence
   *
   * @return the next instruction in sequence
   */
  public final OPT_Instruction nextElement() {
    return next();
  }

  /**
   * Returns the next instruction in sequence
   *
   * @return the next instruction in sequence
   */
  public abstract OPT_Instruction next();
}



