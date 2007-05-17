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
package org.jikesrvm.adaptive.measurements.listeners;

import org.jikesrvm.adaptive.measurements.organizers.VM_Organizer;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A VM_Listener object is invoked when online measurement information
 * needs to be collected.
 *
 * This class does not define the update() method, the call back method from
 * the runtime when a sample should be taken.
 * The expectation is that immediately derived classes define an interface to
 * the update() method from which classes may be further derived.
 *
 * CONSTRAINTS:
 * Classes that are derived from VM_Listener
 * must be annotated as Uninterruptible to ensure that they
 * are not interrupted by a thread switch.
 * Since thread switching is disabled, listeners are
 * expected to complete execution quickly, and therefore,
 * must do a minimal amount of work.
 */
@Uninterruptible
public abstract class VM_Listener {

  /**
   * Entry point to dump what has been collected.
   */
  @Interruptible
  public abstract void report();

  /**
   * Is the listener currently active (interested in getting "update" calls)
   */
  public final boolean isActive() { return active; }

  /**
   * Transition listener to active state
   */
  public final void activate() { active = true; }

  /**
   * Transition listener to passive state
   */
  public final void passivate() { active = false; }

  /**
   * reset the listeners data structures
   */
  public abstract void reset();

  /**
   * Organizer associated with this listener.
   */
  public final void setOrganizer(VM_Organizer organizer) {
    this.organizer = organizer;
  }

  /**
   * Wake up the organizer thread (if any) associated with the listener
   */
  public final void activateOrganizer() {
    if (organizer != null) {
      organizer.activate();
    }
  }

  // Is the listener active or passive?
  private boolean active = false;
  // My organizer.
  private VM_Organizer organizer;

}
