/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.Enumeration;

/**
 * This class represents a set of OPT_Registers corresponding to the
 * IA32 register set.
 *
 * @author Stephen Fink
 */
class OPT_PhysicalRegisterSet extends OPT_GenericPhysicalRegisterSet
    implements VM_RegisterConstants, OPT_PhysicalRegisterConstants {

  /**
   * This array holds a pool of objects representing physical registers
   */
  private OPT_Register[] reg = new OPT_Register[getSize()];

  /**
   * Cache the set of volatile registers for efficiency
   */
  private OPT_BitSet volatileSet;

  /**
   * Cache the set of floating-point registers for efficiency
   */
  private OPT_BitSet fpSet;

  /**
   * Return the total number of physical registers.
   */
  static final int getSize() {
    return NUM_GPRS + NUM_FPRS + NUM_SPECIALS;
  }

  /**
   * Return the total number of physical registers.
   */
  final int getNumberOfPhysicalRegisters() {
    return getSize();
  }

  /**
   * Return the total number of nonvolatile GPRs.
   */
  static final int getNumberOfNonvolatileGPRs() {
    return NUM_NONVOLATILE_GPRS;
  }

  /**
   * Return the total number of GPRs that may hold parameters.
   */
  static final int getNumberOfGPRParams() {
      return NUM_PARAMETER_GPRS;
  }

  /**
   * Return the total number of FPRs that may hold parameters.
   */
  static final int getNumberOfFPRParams() {
      return NUM_PARAMETER_FPRS;
  }


  /**
   * Return the (zero-based indexed) nth GPR that may hold a parameter.
   */
  final OPT_Register getGPRParam(int n) {
      if (VM.VerifyAssertions) VM.assert(n < NUM_PARAMETER_GPRS);
      return getGPR(VOLATILE_GPRS[n]);
  }

  /**
   * Return the (zero-based indexed) nth FPR that may hold a parameter.
   */
  final OPT_Register getFPRParam(int n) {
      return getFPR(VOLATILE_FPRS[n]);
  }

  /**
   * @return the first GPR return
   */
  OPT_Register getReturnGPR(int n) {
      if (VM.VerifyAssertions) VM.assert(n < NUM_RETURN_GPRS);
      return getGPR(VOLATILE_GPRS[n]);
  }

  /**
   * Constructor: set up a pool of physical registers.
   */
  OPT_PhysicalRegisterSet() {
    
    // 1. Create all the physical registers in the pool.
    for (int i = 0; i < reg.length ; i++) {
      OPT_Register r = new OPT_Register(i);
      r.setPhysical();
      reg[i] = r;
    }
    
    // 2. Set the 'integer' attribute on each GPR
    for (int i = FIRST_INT; i < FIRST_DOUBLE; i++) {
      reg[i].setInteger();
    }
    
    // 3. Set the 'double' attribute on each FPR
    for (int i = FIRST_DOUBLE; i < FIRST_SPECIAL; i++) {
      reg[i].setDouble();
    }

    // 4. set up the volatile GPRs
    for (Enumeration e = enumerateVolatileGPRs(); e.hasMoreElements(); ) {
      OPT_Register r = (OPT_Register)e.nextElement();
      r.setVolatile();
    }
    
    // 5. set up the non-volatile GPRs
    for (Enumeration e = enumerateNonvolatileGPRs(); e.hasMoreElements(); ) {
      OPT_Register r = (OPT_Register)e.nextElement();
      r.setNonVolatile();
    }

    // 6. set properties on some special registers
    reg[AF].setSpansBasicBlock();
    reg[CF].setSpansBasicBlock();
    reg[OF].setSpansBasicBlock();
    reg[PF].setSpansBasicBlock();
    reg[SF].setSpansBasicBlock();
    reg[ZF].setSpansBasicBlock();
    reg[C0].setSpansBasicBlock();
    reg[C1].setSpansBasicBlock();
    reg[C2].setSpansBasicBlock();
    reg[C3].setSpansBasicBlock();
    reg[PROCESSOR_REGISTER].setSpansBasicBlock();
    reg[FRAME_POINTER].setSpansBasicBlock();

    // 7. set up the volatile FPRs
    for (Enumeration e = enumerateVolatileFPRs(); e.hasMoreElements(); ) {
      OPT_Register r = (OPT_Register)e.nextElement();
      r.setVolatile();
    }

    // 8. set up the non-volatile FPRs
    for (Enumeration e = enumerateNonvolatileFPRs(); e.hasMoreElements(); ) {
      OPT_Register r = (OPT_Register)e.nextElement();
      r.setNonVolatile();
    }

    // 9. Cache the volatile registers for efficiency
    volatileSet = new OPT_BitSet(this);
    for (Enumeration e = enumerateVolatiles(); e.hasMoreElements(); ) {
      OPT_Register r = (OPT_Register)e.nextElement();
      volatileSet.add(r);
    }

    // 10. Cache the FPRs for efficiency
    fpSet = new OPT_BitSet(this);
    for (Enumeration e = enumerateFPRs(); e.hasMoreElements(); ) {
      OPT_Register r = (OPT_Register)e.nextElement();
      fpSet.add(r);
    }
  }
    
  /**
   * Is a particular register subject to allocation?
   */
  boolean isAllocatable(OPT_Register r) {
    return (r.number < FIRST_SPECIAL && r != getPR() && r != getESP());
  }


  /**
   * @return the processor register
   */
  OPT_Register getPR() {
    return getGPR(PROCESSOR_REGISTER);
  }

  /**
   * @return the frame pointer register
   */
  OPT_Register getFP() {
      return getGPR(FRAME_POINTER);
  }

  /**
   * @return the EAX register
   */
  OPT_Register getEAX() {
      return getGPR(EAX);
  }

  /**
   * @return the ECX register
   */
  OPT_Register getECX() {
      return getGPR(ECX);
  }

  /**
   * @return the EDX register
   */
  OPT_Register getEDX() {
      return getGPR(EDX);
  }

  /**
   * @return the EBX register
   */
  OPT_Register getEBX() {
      return getGPR(EBX);
  }

  /**
   * @return the ESP register
   */
  OPT_Register getESP() {
      return getGPR(ESP);
  }

  /**
   * @return the EBP register
   */
  OPT_Register getEBP() {
      return getGPR(EBP);
  }

  /**
   * @return the ESI register
   */
  OPT_Register getESI() {
      return getGPR(ESI);
  }

  /**
   * @return the EDI register
   */
  OPT_Register getEDI() {
      return getGPR(EDI);
  }


  /**
   * @return a register representing the AF bit of the EFLAGS register.
   */
  OPT_Register getAF() {
    return reg[AF];
  }

  /**
   * @return a register representing the CF bit of the EFLAGS register.
   */
  OPT_Register getCF() {
    return reg[CF];
  }

  /**
   * @return a register representing the OF bit of the EFLAGS register.
   */
  OPT_Register getOF() {
    return reg[OF];
  }

  /**
   * @return a register representing the PF bit of the EFLAGS register.
   */
  OPT_Register getPF() {
    return reg[PF];
  }

  /**
   * @return a register representing the SF bit of the EFLAGS register.
   */
  OPT_Register getSF() {
    return reg[SF];
  }

  /**
   * @return a register representing the ZF bit of the EFLAGS register.
   */
  OPT_Register getZF() {
    return reg[ZF];
  }

  /**
   * @return a register representing the C0 floating-point status bit
   */
  OPT_Register getC0() {
    return reg[C0];
  }

  /**
   * @return a register representing the C1 floating-point status bit
   */
  OPT_Register getC1() {
    return reg[C1];
  }

  /**
   * @return a register representing the C2 floating-point status bit
   */
  OPT_Register getC2() {
    return reg[C2];
  }

  /**
   * @return a register representing the C3 floating-point status bit
   */
  OPT_Register getC3() {
    return reg[C3];
  }

  /**
   * @return the nth physical GPR 
   */
  OPT_Register getGPR(int n) {
    return reg[FIRST_INT+n];
  }

  /**
   * @return the index into the GPR set corresponding to a given register.
   *
   * PRECONDITION: r is a physical GPR
   */
  static int getGPRIndex(OPT_Register r) {
    return r.number - FIRST_INT;
  }

  /**
   * @return the first GPR register used to hold a return value
   */
  OPT_Register getFirstReturnGPR() {
    if (VM.VerifyAssertions) VM.assert(NUM_RETURN_GPRS > 0);
    return getGPR(VOLATILE_GPRS[0]);
  }

  /**
   * @return the second GPR register used to hold a return value
   */
  OPT_Register getSecondReturnGPR() {
    if (VM.VerifyAssertions) VM.assert(NUM_RETURN_GPRS > 1);
    return getGPR(VOLATILE_GPRS[1]);
  }

  /**
   * @return the FPR register used to hold a return value
   */
  OPT_Register getReturnFPR() {
    if (VM.VerifyAssertions) VM.assert(NUM_RETURN_FPRS == 1);
    return getFPR(0);
  }

  /**
   * @return the nth physical FPR 
   */
  OPT_Register getFPR(int n) {
    return reg[FIRST_DOUBLE + n];
  }

  /**
   * @return the index into the GPR set corresponding to a given register.
   *
   * PRECONDITION: r is a physical GPR
   */
  static int getFPRIndex(OPT_Register r) {
    return r.number - FIRST_DOUBLE;
  }

  /**
   * @return the nth physical register in the pool. 
   */
  OPT_Register get(int n) {
    return reg[n];
  }

  /**
   * Given a symbolic register, return a code that gives the physical
   * register type to hold the value of the symbolic register.
   * @param r a symbolic register
   * @return one of INT_REG, DOUBLE_REG 
   */
  static final int getPhysicalRegisterType(OPT_Register r) {
    if (r.isInteger() || r.isLong()) {
      return INT_REG;
    } else if (r.isFloatingPoint()) {
      return DOUBLE_REG;
    } else {
      throw new OPT_OptimizingCompilerException("getPhysicalRegisterType "
                                                + " unexpected " + r);
    }
  }

  /**
   * Register names for each class. used in printing the IR
   */
  private static final String registerName[] = new String[getSize()];
  static {
    String regName[] = registerName;
    for (int i = 0; i < NUM_GPRS; i++)
      regName[i + FIRST_INT] = GPR_NAMES[i];
    for (int i = 0; i < NUM_FPRS; i++)
      regName[i + FIRST_DOUBLE] = FPR_NAMES[i];
//    regName[FRAME_POINTER] = "FP";
    regName[PROCESSOR_REGISTER] = "PR";
    regName[AF] = "AF";
    regName[CF] = "CF";
    regName[OF] = "OF";
    regName[PF] = "PF";
    regName[SF] = "SF";
    regName[ZF] = "ZF";
  }

  /**
   * Get the register name for a register with a particular number in the
   * pool
   */
  static String getName(int number) {
    return registerName[number];
  }
  /**
   * Get the spill size for a register with a particular type
   * @param type one of INT_REG, DOUBLE_REG, SPECIAL_REG
   */
  static int getSpillSize(int type) {
    if (VM.VerifyAssertions) {
      VM.assert( (type == INT_REG) || (type == DOUBLE_REG) ||
                 (type == SPECIAL_REG));
    }
    if (type == DOUBLE_REG) {
      return 8;
    } else {
      return 4;
    }
  }
  /**
   * Get the required spill alignment for a register with a particular type
   * @param type one of INT_REG, DOUBLE_REG,  SPECIAL_REG
   */
  static int getSpillAlignment(int type) {
    if (VM.VerifyAssertions) {
      VM.assert( (type == INT_REG) || (type == DOUBLE_REG) ||
                 (type == SPECIAL_REG));
    }
    if (type == DOUBLE_REG) {
      return 8;
    } else {
      return 4;
    }
  }

  /**
   * Enumerate all the physical registers in this set.
   */
  Enumeration enumerateAll() {
    return new RangeEnumeration(0,getSize()-1);
  }

  /**
   * Enumerate all the GPRs in this set.
   */
  Enumeration enumerateGPRs() {
    return new RangeEnumeration(FIRST_INT,FIRST_DOUBLE-1);
  }

  /**
   * Enumerate all the GPRs in this set.
   */
  Enumeration enumerateFPRs() {
    return new RangeEnumeration(FIRST_DOUBLE,FIRST_SPECIAL-1);
  }

  /**
   * Enumerate all the volatile GPRs in this set.
   */
  Enumeration enumerateVolatileGPRs() {
    OPT_Register r[] = new OPT_Register[ NUM_VOLATILE_GPRS ];
    for(int i = 0; i < NUM_VOLATILE_GPRS; i++)
	r[i] = getGPR(VOLATILE_GPRS[i]);
    return new PhysicalRegisterEnumeration(r);
  }

  /**
   * Enumerate all the nonvolatile GPRs in this set.
   */
  Enumeration enumerateNonvolatileGPRs() {
    OPT_Register r[] = new OPT_Register[ NUM_NONVOLATILE_GPRS ];
    for(int i = 0; i < NUM_NONVOLATILE_GPRS; i++)
	r[i] = getGPR(NONVOLATILE_GPRS[i]);
    return new PhysicalRegisterEnumeration(r);
  }
  /**
   * Enumerate all the volatile FPRs in this set.
   */
  Enumeration enumerateVolatileFPRs() {
    OPT_Register r[] = new OPT_Register[ NUM_VOLATILE_FPRS ];
    for(int i = 0; i < NUM_VOLATILE_FPRS; i++)
	r[i] = getFPR(VOLATILE_FPRS[i]);
    return new PhysicalRegisterEnumeration(r);
  }

  /**
   * Enumerate all the nonvolatile FPRs in this set.
   */
  Enumeration enumerateNonvolatileFPRs() {
    OPT_Register r[] = new OPT_Register[ NUM_NONVOLATILE_FPRS ];
    for(int i = 0; i < NUM_NONVOLATILE_FPRS; i++)
	r[i] = getFPR(NONVOLATILE_FPRS[i]);
    return new PhysicalRegisterEnumeration(r);
  }

  /** 
   * Enumerate the volatile physical registers of a given class.
   * @param regClass one of INT_REG, DOUBLE_REG, SPECIAL_REG
   */
  Enumeration enumerateVolatiles(int regClass) {
    switch (regClass) {
      case INT_REG:
        return enumerateVolatileGPRs();
      case DOUBLE_REG:
        return enumerateVolatileFPRs();
      case SPECIAL_REG:
        return OPT_EmptyEnumerator.EMPTY;
      default:
        throw new OPT_OptimizingCompilerException("Unsupported volatile type");
    }
  }

  /**
   * Enumerate all the volatile physical registers
   */
  Enumeration enumerateVolatiles() {
    Enumeration e1 = enumerateVolatileGPRs();
    Enumeration e2 = enumerateVolatileFPRs();
    return new OPT_CompoundEnumerator(e1, e2);
  }

  /**
   * @return the set of volatile physical registers
   */
  OPT_BitSet getVolatiles() {
    return volatileSet;
  }

  /**
   * @return the set of FPR physical registers
   */
  OPT_BitSet getFPRs() {
    return fpSet;
  }

  /** 
   * Enumerate the nonvolatile physical registers of a given class.
   * @param regClass one of INT_REG, DOUBLE_REG, SPECIAL_REG
   */
  Enumeration enumerateNonvolatiles(int regClass) {
    switch (regClass) {
      case INT_REG:
        return enumerateNonvolatileGPRs();
      case DOUBLE_REG:
        return enumerateNonvolatileFPRs();
      case SPECIAL_REG:
        return OPT_EmptyEnumerator.EMPTY;
      default:
        throw new OPT_OptimizingCompilerException
          ("Unsupported non-volatile type");
    }
  }
  /** 
   * Enumerate the nonvolatile physical registers of a given class,
   * backwards
   * @param regClass one of INT_REG, DOUBLE_REG, SPECIAL_REG
   */
  Enumeration enumerateNonvolatilesBackwards(int regClass) {
    return new OPT_ReverseEnumerator(enumerateNonvolatiles(regClass));
  }
  /**
   * Should this physical register be excluded from liveness analysis?
   *
   * @param r the register to consider skipping
   * @param ir the governing ir
   * @return whether the register should be skipped, i.e., not be
   *          present in the liveness solution
   */
  public boolean excludeFromLiveness(OPT_Register r) {
    return false;
  }


  /**
   * An enumerator for use by the physical register utilities.
   */
  class PhysicalRegisterEnumeration implements Enumeration {
    private int start;
    private int end;
    private int index;
    private OPT_Register r[];
    PhysicalRegisterEnumeration(OPT_Register[] r) {
      this.r = r;
      this.index = 0;
    }
    public Object nextElement() {
      return r[index++];
    }
    public boolean hasMoreElements() {
      return (index < r.length);
    }
  }
  /**
   * An enumerator for use by the physical register utilities.
   */
  class RangeEnumeration implements Enumeration {
    private int start;
    private int end;
    private int index;
    private int exclude = -1; // an index in the register range to exclude
    RangeEnumeration(int start, int end) {
      this.start = start;
      this.end = end;
      this.index = start;
    }
    RangeEnumeration(int start, int end, int exclude) {
      this.start = start;
      this.end = end;
      this.exclude = exclude;
      this.index = start;
    }
    public Object nextElement() {
      if (index == exclude) index++;
      return reg[index++];
    }
    public boolean hasMoreElements() {
      if (index == exclude) index++;
      return (index <= end);
    }
  }
}
