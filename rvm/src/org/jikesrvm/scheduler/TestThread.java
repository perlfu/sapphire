package org.jikesrvm.scheduler;

import static org.jikesrvm.runtime.SysCall.sysCall;

import org.jikesrvm.Callbacks;
import org.jikesrvm.CommandLineArgs;
import org.jikesrvm.Constants;
import org.jikesrvm.HeapLayoutConstants;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.runtime.SysCall;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Untraced;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

@NonMoving
public class TestThread extends Thread implements HeapLayoutConstants, SizeConstants {
  private static final long DRIFT_COMPENSATION_MAX = 10 * 1000 * 1000;
  
  /**
   * Record layout:
   *   Word header
   *   Word flags
   *   long start
   *   long end
   */
  private static final int LONGS_IN_RECORD = 2 + ((2 * BYTES_IN_WORD) / BYTES_IN_LONG);
  private static final int BYTES_IN_RECORD = LONGS_IN_RECORD * BYTES_IN_LONG;
  private static final int MAX_DATA_BYTES = 32 * 1024 * 1024;
  
  private static final int TEST_RECORD      = 1;
  private static final int SKEW_RECORD      = 2;
  private static final int GC_PHASE_RECORD  = 3;
  private static final int MARK_RECORD      = 4;
  
  private static final int TEST_FLAG_START_GC_SHIFT       = 0;
  private static final int TEST_FLAG_START_GC             = 1 << TEST_FLAG_START_GC_SHIFT;
  private static final int TEST_FLAG_END_GC_SHIFT         = 1;
  private static final int TEST_FLAG_END_GC               = 1 << TEST_FLAG_END_GC_SHIFT;
  private static final int TEST_THREAD_COUNT_SHIFT        = 16;
  
  private static final int GC_FLAG_BEFORE_SHIFT           = 16;
  private static final int GC_FLAG_BEFORE                 = 1 << GC_FLAG_BEFORE_SHIFT;
  private static final int GC_FLAG_USER_SHIFT             = 17;
  private static final int GC_FLAG_USER                   = 1 << GC_FLAG_USER_SHIFT;
  private static final int GC_FLAG_OTF_SHIFT              = 18;
  private static final int GC_FLAG_OTF                    = 1 << GC_FLAG_OTF_SHIFT;
  
  private static final int GC_PHASE_GLOBAL                = 1;
  private static final int GC_PHASE_SPECIAL_GLOBAL        = 2;
  private static final int GC_PHASE_YIELD                 = 3;
  private static final int GC_PHASE_COLLECTOR             = 4;
  private static final int GC_PHASE_MUTATOR               = 5;
  private static final int GC_PHASE_OTF_MUTATOR           = 6;
  private static final int GC_PHASE_OTF_MUTATOR_REQUEST   = 7;
  
  private static final int MARK_START                     = 1;
  private static final int MARK_END                       = 2;
  
  private static String testClassArg = null;
  private static long testPeriodArg = 100 * 1000 * 1000;
  private static int priorityArg = 0;
  private static boolean hasPriorityArg = false;
  private static String prefixArg = "";
  private static boolean busyWaitArg = false;
  private static boolean noWriteArg = false;
  private static final boolean useContextSwitchData = true;
  
  /** array containing of records (each a multiple of longs) */
  @Untraced
  private static Address logData = Address.zero();
  
  /** shutdown flag */
  private static volatile boolean shutdown = false;
  
  private final String testClass;
  private final long testPeriod;
  private final boolean shouldBusyWait;
  private final int[] usageBuffer = new int[SysCall.RUSAGE_STRUCT_SIZE + 4];
  private RVMMethod testMethod;
  
  public TestThread() {
    super("TestThread");
    setDaemon(true);
    testClass = testClassArg;
    testPeriod = testPeriodArg;
    testMethod = null;
    shouldBusyWait = busyWaitArg;
  }
  
  public static final boolean hasPriority() {
    return hasPriorityArg;
  }
  
  public static final int relatvePriority() {
    return priorityArg;
  }
  
  public static Address firstRecordAddress() {
    return logData.plus(BYTES_IN_RECORD);
  }
  
  @Inline
  private static Address getLogPointer() {
    return logData.loadAddress();
  }
  
  @Uninterruptible
  @Inline
  private static Address incrementLogPointer() {
    Address old;
    
    do {
      old = logData.prepareAddress();
    } while (!logData.attempt(old, old.plus(BYTES_IN_RECORD)));
    
    if (VM.VerifyAssertions) {
      VM._assert(old.LT(logData.plus(Offset.fromIntZeroExtend(MAX_DATA_BYTES))));
    }
    
    return old;
  }
  
  @Inline
  @Uninterruptible
  private static long nanoTime() {
    return sysCall.sysNanoTime();
  }
  
  @Inline
  private static long nanoSleep(long ns) {
    long start = nanoTime();
    sysCall.sysNanoSleep(ns);
    long end = nanoTime();
    long drift = end - (start + ns);
    return drift;
  }
  
  @Inline
  private static long busyWait(long until) {
    long now;
    
    do {
      now = nanoTime();
      // FIXME: busy stuff
    } while (now < until);
    
    return now;
  }
  
  @Uninterruptible
  @Inline
  private static void storeRecord(Word header, Word flags, long start, long end) {
    if (noWriteArg)
      return;
    
    Address record = incrementLogPointer();
    
    record.store(header, Offset.zero());
    record.store(flags, Offset.fromIntZeroExtend(1 * BYTES_IN_WORD));
    record.store(start, Offset.fromIntZeroExtend(2 * BYTES_IN_WORD));
    record.store(end, Offset.fromIntZeroExtend((2 * BYTES_IN_WORD) + BYTES_IN_LONG));
  }
  
  @Inline
  private static void storeTestRecord(boolean startGC, boolean endGC, int threadCount, long start, long end) {
    Word type = Word.fromIntZeroExtend(TEST_RECORD);
    Word startGCFlag = startGC ? Word.fromIntSignExtend(TEST_FLAG_START_GC) : Word.zero();
    Word endGCFlag = endGC ? Word.fromIntSignExtend(TEST_FLAG_END_GC) : Word.zero();
    Word threads = Word.fromIntSignExtend(threadCount).lsh(TEST_THREAD_COUNT_SHIFT);
    Word flags = startGCFlag.or(endGCFlag).or(threads);
    storeRecord(type, flags, start, end);
  }
  
  @Inline
  private static void storeSkewRecord(int skipped, long start, long end) {
    Word type = Word.fromIntZeroExtend(SKEW_RECORD);
    storeRecord(type, Word.fromIntZeroExtend(skipped), start, end);
  }
  
  @Uninterruptible
  private static void storeGCRecord(int phaseType, short phaseId, int ordinal, boolean before, boolean user, boolean otf) {
    Word type = Word.fromIntZeroExtend(GC_PHASE_RECORD);
    long now = nanoTime();
    Word flags = Word.fromIntZeroExtend(phaseType);
    long end = (long) phaseId | ((long) ordinal << 16);
    if (before)   flags = flags.or(Word.fromIntSignExtend(GC_FLAG_BEFORE));
    if (user)     flags = flags.or(Word.fromIntSignExtend(GC_FLAG_USER));
    if (otf)      flags = flags.or(Word.fromIntSignExtend(GC_FLAG_OTF));
    storeRecord(type, flags, now, end);
  }
  
  @Uninterruptible
  private static void storeMarkRecord(boolean start, boolean end) {
    Word type = Word.fromIntZeroExtend(MARK_RECORD);
    long now = nanoTime();
    Word flags = Word.zero();
    if (start)  flags = flags.or(Word.fromIntSignExtend(MARK_START));
    if (end)    flags = flags.or(Word.fromIntSignExtend(MARK_END));
    storeRecord(type, flags, now, 0);
  }
  
  @Uninterruptible
  private static void storeGCRecord(int phaseType, short phaseId, int ordinal, boolean before) {
    storeGCRecord(phaseType, phaseId, ordinal, before, false, false);
  }
  
  private int getInvoluntaryContextSwitches() {
    if (useContextSwitchData) {
      sysCall.sysGetRUsage(SysCall.RUSAGE_THREAD, usageBuffer);
      return usageBuffer[SysCall.RU_NIVCSW];
    } else {
      return 0;
    }
  }
  
  private static void printRecords() {
    if (noWriteArg)
      return;
    
    Offset offset = Offset.zero();
    Address ptr = firstRecordAddress();
    Address recordEnd = getLogPointer();
    VM.sysWriteln(prefixArg, " === Test Thread Stats");
    while (ptr.LT(recordEnd)) {
      Word header = ptr.loadWord(offset);
      Word flags = ptr.loadWord(offset.plus(BYTES_IN_WORD));
      long start = ptr.loadLong(offset.plus((2 * BYTES_IN_WORD)));
      long end = ptr.loadLong(offset.plus((2 * BYTES_IN_WORD) + BYTES_IN_LONG));
      
      VM.sysWrite(prefixArg); VM.sysWrite(" ");
      VM.sysWrite(header); VM.sysWrite(" ");
      VM.sysWrite(flags); VM.sysWrite(" ");
      VM.sysWriteHex(start); VM.sysWrite(" ");
      VM.sysWriteHex(end); 
      
      if (header.toInt() == GC_PHASE_RECORD) {
        short phaseId = (short) (end & 0xffff);
        VM.sysWrite(" ", MemoryManager.getGCPhaseName(phaseId));
      }
      
      VM.sysWriteln();
      
      ptr = ptr.plus(BYTES_IN_RECORD);
    }
    VM.sysWriteln(prefixArg, " ====");
  }
  
  @Override
  @Entrypoint
  public void run() {
    // setup
    if (testClass == null) {
      return;
    }
    if (!findTestMethod()) {
      return;
    }
    
    // allocate log data buffers
    if (!noWriteArg) {
      logData = sysCall.sysMalloc(MAX_DATA_BYTES);
      if (VM.verboseBoot >= 1) {
        VM.sysWrite("TestThread logData: ");
        VM.sysWriteln(logData);
      }
      logData.store(firstRecordAddress());
    }
    
    // report booted
    if (VM.verboseBoot >= 1) VM.sysWriteln("TestThread booted");
    
    // now we are booted register an exit callback
    Callbacks.addExitMonitor(new TestThread.ExitMonitor());
    
    // register with the debug system to get GC status
    MemoryManager.getDebugging().setTestThread(this);
    
    // main loop
    long driftCompensation = 0;
    long next = nanoTime() + testPeriod;
    for (;;) {
      long now;
      
      // only consider waiting if testPeriod set
      if (testPeriod > 0) {
        if (shouldBusyWait) {
          now = busyWait(next);
        } else {
          now = nanoTime();
          long rem = next - now;
          if ((rem - driftCompensation) > 0) {
            rem = nanoSleep(rem - driftCompensation);
      
            driftCompensation = (((driftCompensation * 2) + driftCompensation) + rem) / 4;
            if (driftCompensation < -DRIFT_COMPENSATION_MAX)
              driftCompensation = -DRIFT_COMPENSATION_MAX;
            else if (driftCompensation > DRIFT_COMPENSATION_MAX)
              driftCompensation = DRIFT_COMPENSATION_MAX;
          }
          now = nanoTime();
        }
      
        next = next + testPeriod;
        int skipped = 0;
        while (next < now) {
          next = next + testPeriod;
          skipped++;
        }
      
        if (skipped > 0) {
          storeSkewRecord(skipped, now, next);
        }
      
        if (shutdown)
          break;
      }
      
      boolean startGC = MemoryManager.gcInProgress();
      int threadCount = RVMThread.numThreads;
      int csw = getInvoluntaryContextSwitches();
      long start = nanoTime();
      invokeTest();
      now = nanoTime();
      boolean endGC = MemoryManager.gcInProgress();
      
      // only accept the result if no involuntary switches occurred
      csw = getInvoluntaryContextSwitches() - csw;
      if (csw == 0) {
        storeTestRecord(startGC, endGC, threadCount, start, now);
      } else {
        /*
        VM.sysWrite("csw:");
        VM.sysWriteln(csw);
        */
      }

      if (shutdown)
        break;
    }
  }
  
  private boolean findTestMethod() {
    // set up application class loader
    ClassLoader cl = RVMClassLoader.getApplicationClassLoader();
    setContextClassLoader(cl);

    RVMClass cls = null;
    try {
      Atom mainAtom = Atom.findOrCreateUnicodeAtom(testClass.replace('.', '/'));
      TypeReference tClass = TypeReference.findOrCreate(cl, mainAtom.descriptorFromClassName());
      cls = tClass.resolve().asClass();
      cls.resolve();
      cls.instantiate();
      cls.initialize();
    } catch (NoClassDefFoundError e) {
      VM.sysWrite(e + "\n");
      return false;
    }
    
    if (VM.verboseBoot >= 2) VM.sysWriteln(cls.toString(), " loaded");
    
    // find "test" method
    Atom methodName = Atom.findOrCreateAsciiAtom(("test"));
    Atom methodDescriptor = Atom.findOrCreateAsciiAtom(("()V"));
    testMethod = cls.findDeclaredMethod(methodName, methodDescriptor);
   
    if (testMethod == null || !testMethod.isPublic() || !testMethod.isStatic()) {
      // no such method
      VM.sysWriteln(cls + " doesn't have a \"public static void test()\" method to execute");
      return false;
    }

    if (VM.verboseBoot >= 2) VM.sysWriteln(cls.toString(), ".test() located");
    
    testMethod.compile();
    
    if (VM.verboseBoot >= 2) VM.sysWriteln(cls.toString(), ".test() compiled");
    
    return true;
  }
  
  @Inline
  private void invokeTest() {
    Reflection.invoke(testMethod, null, null, null, true);
  }
  
  /**
   * A global GC collection phase
   * @param phaseId The phase ID
   * @param before true at the start of the phase, false at the end
   */
  @Uninterruptible
  public void globalPhase(short phaseId, boolean before) {
    boolean user = MemoryManager.gcIsUserTriggered();
    boolean otf = MemoryManager.gcIsOnTheFly();
    storeGCRecord(GC_PHASE_GLOBAL, phaseId, 0, before, user, otf);
  }

  /**
   * A special global GC collection phase
   * @param phaseId The phase ID
   * @param before true at the start of the phase, false at the end
   */
  @Uninterruptible
  public void specialGlobalPhase(short phaseId, boolean before) {
    storeGCRecord(GC_PHASE_SPECIAL_GLOBAL, phaseId, 0, before);
  }

  /**
   * A per-collector GC collection phase
   * @param phaseId The phase ID
   * @param ordinal The collector ID (within this collection)
   * @param before true at the start of the phase, false at the end
   */
  @Uninterruptible
  public void collectorPhase(short phaseId, int ordinal, boolean before) {
    storeGCRecord(GC_PHASE_COLLECTOR, phaseId, ordinal, before);
  }

  /**
   * A per-mutator GC collection phase
   * @param phaseId The phase ID
   * @param ordinal The mutator ID
   * @param before true at the start of the phase, false at the end
   */
  @Uninterruptible
  public void mutatorPhase(short phaseId, int ordinal, boolean before) {
    storeGCRecord(GC_PHASE_MUTATOR, phaseId, ordinal, before);
  }
  
  /**
   * A global request for on-the-fly per-mutator GC collection phase
   * @param phaseId The phase ID
   * @param before true at the start of the phase, false at the end
   */
  @Uninterruptible
  public void requestedOnTheFlyMutatorPhase(short phaseId, boolean before) {
    storeGCRecord(GC_PHASE_OTF_MUTATOR_REQUEST, phaseId, 0, before);
  }
  
  /**
   * An on-the-fly per-mutator GC collection phase
   * @param phaseId The phase ID
   * @param ordinal The mutator ID
   * @param before true at the start of the phase, false at the end
   */
  @Uninterruptible
  public void onTheFlyMutatorPhase(short phaseId, int ordinal, boolean before) { 
    storeGCRecord(GC_PHASE_OTF_MUTATOR, phaseId, ordinal, before);
  }
  
  /**
   * Report statistics at the end of execution.
   */
  private static final class ExitMonitor implements Callbacks.ExitMonitor {
    @Override
    public void notifyExit(int value) {
      TestThread.shutdown = true;
      TestThread.printRecords();
    }
  }
  
  /** Print a short description of every option */
  @org.vmmagic.pragma.NoOptCompile
  public static void printHelp(String prefix) {
    VM.sysWrite("\nBoolean Options (<option>=true or <option>=false)\n");
    VM.sysWrite("Option             Description\n");
    VM.sysWrite("busy_wait          Busy wait between test invocations instead of sleeping\n");
    VM.sysWrite("no_write           Disable storing data records (and writing to non-heap memory)\n");
    
    VM.sysWrite("\nValue Options ("+prefix+"<option>=<value>)\n");
    VM.sysWrite("Option     Type    Description\n");
    VM.sysWrite("class      String  Class containing the test method\n");
    VM.sysWrite("period     int     Test invocation frequency in nanoseconds\n");
    VM.sysWrite("prefix     String  Prefix to place on each line of output\n");
    VM.sysWrite("priority   int     Priority of test thread relative to main thread\n");
    
    VM.sysExit(VM.EXIT_STATUS_PRINTED_HELP_MESSAGE);
  }
  
  /** Called for unrecognized command line options */
  @org.vmmagic.pragma.NoOptCompile
  private static void unrecognizedCommandLineArg(String prefix, String arg) {
    VM.sysWrite("Unrecognized test thread argument \"" + arg + "\"\n");
    VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  
  /** Process a command line option */
  @org.vmmagic.pragma.NoOptCompile
  public static void processCommandLineArg(String prefix, String arg) {
    // If arg is empty then print help
    if (arg.equals("")) {
      printHelp(prefix);
      return;
    }
    
    // Required format of arg is 'name=value'
    // Split into 'name' and 'value' strings
    int split = arg.indexOf('=');
    if (split == -1) {
      VM.sysWrite("Illegal option specification!\n  \""+arg+
                    "\" must be specified as a name-value pair in the form of option=value\n");
      //unrecognizedCommandLineArg(prefix, arg);
      return;
    }
    String name = arg.substring(0,split);
    String value = arg.substring(split+1);
    
    VM.sysWriteln("set ", name, " ", value);
    
    if (name.equals("class")) {
      testClassArg = value;
      return;
    } else if (name.equals("period")) {
      testPeriodArg = CommandLineArgs.primitiveParseInt(value);
      return;
    } else if (name.equals("prefix")) {
      prefixArg = value;
      return;
    } else if (name.equals("priority")) {
      priorityArg = CommandLineArgs.primitiveParseInt(value);
      hasPriorityArg = true;
      return;
    } else if (name.equals("busy_wait")) {
      if (value.equals("true")) {
        busyWaitArg = true;
        return;
      } else if (value.equals("false")){
        busyWaitArg = false;
        return;
      }
    } else if (name.equals("no_write")) {
      if (value.equals("true")) {
        noWriteArg = true;
        return;
      } else if (value.equals("false")){
        noWriteArg = false;
        return;
      }
    }
    
    unrecognizedCommandLineArg(prefix, name);
  }
  
  @Uninterruptible
  public static void markStart() {
    if (logData.NE(Address.zero()))
        storeMarkRecord(true, false);
  }
  
  @Uninterruptible
  public static void markEnd() {
    if (logData.NE(Address.zero()))
      storeMarkRecord(false, true);
  }
}
