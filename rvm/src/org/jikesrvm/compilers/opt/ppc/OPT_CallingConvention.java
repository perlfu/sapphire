/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ppc;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.OPT_DefUse;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.MIR_Call;
import org.jikesrvm.compilers.opt.ir.MIR_Load;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.MIR_Return;
import org.jikesrvm.compilers.opt.ir.MIR_Store;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.OPT_DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_FloatConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_IRTools;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_OperandEnumeration;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_MOVE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_MOVE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_MOVE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_STORE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IR_PROLOGUE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_MOVE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_FMR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_LAddr;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_LFD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_LFS;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_LInt;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_MOVE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_STAddr;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_STFD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_STFS;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_STW;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_LOAD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_STORE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.SYSCALL;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.Prologue;
import org.jikesrvm.compilers.opt.ir.Store;
import org.jikesrvm.compilers.opt.ir.ppc.OPT_PhysicalRegisterSet;
import static org.jikesrvm.compilers.opt.ppc.OPT_PhysicalRegisterConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.compilers.opt.ppc.OPT_PhysicalRegisterConstants.BYTES_IN_DOUBLE;
import static org.jikesrvm.compilers.opt.ppc.OPT_PhysicalRegisterConstants.BYTES_IN_FLOAT;
import static org.jikesrvm.compilers.opt.ppc.OPT_PhysicalRegisterConstants.BYTES_IN_INT;
import static org.jikesrvm.compilers.opt.ppc.OPT_PhysicalRegisterConstants.FIRST_DOUBLE_PARAM;
import static org.jikesrvm.compilers.opt.ppc.OPT_PhysicalRegisterConstants.FIRST_DOUBLE_RETURN;
import static org.jikesrvm.compilers.opt.ppc.OPT_PhysicalRegisterConstants.FIRST_INT_PARAM;
import static org.jikesrvm.compilers.opt.ppc.OPT_PhysicalRegisterConstants.FIRST_INT_RETURN;
import static org.jikesrvm.compilers.opt.ppc.OPT_PhysicalRegisterConstants.LOG_BYTES_IN_ADDRESS;
import static org.jikesrvm.compilers.opt.ppc.OPT_PhysicalRegisterConstants.NUMBER_DOUBLE_PARAM;
import static org.jikesrvm.compilers.opt.ppc.OPT_PhysicalRegisterConstants.NUMBER_INT_PARAM;
import static org.jikesrvm.ppc.VM_StackframeLayoutConstants.STACKFRAME_HEADER_SIZE;
import static org.jikesrvm.ppc.VM_StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET;
import org.vmmagic.unboxed.Offset;

/**
 * This class contains PowerPC Calling conventions.
 * The two public methods are:
 * <ul>
 *  <li> expandCallingConventions(OPT_IR) which is called by
 *      the register allocator immediately before allocation to make
 *      manifest the use of registers by the calling convention.
 *  <li> expandSysCall(OPT_Instruction, OPT_IR) which is called to
 *      expand a SYSCALL HIR instruction into the appropriate
 *      sequence of LIR instructions.
 *  </ul>
 */
public abstract class OPT_CallingConvention extends OPT_IRTools {

  /**
   * Expand calls, returns, and add initialize code for arguments/parms.
   * @param ir
   */
  public static void expandCallingConventions(OPT_IR ir) {
    for (OPT_Instruction inst = ir.firstInstructionInCodeOrder(); inst != null; inst =
        inst.nextInstructionInCodeOrder()) {
      if (inst.isCall()) {
        callExpand(inst, ir);
      } else if (inst.isReturn()) {
        returnExpand(inst, ir);
      }
    }
    prologueExpand(ir);
  }

  /**
   * This is just called for instructions that were added by
   * instrumentation during register allocation
   */
  public static void expandCallingConventionsForInstrumentation(OPT_IR ir, OPT_Instruction from, OPT_Instruction to) {
    for (OPT_Instruction inst = from; inst != to; inst = inst.nextInstructionInCodeOrder()) {
      if (inst.isCall()) {
        callExpand(inst, ir);
      } else if (inst.isReturn()) {
        returnExpand(inst, ir);
      }
    }
  }

  /**
   * Calling convention to implement calls to
   * native (C) routines using the AIX linkage conventions
   */
  public static void expandSysCall(OPT_Instruction s, OPT_IR ir) {
    OPT_RegisterOperand ip = (OPT_RegisterOperand) Call.getClearAddress(s);
    int numberParams = Call.getNumberOfParams(s);

    /* Long, float, and double constants are loaded from the JTOC.
     * We must ensure that they are loaded _before_ we change
     * the JTOC to be the C TOC so inject explicit moves to do so.
     */
    for (int i = 0; i < numberParams; i++) {
      OPT_Operand arg = Call.getParam(s, i);
      if (arg instanceof OPT_LongConstantOperand) {
        OPT_LongConstantOperand op = (OPT_LongConstantOperand) Call.getClearParam(s, i);
        OPT_RegisterOperand rop = ir.regpool.makeTempLong();
        s.insertBefore(Move.create(LONG_MOVE, rop, op));
        Call.setParam(s, i, rop.copy());
      } else if (arg instanceof OPT_DoubleConstantOperand) {
        OPT_DoubleConstantOperand op = (OPT_DoubleConstantOperand) Call.getClearParam(s, i);
        OPT_RegisterOperand rop = ir.regpool.makeTempDouble();
        s.insertBefore(Move.create(DOUBLE_MOVE, rop, op));
        Call.setParam(s, i, rop.copy());
      } else if (arg instanceof OPT_FloatConstantOperand) {
        OPT_FloatConstantOperand op = (OPT_FloatConstantOperand) Call.getClearParam(s, i);
        OPT_RegisterOperand rop = ir.regpool.makeTempFloat();
        s.insertBefore(Move.create(FLOAT_MOVE, rop, op));
        Call.setParam(s, i, rop.copy());
      }
    }

    /* compute the parameter space */
    int parameterWords;
    if (VM.BuildFor32Addr) {
      parameterWords = 0;
      for (int i = 0; i < numberParams; i++) {
        parameterWords++;
        OPT_Operand op = Call.getParam(s, i);
        if (op instanceof OPT_RegisterOperand) {
          OPT_RegisterOperand reg = (OPT_RegisterOperand) op;
          if (reg.type.isLongType() || reg.type.isDoubleType()) {
            parameterWords++;
          }
        }
      }
    } else {
      parameterWords = numberParams;
    }
    // see PowerPC Compiler Writer's Guide, pp. 162
    ir.stackManager.allocateParameterSpace((6 + parameterWords) * BYTES_IN_ADDRESS);
    // IMPORTANT WARNING: as the callee C routine may destroy the cmid field
    // (it is the saved CR field of the callee in C convention)
    // we are restoring the methodID after a sysCall.
    OPT_Instruction s2 =
        Store.create(REF_STORE,
                     ir.regpool.makeJTOCOp(ir, s),
                     ir.regpool.makeFPOp(),
                     AC(Offset.fromIntSignExtend(5 * BYTES_IN_ADDRESS)),
                     null);         // TODO: valid location?
    s.insertBefore(s2);
    if (VM.BuildForPowerOpenABI) {
      s2 =
          Load.create(REF_LOAD, ir.regpool.makeJTOCOp(ir, s), ip, AC(Offset.fromIntZeroExtend(BYTES_IN_ADDRESS)), null);
      s.insertBefore(s2);
      OPT_RegisterOperand iptmp = ir.regpool.makeTempAddress();
      s2 = Load.create(REF_LOAD, iptmp, ip, AC(Offset.zero()), null);
      s.insertBefore(s2);
      ip = iptmp;
    }
    Call.mutate0(s, SYSCALL, Call.getClearResult(s), ip, null);
    s2 =
        Load.create(REF_LOAD,
                    ir.regpool.makeJTOCOp(ir, s),
                    ir.regpool.makeFPOp(),
                    AC(Offset.fromIntSignExtend(5 * BYTES_IN_ADDRESS)),
                    null);         // TODO: valid location?
    s.insertAfter(s2);
    OPT_RegisterOperand temp = ir.regpool.makeTempInt();
    s2 = Move.create(INT_MOVE, temp, IC(ir.compiledMethod.getId()));
    OPT_Instruction s3 =
        Store.create(INT_STORE,
                     temp.copy(),
                     ir.regpool.makeFPOp(),
                     AC(Offset.fromIntSignExtend(STACKFRAME_METHOD_ID_OFFSET)),
                     null);  // TODO: valid location?
    s.insertAfter(s3);
    s.insertAfter(s2);
  }

  /////////////////////
  // Implementation
  /////////////////////

  /**
   * Expand the prologue instruction to make calling convention explicit.
   */
  private static void prologueExpand(OPT_IR ir) {

    // set up register lists for dead code elimination.
    boolean useDU = ir.options.getOptLevel() >= 1;
    if (useDU) {
      OPT_DefUse.computeDU(ir);
    }

    OPT_Instruction prologueInstr = ir.firstInstructionInCodeOrder().nextInstructionInCodeOrder();
    if (VM.VerifyAssertions) VM._assert(prologueInstr.operator == IR_PROLOGUE);
    OPT_Instruction start = prologueInstr.nextInstructionInCodeOrder();

    int int_index = 0;
    int double_index = 0;
    int spilledArgumentCounter =
        (-256 - ArchitectureSpecific.VM_ArchConstants.STACKFRAME_HEADER_SIZE) >> LOG_BYTES_IN_ADDRESS;
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register FP = phys.getFP();
    for (OPT_OperandEnumeration symParams = prologueInstr.getDefs(); symParams.hasMoreElements();) {
      OPT_RegisterOperand symParamOp = (OPT_RegisterOperand) symParams.next();
      OPT_Register symParam = symParamOp.register;
      VM_TypeReference t = symParamOp.type;
      if (t.isFloatType()) {
        // if optimizing, skip dead parameters
        // SJF: This optimization current breaks the paranoid sanity test.
        // Why? TODO: figure this out and remove the 'true' case below
        if (true || !useDU || symParam.useList != null) {
          if (double_index < NUMBER_DOUBLE_PARAM) {
            OPT_Register param = phys.get(FIRST_DOUBLE_PARAM + (double_index));
            start.insertBefore(MIR_Move.create(PPC_FMR, F(symParam), F(param)));
          } else {                  // spilled parameter
            start.insertBefore(MIR_Load.create(PPC_LFS,
                                               F(symParam),
                                               A(FP),
                                               IC((spilledArgumentCounter << LOG_BYTES_IN_ADDRESS) - BYTES_IN_ADDRESS +
                                                  BYTES_IN_FLOAT)));
            spilledArgumentCounter--;
          }
        }
        double_index++;
      } else if (t.isDoubleType()) {
        // if optimizing, skip dead parameters
        // SJF: This optimization current breaks the paranoid sanity test.
        // Why? TODO: figure this out and remove the 'true' case below
        if (true || !useDU || symParam.useList != null) {
          if (double_index < NUMBER_DOUBLE_PARAM) {
            OPT_Register param = phys.get(FIRST_DOUBLE_PARAM + (double_index));
            start.insertBefore(MIR_Move.create(PPC_FMR, D(symParam), D(param)));
          } else {                  // spilled parameter
            start.insertBefore(MIR_Load.create(PPC_LFD,
                                               D(symParam),
                                               A(FP),
                                               IC(spilledArgumentCounter << LOG_BYTES_IN_ADDRESS)));
            spilledArgumentCounter -= BYTES_IN_DOUBLE / BYTES_IN_ADDRESS;
          }
        }
        double_index++;
      } else { // t is object, 1/2 of a long, int, short, char, byte, or boolean
        // if optimizing, skip dead parameters
        // SJF: This optimization current breaks the paranoid sanity test.
        // Why? TODO: figure this out and remove the 'true' case below
        if (true || !useDU || symParam.useList != null) {
          if (int_index < NUMBER_INT_PARAM) {
            OPT_Register param = phys.get(FIRST_INT_PARAM + (int_index));
            start.insertBefore(MIR_Move.create(PPC_MOVE, new OPT_RegisterOperand(symParam, t), A(param)));
          } else {                  // spilled parameter
            if (VM
                .BuildFor64Addr &&
                                (t.isIntType() ||
                                 t.isShortType() ||
                                 t.isByteType() ||
                                 t.isCharType() ||
                                 t.isBooleanType())) {
              start.insertBefore(MIR_Load.create(PPC_LInt,
                                                 new OPT_RegisterOperand(symParam, t),
                                                 A(FP),
                                                 IC((spilledArgumentCounter << LOG_BYTES_IN_ADDRESS) -
                                                    BYTES_IN_ADDRESS + BYTES_IN_INT)));
            } else {
              // same size as addr (ie, either we're in 32 bit mode or we're in 64 bit mode and it's a reference or long)
              start.insertBefore(MIR_Load.create(PPC_LAddr,
                                                 new OPT_RegisterOperand(symParam, t),
                                                 A(FP),
                                                 IC(spilledArgumentCounter << LOG_BYTES_IN_ADDRESS)));
            }
            spilledArgumentCounter--;
          }
        }
        int_index++;
      }
    }

    // Now that we've made the calling convention explicit in the prologue,
    // set IR_PROLOGUE to have no defs.
    prologueInstr.replace(Prologue.create(IR_PROLOGUE, 0));
  }

  /**
   * Expand the call as appropriate
   * @param s the call instruction
   * @param ir the ir
   */
  private static void callExpand(OPT_Instruction s, OPT_IR ir) {
    int NumberParams = MIR_Call.getNumberOfParams(s);
    int int_index = 0;          // points to the first integer volatile
    int double_index = 0;       // poinst to the first f.p.    volatile
    int callSpillLoc = STACKFRAME_HEADER_SIZE;
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Instruction prev = s.prevInstructionInCodeOrder();
    OPT_Register FP = phys.getFP();
    boolean isSysCall = ir.stackManager.isSysCall(s);
    boolean firstLongHalf = false;

    // (1) Expand parameters
    for (int opNum = 0; opNum < NumberParams; opNum++) {
      OPT_Operand param = MIR_Call.getClearParam(s, opNum);
      OPT_RegisterOperand Reg = (OPT_RegisterOperand) param;
      // as part of getting into MIR, we make sure all params are in registers.
      OPT_Register reg = Reg.register;
      if (Reg.type.isFloatType()) {
        if (double_index < NUMBER_DOUBLE_PARAM) {       // register copy
          OPT_Register real = phys.get(FIRST_DOUBLE_PARAM + (double_index++));
          s.insertBefore(MIR_Move.create(PPC_FMR, F(real), Reg));
          Reg = F(real);
          // Record that the call now has a use of the real reg
          // This is to ensure liveness is correct
          MIR_Call.setParam(s, opNum, Reg);
        } else {                  // spill to memory
          OPT_Instruction p = prev.nextInstructionInCodeOrder();
          callSpillLoc += BYTES_IN_ADDRESS;
          p.insertBefore(MIR_Store.create(PPC_STFS, F(reg), A(FP), IC(callSpillLoc - BYTES_IN_FLOAT)));
          // We don't have uses of the heap at MIR, so null it out
          MIR_Call.setParam(s, opNum, null);
        }
      } else if (Reg.type.isDoubleType()) {
        if (double_index < NUMBER_DOUBLE_PARAM) {     // register copy
          OPT_Register real = phys.get(FIRST_DOUBLE_PARAM + (double_index++));
          s.insertBefore(MIR_Move.create(PPC_FMR, D(real), Reg));
          Reg = D(real);
          // Record that the call now has a use of the real reg
          // This is to ensure liveness is correct
          MIR_Call.setParam(s, opNum, Reg);
        } else {                  // spill to memory
          OPT_Instruction p = prev.nextInstructionInCodeOrder();
          p.insertBefore(MIR_Store.create(PPC_STFD, D(reg), A(FP), IC(callSpillLoc)));
          callSpillLoc += BYTES_IN_DOUBLE;
          // We don't have uses of the heap at MIR, so null it out
          MIR_Call.setParam(s, opNum, null);
        }
      } else {                    // IntType (or half of long) or reference
        if (VM.BuildForSVR4ABI) {
          /* NOTE: following adjustment is not stated in SVR4 ABI, but
           * was implemented in GCC.
           */
          if (isSysCall && Reg.type.isLongType()) {
            if (firstLongHalf) {
              firstLongHalf = false;
            } else {
              int true_index = FIRST_INT_PARAM + int_index;
              int_index += (true_index + 1) & 0x01; // if gpr is even, gpr += 1
              firstLongHalf = true;
            }
          }
        }
        if (int_index < NUMBER_INT_PARAM) {             // register copy
          OPT_Register real = phys.get(FIRST_INT_PARAM + (int_index++));
          OPT_RegisterOperand Real = new OPT_RegisterOperand(real, Reg.type);
          s.insertBefore(MIR_Move.create(PPC_MOVE, Real, Reg));
          Reg = new OPT_RegisterOperand(real, Reg.type);
          // Record that the call now has a use of the real reg
          // This is to ensure liveness is correct
          MIR_Call.setParam(s, opNum, Reg);
        } else {                  // spill to memory
          OPT_Instruction p = prev.nextInstructionInCodeOrder();
          callSpillLoc += BYTES_IN_ADDRESS;
          if (VM
              .BuildFor64Addr &&
                              (Reg.type.isIntType() ||
                               Reg.type.isShortType() ||
                               Reg.type.isByteType() ||
                               Reg.type.isCharType() ||
                               Reg.type.isBooleanType())) {
            p.insertBefore(MIR_Store.create(PPC_STW,
                                            new OPT_RegisterOperand(reg, Reg.type),
                                            A(FP),
                                            IC(callSpillLoc - BYTES_IN_INT)));
          } else {
            // same size as addr (ie, either we're in 32 bit mode or we're in 64 bit mode and it's a reference or long)
            p.insertBefore(MIR_Store.create(PPC_STAddr,
                                            new OPT_RegisterOperand(reg, Reg.type),
                                            A(FP),
                                            IC(callSpillLoc - BYTES_IN_ADDRESS)));
          }
          // We don't have uses of the heap at MIR, so null it out
          MIR_Call.setParam(s, opNum, null);
        }
      }
    }
    // If we needed to pass arguments on the stack,
    // then make sure we have a big enough stack
    if (callSpillLoc != STACKFRAME_HEADER_SIZE) {
      ir.stackManager.allocateParameterSpace(callSpillLoc);
    }
    // (2) expand result
    OPT_Instruction lastCallSeqInstr = s;
    if (MIR_Call.hasResult2(s)) {
      if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
      OPT_RegisterOperand result2 = MIR_Call.getClearResult2(s);
      OPT_RegisterOperand physical = new OPT_RegisterOperand(phys.get(FIRST_INT_RETURN + 1), result2.type);
      OPT_Instruction tmp = MIR_Move.create(PPC_MOVE, result2, physical);
      lastCallSeqInstr.insertAfter(tmp);
      lastCallSeqInstr = tmp;
      MIR_Call.setResult2(s, null);
    }
    if (MIR_Call.hasResult(s)) {
      OPT_RegisterOperand result1 = MIR_Call.getClearResult(s);
      if (result1.type.isFloatType() || result1.type.isDoubleType()) {
        OPT_RegisterOperand physical = new OPT_RegisterOperand(phys.get(FIRST_DOUBLE_RETURN), result1.type);
        OPT_Instruction tmp = MIR_Move.create(PPC_FMR, result1, physical);
        lastCallSeqInstr.insertAfter(tmp);
        lastCallSeqInstr = tmp;
        MIR_Call.setResult(s, null);
      } else {
        OPT_RegisterOperand physical = new OPT_RegisterOperand(phys.get(FIRST_INT_RETURN), result1.type);
        OPT_Instruction tmp = MIR_Move.create(PPC_MOVE, result1, physical);
        lastCallSeqInstr.insertAfter(tmp);
        lastCallSeqInstr = tmp;
        MIR_Call.setResult(s, null);
      }
    }
  }

  /**
   * Expand return statements.
   * @param s the return instruction
   * @param ir the ir
   */
  private static void returnExpand(OPT_Instruction s, OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    if (MIR_Return.hasVal(s)) {
      OPT_RegisterOperand symb1 = MIR_Return.getClearVal(s);
      OPT_RegisterOperand phys1;
      if (symb1.type.isFloatType() || symb1.type.isDoubleType()) {
        phys1 = D(phys.get(FIRST_DOUBLE_RETURN));
        s.insertBefore(MIR_Move.create(PPC_FMR, phys1, symb1));
      } else {
        phys1 = new OPT_RegisterOperand(phys.get(FIRST_INT_RETURN), symb1.type);
        s.insertBefore(MIR_Move.create(PPC_MOVE, phys1, symb1));
      }
      MIR_Return.setVal(s, phys1.copyD2U());
    }
    if (MIR_Return.hasVal2(s)) {
      if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
      OPT_RegisterOperand symb2 = MIR_Return.getClearVal2(s);
      OPT_RegisterOperand phys2 = I(phys.get(FIRST_INT_RETURN + 1));
      s.insertBefore(MIR_Move.create(PPC_MOVE, phys2, symb2));
      MIR_Return.setVal2(s, phys2.copyD2U());
    }
  }

  /**
   * Save and restore all nonvolatile registers around a syscall.
   * On PPC, our register conventions are compatablile with the
   * natvie ABI, so there is nothing to do.
   *
   * @param call the sys call
   */
  public static void saveNonvolatilesAroundSysCall(OPT_Instruction call, OPT_IR ir) {
  }

}
