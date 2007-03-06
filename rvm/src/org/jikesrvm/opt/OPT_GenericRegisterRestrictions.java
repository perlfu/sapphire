/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt;

import org.jikesrvm.*;
import org.jikesrvm.ArchitectureSpecific.OPT_PhysicalRegisterSet;
import org.jikesrvm.opt.ir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Enumeration;
import static org.jikesrvm.opt.ir.OPT_Operators.*;

/**
 * An instance of this class provides a mapping from symbolic register to
 * a set of restricted registers.
 *
 * Each architcture will subclass this in a class
 * OPT_RegisterRestrictions.
 * 
 * @author Stephen Fink
 */
public abstract class OPT_GenericRegisterRestrictions {
  // for each symbolic register, the set of physical registers that are
  // illegal for assignment
  private final HashMap<OPT_Register,RestrictedRegisterSet> hash =
    new HashMap<OPT_Register,RestrictedRegisterSet>();

  // a set of symbolic registers that must not be spilled.
  private final HashSet<OPT_Register> noSpill = new HashSet<OPT_Register>();

  protected final OPT_PhysicalRegisterSet phys;

  /**
   * Default Constructor
   */
  protected OPT_GenericRegisterRestrictions(OPT_PhysicalRegisterSet phys) {
    this.phys = phys;
  }

  /**
   * Record that the register allocator must not spill a symbolic
   * register.
   */
  protected final void noteMustNotSpill(OPT_Register r) {
    noSpill.add(r);
  }

  /**
   * Is spilling a register forbidden?
   */
  public final boolean mustNotSpill(OPT_Register r) {
    return noSpill.contains(r);
  }

  /**
   * Record all the register restrictions dictated by an IR.
   *
   * PRECONDITION: the instructions in each basic block are numbered in
   * increasing order before calling this.  The number for each
   * instruction is stored in its <code>scratch</code> field.
   */
  public final void init(OPT_IR ir) {
    // process each basic block
    for (Enumeration<OPT_BasicBlock> e = ir.getBasicBlocks(); e.hasMoreElements(); ) {
      OPT_BasicBlock b = e.nextElement();
      processBlock(b);
    }
  }

  /**
   * Record all the register restrictions dictated by live ranges on a
   * particular basic block.
   *
   * PRECONDITION: the instructions in each basic block are numbered in
   * increasing order before calling this.  The number for each
   * instruction is stored in its <code>scratch</code> field.
   */
  private void processBlock(OPT_BasicBlock bb) {
    ArrayList<OPT_LiveIntervalElement> symbolic =
      new ArrayList<OPT_LiveIntervalElement>(20);
    ArrayList<OPT_LiveIntervalElement> physical =
      new ArrayList<OPT_LiveIntervalElement>(20);
    
    // 1. walk through the live intervals and identify which correspond to
    // physical and symbolic registers
    for (Enumeration<OPT_LiveIntervalElement> e = bb.enumerateLiveIntervals(); e.hasMoreElements();) {
      OPT_LiveIntervalElement li = e.nextElement();
      OPT_Register r = li.getRegister();
      if (r.isPhysical()) {
        if (r.isVolatile() || r.isNonVolatile()) {
          physical.add(li);
        }
      } else {
        symbolic.add(li);
      }
    }

    // 2. walk through the live intervals for physical registers.  For
    // each such interval, record the conflicts where the live range
    // overlaps a live range for a symbolic register.
    for (OPT_LiveIntervalElement phys : physical) {
      for (OPT_LiveIntervalElement symb : symbolic) {
        if (overlaps(phys,symb)) {
          addRestriction(symb.getRegister(),phys.getRegister());
        }
      }
    }

    // 3. Volatile registers used by CALL instructions do not appear in
    // the liveness information.  Handle CALL instructions as a special
    // case.
    for (OPT_InstructionEnumeration ie = bb.forwardInstrEnumerator();
         ie.hasMoreElements(); ) {
      OPT_Instruction s = ie.next();
      if (s.operator.isCall() && s.operator != CALL_SAVE_VOLATILE) {
        for (OPT_LiveIntervalElement symb : symbolic) {
          if (contains(symb,s.scratch)) {
            forbidAllVolatiles(symb.getRegister());
          }
        }
      }

      // Before OSR points, we need to save all FPRs, 
      // On OptExecStateExtractor, all GPRs have to be recovered, 
      // but not FPRS.
      //
      if (s.operator == YIELDPOINT_OSR) {
        for (OPT_LiveIntervalElement symb : symbolic) {
          if (symb.getRegister().isFloatingPoint()) {
            if (contains(symb,s.scratch)) {
              forbidAllVolatiles(symb.getRegister());
            }
          }
        }       
      }
    }

    // 3. architecture-specific restrictions
    addArchRestrictions(bb,symbolic);
  }

  /**
   * Add architecture-specific register restrictions for a basic block.
   * Override as needed.
   *
   * @param bb the basic block 
   * @param symbolics the live intervals for symbolic registers on this
   * block
   */
  public void addArchRestrictions(OPT_BasicBlock bb, ArrayList<OPT_LiveIntervalElement> symbolics) {}

  /**
   * Does a live range R contain an instruction with number n?
   * 
   * PRECONDITION: the instructions in each basic block are numbered in
   * increasing order before calling this.  The number for each
   * instruction is stored in its <code>scratch</code> field.
   */
  protected final boolean contains(OPT_LiveIntervalElement R, int n) {
    int begin = -1;
    int end = Integer.MAX_VALUE;
    if (R.getBegin() != null) {
      begin = R.getBegin().scratch;
    }
    if (R.getEnd() != null) {
      end= R.getEnd().scratch;
    }

    return ((begin<=n) && (n<=end));
  }

  /**
   * Do two live ranges overlap?
   *
   * PRECONDITION: the instructions in each basic block are numbered in
   * increasing order before calling this.  The number for each
   * instruction is stored in its <code>scratch</code> field.
   */
  private boolean overlaps(OPT_LiveIntervalElement li1,
                           OPT_LiveIntervalElement li2) {
    // Under the following conditions: the live ranges do NOT overlap:
    // 1. begin2 >= end1 > -1
    // 2. begin1 >= end2 > -1
    // Under all other cases, the ranges overlap

    int begin1  = -1;
    int end1    = -1;
    int begin2  = -1;
    int end2    = -1;

    if (li1.getBegin() != null) {
      begin1 = li1.getBegin().scratch;
    }
    if (li2.getEnd() != null) {
      end2 = li2.getEnd().scratch;
    }
    if (end2 <= begin1 && end2 > -1) return false;

    if (li1.getEnd() != null) {
      end1 = li1.getEnd().scratch;
    }
    if (li2.getBegin() != null) {
      begin2 = li2.getBegin().scratch;
    }
    return end1 > begin2 || end1 <= -1;

  }

  /**
   * Record that it is illegal to assign a symbolic register symb to any
   * volatile physical registers 
   */
  final void forbidAllVolatiles(OPT_Register symb) {
    RestrictedRegisterSet r = hash.get(symb);
    if (r == null) {
      r = new RestrictedRegisterSet(phys);
      hash.put(symb,r);
    }
    r.setNoVolatiles();
  }

  /**
   * Record that it is illegal to assign a symbolic register symb to any
   * of a set of physical registers 
   */
  protected final void addRestrictions(OPT_Register symb, OPT_BitSet set) {
    RestrictedRegisterSet r = hash.get(symb);
    if (r == null) {
      r = new RestrictedRegisterSet(phys);
      hash.put(symb,r);
    }
    r.addAll(set);
  }

  /**
   * Record that it is illegal to assign a symbolic register symb to a
   * physical register p
   */
  protected final void addRestriction(OPT_Register symb, OPT_Register p) {
    RestrictedRegisterSet r = hash.get(symb);
    if (r == null) {
      r = new RestrictedRegisterSet(phys);
      hash.put(symb,r);
    }
    r.add(p);
  }

  /**
   * Return the set of restricted physical register for a given symbolic
   * register. Return null if no restrictions.
   */
  final RestrictedRegisterSet getRestrictions(OPT_Register symb) {
    return hash.get(symb);
  }

  /**
   * Is it forbidden to assign symbolic register symb to any volatile
   * register?
   * @return true :yes, all volatiles are forbidden
   *         false :maybe, maybe not
   */
  public final boolean allVolatilesForbidden(OPT_Register symb) {
    if (VM.VerifyAssertions) {
      VM._assert(symb != null);
    }
    RestrictedRegisterSet s = getRestrictions(symb);
    if (s == null) return false;
    return s.getNoVolatiles();
  }

  /**
   * Is it forbidden to assign symbolic register symb to physical register
   * phys?
   */
  public final boolean isForbidden(OPT_Register symb, OPT_Register phys) {
    if (VM.VerifyAssertions) {
      VM._assert(symb != null);
      VM._assert(phys != null);
    }
    RestrictedRegisterSet s = getRestrictions(symb);
    if (s == null) return false;
    return s.contains(phys);
  }

  /**
   * Is it forbidden to assign symbolic register symb to physical register r
   * in instruction s?
   */
  public abstract boolean isForbidden(OPT_Register symb, OPT_Register r,
                               OPT_Instruction s);

  /**
   * An instance of this class represents restrictions on physical register 
   * assignment.
   * 
   * @author Stephen Fink
   */
  private static final class RestrictedRegisterSet {
    /**
     * The set of registers to which assignment is forbidden.
     */
    private OPT_BitSet bitset;

    /**
     * additionally, are all volatile registers forbidden?
     */
    private boolean noVolatiles = false;
    boolean getNoVolatiles() { return noVolatiles; }
    void setNoVolatiles() { noVolatiles = true; }

    /**
     * Default constructor
     */
    RestrictedRegisterSet(OPT_PhysicalRegisterSet phys) {
      bitset = new OPT_BitSet(phys);
    }

    /**
     * Add a particular physical register to the set.
     */
    void add(OPT_Register r) {
      bitset.add(r);
    }

    /**
     * Add a set of physical registers to this set.
     */
    void addAll(OPT_BitSet set) {
      bitset.addAll(set);
    }

    /**
     * Does this set contain a particular register?
     */
    boolean contains(OPT_Register r) {
      if (r.isVolatile() && noVolatiles) return true;
      else return bitset.contains(r);
    }
  }
}
