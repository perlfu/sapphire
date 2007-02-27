/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright ANU, 2004
 */
package org.vmmagic.unboxed;

/**
 * The object reference type is used by the runtime system and collector to
 * represent a type that holds a reference to a single object. 
 * We use a separate type instead of the Java Object type for coding clarity,
 * to make a clear distinction between objects the VM is written in, and 
 * objects that the VM is managing. No operations that can not be completed in
 * pure Java should be allowed on Object.
 * 
 * @author Daniel Frampton
 */
public final class ObjectReference {

  /**
   * Convert from an object to a reference.
   * @param obj The object 
   * @return The corresponding reference
   */
  public static ObjectReference fromObject(Object obj) {
    return null;
  }

  /**
   * Return a null reference
   */
  public static ObjectReference nullReference() {
    return null;
  }

  /**
   * Convert from an reference to an object. Note: this is a JikesRVM
   * specific extension to vmmagic.
   * @return The object
   */
  public Object toObject() {
    return null;
  }

  /**
   * Get a heap address for the object.
   */
  public Address toAddress() {
    return null;
  }

  /**
   * Is this a null reference?
   */
  public boolean isNull() {
    return false;
  }
}
