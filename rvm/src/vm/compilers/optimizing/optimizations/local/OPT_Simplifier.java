/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * A constant folder, strength reducer and axiomatic simplifier. 
 *
 * <p> This module performs no analysis, it simply attempts to 
 * simplify the instruction as is. The intent is that
 * analysis modules can call this transformation engine, allowing us to
 * share the tedious simplification code among multiple analysis modules.
 *
 * <p> NOTE: For maintainability purposes, I've intentionally avoided being
 * clever about combining 'similar' operators together into a combined case
 * of the main switch switch statement. Also, operators are in sorted ordered
 * within each major grouping.  Please maintain this coding style.
 * I'd rather have this module be 2000 lines of obviously correct code than 
 * 500 lines of clever code. 
 *
 * @author Dave Grove
 */
abstract class OPT_Simplifier extends OPT_IRTools implements OPT_Operators {
  // NOTE: The convention is that constant folding is controlled based
  // on the type of the result of the operator, not the type of its inputs.
  /** 
   * Constant fold integer operations?
   */
  public static final boolean CF_INT = true;
  /** 
   * Constant fold long operations?
   */
  public static final boolean CF_LONG = true;
  /** 
   * Constant fold float operations?  Default is false to avoid consuming
   * precious JTOC slots to hold new constant values.
   */
  public static final boolean CF_FLOAT = false;  
  /** 
   * Constant fold double operations?  Default is false to avoid consuming
   * precious JTOC slots to hold new constant values.
   */
  public static final boolean CF_DOUBLE = false; 
  /**
   * Enumeration value to indicate an operation is unchanged, although the
   * order of operands may have been canonicalized.
   */
  public static final byte UNCHANGED = 0x00;    
  /**
   * Enumeration value to indicate an operation has been replaced by a
   * move instruction with a constant right hand side.
   */
  public static final byte MOVE_FOLDED = 0x01;  
  /**
   * Enumeration value to indicate an operation has been replaced by a
   * move instruction with a non-constant right hand side.
   */
  public static final byte MOVE_REDUCED = 0x02; 
  /**
   * Enumeration value to indicate an operation has been replaced by 
   * an unconditional trap instruction.
   */
  public static final byte TRAP_REDUCED = 0x03; 
  /**
   * Enumeration value to indicate an operation has been replaced by a
   * cheaper, but non-move instruction.
   */
  public static final byte REDUCED = 0x04;      

  /**
   * Given an instruction, attempt to simplify it.
   * The instruction will be mutated in place.
   *
   * <p> We don't deal with branching operations here -- 
   * doing peephole optimizations of branches
   * is the job of a separate module.
   *
   * @param s the instruction to simplify
   * @return one of UNCHANGED, MOVE_FOLDED, MOVE_REDUCED, TRAP_REDUCED, REDUCED
   */
  public static byte simplify(OPT_Instruction s) {
    switch (s.getOpcode()) {
      ////////////////////
      // GUARD operations
      ////////////////////
    case GUARD_COMBINE_opcode:
      {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2 instanceof OPT_TrueGuardOperand) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1 instanceof OPT_TrueGuardOperand) {
	    // BOTH TrueGuards: FOLD
	    Move.mutate(s, GUARD_MOVE, Binary.getClearResult(s), op1);
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS TrueGuard: MOVE REDUCE
	    Move.mutate(s, GUARD_MOVE, Binary.getClearResult(s), 
			Binary.getClearVal1(s));
	    return MOVE_REDUCED;
	  }
	}
      }
      return UNCHANGED;
      ////////////////////
      // TRAP operations
      ////////////////////
    case TRAP_IF_opcode:
      { 
	OPT_Operand op1 = TrapIf.getVal1(s);
	if (op1.isIntConstant()) {
	  OPT_Operand op2 = TrapIf.getVal2(s);
	  if (op2.isIntConstant()) {
	    boolean willTrap = TrapIf.getCond(s).evaluate(op1, op2);
	    if (willTrap) {
	      Trap.mutate(s, TRAP, TrapIf.getClearGuardResult(s), 
			  TrapIf.getClearTCode(s));
	      return TRAP_REDUCED;
	    } else {
	      Move.mutate(s, GUARD_MOVE, TrapIf.getClearGuardResult(s), TG());
	      return MOVE_FOLDED;
	    }
	  } else {
	    // cannonicalize
	    TrapIf.mutate(s, TRAP_IF, TrapIf.getClearGuardResult(s),
			  TrapIf.getClearVal2(s),
			  TrapIf.getClearVal1(s),
			  TrapIf.getClearCond(s).flipOperands(), 
			  TrapIf.getClearTCode(s));
	  }
	}
      }			  
      return UNCHANGED;
    case NULL_CHECK_opcode:
      {
	OPT_Operand ref = NullCheck.getRef(s);
	if (ref.isNullConstant()) {
	  Trap.mutate(s, TRAP, NullCheck.getClearGuardResult(s),
		      OPT_TrapCodeOperand.NullPtr());
	  return TRAP_REDUCED;
	} else if (ref.isStringConstant()) {
	  Move.mutate(s, GUARD_MOVE, NullCheck.getClearGuardResult(s), TG());
	  return MOVE_FOLDED;
	} 
	return UNCHANGED;
      }
    case INT_ZERO_CHECK_opcode:
      {
	OPT_Operand op = ZeroCheck.getValue(s);
	if (op.isIntConstant()) {
	  int val = op.asIntConstant().value;
	  if (val == 0) {
	    Trap.mutate(s, TRAP, ZeroCheck.getClearGuardResult(s),
			OPT_TrapCodeOperand.DivByZero());
	    return TRAP_REDUCED;
	  } else {
	    Move.mutate(s, GUARD_MOVE, ZeroCheck.getClearGuardResult(s), TG());
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
    case LONG_ZERO_CHECK_opcode:
      {
	OPT_Operand op = ZeroCheck.getValue(s);
	if (op.isLongConstant()) {
	  long val = op.asLongConstant().value;
	  if (val == 0L) {
	    Trap.mutate(s, TRAP, ZeroCheck.getClearGuardResult(s),
			OPT_TrapCodeOperand.DivByZero());
	    return TRAP_REDUCED;
	  } else {
	    Move.mutate(s, GUARD_MOVE, ZeroCheck.getClearGuardResult(s), TG());
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
    case CHECKCAST_opcode:
      { 
	OPT_Operand ref = TypeCheck.getRef(s);
	if (ref.isNullConstant()) {
	  Empty.mutate(s, NOP);
	  return REDUCED;
	} else {
	  VM_Type rType = ref.getType();
	  OPT_TypeOperand op1 = TypeCheck.getType(s);
	  if (OPT_ClassLoaderProxy.proxy.isAssignableWith(op1.type, 
							  rType) == OPT_Constants.YES) {
	    Empty.mutate(s, NOP);
	    return REDUCED;
	  }
	}
      }
      return UNCHANGED;
    case CHECKCAST_NOTNULL_opcode:
      { 
	VM_Type rType = TypeCheck.getRef(s).getType();
	OPT_TypeOperand op1 = TypeCheck.getType(s);
	if (OPT_ClassLoaderProxy.proxy.isAssignableWith(op1.type, 
							rType) == OPT_Constants.YES) {
	  Empty.mutate(s, NOP);
	  return REDUCED;
	}
      }
      return UNCHANGED;
      ////////////////////
      // Conditional moves
      ////////////////////
    case INT_COND_MOVE_opcode:
      {
	OPT_Operand val1 = CondMove.getVal1(s);
	if (val1.isConstant()) {
	  OPT_Operand val2 = CondMove.getVal2(s);
	  if (val2.isConstant()) {
	    // BOTH CONSTANTS: FOLD
	    boolean cond = CondMove.getCond(s).evaluate(val1, val2);
	    OPT_Operand val = 
	      cond ? CondMove.getClearTrueValue(s) : CondMove.getClearFalseValue(s);
	    Move.mutate(s, INT_MOVE, CondMove.getClearResult(s), val);
	    return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
	  } else {	      
	    // Cannonicalize by switching operands and fliping code.
	    OPT_Operand tmp = CondMove.getClearVal1(s);
	    CondMove.setVal1(s, CondMove.getClearVal2(s));
	    CondMove.setVal2(s, tmp);
	    CondMove.getCond(s).flipOperands();
	  }
	}
	OPT_Operand tv = CondMove.getTrueValue(s);
	OPT_Operand fv = CondMove.getFalseValue(s);
	if (tv.similar(fv)) {
	  Move.mutate(s, INT_MOVE, CondMove.getClearResult(s), tv.clear());
	  return tv.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
	}
	if (tv.isIntConstant() && fv.isIntConstant()) {
	  int itv = tv.asIntConstant().value;
	  int ifv = fv.asIntConstant().value;
	  if (itv == 1 && ifv == 0) {
	    BooleanCmp.mutate(s, BOOLEAN_CMP, CondMove.getClearResult(s),
			      CondMove.getClearVal1(s), CondMove.getClearVal2(s),
			      CondMove.getClearCond(s), new OPT_BranchProfileOperand());
	    return REDUCED;
	  }
	  if (itv == 0 && ifv == 1) {
	    BooleanCmp.mutate(s, BOOLEAN_CMP, CondMove.getClearResult(s),
			      CondMove.getClearVal1(s), CondMove.getClearVal2(s),
			      CondMove.getClearCond(s).flipCode(), new OPT_BranchProfileOperand());
	    return REDUCED;
	  }
	}
      }
      return UNCHANGED;
    case LONG_COND_MOVE_opcode:
      {
	OPT_Operand val1 = CondMove.getVal1(s);
	if (val1.isConstant()) {
	  OPT_Operand val2 = CondMove.getVal2(s);
	  if (val2.isConstant()) {
	    // BOTH CONSTANTS: FOLD
	    boolean cond = CondMove.getCond(s).evaluate(val1, val2);
	    OPT_Operand val = 
	      cond ? CondMove.getClearTrueValue(s) : CondMove.getClearFalseValue(s);
	    Move.mutate(s, LONG_MOVE, CondMove.getClearResult(s), val);
	    return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
	  } else {	      
	    // Cannonicalize by switching operands and fliping code.
	    OPT_Operand tmp = CondMove.getClearVal1(s);
	    CondMove.setVal1(s, CondMove.getClearVal2(s));
	    CondMove.setVal2(s, tmp);
	    CondMove.getCond(s).flipOperands();
	  }
	}
	if (CondMove.getTrueValue(s).similar(CondMove.getFalseValue(s))) {
	  OPT_Operand val = CondMove.getClearTrueValue(s);
	  Move.mutate(s, LONG_MOVE, CondMove.getClearResult(s), val);
	  return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
	}
      }
      return UNCHANGED;
    case FLOAT_COND_MOVE_opcode:
      {
	OPT_Operand val1 = CondMove.getVal1(s);
	if (val1.isConstant()) {
	  OPT_Operand val2 = CondMove.getVal2(s);
	  if (val2.isConstant()) {
	    // BOTH CONSTANTS: FOLD
	    boolean cond = CondMove.getCond(s).evaluate(val1, val2);
	    OPT_Operand val = 
	      cond ? CondMove.getClearTrueValue(s) : CondMove.getClearFalseValue(s);
	    Move.mutate(s, FLOAT_MOVE, CondMove.getClearResult(s), val);
	    return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
	  } else {	      
	    // Cannonicalize by switching operands and fliping code.
	    OPT_Operand tmp = CondMove.getClearVal1(s);
	    CondMove.setVal1(s, CondMove.getClearVal2(s));
	    CondMove.setVal2(s, tmp);
	    CondMove.getCond(s).flipOperands();
	  }
	}
	if (CondMove.getTrueValue(s).similar(CondMove.getFalseValue(s))) {
	  OPT_Operand val = CondMove.getClearTrueValue(s);
	  Move.mutate(s, FLOAT_MOVE, CondMove.getClearResult(s), val);
	  return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
	}
      }
      return UNCHANGED;
    case DOUBLE_COND_MOVE_opcode:
      {
	OPT_Operand val1 = CondMove.getVal1(s);
	if (val1.isConstant()) {
	  OPT_Operand val2 = CondMove.getVal2(s);
	  if (val2.isConstant()) {
	    // BOTH CONSTANTS: FOLD
	    boolean cond = CondMove.getCond(s).evaluate(val1, val2);
	    OPT_Operand val = 
	      cond ? CondMove.getClearTrueValue(s) : CondMove.getClearFalseValue(s);
	    Move.mutate(s, DOUBLE_MOVE, CondMove.getClearResult(s), val);
	    return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
	  } else {	      
	    // Cannonicalize by switching operands and fliping code.
	    OPT_Operand tmp = CondMove.getClearVal1(s);
	    CondMove.setVal1(s, CondMove.getClearVal2(s));
	    CondMove.setVal2(s, tmp);
	    CondMove.getCond(s).flipOperands();
	  }
	}
	if (CondMove.getTrueValue(s).similar(CondMove.getFalseValue(s))) {
	  OPT_Operand val = CondMove.getClearTrueValue(s);
	  Move.mutate(s, DOUBLE_MOVE, CondMove.getClearResult(s), val);
	  return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
	}
      }
      return UNCHANGED;
    case REF_COND_MOVE_opcode:
      {
	OPT_Operand val1 = CondMove.getVal1(s);
	if (val1.isConstant()) {
	  OPT_Operand val2 = CondMove.getVal2(s);
	  if (val2.isConstant()) {
	    // BOTH CONSTANTS: FOLD
	    boolean cond = CondMove.getCond(s).evaluate(val1, val2);
	    OPT_Operand val = 
	      cond ? CondMove.getClearTrueValue(s) : CondMove.getClearFalseValue(s);
	    Move.mutate(s, REF_MOVE, CondMove.getClearResult(s), val);
	    return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
	  } else {	      
	    // Cannonicalize by switching operands and fliping code.
	    OPT_Operand tmp = CondMove.getClearVal1(s);
	    CondMove.setVal1(s, CondMove.getClearVal2(s));
	    CondMove.setVal2(s, tmp);
	    CondMove.getCond(s).flipOperands();
	  }
	}
	if (CondMove.getTrueValue(s).similar(CondMove.getFalseValue(s))) {
	  OPT_Operand val = CondMove.getClearTrueValue(s);
	  Move.mutate(s, REF_MOVE, CondMove.getClearResult(s), val);
	  return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
	}
      }
      return UNCHANGED;
    case GUARD_COND_MOVE_opcode:
      {
	OPT_Operand val1 = CondMove.getVal1(s);
	if (val1.isConstant()) {
	  OPT_Operand val2 = CondMove.getVal2(s);
	  if (val2.isConstant()) {
	    // BOTH CONSTANTS: FOLD
	    boolean cond = CondMove.getCond(s).evaluate(val1, val2);
	    OPT_Operand val = 
	      cond ? CondMove.getClearTrueValue(s) : CondMove.getClearFalseValue(s);
	    Move.mutate(s, GUARD_MOVE, CondMove.getClearResult(s), val);
	    return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
	  } else {	      
	    // Cannonicalize by switching operands and fliping code.
	    OPT_Operand tmp = CondMove.getClearVal1(s);
	    CondMove.setVal1(s, CondMove.getClearVal2(s));
	    CondMove.setVal2(s, tmp);
	    CondMove.getCond(s).flipOperands();
	  }
	}
	if (CondMove.getTrueValue(s).similar(CondMove.getFalseValue(s))) {
	  OPT_Operand val = CondMove.getClearTrueValue(s);
	  Move.mutate(s, GUARD_MOVE, CondMove.getClearResult(s), val);
	  return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
	}
      }
      return UNCHANGED;
      ////////////////////
      // INT ALU operations
      ////////////////////
    case BOOLEAN_NOT_opcode:
      if (CF_INT) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isIntConstant()) {
	  // CONSTANT: FOLD
	  int val = op.asIntConstant().value;
	  if (val == 0) {
	    Move.mutate(s, INT_MOVE, Unary.getClearResult(s), I(1));
	  } else {
	    Move.mutate(s, INT_MOVE, Unary.getClearResult(s), I(0));
	  }
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case BOOLEAN_CMP_opcode:
      if (CF_INT) {
	OPT_Operand op1 = BooleanCmp.getVal1(s);
	if (op1.isConstant()) {
	  OPT_Operand op2 = BooleanCmp.getVal2(s);
	  if (op2.isConstant()) {
	    // BOTH CONSTANTS: FOLD
	    Move.mutate(s, INT_MOVE, BooleanCmp.getResult(s), 
			BooleanCmp.getCond(s).evaluate(op1, op2) ? I(1):I(0));
	    return MOVE_FOLDED;
	  } else {
	    // Cannonicalize by switching operands and fliping code.
	    OPT_Operand tmp = BooleanCmp.getClearVal1(s);
	    BooleanCmp.setVal1(s, BooleanCmp.getClearVal2(s));
	    BooleanCmp.setVal2(s, tmp);
	    BooleanCmp.getCond(s).flipOperands();
	  }
	}
      }
      return UNCHANGED;
    case INT_ADD_opcode:
      if (CF_INT) {
	canonicalizeCommutativeOperator(s);
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isIntConstant()) {
	  int val2 = op2.asIntConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isIntConstant()) {
	    // BOTH CONSTANTS: FOLD
	    int val1 = op1.asIntConstant().value;
	    Move.mutate(s, INT_MOVE, Binary.getClearResult(s), I(val1 + val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 0) {
	      Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case INT_AND_opcode:
      if (CF_INT) {
	canonicalizeCommutativeOperator(s);
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isIntConstant()) {
	  int val2 = op2.asIntConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isIntConstant()) {
	    // BOTH CONSTANTS: FOLD
	    int val1 = op1.asIntConstant().value;
	    Move.mutate(s, INT_MOVE, Binary.getClearResult(s), I(val1 & val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 0) {                  // x & 0 == 0
	      Move.mutate(s, INT_MOVE, Binary.getClearResult(s), I(0));
	      return MOVE_FOLDED;
	    }
	    if (val2 == -1) {                 // x & -1 == x & 0xffffffff == x
	      Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case INT_DIV_opcode:
      if (CF_INT) {
	OPT_Operand op2 = GuardedBinary.getVal2(s);
	if (op2.isIntConstant()) {
	  int val2 = op2.asIntConstant().value;
	  if (val2 == 0) {
	    // TODO: This instruction is actually unreachable.  
	    // There will be an INT_ZERO_CHECK
	    // guarding this instruction that will result in an 
	    // ArithmeticException.  We
	    // should probabbly just remove the INT_DIV as dead code.
	    return UNCHANGED;
	  }
	  OPT_Operand op1 = GuardedBinary.getVal1(s);
	  if (op1.isIntConstant()) {
	    // BOTH CONSTANTS: FOLD
	    int val1 = op1.asIntConstant().value;
	    Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
			I(val1/val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 1) {                  // x / 1 == x;
	      Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
			  GuardedBinary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case INT_MUL_opcode:
      if (CF_INT) {
	canonicalizeCommutativeOperator(s);
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isIntConstant()) {
	  int val2 = op2.asIntConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isIntConstant()) {
	    // BOTH CONSTANTS: FOLD
	    int val1 = op1.asIntConstant().value;
	    Move.mutate(s, INT_MOVE, Binary.getClearResult(s), I(val1*val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == -1) {                 // x * -1 == -x
	      Unary.mutate(s, INT_NEG, Binary.getClearResult(s), 
			   Binary.getClearVal1(s));
	      return REDUCED;
	    }
	    if (val2 == 0) {                  // x * 0 == 0
	      Move.mutate(s, INT_MOVE, Binary.getClearResult(s), I(0));
	      return MOVE_FOLDED;
	    }
	    if (val2 == 1) {                  // x * 1 == x
	      Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	    // x * c == x << (log c) if c is power of 2
	    int power = PowerOf2(val2);
	    if (power != -1) {
	      Binary.mutate(s, INT_SHL, Binary.getClearResult(s), 
			    Binary.getClearVal1(s), I(power));
	      return REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case INT_NEG_opcode:
      if (CF_INT) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isIntConstant()) {
	  // CONSTANT: FOLD
	  int val = op.asIntConstant().value;
	  Move.mutate(s, INT_MOVE, Unary.getClearResult(s), I(-val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case INT_NOT_opcode:
      if (CF_INT) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isIntConstant()) {
	  // CONSTANT: FOLD
	  int val = op.asIntConstant().value;
	  Move.mutate(s, INT_MOVE, Unary.getClearResult(s), I(~val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case INT_OR_opcode:
      if (CF_INT) {
	canonicalizeCommutativeOperator(s);
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isIntConstant()) {
	  int val2 = op2.asIntConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isIntConstant()) {
	    // BOTH CONSTANTS: FOLD
	    int val1 = op1.asIntConstant().value;
	    Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
			I(val1 | val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == -1) { // x | -1 == x | 0xffffffff == 0xffffffff == -1
	      Move.mutate(s, INT_MOVE, Binary.getClearResult(s), I(-1));
	      return MOVE_FOLDED;
	    }
	    if (val2 == 0) {                  // x | 0 == x
	      Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case INT_REM_opcode:
      if (CF_INT) {
	OPT_Operand op2 = GuardedBinary.getVal2(s);
	if (op2.isIntConstant()) {
	  int val2 = op2.asIntConstant().value;
	  if (val2 == 0) {
	    // TODO: This instruction is actually unreachable.  
	    // There will be an INT_ZERO_CHECK
	    // guarding this instruction that will result in an 
	    // ArithmeticException.  We
	    // should probabbly just remove the INT_REM as dead code.
	    return UNCHANGED;
	  }
	  OPT_Operand op1 = GuardedBinary.getVal1(s);
	  if (op1.isIntConstant()) {
	    // BOTH CONSTANTS: FOLD
	    int val1 = op1.asIntConstant().value;
	    Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
			I(val1%val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 1) {                  // x % 1 == 0
	      Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
			  I(0));
	      return MOVE_FOLDED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case INT_SHL_opcode:
      if (CF_INT) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isIntConstant()) {
	  int val2 = op2.asIntConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isIntConstant()) {
	    // BOTH CONSTANTS: FOLD
	    int val1 = op1.asIntConstant().value;
	    Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
			I(val1 << val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 0) {                  // x << 0 == x
	      Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case INT_SHR_opcode:
      if (CF_INT) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isIntConstant()) {
	  int val2 = op2.asIntConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isIntConstant()) {
	    // BOTH CONSTANTS: FOLD
	    int val1 = op1.asIntConstant().value;
	    Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
			I(val1 >> val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 0) {                  // x >> 0 == x
	      Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case INT_SUB_opcode:
      if (CF_INT) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isIntConstant()) {
	  int val2 = op2.asIntConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isIntConstant()) {
	    // BOTH CONSTANTS: FOLD
	    int val1 = op1.asIntConstant().value;
	    Move.mutate(s, INT_MOVE, Binary.getClearResult(s), I(val1 - val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 0) {                  // x - 0 == x
	      Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	    // x - c = x + -c
	    // prefer adds, since some architectures have addi but not subi
	    Binary.mutate(s, INT_ADD, Binary.getClearResult(s), 
			  Binary.getClearVal1(s), I(-val2));
	    return REDUCED;
	  }
	}
      }
      return UNCHANGED;
    case INT_USHR_opcode:
      if (CF_INT) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isIntConstant()) {
	  int val2 = op2.asIntConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isIntConstant()) {
	    // BOTH CONSTANTS: FOLD
	    int val1 = op1.asIntConstant().value;
	    Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
			I(val1 >>> val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 0) {                  // x >>> 0 == x
	      Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case INT_XOR_opcode:
      if (CF_INT) {
	canonicalizeCommutativeOperator(s);
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isIntConstant()) {
	  int val2 = op2.asIntConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isIntConstant()) {
	    // BOTH CONSTANTS: FOLD
	    int val1 = op1.asIntConstant().value;
	    Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
			I(val1 ^ val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == -1) {                 // x ^ -1 == x ^ 0xffffffff = ~x
	      Unary.mutate(s, INT_NOT, Binary.getClearResult(s), 
			   Binary.getClearVal1(s));
	      return REDUCED;
	    }
	    if (val2 == 0) {                  // x ^ 0 == x
	      Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
      ////////////////////
      // LONG ALU operations
      ////////////////////
    case LONG_ADD_opcode:
      if (CF_LONG) {
	canonicalizeCommutativeOperator(s);
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isLongConstant()) {
	  long val2 = op2.asLongConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isLongConstant()) {
	    // BOTH CONSTANTS: FOLD
	    long val1 = op1.asLongConstant().value;
	    Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(val1+val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 0L) {                 // x + 0 == x
	      Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case LONG_AND_opcode:
      if (CF_LONG) {
	canonicalizeCommutativeOperator(s);
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isLongConstant()) {
	  long val2 = op2.asLongConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isLongConstant()) {
	    // BOTH CONSTANTS: FOLD
	    long val1 = op1.asLongConstant().value;
	    Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
			LC(val1 & val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 0L) {                 // x & 0L == 0L
	      Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(0L));
	      return MOVE_FOLDED;
	    }
	    if (val2 == -1) {                 // x & -1L == x & 0xff...ff == x
	      Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case LONG_CMP_opcode:
      if (CF_LONG) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isLongConstant()) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isLongConstant()) {
	    // BOTH CONSTANTS: FOLD
	    long val1 = op1.asLongConstant().value;
	    long val2 = op2.asLongConstant().value;
	    int result = (val1 > val2) ? 1 : ((val1 == val2) ? 0 : -1);
	    Move.mutate(s, INT_MOVE, Binary.getClearResult(s), I(result));
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
    case LONG_DIV_opcode:
      if (CF_LONG) {
	OPT_Operand op2 = GuardedBinary.getVal2(s);
	if (op2.isLongConstant()) {
	  long val2 = op2.asLongConstant().value;
	  if (val2 == 0L) {
	    // TODO: This instruction is actually unreachable.  
	    // There will be a LONG_ZERO_CHECK
	    // guarding this instruction that will result in an 
	    // ArithmeticException.  We
	    // should probabbly just remove the LONG_DIV as dead code.
	    return UNCHANGED;
	  }
	  OPT_Operand op1 = GuardedBinary.getVal1(s);
	  if (op1.isLongConstant()) {
	    // BOTH CONSTANTS: FOLD
	    long val1 = op1.asLongConstant().value;
	    Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
			LC(val1/val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 1L) {                 // x / 1L == x
	      Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
			  GuardedBinary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case LONG_MUL_opcode:
      if (CF_LONG) {
	canonicalizeCommutativeOperator(s);
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isLongConstant()) {
	  long val2 = op2.asLongConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isLongConstant()) {
	    // BOTH CONSTANTS: FOLD
	    long val1 = op1.asLongConstant().value;
	    Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
			LC(val1*val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == -1L) {                // x * -1 == -x
	      Move.mutate(s, LONG_NEG, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return REDUCED;
	    }
	    if (val2 == 0L) {                 // x * 0L == 0L
	      Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(0L));
	      return MOVE_FOLDED;
	    }
	    if (val2 == 1L) {                 // x * 1L == x
	      Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case LONG_NEG_opcode:
      if (CF_LONG) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isLongConstant()) {
	  // CONSTANT: FOLD
	  long val = op.asLongConstant().value;
	  Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC(-val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case LONG_NOT_opcode:
      if (CF_LONG) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isLongConstant()) {
	  long val = op.asLongConstant().value;
	  // CONSTANT: FOLD
	  Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(~val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case LONG_OR_opcode:
      if (CF_LONG) {
	canonicalizeCommutativeOperator(s);
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isLongConstant()) {
	  long val2 = op2.asLongConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isLongConstant()) {
	    // BOTH CONSTANTS: FOLD
	    long val1 = op1.asLongConstant().value;
	    Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
			LC(val1 | val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 0L) {                 // x | 0L == x
	      Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	    if (val2 == -1L) { // x | -1L == x | 0xff..ff == 0xff..ff == -1L
	      Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(-1L));
	      return MOVE_FOLDED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case LONG_REM_opcode:
      if (CF_LONG) {
	OPT_Operand op2 = GuardedBinary.getVal2(s);
	if (op2.isLongConstant()) {
	  long val2 = op2.asLongConstant().value;
	  if (val2 == 0L) {
	    // TODO: This instruction is actually unreachable.  
	    // There will be a LONG_ZERO_CHECK
	    // guarding this instruction that will result in an 
	    // ArithmeticException.  We
	    // should probabbly just remove the LONG_REM as dead code.
	    return UNCHANGED;
	  }
	  OPT_Operand op1 = GuardedBinary.getVal1(s);
	  if (op1.isLongConstant()) {
	    // BOTH CONSTANTS: FOLD
	    long val1 = op1.asLongConstant().value;
	    Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
			LC(val1%val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 1L) {                 // x % 1L == 0
	      Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
			  LC(0));
	      return MOVE_FOLDED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case LONG_SHL_opcode:
      if (CF_LONG) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isIntConstant()) {
	  int val2 = op2.asIntConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isLongConstant()) {
	    // BOTH CONSTANTS: FOLD
	    long val1 = op1.asLongConstant().value;
	    Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
			LC(val1 << val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 0) {                  // x << 0 == x
	      Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case LONG_SHR_opcode:
      if (CF_LONG) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isIntConstant()) {
	  int val2 = op2.asIntConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isLongConstant()) {
	    // BOTH CONSTANTS: FOLD
	    long val1 = op1.asLongConstant().value;
	    Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
			LC(val1 >> val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 0) {                  // x >> 0L == x
	      Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case LONG_SUB_opcode:
      if (CF_LONG) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isLongConstant()) {
	  long val2 = op2.asLongConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isLongConstant()) {
	    // BOTH CONSTANTS: FOLD
	    long val1 = op1.asLongConstant().value;
	    Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(val1-val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 0L) {                 // x - 0 == x
	      Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                          Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case LONG_USHR_opcode:
      if (CF_LONG) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isIntConstant()) {
	  int val2 = op2.asIntConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isLongConstant()) {
	    // BOTH CONSTANTS: FOLD
	    long val1 = op1.asLongConstant().value;
	    Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
			LC(val1 >>> val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == 0) {                  // x >>> 0L == x
	      Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
    case LONG_XOR_opcode:
      if (CF_LONG) {
	canonicalizeCommutativeOperator(s);
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isLongConstant()) {
	  long val2 = op2.asLongConstant().value;
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isLongConstant()) {
	    // BOTH CONSTANTS: FOLD
	    long val1 = op1.asLongConstant().value;
	    Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
			LC(val1 ^ val2));
	    return MOVE_FOLDED;
	  } else {
	    // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
	    if (val2 == -1L) {                // x ^ -1L == x ^ 0xff..ff = ~x
	      Unary.mutate(s, LONG_NOT, Binary.getClearResult(s), 
			   Binary.getClearVal1(s));
	      return REDUCED;
	    }
	    if (val2 == 0L) {                 // x ^ 0L == x
	      Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
			  Binary.getClearVal1(s));
	      return MOVE_REDUCED;
	    }
	  }
	}
      }
      return UNCHANGED;
      ////////////////////
      // FLOAT ALU operations
      ////////////////////
    case FLOAT_ADD_opcode:
      if (CF_FLOAT) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isFloatConstant()) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isFloatConstant()) {
	    // BOTH CONSTANTS: FOLD
	    float val1 = op1.asFloatConstant().value;
	    float val2 = op2.asFloatConstant().value;
	    Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), 
			FC(val1 + val2));
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
    case FLOAT_CMPG_opcode:
      if (CF_INT) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isFloatConstant()) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isFloatConstant()) {
	    // BOTH CONSTANTS: FOLD
	    float val1 = op1.asFloatConstant().value;
	    float val2 = op2.asFloatConstant().value;
	    int result = (val1 < val2) ? -1 : ((val1 == val2) ? 0 : 1);
	    Move.mutate(s, INT_MOVE, Binary.getClearResult(s), I(result));
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
    case FLOAT_CMPL_opcode:
      if (CF_INT) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isFloatConstant()) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isFloatConstant()) {
	    // BOTH CONSTANTS: FOLD
	    float val1 = op1.asFloatConstant().value;
	    float val2 = op2.asFloatConstant().value;
	    int result = (val1 > val2) ? 1 : ((val1 == val2) ? 0 : -1);
	    Move.mutate(s, INT_MOVE, Binary.getClearResult(s), I(result));
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
    case FLOAT_DIV_opcode:
      if (CF_FLOAT) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isFloatConstant()) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isFloatConstant()) {
	    // BOTH CONSTANTS: FOLD
	    float val1 = op1.asFloatConstant().value;
	    float val2 = op2.asFloatConstant().value;
	    Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), 
			FC(val1/val2));
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
    case FLOAT_MUL_opcode:
      if (CF_FLOAT) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isFloatConstant()) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isFloatConstant()) {
	    // BOTH CONSTANTS: FOLD
	    float val1 = op1.asFloatConstant().value;
	    float val2 = op2.asFloatConstant().value;
	    Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), 
			FC(val1*val2));
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
    case FLOAT_NEG_opcode:
      if (CF_FLOAT) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isFloatConstant()) {
	  // CONSTANT: FOLD
	  float val = op.asFloatConstant().value;
	  Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), FC(-val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case FLOAT_REM_opcode:
      if (CF_FLOAT) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isFloatConstant()) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isFloatConstant()) {
	    // BOTH CONSTANTS: FOLD
	    float val1 = op1.asFloatConstant().value;
	    float val2 = op2.asFloatConstant().value;
	    Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), 
			FC(val1%val2));
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
    case FLOAT_SUB_opcode:
      if (CF_FLOAT) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isFloatConstant()) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isFloatConstant()) {
	    // BOTH CONSTANTS: FOLD
	    float val1 = op1.asFloatConstant().value;
	    float val2 = op2.asFloatConstant().value;
	    Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), 
			FC(val1 - val2));
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
      ////////////////////
      // DOUBLE ALU operations
      ////////////////////
    case DOUBLE_ADD_opcode:
      if (CF_DOUBLE) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isDoubleConstant()) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isDoubleConstant()) {
	    // BOTH CONSTANTS: FOLD
	    double val1 = op1.asDoubleConstant().value;
	    double val2 = op2.asDoubleConstant().value;
	    Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), 
			DC(val1 + val2));
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
    case DOUBLE_CMPG_opcode:
      if (CF_INT) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isDoubleConstant()) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isDoubleConstant()) {
	    // BOTH CONSTANTS: FOLD
	    double val1 = op1.asDoubleConstant().value;
	    double val2 = op2.asDoubleConstant().value;
	    int result = (val1 < val2) ? -1 : ((val1 == val2) ? 0 : 1);
	    Move.mutate(s, INT_MOVE, Binary.getClearResult(s), I(result));
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
    case DOUBLE_CMPL_opcode:
      if (CF_INT) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isDoubleConstant()) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isDoubleConstant()) {
	    // BOTH CONSTANTS: FOLD
	    double val1 = op1.asDoubleConstant().value;
	    double val2 = op2.asDoubleConstant().value;
	    int result = (val1 > val2) ? 1 : ((val1 == val2) ? 0 : -1);
	    Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), I(result));
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
    case DOUBLE_DIV_opcode:
      if (CF_DOUBLE) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isDoubleConstant()) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isDoubleConstant()) {
	    // BOTH CONSTANTS: FOLD
	    double val1 = op1.asDoubleConstant().value;
	    double val2 = op2.asDoubleConstant().value;
	    Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), 
			DC(val1/val2));
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
    case DOUBLE_MUL_opcode:
      if (CF_DOUBLE) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isDoubleConstant()) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isDoubleConstant()) {
	    // BOTH CONSTANTS: FOLD
	    double val1 = op1.asDoubleConstant().value;
	    double val2 = op2.asDoubleConstant().value;
	    Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), 
			DC(val1*val2));
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
    case DOUBLE_NEG_opcode:
      if (CF_DOUBLE) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isDoubleConstant()) {
	  // CONSTANT: FOLD
	  double val = op.asDoubleConstant().value;
	  Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), DC(-val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case DOUBLE_REM_opcode:
      if (CF_DOUBLE) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isDoubleConstant()) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isDoubleConstant()) {
	    // BOTH CONSTANTS: FOLD
	    double val1 = op1.asDoubleConstant().value;
	    double val2 = op2.asDoubleConstant().value;
	    Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), 
			DC(val1%val2));
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
    case DOUBLE_SUB_opcode:
      if (CF_DOUBLE) {
	OPT_Operand op2 = Binary.getVal2(s);
	if (op2.isDoubleConstant()) {
	  OPT_Operand op1 = Binary.getVal1(s);
	  if (op1.isDoubleConstant()) {
	    // BOTH CONSTANTS: FOLD
	    double val1 = op1.asDoubleConstant().value;
	    double val2 = op2.asDoubleConstant().value;
	    Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), 
			DC(val1 - val2));
	    return MOVE_FOLDED;
	  }
	}
      }
      return UNCHANGED;
      ////////////////////
      // CONVERSION operations
      ////////////////////
    case DOUBLE_2FLOAT_opcode:
      if (CF_FLOAT) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isDoubleConstant()) {
	  // CONSTANT: FOLD
	  double val = op.asDoubleConstant().value;
	  Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC((float)val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case DOUBLE_2INT_opcode:
      if (CF_INT) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isDoubleConstant()) {
	  // CONSTANT: FOLD
	  double val = op.asDoubleConstant().value;
	  Move.mutate(s, INT_MOVE, Unary.getClearResult(s), I((int)val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case DOUBLE_2LONG_opcode:
      if (CF_LONG) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isDoubleConstant()) {
	  // CONSTANT: FOLD
	  double val = op.asDoubleConstant().value;
	  Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC((long)val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case DOUBLE_AS_LONG_BITS_opcode:
      if (CF_LONG) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isDoubleConstant()) {
	  // CONSTANT: FOLD
	  double val = op.asDoubleConstant().value;
	  Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), 
		      LC(Double.doubleToLongBits(val)));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case INT_2DOUBLE_opcode:
      if (CF_DOUBLE) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isIntConstant()) {
	  // CONSTANT: FOLD
	  int val = op.asIntConstant().value;
	  Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), 
		      DC((double)val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case INT_2BYTE_opcode:
      if (CF_INT) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isIntConstant()) {
	  // CONSTANT: FOLD
	  int val = op.asIntConstant().value;
	  Move.mutate(s, INT_MOVE, Unary.getClearResult(s), I((byte)val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case INT_2USHORT_opcode:
      if (CF_INT) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isIntConstant()) {
	  // CONSTANT: FOLD
	  int val = op.asIntConstant().value;
	  Move.mutate(s, INT_MOVE, Unary.getClearResult(s), I((char)val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case INT_2FLOAT_opcode:
      if (CF_FLOAT) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isIntConstant()) {
	  // CONSTANT: FOLD
	  int val = op.asIntConstant().value;
	  Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC((float)val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case INT_2LONG_opcode:
      if (CF_LONG) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isIntConstant()) {
	  // CONSTANT: FOLD
	  int val = op.asIntConstant().value;
	  Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC((long)val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case INT_2SHORT_opcode:
      if (CF_INT) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isIntConstant()) {
	  // CONSTANT: FOLD
	  int val = op.asIntConstant().value;
	  Move.mutate(s, INT_MOVE, Unary.getClearResult(s), I((short)val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case INT_BITS_AS_FLOAT_opcode:
      if (CF_FLOAT) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isIntConstant()) {
	  // CONSTANT: FOLD
	  int val = op.asIntConstant().value;
	  Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), 
		      FC(Float.intBitsToFloat(val)));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case FLOAT_2DOUBLE_opcode:
      if (CF_DOUBLE) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isFloatConstant()) {
	  // CONSTANT: FOLD
	  float val = op.asFloatConstant().value;
	  Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), 
		      DC((double)val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case FLOAT_2INT_opcode:
      if (CF_INT) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isFloatConstant()) {
	  // CONSTANT: FOLD
	  float val = op.asFloatConstant().value;
	  Move.mutate(s, INT_MOVE, Unary.getClearResult(s), I((int)val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case FLOAT_2LONG_opcode:
      if (CF_LONG) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isFloatConstant()) {
	  // CONSTANT: FOLD
	  float val = op.asFloatConstant().value;
	  Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC((long)val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case FLOAT_AS_INT_BITS_opcode:
      if (CF_INT) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isFloatConstant()) {
	  // CONSTANT: FOLD
	  float val = op.asFloatConstant().value;
	  Move.mutate(s, INT_MOVE, Unary.getClearResult(s), 
		      I(Float.floatToIntBits(val)));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case LONG_2FLOAT_opcode:
      if (CF_FLOAT) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isLongConstant()) {
	  // CONSTANT: FOLD
	  long val = op.asLongConstant().value;
	  Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC((float)val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case LONG_2INT_opcode:
      if (CF_INT) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isLongConstant()) {
	  // CONSTANT: FOLD
	  long val = op.asLongConstant().value;
	  Move.mutate(s, INT_MOVE, Unary.getClearResult(s), I((int)val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case LONG_2DOUBLE_opcode:
      if (CF_DOUBLE) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isLongConstant()) {
	  // CONSTANT: FOLD
	  long val = op.asLongConstant().value;
	  Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), 
		      DC((double)val));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    case LONG_BITS_AS_DOUBLE_opcode:
      if (CF_DOUBLE) {
	OPT_Operand op = Unary.getVal(s);
	if (op.isLongConstant()) {
	  // CONSTANT: FOLD
	  long val = op.asLongConstant().value;
	  Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), 
		      DC(Double.longBitsToDouble(val)));
	  return MOVE_FOLDED;
	}
      }
      return UNCHANGED;
    default:
      return UNCHANGED;
    }
  }

  /**
   * To reduce the number of conditions to consider, we 
   * transform all commutative
   * operators to a canoncial form.  The following forms are considered
   * canonical:
   * <ul>
   * <li> <code> Reg = Reg <op> Reg </code>
   * <li> <code> Reg = Reg <op> Constant </code>
   * <li> <code> Reg = Constant <op> Constant </code>
   * </ul>
   */
  private static void canonicalizeCommutativeOperator(OPT_Instruction instr) {
    if (Binary.getVal1(instr).isConstant()) {
      OPT_Operand tmp = Binary.getClearVal1(instr);
      Binary.setVal1(instr, Binary.getClearVal2(instr));
      Binary.setVal2(instr, tmp);
    }
  }

  /**
   * Compute 2 raised to the power v, 0 <= v <= 31
   */
  private static int PowerOf2(int v) {
    int i = 31;
    int power = -1;
    for (; v != 0; v = v << 1, i--)
      if (v < 0) {
        if (power == -1)
          power = i; 
        else 
          return -1;
      }
    return power;
  }
}
