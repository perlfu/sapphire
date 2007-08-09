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
package org.mmtk.vm.gcspy;

import org.mmtk.utility.Log;
import org.mmtk.utility.gcspy.Color;
import org.mmtk.utility.gcspy.GCspy;
import org.mmtk.utility.gcspy.StreamConstants;
import org.mmtk.utility.gcspy.drivers.AbstractDriver;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.*;

/**
 * Set up a GCspy Stream with data type SHORT_TYPE.
 */
@Uninterruptible public abstract class ShortStream extends Stream {

  /****************************************************************************
   *
   * Instance variables
   */
  private short[] data;         // The stream data
  private short defaultValue;   // The default value for the data items

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Construct a new GCspy stream of SHORT_TYPE
   * @param driver          The driver that owns this Stream
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
  public ShortStream(
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

    super(driver, StreamConstants.SHORT_TYPE, name,
          minValue, maxValue, zeroValue, defaultValue,
          stringPre, stringPost, presentation, paintStyle,
          indexMaxStream, colour, summary);

    data = (short[])GCspy.util.createDataArray(new short[0], driver.getMaxTileNum());
    this.defaultValue = defaultValue;
  }

  /**
   * Reset all data in this stream to default values.
   */
  public void resetData() {
    for (int i = 0; i < data.length; i++)
      data[i] = defaultValue;
  }


  /**
   * Distribute a value across a sequence of tiles. This handles the case
   * when when an object spans two or more tiles and its value is to be
   * attributed to each tile proportionally.
   *
   * @param start the index of the starting tile
   * @param remainder the value left in the starting tile
   * @param blockSize the size of each tile
   * @param value the value to distribute
   */
  public void distribute(int start, short remainder, int blockSize, short value) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(remainder <= blockSize);
    if (value <= remainder) {  // fits in this tile
      data[start] += value;
      //checkspace(start, value, "scanObject fits in first tile");
    } else {
      data[start] += remainder;
      //checkspace(start, remainder, "scanObject remainder put in first tile");
      value -= remainder;
      start++;
      while (value >= blockSize) {
        data[start] += blockSize;
        //checkspace(start, blockSize, "scanObject subsequent tile");
        value -= blockSize;
        start++;
      }
      data[start] += value;
      //checkspace(start, value, "scanObject last tile");
    }
  }

  /**
   * Increment the value of a tile.
   * @param index the index
   * @param value the increment
   */
  public void increment(int index, short value) { data[index] += value; }

  /**
   * Send the data and summary for this stream.
   * @param event The event
   * @param numTiles The number of tiles to send (which may be less than maxTileNum)
   */
  public void send(int event, int numTiles) {
    if (DEBUG) {
      Log.write("sending "); Log.write(numTiles); Log.writeln(" int values");
    }
    serverSpace.stream(streamId, numTiles);
    for (int index = 0; index < numTiles; index++)
      serverSpace.streamShortValue(data[index]);
    serverSpace.streamEnd();

    // send the summary
    sendSummary();
  }
}

