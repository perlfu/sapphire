/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;


import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
/**
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of
 * address triples
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */ 
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
public class AddressTripleQueue extends LocalQueue implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 
 
  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //

  /**
   * Constructor
   *
   * @param queue The shared queue to which this queue will append its
   * buffers (when full or flushed) and from which it will aquire new
   * buffers when it has exhausted its own.
   */
  AddressTripleQueue(SharedQueue queue) {
    super(queue);
  }
  
  /**
   * Insert an address triple into the address queue.
   *
   * @param addr1 the first address to be inserted into the address queue
   * @param addr2 the second address to be inserted into the address queue
   * @param addr3 the third address to be inserted into the address queue
   */
  public final void insert(VM_Address addr1, VM_Address addr2, 
			   VM_Address addr3) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(!addr1.isZero());
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(!addr2.isZero());
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(!addr3.isZero());
    checkInsert(3);
    uncheckedInsert(addr1);
    uncheckedInsert(addr2);
    uncheckedInsert(addr3);
  }
  /**
   * Push an address pair onto the address queue.
   *
   * @param addr1 the first value to be pushed onto the address queue
   * @param addr2 the second value to be pushed onto the address queue
   * @param addr2 the third address to be pushed onto the address queue
   */
  public final void push(VM_Address addr1, VM_Address addr2, VM_Address addr3){
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(!addr1.isZero());
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(!addr2.isZero());
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(!addr3.isZero());
    checkPush(3);
    uncheckedPush(addr3);
    uncheckedPush(addr2);
    uncheckedPush(addr1);
  }

  /**
   * Pop the first address in a triple from the address queue, return
   * zero if the queue is empty.
   *
   * @return The next address in the address queue, or zero if the
   * queue is empty
   */
  public final VM_Address pop1() {
     if (checkPop(3))
      return VM_Address.fromInt(uncheckedPop());
    else
      return VM_Address.zero();
  }
  
  /**
   * Pop the second address in a triple from the address queue.
   *
   * @return The next address in the address queue
   */
  public final VM_Address pop2() {
    return VM_Address.fromInt(uncheckedPop());
  }

  
  /**
   * Pop the third address in a triple from the address queue.
   *
   * @return The next address in the address queue
   */
  public final VM_Address pop3() {
    return VM_Address.fromInt(uncheckedPop());
  }
}
