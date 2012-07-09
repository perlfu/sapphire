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
package org.mmtk.harness.lang.pcode;

import org.mmtk.harness.Harness;
import org.mmtk.harness.exception.OutOfMemory;
import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.ast.AST;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.mmtk.harness.lang.type.Type;
import org.mmtk.harness.lang.type.UserType;
import org.mmtk.harness.vm.ObjectModel;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Object allocation operation. 3 operands:
 * <ul>
 *   <li># data words
 *   <li># reference words
 *   <li>alignment
 * </ul>
 * <p>
 * Always produces a result.
 */
public final class AllocUserOp extends UnaryOp {

  /** Call site */
  private final int site;
  private final int dataCount;
  private final int refCount;
  private final Type type;

  /**
   * Allocate an object of the given user-defined type
   * @param source Source code AST element best corresponding to this op
   * @param resultTemp Register to store the result in
   * @param type Type of object to allocate
   * @param doubleAlign Does the object require double-word alignment ?
   * @param site Call site identifier
   */
  public AllocUserOp(AST source, Register resultTemp, UserType type, Register doubleAlign, int site) {
    super(source,"alloc",resultTemp,doubleAlign);
    this.site = site;
    this.dataCount = type.dataFieldCount();
    this.refCount = type.referenceFieldCount();
    this.type = type;
  }
  /** Get the alignment operand from <code>frame</code> */
  private boolean getDoubleAlign(StackFrame frame) {
    return frame.get(operand).getBoolValue();
  }

  @Override
  public void exec(Env env) {
    StackFrame frame = env.top();
    ObjectReference object;
    try {
      object = env.alloc(refCount, dataCount, getDoubleAlign(frame), site);
    } catch (OutOfMemory e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Error allocating object id:"+ObjectModel.lastObjectId()+" refs:"+refCount+
          " ints: "+dataCount+" align:"+getDoubleAlign(frame)+" site:"+site,e);
    }
    setResult(frame,new ObjectValue(object));
    if (Harness.gcEveryAlloc()) {
      env.gc();
    }
  }

  /**
   * String representation of this operation
   * <pre>
   *   tx <- alloc(type)
   * </pre>
   */
  @Override
  public String toString() {
    return super.toString() + "(" + type.getName() + ")";
  }


}
