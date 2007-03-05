/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt.ir;

import java.util.Enumeration;

/**
 * Extend java.util.Enumeration to avoid downcasts from object.
 *
 * @author Dave Grove
 */
public interface OPT_RegisterOperandEnumeration extends Enumeration<OPT_RegisterOperand> {
  /** Same as nextElement but avoid the need to downcast from Object */
  OPT_RegisterOperand next();
}

