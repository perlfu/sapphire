/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.opt.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.Hashtable;
import java.util.Enumeration;

/**
 * VM_StringEventCounterData.java
 * 
 * A generic data object that maps strings to counters.  The key
 * method is "OPT_Instruction getCounterInstructionForEvent(String)"
 * which, given a string, returns a counter instruction that
 * increments the corresponding counter for that string.
 *
 * @author Matthew Arnold
 *
 */
public class VM_StringEventCounterData extends VM_ManagedCounterData
  implements VM_Reportable 
{

  static final boolean DEBUG=false;


  /**
   *  Constructor
   *
   * @param manager The manager that will provide the counter space
   **/
  VM_StringEventCounterData(OPT_InstrumentedEventCounterManager manager,
			    String name)
  {
    // Call superclass constructor
    super(manager);
    
    dataName = name;
  }

  /**
   * Given a string, find or create the counter associated and return
   * and instruction to increment that counter.  
   *
   * @param event The name of the event
   * @return An instruction to increment the count associated with the event.
   */
  public OPT_Instruction getCounterInstructionForEvent(String event) {
    return getCounterInstructionForEvent(event,1.0);
  }

  /**
   * Given a string, find or create the counter associated and return
   * and instruction to increment that counter.  
   *
   * @param event The name of the event
   * @param incrementValue The value to add to counter
   * @return An instruction that will update the count associated with the event.
   *
   */
  public OPT_Instruction getCounterInstructionForEvent(String event, 
						double incrementValue) {

    // Get (or create) the counter for this string and return it.
    int counterIdx = getOrCreateCounterIndexForString(event);

    return createEventCounterInstruction(counterIdx,incrementValue);
  }

  /**
   * Convert a double to string with maximum precision.
   * @param num double to convert
   */
  protected static String doubleToString(double num) {
    long whole = (long)num;
    if (whole == Long.MAX_VALUE || whole == Long.MIN_VALUE)
      return Double.toString(whole);
    double fract = Math.abs(num - (double)whole);
    String res = Long.toString(whole);
    if (fract != 0.0) {
      String f2s = Double.toString(fract + 1.0);
      res += f2s.substring(1);
    }
    return res;
  }

  /**
   * Part of VM_Reportable interface
   * Print a report at the end of execution
   */
  public void report()
  {
    // Turn off future instrumentation to avoid hanging during 
    // iteration
    VM_Instrumentation.disableInstrumentation();

    VM.sysWrite("Printing " + dataName + ":\n");
    VM.sysWrite("--------------------------------------------------\n");
    double total=0;
    for (Enumeration e = stringToCounterMap.keys();
	 e.hasMoreElements();) {
      String stringName = (String) e.nextElement();

      int counterIdx = getCounterIndexForString(stringName);
      double counterVal = getCounter(counterIdx);
      VM.sysWrite(doubleToString(counterVal) + " " + stringName + "\n");
      total += counterVal;
    }
    VM.sysWrite("Total: " + doubleToString(total) + "\n");
  }

  /**
   * For a given string, return the number of the counter associated
   * with this string.  If this string doesn't already have a counter, 
   * reserve one. 
   *
   * @param str The string for which you want the counter number
   * @return The counter number for this string

   */
  public int getOrCreateCounterIndexForString(String str) {

    int counterIdx = getCounterIndexForString(str);
    if (counterIdx == -1) {
      // Use new counter
      counterIdx = ++ eventNumber;
      // remember it, and return it
      stringToCounterMap.put(str,new Integer(eventNumber));
    }

    return counterIdx;
  }


  /**
   * For a given string, return the number of the counter associated
   * with this string.  Ideally this number would be completely hidden
   * from the outside world, but for efficiency it is made public.
   *
   * @param str The string for which you want the counter number
   * @return The counter number for this string, or -1 if the string has no 
             counter associated with it. 
   */
  public int getCounterIndexForString(String str) {

    int counter = -1;
    Integer counterNum = (Integer) stringToCounterMap.get(str);
    if (counterNum != null) 
      counter = counterNum.intValue();

    return counter;
  }

  /**
   *  Part of VM_Reportable interface
   **/
  public void reset() { 
    for (Enumeration e = stringToCounterMap.keys();
	 e.hasMoreElements();) {
      String stringName = (String) e.nextElement();
      int counterIdx = getCounterIndexForString(stringName);
      setCounter(counterIdx, 0.0);
    }
  }

 /** 
  *  Map strings to a counter location
  */
  protected  Hashtable stringToCounterMap = new Hashtable();

  /**
   * A string description of this data;
   */
  String dataName= "";

  /** 
   * Used to keep track of how many counters have been used so far.
   */ 
  int eventNumber=-1;

} // end of class


