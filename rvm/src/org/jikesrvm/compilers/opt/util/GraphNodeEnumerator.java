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
package org.jikesrvm.compilers.opt.util;

import java.util.Enumeration;
import java.util.Iterator;


public abstract class GraphNodeEnumerator implements GraphNodeEnumeration {

  public final GraphNode nextElement() { return next(); }

  public static GraphNodeEnumerator create(Enumeration<GraphNode> e) {
    return new Enum(e);
  }

  public static GraphNodeEnumerator create(Iterator<GraphNode> i) {
    return new Iter(i);
  }

  public static GraphNodeEnumerator create(Iterable<GraphNode> i) {
    return new Iter(i.iterator());
  }

  private static final class Enum extends GraphNodeEnumerator {
    private final Enumeration<GraphNode> e;

    Enum(Enumeration<GraphNode> e) {
      this.e = e;
    }

    public boolean hasMoreElements() { return e.hasMoreElements(); }

    public GraphNode next() { return e.nextElement(); }
  }

  private static final class Iter extends GraphNodeEnumerator {
    private final Iterator<GraphNode> i;

    Iter(Iterator<GraphNode> i) {
      this.i = i;
    }

    public boolean hasMoreElements() { return i.hasNext(); }

    public GraphNode next() { return i.next(); }
  }
}
