/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Machine code generators:
 *
 * Corresponding to a PowerPC assembler instruction of the form
 *    xx A,B,C
 * there will be a method
 *    void emitXX (int A, int B, int C).
 * 
 * The emitXX method appends this instruction to an VM_MachineCode object.
 * The name of a method for generating assembler instruction with the record
 * bit set (say xx.) will be end in a lower-case r (emitXXr).
 * 
 * mIP will be incremented to point to the next machine instruction.
 * 
 * Machine code generators:
 *
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 * @author Derek Lieber
 * @modified Dave Grove
 * @modified Kris Venstermans
 */
public final class VM_Assembler implements VM_BaselineConstants,
				    VM_AssemblerConstants {

  private VM_MachineCode mc;
  private int mIP; // current machine code instruction
  private boolean shouldPrint;
  private VM_Compiler compiler; // VM_Baseline compiler instance for this assembler.  May be null.

  public VM_Assembler (int length) {
    this(length, false);
  }

  public VM_Assembler (int length, boolean sp, VM_Compiler comp) {
    this(length, sp);
    compiler = comp;
  }

  public VM_Assembler (int length, boolean sp) {
    mc = new VM_MachineCode();
    mIP = 0;
    shouldPrint = sp;
  }

  final static boolean fits (long val, int bits) {
    val = val >> bits-1;
    return (val == 0L || val == -1L);
  }

  final static boolean fits (int val, int bits) {
    val = val >> bits-1;
    return (val == 0 || val == -1);
  }

  void noteBytecode (int i, String bcode) {
    String s1 = VM_Services.getHexString(mIP << LG_INSTRUCTION_WIDTH, true);
    VM.sysWrite(s1 + ": [" + i + "] " + bcode + "\n");
  }

  /* Handling backward branch references */

  int getMachineCodeIndex () {
    return mIP;
  }

  /* Handling forward branch references */

  VM_ForwardReference forwardRefs = null;

  /* call before emiting code for the branch */
  final void reserveForwardBranch (int where) {
    VM_ForwardReference fr = new VM_ForwardReference.UnconditionalBranch(mIP, where);
    forwardRefs = VM_ForwardReference.enqueue(forwardRefs, fr);
  }
 
  /* call before emiting code for the branch */
  final void reserveForwardConditionalBranch (int where) {
    emitNOP();
    VM_ForwardReference fr = new VM_ForwardReference.ConditionalBranch(mIP, where);
    forwardRefs = VM_ForwardReference.enqueue(forwardRefs, fr);
  }

  /* call before emiting code for the branch */
  final void reserveShortForwardConditionalBranch (int where) {
    VM_ForwardReference fr = new VM_ForwardReference.ConditionalBranch(mIP, where);
    forwardRefs = VM_ForwardReference.enqueue(forwardRefs, fr);
  }

  /* call before emiting data for the case branch */
  final void reserveForwardCase (int where) {
    VM_ForwardReference fr = new VM_ForwardReference.SwitchCase(mIP, where);
    forwardRefs = VM_ForwardReference.enqueue(forwardRefs, fr);
  }

  /* call before emiting code for the target */
  final void resolveForwardReferences (int label) {
    if (forwardRefs == null) return; 
    forwardRefs = VM_ForwardReference.resolveMatching(this, forwardRefs, label);
  }

  final void patchUnconditionalBranch(int sourceMachinecodeIndex) {
    int delta = mIP - sourceMachinecodeIndex;
    INSTRUCTION instr = mc.getInstruction(sourceMachinecodeIndex);
    if (VM.VerifyAssertions) VM._assert((delta>>>23) == 0); // delta (positive) fits in 24 bits
    instr |= (delta<<2);
    mc.putInstruction(sourceMachinecodeIndex, instr);
  }
  
  final void patchConditionalBranch(int sourceMachinecodeIndex) {
    int delta = mIP - sourceMachinecodeIndex;
    INSTRUCTION instr = mc.getInstruction(sourceMachinecodeIndex);
    if ((delta>>>13) == 0) { // delta (positive) fits in 14 bits
      instr |= (delta<<2);
      mc.putInstruction(sourceMachinecodeIndex, instr);
    } else {
      if (VM.VerifyAssertions) VM._assert((delta>>>23) == 0); // delta (positive) fits in 24 bits
      instr ^= 0x01000008; // make skip instruction with opposite sense
      mc.putInstruction(sourceMachinecodeIndex-1, instr); // skip unconditional branch to target
      mc.putInstruction(sourceMachinecodeIndex,  Btemplate | (delta&0xFFFFFF)<<2);
    }
  }

  final void patchShortBranch(int sourceMachinecodeIndex) {
    int delta = mIP - sourceMachinecodeIndex;
    INSTRUCTION instr = mc.getInstruction(sourceMachinecodeIndex);
    if ((delta>>>13) == 0) { // delta (positive) fits in 14 bits
      instr |= (delta<<2);
      mc.putInstruction(sourceMachinecodeIndex, instr);
    } else {
      throw new InternalError("Long offset doesn't fit in short branch\n");
    }
  }

  //-#if RVM_WITH_OSR
  private int toBePatchedMCAddr;
  private int targetBCpc = -1;
 
  final void registerLoadAddrConst(int target) {
    toBePatchedMCAddr = mIP;
    targetBCpc = target;
  }
 
  /* the prologue is always before any real bytecode index.
   *
   * CAUTION: the machine code to be patched has following pattern:
   *          BL 4
   *          MFLR T1                   <- address in LR
   *          ADDI  T1, offset, T1       <- toBePatchedMCAddr
   *          STU
   *
   * The third instruction should be patched with accurate relative address.
   * It is computed by (mIP - toBePatchedMCAddr + 1)*4;
   */
  final void patchLoadAddrConst(int bIP){
    if (bIP != targetBCpc) return;
 
    int offset = (mIP - toBePatchedMCAddr + 1)*4;
    INSTRUCTION mi = ADDI(T1, offset, T1);
    mc.putInstruction(toBePatchedMCAddr, mi);
    targetBCpc = -1;
  }
 
  final INSTRUCTION ADDI(int RT, int D, int RA) {
    return ADDItemplate | RT << 21 | RA << 16 | (D&0xFFFF);
  }

  public final VM_ForwardReference generatePendingJMP(int bTarget) {
    return this.emitForwardB();
  }
  //-#endif RVM_WITH_OSR

  final void patchSwitchCase(int sourceMachinecodeIndex) {
    int delta = (mIP - sourceMachinecodeIndex) << 2;
    // correction is number of bytes of source off switch base
    int         correction = (int)mc.getInstruction(sourceMachinecodeIndex);
    INSTRUCTION offset = (INSTRUCTION)(delta+correction);
    mc.putInstruction(sourceMachinecodeIndex, offset);
  }


  /* machine instructions */

  static final int ADDtemplate = 31<<26 | 10<<1;

  final void emitADD (int RT, int RA, int RB) {
    INSTRUCTION mi = ADDtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ADDEtemplate = 31<<26 | 138<<1;

  final void emitADDE (int RT, int RA, int RB) {
    INSTRUCTION mi = ADDEtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ADDICrtemplate = 13<<26;

  final void emitADDICr (int RT, int RA, int SI) {
    if (VM.VerifyAssertions) VM._assert(fits(SI, 16));
    INSTRUCTION mi = ADDICrtemplate | RT<<21 | RA<<16 | (SI & 0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ANDtemplate = 31<<26 | 28<<1;

  final void emitAND (int RA, int RS, int RB) {
    INSTRUCTION mi = ANDtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ANDItemplate = 28<<26;

  final void emitANDI (int RA, int RS, int U) {
    if (VM.VerifyAssertions) VM._assert((U>>>16) == 0);
    INSTRUCTION mi = ANDItemplate | RS<<21 | RA<<16 | U;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int Btemplate = 18<<26;

  private final void _emitB (int relative_address) {
    if (VM.VerifyAssertions) VM._assert(fits(relative_address,24));
    INSTRUCTION mi = Btemplate | (relative_address&0xFFFFFF)<<2;
    mIP++;
    mc.addInstruction(mi);
  }

  final void emitB (int relative_address, int label) {
    if (relative_address == 0) {
      reserveForwardBranch(label);
    } else {
      relative_address -= mIP;
    }
    _emitB(relative_address);
  }

  final void emitB (int relative_address) {
    relative_address -= mIP;
    if (VM.VerifyAssertions) VM._assert(relative_address < 0);
    _emitB(relative_address);
  }

  final VM_ForwardReference emitForwardB() {
    VM_ForwardReference fr;
    if (compiler != null) {
      fr = new ShortBranch(mIP, compiler.spTopOffset);
    } else {
      fr = new VM_ForwardReference.ShortBranch(mIP);
    }
    _emitB(0);
    return fr;
  }

  static final int BLAtemplate = 18<<26 | 3;

  final void emitBLA (int address) {
    if (VM.VerifyAssertions) VM._assert(fits(address,24));
    INSTRUCTION mi = BLAtemplate | (address&0xFFFFFF)<<2;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BLtemplate = 18<<26 | 1;

  private final void _emitBL (int relative_address) {
    if (VM.VerifyAssertions) VM._assert(fits(relative_address,24));
    INSTRUCTION mi = BLtemplate | (relative_address&0xFFFFFF)<<2;
    mIP++;
    mc.addInstruction(mi);
  }

  final void emitBL (int relative_address, int label) {
    if (relative_address == 0) {
      reserveForwardBranch(label);
    } else {
      relative_address -= mIP;
    }
    _emitBL(relative_address);
  }


  final VM_ForwardReference emitForwardBL() {
    VM_ForwardReference fr;
    if (compiler != null) { 
      fr = new ShortBranch(mIP, compiler.spTopOffset);
    } else {
      fr = new VM_ForwardReference.ShortBranch(mIP);
    }
    _emitBL(0);
    return fr;
  }

  static final int BCtemplate = 16<<26;

  public static final int flipCode(int cc) {
    switch(cc) {
    case LT: return GE;
    case GT: return LE;
    case EQ: return NE;
    case LE: return GT;
    case GE: return LT;
    case NE: return EQ;
    }
    if (VM.VerifyAssertions) VM._assert(false);
    return -1;
  }

  private final void _emitBC (int cc, int relative_address) {
    if (fits(relative_address, 14)) {
      INSTRUCTION mi = BCtemplate | cc | (relative_address&0x3FFF)<<2;
      mIP++;
      mc.addInstruction(mi);
    } else {
      _emitBC(flipCode(cc), 2);
      _emitB(relative_address-1);
    }
  }

  final void emitBC (int cc, int relative_address, int label) {
    if (relative_address == 0) {
      reserveForwardConditionalBranch(label);
    } else {
      relative_address -= mIP;
    }
    _emitBC(cc, relative_address);
  }

  final void emitShortBC (int cc, int relative_address, int label) {
    if (relative_address == 0) {
      reserveShortForwardConditionalBranch(label);
    } else {
      relative_address -= mIP;
    }
    _emitBC(cc, relative_address);
  }

  final void emitBC (int cc, int relative_address) {
    relative_address -= mIP;
    if (VM.VerifyAssertions) VM._assert(relative_address < 0);
    _emitBC(cc, relative_address);
  }

  final VM_ForwardReference emitForwardBC(int cc) {
    VM_ForwardReference fr;
    if (compiler != null) {
      fr = new ShortBranch(mIP, compiler.spTopOffset);
    } else {
      fr = new VM_ForwardReference.ShortBranch(mIP);
    }
    _emitBC(cc, 0);
    return fr;
  }

  // delta i: difference between address of case i and of delta 0
  final void emitSwitchCase(int i, int relative_address, int bTarget) {
    int data = i << 2;
    if (relative_address == 0) {
      reserveForwardCase(bTarget);
    } else {
      data += ((relative_address - mIP) << 2);
    }
    mIP++;
    mc.addInstruction(data);
  }

  static final int BCLRtemplate = 19<<26 | 0x14<<21 | 16<<1;

  final void emitBCLR () {
    INSTRUCTION mi = BCLRtemplate;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BCLRLtemplate = 19<<26 | 0x14<<21 | 16<<1 | 1;

  final void emitBCLRL () {
    INSTRUCTION mi = BCLRLtemplate;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BCCTRtemplate = 19<<26 | 0x14<<21 | 528<<1;

  public final void emitBCCTR () {
    INSTRUCTION mi = BCCTRtemplate;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BCCTRLtemplate = 19<<26 | 0x14<<21 | 528<<1 | 1;

  final void emitBCCTRL () {
    INSTRUCTION mi = BCCTRLtemplate;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ADDItemplate = 14<<26;

  final void emitADDI (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = ADDItemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ADDIStemplate = 15<<26;

  final void emitADDIS (int RT, int RA, int UI) {
    if (VM.VerifyAssertions) VM._assert(UI == (UI&0xFFFF));
    INSTRUCTION mi = ADDIStemplate | RT<<21 | RA<<16 | UI;
    mIP++;
    mc.addInstruction(mi);
  }

  final void emitADDIS (int RT, int UI) {
    if (VM.VerifyAssertions) VM._assert(UI == (UI&0xFFFF));
    INSTRUCTION mi = ADDIStemplate | RT<<21 | UI;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CMPtemplate = 31<<26;
  final void emitCMP (int BF, int RA, int RB) {
    INSTRUCTION mi = CMPtemplate | BF<<23 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  final void emitCMP (int RA, int RB) {
    INSTRUCTION mi = CMPtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CMPDtemplate = CMPtemplate | 1 << 21;
  final void emitCMPD (int RA, int RB) {
    INSTRUCTION mi = CMPDtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CMPItemplate = 11<<26;
  final void emitCMPI (int BF, int RA, int V) {
    if (VM.VerifyAssertions) VM._assert(fits(V, 16));
    INSTRUCTION mi = CMPItemplate | BF<<23 | RA<<16 | (V&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  final void emitCMPI (int RA, int V) {
    if (VM.VerifyAssertions) VM._assert(fits(V, 16));
    INSTRUCTION mi = CMPItemplate | RA<<16 | (V&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CMPDItemplate = CMPItemplate | 1 << 21;
  final void emitCMPDI (int RA, int V) {
    if (VM.VerifyAssertions) VM._assert(fits(V, 16));
    INSTRUCTION mi = CMPDItemplate | RA<<16 | (V&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CMPLtemplate = 31<<26 | 32<<1;
  final void emitCMPL (int BF, int RA, int RB) {
    INSTRUCTION mi = CMPLtemplate | BF<<23 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  final void emitCMPL (int RA, int RB) {
    INSTRUCTION mi = CMPLtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CMPLDtemplate = CMPLtemplate | 1<<21;
  final void emitCMPLD (int RA, int RB) {
    INSTRUCTION mi = CMPLDtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CRANDtemplate = 19<<26 | 257<<1;

  final void emitCRAND (int BT, int BA, int BB) {
    INSTRUCTION mi = CRANDtemplate | BT<<21 | BA<<16 | BB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CRANDCtemplate = 19<<26 | 129<<1;

  final void emitCRANDC (int BT, int BA, int BB) {
    INSTRUCTION mi = CRANDCtemplate | BT<<21 | BA<<16 | BB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CRORtemplate = 19<<26 | 449<<1;

  final void emitCROR (int BT, int BA, int BB) {
    INSTRUCTION mi = CRORtemplate | BT<<21 | BA<<16 | BB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CRORCtemplate = 19<<26 | 417<<1;

  final void emitCRORC (int BT, int BA, int BB) {
    INSTRUCTION mi = CRORCtemplate | BT<<21 | BA<<16 | BB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FADDtemplate = 63<<26 | 21<<1;

  final void emitFADD (int FRT, int FRA,int FRB) {
    INSTRUCTION mi = FADDtemplate | FRT<<21 | FRA<<16 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FADDStemplate = 59<<26 | 21<<1; // single-percision add

  final void emitFADDS (int FRT, int FRA,int FRB) {
    INSTRUCTION mi = FADDStemplate | FRT<<21 | FRA<<16 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FABStemplate = 63<<26 | 264<<1;

  final void emitFABS (int FRT, int FRB) {
    INSTRUCTION mi = FABStemplate | FRT<<21 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FCMPUtemplate = 63<<26;

  final void emitFCMPU (int FRA,int FRB) {
    INSTRUCTION mi = FCMPUtemplate | FRA<<16 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FDIVtemplate = 63<<26 | 18<<1;

  final void emitFDIV (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FDIVtemplate | FRT<<21 | FRA<<16 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FDIVStemplate = 59<<26 | 18<<1; // single-precision divide

  final void emitFDIVS (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FDIVStemplate | FRT<<21 | FRA<<16 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FMULtemplate = 63<<26 | 25<<1;

  final void emitFMUL (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FMULtemplate | FRT<<21 | FRA<<16 | FRB<<6;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FMULStemplate = 59<<26 | 25<<1; // single-precision fm

  final void emitFMULS (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FMULStemplate | FRT<<21 | FRA<<16 | FRB<<6;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FMADDtemplate = 63<<26 | 29<<1;

  final void emitFMADD (int FRT, int FRA, int FRC, int FRB) {
    INSTRUCTION mi = FMADDtemplate | FRT<<21 | FRA<<16 | FRB<<11 | FRC<<6;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FNMSUBtemplate = 63<<26 | 30<<1;

  final void emitFNMSUB (int FRT, int FRA, int FRC, int FRB) {
    INSTRUCTION mi = FNMSUBtemplate | FRT<<21 | FRA<<16 | FRB<<11 | FRC<<6;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FNEGtemplate = 63<<26 | 40<<1;

  final void emitFNEG (int FRT, int FRB) {
    INSTRUCTION mi = FNEGtemplate | FRT<<21 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FSUBtemplate = 63<<26 | 20<<1;

  final void emitFSUB (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FSUBtemplate | FRT<<21 | FRA<<16 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FSUBStemplate = 59<<26 | 20<<1;

  final void emitFSUBS (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FSUBStemplate | FRT<<21 | FRA<<16 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FSELtemplate = 63<<26 | 23<<1;

  final void emitFSEL (int FRT, int FRA, int FRC, int FRB) {
    INSTRUCTION mi = FSELtemplate | FRT<<21 | FRA<<16 | FRB<<11 | FRC<<6;
    mIP++;
    mc.addInstruction(mi);
  }

  // LOAD/ STORE MULTIPLE

  // TODO!! verify that D is sign extended 
  // (the Assembler Language Reference seems ambiguous) 
  //
  final void emitLMW(int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = (46<<26)  | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  // TODO!! verify that D is sign extended 
  // (the Assembler Language Reference seems ambiguous) 
  //
  final void emitSTMW(int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = (47<<26)  | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }


  static final int LWZtemplate = 32<<26;

  public final void emitLWZ (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = LWZtemplate  | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LBZtemplate = 34<<26;

  final void emitLBZ (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = LBZtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LBZXtemplate = 31<<26 | 87<<1;

  final void emitLBZX (int RT, int RA, int RB) {
    INSTRUCTION mi = LBZXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHAtemplate = 42<<26;

  final void emitLHA (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = LHAtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHZtemplate = 40<<26;

  final void emitLHZ (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = LHZtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFDtemplate = 50<<26;

  public final void emitLFD (int FRT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = LFDtemplate | FRT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFDUtemplate = 51<<26;

  final void emitLFDU (int FRT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = LFDUtemplate | FRT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFDXtemplate = 31<<26 | 599<<1;

  final void emitLFDX (int FRT, int RA, int RB) {
    INSTRUCTION mi = LFDXtemplate | FRT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFStemplate = 48<<26;

  final void emitLFS (int FRT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = LFStemplate | FRT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHAXtemplate = 31<<26 | 343<<1;

  final void emitLHAX (int RT, int RA, int RB) {
    INSTRUCTION mi = LHAXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHZXtemplate = 31<<26 | 279<<1;

  final void emitLHZX (int RT, int RA, int RB) {
    INSTRUCTION mi = LHZXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  final void emitLI (int RT, int D) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = ADDItemplate | RT<<21 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LWZUtemplate = 33<<26;

  final void emitLWZU (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = LWZUtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LWZXtemplate = 31<<26 | 23<<1;

  final void emitLWZX (int RT, int RA, int RB) {
    INSTRUCTION mi = LWZXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LWZUXtemplate = 31<<26 | 55<<1;

  final void emitLWZUX (int RT, int RA, int RB) {
    INSTRUCTION mi = LWZUXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  
  static final int LWARXtemplate = 31<<26 | 20<<1;

  final void emitLWARX (int RT, int RA, int RB) {
    INSTRUCTION mi = LWARXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MFLRtemplate = 31<<26 | 0x08<<16 | 339<<1;

  final void emitMFLR (int RT) {
    INSTRUCTION mi = MFLRtemplate | RT<<21;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MFSPRtemplate = 31<<26 | 339<<1;

  final void emitMFSPR (int RT, int SPR) {
    INSTRUCTION mi = MFSPRtemplate | RT<<21 | SPR<<16;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MTLRtemplate = 31<<26 | 0x08<<16 | 467<<1;

  public final void emitMTLR (int RS) {
    INSTRUCTION mi = MTLRtemplate | RS<<21;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MTCTRtemplate = 31<<26 | 0x09<<16 | 467<<1;

  public final void emitMTCTR (int RS) {
    INSTRUCTION mi = MTCTRtemplate | RS<<21;
    mIP++;
    mc.addInstruction(mi);
  }
 
  static final int MULHWUtemplate = 31<<26 | 11<<1;

  final void emitMULHWU (int RT, int RA, int RB) {
    INSTRUCTION mi = MULHWUtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int DIVWtemplate = 31<<26 | 491<<1;

  final void emitDIVW (int RT, int RA, int RB) {
    INSTRUCTION mi = DIVWtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MULLWtemplate = 31<<26 | 235<<1;

  final void emitMULLW (int RT, int RA, int RB) {
    INSTRUCTION mi = MULLWtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int NEGtemplate = 31<<26 | 104<<1;

  final void emitNEG (int RT, int RA) {
    INSTRUCTION mi = NEGtemplate | RT<<21 | RA<<16;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ORtemplate = 31<<26 | 444<<1;

  final void emitOR (int RA, int RS, int RB) {
    INSTRUCTION mi = ORtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ORItemplate = 24<<26;

  final void emitORI (int RA, int RS, int UI) {
    if (VM.VerifyAssertions) VM._assert(UI == (UI&0xFFFF));
    INSTRUCTION mi = ORItemplate | RS<<21 | RA<<16 | (UI&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ORIStemplate = 25<<26;

  final void emitORIS (int RA, int RS, int UI) {
    if (VM.VerifyAssertions) VM._assert(UI == (UI&0xFFFF));
    INSTRUCTION mi = ORIStemplate | RS<<21 | RA<<16 | (UI&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int RLWINM_template = 21<<26;

  final void emitRLWINM (int RA, int RS, int SH, int MB, int ME) {
    INSTRUCTION mi = RLWINM_template | RS<<21 | RA<<16 | SH<<11 | MB<<6 | ME<<1;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SUBFCrtemplate = 31<<26 | 8<<1 | 1;

  final void emitSUBFCr (int RT, int RA, int RB) {
    INSTRUCTION mi = SUBFCrtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SUBFCtemplate = 31<<26 | 8<<1;

  final void emitSUBFC (int RT, int RA, int RB) {
    INSTRUCTION mi = SUBFCtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SUBFICtemplate = 8<<26;

  final void emitSUBFIC (int RA, int RS, int S) {
    if (VM.VerifyAssertions) VM._assert(fits(S,16));
    INSTRUCTION mi = SUBFICtemplate | RS<<21 | RA<<16 | S;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SUBFErtemplate = 31<<26 | 136<<1 | 1;

  final void emitSUBFEr (int RT, int RA, int RB) {
    INSTRUCTION mi = SUBFErtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SUBFEtemplate = 31<<26 | 136<<1;

  final void emitSUBFE (int RT, int RA, int RB) {
    INSTRUCTION mi = SUBFEtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SUBFZEtemplate = 31<<26 | 200<<1;

  final void emitSUBFZE (int RT, int RA) {
    INSTRUCTION mi = SUBFZEtemplate | RT<<21 | RA<<16;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SLWtemplate = 31<<26 | 24<<1;

  final void emitSLW (int RA, int RS, int RB) {
    INSTRUCTION mi = SLWtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SLWItemplate = 21<<26;

  final void emitSLWI (int RA, int RS, int N) {
    INSTRUCTION mi = SLWItemplate | RS<<21 | RA<<16 | N<<11 | (31-N)<<1;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRWtemplate = 31<<26 | 536<<1;

  final void emitSRW (int RA, int RS, int RB) {
    INSTRUCTION mi = SRWtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRAWtemplate = 31<<26 | 792<<1;

  final void emitSRAW (int RA, int RS, int RB) {
    INSTRUCTION mi = SRAWtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRAWItemplate = 31<<26 | 824<<1;

  final void emitSRAWI (int RA, int RS, int SH) {
    INSTRUCTION mi = SRAWItemplate | RS<<21 | RA<<16 | SH<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRAWIrtemplate = 31<<26 | 824<<1 | 1;

  final void emitSRAWIr (int RA, int RS, int SH) {
    INSTRUCTION mi = SRAWIrtemplate | RS<<21 | RA<<16 | SH<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STWtemplate = 36<<26;

  final void emitSTW (int RS, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = STWtemplate | RS<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STBtemplate = 38<<26;

  final void emitSTB (int RS, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = STBtemplate | RS<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STBXtemplate = 31<<26 | 215<<1;

  final void emitSTBX (int RS, int RA, int RB) {
    INSTRUCTION mi = STBXtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STHXtemplate = 31<<26 | 407<<1;

  final void emitSTHX (int RS, int RA, int RB) {
    INSTRUCTION mi = STHXtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STWXtemplate = 31<<26 | 151<<1;

  final void emitSTWX (int RS, int RA, int RB) {
    INSTRUCTION mi = STWXtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFDtemplate = 54<<26;

  final void emitSTFD (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = STFDtemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFDUtemplate = 55<<26;

  final void emitSTFDU (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = STFDUtemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFDXtemplate = 31<<26 | 727<<1;

  final void emitSTFDX (int FRS, int RA, int RB) {
    INSTRUCTION mi = STFDXtemplate | FRS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFStemplate = 52<<26;

  final void emitSTFS (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = STFStemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFSUtemplate = 53<<26;

  final void emitSTFSU (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = STFSUtemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STWUtemplate = 37<<26;

  final void emitSTWU (int RS, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    INSTRUCTION mi = STWUtemplate | RS<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STWUXtemplate = 31<<26 | 183<<1;

  final void emitSTWUX (int RS, int RA, int RB) {
    INSTRUCTION mi = STWUXtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STWCXrtemplate = 31<<26 | 150<<1 | 1;

  final void emitSTWCXr (int RS, int RA, int RB) {
    INSTRUCTION mi = STWCXrtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TWtemplate = 31<<26 | 4<<1;
  static final int TWLEtemplate = TWtemplate | 0x14<<21 ;

  final void emitTWLE (int RA, int RB) {
    INSTRUCTION mi = TWLEtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TWLTtemplate = TWtemplate | 0x10<<21;

  final void emitTWLT (int RA, int RB) {
    INSTRUCTION mi = TWLTtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TWNEtemplate = TWtemplate | 0x18<<21;

  final void emitTWNE (int RA, int RB) {
    INSTRUCTION mi = TWNEtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TWLLEtemplate = TWtemplate | 0x6<<21;

  final void emitTWLLE (int RA, int RB) {
    INSTRUCTION mi = TWLLEtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TWItemplate = 3<<26;

  final void emitTWI (int TO, int RA, int SI) {
    INSTRUCTION mi = TWItemplate | TO<<21 | RA<<16 | SI&0xFFFF;
    mIP++;
    mc.addInstruction(mi);
  }
  
  static final int TWEQItemplate = TWItemplate | 0x4<<21;

  final void emitTWEQ0 (int RA) {
    INSTRUCTION mi = TWEQItemplate | RA<<16;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TWWItemplate = TWItemplate | 0x3EC<<16;	// RA == 12

  final void emitTWWI (int imm) {
    INSTRUCTION mi = TWWItemplate | imm;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int XORtemplate = 31<<26 | 316<<1;

  final void emitXOR (int RA, int RS, int RB) {
    INSTRUCTION mi = XORtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int XORItemplate = 26<<26;

  final void emitXORI (int RA, int RS, int V) {
    if (VM.VerifyAssertions) VM._assert(fits(V, 16));
    INSTRUCTION mi = XORItemplate |  RS<<21 | RA<<16  | V&0xFFFF;
    mIP++;
    mc.addInstruction(mi);
  }

  /* macro instructions */

  final void emitNOP () {
    INSTRUCTION mi = 24<<26; //ORI 0,0,0
    mIP++;
    mc.addInstruction(mi);
  }

  final void emitLDoffset(int RT, int RA, int offset) {
    if (fits(offset, 16)) {
      emitLD(RT, offset, RA);
    } else if ((offset & 0x8000) == 0) {
      emitADDIS(RT, RA, offset>>16);
      emitLD (RT, offset&0xFFFF, RT);
    } else {
      emitADDIS(RT, RA, (offset>>16)+1);
      emitLD (RT, offset|0xFFFF0000, RT);
    }
  }
  
  final void emitLWAoffset(int RT, int RA, int offset) {
    if (fits(offset, 16)) {
      emitLWA (RT, offset, RA);
    } else if ((offset & 0x8000) == 0) {
      emitADDIS(RT, RA, offset>>16);
      emitLWA (RT, offset&0xFFFF, RT);
    } else {
      emitADDIS(RT, RA, (offset>>16)+1);
      emitLWA (RT, offset|0xFFFF0000, RT);
    }
  }
    
  final void emitLWZoffset(int RT, int RA, int offset) {
    if (fits(offset, 16)) {
      emitLWZ (RT, offset, RA);
    } else if ((offset & 0x8000) == 0) {
      emitADDIS(RT, RA, offset>>16);
      emitLWZ (RT, offset&0xFFFF, RT);
    } else {
      emitADDIS(RT, RA, (offset>>16)+1);
      emitLWZ (RT, offset|0xFFFF0000, RT);
    }
  }
    
  public final void emitLDtoc (int RT, int offset) {
    emitLDoffset(RT, JTOC, offset); 
  }

  public final void emitLWAtoc (int RT, int offset) {
    emitLWAoffset(RT, JTOC, offset);
  }

  public final void emitLWZtoc (int RT, int offset) {
    emitLWZoffset(RT, JTOC, offset);
  }

  final void emitSTDtoc (int RT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitSTD(RT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitADDIS(Rz, JTOC, offset>>16);
      emitSTD(RT, offset&0xFFFF, Rz);
    } else {
      emitADDIS(Rz, JTOC, (offset>>16)+1);
      emitSTD(RT, offset|0xFFFF0000, Rz);
    }
  } 

  final void emitSTWtoc (int RT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitSTW(RT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitADDIS(Rz, JTOC, offset>>16);
      emitSTW(RT, offset&0xFFFF, Rz);
    } else {
      emitADDIS(Rz, JTOC, (offset>>16)+1);
      emitSTW(RT, offset|0xFFFF0000, Rz);
    }
  }

  final void emitADDItoc (int RT, int offset) {
    if (fits(offset, 16)) {
      emitADDI(RT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitADDIS(RT, JTOC, offset>>16);
      emitADDI(RT, offset&0xFFFF, RT);
    } else {
      emitADDIS(RT, JTOC, (offset>>16)+1);
      emitADDI(RT, offset|0xFFFF0000, RT);
    }
  }

  final void emitLFDtoc (int FRT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitLFD(FRT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitADDIS( Rz, JTOC, offset>>16);
      emitLFD(FRT, offset&0xFFFF, Rz);
    } else {
      emitADDIS( Rz, JTOC, (offset>>16)+1);
      emitLFD(FRT, offset|0xFFFF0000, Rz);
    }
  }

  final void emitSTFDtoc (int FRT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitSTFD(FRT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitADDIS ( Rz, JTOC, offset>>16);
      emitSTFD(FRT, offset&0xFFFF, Rz);
    } else {
      emitADDIS ( Rz, JTOC, (offset>>16)+1);
      emitSTFD(FRT, offset|0xFFFF0000, Rz);
    }
  }

  final void emitLFStoc (int FRT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitLFS(FRT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitADDIS( Rz, JTOC, offset>>16);
      emitLFS(FRT, offset&0xFFFF, Rz);
    } else {
      emitADDIS( Rz, JTOC, (offset>>16)+1);
      emitLFS(FRT, offset|0xFFFF0000, Rz);
    }
  }

  final void emitSTFStoc (int FRT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitSTFS(FRT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitADDIS ( Rz, JTOC, offset>>16);
      emitSTFS(FRT, offset&0xFFFF, Rz);
    } else {
      emitADDIS ( Rz, JTOC, (offset>>16)+1);
      emitSTFS(FRT, offset|0xFFFF0000, Rz);
    }
  }

  final void emitLVALAddr (int RT, VM_Address addr) {
//-#if RVM_FOR_64_ADDR
    long val = addr.toLong();
    if (!fits(val,48)){
      emitADDIS(RT, (int)(val>>>48));
      emitORI(RT, RT, (int)((val>>>32)&0xFFFF));
      emitSLDI(RT,RT,32);
      emitORIS(RT, RT, (int)((val>>>16)&0xFFFF));
      emitORI(RT, RT, (int)(val&0xFFFF));
    } else if (!fits(val,32)){
      emitLI(RT, (int)(val>>>32));
      emitSLDI(RT,RT,32);
      emitORIS(RT, RT, (int)((val>>>16)&0xFFFF));
      emitORI(RT, RT, (int)(val&0xFFFF));
    } else 
//-#else
    int val = addr.toInt();
//-#endif         
      if (!fits(val,16)){
      emitADDIS(RT, (int)(val>>>16));
      emitORI(RT, RT, (int)(val&0xFFFF));
    } else {
      emitLI(RT, (int)val);
    }
  }

  final void emitLVAL (int RT, int val) {
    if (fits(val, 16)) { 
      emitLI(RT, val);
    } else { 
      emitADDIS(RT, val>>>16);
      emitORI(RT, RT, val&0xFFFF);
    }
    /*if (fits(val, 16)) { 
      emitLI(RT, val);
    } else if ((val&0x8000) == 0) {
      emitLI(RT, val&0xFFFF);
      emitADDIS(RT, RT,  val>>>16);
    } else {// top half of RT is 0xFFFF
      emitLI(RT, val|0xFFFF0000);
      emitADDIS(RT, RT, (val>>>16)+1);
    }*/
  }

  // Convert generated machine code into final form.
  //
  VM_MachineCode finalizeMachineCode (int[] bytecodeMap) {
    mc.setBytecodeMap(bytecodeMap);
    return makeMachineCode();
  }

  public VM_MachineCode makeMachineCode () {
    mc.finish();
    if (shouldPrint) {
      VM.sysWriteln();
      INSTRUCTION[] instructions = mc.getInstructions();
      boolean saved = VM_BaselineCompiler.options.PRINT_MACHINECODE;
      try {
	VM_BaselineCompiler.options.PRINT_MACHINECODE = false;
	for (int i = 0; i < instructions.length; i++) {
	  VM.sysWrite(VM_Services.getHexString(i << LG_INSTRUCTION_WIDTH, true));
	  VM.sysWrite(" : ");
	  VM.sysWrite(VM_Services.getHexString(instructions[i], false));
	  VM.sysWrite("  ");
	  VM.sysWrite(PPC_Disassembler.disasm(instructions[i], i << LG_INSTRUCTION_WIDTH));
	  VM.sysWrite("\n");
	}
      } finally {
	VM_BaselineCompiler.options.PRINT_MACHINECODE = saved;
      }
    }
    return mc;
  }

  /**
   * Append an array of INSTRUCTION to the current machine code
   */
  void appendInstructions (INSTRUCTION[] instructionSegment) {
    for (int i=0; i<instructionSegment.length; i++) {
      mIP++;
      mc.addInstruction(instructionSegment[i]);
    }
  }

  // new PowerPC instuctions
    
  // The "sync" on Power 4 architectures are expensive and so we use "lwsync" instead to
  //   implement SYNC.  On older arhictectures, there is no problem but the weaker semantics
  //   of lwsync means that there are memory consistency bugs we might need to flush out.
  // static final int SYNCtemplate = 31<<26 | 598<<1;
  // static final int LWSYNCtemplate = 31<<26 | 1 << 21 | 598<<1;
  static final int SYNCtemplate = 31<<26 | 1 << 21 | 598<<1;

  final void emitSYNC () {
    INSTRUCTION mi = SYNCtemplate;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ICBItemplate = 31<<26 | 982<<1;
  
  final void emitICBI (int RA, int RB) {
    INSTRUCTION mi = ICBItemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ISYNCtemplate = 19<<26 | 150<<1;
  
  final void emitISYNC () {
    INSTRUCTION mi = ISYNCtemplate;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int DCBFtemplate = 31<<26 | 86<<1;
  
  final void emitDCBF (int RA, int RB) {
    INSTRUCTION mi = DCBFtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int DCBSTtemplate = 31<<26 | 54<<1;
  
  final void emitDCBST (int RA, int RB) {
    INSTRUCTION mi = DCBSTtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MFTBtemplate = 31<<26 | 392<<11 | 371<<1;
  
  final void emitMFTB (int RT) {
    INSTRUCTION mi = MFTBtemplate | RT<<21;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MFTBUtemplate = 31<<26 | 424<<11 | 371<<1;
  
  final void emitMFTBU (int RT) {
    INSTRUCTION mi = MFTBUtemplate | RT<<21;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FCTIWZtemplate = 63<<26 | 15<<1;
  
  final void emitFCTIWZ (int FRT, int FRB) {
    INSTRUCTION mi = FCTIWZtemplate | FRT<<21 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int EXTSBtemplate = 31<<26 | 954<<1;

  final void emitEXTSB (int RA, int RS) {
    INSTRUCTION mi = EXTSBtemplate | RS<<21 | RA<<16;
    mIP++;
    mc.addInstruction(mi);
  }


  // PowerPC 64-bit instuctions
  static final int DIVDtemplate = 31<<26 | 489<<1;

  final void emitDIVD (int RT, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = DIVDtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int EXTSWtemplate = 31<<26 | 986<<1;

  final void emitEXTSW (int RA, int RS) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = EXTSWtemplate | RS<<21 | RA<<16;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FCTIDZtemplate = 63<<26 | 815<<1;
  
  final void emitFCTIDZ (int FRT, int FRB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = FCTIDZtemplate | FRT<<21 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FCFIDtemplate = 63<<26 | 846<<1;

  final void emitFCFID (int FRT, int FRB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = FCFIDtemplate | FRT<<21 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LDtemplate = 58<<26;

  public final void emitLD (int RT, int DS, int RA) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    if (VM.VerifyAssertions) VM._assert(fits(DS, 16));
    INSTRUCTION mi = LDtemplate  | RT<<21 | RA<<16 | (DS&0xFFFC);
    mIP++;
    mc.addInstruction(mi);
  }
  
  static final int LDARXtemplate = 31<<26 | 84<<1;

  final void emitLDARX (int RT, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = LDARXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LDUtemplate = 58<<26 | 1;

  final void emitLDU (int RT, int DS, int RA) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    if (VM.VerifyAssertions) VM._assert(fits(DS, 16));
    INSTRUCTION mi = LDUtemplate | RT<<21 | RA<<16 | (DS&0xFFFC);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LDUXtemplate = 31<<26 | 53<<1;

  final void emitLDUX (int RT, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = LDUXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LDXtemplate = 31<<26 | 21<<1;

  final void emitLDX (int RT, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = LDXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LWAtemplate = 58<<26 | 2;

  final void emitLWA (int RT, int DS, int RA) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    if (VM.VerifyAssertions) VM._assert(fits(DS, 16));
    INSTRUCTION mi = LWAtemplate | RT<<21 | RA<<16 | (DS&0xFFFC);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LWAXtemplate = 31<<26 | 341<<1;

  final void emitLWAX (int RT, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = LWAXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MULHDUtemplate = 31<<26 | 9<<1;

  final void emitMULHDU (int RT, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = MULHDUtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MULLDtemplate = 31<<26 | 233<<1;

  final void emitMULLD (int RT, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = MULLDtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SLDItemplate = 30<<26 | 1<<2;

  final void emitSLDI (int RA, int RS, int N) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = SLDItemplate | RS<<21 | RA<<16 | (N&0x1F)<<11 | ((31-N)&0x1F)<<6 | ((31-N)&0x20) | (N&0x20)>>4 ;
    mIP++;
    mc.addInstruction(mi);
  }

  final void emitRLDINM (int RA, int RS, int SH, int MB, int ME) {
    VM._assert(false, "PLEASE IMPLEMENT ME");
  }

  static final int SLDtemplate = 31<<26 | 27<<1;

  final void emitSLD (int RA, int RS, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = SLDtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRADtemplate = 31<<26 | 794<<1;

  final void emitSRAD (int RA, int RS, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = SRADtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRADItemplate = 31<<26 | 413<<2;

  final void emitSRADI (int RA, int RS, int SH) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = SRADItemplate | RS<<21 | RA<<16 | (SH&0x1F)<<11 | (SH&0x20)>>4;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRADIrtemplate = SRADItemplate | 1;

  final void emitSRADIr (int RA, int RS, int SH) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = SRADIrtemplate | RS<<21 | RA<<16 | (SH&0x1F)<<11 | (SH&0x20)>>4;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRDtemplate = 31<<26 | 539<<1;

  final void emitSRD (int RA, int RS, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = SRDtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STDtemplate = 62<<26;

  final void emitSTD (int RS, int DS, int RA) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    if (VM.VerifyAssertions) VM._assert(fits(DS, 16));
    INSTRUCTION mi = STDtemplate | RS<<21 | RA<<16 | (DS&0xFFFC);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STDCXrtemplate = 31<<26 | 214<<1 | 1;

  final void emitSTDCXr (int RS, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = STDCXrtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STDUtemplate = 62<<26 | 1;

  final void emitSTDU (int RS, int DS, int RA) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    if (VM.VerifyAssertions) VM._assert(fits(DS, 16));
    INSTRUCTION mi = STDUtemplate | RS<<21 | RA<<16 | (DS&0xFFFC);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STDUXtemplate = 31<<26 | 181<<1;

  final void emitSTDUX (int RS, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = STDUXtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STDXtemplate = 31<<26 | 149<<1;

  final void emitSTDX (int RS, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = STDXtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TDtemplate = 31<<26 | 68<<1;
  static final int TDLEtemplate = TDtemplate | 0x14<<21;

  final void emitTDLE (int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = TDLEtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TDLTtemplate = TDtemplate | 0x10<<21;

  final void emitTDLT (int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = TDLTtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TDLLEtemplate = TDtemplate | 0x6<<21 ;

  final void emitTDLLE (int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = TDLLEtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TDItemplate = 2<<26;

  final void emitTDI (int TO, int RA, int SI) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = TDItemplate | TO<<21 | RA<<16 | SI&0xFFFF;
    mIP++;
    mc.addInstruction(mi);
  }
  
  static final int TDEQItemplate = TDItemplate | 0x4<<21;

  final void emitTDEQ0 (int RA) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = TDEQItemplate | RA<<16;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TDWItemplate = TDItemplate | 0x1F<<21 | 0xC<<16;	

  final void emitTDWI (int SI) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    INSTRUCTION mi = TDWItemplate | SI&0xFFFF;
    mIP++;
    mc.addInstruction(mi);
  }

  // -------------------------------------------------------------- //
  // The following section contains macros to handle address values //
  // -------------------------------------------------------------- //
  public final void emitCMPAddr(int reg1, int reg2){                    
    if (VM.BuildFor64Addr)
      emitCMPLD(reg1, reg2);
    else
      emitCMPL(reg1, reg2);
  }

  public final void emitSTAddr(int src_reg, int offset, int dest_reg){                    
    if (VM.BuildFor64Addr)
      emitSTD(src_reg, offset, dest_reg);                   
    else
      emitSTW(src_reg, offset, dest_reg);                                    
  }

  public final void emitSTAddrX(int src_reg, int offset_reg, int dest_reg){           
    if (VM.BuildFor64Addr) 
      emitSTDX(src_reg, offset_reg, dest_reg);           
    else 
      emitSTWX(src_reg, offset_reg, dest_reg);                            
  }

  public final void emitSTAddrU(int src_reg, int offset, int dest_reg){            
    if (VM.BuildFor64Addr) 
      emitSTDU(src_reg, offset, dest_reg);           
    else 
      emitSTWU(src_reg, offset, dest_reg);                            
  }

  public final void emitLAddr(int dest_reg, int offset, int src_reg){         
    if (VM.BuildFor64Addr) 
      emitLD(dest_reg, offset, src_reg);         
    else 
      emitLWZ(dest_reg, offset, src_reg);                         
  }

  public final void emitLAddrX(int dest_reg, int offset_reg, int src_reg){ 
    if (VM.BuildFor64Addr) 
      emitLDX(dest_reg, offset_reg, src_reg);
    else 
      emitLWZX(dest_reg, offset_reg, src_reg);
  }

  final void emitLAddrU(int dest_reg, int offset, int src_reg){         
    if (VM.BuildFor64Addr) 
      emitLDU(dest_reg, offset, src_reg);         
    else 
      emitLWZU(dest_reg, offset, src_reg);                         
  }

  public final void emitLAddrToc(int dest_reg, int TOCoffset){            
    if (VM.BuildFor64Addr) 
      emitLDtoc(dest_reg, TOCoffset);
    else 
      emitLWZtoc(dest_reg, TOCoffset);
  }
  
  final void emitRLAddrINM (int RA, int RS, int SH, int MB, int ME) {
    if (VM.BuildFor64Addr) 
      emitRLDINM(RA, RS, SH, MB, ME);
    else 
      emitRLWINM(RA, RS, SH, MB, ME);
  }

  public final void emitLInt(int dest_reg, int offset, int src_reg){         
    if (VM.BuildFor64Addr) 
      emitLWA(dest_reg, offset, src_reg);         
    else 
      emitLWZ(dest_reg, offset, src_reg);                         
  }

  public final void emitLIntX(int dest_reg, int offset_reg, int src_reg){         
    if (VM.BuildFor64Addr) 
      emitLWAX(dest_reg, offset_reg, src_reg);         
    else 
      emitLWZX(dest_reg, offset_reg, src_reg);                         
  }

  public final void emitLIntToc(int dest_reg, int TOCoffset){            
    if (VM.BuildFor64Addr) 
      emitLWAtoc(dest_reg, TOCoffset);
    else 
      emitLWZtoc(dest_reg, TOCoffset);
  }
  
  final void emitLIntOffset(int RT, int RA, int offset) {
    if (VM.BuildFor64Addr) 
      emitLWAoffset(RT, RA, offset);
    else 
      emitLWZoffset(RT, RA, offset);
  }

  // -----------------------------------------------------------//
  // The following section contains assembler "macros" used by: //
  //    VM_Compiler                                             //
  //    VM_Barriers                                             //
  // -----------------------------------------------------------//
  
  // Emit baseline stack overflow instruction sequence.
  // Before:   FP is current (calling) frame
  //           PR is the current VM_Processor, which contains a pointer to the active thread.
  // After:    R0, S0 destroyed
  //
  void emitStackOverflowCheck (int frameSize) {
    emitLAddr ( 0,  VM_Entrypoints.activeThreadStackLimitField.getOffset(), PROCESSOR_REGISTER);   // R0 := &stack guard page
    emitADDI(S0, -frameSize, FP);                        // S0 := &new frame
    if (VM.BuildFor64Addr)
      emitTDLT (S0,  0);                                    // trap if new frame below guard page
    else 
      emitTWLT (S0,  0);                                    // trap if new frame below guard page
  }

  // Emit baseline stack overflow instruction sequence for native method prolog.
  // For the lowest Java to C transition frame in the stack, check that there is space of
  // STACK_SIZE_NATIVE words available on the stack;  enlarge stack if necessary.
  // For subsequent Java to C transition frames, check for the requested size and don't resize
  // the stack if overflow
  // Before:   FP is current (calling) frame
  //           PR is the current VM_Processor, which contains a pointer to the active thread.
  // After:    R0, S0 destroyed
  //
  void emitNativeStackOverflowCheck (int frameSize) {
    emitLAddr   (S0, VM_Entrypoints.activeThreadField.getOffset(), PROCESSOR_REGISTER);   // S0 := thread pointer
    emitLAddr   (S0, VM_Entrypoints.jniEnvField.getOffset(), S0);      // S0 := thread.jniEnv
    emitLInt   ( 0, VM_Entrypoints.JNIRefsTopField.getOffset(),S0);   // R0 := thread.jniEnv.JNIRefsTop
//Kris Venstermans :    change to Address?
    emitLAddr   (S0, VM_Entrypoints.activeThreadField.getOffset(), PROCESSOR_REGISTER);   // S0 := thread pointer
    emitCMPI ( 0, 0);                                 	 // check if S0 == 0 -> first native frame on stack
    VM_ForwardReference fr1 = emitForwardBC(EQ);
    // check for enough space for requested frame size
    emitLAddr  ( 0,  VM_Entrypoints.stackLimitField.getOffset(), S0);  // R0 := &stack guard page
    emitADDI(S0, -frameSize, FP);                        // S0 := &new frame pointer
    emitTWLT (S0,  0);                                    // trap if new frame below guard page
    VM_ForwardReference fr2 = emitForwardB();

    // check for enough space for STACK_SIZE_JNINATIVE 
    fr1.resolve(this);
    emitLAddr (0,  VM_Entrypoints.stackLimitField.getOffset(), S0);  // R0 := &stack guard page
    emitLVAL  (S0, STACK_SIZE_JNINATIVE);
    emitSUBFC (S0, S0, FP);             // S0 := &new frame pointer

    emitCMPAddr(0, S0);
    VM_ForwardReference fr3 = emitForwardBC(LE);
    emitTWWI ( 1 );                                    // trap if new frame pointer below guard page
    fr2.resolve(this);
    fr3.resolve(this);
  }

  private static class ShortBranch extends VM_ForwardReference.ShortBranch {
    final int spTopOffset;
    
    ShortBranch (int source, int sp) {
      super(source);
      spTopOffset = sp;
    }
    void resolve (VM_Assembler asm) {
      super.resolve(asm);
      if (asm.compiler != null) {
	asm.compiler.spTopOffset = spTopOffset;
      }
    }
  }
}
