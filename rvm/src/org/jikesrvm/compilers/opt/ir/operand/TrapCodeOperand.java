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
package org.jikesrvm.compilers.opt.ir.operand;

import org.jikesrvm.runtime.VM_Runtime;

/**
 * Encode the semantic reason for a trap instruction.
 *
 * @see Operand
 */
public final class TrapCodeOperand extends Operand {

  /**
   * The trap code.
   */
  private final byte trapCode;

  /**
   * Create a trap code operand
   * @param why the trap code
   */
  private TrapCodeOperand(byte why) {
    trapCode = why;
  }

  /**
   * Create a trap code operand for a null pointer check
   * @return the newly created trap code operand
   */
  public static TrapCodeOperand NullPtr() {
    return new TrapCodeOperand((byte) VM_Runtime.TRAP_NULL_POINTER);
  }

  /**
   * Create a trap code operand for an array bounds check
   * @return the newly created trap code operand
   */
  public static TrapCodeOperand ArrayBounds() {
    return new TrapCodeOperand((byte) VM_Runtime.TRAP_ARRAY_BOUNDS);
  }

  /**
   * Create a trap code operand for a divide by zero check
   * @return the newly created trap code operand
   */
  public static TrapCodeOperand DivByZero() {
    return new TrapCodeOperand((byte) VM_Runtime.TRAP_DIVIDE_BY_ZERO);
  }

  /**
   * Create a trap code operand for a stack overflow
   * @return the newly created trap code operand
   */
  public static TrapCodeOperand StackOverflow() {
    return new TrapCodeOperand((byte) VM_Runtime.TRAP_STACK_OVERFLOW);
  }

  /**
   * Create a trap code operand for a check cast
   * @return the newly created trap code operand
   */
  public static TrapCodeOperand CheckCast() {
    return new TrapCodeOperand((byte) VM_Runtime.TRAP_CHECKCAST);
  }

  /**
   * Create a trap code operand for a must implement
   * @return the newly created trap code operand
   */
  public static TrapCodeOperand MustImplement() {
    return new TrapCodeOperand((byte) VM_Runtime.TRAP_MUST_IMPLEMENT);
  }

  /**
   * Create a trap code operand for a must implement
   * @return the newly created trap code operand
   */
  public static TrapCodeOperand StoreCheck() {
    return new TrapCodeOperand((byte) VM_Runtime.TRAP_STORE_CHECK);
  }

  /**
   * Create a trap code operand for a regeneration trap
   * @return the newly created trap code operand
   */
  public static TrapCodeOperand Regenerate() {
    return new TrapCodeOperand((byte) VM_Runtime.TRAP_REGENERATE);
  }

  /**
   * Does the operand represent a null pointer check?
   * @return <code>true</code> if it does and <code>false</code>
   *         if it does not
   */
  public boolean isNullPtr() {
    return trapCode == VM_Runtime.TRAP_NULL_POINTER;
  }

  /**
   * Does the operand represent an array bounds check ?
   * @return <code>true</code> if it does and <code>false</code>
   *         if it does not
   */
  public boolean isArrayBounds() {
    return trapCode == VM_Runtime.TRAP_ARRAY_BOUNDS;
  }

  /**
   * Does the operand represent a divide by zero check?
   * @return <code>true</code> if it does and <code>false</code>
   *         if it does not
   */
  public boolean isDivByZero() {
    return trapCode == VM_Runtime.TRAP_DIVIDE_BY_ZERO;
  }

  /**
   * Does the operand represent a stack overflow check?
   * @return <code>true</code> if it does and <code>false</code>
   *         if it does not
   */
  public boolean isStackOverflow() {
    return trapCode == VM_Runtime.TRAP_STACK_OVERFLOW;
  }

  /**
   * Does the operand represent a check cast?
   * @return <code>true</code> if it does and <code>false</code>
   *         if it does not
   */
  public boolean isCheckCast() {
    return trapCode == VM_Runtime.TRAP_CHECKCAST;
  }

  /**
   * Does the operand represent a must implement trap?
   * @return <code>true</code> if it does and <code>false</code>
   *         if it does not
   */
  public boolean isDoesImplement() {
    return trapCode == VM_Runtime.TRAP_MUST_IMPLEMENT;
  }

  /**
   * Does the operand represent an array store check?
   * @return <code>true</code> if it does and <code>false</code>
   *         if it does not
   */
  public boolean isStoreCheck() {
    return trapCode == VM_Runtime.TRAP_STORE_CHECK;
  }

  /**
   * Does the operand represent a regeneration trap?
   * @return <code>true</code> if it does and <code>false</code>
   *         if it does not
   */
  public boolean isRegenerate() {
    return trapCode == VM_Runtime.TRAP_REGENERATE;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  public Operand copy() {
    return new TrapCodeOperand(trapCode);
  }

  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code>
   *           if they are not.
   */
  public boolean similar(Operand op) {
    return op instanceof TrapCodeOperand && ((TrapCodeOperand) op).trapCode == trapCode;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    switch (trapCode) {
      case VM_Runtime.TRAP_NULL_POINTER:
        return "<NULL PTR>";
      case VM_Runtime.TRAP_ARRAY_BOUNDS:
        return "<ARRAY BOUNDS>";
      case VM_Runtime.TRAP_DIVIDE_BY_ZERO:
        return "<DIV BY ZERO>";
      case VM_Runtime.TRAP_STACK_OVERFLOW:
        return "<STACK OVERFLOW>";
      case VM_Runtime.TRAP_CHECKCAST:
        return "<CLASSCAST>";
      case VM_Runtime.TRAP_MUST_IMPLEMENT:
        return "<MUST IMPLEMENT>";
      case VM_Runtime.TRAP_STORE_CHECK:
        return "<OBJARRAY STORE CHECK>";
      case VM_Runtime.TRAP_REGENERATE:
        return "<REGENERATE>";
      default:
        return "<UNKNOWN TRAP>";
    }
  }

  /**
   *  Return the numeric value representing the trap code; this is
   * used by the assembler (on Intel) when generating code.
   *
   * @return Numeric value representing this trap code
   */
  public int getTrapCode() {
    return trapCode;
  }

}
