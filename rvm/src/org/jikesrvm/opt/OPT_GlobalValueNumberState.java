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
import org.jikesrvm.opt.ir.*;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;
/**
 * This class holds the results of global value numbering.
 * ala Alpern, Wegman and Zadeck, PoPL 88.
 * See Muchnick p.348 for a discussion (which is quite buggy, should
 * have stuck to the dragon book, which gives a high-level description of
 * the correct algorithm on page 143).
 *
 * @author Stephen Fink
 */
public final class OPT_GlobalValueNumberState {
  /**
   * Constant used to flag "unknown" value numbers
   */
  public static final int UNKNOWN = -1;
  /**
   * Print verbose debugging output?
   */
  private static final boolean DEBUG = false;
  /**
   * Assume parameters are not aliased?
   */
  private static final boolean NO_PARAM_ALIAS = false;

  /**
   * ArrayList of OPT_GVCongruenceClass, indexed by value number.
   */
  private final ArrayList<OPT_GVCongruenceClass> B;
  /**
   * The value graph.
   */
  final OPT_ValueGraph valueGraph;
  /**
   * Stack used for a work list.
   */
  private final Stack<OPT_GVCongruenceClass> workList;

  /**
   * Construct a structure to hold global value number results for an IR.
   *
   * @param ir governing IR
   */
  OPT_GlobalValueNumberState (OPT_IR ir) {
    B = new ArrayList<OPT_GVCongruenceClass>();
    workList = new Stack<OPT_GVCongruenceClass>();
    valueGraph = new OPT_ValueGraph(ir);
    globalValueNumber();
  }

  /** 
   * Compute node congruence over the value number graph.
   *
   * <p> Algorithm: Muchnick pp. 348-355
   * <p> Note: the Muchnick algorithm is buggy.  In particular, it
   * does not put all needed congruence classes on the worklist.
   *
   * <p> Two nodes in the value graph are congruent if one of the
   * following holds:
   * <ul>
   *  <li> they are the same node
   *  <li>  their labels are identical constants
   *  <li>  they have the same operators and their operands are
   *      congruent
   * </ul>
   *    
   *  <p> Optimistic algorithm:
   *  <ul>
   *    <li> Initially assume all nodes with the same label are congruent
   *    <li> start a work list with all congruence classes that
   *       have multiple operands
   *    <li> choose a congruence class from the worklist.  partition its
   *       elements into new congruence classes if we can discover that
   *       they are not congruent.
   *     <li>  Add any newly created congruence classes to the work list.
   *     <li> (Here's the step Muchnick omits:)
   *     For each class C which has a dependence on any of the newly
   *       created congruence classes, add C to the work list
   *     <li> repeat until work list is empty
   *   </ul>
   * 
   *  <p> The following method breaks Muchnick's algorithm, which will
   *  assign m and n the same value number. Muchnick's problem is that
   *  it does not put the congruence class for 'int_mul' back on the worklist
   *  when we discover, for example, that i is not congruent to k
   *  <pre>
   *   public int foo(int a, int b, int c, int d, int e, int f, int g, int h) {
   *      int i = a+b;
   *      int j = c+d;
   *      int k = e+f;
   *      int l = g+h;
   *      int m = i * j;
   *      int n = k * l;
   *      int o = m/n;
   *      return o;
   *   }
   *  </pre>
   */
  private void globalValueNumber () {
    if (DEBUG)
      VM.sysWrite(valueGraph.toString());
    // initialize the congurence classes
    initialize();
    // initialize the work list
    initializeWorkList();
    // drain the work list
    while (!workList.empty()) {
      OPT_GVCongruenceClass partition = workList.pop();
      partitionClass(partition);
    }
    // all done
    if (DEBUG)
      printValueNumbers();
  }
  /**
   * Merge the congruence classes containing vertices v1 and v2.;
   */
   void mergeClasses(OPT_ValueGraphVertex v1, OPT_ValueGraphVertex v2) {
     if (DEBUG)  {
       System.out.println("@@@@ mergeClasses called with v1 = " + v1 + 
                          " ; v2 = " + v2);
     }

     int val1 = v1.getValueNumber();
     int val2 = v2.getValueNumber();
     if ( val1 == val2 ) return;

     OPT_GVCongruenceClass class1 = B.get(val1);

     while ( true ) {
       OPT_GVCongruenceClass class2 = B.get(val2);
       Iterator<OPT_ValueGraphVertex> i = class2.iterator() ;  
       if ( ! i.hasNext() ) break;
       OPT_ValueGraphVertex v = i.next();
       if ( DEBUG) 
         System.out.println("@@@@ moving vertex " + v + " from class " + val2
                            + " to class " + val1);
       class1.addVertex(v);
       class2.removeVertex(v);
       v.setValueNumber(val1);
     }

     // Null out entry for val2
     B.set(val2, null);
   }


   /** 
    * Definitely-same relation.
    * @param name1 first variable
    * @param name2 second variable
    * @return true iff the value numbers for two variables are equal
    */
   boolean DS (Object name1, Object name2) {
     OPT_ValueGraphVertex v1 = valueGraph.getVertex(name1);
     OPT_ValueGraphVertex v2 = valueGraph.getVertex(name2);
     return v1.getValueNumber() == v2.getValueNumber();
   }

  /** 
   * Definitely-different relation for two value numbers.
   * Returns true for the following cases:
   * <ul>
   *  <li> v1 and v2 are both constants, but don't match
   *  <li> v1 and v2 both result from NEW statements, but don't match
   *  <li> one of v1 and v2 is a parameter, and the other results from a
   *      new statement
   * </ul>
   * <p> TODO: add more smarts
   * @param v1 first value number
   * @param v2 second value number 
   * @return true iff the value numbers for two variables are definitely
   * different
   */
  boolean DD (int v1, int v2) {
    if ((v1 == -1) || (v2 == -1))
      return  false;
    OPT_GVCongruenceClass class1 = B.get(v1);
    OPT_GVCongruenceClass class2 = B.get(v2);
    Object label1 = class1.getLabel();
    Object label2 = class2.getLabel();
    // if one is a constant, they must both be ...
    if (isConstant(label1) && !isConstant(label2))
      return  false;
    if (!isConstant(label1) && isConstant(label2))
      return  false;
    if (isConstant(label1))
      return  (v1 != v2);
    // handle DD for allocations
    if (isBornAtAllocation(label1)) {
      if (isBornAtAllocation(label2)) {
        // both are NEW.  Are they dd?
        return  (v1 != v2);
      } 
      else if (class2.containsParameter()) {
        // one is NEW, other is parameter. They are DD.
        return  true;
      }
    } 
    else {
      if (isBornAtAllocation(label2)) {
        if (class1.containsParameter()) {
          // one is NEW, other is parameter. They are DD.
          return  true;
        }
      }
    }
    // assume parameters are not aliased? 
    if (NO_PARAM_ALIAS) {
      if (v1 != v2)  {
        if (class1.containsParameter()) {
          if (class2.containsParameter()) {
            return true;
          }
        }
      }
    }
    
    // if we haven't figured out they're DD, return false;
    return  false;
  }

  /** 
   * Definitely-different relation.
   * Returns true for the following cases:
   * <ul>
   *  <li> name1 and name2 are both constants, but don't match
   *  <li> name1 and name2 both result from NEW statements, but don't match
   *  <li> one of name1 and name2 is a parameter, and the other results from a
   *      new statement
   * </ul>
   * <p> TODO: add more smarts
   * @param name1 name of first object to compare
   * @param name2 name of second object to compare
   * @return true iff the value numbers for two variables are definitely
   * different
   */
  boolean DD (Object name1, Object name2) {
    OPT_ValueGraphVertex v1 = valueGraph.getVertex(name1);
    OPT_ValueGraphVertex v2 = valueGraph.getVertex(name2);
    return  DD(v1.getValueNumber(), v2.getValueNumber());
  }

  OPT_GVCongruenceClass congruenceClass(Object name) {
    OPT_ValueGraphVertex v = valueGraph.getVertex(name);
    return B.get(v.getValueNumber());
  }

  /**
   * Return the (integer) value number for a given variable
   * 
   * @param name name of the variable to look up
   * @return its value number
   */
  int getValueNumber (Object name) {
    OPT_ValueGraphVertex v = valueGraph.getVertex(name);
    if (v == null)
      return  UNKNOWN;
    return  v.getValueNumber();
  }

  /** 
   * Print the value numbers for each node in the value graph.
   */
  void printValueNumbers () {
    for (Enumeration<OPT_GraphNode> e = valueGraph.enumerateVertices(); e.hasMoreElements();) {
      OPT_ValueGraphVertex v = (OPT_ValueGraphVertex)e.nextElement();
      int valueNumber = v.getValueNumber();
      OPT_GVCongruenceClass c = B.get(valueNumber);
      System.out.println(v.getName() + " " + valueNumber + " " + c.getLabel());
    }
  }

  /** 
   * Initialize the congruence classes, assuming that all nodes
   * with the same label are congruent.
   */
  private void initialize () {
    // store a map from label -> congruenceClass
    HashMap<Object,OPT_GVCongruenceClass> labelMap =
      new HashMap<Object,OPT_GVCongruenceClass>(10);
    for (Enumeration<OPT_GraphNode> e = valueGraph.enumerateVertices(); e.hasMoreElements();) {
      OPT_ValueGraphVertex v = (OPT_ValueGraphVertex)e.nextElement();
      Object label = v.getLabel();
      OPT_GVCongruenceClass c = findOrCreateCongruenceClass(label, labelMap);
      // add this node to the congruence class
      c.addVertex(v);
      // set the value number for the node
      v.setValueNumber(c.getValueNumber());
    }
  }

  /**
   * Given a label, return the congruence class for that label.
   * Create one if needed.
   *
   * @param label the label of a congruence class
   * @param labelMap a mapping from labels to congruence class
   * @return the congruence class for the label.
   */
  private OPT_GVCongruenceClass findOrCreateCongruenceClass (Object label, 
                                                             HashMap<Object,OPT_GVCongruenceClass> labelMap) {
    OPT_GVCongruenceClass result = labelMap.get(label);
    if ((result == null) || (label == null)) {
      result = createCongruenceClass(label);
      labelMap.put(label, result);
    }
    return  result;
  }

  /**
   * Given a label, return a new congruence class for that label.
   * @param label the label of a congruence class
   * @return the congruence class for the label.
   */
  private OPT_GVCongruenceClass createCongruenceClass (Object label) {
    // create a new congruence class, and update data structures
    int index = B.size();
    OPT_GVCongruenceClass result = new OPT_GVCongruenceClass(index, label);
    B.add(result);
    return  result;
  }

  /** 
   * Initialize the work list.
   * A congruence class gets put on the work list if any two nodes
   * in the class point to corresponding targets in separate partitions.
   */
  private void initializeWorkList () {
    for (OPT_GVCongruenceClass c : B) {
      if (c.size() == 1)
        continue;
      // store a reference to the first node in c
      Iterator<OPT_ValueGraphVertex> i = c.iterator();
      OPT_ValueGraphVertex first = i.next();
      // now check that each other target matches the first element
      // if not, add this class to the work list
      //
      for (; i.hasNext();) {
        OPT_ValueGraphVertex v = i.next();
        if (!checkCongruence(first, v)) {
          workList.push(c);
          break;
        }
      }
    }
  }

  /** 
   * Partition a congruence class.
   * @param partition the class to partition
   */
  private void partitionClass (OPT_GVCongruenceClass partition) {
    // store a reference to the first node in c, which will serve
    // as a representative for this class
    Iterator<OPT_ValueGraphVertex> i = partition.iterator();
    OPT_ValueGraphVertex first = i.next();
    ArrayList<OPT_GVCongruenceClass> newClasses =
      new ArrayList<OPT_GVCongruenceClass>();
    // now check each other node in c, to see if it matches the
    // representative
    ArrayList<OPT_ValueGraphVertex> toRemove =
      new ArrayList<OPT_ValueGraphVertex>();
    for (; i.hasNext();) {
      OPT_ValueGraphVertex v = i.next();
      if (!checkCongruence(first, v)) {
        // NOT CONGRUENT!!  split the partition.  first check if
        // v fits in any other newly created congruence classes
        int index = findCongruenceMatch(newClasses, v);
        if (index > -1) {
          // MATCH FOUND!! place v in newClasses[index]
          OPT_GVCongruenceClass match = B.get(index);
          match.addVertex(v);
          v.setValueNumber(match.getValueNumber());
        } 
        else {
          // NO MATCH FOUND!! create a new congruence class
          // find the appropriate label for the new congruence class
          // and create a new congruence class with this label
          OPT_GVCongruenceClass c = createCongruenceClass(v);
          newClasses.add(c);
          c.addVertex(v);
          v.setValueNumber(c.getValueNumber());
        }
        // mark v as to be removed from partition
        // (Can't remove it yet while iterating over the set);
        toRemove.add(v);
      }
    }
    // remove necessary vertices
    for (OPT_ValueGraphVertex v : toRemove) {
      partition.removeVertex(v);
    }
    // if needed place the original partition back on the work list
    if ((!newClasses.isEmpty()) && (partition.size() > 1)) {
      workList.push(partition);
    }
    // place any new congruence classes with size > 1 on the worklist
    // also place any classes which might indirectly be affected
    for (OPT_GVCongruenceClass c : newClasses) {
      if (c.size() > 1)
        workList.push(c);
      addDependentClassesToWorklist(c);
    }
  }

  /**
   * Assuming congruence class c has changed: find all other classes
   * that might be affected, and add them to the worklist
   * @param c the congruence class that has changed
   */
  private void addDependentClassesToWorklist (OPT_GVCongruenceClass c) {
    // for each element of this congruence class:
    for (OPT_ValueGraphVertex v : c) {
      // for each vertex which points to v in the value graph
      for (Enumeration<OPT_GraphNode> e = v.inNodes(); e.hasMoreElements();) {
        OPT_ValueGraphVertex in = (OPT_ValueGraphVertex)e.nextElement();
        int vn = in.getValueNumber();
        OPT_GVCongruenceClass x = B.get(vn);
        workList.push(x);
      }
    }
  }

  /** 
   * Does vertex v belong to any congruence class in a vector?
   * If so, returns the value number of the matching congruence class.
   * If none found, returns -1.
   * @param vector a vector of congruence classes
   * @param v the vertex to search for
   * @return the value number corresponding to the congruence class
   * containing v.  -1 iff no such class is found.
   */
  private int findCongruenceMatch (ArrayList<OPT_GVCongruenceClass> vector, OPT_ValueGraphVertex v) {
    for (OPT_GVCongruenceClass klass : vector) {
      if (checkCongruence(v, klass)) {
        return klass.getValueNumber();
      }
    }
    return  -1;
  }

  /** 
   * Does the current state of the algorithm optimistically assume
   * that a vertex v is congruent to the vertices in a congruence 
   * class? Note: this can return true even if 
   * if the value numbers don't currently match.
   * @param v the vertex in question
   * @param c the congurence class to check
   * @return true or false
   */
  private boolean checkCongruence (OPT_ValueGraphVertex v, 
      OPT_GVCongruenceClass c) {
    OPT_ValueGraphVertex r = c.getRepresentative();
    boolean result = checkCongruence(r, v);
    return  result;
  }

  /** 
   * Does the current state of the algorithm optimistically assume
   * that two nodes are congruent? Note: this can return false
   * even if the value numbers are currently the same.
   * @param v1 first vertex
   * @param v2 second vertex
   */
  private boolean checkCongruence (OPT_ValueGraphVertex v1, 
      OPT_ValueGraphVertex v2) {
    if (v1 == v2)
      return  true;
    // make sure the two nodes have the same label
    if (v1.getLabel() != v2.getLabel())
      return  false;
    // make sure that the operands match
    int arity = v1.getArity();
    for (int i = 0; i < arity; i++) {
      OPT_ValueGraphVertex target1 = v1.getTarget(i);
      OPT_ValueGraphVertex target2 = v2.getTarget(i);
      // if either target is null, then that particular control
      // flow path is never realized, so assume TOP
      if ((target1 == null) || (target2 == null))
        continue;
      if (target1.getValueNumber() != target2.getValueNumber())
        return  false;
    }
    return  true;
  }

  /** 
   * Does a given label indicate that the object has a constant value?
   */
  private static boolean isConstant (Object label) {
    return  (label instanceof OPT_ConstantOperand);
  }

  /** 
   * Does a given label indicate that the object is created at an
   * allocation site?
   */
  private static boolean isBornAtAllocation (Object label) {
    return  (label instanceof OPT_Instruction);
  }
}
