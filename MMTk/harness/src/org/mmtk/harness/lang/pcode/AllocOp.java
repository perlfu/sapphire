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
package org.mmtk.harness.lang.pcode;

import org.mmtk.harness.Harness;
import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.ast.AST;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Object allocation operation.  3 operands:
 * - # data words
 * - # reference words
 * - alignment
 * Always produces a result.
 */
public final class AllocOp extends TernaryOp {

  /** Call site */
  private final int site;

  public AllocOp(AST source, Register resultTemp, Register dataCount, Register refCount, Register doubleAlign,int site) {
    super(source,"alloc",resultTemp, dataCount, refCount, doubleAlign);
    this.site = site;
  }

  /** Get the data count operand from <code>frame</code> */
  private int getDataCount(StackFrame frame) {
    return frame.get(op1).getIntValue();
  }
  /** Get the reference count operand from <code>frame</code> */
  private int getRefCount(StackFrame frame) {
    return frame.get(op2).getIntValue();
  }
  /** Get the alignment operand from <code>frame</code> */
  private boolean getDoubleAlign(StackFrame frame) {
    return frame.get(op3).getBoolValue();
  }

  @Override
  public void exec(Env env) {
    StackFrame frame = env.top();
    ObjectReference object = env.alloc(getRefCount(frame), getDataCount(frame), getDoubleAlign(frame),site);
    setResult(frame,new ObjectValue(object));
    if (Harness.gcEveryAlloc()) {
      env.gc();
    }
  }

}
