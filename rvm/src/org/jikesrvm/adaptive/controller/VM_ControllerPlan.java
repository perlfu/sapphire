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
package org.jikesrvm.adaptive.controller;

import java.util.LinkedList;
import org.jikesrvm.VM;
import org.jikesrvm.adaptive.util.VM_AOSGenerator;
import org.jikesrvm.adaptive.util.VM_AOSLogging;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.compilers.common.VM_RuntimeCompiler;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;

/**
 * An instance of this class describes a compilation decision made by
 * the controller
 *
 * Constraints:
 *   Given the plan list of a method:
 *      Only one plan will have status COMPLETED
 *      Multiple plans may have status OUTDATED
 *      Only one plan will have status IN_PROGRESS
 *
 * status states:
 * UNINITIALIZED -> IN_PROGRESS -> COMPLETED -> OUTDATED
 *             \              \--> ABORTED_COMPILATION_ERROR (never recompile method)
 */
public final class VM_ControllerPlan {

  // The plan was created, but the setStatus method was never called
  public static final byte UNINITIALIZED = 0;

  // The plan was successfully completed, i.e., the method was recompiled
  public static final byte COMPLETED = 1;

  // Compilation began the method, but failed in an error
  public static final byte ABORTED_COMPILATION_ERROR = 2;

  // The compilation is still in progress
  public static final byte IN_PROGRESS = 3;

  // The compilation completed, but a new plan for the same method also
  // completed, so this is not the most recent completed plan
  public static final byte OUTDATED = 4;

  // The compilation plan is for a promotion from BASE to OPT
  public static final byte OSR_BASE_2_OPT = 5;

  // This is used by clients to initialize local variables for Java semantics
  public static final byte UNKNOWN = 99;

  /**
   *  The associate compilation plan
   */
  private CompilationPlan compPlan;

  /**
   *  The time we created this plan
   */
  private int timeCreated;

  /**
   *  The time compilation began
   */
  private int timeInitiated = -1;

  /**
   *  The time compilation end
   */
  private int timeCompleted = -1;

  /**
   *  The speedup we were expecting
   */
  private double expectedSpeedup;

  /**
   *  The compilation time we were expecting
   */
  private double expectedCompilationTime;

  /**
   *  The priority associated with this plan
   */
  private double priority;

  /**
   *  The compiled method ID for this plan
   */
  private int CMID;

  /**
   *  The compiled method ID for the previous plan for this method
   */
  private int prevCMID;

  /**
   *  The status of this plan
   */
  private byte status;

  /**
   *  The list that we are onstatus of this plan
   */
  private LinkedList<VM_ControllerPlan> planList;

  /**
   * Construct a controller plan
   *
   * @param compPlan     The compilation plan
   * @param timeCreated  The "time" this plan was created
   * @param prevCMID     The previous compiled method ID
   * @param expectedSpeedup     Expected recompilation benefit
   * @param expectedCompilationTime     Expected recompilation cost
   * @param priority     How important is executing this plan?
   */
  public VM_ControllerPlan(CompilationPlan compPlan, int timeCreated, int prevCMID, double expectedSpeedup,
                           double expectedCompilationTime, double priority) {
    this.compPlan = compPlan;
    this.timeCreated = timeCreated;
    this.prevCMID = prevCMID;
    this.status = VM_ControllerPlan.UNINITIALIZED;
    this.expectedSpeedup = expectedSpeedup;
    this.expectedCompilationTime = expectedCompilationTime;
    this.priority = priority;
  }

  /**
   * Execute the plan.
   *
   * @return true on success, false on failure
   */
  public boolean execute() {
    // mark plan as in progress and insert it into controller memory
    setStatus(VM_ControllerPlan.IN_PROGRESS);
    VM_ControllerMemory.insert(this);

    if (VM_Controller.options
        .BACKGROUND_RECOMPILATION ||
                                  getCompPlan().getMethod().getDeclaringClass().isInBootImage()) {
      VM_Controller.compilationQueue.insert(getPriority(), this);
      VM_AOSLogging.recompilationScheduled(getCompPlan(), getPriority());
      return true;
    } else {
      getCompPlan().getMethod().replaceCompiledMethod(null);
      return true;
    }
  }

  /**
   * This method will recompile the method designated by the controller plan
   * {@link #getCompPlan}.  It also
   *  1) credits the samples associated with the old compiled method
   *     ID to the new method ID and clears the old value.
   *  2) clears inlining information
   *  3) updates the status of the controller plan
   */
  public VM_CompiledMethod doRecompile() {
    CompilationPlan cp = getCompPlan();

    setTimeInitiated(VM_Controller.controllerClock);
    VM_AOSLogging.recompilationStarted(cp);

    if (cp.options.PRINT_METHOD) {
      VM.sysWrite("-oc:O" + cp.options.getOptLevel() + " \n");
    }

    // Compile the method.
    int newCMID = VM_RuntimeCompiler.recompileWithOpt(cp);
    int prevCMID = getPrevCMID();

    if (VM_Controller.options.sampling()) {
      // transfer the samples from the old CMID to the new CMID.
      // scale the number of samples down by the expected speedup
      // in the newly compiled method.
      double expectedSpeedup = getExpectedSpeedup();
      double oldNumSamples = VM_Controller.methodSamples.getData(prevCMID);
      double newNumSamples = oldNumSamples / expectedSpeedup;
      VM_Controller.methodSamples.reset(prevCMID);
      if (newCMID > -1) {
        VM_Controller.methodSamples.augmentData(newCMID, newNumSamples);
      }
    }

    // set the status of the plan accordingly
    if (newCMID != -1) {
      setStatus(VM_ControllerPlan.COMPLETED);
    } else {
      setStatus(VM_ControllerPlan.ABORTED_COMPILATION_ERROR);
    }

    setCMID(newCMID);
    setTimeCompleted(VM_Controller.controllerClock);
    VM_CompiledMethod cm = newCMID == -1 ? null : VM_CompiledMethods.getCompiledMethod(newCMID);
    if (newCMID == -1) {
      VM_AOSLogging.recompilationAborted(cp);
    } else {
      VM_AOSLogging.recompilationCompleted(cp);
      VM_AOSLogging.recordCompileTime(cm, getExpectedCompilationTime());
    }
    if (VM_Controller.options.ENABLE_ADVICE_GENERATION && (newCMID != -1)) {
      VM_AOSGenerator.reCompilationWithOpt(cp);
    }
    return cm;
  }

  /**
   * The compilation plan
   */
  public CompilationPlan getCompPlan() { return compPlan; }

  /**
   * The expected speedup <em>for this method </em> due to this recompilation
   */
  public double getExpectedSpeedup() { return expectedSpeedup; }

  /**
   * The expected compilation time for this method
   */
  public double getExpectedCompilationTime() { return expectedCompilationTime; }

  /**
   * The priority (how important is it that this plan be executed)
   */
  public double getPriority() { return priority; }

  /**
   * The time this plan was created
   */
  public int getTimeCreated() { return timeCreated; }

  /**
   * The time (according to the controller clock) compilation of this plan
   * began.
   */
  public int getTimeInitiated() { return timeInitiated; }

  public void setTimeInitiated(int t) { timeInitiated = t; }

  /**
   * The time (according to the controller clock) compilation of this plan
   * completed.
   */
  public int getTimeCompleted() { return timeCompleted; }

  public void setTimeCompleted(int t) { timeCompleted = t; }

  /**
   * CMID (compiled method id) associated with the code produced
   * by executing this plan
   */
  public int getCMID() { return CMID; }

  public void setCMID(int x) { CMID = x; }

  /**
   * CMID (compiled method id) associated with the *PREVIOUS* compiled
   * version of this method
   */
  public int getPrevCMID() { return prevCMID; }

  /**
   * Status of this compilation plan, choose from the values above
   */
  public byte getStatus() { return status; }

  public void setStatus(byte newStatus) {
    status = newStatus;

    // if we are marking this plan as completed, all previous completed plans
    // for this method should be marked as OUTDATED
    if (newStatus == COMPLETED) {
      // iterate over the planList until we get to this item
      synchronized (planList) {
        for (VM_ControllerPlan curPlan : planList) {
          // exit when we find ourselves
          if (curPlan == this) break;

          if (curPlan.getStatus() == COMPLETED) {
            curPlan.status = OUTDATED;
          }
        } // more to process
      }
    }
  }

  /**
   * List of plans for a source method
   */
  public void setPlanList(LinkedList<VM_ControllerPlan> list) { planList = list; }

  public String getStatusString() {
    switch (status) {
      case UNINITIALIZED:
        return "UNINITIALIZED";
      case COMPLETED:
        return "COMPLETED";
      case ABORTED_COMPILATION_ERROR:
        return "ABORTED_COMPILATION_ERROR";
      case IN_PROGRESS:
        return "IN_PROGRESS";
      case OUTDATED:
        return "OUTDATED";
      case OSR_BASE_2_OPT:
        return "OSR_BASE_2_OPT";
      case UNKNOWN:
        return "UNKNOWN (not error)";
      default:
        return "**** ERROR, UNKNOWN STATUS ****";
    }
  }

  public String toString() {
    StringBuilder buf = new StringBuilder();

    buf.append("Method: ").append(getCompPlan().method).append("\n\tCompiled Method ID: ").append(CMID).append(
        "\n\tPrevious Compiled Method ID: ").append(prevCMID).append("\n\tCreated at ").append(timeCreated).append(
        "\n\tInitiated at ").append(timeInitiated).append("\n\tCompleted at ").append(timeCompleted).append(
        "\n\tExpected Speedup: ").append(expectedSpeedup).append("\n\tExpected Compilation Time: ").append(
        expectedCompilationTime).append("\n\tPriority: ").append(priority).append("\n\tStatus: ").append(getStatusString()).append(
        "\n\tComp. Plan Level: ").append(compPlan.options.getOptLevel()).append("\n");
    return buf.toString();
  }

}
