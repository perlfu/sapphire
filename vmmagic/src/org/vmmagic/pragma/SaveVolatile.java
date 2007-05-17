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
package org.vmmagic.pragma;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import org.vmmagic.Pragma;

/**
 * Methods of a class that implements this interface
 * are treated specially by the machine code compiler:
 * the method prologue saves all the volatile registers
 * and the method epilogue restores all the volatile registers
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Pragma
public @interface SaveVolatile { }
