/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import java.util.HashMap;

/**
 * The static fields and methods comprising a running virtual machine image.
 *
 * <p> These fields and methods form the "root set" of all the objects in the
 * running virtual machine. They are stored in an array whose first element
 * is always pointed to by the virtual machine's "table of contents" (jtoc)
 * register. The slots of this array hold either primitives (byte, int,
 * long, float, etc), object pointers, or array pointers. A second
 * table, co-indexed with the array, describes the contents of each
 * slot in the jtoc.
 *
 * <p> Consider the following declarations:
 *
 * <pre>
 *      class A { static int    i = 123;    }
 *      class B { static String s = "abc";  }
 *      class C { static double d = 4.56;   }
 *      class D { static void m() {} }
 * </pre>
 *
 * <p>Here's a picture of what the corresponding jtoc and descriptive
 * table would look like in memory:
 *
 * <pre>
 *                     +---------------+
 * jtoc:               |   (header)    |
 *                     +---------------+
 * [jtoc register]-> 0:|      0        |
 *                     +---------------+       +---------------+
 *                   1:|     123       |       |   (header)    |
 *                     +---------------+       +---------------+
 *                   2:|  (objref)   --------->|    "abc"      |
 *                     +---------------+       +---------------+
 *                   3:|   4.56 (hi)   |
 *                     +---------------+
 *                   4:|   4.56 (lo)   |
 *                     +---------------+       +---------------+
 *                   5:|  (coderef)  -----+    |   (header)    |
 *                     +---------------+  |    +---------------+
 *                   6:|     ...       |  +--->|  machine code |
 *                     +---------------+       |    for "m"    |
 *                                             +---------------+
 *                     +--------------------+
 * descriptions:       |     (header)       |
 *                     +--------------------+
 *                   0:|      EMPTY         |  ( unused )
 *                     +--------------------+
 *                   1:|     INT_LITERAL    |  ( A.i )
 *                     +--------------------+
 *                   2:|   REFERENCE_FIELD  |  ( B.s )
 *                     +--------------------+
 *                   3:|   DOUBLE_LITERAL   |  ( C.d )
 *                     +--------------------+
 *                     |     (unused)       |
 *                     +--------------------+
 *                   5:|      METHOD        |  ( D.m )
 *                     +--------------------+
 * </pre>
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 * @author Kris Venstermans
 */
public class VM_Statics implements VM_Constants {
  // Kinds of statics that can appear in slots of the jtoc.
  //
  public static final byte WIDE_TAG             = 0x20;
  public static final byte REFERENCE_TAG        = VM.BuildFor64Addr ? 0x40 | WIDE_TAG : 0x40;

  public static final byte EMPTY                = 0x0;

  public static final byte INT_LITERAL          = 0x01;
  public static final byte FLOAT_LITERAL        = 0x02;
  public static final byte LONG_LITERAL         = 0x03 | WIDE_TAG;
  public static final byte DOUBLE_LITERAL       = 0x04 | WIDE_TAG;
  public static final byte STRING_LITERAL       = 0x05 | REFERENCE_TAG;

  public static final byte REFERENCE_FIELD      = 0x06 | REFERENCE_TAG;
  public static final byte NUMERIC_FIELD        = 0x07;
  public static final byte WIDE_NUMERIC_FIELD   = 0x08 | WIDE_TAG;

  public static final byte METHOD               = 0x09 | REFERENCE_TAG;
  public static final byte TIB                  = 0x0a | REFERENCE_TAG;

  public static final byte CONTINUATION         = 0x0f;  // the upper half of a wide-field


  /**
   * static data values (pointed to by jtoc register)
   */
  private static int slots[] = new int[65536];

  /**
   * corresponding descriptions (see "kinds", above)
   */
  private static byte descriptions[] = new byte[slots.length];

  /**
   * next available slot number
   */

  // don't use slot 0.
  private static int nextSlot = VM.BuildFor32Addr ? 1 : (VM.BuildFor64Addr ? 2 : -1);

  /**
   * Mapping from int literals to the jtoc slot that contains them.
   */
  private static HashMap intLiterals = new HashMap();

  /**
   * Mapping from float literals to the jtoc slot that contains them.
   */
  private static HashMap floatLiterals = new HashMap();

  /**
   * Mapping from long literals to the jtoc slot that contains them.
   */
  private static HashMap longLiterals = new HashMap();

  /**
   * Mapping from double literals to the jtoc slot that contains them.
   */
  private static HashMap doubleLiterals = new HashMap();

  /**
   * Mapping from string literals to the jtoc slot that contains them.
   */
  private static HashMap stringLiterals = new HashMap();


  /**
   * Find or allocate a slot in the jtoc for an int literal.
   * @param       literal value (as bits)
   * @return    slot number that was allocated
   * Side effect: literal value is stored into jtoc
   */ 
  public static synchronized int findOrCreateIntLiteral(int literal) {
    Integer slot = (Integer)intLiterals.get(new Integer(literal));
    if (slot != null) return slot.intValue();
    int newSlot = allocateSlot(INT_LITERAL);
    intLiterals.put(new Integer(literal), new Integer(newSlot));
    slots[newSlot] = literal;
    return newSlot;
  }

  /**
   * Find or allocate a slot in the jtoc for a float literal.
   * @param       literal value (as bits)
   * @return    slot number that was allocated
   * Side effect: literal value is stored into jtoc
   */ 
  public static synchronized int findOrCreateFloatLiteral(int literal) {
    Integer slot = (Integer)floatLiterals.get(new Integer(literal)); // NOTE: keep mapping in terms of int bits!
    if (slot != null) return slot.intValue();
    int newSlot = allocateSlot(FLOAT_LITERAL);
    floatLiterals.put(new Integer(literal), new Integer(newSlot));
    slots[newSlot] = literal;
    return newSlot;
  }

  /**
   * Find or allocate a slot in the jtoc for a long literal.
   * @param       literal value (as bits)
   * @return    slot number of first of two slots that were allocated
   * Side effect: literal value is stored into jtoc
   */ 
  public static synchronized int findOrCreateLongLiteral(long literal) {
    Integer slot = (Integer)longLiterals.get(new Long(literal));
    if (slot != null) return slot.intValue();
    int newSlot = allocateSlot(LONG_LITERAL);
    longLiterals.put(new Long(literal), new Integer(newSlot));
    setSlotContents(newSlot, literal);
    return newSlot;
  }

  /**
   * Find or allocate a slot in the jtoc for a double literal.
   * @param       literal value (as bits)
   * @return    slot number of first of two slots that were allocated
   * Side effect: literal value is stored into jtoc
   */ 
  public static synchronized int findOrCreateDoubleLiteral(long literal) {
    Integer slot = (Integer)doubleLiterals.get(new Long(literal)); // NOTE: keep mapping in terms of long bits
    if (slot != null) return slot.intValue();
    int newSlot = allocateSlot(DOUBLE_LITERAL);
    doubleLiterals.put(new Long(literal), new Integer(newSlot));
    setSlotContents(newSlot, literal);
    return newSlot;
  }

  /**
   * Find or allocate a slot in the jtoc for a string literal.
   * @param       literal value
   * @return    slot number that was allocated
   * Side effect: literal value is stored into jtoc
   */ 
  public static synchronized int findOrCreateStringLiteral(VM_Atom literal) throws java.io.UTFDataFormatException {
    Integer slot = (Integer)stringLiterals.get(literal);
    if (slot != null) return slot.intValue();
    int newSlot = allocateSlot(STRING_LITERAL);
    stringLiterals.put(literal, new Integer(newSlot));
    setSlotContents(newSlot, literal.toUnicodeString());
    return newSlot;
  }

  /**
   * Allocate a slot in the jtoc.
   * @param    description of a static field or method (see "kinds", above)
   * @return slot number that was allocated 
   * (two slots are allocated for longs and doubles)
   */ 
  public static synchronized int allocateSlot(byte description) {

    if (nextSlot + 2 > slots.length) {
      // !!TODO: enlarge slots[] and descriptions[], and modify jtoc register to
      // point to newly enlarged slots[]
      // NOTE: very tricky on IA32 because opt uses 32 bit literal address to access jtoc.
      VM.sysFail("VM_Statics.allocateSlot: jtoc is full");
    }

    // Allocate two slots for wide items after possibly blowing another slot for alignment.
    // Wide things include long or double and addresses on 64-bit architecture.
    //
    boolean isWide = (description & WIDE_TAG) == WIDE_TAG;

    if (isWide && (nextSlot & 1) == 1) {
      descriptions[nextSlot] = EMPTY;
      nextSlot++;
    }

    int slot = nextSlot;

    if (isWide) {
      descriptions[slot] = description;
      descriptions[slot+1] = CONTINUATION;
      nextSlot += 2;
    } else {
      descriptions[slot] = description;
      nextSlot++;
    } 
	 
    if ((slot > 2000 && slot < 2100 && false) || VM.TraceStatics) VM.sysWrite("VM_Statics: allocated jtoc slot " + slot + " for " + getSlotDescriptionAsString(slot) + "\n");
    return slot;
  }

  /**
   * Fetch number of jtoc slots currently allocated.
   */ 
  public static int getNumberOfSlots() throws VM_PragmaUninterruptible {
    return nextSlot;
  }

  /**
   * Fetch total number of slots comprising the jtoc.
   */ 
  public static int getTotalNumberOfSlots() throws VM_PragmaUninterruptible {
    return slots.length;
  }

  /**
   * Does specified jtoc slot contain a reference?
   * @param    slot number obtained from allocateSlot()
   * @return true --> slot contains a reference
   */ 
  public static boolean isReference(int slot) throws VM_PragmaUninterruptible {
    byte type = descriptions[slot];
    // if (type == CONTINUATION) VM.sysFail("Asked about type of a JTOC continuation slot");
    return (descriptions[slot] & VM_Statics.REFERENCE_TAG) == VM_Statics.REFERENCE_TAG;
  }

  /**
   * Fetch description of specified jtoc slot.
   * @param    slot number obtained from allocateSlot()
   * @return description of slot contents (see "kinds", above)
   */
  public static byte getSlotDescription(int slot) throws VM_PragmaUninterruptible {
    return descriptions[slot];
  }

  public static int getSlotSize (int slot) throws VM_PragmaUninterruptible {
      return ((descriptions[slot] & WIDE_TAG) == WIDE_TAG) ? 2 : 1;
  }

  /**
   * Fetch description of specified jtoc slot as a string.
   * @param    slot number obtained from allocateSlot()
   * @return description of slot contents (see "kinds", above)
   */ 
  public static String getSlotDescriptionAsString(int slot) {
    String kind = null;
    switch (getSlotDescription(slot)) {
      case INT_LITERAL        : kind = "INT_LITERAL";        break;
      case FLOAT_LITERAL      : kind = "FLOAT_LITERAL";      break;
      case LONG_LITERAL       : kind = "LONG_LITERAL";       break;
      case DOUBLE_LITERAL     : kind = "DOUBLE_LITERAL";     break;
      case STRING_LITERAL     : kind = "STRING_LITERAL";     break;
      case REFERENCE_FIELD    : kind = "REFERENCE_FIELD";    break;
      case NUMERIC_FIELD      : kind = "NUMERIC_FIELD";      break;
      case WIDE_NUMERIC_FIELD : kind = "WIDE_NUMERIC_FIELD"; break;
      case METHOD             : kind = "METHOD";             break;
      case TIB                : kind = "TIB";                break;
      case EMPTY              : kind = "EMPTY SLOT";         break;
      case CONTINUATION       : kind = "CONTINUATION";       break;
    }
    return kind;
  }

  /**
   * Fetch jtoc object (for JNI environment and GC).
   */ 
  public static VM_Address getSlots() throws VM_PragmaUninterruptible {
    return VM_Magic.objectAsAddress(slots);
  }

  /**
   * Fetch jtoc object (for JNI environment and GC).
   */ 
  public static int [] getSlotsAsIntArray() throws VM_PragmaUninterruptible {
    return slots;
  }

  /**
   * Fetch contents of a slot, as an integer
   */ 
  public static int getSlotContentsAsInt(int slot) throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions)
      VM._assert((descriptions[slot] & WIDE_TAG) != WIDE_TAG);
    return slots[slot];
  }

  /**
   * Fetch contents of a slot-pair, as a long integer.
   */ 
  public static long getSlotContentsAsLong(int slot) throws VM_PragmaUninterruptible {	
    //Kris Venstermans : future optimaliation
    //if (VM.runningVM)
    //  return VM_Magic.getLongAtOffset(slots, slot << LOG_BYTES_IN_INT); 
    long result;
    if (VM.LittleEndian) {
      result = (((long) slots[slot+1]) << BITS_IN_INT); // hi
      result |= ((long) slots[slot]) & 0xFFFFFFFFL; // lo
    } else {
      result = (((long) slots[slot]) << BITS_IN_INT);     // hi
      result |= ((long) slots[slot+1]) & 0xFFFFFFFFL; // lo
    }
    return result;
  }

  /**
   * Fetch contents of a slot, as an object.
   */ 
  public static Object getSlotContentsAsObject(int slot) throws VM_PragmaUninterruptible {
    //-#if RVM_FOR_64_ADDR
    return VM_Magic.addressAsObject(VM_Address.fromLong(getSlotContentsAsLong(slot)));
    //-#else
    return VM_Magic.addressAsObject(VM_Address.fromInt(slots[slot]));
    //-#endif
  }

  /**
   * Fetch contents of a slot, as an object array.
   */ 
  public static Object[] getSlotContentsAsObjectArray(int slot) throws VM_PragmaUninterruptible {
    //-#if RVM_FOR_64_ADDR
    return VM_Magic.addressAsObjectArray(VM_Address.fromLong(getSlotContentsAsLong(slot)));
    //-#else
    return VM_Magic.addressAsObjectArray(VM_Address.fromInt(slots[slot]));
    //-#endif
  }

  /**
   * Set contents of a slot, as an integer.
   */
  public static void setSlotContents(int slot, int value) throws VM_PragmaUninterruptible {
    slots[slot] = value;
  }

  /**
   * Set contents of a slot, as a long integer.
   */
  public static void setSlotContents(int slot, long value) throws VM_PragmaUninterruptible {
    //Kris Venstermans : future optimaliation
    //if (VM.runningVM)
    //  VM_Magic.setLongAtOffset(slots, slot << LOG_BYTES_IN_INT , value);
    //else
    if (VM.LittleEndian) {
      slots[slot + 1] = (int)(value >>> BITS_IN_INT); // hi
      slots[slot    ] = (int)(value       ); // lo
    } else {
      slots[slot    ] = (int)(value >>> BITS_IN_INT); // hi
      slots[slot + 1] = (int)(value       ); // lo
    }
  }

  /**
   * Set contents of a slot, as an object.
   */ 
  public static void setSlotContents(int slot, Object object) throws VM_PragmaUninterruptible {
    //Kris Venstermans : future optimaliation
    //if (VM.runningVM)
    //  VM_Magic.setObjectAtOffset(slots, slot << LOG_BYTES_IN_INT , object); 
    //else  
    //-#if RVM_FOR_64_ADDR
    setSlotContents(slot, VM_Magic.objectAsAddress(object).toLong());
    //-#else
    slots[slot] = VM_Magic.objectAsAddress(object).toInt();
    //-#endif
  }
}
