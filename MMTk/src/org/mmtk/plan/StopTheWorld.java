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
package org.mmtk.plan;

import org.mmtk.utility.Log;
import org.mmtk.utility.options.Options;
import org.vmmagic.pragma.*;

/**
 * This abstract class implements the core functionality for
 * stop-the-world collectors.  Stop-the-world collectors should
 * inherit from this class.<p>
 *
 * This class defines the collection phases, and provides base
 * level implementations of them.  Subclasses should provide
 * implementations for the spaces that they introduce, and
 * delegate up the class hierarchy.<p>
 *
 * For details of the split between global and thread-local operations
 * @see org.mmtk.plan.Plan
 */
@Uninterruptible
public abstract class StopTheWorld extends Simple {

  // CHECKSTYLE:OFF

  /** Build and validate a sanity table */
  protected static final short preSanityPhase = Phase.createComplex("pre-sanity", null,
      Phase.scheduleGlobal     (SANITY_SET_PREGC),
      Phase.scheduleComplex    (sanityBuildPhase),
      Phase.scheduleComplex    (sanityCheckPhase));

  /** Build and validate a sanity table */
  protected static final short postSanityPhase = Phase.createComplex("post-sanity", null,
      Phase.scheduleGlobal     (SANITY_SET_POSTGC),
      Phase.scheduleComplex    (sanityBuildPhase),
      Phase.scheduleComplex    (sanityCheckPhase));

  // CHECKSTYLE:ON

  /****************************************************************************
   * Collection
   */

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  @Interruptible
  public void postBoot() {
    super.postBoot();

    if (Options.sanityCheck.getValue()) {
      Log.writeln("Collection sanity checking enabled.");
      replacePhase(Phase.schedulePlaceholder(PRE_SANITY_PLACEHOLDER),  Phase.scheduleComplex(preSanityPhase));
      replacePhase(Phase.schedulePlaceholder(POST_SANITY_PLACEHOLDER), Phase.scheduleComplex(postSanityPhase));
    }
  }
}
