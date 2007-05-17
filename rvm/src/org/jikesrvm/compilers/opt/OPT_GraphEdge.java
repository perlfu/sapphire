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
 *  Graph representations that use explicit ede objects should have
 * their edge objects implement this interface.
 */
interface OPT_GraphEdge extends OPT_VCGEdge {

  OPT_GraphNode from();

  OPT_GraphNode to();

}



