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
package org.jikesrvm.compilers.baseline;

import org.jikesrvm.ArchitectureSpecific.VM_BaselineConstants;
import org.jikesrvm.VM;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Scratch space for JSR processing.  Used from VM_ReferenceMaps
 */
@Uninterruptible
final class VM_JSRInfo implements VM_BaselineConstants {

  int numberUnusualMaps;
  VM_UnusualMaps[] unusualMaps;
  byte[] unusualReferenceMaps;
  int freeMapSlot = 0;
  VM_UnusualMaps extraUnusualMap = new VM_UnusualMaps(); //merged jsr ret  and callers maps
  int tempIndex = 0;
  int mergedReferenceMap = 0;       // result of jsrmerged maps - stored in referenceMaps
  int mergedReturnAddressMap = 0;   // result of jsrmerged maps - stored return addresses

  VM_JSRInfo(int initialMaps) {
    unusualMaps = new VM_UnusualMaps[initialMaps];
  }

  /**
   * show the basic information for each of the unusual maps this is for testing
   * use
   */
  public void showUnusualMapInfo(int bytesPerMap) {
    VM.sysWrite("-------------------------------------------------\n");
    VM.sysWriteln("     numberUnusualMaps = ", numberUnusualMaps);

    for (int i = 0; i < numberUnusualMaps; i++) {
      VM.sysWrite("-----------------\n");
      VM.sysWrite("Unusual map #", i);
      VM.sysWrite(":\n");
      unusualMaps[i].showInfo();
      VM.sysWrite("    -- reference Map:   ");
      showAnUnusualMap(unusualMaps[i].getReferenceMapIndex(), bytesPerMap);
      VM.sysWrite("\n");
      VM.sysWrite("    -- non-reference Map:   ");
      showAnUnusualMap(unusualMaps[i].getNonReferenceMapIndex(), bytesPerMap);
      VM.sysWrite("\n");
      VM.sysWrite("    -- returnAddress Map:   ");
      showAnUnusualMap(unusualMaps[i].getReturnAddressMapIndex(), bytesPerMap);
      VM.sysWrite("\n");
    }
    VM.sysWrite("------ extraUnusualMap:   ");
    extraUnusualMap.showInfo();
    showAnUnusualMap(extraUnusualMap.getReferenceMapIndex(), bytesPerMap);
    showAnUnusualMap(extraUnusualMap.getNonReferenceMapIndex(), bytesPerMap);
    showAnUnusualMap(extraUnusualMap.getReturnAddressMapIndex(), bytesPerMap);
    VM.sysWrite("\n");
  }

  /**
   * show the basic information for a single unusualmap this is for testing use
   */
  public void showAnUnusualMap(int mapIndex, int bytesPerMap) {
    VM.sysWrite("unusualMap with index = ", mapIndex);
    VM.sysWrite("   Map bytes =  ");
    for (int i = 0; i < bytesPerMap; i++) {
      VM.sysWrite(unusualReferenceMaps[mapIndex + i]);
      VM.sysWrite("   ");
    }
    VM.sysWrite("   ");
  }

}
