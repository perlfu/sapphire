/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Low level memory management functions.
 *
 * Note that this class is "uninterruptible" - calling its methods will never 
 * cause the current thread to yield the cpu to another thread (one that
 * might cause a gc, for example).
 *
 * @author Dave Grove
 * @author Derek Lieber
 * @author Kris Venstermans
 */
public class VM_Memory implements VM_Uninterruptible , VM_SizeConstants {

  ////////////////////////
  // (1) Utilities for copying/filling/zeroing memory
  ////////////////////////

  /** 
   * How many bytes is considered large enough to justify the transition to
   * C code to use memcpy?
   */
  private static final int NATIVE_THRESHOLD = 512; 

  private static final boolean USE_NATIVE = true;
  
  /**
   * Low level copy of len elements from src[srcPos] to dst[dstPos].
   *
   * Assumptions: <code> src != dst || (scrPos >= dstPos + 4) </code>
   *              and src and dst are 8Bit arrays.
   * @param src     the source array
   * @param srcPos  index in the source array to begin copy
   * @param dst     the destination array
   * @param dstPos  index in the destination array to being copy
   * @param len     number of array elements to copy
   */
  public static void arraycopy8Bit(Object src, int srcPos, Object dst, int dstPos, int len) throws VM_PragmaInline {
    if (USE_NATIVE && len > NATIVE_THRESHOLD) {
      memcopy(VM_Magic.objectAsAddress(dst).add(dstPos), 
              VM_Magic.objectAsAddress(src).add(srcPos), 
              len);
    } else {
      if (len >= BYTES_IN_ADDRESS && (srcPos & (BYTES_IN_ADDRESS - 1)) == (dstPos & (BYTES_IN_ADDRESS - 1))) {
        // relative alignment is the same
        int byteStart = srcPos;
        int wordStart = alignUp(srcPos, BYTES_IN_ADDRESS);
        int wordEnd = alignDown(srcPos + len, BYTES_IN_ADDRESS);
        int byteEnd = srcPos + len;
        int startDiff = wordStart - byteStart;
        int endDiff = byteEnd - wordEnd;
        int wordLen = wordEnd - wordStart;
        VM_Address srcPtr = VM_Magic.objectAsAddress(src).add(srcPos+startDiff);
        VM_Address dstPtr = VM_Magic.objectAsAddress(dst).add(dstPos+startDiff);

        switch(startDiff) {
        //-#if RVM_FOR_64_ADDR
        case 7:
          VM_Magic.setByteAtOffset(VM_Magic.addressAsObject(dstPtr), -7,
                                   VM_Magic.getByteAtOffset(VM_Magic.addressAsObject(srcPtr), -7));
        case 6:
          VM_Magic.setByteAtOffset(VM_Magic.addressAsObject(dstPtr), -6,
                                   VM_Magic.getByteAtOffset(VM_Magic.addressAsObject(srcPtr), -6));
        case 5:
          VM_Magic.setByteAtOffset(VM_Magic.addressAsObject(dstPtr), -5,
                                   VM_Magic.getByteAtOffset(VM_Magic.addressAsObject(srcPtr), -5));
        case 4:
          VM_Magic.setByteAtOffset(VM_Magic.addressAsObject(dstPtr), -4,
                                   VM_Magic.getByteAtOffset(VM_Magic.addressAsObject(srcPtr), -4));
        //-#endif
        case 3:
          VM_Magic.setByteAtOffset(VM_Magic.addressAsObject(dstPtr), -3,
                                   VM_Magic.getByteAtOffset(VM_Magic.addressAsObject(srcPtr), -3));
        case 2:
          VM_Magic.setByteAtOffset(VM_Magic.addressAsObject(dstPtr), -2,
                                   VM_Magic.getByteAtOffset(VM_Magic.addressAsObject(srcPtr), -2));
        case 1:
          VM_Magic.setByteAtOffset(VM_Magic.addressAsObject(dstPtr), -1,
                                   VM_Magic.getByteAtOffset(VM_Magic.addressAsObject(srcPtr), -1));
        }
        
        VM_Address endPtr = srcPtr.add(wordLen);
        while (srcPtr.LT(endPtr)) {
          VM_Magic.setMemoryWord(dstPtr, VM_Magic.getMemoryWord(srcPtr));
          srcPtr = srcPtr.add(BYTES_IN_ADDRESS);
          dstPtr = dstPtr.add(BYTES_IN_ADDRESS);
        }

        switch(endDiff) {
        //-#if RVM_FOR_64_ADDR
        case 7:
          VM_Magic.setByteAtOffset(VM_Magic.addressAsObject(dstPtr), 6,
                                   VM_Magic.getByteAtOffset(VM_Magic.addressAsObject(srcPtr), 6));
        case 6:
          VM_Magic.setByteAtOffset(VM_Magic.addressAsObject(dstPtr), 5,
                                   VM_Magic.getByteAtOffset(VM_Magic.addressAsObject(srcPtr), 5));
        case 5:
          VM_Magic.setByteAtOffset(VM_Magic.addressAsObject(dstPtr), 4,
                                   VM_Magic.getByteAtOffset(VM_Magic.addressAsObject(srcPtr), 4));
        case 4:
          VM_Magic.setByteAtOffset(VM_Magic.addressAsObject(dstPtr), 3,
                                   VM_Magic.getByteAtOffset(VM_Magic.addressAsObject(srcPtr), 3));
        //-#endif
        case 3:
          VM_Magic.setByteAtOffset(VM_Magic.addressAsObject(dstPtr), 2,
                                   VM_Magic.getByteAtOffset(VM_Magic.addressAsObject(srcPtr), 2));
        case 2:
          VM_Magic.setByteAtOffset(VM_Magic.addressAsObject(dstPtr), 1,
                                   VM_Magic.getByteAtOffset(VM_Magic.addressAsObject(srcPtr), 1));
        case 1:
          VM_Magic.setByteAtOffset(VM_Magic.addressAsObject(dstPtr), 0,
                                   VM_Magic.getByteAtOffset(VM_Magic.addressAsObject(srcPtr), 0));
        }

      } else {
        VM_Address srcPtr = VM_Magic.objectAsAddress(src).add(srcPos);
        VM_Address dstPtr = VM_Magic.objectAsAddress(dst).add(dstPos);
        VM_Address endPtr = srcPtr.add(len);
        while (srcPtr.LT(endPtr)) {
          VM_Magic.setByteAtOffset(VM_Magic.addressAsObject(dstPtr), 0,
                                   VM_Magic.getByteAtOffset(VM_Magic.addressAsObject(srcPtr), 0));
          srcPtr = srcPtr.add(1);
          dstPtr = dstPtr.add(1);
        }
      }
    }
  }

  /**
   * Low level copy of len elements from src[srcPos] to dst[dstPos].
   *
   * Assumption src != dst || (srcPos >= dstPos + 2).
   * 
   * @param src     the source array
   * @param srcPos  index in the source array to begin copy
   * @param dst     the destination array
   * @param dstPos  index in the destination array to being copy
   * @param len     number of array elements to copy
   */
  public static void arraycopy16Bit(Object src, int srcPos, Object dst, int dstPos, int len) throws VM_PragmaInline {
    if (USE_NATIVE && len > (NATIVE_THRESHOLD >> LOG_BYTES_IN_SHORT)) {
      memcopy(VM_Magic.objectAsAddress(dst).add(dstPos<<LOG_BYTES_IN_SHORT), 
              VM_Magic.objectAsAddress(src).add(srcPos<<LOG_BYTES_IN_SHORT),
              len<<LOG_BYTES_IN_SHORT);
    } else {
      if (len >= (BYTES_IN_ADDRESS >>> LOG_BYTES_IN_SHORT) && (srcPos & ((BYTES_IN_ADDRESS - 1) >>> LOG_BYTES_IN_SHORT)) == (dstPos & ((BYTES_IN_ADDRESS - 1) >>> LOG_BYTES_IN_SHORT))) {
        // relative alignment is the same
        int byteStart = srcPos<<LOG_BYTES_IN_SHORT;
        int wordStart = alignUp(byteStart , BYTES_IN_ADDRESS);
        int wordEnd = alignDown(byteStart + (len<<LOG_BYTES_IN_SHORT),BYTES_IN_ADDRESS ) ;
        int byteEnd = byteStart + (len<<LOG_BYTES_IN_SHORT);
        int startDiff = wordStart - byteStart;
        int endDiff = byteEnd - wordEnd;
        int wordLen = wordEnd - wordStart;
        VM_Address srcPtr = VM_Magic.objectAsAddress(src).add((srcPos<<LOG_BYTES_IN_SHORT)+startDiff);
        VM_Address dstPtr = VM_Magic.objectAsAddress(dst).add((dstPos<<LOG_BYTES_IN_SHORT)+startDiff);

        switch(startDiff) {
        //-#if RVM_FOR_64_ADDR
        case 6:
          VM_Magic.setCharAtOffset(VM_Magic.addressAsObject(dstPtr), -6,
                                   VM_Magic.getCharAtOffset(VM_Magic.addressAsObject(srcPtr), -6));
        case 4:
          VM_Magic.setCharAtOffset(VM_Magic.addressAsObject(dstPtr), -4,
                                   VM_Magic.getCharAtOffset(VM_Magic.addressAsObject(srcPtr), -4));
        //-#endif
        case 2:
          VM_Magic.setCharAtOffset(VM_Magic.addressAsObject(dstPtr), -2,
                                   VM_Magic.getCharAtOffset(VM_Magic.addressAsObject(srcPtr), -2));
        }
        
        VM_Address endPtr = srcPtr.add(wordLen);
        while (srcPtr.LT(endPtr)) {
          VM_Magic.setMemoryWord(dstPtr, VM_Magic.getMemoryWord(srcPtr));
          srcPtr = srcPtr.add(BYTES_IN_ADDRESS);
          dstPtr = dstPtr.add(BYTES_IN_ADDRESS);
        }

        switch(endDiff) {
        //-#if RVM_FOR_64_ADDR
        case 6:
          VM_Magic.setCharAtOffset(VM_Magic.addressAsObject(dstPtr), 4,
                                   VM_Magic.getCharAtOffset(VM_Magic.addressAsObject(srcPtr), 4));
        case 4:
          VM_Magic.setCharAtOffset(VM_Magic.addressAsObject(dstPtr), 2,
                                   VM_Magic.getCharAtOffset(VM_Magic.addressAsObject(srcPtr), 2));
        //-#endif
        case 2:
          VM_Magic.setCharAtOffset(VM_Magic.addressAsObject(dstPtr), 0,
                                   VM_Magic.getCharAtOffset(VM_Magic.addressAsObject(srcPtr), 0));
        }

      } else {
        VM_Address srcPtr = VM_Magic.objectAsAddress(src).add(srcPos<<LOG_BYTES_IN_CHAR);
        VM_Address dstPtr = VM_Magic.objectAsAddress(dst).add(dstPos<<LOG_BYTES_IN_CHAR);
        VM_Address endPtr = srcPtr.add(len<<LOG_BYTES_IN_CHAR);
        while (srcPtr.LT(endPtr)) {
          VM_Magic.setCharAtOffset(VM_Magic.addressAsObject(dstPtr), 0,
                                   VM_Magic.getCharAtOffset(VM_Magic.addressAsObject(srcPtr), 0));
          srcPtr = srcPtr.add(2);
          dstPtr = dstPtr.add(2);
        }
      }
    }
  }    

  /**
   * Low level copy of len elements from src[srcPos] to dst[dstPos].
   *
   * Assumption src != dst || (srcPos >= dstPos) and element size is 4 bytes.
   * 
   * @param src     the source array
   * @param srcPos  index in the source array to begin copy
   * @param dst     the destination array
   * @param dstPos  index in the destination array to being copy
   * @param len     number of array elements to copy
   */
  public static void arraycopy32Bit(Object src, int srcIdx, Object dst, int dstIdx, int len) throws VM_PragmaInline {
    VM_Address srcPtr = VM_Magic.objectAsAddress(src).add(srcIdx<<LOG_BYTES_IN_INT);
    VM_Address dstPtr = VM_Magic.objectAsAddress(dst).add(dstIdx<<LOG_BYTES_IN_INT);
    int copyBytes = len<<LOG_BYTES_IN_INT;
    if (USE_NATIVE && len > (NATIVE_THRESHOLD >> LOG_BYTES_IN_INT)) {
      memcopy(dstPtr, srcPtr, copyBytes);
    } else {
      // The elements of int[] and float[] are always 32 bit aligned
      // therefore we can do 32 bit load/stores without worrying about alignment.
      // TODO: do measurements to determine if on PPC it is a good idea to check
      //       for compatible doubleword alignment and handle that case via the FPRs in 64 bit chunks.
      //       Unclear if this will be a big enough win to justify checking because for big copies
      //       we are going into memcopy anyways and that will be faster than anything we do here.
      VM_Address endPtr = srcPtr.add(copyBytes);
      while (srcPtr.LT(endPtr)) {
        VM_Magic.setIntAtOffset(VM_Magic.addressAsObject(dstPtr), 0,
                                VM_Magic.getIntAtOffset(VM_Magic.addressAsObject(srcPtr), 0));
        srcPtr = srcPtr.add(4);
        dstPtr = dstPtr.add(4);
      }
    }
  }    
  
  /**
   * Low level copy of len elements from src[srcPos] to dst[dstPos].
   *
   * Assumption src != dst || (srcPos >= dstPos) and element size is 8 bytes.
   * 
   * @param src     the source array
   * @param srcPos  index in the source array to begin copy
   * @param dst     the destination array
   * @param dstPos  index in the destination array to being copy
   * @param len     number of array elements to copy
   */
  public static void arraycopy64Bit(Object src, int srcIdx, Object dst, int dstIdx, int len) throws VM_PragmaInline {
    VM_Address srcPtr = VM_Magic.objectAsAddress(src).add(srcIdx<<LOG_BYTES_IN_DOUBLE);
    VM_Address dstPtr = VM_Magic.objectAsAddress(dst).add(dstIdx<<LOG_BYTES_IN_DOUBLE);
    int copyBytes = len<<LOG_BYTES_IN_DOUBLE;
    if (USE_NATIVE && len > (NATIVE_THRESHOLD >> LOG_BYTES_IN_DOUBLE)) {
      memcopy(dstPtr, srcPtr, copyBytes);
    } else {
      // The elements of long[] and double[] are always doubleword aligned
      // therefore we can do 64 bit load/stores without worrying about alignment.
      VM_Address endPtr = srcPtr.add(copyBytes);
      while (srcPtr.LT(endPtr)) {
        // We generate abysmal code on IA32 if we try to use the FP registers,
        // so use the gprs instead even though it results in more instructions.
        if (VM.BuildForIA32) {
          VM_Magic.setIntAtOffset(VM_Magic.addressAsObject(dstPtr), 0,
                                  VM_Magic.getIntAtOffset(VM_Magic.addressAsObject(srcPtr), 0));
          VM_Magic.setIntAtOffset(VM_Magic.addressAsObject(dstPtr), 4,
                                  VM_Magic.getIntAtOffset(VM_Magic.addressAsObject(srcPtr), 4));
        } else {          
          VM_Magic.setDoubleAtOffset(VM_Magic.addressAsObject(dstPtr), 0,
                                     VM_Magic.getDoubleAtOffset(VM_Magic.addressAsObject(srcPtr), 0));
        }
        srcPtr = srcPtr.add(8);
        dstPtr = dstPtr.add(8);
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
  public static void aligned32Copy(VM_Address dst, VM_Address src, int numBytes) throws VM_PragmaInline {
    if (USE_NATIVE && numBytes > NATIVE_THRESHOLD) {
      memcopy(dst, src, numBytes);
    } else {
      if (VM.BuildFor64Addr) {
        VM_Word wordMask = VM_Word.one().lsh(LOG_BYTES_IN_ADDRESS).sub(VM_Word.one());
        int srcAlignment = src.toWord().and(wordMask).toInt();
        if (srcAlignment == dst.toWord().and(wordMask).toInt()) {
          int i = 0;
          if (srcAlignment == BYTES_IN_INT) { 
            VM_Magic.setMemoryInt(dst.add(i), VM_Magic.getMemoryInt(src.add(i)));
            i += BYTES_IN_INT;
          }
          int endAlignment =( numBytes + srcAlignment) % BYTES_IN_ADDRESS;
          numBytes -= endAlignment;
          for (; i<numBytes; i+= BYTES_IN_ADDRESS) {
            VM_Magic.setMemoryWord(dst.add(i), VM_Magic.getMemoryWord(src.add(i)));
          }
          if (endAlignment != 0) { 
            VM_Magic.setMemoryInt(dst.add(i), VM_Magic.getMemoryInt(src.add(i)));
          }
        return;
        }
      } 
      //normal case: 32 bit or (64 bit not aligned)
      for (int i=0; i<numBytes; i+= BYTES_IN_INT) {
        VM_Magic.setMemoryInt(dst.add(i), VM_Magic.getMemoryInt(src.add(i)));
      }
    }
  }

  public static void aligned32Copy(VM_Address dst, VM_Address src, VM_Offset numBytes) throws VM_PragmaInline {
    aligned32Copy(dst, src, numBytes.toInt());
  }

  /**
   * Copy numbytes from src to dst.
   * Assumption either the ranges are non overlapping, or src >= dst + BYTES_IN_ADDRESS.
   * Also, src and dst are word aligned and numBytes is a multiple of BYTES_IN_ADDRESS.
   * @param dst the destination addr
   * @param src the source addr
   * @param numBytes the number of bytes top copy
   */
  public static void alignedWordCopy(VM_Address dst, VM_Address src, int numBytes) throws VM_PragmaInline {
    if (USE_NATIVE && numBytes > NATIVE_THRESHOLD) {
      memcopy(dst, src, numBytes);
    } else {
      internalAlignedWordCopy(dst, src, numBytes);
    }
  }

  public static void alignedWordCopy(VM_Address dst, VM_Address src, VM_Offset numBytes) throws VM_PragmaInline {
    alignedWordCopy(dst, src, numBytes.toInt());
  }

  /**
   * Copy numbytes from src to dst.
   * Assumption either the ranges are non overlapping, or src >= dst + BYTES_IN_ADDRESS.
   * @param dst the destination addr
   * @param src the source addr
   * @param numBytes the number of bytes top copy
   */
  private static void internalAlignedWordCopy(VM_Address dst, VM_Address src, int numBytes) throws VM_PragmaInline {
    VM_Address end = src.add(numBytes);
    while (src.LT(end)) {
      VM_Magic.setMemoryWord(dst, VM_Magic.getMemoryWord(src));
      src = src.add(BYTES_IN_ADDRESS);
      dst = dst.add(BYTES_IN_ADDRESS);
    }
  }

  /**
   * Copy a region of memory.
   * @param destination address
   * @param source address
   * @param number of bytes to copy
   * Assumption: source and destination regions do not overlap
   */
  public static void memcopy(VM_Address dst, VM_Address src, int cnt) {
    VM_SysCall.sysCopy(dst, src, cnt);
  }

  /**
   * Fill a region of memory.
   * @param destination address
   * @param pattern
   * @param number of bytes to fill with pattern
   */
  public static void fill(VM_Address dst, byte pattern, int cnt) {
    VM_SysCall.sysFill(dst, pattern, cnt);
  }

  /**
   * Zero a region of memory.
   * @param start of address range (inclusive)
   * @param end of address range   (exclusive)
   */
  public static void zero(VM_Address start, VM_Address end) {
    VM_SysCall.sysZero(start, end.diff(start).toInt());
  }

  // temporary different name
  public static void zero(VM_Address start, int len) {
    VM_SysCall.sysZero(start, len);
  }

  public static void zero(VM_Address start, VM_Extent len) {
    VM_SysCall.sysZero(start, len.toInt());
  }

  /**
   * Zero a range of pages of memory.
   * @param start address       (must be a page address)
   * @param number of bytes     (must be multiple of page size)
   */
  public static void zeroPages(VM_Address start, int len) {
    if (VM.VerifyAssertions) VM._assert(isPageAligned(start) && isPageMultiple(len));
    VM_SysCall.sysZeroPages(start, len);
  }

  ////////////////////////
  // (2) Cache management
  ////////////////////////

  /**
   * Synchronize a region of memory: force data in dcache to be written out to main 
   * memory so that it will be seen by icache when instructions are fetched back.
   * @param start of address range
   * @param size of address range (bytes)
   */
  public static void sync(VM_Address address, int size) {
    VM_SysCall.sysSyncCache(address, size);
  }


  ////////////////////////
  // (3) MMap
  ////////////////////////

  // constants for protection and mapping calls
  //-#if RVM_FOR_OSX    
  public static final int PROT_NONE  = 0;
  public static final int PROT_READ  = 1;
  public static final int PROT_WRITE = 2;
  public static final int PROT_EXEC  = 4;

  public static final int MAP_FILE      =  0;
  public static final int MAP_SHARED    =  1;
  public static final int MAP_PRIVATE   =  2;
  public static final int MAP_FIXED     = 0x0010;
  public static final int MAP_ANONYMOUS = 0x1000;

  public static final int MS_ASYNC      = 1;
  public static final int MS_INVALIDATE = 2;
  public static final int MS_SYNC       = 0;
  //-#endif
  //-#if RVM_FOR_LINUX
  public static final int PROT_NONE  = 0;
  public static final int PROT_READ  = 1;
  public static final int PROT_WRITE = 2;
  public static final int PROT_EXEC  = 4;

  public static final int MAP_FILE      =  0;
  public static final int MAP_SHARED    =  1;
  public static final int MAP_PRIVATE   =  2;
  public static final int MAP_FIXED     = 16;
  public static final int MAP_ANONYMOUS = 32;

  public static final int MS_ASYNC      = 1;
  public static final int MS_INVALIDATE = 2;
  public static final int MS_SYNC       = 4;
  //-#endif
  //-#if RVM_FOR_AIX
  public static final int PROT_NONE  = 0;
  public static final int PROT_READ  = 1;
  public static final int PROT_WRITE = 2;
  public static final int PROT_EXEC  = 4;

  public static final int MAP_FILE      =  0;
  public static final int MAP_SHARED    =  1;
  public static final int MAP_PRIVATE   =  2;
  public static final int MAP_FIXED     = 256;
  public static final int MAP_ANONYMOUS = 16;

  public static final int MS_ASYNC      = 16;
  public static final int MS_INVALIDATE = 32;
  public static final int MS_SYNC       = 64;
  //-#endif



  public static boolean isPageMultiple(int val) {
    int pagesizeMask = getPagesize() - 1;
    return ((val & pagesizeMask) == 0);
  }

  public static boolean isPageMultiple(long val) {
    int pagesizeMask = getPagesize() - 1;
    return ((val & ((long) pagesizeMask)) == 0);
  }

  public static boolean isPageMultiple(VM_Extent val) {
    VM_Word pagesizeMask = VM_Word.fromIntZeroExtend(getPagesize() - 1);
    return val.toWord().and(pagesizeMask).isZero();
  }

  public static boolean isPageAligned(VM_Address addr) {
    VM_Word pagesizeMask = VM_Word.fromIntZeroExtend(getPagesize() - 1);
    return addr.toWord().and(pagesizeMask).isZero();
  }

  // Round size (interpreted as an unsigned int) up to the next page
  public static int roundDownPage(int size) {     
    size &= ~(getPagesize() - 1);   
    return size;
  }

  public static VM_Address roundDownPage(VM_Address addr) { 
     return VM_Memory.alignDown(addr , getPagesize());
  }

  public static int roundUpPage(int size) {     // Round size up to the next page
    return roundDownPage(size + getPagesize() - 1);
  }

  public static VM_Address roundUpPage(VM_Address addr) {
    return VM_Memory.alignUp(addr, getPagesize() );
  }

  /**
   * Do mmap general memory mapping call.
   * Please consult your system's mmap system call documentation for semantics.
   * @param start of address range (VM_Address)
   * @param size of address range
   * @param protection 
   * @param flags
   * @param fd 
   * @param offset
   * @return VM_Address (of region) if successful; errno (1 to 127) otherwise
   */
  public static VM_Address mmap(VM_Address address, int size, 
                                int prot, int flags, int fd, long offset) {
    if (VM.VerifyAssertions)
      VM._assert(isPageAligned(address) && isPageMultiple(size) && isPageMultiple(offset));
    return VM_SysCall.sysMMapErrno(address,VM_Extent.fromInt(size), prot, flags, fd, offset);
  }

  /**
   * Do mmap file memory mapping call
   * @param start of address range (VM_Address)
   * @param size of address range
   * @param fd file desciptor of file to be mapped
   * @return VM_Address (of region) if successful; errno (1 to 127) otherwise
   */
  public static VM_Address mmapFile(VM_Address address, VM_Extent size, int fd, int prot) {
    if (VM.VerifyAssertions)
      VM._assert(isPageAligned(address) && isPageMultiple(size));
    int flag = MAP_FILE | MAP_FIXED | MAP_SHARED;
    return VM_SysCall.sysMMapErrno(address,size,prot,flag,fd,0);
  }

  /**
   * Do mmap non-file memory mapping call
   * @param start of address range (VM_Address)
   * @param size of address range 
   * @param protection (int)
   * @param flags (int)
   * @return VM_Address (of region) if successful; errno (1 to 127) otherwise
   */
  public static VM_Address mmap(VM_Address address, VM_Extent size, int prot, int flags) {
    if (VM.VerifyAssertions)
      VM._assert(isPageAligned(address) && isPageMultiple(size));
    return VM_SysCall.sysMMapErrno(address,size,prot,flags,-1,0);
  }

  /**
   * Do mmap demand zero fixed address memory mapping call
   * @param start of address range
   * @param size of address range 
   * @return VM_Address (of region) if successful; errno (1 to 127) otherwise
   */
  public static VM_Address mmap(VM_Address address, VM_Extent size) {
    if (VM.VerifyAssertions)
      VM._assert(isPageAligned(address) && isPageMultiple(size));
    int prot = PROT_READ | PROT_WRITE | PROT_EXEC;
    int flag = MAP_ANONYMOUS | MAP_PRIVATE | MAP_FIXED;
    return VM_SysCall.sysMMapErrno(address, size, prot, flag, -1, 0);
  }

  /**
   * Do mmap demand zero any address memory mapping call
   * @param size of address range (VM_Address)
   * @return VM_Address (of region) if successful; errno (1 to 127) otherwise 
   */
  public static VM_Address mmap(VM_Extent size) {
    if (VM.VerifyAssertions) VM._assert(isPageMultiple(size));
    int prot = PROT_READ | PROT_WRITE | PROT_EXEC;
    int flag = MAP_ANONYMOUS | MAP_PRIVATE;
    return VM_SysCall.sysMMapErrno(VM_Address.zero(), size, prot, flag, -1, 0);
  }

  /**
   * Do munmap system call
   * @param start of address range (VM_Address)
   * @param size of address range 
   * @return 0 if successfull; errno otherwise
   */
  public static int munmap(VM_Address address, VM_Extent size) {
    if (VM.VerifyAssertions)
      VM._assert(isPageAligned(address) && isPageMultiple(size));
    return VM_SysCall.sysMUnmap(address, size);
  }

  /**
   * Do mprotect system call
   * @param start of address range (VM_Address)
   * @param size of address range 
   * @param protection (int)
   * @return true iff success
   */
  public static boolean mprotect(VM_Address address, VM_Extent size, int prot) {
    if (VM.VerifyAssertions)
      VM._assert(isPageAligned(address) && isPageMultiple(size));
    return VM_SysCall.sysMProtect(address, size, prot) == 0;
  }

  /**
   * Do msync system call
   * @param of address range (VM_Address)
   * @param size of address range 
   * @param flags (int)
   * @return true iff success
   */
  public static boolean msync(VM_Address address, VM_Extent size, int flags) {
    if (VM.VerifyAssertions)
      VM._assert(isPageAligned(address) && isPageMultiple(size));
    return VM_SysCall.sysMSync(address, size, flags) == 0;
  }

  /**
   * Do madvise system call (UNIMPLEMENTED IN LINUX)
   * @param start of address range (VM_Address)
   * @param size of address range 
   * @param advice (int)
   * @return true iff success
   */
  public static boolean madvise(VM_Address address, VM_Extent size, int advice) {
    if (VM.VerifyAssertions)
      VM._assert(isPageAligned(address) && isPageMultiple(size));
    return VM_SysCall.sysMAdvise(address, size, advice) == 0;
  }


  //-#if RVM_FOR_AIX
  public static final int SHMGET_IPC_CREAT = 1 * 512; // 0001000 Creates the data structure if it does not already exist. 
  public static final int SHMGET_IPC_EXCL = 2 * 512;  // 0002000 Causes the shmget subroutine to be unsuccessful 
  //         if the IPC_CREAT flag is also set, and the data structure already exists. 
  public static final int SHMGET_IRUSR = 4 * 64; // 0000400 self can read
  public static final int SHMGET_IWUSR = 2 * 64; // 0000200 self can write
  public static final int SHMGET_IRGRP = 4 * 8;  // 0000040 group can read
  public static final int SHMGET_IWGRP = 2 * 8;  // 0000020 group can write
  public static final int SHMGET_IROTH = 4;      // 0000004 others can read
  public static final int SHMGET_IWOTH = 2;      // 0000002 others can write

  public static final int SHMAT_MAP = 4 * 512;     // 004000 Maps a file onto the address space instead of a shared memory segment. 
  //        The SharedMemoryID parameter must specify an open file descriptor.
  public static final int SHMAT_LBA = 268435456;   // 0x10000000 Specifies the low boundary address multiple of a segment. 
  public static final int SHMAT_RDONLY = 1 * 4096; // 010000 Specifies read-only mode instead of the default read-write mode. 
  public static final int SHMAT_RND = 2 * 4096;    // 020000 Rounds the address given by the SharedMemoryAddress parameter 
  //        to the next lower segment boundary, if necessary. 
  public static final int SHMCTL_IPC_RMID = 0;    // Removes the shared memory identifier specified by the shmid.
  // There are other SHMCTL that are not included for now.
  //-#endif

  //-#if RVM_FOR_LINUX || RVM_FOR_OSX
  public static final int SHMGET_IPC_CREAT  = 1 * 512;  // 01000 Create key if key does not exist
  public static final int SHMGET_IPC_EXCL   = 2 * 512;  // 02000 Fail if key exists
  public static final int SHMGET_IPC_NOWAIT = 4 * 512;  // 04000 Return error on wait

  public static final int SHMGET_IRUSR = 4 * 64; // 0000400 self can read
  public static final int SHMGET_IWUSR = 2 * 64; // 0000200 self can write
  public static final int SHMGET_IRGRP = 4 * 8;  // 0000040 group can read
  public static final int SHMGET_IWGRP = 2 * 8;  // 0000020 group can write
  public static final int SHMGET_IROTH = 4;      // 0000004 others can read
  public static final int SHMGET_IWOTH = 2;      // 0000002 others can write

  public static final int SHMAT_RDONLY = 1 * 4096; // 010000 Specifies read-only mode instead of the default read-write mode. 
  public static final int SHMAT_RND = 2 * 4096;    // 020000 Rounds the address given by the SharedMemoryAddress parameter 
  public static final int SHMAT_REMAP = 4 * 4096;    // 040000 take-over region on attach
  // public static final int SHMAT_MAP  - can't find this in linux's shm.h

  public static final int SHMCTL_IPC_RMID = 0;    // Removes the shared memory identifier specified by the shmid.

  // There are other SHMCTL that are not included for now.
  //-#endif


  /**
   * Do shmget call
   * @param secret key or IPC_PRIVATE
   * @param size of address range
   * @param segment attributes
   * @return shared memory segment id 
   */
  public static int shmget(int key, int size, int flags) {
    return VM_SysCall.sysShmget(key, size, flags);
  }

  /**
   * Do shmat call
   * @param shmid obtained from shmget
   * @param size of address range
   * @param access attributes
   * @return address of attached shared memory segment 
   */
  public static VM_Address shmat(int shmid, VM_Address addr, int flags) {
    return VM_SysCall.sysShmat(shmid, addr, flags);
  }

  /**
   * Do shmdt call
   * @param address of mapped region
   * @return shared memory segment id 
   */
  public static int shmdt(VM_Address addr) {
    return VM_SysCall.sysShmdt(addr);
  }

  /**
   * Do shmctl call
   * @param shmid obtained from shmget
   * @param command
   * @param missing buffer argument
   * @return shared memory segment id 
   */
  public int shmctl(int shmid, int command) {
    return VM_SysCall.sysShmctl(shmid, command);
  }


  private static int pagesize = -1;
  private static int pagesizeLog = -1;

  /**
   * Do getpagesize call
   * @return page size
   */
  public static int getPagesize() {
    if (pagesize == -1) {
      pagesize = VM_SysCall.sysGetPageSize();
      pagesizeLog = -1;
      int temp = pagesize;
      while (temp > 0) {
        temp >>>= 1;
        pagesizeLog++;
      }
      if (VM.VerifyAssertions) VM._assert((1 << pagesizeLog) == pagesize);
    }
    return pagesize;
  }

  static int getPagesizeLog() {
    if (pagesize == -1) 
      getPagesize();
    return pagesizeLog;
  }

  public static void dumpMemory(VM_Address start, int beforeBytes, int afterBytes) {

    beforeBytes = alignDown(beforeBytes , BYTES_IN_ADDRESS );
    afterBytes = alignUp(afterBytes , BYTES_IN_ADDRESS ) ;
    VM.sysWrite("---- Dumping memory from ");
    VM.sysWrite(start.sub(beforeBytes));
    VM.sysWrite(" to ");
    VM.sysWrite(start.add(afterBytes));
    VM.sysWrite(" ----\n");
    for (int i = -beforeBytes; i < afterBytes; i +=BYTES_IN_ADDRESS ) {
      VM.sysWrite(i, ": ");
      VM.sysWrite(start.add(i));
      VM_Word value = VM_Magic.getMemoryWord(start.add(i));
      VM.sysWriteln("  ", value);
    }
  }

  static void dumpMemory(VM_Address start, int afterBytes) {
    dumpMemory(start, 0, afterBytes);
  }

  // test routine
  static void test_mmap() {
    int psize = VM_Memory.getPagesize();
    VM_Extent size = VM_Extent.fromIntZeroExtend(1024 * 1024);
    int ro = VM_Memory.PROT_READ;
    VM_Address base = VM_Address.fromIntZeroExtend(0x38000000);
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
    if (addr.NE(VM_Address.fromIntSignExtend(-1)) ){
      VM_Magic.setMemoryInt(addr, 17);
      if (VM_Magic.getMemoryInt(addr) == 17) {
        VM.sysWrite("write and read in memory region succeeded\n");
      } else {
        VM.sysWrite("read in memory region did not return value written\n");
      }

      if (!VM_Memory.mprotect(addr, size, ro)) {
        VM.sysWrite("mprotect failed\n");
      } else {
        VM.sysWrite("mprotect succeeded!\n");
      }
      if (VM_Magic.getMemoryInt(addr) == 17) {
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

    if (addr.NE(VM_Address.fromIntSignExtend(-1)) ){
      VM_Magic.setMemoryInt(addr, 17);
      if (VM_Magic.getMemoryInt(addr) == 17) {
        VM.sysWrite("write and read in memory region succeeded\n");
      } else {
        VM.sysWrite("read in memory region did not return value written\n");
      }

      if (!VM_Memory.mprotect(addr, size, ro)) {
        VM.sysWrite("mprotect failed\n");
      } else {
        VM.sysWrite("mprotect succeeded!\n");
      }

      if (VM_Magic.getMemoryInt(addr) == 17) {
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

  /**
  * @deprecated use alignUp(..) instead
  */
  public static VM_Address align (VM_Address address, int alignment) throws VM_PragmaInline {
        return alignUp(address, alignment); }
     
  /**
  * @deprecated use alignUp(..) instead
  */
  public static int align (int address, int alignment) throws VM_PragmaInline {
        return alignUp(address, alignment); }
  
  public static VM_Address alignUp (VM_Address address, int alignment) throws VM_PragmaInline {
    return address.add(alignment-1).toWord().and(VM_Word.fromIntSignExtend(~(alignment - 1))).toAddress();
  }

  public static VM_Address alignDown (VM_Address address, int alignment) throws VM_PragmaInline {
    return address.toWord().and(VM_Word.fromIntSignExtend(~(alignment - 1))).toAddress();
  }

  // These versions are here to accomodate the boot image writer
  public static int alignUp (int address, int alignment) throws VM_PragmaInline {
    return ((address + alignment - 1) & ~(alignment - 1));
  }
  
  public static int alignDown (int address, int alignment) throws VM_PragmaInline {
    return (address & ~(alignment - 1));
  }
}
