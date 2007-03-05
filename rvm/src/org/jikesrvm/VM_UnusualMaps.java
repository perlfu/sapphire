/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm;

import org.vmmagic.pragma.*;

/**
 * Unusual maps are maps to track references that don't take the usual format.
 * Currently unusual maps include:
 *    maps of locations within JSR subroutines (includes return address map)
 * In the future the return address maps may be expanded to include other
 * internal pointers or internal/external pointers may be handled separately.
 *
 * @author Anthony Cocchi
 */
@Uninterruptible final class VM_UnusualMaps {

  // set the index in the stack frame of the return address for this map
  //
  void setReturnAddressIndex(int index) {
    returnAddressIndex = index;
  }

  // provide the index in the stack frame of the return address for this map
  //
  int getReturnAddressIndex() {
    return returnAddressIndex;
  }

  // set the  offset of the reference map in the stackmap list of maps
  //
  void setReferenceMapIndex(int index) {
    referenceMapIndex = index;
  }

  // provide the index in the stackmaps for the refernce map
  //
  int getReferenceMapIndex() {
    return referenceMapIndex;
  }

  // set the  offset of the non-reference map in the stackmap list of maps
  //
  void setNonReferenceMapIndex(int index) {
    nonReferenceMapIndex = index;
  }

  // provide the index in the stackmaps for the non-refernce map
  //
  int getNonReferenceMapIndex() {
    return nonReferenceMapIndex;
  }


  // set the  offset of the returnAddress map in the stackmap list of maps
  //
  void setReturnAddressMapIndex(int index) {
    returnAddressMapIndex = index;
  }

  // provide the index in the stackmaps for the return Address  map
  //
  int getReturnAddressMapIndex() {
    return returnAddressMapIndex;
  }

  // provide the normal map index ie the backpointer
  //
  int getNormalMapIndex() {
    return normalMapIndex;
  }

  // set the normal map index ie the backpointer
  //
  void setNormalMapIndex(int index) {
    normalMapIndex = index;
  }


  // For maps of JSR subroutine locations
  int returnAddressIndex;   // index into the normal reference map of where the
  // return address can be located
  int referenceMapIndex;     // index into the map table of the references set map
  int nonReferenceMapIndex;  // index into the map table of the non-reference set map
  int returnAddressMapIndex; // index into the map table of the return address map
  int normalMapIndex;        // index into the array of normal maps ie the backpointer

  public void showInfo() {
    VM.sysWrite("  UnusualMap showInfo- ");

    VM.sysWrite("    return address index = ");
    VM.sysWrite(returnAddressIndex);
    VM.sysWrite("\n    referenceMapIndex = ");
    VM.sysWrite(referenceMapIndex);
    VM.sysWrite("\n    nonReferenceMapIndex = ");
    VM.sysWrite(nonReferenceMapIndex);
    VM.sysWrite("\n    returnAddressMapIndex = ");
    VM.sysWrite(returnAddressMapIndex);
    VM.sysWrite("\n");
  }

}
