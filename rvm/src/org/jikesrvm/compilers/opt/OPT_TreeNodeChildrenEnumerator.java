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
import java.util.NoSuchElementException;

/**
 * This class provides enumeration of all children of a OPT_TreeNode
 */
final class OPT_TreeNodeChildrenEnumerator implements Enumeration<OPT_TreeNode> {

  /**
   * the current child we are working on
   */
  private OPT_TreeNode currentChild;

  /**
   * Provides iteration over a list of children tree nodes
   * @param   node  Root of the tree to iterate over.
   */
  OPT_TreeNodeChildrenEnumerator(OPT_TreeNode node) {
    // start at the first child
    currentChild = node.getLeftChild();
  }

  /**
   * any elements left?
   * @return whether there are any elements left
   */
  public boolean hasMoreElements() {
    return currentChild != null;
  }

  /**
   * returns the next element in the list iterator
   * @return the next element in the list iterator or null
   */
  public OPT_TreeNode nextElement() {
    // save the return value
    OPT_TreeNode returnValue = currentChild;

    // update the currentChild pointer, if possible
    if (currentChild != null) {
      currentChild = currentChild.getRightSibling();
    } else {
      throw new NoSuchElementException("OPT_TreeNodeChildrenEnumerator");
    }

    // return the value
    return returnValue;
  }
}



