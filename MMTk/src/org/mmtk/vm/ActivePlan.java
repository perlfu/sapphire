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
package org.mmtk.vm;

import org.mmtk.plan.Plan;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.PlanConstraints;

import org.mmtk.utility.Log;

import org.vmmagic.pragma.*;

/**
 * Stub to give access to plan local, constraint and global instances
 */
@Uninterruptible public abstract class ActivePlan {

  /** @return The active Plan instance. */
  public abstract Plan global();

  /** @return The active PlanConstraints instance. */
  public abstract PlanConstraints constraints();

  /** @return The active <code>CollectorContext</code> instance. */
  public abstract CollectorContext collector();

  /** @return The active <code>MutatorContext</code> instance. */
  public abstract MutatorContext mutator();

  /** @return The log for the active thread */
  public abstract Log log();

  /**
   * Return the <code>CollectorContext</code> instance given its unique identifier.
   *
   * @param id The identifier of the <code>CollectorContext</code>  to return
   * @return The specified <code>CollectorContext</code>
   */
  public abstract CollectorContext collector(int id);

  /**
   * Return the <code>MutatorContext</code> instance given its unique identifier.
   *
   * @param id The identifier of the <code>MutatorContext</code>  to return
   * @return The specified <code>MutatorContext</code>
   */
  public abstract MutatorContext mutator(int id);

  /** @return The number of registered <code>CollectorContext</code> instances. */
  public abstract int collectorCount();

  /** @return The number of registered <code>MutatorContext</code> instances. */
  public abstract int mutatorCount();

  /** Reset the mutator iterator */
  public abstract void resetMutatorIterator();

  /**
   * Return the next <code>MutatorContext</code> in a
   * synchronized iteration of all mutators.
   *
   * @return The next <code>MutatorContext</code> in a
   *  synchronized iteration of all mutators, or
   *  <code>null</code> when all mutators have been done.
   */
  public abstract MutatorContext getNextMutator();

  /**
   * Register a new <code>CollectorContext</code> instance.
   *
   * @param collector The <code>CollectorContext</code> to register.
   * @return The <code>CollectorContext</code>'s unique identifier
   */
  @Interruptible
  public abstract int registerCollector(CollectorContext collector);

  /**
   * Register a new <code>MutatorContext</code> instance.
   *
   * @param mutator The <code>MutatorContext</code> to register.
   * @return The <code>MutatorContext</code>'s unique identifier
   */
  @Interruptible
  public abstract int registerMutator(MutatorContext mutator);
}
