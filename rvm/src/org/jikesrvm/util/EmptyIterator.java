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
package org.jikesrvm.util;

import java.util.Iterator;

/**
 * A generic iterator containing no items
 */
public final class EmptyIterator<T> implements Iterator<T> {
  public boolean hasNext() {
    return false;
  }
  public T next() {
    return null;
  }
  public void remove() {}
}
