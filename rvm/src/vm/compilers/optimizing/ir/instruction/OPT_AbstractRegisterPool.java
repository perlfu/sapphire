/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;

/**
 * @author Julian Dolby
 * @date May 20, 2002
 */
public abstract class OPT_AbstractRegisterPool {

  /* inlined behavior of DoublyLinkedList */
  private OPT_Register start, end;

  /**
   * Return the first symbolic register in this pool.
   */
  public OPT_Register getFirstSymbolicRegister() {
    return start;
  }

  private void registerListappend(OPT_Register reg) {
    if (start == null) {
      start = end = reg;
    } else {
      end.append(reg);
      end = reg;
    }
  }

  private void registerListremove(OPT_Register e) {
    if (e == start) {
      if (e == end) {
        start = end = null;
      } else {
        OPT_Register next = e.next;
        start = next;
        next.prev = null;
      }
    } else if (e == end) {
      OPT_Register prev = e.prev;
      end = prev;
      prev.next = null;
    } else {
      e.remove();
    }
  }
  /* end of inlined behavior */

  /**
   * All registers are assigned unique numbers; currentNum is the counter
   * containing the next available register number.
   */
  protected int currentNum;
    
  private OPT_Register makeNewReg() {
    OPT_Register reg = new OPT_Register(currentNum);
    currentNum++;
    registerListappend(reg);
    return reg;
  }


  /**
   * Release a now unused register.
   * NOTE: It is the CALLERS responsibility to ensure that the register is no
   * longer used!!!!
   * @param r the register to release
   */
  public void release(OPT_RegisterOperand r) {
    OPT_Register reg = r.register;
    if (reg.number == currentNum -1) {
      currentNum--;
      registerListremove(end);
    }
  }


  /**
   * Remove register from register pool.
   */
  public void removeRegister(OPT_Register reg) {
    registerListremove(reg);
  }

  /**
   * Gets a new address register.
   *
   * @return the newly created register object
   */
  public OPT_Register getAddress() {
    OPT_Register reg = makeNewReg();
    reg.setAddress();
    return reg;
  }

  /**
   * Gets a new integer register.
   *
   * @return the newly created register object
   */
  public OPT_Register getInteger() {
    OPT_Register reg = makeNewReg();
    reg.setInteger();
    return reg;
  }

  /**
   * Gets a new float register.
   *
   * @return the newly created register object
   */
  public OPT_Register getFloat() {
    OPT_Register reg = makeNewReg();
    reg.setFloat();
    return reg;
  }

  /**
   * Gets a new double register.
   *
   * @return the newly created register object
   */
  public OPT_Register getDouble() {
    OPT_Register reg;
    reg = makeNewReg();
    reg.setDouble();
    return reg;
  }

  /**
   * Gets a new condition register.
   *
   * @return the newly created register object
   */
  public OPT_Register getCondition() {
    OPT_Register reg = makeNewReg();
    reg.setCondition();
    return reg;
  }

  /**
   * Gets a new long register.
   *
   * @return the newly created register object
   */
  public OPT_Register getLong() {
    OPT_Register reg;
    reg = makeNewReg();
    reg.setLong();
    return reg;
  }

  /**
   * Gets a new validation register.
   *
   * @return the newly created register object
   */
  public OPT_Register getValidation() {
    OPT_Register reg = makeNewReg();
    reg.setValidation();
    return reg;
  }


  /**
   * Get a new register of the same type as the argument register
   * 
   * @param template the register to get the type from
   * @return the newly created register object 
   */
  public OPT_Register getReg(OPT_Register template) {
    switch(template.getType()) {
    case OPT_Register.ADDRESS_TYPE:
      return getAddress();
    case OPT_Register.INTEGER_TYPE:
      return getInteger();
    case OPT_Register.FLOAT_TYPE:
      return getFloat();
    case OPT_Register.DOUBLE_TYPE:
      return getDouble();
    case OPT_Register.CONDITION_TYPE:
      return getCondition();
    case OPT_Register.LONG_TYPE:
      return getLong();
    case OPT_Register.VALIDATION_TYPE: 
      return getValidation();
    }
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Get a new register of the same type as the argument RegisterOperand
   * 
   * @param template the register operand to get the type from
   * @return the newly created register object 
   */
  public OPT_Register getReg(OPT_RegisterOperand template) {
    return getReg(template.register);
  }


  /**
   * Get a new register of the appropriate type to hold values of 'type'
   * 
   * @param type the type of values that the register will hold
   * @return the newly created register object 
   */
  public OPT_Register getReg(VM_TypeReference type) {
    if (type.isLongType())
      return getLong();
    else if (type.isDoubleType())
      return getDouble();
    else if (type.isFloatType())
      return getFloat();
    else if (type == VM_TypeReference.VALIDATION_TYPE)
      return getValidation();
    else if (type.isWordType() || type.isReferenceType())
      return getAddress();
    else
      return getInteger();
  }

  private java.util.HashMap _regPairs = new java.util.HashMap();
  /**
   * MIR: Get the other half of the register pair that is 
   * associated with the argument register.
   * Note: this isn't incredibly general, but all architectures we're currently
   * targeting need at most 2 machine registers to hold Java data values, so
   * for now don't bother implementing a general mechanism.
   * 
   * @param reg a register that may already be part of a register pair
   * @return the register that is the other half of the register pair,
   *         if the pairing doesn't already exist then it is created.
   */
  public OPT_Register getSecondReg(OPT_Register reg) {
    OPT_Register otherHalf = (OPT_Register)_regPairs.get(reg);
    if (otherHalf == null) {
      otherHalf = getReg(reg);
      _regPairs.put(reg, otherHalf);
      if (reg.isLocal()) otherHalf.setLocal();
      if (reg.isSSA()) otherHalf.setSSA();
    }
    return otherHalf;
  }


  /**
   * Make a temporary register operand to hold values of the specified type
   * (a new register is allocated).
   * 
   * @param type the type of values to be held in the temp register
   * @return the new temp
   */
  public OPT_RegisterOperand makeTemp(VM_TypeReference type) {
    return new OPT_RegisterOperand(getReg(type), type);
  }

  /**
   * Make a temporary register operand that is similar to the argument.
   * 
   * @param template the register operand to use as a template.
   * @return the new temp
   */
  public OPT_RegisterOperand makeTemp(OPT_RegisterOperand template) {
    OPT_RegisterOperand temp = 
      new OPT_RegisterOperand(getReg(template), template.type);
    temp.addFlags(template.getFlags());
    return temp;
  }

  /**
   * Make a temporary register operand that can hold the values 
   * implied by the passed operand.
   * 
   * @param op the operand to use as a template.
   * @return the new temp
   */
  public OPT_RegisterOperand makeTemp(OPT_Operand op) {
    OPT_RegisterOperand result;
    if (op.isRegister()) {
      result = makeTemp((OPT_RegisterOperand)op);
    } else {
		result = makeTemp(op.getType());
	 }
    return result;
  }

  /**
   * Make a temporary to hold an address (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempAddress() {
    return new OPT_RegisterOperand(getAddress(), VM_TypeReference.Address);
  }

  /**
   * Make a temporary to hold an address (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempOffset() {
    return new OPT_RegisterOperand(getAddress(), VM_TypeReference.Offset);
  }

  /**
   * Make a temporary to hold an int (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempInt() {
    return new OPT_RegisterOperand(getInteger(), VM_TypeReference.Int);
  }

  /**
   * Make a temporary to hold a boolean (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempBoolean() {
    return new OPT_RegisterOperand(getInteger(), VM_TypeReference.Boolean);
  }

  /**
   * Make a temporary to hold a float (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempFloat() {
    return new OPT_RegisterOperand(getFloat(), VM_TypeReference.Float);
  }

  /**
   * Make a temporary to hold a double (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempDouble() {
    return new OPT_RegisterOperand(getDouble(), VM_TypeReference.Double);
  }

  /**
   * Make a temporary to hold a long (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempLong() {
    return new OPT_RegisterOperand(getLong(), VM_TypeReference.Long);
  }

  /**
   * Make a temporary to hold a condition code (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempCondition() {
    OPT_Register reg = getCondition();
    return new OPT_RegisterOperand(reg, VM_TypeReference.Int);
  }

  /**
   * Make a temporary to hold a guard (validation) (allocating a new register).
   * 
   * @return the newly created temporary
   */
  public OPT_RegisterOperand makeTempValidation() {
    OPT_Register reg = getValidation();
    reg.setValidation();
    return new OPT_RegisterOperand(reg, VM_TypeReference.VALIDATION_TYPE);
  }

}
