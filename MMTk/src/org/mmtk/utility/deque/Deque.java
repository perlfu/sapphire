/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.utility.deque;

import org.mmtk.utility.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Class that defines a doubly-linked double-ended queue (deque). The
 * double-linking increases the space demands slightly, but makes it far
 * more efficient to dequeue buffers and, for example, enables sorting of
 * its contents.
 * 
 * @author Steve Blackburn
 * @modified <a href="http://www-ali.cs.umass.edu">Matthew Hertz</a>
 * @version $Revision$
 * @date $Date$
 */
class Deque implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   * 
   * Protected instance methods
   * 
   * protected int enqueued;
   */

  protected final Offset bufferOffset(Address buf) throws InlinePragma {
    return buf.toWord().and(BUFFER_MASK).toOffset();
  }
  protected final Address bufferStart(Address buf) throws InlinePragma {
    return buf.toWord().and(BUFFER_MASK.not()).toAddress();
  }
  protected final Address bufferEnd(Address buf) throws InlinePragma {
    return bufferStart(buf).plus(USABLE_BUFFER_BYTES);
  }
  protected final Address bufferFirst(Address buf) throws InlinePragma {
    return bufferStart(buf);
  }
  protected final Address bufferLast(Address buf, int arity) throws InlinePragma {
    return bufferStart(buf).plus(bufferLastOffset(arity));
  }
  protected final Address bufferLast(Address buf) throws InlinePragma {
    return bufferLast(buf, 1);
  }
  protected final Offset bufferLastOffset(int arity) throws InlinePragma {
    return Offset.fromIntZeroExtend(USABLE_BUFFER_BYTES - BYTES_IN_ADDRESS
        - (USABLE_BUFFER_BYTES % (arity << LOG_BYTES_IN_ADDRESS)));
  }

  /****************************************************************************
   * 
   * Private and protected static final fields (aka constants)
   */
  protected static final int LOG_PAGES_PER_BUFFER = 0;
  protected static final int PAGES_PER_BUFFER = 1 << LOG_PAGES_PER_BUFFER;
  private static final int LOG_BUFFER_SIZE = (LOG_BYTES_IN_PAGE + LOG_PAGES_PER_BUFFER);
  protected static final int BUFFER_SIZE = 1 << LOG_BUFFER_SIZE;
  protected static final Word BUFFER_MASK = Word.one().lsh(LOG_BUFFER_SIZE).minus(Word.one());
  protected static final int NEXT_FIELD_OFFSET = BYTES_IN_ADDRESS;
  protected static final int META_DATA_SIZE = 2 * BYTES_IN_ADDRESS;
  protected static final int USABLE_BUFFER_BYTES = BUFFER_SIZE - META_DATA_SIZE;
  protected static final Address TAIL_INITIAL_VALUE = Address.zero();
  protected static final Address HEAD_INITIAL_VALUE = Address.zero();
}
