/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * VM_AOSDatabase.java
 * 
 * Used to keep track of the various data structures that make up the
 * AOS database.  
 *
 * @author Matthew Arnold 
 */
public final class VM_AOSDatabase 
{
  /** 
   * Static links to data objects that are "whole-program" (as opposed
   * to per-method)
    */
  static VM_MethodInvocationCounterData methodInvocationCounterData;
  static VM_YieldpointCounterData yieldpointCounterData;
  static VM_StringEventCounterData instructionCounterData;
  static VM_StringEventCounterData debuggingCounterData;
 
  /**
   * Called at startup
   **/
  static void boot(VM_AOSOptions options)
  {
  }
}
