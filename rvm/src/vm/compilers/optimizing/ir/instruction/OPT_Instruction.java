/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;
import java.util.Enumeration;

import org.vmmagic.pragma.*;

/**
 * Instructions are the basic atomic unit of the IR.
 * An instruction contains an {@link OPT_Operator operator} and
 * (optionally) some {@link OPT_Operand operands}.  
 * In addition, an instruction may (or may not) have
 * valid {@link #bcIndex} and{@link #position} fields that
 * together encode a description of the bytecode that it came from. 
 * <p>
 * Although we use a single class, <code>OPT_Instruction</code>, 
 * to implement all IR instructions, there are logically a number 
 * of different kinds of instructions.  
 * For example, binary operators, array loads, calls,
 * and null_checks all have different number of operands with differing
 * semantics.  To manage this in an abstract, somewhat object-oriented,
 * but still highly efficient fashion we have the notion of an 
 * <em>Instruction Format</em>. An Instruction Format is a class
 * external to OPT_Instruction (defined in the instructionFormat package)
 * that provides static methods to create instructions and symbolically 
 * access their operands.  Every instance of <code>OPT_Operator</code>
 * is assigned to exactly one Instruction Format.  Thus, the instruction's
 * operator implies which Instruction Format class can be used to 
 * access the instruction's operands.
 * <p> 
 * There are some common logical operands (eg Result, Location) that
 * appear in a large number of Instruction Formats.  In addition to the
 * basic Instruction Format classes, we provided additional classes 
 * (eg ResultCarrier, LocationCarrier) that allow manipulation of all
 * instructions that contain a common operands. 
 * <p>
 * A configuration (OptOptVIFcopyingGC) is defined in which all methods of
 * all Instruction Format classes verify that the operator of the instruction
 * being manipulated actually belongs to the appropriate Instruction Format.
 * This configuration is quite slow, but is an important sanity check to make
 * sure that Instruction Formats are being used in a consistent fashion.
 * <p>
 * The instruction's operator also has a number of traits.  Methods on 
 * <code>OPT_Instruction</code> are provided to query these operator traits.
 * In general, clients should use the methods of OPT_Instruction to query
 * traits, since a particular instruction may override the operator-provided
 * default in some cases. For example, {@link #isMove()}, {@link #isBranch()},
 * {@link #isPEI()}, and {@link #isCall()} are some of the trait queries.
 * <p>
 * Unfortunately, the combination of operators, operator traits, and
 * Instruction Formats often leads to a tricky decision of which of three
 * roughly equivalent idioms one should use when writing code that 
 * needs to manipulate instructions and their operands. 
 * For example,
 * <pre>
 * if (Call.conforms(instr)) {
 *    return Call.getResult(instr);
 * }
 * </pre>
 * and 
 * <pre>
 * if (instr.operator() == CALL) {
 *    return Call.getResult(instr);
 * }
 * </pre>
 * and 
 * <pre>
 * if (instr.isCall()) {
 *    return ResultCarrier.getResult(instr);
 * } 
 * </pre>
 * are more or less the same. 
 * In some cases, picking an idiom is simply a matter of taste, 
 * but in others making the wrong choice can lead to code that is less 
 * robust or maintainable as operators and/or instruction formats are added
 * and removed from the IR. One should always think carefully about which 
 * idiom is the most concise, maintainable, robust and efficient means of 
 * accomplishing a given task.
 * Some general rules of thumb (or at least one person's opinion):
 * <ul>
 * <li> Tests against operator traits should be preferred
 *      to use of the conforms method of an Instruction Format class if 
 *      both are possible.  This is definitely true if the code in question 
 *      does not need to access specific operands of the instruction. 
 *      Things are murkier if the code needs to manipulate specific 
 *      (non-common) operands of the instruction.
 * <li> If you find yourself writing long if-then-else constructs using
 *      either Instruction Format conforms or operator traits then you ought to
 *      at least consider writing a switch statement on the opcode of the 
 *      operator.  It should be more efficient and, depending on what your  
 *      desired default behavior is, may be more robust/maintainable as well.
 * <li> Instruction Format classes are really intended to represent the 
 *      "syntactic form" of an instruction, not the semantics of its operator.
 *      Using "conforms" when no specific operands are being manipulated
 *      is almost always not the right way to do things. 
 * </ul>
 *
 * @see OPT_Operator
 * @see OPT_Operand
 * @see OPT_BasicBlock
 * 
 * @author Jong Choi
 * @author Dave Grove
 * @author Mauricio J. Serrano
 * @author Harini Srinivasan
 * @author John Whaley
 */
public final class OPT_Instruction 
  implements VM_Constants, 
             OPT_Operators, 
             OPT_Constants {


  /** 
   * BITFIELD used to encode {@link #operatorInfo}.
   * NB: OI_INVALID must be default value!
   */
  private static final byte OI_INVALID    = 0x00;
  /** BITFIELD used to encode {@link #operatorInfo}. */
  private static final byte OI_PEI_VALID  = 0x01;
  /** BITFIELD used to encode {@link #operatorInfo}. */
  private static final byte OI_PEI        = 0x02; 
  /** BITFIELD used to encode {@link #operatorInfo}. */
  private static final byte OI_GC_VALID   = 0x04;
  /** BITFIELD used to encode {@link #operatorInfo}. */
  private static final byte OI_GC         = 0x08; 
  /** BITFIELD used to encode {@link #operatorInfo}. */
  private static final byte MARK1         = 0x20;
  /** BITFIELD used to encode {@link #operatorInfo}. */
  private static final byte MARK2         = 0x40;
  /*
   * NOTE: There are currently two free bits: 0x10 and 0x80.
   */


  /**
   * The index of the bytecode that this instruction came from.
   * In combination with the {@link #position}, the bcIndex field
   * uniquely identifies the source postion of the bytecode that
   * this instruction came from.
   */
  public int bcIndex = UNKNOWN_BCI;

  /**
   * A description of the tree of inlined methods that contains the bytecode 
   * that this instruction came from. 
   * In combination with the {@link #bcIndex}, the position field
   * uniquely identifies the source postion of the bytecode that
   * this instruction came from.
   * A single postion operator can be shared by many instruction objects.
   * 
   * @see OPT_InlineSequence
   * @see VM_OptEncodedCallSiteTree
   */
  public OPT_InlineSequence position;


  /**
   * A scratch word to be used as needed by analyses/optimizations to store 
   * information during an optimization. 
   * Cannot be used to comunicate information between compiler phases since 
   * any phase is allowed to mutate it.
   * Cannot safely be assumed to have a particular value at the start of
   * a phase.
   * Typical uses:  scratch bits to encode true/false or numbering
   *                store an index into a lookaside array of other information.
   */
  public int scratch;

  /**
   * A scratch object to be used as needed by analyses/optimizations to store 
   * information during an optimization. 
   * Cannot be used to comunicate information between compiler phases since 
   * any phase is allowed to mutate it.
   * Cannot safely be assumed to have a particular value at the start of
   * a phase.
   * To be used when more than one word of information is needed and
   * lookaside arrays are not desirable.
   * Typical uses:  attribute objects or links to shared data
   */
  public Object scratchObject;


  /**
   * The operator for this instruction.
   * The prefered idiom is to use the {@link #operator()} accessor method
   * instead of accessing this field directly, but we are still in the process
   * of updating old code. 
   * The same operator object can be shared by many instruction objects.
   * TODO: finish conversion and make this field private.
   */
  public OPT_Operator operator;

  /** 
   * The next instruction in the intra-basic-block list of instructions, 
   * will be null if no such instruction exists.
   */
  private OPT_Instruction next;

  /** 
   * The previous instruction in the intra-basic-block list of instructions, 
   * will be null if no such instruction exists.
   */
  private OPT_Instruction prev;

  /**
   * Override and refine the operator-based trait (characteristic)
   * information. 
   * @see OPT_Operator
   */
  private byte operatorInfo;

  /**
   * The operands of this instruction.
   */
  private OPT_Operand[] ops;


  
  /**
   * INTERNAL IR USE ONLY: create a new instruction with the specified number 
   * of operands.
   * For internal use only -- general clients must use the appropriate 
   * InstructionFormat class's create and mutate methods to create 
   * instruction objects!!!  But, because we put the InstructionFormat
   * classes in their own package for name space control, we have to
   * make this constructor public.  What a crock!
   *
   * @param op operator
   * @param size number of operands
   */
  public OPT_Instruction(OPT_Operator op, int size) {
    operator = op;
    ops = new OPT_Operand[size];
  }


  /**
   * Create a copy of this instruction.
   * The copy has the same operator and operands, but is not linked into
   * an instruction list.
   *
   * @return the copy
   */
  public OPT_Instruction copyWithoutLinks() {
     OPT_Instruction copy = new OPT_Instruction(operator, ops.length);
     for (int i = 0; i < ops.length; i++) {
        if (ops[i] != null) {
           copy.ops[i] = ops[i].copy();
           copy.ops[i].instruction = copy;
        }
     }
     copy.bcIndex = bcIndex;
     copy.operatorInfo = operatorInfo;
     copy.position = position;

     return copy;
  }
  
  /**
   * Returns the string representation of this instruction 
   * (mainly intended for use when printing the IR).
   * 
   * @return string representation of this instruction.
   */
  public String toString() {
    StringBuffer result = new StringBuffer("    ");
    if (isPEI())
      result.setCharAt(0, 'E');
    if (isGCPoint())
      result.setCharAt(1, 'G');

    if (operator == LABEL) {
      result.append("LABEL"+Label.getBlock(this).block.getNumber());
      if (Label.getBlock(this).block.getInfrequent()) result.append(" (Infrequent)");
      return result.toString();
    }

    result.append(operator);
    OPT_Operand op;
    int N = getNumberOfOperands();
    int numDefs = getNumberOfDefs();
    int numIDefs = operator.getNumberOfImplicitDefs();

    // print explicit defs
    int defsPrinted = 0;
    for (int i = 0; i < numDefs; i++) {
      op = getOperand(i);
      if (op != null) {
        if (defsPrinted > 0) result.append(", ");
        if (defsPrinted % 10 == 9) result.append('\n');
        result.append(op);
        defsPrinted++;
      }
    }
  
    // print implicit defs
    result.append(OPT_PhysicalDefUse.getString(operator.implicitDefs));
    defsPrinted += operator.getNumberOfImplicitDefs();

    // print separator
    if (defsPrinted > 0)
      if (operator.getNumberOfDefUses() == 0) 
        result.append(" = ");
      else
        result.append(" <-- ");
    
    // print explicit uses
    int usesPrinted = 0;
    for (int i = numDefs; i < N; i++) {
      op = getOperand(i);
      if (usesPrinted > 0) result.append(", ");
      if ((defsPrinted + usesPrinted) % 10 == 9) result.append('\n');
      usesPrinted++;
      if (op != null) {
        result.append(op);
      } else {
        result.append("<unused>");
      }
    }

    // print implicit defs
    result.append(OPT_PhysicalDefUse.getString(operator.implicitUses));
    usesPrinted += operator.getNumberOfImplicitUses();

    return result.toString();
  }


  /**
   * Return the next instruction with respect to the current 
   * code linearization order.
   *
   * @return the next insturction in the code order or 
   *         <code>null</code> if no such instruction exists
   */
  public OPT_Instruction nextInstructionInCodeOrder() {
    if (next == null) {
      OPT_BasicBlock nBlock = 
        BBend.getBlock(this).block.nextBasicBlockInCodeOrder();
      if (nBlock == null) {
        return null;
      } else {
        return nBlock.firstInstruction();
      }
    } else {
      return next;
    }
  }


  /**
   * Return the previous instruction with respect to the current 
   * code linearization order.
   *
   * @return the previous insturction in the code order or 
   *         <code>null</code> if no such instruction exists
   */
  public OPT_Instruction prevInstructionInCodeOrder() {
    if (prev == null) {
      OPT_BasicBlock nBlock = 
        Label.getBlock(this).block.prevBasicBlockInCodeOrder();
      if (nBlock == null) {
        return null;
      } else {
        return nBlock.lastInstruction();
      }
    } else {
      return prev;
    }
  }


  /**
   * Get the basic block that contains this instruction.
   * Note: this instruction takes O(1) time for LABEL and BBEND
   * instructions, but will take O(# of instrs in the block)
   * for all other instructions. Therefore, although it can be used
   * on any instruction, care must be taken when using it to avoid 
   * doing silly O(N^2) work for what could be done in O(N) work.
   */
  public OPT_BasicBlock getBasicBlock() {
    if (isBbFirst()) {
      return Label.getBlock(this).block;
    } else if (isBbLast()) {
      return BBend.getBlock(this).block;
    } else {
      // Find basic block by going forwards to BBEND instruction
      OPT_Instruction instr = null; // Set = null to avoid compiler warning.
      for (instr = getNext(); !instr.isBbLast(); instr = instr.getNext()) ;
      return BBend.getBlock(instr).block;
    }
  }

  
  /*
   * Set the source position description ({@link #bcIndex},
   * {@link #position}) for this instruction to be the same as the
   * source instruction's source position description.
   * 
   * @param source the instruction to copy the source position from
   */
  public final void copyPosition(OPT_Instruction source) {
    bcIndex = source.bcIndex;
    position = source.position;
  }


  /**
   * Get the {@link #bcIndex bytecode index} of the instruction.
   * 
   * @return the bytecode index of the instruction
   */
  public int getBytecodeIndex() { 
    return bcIndex; 
  }

  /**
   * Set the {@link #bcIndex bytecode index} of the instruction.
   * 
   * @param bci the new bytecode index
   */
  public void setBytecodeIndex(int bci) { 
    bcIndex = bci; 
  }


  /**
   * Get the offset into the machine code array (in bytes) that
   * corresponds to the first byte after this instruction.  
   * This method only returns a valid value after it has been set as a
   * side-effect of {@link OPT_Assembler#generateCode final assembly}.
   * To get the offset in INSTRUCTIONs you must shift by LG_INSTURUCTION_SIZE.
   * 
   * @return the offset (in bytes) of the machinecode instruction 
   *         generated for this IR instruction in the final machinecode
   */
  public int getmcOffset() {
    return scratch;
  }

  /**
   * Only for use by {@link OPT_Assembler#generateCode}; sets the machine
   * code offset of the instruction as described in {@link #getmcOffset}.
   * 
   * @param mcOffset the offset (in bytes) for this instruction.
   */
  public void setmcOffset(int mcOffset) {
    scratch = mcOffset;
  }


  /**
   * Return the instruction's operator.
   * 
   * @return the operator
   */
  public OPT_Operator operator() {
    return operator;
  }

  /**
   * Return the opcode of the instruction's operator
   * (a unique id suitable for use in switches); see
   * {@link OPT_Operator#opcode}.
   * 
   * @return the operator's opcode
   */
  public char getOpcode() {
    return operator.opcode;
  }


  /*
   * Functions dealing with the instruction's operands.
   * Clients currently are grudgingly allowed (but definitely NOT encouraged) 
   * to depend on the fact that operands are partially ordered: 
   * first all the defs, then all the def/uses, then all the uses.
   * This may change in the future, so please try not to depend on it unless
   * absolutely necessary.
   *
   * Clients are NOT allowed to assume that specific operands appear in 
   * a particular order or at a particular index in the operand array.
   * Doing so results in fragile code and is generally evil. 
   * Virtually all access to operands should be through the OperandEnumerations
   * or through accessor functions of the InstructionFormat classes.
   */


  /**
   * Get the number of operands in this instruction.
   *
   * @return number of operands
   */
  public int getNumberOfOperands() {
    if (operator.hasVarUsesOrDefs()) {
      return getNumberOfOperandsVarUsesOrDefs();
    } else {
      return operator.getNumberOfDefs() + operator.getNumberOfPureUses();
    }
  }
  // isolate uncommon cases to enable inlined common case of getNumberOfOperands
  private int getNumberOfOperandsVarUsesOrDefs() {
    int numOps = ops.length - 1;
    int minOps;
    if (operator().hasVarUses()) {
      minOps = operator.getNumberOfDefs() + operator.getNumberOfPureFixedUses() - 1;
    } else {
      minOps = operator.getNumberOfFixedPureDefs() - 1;
    } 
    while (numOps > minOps && ops[numOps] == null) numOps--;
    return numOps + 1;
  }

  /**
   * Returns the number of operands that are defs
   * (either pure defs or combined def/uses).
   * By convention, operands are ordered in instructions
   * such that all defs are first, followed by all
   * combined defs/uses, followed by all pure uses.
   *
   * @return number of operands that are defs
   */
  public int getNumberOfDefs() {
    if (operator.hasVarDefs()) {
      int numOps = operator.getNumberOfFixedPureDefs();
      for (; numOps < ops.length; numOps++) {
        if (ops[numOps] == null) break;
      }
      return numOps;
    } else {
      return operator.getNumberOfDefs();
    }
  }

  
  /**
   * Returns the number of operands that are pure defs.
   * By convention, operands are ordered in instructions
   * such that all defs are first, followed by all
   * combined defs/uses, followed by all pure uses.
   *
   * @return number of operands that are defs
   */
  public int getNumberOfPureDefs() {
    if (operator.hasVarDefs()) {
      if (VM.VerifyAssertions) {
        VM._assert(operator.getNumberOfDefUses() == 0);
      }
      int numOps = operator.getNumberOfFixedPureDefs();
      for (; numOps < ops.length; numOps++) {
        if (ops[numOps] == null) break;
      }
      return numOps;
    } else {
      return operator.getNumberOfFixedPureDefs();
    }
  }
  /**
   * Returns the number of operands that are pure uses.
   * By convention, operands are ordered in instructions
   * such that all defs are first, followed by all
   * combined defs/uses, followed by all pure uses.
   *
   * @return number of operands that are defs
   */
  public int getNumberOfPureUses() {
    if (operator.hasVarDefs()) {
      if (VM.VerifyAssertions) {
        VM._assert(operator.getNumberOfDefUses() == 0);
      }
      int numOps = operator.getNumberOfFixedPureUses();
      int i = getNumberOfDefs() + numOps;
      for (; i < ops.length; i++) {
        if (ops[i] == null) break;
        numOps++;
      }
      return numOps;
    } else {
      return operator.getNumberOfFixedPureUses();
    }
  }

  
  /**
   * Returns the number of operands that are uses 
   * (either combined def/uses or pure uses).
   * By convention, operands are ordered in instructions
   * such that all defs are first, followed by all
   * combined defs/uses, followed by all pure uses.
   *
   * @return how many operands are uses
   */
  public int getNumberOfUses() {
    if (operator.hasVarUses()) {
      return getNumberOfOperands() - operator.getNumberOfPureDefs();
    } else {
      return operator.getNumberOfUses();
    }
  }


  /**
   * Replace all occurances of the first operand with the second.
   *
   * @param oldOp   The operand to replace
   * @param newOp   The new one to replace it with
   */
  public void replaceOperand(OPT_Operand oldOp, OPT_Operand newOp) {
    for (int i = 0; i < ops.length; i++) {
      if (getOperand(i) == oldOp) {
        putOperand(i, newOp);
      }
    }
  }

  /**
   * Replace any operands that are similar to the first operand 
   * with a copy of the second operand.
   *
   * @param oldOp   The operand whose similar operands should be replaced
   * @param newOp   The new one to replace it with
   */
  public void replaceSimilarOperands(OPT_Operand oldOp, OPT_Operand newOp) {
    for (int i = 0; i < ops.length; i++) {
      if (oldOp.similar(getOperand(i))) {
        putOperand(i, newOp.copy());
      }
    }
  }

  /**
   * Replace all occurances of register r with register n
   *
   * @param r the old register
   * @param n the new register
   */
  public void replaceRegister(OPT_Register r, OPT_Register n) {
    for (Enumeration u = getUses(); u.hasMoreElements(); ) {
      OPT_Operand use = (OPT_Operand)u.nextElement();
      if (use.isRegister()) {
        if (use.asRegister().register == r) {
          use.asRegister().register = n;
        }
      }
    }
    for (Enumeration d = getDefs(); d.hasMoreElements(); ) {
      OPT_Operand def = (OPT_Operand)d.nextElement();
      if (def.isRegister()) {
        if (def.asRegister().register == r) {
          def.asRegister().register = n;
        }
      }
    }
  }

  /**
   * Does this instruction hold any memory or stack location operands?
   */
  public final boolean hasMemoryOperand() {
    for (int i = 0; i<ops.length; i++) {
      OPT_Operand op = getOperand(i);
      if (op instanceof OPT_MemoryOperand ||
          op instanceof OPT_StackLocationOperand) {
        return true;
      }
    }
    return false;
  }

  /**
   * Enumerate all "leaf" operands of an instruction.
   * NOTE: DOES NOT RETURN MEMORY OPERANDS, ONLY
   *       THEIR CONTAINED OPERANDS!!!!!
   * 
   * @return an enumeration of the instruction's operands.
   */
  public final OPT_OperandEnumeration getOperands() {
    // By passing -1 as the last parameter we pretending
    // that treating all operands are uses. Somewhat ugly,
    // but avoids a third OE class.
    return new OE(this, 0, getNumberOfOperands()-1, -1);
  }

  /**
   * Enumerate all memory operands of an instruction
   * 
   * @return an enumeration of the instruction's operands.
   */
  public final OPT_OperandEnumeration getMemoryOperands() {
    return new MOE(this, 0, getNumberOfOperands()-1);
  }

  /**
   * Enumerate all the root operands of an instruction
   * (DOES NOT ENUMERATE CONTAINED OPERANDS OF MEMORY OPERANDS)
   * 
   * @return an enumeration of the instruction's operands.
   */
  public final OPT_OperandEnumeration getRootOperands() {
    return new ROE(this, 0, getNumberOfOperands()-1);
  }

  /**
   * Enumerate all defs (both pure defs and def/uses) of an instruction.
   * 
   * @return an enumeration of the instruction's defs.
   */
  public final OPT_OperandEnumeration getDefs() {
    return new OEDefsOnly(this, 0, getNumberOfDefs()-1);
  }

  /**
   * Enumerate all the pure defs (ie not including def/uses) of an instruction.
   * 
   * @return an enumeration of the instruction's pure defs.
   */
  public final OPT_OperandEnumeration getPureDefs() {
    return new OEDefsOnly(this, 0, getNumberOfPureDefs()-1);
  }

  /**
   * Enumerate all the pure uses (ie not including def/uses) of an instruction.
   * 
   * @return an enumeration of the instruction's pure defs.
   */
  public final OPT_OperandEnumeration getPureUses() {
    return new OEDefsOnly(this, getNumberOfDefs(), getNumberOfOperands()-1);
  }

  /**
   * Enumerate all the def/uses of an instruction.
   * 
   * @return an enumeration of the instruction's def/uses.
   */
  public final OPT_OperandEnumeration getDefUses() {
    return new OEDefsOnly(this, getNumberOfPureDefs(), getNumberOfDefs()-1);
  }

  /**
   * Enumerate all uses of an instruction (includes def/use).
   * 
   * @return an enumeration of the instruction's uses.
   */
  public final OPT_OperandEnumeration getUses() throws InlinePragma {
    int numOps = getNumberOfOperands() - 1;
    int defsEnd = 
      operator.hasVarDefs() ? numOps : operator.getNumberOfPureDefs()-1;
    return new OE(this, 0, numOps, defsEnd);
  }

  /**
   * Enumerate all root uses of an instruction.
   * 
   * @return an enumeration of the instruction's uses.
   */
  public final OPT_OperandEnumeration getRootUses() {
    return new ROE(this, getNumberOfPureDefs(), 
                   getNumberOfOperands() - 1);
  }


  /*
   * Methods dealing with the instruction's operator.
   * In the HIR and LIR these methods act simply as forwarding
   * methods to the OPT_Operator method.  In the MIR, they allow
   * us to override the operator-level defaults. Overrides mainly
   * occur from null-check combining (the null check gets folded into
   * a load/store instruction which does the check in hardware via 
   * a segv when the ptr is null), but may occur for other reasons as well.
   * In the future, we may allow overrides on the HIR/LIR as well.
   * Thus, it is generally a good idea for clients to always use the 
   * instruction variant of these methods rather than calling the 
   * corresponding method directly on the operator.
   */


  /**
   * Does the instruction represent a simple move (the value is unchanged) 
   * from one "register" location to another "register" location?
   * 
   * @return <code>true</code> if the instruction is a simple move
   *         or <code>false</code> if it is not.
   */
  public boolean isMove() { 
    return operator.isMove(); 
  }

  /**
   * Is the instruction an intraprocedural branch?
   * 
   * @return <code>true</code> if the instruction is am
   *         intraprocedural branch or <code>false</code> if it is not.
   */
  public boolean isBranch() { 
    return operator.isBranch(); 
  }

  /**
   * Is the instruction a conditional intraprocedural branch?
   * 
   * @return <code>true</code> if the instruction is a conditoonal
   *         intraprocedural branch or <code>false</code> if it is not.
   */
  public boolean isConditionalBranch() { 
    return operator.isConditionalBranch(); 
  }

  /**
   * Is this instruction a branch that has that has only two possible
   * successors?
   *
   * @return <code>true</code> if the instruction is an
   * interprocedural conditional branch with only two possible
   * outcomes (taken or not taken).  
   */
  public boolean isTwoWayBranch() { 
    // Is there a cleaner way to answer this question?
    return (isConditionalBranch() &&
            !IfCmp2.conforms(this) 
            && !MIR_CondBranch2.conforms(this)
            );
  }

  /**
   * Is the instruction an unconditional intraprocedural branch?
   * We consider various forms of switches to be unconditional
   * intraprocedural branches, even though they are multi-way branches
   * and we may not no exactly which target will be taken.  
   * This turns out to be the right thing to do, since some
   * arm of the switch will always be taken (unlike conditional branches).
   * 
   * @return <code>true</code> if the instruction is an unconditional
   *         intraprocedural branch or <code>false</code> if it is not.
   */
  public boolean isUnconditionalBranch() { 
    return operator.isUnconditionalBranch(); 
  }

  /**
   * Is the instruction a direct intraprocedural branch?
   * In the HIR and LIR we consider switches to be direct branches, 
   * because their targets are known precisely.
   * 
   * @return <code>true</code> if the instruction is a direct
   *         intraprocedural branch or <code>false</code> if it is not.
   */
  public boolean isDirectBranch() { 
    return operator.isDirectBranch(); 
  }

  /**
   * Is the instruction an indirect intraprocedural branch?
   * 
   * @return <code>true</code> if the instruction is an indirect
   *         interprocedural branch or <code>false</code> if it is not.
   */
  public boolean isIndirectBranch() { 
    return operator.isIndirectBranch(); 
  }

  /**
   * Is the instruction a call (one kind of interprocedural branch)?
   * 
   * @return <code>true</code> if the instruction is a call
   *         or <code>false</code> if it is not.
   */
  public boolean isCall() { 
    return operator.isCall(); 
  }

  /**
   * Is the instruction a conditional call? 
   * We only allow conditional calls in the MIR, since they
   * tend to only be directly implementable on some architecutres.
   * 
   * @return <code>true</code> if the instruction is a 
   *         conditional call or <code>false</code> if it is not.
   */
  public boolean isConditionalCall() { 
    return operator.isConditionalCall(); 
  }

  /**
   * Is the instruction an unconditional call?
   * Really only an interesting question in the MIR, since
   * it is by definition true for all HIR and LIR calls.
   * 
   * @return <code>true</code> if the instruction is an unconditional
   *         call or <code>false</code> if it is not.
   */
  public boolean isUnconditionalCall() { 
    return operator.isUnconditionalCall(); 
  }

  /**
   * Is the instruction a direct call?
   * Only interesting on the MIR.  In the HIR and LIR we pretend that
   * all calls are "direct" even though most of them aren't.
   * 
   * @return <code>true</code> if the instruction is a direct call
   *         or <code>false</code> if it is not.
   */
  public boolean isDirectCalll() { 
    return operator.isDirectCall(); 
  }

  /**
   * Is the instruction an indirect call?
   * Only interesting on the MIR.  In the HIR and LIR we pretend that
   * all calls are "direct" even though most of them aren't.
   * 
   * @return <code>true</code> if the instruction is an indirect call
   *         or <code>false</code> if it is not.
   */
  public boolean isIndirectCall() { 
    return operator.isIndirectCall(); 
  }

  /**
   * Is the instruction an explicit load of a finite set of values from
   * a finite set of memory locations (load, load multiple, _not_ call)?
   * 
   * @return <code>true</code> if the instruction is an explicit load
   *         or <code>false</code> if it is not.
   */
  public boolean isExplicitLoad() { 
    return operator.isExplicitLoad(); 
  }

  /**
   * Should the instruction be treated as a load from some unknown location(s)
   * for the purposes of scheduling and/or modeling the memory subsystem?
   * 
   * @return <code>true</code> if the instruction is an implicit load
   *         or <code>false</code> if it is not.
   */
  public boolean isImplicitLoad() { 
    return operator.isImplicitLoad(); 
  }

  /** 
   * @deprecated use {@link #isExplicitLoad()} or {@link #isImplicitLoad()}
   */ 
  public boolean isLoad() { 
    return isImplicitLoad(); 
  }
  

  /**
   * Is the instruction an explicit store of a finite set of values to
   * a finite set of memory locations (store, store multiple, _not_ call)?
   * 
   * @return <code>true</code> if the instruction is an explicit store
   *         or <code>false</code> if it is not.
   */
  public boolean isExplicitStore() { 
    return operator.isExplicitStore(); 
  }

  /**
   * Should the instruction be treated as a store to some unknown location(s)
   * for the purposes of scheduling and/or modeling the memory subsystem?
   * 
   * @return <code>true</code> if the instruction is an implicit store
   *         or <code>false</code> if it is not.
   */
  public boolean isImplicitStore() { 
    return operator.isImplicitStore(); 
  }

  /** 
   * @deprecated use {@link #isExplicitStore()} or {@link #isImplicitStore()}
   */ 
  public boolean isStore() { 
    return isImplicitStore(); 
  }

  /**
   * Is the instruction a throw of a Java exception?
   * 
   * @return <code>true</code> if the instruction is a throw
   *         or <code>false</code> if it is not.
   */
  public boolean isThrow() { 
    return operator.isThrow(); 
  }

  /**
   * Is the instruction a PEI (Potentially Excepting Instruction)?
   * 
   * @return <code>true</code> if the instruction is a PEI
   *         or <code>false</code> if it is not.
   */
  public boolean isPEI() { 
    // The operator level default may be overriden by instr specific info.
    if ((operatorInfo & OI_PEI_VALID) != 0) {
      return (operatorInfo & OI_PEI) != 0;
    } else {
      return operator.isPEI();
    }
  }

  /**
   * Has the instruction been explictly marked as a a PEI (Potentially Excepting Instruction)?
   * 
   * @return <code>true</code> if the instruction is explicitly marked as a PEI
   *         or <code>false</code> if it is not.
   */
  public boolean isMarkedAsPEI() { 
    if ((operatorInfo & OI_PEI_VALID) != 0) {
      return (operatorInfo & OI_PEI) != 0;
    } else {
      return false;
    }
  }

  /**
   * Is the instruction a potential GC point?
   * 
   * @return <code>true</code> if the instruction is a potential 
   *         GC point or <code>false</code> if it is not.
   */
  public boolean isGCPoint() { 
    // The operator level default may be overridden by instr specific info.
    if ((operatorInfo & OI_GC_VALID) != 0) {
      return (operatorInfo & OI_GC) != 0;
    } else {
      return operator.isGCPoint();
    }
  }

  /**
   * Is the instruction a potential thread switch point?
   * 
   * @return <code>true</code> if the instruction is a potential 
   *         thread switch point or <code>false</code> if it is not.
   */
  public boolean isTSPoint() { 
    // Currently the same as asking if the instruction is a GCPoint, but
    // give it a separate name for documentation & future flexibility
    return isGCPoint();
  }

  /**
   * Is the instruction a compare (val,val) => condition?
   * 
   * @return <code>true</code> if the instruction is a compare
   *         or <code>false</code> if it is not.
   */
  public boolean isCompare() { 
    return operator.isCompare(); 
  }

  /**
   * Is the instruction an actual memory allocation instruction 
   * (NEW, NEWARRAY, etc)?
   * 
   * @return <code>true</code> if the instruction is an allocation
   *         or <code>false</code> if it is not.
   */
  public boolean isAllocation() { 
    return operator.isAllocation(); 
  }

  /**
   * Is the instruction a return (interprocedural branch)?
   * 
   * @return <code>true</code> if the instruction is a return
   *         or <code>false</code> if it is not.
   */
  public boolean isReturn() { 
    return operator.isReturn(); 
  }

  /**
   * Is the instruction an acquire (monitorenter/lock)?
   * 
   * @return <code>true</code> if the instruction is an acquire
   *         or <code>false</code> if it is not.
   */
  public boolean isAcquire() { 
    return operator.isAcquire(); 
  }

  /**
   * Is the instruction a release (monitorexit/unlock)?
   * 
   * @return <code>true</code> if the instruction is a release
   *         or <code>false</code> if it is not.
   */
  public boolean isRelease() { 
    return operator.isRelease(); 
  }

  /**
   * Could the instruction either directly or indirectly 
   * cause dynamic class loading?
   * 
   * @return <code>true</code> if the instruction is a dynamic linking point
   *         or <code>false</code> if it is not.
   */
  public boolean isDynamicLinkingPoint() { 
    return operator.isDynamicLinkingPoint(); 
  }

  /**
   * Is the instruction a yield point?
   * 
   * @return <code>true</code> if the instruction is a yield point
   *          or <code>false</code> if it is not.
   */
  public boolean isYieldPoint() { 
    return operator.isYieldPoint();
  }

  /**
   * Record that this instruction is not a PEI. 
   * Leave GCPoint status (if any) unchanged.
   */
  public void markAsNonPEI() {
    operatorInfo &= ~OI_PEI;
    operatorInfo |= OI_PEI_VALID;
  }

  /**
   * NOTE: ONLY FOR USE ON MIR INSTRUCTIONS!!!!
   * Record that this instruction is a PEI.
   * Note that marking as a PEI implies marking as GCpoint.
   */
  public void markAsPEI() {
    if (VM.VerifyAssertions) VM._assert(getOpcode() > MIR_START_opcode);
    operatorInfo |= (OI_PEI_VALID | OI_PEI | OI_GC_VALID | OI_GC);
  }

  /**
   * NOTE: ONLY FOR USE ON MIR INSTRUCTIONS!!!!
   * Record that this instruction does not represent a potential GC point.
   * Leave exception state (if any) unchanged.
   */
  public void markAsNonGCPoint() {
    if (VM.VerifyAssertions) VM._assert(getOpcode() > MIR_START_opcode);
    operatorInfo &= ~OI_GC;
    operatorInfo |= OI_GC_VALID;
  }

  /**
   * NOTE: ONLY FOR USE ON MIR INSTRUCTIONS!!!!
   * Record that this instruction is a potential GC point.
   * Leave PEI status (if any) unchanged.
   */
  public void markAsGCPoint() {
    if (VM.VerifyAssertions) VM._assert(getOpcode() > MIR_START_opcode);
    operatorInfo |= (OI_GC_VALID | OI_GC);
  }

  /**
   * NOTE: ONLY FOR USE ON MIR INSTRUCTIONS!!!!
   * Mark this instruction as being neither an exception or GC point.
   */
  public void markAsNonPEINonGCPoint() {
    if (VM.VerifyAssertions) VM._assert(getOpcode() > MIR_START_opcode);
    operatorInfo &= ~(OI_PEI | OI_GC);
    operatorInfo |= (OI_PEI_VALID | OI_GC_VALID);
  }

  /**
   * Is the first mark bit of the instruction set?
   * 
   * @return <code>true</code> if the first mark bit is set
   *         or <code>false</code> if it is not.
   */
  boolean isMarked1() { 
    return (operatorInfo & MARK1) != 0; 
  }

  /**
   * Is the second mark bit of the instruction set?
   * 
   * @return <code>true</code> if the first mark bit is set
   *         or <code>false</code> if it is not.
   */
  boolean isMarked2() { 
    return (operatorInfo & MARK2) != 0; 
  }

  /**
   * Set the first mark bit of the instruction.
   */
  void setMark1() { 
    operatorInfo |= MARK1;              
  }

  /**
   * Set the second mark bit of the instruction.
   */
  void setMark2() { 
    operatorInfo |= MARK2;              
  }

  /**
   * Clear the first mark bit of the instruction.
   */
  void clearMark1() { 
    operatorInfo &= ~MARK1;             
  }

  /**
   * Clear the second mark bit of the instruction.
   */
  void clearMark2() { 
    operatorInfo &= ~MARK2;             
  }

  /**
   * Return the probability (in the range 0.0 - 1.0) that this two-way
   * branch instruction is taken (as opposed to falling through).
   *
   * @return The probability that the branch is taken.  
   */
  public float getBranchProbability() {
    if (VM.VerifyAssertions) VM._assert(isTwoWayBranch());
    return BranchProfileCarrier.getBranchProfile(this).takenProbability;
  }

  /**
   * Record the probability (in the range 0.0 - 1.0) that this two-way
   * branch instruction is taken (as opposed to falling through).
   *
   * @param takenProbability    The probability that the branch is taken.  
   */
  public void setBranchProbability(float takenProbability) {
    if (VM.VerifyAssertions) VM._assert(isTwoWayBranch());
    BranchProfileCarrier.getBranchProfile(this).takenProbability = 
      takenProbability;
  } 

  /**
   * Invert the probabilty of this branch being taken.  This method
   * should be called on a branch instruction when its condition is
   * reversed using flipCode().
   * 
   */
  public void flipBranchProbability() {
    if (VM.VerifyAssertions) VM._assert(isTwoWayBranch());
    setBranchProbability(1.0f-getBranchProbability());
  }

  /**
   * Returns the basic block jumped to by this BRANCH instruction.
   * TODO: Not all types of branches supported yet.
   * 
   * @return the target of this branch instruction
   */
  public OPT_BasicBlock getBranchTarget() {
    switch (getOpcode()) {
    case GOTO_opcode:
      return Goto.getTarget(this).target.getBasicBlock();

    case INT_IFCMP_opcode:
    case REF_IFCMP_opcode:
    case LONG_IFCMP_opcode:
    case FLOAT_IFCMPL_opcode:
    case FLOAT_IFCMPG_opcode:
    case DOUBLE_IFCMPL_opcode:
    case DOUBLE_IFCMPG_opcode:
      return IfCmp.getTarget(this).target.getBasicBlock();

    case IG_CLASS_TEST_opcode:
    case IG_METHOD_TEST_opcode:
    case IG_PATCH_POINT_opcode:
      return InlineGuard.getTarget(this).target.getBasicBlock();
      
    default:
      if (MIR_Branch.conforms(this)) {
        return MIR_Branch.getTarget(this).target.getBasicBlock();
      } else if (MIR_CondBranch.conforms(this)) {
        return MIR_CondBranch.getTarget(this).target.getBasicBlock();
      } else

      throw new OPT_OptimizingCompilerException("getBranchTarget()",
                                                "operator not implemented",
                                                operator.toString());
      
    }
  }

  /**
   * Return an enumeration of the basic blocks that are targets of this
   * branch instruction.
   * 
   * @return the targets of this branch instruction
   */
  public OPT_BasicBlockEnumeration getBranchTargets() {
    int n = getNumberOfOperands();
    OPT_BasicBlock.ComputedBBEnum e = new OPT_BasicBlock.ComputedBBEnum(n);

    switch (getOpcode()) {
    case GOTO_opcode:
      {
        OPT_BranchOperand tgt = Goto.getTarget(this);
        e.addElement(tgt.target.getBasicBlock());
      }
      break;

    case INT_IFCMP2_opcode:
      e.addElement(IfCmp2.getTarget1(this).target.getBasicBlock());
      e.addPossiblyDuplicateElement(IfCmp2.getTarget2(this).target.getBasicBlock());
      break;

    case INT_IFCMP_opcode:
    case REF_IFCMP_opcode:
    case LONG_IFCMP_opcode:
    case FLOAT_IFCMPL_opcode:
    case FLOAT_IFCMPG_opcode:
    case DOUBLE_IFCMPL_opcode:
    case DOUBLE_IFCMPG_opcode:
      e.addElement(IfCmp.getTarget(this).target.getBasicBlock());
      break;

    case IG_PATCH_POINT_opcode:
    case IG_CLASS_TEST_opcode:
    case IG_METHOD_TEST_opcode:
      e.addElement(InlineGuard.getTarget(this).target.getBasicBlock());
      break;

    case TABLESWITCH_opcode:
      e.addElement(TableSwitch.getDefault(this).target.getBasicBlock());
      for(int i = 0; i < TableSwitch.getNumberOfTargets(this); i++) 
        e.addPossiblyDuplicateElement(TableSwitch.getTarget(this, i).target.getBasicBlock());
      break;

    case LOWTABLESWITCH_opcode:
      for(int i = 0; i < LowTableSwitch.getNumberOfTargets(this); i++) 
        e.addPossiblyDuplicateElement(LowTableSwitch.getTarget(this, i).target.getBasicBlock());
      break;
      
    case LOOKUPSWITCH_opcode:
      e.addElement(LookupSwitch.getDefault(this).target.getBasicBlock());
      for(int i = 0; i < LookupSwitch.getNumberOfTargets(this); i++) 
        e.addPossiblyDuplicateElement(LookupSwitch.getTarget(this, i).target.getBasicBlock());
      break;

    default:
      if (MIR_Branch.conforms(this)) {
        e.addElement(MIR_Branch.getTarget(this).target.getBasicBlock());
      } else if (MIR_CondBranch.conforms(this)) {
        e.addElement(MIR_CondBranch.getTarget(this).target.getBasicBlock());
      } else if (MIR_CondBranch2.conforms(this)) {
        e.addElement(MIR_CondBranch2.getTarget1(this).target.getBasicBlock());
        e.addPossiblyDuplicateElement(MIR_CondBranch2.getTarget2(this).target.getBasicBlock());
      //-#if RVM_FOR_IA32
      // TODO: should factor the MIR-specific stuff into an arch-specific
      // file.  Too lazy to do it today (SJF).
      } else if (MIR_LowTableSwitch.conforms(this)) {
          for(int i = 0; i < MIR_LowTableSwitch.getNumberOfTargets(this); i++) 
            e.addPossiblyDuplicateElement(MIR_LowTableSwitch.getTarget(this,i).
                                          target.getBasicBlock());
      //-#endif
      } else if (MIR_CondBranch2.conforms(this)) {
        throw new OPT_OptimizingCompilerException("getBranchTargets()",
                                                  "operator not implemented",
                                                  operator().toString());
      } else

      throw new OPT_OptimizingCompilerException("getBranchTargets()",
                                                "operator not implemented",
                                                operator().toString());
    }

    return e;
  }


  /**
   * Return true if this instruction is the first instruction in a
   * basic block.  By convention (construction) every basic block starts
   * with a label instruction and a label instruction only appears at
   * the start of a basic block
   * 
   * @return <code>true</code> if the instruction is the first instruction
   *         in its basic block or <code>false</code> if it is not.
   */
  public boolean isBbFirst() {
    return operator == LABEL;
  }

  /**
   * Return true if this instruction is the last instruction in a
   * basic block.  By convention (construction) every basic block ends
   * with a BBEND instruction and a BBEND instruction only appears at the
   * end of a basic block
   * 
   * @return <code>true</code> if the instruction is the last instruction
   *         in its basic block or <code>false</code> if it is not.
   */
  public boolean isBbLast() {
    return operator == BBEND;
  }

  /**
   * Mainly intended for assertion checking;  returns true if the instruction
   * is expected to appear on the "inside" of a basic block, false otherwise.
   * 
   * @return <code>true</code> if the instruction is expected to appear
   *         on the inside (not first or last) of its basic block
   *         or <code>false</code> if it is expected to be a first/last
   *         instruction.
   */
  public boolean isBbInside() {
    return !(operator == LABEL || operator == BBEND);
  }



  /*
   * Primitive Instruction List manipulation routines.
   * All of these operations assume that the IR invariants 
   * (mostly well-formedness of the data structures) are true
   * when they are invoked. 
   * Effectively, the IR invariants are defined by OPT_IR.verify().
   * These primitive functions will locally check their invariants
   * when OPT_IR.PARANOID is true.
   * If the precondition is met, then the IR invariants will be true when
   * the operation completes.
   */


  /**
   * Insertion: Insert newInstr immediately after this in the 
   * instruction stream.
   * Can't insert after a BBEND instruction, since it must be the last 
   * instruction in its basic block.
   *
   * @param newInstr the instruction to insert, must not be a BBEND or in an 
   *                 instruction list already.
   */
  public void insertFront(OPT_Instruction newInstr) {
    insertAfter(newInstr);
  }

  /**
   * Insertion: Insert newInstr immediately after this in the 
   * instruction stream.
   * Can't insert after a BBEND instruction, since it must be the last 
   * instruction in its basic block.
   *
   * @param newInstr the instruction to insert, must not be in an 
   *                 instruction list already.
   */
  public void insertAfter(OPT_Instruction newInstr) {
    if (OPT_IR.PARANOID) {
      isForwardLinked();
      newInstr.isNotLinked();
      VM._assert(!isBbLast(), "cannot insert after last instruction of block");
    }

    // set position unless someone else has
    if (newInstr.position == null) {
        newInstr.position = position;
        newInstr.bcIndex = bcIndex;
    }

    // Splice newInstr into the doubly linked list of instructions
    OPT_Instruction old_next = next;
    next = newInstr;
    newInstr.prev = this;
    newInstr.next = old_next;
    old_next.prev = newInstr;
  }


  /**
   * Insertion: Insert newInstr immediately before this in the 
   * instruction stream.
   * Can't insert before a LABEL instruction, since it must be the last 
   * instruction in its basic block.
   *
   * @param newInstr the instruction to insert, must not be in 
   *                 an instruction list already.
   */
  public void insertBack(OPT_Instruction newInstr) {
    insertBefore(newInstr);
  }

  /**
   * Insertion: Insert newInstr immediately before this in the 
   * instruction stream.
   * Can't insert before a LABEL instruction, since it must be the last 
   * instruction in its basic block.
   *
   * @param newInstr the instruction to insert, must not be in 
   *                 an instruction list already.
   */
  public void insertBefore(OPT_Instruction newInstr) {
    if (OPT_IR.PARANOID) {
      isBackwardLinked();
      newInstr.isNotLinked();
      VM._assert(!isBbFirst(), "Cannot insert before first instruction of block");
    }

    // set position unless someone else has
    if (newInstr.position == null) {
        newInstr.position = position;
        newInstr.bcIndex = bcIndex;
    }

    // Splice newInstr into the doubly linked list of instructions
    OPT_Instruction old_prev = prev;
    prev = newInstr;
    newInstr.next = this;
    newInstr.prev = old_prev;
    old_prev.next = newInstr;
  }


  /**
   * Replacement: Replace this with newInstr.
   * We could allow replacement of first & last instrs in the basic block,
   * but it would be a fair amount of work to update everything, and probably
   * isn't useful, so we'll simply disallow it for now.
   *
   * @param newInstr  the replacement instruction must not be in an 
   *                  instruction list already and must not be a 
   *                  LABEL or BBEND instruction.
   */
  public void replace(OPT_Instruction newInstr) {
    if (OPT_IR.PARANOID) {
      isLinked();
      newInstr.isNotLinked();
      VM._assert(isBbInside(), "Can only replace BbInside instructions");
    }

    OPT_Instruction old_prev = prev;
    OPT_Instruction old_next = next;

    // Splice newInstr into the doubly linked list of instructions
    newInstr.prev = old_prev;
    old_prev.next = newInstr;
    newInstr.next = old_next;
    old_next.prev = newInstr;
    next = null; prev = null;
  }


  /**
   * Removal: Remove this from the instruction stream.
   *
   *  We currently forbid the removal of LABEL instructions to avoid 
   *  problems updating branch instructions that reference the label.
   *  We also outlaw removal of BBEND instructions.
   *  <p>
   *  NOTE: We allow the removal of branch instructions, but don't update the
   *  CFG data structure.....right now we just assume the caller knows what
   *  they are doing and takes care of it.
   *  <p>
   *  NB: execution of this method nulls out the prev & next fields of this
   *
   * @return the previous instruction in the instruction stream
   */
  public OPT_Instruction remove() {
    if (OPT_IR.PARANOID) {
      isLinked();
      VM._assert(!isBbFirst() && !isBbLast(), 
                "Removal of first/last instructions in block not supported");
    }

    // Splice this out of instr list
    OPT_Instruction Prev = prev, Next = next;
    Prev.next = Next;
    Next.prev = Prev;
    next = null;
    prev = null;
    return Prev;
  }

  /*
   * Helper routines to verify instruction list invariants.
   * Invocations to these functions are guarded by OPT_IR.PARANOID and thus 
   * the calls to VM.Assert don't need to be guarded by VM.VerifyAssertions.
   */
  private void isLinked() {
    VM._assert(prev.next == this, "is_linked: failure (1)");
    VM._assert(next.prev == this, "is_linked: failure (2)");
  }
  private void isBackwardLinked() {
    VM._assert(prev.next == this, "is_backward_linked: failure (1)");
    // OK if next is null (IR under construction)
    VM._assert(next == null || next.prev == this, 
              "is_backward_linked: failure (2)");
  }
  private void isForwardLinked() {
    // OK if prev is null (IR under construction)
    VM._assert(prev == null || prev.next == this, 
              "is_forward_linked: failure (1)");
    VM._assert(next.prev == this, "is_forward_linked (2)");
  }
  private void isNotLinked() {
    VM._assert(prev == null && next == null, "is_not_linked: failure (1)");
  }

  
  /*
   * Implementation: Operand enumeration classes
   */
  // Shared functionality
  private static abstract class BASE_OE implements OPT_OperandEnumeration {
    protected OPT_Instruction instr;
    protected int i;
    protected int end;
    protected OPT_Operand nextElem;
    final static protected boolean DEBUG=false;
    private BASE_OE() {} 
    protected BASE_OE(OPT_Instruction instr, int start, int end) {
      this.instr = instr;
      this.i = start - 1;
      this.end = end;
      this.nextElem = null;
    }
    public final boolean hasMoreElements() { return nextElem != null; }
    public final Object nextElement() { return next(); }
    public final OPT_Operand next() {
      OPT_Operand temp = nextElem;
      if (temp == null) fail();
      advance();
      if (DEBUG) {  System.out.println(" next() returning: "+ temp);   }
      return temp;
    }
    protected abstract void advance();
    private static void fail() throws NoInlinePragma {
      throw new java.util.NoSuchElementException("OperandEnumerator");
    }
  }

  // enumerate leaf operands in the given ranges
  private static final class OE extends BASE_OE {
    private int defEnd;
    private OPT_Operand deferredMOReg;
    public OE(OPT_Instruction instr, int start, int end, int defEnd) {
      super(instr, start, end);
      this.defEnd = defEnd;
      if (DEBUG) {
        System.out.println(" --> OE called with inst\n"+ instr
                           +"\n start: "+ start +", end: "+ end
                           +", defEnd: "+ defEnd);
      }
      advance();
    }
    protected final void advance() {
      if (deferredMOReg != null) {
        nextElem = deferredMOReg;
        deferredMOReg = null;
      } else {
        OPT_Operand temp;
        do {
          i++; 
          if (i > end) { 
            temp = null; 
            break;
          }
          temp = instr.getOperand(i);
          if (temp instanceof OPT_MemoryOperand) {
            OPT_MemoryOperand mo = (OPT_MemoryOperand)temp;
            if (mo.base != null) {
              temp = mo.base;
              deferredMOReg = mo.index;
              break;
            } else {
              temp = mo.index;
            }
          } else {
            if (i <= defEnd) {
              // if i is in the defs, ignore non memory operands
              temp = null;
            }
          }
        } while (temp == null);
        nextElem = temp;
      }
    }
  }

  // Enumerate the def operands of an instruction (ignores memory
  // operands, since the contained operands of a MO are uses).
  private static final class OEDefsOnly extends BASE_OE {
    public OEDefsOnly(OPT_Instruction instr, int start, int end) {
      super(instr, start, end);
      if (DEBUG) {
        System.out.println(" --> OEDefsOnly called with inst\n"+ instr
                           +"\n start: "+ start +", end: "+ end);
      }
      advance();
    }
    protected final void advance() {
      OPT_Operand temp;
      do {
        i++;
        if (i > end) {
          temp = null;
          break;
        }
        temp = instr.getOperand(i);
      } while (temp == null || temp instanceof OPT_MemoryOperand);
      nextElem = temp;
      // (i>end and nextElem == null) or nextElem is neither memory nor null
    }
  }


  // Enumerate the memory operands of an instruction
  private static final class MOE extends BASE_OE {
    public MOE(OPT_Instruction instr, int start, int end) {
      super(instr, start, end);
      if (DEBUG) {
        System.out.println(" --> MOE called with inst\n"+ instr
                           +"\n start: "+ start +", end: "+ end);
      }
      advance();
    }
    protected final void advance() {
      OPT_Operand temp;
      do {
        i++;
        if (i > end) {
          temp = null;
          break;
        }
        temp = instr.getOperand(i);
      } while (!(temp instanceof OPT_MemoryOperand));
      nextElem = temp;
      // (i>end and nextElem == null) or nextElem is memory
    }
  }

  // Enumerate the root operands of an instruction
  private static final class ROE extends BASE_OE {
    public ROE(OPT_Instruction instr, int start, int end) {
      super(instr, start, end);
      if (DEBUG) {
        System.out.println(" --> ROE called with inst\n"+ instr
                           +"\n start: "+ start +", end: "+ end);
      }
      advance();
    }
    protected final void advance() {
      OPT_Operand temp;
      do {
        i++;
        if (i > end) {
          temp = null;
          break;
        }
        temp = instr.getOperand(i);
      } while (temp == null);
      nextElem = temp;
      // (i>end and nextElem == null) or nextElem != null
    }
  }


  /*
   * The following operand functions are really only meant to be
   * used in the classes (such as instruction formats) that
   * collaborate in the low-level implementation of the IR.
   * General clients are strongly discouraged from using them.
   */

  /**
   * NOTE: It is incorrect to use getOperand with a constant argument
   * outside of the automatically generated code in OPT_Operators.
   * The only approved direct use of getOperand is in a loop over
   * some subset of an instructions operands (all of them, all uses, all defs).
   *
   * @param i which operand to return
   * @return the ith operand
   */
  public OPT_Operand getOperand(int i) {
    return ops[i];
  }

  /**
   * NOTE: It is incorrect to use getClearOperand with a constant argument
   * outside of the automatically generated code in OPT_Operators.
   * The only approved direct use of getOperand is in a loop over
   * some subset of an instructions operands (all of them, all uses, all defs).
   *
   * @param i which operand to return
   * @return the ith operand detatching it from the instruction
   */
  public OPT_Operand getClearOperand(int i) {
    OPT_Operand o = ops[i];
    if (o != null)
      o.instruction = null;
    return o;
  }

  /**
   * NOTE: It is incorrect to use putOperand with a constant argument
   * outside of the automatically generated code in OPT_Operators.
   * The only approved direct use of getOperand is in a loop over
   * some subset of an instruction's operands (all of them, all uses, all defs).
   * 
   * @param i which operand to set
   * @param op the operand to set it to
   */
  public void putOperand(int i, OPT_Operand op) {
    if (op == null) {
      ops[i] = null;
    } else {
      // TODO: Replace this silly copying code with an assertion that operands 
      //       are not shared between instructions and force people to be
      //       more careful!
      if (op.instruction != null) {
        op = outOfLineCopy(op);
      }
      op.instruction = this;
      ops[i] = op;
    }
  }
  private OPT_Operand outOfLineCopy(OPT_Operand op) throws NoInlinePragma {
    return op.copy();
  }

  /**
   * Enlarge the number of operands in this instruction, if necessary.
   * Only meant to be used by InstructionFormat classes.
   * 
   * @param newSize the new minimum number of operands.
   */
  void resizeNumberOfOperands(int newSize) {
    int oldSize = ops.length;
    if (oldSize != newSize) {
      OPT_Operand newOps[] = new OPT_Operand[newSize];
      int min = oldSize;
      if (newSize < oldSize) {
        min = newSize;
      }
      for (int i = 0; i < min; i ++) {
        newOps[i] = ops[i];
      }
      ops = newOps;
    }
  }

  /**
   * For IR internal use only; general clients should use
   * {@link #nextInstructionInCodeOrder()}.
   * 
   * @return the contents of {@link #next}
   */
  OPT_Instruction getNext() { 
    return next; 
  }

  /**
   * For IR internal use only;   general clients should always use higer level
   * mutation functions. 
   * Set the {@link #next} field of the instruction.
   * 
   * @param n the new value for next
   */
  protected void setNext(OPT_Instruction n) { 
    next = n; 
  }

  /**
   * For IR internal use only; General clients should use
   * {@link #prevInstructionInCodeOrder()}.
   * 
   * @return the contents of {@link #prev}
   */
  public OPT_Instruction getPrev() { 
    return prev; 
  }

  /**
   * For IR internal use only;   general clients should always use higer level
   * mutation functions. 
   * Set the {@link #prev} field of the instruction.
   * 
   * @param p the new value for prev
   */
  protected void setPrev(OPT_Instruction p) { 
    prev = p; 
  }

  /**
   * For IR internal use only;   general clients should always use higer level
   * mutation functions. 
   * Clear the {@link #prev} and {@link #next} fields of the instruction.
   * 
   */
  protected void clearLinks() {
    next = null;
    prev = null;
  }

  /**
   * For IR internal use only;   general clients should always use higer level
   * mutation functions. 
   * Link this and other together by setting this's {@link #next} field to
   * point to other and other's {@link #prev} field to point to this.
   * 
   * @param other the instruction to link with.
   */
  protected void linkWithNext(OPT_Instruction other) {
    next = other;
    other.prev = this;
  }

  /**
   * Temp kludge for BURS as we bring the ir package on line
   * @deprecated
   */
  public void BURS_KLUDGE_linkWithNext(OPT_Instruction other) {
    linkWithNext(other);
  }

  /**
   * Might this instruction be a load from a field that is declared 
   * to be volatile?
   *
   * @return <code>true</code> if the instruction might be a load
   *         from a volatile field or <code>false</code> if it 
   *         cannot be a load from a volatile field
   */
  public boolean mayBeVolatileFieldLoad() {
    if (OPT_LocalCSE.isLoadInstruction(this)) {
      return LocationCarrier.getLocation(this).mayBeVolatile();
    }
    return false;
  }
}
