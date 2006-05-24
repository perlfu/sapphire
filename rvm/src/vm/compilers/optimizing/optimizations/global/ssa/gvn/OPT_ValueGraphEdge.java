/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * This class implements an edge in the value graph used in global value 
 * numbering
 * ala Alpern, Wegman and Zadeck.  See Muchnick p.348 for a nice
 * discussion.
 *
 * @author Stephen Fink
 */
final class OPT_ValueGraphEdge extends OPT_SpaceEffGraphEdge {

  OPT_ValueGraphEdge (OPT_ValueGraphVertex src, OPT_ValueGraphVertex target) {
    super(src, target);
  }

  public String toString () {
    OPT_ValueGraphVertex src = (OPT_ValueGraphVertex)fromNode();
    OPT_ValueGraphVertex dest = (OPT_ValueGraphVertex)toNode();
    return  src.getName() + " --> " + dest.getName();
  }
}



