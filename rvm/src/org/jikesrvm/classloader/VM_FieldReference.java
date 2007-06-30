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

import org.jikesrvm.VM;
import org.jikesrvm.VM_SizeConstants;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A class to represent the reference in a class file to a field.
 */
public final class VM_FieldReference extends VM_MemberReference implements VM_SizeConstants {

  /**
   * The VM_Field that this field reference resolved to (null if not yet resolved).
   */
  private VM_Field resolvedMember;

  /**
   * The field's type
   */
  private final VM_TypeReference fieldContentsType;

  /**
   * @param tr a type reference
   * @param mn the field or method name
   * @param d the field or method descriptor
   */
  VM_FieldReference(VM_TypeReference tr, VM_Atom mn, VM_Atom d) {
    super(tr, mn, d);
    fieldContentsType = VM_TypeReference.findOrCreate(tr.getClassLoader(), d);
  }

  /**
   * @return the type of the field's value
   */
  @Uninterruptible
  public VM_TypeReference getFieldContentsType() {
    return fieldContentsType;
  }

  /**
   * How many stackslots do value of this type take?
   */
  public int getNumberOfStackSlots() {
    return getFieldContentsType().getStackWords();
  }

  /**
   * Get size of the field's value, in bytes.
   */
  @Uninterruptible
  public int getSize() {
    return fieldContentsType.getMemoryBytes();
  }

  /**
   * Do this and that definitely refer to the different fields?
   */
  public boolean definitelyDifferent(VM_FieldReference that) {
    if (this == that) return false;
    if (getName() != that.getName() || getDescriptor() != that.getDescriptor()) {
      return true;
    }
    VM_Field mine = peekResolvedField();
    VM_Field theirs = that.peekResolvedField();
    if (mine == null || theirs == null) return false;
    return mine != theirs;
  }

  /**
   * Do this and that definitely refer to the same field?
   */
  public boolean definitelySame(VM_FieldReference that) {
    if (this == that) return true;
    if (getName() != that.getName() || getDescriptor() != that.getDescriptor()) {
      return false;
    }
    VM_Field mine = peekResolvedField();
    VM_Field theirs = that.peekResolvedField();
    if (mine == null || theirs == null) return false;
    return mine == theirs;
  }

  /**
   * Has the field reference already been resolved into a target method?
   */
  public boolean isResolved() {
    return resolvedMember != null;
  }

  /**
   * For use by VM_Field constructor
   */
  void setResolvedMember(VM_Field it) {
    if (VM.VerifyAssertions) VM._assert(resolvedMember == null || resolvedMember == it);
    resolvedMember = it;
  }

  /**
   * Find the VM_Field that this field reference refers to using
   * the search order specified in JVM spec 5.4.3.2.
   * @return the VM_Field that this method ref resolved to or null if it cannot be resolved.
   */
  public VM_Field peekResolvedField() {
    if (resolvedMember != null) return resolvedMember;

    // Hasn't been resolved yet. Try to do it now without triggering class loading.
    VM_Class declaringClass = (VM_Class) type.peekType();
    if (declaringClass == null) return null;
    return resolveInternal(declaringClass);
  }

  /**
   * Find the VM_Field that this field reference refers to using
   * the search order specified in JVM spec 5.4.3.2.
   * @return the VM_Field that this method ref resolved to.
   */
  public synchronized VM_Field resolve() {
    if (resolvedMember != null) return resolvedMember;

    // Hasn't been resolved yet. Do it now triggering class loading if necessary.
    return resolveInternal((VM_Class) type.resolve());
  }

  private VM_Field resolveInternal(VM_Class declaringClass) {
    if (!declaringClass.isResolved()) {
      declaringClass.resolve();
    }
    for (VM_Class c = declaringClass; c != null; c = c.getSuperClass()) {
      // Look in this class
      VM_Field it = c.findDeclaredField(name, descriptor);
      if (it != null) {
        resolvedMember = it;
        return resolvedMember;
      }
      // Look at all interfaces directly and indirectly implemented by this class.
      for (VM_Class i : c.getDeclaredInterfaces()) {
        it = searchInterfaceFields(i);
        if (it != null) {
          resolvedMember = it;
          return resolvedMember;
        }
      }
    }
    throw new NoSuchFieldError(this.toString());
  }

  private VM_Field searchInterfaceFields(VM_Class c) {
    VM_Field it = c.findDeclaredField(name, descriptor);
    if (it != null) return it;
    for (VM_Class i : c.getDeclaredInterfaces()) {
      it = searchInterfaceFields(i);
      if (it != null) return it;
    }
    return null;
  }
}
