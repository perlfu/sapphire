/*
 * (C) Copyright IBM Corp 2001,2002
 */
package org.mmtk.utility;

import org.mmtk.utility.gcspy.TreadmillDriver;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;


/**
 * Each instance of this class is a doubly-linked list, in which
 * each item or node is a piece of memory.  The first two words of each node
 * contains the forward and backward links.  The third word contains
 * the treadmill.  The remaining portion is the payload.
 *  
 * The treadmill object itself must not be moved.
 *
 * Access to the instances may be synchronized depending on the constructor argument.
 *
 * @author Perry Cheng
 * @version $Revision$
 * @date $Date$
 */
public final class Treadmill
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */

  /****************************************************************************
   *
   * Instance variables
   */
  private DoublyLinkedList fromSpace;
  private DoublyLinkedList toSpace;

  /****************************************************************************
   *
   * Instance Methods
   */

  /**
   * Constructor
   */
  public Treadmill (int granularity, boolean shared) {
    fromSpace = new DoublyLinkedList (granularity, shared, this); 
    toSpace = new DoublyLinkedList (granularity, shared, this); 
  }

  static public final Treadmill getTreadmill (VM_Address node) {
    return (Treadmill) DoublyLinkedList.getOwner(node);
  }

  static public final int headerSize() throws VM_PragmaInline {
    return DoublyLinkedList.headerSize();
  }

  static public final VM_Address nodeToPayload(VM_Address payload) throws VM_PragmaInline {
    return DoublyLinkedList.nodeToPayload(payload);
  }

  static public final VM_Address payloadToNode(VM_Address payload) throws VM_PragmaInline {
    return DoublyLinkedList.payloadToNode(payload);
  }

  static public final VM_Address midPayloadToNode(VM_Address payload) throws VM_PragmaInline {
    return DoublyLinkedList.midPayloadToNode(payload);
  }

  public final void addToFromSpace (VM_Address node) throws VM_PragmaInline {
    fromSpace.add(node);
  }

  public final VM_Address popFromSpace () throws VM_PragmaInline {
    return fromSpace.pop();
  }

  public final void copy (VM_Address node) throws VM_PragmaInline { 
    fromSpace.remove(node);
    toSpace.add(node);
  }

  public final boolean toSpaceEmpty () throws VM_PragmaInline {
    return toSpace.isEmpty();
  }

  public final void flip() {  
    DoublyLinkedList tmp = fromSpace;
    fromSpace = toSpace;
    toSpace = tmp;
  }

  /**
   * Gather data for GCSpy
   * @param event the gc event
   * @param gcspyDriver the GCSpy space driver
   * @param tospace gather from tospace?
   */
  public void gcspyGatherData(int event, TreadmillDriver tmDriver, boolean tospace) {
    if (tospace) 
      toSpace.gcspyGatherData(tmDriver);
    else
      fromSpace.gcspyGatherData(tmDriver);
  }

}
