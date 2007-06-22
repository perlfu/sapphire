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
package org.mmtk.vm;

import org.vmmagic.pragma.Uninterruptible;


/**
 * A counter that supports atomic increment and reset.
 */
@Uninterruptible public abstract class SynchronizedCounter {

  /**
   * Reset the counter to 0, returning its previous value.
   *
   * @return The value of the counter, prior to reset.
   */
  public abstract int reset();

  /**
   * Adds 1 to the counter.
   *
   * @return the value before the add
   */
  public abstract int increment();

  /**
   * Peek at the counter
   *
   * @return The current value of the counter.
   */
  public abstract int peek();
}
