/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import com.ibm.JikesRVM.classloader.VM_Type;

/**
 * @author Perry Cheng
 */
class TypeComparator implements java.util.Comparator {
    
  public int compare (Object a, Object b) {
    if (a == null) return 1;
    if (b == null) return -1;
    if ((a instanceof VM_Type) && (b instanceof VM_Type)) {
      VM_Type aa = (VM_Type) a;
      VM_Type bb = (VM_Type) b;
      if (aa.bootBytes > bb.bootBytes) return -1;
      if (aa.bootBytes < bb.bootBytes) return 1;
      return 0;
    }
    return 0;
  }
}
