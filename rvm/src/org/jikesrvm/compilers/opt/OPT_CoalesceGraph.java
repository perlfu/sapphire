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

import java.util.Enumeration;
import java.util.HashMap;
import org.jikesrvm.compilers.opt.ir.OPT_Register;

/**
 * This class represents a graph, where
 *    - the nodes are registers
 *    - the edge weights represent affinities between registers.
 *
 * This graph is used to drive coalescing during register allocation.
 *
 * Implementation: this is meant to be an undirected graph.  By
 * convention, we enforce that the register with the lower number is the
 * source of an edge.
 */
class OPT_CoalesceGraph extends OPT_SpaceEffGraph {

  /**
   * Mapping register -> Node
   */
  final HashMap<OPT_Register, Node> nodeMap = new HashMap<OPT_Register, Node>();

  /**
   * find or create a node in the graph corresponding to a register.
   */
  private Node findOrCreateNode(OPT_Register r) {
    Node n = nodeMap.get(r);
    if (n == null) {
      n = new Node(r);
      nodeMap.put(r, n);
      addGraphNode(n);
    }
    return n;
  }

  /**
   * Find the node corresponding to a regsiter.
   */
  Node findNode(OPT_Register r) {
    return nodeMap.get(r);
  }

  /**
   * find or create an edge in the graph
   */
  private Edge findOrCreateEdge(Node src, Node dest) {
    Edge edge = null;
    for (Enumeration<OPT_VisEdge> e = src.edges(); e.hasMoreElements();) {
      Edge candidate = (Edge) e.nextElement();
      if (candidate.toNode() == dest) {
        edge = candidate;
        break;
      }
    }
    if (edge == null) {
      edge = new Edge(src, dest);
      addGraphEdge(edge);
    }
    return edge;
  }

  /**
   * Add an affinity of weight w between registers r1 and r2
   */
  void addAffinity(int w, OPT_Register r1, OPT_Register r2) {
    Node src;
    Node dest;
    if (r1.getNumber() == r2.getNumber()) return;

    // the register with the smaller number is the source of the edge.
    if (r1.getNumber() < r2.getNumber()) {
      src = findOrCreateNode(r1);
      dest = findOrCreateNode(r2);
    } else {
      src = findOrCreateNode(r2);
      dest = findOrCreateNode(r1);
    }

    Edge edge = findOrCreateEdge(src, dest);

    edge.addWeight(w);
  }

  static class Node extends OPT_SpaceEffGraphNode {
    OPT_Register r;

    Node(OPT_Register r) {
      this.r = r;
    }

    OPT_Register getRegister() {
      return r;
    }
  }

  static class Edge extends OPT_SpaceEffGraphEdge {
    private int w;

    Edge(Node src, Node dest) {
      super(src, dest);
    }

    void addWeight(int x) {
      w += x;
    }

    int getWeight() {
      return w;
    }
  }
}
