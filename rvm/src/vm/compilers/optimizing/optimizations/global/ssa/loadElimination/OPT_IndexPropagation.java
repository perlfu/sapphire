/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * OPT_IndexPropagation.java
 *
 * <p> Perform index propagation (see Fink, Knobe && Sarkar, SAS 2000)
 *
 * <p> This analysis computes for each Array SSA variable A,
 * the set of value numbers V(k) such that location
 * A[k] is "available" at def A, and thus at all uses of A
 *
 * <p> We formulate this as a data flow problem as described in the paper.
 *
 * <p> This class relies on Array SSA form, global value numbering, and
 * the dataflow equation solver framework.
 *
 * <p> TODO: This implementation is not terribly efficient.  Speed it up.
 *
 * @author Stephen Fink
 */
public final class OPT_IndexPropagation extends OPT_CompilerPhase {

  /**
   * Should this phase be performed?
   * @param options controlling compiler options
   * @return true or false
   */
  public final boolean shouldPerform(OPT_Options options) {
    return  true;
  }

  /**
   * Return the name of this compiler phase.
   * @return "Index Propagation"
   */
  public final String getName() {
    return  "Index Propagation";
  }

  /**
   * Print vervose debugging messages?
   */ 
  private static final boolean DEBUG = false;

  /** 
   * Perform the analysis.
   * <p> Pre-condition: The ir is in Array SSA form and global value numbers
   *  	have been computed.
   *
   * @param ir the IR to optimize 
   */
  public void perform(OPT_IR ir) {
    if (ir.desiredSSAOptions.getAbort()) return;
    OPT_IndexPropagationSystem system = new OPT_IndexPropagationSystem(ir);
    if (DEBUG)
      System.out.print("Solving...");
    system.solve();
    if (DEBUG)
      System.out.println("done");
    OPT_DF_Solution solution = system.getSolution();
    if (DEBUG)
      System.out.println("Index Propagation Solution: " + solution);
    ir.HIRInfo.indexPropagationSolution = solution;
  }


  /**
   * An ObjectCell is a lattice cell for the index propagation 
   * problem, used in redundant load elimination for fields.
   * <p> An ObjectCell represents a set of value numbers, 
   * indicating that
   * the elements indexed by these value numbers are available for
   * a certain field type.
   *
   * <p> Note: this implementation does not scale, and is not terribly
   * efficient.
   *
   * @author Stephen Fink
   */
  final static class ObjectCell extends OPT_DF_AbstractCell {
    /**
     * a bound on the size of a lattice cell.
     */
    final static int CAPACITY = 10; 
    /**
     * a set of value numbers comparising this lattice cell.
     */
    int[] numbers = null;
    /**
     * The number of value numbers in this cell.
     */
    int size = 0;
    /**
     * The heap variable this lattice cell tracks information for.
     */
    OPT_HeapVariable key;
    /**
     * Does this lattice cell represent TOP?
     */
    boolean TOP = true;

    /**
     * Create a latticle cell corresponding to a heap variable.
     * @param   key the heap variable associated with this cell.
     */
    ObjectCell(OPT_HeapVariable key) {
      this.key = key;
    }

    /**
     * Does this cell represent the TOP element in the dataflow lattice?
     * @return true or false.
     */
    boolean isTOP() {
      return TOP;
    }

    /**
     * Does this cell represent the BOTTOM element in the dataflow lattice?
     * @return true or false.
     */
    boolean isBOTTOM() {
      return !TOP && (size==0);
    }

    /**
     * Mark this cell as representing (or not) the TOP element in the 
     * dataflow lattice.
     * @param b should this cell contain TOP?
     */
    void setTOP(boolean b) {
      TOP = b;
      numbers = null;
    }

    /**
     * Set the value of this cell to BOTTOM.
     */
    void setBOTTOM() {
      clear();
    }

    /**
     * Does this cell contain the value number v?
     *
     * @param v value number in question
     * @return true or false
     */
    boolean contains(int v) {

      if (isTOP()) return true;
      if (v == OPT_GlobalValueNumberState.UNKNOWN) return false;

      for (int i = 0; i < size; i++) {
        if (numbers[i] == v)
          return  true;
      }
      return  false;
    }

    /**
     * Add a value number to this cell.
     *
     * @param v value number
     */
    void add(int v) {
      if (isTOP()) return;

      if ((size < CAPACITY) && !contains(v)) {
        if (size == 0) {
          numbers = new int[CAPACITY];
        }
        numbers[size] = v;
        size++;
      }
    }

    /**
     * Remove a value number from this cell.
     *
     * @param v value number
     */
    void remove(int v) {
      if (isTOP()) {
        throw  new OPT_OptimizingCompilerException("Unexpected lattice operation");
      }
      int[] old = numbers;
      int[] numbers = new int[CAPACITY];
      int index = 0;
      for (int i = 0; i < size; i++) {
        if (old[i] == v) {
          size--;
        } 
        else {
          numbers[index++] = old[i];
        }
      }
    }

    /**
     * Clear all value numbers from this cell.
     */
    void clear() {
      setTOP(false);
      size = 0;
      numbers = null;
    }

    /**
     * Return a deep copy of the value numbers in this cell.  
     * @return a deep copy of the value numbers in this cell, null to
     * represent empty set.
     */
    int[] copyValueNumbers() {
      if (isTOP()) { 
        throw  new OPT_OptimizingCompilerException("Unexpected lattice operation");
      }
      if (size == 0) return null;

      int[] result = new int[size];
      for (int i = 0; i < size; i++) {
        result[i] = numbers[i];
      }
      return result;
    }

    /**
     * Return a string representation of this cell 
     * @return a string representation of this cell 
     */
    public String toString() {
      StringBuffer s = new StringBuffer(key.toString());

      if (isTOP()) return s.append("{TOP}").toString();
      if (isBOTTOM()) return s.append("{BOTTOM}").toString();

      s.append("{");
      for (int i = 0; i < size; i++) {
        s.append(" ").append(numbers[i]);
      }
      s.append("}");
      return s.toString();
    }

    /**
     * Do two sets of value numbers differ? 
     * <p> SIDE EFFECT: sorts the sets
     * 
     * @param set1 first set to compare
     * @param set2 second set to compare
     * @return true iff the two sets are different
     */
    public static boolean setsDiffer(int[] set1, int[] set2) {

      if (set1 == null) {
        return (set2 != null);
      } else if (set2 == null) {
        return true;
      }

      if (set1.length != set2.length) return  true;

      sort(set1);
      sort(set2);

      for (int i = 0; i < set1.length; i++) {
        if (set1[i] != set2[i])
          return  true;
      }
      return  false;
    }

    /** 
     * Sort an array of value numbers with bubble sort. 
     * Note that these sets
     * will be small  (< CAPACITY), so bubble sort
     * should be ok.
     * @param set the set to sort
     */
    public static void sort(int[] set) {

      if (set == null) return;

      for (int i = set.length - 1; i >= 0; i--) {
        for (int j = 0; j < i; j++) {
          if (set[j] > set[j + 1]) {
            int temp = set[j + 1];
            set[j + 1] = set[j];
            set[j] = temp;
          }
        }
      }
    }
  }


  /**
   * An ArrayCell is a lattice cell for the index propagation 
   * problem, used in redundant load elimination for one-dimensional arrays.
   * <p> An ArrayCell represents a set of value number pairs, 
   * indicating that
   * the elements indexed by these value numbers are available for
   * a certain array type.
   *
   * <p> For example, suppose p is an int[], with value number v(p).
   * Then the value number pair <p,5> for heap variable I[ means
   * that p[5] is available.
   *
   * <p> Note: this implementation does not scale, and is not terribly
   * efficient.
   *
   * @author Stephen Fink
   */
  final static class ArrayCell extends OPT_DF_AbstractCell {
    /**
     * a bound on the size of a lattice cell.
     */
    final static int CAPACITY = 10;      
    /**
     * a set of value number pairs comparising this lattice cell.
     */
    OPT_ValueNumberPair[] numbers = null;
    /**
     * The number of value number pairs in this cell.
     */
    int size = 0;
    /**
     * The heap variable this lattice cell tracks information for.
     */
    OPT_HeapVariable key;
    /**
     * Does this lattice cell represent TOP?
     */
    boolean TOP = true;

    /**
     * Create a latticle cell corresponding to a heap variable.
     * @param   key the heap variable associated with this cell.
     */
    ArrayCell(OPT_HeapVariable key) {
      this.key = key;
    }

    /**
     * Does this cell represent the TOP element in the dataflow lattice?
     * @return true or false.
     */
    boolean isTOP() {
      return TOP;
    }

    /**
     * Does this cell represent the BOTTOM element in the dataflow lattice?
     * @return true or false.
     */
    boolean isBOTTOM() {
      return !TOP && (size==0);
    }

    /**
     * Mark this cell as representing (or not) the TOP element in the 
     * dataflow lattice.
     * @param b should this cell contain TOP?
     */
    void setTOP(boolean b) {
      TOP = b;
      numbers = null;
    }

    /**
     * Set the value of this cell to BOTTOM.
     */
    void setBOTTOM() {
      clear();
    }

    /**
     * Does this cell contain the value number pair v1, v2?
     *
     * @param v1 first value number
     * @param v2 second value number
     * @return true or false
     */
    boolean contains(int v1, int v2) {
      if (isTOP()) return  true;
      if (v1 == OPT_GlobalValueNumberState.UNKNOWN) return  false;
      if (v2 == OPT_GlobalValueNumberState.UNKNOWN) return  false;
      if (size == 0) return false;

      OPT_ValueNumberPair p = new OPT_ValueNumberPair(v1, v2);
      for (int i = 0; i < size; i++) {
        if (numbers[i].equals(p))
          return  true;
      }
      return  false;
    }

    /**
     * Add a value number pair to this cell.
     *
     * @param v1 first value number
     * @param v2 second value number
     */
    void add(int v1, int v2) {
      if (isTOP()) return;

      if ((size < CAPACITY) && !contains(v1, v2)) {
        if (size == 0) {
          numbers = new OPT_ValueNumberPair[CAPACITY];
        }
        OPT_ValueNumberPair p = new OPT_ValueNumberPair(v1, v2);
        numbers[size] = p;
        size++;
      }
    }

    /**
     * Remove a value number pair from this cell.
     *
     * @param v1 first value number
     * @param v2 second value number
     */
    void remove(int v1, int v2) {
      if (isTOP()) {
        throw  new OPT_OptimizingCompilerException("Unexpected lattice operation");
      }
      OPT_ValueNumberPair[] old = numbers;
      OPT_ValueNumberPair[] numbers = new OPT_ValueNumberPair[CAPACITY];
      int index = 0;
      OPT_ValueNumberPair p = new OPT_ValueNumberPair(v1, v2);
      for (int i = 0; i < size; i++) {
        if (old[i].equals(p)) {
          size--;
        } 
        else {
          numbers[index++] = old[i];
        }
      }
    }

    /**
     * Clear all value numbers from this cell.
     */
    void clear() {
      setTOP(false);
      size = 0;
      numbers = null;
    }

    /**
     * Return a deep copy of the value numbers in this cell
     * @return a deep copy of the value numbers in this cell 
     */
    OPT_ValueNumberPair[] copyValueNumbers() {
      if (isTOP()) {
        throw  new OPT_OptimizingCompilerException("Unexpected lattice operation");
      }

      if (size == 0) return null;

      OPT_ValueNumberPair[] result = new OPT_ValueNumberPair[size];
      for (int i = 0; i < size; i++) {
        result[i] = new OPT_ValueNumberPair(numbers[i]);
      }
      return  result;
    }

    /**
     * Return a string representation of this cell 
     * @return a string representation of this cell 
     */
    public String toString() {
      StringBuffer s = new StringBuffer(key.toString());

      if (isTOP()) return  s.append("{TOP}").toString();
      if (isBOTTOM()) return s.append("{BOTTOM}").toString();

      s.append("{");
      for (int i = 0; i < size; i++) {
        s.append(" ").append(numbers[i]);
      }
      s.append("}");
      return s.toString();
    }

    /**
     * Do two sets of value number pairs differ? 
     * <p> SIDE EFFECT: sorts the sets
     * 
     * @param set1 first set to compare
     * @param set2 second set to compare
     * @return true iff the two sets are different
     */
    public static boolean setsDiffer(OPT_ValueNumberPair[] set1, 
                                      OPT_ValueNumberPair[] set2) {
      if (set1 == null) {
        return (set2 != null);
      } else if (set2 == null) {
        return true;
      }

      if (set1.length != set2.length) return  true;

      sort(set1);
      sort(set2);

      for (int i = 0; i < set1.length; i++) {
        if (!set1[i].equals(set2[i]))
          return  true;
      }
      return  false;
    }

    /**
     * Sort an array of value number pairs with bubble sort. 
     * Note that these sets
     * will be small  (< CAPACITY), so bubble sort
     * should be ok.
     * @param set the set to sort
     */
    public static void sort(OPT_ValueNumberPair[] set) {

      if (set == null) return;

      for (int i = set.length - 1; i >= 0; i--) {
        for (int j = 0; j < i; j++) {
          if (set[j].greaterThan(set[j + 1])) {
            OPT_ValueNumberPair temp = set[j + 1];
            set[j + 1] = set[j];
            set[j] = temp;
          }
        }
      }
    }
  }
}
