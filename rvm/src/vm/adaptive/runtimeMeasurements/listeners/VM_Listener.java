/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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
 * must inherit directly from VM_Uninterruptible to ensure that they
 * are not interrupted by a thread switch.  
 * Since thread switching is disabled, listeners are 
 * expected to complete execution quickly, and therefore, 
 * must do a minimal amount of work.
 *
 * @author Peter Sweeney
 * @modified Dave Grove
 */
abstract class VM_Listener implements VM_Uninterruptible {

  /**
   * Entry point to dump what has been collected.
   */
  abstract public void report() throws VM_PragmaInterruptible;

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
   * Organizer associated with this listener.
   */
  public final void setOrganizer(VM_Organizer organizer) {
    this.organizer = organizer;
  }

  /**
   * Wake up the organizer thread (if any) associated with the listener
   */
  public final void notifyOrganizer() {
    if (organizer != null) {
      synchronized(organizer) {
	try {
	  organizer.notify();
	} catch (Exception e) {
	  e.printStackTrace();
	}
      }
    }
  }

  // Is the listener active or passive?
  private boolean active = false;
  // My organizer.
  private VM_Organizer organizer;

}
