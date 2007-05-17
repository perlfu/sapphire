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
package org.jikesrvm.objectmodel;

import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.runtime.VM_Memory;

/**
 * State for the field layout engine.  Subtypes of this are closely
 * tied to field layout schemes, and are generally defined in same.
 *
 * A FieldLayoutContext deals in an abstract offset space, where
 * there is no header, and fields are laid out relative to 0.
 *
 * This abstract superclass looks after the total object size and
 * alignment.
 */
public abstract class VM_FieldLayoutContext implements VM_SizeConstants {

  /* *****************************************************************
   *                         Constants
   */
  protected static final int OBJECT_SIZE_ALIGN = BYTES_IN_ADDRESS;

  /* *****************************************************************
  *                        Class fields
  */

  /** Alignment requirements.  */
  private byte alignment = BYTES_IN_INT;

  /** The size of the current object as laid out */
  private int objectSize = 0;

  /** Return the offset of a new field of the given size */
  abstract int nextOffset(int size, boolean isReference);

  /* *****************************************************************
  *                        Initialization
  */

  /**
   * Constructor for a (the) top-level object.
   *
   * @param alignment
   */
  protected VM_FieldLayoutContext(byte alignment) {
    this.alignment = alignment;
  }

  /**
   * Constructor for an object with a superclass.  The superclass
   * is used to initialize the layout.
   *
   * @param alignment
   * @param superLayout
   */
  protected VM_FieldLayoutContext(byte alignment, VM_FieldLayoutContext superLayout) {
    this.alignment = alignment;
    if (superLayout != null) {
      objectSize = superLayout.getObjectSize();
    }
  }

  /* *****************************************************************
   *                  Instance methodss
   */

  /**
   * Adjust alignment to the next highest value.  In Java, the only 2
   * possibilities are int-aligned or long-aligned.
   *
   * @param fieldSize
   */
  protected void adjustAlignment(int fieldSize) {
    alignment = (fieldSize == BYTES_IN_LONG) ? BYTES_IN_LONG : alignment;
  }

  /**
   * @return the current alignment value
   */
  int getAlignment() {
    return alignment;
  }

  /**
   * @return The current size of the object (excluding header)
   */
  protected int getObjectSize() {
    return objectSize;
  }

  /**
   * Adjust the size of the object if necessary to accommodate a field.
   *
   * @param size The size occupied by data fields in the object.
   */
  protected void ensureObjectSize(int size) {
    objectSize = size > objectSize ? VM_Memory.alignUp(size, OBJECT_SIZE_ALIGN) : objectSize;
  }
}
