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
package org.mmtk.utility.options;

/**
 * GCspy Tile Size.
 */
public final class GCspyTileSize extends org.vmutil.options.IntOption {
  /**
   * Create the option.
   */
  public GCspyTileSize() {
    super(Options.set, "GCspy Tile Size",
          "GCspy Tile Size",
          131072);
  }

  /**
   * Ensure the tile size is positive
   */
  protected void validate() {
    failIf(this.value <= 0, "Unreasonable gcspy tilesize");
  }
}
