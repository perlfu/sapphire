/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.librarySupport;
import VM_StackTrace;
import java.io.*;

/**
 * This class provides a handle to a VM-specific object representing
 * a stack trace.  This is used in the implemented of java.lang.Throwable,
 * which holds a reference to such an object.
 *
 * @author Stephen Fink
 */
public class StackTrace {
  /**
   * Internal representation of the stack strace
   */
  private VM_StackTrace[] stackTrace;

  /**
   * Private constructor
   */
  private StackTrace(VM_StackTrace[] s) {
    stackTrace = s;
  }

  /** 
   * Create a trace (walkback) of our own call stack and store it away in
   * an object.
   * @return a reference to the object
   */
  public static StackTrace create() {
    VM_StackTrace[] s = VM_StackTrace.create();
    return new StackTrace(s);
  }

  /** 
   * Print a stack trace to a stream.
   *
   * @param s the stacktrace to print
   * @param err the stream
   */
  public static void print(StackTrace s, PrintStream err) {
    VM_StackTrace.print(s.stackTrace,err);
  }

  /** 
   * Print a stack trace to a PrintWriter.
   *
   * @param s the stacktrace to print
   * @param err the stream
   */
  public static void print(StackTrace s, PrintWriter err) {
    VM_StackTrace.print(s.stackTrace,err);
  }
}
