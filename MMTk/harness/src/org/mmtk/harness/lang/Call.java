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
package org.mmtk.harness.lang;

import java.util.List;

import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.parser.MethodTable;

/**
 * A call to a method.
 */
public class Call implements Statement, Expression {
  /** Method table */
  private final MethodTable methods;
  /** Method name */
  private final String methodName;
  /** Parameter expressions */
  private final List<Expression> params;

  /**
   * Call a method.
   */
  public Call(MethodTable methods, String methodName, List<Expression> params) {
    this.methods = methods;
    this.methodName = methodName;
    this.params = params;
  }

  /**
   * Run this statement.
   */
  public void exec(Env env) throws ReturnException {
    Method method = methods.get(methodName);

    Value[] values = evalParams(env);
    popTemporaries(env, values);
    method.exec(env, values);
  }

  /**
   * Call as an expression
   */
  public Value eval(Env env) {
    Method method = methods.get(methodName);

    Value[] values = evalParams(env);

    if (Trace.isEnabled(Item.CALL)) {
      System.out.printf("Call %s(",methodName);
      for (int i=0; i < values.length; i++) {
        System.out.printf("%s%s",values[i].toString(),i == values.length-1 ? ")\n" : ", ");
      }
    }
    popTemporaries(env, values);
    // No GC safe points between here and when everything is saved in the callee's stack
    return method.eval(env, values);
  }

  /**
   * Evaluate method parameters, ensuring that temporaries are gc-safe between
   * each evaluation.
   * @param env
   * @return
   */
  private Value[] evalParams(Env env) {
    Value[] values = new Value[params.size()];
    for(int i=0; i < params.size(); i++) {
      values[i] = params.get(i).eval(env);
      // GC may occur between evaluating each parameter
      env.pushTemporary(values[i]);
      env.gcSafePoint();
    }
    return values;
  }

  /**
   * Pop all temporaries off the stack
   * @param env
   * @param values
   */
  private void popTemporaries(Env env, Value[] values) {
    for(int i=params.size() - 1; i >= 0; i--) {
      env.popTemporary(values[i]);
    }
  }


}
