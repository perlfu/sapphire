/**
 ** Util
 **
 ** GCspy utilities
 **
 ** (C) Richard Jones, 2003
 ** Computing Laboratory, University of Kent at Canterbury
 ** All rights reserved.
 **/

package uk.ac.kent.JikesRVM.memoryManagers.JMTk.gcspy;

import com.ibm.JikesRVM.VM_SysCall;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Synchronization;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.JMTk.Log;
import com.ibm.JikesRVM.VM_SizeConstants;


/**
 * This class provides generally useful methods.
 *
 * @author <a href="www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class Util 
  implements VM_Uninterruptible, Constants, VM_SizeConstants {
  public final static String Id = "$Id$";
  
  private static final boolean DEBUG_ = false;
  private static final int LOG_BYTES_IN_WORD = LOG_BYTES_IN_INT;
  private static final int BYTES_IN_WORD = 1 << LOG_BYTES_IN_WORD;
  
  /**
   * Allocate an array of bytes with malloc
   * 
   * @param size The size to allocate
   * @return The start address of the memory allocated in C space
   * @see free
   */
  public static final VM_Address malloc(int size) {
    VM_Address rtn  = VM_SysCall.sysMalloc(size);
    if (rtn.isZero())
      VM_Interface.sysFail("GCspy malloc failure");
    return rtn;
  }

  /**
   * Free an array of bytes previously allocated with malloc
   * 
   * @param addr The address of some memory previously allocated with malloc
   * @see malloc
   */
  public static final void free(VM_Address addr) {
    if (!addr.isZero())
      VM_SysCall.sysFree(addr);
  }
  
  /**
   * Dump a range in format [start,end)
   * 
   * @param start The start of the range
   * @param end The end of the range
   */
  public static final void dumpRange(VM_Address start, VM_Address end) {
    Log.write("[", start);
    Log.write(",", end);
    Log.write(')');
  }

  
  // From VM.java
  private static int sysWriteLock = 0;
  private static int sysWriteLockOffset = -1;

  private static final void swLock() {
    if (sysWriteLockOffset == -1) return;
    while (!VM_Synchronization.testAndSet(VM_Magic.getJTOC(), sysWriteLockOffset, 1)) 
      ;
  }

  private static final void swUnlock() {
    if (sysWriteLockOffset == -1) return;
    VM_Synchronization.fetchAndStore(VM_Magic.getJTOC(), sysWriteLockOffset, 0);
  }

  /**
   * Convert a String to a 0-terminated array of bytes
   *
   * @param str The string to convert
   * @return The address of a null-terminated array in C-space
   *
   * WARNING: we call out to String.length and String.charAt, both of
   * which are interruptible. We protect these calls with a
   * swLock/swUnlock mechanism, as per VM.sysWrite on String
   */
  public static final VM_Address getBytes (String str) 
    throws VM_PragmaLogicallyUninterruptible {
    if (str == null) 
      return VM_Address.zero();

    if (DEBUG_) {
      Log.write("getBytes: ");
      Log.write(str);
      Log.write("->");
    }

    // Grab some memory sufficient to hold the null terminated string,
    // rounded up to an integral number of ints.
    int len;
    swLock(); 
      len = str.length(); 
    swUnlock();
    int size = ((len >>> LOG_BYTES_IN_WORD) + 1) << LOG_BYTES_IN_WORD;
    VM_Address rtn = malloc(size);
   
    // Write the string into it, one word at a time, being carefull about endianism
    for (int w = 0; w <= (len >>> LOG_BYTES_IN_WORD); w++)  {
      int value = 0;
      int offset = w << LOG_BYTES_IN_WORD;
      int shift = 0;
      for (int b = 0; b < BYTES_IN_WORD; b++) {
	byte byteVal = 0;
	if (offset + b < len) {
	  swLock(); 
	    byteVal = (byte) str.charAt(offset + b);    // dodgy conversion!
	  swUnlock();
	}
	//-#if RVM_FOR_IA32
	// Endianism matters
	value = (byteVal << shift) | value;
	//-#else
	value = (value << shift) | byteVal; // not tested
	//-#endif
	shift += BITS_IN_BYTE;
      }
      VM_Magic.setMemoryWord(rtn.add(offset), VM_Word.fromInt(value));
    }
    if (DEBUG_) {
      VM_SysCall.sysWriteBytes(2/*SysTraceFd*/, rtn, size);
      Log.write("\n");
    }
    return rtn;
  }

  public static final int KILOBYTE = 1024;
  public static final int MEGABYTE = 1024 * 1024;


  /**
   * Pretty print a size, converting from bytes to kilo- or mega-bytes as appropriate
   * 
   * @param buffer The buffer (in C space) in which to place the formatted size
   * @param size The size in bytes
   */
  public static final void formatSize(VM_Address buffer, int size) {
    VM_SysCall.gcspyFormatSize(buffer, size);
  }

  

  /**
   * Place a string representation of a long in an array of bytes
   * without incurring allocation
   * 
   * @param buffer The byte array
   * @param value The long to convert
   * @return The length of the string representation of the integer
   *         -1 indicates some problem (e.g the char buffer was too small)
   */
  public static final int numToBytes(byte[] buffer, long value) {
    return numToBytes(buffer, value, 10);
  }
  
  /**
   * Place a string representation of a long in an array of bytes
   * without incurring allocation
   * 
   * @param buffer The byte array
   * @param value The long to convert
   * @param radix the base to use for conversion
   * @return The length of the string representation of the integer
   *         -1 indicates some problem (e.g the char buffer was too small)
   */
  public static final int numToBytes(byte[] buffer, long value, int radix) {

    if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX)
      radix = 10;
    
    if (value == 0) {
      buffer[0] = (byte)'0';
      return 1;
    }
    
    boolean negative;
    long longValue;
    int count;
    if (!(negative = (value < 0))) {
      longValue = -value;
      count = 1;
    } else {
      longValue = value;
      count = 2;
    }
    
    long j = longValue;
    while ((j /= radix) != 0) count++;
    if (count > buffer.length)
      return -1; // overflow
    
    int i = count;
    do {
      int ch = (int) -(longValue % radix);
      if (ch > 9)
        ch -= (10 - (int) 'a');
      else
        ch += (int) '0';
      buffer [--i] = (byte) ch;
    } while ((longValue /= radix) != 0);
    if (negative) buffer [0] = (byte)'-';
    
    return count;

  }
  

  //----------- Various methods modelled on string.c ---------------------//

  /**
   * sprintf(char *str, char *format, char* value)
   * 
   * @param str The destination 'string' (memory in C space)
   * @param format The format 'string' (memory in C space)
   * @param value The value 'string' (memory in C space)
   * @return The number of characters printed (as returned by C's sprintf
   */
  public static final int sprintf(VM_Address str, VM_Address format, VM_Address value) {
    return VM_SysCall.gcspySprintf(str, format, value);
  }

}

