/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

//-#if RVM_WITH_OPT_COMPILER
import instructionFormats.*;
//-#endif 

/**
 * This class provides a layer of abstraction that the rest of the VM must
 * use in order to access the current <code>VM_Processor</code> object.
 *
 * @see VM_Processor
 *
 * @author Stephen Fink
 */
final class VM_ProcessorLocalState 
//-#if RVM_WITH_OPT_COMPILER
extends OPT_IRTools
//-#endif 
{
  
  static byte PROCESSOR_REGISTER = VM_RegisterConstants.ESI;

  /**
   * The C bootstrap program has placed a pointer to the initial
   * VM_Processor in ESI.  
   */
  static void boot() {
    // do nothing - everything is already set up.
  }


  /**
   * Return the current VM_Processor object
   */
  static VM_Processor getCurrentProcessor() throws VM_PragmaUninterruptible {
    return VM_Magic.getESIAsProcessor();
  }

  /**
   * Set the current VM_Processor object
   */
  static void setCurrentProcessor(VM_Processor p) throws VM_PragmaUninterruptible {
    VM_Magic.setESIAsProcessor(p);
  }

  /**
   * Emit an instruction sequence to move the value of a register into a field 
   * in the current processor offset 
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   * @param reg number of the register supplying the new value
   */
  static void emitMoveRegToField(VM_Assembler asm, int offset, byte reg) {
    asm.emitMOV_RegDisp_Reg(PROCESSOR_REGISTER,offset,reg);
  }

  /**
   * Emit an instruction sequence to move an immediate value into a field 
   * in the current processor offset 
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   * @param imm immediate value
   */
  static void emitMoveImmToField(VM_Assembler asm, int offset, int imm) {
    asm.emitMOV_RegDisp_Imm(PROCESSOR_REGISTER,offset,imm);
  }

  /**
   * Emit an instruction sequence to move the value of a field in the 
   * current processor offset to a register
   *
   * @param asm assembler object
   * @param dest number of destination register
   * @param offset of field in the <code>VM_Processor</code> object
   */
  static void emitMoveFieldToReg(VM_Assembler asm, byte dest, int offset) {
    asm.emitMOV_Reg_RegDisp(dest,PROCESSOR_REGISTER,offset);
  }

  /**
   * Emit an instruction sequence to compare the value of a field in the 
   * current processor offset with an immediate value
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   * @param imm immediate value to compare with
   */
  static void emitCompareFieldWithImm(VM_Assembler asm, int offset, int imm) {
    asm.emitCMP_RegDisp_Imm(PROCESSOR_REGISTER,offset,imm);
  }
  /**
   * Emit an instruction sequence to decrement the value of a field in the 
   * current processor offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   */
  static void emitDecrementField(VM_Assembler asm, int offset) {
    asm.emitDEC_RegDisp(PROCESSOR_REGISTER,offset);
  }
  /**
   * Emit an instruction sequence to PUSH the value of a field in the 
   * current processor offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   */
  static void emitPushField(VM_Assembler asm, int offset) {
    asm.emitPUSH_RegDisp(PROCESSOR_REGISTER,offset);
  }
  /**
   * Emit an instruction sequence to POP a value into a field in the 
   * current processor offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   */
  static void emitPopField(VM_Assembler asm, int offset) {
    asm.emitPOP_RegDisp(PROCESSOR_REGISTER,offset);
  }

  /**
   * Emit an instruction sequence to set the current VM_Processor 
   * to be the value at [base] + offset
   *
   * <P>TODO: this method is used only by the JNI compiler.  Consider
   * rewriting the JNI compiler to allow us to deprecate this method.
   *
   * @param asm assembler object
   * @param base number of base register
   * @param offset offset
   */
  static void emitSetProcessor(VM_Assembler asm, byte base, int offset) {
    asm.emitMOV_Reg_RegDisp(PROCESSOR_REGISTER, base, offset);
  }

  /**
   * Emit an instruction sequence to PUSH a pointer to the current VM_Processor
   * object on the stack.
   *
   * @param asm assembler object
   */
  static void emitPushProcessor(VM_Assembler asm) {
    asm.emitPUSH_Reg(PROCESSOR_REGISTER);
  }

  /**
   * Emit an instruction sequence to POP a value on the stack, and set the
   * current processor reference to be this value.
   *
   * @param asm assembler object
   */
  static void emitPopProcessor(VM_Assembler asm) {
    asm.emitPOP_Reg(PROCESSOR_REGISTER);
  }

  /**
   * Emit an instruction sequence to store a pointer to the current VM_Processor
   * object at a location defined by [base]+offset
   *
   * @param asm assembler object
   * @param base number of base register
   * @param offset offset
   */
  static void emitStoreProcessor(VM_Assembler asm, byte base, int offset) {
    asm.emitMOV_RegDisp_Reg(base,offset,PROCESSOR_REGISTER);
  }
  /**
   * Emit an instruction sequence to load current VM_Processor
   * object from a location defined by [base]+offset
   *
   * @param asm assembler object
   * @param base number of base register
   * @param offset offset
   */
  static void emitLoadProcessor(VM_Assembler asm, byte base, int offset) {
    asm.emitMOV_Reg_RegDisp(PROCESSOR_REGISTER,base,offset);
  }

  //-#if RVM_WITH_OPT_COMPILER
  /**
   * Insert code during BURS to load a pointer to the current processor
   * into a symbolic register, and return the resultant operand
   */
  static OPT_RegisterOperand insertGetCurrentProcessor(OPT_BURS burs) {
    OPT_RegisterOperand result =
      burs.ir.regpool.makeTemp(OPT_ClassLoaderProxy.VM_ProcessorType);
    OPT_Register ESI = burs.ir.regpool.getPhysicalRegisterSet().getESI();

    burs.append(MIR_Move.create(IA32_MOV,result,R(ESI)));
    return result;
  }

  /**
   * Insert code before instruction s to load a pointer to the current 
   * processor into a symbolic register, and return the resultant operand
   */
  static OPT_RegisterOperand insertGetCurrentProcessor(OPT_IR ir,
                                                       OPT_Instruction s) {
    OPT_RegisterOperand result = ir.regpool.makeTemp
                                 (OPT_ClassLoaderProxy.VM_ProcessorType);
    OPT_Register ESI = ir.regpool.getPhysicalRegisterSet().getESI();

    s.insertBefore(MIR_Move.create(IA32_MOV,result,R(ESI)));
    return result;
  }
  /**
   * Insert code before instruction s to load a pointer to the current 
   * processor into a particular register operand.
   */
  static OPT_RegisterOperand insertGetCurrentProcessor(OPT_IR ir,
                                                       OPT_Instruction s,
                                                       OPT_RegisterOperand rop)
  {
    OPT_Register ESI = ir.regpool.getPhysicalRegisterSet().getESI();

    OPT_RegisterOperand result = rop.copyRO();
    s.insertBefore(MIR_Move.create(IA32_MOV,result,R(ESI)));
    return result;
  }
  /**
   * Insert code after instruction s to set the current 
   * processor to be the value of a particular register operand.
   */
  static OPT_RegisterOperand appendSetCurrentProcessor(OPT_IR ir,
                                                       OPT_Instruction s,
                                                       OPT_RegisterOperand rop)
  {
    OPT_Register ESI = ir.regpool.getPhysicalRegisterSet().getESI();

    s.insertBefore(MIR_Move.create(IA32_MOV,R(ESI),rop.copyRO()));
    return rop;
  }
  //-#endif
}
