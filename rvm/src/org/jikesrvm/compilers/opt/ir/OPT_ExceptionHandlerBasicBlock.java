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
package org.jikesrvm.compilers.opt.ir;

import java.util.Enumeration;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.OPT_ClassLoaderProxy;
import static org.jikesrvm.compilers.opt.OPT_Constants.MAYBE;
import static org.jikesrvm.compilers.opt.OPT_Constants.NO;
import static org.jikesrvm.compilers.opt.OPT_Constants.YES;
import org.jikesrvm.compilers.opt.OPT_LiveSet;

/**
 * A basic block that marks the start of an exception handler.
 * Exception Handler Basic Block; acronym EHBB.
 */
public final class OPT_ExceptionHandlerBasicBlock extends OPT_BasicBlock {

  /**
   * The VM_Type(s) of the exception(s) caught by this block.
   */
  private OPT_TypeOperand[] exceptionTypes;

  /**
   * The liveness information at the beginning of this block
   *  NOTE: If we decide to store this for all blocks, we should move
   *  this field to OPT_BasicBlock (the parent class)
   */
  private OPT_LiveSet liveSet;

  /**
   * Creates a new exception handler basic block at the specified location,
   * which catches the specified type of exception.
   *
   * @param loc   Bytecode index to create basic block at
   * @param position  The inline context for this basic block
   * @param type  The exception type
   * @param cfg   The OPT_ControlFlowGraph that will contain the basic block
   */
  OPT_ExceptionHandlerBasicBlock(int loc, OPT_InlineSequence position, OPT_TypeOperand type, OPT_ControlFlowGraph cfg) {
    super(loc, position, cfg);
    exceptionTypes = new OPT_TypeOperand[1];
    exceptionTypes[0] = type;
    setExceptionHandlerBasicBlock();
    liveSet = null;
  }

  /**
   * Add a new exception type to an extant exception handler block.
   * Do filtering of duplicates internally for efficiency.
   * NOTE: this routine is only intended to be called by
   * {@link OPT_BC2IR}.
   *
   * @param et the exception type to be added
   */
  public void addCaughtException(OPT_TypeOperand et) {
    for (OPT_TypeOperand exceptionType : exceptionTypes) {
      if (exceptionType.similar(et)) return;
    }
    OPT_TypeOperand[] newets = new OPT_TypeOperand[exceptionTypes.length + 1];
    for (int i = 0; i < exceptionTypes.length; i++) {
      newets[i] = exceptionTypes[i];
    }
    newets[exceptionTypes.length] = et;
    exceptionTypes = newets;
  }

  /**
   * Return YES/NO/MAYBE values that answer the question is it possible for
   * this handler block to catch an exception of the type et.
   *
   * @param cand the VM_TypeReference of the exception in question.
   * @return YES, NO, MAYBE
   */
  public byte mayCatchException(VM_TypeReference cand) {
    boolean seenMaybe = false;
    byte t;
    for (OPT_TypeOperand exceptionType : exceptionTypes) {
      t = OPT_ClassLoaderProxy.includesType(exceptionType.getTypeRef(), cand);
      if (t == YES) return YES;
      seenMaybe |= (t == MAYBE);
      t = OPT_ClassLoaderProxy.includesType(cand, exceptionType.getTypeRef());
      if (t == YES) return YES;
      seenMaybe |= (t == MAYBE);
    }
    return seenMaybe ? MAYBE : NO;
  }

  /**
   * Return YES/NO/MAYBE values that answer the question is it guarenteed that
   * this handler block will catch an exception of type <code>cand</code>
   *
   * @param cand  the VM_TypeReference of the exception in question.
   * @return YES, NO, MAYBE
   */
  public byte mustCatchException(VM_TypeReference cand) {
    boolean seenMaybe = false;
    byte t;
    for (OPT_TypeOperand exceptionType : exceptionTypes) {
      t = OPT_ClassLoaderProxy.includesType(exceptionType.getTypeRef(), cand);
      if (t == YES) return YES;
      seenMaybe |= (t == MAYBE);
    }
    if (seenMaybe) {
      return MAYBE;
    } else {
      return NO;
    }
  }

  /**
   * Return an Enumeration of the caught exception types.
   * Mainly intended for creation of exception tables during
   * final assembly. Most other clients shouldn't care about this
   * level of detail.
   */
  public Enumeration<OPT_TypeOperand> getExceptionTypes() {
    return new Enumeration<OPT_TypeOperand>() {
      private int idx = 0;

      public boolean hasMoreElements() {
        return idx != exceptionTypes.length;
      }

      public OPT_TypeOperand nextElement() {
        try {
          return exceptionTypes[idx++];
        } catch (ArrayIndexOutOfBoundsException e) {
          throw new java.util.NoSuchElementException("OPT_ExceptionHandlerBasicBlock.getExceptionTypes");
        }
      }
    };
  }

  /**
   * Get how many table entires this EHBB needs.
   * Really only of interest during final assembly.
   *
   * @see org.jikesrvm.compilers.opt.VM_OptExceptionTable
   *
   * @return the number of table entries for this basic block
   */
  public int getNumberOfExceptionTableEntries() {
    return exceptionTypes.length;
  }

  /**
   * Returns the set of registers live before the first instruction of
   * this basic block
   *
   * @return the set of registers live before the first instruction of
   * this basic block
   */
  public OPT_LiveSet getLiveSet() {
    return liveSet;
  }

  /**
   * Set the set of registers live before the first instruction of
   * this basic block
   *
   * @param   liveSet The set of registers live before the first instruction of
   * this basic block
   */
  public void setLiveSet(OPT_LiveSet liveSet) {
    this.liveSet = liveSet;
  }

  /**
   * Return a string representation of the basic block
   * (augment {@link OPT_BasicBlock#toString} with
   * the exceptions caught by this handler block).
   *
   * @return a string representation of the block
   */
  public String toString() {
    String exmsg = " (catches ";
    for (int i = 0; i < exceptionTypes.length - 1; i++) {
      exmsg = exmsg + exceptionTypes[i].toString() + ", ";
    }
    exmsg = exmsg + exceptionTypes[exceptionTypes.length - 1].toString();
    exmsg = exmsg + " for";
    OPT_BasicBlockEnumeration in = getIn();
    while (in.hasMoreElements()) {
      exmsg = exmsg + " " + in.next().toString();
    }
    exmsg = exmsg + ")";

    return super.toString() + exmsg;
  }
}
