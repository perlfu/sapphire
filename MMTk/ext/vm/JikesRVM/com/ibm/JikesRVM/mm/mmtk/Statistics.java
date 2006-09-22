/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package com.ibm.JikesRVM.mm.mmtk;

import org.mmtk.utility.Constants;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * $Id: Statistics.java,v 1.2 2006/06/19 06:08:15 steveb-oss Exp $ 
 *
 * @author Steve Blackburn
 * @author Perry Cheng
 *
 * @version $Revision: 1.2 $
 * @date $Date: 2006/06/19 06:08:15 $
 */
public final class Statistics extends org.mmtk.vm.Statistics implements Constants, Uninterruptible {
  /**
   * Returns the number of collections that have occured.
   *
   * @return The number of collections that have occured.
   */
  public final int getCollectionCount()
    throws UninterruptiblePragma {
    return MM_Interface.getCollectionCount();
  }

  /**
   * Read cycle counter
   */
  public final long cycles() {
    return VM_Time.cycles();
  }

  /**
   * Convert cycles to milliseconds
   */
  public final double cyclesToMillis(long c) {
    return VM_Time.cyclesToMillis(c);
  }

  /**
   * Convert cycles to seconds
   */
  public final double cyclesToSecs(long c) {
    return VM_Time.cyclesToSecs(c);
  }

  /**
   * Convert milliseconds to cycles
   */
  public final long millisToCycles(double t) {
    return VM_Time.millisToCycles(t);
  }

  /**
   * Convert seconds to cycles
   */
  public final long secsToCycles(double t) {
    return VM_Time.secsToCycles(t);
  }
}
