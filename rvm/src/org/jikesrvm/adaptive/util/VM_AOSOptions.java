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
package org.jikesrvm.adaptive.util;

/**
 * Additional option values that are computed internally are defined
 * here.  Commnad line options are inherited from VM_AOSExternalOptions
 * which is machine generated from the various .dat files.
 */
public final class VM_AOSOptions extends VM_AOSExternalOptions {
  public int DERIVED_MAX_OPT_LEVEL;

  public int DERIVED_FILTER_OPT_LEVEL;
}
