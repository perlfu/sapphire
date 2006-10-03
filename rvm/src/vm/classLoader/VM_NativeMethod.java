/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;

import org.vmmagic.unboxed.*;

/**
 * A native method of a java class.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 * @modified Ian Rogers
 */
public final class VM_NativeMethod extends VM_Method {

  /**
   * the name of the native procedure in the native library
   */
  private String nativeProcedureName;                 

  /**
   * the IP of the native p rocedure
   */
  private Address nativeIP;                               

  //-#if RVM_WITH_POWEROPEN_ABI
  /**
   * the TOC of the native procedure
   */
  private Address nativeTOC;                              
  //-#endif
  
  /**
   * Construct native method information
   *
   * @param declaringClass the VM_Class object of the class that
   *                       declared this method.
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param exceptionTypes exceptions thrown by this method.
   * @param signature generic type of this method.
   * @param runtimeVisibleAnnotations array of runtime visible
   * annotations
   * @param runtimeInvisibleAnnotations optional array of runtime
   * invisible annotations
   * @param runtimeVisibleParameterAnnotations array of runtime
   * visible parameter annotations
   * @param runtimeInvisibleParameterAnnotations optional array of
   * runtime invisible parameter annotations
   * @param annotationDefault value for this annotation that appears
   * in annotation classes
   */
  VM_NativeMethod(VM_TypeReference declaringClass, VM_MemberReference memRef,
                  int modifiers, VM_TypeReference[] exceptionTypes,
                  VM_Atom signature,
                  VM_Annotation runtimeVisibleAnnotations[],
                  VM_Annotation runtimeInvisibleAnnotations[],
                  VM_Annotation runtimeVisibleParameterAnnotations[],
                  VM_Annotation runtimeInvisibleParameterAnnotations[],
                  Object annotationDefault)
  {
    super(declaringClass, memRef, modifiers, exceptionTypes, signature,
          runtimeVisibleAnnotations, runtimeInvisibleAnnotations,
          runtimeVisibleParameterAnnotations, runtimeInvisibleParameterAnnotations,
          annotationDefault);
  }

  /**
   * Generate the code for this method
   */
  protected synchronized VM_CompiledMethod genCode() {
    if (!resolveNativeMethod()) {
      // if fail to resolve native, get code to throw unsatifiedLinkError
      VM_Entrypoints.unimplementedNativeMethodMethod.compile();
      return VM_Entrypoints.unimplementedNativeMethodMethod.getCurrentCompiledMethod();
    } else {
      if (VM.writingBootImage) {
        return VM_BootImageCompiler.compile(this);
      } else {
        return VM_RuntimeCompiler.compile(this);
      }
    }
  }

  /**
   * Get the native IP for this method
   */
  public final Address getNativeIP() { 
    return nativeIP;
  }
  
  /**
   * get the native TOC for this method
   */
  public Address getNativeTOC() { 
    //-#if RVM_WITH_POWEROPEN_ABI
    return nativeTOC;
    //-#else
    return Address.zero();
    //-#endif
  }

  /**
   * replace a character in a string with a string
   */
  private String replaceCharWithString(String originalString, 
                                       char targetChar, 
                                       String replaceString) {
    String returnString;
    int first = originalString.indexOf(targetChar);
    int next  = originalString.indexOf(targetChar, first+1);
    if (first!=-1) {
      returnString = originalString.substring(0,first) + replaceString;
      while (next!=-1) {
        returnString += originalString.substring(first+1, next) + replaceString;
        first = next;
        next = originalString.indexOf(targetChar, next+1);
      }
      returnString += originalString.substring(first+1);
    } else {
      returnString = originalString;
    }
    return returnString;
  }


  /**
   * Compute the mangled name of the native routine: Java_Class_Method_Sig
   */
  private String getMangledName(boolean sig) {
    String mangledClassName, mangledMethodName;
    String className = getDeclaringClass().toString();
    String methodName = getName().toString();
    int first, next;

    // Mangled Class name
    // Special case: underscore in class name
    mangledClassName = replaceCharWithString(className, '_', "_1");

    // Mangled Method name
    // Special case: underscore in method name
    //   class._underscore  -> class__1underscore
    //   class.with_underscore  -> class_with_1underscore
    mangledMethodName = replaceCharWithString(methodName, '_', "_1");

    if (sig) {
      String sigName = getDescriptor().toString();
      sigName = sigName.substring( sigName.indexOf('(')+1, sigName.indexOf(')') );
      sigName = replaceCharWithString(sigName, '[', "_3");
      sigName = replaceCharWithString(sigName, ';', "_2");
      sigName = sigName.replace( '/', '_');
      mangledMethodName += "__" + sigName;
    }


    String mangledName = "Java_" + mangledClassName + "_" + mangledMethodName;
    mangledName = mangledName.replace( '.', '_' );
    // VM.sysWrite("getMangledName:  " + mangledName + " \n");

    return mangledName;
  }

  private boolean resolveNativeMethod() {
    if (!nativeIP.isZero()) {
      // method has already been resolved via registerNative.
      return true;
    }

    nativeProcedureName = getMangledName(false);
    String nativeProcedureNameWithSignature = getMangledName(true);

    Address symbolAddress = VM_DynamicLibrary.resolveSymbol(nativeProcedureNameWithSignature);
    if (symbolAddress.isZero()) {
      symbolAddress = VM_DynamicLibrary.resolveSymbol(nativeProcedureName);
    }

    if (symbolAddress.isZero()) {
      // native procedure not found in library
      return false;
    } else {
      //-#if RVM_WITH_POWEROPEN_ABI
      nativeIP  = symbolAddress.loadAddress();
      nativeTOC = symbolAddress.loadAddress(Offset.fromInt(BYTES_IN_ADDRESS));
      //-#else
      nativeIP = symbolAddress;
      //-#endif
      return true;
    }
  }


  /**
   * Registers a native method
   * @param symbolAddress address of native function that implements the method
   */
  public synchronized void registerNativeSymbol(Address symbolAddress) {
    //-#if RVM_WITH_POWEROPEN_ABI
    nativeIP  = symbolAddress.loadAddress();
    nativeTOC = symbolAddress.loadAddress(Offset.fromInt(BYTES_IN_ADDRESS));
    //-#else
    nativeIP = symbolAddress;
    //-#endif
    replaceCompiledMethod(null);
  }

  /**
   * Unregisters a native method
   */
  public synchronized void unregisterNativeSymbol() {
    //-#if RVM_WITH_POWEROPEN_ABI
    nativeIP  = Address.zero();
    nativeTOC = Address.zero();
    //-#else
    nativeIP  = Address.zero();
    //-#endif
    replaceCompiledMethod(null);
  }
}
