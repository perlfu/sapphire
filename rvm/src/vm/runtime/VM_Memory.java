/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Low level memory management functions.
 *
 * Note that this class is "uninterruptible" - calling its methods will never 
 * cause the current thread to yield the cpu to another thread (one that
 * might cause a gc, for example).
 *
 * @author Dave Grove
 * @author Derek Lieber
 */
class VM_Memory implements VM_Uninterruptible {

  ////////////////////////
  // (1) Utilities for copying/filling/zeroing memory
  ////////////////////////
  /** 
   * How many bytes is considered large enough to justify the transition to
   * C code to use memcpy?
   */
  private static final int NATIVE_THRESHOLD = 256; 

  /**
   * Low level copy of len elements from src[srcPos] to dst[dstPos].
   * Assumptions: src != dst || (scrPos >= dstPos + 4) and
   *              src and dst are 8Bit arrays.
   * @param src     the source array
   * @param srcPos  index in the source array to begin copy
   * @param dst     the destination array
   * @param dstPos  index in the destination array to being copy
   * @param len     number of array elements to copy
   */
  static void arraycopy8Bit(Object src, int srcPos, Object dst, int dstPos, int len) throws VM_PragmaInline {
    if (len > NATIVE_THRESHOLD) {
      memcopy(VM_Magic.objectAsAddress(dst).add(dstPos), 
              VM_Magic.objectAsAddress(src).add(srcPos), 
              len);
    } else {
      if (len >= 4 && (srcPos & 0x3) == (dstPos & 0x3)) {
        // alignment is the same
        int byteStart = srcPos;
        int wordStart = (srcPos + 3) & ~0x3;
        int wordEnd = (srcPos + len) & ~0x3;
        int byteEnd = srcPos + len;
        int startDiff = wordStart - byteStart;
        int endDiff = byteEnd - wordEnd;
        int wordLen = wordEnd - wordStart;
        if (startDiff == 3) {
          VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
          VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
          VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
        } else if (startDiff == 2) {
          VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
          VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
        } else if (startDiff == 1) {
          VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
        }
        internalAligned32Copy(VM_Magic.objectAsAddress(dst).add(dstPos),
                              VM_Magic.objectAsAddress(src).add(srcPos),
                              wordLen);
        srcPos += wordLen;
        dstPos += wordLen;
        if (endDiff == 3) {
          VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
          VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
          VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
        } else if (endDiff == 2) {
          VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
          VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
        } else if (endDiff == 1) {
          VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
        }	  
      } else {
        for (int i=0; i<len; i++) {
          VM_Magic.setByteAtOffset(dst, dstPos++, VM_Magic.getByteAtOffset(src, srcPos++));
        }
      }
    }
  }    

  /**
   * Low level copy of len elements from src[srcPos] to dst[dstPos].
   * Assumption src != dst || (srcPos >= dstPos + 2).
   * 
   * @param src     the source array
   * @param srcPos  index in the source array to begin copy
   * @param dst     the destination array
   * @param dstPos  index in the destination array to being copy
   * @param len     number of array elements to copy
   */
  static void arraycopy(short[] src, int srcPos, short[] dst, int dstPos, int len) throws VM_PragmaInline {
    if (len > (NATIVE_THRESHOLD>>1)) {
      memcopy(VM_Magic.objectAsAddress(dst).add(dstPos<<1), 
              VM_Magic.objectAsAddress(src).add(srcPos<<1), 
              len<<1);
    } else {
      if (len > 1 && (srcPos & 0x1) == (dstPos & 0x1)) {
        // alignment is the same
        int byteStart = srcPos<<1;
        int wordStart = (byteStart + 3) & ~0x3;
        int wordEnd = (byteStart + (len<<1)) & ~0x3;
        int byteEnd = byteStart + (len<<1);
        int startDiff = wordStart - byteStart;
        int endDiff = byteEnd - wordEnd;
        int wordLen = wordEnd - wordStart;
        if (startDiff != 0) {
          dst[dstPos++] = src[srcPos++];
        }
        internalAligned32Copy(VM_Magic.objectAsAddress(dst).add(dstPos<<1),
                              VM_Magic.objectAsAddress(src).add(srcPos<<1),
                              wordLen);
        wordLen = wordLen >>> 1;
        srcPos += wordLen;
        dstPos += wordLen;
        if (endDiff != 0) {
          dst[dstPos++] = src[srcPos++];
        }	  
      } else {
        for (int i=0; i<len; i++) {
          dst[dstPos+i] = src[srcPos+i];
        }
      }
    }
  }    

  /**
   * Low level copy of len elements from src[srcPos] to dst[dstPos].
   * Assumption src != dst || (srcPos >= dstPos + 2).
   * 
   * @param src     the source array
   * @param srcPos  index in the source array to begin copy
   * @param dst     the destination array
   * @param dstPos  index in the destination array to being copy
   * @param len     number of array elements to copy
   */
  static void arraycopy(char[] src, int srcPos, char[] dst, int dstPos, int len) throws VM_PragmaInline {
    if (len > (NATIVE_THRESHOLD>>1)) {
      memcopy(VM_Magic.objectAsAddress(dst).add(dstPos<<1), 
              VM_Magic.objectAsAddress(src).add(srcPos<<1), 
              len<<1);
    } else {
      if (len > 1 && (srcPos & 0x1) == (dstPos & 0x1)) {
        // alignment is the same
        int byteStart = srcPos<<1;
        int wordStart = (byteStart + 3) & ~0x3;
        int wordEnd = (byteStart + (len<<1)) & ~0x3;
        int byteEnd = byteStart + (len<<1);
        int startDiff = wordStart - byteStart;
        int endDiff = byteEnd - wordEnd;
        int wordLen = wordEnd - wordStart;
        if (startDiff != 0) {
          dst[dstPos++] = src[srcPos++];
        }
        internalAligned32Copy(VM_Magic.objectAsAddress(dst).add(dstPos<<1),
                              VM_Magic.objectAsAddress(src).add(srcPos<<1),
                              wordLen);
        wordLen = wordLen >>> 1;
        srcPos += wordLen;
        dstPos += wordLen;
        if (endDiff != 0) {
          dst[dstPos++] = src[srcPos++];
        }	  
      } else {
        for (int i=0; i<len; i++) {
          dst[dstPos+i] = src[srcPos+i];
        }
      }
    }
  }    


  /**
   * Copy numbytes from src to dst.
   * Assumption either the ranges are non overlapping, or src >= dst + 4.
   * Also, src and dst are 4 byte aligned and numBytes is a multiple of 4.
   * @param dst the destination addr
   * @param src the source addr
   * @param numBytes the number of bytes top copy
   */
  static void aligned32Copy(VM_Address dst, VM_Address src, int numBytes) throws VM_PragmaInline {
    if (numBytes > NATIVE_THRESHOLD) {
      memcopy(dst, src, numBytes);
    } else {
      internalAligned32Copy(dst, src, numBytes);
    }
  }

  /**
   * Copy numbytes from src to dst.
   * Assumption either the ranges are non overlapping, or src >= dst + 4.
   * @param dst the destination addr
   * @param src the source addr
   * @param numBytes the number of bytes top copy
   */
  private static void internalAligned32Copy(VM_Address dst, VM_Address src, int numBytes) throws VM_PragmaInline {
    for (int i=0; i<numBytes; i+= 4) {
      VM_Magic.setMemoryWord(dst.add(i), VM_Magic.getMemoryWord(src.add(i)));
    }
  }


  /**
   * Copy a region of memory.
   * Taken:    destination address
   *           source address
   *           number of bytes to copy
   * Returned: nothing
   * Assumption: source and destination regions do not overlap
   */
  static void memcopy(VM_Address dst, VM_Address src, int cnt) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    VM.sysCall3(bootRecord.sysCopyIP, dst.toInt(), src.toInt(), cnt);
  }

  /**
   * Fill a region of memory.
   * Taken:    destination address
   *           pattern
   *           number of bytes to fill with pattern
   * Returned: nothing
   */
  static void fill(VM_Address dst, byte pattern, int cnt) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    VM.sysCall3(bootRecord.sysFillIP, dst.toInt(), pattern, cnt);
  }

  /**
   * Zero a region of memory.
   * Taken:    start of address range (inclusive)
   *           end of address range   (exclusive)
   * Returned: nothing
   */
  static void zero(VM_Address start, VM_Address end) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    VM.sysCall2(bootRecord.sysZeroIP, start.toInt(), end.diff(start));
  }

  // temporary different name
  static void zeroTemp(VM_Address start, int len) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    VM.sysCall2(bootRecord.sysZeroIP, start.toInt(), len);
  }

  /**
   * Zero a range of pages of memory.
   * Taken:    start address       (must be a page address)
   *           number of bytes     (must be multiple of page size)
   * Returned: nothing
   */
  static void zeroPages(VM_Address start, int len) {
    if (VM.VerifyAssertions) VM.assert(isPageAligned(start) && isPageMultiple(len));
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    VM.sysCall2(bootRecord.sysZeroPagesIP, start.toInt(), len);
  }

  ////////////////////////
  // (2) Cache management
  ////////////////////////

  /**
   * Synchronize a region of memory: force data in dcache to be written out to main 
   * memory so that it will be seen by icache when instructions are fetched back.
   * Taken:    start of address range
   *           size of address range (bytes)
   * Returned: nothing
   */
  static void sync(VM_Address address, int size) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    VM.sysCall2(bootRecord.sysSyncCacheIP, address.toInt(), size);
  }


  ////////////////////////
  // (3) MMap
  ////////////////////////

  // constants for protection and mapping calls
  //-#if RVM_FOR_LINUX
  static final int PROT_NONE  = 0;
  static final int PROT_READ  = 1;
  static final int PROT_WRITE = 2;
  static final int PROT_EXEC  = 4;

  static final int MAP_SHARED    =  1;
  static final int MAP_PRIVATE   =  2;
  static final int MAP_FIXED     = 16;
  static final int MAP_ANONYMOUS = 32;

  static final int MS_ASYNC      = 1;
  static final int MS_INVALIDATE = 2;
  static final int MS_SYNC       = 4;
  //-#endif
  //-#if RVM_FOR_AIX
  static final int PROT_NONE  = 0;
  static final int PROT_READ  = 1;
  static final int PROT_WRITE = 2;
  static final int PROT_EXEC  = 4;

  static final int MAP_SHARED    =  1;
  static final int MAP_PRIVATE   =  2;
  static final int MAP_FIXED     = 256;
  static final int MAP_ANONYMOUS = 16;

  static final int MS_ASYNC      = 16;
  static final int MS_INVALIDATE = 32;
  static final int MS_SYNC       = 64;
  //-#endif



  static boolean isPageMultiple(int val) {
    int pagesizeMask = getPagesize() - 1;
    return ((val & pagesizeMask) == 0);
  }

  static boolean isPageMultiple(long val) {
    int pagesizeMask = getPagesize() - 1;
    return ((val & ((long) pagesizeMask)) == 0);
  }

  static boolean isPageAligned(VM_Address addr) {
    return isPageMultiple(addr.toInt());
  }

  // Round size (interpreted as an unsigned int) up to the next page
  static public int roundDownPage(int size) {     
    size &= ~(getPagesize() - 1);   
    return size;
  }

  static public VM_Address roundDownPage(VM_Address addr) { 
    int temp = addr.toInt();  // might be negative - consider as unsigned
    temp &= ~(getPagesize() - 1);
    return VM_Address.fromInt(temp);
  }

  static public int roundUpPage(int size) {     // Round size up to the next page
    return roundDownPage(size + getPagesize() - 1);
  }

  static public VM_Address roundUpPage(VM_Address addr) {
    return VM_Address.fromInt(roundUpPage(addr.toInt()));
  }

  /**
   * Do mmap general memory mapping call (not implemented)
   * Taken:    start of address range (VM_Address)
   *           size of address range
   *           protection (int)
   *           flags (int)
   *           fd (int)
   *           offset (long)
   * Returned: VM_Address (of region)
   */
  static VM_Address mmap(VM_Address address, int size, 
                         int prot, int flags, int fd, long offset) {
    if (VM.VerifyAssertions)
      VM.assert(isPageAligned(address) && isPageMultiple(size) && isPageMultiple(offset));
    return VM_Address.max();  // not implemented: requires new magic for 6 args, etc.
    // VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    // return VM.sysCallXXX(bootRecord.sysMMapIP, address, size, prot, flags, fd, offset);
  }

  /**
   * Do mmap file memory mapping call
   * Taken:    start of address range (VM_Address)
   *           size of address range
   *           file name (char *)
   * Returned: VM_Address (of region)
   */
  static VM_Address mmapFile(VM_Address address, int size, int fd, int prot) {
    if (VM.VerifyAssertions)
      VM.assert(isPageAligned(address) && isPageMultiple(size));
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM_Address.fromInt(VM.sysCall4(bootRecord.sysMMapGeneralFileIP, address.toInt(), size, fd, prot));
  }

  /**
   * Do mmap non-file memory mapping call
   * Taken:    start of address range (VM_Address)
   *           size of address range 
   *           protection (int)
   *           flags (int)
   * Returned: VM_Address (of region) if successful; errno (1 to 127) otherwise
   */
  static VM_Address mmap(VM_Address address, int size, int prot, int flags) {
    if (VM.VerifyAssertions)
      VM.assert(isPageAligned(address) && isPageMultiple(size));
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    int result = VM.sysCall4(bootRecord.sysMMapNonFileIP, address.toInt(), size, prot, flags);
    return VM_Address.fromInt(result);
  }

  /**
   * Do mmap demand zero fixed address memory mapping call
   * Taken:    start of address range (VM_Address)
   *           size of address range 
   * Returned: VM_Address (of region)
   */
  static VM_Address mmap(VM_Address address, int size) {
    if (VM.VerifyAssertions)
      VM.assert(isPageAligned(address) && isPageMultiple(size));
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM_Address.fromInt(VM.sysCall2(bootRecord.sysMMapDemandZeroFixedIP, address.toInt(), size));
  }

  /**
   * Do mmap demand zero any address memory mapping call
   * Taken:    size of address range (VM_Address)
   * Returned: VM_Address (of region)
   */
  static VM_Address mmap(int size) {
    if (VM.VerifyAssertions) VM.assert(isPageMultiple(size));
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM_Address.fromInt(VM.sysCall1(bootRecord.sysMMapDemandZeroAnyIP, size));
  }

  /**
   * Do munmap system call
   * Taken:    start of address range (VM_Address)
   *           size of address range 
   * Returned: 0 if successfull; errno otherwise
   */
  static int munmap(VM_Address address, int size) {
    if (VM.VerifyAssertions)
      VM.assert(isPageAligned(address) && isPageMultiple(size));
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall2(bootRecord.sysMUnmapIP, address.toInt(), size);
  }

  /**
   * Do mprotect system call
   * Taken:    start of address range (VM_Address)
   *           size of address range 
   *           protection (int)
   * Returned: true if success
   */
  static boolean mprotect(VM_Address address, int size, int prot) {
    if (VM.VerifyAssertions)
      VM.assert(isPageAligned(address) && isPageMultiple(size));
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall3(bootRecord.sysMProtectIP, address.toInt(), size, prot) == 0;
  }

  /**
   * Do msync system call
   * Taken:    start of address range (VM_Address)
   *           size of address range 
   *           flags (int)
   * Returned: true if success
   */
  static boolean msync(VM_Address address, int size, int flags) {
    if (VM.VerifyAssertions)
      VM.assert(isPageAligned(address) && isPageMultiple(size));
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall3(bootRecord.sysMSyncIP, address.toInt(), size, flags) == 0;
  }

  /**
   * Do madvise system call (UNIMPLEMENTED IN LINUX)
   * Taken:    start of address range (VM_Address)
   *           size of address range 
   *           advice (int)
   * Returned: true if success
   */
  static boolean madvise(VM_Address address, int size, int advice) {
    if (VM.VerifyAssertions)
      VM.assert(isPageAligned(address) && isPageMultiple(size));
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall3(bootRecord.sysMAdviseIP, address.toInt(), size, advice) == 0;
  }


  //-#if RVM_FOR_AIX
  static final int SHMGET_IPC_CREAT = 1 * 512; // 0001000 Creates the data structure if it does not already exist. 
  static final int SHMGET_IPC_EXCL = 2 * 512;  // 0002000 Causes the shmget subroutine to be unsuccessful 
  //         if the IPC_CREAT flag is also set, and the data structure already exists. 
  static final int SHMGET_IRUSR = 4 * 64; // 0000400 self can read
  static final int SHMGET_IWUSR = 2 * 64; // 0000200 self can write
  static final int SHMGET_IRGRP = 4 * 8;  // 0000040 group can read
  static final int SHMGET_IWGRP = 2 * 8;  // 0000020 group can write
  static final int SHMGET_IROTH = 4;      // 0000004 others can read
  static final int SHMGET_IWOTH = 2;      // 0000002 others can write

  static final int SHMAT_MAP = 4 * 512;     // 004000 Maps a file onto the address space instead of a shared memory segment. 
  //        The SharedMemoryID parameter must specify an open file descriptor.
  static final int SHMAT_LBA = 268435456;   // 0x10000000 Specifies the low boundary address multiple of a segment. 
  static final int SHMAT_RDONLY = 1 * 4096; // 010000 Specifies read-only mode instead of the default read-write mode. 
  static final int SHMAT_RND = 2 * 4096;    // 020000 Rounds the address given by the SharedMemoryAddress parameter 
  //        to the next lower segment boundary, if necessary. 
  static final int SHMCTL_IPC_RMID = 0;    // Removes the shared memory identifier specified by the shmid.
  // There are other SHMCTL that are not included for now.
  //-#endif

  //-#if RVM_FOR_LINUX
  static final int SHMGET_IPC_CREAT  = 1 * 512;  // 01000 Create key if key does not exist
  static final int SHMGET_IPC_EXCL   = 2 * 512;  // 02000 Fail if key exists
  static final int SHMGET_IPC_NOWAIT = 4 * 512;  // 04000 Return error on wait

  static final int SHMGET_IRUSR = 4 * 64; // 0000400 self can read
  static final int SHMGET_IWUSR = 2 * 64; // 0000200 self can write
  static final int SHMGET_IRGRP = 4 * 8;  // 0000040 group can read
  static final int SHMGET_IWGRP = 2 * 8;  // 0000020 group can write
  static final int SHMGET_IROTH = 4;      // 0000004 others can read
  static final int SHMGET_IWOTH = 2;      // 0000002 others can write

  static final int SHMAT_RDONLY = 1 * 4096; // 010000 Specifies read-only mode instead of the default read-write mode. 
  static final int SHMAT_RND = 2 * 4096;    // 020000 Rounds the address given by the SharedMemoryAddress parameter 
  static final int SHMAT_REMAP = 4 * 4096;    // 040000 take-over region on attach
  // static final int SHMAT_MAP  - can't find this in linux's shm.h

  static final int SHMCTL_IPC_RMID = 0;    // Removes the shared memory identifier specified by the shmid.

  // There are other SHMCTL that are not included for now.
  //-#endif


  /**
   * Do shmget call
   * Taken:    secret key or IPC_PRIVATE
   *           size of address range
   *           segment attributes
   * Returned: shared memory segment id 
   */
  static int shmget(int key, int size, int flags) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall3(bootRecord.sysShmgetIP, key, size, flags);
  }

  /**
   * Do shmat call
   * Taken:    shmid obtained from shmget
   *           size of address range
   *           access attributes
   * Returned: address of attached shared memory segment 
   */
  static VM_Address shmat(int shmid, VM_Address addr, int flags) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    int result = VM.sysCall3(bootRecord.sysShmatIP, shmid, addr.toInt(), flags);
    return VM_Address.fromInt(result);
  }

  /**
   * Do shmdt call
   * Taken:    address of mapped region
   * Returned: shared memory segment id 
   */
  static int shmdt(VM_Address addr) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM.sysCall1(bootRecord.sysShmdtIP, addr.toInt());
  }

  /**
   * Do shmctl call
   * Taken:    shmid obtained from shmget
   *           command
   *           missing buffer argument
   * Returned: shared memory segment id 
   */
  static VM_Address shmctl(int shmid, int command) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM_Address.fromInt(VM.sysCall2(bootRecord.sysShmctlIP, shmid, command));
  }


  /**
   * Do getpagesize call
   * Taken:    none
   * Returned: page size
   */
  private static int pagesize = -1;
  private static int pagesizeLog = -1;

  static int getPagesize() {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    if (pagesize == -1) {
      pagesize = VM.sysCall0(bootRecord.sysGetPageSizeIP);
      pagesizeLog = -1;
      int temp = pagesize;
      while (temp > 0) {
        temp >>>= 1;
        pagesizeLog++;
      }
      if (VM.VerifyAssertions) VM.assert((1 << pagesizeLog) == pagesize);
    }
    return pagesize;
  }

  static int getPagesizeLog() {
    if (pagesize == -1) 
      getPagesize();
    return pagesizeLog;
  }

  static void dumpMemory(VM_Address start, int beforeBytes, int afterBytes) {

    beforeBytes = beforeBytes & ~3;
    afterBytes = (afterBytes + 3) & ~3;
    VM.sysWrite("---- Dumping memory from ");
    VM.sysWrite(start.sub(beforeBytes));
    VM.sysWrite(" to ");
    VM.sysWrite(start.add(afterBytes));
    VM.sysWrite(" ----\n");
    for (int i = beforeBytes; i < afterBytes; i += 4) {
      VM.sysWrite(i, ": ");
      VM.sysWrite(start.add(i));
      VM.sysWrite(" ");
      int value = VM_Magic.getMemoryWord(start.add(i));
      VM.sysWrite(value);
      VM.sysWrite("\n");
    }
  }

  static void dumpMemory(VM_Address start, int afterBytes) {
    dumpMemory(start, 0, afterBytes);
  }

  // test routine
  static void test_mmap() {
    int psize = VM_Memory.getPagesize();
    int size = 1024 * 1024;
    int ro = VM_Memory.PROT_READ;
    VM_Address base = VM_Address.fromInt(0x38000000);
    VM_Address addr = VM_Memory.mmap(base, size);
    VM.sysWrite("page size = ");
    VM.sysWrite(psize);
    VM.sysWrite("\n");
    VM.sysWrite("requested ");
    VM.sysWrite(size);
    VM.sysWrite(" bytes at ");
    VM.sysWrite(base);
    VM.sysWrite("\n");
    VM.sysWrite("mmap call returned ");
    VM.sysWrite(addr);
    VM.sysWrite("\n");
    if (addr.toInt() != -1) {
      VM_Magic.setMemoryWord(addr, 17);
      if (VM_Magic.getMemoryWord(addr) == 17) {
        VM.sysWrite("write and read in memory region succeeded\n");
      } else {
        VM.sysWrite("read in memory region did not return value written\n");
      }

      if (!VM_Memory.mprotect(addr, size, ro)) {
        VM.sysWrite("mprotect failed\n");
      } else {
        VM.sysWrite("mprotect succeeded!\n");
      }
      if (VM_Magic.getMemoryWord(addr) == 17) {
        VM.sysWrite("read in memory region succeeded\n");
      } else {
        VM.sysWrite("read in memory region did not return value written\n");
      }

      if (VM_Memory.munmap(addr, size) == 0) 
        VM.sysWrite("munmap succeeded!\n");
      else 
        VM.sysWrite("munmap failed\n");
    }

    addr = VM_Memory.mmap(size);
    VM.sysWrite("requested ");
    VM.sysWrite(size);
    VM.sysWrite(" bytes at any address\n");
    VM.sysWrite("mmap call returned ");
    VM.sysWrite(addr);
    VM.sysWrite("\n");

    if (addr.toInt() != -1) {
      VM_Magic.setMemoryWord(addr, 17);
      if (VM_Magic.getMemoryWord(addr) == 17) {
        VM.sysWrite("write and read in memory region succeeded\n");
      } else {
        VM.sysWrite("read in memory region did not return value written\n");
      }

      if (!VM_Memory.mprotect(addr, size, ro)) {
        VM.sysWrite("mprotect failed\n");
      } else {
        VM.sysWrite("mprotect succeeded!\n");
      }

      if (VM_Magic.getMemoryWord(addr) == 17) {
        VM.sysWrite("read in memory region succeeded\n");
      } else {
        VM.sysWrite("read in memory region did not return value written\n");
      }

      if (VM_Memory.munmap(addr, size) == 0) 
        VM.sysWrite("munmap succeeded!\n");
      else
        VM.sysWrite("munmap failed\n");
    }

    VM.sysWrite("mmap tests done\n");
  }


  static VM_Address align (VM_Address address, int alignment) throws VM_PragmaInline {
    return VM_Address.fromInt((address.toInt() + alignment - 1) & ~(alignment - 1));
  }

  // This version is here to accomodate the boot image writer
  static int align (int address, int alignment) throws VM_PragmaInline {
    return ((address + alignment - 1) & ~(alignment - 1));
  }
}
