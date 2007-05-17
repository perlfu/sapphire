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

/**
 *  This class generates an enumeration of nodes of a graph, in order
 * of increasing finishing time in a reverse Depth First Search,
 * i.e. a search traversing nodes from target to source.
 */
class OPT_ReverseDFSenumerateByFinish extends OPT_DFSenumerateByFinish {

  /**
   *  Construct a reverse DFS across a graph.
   *
   * @param net The graph over which to search.
   */
  OPT_ReverseDFSenumerateByFinish(OPT_Graph net) {
    super(net);
  }

  /**
   *  Construct a reverse DFS across a subset of a graph, starting
   * at the given set of nodes.
   *
   * @param net The graph over which to search
   * @param nodes The nodes at which to start the search
   */
  OPT_ReverseDFSenumerateByFinish(OPT_Graph net, OPT_GraphNodeEnumeration nodes) {
    super(net, nodes);
  }

  /**
   *  Traverse edges from target to source.
   *
   * @param n A node in the DFS
   * @return The nodes that have edges leading to n
   */
  protected OPT_GraphNodeEnumeration getConnected(OPT_GraphNode n) {
    return n.inNodes();
  }
}



