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
package org.jikesrvm;

import org.jikesrvm.objectmodel.VM_TIBLayoutConstants;
import org.jikesrvm.objectmodel.VM_ThinLockConstants;

/**
 * Constants describing vm object, stack, and register characteristics.
 * Some of these constants are architecture-specific
 * and some are (at the moment) architecture-neutral.
 */
public interface VM_Constants extends VM_ThinLockConstants,         // architecture-neutral
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
