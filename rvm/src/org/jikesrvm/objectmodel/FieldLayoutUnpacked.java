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

import org.jikesrvm.SizeConstants;
import org.jikesrvm.classloader.RVMClass;

public class FieldLayoutUnpacked extends FieldLayout implements SizeConstants {

  private static class LayoutContext extends FieldLayoutContext {
    private static final int NO_HOLE = -1;
    int intHole = NO_HOLE;

    LayoutContext(byte alignment) {
      super(alignment);
    }

    LayoutContext(byte alignment, LayoutContext superLayout) {
      super(alignment, superLayout);
      if (superLayout != null) {
        intHole = superLayout.intHole;
      }
    }

    /** Return the next available offset for a given size */
    @Override
    int nextOffset(int size, boolean isReference) {
      int objectSize = getObjectSize();
      if (size == FieldLayoutUnpacked.BYTES_IN_DOUBLE) {
        adjustAlignment(FieldLayoutUnpacked.BYTES_IN_DOUBLE);
        if ((objectSize & 0x7) == 0) {
          ensureObjectSize(objectSize + FieldLayoutUnpacked.BYTES_IN_DOUBLE);
          return objectSize;
        } else {
          ensureObjectSize(objectSize + FieldLayoutUnpacked.BYTES_IN_DOUBLE + FieldLayoutUnpacked.BYTES_IN_INT);
          intHole = objectSize;
          return objectSize + FieldLayoutUnpacked.BYTES_IN_INT;
        }
      } else if (intHole >= 0) {
        int result = intHole;
        intHole = NO_HOLE;
        return result;
      } else {
        ensureObjectSize(objectSize + FieldLayoutUnpacked.BYTES_IN_INT);
        return objectSize;
      }
    }
  }

  public FieldLayoutUnpacked(boolean largeFieldsFirst, boolean clusterReferenceFields) {
    super(largeFieldsFirst, clusterReferenceFields);
  }

  /**
   * @param klass the class to layout
   * @return The layout context
   */
  @Override
  protected FieldLayoutContext getLayoutContext(RVMClass klass) {
    return new LayoutContext((byte) klass.getAlignment(), (LayoutContext) klass.getFieldLayoutContext());
  }
}
