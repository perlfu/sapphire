/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 *
 * GCspy components
 * (C) Copyright Richard Jones, 2005-6
 * Computing Laboratory, University of Kent at Canterbury
 */
package com.ibm.JikesRVM.mm.mmtk;

import org.mmtk.utility.gcspy.Color;
import org.mmtk.utility.gcspy.drivers.AbstractDriver;
import com.ibm.JikesRVM.mm.mmtk.gcspy.*;

import com.ibm.JikesRVM.VM;

/**
 * This is a VM-specific class which defines factory methods for
 * VM-specific types which must be instantiated within MMTk.
 * 
 * @see org.mmtk.vm.Factory
 * 
 * $Id: Factory.java,v 1.7 2006/06/21 07:38:13 steveb-oss Exp $
 * 
 * @author Steve Blackburn
 * @version $Revision: 1.7 $
 * @date $Date: 2006/06/21 07:38:13 $
 */
public final class Factory extends org.mmtk.vm.Factory {

  /**
   * Create a new ActivePlan instance using the appropriate VM-specific
   * concrete ActivePlan sub-class.
   * 
   * @see ActivePlan
   * @return A concrete VM-specific ActivePlan instance.
   */
  public org.mmtk.vm.ActivePlan newActivePlan() {
    try {
      return new ActivePlan();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ActivePlan!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new Assert instance using the appropriate VM-specific
   * concrete Assert sub-class.
   * 
   * @see Assert
   * @return A concrete VM-specific Assert instance.
   */
  public org.mmtk.vm.Assert newAssert() {
    try {
      return new Assert();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Assert!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new Barriers instance using the appropriate VM-specific
   * concrete Barriers sub-class.
   * 
   * @see Barriers
   * @return A concrete VM-specific Barriers instance.
   */
  public org.mmtk.vm.Barriers newBarriers() {
    try {
      return new Barriers();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Barriers!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new Collection instance using the appropriate VM-specific
   * concrete Collection sub-class.
   * 
   * @see Collection
   * @return A concrete VM-specific Collection instance.
   */
  public org.mmtk.vm.Collection newCollection() {
    try {
      return new Collection();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Collection!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new Lock instance using the appropriate VM-specific
   * concrete Lock sub-class.
   * 
   * @see Lock
   * 
   * @param name The string to be associated with this lock instance
   * @return A concrete VM-specific Lock instance.
   */
  public org.mmtk.vm.Lock newLock(String name) {
    try {
      return new Lock(name);
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Lock!");
      return null; // never get here
    }
  }

  /**
   * Create a new Memory instance using the appropriate VM-specific
   * concrete Memory sub-class.
   * 
   * @see Memory
   * @return A concrete VM-specific Memory instance.
   */
  public org.mmtk.vm.Memory newMemory() {
    try {
      return new Memory();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Memory!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new ObjectModel instance using the appropriate VM-specific
   * concrete ObjectModel sub-class.
   * 
   * @see ObjectModel
   * @return A concrete VM-specific ObjectModel instance.
   */
  public org.mmtk.vm.ObjectModel newObjectModel() {
    try {
      return new ObjectModel();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ObjectModel!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new Options instance using the appropriate VM-specific
   * concrete Options sub-class.
   * 
   * @see Options
   * @return A concrete VM-specific Options instance.
   */
  public org.mmtk.vm.Options newOptions() {
    try {
      return new Options();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Options!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new ReferenceGlue instance using the appropriate VM-specific
   * concrete ReferenceGlue sub-class.
   * 
   * @see ReferenceGlue
   * @return A concrete VM-specific ReferenceGlue instance.
   */
  public org.mmtk.vm.ReferenceGlue newReferenceGlue() {
    try {
      return new ReferenceGlue();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ReferenceGlue!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new Scanning instance using the appropriate VM-specific
   * concrete Scanning sub-class.
   * 
   * @see Scanning
   * @return A concrete VM-specific Scanning instance.
   */
  public org.mmtk.vm.Scanning newScanning() {
    try {
      return new Scanning();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Scanning!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new Statistics instance using the appropriate VM-specific
   * concrete Statistics sub-class.
   * 
   * @see Statistics
   * @return A concrete VM-specific Statistics instance.
   */
  public org.mmtk.vm.Statistics newStatistics() {
    try {
      return new Statistics();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Statistics!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new Strings instance using the appropriate VM-specific
   * concrete Strings sub-class.
   * 
   * @see Strings
   * @return A concrete VM-specific Strings instance.
   */
  public org.mmtk.vm.Strings newStrings() {
    try {
      return new Strings();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Strings!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new SynchronizedCounter instance using the appropriate
   * VM-specific concrete SynchronizedCounter sub-class.
   * 
   * @see SynchronizedCounter
   * 
   * @return A concrete VM-specific SynchronizedCounter instance.
   */
  public org.mmtk.vm.SynchronizedCounter newSynchronizedCounter() {
    try {
      return new SynchronizedCounter();
    } catch (Exception e) {
     VM.sysFail("Failed to allocate new SynchronizedCounter!");
      return null; // never get here
    }
  }

  /**
   * Create a new TraceInterface instance using the appropriate VM-specific
   * concrete TraceInterface sub-class.
   * 
   * @see TraceInterface
   * @return A concrete VM-specific TraceInterface instance.
   */
  public org.mmtk.vm.TraceInterface newTraceInterface() {
    try {
      return new TraceInterface();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new TraceInterface!");
      return null; // never get here
    }
  }
  
  /**********************************************************************
   * GCspy methods
   */

  /**
   * Create a new Util instance using the appropriate VM-specific
   * concrete Util sub-class.
   * 
   * @see Util
   * @return A concrete VM-specific Util instance.
   */
  public org.mmtk.vm.gcspy.Util newGCspyUtil() {
    try {
      return new com.ibm.JikesRVM.mm.mmtk.gcspy.Util();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new Util!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new ServerInterpreter instance using the appropriate VM-specific
   * concrete ServerInterpreter sub-class.
   * 
   * @see ServerInterpreter
   * @return A concrete VM-specific ServerInterpreter instance.
   */
  public org.mmtk.vm.gcspy.ServerInterpreter newGCspyServerInterpreter() {
    try {
      return new com.ibm.JikesRVM.mm.mmtk.gcspy.ServerInterpreter();
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ServerInterpreter!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new ServerSpace instance using the appropriate VM-specific
   * concrete ServerSpace sub-class.
   * 
   * @param serverInterpreter The server that owns this space
   * @param serverName The server's name
   * @param driverName The space driver's name
   * @param title Title for the space
   * @param blockInfo A label for each block
   * @param tileNum Max number of tiles in this space
   * @param unused A label for unused blocks
   * @param mainSpace Whether this space is the main space
   * 
   * @see ServerSpace
   * @return A concrete VM-specific ServerSpace instance.
   */
  public org.mmtk.vm.gcspy.ServerSpace newGCspyServerSpace(
      org.mmtk.vm.gcspy.ServerInterpreter serverInterpreter,
      String serverName, 
      String driverName,
      String title,
      String blockInfo,
      int tileNum,
      String unused, 
      boolean mainSpace){
    try {
      return new com.ibm.JikesRVM.mm.mmtk.gcspy.ServerSpace(
          serverInterpreter, serverName, driverName, title, 
          blockInfo, tileNum, unused, mainSpace);
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ServerSpace!");
      return null; // never get here
    }
  }
  
  /**
   * Create a new ByteStream instance using the appropriate
   * VM-specific concrete ByteStream sub-class.
   * 
   * @param driver        The driver that owns this Stream
   * @param name           The name of the stream (e.g. "Used space")
   * @param minValue       The minimum value for any item in this stream. 
   *                       Values less than this will be represented as "minValue-"
   * @param maxValue       The maximum value for any item in this stream. 
   *                       Values greater than this will be represented as "maxValue+"
   * @param zeroValue      The zero value for this stream
   * @param defaultValue   The default value for this stream
   * @param stringPre      A string to prefix values (e.g. "Used: ")
   * @param stringPost     A string to suffix values (e.g. " bytes.")
   * @param presentation   How a stream value is to be presented.    
   * @param paintStyle     How the value is to be painted.   
   * @param indexMaxStream The index of the maximum stream if the presentation is *_VAR.
   * @param colour         The default colour for tiles of this stream
   * @see IntStream
   * 
   * @return A concrete VM-specific ByteStream instance. 
   */
  public org.mmtk.vm.gcspy.ByteStream newGCspyByteStream(
      AbstractDriver driver,
      String name,
      byte minValue,         
      byte maxValue,
      byte zeroValue,
      byte defaultValue,
      String stringPre,
      String stringPost,
      int presentation,
      int paintStyle,
      int indexMaxStream,
      Color colour,
      boolean summary) {
    try {
      return new com.ibm.JikesRVM.mm.mmtk.gcspy.ByteStream(
          driver, name, minValue,  maxValue,
          zeroValue, defaultValue, stringPre, stringPost,
          presentation, paintStyle, indexMaxStream,
          colour, summary);
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ByteStream!");
      return null; // never get here
    }
  } 
  /**
   * Create a new IntStream instance using the appropriate
   * VM-specific concrete IntStream sub-class.
   * 
   * @param driver        The driver that owns this Stream
   * @param name           The name of the stream (e.g. "Used space")
   * @param minValue       The minimum value for any item in this stream. 
   *                       Values less than this will be represented as "minValue-"
   * @param maxValue       The maximum value for any item in this stream. 
   *                       Values greater than this will be represented as "maxValue+"
   * @param zeroValue      The zero value for this stream
   * @param defaultValue   The default value for this stream
   * @param stringPre      A string to prefix values (e.g. "Used: ")
   * @param stringPost     A string to suffix values (e.g. " bytes.")
   * @param presentation   How a stream value is to be presented.    
   * @param paintStyle     How the value is to be painted.   
   * @param indexMaxStream The index of the maximum stream if the presentation is *_VAR.
   * @param colour         The default colour for tiles of this stream
   * @see IntStream
   * 
   * @return A concrete VM-specific IntStream instance. 
   */
  public org.mmtk.vm.gcspy.IntStream newGCspyIntStream(
      AbstractDriver driver,
      String name,
      int minValue,         
      int maxValue,
      int zeroValue,
      int defaultValue,
      String stringPre,
      String stringPost,
      int presentation,
      int paintStyle,
      int indexMaxStream,
      Color colour,
      boolean summary) {
    try {
      return new com.ibm.JikesRVM.mm.mmtk.gcspy.IntStream(
          driver, name, minValue,  maxValue,
          zeroValue, defaultValue, stringPre, stringPost,
          presentation, paintStyle, indexMaxStream,
          colour, summary);
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new IntStream!");
      return null; // never get here
    }
  }

  /**
   * Create a new ShortStream instance using the appropriate
   * VM-specific concrete ShortStream sub-class.
   * 
   * @param driver        The driver that owns this Stream
   * @param name           The name of the stream (e.g. "Used space")
   * @param minValue       The minimum value for any item in this stream. 
   *                       Values less than this will be represented as "minValue-"
   * @param maxValue       The maximum value for any item in this stream. 
   *                       Values greater than this will be represented as "maxValue+"
   * @param zeroValue      The zero value for this stream
   * @param defaultValue   The default value for this stream
   * @param stringPre      A string to prefix values (e.g. "Used: ")
   * @param stringPost     A string to suffix values (e.g. " bytes.")
   * @param presentation   How a stream value is to be presented.    
   * @param paintStyle     How the value is to be painted.   
   * @param indexMaxStream The index of the maximum stream if the presentation is *_VAR.
   * @param colour         The default colour for tiles of this stream
   * @see IntStream
   * 
   * @return A concrete VM-specific ShortStream instance. 
   */
  public org.mmtk.vm.gcspy.ShortStream newGCspyShortStream(
      AbstractDriver driver,
      String name,
      short minValue,         
      short maxValue,
      short zeroValue,
      short defaultValue,
      String stringPre,
      String stringPost,
      int presentation,
      int paintStyle,
      int indexMaxStream,
      Color colour,
      boolean summary) {
    try {
      return new com.ibm.JikesRVM.mm.mmtk.gcspy.ShortStream(
          driver, name, minValue,  maxValue,
          zeroValue, defaultValue, stringPre, stringPost,
          presentation, paintStyle, indexMaxStream,
          colour, summary);
    } catch (Exception e) {
      VM.sysFail("Failed to allocate new ShortStream!");
      return null; // never get here
    }
  }
}
