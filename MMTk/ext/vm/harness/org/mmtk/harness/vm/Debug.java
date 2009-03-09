/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.harness.vm;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.sanity.FromSpaceInvariant;
import org.mmtk.plan.Simple;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Debugger support for the MMTk harness
 */
public final class Debug extends org.mmtk.vm.Debug {

  /**
   * Enable MMTk debugger support
   */
  @Override
  public boolean isEnabled() {
    return true;
  }

  private String format(ObjectReference obj) {
    if (obj.isNull()) {
      return obj.toString();
    }
    return ObjectModel.getString(obj);
  }

  private String format(Address addr) {
    return format(addr.toObjectReference());
  }

  /**
   * @see org.mmtk.vm.Debug#arrayRemsetEntry(org.vmmagic.unboxed.Address, org.vmmagic.unboxed.Address)
   */
  @Override
  public void arrayRemsetEntry(Address start, Address guard) {
    Trace.printf(Item.COLLECT, "arrayRemset: [%s,%s)", start, guard);
  }

  /**
   * @see org.mmtk.vm.Debug#modbufEntry(org.vmmagic.unboxed.ObjectReference)
   */
  @Override
  public void modbufEntry(ObjectReference object) {
    Trace.printf(Item.COLLECT, "modbuf: %s", format(object));
  }

  /**
   * @see org.mmtk.vm.Debug#remsetEntry(org.vmmagic.unboxed.Address)
   */
  @Override
  public void remsetEntry(Address slot) {
    Trace.printf(Item.COLLECT, "remset: %s->%s", format(slot), format(slot.loadAddress()));
  }

  /**
   * @see org.mmtk.vm.Debug#globalPhase(short, boolean)
   */
  @Override
  public void globalPhase(short phaseId, boolean before) {
    if (phaseId == Simple.RELEASE && before) {
      new FromSpaceInvariant();
    }
  }
}
