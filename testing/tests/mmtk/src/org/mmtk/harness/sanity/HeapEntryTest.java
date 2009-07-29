/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.harness.sanity;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mmtk.harness.Harness;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.harness.ArchitecturalWord;

public class HeapEntryTest {

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    ArchitecturalWord.init(Harness.bits.getValue());
  }

  @Test
  public void testHashCode() {
    assertEquals(961,new HeapEntry(ObjectReference.nullReference(),0).hashCode());
    assertEquals(992,new HeapEntry(ObjectReference.nullReference(),1).hashCode());
    assertEquals(963,new HeapEntry(Address.fromIntSignExtend(8).toObjectReference(),0).hashCode());
    assertEquals(994,new HeapEntry(Address.fromIntSignExtend(8).toObjectReference(),1).hashCode());
  }

  @Test
  public void testIsRootReachable() {
    fail("Not yet implemented");
  }

  @Test
  public void testGetRefCount() {
    fail("Not yet implemented");
  }

  @Test
  public void testIncRefCount() {
    fail("Not yet implemented");
  }

  @Test
  public void testSetRootReachable() {
    fail("Not yet implemented");
  }

  @Test
  public void testEqualsObject() {
    ObjectReference[] references = new ObjectReference[] {
        ObjectReference.nullReference(),
        Address.fromIntSignExtend(0x4567890).toObjectReference()
    };
    int[] ids = new int[] { 0,1,2,3,4 };
    HeapEntry[] left = new HeapEntry[references.length*ids.length];
    HeapEntry[] right = new HeapEntry[references.length*ids.length];

    for (int i=0; i < references.length; i++) {
      for (int j=0; j < ids.length; j++) {
        left[i*ids.length+j] = new HeapEntry(references[i],ids[j]);
        right[i*ids.length+j] = new HeapEntry(references[i],ids[j]);
      }
    }

    for (int i=0; i < left.length; i++) {
      for (int j=0; j < left.length; j++) {
        if (i == j) {
          assertEquals(left[i],right[j]);
        } else {
          assertFalse(left[i].equals(right[j]));
        }
      }
    }
  }

  @Test
  public void testCompareTo() {
    fail("Not yet implemented");
  }

}
