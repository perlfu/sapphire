/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.Enumeration;

/**
 * This class provides utilities to record defs and uses of physical
 * registers by IR operators.
 *
 * @author Stephen Fink
 * @author Dave Grove
 */
class OPT_PhysicalDefUse {

  // constants used to encode defs/uses of physical registers
  final static int mask             = 0x0000;  // empty mask
  final static int maskAF           = 0x0001;
  final static int maskCF           = 0x0002;
  final static int maskOF           = 0x0004;
  final static int maskPF           = 0x0008;
  final static int maskSF           = 0x0010;
  final static int maskZF           = 0x0020;
  final static int maskC0           = 0x0040;
  final static int maskC1           = 0x0080;
  final static int maskC2           = 0x0100;
  final static int maskC3           = 0x0200;
  final static int maskPR           = 0x0400;
  // Meta mask for the enumeration.
  private final static int maskHIGH = 0x0400;
  private final static int maskALL  = 0x07FF;
  
  final static int maskCF_OF = maskCF | maskOF;
  final static int maskCF_PF_ZF = maskCF | maskPF | maskZF;
  final static int maskCF_OF_PF_SF_ZF = maskCF | maskOF | maskPF | maskSF | 
                                        maskZF;
  final static int maskAF_OF_PF_SF_ZF = maskAF | maskOF | maskPF | maskSF | 
                                        maskZF;
  final static int maskAF_CF_OF_PF_SF_ZF = maskAF | maskCF | maskOF |
                                           maskPF | maskSF | maskZF;
  final static int maskC0_C1_C2_C3 = maskC0 | maskC1 | maskC2 | maskC3;
  final static int maskcallDefs = maskAF_CF_OF_PF_SF_ZF;
  final static int maskcallUses = mask;
  final static int maskIEEEMagicUses = mask;
  final static int maskTSPUses = mask;
  final static int maskTSPDefs = maskAF_CF_OF_PF_SF_ZF | maskPR;


  /**
   * @return a string representation of the physical registers encoded by
   * an integer
   */
  static String getString(int code) {
    if (code == mask) return "";
    if (code == maskAF_CF_OF_PF_SF_ZF) return " AF CF OF PF SF ZF";
    // Not a common case, construct it...
    String s = "";
    if ((code & maskAF) != 0) s += " AF";
    if ((code & maskCF) != 0) s += " CF";
    if ((code & maskOF) != 0) s += " OF";
    if ((code & maskPF) != 0) s += " PF";
    if ((code & maskZF) != 0) s += " ZF";
    if ((code & maskC0) != 0) s += " CO";
    if ((code & maskC1) != 0) s += " C1";
    if ((code & maskC2) != 0) s += " C2";
    if ((code & maskC3) != 0) s += " C3";
    if ((code & maskPR) != 0) s += " PR";
    return s;
  }

  /**
   * @param code an integer that encodes a set of physical registers
   * @param ir the governing IR
   * @return an enumeration of the physical registers embodied by a code
   */
  static PDUEnumeration enumerate(int code, OPT_IR ir) {
    return new PDUEnumeration(code,ir);
  }

  /**
   * @param ir the governing IR
   * @return an enumeration of all physical registers that code be 
   *         implicitly defed/used
   */
  static PDUEnumeration enumerateAllImplicitDefUses(OPT_IR ir) {
    return new PDUEnumeration(maskALL,ir);
  }

  /**
   * A class to enumerate physical registers based on a code.
   */
  static final class PDUEnumeration implements Enumeration {
    private int code;
    private int curMask;
    private OPT_PhysicalRegisterSet phys;
    
    PDUEnumeration(int c, OPT_IR ir) {
      phys = ir.regpool.getPhysicalRegisterSet();
      code = c;
      curMask = maskHIGH;
    }

    public boolean hasMoreElements() {
      return code != 0;
    }

    public Object nextElement() {
      while (true) {
	int curBit = code & curMask;
	code -= curBit;
	curMask = curMask >> 1;
	if (curBit != 0) return getReg(curBit, phys);
      }
    }

    // artifically make static to enable scalar replacement of 
    // enumeration object without requiring this method to be inlined.
    private static OPT_Register getReg(int m, OPT_PhysicalRegisterSet phys) {
      switch(m) {
      case maskAF: return phys.getAF();
      case maskCF: return phys.getCF();
      case maskOF: return phys.getOF();
      case maskPF: return phys.getPF();
      case maskSF: return phys.getSF();
      case maskZF: return phys.getZF();
      case maskC0: return phys.getC0();
      case maskC1: return phys.getC1();
      case maskC2: return phys.getC2();
      case maskC3: return phys.getC3();
      case maskPR: return phys.getPR();
      }
      OPT_OptimizingCompilerException.UNREACHABLE();
      return null; // placate jikes.
    }
  }
}
