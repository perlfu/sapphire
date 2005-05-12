/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;

//-#if RVM_WITH_OSR
import com.ibm.JikesRVM.OSR.*;
//-#endif

import java.util.Stack;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * This class performs a flow-sensitive iterative live variable analysis. 
 * The result of this analysis is live ranges for each basic block.
 * (@see OPT_BasicBlock)
 * This class can also optionally construct GC maps. These GC maps
 * are later used to create the final gc map (see VM_OptReferenceMap.java). 
 *
 * The bottom of the file contains comments regarding imprecise exceptions.
 *
 * @author Michael Hind
 * @author Martin Trapp
 * @author Stephen Fink
 */
final class OPT_LiveAnalysis extends OPT_CompilerPhase 
  implements OPT_Operators 
//-#if RVM_WITH_OSR
             , OSR_Constants
//-#endif
{
  // Real Instance Variables
  /**
   *  Should we also create GC maps while we are computing liveness
   */
  private boolean createGCMaps;

  /**
   *  Should we store liveness information at the top of each handler block?
   */
  private boolean storeLiveAtHandlers;

  /**
   *  Should we skip guard registers?
   */
  private boolean skipGuards;

  /**
   *  Should we skip the (final) local propagation phase?
   */
  private boolean skipLocal;

  /**
   *  The current OPT_LiveSet that we will carry around
   */
  private OPT_LiveSet currentSet;

  /**
   *  Temporary live information associated with each basic block
   */
  private BBLiveElement[] bbLiveInfo;

  /**
   * The GC map associated with the IR, optionally filled by this class
   */
  private OPT_GCIRMap map;

  //-#if RVM_WITH_OSR
  private OSR_VariableMap osrMap;
  //-#endif

  /**
   * For each register, the set of live interval elements describing the
   * register.
   */
  private ArrayList[] registerMap;
  
  // Debugging information
  // Live Intervals, GC Maps, and fixed-point results
  private static final boolean dumpFinalLiveIntervals = false;
  private static final boolean dumpFinalMaps = false;
  private static final boolean dumpFixedPointResults = false;

  // used for debugging. make it nonfinal to accept the argument from the input
  private static final boolean debug = false;

  // even more debugging info
  private static final boolean verbose = false;
  // End debugging information

  public final String getName() {
    return  "Live Analysis";
  }

  /**
   * The constructor is used to specify whether GC maps should be computed
   * along with live analysis.
   * 
   * @param createGCMaps should we create GC maps?
   * @param skipLocal should we skip the (final) local propagation phase?
   */
  OPT_LiveAnalysis(boolean createGCMaps, boolean skipLocal) {
    this.createGCMaps = createGCMaps;
    this.skipLocal = skipLocal;
    this.storeLiveAtHandlers = false;
    this.skipGuards = true;
  }

  /**
   * The constructor is used to specify whether GC maps should be computed
   * along with live analysis.
   * 
   * @param createGCMaps should we create GC maps?
   * @param skipLocal should we skip the (final) local propagation phase?
   * @param storeLiveAtHandlers should we store liveness info at the 
   * top of each handler block?
   */
  OPT_LiveAnalysis(boolean createGCMaps, 
                   boolean skipLocal, 
                   boolean storeLiveAtHandlers) {
    this.createGCMaps = createGCMaps;
    this.skipLocal = skipLocal;
    this.storeLiveAtHandlers = storeLiveAtHandlers;
    this.skipGuards = true;
  }

  /**
   * The constructor is used to specify whether GC maps should be computed
   * along with live analysis.
   * 
   * @param createGCMaps should we create GC maps?
   * @param skipLocal should we skip the (final) local propagation phase?
   * @param storeLiveAtHandlers should we store liveness info at the 
   * top of each handler block?
   * @param skipGuards should we ignore validation registers?
   */
  OPT_LiveAnalysis(boolean createGCMaps, 
                 boolean skipLocal, 
                 boolean storeLiveAtHandlers,
                 boolean skipGuards) {
    this.createGCMaps = createGCMaps;
    this.skipLocal = skipLocal;
    this.storeLiveAtHandlers = storeLiveAtHandlers;
    this.skipGuards = skipGuards;
  }
  
  /**
   *  By default we don't create GC maps and do perform the local prop phase
   */
  OPT_LiveAnalysis() {
    this.createGCMaps = false;
    this.skipLocal = false;
    this.skipGuards = true;
  }

  /** 
   * The entry point into this class
   * Perform live variable analysis on this IR, constructing live
   * range info and (optionally) GC map info as we go.
   *
   * @param ir the ir
   */
  public void perform(OPT_IR ir) {

    // make sure IR info is up-to-date
    OPT_DefUse.recomputeSpansBasicBlock(ir);
    debugBegining(ir, createGCMaps);
    bbLiveInfo = new BBLiveElement[ir.cfg.numberOfNodes()];

    for (int i = 0; i < ir.cfg.numberOfNodes(); i++) {
      bbLiveInfo[i] = new BBLiveElement();
    }

    if (createGCMaps) {
      // Create the IR-based Map we will use during compilation
      // At a later phase this map is converted into the "runtime"
      //  map, which is called VM_OptReferenceMap.
      map = new OPT_GCIRMap();

      //-#if RVM_WITH_OSR
      osrMap = new OSR_VariableMap();
      //-#endif
    }

    // allocate the "currentSet" which is used to cache the current results
    currentSet = new OPT_LiveSet();
    boolean reuseCurrentSet = false;

    // make sure reverse top sort order is built            
    // currentBlock is the first node in the list 
    OPT_BasicBlock currentBlock = (OPT_BasicBlock)ir.cfg.buildRevTopSort();

    // 2nd parm: true means forward analysis; false means backward analysis
    OPT_SortedGraphIterator bbIter = new OPT_SortedGraphIterator(currentBlock, 
                                                                 false);
    while (currentBlock != null) {
      boolean changed = processBlock(currentBlock, reuseCurrentSet, ir);

      // mark this block as processed and get the next one
      OPT_BasicBlock nextBlock = 
        (OPT_BasicBlock)bbIter.markAndGetNextTopSort(changed);

      // check to see if nextBlock has only one successor, currentBlock.
      // If so, we can reuse the current set and avoid performing a meet.
      if (nextBlock != null && 
          bbIter.isSinglePredecessor(currentBlock, nextBlock)) {
        reuseCurrentSet = true;
      } else {
        reuseCurrentSet = false;
      }
      currentBlock = nextBlock;
    }
    debugPostGlobal(ir);

    // Now compute live ranges, using results from the fixed point computation 
    // Also, optionally create GC maps
    // SSA doesn't need this part so we allow it to be optional.
    // However, if we don't perform it than the final maps and intervals aren't
    // created, so we can't print them.
    if (!skipLocal) {
      performLocalPropagation(ir, createGCMaps);

      if (createGCMaps && dumpFinalMaps) {
        System.out.println("**** START OF IR for method: " + ir.method.getName()
          + " in class: " + ir.method.getDeclaringClass());
        ir.printInstructions();
        System.out.println("**** END   OF IR INSTRUCTION DUMP ****");
        
        printFinalMaps(ir);
      }
      if (dumpFinalLiveIntervals) {
        printFinalLiveIntervals(ir);
      }
      // If we performed the local propagation, live interval information 
      // lives off of each basic block.
      // Thus, we no longer need bbLiveInfo (the fixed points results)
      // When we don't perform the local propagation, such as translating
      // out of SSA, then we need to keep bbLiveInfo around
      bbLiveInfo = null;
   
      // compute the mapping from registers to live interval elements
      computeRegisterMap(ir);
    }

    // No longer need currentSet, which is simply a cache of a LiveSet).
    currentSet = null;

    // This will be null if createGCMaps is false
    if (createGCMaps) {
      ir.MIRInfo.gcIRMap = map;
      //-#if RVM_WITH_OSR
      ir.MIRInfo.osrVarMap = osrMap;
      //-#endif
    }

    // inform the IR that handler liveness is now available
    if (storeLiveAtHandlers) {
      ir.setHandlerLivenessComputed();
    }

  }

  /**
   * Return an iterator over all the live interval elements for a given
   * register.
   */
  public Iterator iterateLiveIntervals(OPT_Register r) {
    ArrayList set = registerMap[r.getNumber()];
    if (set == null) {
      return new OPT_EmptyIterator();
    } else {
      return set.iterator();
    }
  }

  /**
   * Update the data structures to reflect that all live intervals for r2
   * are now intervals for r1.
   */
  public void merge(OPT_Register r1, OPT_Register r2) {
    ArrayList toRemove = new ArrayList(5);

    for (Iterator i = iterateLiveIntervals(r2); i.hasNext(); ) {
      OPT_LiveIntervalElement interval = (OPT_LiveIntervalElement)i.next();
      interval.setRegister(r1);
      addToRegisterMap(r1,interval);
      // defer removing the interval to avoid concurrent modification of
      // the iterator's backing set.
      toRemove.add(interval);
    }
    // perform deferred removals
    for (Iterator i = toRemove.iterator(); i.hasNext(); ) {
      OPT_LiveIntervalElement interval = (OPT_LiveIntervalElement)i.next();
      removeFromRegisterMap(r2,interval);
    }
  }

  /**
   * Set up a mapping from each register to the set of live intervals for
   * the register.
   * <p>
   * Side effect: map each live interval element to its basic block.
   */
  private void computeRegisterMap(OPT_IR ir) {
    registerMap = new ArrayList[ir.regpool.getNumberOfSymbolicRegisters()];
    for (Enumeration e = ir.getBasicBlocks(); e.hasMoreElements(); ) {
      OPT_BasicBlock bb = (OPT_BasicBlock)e.nextElement();
      for (Enumeration i = bb.enumerateLiveIntervals(); i.hasMoreElements(); ) {
        OPT_LiveIntervalElement lie = (OPT_LiveIntervalElement)i.nextElement();
        lie.setBasicBlock(bb);
        if (lie.getRegister().isSymbolic()) {
          addToRegisterMap(lie.getRegister(),lie);
        }
      }
    }
  }

  /**
   * Add the live interval element i to the map for register r.
   */
  private void addToRegisterMap(OPT_Register r, OPT_LiveIntervalElement i) {
    ArrayList set = registerMap[r.getNumber()];
    if (set == null) {
      set = new ArrayList(3);
      registerMap[r.getNumber()] = set;
    }
    set.add(i);
  }

  /**
   * Remove the live interval element i from the map for register r.
   */
  private void removeFromRegisterMap(OPT_Register r, 
                                     OPT_LiveIntervalElement i) {
    ArrayList set = registerMap[r.getNumber()];
    if (set == null) {
      return;
    } else {
      set.remove(i);
    }
  }

  /**
   * Compute summary (local) live variable analysis for a basic block, which
   * is basically Gen and Kill information.
   *
   * @param bblock the basic block 
   * @param ir the governing if
   * @see "Efficient and Precise Modeling of Exceptions for the 
   *      Analysis of Java Programs" by Choi, Grove, Hind, Sarkar
   *      in ACM PASTE99 workshop (available at
   *      www.research.ibm.com/jalapeno)"
   */
  private void computeBlockGenAndKill(OPT_BasicBlock bblock, OPT_IR ir) {
    if (verbose) {
      System.out.println(" --> Computing Gen/Kill for block " + bblock);
    }
    
    // Tells whether we've seen the first PEI
    boolean seenFirstPEI = false; 
    // Because control flow may emanate from a potentially excepting
    // instruction (PEI) out of the basic block, care must be taken 
    // when computing what can be killed by a basic block.  
    //
    //  S1:  y = 
    //  S2:  <exception-raising inst>
    //  S3:  x =
    // For example in the block above, live variables coming from
    // the normal exit of the block (i.e., after S3) can be killed
    // by S1 or S3 (depending on the variable).  However, live variables
    // coming from the PEI edge (at S2) can only be killed by S1.
    // Thus, when a block contains PEIs, we need to distinguish the
    // kill sets.  Namely, we need 
    //    Kill_tot  -  what can be killed anywhere in the block
    //    Kill_n    -  what can be killed from PEI_n on up
    //    Kill_n-1  -  what can be killed from PEI_n-1 on up
    //      ...
    //    Kill_1    -  what can be killed from PEI_1 on up
    // We would then compute In as follows
    //
    //  In = Out_norm - Kill_tot   (all vars entering from bottom are eligible
    //                                to be killed)
    //     U Out_n - Kill_n    
    //     U Out_n-1 - Kill_n-1
    //     ...
    //     U Out_1 - Kill_1
    //     U Gen
    //  where Out_n is the out information at PEI i, i.e., the IN information
    //  for whatever handlers may catch PEI i
    //    ...
    //    PEI 1
    //    ...
    //    PEI n-1
    //    ...
    //    PEI n
    //    ...
    //  If we conservatively assume all handlers for the block of interest
    //  can be reached by all PEIs in this block then the equation becomes
    //   In = (Out_norm - Kill_tot)
    //     U (Out_hand - Kill_n)
    //     U (Out_hand - Kill_n-1)
    //     ...
    //     U (Out_hand - Kill_1)
    //     U Gen
    // where "Out_hand" is the union of the in sets for all handlers.
    // Since Kill_i is a subset of Kill_j, for i < j, we can simplify to
    //   In = (Out_norm - Kill_tot)
    //     U (Out_hand - Kill_1)    (1)
    //     U Gen
    // Since kill_1 is a subset of kill_tot, we don't need the
    // the parenthesis (and the intermediate set)
    // If there are no handlers than (1) is empty and we don't need
    // to compute Kill_1.  We will take this approach for now.
    // So for each block we will have at most 2 kill sets: Kill_tot and Kill_1
    // This code finds the first PEI in the block
    
    OPT_Instruction firstPEI = null;
    if (bblock.canThrowExceptions()) {
      for (OPT_Instruction inst = bblock.firstInstruction(); 
          inst != bblock.lastInstruction(); inst = inst.nextInstructionInCodeOrder()) {
        if (inst.isPEI() && 
            bblock.getApplicableExceptionalOut(inst).hasMoreElements()) {
          firstPEI = inst;
          // remember that this block has a PEI with a handler for use
          //  later in "processBlock"
          bbLiveInfo[bblock.getNumber()].setContainsPEIWithHandler(true);
          break;
        }
      }
    }

    // Get any uses from PHIs, which are in the successor blocks
    getUsesFromPhis(bblock);


    // Traverse instructions in reverse order within the basic block.
    for (OPT_Instruction inst = bblock.lastInstruction(); inst != bblock.firstInstruction(); 
         inst = inst.prevInstructionInCodeOrder()) {

      // traverse from defs to uses becauses uses happen after 
      // (in a backward sense) defs
      OPT_OperandEnumeration defs = inst.getPureDefs();
      while (defs.hasMoreElements()) {
        OPT_Operand def = defs.next();
        if (def instanceof OPT_RegisterOperand) {
          OPT_RegisterOperand regOp = (OPT_RegisterOperand)def;

          // Do we care about this reg?
          if (isSkippableReg(regOp,ir))
            continue;
          VM_TypeReference regType = regOp.type;

          // Because the summary we compute is used to propagate to other
          // basic blocks, if a register is block local, we don't need to
          // include it.  It will be picked up later by local propagation phase.
          if (regOp.register.spansBasicBlock() && regType != null) {

            // if it is a DEF we place it is the BBKillSet and remove it from
            // the GEN set, (GEN should only contain upward-exposed uses,
            // i.e., uses that are NOT dominated by a DEF).
            // We don't need to worry about PEIs here because 
            // later instructions (traversing backwards like we are) 
            // will always dominate earlier instructions *of this block*
            bbLiveInfo[bblock.getNumber()].BBKillSet().add(regOp);
            bbLiveInfo[bblock.getNumber()].getGen().remove(regOp);

            // However, if an exception can emanate from this basic block
            // we are not guaranteed that all instructions will be executed
            // in this block.  We allow killing of instructions
            // after the last (in a backwards sense) potential exception
            // throwing statement. (PEI)
            // If there are no PEIs in this block we don't bother to add
            if (seenFirstPEI) {
              bbLiveInfo[bblock.getNumber()].firstPEIKillSet().add(regOp);
            }
          }
        }
      }

      // Now process the uses, unless this is a PHI operator
      if (inst.operator() != PHI) {
        for (OPT_OperandEnumeration uses = inst.getUses(); 
            uses.hasMoreElements();) {
          OPT_Operand use = uses.next();
          if (use instanceof OPT_RegisterOperand) {
            OPT_RegisterOperand regOp = (OPT_RegisterOperand)use;

            // Do we care about this reg?
            if (isSkippableReg(regOp,ir))
              continue;

            VM_TypeReference regType = regOp.type;

            // Because the summary we compute is used to propagate to
            // other basic blocks, if a register is block local, 
            // we don't need to include it.  It will be picked up 
            // later by local propagation phase.
            if (regOp.register.spansBasicBlock() && regType != null) {
              bbLiveInfo[bblock.getNumber()].getGen().add(regOp);
            }
          }                     // is RegOp
        }       // foreach use
      }         // not a PHI
      // check whether the instruction we just processed is the first
      // (last, thinking backwards) exception-raising instruction.
      // If so, set the flag so we can start killing.
      if (firstPEI == inst) {
        seenFirstPEI = true;
      }
    }           // foreach instruction in block
    if (verbose) {
      System.out.println("  Gen: " + bbLiveInfo[bblock.getNumber()].getGen());
      System.out.println("  Kill: " 
          + bbLiveInfo[bblock.getNumber()].BBKillSet());
      System.out.println("  1st PEI Kill: " 
          + bbLiveInfo[bblock.getNumber()].firstPEIKillSet());
      System.out.println(" ---> Done computing Gen/Kill for block");
    }
  }

  /**
   * The rvals of phi nodes are logically uses in the phi's predecessor 
   * blocks, so here we collect phi rvals from the current block's
   * successors into the gen set for this block, being careful to
   * collect only the appropriate rval
   *
   * @param bblock the basic block of interest
   *
   * pre: Assumes the liveInfo array is allocated for this block
   * post: May add to liveInfo for this block
   */
  private void getUsesFromPhis(OPT_BasicBlock bblock) {
    Enumeration successors = bblock.getOut();
    while (successors.hasMoreElements()) {
      OPT_BasicBlock sb = (OPT_BasicBlock)successors.nextElement();
      if (sb.isExit())
        continue;

      for (OPT_Instruction phi = sb.firstInstruction(); 
           phi != sb.lastInstruction(); 
           phi = phi.nextInstructionInCodeOrder()) {
        if (phi.operator() == PHI) {
          for (int j = 0; j < Phi.getNumberOfValues(phi); j++) {
            OPT_BasicBlockOperand bbop = Phi.getPred(phi,j);
            if (bbop.block == bblock) {
              OPT_Operand myRval = Phi.getValue(phi, j);
              if (myRval instanceof OPT_RegisterOperand) {
                OPT_RegisterOperand regOp = (OPT_RegisterOperand)myRval;
                VM_TypeReference regType = regOp.type;
                if (regOp.register.spansBasicBlock() && regType != null) {
                  bbLiveInfo[bblock.getNumber()].getGen().add(regOp);
                }
              }                    
            }
          }
        }     
      }      
    }       
  }

  /**
   *  Compute the in set for this block given the out, gen, and kill set
   *  @param block the block of interest
   *  @param reuseCurrentSet whether we can reuse the "currentSet" or else
   *                         clear it out and recompute the meet of our succs
   *  @param ir the governing ir
   *
   */
  private boolean processBlock(OPT_BasicBlock block, boolean reuseCurrentSet,
                               OPT_IR ir) {
    if (verbose) {
      System.out.println(" *** processing block " + block + " # out edges: "
          + block.getNumberOfOut());
      block.printExtended();
    }

    // Used in processBlock as a summary of the IN sets of all
    // exception handlers for the block
    OPT_LiveSet exceptionBlockSummary = new OPT_LiveSet();
            
    boolean blockHasHandlers = 
      bbLiveInfo[block.getNumber()].getContainsPEIWithHandler();

    // The computation of the Kill set takes into consideration exception
    // semantics, i.e., that live information may flow into the middle
    // of a basic block due to implicit edges at exception points.
    // We keep 2 kill sets. 
    //     BBKillSet() contains variables that are killed if the block
    //              is exited in the normal fashion
    //     firstPEIKillSet() contains variables that are definitely killed  
    //              before the first PEI is executed. 
    //       This set contains only variables that are definitely
    //       killed in this block despite the implicit exception edges.
    //
    if (!reuseCurrentSet) {
      currentSet.clear();

      // visit each successor, if it is a regular successor, add
      // it to "currentSet".  If it is a handler block, add it to
      // ExceptionBlockSummary.
      for (OPT_BasicBlockEnumeration bbEnum = block.getOut(); 
          bbEnum.hasMoreElements();) {
        OPT_BasicBlock succ = (OPT_BasicBlock)bbEnum.next();

        // sometimes we may have a CFG edge to a handler, but no longer a
        //   PEI that can make the edge realizable.  Thus, we have two
        //   conditions in the following test.
        if (blockHasHandlers && succ.isExceptionHandlerBasicBlock()) {
          exceptionBlockSummary.add(bbLiveInfo[succ.getNumber()].getIn());
        } else {
          currentSet.add(bbLiveInfo[succ.getNumber()].getIn());
        }
      }
    }
    if (verbose) {
      System.out.println("\t Before applying transfor function:");
      System.out.println("\t currentSet: " + currentSet);
      System.out.println("\t exceptionBlockSummary: " + exceptionBlockSummary);
    }

    // At this point, currentSet contains the union of our normal successors'
    // IN sets.
    // Compute:    In = currentSet - BBKillSet
    //                  U (exceptionBlockSummary - firstPEIKillSet)   (1)
    //                  U Gen
    // Since firstPEIKillSet is a subset of BBKillSet, we don't need the
    // the parenthesis (and the intermediate set)
    //
    // If there are no handlers than exceptionBlockSummary is empty and
    // we don't need to perform line (1)
    // We compute Gen and Kill on demand
    if (bbLiveInfo[block.getNumber()].BBKillSet() == null) {
      bbLiveInfo[block.getNumber()].createKillAndGen();
      computeBlockGenAndKill(block,ir);
    }

    // currentSet = currentSet - BBKillSet
    // first kill off variables that are killed anywhere in the block
    currentSet.remove(bbLiveInfo[block.getNumber()].BBKillSet());
    if (blockHasHandlers) {

      // currentSet = currentSet U exceptionBlockSummary
      // add in the IN sets for the handlers
      currentSet.add(exceptionBlockSummary);

      // currentSet = currentSet - firstPEIKillSet
      // kill off variables that are definitely killed, i.e., killed before
      // the first PEI
      currentSet.remove(bbLiveInfo[block.getNumber()].firstPEIKillSet());
    }

    // currentSet = currentSet U gen
    // add in the GEN set
    currentSet.add(bbLiveInfo[block.getNumber()].getGen());

    // since we have monotonicity, we can add currentSet to
    // the previous In set.  If it results in an addition we have
    // a change and return true, otherwise return false.
    if (bbLiveInfo[block.getNumber()].getIn().add(currentSet)) {
      if (verbose) {
        System.out.println(" *** processBlock returning true, currentSet: "
            + currentSet);
      }
      return  true;
    } else {
      if (verbose) {
        System.out.println(" *** processBlock returning false, currentSet: "
            + currentSet);
      }
      return  false;
    }
  }

  /**
   *  This method performs the last phase of the analysis, local propagation.
   *  It uses the results from the fixed point analysis to determine the
   *  local live information within a basic block.
   *
   *  It walks the IR and, using the live information computed for each
   *  basic block, i.e., the results of the iterative solution, makes a single
   *  pass backward walk through the basic block, GENing and KILLing
   *  local information.  This produces the set of live variables at each
   *  instruction.
   *
   *  This information is saved into two data structures:
   *       - at all GC points, live references are recorded
   *       - at all instructions, live range information is recorded
   *
   *  @param ir the IR
   */
  private void performLocalPropagation(OPT_IR ir, boolean createGCMaps) {
    if (debug) {
      System.out.println(" .... starting local propagation\n");
    }
    Stack blockStack = null;
    if (createGCMaps) {
      // We want to add GC map entries in IR instruction order. However, we are 
      // visiting instructions in reverse order within a block. We solve this
      // by pushing all additions to a local stack and pop (and add)
      // the information to the GC map after the block has been processed.
      blockStack = new Stack();
    }
    for (OPT_BasicBlock block = ir.firstBasicBlockInCodeOrder(); 
         block != null; 
         block = block.nextBasicBlockInCodeOrder()) {
      if (verbose) {
        System.out.print(" ....   processing block # " + block.getNumber()
            + " ...");
      }

      // This set will hold the live variables for the current
      // instruction.  It is initialized from the "In" set of the block's
      // successors and updated during the walk up the basic block.
      OPT_LiveSet local = new OPT_LiveSet();

      // The union of the IN sets of all exception blocks that can
      // be reached (directly) from this block
      OPT_LiveSet exceptionBlockSummary = new OPT_LiveSet();

      // Create the out set by unioning the successors' in sets.
      // During this processing we should NOT include exception-catching
      // blocks, but instead remember them for use at execption-raising
      // statements in this block
      for (OPT_BasicBlockEnumeration bbEnum = block.getOut(); 
          bbEnum.hasMoreElements();) {
        OPT_BasicBlock succ = (OPT_BasicBlock)bbEnum.next();
        if (succ.isExceptionHandlerBasicBlock()) {
          exceptionBlockSummary.add(bbLiveInfo[succ.getNumber()].getIn());
        } else {
          local.add(bbLiveInfo[succ.getNumber()].getIn());
        }
      }
      if (verbose) {
        System.out.println(" Completed succ walk. exceptionBlockSummary: "
            + exceptionBlockSummary + "\n local: " + local);
      }

      // initialize live range for this block
      block.initializeLiveRange();

      // For each item in "local", create live interval info for this block.
      OPT_LiveInterval.createEndLiveRange(local, block, null);

      // Process the block, an instruction at a time.
      for (OPT_Instruction inst = block.lastInstruction(); inst != block.firstInstruction(); 
          inst = inst.prevInstructionInCodeOrder()) {
        if (verbose) {
          System.out.println("Processing: " + inst);
        }

        // If this instruction can raise an exception, then the catch
        // block can be a direct successor to it
        // To compensate this, we must first add in the live information
        // for all such blocks, which we computed above and stored
        // in "exceptionBlockSummary"
        if (inst.isPEI()) {
          local.add(exceptionBlockSummary);

          // For each item in "exceptionBlockSummary", create live interval 
          // info for this block.
          OPT_LiveInterval.createEndLiveRange(exceptionBlockSummary, block, 
              inst);
        }

        // compute In set for this instruction & GC point info
        // traverse from defs to uses, do "kill" then "gen" (backwards analysis)
        // Def loop
        for (OPT_OperandEnumeration defs = inst.getPureDefs(); 
            defs.hasMoreElements();) {
          OPT_Operand op = defs.next();
          if (op instanceof OPT_RegisterOperand) {
            OPT_RegisterOperand regOp = (OPT_RegisterOperand)op;
            // Currently, clients of live information do not need to know
            // about validation reges.  (Reg Alloc cares about physical regs.)
            if (isSkippableReg(regOp,ir))
              continue;
            if (regOp.type != null) {
              // process the def as a kill
              local.remove(regOp);
              if (verbose) {
                System.out.println("  Killing: " + regOp + "\n local: "
                                   + local);
              }

              // mark this instruction as the start of the live range for reg
              OPT_LiveInterval.setStartLiveRange(regOp.register, inst, block);
            }
          } // if operand is a Register
        }   // defs

        // GC Map Code:
        //
        // Now that we have processed all of the defs, if any, we check 
        // if the instruction is a potential GC point, insert it into 
        // the map.   
        // We do this after processing the defs (remember, this is a backward
        // analysis) because the GC will occur (at least in the case of calls) 
        // after the uses have occurred (in the forward sense).  Thus, we 
        // don't yet factor in the uses for this instruction.
        // For a statement like "x = y", we are capturing the program point 
        // "in the middle", i.e., during the execution, after the y has been 
        // fetched, but before the x has been defined.
                
                //-#if RVM_WITH_OSR
                // Above observation is not true for an OSR instruction. The current
                // design of the OSR instruction requires the compiler build a GC map
                // for variables used by the instruction.
                // Otherwise, the compiler generates an empty gc map for the instruction.
                // This results run away references if GC happens when a thread is being OSRed.
                //
                // TODO: better design of yieldpoint_osr instruction.
                // -- Feng July 15, 2003
                if (createGCMaps && !OsrPoint.conforms(inst) && inst.isGCPoint()) {               
                //-#else
        if (createGCMaps && inst.isGCPoint()) {
                //-#endif
          // make deep copy (and translate to regList) because we reuse
          // local above.
          // NOTE: this translation does some screening, see OPT_GCIRMap.java
          OPT_LinkedList regList = map.createDU(local);
          blockStack.push(new MapElement(inst, regList));
          if (verbose) { System.out.println("SAVING GC Map"); }
        }       // is GC instruction, and map not already made
                
        // now process the uses
        for (OPT_OperandEnumeration uses = inst.getUses(); 
            uses.hasMoreElements();) {
          OPT_Operand op = uses.next();
          if (op instanceof OPT_RegisterOperand) {
            OPT_RegisterOperand regOp = (OPT_RegisterOperand)op;
            // Do we care about this reg?
            if (isSkippableReg(regOp,ir))
              continue;
            VM_TypeReference regType = regOp.type;
            // see Def loop comment about magics
            if (regType != null) {
              // process the use as a gen
              local.add(regOp);
              if (verbose) {
                System.out.println("  Gen-ing: " + regOp);
                System.out.println("local: " + local);
              }
              // mark this instruction as the end of the live range for reg
              OPT_LiveInterval.createEndLiveRange(regOp.register, block, 
                  inst);
            }
          }     // if operand is a Register
        }     // uses

        //-#if RVM_WITH_OSR
        if (createGCMaps && OsrPoint.conforms(inst)) {
          
                  // delayed gc map generation for Osr instruction, 
                  // see comments before processing uses -- Feng, July 15, 2003
                  OPT_LinkedList regList = map.createDU(local);
          blockStack.push(new MapElement(inst, regList));

              // collect osr info using live set
          collectOsrInfo(inst, local);
        }
        //-#endif
      }     // end instruction loop

      // The register allocator prefers that any registers that are live
      // on entry be listed first.  This call makes it so.
      OPT_LiveInterval.moveUpwardExposedRegsToFront(block);
      if (createGCMaps) {
        // empty the stack, insert the information into the map
        while (!blockStack.isEmpty()) {
          MapElement elem = (MapElement)blockStack.pop();
          map.insert(elem.getInst(), elem.getList());
        }
      }

      if (storeLiveAtHandlers && block.isExceptionHandlerBasicBlock()) {
        OPT_ExceptionHandlerBasicBlock handlerBlock =
          (OPT_ExceptionHandlerBasicBlock)block;

        // use local because we need to get a fresh one anyway.
        handlerBlock.setLiveSet(local);
        local = new OPT_LiveSet();
      } else {

        // clear the local set for the next block
        local.clear();
      }
    }           // end basic block for loop
    if (debug) {
      System.out.println(" .... completed local propagation\n");
    }
  }

  /**
   * Should this register be included in the liveness solution?
   *
   * @param regOp the register operand to consider skipping
   * @param ir the governing ir
   * @return whether the register should be skipped, i.e., not be
   *          present in the liveness solution
   */
  private boolean isSkippableReg(OPT_RegisterOperand regOp, OPT_IR ir) {
    // The old test would exclude all physical registers.  However,
    // register allocation needs to know about physical registers, except
    // for the ones listed below.  Such regs are inserted in the IR
    // during call expansion. 
    if (regOp.register.isExcludedLiveA()
        || (regOp.register.isValidation() && skipGuards)) {
      return  true;
    }
    return  false;
  }

  /**
   * Just a helper method to encapsulate the optional debugging info
   * that is performed at the beginning of the perform method
   * @param ir  the IR
   * @param createGCMaps are we creating GC maps?
   */
  private void debugBegining(OPT_IR ir, boolean createGCMaps) {
    if (debug || dumpFixedPointResults 
        || dumpFinalMaps || dumpFinalLiveIntervals) {
      System.out.print("\n ====>  Performing liveness analysis ");
      if (createGCMaps) {
        System.out.print("and GC Maps ");
      }
      System.out.println("for method: " + ir.method.getName() + " in class: "
          + ir.method.getDeclaringClass() + "\n");
      System.out.println("  method has " 
          + ir.cfg.numberOfNodes() + " basic blocks");
    }
    if (debug || dumpFinalMaps) {
      System.out.println("**** START OF IR for method: " + ir.method.getName()
          + " in class: " + ir.method.getDeclaringClass());
      ir.printInstructions();
      System.out.println("**** END   OF IR INSTRUCTION DUMP ****");
    }
  }

  /**
   * Just a helper method to encapsulate the optional debugging info
   * that is performed after the global propagation step of "perform"
   *
   * @param ir the IR
   */
  private void debugPostGlobal(OPT_IR ir) {
    if (debug) {
      System.out.println(" .... Completed global live computation ....");
      if (verbose) {
        // Print the basic blocks
        System.out.println(" .... CFG:");
        System.out.println(ir.cfg);
        OPT_VCG.printVCG(ir.method + ".vcg", ir.cfg);
        // Print the results for basic blocks
        printFixedPointResults(ir);
      }
    }
    if (dumpFixedPointResults) {
      printFixedPointResults(ir);
    }
  }

  /**
   *  Prints the results of the fixed point computation.
   *  (Use printFinalMaps for the final GC map)
   *  @param ir the IR
   */
  private void printFixedPointResults(OPT_IR ir) {
    System.out.println("\n  ***** Fixed point results for IR-based GC Maps for "
        + ir.method.getDeclaringClass() + "." + ir.method.getName());
    int length = bbLiveInfo.length;
    for (int i = 0; i < length; i++) {
      System.out.println("Live Info for Block #" + i);
      System.out.println(bbLiveInfo[i]);
    }
  }

  /**
   * Prints the final maps
   * @param ir
   */
  private void printFinalMaps(OPT_IR ir) {
    System.out.println("\n  =-=-=-=-=- Final IR-based GC Maps for " + 
        ir.method.getDeclaringClass() + "." + ir.method.getName());
    map.dump();
    System.out.println("  =-=-=-=-=- End Final IR-based GC Maps\n");
  }

  /**
   *  Dumps the GC map, see printFinalMaps
   */
  public void dumpMap() {
    if (VM.VerifyAssertions)
      VM._assert(map != null);
    map.dump();
  }

  /**
   * Prints the Final Live Intervals
   * @param ir the IR
   */
  private void printFinalLiveIntervals(OPT_IR ir) {
    ir.printInstructions();
    System.out.println("\n  *+*+*+*+*+ Final Live Intervals for " 
                       + ir.method.getDeclaringClass() + "." + ir.method.getName());
    for (OPT_BasicBlock block = ir.firstBasicBlockInCodeOrder(); block
        != null; block = block.nextBasicBlockInCodeOrder()) {
      OPT_LiveInterval.printLiveIntervalList(block);
    }
    System.out.println("  *+*+*+*+*+ End Final Live Intervals\n");
  }


  /**
   * REturns the live information for a particular block
   * @param bb the basic block of interest
   * @return  the live information for the block
   */
  BBLiveElement getLiveInfo(OPT_BasicBlock bb) {
    return  bbLiveInfo[bb.getNumber()];
  }

  /**
   * Return the set of registers that are live on the control-flow edge 
   * basic block bb1 to basic block bb2
   */
  HashSet getLiveRegistersOnEdge(OPT_BasicBlock bb1, OPT_BasicBlock bb2) {
    HashSet s1 = getLiveRegistersOnExit(bb1);
    HashSet s2 = getLiveRegistersOnEntry(bb2);
    s1.retainAll(s2);
    return s1;
  }

  /**
   * Return the set of registers that are live across a basic block, and who 
   * are live after the basic block exit.
   */   
  HashSet getLiveRegistersOnExit(OPT_BasicBlock bb) {
    HashSet result = new HashSet(10);
    for (Enumeration e = bb.enumerateLiveIntervals(); e.hasMoreElements(); ){
      OPT_LiveIntervalElement lie = (OPT_LiveIntervalElement)e.nextElement();
      OPT_Instruction end = lie.getEnd(); 
      if (end == null) result.add(lie.getRegister());
    }
    return result;
  }
  /**
   * Return the set of registers that are live across a basic block, and who 
   * are live before the basic block entry.
   */   
  HashSet getLiveRegistersOnEntry(OPT_BasicBlock bb) {
    HashSet result = new HashSet(10);
    for (Enumeration e = bb.enumerateLiveIntervals(); e.hasMoreElements(); ){
      OPT_LiveIntervalElement lie = (OPT_LiveIntervalElement)e.nextElement();
      OPT_Instruction begin = lie.getBegin(); 
      if (begin == null) result.add(lie.getRegister());
    }
    return result;
  }

  // A simple class used to store live info
  static final class BBLiveElement {
    private OPT_LiveSet gen;
    private OPT_LiveSet BBKillSet;
    private OPT_LiveSet firstPEIKillSet;
    private OPT_LiveSet in;
    private boolean containsPEIWithHandler = false;   

    /**
     *  The constructor
     */
    BBLiveElement() {
      in = new OPT_LiveSet();
    }

    /**
     * Returns the kill set
     * @return  the Kill set for this block
     */
    public final OPT_LiveSet BBKillSet() {
      return  BBKillSet;
    }

    /**
     * Returns the first PEI kill set, i.e., the Kill set up to the first PEI
     * @return the Kill set up to the first PEI
     */
    public final OPT_LiveSet firstPEIKillSet() {
      return  firstPEIKillSet;
    }

    /**
     * Returns the Gen set
     * @return  the Gen set for this block
     */
    public final OPT_LiveSet getGen() {
      return  gen;
    }

    /**
     * Returns the In set
     * @return the In set for this block
     */
    public final OPT_LiveSet getIn() {
      return  in;
    }

    /**
     * Returns whether this block has a PEI with a handler in this method
     * @return whether this block has a PEI with a handler in this method
     */
    public final boolean getContainsPEIWithHandler() {
      return containsPEIWithHandler;
    }

    /**
     * @param value whether this block has a PEI with a handler in this method
     */
    public final void setContainsPEIWithHandler(boolean value) {
      containsPEIWithHandler = value;
    }

    /**
     * creates (allocates) the Gen and Kill Sets
     */
    public final void createKillAndGen () {
      BBKillSet = new OPT_LiveSet();
      firstPEIKillSet = new OPT_LiveSet();
      gen = new OPT_LiveSet();
    }

    /**
     * DEPRECIATED: Don't Use
     */
    public final OPT_LiveSet gen() {
      return  gen;
    }

    /**
     * DEPRECIATED: Don't Use
     */
    public final OPT_LiveSet in() {
      return  in;
    }

    /**
     * creates a string representation of this object
     * @return string representation of this object
     */
    public String toString() {
      StringBuffer buf = new StringBuffer("");
      ;
      buf.append(" Gen: " + gen + "\n");
      buf.append(" BB Kill: " + BBKillSet + "\n");
      buf.append(" first PEI Kill: " + firstPEIKillSet + "\n");
      buf.append(" In: " + in + "\n");
      return  buf.toString();
    }

  }

  /**
   * A simple class used just in this file when creating GC maps
   */
  static final class MapElement {

    /**
     * constructor
     * @param inst 
     * @param list
     */
    public MapElement(OPT_Instruction inst, OPT_LinkedList list) {
      this.inst = inst;
      this.list = list;
    }

    /**
     * returns the instruction
     * @return the instruction
     */
    public final OPT_Instruction getInst() {
      return  inst;
    }

    /**
     * returns the list
     * @return the list 
     */
    public final OPT_LinkedList getList() {
      return  list;
    }
    private OPT_Instruction inst;
    private OPT_LinkedList list;
  }

  //-#if RVM_WITH_OSR
  /* collect osr info according to live information */
  private void collectOsrInfo(OPT_Instruction inst, OPT_LiveSet lives) {
    // create an entry to the OSRIRMap, order: callee => caller
    LinkedList mvarList = new LinkedList();
 
    // get the type info for locals and stacks
    OPT_InlinedOsrTypeInfoOperand typeInfo =
      OsrPoint.getInlinedTypeInfo(inst);
 
    /* iterator over type info and create LocalRegTuple
     * for each variable.
     * NOTE: we do not process LONG type register operand here,
     * which was splitted in BURS.
     */
    byte[][] ltypes = typeInfo.localTypeCodes;
    byte[][] stypes = typeInfo.stackTypeCodes;

    int nummeth = typeInfo.methodids.length;

    int elm_idx = 0; 
    int snd_long_idx = typeInfo.validOps;
    for (int midx = 0; midx < nummeth; midx++) {

      LinkedList tupleList = new LinkedList();

      byte[] ls = ltypes[midx];
      byte[] ss = stypes[midx];

      /* record maps for local variables, skip dead ones */
      for (int i=0, n=ls.length; i<n; i++) {
        if (ls[i] != VoidTypeCode) {
          // check liveness
          int cur_idx = elm_idx;
          OPT_Operand op = OsrPoint.getElement(inst, elm_idx++);
          OSR_LocalRegPair tuple =
            new OSR_LocalRegPair(OSR_Constants.LOCAL, i, ls[i], op);
          // put it in the list
          tupleList.add(tuple);

          // get another half of a long type operand
          if (VM.BuildFor32Addr && (ls[i] == LongTypeCode)) {
            OPT_Operand other_op = OsrPoint.getElement(inst, snd_long_idx++);
            tuple._otherHalf = 
              new OSR_LocalRegPair(OSR_Constants.LOCAL, i, ls[i], other_op);

          }
        }
      }
 
      /* record maps for stack slots */
      for (int i=0, n=ss.length; i<n; i++) {
        if (ss[i] != VoidTypeCode) {
          OSR_LocalRegPair tuple =
            new OSR_LocalRegPair(OSR_Constants.STACK,
                                    i,
                                    ss[i],
                                    OsrPoint.getElement(inst, elm_idx++));
 
          tupleList.add(tuple);

          if (VM.BuildFor32Addr && (ss[i] == LongTypeCode)) {
            tuple._otherHalf = 
              new OSR_LocalRegPair(OSR_Constants.STACK,
                                      i,
                                      ss[i],
                              OsrPoint.getElement(inst, snd_long_idx++));
          }
        }
      }

      // create OSR_MethodVariables    
      OSR_MethodVariables mvar =
        new OSR_MethodVariables(typeInfo.methodids[midx],
                                   typeInfo.bcindexes[midx],
                                   tupleList);
      mvarList.add(mvar);
    }

    // put the method variables for this OSR in the osrMap, encoding later.
    osrMap.insertFirst(inst, mvarList);
  }
  //-#endif RVM_WITH_OSR
}



// Previously we used to be concerned that we didn't lose any
// precision due to imprecise modeling of exceptions.
// However, the troublesome situation cannot occur in Java
// because the Java SPEC forces the compiler to declare a variable
// as uninitialized.
//
// Conside the following ***ILLEGAL*** example:
//
//  static void main(String arg[]) {
//    Object x;
//    try {
//      foo();
//      x = null;
//      bar();
//    }
//    catch (FooException e) {
//    }
//
//    catch (BarException e) {
//      Object a = x;
//    }
//  }
//
//  Here x is live in the Bar catch block, but not above the assignment
//  in the try block.  Recall that we only kill off values coming from
//  catch blocks if they are in the first PEI region (above the call
//  to foo).  Thus, our analysis will conservatively record that x
//  is live above the assignment.
//
//  However, the Java SPEC requires compilers to state that x is uninitialized
//  here, even though it isn't.  Thus, this scenario cannot occur.
//  Basically, every variable used in a catch block needs to be defined
//  before the TRY statement.  (The SPEC doesn't explicitly state this, but
//  on page 398 (Sec 16.2.13) it says that
//     every variable used in a *finally* block needs to be defined
//  before the TRY statement, which is even more restrictive.
//  Bottomline: losing precision is not a concern!

