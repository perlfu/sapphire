package org.mmtk.plan.otfsapphire;

import org.mmtk.plan.Trace;
import org.mmtk.policy.RawPageSpace;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.LinearScan;
import org.mmtk.vm.Lock;
import org.mmtk.vm.ObjectModel;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;

/* Abstract class for concurrent copy.
 *   This class provides
 *     - general profiling method
 *     - assertions
 *   To implement an individual copying method
 *     - Implement scan() method, which should be @NoInline for
 *       fair comparison and avoiding inefficient guarded inlining
 */
public abstract class OTFSapphireCopyScan extends LinearScan implements Constants {
  protected static final boolean PROFILE  = false;
  /* profile */
  private static int totalFailureCount;
  private static int totalCopyCount;
  private static long totalCopyBytes;
  private static long totalCopyPointers;
  private static int accumulatedFailureCount;
  private static int accumulatedCopyCount;
  private static long accumulatedCopyBytes;
  private static long accumulatedCopyPointers;
  private static int committedThreads;
  private int failureCount;
  private int copyCount;
  private long copyBytes;
  private long copyPointers;
  protected static final Lock profileLock;
  
  static {
    if (PROFILE)
      profileLock = VM.newLock("profile-lock");
    else
      profileLock = null;
  }

  protected final OTFSapphireFlipTraceLocal flipTrace;
  private String name;
  public OTFSapphireCopyScan(Trace globalTrace, String name) {
    flipTrace = new OTFSapphireFlipTraceLocal(globalTrace);
    this.name = name;
  }
  
  @Uninterruptible
  @Inline
  public final String getName() { return name; }
  
  @Uninterruptible
  @Inline
  protected final void boilerplate(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(OTFSapphire.inToSpace(object));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!ReplicatingSpace.getReplicaPointer(object).isNull());
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(OTFSapphire.inFromSpace(ReplicatingSpace.getReplicaPointer(object)));
    if (PROFILE) {
      copyBytes += VM.objectModel.getBytesWhenCopied(object);
      copyPointers += VM.objectModel.getNumberOfPointerFields(object);
      copyCount++;
    }
  }
  
  
  @Uninterruptible
  @NoInline
  protected final void fallbackCopyObject(ObjectReference fromObject, ObjectReference toObject) {
    if (PROFILE) failureCount++;
    VM.objectModel.concurrentCopyCAS2(flipTrace, fromObject, toObject);
  }

  @Uninterruptible
  public void prepare() {
    if (PROFILE) {
      totalCopyBytes = 0;
      totalFailureCount = 0;
      totalCopyCount = 0;
      totalCopyPointers = 0;
      failureCount = 0;
      copyCount = 0;
      copyBytes = 0;
      copyPointers = 0;
      committedThreads = 0;
    }
  }

  @Uninterruptible
  public void release() {
    if (PROFILE) {
      profileLock.acquire();
      totalCopyBytes += copyBytes;
      totalCopyCount += copyCount;
      totalFailureCount += failureCount;
      totalCopyPointers += copyPointers;
      if (++committedThreads >= VM.activePlan.collectorCount()) {
        Log.writeln();
        Log.write("ConcurrentCopy ");
        Log.write(" failure ");
        Log.write(totalFailureCount);
        Log.write(" / ");
        Log.writeln(totalCopyCount);
        Log.write(" copy bytes ");
        Log.write(totalCopyBytes);
        Log.write(" avg obj size ");
        if (copyCount > 0)
          Log.write(((float)totalCopyBytes)  / ((float) copyCount));
        else
          Log.write("0");
        Log.writeln();
        Log.write(" pointers ");
        Log.write(totalCopyPointers);
        Log.write(" avg pointers in obj ");
        if (copyCount > 0)
          Log.write(((float) totalCopyPointers) / ((float) copyCount));
        else
          Log.write(0);
        Log.writeln();
        
        accumulatedFailureCount += totalFailureCount;
        accumulatedCopyCount += totalCopyCount;
        accumulatedCopyBytes += totalCopyBytes;
        accumulatedCopyPointers += totalCopyPointers;
      }
      profileLock.release();
    }
  }

  @Uninterruptible
  public static int getFailureCount() {
    return accumulatedFailureCount;
  }
  
  @Uninterruptible
  public static int getCopyCount() {
    return accumulatedCopyCount;
  }
  
  @Uninterruptible
  public static long getCopyBytes() {
    return accumulatedCopyBytes;
  }
  
  @Uninterruptible
  public static long getCopyPointers() {
    return accumulatedCopyPointers;
  }
  
  @NonMoving
  public static class CopyScanCAS extends OTFSapphireCopyScan {
    public CopyScanCAS(Trace globalTrace) {
      super(globalTrace, "cas");
    }

    @Uninterruptible
    @Override
    public void scan(ObjectReference object) {
      scanCAS(object);
    }
    
    @Uninterruptible
    @Inline
    final void scanCAS(ObjectReference object) {
      boilerplate(object);
      ObjectReference fromObject = ReplicatingSpace.getReplicaPointer(object);
      VM.objectModel.concurrentCopy(flipTrace, fromObject, object);
    }
  }
  
  @NonMoving
  public static class CopyScanCAS2 extends OTFSapphireCopyScan {
    public CopyScanCAS2(Trace globalTrace) {
      super(globalTrace, "cas2");
    }

    @Uninterruptible
    @Override
    public void scan(ObjectReference object) {
      scanCAS2(object);
    }
    
    @Uninterruptible
    @Inline
    final void scanCAS2(ObjectReference object) {
      boilerplate(object);
      ObjectReference fromObject = ReplicatingSpace.getReplicaPointer(object);
      VM.objectModel.concurrentCopyCAS2(flipTrace, fromObject, object);
    }
  }

  @NonMoving
  public static class CopyScanHTM extends OTFSapphireCopyScan {
    public CopyScanHTM(Trace globalTrace) {
      super(globalTrace, "HTM");
    }
    
    @Uninterruptible
    @Override
    public void scan(ObjectReference object) {
      scanHTM(object);
    }
    
    @Uninterruptible
    @Inline
    final void scanHTM(ObjectReference object) {
      boilerplate(object);
      ObjectReference fromObject = ReplicatingSpace.getReplicaPointer(object);
      int result = VM.objectModel.concurrentCopyHTM(flipTrace, fromObject, object, 2);
      if (result == ObjectModel.CONCURRENT_COPY_FAILED)
        fallbackCopyObject(fromObject, object);
    }
  }
  
  @NonMoving
  public static class CopyScanHTM2 extends OTFSapphireCopyScan {
    public CopyScanHTM2(Trace globalTrace) {
      super(globalTrace, "HTM2");
    }
    
    @Uninterruptible
    @Override
    public void scan(ObjectReference object) {
      scanHTM2(object);
    }
    
    @Uninterruptible
    @Inline
    final void scanHTM2(ObjectReference object) {
      boilerplate(object);
      ObjectReference fromObject = ReplicatingSpace.getReplicaPointer(object);
      int result = VM.objectModel.concurrentCopyHTM2(flipTrace, fromObject, object, 2);
      if (result == ObjectModel.CONCURRENT_COPY_FAILED)
        fallbackCopyObject(fromObject, object);
    }
  }
  
  @NonMoving
  public static class CopyScanMHTM extends OTFSapphireCopyScan {
    private final int targetTransactionBytes;
    private boolean active = false;
    private int remainingBytes = 0; // only valid when active == true
    
    public CopyScanMHTM(Trace globalTrace, int targetTransactionBytes) {
      super(globalTrace, "MHTM");
      this.targetTransactionBytes = targetTransactionBytes;
    }
    
    @Uninterruptible
    @Override
    public void scan(ObjectReference object) {
      scanMHTM(object);
    }
    
    @Uninterruptible
    @Inline
    final void scanMHTM(ObjectReference object) {
      boilerplate(object);
      
      ObjectReference fromObject = ReplicatingSpace.getReplicaPointer(object);
      int copyBytes = VM.objectModel.getBytesWhenCopied(object);
      
      if ((!active) || (remainingBytes < copyBytes)) {
        if (active) {
          VM.objectModel.concurrentCopyHTMEnd();
          active = false;
        }
        
        int result = VM.objectModel.concurrentCopyHTMBegin();
        
        if (result != ObjectModel.CONCURRENT_COPY_FAILED) {
          active = true;
          remainingBytes = targetTransactionBytes;
        } else {
          // returned from transaction failure; use fallback
        }
      }
      
      if (active) {
        VM.objectModel.concurrentCopyUnsafe2(flipTrace, fromObject, object);
        remainingBytes -= copyBytes;
      } else {
        fallbackCopyObject(fromObject, object);
      }
    }
    
    @Uninterruptible
    @Override
    @Inline
    public void startScanSeries() {
      active = false;
    }
    
    @Uninterruptible
    @Override
    @Inline
    public void endScanSeries() {
      if (active) {
        VM.objectModel.concurrentCopyHTMEnd();
        active = false;
      }
    }
  }

  @NonMoving
  public static class CopyScanSTM extends OTFSapphireCopyScan {
    protected static final int BUFFER_PAGES = 1;
    private final RawPageSpace rps;
    protected Address buf;
        
    public CopyScanSTM(Trace globalTrace, RawPageSpace rps) {
      this(globalTrace, "STM", rps);
    }
    
    protected CopyScanSTM(Trace globalTrace, String name, RawPageSpace rps) {
      super(globalTrace, name);
      this.rps = rps;
    }
    
    @Uninterruptible
    @Override
    @NoInline
    public void scan(ObjectReference object) {
      scanSTM(object);
    }
    
    @Uninterruptible
    @Inline
    private final void scanSTM(ObjectReference object) {
      boilerplate(object);
      ObjectReference fromObject = ReplicatingSpace.getReplicaPointer(object);
      if (!copyAndVerifyObjectWithBuffer(fromObject, object, Offset.zero()))
        fallbackCopyObject(fromObject, object);
    }
    
    @Uninterruptible
    @Inline
    protected final boolean copyAndVerifyObjectWithBuffer(ObjectReference fromObject, ObjectReference toObject, Offset offset) {
      Extent bufSize = Extent.fromIntSignExtend((BUFFER_PAGES << LOG_BYTES_IN_PAGE) - offset.toInt());
      return VM.objectModel.concurrentCopySTM(flipTrace, fromObject, toObject, buf.plus(offset), bufSize);
    }

    @Uninterruptible
    @Override
    public void prepare() {
      super.prepare();
      buf = rps.acquire(BUFFER_PAGES);
    }
    
    @Uninterruptible
    @Override
    public void release() {
      super.release();
      rps.release(buf);
    }
  }

  @NonMoving
  public static class CopyScanSTMseq extends CopyScanSTM {
    public CopyScanSTMseq(Trace globalTrace, RawPageSpace rps) {
      super(globalTrace, "STMseq", rps);
    }
    
    @Uninterruptible
    @Override
    @NoInline
    public void scan(ObjectReference object) {
      scanSTMseq(object);
    }
    
    @Uninterruptible
    @Inline
    public void scanSTMseq(ObjectReference object) {
      boilerplate(object);
      ObjectReference fromObject = ReplicatingSpace.getReplicaPointer(object);
      if (!copyAndVerifyObjectWithBufferSeq(fromObject, object, Offset.zero()))
        fallbackCopyObject(fromObject, object);
    }
    
    @Uninterruptible
    @Inline
    protected final boolean copyAndVerifyObjectWithBufferSeq(ObjectReference fromObject, ObjectReference toObject, Offset offset) {
      Extent bufSize = Extent.fromIntSignExtend((BUFFER_PAGES << LOG_BYTES_IN_PAGE) - offset.toInt());
      return VM.objectModel.concurrentCopySTMSeq(flipTrace, fromObject, toObject, buf.plus(offset), bufSize);
    }
  }
  
  @NonMoving
  public static class CopyScanSTMseq2 extends CopyScanSTM {
    public CopyScanSTMseq2(Trace globalTrace, RawPageSpace rps) {
      super(globalTrace, "STMseq2", rps);
    }
    
    @Uninterruptible
    @Override
    @NoInline
    public void scan(ObjectReference object) {
      scanSTMseq2(object);
    }
    
    @Uninterruptible
    @Inline
    public void scanSTMseq2(ObjectReference object) {
      boilerplate(object);
      ObjectReference fromObject = ReplicatingSpace.getReplicaPointer(object);
      if (!copyAndVerifyObjectWithBufferSeq(fromObject, object, Offset.zero()))
        fallbackCopyObject(fromObject, object);
    }
    
    @Uninterruptible
    @Inline
    protected final boolean copyAndVerifyObjectWithBufferSeq(ObjectReference fromObject, ObjectReference toObject, Offset offset) {
      Extent bufSize = Extent.fromIntSignExtend((BUFFER_PAGES << LOG_BYTES_IN_PAGE) - offset.toInt());
      return VM.objectModel.concurrentCopySTMSeq2(flipTrace, fromObject, toObject, buf.plus(offset), bufSize);
    }
  }

  @NonMoving
  public static class CopyScanSTMseq2P extends CopyScanSTM {
    public CopyScanSTMseq2P(Trace globalTrace, RawPageSpace rps) {
      super(globalTrace, "STMseq2P", rps);
    }
    
    @Uninterruptible
    @Override
    @NoInline
    public void scan(ObjectReference object) {
      scanSTMseq2P(object);
    }
    
    @Uninterruptible
    @Inline
    public void scanSTMseq2P(ObjectReference object) {
      boilerplate(object);
      ObjectReference fromObject = ReplicatingSpace.getReplicaPointer(object);
      if (!copyAndVerifyObjectWithBufferSeq(fromObject, object, Offset.zero()))
        fallbackCopyObject(fromObject, object);
    }
    
    @Uninterruptible
    @Inline
    protected final boolean copyAndVerifyObjectWithBufferSeq(ObjectReference fromObject, ObjectReference toObject, Offset offset) {
      Extent bufSize = Extent.fromIntSignExtend((BUFFER_PAGES << LOG_BYTES_IN_PAGE) - offset.toInt());
      return VM.objectModel.concurrentCopySTMSeq2P(flipTrace, fromObject, toObject, buf.plus(offset), bufSize);
    }
  }

  @NonMoving
  public static class CopyScanSTMseq2N extends CopyScanSTM {
    public CopyScanSTMseq2N(Trace globalTrace, RawPageSpace rps) {
      super(globalTrace, "STMseq2N", rps);
    }
    
    @Uninterruptible
    @Override
    @NoInline
    public void scan(ObjectReference object) {
      scanSTMseq2N(object);
    }
    
    @Uninterruptible
    @Inline
    public void scanSTMseq2N(ObjectReference object) {
      boilerplate(object);
      ObjectReference fromObject = ReplicatingSpace.getReplicaPointer(object);
      if (!copyAndVerifyObjectWithBufferSeq(fromObject, object, Offset.zero()))
        fallbackCopyObject(fromObject, object);
    }
    
    @Uninterruptible
    @Inline
    protected final boolean copyAndVerifyObjectWithBufferSeq(ObjectReference fromObject, ObjectReference toObject, Offset offset) {
      Extent bufSize = Extent.fromIntSignExtend((BUFFER_PAGES << LOG_BYTES_IN_PAGE) - offset.toInt());
      return VM.objectModel.concurrentCopySTMSeq2N(flipTrace, fromObject, toObject, buf.plus(offset), bufSize);
    }
  }

  @NonMoving
  public static class CopyScanMSTM extends CopyScanSTM {
    private static final int MAX_OBJECTS = 32;

    private Address copiedObjects;
    private Address bufferOffsets;
    private int nCopiedObjects;
    private Offset bufUsed;
    private Extent bufSize;

    private static int totalObjectLimitReached;
    private static int totalBufferLimitReached;
    private static int committedThreads;
    private int objectLimitReached;
    private int bufferLimitReached;
    
    public CopyScanMSTM(Trace globalTrace, RawPageSpace rps) {
      super(globalTrace, "STM*", rps);
      bufSize = Extent.fromIntSignExtend((BUFFER_PAGES << LOG_BYTES_IN_PAGE) - (MAX_OBJECTS << LOG_BYTES_IN_WORD) * 2);
    }
    
    @Uninterruptible
    @Override
    @NoInline
    public void scan(ObjectReference object) {
      scanMSTM(object);
    }

    @Uninterruptible
    @Inline
    public void scanMSTM(ObjectReference object) {
      boilerplate(object);
      ObjectReference fromObject = ReplicatingSpace.getReplicaPointer(object);
      int consume;
      
      if (nCopiedObjects >= MAX_OBJECTS) {
        consume = ObjectModel.CONCURRENT_COPY_FAILED;
        if (PROFILE) objectLimitReached++;
      } else {
        consume = copyObjectWithBuffer(fromObject, object, bufUsed);
        if (PROFILE) if (consume == ObjectModel.CONCURRENT_COPY_FAILED) bufferLimitReached++;
      }
      
      if (consume == ObjectModel.CONCURRENT_COPY_FAILED) {
        commit();
        reset();
        consume = copyObjectWithBufferOOL(fromObject, object, bufUsed);
        if (consume == ObjectModel.CONCURRENT_COPY_FAILED) {
          fallbackCopyObject(fromObject, object);
          return;
        }
      }
      copiedObjects.store(object, Offset.fromIntSignExtend(nCopiedObjects << LOG_BYTES_IN_WORD));
      bufferOffsets.store(bufUsed.toInt(), Offset.fromIntSignExtend(nCopiedObjects << LOG_BYTES_IN_WORD));
      nCopiedObjects++;
      bufUsed = bufUsed.plus(consume);
    }
    
    @Uninterruptible
    @Override
    public void prepare() {
      super.prepare();
      copiedObjects = buf.plus(Extent.fromIntSignExtend((BUFFER_PAGES << LOG_BYTES_IN_PAGE) - (MAX_OBJECTS << LOG_BYTES_IN_WORD)));
      bufferOffsets = copiedObjects.minus(MAX_OBJECTS << LOG_BYTES_IN_WORD);
      if (PROFILE) {
        totalObjectLimitReached = 0;
        totalBufferLimitReached = 0;
        objectLimitReached = 0;
        bufferLimitReached = 0;
        committedThreads = 0;
      }
    }
    
    @Uninterruptible
    @Override
    public void release() {
      super.release();
      if (PROFILE) {
        profileLock.acquire();
        totalObjectLimitReached += objectLimitReached;
        totalBufferLimitReached += bufferLimitReached;
        if (++committedThreads >= VM.activePlan.collectorCount()) {
          Log.write("               objectLimit ");
          Log.write(totalObjectLimitReached);
          Log.write(" bufferLimit ");
          Log.writeln(totalBufferLimitReached);
        }
        profileLock.release();
      }
    }
    
    @Uninterruptible
    @Override
    public void startScanSeries() {
      reset();
    }
    
    @Uninterruptible
    @Override
    public void endScanSeries() {
      commit();
    }
    
    @Uninterruptible
    @Inline
    protected void reset() {
      bufUsed = Offset.zero();
      nCopiedObjects = 0;
    }
    
    @Uninterruptible
    @Inline
    protected void commit() {
      VM.memory.fence();
      for (int i = 0; i < nCopiedObjects; i++) {
        ObjectReference toObject = copiedObjects.loadObjectReference(Offset.fromIntSignExtend(i << LOG_BYTES_IN_WORD));
        ObjectReference fromObject = ReplicatingSpace.getReplicaPointer(toObject);
        Offset offset = Offset.fromIntSignExtend(bufferOffsets.loadInt(Offset.fromIntSignExtend(i << LOG_BYTES_IN_WORD)));
        if (!VM.objectModel.concurrentCopySTMSeqVerify(fromObject, toObject, buf.plus(offset))) {
          fallbackCopyObject(fromObject, toObject);
        }
      }
    }
    
    @Uninterruptible
    @Inline
    protected final int copyObjectWithBuffer(ObjectReference fromObject, ObjectReference toObject, Offset offset) {
      return VM.objectModel.concurrentCopySTMSeqCopy(flipTrace, fromObject, toObject, buf.plus(offset), bufSize.minus(offset.toInt()));
    }

    @Uninterruptible
    @NoInline
    protected int copyObjectWithBufferOOL(ObjectReference fromObject, ObjectReference toObject, Offset offset) {
      return VM.objectModel.concurrentCopySTMSeqCopy(flipTrace, fromObject, toObject, buf.plus(offset), bufSize.minus(offset.toInt()));
    }
  }
  
  public static class CopyScanSTW extends OTFSapphireCopyScan {
    public CopyScanSTW(Trace globalTrace) {
      super(globalTrace, "unsafe");
    }
    
    @Uninterruptible
    @Override
    @NoInline
    public void scan(ObjectReference object) {
      scanSTW(object);
    }
    
    @Uninterruptible
    @Inline
    public void scanSTW(ObjectReference object) {
      boilerplate(object);
      ObjectReference fromObject = ReplicatingSpace.getReplicaPointer(object);
      VM.objectModel.concurrentCopyUnsafe(flipTrace, fromObject, object);
    }
  }

  public static class CopyScanUnsafe2 extends OTFSapphireCopyScan {
    public CopyScanUnsafe2(Trace globalTrace) {
      super(globalTrace, "unsafe2");
    }
    
    @Uninterruptible
    @Override
    @NoInline
    public void scan(ObjectReference object) {
      scanUnsafe2(object);
    }
    
    @Uninterruptible
    @Inline
    public void scanUnsafe2(ObjectReference object) {
      boilerplate(object);
      ObjectReference fromObject = ReplicatingSpace.getReplicaPointer(object);
      VM.objectModel.concurrentCopyUnsafe2(flipTrace, fromObject, object);
    }
  }
}