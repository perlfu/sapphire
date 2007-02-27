/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002, 2004, 2005
 */
package gnu.java.lang;

import java.lang.instrument.Instrumentation;

/**
 * @author Elias Naur
 */
public final class JikesRVMSupport {
  public static Instrumentation createInstrumentation() {
    return new InstrumentationImpl();
  }
}
