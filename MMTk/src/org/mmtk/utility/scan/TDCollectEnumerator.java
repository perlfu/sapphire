/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
//$Id$
package org.mmtk.utility.scan;

import org.mmtk.utility.TrialDeletion;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * A pointer enumeration class.  This class is used by the trial
 * deletion cycle detector to perform transitive closure of its
 * "collect" phase.
 * 
 * @author Steve Blackburn
 * @version $Revision$
 * @date $date: $
 */
public class TDCollectEnumerator extends Enumerator implements Uninterruptible {
  private TrialDeletion td;

  /**
   * Constructor.
   * 
   * @param td The trial deletion instance with respect to which the
   * enumeration will occur.
   */
  public TDCollectEnumerator(TrialDeletion td) {
    this.td = td;
  }

  /**
   * Enumerate a pointer. In this case it is a collect event.
   * 
   * @param objLoc The address of the field being enumerated.
   */
  public void enumeratePointerLocation(Address objLoc) 
    throws InlinePragma {
    td.enumerateCollect(objLoc.loadObjectReference());
  }
}
