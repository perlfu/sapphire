/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002, 2003, 2004
 */
package org.mmtk.policy;

import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.heap.MonotonePageResource;
import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements tracing for a simple immortal collection
 * policy.  Under this policy all that is required is for the
 * "collector" to propogate marks in a liveness trace.  It does not
 * actually collect.  This class does not hold any state, all methods
 * are static.
 * 
 * $Id$ 
 * 
 * @author Perry Cheng
 * @author Steve Blackburn
 * @version $Revision$
 * @date $Date$
 */
public final class ImmortalSpace extends Space 
  implements Constants, Uninterruptible {

  /****************************************************************************
   * 
   * Class variables
   */
  static final Word GC_MARK_BIT_MASK = Word.one();
  private static final int META_DATA_PAGES_PER_REGION = CARD_META_PAGES_PER_REGION;

  /****************************************************************************
   * 
   * Instance variables
   */
  private Word markState = Word.zero(); // when GC off, the initialization value

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * The caller specifies the region of virtual memory to be used for
   * this space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param start The start address of the space in virtual memory
   * @param bytes The size of the space in virtual memory, in bytes
   */
  public ImmortalSpace(String name, int pageBudget, Address start,
                       Extent bytes) {
    super(name, false, true, start, bytes);
    pr = new MonotonePageResource(pageBudget, this, start, extent, META_DATA_PAGES_PER_REGION);
  }

  /**
   * Construct a space of a given number of megabytes in size.<p>
   * 
   * The caller specifies the amount virtual memory to be used for
   * this space <i>in megabytes</i>.  If there is insufficient address
   * space, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param mb The size of the space in virtual memory, in megabytes (MB)
   */
  public ImmortalSpace(String name, int pageBudget, int mb) {
    super(name, false, true, mb);
    pr = new MonotonePageResource(pageBudget, this, start, extent, META_DATA_PAGES_PER_REGION);
  }

  /**
   * Construct a space that consumes a given fraction of the available
   * virtual memory.<p>
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>as a fraction of the total available</i>.  If there
   * is insufficient address space, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param frac The size of the space in virtual memory, as a
   * fraction of all available virtual memory
   */
  public ImmortalSpace(String name, int pageBudget, float frac) {
    super(name, false, true, frac);
    pr = new MonotonePageResource(pageBudget, this, start, extent, META_DATA_PAGES_PER_REGION);
  }

  /**
   * Construct a space that consumes a given number of megabytes of
   * virtual memory, at either the top or bottom of the available
   * virtual memory.
   * 
   * The caller specifies the amount virtual memory to be used for
   * this space <i>in megabytes</i>, and whether it should be at the
   * top or bottom of the available virtual memory.  If the request
   * clashes with existing virtual memory allocations, then the
   * constructor will fail.
   * 
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param mb The size of the space in virtual memory, in megabytes (MB)
   * @param top Should this space be at the top (or bottom) of the
   * available virtual memory.
   */
  public ImmortalSpace(String name, int pageBudget, int mb, boolean top) {
    super(name, false, true, mb, top);
    pr = new MonotonePageResource(pageBudget, this, start, extent, META_DATA_PAGES_PER_REGION);
  }

  /**
   * Construct a space that consumes a given fraction of the available
   * virtual memory, at either the top or bottom of the available
   *          virtual memory.
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>as a fraction of the total available</i>, and
   * whether it should be at the top or bottom of the available
   * virtual memory.  If the request clashes with existing virtual
   * memory allocations, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param frac The size of the space in virtual memory, as a
   * fraction of all available virtual memory
   * @param top Should this space be at the top (or bottom) of the
   * available virtual memory.
   */
  public ImmortalSpace(String name, int pageBudget, float frac, boolean top) {
    super(name, false, true, frac, top);
    pr = new MonotonePageResource(pageBudget, this, start, extent, META_DATA_PAGES_PER_REGION);
  }

  /** @return the current mark state */
  public final Word getMarkState() throws InlinePragma { return markState; }

  /****************************************************************************
   * 
   * Object header manipulations
   */

  /**
   * Initialize the object header post-allocation.  We need to set the mark state
   * correctly and set the logged bit if necessary.
   * 
   * @param object The newly allocated object instance whose header we are initializing
   */
  public final void initializeHeader(ObjectReference object) {
    Word oldValue = VM.objectModel.readAvailableBitsWord(object);
    Word newValue = oldValue.and(GC_MARK_BIT_MASK.not()).or(markState);
    VM.objectModel.writeAvailableBitsWord(object, newValue);
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects during GC
   * Returns true if marking was done.
   */
  private static boolean testAndMark(ObjectReference object, Word value)
      throws InlinePragma {
    Word oldValue;
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      Word markBit = oldValue.and(GC_MARK_BIT_MASK);
      if (markBit.EQ(value)) return false;
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue,
                                               oldValue.xor(GC_MARK_BIT_MASK)));
    return true;
  }

  /**
   * Trace a reference to an object under an immortal collection
   * policy.  If the object is not already marked, enqueue the object
   * for subsequent processing. The object is marked as (an atomic)
   * side-effect of checking whether already marked.
   *
   * @param trace The trace being conducted.
   * @param object The object to be traced.
   */
  public final ObjectReference traceObject(TraceLocal trace,
                                           ObjectReference object) 
    throws InlinePragma {
    if (testAndMark(object, markState))
      trace.enqueue(object);
    return object;
  }

  /**
   * Prepare for a new collection increment.  For the immortal
   * collector we must flip the state of the mark bit between
   * collections.
   */
  public void prepare() {
    markState = GC_MARK_BIT_MASK.minus(markState);
  }

  public void release() {}

  /**
   * Release an allocated page or pages.  In this case we do nothing
   * because we only release pages enmasse.
   * 
   * @param start The address of the start of the page or pages
   */
  public final void release(Address start) throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false); // this policy only releases pages enmasse
  }

  public final boolean isLive(ObjectReference object) throws InlinePragma {
    return true;
  }

  /**
   * Returns if the object in question is currently thought to be reachable.
   * This is done by comparing the mark bit to the current mark state. For the
   * immortal collector reachable and live are different, making this method
   * necessary.
   * 
   * @param object The address of an object in immortal space to test
   * @return True if <code>ref</code> may be a reachable object (e.g., having
   *         the current mark state).  While all immortal objects are live,
   *         some may be unreachable.
   */
  public boolean isReachable(ObjectReference object) {
    if (Plan.SCAN_BOOT_IMAGE && this == Plan.vmSpace)
      return true;  // ignore boot image "reachabilty" if we're not tracing it
    else
      return (VM.objectModel.readAvailableBitsWord(object).and(GC_MARK_BIT_MASK).EQ(markState));
  }
}
