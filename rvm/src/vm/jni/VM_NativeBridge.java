/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.jni;

/**
 * Methods of a class that implements this interface are treated specially 
 * by the compilers:
 *  -They are only called from C or C++ program
 *  -The compiler will generate the necessary prolog to insert a glue stack
 *   frame to map from the native stack/register convention to RVM's convention
 *  -It is an error to call these methods from Java
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */ 
interface VM_NativeBridge { }
