/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;
import java.util.*;

/**
 * This class holds information on scratch register usage, needed to
 * adjust GC Maps.
 *
 * @author Stephen Fink
 */
final class OPT_ScratchMap {

  private static final boolean DEBUG = false;

  /**
   * For each register, the set of intervals describing the register.
   */
  private HashMap map = new HashMap();

  /**
   * For each register, a pending (incomplete) interval under
   * construction.
   */
  private HashMap pending = new HashMap();

  /**
   * For each GC Point s, a set of symbolic registers that are cached in
   * dirty scratch registers before s.
   */
  private HashMap dirtyMap = new HashMap();

  /**
   * Begin a new interval of scratch-ness for a symbolic register.
   *
   * @param r the symbolic register being moved into scratch
   * @param scratch the physical register being used as a scratch
   * @param begin the instruction before which the physical register is
   * vacated.
   */
  void beginSymbolicInterval(OPT_Register r, OPT_Register scratch,
                             OPT_Instruction begin) {
    if (DEBUG) {
      System.out.println("beginSymbolicInterval " + r + " " + scratch + 
                         " " + begin.scratch); 
    }

    SymbolicInterval i = new SymbolicInterval(r,scratch);
    i.begin = begin;
    ArrayList v = findOrCreateIntervalSet(r);
    v.add(i);
    pending.put(r,i);
  }

  /**
   * End an interval of scratch-ness for a symbolic register.
   *
   * @param r the symbolic register being moved into scratch
   * @param end the instruction before which the scratch interval ends
   */
  void endSymbolicInterval(OPT_Register r, OPT_Instruction end) {
    if (DEBUG) {
      System.out.println("endSymbolicInterval " + r + " " + end.scratch); 
    }

    SymbolicInterval i = (SymbolicInterval)pending.get(r);
    i.end = end;
    pending.remove(i);
  }


  /**
   * Begin a new interval of scratch-ness for a physical register.
   *
   * @param r the physical register being used as a scratch
   * @param begin the instruction before which the physical register is
   * vacated.
   */
  void beginScratchInterval(OPT_Register r, OPT_Instruction begin) {
    if (DEBUG) {
      System.out.println("beginScratchInterval " + r + " " + begin.scratch);
    }
    PhysicalInterval p = new PhysicalInterval(r);
    p.begin = begin;
    ArrayList v = findOrCreateIntervalSet(r);
    v.add(p);
    pending.put(r,p);
  }

  /**
   * End an interval of scratch-ness for a physical register.
   *
   * @param r the physical register being used as a scratch
   * @param end the instruction before which the physical register is
   * vacated.
   */
  void endScratchInterval(OPT_Register r, OPT_Instruction end) {
    if (DEBUG) {
      System.out.println("endScratchInterval " + r + " "  + end.scratch);
    }
    PhysicalInterval p = (PhysicalInterval)pending.get(r);
    p.end = end;
    pending.remove(r);
  }

  /**
   * Find or create the set of intervals corresponding to a register r.
   */
  private ArrayList findOrCreateIntervalSet(OPT_Register r) {
    ArrayList v = (ArrayList)map.get(r);
    if (v == null) {
      v = new ArrayList();
      map.put(r,v);
    }
    return v;
  }

  /**
   * If a physical register is being used as a scratch register at
   * instruction n, return true; else, return false;
   */
  boolean isScratch(OPT_Register r, int n) {
    ArrayList v = (ArrayList)map.get(r);
    if (v == null) return false;
    for (Iterator e = v.iterator(); e.hasNext(); ) {
      PhysicalInterval i = (PhysicalInterval)e.next();
      if (i.contains(n)) return true;
    }
    return false;
  }

  /**
   * If a symbolic register resides in a scratch register at an
   * instruction numbered n, then return the scratch register. Else,
   * return null.
   */
  OPT_Register getScratch(OPT_Register r, int n) {
    ArrayList v = (ArrayList)map.get(r);
    if (v == null) return null;
    for (Iterator e = v.iterator(); e.hasNext(); ) {
      SymbolicInterval i = (SymbolicInterval)e.next();
      if (i.contains(n)) return i.scratch;
    }
    return null;
  }

  /**
   * Is this map empty?
   */
  public boolean isEmpty() {
    return map.isEmpty();
  }

  /**
   * Note that at GC point s, the real value of register symb is cached in
   * a dirty scratch register.
   */
  public void markDirty(OPT_Instruction s, OPT_Register symb) {
    HashSet set = (HashSet)dirtyMap.get(s);
    if (set == null) {
      set = new HashSet(3);
      dirtyMap.put(s,set);
    }
    set.add(symb);
  }

  /**
   * At GC point s, is the value of register r cached in a dirty scratch
   * register?
   */
  public boolean isDirty(OPT_Instruction s, OPT_Register r) {
    HashSet set = (HashSet)dirtyMap.get(s);
    if (set == null) {
      return false;
    } else {
      return set.contains(r);
    }
  }

  /**
   * Return a String representation.
   */
  public String toString() {
    String result = "";
    for (Iterator i = map.values().iterator(); i.hasNext(); ) {
      ArrayList v = (ArrayList)i.next();
      for (Iterator e = v.iterator(); e.hasNext(); ) {
        result += e.next() + "\n";
      }
    }
    return result;
  }

  /**
   * An object that represents an interval where a symbolic register
   * resides in a scratch register.
   * Note that this interval must not span a basic block.
   */
  static class SymbolicInterval {
    /**
     * The symbolic register
     */
    OPT_Register symbolic;
    /**
     * The physical scratch register.
     */ 
    OPT_Register scratch;
    /**
     * The instruction before which the scratch range begins.
     */
    OPT_Instruction begin;
    /**
     * The instruction before which the scratch range ends.
     */
    OPT_Instruction end;

    SymbolicInterval(OPT_Register symbolic, OPT_Register scratch) {
      this.symbolic = symbolic;
      this.scratch = scratch;
    }

    /**
     * Return a string representation, assuming the 'scratch' field of
     * OPT_Instruction identifies an instruction.
     */
    public String toString() {
      return "SI: " + symbolic + " " + scratch + " [" +
              begin.scratch + "," + end.scratch + "]";
    }

    /**
     * Does this interval contain the instruction numbered n?
     */
    boolean contains(int n) {
      return (begin.scratch <= n && end.scratch > n);
    }
  }

  /**
   * An object that represents an interval where a physical register's
   * contents are evicted so that the physical register can be used as a 
   * scratch.  Note that this interval must not span a basic block.
   */
  static class PhysicalInterval {
    /**
     * The physical register evicted.
     */ 
    OPT_Register scratch;
    /**
     * The instruction before which the register is vacated.
     */
    OPT_Instruction begin;
    /**
     * The instruction before which the register is restored.
     */
    OPT_Instruction end;

    PhysicalInterval(OPT_Register scratch) {
      this.scratch=scratch;
    }

    /**
     * Return a string representation, assuming the 'scratch' field of
     * OPT_Instruction identifies an instruction.
     */
    public String toString() {
      return "PI: " + scratch + " [" +
              begin.scratch + "," + end.scratch + "]";
    }

    /**
     * Does this interval contain the instruction numbered n?
     */
    boolean contains(int n) {
      return (begin.scratch <= n && end.scratch > n);
    }
  }
}
