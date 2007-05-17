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

import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_Register;

/**
 * This class defines a LiveInterval node created by Live Variable analysis
 * and used in Linear Scan.
 *
 * @see OPT_LinearScan
 */
public final class OPT_LiveIntervalElement {

  /**
   * register that this live interval is for
   */
  private OPT_Register register;

  /**
   * instruction where the live interval begins
   * (null if alive at basic block entry)
   */
  private OPT_Instruction begin;

  /**
   * instruction where the live interval ends
   * (null if alive at basic block exit)
   */
  private OPT_Instruction end;

  /**
   * The basic block holding this live interval element
   */
  private OPT_BasicBlock bb;

  /**
   * LiveIntervalElements are linked in a singly-linked list; this is the
   * next pointer.
   */
  OPT_LiveIntervalElement next;

  /**
   * Use this constructor when the live interval spans a basic block
   * boundary.
   *
   * @param reg The OPT_Register whose live interval we are representing
   */
  public OPT_LiveIntervalElement(OPT_Register reg) {
    register = reg;
    begin = null;
    end = null;
  }

  /**
   * Use this constructur when the live interval is within a basic block
   *
   * @param reg   the OPT_Register whose live interval we are representing
   * @param begin the definition of the register
   * @param end   the last use of the register
   */
  public OPT_LiveIntervalElement(OPT_Register reg, OPT_Instruction begin, OPT_Instruction end) {
    register = reg;
    this.begin = begin;
    this.end = end;
  }

  public String toString() {
    return "Reg: " + register + "\n     Begin: " + begin + "\n     End:   " + end;
  }

  public int hashCode() {
    return register.hashCode();
  }

  /*
   * Getters and setters for instance fields
   */
  public OPT_Instruction getBegin() { return begin; }

  public void setBegin(OPT_Instruction begin) { this.begin = begin; }

  public OPT_Instruction getEnd() { return end; }

  public OPT_Register getRegister() { return register; }

  public void setRegister(OPT_Register r) { register = r; }

  public OPT_LiveIntervalElement getNext() { return next; }

  public void setNext(OPT_LiveIntervalElement Next) { next = Next; }

  public OPT_BasicBlock getBasicBlock() { return bb; }

  public void setBasicBlock(OPT_BasicBlock bb) { this.bb = bb; }
}
