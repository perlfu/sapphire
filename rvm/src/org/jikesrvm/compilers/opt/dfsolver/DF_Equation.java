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
package org.jikesrvm.compilers.opt.dfsolver;

import org.jikesrvm.compilers.opt.util.GraphNode;
import org.jikesrvm.compilers.opt.util.GraphNodeEnumeration;

/**
 * DF_Equation.java
 *
 * represents a single Data Flow equation
 */
public class DF_Equation implements GraphNode {

  /**
   * Evaluate this equation, setting a new value for the
   * left-hand side.
   *
   * @return true if the lhs value changed. false otherwise
   */
  boolean evaluate() {
    return operator.evaluate(operands);
  }

  /**
   * Return the left-hand side of this equation.
   *
   * @return the lattice cell this equation computes
   */
  DF_LatticeCell getLHS() {
    return operands[0];
  }

  /**
   * Return the operandsin this equation.
   * @return the operands in this equation.
   */
  public DF_LatticeCell[] getOperands() {
    return operands;
  }

  /**
   * Return the operator for this equation
   * @return the operator for this equation
   */
  DF_Operator getOperator() {
    return operator;
  }

  /**
   * Does this equation contain an appearance of a given cell?
   * @param cell the cell in question
   * @return true or false
   */
  public boolean hasCell(DF_LatticeCell cell) {
    for (DF_LatticeCell operand : operands) {
      if (operand == cell) {
        return true;
      }
    }
    return false;
  }

  /**
   * Return a string representation of this object
   * @return a string representation of this object
   */
  public String toString() {
    if (operands[0] == null) {
      return ("NULL LHS");
    }
    String result = operands[0].toString();
    result = result + " " + operator + " ";
    for (int i = 1; i < operands.length; i++) {
      result = result + operands[i] + "  ";
    }
    return result;
  }

  /**
   * Constructor for case of one operand on the right-hand side.
   *
   * @param lhs the lattice cell set by this equation
   * @param operator the equation operator
   * @param op1 the first operand on the rhs
   */
  DF_Equation(DF_LatticeCell lhs, DF_Operator operator, DF_LatticeCell op1) {
    this.operator = operator;
    operands = new DF_LatticeCell[2];
    operands[0] = lhs;
    operands[1] = op1;
  }

  /**
   * Constructor for case of two operands on the right-hand side.
   *
   * @param lhs the lattice cell set by this equation
   * @param operator the equation operator
   * @param op1 the first operand on the rhs
   * @param op2 the second operand on the rhs
   */
  DF_Equation(DF_LatticeCell lhs, DF_Operator operator, DF_LatticeCell op1, DF_LatticeCell op2) {
    this.operator = operator;
    operands = new DF_LatticeCell[3];
    operands[0] = lhs;
    operands[1] = op1;
    operands[2] = op2;
  }

  /**
   * Constructor for case of three operands on the right-hand side.
   *
   * @param lhs the lattice cell set by this equation
   * @param operator the equation operator
   * @param op1 the first operand on the rhs
   * @param op2 the second operand on the rhs
   * @param op3 the third operand on the rhs
   */
  DF_Equation(DF_LatticeCell lhs, DF_Operator operator, DF_LatticeCell op1, DF_LatticeCell op2,
                  DF_LatticeCell op3) {
    this.operator = operator;
    operands = new DF_LatticeCell[4];
    operands[0] = lhs;
    operands[1] = op1;
    operands[2] = op2;
    operands[3] = op3;
  }

  /**
   * Constructor for case of more than three operands on the right-hand side.
   *
   * @param lhs the lattice cell set by this equation
   * @param operator the equation operator
   * @param rhs the operands of the right-hand side in order
   */
  DF_Equation(DF_LatticeCell lhs, DF_Operator operator, DF_LatticeCell[] rhs) {
    this.operator = operator;
    operands = new DF_LatticeCell[rhs.length + 1];
    operands[0] = lhs;
    for (int i = 0; i < rhs.length; i++) {
      operands[i + 1] = rhs[i];
    }
  }

  /**
   * Get the topological number for this equation
   * @return the topological number
   */
  int getTopologicalNumber() {
    return topologicalNumber;
  }

  /**
   * Get the topological number for this equation
   * @param n the topological order
   */
  void setTopologicalNumber(int n) {
    topologicalNumber = n;
  }

  /** Implementation */
  /**
   * The operator in the equation
   */
  protected DF_Operator operator;
  /**
   * The operands. Operand[0] is the left hand side.
   */
  protected DF_LatticeCell[] operands;
  /**
   * The number of this equation when the system is sorted in topological
   * order.
   */
  int topologicalNumber;
  /**
   * Field used for GraphNode interface.  TODO: is this needed?
   */
  private int index;

  /**
   * Implementation of GraphNode interface.
   */
  public void setIndex(int i) {
    index = i;
  }

  /**
   * Implementation of GraphNode interface.
   */
  public int getIndex() {
    return index;
  }

  /**
   * Return an enumeration of the equations which use the result of this
   * equation.
   * @return an enumeration of the equations which use the result of this
   * equation.
   */
  public GraphNodeEnumeration outNodes() {
    return new GraphNodeEnumeration() {
      private GraphNode elt = getLHS();

      public boolean hasMoreElements() {
        return elt != null;
      }

      public GraphNode next() {
        GraphNode x = elt;
        elt = null;
        return x;
      }

      public GraphNode nextElement() {
        return next();
      }
    };
  }

  /**
   * Return an enumeration of the equations upon whose results this
   * equation depends.
   * @return an enumeration of the equations upon whose results this
   * equation depends
   */
  public GraphNodeEnumeration inNodes() {
    return new GraphNodeEnumeration() {
      private int i = 1;

      public boolean hasMoreElements() {
        return (i < operands.length);
      }

      public GraphNode next() {
        return operands[i++];
      }

      public GraphNode nextElement() {
        return next();
      }
    };
  }

  private int scratch;

  public int getScratch() {
    return scratch;
  }

  public int setScratch(int o) {
    return (scratch = o);
  }
}
