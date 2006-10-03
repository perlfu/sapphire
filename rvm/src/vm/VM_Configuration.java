/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2005
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;

/**
 * Flags that specify the configuration of our virtual machine.
 *
 * Note: Changing any <code>final</code> flags requires that the whole vm
 *       be recompiled and rebuilt after their values are changed.
 *
 * @author Bowen Alpern
 * @author Stephen Fink
 * @author David Grove
 */
public abstract class VM_Configuration {

  public static final boolean BuildForPowerPC =
        //-#if RVM_FOR_POWERPC
          true;
        //-#else
          false;
        //-#endif

  public static final boolean BuildForIA32 =
        //-#if RVM_FOR_IA32
          true;
        //-#else
          false;
        //-#endif

  public static final boolean LittleEndian = BuildForIA32;
  
  public static final boolean BuildFor32Addr = 
        //-#if RVM_FOR_32_ADDR
          true;
        //-#else
          false;
        //-#endif

  public static final boolean BuildFor64Addr = 
        //-#if RVM_FOR_64_ADDR
          true;
        //-#else
          false;
        //-#endif

  public static final boolean BuildForAix =
        //-#if RVM_FOR_AIX
        true;
        //-#else
        false;
        //-#endif

  public static final boolean BuildForLinux =
        //-#if RVM_FOR_LINUX
          true;
        //-#else
          false;
        //-#endif

  public static final boolean BuildForOsx =
        //-#if RVM_FOR_OSX
          true;
        //-#else
          false;
        //-#endif

  public static final boolean BuildForPowerOpenABI =
        //-#if RVM_WITH_POWEROPEN_ABI
          true;
        //-#else
          false;
        //-#endif
  
  public static final boolean BuildForSVR4ABI =
        //-#if RVM_WITH_SVR4_ABI
          true;
        //-#else
          false;
        //-#endif
  
  /** Used for OS/X (Darwin) */
  public static final boolean BuildForMachOABI =
        //-#if RVM_WITH_MACH_O_ABI
          true;
        //-#else
          false;
        //-#endif

  /**
   * Can a dereference of a null pointer result in an access
   * to 'low' memory addresses that must be explicitly guarded because the
   * target OS doesn't allow us to read protect low memory?
   */
  public static final boolean ExplicitlyGuardLowMemory =
      //-#if RVM_WITH_EXPLICITLY_GUARDED_LOW_MEMORY
      true;
      //-#else
      false;
      //-#endif
  
 /** Assertion checking.
      <dl>
      <dt>false</dt>  <dd> no assertion checking at runtime</dd>
      <dt>true  </dt> <dd> execute assertion checks at runtime</dd>
      <dl>

      Note: code your assertion checks as 
      <pre>
        if (VM.VerifyAssertions) 
          VM._assert(xxx);
      </pre> 
  */
  public static final boolean VerifyAssertions = 
        //-#if RVM_WITHOUT_ASSERTIONS
          false;
        //-#else
          true;
        //-#endif

  public static final boolean ExtremeAssertions = 
        //-#if RVM_WITH_EXTREME_ASSERTIONS
          true;
        //-#else
          false;
        //-#endif

  /**  
   * If set, verify that Uninterruptible methods actually cannot be
   * interrupted.
   */ 
  public static final boolean VerifyUnint = true && VerifyAssertions;

  // If set, ignore the supression pragma and print all warning messages.
  public static final boolean ParanoidVerifyUnint = false;


  /** Does this build include support for Hardware Performance Monitors? */
  public static final boolean BuildForHPM = 
    //-#if RVM_WITH_HPM
      true;
    //-#else 
      false;
    //-#endif

  // Is this an adaptive build?
  public static final boolean BuildForAdaptiveSystem =
      //-#if RVM_WITH_ADAPTIVE_SYSTEM
        true;
      //-#else
        false;
      //-#endif

  // Interface method invocation.
  // We have five mechanisms:
  //   IMT-based (Alpern, Cocchi, Fink, Grove, and Lieber). 
  //    - embedded directly in the TIB
  //    - indirectly accessed off the TIB
  //   ITable-based
  //    - directly indexed (by interface id) iTables. 
  //    - searched (at dispatch time); 
  //   Naive, class object is searched for matching method on every dispatch.
  public static final boolean BuildForIMTInterfaceInvocation = true;
  public static final boolean BuildForIndirectIMT = true && 
                                              BuildForIMTInterfaceInvocation;
  public static final boolean BuildForEmbeddedIMT = !BuildForIndirectIMT && 
                                              BuildForIMTInterfaceInvocation;
  public static final boolean BuildForITableInterfaceInvocation = true && 
                                              !BuildForIMTInterfaceInvocation;
  public static final boolean DirectlyIndexedITables = false;

  /** Epilogue yieldpoints increase sampling accuracy for adaptive
      recompilation.  In particular, they are key for large, leaf, loop-free
      methods.  */
  public static final boolean UseEpilogueYieldPoints =
      //-#if RVM_WITH_ADAPTIVE_SYSTEM
        true;
      //-#else
        false;
      //-#endif

  /** Adaptive compilation. */
  public static final boolean LogAOSEvents =
      //-#if RVM_WITHOUT_AOS_LOG 
        false;
      //-#else
        true;
      //-#endif

  /** Do we use nonblocking I/O for files? */
  public static final boolean NonBlockingFDs = 
    //-#if RVM_WITH_NON_BLOCKING_FDS_FOR_CLASSPATH
    true;
    //-#else
    false;
    //-#endif

  /** The following configuration objects are final when disabled, but
      non-final when enabled. */
  
  //-#if RVM_FOR_STRESSGC
  public static boolean ParanoidGCCheck       = true;
  public static boolean ForceFrequentGC       = true;
  //-#else
  public final static boolean ParanoidGCCheck = false;
  public final static boolean ForceFrequentGC = false;
  //-#endif

  public final static boolean CompileForGCTracing = 
    MM_Interface.GENERATE_GC_TRACE;

  //-#if RVM_FOR_IA32
  /**
   * Is ESI dedicated to always hold the processor register?
   */
  public final static boolean dedicatedESI = true;
  //-#endif

  /** Do we have the facilities to intercept blocking system calls? */
  public final static boolean withoutInterceptBlockingSystemCalls =
    //-#if RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
    true;
    //-#else
    false;
    //-#endif


}
