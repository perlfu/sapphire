/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * A class that encapsulates the GCMap portion of the machine code maps.
 * An instance of this class is created to encode and instance of a
 * OPT_GCIRMap into an int[].  The int[] is stored persistently,
 * but the instance of the VM_OptGCMap is NOT.
 * 
 * <ul>
 * <li> each map will be a sequence of 1 or more ints
 * <li> the first int in each map is a bit map of registers that
 *   contain references (the MSB is used for chaining,
 *   we assume it will never contain a reference)
 * <li> the sequence will continue as long as the most significant bit
 *   is set to 1
 * </ul>
 * 
 *  Note: This file contains two types of methods
 *         1) methods called during compilation to create the GC maps
 *            these methods are virtual
 *         2) methods called at GC time (no allocation allowed!)
 *            these methods are static
 *
 * @author Dave Grove
 * @author Michael Hind
 * @author Mauricio Serrano
 */
public final class VM_OptGCMap implements VM_OptGCMapIteratorConstants  {
  public static final int NO_MAP_ENTRY = -1;
  public static final int ERROR        = -2;

  /**
   * bit pattern for the "next" bit in the GC maps array
   */
  private static final int NEXT_BIT = 0x80000000;
  /** 
   * the index of the last map entry
   */
  private int lastGCMapEntry;      
  private int[] gcMapInformation;

  /**
   * Constructor, called during compilation
   */
  VM_OptGCMap() {
    lastGCMapEntry = -1;
    gcMapInformation = new int[16];   // initial map size
  }
  
  /**
   * Called to complete the encoding and return the final int[]
   */
  public int[] finish() {
    if ((gcMapInformation != null) && 
	(lastGCMapEntry < gcMapInformation.length - 1)) {
      resizeMapInformation(lastGCMapEntry + 1);
    }
    return gcMapInformation;
  }


  /**
   * Construct the GCMap for the argument GCIRMapElement
   * @param the irMapElem to create a GCMap for
   * @return the GCMap index.
   */
  public int generateGCMapEntry(OPT_GCIRMapElement irMapElem) {
    // the index into the GC maps we will use for this instruction.
    int mapIndex = NO_MAP_ENTRY;

    // Before requesting the (first) map entry, lets make sure we
    // will need it.  If the reg/spill list is empty, we don't
    // need a map slot, i.e., no references are live at this instruction
    OPT_RegSpillListEnumerator enum = irMapElem.regSpillListEnumerator();
    if (enum.hasMoreElements()) {

      // For efficiency we create our own bit map and then set the
      // appropriate array value
      int bitMap = 0;
      // count the spills so we know how big of an array we'll need
      int numSpills = 0;

      // Because the output data structure (the map) stores register
      // information before spills, we need to traverse the list twice
      // the first time we get the register mask, the 2nd time we get
      // the spills
      // process the register
      while (enum.hasMoreElements()) {
        OPT_RegSpillListElement elem = (OPT_RegSpillListElement)
            enum.nextElement();
        if (elem.isSpill()) {
	  numSpills++;
	} else {
          int realRegNumber = elem.getRealRegNumber();

          if (VM.VerifyAssertions && realRegNumber > LAST_GCMAP_REG) {
            System.out.println(elem);
            System.out.println(LAST_GCMAP_REG);
            VM.assert(false, "reg > last GC Map Reg!!");
          }

	  // get the bit position for this register number
	  int bitPosition = getRegBitPosition(realRegNumber);

          // Set the appropriate bit
          bitMap = bitMap | (NEXT_BIT >>> bitPosition);
        }
      }

      // get the next free Map entry
      int index = setRegisterBitMap(bitMap);

      int[] spillArray = new int[numSpills];
      int spillIndex = 0;
      // Now we need to walk the list again to process the spills.
      // first, get a fresh enumerator
      enum = irMapElem.regSpillListEnumerator();
      while (enum.hasMoreElements()) {
        OPT_RegSpillListElement elem = (OPT_RegSpillListElement)
            enum.nextElement();
        if (elem.isSpill()) {
	  spillArray[spillIndex++] = elem.getSpill();
	}
      }

      // add the spills into the map
      addAllSpills(spillArray);

      // don't forget to report that there are no more spills
      mapIndex = endCurrentMap(index);
    }
    return mapIndex;
  }


  ////////////////////////////////////////////
  // Methods called at GC time
  ////////////////////////////////////////////
  /**
   * Returns the GC map information for the GC map information entry passed
   * @param  entry     map entry
   * @param  gcMap            the encoded GCMap
   * @param  gcMap     the gc map
   */
  public static int gcMapInformation(int entry, int[] gcMap) {
    // before returning remember to clear the MSB.
    return gcMap[entry] & ~NEXT_BIT;
  }

  /**
   * Determines if the register map information for the entry passed is true
   * @param  entry            map entry
   * @param  registerNumber   the register number
   * @param  gcMap            the encoded GCMap
   */
  public static boolean registerIsSet(int entry, 
				      int registerNumber, 
				      int[] gcMap) {
    if (VM.VerifyAssertions) {
      VM.assert(registerNumber >= FIRST_GCMAP_REG && 
		registerNumber <= LAST_GCMAP_REG, 
		"Bad registerNumber");
    }

    // Get the bit position for the register number
    int bitPosition = getRegBitPosition(registerNumber);

    // Using the register number passed construct the appropriate bit string,
    // "and" it with the value, and see if we get a positive value
    return  (gcMap[entry] & (NEXT_BIT >>> bitPosition)) > 0;
  }

  /**
   * @param  gcMap            the encoded GCMap
   * @return the next (relative) location or -1 for no more locations
   */
  public static int nextLocation(int currentIndex, int[] gcMap) {
    // Does the next entry contain anything useful?
    if (nextBitSet(currentIndex, gcMap)) {
      // if so, return the next index
      return currentIndex + 1;
    } else {
      return -1;
    }
  }

  /**
   *  This method maps a register number to its bit position
   *  @param registerNumber the register number of interest
   */
  private static int getRegBitPosition(int registerNumber) {
    //  Because we can't use bit position 0 (that is the next bit), we 
    // adjust depending on the value of FIRST_GCMAP_REG
    //
    // For example,
    //  FIRST_GCMAP_REG = 1 => registerNumber = 1    (PPC)
    //  FIRST_GCMAP_REG = 0 => registerNumber = 1    (IA32)
    // 
    return registerNumber - FIRST_GCMAP_REG + 1;
  }


  /**
   * put your documentation comment here
   * @param entry
   * @return 
   */
  private static boolean nextBitSet(int entry, int[] gcMap) {
    return (gcMap[entry] & NEXT_BIT) == NEXT_BIT;
  }


  /**
   * Dumps the GCmap that starts at entry.
   * @param entry  the entry where the map begins
   * @param gcMap the encoded GCmaps
   */
  public static void dumpMap(int entry, int[] gcMap) {
    VM.sysWrite("Regs [");
    // Inspect the register bit map for the entry passed and print
    // those bit map entries that are true
    int bitmap = gcMap[entry];
    for (int registerNumber = FIRST_GCMAP_REG; 
	 registerNumber <= LAST_GCMAP_REG; 
	 registerNumber++) {
      if (registerIsSet(entry, registerNumber, gcMap)) {
	VM.sysWrite(registerNumber, false);
	VM.sysWrite(" ");
      }
    }
    VM.sysWrite("]");
    VM.sysWrite(" Spills [");
    while (nextBitSet(entry, gcMap)) {
      entry++;
      VM.sysWrite(gcMapInformation(entry, gcMap));
      VM.sysWrite(" ");
    }
    VM.sysWrite("]");
  }


  ////////////////////////////////////////////
  // Helper methods for GCMap creation
  ////////////////////////////////////////////
  /**
   * Returns the next GC map entry for use
   * @return the entry in the map table that can be used
   */
  private int getNextMapEntry() {
    // make sure we have enough room
    int oldLength = gcMapInformation.length - 1;
    if (lastGCMapEntry >= oldLength)
      // expand the mapInformation array to be twice as big
      resizeMapInformation(oldLength << 1);
    return ++lastGCMapEntry;
  }

  /**
   * put your documentation comment here
   * @param newSize
   */
  private void resizeMapInformation(int newSize) {
    int[] newMapInformation = new int[newSize];
    for (int i = 0; i <= lastGCMapEntry; i++) {
      newMapInformation[i] = gcMapInformation[i];
    }
    gcMapInformation = newMapInformation;
  }

  //////////
  // Setters for GC Maps
  //////////
  /**
   * Sets the register map information for the entry passed
   * @param  entry            map entry
   * @param  registerNumber   the register number to set to true
   */
  private final int setRegisterBitMap(int bitMap) {
    // Set the appropriate bit, but make sure we preserve the NEXT bit!
    int entry = getNextMapEntry();
    gcMapInformation[entry] = bitMap | NEXT_BIT;
    return  entry;
  }

  /**
   * If we will be looking for missed references we need to sort the list
   *  of spills and then add them to the map, otherwise, nothing to do
   * @param spillArray an array of spills
   */
  private final void addAllSpills(int spillArray[]) {
    // 1) sort all the spills we saved to allow for checking for missed refs
    //  at GC time, if the flag is on in VM_OptGenericGCMapIterator.java
    int length = spillArray.length;
    for (int i = 0; i < length - 1; i++) {
      for (int j = i+1; j < length; j++) {
	if (spillArray[i] > spillArray[j]) {
	  int tmp = spillArray[i];
	  spillArray[i] = spillArray[j];
	  spillArray[j] = tmp;
	}
      }
    }

    // 2) add them to the map using addSpillLocation
    for (int i = 0; i < spillArray.length; i++) {
      addSpillLocation(spillArray[i]);
    } 
  }

  /**
   * Adds the passed spill value to the current map
   * @param spill the spill location
   */
  private final void addSpillLocation(int spill) {
    // make sure the value doesn't overflow the maximum spill location
    if (VM.VerifyAssertions && ((spill < 0) || (spill > 32767)))
      VM.assert(false, "Unexpected spill passed:" + spill);
    // get the next entry (with the NEXT bit set) ...
    int entry = getNextMapEntry();
    gcMapInformation[entry] = spill | NEXT_BIT;
  }

  /**
   * Ends the current map
   * @param firstIndex
   * @return 
   */
  private final int endCurrentMap(int firstIndex) {
    int lastEntry = lastGCMapEntry;

    // adjust the last entry so that the NEXT bit is not set.
    gcMapInformation[lastEntry] = gcMapInformation[lastEntry] & ~NEXT_BIT;

    // search in previous locations
    loop: for (int i = 0; i < firstIndex;) {
      int index = i;
      for (int k = firstIndex; k <= lastEntry;) {
        int old = gcMapInformation[i++];
        int nnn = gcMapInformation[k++];
        if (old != nnn) {
          // find next entry
          while ((old & NEXT_BIT) != 0)
            old = gcMapInformation[i++];
          continue  loop;
        }
      }
      // found it !!, eliminate entry and reuse an old one
      lastGCMapEntry = firstIndex - 1;
      return index;
    }
    return firstIndex;
  }

}  
