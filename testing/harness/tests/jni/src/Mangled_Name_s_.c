/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 *$Id$
 */
/* Native code for Mangled_Name_s_.java
 *
 * @author unascribed
 */

#include <stdio.h>
#include "Mangled_Name_s_.h"
#include <jni.h>

int verbose=1;

/*
 * Class:     Mangled_1Name_1s_1
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_Mangled_1Name_1s_1_setVerboseOff
  (JNIEnv *env, jclass cls) {
  verbose=0;
}

/*
 * Class:     Mangled_1Name_1s_1
 * Method:    _underscore
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_Mangled_1Name_1s_1__1underscore
  (JNIEnv *env, jclass cls) {
  return 0;
}

/*
 * Class:     Mangled_1Name_1s_1
 * Method:    with_underscore
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_Mangled_1Name_1s_1_with_1underscore
  (JNIEnv *env, jclass cls) {
  return 0;
}

/*
 * Class:     Mangled_1Name_1s_1
 * Method:    overload
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_Mangled_1Name_1s_1_overload__
  (JNIEnv *env, jclass cls) {
  return 0;
}

/*
 * Class:     Mangled_1Name_1s_1
 * Method:    overload
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_Mangled_1Name_1s_1_overload__I
  (JNIEnv *env, jclass cls, jint myInt) {
  return 0;
} 

/*
 * Class:     Mangled_1Name_1s_1
 * Method:    overload
 * Signature: (ZI[Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_Mangled_1Name_1s_1_overload__ZI_3Ljava_lang_String_2
  (JNIEnv *env, jclass cls, jboolean myBoolean, jint myInt, jobjectArray myArray) {
  return 0;
}

