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
package org.jikesrvm.classloader;

import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.runtime.Entrypoints;

/**
 * An abstract method of a java class.
 */
public final class AbstractMethod extends RVMMethod {

  /**
   * Construct abstract method information
   *
   * @param declaringClass the TypeReference object of the class that declared this method
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param exceptionTypes exceptions thrown by this method.
   * @param signature generic type of this method.
   * @param annotations array of runtime visible annotations
   * @param parameterAnnotations array of runtime visible parameter annotations
   * @param annotationDefault value for this annotation that appears
   */
  AbstractMethod(TypeReference declaringClass, MemberReference memRef, short modifiers,
                    TypeReference[] exceptionTypes, Atom signature, RVMAnnotation[] annotations,
                    RVMAnnotation[][] parameterAnnotations, Object annotationDefault) {
    super(declaringClass,
          memRef,
          modifiers,
          exceptionTypes,
          signature,
          annotations,
          parameterAnnotations,
          annotationDefault);
  }

  /**
   * Generate the code for this method
   */
  protected CompiledMethod genCode() {
    Entrypoints.unexpectedAbstractMethodCallMethod.compile();
    return Entrypoints.unexpectedAbstractMethodCallMethod.getCurrentCompiledMethod();
  }
}
