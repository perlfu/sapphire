/*
 * (C) Copyright IBM Corp. 2001, 2004
 *
 * $Id$:
 *
 * @author Ton Ngo
 * @author Steven Augart
 */
/* Test method invocation from native code 
 * Implement native methods from JNI12.java 
 */

#include <stdio.h>
#include <stdlib.h>             /* malloc() */

#include <jni.h>
#include "JNI12.h"

#define TRACE 0
/** This bug must be fixed; we have an outstanding defect report. */
#define RETURNING_GLOBALS_AND_WEAKS_BROKEN 1
int verbose=1;

/*
 * Class:     Allocation
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_JNI12_setVerboseOff (JNIEnv *env, jclass cls) 
{
    verbose=0;
}


jweak heldWeak = NULL;
jobject heldGlobal = NULL;

/*
 * Class:     JNI12
 * Method:    testReflectedMethods
 * Signature: (Ljava/lang/Class;Ljava/lang/reflect/Method;)Ljava/lang/reflect/Method;
 */
jobject JNICALL 
Java_JNI12_testReflectedMethods(JNIEnv *env, jclass mine, jclass cls, jobject oldMethObj) 
{
    if (TRACE)
        fprintf(stderr, "Entering testReflectedMethods\n");
    jmethodID methID = (*env)->FromReflectedMethod(env, oldMethObj);
    if (TRACE || verbose)
        printf("derived methID = %p\n", methID);
    if ( ! methID ) {
        fprintf(stderr, "FromReflectedMethod failed!\n");
        return NULL;
    }
    jobject newMethObj = 
        (*env)->ToReflectedMethod(env, cls, methID, JNI_FALSE);

    if (TRACE)
        fprintf(stderr, "Exiting testReflectedMethods\n");
    return newMethObj;
}


jobject JNICALL 
Java_JNI12_testGlobalCreationAndReturn(JNIEnv *env, jclass mine, jobject methObj) 
{
    if (TRACE)
        fprintf(stderr, "Trying NewGlobalRef\n");
    heldGlobal = (*env)->NewGlobalRef(env, methObj);
    if (TRACE) 
        fprintf(stderr, "methObj = %p ==> heldGlobal = %p \n", methObj, heldGlobal);
    if (RETURNING_GLOBALS_AND_WEAKS_BROKEN)
        return (*env)->NewLocalRef(env, heldGlobal);
    else
        return heldGlobal;
}


jobject JNICALL 
Java_JNI12_testWeakCreationAndReturn(JNIEnv *env, jclass mine, jobject methObj) 
{
    if (TRACE)
        fprintf(stderr, "Trying NewWeakGlobalRef\n");
    heldWeak = (*env)->NewWeakGlobalRef(env, methObj);
    if (TRACE) 
        fprintf(stderr, "methObj = %p ==> heldWeak = %p \n", methObj, heldWeak);
    if (RETURNING_GLOBALS_AND_WEAKS_BROKEN)
        return (*env)->NewLocalRef(env, heldWeak);
    else
        return heldWeak;
}


/** 0 on success, nonzero on failure. */
jint JNICALL 
Java_JNI12_testGlobalPersistenceAndDestruction(JNIEnv *env, jclass mine, jobject passedNewMethObj) 
{
    if (TRACE) 
        fprintf(stderr, "methObj = %p ==> heldGlobal = %p \n", passedNewMethObj, heldGlobal);
    if (! (*env)->IsSameObject(env, heldGlobal, passedNewMethObj)) {
        fprintf(stderr, "IsSameObject failed on retained global ref!\n");
        return -1;
    }
    
    (*env)->DeleteGlobalRef(env, heldGlobal);
    heldGlobal = NULL;
    return 0;                   /* OK */
}

/** 0 on success, nonzero on failure. */
jint JNICALL 
Java_JNI12_testWeakPersistenceAndDestruction(JNIEnv *env, jclass mine, jobject passedNewMethObj) 
{
    if (TRACE) 
        fprintf(stderr, "methObj = %p ==> heldWeak = %p \n", passedNewMethObj, heldWeak);
    if (! (*env)->IsSameObject(env, heldWeak, passedNewMethObj)) {
        fprintf(stderr, "IsSameObject failed on retained weak ref!\n");
        return -1;
    }
    
    (*env)->DeleteWeakGlobalRef(env, heldWeak);
    heldWeak = NULL;
    return 0;                   /* OK */
}



/*
 * Class:     JNI12
 * Method:    testReflectedFields
 * Signature: (Ljava/lang/Class;Ljava/lang/reflect/Field;)Ljava/lang/reflect/Field;
 *  Also tests NewLocalRef.
 */
jobject JNICALL 
Java_JNI12_testReflectedFields(JNIEnv *env, jclass myClass, 
                               jclass cls, jobject oldFldObj)
{
    if (TRACE) {
        fprintf(stderr, "Entering testReflectedFields\n");
        fprintf(stderr, "oldFldObj = %p \n", oldFldObj);
    }
    jfieldID fldID = (*env)->FromReflectedField(env, oldFldObj);
    if (TRACE)
        fprintf(stderr, "ran FromReflectedField\n");
    if (TRACE || verbose)
        printf("derived fldID = %p\n", fldID);
    if ( ! fldID ) {
        fprintf(stderr, "FromReflectedField failed!\n");
        return NULL;
    }
    if (TRACE)
        fprintf(stderr, "calling ToReflectedField\n");
    jobject newFldObj = (*env)->ToReflectedField(env, cls, fldID, JNI_FALSE);
    if (TRACE) {
        fprintf(stderr, "ran ToReflectedField\n");
        fprintf(stderr, "Exiting testReflectedFields\n");
    }
    /** Test NewLocalRef while we're at it. */
    return (*env)->NewLocalRef(env, newFldObj);
}

/* Local Variables: */
/* c-font-lock-extra-types: ("JNIEnv" "jclass" "jweak" "jfieldID" "jobject" "jmethodID") */
/* End: */
