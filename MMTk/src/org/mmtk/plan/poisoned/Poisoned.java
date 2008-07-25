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
package org.mmtk.plan.poisoned;

import org.mmtk.plan.marksweep.MS;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Word;

/**
 * This class implements a poisoned collector, that is essentially a test
 * case for read and write barriers in the VM.
 */
@Uninterruptible
public class Poisoned extends MS {
  /**
   * Perform any required write barrier action when installing an object reference
   * a boot time.
   *
   * @param reference the reference value that is to be stored
   * @return The raw value to be
   */
  public Word bootTimeWriteBarrier(Word reference) {
    return reference.or(Word.one());
  }

  /**
   * Poison a reference value.
   */
  @Inline
  public static Word poison(ObjectReference reference) {
    return reference.toAddress().toWord().or(Word.one());
  }

  /**
   * DePoison a reference value.
   */
  @Inline
  public static ObjectReference depoison(Word value) {
    return value.and(Word.one().not()).toAddress().toObjectReference();
  }
}
