/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * @author Julian Dolby
 */
interface OPT_SpecializationGraphNode extends OPT_GraphNode {

    VM_Method getSpecializedMethod();

}

