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

import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_IR;

/**
 * OPT_DominatorCell represents a set of basic blocks, used in
 * the dataflow calculation
 */
class OPT_DominatorCell extends OPT_DF_AbstractCell {

  /**
   * Pointer to the governing IR.
   */
  OPT_IR ir;
  /**
   * The basic block corresponding to this lattice cell.
   */
  OPT_BasicBlock block;
  /**
   * Bit set representation of the dominators for this basic block.
   */
  OPT_BitVector dominators;
  /**
   * A guess of the upper bound on the number of out edges for most basic
   * blocks.
   */
  static final int CAPACITY = 5;

  /**
   * Make a bit set for a basic block
   * @param bb the basic block
   * @param ir the governing IR
   */
  public OPT_DominatorCell(OPT_BasicBlock bb, OPT_IR ir) {
    super(CAPACITY);
    block = bb;
    dominators = new OPT_BitVector(ir.getMaxBasicBlockNumber() + 1);
    this.ir = ir;
  }

  /**
   * Return a String representation of this cell.
   * @return a String representation of this cell.
   */
  public String toString() {
    return block + ":" + dominators;
  }

  /**
   * Include a single basic block in this set.
   * @param bb the basic block
   */
  public void addSingleBlock(OPT_BasicBlock bb) {
    dominators.set(bb.getNumber());
  }

  /**
   * Include all basic blocks in this set.
   * <p> TODO: make this more efficient.
   * @param ir the governing ir
   */
  public void setTOP(OPT_IR ir) {
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); e.hasMoreElements();) {
      OPT_BasicBlock b = e.next();
      dominators.set(b.getNumber());
    }
  }
}
