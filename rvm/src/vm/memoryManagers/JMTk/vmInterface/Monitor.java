/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.mmInterface;

import org.mmtk.utility.Constants;

import com.ibm.JikesRVM.VM_Callbacks;

import org.vmmagic.pragma.*;

/**
 * This class allows JMTk to register call backs with VM_Callbacks.
 * 
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public class Monitor 
  implements Constants, Uninterruptible, VM_Callbacks.ExitMonitor {
  public final static String Id = "$Id$"; 

  /**
   * Register the exit monitor at boot time.
   */
  public static void boot() throws InterruptiblePragma {
    VM_Callbacks.addExitMonitor(new Monitor());
  }

  /**
   * The VM is about to exit.  Notify the plan.
   *
   * @param value The exit value
   */
  public void notifyExit(int value) {
    SelectedPlan.get().notifyExit(value);
  }
}
