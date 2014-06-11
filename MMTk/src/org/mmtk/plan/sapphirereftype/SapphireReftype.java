/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.sapphirereftype;

import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.*;

@Uninterruptible
public class SapphireReftype extends org.mmtk.plan.otfsapphire.OTFSapphire {
  public static final boolean REFERENCE_PROCESS_INFLOOP = false;
  public static final boolean REFERENCE_PROCESS_STW     = false;
  public static final boolean REFERENCE_PROCESS_YUASA   = true;
  
  public static final boolean REFERENCE_REPORT_TRACE_OBJS = false;
  public static final boolean REFERENCE_REPORT_NUM_REFS   = false;
  public static final boolean REFERENCE_REPORT_ITRATIONS  = false;
  public static final boolean REFERENCE_REPORT_USES       = false;
  public static final boolean REFERENCE_REPORT_TIME       = false;
  public static final boolean REFERENCE_REPORT_OPTIONS    = false;
  
  public static final org.mmtk.vm.SynchronizedCounter traceCount = VM.newSynchronizedCounter(); 
  public static int maxTerminationLoopCount = 0;
  public static int terminationLoopCountHist[] = new int[51];
  public static final org.mmtk.vm.SynchronizedCounter weakRefUseCount = VM.newSynchronizedCounter();
  public static int lastWeakRefUseCount;
  public static final long WEAK_REF_TIME_NOT_STARTED = -1;
  public static long weakRefTime = WEAK_REF_TIME_NOT_STARTED;
  public static long weakRefProcessStart;
  public static long maxRefProcessTime = 0;
  public static final org.mmtk.vm.Lock weakRefTimeLock = VM.newLock("weak-ref-time-lock");
  public static void reportUseOfWeakRef() {
  	if (REFERENCE_REPORT_USES) {
  		if (weakRefTime == WEAK_REF_TIME_NOT_STARTED) {
  			weakRefTime = VM.statistics.nanoTime();
  		} else {
	  		long now = VM.statistics.nanoTime();
	  		if (now - weakRefTime > 10 * 1000 * 1000 /* 10 ms */) {
	  			weakRefTimeLock.acquire();
	  			if (now - weakRefTime > 10 * 1000 * 1000 /* 10 ms */) {
		  			Log.write("ref-use: ");
		  			Log.write(weakRefUseCount.peek());
		  			Log.write(" time: ");
		  			Log.writeln(weakRefTime); 
		  			weakRefTime = now;
	  			}
	  			weakRefTimeLock.release();
	  		}
  		}
  		weakRefUseCount.increment();
  	}
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase
   */
  @Inline
  public void collectionPhase(short phaseId) {
    if (phaseId == CLOSURE) {
      super.collectionPhase(phaseId);
      if (REFERENCE_REPORT_TRACE_OBJS) {
        traceCount.reset();Log.writeln(traceCount.peek());
      }
      return;
    }

    if (phaseId == OTF_PREPARE_REFERENCE) {
      if (REFERENCE_REPORT_TIME) {
        weakRefProcessStart = VM.statistics.nanoTime();
      }
      if (REFERENCE_REPORT_TRACE_OBJS) {
        Log.writeln(); Log.write("traced: prep: "); Log.writeln(traceCount.peek());
      }
      super.collectionPhase(phaseId);
      return;
    }

    if (phaseId == OTF_RELEASE_REFERENCE) {
      super.collectionPhase(phaseId);
      if (REFERENCE_REPORT_TRACE_OBJS) {
        Log.write("traced: "); Log.write(iuTerminationLoopCount); Log.write(" "); Log.writeln(traceCount.peek());
      }
      if (REFERENCE_REPORT_TIME) {
        long now = VM.statistics.nanoTime();
        weakRefTimeLock.acquire();
        //	    	Log.writeln(); Log.write("ref-process: "); Log.writeln(VM.statistics.nanosToMillis(now - weakRefProcessStart));
        Log.writeln();
        Log.write("ref-process: ");
        Log.write(weakRefProcessStart);
        Log.write(" ");
        Log.writeln(now);
        weakRefTimeLock.release();
        if (maxRefProcessTime < now - weakRefProcessStart)
          maxRefProcessTime = now - weakRefProcessStart;
      }
      return;
    }

    if (phaseId == COMPLETE) {
      super.collectionPhase(phaseId);
      if (REFERENCE_REPORT_ITRATIONS) {
        Log.write(" loop: "); Log.writeln(iuTerminationLoopCount);
        if (iuTerminationLoopCount < 50)
          terminationLoopCountHist[iuTerminationLoopCount]++;
        else
          terminationLoopCountHist[50]++;
        if (maxTerminationLoopCount < iuTerminationLoopCount)
          maxTerminationLoopCount = iuTerminationLoopCount;
      }

      return;
    }

    super.collectionPhase(phaseId);
  }

  /**
   * This method controls the triggering of a GC. It is called periodically
   * during allocation. Returns true to trigger a collection.
   *
   * @param spaceFull Space request failed, must recover pages within 'space'.
   * @param space TODO
   * @return True if a collection is requested by the plan.
   */
  protected boolean collectionRequired(boolean spaceFull, Space space) {
    return super.collectionRequired(spaceFull, space);
  }

  /**
   * The processOptions method is called by the runtime immediately after command-line arguments are available. Allocation must be
   * supported prior to this point because the runtime infrastructure may require allocation in order to parse the command line
   * arguments. For this reason all plans should operate gracefully on the default minimum heap size until the point that
   * processOptions is called.
   */
  @Interruptible
  public void processOptions() {
    super.processOptions();

    if (REFERENCE_REPORT_OPTIONS) {
      if (REFERENCE_PROCESS_YUASA)   Log.writeln("options REFERENCE_PROCESS_YUASA");
      if (REFERENCE_PROCESS_STW)     Log.writeln("options REFERENCE_PROCESS_STW");
      if (REFERENCE_PROCESS_INFLOOP) Log.writeln("options REFERENCE_PROCESS_INFLOOP");

      if (REFERENCE_REPORT_TRACE_OBJS) Log.writeln("options REFERENCE_REPORT_TRACE_OBJS");
      if (REFERENCE_REPORT_NUM_REFS)   Log.writeln("options REFERENCE_REPORT_NUM_REFS");
      if (REFERENCE_REPORT_ITRATIONS)  Log.writeln("options REFERENCE_REPORT_ITRATIONS");
      if (REFERENCE_REPORT_USES)       Log.writeln("options REFERENCE_REPORT_USES");
      if (REFERENCE_REPORT_TIME)       Log.writeln("options REFERENCE_REPORT_TIME");
      if (REFERENCE_REPORT_OPTIONS)    Log.writeln("options REFERENCE_REPORT_OPTIONS");
    }
  }

  /**
   * Any Plan can override this to provide additional plan specific
   * timing information.
   *
   * @param totals Print totals
   */
  @Override
  protected void printDetailedTiming(boolean totals) {
    super.printDetailedTiming(totals);
    if (REFERENCE_REPORT_ITRATIONS) {
    	Log.write("max-termiantion-loop-count ");
    	Log.writeln(maxTerminationLoopCount);
      for (int i = 0; i < 51; i++) {
      	Log.write(i); Log.write(" "); Log.writeln(terminationLoopCountHist[i]);
      }
    }
    if (REFERENCE_REPORT_TIME) {
    	Log.write("max-ref-process-time: "); Log.writeln(VM.statistics.nanosToMillis(maxRefProcessTime));
    }
  }
}
