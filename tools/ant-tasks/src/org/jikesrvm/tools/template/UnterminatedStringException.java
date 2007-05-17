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
package org.jikesrvm.tools.template;

class UnterminatedStringException extends RuntimeException {
  private static final long serialVersionUID = 5639864127476661778L;
  public UnterminatedStringException() { super(); }
  public UnterminatedStringException(String msg) { super(msg); }
}
