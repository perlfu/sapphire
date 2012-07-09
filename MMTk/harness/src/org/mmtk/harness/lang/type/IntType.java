/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.harness.lang.type;

import org.mmtk.harness.lang.runtime.IntValue;
import org.mmtk.harness.lang.runtime.Value;

/**
 * The built-in <code>int</code> type
 */
public class IntType extends AbstractType {

  IntType() {
    super("int");
  }

  @Override
  public Value initialValue() {
    return IntValue.ZERO;
  }

}
