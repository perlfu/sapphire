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

import org.jikesrvm.classloader.VM_Atom;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import gnu.java.lang.reflect.ClassSignatureParser;

/**
 * Class library dependent helper methods used to implement
 * class library independent code in java.lang.
 */
class JikesRVMHelpers {


  static Type[] getInterfaceTypesFromSignature(Class<?> clazz, VM_Atom sig) {
    ClassSignatureParser p = new ClassSignatureParser(clazz, sig.toString());
    return p.getInterfaceTypes();
  }

  static Type getSuperclassType(Class<?> clazz, VM_Atom sig) {
    ClassSignatureParser p = new ClassSignatureParser(clazz, sig.toString());
    return p.getSuperclassType();
  }

  static <T> TypeVariable<Class<T>>[] getTypeParameters(Class<T> clazz, VM_Atom sig) {
    ClassSignatureParser p = new ClassSignatureParser(clazz, sig.toString());
    return p.getTypeParameters();
  }


}
