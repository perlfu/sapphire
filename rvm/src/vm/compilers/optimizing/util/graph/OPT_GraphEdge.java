/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 *  Graph representations that use explicit ede objects should have
 * their edge objects implement this interface.
 *
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
interface OPT_GraphEdge {

    OPT_GraphNode from();

    OPT_GraphNode to();

}



