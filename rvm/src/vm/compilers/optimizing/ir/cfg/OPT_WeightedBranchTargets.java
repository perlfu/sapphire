/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.*;

/**
 * Used to iterate over the branch targets (including the fall through edge) 
 * and associated probabilites of a basic block.  
 * Takes into account the ordering of branch instructions when 
 * computing the edge weights such that the total target weight will always 
 * be equal to 1.0 (flow in == flow out).  
 *
 * @author Dave Grove
 */
public final class OPT_WeightedBranchTargets {
  private OPT_BasicBlock[] targets;
  private float[] weights;
  private int cur;
  private int max;
  
  public void reset() { cur = 0; }
  public boolean hasMoreElements() { return cur < max; }
  public void advance() { cur++; }
  public OPT_BasicBlock curBlock() { return targets[cur]; }
  public float curWeight() { return weights[cur]; }
  
  public OPT_WeightedBranchTargets(OPT_BasicBlock bb) {
    targets = new OPT_BasicBlock[3];
    weights = new float[3];
    cur = 0;
    max = 0;

    float prob = 1f;
    for (OPT_InstructionEnumeration ie = bb.enumerateBranchInstructions();
         ie.hasMoreElements();) {
      OPT_Instruction s = ie.next();
      if (IfCmp.conforms(s)) {
        OPT_BasicBlock target = IfCmp.getTarget(s).target.getBasicBlock();
        OPT_BranchProfileOperand prof = IfCmp.getBranchProfile(s);
        float taken = prob * prof.takenProbability;
        prob = prob * (1f - prof.takenProbability);
        addEdge(target, taken);
      } else if (Goto.conforms(s)) {
        OPT_BasicBlock target = Goto.getTarget(s).target.getBasicBlock();
        addEdge(target, prob);
      } else if (InlineGuard.conforms(s)) {
        OPT_BasicBlock target = InlineGuard.getTarget(s).target.getBasicBlock();
        OPT_BranchProfileOperand prof = InlineGuard.getBranchProfile(s);
        float taken = prob * prof.takenProbability;
        prob = prob * (1f - prof.takenProbability);
        addEdge(target, taken);
      } else if (IfCmp2.conforms(s)) {
        OPT_BasicBlock target = IfCmp2.getTarget1(s).target.getBasicBlock();
        OPT_BranchProfileOperand prof = IfCmp2.getBranchProfile1(s);
        float taken = prob * prof.takenProbability;
        prob = prob * (1f - prof.takenProbability);
        addEdge(target, taken);
        target = IfCmp2.getTarget2(s).target.getBasicBlock();
        prof = IfCmp2.getBranchProfile2(s);
        taken = prob * prof.takenProbability;
        prob = prob * (1f - prof.takenProbability);
        addEdge(target, taken);
      } else if (TableSwitch.conforms(s)) {
        int lowLimit = TableSwitch.getLow(s).value;
        int highLimit = TableSwitch.getHigh(s).value;
        int number = highLimit - lowLimit + 1;
        float total = 0f;
        for (int i=0; i<number; i++) {
          OPT_BasicBlock target = TableSwitch.getTarget(s, i).target.getBasicBlock();
          OPT_BranchProfileOperand prof = TableSwitch.getBranchProfile(s, i);
          float taken = prob * prof.takenProbability;
          total += prof.takenProbability;
          addEdge(target, taken);
        }
        OPT_BasicBlock target = TableSwitch.getDefault(s).target.getBasicBlock();
        OPT_BranchProfileOperand prof = TableSwitch.getDefaultBranchProfile(s);
        float taken = prob * prof.takenProbability;
        total += prof.takenProbability;
        if (VM.VerifyAssertions && !epsilon(total, 1f)) {
          VM.sysFail("Total outflow ("+total+") does not sum to 1 for: "+s);
        }
        addEdge(target, taken);
      } else if (LowTableSwitch.conforms(s)) {
        int number = LowTableSwitch.getNumberOfTargets(s);
        float total = 0f;
        for (int i=0; i<number; i++) {
          OPT_BasicBlock target = LowTableSwitch.getTarget(s, i).target.getBasicBlock();
          OPT_BranchProfileOperand prof = LowTableSwitch.getBranchProfile(s, i);
          float taken = prob * prof.takenProbability;
          total += prof.takenProbability;
          addEdge(target, taken);
        }
        if (VM.VerifyAssertions && !epsilon(total, 1f)) {
          VM.sysFail("Total outflow ("+total+") does not sum to 1 for: "+s);
        }
      } else if (LookupSwitch.conforms(s)) {
        int number = LookupSwitch.getNumberOfTargets(s);
        float total = 0f;
        for (int i=0; i<number; i++) {
          OPT_BasicBlock target = LookupSwitch.getTarget(s, i).target.getBasicBlock();
          OPT_BranchProfileOperand prof = LookupSwitch.getBranchProfile(s, i);
          float taken = prob * prof.takenProbability;
          total += prof.takenProbability;
          addEdge(target, taken);
        }
        OPT_BasicBlock target = LookupSwitch.getDefault(s).target.getBasicBlock();
        OPT_BranchProfileOperand prof = LookupSwitch.getDefaultBranchProfile(s);
        float taken = prob * prof.takenProbability;
        total += prof.takenProbability;
        if (VM.VerifyAssertions && !epsilon(total, 1f)) {
          VM.sysFail("Total outflow ("+total+") does not sum to 1 for: "+s);
        }
        addEdge(target, taken);
      } else {
        throw new OPT_OptimizingCompilerException("TODO "+s+"\n");
      }
    }
    OPT_BasicBlock ft = bb.getFallThroughBlock();
    if (ft != null) addEdge(ft, prob);
  }

  private void addEdge(OPT_BasicBlock target, float weight) {
    if (max == targets.length) {
      OPT_BasicBlock[] tmp = new OPT_BasicBlock[targets.length << 1];
      for (int i=0; i<targets.length; i++) {
        tmp[i] = targets[i];
      }
      targets = tmp;
      float [] tmp2 = new float[weights.length << 1];
      for (int i=0; i<weights.length; i++) {
        tmp2[i] = weights[i];
      }
      weights = tmp2;
    }
    targets[max] = target;
    weights[max] = weight;
    max++;
  }

  private boolean epsilon(float a, float b) {
    return Math.abs(a - b) < 0.001;
  }
}



