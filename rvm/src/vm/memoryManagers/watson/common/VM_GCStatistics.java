/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.watson;

import com.ibm.JikesRVM.memoryManagers.vmInterface.*;
import com.ibm.JikesRVM.classloader.*;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_JavaHeader;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Synchronization;
import com.ibm.JikesRVM.VM_Synchronizer;
import com.ibm.JikesRVM.VM_EventLogger;
import com.ibm.JikesRVM.VM_Callbacks;
import com.ibm.JikesRVM.VM_Statistic;
import com.ibm.JikesRVM.VM_TimeStatistic;

/**
 * Contains common statistic, profiling, and debugging code
 * for the watson memory managers.
 *
 * @author Dave Grove
 * @author Perry Cheng
 */
public class VM_GCStatistics implements VM_GCConstants, VM_Callbacks.ExitMonitor, VM_Callbacks.AppRunStartMonitor {


  // Number and types of GC
  static int gcExternalCount = 0;   // number of calls from System.gc
  static int gcCount = 0;           // number of minor collections
  static int gcMajorCount = 0;      // number of major collections

  // accumulated times & counts for sysExit callback printout
  static final VM_Statistic bytesCopied = new VM_Statistic();
  static final VM_Statistic minorBytesCopied = new VM_Statistic();     
  static final VM_Statistic majorBytesCopied = bytesCopied;

  // time spend in various phases
  static final VM_TimeStatistic startTime = new VM_TimeStatistic();
  static final VM_TimeStatistic initTime = new VM_TimeStatistic();
  static final VM_TimeStatistic rootTime = new VM_TimeStatistic();
  static final VM_TimeStatistic scanTime = new VM_TimeStatistic();
  static final VM_TimeStatistic finalizeTime = new VM_TimeStatistic();
  static final VM_TimeStatistic finishTime = new VM_TimeStatistic();
  static final VM_TimeStatistic GCTime = new VM_TimeStatistic();
  static final VM_TimeStatistic minorGCTime = new VM_TimeStatistic();
  static final VM_TimeStatistic majorGCTime = GCTime;

  // collisions in obtaining object ownership to copy
  static final boolean COUNT_COLLISIONS = false;
  static int collisionCount = 0;

  // more statistics
  static final boolean COUNT_BY_TYPE     = false;
  static final boolean COUNT_ALLOCATIONS = false;

  // verify that all allocations are word size aligned
  static final boolean VERIFY_ALIGNMENT = false;

  // verify that all allocations return zero-filled storage.
  static final boolean VERIFY_ZEROED_ALLOCATIONS = false;

  static final int DEFAULT = 0;  // non-generational
  static final int MINOR = 1;
  static final int MAJOR = 2;

  private static final VM_Atom TOTALAtom = VM_Atom.findOrCreateAsciiAtom("TOTAL");

  public static void boot() throws VM_PragmaInterruptible {
    VM_Callbacks.addExitMonitor(new VM_GCStatistics());
    VM_Callbacks.addAppRunStartMonitor(new VM_GCStatistics());
  }

  /**
   * To be called when the VM is about to exit.
   * @param value the exit value
   */
  public void notifyExit(int value) {
    printSummaryStatistics();
  }

  /**
   * To be called when the application starts a run
   * @param value the exit value
   */
  public void notifyAppRunStart(String app, int value) throws VM_PragmaUninterruptible {
    if (VM_Allocator.verbose >= 1) VM.sysWrite("Clearing VM_Allocator statistics\n");
    clearSummaryStatistics();
  }

  static void updateGCStats(int GCType, int copied) throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) 
      VM._assert(copied >= 0);
    if (GCType == DEFAULT || GCType == MAJOR)
      bytesCopied.addSample(copied);
    else if (GCType == MINOR)
      minorBytesCopied.addSample(copied);
    else
      VM._assert(false);
  }

  static void printGCStats(int GCType, double beginTime, double endTime) throws VM_PragmaUninterruptible {

    if (VM_Allocator.verbose >= 2)
      printGCPhaseTimes();  	

    if (VM_Allocator.verbose >= 1 ) {
      printVerboseOutputLine(GCType, beginTime, endTime);
      if (VM_CollectorThread.MEASURE_WAIT_TIMES)
        VM_CollectorThread.printThreadWaitTimes();
      else {
        if (VM_GCWorkQueue.MEASURE_WAIT_TIMES) {
          VM.sysWrite("*** Wait Times for Scanning \n");
          VM_GCWorkQueue.printAllWaitTimes();
          VM_GCWorkQueue.saveAllWaitTimes();
          VM.sysWrite("*** Wait Times for Finalization \n");
          VM_GCWorkQueue.printAllWaitTimes();
          VM_GCWorkQueue.resetAllWaitTimes();
        }
      }

      if (VM_GCWorkQueue.WORKQUEUE_COUNTS) {
        VM.sysWrite("*** Work Queue Counts for Scanning \n");
        VM_GCWorkQueue.printAllCounters();
        VM_GCWorkQueue.saveAllCounters();
        VM.sysWrite("*** WorkQueue Counts for Finalization \n");
        VM_GCWorkQueue.printAllCounters();
        VM_GCWorkQueue.resetAllCounters();
      }

      if (VM_CollectorThread.SHOW_RENDEZVOUS_TIMES) 
        VM_CollectorThread.gcBarrier.printRendezvousTimes();
    }
  }

  private static void printGCPhaseTimes () throws VM_PragmaUninterruptible {

    // if invoked with -verbose:gc print output line for this last GC
    VM.sysWrite("<GC ", gcCount, "> ");
    VM.sysWrite("startTime ", (int)(startTime.last()*1000000.0), "(us) ");
    VM.sysWrite("init ", (int)(initTime.last()*1000000.0), "(us) ");
    VM.sysWrite("stacks & statics ", (int)(rootTime.last()*1000000.0), "(us) ");
    VM.sysWrite("scanning ", (int)(scanTime.last()*1000.0), "(ms) ");
    VM.sysWrite("finalize ", (int)(finalizeTime.last()*1000000.0), "(us) ");
    VM.sysWriteln("finish ",  (int)(finishTime.last()*1000000.0), "(us) ");
  }


  private static void printVerboseOutputLine (int GCType, double beginTime, double endTime) 
      throws VM_PragmaUninterruptible {

    int gcTimeMs = (GCType == MINOR) ? minorGCTime.lastMs() : GCTime.lastMs();
    int free = (int) VM_Allocator.allSmallFreeMemory();
    int total = (int) VM_Allocator.allSmallUsableMemory();
    double freeFraction = free / (double) total;
    int copiedKb = (int) (((GCType == MINOR) ? minorBytesCopied.last() : bytesCopied.last()) / 1024);

    if (VM_Allocator.verbose >= 1) {
	VM.sysWrite("[GC ", gcCount);
	VM.sysWrite(" start ", beginTime * 1000.0, "ms");
	VM.sysWrite(" 0KB -> 0KB  ");
	VM.sysWriteln(endTime * 1000.0, "ms]");
    }

    if (VM_Allocator.verbose >= 2) {
	VM.sysWrite("[GC   small: ", copiedKb, " Kb copied     ");
	VM.sysWrite(free / 1024, " Kb free (");
	VM.sysWrite(freeFraction * 100.0); VM.sysWrite("%)   ");
	VM.sysWrite("rate = "); VM.sysWrite(((double) copiedKb) / gcTimeMs); 
	VM.sysWriteln("(Mb/s)]");
    }

    if (COUNT_BY_TYPE)	printCountsByType();

  }

  static void clearSummaryStatistics () throws VM_PragmaUninterruptible {
    VM_ObjectModel.hashRequests = 0;
    VM_ObjectModel.hashTransition1 = 0;
    VM_ObjectModel.hashTransition2 = 0;

    VM_Processor st;
    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
      st = VM_Scheduler.processors[i];
      st.totalBytesAllocated = 0;
      st.totalObjectsAllocated = 0;
      st.synchronizedObjectsAllocated = 0;
    }
  }

  static void printSummaryStatistics () throws VM_PragmaUninterruptible {

    if (VM_ObjectModel.HASH_STATS) {
      VM.sysWriteln("Hash operations:    ", VM_ObjectModel.hashRequests);
      VM.sysWriteln("Unhashed -> Hashed: ", VM_ObjectModel.hashTransition1);
      VM.sysWriteln("Hashed   -> Moved:  ", VM_ObjectModel.hashTransition2);
    }

    int np = VM_Scheduler.numProcessors;

    // showParameter();
    if (VM_Allocator.verbose >=1) {
	VM.sysWrite("[End ");
	VM.sysWrite(1000.0 * (VM_Time.now() - VM_Allocator.bootTime));
	VM.sysWriteln("ms]");
    }
    if (VM_Allocator.verbose >=2) {
      VM.sysWriteln("\nGC Summary:  ", gcCount, " Collections");
      if (gcCount != 0) {
        if (minorGCTime.count() > 0) {
          VM.sysWrite("GC Summary:  Minor Times   ");
          VM.sysWrite("total ", minorGCTime.sumS(), " (s)    ");
          VM.sysWrite("avg ", minorGCTime.avgMs(), " (ms)    ");
          VM.sysWriteln("max ", minorGCTime.maxMs(), " (ms)    ");
        }
        if (majorGCTime.count() > 0) {
          VM.sysWrite("GC Summary:  Major Times   ");
          VM.sysWrite("total ", majorGCTime.sumS(), " (s)    ");
          VM.sysWrite("avg ", majorGCTime.avgMs(), " (ms)    ");
          VM.sysWriteln("max ", majorGCTime.maxMs(), " (ms)    ");
        }
        if (minorBytesCopied.count() > 0) {
          VM.sysWrite("GC Summary:  Minor copied  ");
          VM.sysWrite("avg ", (int) minorBytesCopied.avg() / 1024, " (Kb)    ");
          VM.sysWriteln("max ", (int) minorBytesCopied.max() / 1024, " (Kb)");
        }
        if (majorBytesCopied.count() > 0) {
          VM.sysWrite("GC Summary:  Major copied  ");
          VM.sysWrite("avg ", (int) majorBytesCopied.avg() / 1024, " (Kb)    ");
          VM.sysWriteln("max ", (int) majorBytesCopied.max() / 1024, " (Kb)");
        }
      }
    }

    if (COUNT_COLLISIONS && (gcCount>0) && (np>1)) {
      VM.sysWriteln("GC Summary:  avg number of collisions per collection = ",
                    collisionCount/gcCount);
    }

    if (VM_Allocator.verbose >= 2 && gcCount>0) {
      VM.sysWrite("Average Time in Phases of Collection:\n");
      VM.sysWrite("startTime ", startTime.avgUs(), "(us) init ");
      VM.sysWrite( initTime.avgUs(), "(us) stacks & statics ");
      VM.sysWrite( rootTime.avgUs(), "(us) scanning ");
      VM.sysWrite( scanTime.avgMs(), "(ms) finalize ");
      VM.sysWrite( finalizeTime.avgUs(), "(us) finish ");
      VM.sysWrite( finishTime.avgUs(), "(us)>\n\n");
    }

    if (VM_CollectorThread.MEASURE_WAIT_TIMES && (gcCount>0)) {
      double totalBufferWait = 0.0;
      double totalFinishWait = 0.0;
      double totalRendezvousWait = 0.0;
      int avgBufferWait=0, avgFinishWait=0, avgRendezvousWait=0;

      VM_CollectorThread ct;
      for (int i=1; i <= np; i++ ) {
        ct = VM_CollectorThread.collectorThreads[VM_Scheduler.processors[i].id];
        totalBufferWait += ct.totalBufferWait;
        totalFinishWait += ct.totalFinishWait;
        totalRendezvousWait += ct.totalRendezvousWait;
      }
      avgBufferWait = ((int)((totalBufferWait/(double)gcCount)*1000000.0))/np;
      avgFinishWait = ((int)((totalFinishWait/(double)gcCount)*1000000.0))/np;
      avgRendezvousWait = ((int)((totalRendezvousWait/(double)gcCount)*1000000.0))/np;

      VM.sysWrite("Average Wait Times For Each Collector Thread In A Collection:\n");
      VM.sysWrite("Buffer Wait ", avgBufferWait, " (us) Finish Wait ");
      VM.sysWrite( avgFinishWait, " (us) Rendezvous Wait ");
      VM.sysWrite( avgRendezvousWait, " (us)\n\n");
    }

    if (COUNT_ALLOCATIONS) {
      long bytes = 0, objects = 0, syncObjects = 0;
      VM_Processor st;
      for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
        st = VM_Scheduler.processors[i];
        bytes += st.totalBytesAllocated;
        objects += st.totalObjectsAllocated;
        syncObjects += st.synchronizedObjectsAllocated;
      }
      VM.sysWrite(" Total No. of Objects Allocated in this run ");
      VM.sysWrite(Long.toString(objects));
      VM.sysWrite("\n Total No. of Synchronized Objects Allocated in this run ");
      VM.sysWrite(Long.toString(syncObjects));
      VM.sysWrite("\n Total No. of bytes Allocated in this run ");
      VM.sysWrite(Long.toString(bytes));
      VM.sysWrite("\n");
    }

    if (COUNT_BY_TYPE)	printCountsByType();

  } // printSummaryStatistics

  private static void printBytes(int fieldWidth, int bytes) throws VM_PragmaUninterruptible {
    if (bytes > 10000) {
      VM.sysWriteField(fieldWidth - 3, bytes / 1024); 
      VM.sysWrite(" Kb");
    }
    else
      VM.sysWriteField(fieldWidth, bytes); 
  }

  private static void printCountsLine(VM_Atom descriptor, 
                                      int allocCount, int allocBytes,
                                      int copyCount, int copyBytes,
                                      int scanCount, int scanBytes) throws VM_PragmaUninterruptible {
    VM.sysWriteField(10, allocCount);
    VM.sysWrite(" ("); printBytes(9, allocBytes); VM.sysWrite(")");
    VM.sysWriteField(10, copyCount);
    VM.sysWrite(" ("); printBytes(9, copyBytes); VM.sysWrite(")");
    VM.sysWriteField(10, scanCount);
    VM.sysWrite(" ("); printBytes(9, scanBytes); VM.sysWrite(")");
    VM.sysWrite("     ");
    VM.sysWrite(descriptor);
    VM.sysWriteln();
  }

  static void printCountsByType()  throws VM_PragmaUninterruptible {

    VM.sysWriteln("  Object Demographics by type (Grouped by allocBytes: >=1Mb, >=10K, <10K)");
    VM.sysWriteln("          Alloc                  Copy                  Scan            Class     ");
    VM.sysWriteln("     count       bytes     count      bytes      count       bytes               ");
    VM.sysWriteln("--------------------------------------------------------------------------------------------");
    int  allocCount = 0, allocBytes = 0;
    int  copyCount = 0, copyBytes = 0;
    int  scanCount = 0, scanBytes = 0;
    int  maxId = VM_Type.numTypes();
    for (int i = 1; i < maxId; i++) {
      VM_Type type = VM_Type.getType(i);
      allocCount += type.allocCount;
      allocBytes += type.allocBytes;
      copyCount += type.copyCount;
      copyBytes += type.copyBytes;
      scanCount += type.scanCount;
      scanBytes += type.scanBytes;
    }
    for (int i = 1; i < maxId; i++) {
      VM_Type type = VM_Type.getType(i);
      if (type.allocBytes >= 1024 * 1024)
        printCountsLine(type.getDescriptor(),
                        type.allocCount, type.allocBytes,
                        type.copyCount, type.copyBytes,
                        type.scanCount, type.scanBytes);
    }
    for (int i = 1; i < maxId; i++) {
      VM_Type type = VM_Type.getType(i);
      if (type.allocBytes >= 10 * 1024 && type.allocBytes < 1024 * 1024)
        printCountsLine(type.getDescriptor(),
                        type.allocCount, type.allocBytes,
                        type.copyCount, type.copyBytes,
                        type.scanCount, type.scanBytes);
    }
    for (int i = 1; i < maxId; i++) {
      VM_Type type = VM_Type.getType(i);
      if (type.allocBytes < 1024 && type.allocBytes > 0)
        printCountsLine(type.getDescriptor(),
                        type.allocCount, type.allocBytes,
                        type.copyCount, type.copyBytes,
                        type.scanCount, type.scanBytes);
    }
    VM.sysWriteln();
    printCountsLine(TOTALAtom,
                    allocCount, allocBytes,
                    copyCount, copyBytes,
                    scanCount, scanBytes);
  }


  public static void printclass(VM_Address ref) throws VM_PragmaUninterruptible {
    VM_Type  type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));
    VM.sysWrite(type.getDescriptor());
  }


  static void profileCopy(Object obj, int size, Object[] tib) throws VM_PragmaInline, VM_PragmaUninterruptible { 
    if (COUNT_BY_TYPE) {
      VM_Type t = VM_Magic.objectAsType(tib[0]);
      t.copyCount++;
      t.copyBytes += size;
    }
  }

  static void profileScan(Object obj, int size, Object[] tib) throws VM_PragmaInline, VM_PragmaUninterruptible {
    if (COUNT_BY_TYPE) {
      VM_Type t = VM_Magic.objectAsType(tib[0]);
      t.scanCount++;
      t.scanBytes += size;
    }
  }

  static void profileAlloc (VM_Address addr, int size, Object[] tib) throws VM_PragmaUninterruptible {
    if (COUNT_BY_TYPE) {
      VM_Type t = VM_Magic.objectAsType(tib[0]);
      t.allocCount++;
      t.allocBytes += size;
    }

    if (COUNT_ALLOCATIONS) {
      VM_Processor st = VM_Processor.getCurrentProcessor();
      st.totalBytesAllocated += size;
      st.totalObjectsAllocated++;
      VM_Type t = VM_Magic.objectAsType(tib[0]);
      if (t.getThinLockOffset() != -1) {
        st.synchronizedObjectsAllocated++;
      }
    }

    if (VERIFY_ALIGNMENT) {
      if ((size & ~(WORDSIZE - 1)) != size ||
          VM_Memory.align(addr, WORDSIZE).NE(addr)) {
        VM.sysWrite("Non word size aligned region allocated ");
        VM.sysWrite("size is ", size);
        VM.sysWriteln(" address is ", addr.toInt());
        VM.sysFail("...exiting VM");
      }
    }

    if (VERIFY_ZEROED_ALLOCATIONS) {
      for (int i=0; i<size; i+= 4) {
        int val = VM_Magic.getMemoryInt(addr.add(i));
        if (val != 0) {
          VM.sysWrite("Non-zeroed memory allocated ");
          VM.sysWriteln("\taddress is ",addr.toInt());
          VM.sysWriteln("\tnon-zero address is ", addr.add(i).toInt());
          VM.sysWriteln("\tvalue is ", val);
          VM.sysFail("...exiting VM");
        }
      }
    }
  }  // profileAlloc
}
