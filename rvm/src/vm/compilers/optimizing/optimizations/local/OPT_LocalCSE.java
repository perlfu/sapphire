/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.*;
import instructionFormats.*;

/**
 * Perform local common-subexpression elimination for a factored basic
 * block.
 * <ul>
 *   <li> Note: this module also performs scalar replacement of loads 
 *   <li> Note: this module also performs elimination of redundant 
 *         nullchecks, boundchecks, and zero checks.
 * </ul>
 * Algorithm: Muchnick pp.379-385
 *
 * @author Stephen Fink
 * @modified Dave Grove (elimination of checks, 
 * partially based on OPT_LocalBoundsCheck)
 */
public class OPT_LocalCSE extends OPT_CompilerPhase implements OPT_Operators {
  /** Is the tranformation being performed on LIR? */
  boolean isLIR;

  /**
   * @param   isLIR is the IR in LIR?
   */
  OPT_LocalCSE (boolean isLIR) {
    this.isLIR = isLIR;
  }

  final boolean shouldPerform (OPT_Options options) {
    // only perform when the following options are set.
    return  options.LOCAL_CSE || options.LOCAL_SCALAR_REPLACEMENT;
  }

  final String getName () {
    return  "Local CSE";
  }

  final boolean printingEnabled (OPT_Options options, boolean before) {
    return  false;
  }
  static final boolean debug = false;

  /**
   * Perform Local CSE for a method.
   * 
   * @param ir the IR to optimize
   */
  public void perform (OPT_IR ir) {
    // iterate over each basic block
    for (OPT_BasicBlock bb = ir.firstBasicBlockInCodeOrder(); bb != null; 
        bb = bb.nextBasicBlockInCodeOrder()) {
      if (!bb.isEmpty())
        optimizeBasicBlock(ir, bb, ir.options);
    }
    if (debug)
      OPT_Compiler.printInstructions(ir, "CSE");
  }

  /**
   * Perform Local CSE for a basic block.
   *
   * @param ir the method's ir
   * @param bb the basic block
   * @param options controlling compiler options
   */
  private void optimizeBasicBlock(OPT_IR ir, OPT_BasicBlock bb, 
				  OPT_Options options) {
    OPT_AvExCache cache = new OPT_AvExCache();
    // iterate over all instructions in the basic block
    for (OPT_Instruction inst = bb.firstRealInstruction(), 
	   sentinel = bb.lastInstruction(), 
	   nextInstr = null; 
	 inst != sentinel; 
	 inst = nextInstr) {
      nextInstr = inst.nextInstructionInCodeOrder(); // cache before we 
                                                     // mutate prev/next links
      // 1. try and replace this instruction according to
      // available expressions in the cache, and update cache
      // accordingly.
      if (options.LOCAL_SCALAR_REPLACEMENT) {
        if (isLoadInstruction(inst)) {
          loadHelper(ir, cache, inst);
        } else if (isStoreInstruction(inst)) {
          storeHelper(cache, inst);
        }
      }
      if (options.LOCAL_CSE) {
        if (isUnaryOrBinary(inst)) {
          expressionHelper(ir, cache, inst);
	}
      }
      if (options.LOCAL_CHECK) {
        if (isCheck(inst)) {
          checkHelper(ir, cache, inst);
        } else if (isTypeCheck(inst)) {
	  typeCheckHelper(ir, cache, inst);
	}
      }
      // 2. update the cache according to which expressions this
      // instruction kills
      cache.eliminate(inst, options);
      if (options.LOCAL_SCALAR_REPLACEMENT) {
        // CALL instructions and synchronizations KILL all memory
        // locations!
        if (Call.conforms(inst) || CallSpecial.conforms(inst) || 
            isSynchronizing(inst)
            || inst.isDynamicLinkingPoint()) {
          cache.invalidateAllLoads();
        }
      }
    }
  }

  /**
   * Is a given instruction a "load" for scalar replacement purposes ?
   */
  public static boolean isLoadInstruction (OPT_Instruction s) {
    return  GetField.conforms(s) || GetStatic.conforms(s);
  }

  /**
   * Is a given instruction a "store" for scalar replacement purposes ?
   */
  public static boolean isStoreInstruction (OPT_Instruction s) {
    return  PutField.conforms(s) || PutStatic.conforms(s);
  }

  /** 
   * Does the instruction compute a unary or binary expression?
   *
   * @param inst the instruction in question
   * @return true or false, as appropriate
   */
  private boolean isUnaryOrBinary (OPT_Instruction inst) {
    return  Unary.conforms(inst) || GuardedUnary.conforms(inst) || 
        Binary.conforms(inst)
        || GuardedBinary.conforms(inst);
  }

  /** 
   * Is the given instruction a check instruction?
   *
   * @param inst the instruction in question
   * @return true or false, as appropriate
   */
  private boolean isCheck (OPT_Instruction inst) {
    switch (inst.getOpcode()) {
      case NULL_CHECK_opcode:case BOUNDS_CHECK_opcode:
      case INT_ZERO_CHECK_opcode:case LONG_ZERO_CHECK_opcode:
        return  true;
      case TRAP_IF_opcode:
        OPT_TrapCodeOperand tc = TrapIf.getTCode(inst);
        return  tc.isNullPtr() || tc.isArrayBounds() || tc.isDivByZero();
      default:
        return  false;
    }
  }

  private boolean isTypeCheck(OPT_Instruction inst) {
    return TypeCheck.conforms(inst);
  }


  /**
   * Process a load instruction
   *
   * @param ir the containing IR object.
   * @param cache the cache of available expressions
   * @param inst the instruction begin processed
   */
  private void loadHelper(OPT_IR ir, OPT_AvExCache cache, 
			  OPT_Instruction inst) {
    // look up the expression in the cache
    OPT_AvailableExpression ae = cache.find(inst);
    if (ae != null) {
      OPT_RegisterOperand dest = ResultCarrier.getClearResult(inst);
      if (ae.tmp == null) {
        // (1) generate a new temporary, and store in the AE cache
	OPT_RegisterOperand newRes = ir.regpool.makeTemp(dest.type);
	ae.tmp = newRes.register;
        // (2) get the CSE value into newRes
        if (ae.isLoad()) {
          // the first appearance was a load.
	  // Modify the first load to assign its result to a new temporary
	  // and then insert a move from the new temporary to the old result 
	  // after the mutated first load.
	  OPT_RegisterOperand res = ResultCarrier.getClearResult(ae.inst);
	  ResultCarrier.setResult(ae.inst, newRes);
	  ae.inst.insertAfter(Move.create(getMoveOp(res), res, newRes.copyD2U()));
        } else {
	  // the first appearance was a store.
	  // Insert a move that assigns the value to newRes before
	  // the store instruction.
          OPT_Operand value;
	  if (PutStatic.conforms(ae.inst))
	    value = PutStatic.getValue(ae.inst);
	  else 
	    value = PutField.getValue(ae.inst);
	  ae.inst.insertBefore(Move.create(getMoveOp(newRes), newRes, value.copy()));
	}
	// (3) replace second load with a move from the new temporary
	Move.mutate(inst, getMoveOp(dest), dest, newRes.copyD2U());
      } else {
        // already have a temp. replace the load with a move
	OPT_RegisterOperand newRes = new OPT_RegisterOperand(ae.tmp, dest.type);
	Move.mutate(inst, getMoveOp(dest), dest, newRes);
      }
    } else {
      // did not find a match: insert new entry in cache
      cache.insert(inst);
    }
  }


  /**
   * Process a store instruction
   *
   * @param cache the cache of available expressions
   * @param inst the instruction begin processed
   */
  private void storeHelper (OPT_AvExCache cache, OPT_Instruction inst) {
    // look up the expression in the cache
    OPT_AvailableExpression ae = cache.find(inst);
    if (ae == null) {
      // did not find a match: insert new entry in cache
      cache.insert(inst);
    }
  }


  /**
   * Process a unary or binary expression.
   *
   * @param ir the containing IR object
   * @param cache the cache of available expressions
   * @param inst the instruction begin processed
   */
  private void expressionHelper(OPT_IR ir, OPT_AvExCache cache, 
				OPT_Instruction inst) {
    // look up the expression in the cache
    OPT_AvailableExpression ae = cache.find(inst);
    if (ae != null) {
      OPT_RegisterOperand dest = ResultCarrier.getClearResult(inst);
      if (ae.tmp == null) {
        // (1) generate a new temporary, and store in the AE cache
	OPT_RegisterOperand newRes = ir.regpool.makeTemp(dest.type);
	ae.tmp = newRes.register;
	// (2) Modify ae.inst to assign its result to the new temporary
	// and then insert a move from the new temporary to the old result
	// of ae.inst after ae.inst.
	OPT_RegisterOperand res = ResultCarrier.getClearResult(ae.inst);
	ResultCarrier.setResult(ae.inst, newRes);
	ae.inst.insertAfter(Move.create(getMoveOp(res), res, newRes.copyD2U()));
	// (3) replace inst with a move from the new temporary
	Move.mutate(inst, getMoveOp(dest), dest, newRes.copyD2U());
      } else {
        // already have a temp. replace inst with a move
	OPT_RegisterOperand newRes = new OPT_RegisterOperand(ae.tmp, dest.type);
	Move.mutate(inst, getMoveOp(dest), dest, newRes);
      } 
    } else {
      // did not find a match: insert new entry in cache
      cache.insert(inst);
    }
  }


  /**
   * Process a check instruction
   *
   * @param cache the cache of available expressions
   * @param inst the instruction begin processed
   * @param e the controlling instruction enumerator
   */
  private void checkHelper (OPT_IR ir, OPT_AvExCache cache, 
			    OPT_Instruction inst) {
    // look up the check in the cache
    OPT_AvailableExpression ae = cache.find(inst);
    if (ae != null) {
      OPT_RegisterOperand dest = GuardResultCarrier.getClearGuardResult(inst);
      if (ae.tmp == null) {
	// generate a new temporary, and store in the AE cache
	OPT_RegisterOperand newRes = ir.regpool.makeTemp(dest.type);
	ae.tmp = newRes.register;
	// (2) Modify ae.inst to assign its guard result to the new temporary
	// and then insert a guard move from the new temporary to the 
	// old guard result of ae.inst after ae.inst.
	OPT_RegisterOperand res = GuardResultCarrier.getClearGuardResult(ae.inst);
	GuardResultCarrier.setGuardResult(ae.inst, newRes);
	ae.inst.insertAfter(Move.create(GUARD_MOVE, res, newRes.copyD2U()));
	// (3) replace inst with a move from the new temporary
	Move.mutate(inst, GUARD_MOVE, dest, newRes.copyD2U());
      } else {
	// already have a temp. replace inst with a guard move
	OPT_RegisterOperand newRes = new OPT_RegisterOperand(ae.tmp, dest.type);
	Move.mutate(inst, GUARD_MOVE, dest, newRes);
      }
    } else {
      // did not find a match: insert new entry in cache
      cache.insert(inst);
    }
  }


  /**
   * Process a type check instruction
   *
   * @param cache the cache of available expressions
   * @param inst the instruction begin processed
   * @param e the controlling instruction enumerator
   */
  private void typeCheckHelper(OPT_IR ir, OPT_AvExCache cache, 
			       OPT_Instruction inst) {
    // look up the check in the cache
    OPT_AvailableExpression ae = cache.find(inst);
    if (ae != null) {
      // it's a duplicate; blow it away.
      inst.remove();
    } else {
      // did not find a match: insert new entry in cache
      cache.insert(inst);
    }
  }


  private OPT_Operator getMoveOp(OPT_RegisterOperand r) {
    return OPT_IRTools.getMoveOp(r.type);
  }


  /** 
   * Is this a synchronizing instruction?
   *
   * @param inst the instruction in question
   */
  private static boolean isSynchronizing (OPT_Instruction inst) {
    switch (inst.getOpcode()) {
      case MONITORENTER_opcode:case MONITOREXIT_opcode:
      case READ_CEILING_opcode:case WRITE_FLOOR_opcode:
        return  true;
      default:
        return  false;
    }
  }

}


/** Implements a cache of OPT_Available Expressions */
class OPT_AvExCache
    implements OPT_Operators {

  /** 
   * Default Constructor
   */
  OPT_AvExCache () {
    cache = new Vector(0);
  }

  /**
   * Find and return a matching available expression
   *
   * @param inst the instruction to match
   * @return the matching AE if found, null otherwise
   */
  public OPT_AvailableExpression find (OPT_Instruction inst) {
    OPT_Operator opr = inst.operator();
    OPT_Operand op1 = null;
    OPT_Operand op2 = null;
    OPT_Operand op3 = null;
    OPT_LocationOperand location = null;
    if (GetField.conforms(inst)) {
      op1 = GetField.getRef(inst);
      location = GetField.getLocation(inst);
    } else if (GetStatic.conforms(inst)) {
      location = GetStatic.getLocation(inst);
    } else if (PutField.conforms(inst)) {
      op1 = PutField.getRef(inst);
      location = PutField.getLocation(inst);
    } else if (PutStatic.conforms(inst)) {
      location = PutStatic.getLocation(inst);
    } else if (Unary.conforms(inst)) {
      op1 = Unary.getVal(inst);
    } else if (GuardedUnary.conforms(inst)) {
      op1 = GuardedUnary.getVal(inst);
    } else if (Binary.conforms(inst)) {
      op1 = Binary.getVal1(inst);
      op2 = Binary.getVal2(inst);
    } else if (GuardedBinary.conforms(inst)) {
      op1 = GuardedBinary.getVal1(inst);
      op2 = GuardedBinary.getVal2(inst);
    } else if (Move.conforms(inst)) {
      op1 = Move.getVal(inst);
    } else if (NullCheck.conforms(inst)) {
      op1 = NullCheck.getRef(inst);
    } else if (ZeroCheck.conforms(inst)) {
      op1 = ZeroCheck.getValue(inst);
    } else if (BoundsCheck.conforms(inst)) {
      op1 = BoundsCheck.getRef(inst);
      op2 = BoundsCheck.getIndex(inst);
    } else if (TrapIf.conforms(inst)) {
      op1 = TrapIf.getVal1(inst);
      op2 = TrapIf.getVal2(inst);
      op3 = TrapIf.getTCode(inst);
    } else if (TypeCheck.conforms(inst)) {
      op1 = TypeCheck.getRef(inst);
      op2 = TypeCheck.getType(inst);
    } else 
      throw  new OPT_OptimizingCompilerException("Unsupported type " + 
						 inst);
    OPT_AvailableExpression ae = 
      new OPT_AvailableExpression(inst, opr, op1, op2, op3, location, null);
    int index = cache.indexOf(ae);
    if (index == -1)
      return  null;
    return  ((OPT_AvailableExpression)cache.elementAt(index));
  }

  /**
   * Insert a new available expression in the cache
   *
   * @param inst the instruction that defines the AE
   */
  public void insert (OPT_Instruction inst) {
    OPT_Operator opr = inst.operator();
    OPT_Operand op1 = null;
    OPT_Operand op2 = null;
    OPT_Operand op3 = null;
    OPT_LocationOperand location = null;
    if (GetField.conforms(inst)) {
      op1 = GetField.getRef(inst);
      location = GetField.getLocation(inst);
    } else if (GetStatic.conforms(inst)) {
      location = GetStatic.getLocation(inst);
    } else if (PutField.conforms(inst)) {
      op1 = PutField.getRef(inst);
      location = PutField.getLocation(inst);
    } else if (PutStatic.conforms(inst)) {
      location = PutStatic.getLocation(inst);
    } else if (Unary.conforms(inst)) {
      op1 = Unary.getVal(inst);
    } else if (GuardedUnary.conforms(inst)) {
      op1 = GuardedUnary.getVal(inst);
    } else if (Binary.conforms(inst)) {
      op1 = Binary.getVal1(inst);
      op2 = Binary.getVal2(inst);
    } else if (GuardedBinary.conforms(inst)) {
      op1 = GuardedBinary.getVal1(inst);
      op2 = GuardedBinary.getVal2(inst);
    } else if (Move.conforms(inst)) {
      op1 = Move.getVal(inst);
    } else if (NullCheck.conforms(inst)) {
      op1 = NullCheck.getRef(inst);
    } else if (ZeroCheck.conforms(inst)) {
      op1 = ZeroCheck.getValue(inst);
    } else if (BoundsCheck.conforms(inst)) {
      op1 = BoundsCheck.getRef(inst);
      op2 = BoundsCheck.getIndex(inst);
    } else if (TrapIf.conforms(inst)) {
      op1 = TrapIf.getVal1(inst);
      op2 = TrapIf.getVal2(inst);
      op3 = TrapIf.getTCode(inst);
    } else if (TypeCheck.conforms(inst)) {
      op1 = TypeCheck.getRef(inst);
      op2 = TypeCheck.getType(inst);
    }else 
      throw  new OPT_OptimizingCompilerException("Unsupported type " + 
						 inst);
    OPT_AvailableExpression ae = 
      new OPT_AvailableExpression(inst, opr, 
				  op1, op2, op3, location, null);
    cache.addElement(ae);
  }

  /**
   * Eliminate all AE tuples that contain a given operand
   *
   * @param op the operand in question
   */
  private void eliminate (OPT_RegisterOperand op) {
    int i = 0;
    while (i < cache.size()) {
      OPT_AvailableExpression ae = (OPT_AvailableExpression)cache.elementAt(i);
      OPT_Operand opx = ae.op1;
      if ((opx != null) && (opx instanceof OPT_RegisterOperand) && 
          (((OPT_RegisterOperand)opx).register == op.register)) {
        cache.removeElementAt(i);
        continue;               // don't increment i, since we removed 
      }
      opx = ae.op2;
      if ((opx != null) && (opx instanceof OPT_RegisterOperand) && 
          (((OPT_RegisterOperand)opx).register == op.register)) {
        cache.removeElementAt(i);
        continue;               // don't increment i, since we removed
      }
      opx = ae.op3;
      if ((opx != null) && (opx instanceof OPT_RegisterOperand) && 
          (((OPT_RegisterOperand)opx).register == op.register)) {
        cache.removeElementAt(i);
        continue;               // don't increment i, since we removed
      }
      i++;
    }
  }

  /**
   * Eliminate all AE tuples that are killed by a given instruction
   *
   * @param s the store instruction
   * @param options controlling compiler options
   */
  public void eliminate (OPT_Instruction s, OPT_Options options) {
    int i = 0;
    // first kill all registers that this instruction defs
    for (OPT_OperandEnumeration defs = s.getDefs(); defs.hasMoreElements();) {
      // first KILL any registers this instruction DEFS
      OPT_Operand def = defs.next();
      if (def instanceof OPT_RegisterOperand) {
        eliminate((OPT_RegisterOperand)def);
      }
    }
    // if doing scalar replacement, eliminate all memory locations
    // killed by stores
    if (options.LOCAL_SCALAR_REPLACEMENT) {
      boolean isVolatileLoad = OPT_RVMIRTools.mayBeVolatileFieldLoad(s);
      if (OPT_LocalCSE.isStoreInstruction(s) || isVolatileLoad ||
          (options.READS_KILL && OPT_LocalCSE.isLoadInstruction(s))) {
        // sLocation holds the location killed by this instruction
        OPT_LocationOperand sLocation = LocationCarrier.getLocation(s);
        // walk through the cache and invalidate any killed locations
        while (i < cache.size()) {
          OPT_AvailableExpression ae = (OPT_AvailableExpression)
              cache.elementAt(i);
          if (ae.inst != s) {   // a store instruction doesn't kill itself 
            boolean killIt = false;
            if (ae.isLoadOrStore()) {
              if ((sLocation == null) && (ae.location == null)) {
                // !TODO: is this too conservative??
                killIt = true;
              }
              if ((sLocation != null) && (ae.location != null)) {
                killIt = OPT_LocationOperand.mayBeAliased(sLocation, 
                    ae.location);
              }
            }
            if (killIt) {
              cache.removeElementAt(i);
              continue;         // don't increment i, since we removed 
            }
          }
          i++;
        }
      }
    }
  }

  /**
   * Eliminate all AE tuples that cache ANY memory location.
   */
  public void invalidateAllLoads () {
    int i = 0;
    while (i < cache.size()) {
      OPT_AvailableExpression ae = (OPT_AvailableExpression)cache.elementAt(i);
      if (ae.isLoadOrStore()) {
        if (OPT_LocalCSE.debug)
          System.out.println("FOUND KILL " + ae);
        cache.removeElementAt(i);
        continue;               // don't increment i, since we removed 
      }
      i++;
    }
  }

  /** Implementation of the cache */
  private Vector cache;
}


/** Implements a tuple to record an OPT_Available Expression */
class OPT_AvailableExpression
    implements OPT_Operators {
  /**
   * the instruction which makes this expression available
   */
  OPT_Instruction inst;
  /**
   * the operator of the expression
   */
  OPT_Operator opr;
  /**
   * first operand
   */
  OPT_Operand op1;
  /**
   * second operand
   */
  OPT_Operand op2;
  /**
   * third operand
   */
  OPT_Operand op3;
  /**
   * location operand for memory (load/store) expressions
   */
  OPT_LocationOperand location;
  /**
   * temporary register holding the result of the available
   * expression
   */
  OPT_Register tmp;

  /**
   * @param i the instruction which makes this expression available
   * @param op the operator of the expression
   * @param o1 first operand
   * @param o2 second operand
   * @param o3 third operand
   * @param location location operand for memory (load/store) expressions
   * @param t temporary register holding the result of the available
   * expression
   */
  OPT_AvailableExpression (OPT_Instruction i, OPT_Operator op, OPT_Operand o1, 
      OPT_Operand o2, OPT_Operand o3, OPT_LocationOperand location, 
      OPT_Register t) {
    inst = i;
    opr = op;
    op1 = o1;
    op2 = o2;
    op3 = o3;
    this.location = location;
    tmp = t;
  }

  /**
   * Two AEs are "equal" iff  
   *  <ul> 
   *   <li> for unary, binary and ternary expressions: 
   *     the operator and the operands match
   *    <li> for loads and stores: if the 2 operands and the location match
   *  </ul>
   */
  public boolean equals (Object o) {
    if (!(o instanceof OPT_AvailableExpression))
      return  false;
    OPT_AvailableExpression ae = (OPT_AvailableExpression)o;
    if (isLoadOrStore()) {
      if (!ae.isLoadOrStore())
        return  false;
      boolean result = OPT_LocationOperand.mayBeAliased(location, ae.location);
      if (op1 != null) {
        result = result && op1.similar(ae.op1);
      } else {
	/* op1 is null, so ae.op1 must also be null */
        if (ae.op1 != null)
          return  false;
      }
      if (op2 != null) {
        result = result && op2.similar(ae.op2);
      } else {
	/* op2 is null, so ae.op2 must also be null */
        if (ae.op2 != null)
          return  false;
      }
      return  result;
    } else if (isBoundsCheck()) {
      // Augment equality with BC(ref,C1) ==> BC(ref,C2) 
      // when C1>0, C2>=0, and C1>C2
      if (!opr.equals(ae.opr))
        return  false;
      if (!op1.similar(ae.op1))
        return  false;
      if (op2.similar(ae.op2))
        return  true;
      if (op2 instanceof OPT_IntConstantOperand && 
          ae.op2 instanceof OPT_IntConstantOperand) {
        int C1 = ((OPT_IntConstantOperand)op2).value;
        int C2 = ((OPT_IntConstantOperand)ae.op2).value;
        return  C1 > 0 && C2 >= 0 && C1 > C2;
      } else {
        return  false;
      }
    } else {
      if (!opr.equals(ae.opr))
        return  false;
      if (isTernary() && !op3.similar(ae.op3))
        return  false;
      if (isBinary() && !op2.similar(ae.op2))
        return  false;
      return  op1.similar(ae.op1);
    }
  }

  /**
   * Does this expression use three operands?
   */
  public final boolean isTernary () {
    return  op3 != null;
  }

  /**
   * Does this expression use two or more operands?
   */
  public final boolean isBinary () {
    return  op2 != null;
  }

  /**
   * Does this expression represent the result of a load or store?
   */
  public final boolean isLoadOrStore () {
    return  GetField.conforms(opr) || GetStatic.conforms(opr) || 
        PutField.conforms(opr)
        || PutStatic.conforms(opr);
  }

  /**
   * Does this expression represent the result of a load?
   */
  public final boolean isLoad () {
    return  GetField.conforms(opr) || GetStatic.conforms(opr);
  }

  /**
   * Does this expression represent the result of a store?
   */
  public final boolean isStore () {
    return  PutField.conforms(opr) || PutStatic.conforms(opr);
  }

  /**
   * Does this expression represent the result of a bounds check?
   */
  public final boolean isBoundsCheck () {
    return  BoundsCheck.conforms(opr) 
    || (TrapIf.conforms(opr) && ((OPT_TrapCodeOperand)op3).isArrayBounds());
  }
}



