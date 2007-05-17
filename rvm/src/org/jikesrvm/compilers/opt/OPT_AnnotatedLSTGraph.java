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

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.OPT_IR;

/**
 * Extends the functionality of a {@link OPT_LSTGraph} so that it comprises
 * {@link OPT_AnnotatedLSTNode}s which have extra information in them.
 *
 * @see OPT_LSTGraph
 * @see OPT_AnnotatedLSTNode
 */
public class OPT_AnnotatedLSTGraph extends OPT_LSTGraph {
  /**
   * Debug messages?
   */
  private static final boolean DEBUG = false;

  /**
   * Debug helper
   * @param message debug message
   */
  private static void report(String message) {
    if (DEBUG) {
      VM.sysWrite(message);
    }
  }

  /**
   * The main entry point
   * @param ir the IR to process
   */
  public static void perform(OPT_IR ir) {
    if (DEBUG) {
      report("Creating an AnnotatedLSTGraph for " + ir.method);
    }
    ir.HIRInfo.LoopStructureTree = new OPT_AnnotatedLSTGraph(ir, ir.HIRInfo.LoopStructureTree);
    if (DEBUG) {
      report(ir.HIRInfo.LoopStructureTree.toString());
    }
  }

  /**
   * Constructor
   *
   * @param ir    The containing IR
   * @param graph The {@link OPT_LSTGraph} to convert into an annotated graph
   */
  OPT_AnnotatedLSTGraph(OPT_IR ir, OPT_LSTGraph graph) {
    super(graph);
    rootNode = new OPT_AnnotatedLSTNode(ir, rootNode);
  }
}
