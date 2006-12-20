/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Richard Jones, 2005-6
 * Computing Laboratory, University of Kent at Canterbury
 */
package com.ibm.jikesrvm.mm.mmtk.gcspy;

import org.mmtk.utility.gcspy.Color;
import org.mmtk.utility.gcspy.GCspy;
import org.mmtk.utility.gcspy.StreamConstants;
import org.mmtk.utility.gcspy.drivers.AbstractDriver;

import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.VM_SysCall;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Address;

/**
 * Set up a GCspy Stream with data type BYTE_TYPE.
 *
 * $Id$
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */

@Uninterruptible public class ByteStream extends org.mmtk.vm.gcspy.ByteStream {

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Construct a new GCspy stream of BYTE_TYPE
   * @param driver         The driver that owns this Stream
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
   */
  public ByteStream(
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
    super(driver, name, 
          minValue, maxValue, zeroValue, defaultValue,
          stringPre, stringPost, presentation, paintStyle, 
          indexMaxStream, colour, summary);
    
    if (VM.BuildWithGCSpy) {
      // We never delete these
      Address tmpName = GCspy.util.getBytes(name);
      Address tmpPre = GCspy.util.getBytes(stringPre);
      Address tmpPost = GCspy.util.getBytes(stringPost);
  
      VM_SysCall.gcspyStreamInit(stream, streamId, StreamConstants.BYTE_TYPE,
          tmpName, minValue, maxValue, zeroValue, defaultValue, 
          tmpPre, tmpPost, presentation, paintStyle, indexMaxStream, 
          colour.getRed(), colour.getGreen(), colour.getBlue());
    }
  }

}

