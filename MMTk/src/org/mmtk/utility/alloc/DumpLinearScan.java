/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.alloc;

import org.mmtk.vm.VM;
import org.mmtk.utility.Log;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Simple linear scan to dump object information.
 * 
 * @author Daniel Frampton
 */
@Uninterruptible
public final class DumpLinearScan extends LinearScan {
  /**
   * Scan an object.
   * 
   * @param object The object to scan
   */
  @Inline
  public void scan(ObjectReference object) { 
    Log.write("[");
    Log.write(object.toAddress());
    Log.write("], SIZE = ");
    Log.writeln(VM.objectModel.getCurrentSize(object));
  }
}
