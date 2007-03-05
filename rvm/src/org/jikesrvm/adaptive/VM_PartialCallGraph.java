/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2004
 */
package org.jikesrvm.adaptive;

import org.jikesrvm.VM;
import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;
import org.jikesrvm.classloader.*;

import java.util.*;
import java.io.*;

/**
 * A partial call graph (PCG) is a partial mapping from callsites
 * to weighted targets.
 *
 * @author Dave Grove
 */
public final class VM_PartialCallGraph implements VM_Decayable,
                                                  VM_Reportable {

  /**
   * The dynamic call graph, which is a mapping from
   * VM_CallSites to VM_WeightedCallTargets.
   */
  private final HashMap<VM_CallSite,VM_WeightedCallTargets> callGraph = 
    new HashMap<VM_CallSite,VM_WeightedCallTargets>();
  private final HashMap<VM_UnResolvedCallSite,VM_UnResolvedWeightedCallTargets> unresolvedCallGraph = 
    new HashMap<VM_UnResolvedCallSite,VM_UnResolvedWeightedCallTargets>();

  /**
   * sum of all edge weights in the call graph
   */
  private double totalEdgeWeights;

  /**
   * Initial seed weight; saved for use in the reset method
   */
  private final double seedWeight;
  
  /**
   * Create a partial call graph.
   * @param initialWeight an initial value for totalEdgeWeights.
   *        Used by AOS to cause inlining based in the dynamic call graph
   *        to initially be conservative until sufficient samples have
   *        accumulated that there is more confidence in the accuracy
   *        of the call graph.
   */
  public VM_PartialCallGraph(double initialWeight) {
    seedWeight = initialWeight; // save for rest function
    totalEdgeWeights = initialWeight;
  }

  /**
   * Reset data
   */
  public synchronized void reset() {
    callGraph.clear();
    totalEdgeWeights = seedWeight;
  }
  
  /**
   * @return sum of all edge weights in the partial call graph
   */
  double getTotalEdgeWeights() { return totalEdgeWeights; }

  /**
   * Visit the WeightedCallTargets for every call site send them the
   * decay message.
   */
  public synchronized void decay() { 
    double rate = VM_Controller.options.DCG_DECAY_RATE;
    // if we are dumping dynamic call graph, don't decay the graph
    if (VM_Controller.options.DYNAMIC_CALL_FILE_OUTPUT != null) return;

    for (VM_WeightedCallTargets ct : callGraph.values()) {
      ct.decay(rate);
    }
    totalEdgeWeights /= rate;
  }
  
  /**
   * @param caller caller method
   * @param bcIndex bytecode index in caller method
   * @return the VM_WeightedCallTargets currently associated with the
   *         given caller bytecodeIndex pair.
   */
  public VM_WeightedCallTargets getCallTargets(VM_Method caller, int bcIndex) {
    VM_MethodReference callerRef=caller.getMemberRef().asMethodReference();
    VM_UnResolvedWeightedCallTargets unresolvedTargets = unresolvedCallGraph.get(new VM_UnResolvedCallSite(callerRef, bcIndex));
    if (unresolvedTargets != null) {
      final VM_Method fCaller= caller;
      final int fBcIndex=bcIndex;
      final VM_PartialCallGraph pg=this;
      unresolvedTargets.visitTargets(new VM_UnResolvedWeightedCallTargets.Visitor() {
          public void visit(VM_MethodReference calleeRef, double weight) {
            VM_Method callee = calleeRef.getResolvedMember();
            if (callee != null) {
              pg.incrementEdge(fCaller, fBcIndex, callee, (float)weight);
            }
          }
        });
    }
    return getCallTargets(new VM_CallSite(caller, bcIndex));
  }

  /**
   * @param callSite the callsite to look for
   * @return the VM_WeightedCallTargets currently associated with callSite.
   */
  public synchronized VM_WeightedCallTargets getCallTargets(VM_CallSite callSite) {
    return callGraph.get(callSite);
  }

  /**
   * Increment the edge represented by the input parameters, 
   * creating it if it is not already in the call graph.
   *
   * @param caller   method making the call
   * @param bcIndex  call site, if -1 then no call site is specified.
   * @param callee   method called
   */
  public synchronized void incrementEdge(VM_Method caller, int bcIndex, VM_Method callee) {
    augmentEdge(caller, bcIndex, callee, 1);
  }

  /**
   * Increment the edge represented by the input parameters, 
   * creating it if it is not already in the call graph.
   *
   * @param caller   method making the call
   * @param bcIndex  call site, if -1 then no call site is specified.
   * @param callee   method called
   * @param weight   the frequency of this calling edge
   */
  public synchronized void incrementEdge(VM_Method caller, int bcIndex, VM_Method callee, float weight) {
    augmentEdge(caller, bcIndex, callee, (double) weight);
  }

  /**
   * For the calling edge we read from a profile, we may not have 
   * the methods loaded in yet. Therefore, we will record the method 
   * reference infomation first, the next time we resolved the method, 
   * we will promote it into the regular call graph.
   * Increment the edge represented by the input parameters, 
   * creating it if it is not already in the call graph.
   *
   * @param callerRef   method making the call
   * @param bcIndex     call site, if -1 then no call site is specified.
   * @param calleeRef   method called
   * @param weight      the frequency of this calling edge
   */
  public synchronized void incrementUnResolvedEdge(VM_MethodReference callerRef, int bcIndex, VM_MethodReference calleeRef, float weight) {
    VM_UnResolvedCallSite callSite = new VM_UnResolvedCallSite(callerRef, bcIndex);
    VM_UnResolvedWeightedCallTargets targets = unresolvedCallGraph.get(callSite);
    if (targets == null) {
      targets = VM_UnResolvedWeightedCallTargets.create(calleeRef, weight);
      unresolvedCallGraph.put(callSite, targets);
    } else {
      VM_UnResolvedWeightedCallTargets orig = targets;
      targets = targets.augmentCount(calleeRef, weight);
      if (orig != targets) {
        unresolvedCallGraph.put(callSite, targets);
      }
    }
  }

  /**
   * Increment the edge represented by the input parameters, 
   * creating it if it is not already in the call graph.
   *
   * @param caller   method making the call
   * @param bcIndex  call site, if -1 then no call site is specified.
   * @param callee   method called
   * @param weight   the frequency of this calling edge
   */
  private void augmentEdge(VM_Method caller,
                           int bcIndex,
                           VM_Method callee,
                           double weight) {
    VM_CallSite callSite = new VM_CallSite(caller, bcIndex);
    VM_WeightedCallTargets targets = callGraph.get(callSite);
    if (targets == null) {
      targets = VM_WeightedCallTargets.create(callee, weight);
      callGraph.put(callSite, targets);
    } else {
      VM_WeightedCallTargets orig = targets;
      targets = targets.augmentCount(callee, weight);
      if (orig != targets) {
        callGraph.put(callSite, targets);
      }
    }
    totalEdgeWeights += weight;
  }

  /**
   * Dump out set of edges in sorted order.
   */
  public synchronized void report() {
    System.out.println("Partial Call Graph");
    System.out.println("  Number of callsites "+callGraph.size()+
                       ", total weight: "+totalEdgeWeights);
    System.out.println();
    
    TreeSet<VM_CallSite> tmp = new TreeSet<VM_CallSite>(new OrderByTotalWeight());
    tmp.addAll(callGraph.keySet());

    for (final VM_CallSite cs : tmp) {
      VM_WeightedCallTargets ct = callGraph.get(cs);
      ct.visitTargets(new VM_WeightedCallTargets.Visitor() {
          public void visit(VM_Method callee, double weight) {
            System.out.println(weight+" <"+cs.getMethod()+", "+cs.getBytecodeIndex()+", "+callee+">");
          }
        });
      System.out.println();
    }
  }
  /**
   * Dump all profile data to the given file
   */
  public synchronized void dumpGraph() {
    dumpGraph(VM_Controller.options.DYNAMIC_CALL_FILE_OUTPUT);
  }

  /**
   * Dump all profile data to the given file
   * @param fn output file name
   */
  public synchronized void dumpGraph(String fn) {
    final BufferedWriter f;
    try {
      f =  new BufferedWriter(new 
        OutputStreamWriter(new FileOutputStream(fn),"ISO-8859-1"));
    } catch (IOException e) {
      VM.sysWrite("\n\nVM_PartialCallGraph.dumpGraph: Error opening output file!!\n\n");
      return;
    }
    TreeSet<VM_CallSite> tmp = new TreeSet<VM_CallSite>(new OrderByTotalWeight());
    tmp.addAll(callGraph.keySet());

    for (final VM_CallSite cs : tmp) {
      VM_WeightedCallTargets ct = callGraph.get(cs);
      ct.visitTargets(new VM_WeightedCallTargets.Visitor() {
          public void visit(VM_Method callee, double weight) {
            VM_CodeArray callerArray = cs.getMethod().getCurrentEntryCodeArray();
            VM_CodeArray calleeArray = callee.getCurrentEntryCodeArray();
            try {
              f.write("CallSite "+
                      cs.getMethod().getMemberRef() +" " +
                      callerArray.length() + " " +
                      +cs.getBytecodeIndex()+" "+
                      callee.getMemberRef() +" "+
                      +calleeArray.length()+" weight: "+ weight+"\n");
              f.flush();
            } catch (IOException exc) {
              System.err.println("I/O error writing to dynamic call graph profile.");
            }
          }
        });
    }
  }

  /**
   * Used to compare two call sites by total weight.
   * @author robing
   *
   */
  private final class OrderByTotalWeight implements Comparator<VM_CallSite> {
    public int compare(VM_CallSite o1, VM_CallSite o2) {
      if (o1.equals(o2)) return 0;
      double w1 = callGraph.get(o1).totalWeight();
      double w2 = callGraph.get(o2).totalWeight();
      if (w1 < w2) { return 1; }
      if (w1 > w2) { return -1; }
      // equal weights; sort lexicographically
      return o1.toString().compareTo(o2.toString());
    }
  }

}
