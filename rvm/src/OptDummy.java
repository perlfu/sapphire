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

/*
 * Dummy class containing enough references to force java compiler
 * to find every class comprising the opt compiler, so everything gets
 * recompiled by just compiling "OptDummy.java".
 * <p/>
 * The minimal set has to be discovered by trial and error. Sorry. --Derek
 */
class OptDummy {
  static org.jikesrvm.compilers.opt.driver.OptimizingCompiler a;
  static org.jikesrvm.compilers.opt.runtimesupport.OptSaveVolatile g;
  static org.jikesrvm.compilers.opt.specialization.SpecializedMethodPool q;
}
