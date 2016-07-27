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
package org.mmtk.plan.semispace;

import org.mmtk.plan.*;
import org.mmtk.policy.CopySpace;
import org.vmmagic.pragma.*;

/**
 * SemiSpace common constants.
 */
@Uninterruptible
public class SSConstraints extends StopTheWorldConstraints {
  private static final boolean USE_BARRIERS = false;

  @Override
  public boolean movesObjects() { return true; }
  @Override
  public int gcHeaderBits() { return CopySpace.LOCAL_GC_BITS_REQUIRED; }
  @Override
  public int gcHeaderWords() { return CopySpace.GC_HEADER_WORDS_REQUIRED; }
  @Override
  public int numSpecializedScans() { return 0; }
  
  /** @return True if biased locking is supported; sapphire does not */
  @Override
  public boolean supportsBiasedLocking() { return false; }
  
  /** @return True if this Plan requires write barriers on booleans. */
  @Override
  public boolean needsBooleanWriteBarrier() { return USE_BARRIERS; }

  /** @return True if this Plan can perform bulk boolean arraycopy barriers. */
  @Override
  public boolean booleanBulkCopySupported() { return false; }

  /** @return True if this Plan requires write barriers on bytes. */
  @Override
  public boolean needsByteWriteBarrier() { return USE_BARRIERS; }

  /** @return True if this Plan can perform bulk byte arraycopy barriers. */
  @Override
  public boolean byteBulkCopySupported() { return false; }

  /** @return True if this Plan requires write barriers on chars. */
  @Override
  public boolean needsCharWriteBarrier() { return USE_BARRIERS; }

  /** @return True if this Plan can perform bulk char arraycopy barriers. */
  @Override
  public boolean charBulkCopySupported() { return false; }

  /** @return True if this Plan requires write barriers on shorts. */
  @Override
  public boolean needsShortWriteBarrier() { return USE_BARRIERS; }

  /** @return True if this Plan can perform bulk short arraycopy barriers. */
  @Override
  public boolean shortBulkCopySupported() { return false; }

  /** @return True if this Plan requires write barriers on ints. */
  @Override
  public boolean needsIntWriteBarrier() { return USE_BARRIERS; }

  /** @return True if this Plan can perform bulk int arraycopy barriers. */
  @Override
  public boolean intBulkCopySupported() { return false; }

  /** @return True if this Plan requires write barriers on longs. */
  @Override
  public boolean needsLongWriteBarrier() { return USE_BARRIERS; }

  /** @return True if this Plan can perform bulk long arraycopy barriers. */
  @Override
  public boolean longBulkCopySupported() { return false; }

  /** @return True if this Plan requires write barriers on floats. */
  @Override
  public boolean needsFloatWriteBarrier() { return USE_BARRIERS; }

  /** @return True if this Plan can perform bulk float arraycopy barriers. */
  @Override
  public boolean floatBulkCopySupported() { return false; }

  /** @return True if this Plan requires write barriers on doubles. */
  @Override
  public boolean needsDoubleWriteBarrier() { return USE_BARRIERS; }

  /** @return True if this Plan can perform bulk double arraycopy barriers. */
  @Override
  public boolean doubleBulkCopySupported() { return false; }

  /** @return True if this Plan requires write barriers on Words. */
  @Override
  public boolean needsWordWriteBarrier() { return USE_BARRIERS; }

  /** @return True if this Plan requires write barriers on Address's. */
  @Override
  public boolean needsAddressWriteBarrier() { return true;/*USE_BARRIERS;*/ }

  /** @return True if this Plan requires write barriers on Extents. */
  @Override
  public boolean needsExtentWriteBarrier() { return USE_BARRIERS; }

  /** @return True if this Plan requires write barriers on Offsets. */
  @Override
  public boolean needsOffsetWriteBarrier() { return USE_BARRIERS; }

  /** @return True if this Plan requires write barriers on object references. */
  @Override
  public boolean needsObjectReferenceWriteBarrier() { return USE_BARRIERS; }

  /** @return True if this Plan requires non-heap write barriers on object references. */
  @Override
  public boolean needsObjectReferenceNonHeapWriteBarrier() { return USE_BARRIERS; }

  /** @return True if this Plan requires read barriers on java.lang.reference types. */
  @Override
  public boolean needsJavaLangReferenceReadBarrier() { return false; }  // this probably needs changing
  
  /** @return True if this Plan requires a barrier on object equality tests. */
  @Override
  public boolean needsObjectReferenceCompareBarrier() { return USE_BARRIERS; }
}
