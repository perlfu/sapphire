/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
//$Id$
package com.ibm.JikesRVM.memoryManagers.mmInterface;

import com.ibm.JikesRVM.VM_Processor;

import org.vmmagic.pragma.*;

/**
 * This class selects the appropriate MMTk plan local type. 
 *
 * @author Daniel Frampton 
 * @author Robin Garner
 *
 * @version $Revision$
 * @date $Date$
 */

public final class SelectedCollectorContext extends
//-#value RVM_WITH_MMTK_COLLECTORCONTEXT
  implements Uninterruptible {

  private VM_Processor processor;

  /**
   * Constructor.  Create a back-link from this context to our parent
   * processor. 
   *
   * @param parent The <code>VM_Processor</code> containing this context.
   */
  public SelectedCollectorContext(VM_Processor parent) {
    super();
    this.processor = parent;
  }

  /** @return The <code>VM_Processor</code> which contains this context */
  public final VM_Processor getProcessor() throws InlinePragma { 
    return processor;
  }

  /**
   * Gets the plan instance associated with the current processor.
   *
   * @return the plan instance for the current processor
   */
  public static final SelectedCollectorContext get() throws InlinePragma {
    return VM_Processor.getCurrentProcessor().collectorContext;
  }
}
