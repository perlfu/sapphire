/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * Contains IA32-specific helper functions for BURS.
 * 
 * @author Dave Grove
 * @author Stephen Fink
 */
abstract class OPT_BURS_Helpers extends OPT_BURS_MemOp_Helpers {
  
  OPT_BURS_Helpers(OPT_BURS burs) {
    super(burs);
  }

  // condition code state
  private OPT_ConditionOperand cc;
  protected final void pushCOND(OPT_ConditionOperand c) {
    if (VM.VerifyAssertions) VM._assert(cc == null);
    cc = c ;
  }
  protected final OPT_ConditionOperand consumeCOND() {
    OPT_ConditionOperand ans = cc;
    if (VM.VerifyAssertions) {
      VM._assert(cc != null);
      cc = null;
    }
    return ans;
  }

  // can an IV be the scale in a LEA instruction?
  protected final int LEA_SHIFT(OPT_Operand op, int trueCost) {
    return LEA_SHIFT(op, trueCost, INFINITE);
  }
  protected final int LEA_SHIFT(OPT_Operand op, int trueCost, int falseCost) {
    if (op.isIntConstant()) {
      int val = IV(op);
      if (val >=0 && val <= 3) {
        return trueCost;
      }
    }
    return falseCost;
  }
  protected final byte LEA_SHIFT(OPT_Operand op) {
    switch (IV(op)) {
    case 0: return B_S;
    case 1: return W_S;
    case 2: return DW_S;
    case 3: return QW_S;
    default:
      throw new OPT_OptimizingCompilerException("bad val for LEA shift "+op);
    }
  }

  protected final int isFPC_ONE(OPT_Instruction s, int trueCost) {
    return isFPC_ONE(s, trueCost, INFINITE);
  }
  protected final int isFPC_ONE(OPT_Instruction s, int trueCost, int falseCost) {
    OPT_Operand val = Binary.getVal2(s);
    if (val instanceof OPT_FloatConstantOperand) {
      OPT_FloatConstantOperand fc = (OPT_FloatConstantOperand)val;
      return fc.value == 1.0f ? trueCost : falseCost;
    } else {
      OPT_DoubleConstantOperand dc = (OPT_DoubleConstantOperand)val;
      return dc.value == 1.0 ? trueCost : falseCost;
    }
  }
  protected final int isFPC_ZERO(OPT_Instruction s, int trueCost) {
    return isFPC_ZERO(s, trueCost, INFINITE);
  }
  protected final int isFPC_ZERO(OPT_Instruction s, int trueCost, int falseCost) {
    OPT_Operand val = Binary.getVal2(s);
    if (val instanceof OPT_FloatConstantOperand) {
      OPT_FloatConstantOperand fc = (OPT_FloatConstantOperand)val;
      return fc.value == 0.0f ? trueCost : falseCost;
    } else {
      OPT_DoubleConstantOperand dc = (OPT_DoubleConstantOperand)val;
      return dc.value == 0.0 ? trueCost : falseCost;
    }
  }

  protected final OPT_IA32ConditionOperand COND(OPT_ConditionOperand op) {
    return new OPT_IA32ConditionOperand(op);
  }

  // Get particular physical registers
  protected final OPT_Register getEAX () {
    return getIR().regpool.getPhysicalRegisterSet().getEAX();
  }
  protected final OPT_Register getECX () {
    return getIR().regpool.getPhysicalRegisterSet().getECX();
  }
  protected final OPT_Register getEDX () {
    return getIR().regpool.getPhysicalRegisterSet().getEDX();
  }
  protected final OPT_Register getEBX () {
    return getIR().regpool.getPhysicalRegisterSet().getEBX();
  }
  protected final OPT_Register getESP () {
    return getIR().regpool.getPhysicalRegisterSet().getESP();
  }
  protected final OPT_Register getEBP () {
    return getIR().regpool.getPhysicalRegisterSet().getEBP();
  }
  protected final OPT_Register getESI () {
    return getIR().regpool.getPhysicalRegisterSet().getESI();
  }
  protected final OPT_Register getEDI () {
    return getIR().regpool.getPhysicalRegisterSet().getEDI();
  }
  protected final OPT_Register getFPR (int n) {
    return getIR().regpool.getPhysicalRegisterSet().getFPR(n);
  }

  protected final OPT_Operand myFP0() {
    return new OPT_BURSManagedFPROperand(0);
  }
  protected final OPT_Operand myFP1() {
    return new OPT_BURSManagedFPROperand(1);
  }

  protected final OPT_Operand MO_CONV(byte size) {
    int offset = - burs.ir.stackManager.allocateSpaceForConversion();
    return new OPT_StackLocationOperand(true, offset, size);
  }

  protected final void STORE_LONG_FOR_CONV(OPT_Operand op) {
    int offset = - burs.ir.stackManager.allocateSpaceForConversion();
    if (op instanceof OPT_RegisterOperand) {
      OPT_RegisterOperand hval = R(op);
      OPT_RegisterOperand lval = R(regpool.getSecondReg(hval.register));
      EMIT(MIR_Move.create(IA32_MOV, new OPT_StackLocationOperand(true, offset+4, DW), hval));
      EMIT(MIR_Move.create(IA32_MOV, new OPT_StackLocationOperand(true, offset, DW), lval));
    } else {
      OPT_LongConstantOperand val = L(op);
      EMIT(MIR_Move.create(IA32_MOV, new OPT_StackLocationOperand(true, offset+4, DW), I(val.upper32())));
      EMIT(MIR_Move.create(IA32_MOV, new OPT_StackLocationOperand(true, offset, DW), I(val.lower32())));
    }
  }      

  // emit code to load 32 bits form a given jtoc offset
  private OPT_MemoryOperand loadFromJTOC(int offset) {
    OPT_LocationOperand loc = new OPT_LocationOperand(offset);
    OPT_Operand guard = TG();
    if (burs.ir.options.FIXED_JTOC) {
      return OPT_MemoryOperand.D(VM_Magic.getTocPointer().add(offset).toInt(),
                                 (byte)4, loc, guard);
    } else {
      OPT_Operand jtoc = 
        OPT_MemoryOperand.BD(R(regpool.getPhysicalRegisterSet().getPR()),
                             VM_Entrypoints.jtocField.getOffset(), 
                             (byte)4, null, TG());
      OPT_RegisterOperand regOp = regpool.makeTempInt();
      EMIT(MIR_Move.create(IA32_MOV, regOp, jtoc));
      return OPT_MemoryOperand.BD(regOp.copyD2U(), offset, (byte)4, loc, guard);
    }
  }

  /*
   * IA32-specific emit rules that are complex 
   * enough that we didn't want to write them in the LIR2MIR.rules file.
   * However, all expansions in this file are called during BURS and
   * thus are constrained to generate nonbranching code (ie they can't
   * create new basic blocks and/or do branching).
   *
   */

  /**
   * Emit code to get a caught exception object into a register
   * 
   * @param s the instruction to expand
   */
  protected final void GET_EXCEPTION_OBJECT(OPT_Instruction s) {
    int offset = - burs.ir.stackManager.allocateSpaceForCaughtException();
    OPT_StackLocationOperand sl = new OPT_StackLocationOperand(true, offset, DW);
    EMIT(MIR_Move.mutate(s, IA32_MOV, Nullary.getResult(s), sl));
  }


  /**
   * Emit code to move a value in a register to the stack location
   * where a caught exception object is expected to be.
   * 
   * @param s the instruction to expand
   */
  protected final void SET_EXCEPTION_OBJECT(OPT_Instruction s) {
    int offset = - burs.ir.stackManager. allocateSpaceForCaughtException();
    OPT_StackLocationOperand sl = new OPT_StackLocationOperand(true, offset, DW);
    OPT_RegisterOperand obj = (OPT_RegisterOperand)CacheOp.getRef(s);
    EMIT(MIR_Move.mutate(s, IA32_MOV, sl, obj));
  }


  /**
   * Expansion of INT_2LONG
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param value the second operand
   */
  protected final void INT_2LONG(OPT_Instruction s,
                       OPT_RegisterOperand result,
                       OPT_Operand value) {
    OPT_Register hr = result.register;
    OPT_Register lr = regpool.getSecondReg(hr);
    EMIT(MIR_Move.create(IA32_MOV, R(lr), value));
    EMIT(MIR_Move.create(IA32_MOV, R(hr), R(lr)));
    EMIT(MIR_BinaryAcc.create(IA32_SAR, R(hr), I(31)));
  }

  /**
   * Expansion of FLOAT_2INT and DOUBLE_2INT, using the FIST instruction.
   * This expansion does some boolean logic and conditional moves in order
   * to avoid changing the floating-point rounding mode or inserting
   * branches.  Other expansions are possible, and may be better?
   * 
   * @param s the instruction to expand
   * @param result the result operand
   * @param value the second operand
   */
  protected final void FPR_2INT(OPT_Instruction s,
                                OPT_RegisterOperand result,
                                OPT_Operand value) {
    OPT_MemoryOperand M;

    // Step 1: Get value to be converted into myFP0
    //         and in 'strict' IEEE mode.
    if (value instanceof OPT_MemoryOperand) {
      // value is in memory, all we have to do is load it
      EMIT(MIR_Move.create(IA32_FLD, myFP0(), value));
    } else {
      // sigh.  value is an FP register. Unfortunately,
      // SPECjbb requires some 'strict' FP semantics.  Naturally, we don't
      // normally implement strict semantics, but we try to slide by in
      // order to pass the benchmark.  
      // In order to pass SPECjbb, it turns out we need to enforce 'strict'
      // semantics before doing a particular f2int conversion.  To do this
      // we must have a store/load sequence to cause IEEE rounding.
      if (value instanceof OPT_BURSManagedFPROperand) {
        if (VM.VerifyAssertions) VM._assert(value.similar(myFP0()));
        EMIT(MIR_Move.create(IA32_FSTP, MO_CONV(DW), value));
        EMIT(MIR_Move.create(IA32_FLD, myFP0(), MO_CONV(DW)));
      } else {
        EMIT(MIR_Move.create(IA32_FMOV, MO_CONV(DW), value));
        EMIT(MIR_Move.create(IA32_FLD, myFP0(), MO_CONV(DW)));
      }
    }

    // FP Stack: myFP0 = value 
    EMIT(MIR_Move.create(IA32_FIST, MO_CONV(DW),  myFP0()));
    // MO_CONV now holds myFP0 converted to an integer (round-toward nearest)
    // FP Stack: myFP0 == value

    // isPositive == 1 iff 0.0 < value
    // isNegative == 1 iff 0.0 > value
    OPT_Register one        = regpool.getInteger();
    OPT_Register isPositive = regpool.getInteger();
    OPT_Register isNegative = regpool.getInteger();
    EMIT(MIR_Move.create(IA32_MOV, R(one), I(1)));
    EMIT(MIR_Move.create(IA32_MOV, R(isPositive), I(0)));
    EMIT(MIR_Move.create(IA32_MOV, R(isNegative), I(0)));
    EMIT(MIR_Nullary.create(IA32_FLDZ, myFP0()));
    // FP Stack: myFP0 = 0.0; myFP1 = value 
    EMIT(MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1()));
    // FP Stack: myFP0 = value
    EMIT(MIR_CondMove.create(IA32_CMOV, R(isPositive), R(one),
                                    OPT_IA32ConditionOperand.LLT()));
    EMIT(MIR_CondMove.create(IA32_CMOV, R(isNegative), R(one),
                                    OPT_IA32ConditionOperand.LGT()));

    EMIT(MIR_Move.create(IA32_FILD, myFP0(), MO_CONV(DW)));
    // FP Stack: myFP0 = round(value), myFP1 = value

    // addee      = 1 iff round(x) < x
    // subtractee = 1 iff round(x) > x
    OPT_Register addee      = regpool.getInteger();
    OPT_Register subtractee = regpool.getInteger();
    EMIT(MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1()));
    // FP Stack: myFP0 = value
    EMIT(MIR_Move.create(IA32_MOV, R(addee) , I(0)));
    EMIT(MIR_Move.create(IA32_MOV, R(subtractee) , I(0)));
    EMIT(MIR_CondMove.create(IA32_CMOV, R(addee), R(one),
                                    OPT_IA32ConditionOperand.LLT()));
    EMIT(MIR_CondMove.create(IA32_CMOV, R(subtractee), R(one),
                                    OPT_IA32ConditionOperand.LGT()));
    
    // Now a little tricky part.
    // We will add 1 iff isNegative and x > round(x)
    // We will subtract 1 iff isPositive and x < round(x)
    EMIT(MIR_BinaryAcc.create(IA32_AND, R(addee), R(isNegative)));
    EMIT(MIR_BinaryAcc.create(IA32_AND, R(subtractee), R(isPositive)));
    EMIT(MIR_Move.create(IA32_MOV, result.copy(), MO_CONV(DW)));
    EMIT(MIR_BinaryAcc.create(IA32_ADD, result.copy(), R(addee)));
    EMIT(MIR_BinaryAcc.create(IA32_SUB, result.copy(), R(subtractee)));

    // Acquire the JTOC in a register
    OPT_Register jtoc = null;
    if (!burs.ir.options.FIXED_JTOC) {
      jtoc = regpool.getInteger();
      EMIT(MIR_Move.create(IA32_MOV, 
                                  R(jtoc), 
                                  MO_BD(R(regpool.getPhysicalRegisterSet().getPR()),
                                        VM_Entrypoints.jtocField.getOffset(), DW, null, null)));
    }

    // Compare myFP0 with (double)Integer.MAX_VALUE
    if (burs.ir.options.FIXED_JTOC) {
      M = OPT_MemoryOperand.D(VM_Magic.getTocPointer().add(VM_Entrypoints.maxintField.getOffset()).toInt(),
                              QW, null, null);
    } else {
      M = OPT_MemoryOperand.BD(R(jtoc), VM_Entrypoints.maxintField.getOffset(), QW, null, null);
    }
    EMIT(MIR_Move.create(IA32_FLD, myFP0(), M));
    // FP Stack: myFP0 = (double)Integer.MAX_VALUE; myFP1 = value
    EMIT(MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1()));
    // FP Stack: myFP0 = value
    // If MAX_VALUE < value, then result := MAX_INT
    OPT_Register maxInt = regpool.getInteger();
    EMIT(MIR_Move.create(IA32_MOV, R(maxInt), I(Integer.MAX_VALUE)));
    EMIT(MIR_CondMove.create(IA32_CMOV, result.copy(), R(maxInt), 
                                    OPT_IA32ConditionOperand.LLT()));
    
    // Compare myFP0 with (double)Integer.MIN_VALUE
    if (burs.ir.options.FIXED_JTOC) {
      M = OPT_MemoryOperand.D(VM_Magic.getTocPointer().add(VM_Entrypoints.minintField.getOffset()).toInt(),
                              QW, null, null);
    } else {
      M = OPT_MemoryOperand.BD(R(jtoc), VM_Entrypoints.minintField.getOffset(), QW, null, null);
    }
    EMIT(MIR_Move.create(IA32_FLD, myFP0(), M));
    // FP Stack: myFP0 = (double)Integer.MIN_VALUE; myFP1 = value
    EMIT(MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1()));
    // FP Stack: myFP0 = value
    // If MIN_VALUE > value, then result := MIN_INT
    OPT_Register minInt = regpool.getInteger();
    EMIT(MIR_Move.create(IA32_MOV, R(minInt), I(Integer.MIN_VALUE)));
    EMIT(MIR_CondMove.create(IA32_CMOV, result.copy(), R(minInt), 
                                    OPT_IA32ConditionOperand.LGT()));
    
    // Set condition flags: set PE iff myFP0 is a NaN
    EMIT(MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP0()));
    // FP Stack: back to original level (all BURS managed slots freed)
    // If FP0 was classified as a NaN, then result := 0
    OPT_Register zero = regpool.getInteger();
    EMIT(MIR_Move.create(IA32_MOV, R(zero), I(0)));
    EMIT(MIR_CondMove.create(IA32_CMOV, result.copy(), R(zero),
                                    OPT_IA32ConditionOperand.PE()));
    
  }

  /**
   * Emit code to move 64 bits from FPRs to GPRs
   */
  protected final void FPR2GPR_64(OPT_Instruction s) {
    int offset = - burs.ir.stackManager.allocateSpaceForConversion();
    OPT_StackLocationOperand sl = new OPT_StackLocationOperand(true, offset, QW);
    OPT_StackLocationOperand sl1 = new OPT_StackLocationOperand(true, offset+4, DW);
    OPT_StackLocationOperand sl2 = new OPT_StackLocationOperand(true, offset, DW);
    EMIT(MIR_Move.create(IA32_FMOV, sl, Unary.getVal(s)));
    OPT_RegisterOperand i1 = Unary.getResult(s);
    OPT_RegisterOperand i2 = R(regpool.getSecondReg(i1.register));
    EMIT(MIR_Move.create(IA32_MOV, i1, sl1));
    EMIT(MIR_Move.mutate(s, IA32_MOV, i2, sl2));
  }


  /**
   * Emit code to move 64 bits from GPRs to FPRs
   */
  protected final void GPR2FPR_64(OPT_Instruction s) {
    int offset = - burs.ir.stackManager.allocateSpaceForConversion();
    OPT_StackLocationOperand sl = new OPT_StackLocationOperand(true, offset, QW);
    OPT_StackLocationOperand sl1 = new OPT_StackLocationOperand(true, offset+4, DW);
    OPT_StackLocationOperand sl2 = new OPT_StackLocationOperand(true, offset, DW);
    OPT_Operand i1, i2;
    OPT_Operand val = Unary.getVal(s);
    if (val instanceof OPT_RegisterOperand) {
      OPT_RegisterOperand rval = (OPT_RegisterOperand)val;
      i1 = val;
      i2 = R(regpool.getSecondReg(rval.register));
    } else {
      OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)val;
      i1 = I(rhs.upper32());
      i2 = I(rhs.lower32());
    }      
    EMIT(MIR_Move.create(IA32_MOV, sl1, i1));
    EMIT(MIR_Move.create(IA32_MOV, sl2, i2));
    EMIT(MIR_Move.mutate(s, IA32_FMOV, Unary.getResult(s), sl));
  }

  /**
   * Expansion of ROUND_TO_ZERO.
   * 
   * @param s the instruction to expand
   */
  protected final void ROUND_TO_ZERO(OPT_Instruction s) {
    // load the JTOC into a register
    OPT_RegisterOperand PR = R(regpool.getPhysicalRegisterSet().
                               getPR());
    OPT_Operand jtoc = OPT_MemoryOperand.BD(PR, VM_Entrypoints.jtocField.getOffset(), 
                                            DW, null, null);
    OPT_RegisterOperand regOp = regpool.makeTempInt();
    EMIT(MIR_Move.create(IA32_MOV, regOp, jtoc));

    // Store the FPU Control Word to a JTOC slot
    OPT_MemoryOperand M = OPT_MemoryOperand.BD
      (regOp.copyRO(), VM_Entrypoints.FPUControlWordField.getOffset(), W, null, null);
    EMIT(MIR_UnaryNoRes.create(IA32_FNSTCW, M));
    // Set the bits in the status word that control round to zero.
    // Note that we use a 32-bit and, even though we only care about the
    // low-order 16 bits
    EMIT(MIR_BinaryAcc.create(IA32_OR, M.copy(), I(0x00000c00)));
    // Now store the result back into the FPU Control Word
    EMIT(MIR_Nullary.mutate(s,IA32_FLDCW, M.copy()));
    return;
  }


  /**
   * Expansion of INT_DIV and INT_REM
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param val1 the first operand
   * @param val2 the second operand
   * @param isDiv true for div, false for rem
   */
  protected final void INT_DIVIDES(OPT_Instruction s,
                                   OPT_RegisterOperand result,
                                   OPT_Operand val1,
                                   OPT_Operand val2,
                                   boolean isDiv) {
    EMIT(MIR_Move.create(IA32_MOV, R(getEAX()), val1));
    EMIT(MIR_ConvertDW2QW.create(IA32_CDQ, R(getEDX()), R(getEAX())));
    if (val2 instanceof OPT_IntConstantOperand) {
      OPT_RegisterOperand temp = regpool.makeTempInt();
      EMIT(MIR_Move.create(IA32_MOV, temp, val2));
      val2 = temp;
    }
    EMIT(MIR_Divide.mutate(s, IA32_IDIV, R(getEDX()), R(getEAX()), 
                                  val2, GuardedBinary.getGuard(s)));
    if (isDiv) {
      EMIT(MIR_Move.create(IA32_MOV, result.copyD2D(), R(getEAX())));
    } else {
      EMIT(MIR_Move.create(IA32_MOV, result.copyD2D(), R(getEDX())));
    }      
  }


  /**
   * Expansion of LONG_ADD_ACC
   *
   * @param s the instruction to expand
   * @param result the result/first operand
   * @param value the second operand
   */
  protected final void LONG_ADD(OPT_Instruction s,
                                OPT_RegisterOperand result,
                                OPT_Operand value) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
    if (value instanceof OPT_RegisterOperand) {
      OPT_Register rhsReg = ((OPT_RegisterOperand)value).register;
      OPT_Register lowrhsReg = regpool.getSecondReg(rhsReg);
      EMIT(MIR_BinaryAcc.create(IA32_ADD, R(lowlhsReg), R(lowrhsReg)));
      EMIT(MIR_BinaryAcc.mutate(s, IA32_ADC, R(lhsReg), R(rhsReg)));
    } else {
      OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)value;
      int low = rhs.lower32();
      int high = rhs.upper32();
      if (low == 0) {
        EMIT(MIR_BinaryAcc.mutate(s, IA32_ADD, R(lhsReg), I(high)));
      } else {
        EMIT(MIR_BinaryAcc.create(IA32_ADD, R(lowlhsReg), I(low)));
        EMIT(MIR_BinaryAcc.mutate(s, IA32_ADC, R(lhsReg), I(high)));
      }
    }
  }


  /**
   * Expansion of LONG_SUB_ACC
   *
   * @param s the instruction to expand
   * @param result the result/first operand
   * @param value the second operand
   */
  protected final void LONG_SUB(OPT_Instruction s,
                                OPT_RegisterOperand result,
                                OPT_Operand value) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
    if (value instanceof OPT_RegisterOperand) {
      OPT_Register rhsReg = ((OPT_RegisterOperand)value).register;
      OPT_Register lowrhsReg = regpool.getSecondReg(rhsReg);
      EMIT(MIR_BinaryAcc.create(IA32_SUB, R(lowlhsReg), R(lowrhsReg)));
      EMIT(MIR_BinaryAcc.mutate(s, IA32_SBB, R(lhsReg), R(rhsReg)));
    } else {
      OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)value;
      int low = rhs.lower32();
      int high = rhs.upper32();
      if (low == 0) {
        EMIT(MIR_BinaryAcc.mutate(s, IA32_SUB, R(lhsReg), I(high)));
      } else {
        EMIT(MIR_BinaryAcc.create(IA32_SUB, R(lowlhsReg), I(low)));
        EMIT(MIR_BinaryAcc.mutate(s, IA32_SBB, R(lhsReg), I(high)));
      }
    }
  }

  /**
   * Expansion of RDTSC (called GET_TIME_BASE for consistency with PPC)
   *
   * @param s the instruction to expand
   * @param result the result/first operand
   */
  protected final void GET_TIME_BASE(OPT_Instruction s,
                                     OPT_RegisterOperand result) {
    OPT_Register highReg = result.register;
    OPT_Register lowReg = regpool.getSecondReg(highReg);
    EMIT(MIR_RDTSC.create(IA32_RDTSC, R(getEAX()),R(getEDX())));
    EMIT(MIR_Move.create(IA32_MOV, R(lowReg), R(getEAX())));
    EMIT(MIR_Move.create(IA32_MOV, R(highReg), R(getEDX())));
  }

  /**
   * Expansion of LONG_MUL_ACC
   *
   * @param s the instruction to expand
   * @param result the result/first operand
   * @param value the second operand
   */
  protected final void LONG_MUL(OPT_Instruction s,
                                OPT_RegisterOperand result,
                                OPT_Operand value) {
    // In general, (a,b) * (c,d) = (l(a imul d)+l(b imul c)+u(b mul d), l(b mul d))
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
    if (value instanceof OPT_RegisterOperand) {
      OPT_Register rhsReg = ((OPT_RegisterOperand)value).register;
      OPT_Register lowrhsReg = regpool.getSecondReg(rhsReg);
      OPT_Register tmp = regpool.getInteger();
      EMIT(MIR_BinaryAcc.create(IA32_IMUL2, R(lhsReg), R(lowrhsReg)));
      EMIT(MIR_Move.create(IA32_MOV, R(tmp), R(rhsReg)));
      EMIT(MIR_BinaryAcc.create(IA32_IMUL2, R(tmp), R(lowlhsReg)));
      EMIT(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(tmp)));
      EMIT(MIR_Move.create(IA32_MOV, R(getEAX()), R(lowlhsReg)));
      EMIT(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowrhsReg)));
      EMIT(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
      EMIT(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(getEDX())));
    } else {
      OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)value;
      int low = rhs.lower32();
      int high = rhs.upper32();

      // We only have to handle those cases that OPT_Simplifier wouldn't get.  
      // OPT_Simplifier catches 
      // high   low
      //    0     0  (0L)
      //    0     1  (1L)
      //   -1    -1 (-1L)
      // So, the possible cases we need to handle here:
      //   -1     0 
      //   -1     1
      //   -1     *
      //    0    -1
      //    0     *
      //    1    -1
      //    1     0 
      //    1     1
      //    1     *
      //    *    -1
      //    *     0
      //    *     1
      //    *     *
      // (where * is something other than -1,0,1)
      if (high == -1) {
        if (low == 0) {
          // -1, 0
          // CLAIM: (x,y) * (-1,0) = (-y,0)
          EMIT(MIR_Move.create(IA32_MOV, R(lhsReg), R(lowlhsReg)));
          EMIT(MIR_UnaryAcc.create(IA32_NEG, R(lhsReg)));
          EMIT(MIR_Move.create(IA32_MOV, R(lowlhsReg), I(0)));
        } else if (low == 1) {
          // -1, 1
          // CLAIM: (x,y) * (-1,1) = (x-y,y)
          EMIT(MIR_BinaryAcc.create(IA32_SUB, R(lhsReg), R(lowlhsReg)));
        } else {
          // -1, *
          // CLAIM: (x,y) * (-1, z) = (l(x imul z)-y+u(y mul z)+, l(y mul z))
          EMIT(MIR_BinaryAcc.create(IA32_IMUL2, R(lhsReg), I(low)));
          EMIT(MIR_BinaryAcc.create(IA32_SUB, R(lhsReg), R(lowlhsReg)));
          EMIT(MIR_Move.create(IA32_MOV, R(getEAX()), I(low)));
          EMIT(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowlhsReg)));
          EMIT(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
          EMIT(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(getEDX())));
        }
      } else if (high == 0) {
        if (low == -1) {
          // 0, -1
          // CLAIM: (x,y) * (0,-1) = (u(y mul -1)-x, l(y mul -1))
          EMIT(MIR_Move.create(IA32_MOV, R(getEAX()), I(-1)));
          EMIT(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowlhsReg)));
          EMIT(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
          EMIT(MIR_BinaryAcc.create(IA32_SUB, R(getEDX()), R(lhsReg)));
          EMIT(MIR_Move.create(IA32_MOV, R(lhsReg), R(getEDX())));
        } else {
          // 0, *
          // CLAIM: (x,y) * (0,z) = (l(x imul z)+u(y mul z), l(y mul z))
          EMIT(MIR_BinaryAcc.create(IA32_IMUL2, R(lhsReg), I(low)));
          EMIT(MIR_Move.create(IA32_MOV, R(getEAX()), I(low)));
          EMIT(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowlhsReg)));
          EMIT(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
          EMIT(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(getEDX())));
        }
      } else if (high == 1) {
        if (low == -1) {
          // 1, -1
          // CLAIM: (x,y) * (1,-1) = (-x+y+u(y mul -1), l(y mul -1))
          EMIT(MIR_UnaryAcc.create(IA32_NEG, R(lhsReg)));
          EMIT(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(lowlhsReg)));
          EMIT(MIR_Move.create(IA32_MOV, R(getEAX()), I(-1)));
          EMIT(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowlhsReg)));
          EMIT(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
          EMIT(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(getEDX())));
        } else if (low == 0) {
          // 1, 0 
          // CLAIM: (x,y) * (1,0) = (y,0)
          EMIT(MIR_Move.create(IA32_MOV, R(lhsReg), R(lowlhsReg)));
          EMIT(MIR_Move.create(IA32_MOV, R(lowlhsReg), I(0)));
        } else if (low == 1) {
          // 1, 1
          // CLAIM: (x,y) * (1,1)  = (x+y,y)
          EMIT(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(lowlhsReg)));
        } else {
          // 1, *
          // CLAIM: (x,y) * (1,z) = (l(x imul z)+y+u(y mul z), l(y mul z))
          EMIT(MIR_BinaryAcc.create(IA32_IMUL2, R(lhsReg), I(low)));
          EMIT(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(lowlhsReg)));
          EMIT(MIR_Move.create(IA32_MOV, R(getEAX()), I(low)));
          EMIT(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowlhsReg)));
          EMIT(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
          EMIT(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(getEDX())));
        }
      } else {
        if (low == -1) {
          // *, -1
          // CLAIM: (x,y) * (z,-1) = (-x+l(y imul z)+u(y mul -1), l(y mul -1))
          OPT_Register tmp = regpool.getInteger();
          EMIT(MIR_UnaryAcc.create(IA32_NEG, R(lhsReg)));
          EMIT(MIR_Move.create(IA32_MOV, R(tmp), I(high)));
          EMIT(MIR_BinaryAcc.create(IA32_IMUL2, R(tmp), R(lowlhsReg)));
          EMIT(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(tmp)));
          EMIT(MIR_Move.create(IA32_MOV, R(getEAX()), I(low)));
          EMIT(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowlhsReg)));
          EMIT(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
          EMIT(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(getEDX())));
        } else if (low == 0) {
          // *,  0
          // CLAIM: (x,y) * (z,0) = (l(y imul z),0)
          EMIT(MIR_Move.create(IA32_MOV, R(lhsReg), I(high)));
          EMIT(MIR_BinaryAcc.create(IA32_IMUL2, R(lhsReg), R(lowlhsReg)));
          EMIT(MIR_Move.create(IA32_MOV, R(lowlhsReg), I(0)));
        } else if (low == 1) {
          // *, 1
          // CLAIM: (x,y) * (z,1) = (l(y imul z)+x,y)   
          OPT_Register tmp = regpool.getInteger();
          EMIT(MIR_Move.create(IA32_MOV, R(tmp), R(lowlhsReg)));
          EMIT(MIR_BinaryAcc.create(IA32_IMUL2, R(tmp), I(high)));
          EMIT(MIR_Move.create(IA32_ADD, R(lhsReg), R(tmp)));
        } else {
          // *, * (sigh, can't do anything interesting...)
          OPT_Register tmp = regpool.getInteger();
          EMIT(MIR_BinaryAcc.create(IA32_IMUL2, R(lhsReg), I(low)));
          EMIT(MIR_Move.create(IA32_MOV, R(tmp), I(high)));
          EMIT(MIR_BinaryAcc.create(IA32_IMUL2, R(tmp), R(lowlhsReg)));
          EMIT(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(tmp)));
          EMIT(MIR_Move.create(IA32_MOV, R(getEAX()), I(low)));
          EMIT(MIR_Multiply.create(IA32_MUL, R(getEDX()), R(getEAX()), R(lowlhsReg)));
          EMIT(MIR_Move.create(IA32_MOV, R(lowlhsReg), R(getEAX())));
          EMIT(MIR_BinaryAcc.create(IA32_ADD, R(lhsReg), R(getEDX())));
        }
      }
    }
  }


  /**
   * Expansion of LONG_NEG_ACC
   *
   * @param s the instruction to expand
   * @param result the result/first operand
   */
  protected final void LONG_NEG(OPT_Instruction s,
                      OPT_RegisterOperand result) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
    EMIT(MIR_UnaryAcc.create(IA32_NEG, R(lhsReg)));
    EMIT(MIR_UnaryAcc.create(IA32_NEG, R(lowlhsReg)));
    EMIT(MIR_BinaryAcc.mutate(s, IA32_SBB, R(lhsReg), I(0)));
  }


  /**
   * Expansion of LONG_AND
   *
   * @param s the instruction to expand
   * @param result the result/first operand
   * @param value the second operand
   */
  protected final void LONG_AND(OPT_Instruction s,
                      OPT_RegisterOperand result,
                      OPT_Operand value) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
    if (value instanceof OPT_RegisterOperand) {
      OPT_Register rhsReg = ((OPT_RegisterOperand)value).register;
      OPT_Register lowrhsReg = regpool.getSecondReg(rhsReg);
      EMIT(MIR_BinaryAcc.create(IA32_AND, R(lowlhsReg), R(lowrhsReg)));
      EMIT(MIR_BinaryAcc.mutate(s, IA32_AND, R(lhsReg), R(rhsReg)));
    } else {
      OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)value;
      int low = rhs.lower32();
      int high = rhs.upper32();
      if (low == 0) { // x &= 0 ==> x = 0
        EMIT(MIR_Move.create(IA32_MOV, R(lowlhsReg), I(0)));
      } else if (low == -1) { // x &= 0xffffffff ==> x = x ==> nop
      } else {
        EMIT(MIR_BinaryAcc.create(IA32_AND, R(lowlhsReg), I(low)));
      }
      if (high == 0) { // x &= 0 ==> x = 0
        EMIT(MIR_Move.create(IA32_MOV, R(lhsReg), I(0)));
      } else if (high == -1) { // x &= 0xffffffff ==> x = x ==> nop
      } else {
        EMIT(MIR_BinaryAcc.create(IA32_AND, R(lhsReg), I(high)));
      }
    }   
  }


  /**
   * Expansion of LONG_OR
   *
   * @param s the instruction to expand
   * @param result the result/first operand
   * @param value the second operand
   */
  protected final void LONG_OR(OPT_Instruction s,
                               OPT_RegisterOperand result,
                               OPT_Operand value) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
    if (value instanceof OPT_RegisterOperand) {
      OPT_Register rhsReg = ((OPT_RegisterOperand)value).register;
      OPT_Register lowrhsReg = regpool.getSecondReg(rhsReg);
      EMIT(MIR_BinaryAcc.create(IA32_OR, R(lowlhsReg), R(lowrhsReg)));
      EMIT(MIR_BinaryAcc.mutate(s, IA32_OR, R(lhsReg), R(rhsReg)));
    } else {
      OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)value;
      int low = rhs.lower32();
      int high = rhs.upper32();
      if (low == 0) { // x |= 0 ==> x = x ==> nop
      } else if (low == -1) { // x |= 0xffffffff ==> x = 0xffffffff
        EMIT(MIR_Move.create(IA32_MOV, R(lowlhsReg), I(-1)));
      } else {
        EMIT(MIR_BinaryAcc.create(IA32_OR, R(lowlhsReg), I(low)));
      }
      if (high == 0) { // x |= 0 ==> x = x ==> nop
      } else if (high == -1) { // x |= 0xffffffff ==> x = 0xffffffff
        EMIT(MIR_Move.create(IA32_MOV, R(lhsReg), I(-1)));
      } else {
        EMIT(MIR_BinaryAcc.create(IA32_OR, R(lhsReg), I(high)));
      }
    }   
  }


  /**
   * Expansion of LONG_XOR
   *
   * @param s the instruction to expand
   * @param result the result/first operand
   * @param value the second operand
   */
  protected final void LONG_XOR(OPT_Instruction s,
                                OPT_RegisterOperand result,
                                OPT_Operand value) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
    if (value instanceof OPT_RegisterOperand) {
      OPT_Register rhsReg = ((OPT_RegisterOperand)value).register;
      OPT_Register lowrhsReg = regpool.getSecondReg(rhsReg);
      EMIT(MIR_BinaryAcc.create(IA32_XOR, R(lowlhsReg), R(lowrhsReg)));
      EMIT(MIR_BinaryAcc.mutate(s, IA32_XOR, R(lhsReg), R(rhsReg)));
    } else {
      OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)value;
      int low = rhs.lower32();
      int high = rhs.upper32();
      if (low == 0) { // x ^= 0 ==> x = x ==> nop
      } else if (low == -1) { // x ^= 0xffffffff ==> x = ~x
        EMIT(MIR_UnaryAcc.create(IA32_NOT, R(lowlhsReg)));
      } else {
        EMIT(MIR_BinaryAcc.create(IA32_XOR, R(lowlhsReg), I(low)));
      }
      if (high == 0) { // x ^= 0 ==> x = x ==> nop
      } else if (high == -1) { // x ^= 0xffffffff ==> x = ~x
        EMIT(MIR_UnaryAcc.create(IA32_NOT, R(lhsReg)));
      } else {
        EMIT(MIR_BinaryAcc.create(IA32_XOR, R(lhsReg), I(high)));
      }
    }
  }


  /**
   * Expansion of LONG_NOT
   *
   * @param s the instruction to expand
   * @param result the result/first operand
   */
  protected final void LONG_NOT(OPT_Instruction s,
                                OPT_RegisterOperand result) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
    EMIT(MIR_UnaryAcc.create(IA32_NOT, R(lowlhsReg)));
    EMIT(MIR_UnaryAcc.mutate(s, IA32_NOT, R(lhsReg)));
  }


  /**
   * Expansion of FP_ADD_ACC, FP_MUL_ACC, 
   * FP_SUB_ACC, and FP_DIV_ACC.
   * Moves first value into fp0,
   * accumulates second value into fp0 using op,
   * moves fp0 into result.
   *
   * @param s the instruction to expand
   * @param op the floating point op to use
   * @param result the result operand
   * @param val1 the first operand
   * @param val2 the second operand
   */
  protected final void FP_MOV_OP_MOV(OPT_Instruction s,
                                     OPT_Operator op,
                                     OPT_Operand result,
                                     OPT_Operand val1,
                                     OPT_Operand val2) {
    EMIT(MIR_Move.create(IA32_FMOV, D(getFPR(0)), val1));
    EMIT(MIR_BinaryAcc.mutate(s, op, D(getFPR(0)), val2));
    EMIT(MIR_Move.create(IA32_FMOV, result, D(getFPR(0))));
  }
  /**
   * Expansion of FP_ADD_ACC, FP_MUL_ACC, 
   * FP_SUB_ACC, and FP_DIV_ACC.
   * Moves first value into fp0,
   * accumulates second value into fp0 using op.
   *
   * @param s the instruction to expand
   * @param op the floating point op to use
   * @param val1 the first operand
   * @param val2 the second operand
   */
  protected final void FP_MOV_OP(OPT_Instruction s,
                                 OPT_Operator op,
                                 OPT_Operand val1,
                                 OPT_Operand val2) {
    EMIT(MIR_Move.create(IA32_FMOV, D(getFPR(0)), val1));
    EMIT(MIR_BinaryAcc.mutate(s, op, D(getFPR(0)), val2));
  }
  /**
   * Expansion of FP_ADD_ACC, FP_MUL_ACC, 
   * FP_SUB_ACC, and FP_DIV_ACC.
   * apply op to val1 and val2
   * move val1 to result using movop
   *
   * @param s the instruction to expand
   * @param op the floating point op to use
   * @param movop the move op to use
   * @param result the result operand
   * @param val1 the first operand
   * @param val2 the second operand
   */
  protected final void FP_OP_MOV(OPT_Instruction s,
                                 OPT_Operator op,
                                 OPT_Operator movop,
                                 OPT_Operand result,
                                 OPT_Operand val1,
                                 OPT_Operand val2) {
    EMIT(MIR_BinaryAcc.mutate(s, op, val1, val2));
    EMIT(MIR_Move.create(movop, result, val1.copy()));
  }
  /**
   * Expansion of FP_ADD_ACC, FP_MUL_ACC, 
   * FP_SUB_ACC, and FP_DIV_ACC.
   * apply op to val1 and val2.
   * NOTE: either val1 or val2 must be either FPR0 or ST(0)!
   * 
   * @param s the instruction to expand
   * @param op the floating point op to use
   * @param val1 the first operand
   * @param val2 the second operand
   */
  protected final void FP_OP(OPT_Instruction s,
                             OPT_Operator op,
                             OPT_Operand val1,
                             OPT_Operand val2) {
    EMIT(MIR_BinaryAcc.mutate(s, op, val1, val2));
  }

  /**
   * Expansion of FP_REM 
   *
   * @param s the instruction to expand
   * @param val1 the first operand
   * @param val2 the second operand
   */
  protected final void FP_REM(OPT_Instruction s,
                              OPT_Operand val1,
                              OPT_Operand val2) {
    EMIT(MIR_Move.create(IA32_FMOV, D(getFPR(1)), val2));
    EMIT(MIR_Move.create(IA32_FMOV, D(getFPR(0)), val1));
    EMIT(MIR_BinaryAcc.mutate(s,IA32_FPREM, D(getFPR(0)), D(getFPR(1))));
  }
  /**
   * Expansion of FP_REM
   *
   * @param s the instruction to expand
   * @param val the operand to divide with fp0 to get a remainder
   */
  protected final void FP_REM(OPT_Instruction s,
                              OPT_Operand val) {
    EMIT(MIR_Move.create(IA32_FMOV, D(getFPR(1)), val));
    EMIT(MIR_BinaryAcc.mutate(s,IA32_FPREM, D(getFPR(0)), D(getFPR(1))));
  }


  /**
   * Expansion of BOOLEAN_CMP
   *
   * @param s the instruction to copy position info from
   * @param result the result operand
   * @param val1   the first value
   * @param val2   the second value
   * @param cond   the condition operand
   */
  protected final void BOOLEAN_CMP(OPT_Instruction s,
                                   OPT_Operand res, 
                                   OPT_Operand val1,
                                   OPT_Operand val2,
                                   OPT_ConditionOperand cond) {
    EMIT(CPOS(s, MIR_Compare.create(IA32_CMP, val1, val2)));
    OPT_RegisterOperand temp = regpool.makeTemp(VM_TypeReference.Boolean);
    EMIT(CPOS(s, MIR_Set.create(IA32_SET$B, temp, COND(cond))));
    EMIT(MIR_Unary.mutate(s, IA32_MOVZX$B, res, temp.copyD2U()));
  }


  /**
   * Expansion of a special case of BOOLEAN_CMP when the 
   * condition registers have already been set by the previous
   * ALU op.
   *
   * @param s the instruction to copy position info from
   * @param result the result operand
   * @param cond   the condition operand
   */
  protected final void BOOLEAN_CMP(OPT_Instruction s,
                                   OPT_Operand res, 
                                   OPT_ConditionOperand cond) {
    OPT_RegisterOperand temp = regpool.makeTemp(VM_TypeReference.Boolean);
    EMIT(CPOS(s, MIR_Set.create(IA32_SET$B, temp, COND(cond))));
    EMIT(MIR_Unary.mutate(s, IA32_MOVZX$B, res, temp.copyD2U()));
  }


  /**
   * Generate a compare and branch sequence.
   * Used in the expansion of trees where INT_IFCMP is a root
   * 
   * @param s the ifcmp instruction 
   * @param val1 the first value operand
   * @param val2 the second value operand
   * @param cond the condition operand
   */
  protected final void IFCMP(OPT_Instruction s,
                             OPT_Operand val1, OPT_Operand val2,
                             OPT_ConditionOperand cond) {
    EMIT(CPOS(s, MIR_Compare.create(IA32_CMP, val1, val2)));
    EMIT(MIR_CondBranch.mutate(s, IA32_JCC, COND(cond),
                                      IfCmp.getTarget(s), 
                                      IfCmp.getBranchProfile(s)));
  }


  /**
   * Generate the compare portion of a conditional move.
   * 
   * @param s the instruction to copy position info from
   * @param val1 the first value to compare
   * @param val2 the second value to compare
   */
  protected final void CMOV_CMP(OPT_Instruction s,
                                OPT_Operand val1, OPT_Operand val2) {
    if (val1.isRegister() && val1.asRegister().register.isFloatingPoint()) {
      if (VM.VerifyAssertions) {
        VM._assert(val2.isRegister());
        VM._assert(val2.asRegister().register.isFloatingPoint());
      }
      EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, D(getFPR(0)), val1)));
      EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMI, D(getFPR(0)), val2)));
    } else {
      EMIT(CPOS(s, MIR_Compare.create(IA32_CMP, val1, val2)));
    }
  }

  /**
   * Generate the move portion of a conditional move.
   *
   * @param s the instruction to copy position info from
   * @param result the result of the conditional move
   * @param cond the condition operand
   * @param trueVal the value to move to result if cond is true
   * @param falseVal the value to move to result if cond is not true
   */
  protected final void CMOV_MOV(OPT_Instruction s,
                                OPT_RegisterOperand result,
                                OPT_ConditionOperand cond,
                                OPT_Operand trueValue,
                                OPT_Operand falseValue) {
    OPT_Operator movop, cmovop;
    if (result.type.isDoubleType() || result.type.isFloatType()) {
      movop = IA32_FMOV;
      cmovop = IA32_FCMOV;
    } else {
      movop = IA32_MOV;
      cmovop = IA32_CMOV;
    }

    if (result.similar(trueValue)) {
      // in this case, only need a conditional move for the false branch.
      EMIT(MIR_CondMove.mutate(s, cmovop, result,
                                      asReg(s, movop, falseValue),
                                      COND(cond.flipCode())));
    } else if (result.similar(falseValue)) {
      // in this case, only need a conditional move for the true branch.
      EMIT(MIR_CondMove.mutate(s, cmovop, result, 
                                      asReg(s, movop, trueValue),
                                      COND(cond)));
    } else {
      // need to handle both possible assignments. Unconditionally
      // assign one value then conditionally assign the other.
      if (falseValue.isRegister()) {
        EMIT(CPOS(s,MIR_Move.create(movop, result, trueValue)));
        EMIT(MIR_CondMove.mutate(s, cmovop, result.copy(), 
                                        falseValue,
                                        COND(cond.flipCode())));
      } else {
        EMIT(CPOS(s,MIR_Move.create(movop, result, falseValue)));
        EMIT(MIR_CondMove.mutate(s, cmovop, result.copy(), 
                                        asReg(s, movop, trueValue),
                                        COND(cond)));
      }
    }
  }

  // move op into a register operand if it isn't one already.
  private OPT_Operand asReg(OPT_Instruction s, 
                            OPT_Operator movop, OPT_Operand op) {
    if (op.isRegister()) return op;
    OPT_RegisterOperand tmp = regpool.makeTemp(op);
    EMIT(CPOS(s, MIR_Move.create(movop, tmp, op)));
    return tmp.copy();
  }


  /**
   * Expand a prologue by expanding out longs into pairs of ints
   */
  protected final void PROLOGUE(OPT_Instruction s) {
    int numFormals = Prologue.getNumberOfFormals(s);
    int numLongs = 0;
    for (int i=0; i<numFormals; i++) {
      if (Prologue.getFormal(s, i).type.isLongType()) numLongs ++;
    }
    if (numLongs != 0) {
      OPT_Instruction s2 = Prologue.create(IR_PROLOGUE, numFormals+numLongs);
      for (int sidx=0, s2idx=0; sidx<numFormals; sidx++) {
        OPT_RegisterOperand sForm = Prologue.getFormal(s, sidx);
        if (sForm.type.isLongType()) {
          sForm.type = VM_TypeReference.Int;
          Prologue.setFormal(s2, s2idx++, sForm);
          OPT_Register r2 = regpool.getSecondReg(sForm.register);
          Prologue.setFormal(s2, s2idx++, R(r2));
          sForm.register.clearType();
          sForm.register.setInteger();
          r2.clearType();
          r2.setInteger();
        } else {
          Prologue.setFormal(s2, s2idx++, sForm);
        }
      }                                                                      
      EMIT(s2);
    } else {
      EMIT(s);
    }
  }

  /**
   * Expansion of CALL.
   * Expand longs registers into pairs of int registers.
   *
   * @param s the instruction to expand
   * @param address the operand containing the target address
   */
  protected final void CALL(OPT_Instruction s, OPT_Operand address) {
    // Step 1: Find out how many parameters we're going to have.
    int numParams = Call.getNumberOfParams(s);
    int longParams = 0;
    for (int pNum = 0; pNum < numParams; pNum++) {
      if (Call.getParam(s, pNum).getType().isLongType()) {
        longParams++;
      }
    }

    // Step 2: Figure out what the result and result2 values will be.
    OPT_RegisterOperand result = Call.getResult(s);
    OPT_RegisterOperand result2 = null;
    if (result != null && result.type.isLongType()) {
      result.type = VM_TypeReference.Int;
      result2 = R(regpool.getSecondReg(result.register));
    }
    
    // Step 3: Mutate the Call to an MIR_Call.
    // Note MIR_Call and Call have a different number of fixed 
    // arguments, so some amount of copying is required. 
    OPT_Operand[] params = new OPT_Operand[numParams];
    for (int i = 0; i < numParams; i++) {
      params[i] = Call.getParam(s, i);
    }
    MIR_Call.mutate(s, IA32_CALL, result, result2, 
                    address, Call.getMethod(s),
                    numParams + longParams);
    for (int paramIdx = 0, mirCallIdx = 0; paramIdx < numParams;) {
      OPT_Operand param = params[paramIdx++];
      if (param instanceof OPT_RegisterOperand) {
        MIR_Call.setParam(s, mirCallIdx++, param);
        OPT_RegisterOperand rparam = (OPT_RegisterOperand)param;
        if (rparam.type.isLongType()) {
          MIR_Call.setParam(s, mirCallIdx++, 
                            L(regpool.getSecondReg(rparam.register)));
        }
      } else if (param instanceof OPT_LongConstantOperand) {
        OPT_LongConstantOperand val = (OPT_LongConstantOperand)param;
        MIR_Call.setParam(s, mirCallIdx++, I(val.upper32()));
        MIR_Call.setParam(s, mirCallIdx++, I(val.lower32()));
      } else {
        MIR_Call.setParam(s, mirCallIdx++, param);
      }
    }

    // emit the call instruction.
    EMIT(s);
  }

  /**
   * Expansion of SYSCALL.
   * Expand longs registers into pairs of int registers.
   *
   * @param s the instruction to expand
   * @param address the operand containing the target address
   */
  protected final void SYSCALL(OPT_Instruction s, OPT_Operand address) {
    burs.ir.setHasSysCall(true);

    // Step 1: Find out how many parameters we're going to have.
    int numParams = Call.getNumberOfParams(s);
    int longParams = 0;
    for (int pNum = 0; pNum < numParams; pNum++) {
      if (Call.getParam(s, pNum).getType().isLongType()) {
        longParams++;
      }
    }

    // Step 2: Figure out what the result and result2 values will be.
    OPT_RegisterOperand result = Call.getResult(s);
    OPT_RegisterOperand result2 = null;
    // NOTE: C callee returns longs little endian!
    if (result != null && result.type.isLongType()) {
      result.type = VM_TypeReference.Int;
      result2 = result;
      result = R(regpool.getSecondReg(result.register));
    }
    
    // Step 3: Mutate the Call to an MIR_Call.
    // Note MIR_Call and Call have a different number of fixed 
    // arguments, so some amount of copying is required. 
    OPT_Operand[] params = new OPT_Operand[numParams];
    for (int i = 0; i < numParams; i++) {
      params[i] = Call.getParam(s, i);
    }
    MIR_Call.mutate(s, IA32_SYSCALL, result, result2, 
                    address, Call.getMethod(s),
                    numParams + longParams);
    for (int paramIdx = 0, mirCallIdx = 0; paramIdx < numParams;) {
      OPT_Operand param = params[paramIdx++];
      if (param instanceof OPT_RegisterOperand) {
        // NOTE: longs passed little endian to C callee!
        OPT_RegisterOperand rparam = (OPT_RegisterOperand)param;
        if (rparam.type.isLongType()) {
          MIR_Call.setParam(s, mirCallIdx++, 
                            L(regpool.getSecondReg(rparam.register)));
        }
        MIR_Call.setParam(s, mirCallIdx++, param);
      } else if (param instanceof OPT_LongConstantOperand) {
        long value = ((OPT_LongConstantOperand)param).value; 
        int valueHigh = (int)(value >> 32);
        int valueLow = (int)(value & 0xffffffff);
        // NOTE: longs passed little endian to C callee!
        MIR_Call.setParam(s, mirCallIdx++, I(valueLow));
        MIR_Call.setParam(s, mirCallIdx++, I(valueHigh));
      } else {
        MIR_Call.setParam(s, mirCallIdx++, param);
      }
    }

    // emit the call instruction.
    EMIT(s);
  }

  /**
   * Expansion of LOWTABLESWITCH.  
   *
   * @param s the instruction to expand
   */
  protected final void LOWTABLESWITCH(OPT_Instruction s) {
    // (1) We're changing index from a U to a DU.
    //     Inject a fresh copy instruction to make sure we aren't
    //     going to get into trouble (if someone else was also using index).
    OPT_RegisterOperand newIndex = regpool.makeTempInt(); 
    EMIT(MIR_Move.create(IA32_MOV, newIndex, LowTableSwitch.getIndex(s))); 
    int number = LowTableSwitch.getNumberOfTargets(s);
    OPT_Instruction s2 = CPOS(s,MIR_LowTableSwitch.create(MIR_LOWTABLESWITCH, newIndex, number*2));
    for (int i=0; i<number; i++) {
      MIR_LowTableSwitch.setTarget(s2,i,LowTableSwitch.getTarget(s,i));
      MIR_LowTableSwitch.setBranchProfile(s2,i,LowTableSwitch.getBranchProfile(s,i));
    }
    EMIT(s2);
  }

  /**
   * Expansion of RESOLVE.  Dynamic link point.
   * Build up MIR instructions for Resolve.
   *
   * @param s the instruction to expand
   */
  protected final void RESOLVE(OPT_Instruction s) {
    OPT_Operand target = loadFromJTOC(VM_Entrypoints.optResolveMethod.getOffset());
    EMIT(CPOS(s, MIR_Call.mutate0(s, CALL_SAVE_VOLATILE, 
                                         null, null,  target, 
                                         OPT_MethodOperand.STATIC(VM_Entrypoints.optResolveMethod))));
  }
  /**
   * Expansion of TRAP_IF, with an int constant as the second value.
   *
   * @param s the instruction to expand
   */
  protected final void TRAP_IF_IMM(OPT_Instruction s) {
    OPT_RegisterOperand gRes = TrapIf.getGuardResult(s);
    OPT_RegisterOperand v1 =  (OPT_RegisterOperand)TrapIf.getVal1(s);
    OPT_IntConstantOperand v2 = (OPT_IntConstantOperand)TrapIf.getVal2(s);
    OPT_ConditionOperand cond = TrapIf.getCond(s);
    OPT_TrapCodeOperand tc = TrapIf.getTCode(s);

    // A slightly ugly matter, but we need to deal with combining
    // the two pieces of a long register from a LONG_ZERO_CHECK.  
    // A little awkward, but probably the easiest workaround...
    if (tc.getTrapCode() == VM_Runtime.TRAP_DIVIDE_BY_ZERO && v1.type.isLongType()) {
      OPT_RegisterOperand rr = regpool.makeTempInt();
      EMIT(MIR_Move.create(IA32_MOV, rr, v1.copy()));
      EMIT(MIR_BinaryAcc.create(IA32_OR, rr.copy(), 
                                       R(regpool.getSecondReg
                                         (v1.register))));
      v1 = rr.copyD2U();
    } 

    // emit the trap instruction
    EMIT(MIR_TrapIf.mutate(s, IA32_TRAPIF, gRes, v1, v2, COND(cond),
                                  tc));
  }


  /**
   * This routine expands an ATTEMPT instruction 
   * into an atomic compare exchange.
   *
   * @param result   the register operand that is set to 0/1 as a result of the attempt
   * @param mo       the address at which to attempt the exchange
   * @param oldValue the old value at the address mo
   * @param newValue the new value at the address mo
   */
  protected final void ATTEMPT(OPT_RegisterOperand result,
                               OPT_MemoryOperand mo,
                               OPT_Operand oldValue,
                               OPT_Operand newValue) {
    OPT_RegisterOperand temp = regpool.makeTempInt();
    OPT_RegisterOperand temp2 = regpool.makeTemp(result);
    EMIT(MIR_Move.create(IA32_MOV, temp, newValue));
    EMIT(MIR_Move.create(IA32_MOV, R(getEAX()), oldValue));
    EMIT(MIR_CompareExchange.create(IA32_LOCK_CMPXCHG, R(getEAX()), 
                                           mo, (OPT_RegisterOperand)temp.copy())); 
    EMIT(MIR_Set.create(IA32_SET$B, temp2, OPT_IA32ConditionOperand.EQ()));
    // need to zero-extend the result of the set
    EMIT(MIR_Unary.create(IA32_MOVZX$B, result, temp2.copy()));
  }


  /**
   * This routine expands the compound pattern
   * IFCMP(ATTEMPT, ZERO) into an atomic compare/exchange 
   * followed by a branch on success/failure
   * of the attempted atomic compare/exchange.
   *
   * @param mo       the address at which to attempt the exchange
   * @param oldValue the old value at the address mo
   * @param newValue the new value at the address mo
   * @param cond     the condition to branch on
   * @param target   the branch target
   * @param bp       the branch profile information
   */
  protected final void ATTEMPT_IFCMP(OPT_MemoryOperand mo,
                                     OPT_Operand oldValue,
                                     OPT_Operand newValue,
                                     OPT_ConditionOperand cond,
                                     OPT_BranchOperand target,
                                     OPT_BranchProfileOperand bp) {
    OPT_RegisterOperand temp = regpool.makeTempInt();
    EMIT(MIR_Move.create(IA32_MOV, temp, newValue));
    EMIT(MIR_Move.create(IA32_MOV, R(getEAX()), oldValue));
    EMIT(MIR_CompareExchange.create(IA32_LOCK_CMPXCHG, R(getEAX()), 
                                           mo, (OPT_RegisterOperand)temp.copy())); 
    EMIT(MIR_CondBranch.create(IA32_JCC, COND(cond), target, bp));
  }

  /* special case handling OSR instructions 
   * expand long type variables to two intergers
   */
  void OSR(OPT_BURS burs, OPT_Instruction s) {
//-#if RVM_WITH_OSR
   if (VM.VerifyAssertions) VM._assert(OsrPoint.conforms(s));

    // 1. how many params
    int numparam = OsrPoint.getNumberOfElements(s);
    int numlong = 0;
    for (int i = 0; i < numparam; i++) {
      OPT_Operand param = OsrPoint.getElement(s, i);
      if (param.getType().isLongType()) {
        numlong++;
      }
    }

    // 2. collect params
    OPT_InlinedOsrTypeInfoOperand typeInfo = 
      OsrPoint.getClearInlinedTypeInfo(s);

    if (VM.VerifyAssertions) {
      if (typeInfo == null) {
        VM.sysWriteln("OsrPoint "+s+" has a <null> type info:");
        VM.sysWriteln("  position :"+s.bcIndex+"@"+s.position.method);
      }
      VM._assert(typeInfo != null);
    }

    OPT_Operand[] params = new OPT_Operand[numparam];
    for (int i = 0; i <numparam; i++) {
      params[i] = OsrPoint.getClearElement(s, i);
    }

    // set the number of valid params in osr type info, used
    // in LinearScan
    typeInfo.validOps = numparam;

    // 3: only makes second half register of long being used
    //    creates room for long types.
    burs.append(OsrPoint.mutate(s, s.operator(), 
                                typeInfo,
                                numparam + numlong));

    int pidx = numparam;
    for (int i = 0; i < numparam; i++) {
      OPT_Operand param = params[i];
      OsrPoint.setElement(s, i, param);
      if (param instanceof OPT_RegisterOperand) {
        OPT_RegisterOperand rparam = (OPT_RegisterOperand)param;
        // the second half is appended at the end
        // OPT_LinearScan will update the map.
        if (rparam.type.isLongType()) {
          OsrPoint.setElement(s, pidx++, 
                            L(burs.ir.regpool.getSecondReg(rparam.register)));
        }
      } else if (param instanceof OPT_LongConstantOperand) {
        OPT_LongConstantOperand val = (OPT_LongConstantOperand)param;

        if (VM.TraceOnStackReplacement) {
          VM.sysWriteln("caught a long const " + val);
        }

        OsrPoint.setElement(s, i, I(val.upper32()));
        OsrPoint.setElement(s, pidx++, I(val.lower32()));
      } else if (param instanceof OPT_IntConstantOperand){
        continue;
      } else {
        throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers", "unexpected parameter type"+param);
      }
    }

    if (pidx != (numparam+numlong)) {
      VM.sysWriteln("pidx = "+pidx);
      VM.sysWriteln("numparam = "+numparam);
      VM.sysWriteln("numlong = "+numlong);
    }

    if (VM.VerifyAssertions) VM._assert(pidx == (numparam+numlong));

        /*
    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("BURS rewrite OsrPoint "+s);
      VM.sysWriteln("  position "+s.bcIndex+"@"+s.position.method);
    }
        */
  //-#endif
  }
}
