/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm;

/**
 * Profile data for a branch instruction.
 * 
 * @author Dave Grove
 */
public final class VM_SwitchBranchProfile extends VM_BranchProfile {

  /**
   * The number of times that the different arms of a switch were
   * taken. By convention, the default case is the last entry.
   */
  final float[] counts;
  
  /**
   * @param _bci the bytecode index of the source branch instruction
   * @param cs counts
   * @param start idx of first entry in cs
   * @param numEntries number of entries in cs for this switch
   */
  VM_SwitchBranchProfile(int _bci, int[] cs, int start, int numEntries) {
    super(_bci, sumCounts(cs, start, numEntries));
    counts = new float[numEntries];
    for (int i=0; i<numEntries; i++) {
      counts[i] = (float)cs[start+i];
    }
  }

  public float getDefaultProbability() {
    return getProbability(counts.length-1);
  }

  public float getCaseProbability(int n) {
    return getProbability(n);
  }

  float getProbability(int n) {
    if (freq > 0) {
      return counts[n] / freq;
    } else {
      return 1.0f / counts.length;
    }
  }

  public String toString() {
    String res = bci + "\tswitch     < " + (int)counts[0];
    for (int i=1; i<counts.length; i++) {
      res += ", "+(int)counts[i];
    }
    return res + " >";
  }

  private static float sumCounts(int[] counts, int start, int numEntries) {
    float sum = 0.0f;
    for (int i=start; i<start+numEntries; i++) {
      sum += (float)counts[i];
    }
    return sum;
  }
}
