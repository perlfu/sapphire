/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm;

import org.jikesrvm.ArchitectureSpecific.VM_StackframeLayoutConstants;
import org.jikesrvm.ArchitectureSpecific.VM_RegisterConstants;
import org.jikesrvm.ArchitectureSpecific.VM_TrapConstants;

/**
 * Constants describing vm object, stack, and register characteristics.
 * Some of these constants are architecture-specific
 * and some are (at the moment) architecture-neutral.
 *
 * @author Bowen Alpern
 * @author Stephen Fink
 * @author David Grove
 */
public interface VM_Constants
extends   VM_ThinLockConstants,         // architecture-neutral
          VM_TIBLayoutConstants,        // architecture-neutral
          VM_HeapLayoutConstants,       // architecture-neutral
          VM_SizeConstants             // 'semi-'architecture-neutral
{
  /**
   * For assertion checking things that should never happen.
   */
  boolean NOT_REACHED = false;

  /**
   * Reflection uses an integer return from a function which logically
   * returns a triple.  The values are packed in the interger return value
   * by the following masks.
   */
  int REFLECTION_GPRS_BITS = 5;
  int REFLECTION_GPRS_MASK = (1 << REFLECTION_GPRS_BITS) - 1;
  int REFLECTION_FPRS_BITS = 5;
  int REFLECTION_FPRS_MASK = (1 << REFLECTION_FPRS_BITS) - 1;

}
