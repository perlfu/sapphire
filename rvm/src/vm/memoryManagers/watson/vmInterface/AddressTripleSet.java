/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.memoryManagers.watson;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_EventLogger;
import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * @author Perry Cheng  
 */  
public class AddressTripleSet implements VM_Uninterruptible {

  // Deficiency in compiler prevents use of VM_Address []
  int [] address;
  int cursor;

  public AddressTripleSet(int size) { 
    address = new int[3 * size];
  }

  public void clear() { 
    cursor = 0; 
  }

  public boolean isEmpty() {
    return cursor == 0;
  }

  public void push(VM_Address addr1, VM_Address addr2, VM_Address addr3) { 
    if (VM.VerifyAssertions) VM._assert(!addr1.isZero());
    if (VM.VerifyAssertions) VM._assert(!addr2.isZero());
    if (VM.VerifyAssertions) VM._assert(!addr3.isZero());
    // Backwards so that pop1/pop2/pop3 will return in the right order
    address[cursor++] = addr3.toInt(); 
    address[cursor++] = addr2.toInt(); 
    address[cursor++] = addr1.toInt(); 
  }

  public VM_Address pop1() {
    if (cursor == 0)
      return VM_Address.zero();
    if (VM.VerifyAssertions) VM._assert((cursor % 3) == 0);
    return VM_Address.fromInt(address[--cursor]);
  }

  public VM_Address pop2() {
    if (cursor == 0)
      return VM_Address.zero();
    if (VM.VerifyAssertions) VM._assert((cursor % 3) == 1);
    return VM_Address.fromInt(address[--cursor]);
  }

  public VM_Address pop3() {
    if (cursor == 0)
      return VM_Address.zero();
    if (VM.VerifyAssertions) VM._assert((cursor % 3) == 2);
    return VM_Address.fromInt(address[--cursor]);
  }


}
