/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Handles the conversion from LIR to MIR of operators whose 
 * expansion requires the introduction of new control flow (new basic blocks).
 *
 * @author Dave Grove
 * @modified Peter Sweeney
 */
abstract class OPT_ComplexLIR2MIRExpansion extends OPT_RVMIRTools {

  /**
   * Converts the given IR to low level IA32 IR.
   *
   * @param ir IR to convert
   */
  public static void convert(OPT_IR ir) {
    OPT_Instruction nextInstr;
    for (OPT_Instruction s = ir.firstInstructionInCodeOrder();
	 s != null; s = nextInstr) {
      switch (s.getOpcode()) {
      case LONG_SHL_ACC_opcode:
	nextInstr = long_shl(s, ir);
	break;
      case LONG_SHR_ACC_opcode:
	nextInstr = long_shr(s, ir);
	break;
      case LONG_USHR_ACC_opcode:
	nextInstr = long_ushr(s, ir);
	break;
      case LONG_IFCMP_opcode:
	{
	  OPT_Operand val2 = IfCmp.getVal2(s);
	  if (val2 instanceof OPT_RegisterOperand) {
	    nextInstr = long_ifcmp(s, ir);
	  } else {
	    nextInstr = long_ifcmp_imm(s, ir);
	  }
	}
	break;
      case LONG_CMP_opcode:
	nextInstr = threeValueLongCmp(s, ir);
	break;
      case FLOAT_IFCMPL_opcode:
      case FLOAT_IFCMPG_opcode:
      case DOUBLE_IFCMPL_opcode:
      case DOUBLE_IFCMPG_opcode:
	nextInstr = fp_ifcmp(s, ir);
	break;
      case FLOAT_CMPL_opcode:
      case FLOAT_CMPG_opcode:
      case DOUBLE_CMPL_opcode:
      case DOUBLE_CMPG_opcode:
	nextInstr = threeValueFPCmp(s, ir);
	break;
      case YIELDPOINT_PROLOGUE_opcode:
      case YIELDPOINT_EPILOGUE_opcode:
      case YIELDPOINT_BACKEDGE_opcode:
	nextInstr = yield_point(s,ir);
	break;
      default:
	nextInstr = s.nextInstructionInCodeOrder();
	break;
      }
    }
    OPT_DefUse.recomputeSpansBasicBlock(ir);
  }


  private static OPT_Instruction long_shl(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    OPT_Register hval = BinaryAcc.getResult(s).register;
    OPT_Register lval = ir.regpool.getSecondReg(hval);
    OPT_Operand shiftOp = BinaryAcc.getClearValue(s);
    
    if (shiftOp instanceof OPT_IntConstantOperand) {
      int shift = ((OPT_IntConstantOperand)shiftOp).value;
      shift = shift & 0x3F; // only bottom six bits matter;
      if (shift == 0) {
	s.remove(); // operation is a nop.
      } else if (shift >= 32) {
	s.insertBefore(MIR_Move.create(IA32_MOV, R(hval), R(lval)));
	s.insertBefore(MIR_BinaryAcc.create(IA32_SHL, R(hval), I(shift)));
	MIR_Move.mutate(s, IA32_MOV, R(lval), I(0));
      } else {
	s.insertBefore(MIR_DoubleShift.create(IA32_SHLD, R(hval), R(lval), I(shift)));
	MIR_BinaryAcc.mutate(s, IA32_SHL, R(lval), I(shift));
      }
    } else {
      OPT_RegisterOperand shiftTemp = ir.regpool.makeTempInt();
      OPT_Register shift = shiftTemp.register;
      OPT_Register ecx = ir.regpool.getPhysicalRegisterSet().getECX();

      OPT_BasicBlock sizeTestBB = s.getBasicBlock();
      OPT_BasicBlock nextBB = sizeTestBB.splitNodeAt(s, ir);
      OPT_BasicBlock gt32BB = sizeTestBB.createSubBlock(s.bcIndex, ir);
      OPT_BasicBlock lt32BB = sizeTestBB.createSubBlock(s.bcIndex, ir);
      sizeTestBB.insertOut(gt32BB);
      sizeTestBB.insertOut(lt32BB);
      gt32BB.insertOut(nextBB);
      lt32BB.insertOut(nextBB);
      ir.cfg.linkInCodeOrder(sizeTestBB, gt32BB);
      ir.cfg.linkInCodeOrder(gt32BB, lt32BB);
      ir.cfg.linkInCodeOrder(lt32BB, nextBB);

      s.remove();

      // copy the shift value to a temporary so we can destroy it.
      sizeTestBB.appendInstruction(MIR_Move.create(IA32_MOV, R(shift), shiftOp));
      
      // See if the shift is lt or gt 32
      sizeTestBB.appendInstruction(MIR_Move.create(IA32_MOV, R(ecx), R(shift)));
      sizeTestBB.appendInstruction(MIR_BinaryAcc.create(IA32_AND, R(shift), I(32)));
      sizeTestBB.appendInstruction(MIR_CondBranch.create(IA32_JCC, 
							 OPT_IA32ConditionOperand.EQ(),
							 lt32BB.makeJumpTarget(),
							 new OPT_BranchProfileOperand()));
      
      // handle shift gt 32
      gt32BB.appendInstruction(MIR_BinaryAcc.create(IA32_XOR, R(ecx), R(shift)));
      gt32BB.appendInstruction(MIR_Move.create(IA32_MOV, R(hval), R(lval)));
      gt32BB.appendInstruction(MIR_BinaryAcc.create(IA32_SHL, R(hval), R(ecx)));
      gt32BB.appendInstruction(MIR_Move.create(IA32_MOV, R(lval), I(0)));
      gt32BB.appendInstruction(MIR_Branch.create(IA32_JMP, nextBB.makeJumpTarget()));
      
      // handle shift lt 32
      lt32BB.appendInstruction(MIR_DoubleShift.create(IA32_SHLD, R(hval), R(lval), R(ecx)));
      lt32BB.appendInstruction(MIR_BinaryAcc.create(IA32_SHL, R(lval), R(ecx)));
    }
    return nextInstr;
  }


  private static OPT_Instruction long_shr(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    OPT_Register hval = BinaryAcc.getResult(s).register;
    OPT_Register lval = ir.regpool.getSecondReg(hval);
    OPT_Operand shiftOp = BinaryAcc.getClearValue(s);
    
    if (shiftOp instanceof OPT_IntConstantOperand) {
      int shift = ((OPT_IntConstantOperand)shiftOp).value;
      shift = shift & 0x3F; // only bottom six bits matter;
      if (shift == 0) {
	s.remove(); // operation is a nop.
      } else if (shift >= 32) {
	s.insertBefore(MIR_Move.create(IA32_MOV, R(lval), R(hval)));
	s.insertBefore(MIR_BinaryAcc.create(IA32_SAR, R(lval), I(shift)));
	MIR_BinaryAcc.mutate(s, IA32_SAR, R(hval), I(31));
      } else {
	s.insertBefore(MIR_DoubleShift.create(IA32_SHRD, R(lval), R(hval), I(shift)));
	MIR_BinaryAcc.mutate(s, IA32_SAR, R(hval), I(shift));
      }
    } else {
      OPT_RegisterOperand shiftTemp = ir.regpool.makeTempInt();
      OPT_Register shift = shiftTemp.register;
      OPT_Register ecx = ir.regpool.getPhysicalRegisterSet().getECX();

      OPT_BasicBlock sizeTestBB = s.getBasicBlock();
      OPT_BasicBlock nextBB = sizeTestBB.splitNodeAt(s, ir);
      OPT_BasicBlock gt32BB = sizeTestBB.createSubBlock(s.bcIndex, ir);
      OPT_BasicBlock lt32BB = sizeTestBB.createSubBlock(s.bcIndex, ir);
      sizeTestBB.insertOut(gt32BB);
      sizeTestBB.insertOut(lt32BB);
      gt32BB.insertOut(nextBB);
      lt32BB.insertOut(nextBB);
      ir.cfg.linkInCodeOrder(sizeTestBB, gt32BB);
      ir.cfg.linkInCodeOrder(gt32BB, lt32BB);
      ir.cfg.linkInCodeOrder(lt32BB, nextBB);

      s.remove();
      
      // copy the shift value to a temporary so we can destroy it.
      sizeTestBB.appendInstruction(MIR_Move.create(IA32_MOV, R(shift), shiftOp));
      
      // See if the shift is lt or gt 32
      sizeTestBB.appendInstruction(MIR_Move.create(IA32_MOV, R(ecx), R(shift)));
      sizeTestBB.appendInstruction(MIR_BinaryAcc.create(IA32_AND, R(shift), I(32)));
      sizeTestBB.appendInstruction(MIR_CondBranch.create(IA32_JCC, 
							 OPT_IA32ConditionOperand.EQ(),
							 lt32BB.makeJumpTarget(),
							 new OPT_BranchProfileOperand()));
      
      // handle shift gt 32
      gt32BB.appendInstruction(MIR_BinaryAcc.create(IA32_XOR, R(ecx), R(shift)));
      gt32BB.appendInstruction(MIR_Move.create(IA32_MOV, R(lval), R(hval)));
      gt32BB.appendInstruction(MIR_BinaryAcc.create(IA32_SAR, R(lval), R(ecx)));
      gt32BB.appendInstruction(MIR_BinaryAcc.create(IA32_SAR, R(hval), I(31)));
      gt32BB.appendInstruction(MIR_Branch.create(IA32_JMP, nextBB.makeJumpTarget()));
      
      // handle shift lt 32
      lt32BB.appendInstruction(MIR_DoubleShift.create(IA32_SHRD, R(lval), R(hval), R(ecx)));
      lt32BB.appendInstruction(MIR_BinaryAcc.create(IA32_SAR, R(hval), R(ecx)));
    }
    return nextInstr;
  }

  
  private static OPT_Instruction long_ushr(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    OPT_Register hval = BinaryAcc.getResult(s).register;
    OPT_Register lval = ir.regpool.getSecondReg(hval);
    OPT_Operand shiftOp = BinaryAcc.getClearValue(s);
    
    if (shiftOp instanceof OPT_IntConstantOperand) {
      int shift = ((OPT_IntConstantOperand)shiftOp).value;
      shift = shift & 0x3F; // only bottom six bits matter;
      if (shift == 0) {
	s.remove(); // operation is a nop.
      } else if (shift >= 32) {
	s.insertBefore(MIR_Move.create(IA32_MOV, R(lval), R(hval)));
	s.insertBefore(MIR_BinaryAcc.create(IA32_SHR, R(lval), I(shift)));
	MIR_Move.mutate(s, IA32_MOV, R(hval), I(0));
      } else {
	s.insertBefore(MIR_DoubleShift.create(IA32_SHRD, R(lval), R(hval), I(shift)));
	MIR_BinaryAcc.mutate(s, IA32_SHR, R(hval), I(shift));
      }
    } else {
      OPT_RegisterOperand shiftTemp = ir.regpool.makeTempInt();
      OPT_Register shift = shiftTemp.register;
      OPT_Register ecx = ir.regpool.getPhysicalRegisterSet().getECX();

      OPT_BasicBlock sizeTestBB = s.getBasicBlock();
      OPT_BasicBlock nextBB = sizeTestBB.splitNodeAt(s, ir);
      OPT_BasicBlock gt32BB = sizeTestBB.createSubBlock(s.bcIndex, ir);
      OPT_BasicBlock lt32BB = sizeTestBB.createSubBlock(s.bcIndex, ir);
      sizeTestBB.insertOut(gt32BB);
      sizeTestBB.insertOut(lt32BB);
      gt32BB.insertOut(nextBB);
      lt32BB.insertOut(nextBB);
      ir.cfg.linkInCodeOrder(sizeTestBB, gt32BB);
      ir.cfg.linkInCodeOrder(gt32BB, lt32BB);
      ir.cfg.linkInCodeOrder(lt32BB, nextBB);

      s.remove();
      
      // copy the shift value to a temporary so we can destroy it.
      sizeTestBB.appendInstruction(MIR_Move.create(IA32_MOV, R(shift), shiftOp));
      
      // See if the shift is lt or gt 32
      sizeTestBB.appendInstruction(MIR_Move.create(IA32_MOV, R(ecx), R(shift)));
      sizeTestBB.appendInstruction(MIR_BinaryAcc.create(IA32_AND, R(shift), I(32)));
      sizeTestBB.appendInstruction(MIR_CondBranch.create(IA32_JCC, 
							 OPT_IA32ConditionOperand.EQ(),
							 lt32BB.makeJumpTarget(),
							 new OPT_BranchProfileOperand()));
      
      // handle shift gt 32
      gt32BB.appendInstruction(MIR_BinaryAcc.create(IA32_XOR, R(ecx), R(shift)));
      gt32BB.appendInstruction(MIR_Move.create(IA32_MOV, R(lval), R(hval)));
      gt32BB.appendInstruction(MIR_BinaryAcc.create(IA32_SHR, R(lval), R(ecx)));
      gt32BB.appendInstruction(MIR_Move.create(IA32_MOV, R(hval), I(0)));
      gt32BB.appendInstruction(MIR_Branch.create(IA32_JMP, nextBB.makeJumpTarget()));
      
      // handle shift lt 32
      lt32BB.appendInstruction(MIR_DoubleShift.create(IA32_SHRD, R(lval), R(hval), R(ecx)));
      lt32BB.appendInstruction(MIR_BinaryAcc.create(IA32_SHR, R(hval), R(ecx)));
    }
    return nextInstr;
  }


  private static OPT_Instruction long_ifcmp(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    OPT_ConditionOperand cond = IfCmp.getCond(s);
    OPT_Register xh = ((OPT_RegisterOperand)IfCmp.getVal1(s)).register;
    OPT_Register xl = ir.regpool.getSecondReg(xh);
    OPT_RegisterOperand yh = (OPT_RegisterOperand)IfCmp.getClearVal2(s);
    OPT_RegisterOperand yl = R(ir.regpool.getSecondReg(yh.register));
    basic_long_ifcmp(s, ir, cond, xh, xl, yh, yl);
    return nextInstr;
  }


  private static OPT_Instruction long_ifcmp_imm(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    OPT_ConditionOperand cond = IfCmp.getCond(s);
    OPT_Register xh = ((OPT_RegisterOperand)IfCmp.getVal1(s)).register;
    OPT_Register xl = ir.regpool.getSecondReg(xh);
    OPT_LongConstantOperand rhs = (OPT_LongConstantOperand)IfCmp.getVal2(s);
    int low = rhs.lower32();
    int high = rhs.upper32();
    OPT_IntConstantOperand yh = I(high);
    OPT_IntConstantOperand yl = I(low);
    
    if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
      // tricky... ((xh^yh)|(xl^yl) == 0) <==> (lhll == rhrl)!!
      OPT_Register th = ir.regpool.getInteger(false);
      OPT_Register tl = ir.regpool.getInteger(false);
      if (high == 0) {
	if (low == 0) { // 0,0
	  s.insertBefore(MIR_Move.create(IA32_MOV, R(th), R(xh)));
	  s.insertBefore(MIR_BinaryAcc.create(IA32_OR, R(th), R(xl)));
	} else if (low == -1) { // 0,-1
	  s.insertBefore(MIR_Move.create(IA32_MOV, R(tl), R(xl)));
	  s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, R(tl)));
	  s.insertBefore(MIR_BinaryAcc.create(IA32_OR, R(tl), R(xh)));
	} else { // 0,*
	  s.insertBefore(MIR_Move.create(IA32_MOV, R(tl), R(xl)));
	  s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, R(tl), yl));
	  s.insertBefore(MIR_BinaryAcc.create(IA32_OR, R(tl), R(xh)));
	}
      } else if (high == -1) {
	if (low == 0) { // -1,0
	  s.insertBefore(MIR_Move.create(IA32_MOV, R(th), R(xh)));
	  s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, R(th)));
	  s.insertBefore(MIR_BinaryAcc.create(IA32_OR, R(th), R(xl)));
	} else if (low == -1) { // -1,-1
	  s.insertBefore(MIR_Move.create(IA32_MOV, R(th), R(xh)));
	  s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, R(th)));
	  s.insertBefore(MIR_Move.create(IA32_MOV, R(tl), R(xl)));
	  s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, R(tl)));
	  s.insertBefore(MIR_BinaryAcc.create(IA32_OR, R(th), R(tl)));
	} else { // -1,*
	  s.insertBefore(MIR_Move.create(IA32_MOV, R(th), R(xh)));
	  s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, R(th)));
	  s.insertBefore(MIR_Move.create(IA32_MOV, R(tl), R(xl)));
	  s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, R(tl), yl));
	  s.insertBefore(MIR_BinaryAcc.create(IA32_OR, R(th), R(tl)));
	}
      } else { 
	if (low == 0) { // *,0
	  s.insertBefore(MIR_Move.create(IA32_MOV, R(th), R(xh)));
	  s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, R(th), yh));
	  s.insertBefore(MIR_BinaryAcc.create(IA32_OR, R(th), R(xl)));
	} else if (low == -1) { // *,-1
	  s.insertBefore(MIR_Move.create(IA32_MOV, R(th), R(xh)));
	  s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, R(th), yh));
	  s.insertBefore(MIR_Move.create(IA32_MOV, R(tl), R(xl)));
	  s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, R(tl)));
	  s.insertBefore(MIR_BinaryAcc.create(IA32_OR, R(th), R(tl)));
	} else { // neither high nor low is special
	  s.insertBefore(MIR_Move.create(IA32_MOV, R(th), R(xh)));
	  s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, R(th), yh));
	  s.insertBefore(MIR_Move.create(IA32_MOV, R(tl), R(xl)));
	  s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, R(tl), yl));
	  s.insertBefore(MIR_BinaryAcc.create(IA32_OR, R(th), R(tl)));
	}
      }
      MIR_CondBranch.mutate(s, IA32_JCC, 
			    new OPT_IA32ConditionOperand(cond),
			    IfCmp.getTarget(s),
			    IfCmp.getBranchProfile(s));
      return nextInstr;
    } else {
      // pick up a few special cases where the sign of xh is sufficient
      if (rhs.value == 0L) {
	if (cond.isLESS()) {
	  // xh < 0 implies true
	  s.insertBefore(MIR_Compare.create(IA32_CMP, R(xh), I(0)));
	  MIR_CondBranch.mutate(s, IA32_JCC,
				OPT_IA32ConditionOperand.LT(),
				IfCmp.getTarget(s),
				IfCmp.getBranchProfile(s));
	  return nextInstr;
	} else if (cond.isGREATER_EQUAL()) {
	  s.insertBefore(MIR_Compare.create(IA32_CMP, R(xh), I(0)));
	  MIR_CondBranch.mutate(s, IA32_JCC,
				OPT_IA32ConditionOperand.GE(),
				IfCmp.getTarget(s),
				IfCmp.getBranchProfile(s));
	  return nextInstr;
	}
      } else if (rhs.value == -1L) {
	if (cond.isLESS_EQUAL()) {
	  s.insertBefore(MIR_Compare.create(IA32_CMP, R(xh), I(-1)));
	  MIR_CondBranch.mutate(s, IA32_JCC,
				OPT_IA32ConditionOperand.LE(),
				IfCmp.getTarget(s),
				IfCmp.getBranchProfile(s));
	  return nextInstr;
	} else if (cond.isGREATER()) {
	  s.insertBefore(MIR_Compare.create(IA32_CMP, R(xh), I(0)));
	  MIR_CondBranch.mutate(s, IA32_JCC,
				OPT_IA32ConditionOperand.GE(),
				IfCmp.getTarget(s),
				IfCmp.getBranchProfile(s));
	  return nextInstr;
	}
      }

      basic_long_ifcmp(s, ir, cond, xh, xl, yh, yl);
      return nextInstr;
    }
  }


  private static void basic_long_ifcmp(OPT_Instruction s, OPT_IR ir, 
				       OPT_ConditionOperand cond, 
				       OPT_Register xh, 
				       OPT_Register xl, 
				       OPT_Operand yh, 
				       OPT_Operand yl) {
    if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
      OPT_RegisterOperand th = ir.regpool.makeTempInt();
      OPT_RegisterOperand tl = ir.regpool.makeTempInt();
      // tricky... ((xh^yh)|(xl^yl) == 0) <==> (lhll == rhrl)!!
      s.insertBefore(MIR_Move.create(IA32_MOV, th, R(xh)));
      s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, th.copyD2D(), yh));
      s.insertBefore(MIR_Move.create(IA32_MOV, tl, R(xl)));
      s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, tl.copyD2D(), yl));
      s.insertBefore(MIR_BinaryAcc.create(IA32_OR, th.copyD2D(), tl.copyD2U()));
      MIR_CondBranch.mutate(s, IA32_JCC, 
			    new OPT_IA32ConditionOperand(cond),
			    IfCmp.getTarget(s),
			    IfCmp.getBranchProfile(s));
    } else {
      // Do the naive thing and generate multiple compare/branch implementation.
      OPT_IA32ConditionOperand cond1;
      OPT_IA32ConditionOperand cond2;
      OPT_IA32ConditionOperand cond3;
      if (cond.isLESS()) {
	cond1 = OPT_IA32ConditionOperand.LT();
	cond2 = OPT_IA32ConditionOperand.GT();
	cond3 = OPT_IA32ConditionOperand.LLT();
      } else if (cond.isGREATER()) {
	cond1 = OPT_IA32ConditionOperand.GT();
	cond2 = OPT_IA32ConditionOperand.LT();
	cond3 = OPT_IA32ConditionOperand.LGT();
      } else if (cond.isLESS_EQUAL()) {
	cond1 = OPT_IA32ConditionOperand.LT();
	cond2 = OPT_IA32ConditionOperand.GT();
	cond3 = OPT_IA32ConditionOperand.LLE();
      } else if (cond.isGREATER_EQUAL()) {
	cond1 = OPT_IA32ConditionOperand.GT();
	cond2 = OPT_IA32ConditionOperand.LT();
	cond3 = OPT_IA32ConditionOperand.LGE();
      } else {
	// I don't think we use the unsigned compares for longs,
	// so defer actually implementing them until we find a test case. --dave
	cond1 = cond2 = cond3 = null;
	OPT_OptimizingCompilerException.TODO();
      }

      OPT_BasicBlock myBlock = s.getBasicBlock();
      OPT_BasicBlock test2Block = myBlock.createSubBlock(s.bcIndex, ir);
      OPT_BasicBlock falseBlock = myBlock.splitNodeAt(s, ir);
      OPT_BasicBlock trueBlock = IfCmp.getTarget(s).target.getBasicBlock();
      
      falseBlock.recomputeNormalOut(ir);
      myBlock.insertOut(test2Block);
      myBlock.insertOut(falseBlock);
      myBlock.insertOut(trueBlock);
      test2Block.insertOut(falseBlock);
      test2Block.insertOut(trueBlock);
      ir.cfg.linkInCodeOrder(myBlock, test2Block);
      ir.cfg.linkInCodeOrder(test2Block, falseBlock);
      
      s.remove();
      
      myBlock.appendInstruction(MIR_Compare.create(IA32_CMP, R(xh), yh));
      myBlock.appendInstruction(MIR_CondBranch2.create(IA32_JCC2, 
						       cond1, trueBlock.makeJumpTarget(), new OPT_BranchProfileOperand(),
						       cond2, falseBlock.makeJumpTarget(), new OPT_BranchProfileOperand()));
      test2Block.appendInstruction(MIR_Compare.create(IA32_CMP, R(xl), yl));
      test2Block.appendInstruction(MIR_CondBranch.create(IA32_JCC, cond3, trueBlock.makeJumpTarget(), new OPT_BranchProfileOperand()));
    }
  }


  // done here instead of in burs because in some
  // cases we must remove a trailing goto, and we 
  // can't do that in burs!
  private static OPT_Instruction fp_ifcmp(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    OPT_Operator op = s.operator();
    OPT_BranchOperand testFailed;
    OPT_BasicBlock bb = s.getBasicBlock();
    OPT_Instruction lastInstr = bb.lastRealInstruction();
    if (lastInstr.operator() == IA32_JMP) {
      // We're in trouble if there is another instruction between s and lastInstr!
      if (VM.VerifyAssertions) VM.assert(s.nextInstructionInCodeOrder() == lastInstr);
      // Set testFailed to target of GOTO
      testFailed = MIR_Branch.getTarget(lastInstr);
      nextInstr = lastInstr.nextInstructionInCodeOrder();
      lastInstr.remove();
    } else {
      // Set testFailed to label of next (fallthrough basic block)
      testFailed = bb.nextBasicBlockInCodeOrder().makeJumpTarget();
    }

    OPT_Operand left = IfCmp.getVal1(s);
    OPT_Operand right = IfCmp.getVal2(s);
    OPT_ConditionOperand c = IfCmp.getCond(s);
    OPT_BranchOperand target = IfCmp.getTarget(s);
    boolean UeqL = (op == DOUBLE_IFCMPL) || (op == FLOAT_IFCMPL);
    boolean UeqG = (op == DOUBLE_IFCMPG) || (op == FLOAT_IFCMPG);
    OPT_BranchOperand unorderedTarget;
    if (c.value == OPT_ConditionOperand.EQUAL || 
	(UeqL && (c.value == OPT_ConditionOperand.GREATER || 
		  c.value == OPT_ConditionOperand.GREATER_EQUAL)) || 
	(UeqG && (c.value == OPT_ConditionOperand.LESS || 
		  c.value == OPT_ConditionOperand.LESS_EQUAL))) {
      unorderedTarget = (OPT_BranchOperand)testFailed.copy();
    } else {
      unorderedTarget = (OPT_BranchOperand)target.copy();
    }
    
    // IMPORTANT: FCOMI only sets 3 of the 6 bits in EFLAGS, so
    // we can't just translate the condition operand as if it 
    // were an integer compare.
    // FCMOI sets ZF, PF, and CF as follows: 
    // Compare Results      ZF     PF      CF
    // left > right          0      0       0
    // left < right          0      0       1
    // left == right         1      0       0
    // UNORDERED             1      1       1
    OPT_Register FP0 = ir.regpool.getPhysicalRegisterSet().getFPR(0);
    s.insertBefore(MIR_Move.create(IA32_FMOV, R(FP0), left));
    s.insertBefore(MIR_Compare.create(IA32_FCOMI, R(FP0), right));
    if (c.isEQUAL()) {
      s.insertBefore(MIR_CondBranch2.create(IA32_JCC2, 
					    OPT_IA32ConditionOperand.PE(),  // PF == 1
					    unorderedTarget,
					    new OPT_BranchProfileOperand(),
					    OPT_IA32ConditionOperand.EQ(),  // ZF == 1
					    target,
					    new OPT_BranchProfileOperand()));
      s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
    } else if (c.isNOT_EQUAL()) {
      s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
					    OPT_IA32ConditionOperand.PE(),  // PF == 1
					    unorderedTarget,
					    new OPT_BranchProfileOperand(),
					    OPT_IA32ConditionOperand.EQ(),  // ZF == 1
					    testFailed,
					    new OPT_BranchProfileOperand()));
      s.insertBefore(MIR_Branch.create(IA32_JMP, target));
    } else if (c.isLESS()) {
      s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
					    OPT_IA32ConditionOperand.PE(),  // PF == 1
					    unorderedTarget,
					    new OPT_BranchProfileOperand(),
					    OPT_IA32ConditionOperand.LLT(), // CF == 1
					    target,
					    new OPT_BranchProfileOperand()));
      s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
    } else if (c.isGREATER()) {
      s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
					    OPT_IA32ConditionOperand.PE(),  // PF == 1
					    unorderedTarget,
					    new OPT_BranchProfileOperand(),
					    OPT_IA32ConditionOperand.LGT(), // ZF == 0 and CF == 0
					    target,
					    new OPT_BranchProfileOperand()));
      s.insertBefore(MIR_Branch.create(IA32_JMP,testFailed));
    } else if (c.isLESS_EQUAL()) {
      s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
					    OPT_IA32ConditionOperand.PE(),  // PF == 1
					    unorderedTarget,
					    new OPT_BranchProfileOperand(),
					    OPT_IA32ConditionOperand.LGT(), // ZF == 0 and CF == 0
					    testFailed,
					    new OPT_BranchProfileOperand()));
      s.insertBefore(MIR_Branch.create(IA32_JMP, target));
    } else if (c.isGREATER_EQUAL()) {
      s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
					    OPT_IA32ConditionOperand.PE(),  // PF == 1
					    unorderedTarget,
					    new OPT_BranchProfileOperand(),
					    OPT_IA32ConditionOperand.LLT(), // CF == 1
					    testFailed,
					    new OPT_BranchProfileOperand()));
      s.insertBefore(MIR_Branch.create(IA32_JMP, target));
    } else {
      throw new OPT_OptimizingCompilerException("Unexpected fp compare operation" + c.toString());
    }
    s.remove();
    return nextInstr;
  }


  /**
   * compare to values and set result to -1, 0, 1 for <, =, >, respectively
   * @param s the compare instruction
   * @param ir the governing IR
   */
  private static OPT_Instruction threeValueFPCmp (OPT_Instruction s, OPT_IR ir) {
    // IMPORTANT: FCOMI only sets 3 of the 6 bits in EFLAGS, so 
    // we can't quite just translate the condition operand as if it 
    // were an integer compare.
    // FCMOI sets ZF, PF, and CF as follows: 
    // Compare Results      ZF     PF      CF
    // left > right          0      0       0
    // left < right          0      0       1
    // left == right         1      0       0
    // UNORDERED             1      1       1
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    OPT_Register res = Binary.getClearResult(s).register;
    OPT_RegisterOperand one = (OPT_RegisterOperand) Binary.getClearVal1(s);
    OPT_RegisterOperand two = (OPT_RegisterOperand) Binary.getClearVal2(s);
    res.setSpansBasicBlock();
    OPT_BasicBlock BB1 = s.getBasicBlock();
    OPT_BasicBlock BB6 = BB1.splitNodeAt(s, ir);
    s.remove();
    OPT_BasicBlock oneBlock = BB1.createSubBlock(0, ir);
    OPT_BasicBlock zeroBlock = BB1.createSubBlock(0, ir);
    OPT_BasicBlock minusOneBlock = BB1.createSubBlock(0, ir);
    OPT_BasicBlock unorderedBlock = oneBlock;
    if ((s.operator == DOUBLE_CMPL) || (s.operator == FLOAT_CMPL)) {
      unorderedBlock = minusOneBlock;
    }

    OPT_Register FP0 = ir.regpool.getPhysicalRegisterSet().getFPR(0);
    BB1.appendInstruction(MIR_Move.create(IA32_FMOV, R(FP0), one));
    BB1.appendInstruction(MIR_Compare.create(IA32_FCOMI, R(FP0), two));
    BB1.appendInstruction(MIR_CondBranch2.create(IA32_JCC2,
						 OPT_IA32ConditionOperand.PE(),  // PF == 1	
						 unorderedBlock.makeJumpTarget(),
						 new OPT_BranchProfileOperand(0.01),
						 OPT_IA32ConditionOperand.EQ(),  // ZF == 1
						 zeroBlock.makeJumpTarget(),
						 new OPT_BranchProfileOperand(0.33)));
    BB1.appendInstruction(MIR_CondBranch.create(IA32_JCC,
						OPT_IA32ConditionOperand.LLT(), // CF == 1
						minusOneBlock.makeJumpTarget(),
						new OPT_BranchProfileOperand(0.33)));
    
    oneBlock.appendInstruction(MIR_Move.create(IA32_MOV, R(res), I(1)));
    oneBlock.appendInstruction(MIR_Branch.create(IA32_JMP, BB6.makeJumpTarget()));

    zeroBlock.appendInstruction(MIR_Move.create(IA32_MOV, R(res), I(0)));
    zeroBlock.appendInstruction(MIR_Branch.create(IA32_JMP, BB6.makeJumpTarget()));

    minusOneBlock.appendInstruction(MIR_Move.create(IA32_MOV, R(res), I(-1)));
    minusOneBlock.appendInstruction(MIR_Branch.create(IA32_JMP, BB6.makeJumpTarget()));


    // fix CFG
    BB1.insertOut(oneBlock);
    BB1.insertOut(zeroBlock);
    BB1.insertOut(minusOneBlock);
    oneBlock.insertOut(BB6);
    zeroBlock.insertOut(BB6);
    minusOneBlock.insertOut(BB6);

    ir.cfg.linkInCodeOrder(BB1, oneBlock);
    ir.cfg.linkInCodeOrder(oneBlock, zeroBlock);
    ir.cfg.linkInCodeOrder(zeroBlock, minusOneBlock);
    ir.cfg.linkInCodeOrder(minusOneBlock, BB6);
    return nextInstr;
  }

  /**
   * compare to values and set result to -1, 0, 1 for <, =, >, respectively
   * @param s the compare instruction
   * @param ir the governing IR
   */
  private static OPT_Instruction threeValueLongCmp (OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    OPT_Register res = Binary.getClearResult(s).register;
    OPT_RegisterOperand one = (OPT_RegisterOperand) Binary.getClearVal1(s);
    OPT_RegisterOperand lone = L(ir.regpool.getSecondReg(one.register));
    OPT_Operand two = Binary.getClearVal2(s);
    OPT_Operand ltwo;
    if (two instanceof OPT_RegisterOperand) {
      ltwo = L(ir.regpool.getSecondReg(((OPT_RegisterOperand)two).register));
    } else {
      OPT_LongConstantOperand tmp = (OPT_LongConstantOperand)two;
      two = I(tmp.upper32());
      ltwo = I(tmp.lower32());
    }
    res.setSpansBasicBlock();
    OPT_BasicBlock BB1 = s.getBasicBlock();
    OPT_BasicBlock BB6 = BB1.splitNodeAt(s, ir);
    s = s.remove();
    OPT_BasicBlock BB2 = BB1.createSubBlock(0, ir);
    OPT_BasicBlock BB3 = BB1.createSubBlock(0, ir);
    OPT_BasicBlock BB4 = BB1.createSubBlock(0, ir);
    OPT_BasicBlock BB5 = BB1.createSubBlock(0, ir);
    BB1.appendInstruction(MIR_Compare.create(IA32_CMP, one, two));
    BB1.appendInstruction(MIR_CondBranch2.create(IA32_JCC2, 
						 OPT_IA32ConditionOperand.LT(),
						 BB4.makeJumpTarget(),
						 new OPT_BranchProfileOperand(0.49),
						 OPT_IA32ConditionOperand.GT(),
						 BB5.makeJumpTarget(),
						 new OPT_BranchProfileOperand(0.49)));
    BB2.appendInstruction(MIR_Compare.create(IA32_CMP, lone, ltwo));
    BB2.appendInstruction(MIR_CondBranch2.create(IA32_JCC2, 
						 OPT_IA32ConditionOperand.LLT(),
						 BB4.makeJumpTarget(),
						 new OPT_BranchProfileOperand(0.49),
						 OPT_IA32ConditionOperand.LGT(),
						 BB5.makeJumpTarget(),
						 new OPT_BranchProfileOperand(0.49)));
    BB3.appendInstruction(MIR_Move.create(IA32_MOV, R(res), I(0)));
    BB3.appendInstruction(MIR_Branch.create(IA32_JMP, BB6.makeJumpTarget()));
    BB4.appendInstruction(MIR_Move.create(IA32_MOV, R(res), I(-1)));
    BB4.appendInstruction(MIR_Branch.create(IA32_JMP, BB6.makeJumpTarget()));
    BB5.appendInstruction(MIR_Move.create(IA32_MOV, R(res), I(1)));
    // fix CFG
    BB1.insertOut(BB2);
    BB1.insertOut(BB4);
    BB1.insertOut(BB5);
    BB2.insertOut(BB3);
    BB2.insertOut(BB4);
    BB2.insertOut(BB5);
    BB3.insertOut(BB6);
    BB4.insertOut(BB6);
    BB5.insertOut(BB6);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    ir.cfg.linkInCodeOrder(BB2, BB3);
    ir.cfg.linkInCodeOrder(BB3, BB4);
    ir.cfg.linkInCodeOrder(BB4, BB5);
    ir.cfg.linkInCodeOrder(BB5, BB6);
    return nextInstr;
  }


  /*
   * This routine expands a yield_point instruction.
   * Split the yield point's basic block just after the yield point instruction.
   * Create a new yield point basic block that jumps to the thread switch code
   *   and then jumps to the split basic block.
   * Before the yield point, test if thread switch flag is on.
   *  Mutate yield point to a conditional jump if true to yield point 
   *  basic block
   */
  private static OPT_Instruction yield_point(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    // get the correct method to be called for a thread switch
    VM_Method meth = null;
    if (s.getOpcode() == YIELDPOINT_PROLOGUE_opcode) {
      meth = VM_OptLinker.optThreadSwitchFromPrologueMethod;
    } else if (s.getOpcode() == YIELDPOINT_EPILOGUE_opcode) {
      meth = VM_OptLinker.optThreadSwitchFromEpilogueMethod;
    } else { 
      meth = VM_OptLinker.optThreadSwitchFromBackedgeMethod;
    }

    // split the basic block after the yieldpoint
    OPT_BasicBlock thisBlock = s.getBasicBlock();
    OPT_BasicBlock nextBlock = thisBlock.splitNodeWithLinksAt(s,ir);
    
    // create a basic block at the end of the IR to hold the yieldpoint   
    OPT_BasicBlock yieldpoint = thisBlock.createSubBlock(s.bcIndex, ir);
    thisBlock.insertOut(yieldpoint);
    yieldpoint.insertOut(nextBlock);
    ir.cfg.addLastInCodeOrder(yieldpoint);
    
    int offset = meth.getOffset();
    // get the jtoc
    OPT_Operand jtoc = 
      OPT_MemoryOperand.BD(R(ir.regpool.getPhysicalRegisterSet().getPR()),
			   VM_Entrypoints.jtocOffset, 
			   (byte)4, null, TG());
    // load jtoc into temporary register
    OPT_RegisterOperand regOp = ir.regpool.makeTempInt();
    yieldpoint.appendInstruction(MIR_Move.create(IA32_MOV, regOp, jtoc));
    // load the address of thread switch from jtoc
    OPT_Operand target =
      OPT_MemoryOperand.BD(regOp.copyD2U(), 
			   offset, (byte)4, new OPT_LocationOperand(offset),
			   TG());
    // call thread switch
    OPT_Instruction call = 
      MIR_Call.create0(CALL_SAVE_VOLATILE, null, null, target, 
		       OPT_MethodOperand.STATIC(meth));
    call.copyPosition(s);
    yieldpoint.appendInstruction(call);
    yieldpoint.appendInstruction(MIR_Branch.create(IA32_JMP,
						   nextBlock.makeJumpTarget())); 
    
    // Check for the threadSwitch bit
    OPT_Register PR = ir.regpool.getPhysicalRegisterSet().getPR();
    int tsr = VM_Entrypoints.threadSwitchRequestedOffset;
    OPT_MemoryOperand M = OPT_MemoryOperand.BD(R(PR),tsr,(byte)4,null,null);
    OPT_Instruction compare = MIR_Compare.create(IA32_CMP, M, I(0));
    s.insertBefore(compare);
    MIR_CondBranch.mutate(s, IA32_JCC, OPT_IA32ConditionOperand.NE(),
			  yieldpoint.makeJumpTarget(),
			  OPT_BranchProfileOperand.unlikely());
    return nextInstr;
  }
}

