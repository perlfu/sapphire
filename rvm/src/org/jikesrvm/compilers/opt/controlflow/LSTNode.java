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
package org.jikesrvm.compilers.opt.controlflow;

import java.util.ArrayList;
import java.util.Enumeration;

import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.util.SpaceEffGraphEdge;
import org.jikesrvm.compilers.opt.util.SpaceEffGraphNode;
import org.jikesrvm.util.VM_BitVector;

/**
 * A node in the LST (Loop Structure Tree)
 */
public class LSTNode extends SpaceEffGraphNode {

  /**
   * Basic block which is the loop head
   */
  public final BasicBlock header;

  /**
   * Basic blocks in the loop
   */
  VM_BitVector loop;

  /**
   * The depth of the loop
   */
  int depth;

  /**
   * If the loop is entered from the loopHeader x times,
   * then the loopHead is executed loopMultiplier * x times.
   */
  float loopMultiplier;

  /**
   * The CFG Edges that are exits from the loop.
   */
  ArrayList<Edge> loopExits;

  LSTNode(BasicBlock bb) {
    header = bb;
  }

  /**
   * Copy constructor
   *
   * @param node for copying
   */
  protected LSTNode(LSTNode node) {
    header = node.header;
    loop = node.loop;
    depth = node.depth;
    loopMultiplier = node.loopMultiplier;
    loopExits = node.loopExits;
  }

  public BasicBlock getHeader() {
    return header;
  }

  public VM_BitVector getLoop() {
    return loop;
  }

  public String toString() {
    String tab = "";
    for (int i = 0; i < depth; i++) {
      tab += "\t";
    }
    return tab + header + " " + loop + " " + loopExits + "\n";
  }

  public LSTNode getParent() { return (LSTNode) inNodes().next(); }

  public Enumeration<LSTNode> getChildren() {
    return new Enumeration<LSTNode>() {
      private SpaceEffGraphEdge _edge = _outEdgeStart;

      public boolean hasMoreElements() { return _edge != null; }

      public LSTNode nextElement() {
        SpaceEffGraphEdge e = _edge;
        _edge = e.getNextOut();
        return (LSTNode) e.toNode();
      }
    };
  }

  public void initializeLoopExits() {
    loopExits = new ArrayList<Edge>();
  }

  public void addLoopExit(BasicBlock source, BasicBlock target, float prob) {
    loopExits.add(new Edge(source, target, prob));
  }

  static final class Edge {
    BasicBlock source;
    BasicBlock target;
    float probability;

    Edge(BasicBlock s, BasicBlock t, float p) {
      source = s;
      target = t;
      probability = p;
    }

    public String toString() {
      return source + "->" + target + " prob = " + probability;
    }
  }

}



