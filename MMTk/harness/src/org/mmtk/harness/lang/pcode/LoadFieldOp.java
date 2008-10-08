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

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.ast.AST;
import org.mmtk.harness.lang.ast.Type;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.IntValue;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.vmmagic.unboxed.ObjectReference;

public final class LoadFieldOp extends BinaryOp {

  /** The field type (int or object) */
  public final Type fieldType;

  /**
   * Create an instruction for the operation
   *   resultTemp <- object.<fieldType>[index]
   *
   * @param source     Source file location (parser Token)
   * @param resultTemp Result destination
   * @param object     Object operand
   * @param index      Index operand
   * @param fieldType  Field type (int or object)
   */
  public LoadFieldOp(AST source, Register resultTemp, Register object, Register index, Type fieldType) {
    super(source,"loadfield",resultTemp, object, index);
    this.fieldType = fieldType;
  }

  /**
   * Get the object on which this instruction operates from the
   * location it occupies in the stack frame.
   *
   * @param frame
   * @return
   */
  private ObjectReference getObject(StackFrame frame) {
    return frame.get(op1).getObjectValue();
  }

  /**
   * Get the field index from the stack frame (ie from the location
   * where the result of evaluating the index expression has been
   * stored).
   *
   * @param frame
   * @return
   */
  public int getIndex(StackFrame frame) {
    return frame.get(op2).getIntValue();
  }

  public String toString() {
    return String.format("t%d <- t%d.%s[t%d]", getResult(), op1,
        fieldType == Type.OBJECT ? "object" : "int",  op2);
  }

  @Override
  public void exec(Env env) {
    StackFrame frame = env.top();
    switch (fieldType) {
      case INT:
        setResult(frame, new IntValue(env.loadDataField(getObject(frame), getIndex(frame))));
        break;
      case OBJECT:
        setResult(frame, new ObjectValue(env.loadReferenceField(getObject(frame), getIndex(frame))));
        break;
    }
  }

}
