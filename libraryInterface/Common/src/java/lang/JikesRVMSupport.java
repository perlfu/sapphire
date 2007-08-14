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
package java.lang;

import java.security.ProtectionDomain;
import java.lang.instrument.Instrumentation;

import org.jikesrvm.classloader.VM_Type;

import org.vmmagic.pragma.*;

import org.jikesrvm.VM;              // for VerifyAssertions and _assert()
import org.jikesrvm.scheduler.VM_Thread;

/**
 * Library support interface of Jikes RVM
 */
public class JikesRVMSupport {

  public static void initializeInstrumentation(Instrumentation instrumenter) {
    VMClassLoader.setInstrumenter(instrumenter);
  }

  public static Class<?>[] getAllLoadedClasses() {
    return VMClassLoader.getAllLoadedClasses();
  }

  public static Class<?>[] getInitiatedClasses(ClassLoader classLoader) {
    return VMClassLoader.getInitiatedClasses(classLoader);
  }

  public static Class<?> createClass(VM_Type type) {
    return Class.create(type);
  }

  public static Class<?> createClass(VM_Type type, ProtectionDomain pd) {
    Class<?> c = Class.create(type);
    setClassProtectionDomain(c, pd);
    return c;
  }

  public static VM_Type getTypeForClass(Class<?> c) {
    return c.type;
  }

  public static void setClassProtectionDomain(Class<?> c, ProtectionDomain pd) {
    c.pd = pd;
  }

  /***
   * String stuff
   * */

  @Uninterruptible
  public static char[] getBackingCharArray(String str) {
    return str.value;
  }

  @Uninterruptible
  public static int getStringLength(String str) {
    return str.count;
  }

  @Uninterruptible
  public static int getStringOffset(String str) {
    return str.offset;
  }

  /***
   * Thread stuff
   * */
  public static Thread createThread(VM_Thread vmdata, String myName) {
    if (VM.VerifyAssertions) VM._assert(VM.runningVM);
    Thread bootThread = new Thread(new VMThread(vmdata), myName,
        vmdata.getPriority(), vmdata.isDaemonThread());
    bootThread.group = ThreadGroup.root;
    return bootThread;
  }

  public static VM_Thread getThread(Thread thread) {
    if (thread == null) {
      return null;
    } else if(thread.vmThread == null) {
      return null;
    } else {
      return thread.vmThread.vmdata;
    }
  }

  public static void threadDied(Thread thread) {
    thread.die();
  }
  public static Throwable getStillBorn(Thread thread) {
    return thread.stillborn;
  }
  public static void setStillBorn(Thread thread, Throwable stillborn) {
    thread.stillborn = stillborn;
  }
  /***
   * Enum stuff
   */
  @Uninterruptible
  public static int getEnumOrdinal(Enum<?> e) {
    return e.ordinal;
  }
  @Uninterruptible
  public static String getEnumName(Enum<?> e) {
    return e.name;
  }
}
