/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.Vector;

/**
 * A container for recording how often a method is executed.
 *
 * @author Dave Grove
 * @author Michael Hind
 * @modified Peter Sweeney
 */
public final class VM_MethodCountData 
  implements VM_Decayable, VM_Reportable {

  private static final boolean DEBUG = false;
  
  /**
   * Sum of values in count array that are decayed over time.
   */
  private double totalCountsTaken;

  /**
   * How many counts have really been taken (ignoring decay).
   */
  private double undecayedTotalCountsTaken;

  /**
   * Count array: counts how many times a method is executed.
   * Constraint: counts[0] is not used.
   */
  private double[] counts;
  /**
   * Maps count array index to compiled method id.
   * Constraint: cmids[0] is not used.
   */
  private int[] cmids;
  /**
   * Maps compiled method id to count array index.
   * '0' implies that there is no entry in the count array for this cmid
   */
  private int[] map;
  /**
   * Next available count array entry.
   */
  private int nextIndex;
  
  /**
   * Constructor
   */
  VM_MethodCountData() {
    initialize();
  }

  /**
   * Reset fields.
   */
  private void initialize() {
    int numCompiledMethods = VM_CompiledMethods.numCompiledMethods();
    map = new int[ numCompiledMethods + (numCompiledMethods >>> 2) ];
    counts = new double[256];
    cmids = new int[256];
    nextIndex = 1;
    totalCountsTaken = 0;
    undecayedTotalCountsTaken = 0;
  }

  /** 
   * Drain a buffer of compiled method id's and update the count array.
   *
   * @param countBuffer a buffer of compiled method id's
   * @param numCounts the number of valid entries in the buffer
   */
  public final synchronized void update(int[] countBuffer, int numCounts) {
    for (int i=0; i<numCounts; i++) {
      int cmid = countBuffer[i];
      int index = findOrCreateHeapIdx(cmid);
      counts[index]++;      // Record count
      heapifyUp(index);     // Fix up the heap
    }
    totalCountsTaken += numCounts;
    undecayedTotalCountsTaken += numCounts;
    if (DEBUG) validityCheck();
  }

  /**
   * Increment the count for a compiled method id.
   *
   * @param cmid compiled method id
   * @param numCounts number of counts
   */
  public final synchronized void update(int cmid, double numCounts) {
    int index = findOrCreateHeapIdx(cmid);
    counts[index] += numCounts;       // Record counts
    heapifyUp(index);                 // Fix up the heap
    totalCountsTaken += numCounts;
    undecayedTotalCountsTaken += numCounts;
    if (DEBUG) validityCheck();
  }

  /**
   * Decay the method counts.
   */
  public final synchronized void decay() {
    double rate = VM_Controller.options.DECAY_RATE;
    for (int i=1; i<nextIndex; i++) {
      counts[i] /= rate;
    }
    totalCountsTaken /= rate;
  }


  /** 
   *  Print the counted (nonzero) methods.
   *  To get a sorted list, pipe the output through sort -n -r.
   */
  public final synchronized void report() {
    VM.sysWrite("Method counts: A total of "+totalCountsTaken+
		" times counted (undecayed  "+undecayedTotalCountsTaken+")\n");
    for (int i=1; i<nextIndex; i++) {
      double percent = 100 * countsToHotness(counts[i]);
      VM_Method m = VM_CompiledMethods.getCompiledMethod(cmids[i]).getMethod();
      VM.sysWrite(counts[i] + " ("+percent+"%) "+m+"\n");
      if (m.getDeclaringClass().isInBootImage()) {
	VM.sysWrite(" BOOT\n");
      } else {
	VM.sysWrite("\n");
      }
    }    
  }

  /**
   * Reset (clear) the method counts
   */
  public final synchronized void reset() {
    initialize();
  }

  /**
   * Get the current count for a given compiled method id.
   *
   * @param cmid compiled method id
   */
  public final synchronized double getData(int cmid) {
    int index = findHeapIdx(cmid);
    if (index > 0) {
      return counts[index];
    } else {
      return 0.0;
    }
  }

  /**
   * Reset (set to 0.0) the count for a given compiled method id.
   *
   * @param cmid compiled method id
   */
  public final synchronized void reset(int cmid) {
    int index = findHeapIdx(cmid);
    if (index > 0) {
      // Cmid does have a value in the heap. 
      // (1) clear map[cmid].
      // (2) shrink the heap by one slot.  
      //     (a) If index is the last element in the heap we have nothing 
      //         to do after we decrement nextIndex.
      //     (b) If index is not the last element in the heap, then move the
      //         last heap element to index and heapify.
      map[cmid] = 0;
      nextIndex--;
      if (index < nextIndex) {
	double oldValue = counts[index];
	counts[index] = counts[nextIndex];
	cmids[index] = cmids[nextIndex];
	map[cmids[index]] = index;
	if (counts[index] > oldValue) {
	  heapifyUp(index);
	} else {
	  heapifyDown(index);
	}
      } 
    }
    if (DEBUG) validityCheck();
  }

  /**
   * Set the raw data for a given cmid to the specified value
   *
   * @param cmid compiled method id
   * @param newVal new value for compiled method id count
   */
  public final synchronized void setData(int cmid, double newVal) {
    if (newVal == 0.0) {
      reset(cmid); // Prefer reset, since it frees up a slot in the heap.
    } else {
      int index = findOrCreateHeapIdx(cmid);
      double oldVal = counts[index];
      counts[index] = newVal;
      if (newVal > oldVal) {
	heapifyUp(index);
      } else {
	heapifyDown(index);
      }
    }
    if (DEBUG) validityCheck();
  }

  /**
   * Enqueue events describing the "hot" methods on the organizer's event queue.
   *
   * @param filterOptLevel filter out all methods already compiled at 
   *                       this opt level (or higher)
   * @param threshold hotness value above which the method is considered
   *                  to be hot. (0.0 to 1.0)
   */
  public final synchronized void insertHotMethods(int filterOptLevel, 
						  double threshold) {
    if (DEBUG) validityCheck();
    insertHotMethodsInternal(1, filterOptLevel, hotnessToCounts(threshold));
  }


  /**
   * Collect the hot methods that have been Opt level 2 compiled.
   *
   * @param threshold hotness value above which the method is considered to
   *                  be hot. (0.0 to 1.0)
   * @return a VM_MethodCountSet containing an
   * 		array of methods (cmids) and an array of their counts.
   * 
   */
  public final synchronized 
    VM_MethodCountSet collectHotO2Methods(double threshold) 
  {
    if (DEBUG) validityCheck();
    Vector collect = new Vector();
    collectHotOptNMethodsInternal(1, collect, hotnessToCounts(threshold), 2);

    // now package the data into the form the caller expects.
    int numHotMethods = collect.size();
    double[] numCounts = new double[numHotMethods];
    int   [] hotMethods = new int[numHotMethods];
    for (int i=0; i<numHotMethods; i++) {
      VM_HotMethodEvent event = (VM_HotMethodEvent)collect.elementAt(i);
      hotMethods[i] = event.getCMID();
      numCounts[i] = event.getNumSamples();
    }
    return new VM_MethodCountSet(hotMethods, numCounts);
  }

  /** 
   * Convert from a [0.0...1.0] hotness value to the number of counts
   * that represents that fraction of hotness
   *
   * @param hotness a value [0.0...1.0]
   * @return a number of counts
   */
  private double hotnessToCounts(double hotness) {
    return totalCountsTaken * hotness;
  }

  /**
   * Convert a value to a [0.0...1.0] fractional hotness value
   *
   * @param numCounts number of counts
   * @return a value [0.0...1.0]
   */
  private double countsToHotness(double numCounts) {
    if (VM.VerifyAssertions) VM.assert(numCounts <= totalCountsTaken);
    return numCounts / totalCountsTaken;
  }

  /**
   * Convert a possibly decayed numCounts into an
   * "undecayed" count value for use in model calculations of the 
   * time spent executing in this method so far.
   *
   * @param numCounts number of decayed counts
   * @return number of undecayed counts
   */
  private double numCountsForModel(double numCounts) {
    return undecayedTotalCountsTaken * countsToHotness(numCounts);
  }

  /**
   * Recursive implementation of insertHotMethods. Exploit heap property.
   * Note threshold has been converted into a count value by my caller!
   *
   * @param index count array index
   * @param filterOptLevel filter out all methods already compiled at 
   *                       this opt level (or higher)
   * @param threshold hotness value above which the method is considered
   *                  to be hot. (0.0 to 1.0)
   */
  private void insertHotMethodsInternal(int index, 
					int filterOptLevel, 
					double threshold) {
    if (index < nextIndex) {
      if (counts[index] > threshold) {
	int cmid = cmids[index];
	VM_CompilerInfo info = 
	  VM_CompiledMethods.getCompiledMethod(cmid).getCompilerInfo();
	int compilerType = info.getCompilerType();

	// Enqueue it unless it's either a trap method or already opt compiled
	// at filterOptLevel or higher.
	if (!(compilerType == VM_CompilerInfo.TRAP ||
	      (compilerType == VM_CompilerInfo.OPT && 
	       (((VM_OptCompilerInfo)info).getOptLevel() >= filterOptLevel)))) {

	  double ns = numCountsForModel(counts[index]);
	  VM_HotMethodRecompilationEvent event = 
	    new VM_HotMethodRecompilationEvent(cmid, ns);
	  if (VM_Controller.controllerInputQueue.prioritizedInsert(ns, event)){
	    if (VM.LogAOSEvents) {
	      VM_CompiledMethod m = VM_CompiledMethods.getCompiledMethod(cmid);
	      VM_AOSLogging.controllerNotifiedForHotness(m, ns);
	    }
	  } else {
	    if (VM.LogAOSEvents) VM_AOSLogging.controllerInputQueueFull(event);
	  }
	}

	// Since I was hot enough, also consider my children.
	insertHotMethodsInternal(index * 2, filterOptLevel, threshold);
	insertHotMethodsInternal(index * 2 + 1, filterOptLevel, threshold);
      }
    }
  }

  /**
   * Recursive implementation of collectHotO2Methods. 
   * Exploit heap property. 
   * Constraint: threshold has been converted into a count value by my caller!
   *
   * @param index count array index
   * @param filterOptLevel filter out all methods already compiled at 
   *                       this opt level (or higher)
   * @param threshold hotness value above which the method is considered
   *                  to be hot. (0.0 to 1.0)
   * @param optLevel level at which method is optimized
   */
  private void collectHotOptNMethodsInternal(int index, 
					     Vector collect, 
					     double threshold, 
					     int optLevel) {
    if (index < nextIndex) {
      if (counts[index] > threshold) {
	int cmid = cmids[index];
	VM_CompilerInfo info = 
	  VM_CompiledMethods.getCompiledMethod(cmid).getCompilerInfo();
	int compilerType = info.getCompilerType();
	if (compilerType == VM_CompilerInfo.OPT && 
	    ((VM_OptCompilerInfo)info).getOptLevel() == optLevel) {
	  double ns = numCountsForModel(counts[index]);
	  collect.add(new VM_HotMethodRecompilationEvent(cmid, ns));
	}
	
	// Since I was hot enough, also consider my children.
	collectHotOptNMethodsInternal(index * 2, collect, threshold, optLevel);
	collectHotOptNMethodsInternal(index * 2 + 1, collect, threshold, optLevel);
      }
    }
  }

  /**
   * Either find the index that is already being used to hold the counts
   * for cmid or allocate a new entry in the heap for cmid.
   *
   * @param cmid compiled method id
   * @return count array index
   */
  private int findOrCreateHeapIdx(int cmid) {
    if (cmid >= map.length) {
      growHeapMap(cmid);
    }
    int index = map[cmid];
    if (index == 0) {
      // A new cmid. Allocate a heap entry for it.
      index = nextIndex++;
      if (index >= counts.length) {
	growHeap();
      }
      counts[index] = 0.0;
      cmids[index] = cmid;
      map[cmid] = index;
    }
    return index;
  }
    
    
    /**
   * Find the index that is already being used to hold the counts for cmid.
   * If no such index exists, return 0.
   *
   * @param cmid compiled method id
   */
  private int findHeapIdx(int cmid) {
    if (cmid < map.length) {
      int index = map[cmid];
      return index;
    } else {
      return 0;
    }
  }


  /**
   * Grow the map to be at least as large as would be required to map cmid
   *
   * @param cmid compiled method id
   */
  private void growHeapMap(int cmid) {
    int[] newMap = new int[Math.max((int)(map.length * 1.25), cmid+1)];
    for (int j=0; j<map.length; j++) {
      newMap[j] = map[j];
    }
    map = newMap;
  }

  /**
   * Increase the size of the count's backing arrays
   */
  private void growHeap() {
    double[] tmp1 = new double[counts.length * 2];
    for (int i=1; i< counts.length; i++) {
      tmp1[i] = counts[i];
    }
    counts = tmp1;
    int[] tmp2 = new int[cmids.length * 2];
    for (int i=1; i< cmids.length; i++) {
      tmp2[i] = cmids[i];
    }
    cmids = tmp2;
  }

  /**
   * Restore the heap property after increasing a count array entry's value
   *
   * @param index of count array entry
   */
  private void heapifyUp(int index) {
    int current = index;
    int parent = index / 2;
    while (parent > 0 && counts[parent] < counts[current]) {
      swap(parent, current);
      current = parent;
      parent = parent / 2;
    }
  }

  /**
   * Restore the heap property after decreasing a count array entry's value
   *
   * @param index of count array entry
   */
  private void heapifyDown(int index) {
    int current = index;
    int child1 = current * 2;
    while (child1<nextIndex) {
      int child2 = current * 2 + 1;
      int larger = 
	(child2<nextIndex && counts[child2]>counts[child1]) ? child2 : child1;
      if (counts[current] >= counts[larger]) break; // done
      swap(current, larger);
      current = larger;
      child1 = current * 2;
    }
  }

  /**
   * Swap the heap entries at i and j.
   *
   * @param i count array index
   * @param j count array index
   */
  private void swap(int i, int j) {
    double tmpS = counts[i];
    counts[i] = counts[j];
    counts[j] = tmpS;
    int tmpC = cmids[i];
    cmids[i] = cmids[j];
    cmids[j] = tmpC;
    map[cmids[i]] = i;
    map[cmids[j]] = j;
  }

  /**
   * Validate that internal fields are consistent.
   * This is very expensive.  Only use for debugging purposes.
   */
  private void validityCheck() {
    if (DEBUG && VM.VerifyAssertions) {
      // (1) Verify map and cmids are in synch
      for (int i=0; i<map.length; i++) {
	VM.assert(map[i] == 0 || cmids[map[i]] == i);
      }
      for (int i=1; i<nextIndex; i++) {
	VM.assert(map[cmids[i]] == i);
	VM.assert(VM_CompiledMethods.getCompiledMethod(cmids[i]) != null);
      }

      // Verify that heap property holds on data.
      for (int i=2; i<nextIndex; i++) {
	VM.assert(counts[i] <= counts[i/2]);
      }
    }
  }
}
