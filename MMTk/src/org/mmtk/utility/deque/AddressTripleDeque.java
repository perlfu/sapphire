/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.utility.deque;

import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.Uninterruptible;


/**
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of
 * address triples
 * 
 * @author Steve Blackburn
 */
@Uninterruptible public class AddressTripleDeque extends LocalDeque implements Constants {

  /****************************************************************************
   * 
   * Public instance methods
   */

  /**
   * Constructor
   * 
   * @param queue The shared queue to which this queue will append its
   * buffers (when full or flushed) and from which it will aquire new
   * buffers when it has exhausted its own.
   */
  AddressTripleDeque(SharedDeque queue) {
    super(queue);
  }

  /**
   * Insert an address triple into the address queue.
   * 
   * @param addr1 the first address to be inserted into the address queue
   * @param addr2 the second address to be inserted into the address queue
   * @param addr3 the third address to be inserted into the address queue
   */
  public final void insert(Address addr1, Address addr2, 
                           Address addr3) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!addr1.isZero());
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!addr2.isZero());
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!addr3.isZero());
    checkTailInsert(3);
    uncheckedTailInsert(addr1);
    uncheckedTailInsert(addr2);
    uncheckedTailInsert(addr3);
  }
  /**
   * Push an address pair onto the address queue.
   * 
   * @param addr1 the first value to be pushed onto the address queue
   * @param addr2 the second value to be pushed onto the address queue
   * @param addr3 the third address to be pushed onto the address queue
   */
  public final void push(Address addr1, Address addr2, Address addr3) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!addr1.isZero());
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!addr2.isZero());
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!addr3.isZero());
    checkHeadInsert(3);
    uncheckedHeadInsert(addr3);
    uncheckedHeadInsert(addr2);
    uncheckedHeadInsert(addr1);
  }

  /**
   * Pop the first address in a triple from the address queue, return
   * zero if the queue is empty.
   * 
   * @return The next address in the address queue, or zero if the
   * queue is empty
   */
  public final Address pop1() {
    if (checkDequeue(3))
      return uncheckedDequeue();
    else
      return Address.zero();
  }

  /**
   * Pop the second address in a triple from the address queue.
   * 
   * @return The next address in the address queue
   */
  public final Address pop2() {
    return uncheckedDequeue();
  }

  
  /**
   * Pop the third address in a triple from the address queue.
   * 
   * @return The next address in the address queue
   */
  public final Address pop3() {
    return uncheckedDequeue();
  }
}
