/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */

package org.mmtk.vm;

import com.ibm.JikesRVM.VM_SizeConstants;
import com.ibm.JikesRVM.VM_JavaHeader;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class defines MMTk's VM-specific constants for Jikes RVM.  It
 * shadows the corresponding stub file within MMTk proper.
 *
 * Note that these methods look as though they are constants.  This is
 * intentional.  They would be constants except that we want MMTk to
 * be Java->bytecode compiled separately, ahead of time, in a
 * VM-neutral way.  MMTk must be compiled against the stub which this
 * file shadows, but if these were actual constants rather than
 * methods, then the Java compiler would legally constant propagate
 * and constant fold the values in the stub file, thereby ignoring the
 * real values held in this VM-specific file.  The constants are
 * realized correctly at class initialization time, so the performance
 * overhead of this approach is negligible (and has been measured to
 * be insignificant).
 *
 * $Id$
 *
 * @author Steve Blackburn
 * @version $Revision$
 * @date $Date$
 */
public class VMConstants {
  /** @return The log base two of the size of an address */
  public static final byte LOG_BYTES_IN_ADDRESS() throws InlinePragma { 
    return VM_SizeConstants.LOG_BYTES_IN_ADDRESS; 
  }
  /** @return The log base two of the size of a word */
  public static final byte LOG_BYTES_IN_WORD() throws InlinePragma { 
    return VM_SizeConstants.LOG_BYTES_IN_WORD; 
  }
  /** @return The log base two of the size of an OS page */
  public static final byte LOG_BYTES_IN_PAGE() throws InlinePragma { 
    return 12; 
  }
  /** @return The log base two of the minimum allocation alignment */
  public static final byte LOG_MIN_ALIGNMENT() throws InlinePragma { 
    return VM_JavaHeader.LOG_MIN_ALIGNMENT;
  }
  /** @return The log base two of (MAX_ALIGNMENT/MIN_ALIGNMENT) */
  public static final byte MAX_ALIGNMENT_SHIFT() throws InlinePragma { 
    return VM_SizeConstants.LOG_BYTES_IN_LONG - VM_SizeConstants.LOG_BYTES_IN_INT; 
  }
  /** @return The maximum number of bytes of padding to prepend to an object */
  public static final int MAX_BYTES_PADDING() throws InlinePragma { 
    return VM_SizeConstants.BYTES_IN_DOUBLE; 
  }
  
  /** @return The value to store in alignment holes */
  public static final int ALIGNMENT_VALUE() throws InlinePragma { 
    return VM_JavaHeader.ALIGNMENT_VALUE;
  }

}

