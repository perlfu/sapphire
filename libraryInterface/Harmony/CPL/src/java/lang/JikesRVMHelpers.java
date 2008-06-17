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
package java.lang;

import org.jikesrvm.classloader.Atom;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * Class library dependent helper methods used to implement
 * class library independent code in java.lang.
 */
class JikesRVMHelpers {
  static Type[] getInterfaceTypesFromSignature(Class<?> clazz, Atom sig) {
    throw new Error("TODO");
  }

  static Type getSuperclassType(Class<?> clazz, Atom sig) {
    throw new Error("TODO");
  }

  static <T> TypeVariable<Class<T>>[] getTypeParameters(Class<T> clazz, Atom sig) {
    throw new Error("TODO");
  }
}
