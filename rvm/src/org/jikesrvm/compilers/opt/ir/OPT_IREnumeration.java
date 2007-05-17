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

import java.util.Iterator;
import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.ArchitectureSpecific.OPT_PhysicalDefUse;
import org.jikesrvm.classloader.VM_TypeReference;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PHI;
import org.vmmagic.pragma.NoInline;

/**
 * This class is not meant to be instantiated.
 * It simply serves as a place to collect the implementation of
 * primitive IR enumerations.
 * None of these functions are meant to be called directly from
 * anywhere except OPT_IR, OPT_Instruction, and OPT_BasicBlock.
 * General clients should use the higher level interfaces provided
 * by those classes
 */
public abstract class OPT_IREnumeration {

  /**
   * Forward intra basic block instruction enumerations from
   * from start...last inclusive.
   *
   * NB: start and last _must_ be in the same basic block
   *     and must be in the proper relative order.
   *     This code does _not_ check this invariant, and will
   *     simply fail by eventually thowing a NoSuchElementException
   *     if it is not met. Caller's must be sure the invariants are met.
   *
   * @param start the instruction to start with
   * @param end   the instruction to end with
   * @return an enumeration of the instructions from start to end
   */
  public static OPT_InstructionEnumeration forwardIntraBlockIE(final OPT_Instruction start, final OPT_Instruction end) {
    return new OPT_InstructionEnumeration() {
      private OPT_Instruction current = start;
      private final OPT_Instruction last = end;

      public boolean hasMoreElements() { return current != null; }

      public OPT_Instruction nextElement() { return next(); }

      public OPT_Instruction next() {
        OPT_Instruction res = current;
        if (current == last) {
          current = null;
        } else {
          try {
            current = current.getNext();
          } catch (NullPointerException e) {
            fail("forwardIntraBlockIE");
          }
        }
        return res;
      }
    };
  }

  /**
   * Reverse intra basic block instruction enumerations from
   * from start...last inclusive.
   *
   * NB: start and last _must_ be in the same basic block
   *     and must be in the proper relative order.
   *     This code does _not_ check this invariant, and will
   *     simply fail by eventually thowing a NoSuchElementException
   *     if it is not met. Caller's must be sure the invariants are met.
   *
   * @param start the instruction to start with
   * @param end   the instruction to end with
   * @return an enumeration of the instructions from start to end
   */
  public static OPT_InstructionEnumeration reverseIntraBlockIE(final OPT_Instruction start, final OPT_Instruction end) {
    return new OPT_InstructionEnumeration() {
      private OPT_Instruction current = start;
      private final OPT_Instruction last = end;

      public boolean hasMoreElements() { return current != null; }

      public OPT_Instruction nextElement() { return next(); }

      public OPT_Instruction next() {
        OPT_Instruction res = current;
        if (current == last) {
          current = null;
        } else {
          try {
            current = current.getPrev();
          } catch (NullPointerException e) {
            fail("reverseIntraBlockIE");
          }
        }
        return res;
      }
    };
  }

  /**
   * A forward enumeration of all the instructions in the IR.
   *
   * @param ir the IR to walk over
   * @return a forward enumeration of the insturctions in ir
   */
  public static OPT_InstructionEnumeration forwardGlobalIE(final OPT_IR ir) {
    return new OPT_InstructionEnumeration() {
      private OPT_Instruction current = ir.firstInstructionInCodeOrder();

      public boolean hasMoreElements() { return current != null; }

      public OPT_Instruction nextElement() { return next(); }

      public OPT_Instruction next() {
        try {
          OPT_Instruction res = current;
          current = current.nextInstructionInCodeOrder();
          return res;
        } catch (NullPointerException e) {
          fail("forwardGlobalIR");
          return null; // placate jikes
        }
      }
    };
  }

  /**
   * A reverse enumeration of all the instructions in the IR.
   *
   * @param ir the IR to walk over
   * @return a forward enumeration of the insturctions in ir
   */
  public static OPT_InstructionEnumeration reverseGlobalIE(final OPT_IR ir) {
    return new OPT_InstructionEnumeration() {
      private OPT_Instruction current = ir.lastInstructionInCodeOrder();

      public boolean hasMoreElements() { return current != null; }

      public OPT_Instruction nextElement() { return next(); }

      public OPT_Instruction next() {
        try {
          OPT_Instruction res = current;
          current = current.prevInstructionInCodeOrder();
          return res;
        } catch (NullPointerException e) {
          fail("forwardGlobalIR");
          return null; // placate jikes
        }
      }
    };
  }

  /**
   * A forward enumeration of all the basic blocks in the IR.
   *
   * @param ir the IR to walk over
   * @return a forward enumeration of the basic blocks in ir
   */
  public static OPT_BasicBlockEnumeration forwardBE(final OPT_IR ir) {
    return new OPT_BasicBlockEnumeration() {
      private OPT_BasicBlock current = ir.firstBasicBlockInCodeOrder();

      public boolean hasMoreElements() { return current != null; }

      public OPT_BasicBlock nextElement() { return next(); }

      public OPT_BasicBlock next() {
        try {
          OPT_BasicBlock res = current;
          current = current.nextBasicBlockInCodeOrder();
          return res;
        } catch (NullPointerException e) {
          fail("forwardBE");
          return null; // placate jikes
        }
      }
    };
  }

  /**
   * A reverse enumeration of all the basic blocks in the IR.
   *
   * @param ir the IR to walk over
   * @return a reverse enumeration of the basic blocks in ir
   */
  public static OPT_BasicBlockEnumeration reverseBE(final OPT_IR ir) {
    return new OPT_BasicBlockEnumeration() {
      private OPT_BasicBlock current = ir.lastBasicBlockInCodeOrder();

      public boolean hasMoreElements() { return current != null; }

      public OPT_BasicBlock nextElement() { return next(); }

      public OPT_BasicBlock next() {
        try {
          OPT_BasicBlock res = current;
          current = current.prevBasicBlockInCodeOrder();
          return res;
        } catch (NullPointerException e) {
          fail("forwardBE");
          return null; // placate jikes
        }
      }
    };
  }

  /**
   * This class implements an {@link OPT_InstructionEnumeration} over
   * all instructions for a basic block. This enumeration includes
   * explicit instructions in the IR and implicit phi instructions for
   * heap variables, which are stored only in this lookaside
   * structure.
   * @see org.jikesrvm.compilers.opt.OPT_SSADictionary
   */
  public static final class AllInstructionsEnum implements OPT_InstructionEnumeration {
    /**
     * An enumeration of the explicit instructions in the IR for a
     * basic block
     */
    private final OPT_InstructionEnumeration explicitInstructions;

    /**
     * An enumeration of the implicit instructions in the IR for a
     * basic block.  These instructions appear only in the SSA
     * dictionary lookaside structure.
     */
    private final Iterator<OPT_Instruction> implicitInstructions;

    /**
     * The label instruction for the basic block - the label is
     * special as we want it to appear in the enumeration before the
     * implicit SSA instructions
     */
    private OPT_Instruction labelInstruction;

    /**
     * Construct an enumeration for all instructions, both implicit and
     * explicit in the IR, for a given basic block
     *
     * @param block the basic block whose instructions this enumerates
     */
    public AllInstructionsEnum(OPT_IR ir, OPT_BasicBlock block) {
      explicitInstructions = block.forwardInstrEnumerator();
      if (ir.inSSAForm()) {
        implicitInstructions = ir.HIRInfo.SSADictionary.getHeapPhiInstructions(block);
      } else {
        implicitInstructions = null;
      }
      labelInstruction = explicitInstructions.next();
    }

    /**
     * Are there more elements in the enumeration?
     *
     * @return true or false
     */
    public boolean hasMoreElements() {
      return (((implicitInstructions != null) && implicitInstructions.hasNext()) ||
              explicitInstructions.hasMoreElements());
    }

    /**
     * Get the next instruction in the enumeration
     *
     * @return the next instruction
     */
    public OPT_Instruction next() {
      if (labelInstruction != null) {
        OPT_Instruction temp = labelInstruction;
        labelInstruction = null;
        return temp;
      } else if ((implicitInstructions != null) && implicitInstructions.hasNext()) {
        return implicitInstructions.next();
      } else {
        return explicitInstructions.next();
      }
    }

    /**
     * Get the next instruction in the enumeration
     *
     * @return the next instruction
     */
    public OPT_Instruction nextElement() {
      return next();
    }
  }

  /**
   * This class implements an {@link OPT_OperandEnumeration}. It used
   * for holding the definitions of a particular instruction and being
   * used as an enumeration for iterating over. It differs from other
   * {@link OPT_OperandEnumeration} as it iterates over both implicit
   * and explicit operands.
   * @see org.jikesrvm.compilers.opt.OPT_SSADictionary
   */
  public static final class AllDefsEnum implements OPT_OperandEnumeration {
    /**
     * Enumeration of non-heap operands defined by the instruction
     */
    private final OPT_OperandEnumeration instructionOperands;
    /**
     * Array of heap operands defined by the instruction
     */
    private final OPT_HeapOperand<?>[] heapOperands;
    /**
     * Current heap operand we're upto for the enumeration
     */
    private int curHeapOperand;
    /**
     * Implicit definitions from the operator
     */
    private final ArchitectureSpecific.OPT_PhysicalDefUse.PDUEnumeration implicitDefs;
    /**
     * Defining instruction
     */
    private final OPT_Instruction instr;

    /**
     * Construct/initialize object
     *
     * @param ir    Containing IR
     * @param instr Instruction to get definitions for
     */
    public AllDefsEnum(OPT_IR ir, OPT_Instruction instr) {
      this.instr = instr;
      instructionOperands = instr.getDefs();
      if (instr.operator().getNumberOfImplicitDefs() > 0) {
        implicitDefs = ArchitectureSpecific.OPT_PhysicalDefUse.enumerate(instr.operator().implicitDefs, ir);
      } else {
        implicitDefs = null;
      }
      if (ir.inSSAForm() && (instr.operator != PHI)) {
        // Phi instructions store the heap SSA in the actual
        // instruction
        heapOperands = ir.HIRInfo.SSADictionary.getHeapDefs(instr);
      } else {
        heapOperands = null;
      }
    }

    /**
     * Are there any more elements in the enumeration
     */
    public boolean hasMoreElements() {
      return ((instructionOperands.hasMoreElements()) ||
              ((heapOperands != null) && (curHeapOperand < heapOperands.length)) ||
              ((implicitDefs != null) && (implicitDefs.hasMoreElements())));
    }

    /**
     * Next element in the enumeration
     */
    public OPT_Operand next() {
      if (instructionOperands.hasMoreElements()) {
        return instructionOperands.next();
      } else {
        if ((implicitDefs != null) && implicitDefs.hasMoreElements()) {
          OPT_RegisterOperand rop = new OPT_RegisterOperand(implicitDefs.nextElement(), VM_TypeReference.Int);
          rop.instruction = instr;
          return rop;
        } else {
          if (curHeapOperand >= heapOperands.length) {
            fail("Regular and heap operands exhausted");
          }
          OPT_HeapOperand<?> result = heapOperands[curHeapOperand];
          curHeapOperand++;
          return result;
        }
      }
    }

    /**
     * Next element in the enumeration
     */
    public OPT_Operand nextElement() {
      return next();
    }
  }

  /**
   * This class implements an {@link OPT_OperandEnumeration}. It used
   * for holding the uses of a particular instruction and being used
   * as an enumeration for iterating over. It differs from other
   * {@link OPT_OperandEnumeration} as it iterates over both implicit
   * and explicit operands.
   * @see org.jikesrvm.compilers.opt.OPT_SSADictionary
   */
  public static final class AllUsesEnum implements OPT_OperandEnumeration {
    /**
     * Enumeration of non-heap operands defined by the instruction
     */
    private final OPT_OperandEnumeration instructionOperands;
    /**
     * Array of heap operands defined by the instruction
     */
    private final OPT_HeapOperand<?>[] heapOperands;
    /**
     * Current heap operand we're upto for the enumeration
     */
    private int curHeapOperand;
    /**
     * Implicit uses from the operator
     */
    private final OPT_PhysicalDefUse.PDUEnumeration implicitUses;
    /**
     * Defining instruction
     */
    private final OPT_Instruction instr;

    /**
     * Construct/initialize object
     *
     * @param ir    Containing IR
     * @param instr Instruction to get uses for
     */
    public AllUsesEnum(OPT_IR ir, OPT_Instruction instr) {
      this.instr = instr;
      instructionOperands = instr.getUses();
      if (instr.operator().getNumberOfImplicitUses() > 0) {
        implicitUses = OPT_PhysicalDefUse.enumerate(instr.operator().implicitUses, ir);
      } else {
        implicitUses = null;
      }
      if (ir.inSSAForm() && (instr.operator != PHI)) {
        // Phi instructions store the heap SSA in the actual
        // instruction
        heapOperands = ir.HIRInfo.SSADictionary.getHeapUses(instr);
      } else {
        heapOperands = null;
      }
    }

    /**
     * Are there any more elements in the enumeration
     */
    public boolean hasMoreElements() {
      return ((instructionOperands.hasMoreElements()) ||
              ((heapOperands != null) && (curHeapOperand < heapOperands.length)) ||
              ((implicitUses != null) && (implicitUses.hasMoreElements())));
    }

    /**
     * Next element in the enumeration
     */
    public OPT_Operand next() {
      if (instructionOperands.hasMoreElements()) {
        return instructionOperands.next();
      } else {
        if ((implicitUses != null) && implicitUses.hasMoreElements()) {
          OPT_RegisterOperand rop = new OPT_RegisterOperand(implicitUses.nextElement(), VM_TypeReference.Int);
          rop.instruction = instr;
          return rop;
        } else {
          if (curHeapOperand >= heapOperands.length) {
            fail("Regular and heap operands exhausted");
          }
          OPT_HeapOperand<?> result = heapOperands[curHeapOperand];
          curHeapOperand++;
          return result;
        }
      }
    }

    /**
     * Next element in the enumeration
     */
    public OPT_Operand nextElement() {
      return next();
    }
  }

  @NoInline
  private static void fail(String msg) throws java.util.NoSuchElementException {
    throw new java.util.NoSuchElementException(msg);
  }
}

