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

import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.HashMap;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.BBend;
import org.jikesrvm.compilers.opt.ir.GuardResultCarrier;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ADDR_2INT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ADDR_2LONG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ARCH_INDEPENDENT_END_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ARRAYLENGTH_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BOOLEAN_CMP_ADDR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BOOLEAN_CMP_INT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BOOLEAN_NOT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BOUNDS_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CHECKCAST_NOTNULL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CHECKCAST_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CHECKCAST_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_2FLOAT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_2INT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_2LONG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_ADD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_AS_LONG_BITS_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_CMPG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_CMPL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_DIV_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_MUL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_NEG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_REM_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_SUB_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_2DOUBLE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_2INT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_2LONG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_ADD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_AS_INT_BITS_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_CMPG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_CMPL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_DIV_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_MUL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_NEG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_REM_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_SUB_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GET_ARRAY_ELEMENT_TIB_FROM_TIB_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GET_CLASS_OBJECT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GET_CLASS_TIB_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GET_DOES_IMPLEMENT_FROM_TIB_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GET_OBJ_TIB_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GET_SUPERCLASS_IDS_FROM_TIB_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GET_TYPE_FROM_TIB_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GUARD_COMBINE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GUARD_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INSTANCEOF_NOTNULL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INSTANCEOF_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INSTANCEOF_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_2ADDRSigExt_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_2ADDRZerExt_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_2BYTE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_2DOUBLE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_2FLOAT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_2LONG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_2SHORT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_2USHORT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_ADD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_AND_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_BITS_AS_FLOAT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_DIV_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_MUL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_NEG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_NOT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_OR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_REM_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_SHL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_SHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_SUB_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_USHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_XOR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_ZERO_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_2ADDR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_2DOUBLE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_2FLOAT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_2INT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_ADD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_AND_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_BITS_AS_DOUBLE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_CMP_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_DIV_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_MUL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_NEG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_NOT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_OR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_REM_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SHL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SUB_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_USHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_XOR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_ZERO_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.MUST_IMPLEMENT_INTERFACE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.NULL_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.OBJARRAY_STORE_CHECK_NOTNULL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.OBJARRAY_STORE_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PI_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_ADD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_AND_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_NOT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_OR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_SHL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_SHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_SUB_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_USHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_XOR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.TRAP_IF_opcode;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperandEnumeration;
import org.jikesrvm.compilers.opt.ir.ResultCarrier;

/**
 * This class provides global common sub expression elimination.
 */
public final class OPT_GlobalCSE extends OPT_CompilerPhase {

  /** Output debug messages */
  public boolean verbose = true;
  /** Cache of IR being processed by this phase */
  private OPT_IR ir;
  /** Cache of the value numbers from the IR  */
  private OPT_GlobalValueNumberState valueNumbers;
  /**
   * Cache of dominator tree that should be computed prior to this
   * phase
   */
  private OPT_DominatorTree dominator;
  /**
   * Available expressions. From Muchnick, "an expression
   * <em>exp</em>is said to be </em>available</em> at the entry to a
   * basic block if along every control-flow path from the entry block
   * to this block there is an evaluation of exp that is not
   * subsequently killed by having one or more of its operands
   * assigned a new value." Our available expressions are a mapping
   * from a value number to the first instruction to define it as we
   * traverse the dominator tree.
   */
  private final HashMap<Integer, OPT_Instruction> avail;

  /**
   * Constructor
   */
  public OPT_GlobalCSE() {
    avail = new HashMap<Integer, OPT_Instruction>();
  }

  /**
   * Redefine shouldPerform so that none of the subphases will occur
   * unless we pass through this test.
   */
  public boolean shouldPerform(OPT_Options options) {
    return options.GCSE;
  }

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<OPT_CompilerPhase> constructor = getCompilerPhaseConstructor(OPT_GlobalCSE.class);

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  public Constructor<OPT_CompilerPhase> getClassConstructor() {
    return constructor;
  }

  /**
   * Returns the name of the phase
   */
  public String getName() {
    return "Global CSE";
  }

  /**
   * Perform the GlobalCSE compiler phase
   */
  public void perform(OPT_IR ir) {
    // conditions to leave early
    if (ir.hasReachableExceptionHandlers() || OPT_GCP.tooBig(ir)) {
      return;
    }
    // cache useful values
    verbose = ir.options.VERBOSE_GCP;
    this.ir = ir;
    dominator = ir.HIRInfo.dominatorTree;

    // perform GVN
    (new OPT_GlobalValueNumber()).perform(ir);
    valueNumbers = ir.HIRInfo.valueNumbers;

    if (verbose) VM.sysWrite("in GCSE for " + ir.method + "\n");

    // compute DU and perform copy propagation
    OPT_DefUse.computeDU(ir);
    OPT_Simple.copyPropagation(ir);
    OPT_DefUse.computeDU(ir);

    // perform GCSE starting at the entry block
    globalCSE(ir.firstBasicBlockInCodeOrder());

    if (VM.VerifyAssertions) {
      VM._assert(avail.isEmpty(), avail.toString());
    }
    ir.actualSSAOptions.setScalarValid(false);
  }

  /**
   * Recursively descend over all blocks dominated by b. For each
   * instruction in the block, if it defines a GVN then record it in
   * the available expressions. If the GVN already exists in the
   * available expressions then eliminate the instruction and change
   * all uses of the result of the instruction to be uses of the first
   * instruction to define the result of this expression.
   * @param b the current block to process
   */
  private void globalCSE(OPT_BasicBlock b) {
    OPT_Instruction next, inst;
    // Iterate over instructions in b
    inst = b.firstInstruction();
    while (!BBend.conforms(inst)) {
      next = inst.nextInstructionInCodeOrder();
      // check instruction is safe for GCSE, {@see shouldCSE}
      if (!shouldCSE(inst)) {
        inst = next;
        continue;
      }
      // check the instruction defines a result
      OPT_RegisterOperand result = getResult(inst);
      if (result == null) {
        inst = next;
        continue;
      }
      // get the value number for this result. The value number for
      // the same sub-expression is shared by all results showing they
      // can be eliminated. If the value number is UNKNOWN the result
      // is negative.
      int vn = valueNumbers.getValueNumber(result);
      if (vn < 0) {
        inst = next;
        continue;
      }
      // was this the first definition of the value number?
      Integer Vn = vn;
      OPT_Instruction former = avail.get(Vn);
      if (former == null) {
        // first occurance of value number, record it in the available
        // expressions
        avail.put(Vn, inst);
      } else {
        // this value number has been seen before so we can use the
        // earlier version
        // NB instead of trying to repair Heap SSA, we rebuild it
        // after CSE

        // relink scalar dependencies - make all uses of the current
        // instructions result use the first definition of the result
        // by the earlier expression
        OPT_RegisterOperand formerDef = getResult(former);
        OPT_Register reg = result.register;
        formerDef.register.setSpansBasicBlock();
        OPT_RegisterOperandEnumeration uses = OPT_DefUse.uses(reg);
        while (uses.hasMoreElements()) {
          OPT_RegisterOperand use = uses.next();
          OPT_DefUse.transferUse(use, formerDef);
        }
        if (verbose) {
          VM.sysWrite("using      " + former + "\n" + "instead of " + inst + "\n");
        }
        // remove the redundant instruction
        inst.remove();
      }
      inst = next;
    } // end of instruction iteration
    // Recurse over all blocks that this block dominates
    Enumeration<OPT_TreeNode> e = dominator.getChildren(b);
    while (e.hasMoreElements()) {
      OPT_DominatorTreeNode n = (OPT_DominatorTreeNode) e.nextElement();
      OPT_BasicBlock bl = n.getBlock();
      // don't process infrequently executed basic blocks
      if (ir.options.FREQ_FOCUS_EFFORT && bl.getInfrequent()) continue;
      globalCSE(bl);
    }
    // Iterate over instructions in this basic block removing
    // available expressions that had been created for this block
    inst = b.firstInstruction();
    while (!BBend.conforms(inst)) {
      next = inst.nextInstructionInCodeOrder();
      if (!shouldCSE(inst)) {
        inst = next;
        continue;
      }
      OPT_RegisterOperand result = getResult(inst);
      if (result == null) {
        inst = next;
        continue;
      }
      int vn = valueNumbers.getValueNumber(result);
      if (vn < 0) {
        inst = next;
        continue;
      }
      Integer Vn = vn;
      OPT_Instruction former = avail.get(Vn);
      if (former == inst) {
        avail.remove(Vn);
      }
      inst = next;
    }
  }

  /**
   * Get the result operand of the instruction
   * @param inst
   */
  private OPT_RegisterOperand getResult(OPT_Instruction inst) {
    if (ResultCarrier.conforms(inst)) {
      return ResultCarrier.getResult(inst);
    }
    if (GuardResultCarrier.conforms(inst)) {
      return GuardResultCarrier.getGuardResult(inst);
    }
    return null;
  }

  /**
   * should this instruction be cse'd  ?
   * @param inst
   */
  private boolean shouldCSE(OPT_Instruction inst) {

    if ((inst.isAllocation()) ||
        inst.isDynamicLinkingPoint() ||
        inst.isImplicitLoad() ||
        inst.isImplicitStore() ||
        inst.operator.opcode >= ARCH_INDEPENDENT_END_opcode) {
      return false;
    }

    switch (inst.operator.opcode) {
      case INT_MOVE_opcode:
      case LONG_MOVE_opcode:
      case GET_CLASS_OBJECT_opcode:
      case CHECKCAST_opcode:
      case CHECKCAST_NOTNULL_opcode:
      case CHECKCAST_UNRESOLVED_opcode:
      case MUST_IMPLEMENT_INTERFACE_opcode:
      case INSTANCEOF_opcode:
      case INSTANCEOF_NOTNULL_opcode:
      case INSTANCEOF_UNRESOLVED_opcode:
      case PI_opcode:
      case FLOAT_MOVE_opcode:
      case DOUBLE_MOVE_opcode:
      case REF_MOVE_opcode:
      case GUARD_MOVE_opcode:
      case GUARD_COMBINE_opcode:
      case TRAP_IF_opcode:
      case REF_ADD_opcode:
      case INT_ADD_opcode:
      case LONG_ADD_opcode:
      case FLOAT_ADD_opcode:
      case DOUBLE_ADD_opcode:
      case REF_SUB_opcode:
      case INT_SUB_opcode:
      case LONG_SUB_opcode:
      case FLOAT_SUB_opcode:
      case DOUBLE_SUB_opcode:
      case INT_MUL_opcode:
      case LONG_MUL_opcode:
      case FLOAT_MUL_opcode:
      case DOUBLE_MUL_opcode:
      case INT_DIV_opcode:
      case LONG_DIV_opcode:
      case FLOAT_DIV_opcode:
      case DOUBLE_DIV_opcode:
      case INT_REM_opcode:
      case LONG_REM_opcode:
      case FLOAT_REM_opcode:
      case DOUBLE_REM_opcode:
      case INT_NEG_opcode:
      case LONG_NEG_opcode:
      case FLOAT_NEG_opcode:
      case DOUBLE_NEG_opcode:
      case REF_SHL_opcode:
      case INT_SHL_opcode:
      case LONG_SHL_opcode:
      case REF_SHR_opcode:
      case INT_SHR_opcode:
      case LONG_SHR_opcode:
      case REF_USHR_opcode:
      case INT_USHR_opcode:
      case LONG_USHR_opcode:
      case REF_AND_opcode:
      case INT_AND_opcode:
      case LONG_AND_opcode:
      case REF_OR_opcode:
      case INT_OR_opcode:
      case LONG_OR_opcode:
      case REF_XOR_opcode:
      case INT_XOR_opcode:
      case REF_NOT_opcode:
      case INT_NOT_opcode:
      case LONG_NOT_opcode:
      case LONG_XOR_opcode:
      case INT_2LONG_opcode:
      case INT_2FLOAT_opcode:
      case INT_2DOUBLE_opcode:
      case INT_2ADDRSigExt_opcode:
      case INT_2ADDRZerExt_opcode:
      case LONG_2ADDR_opcode:
      case ADDR_2INT_opcode:
      case ADDR_2LONG_opcode:
      case LONG_2INT_opcode:
      case LONG_2FLOAT_opcode:
      case LONG_2DOUBLE_opcode:
      case FLOAT_2INT_opcode:
      case FLOAT_2LONG_opcode:
      case FLOAT_2DOUBLE_opcode:
      case DOUBLE_2INT_opcode:
      case DOUBLE_2LONG_opcode:
      case DOUBLE_2FLOAT_opcode:
      case INT_2BYTE_opcode:
      case INT_2USHORT_opcode:
      case INT_2SHORT_opcode:
      case LONG_CMP_opcode:
      case FLOAT_CMPL_opcode:
      case FLOAT_CMPG_opcode:
      case DOUBLE_CMPL_opcode:
      case DOUBLE_CMPG_opcode:
      case NULL_CHECK_opcode:
      case BOUNDS_CHECK_opcode:
      case INT_ZERO_CHECK_opcode:
      case LONG_ZERO_CHECK_opcode:
      case OBJARRAY_STORE_CHECK_opcode:
      case OBJARRAY_STORE_CHECK_NOTNULL_opcode:
      case BOOLEAN_NOT_opcode:
      case BOOLEAN_CMP_INT_opcode:
      case BOOLEAN_CMP_ADDR_opcode:
      case FLOAT_AS_INT_BITS_opcode:
      case INT_BITS_AS_FLOAT_opcode:
      case DOUBLE_AS_LONG_BITS_opcode:
      case LONG_BITS_AS_DOUBLE_opcode:
      case ARRAYLENGTH_opcode:
      case GET_OBJ_TIB_opcode:
      case GET_CLASS_TIB_opcode:
      case GET_TYPE_FROM_TIB_opcode:
      case GET_SUPERCLASS_IDS_FROM_TIB_opcode:
      case GET_DOES_IMPLEMENT_FROM_TIB_opcode:
      case GET_ARRAY_ELEMENT_TIB_FROM_TIB_opcode:
        return !(OPT_GCP.usesOrDefsPhysicalRegisterOrAddressType(inst));
    }
    return false;
  }
}
