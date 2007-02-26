/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.jikesrvm.ia32;
/**
 * Constants exported by the assembler
 * @author Julian Dolby
 */
public interface VM_AssemblerConstants {
  String [] CONDITION = {
   "O", "NO", "LLT", "LGE", "EQ", "NE", "LLE", "LGT", "S", "NS", "PE", "PO", "LT", "GE", "LE", "GT" 
  };

  byte   O = 0x0; // OF == 1 - overflow
  byte  NO = 0x1; // OF == 0 - not overflow
  byte LLT = 0x2; // CF == 1 - logically less than (below)
  byte LGE = 0x3; // CF == 0 - logically greater than or equal (not below)
  byte  EQ = 0x4; // ZF == 1 - equal (zero)
  byte  NE = 0x5; // ZF == 0 - not equal (not zero)
  byte LLE = 0x6; // CF == 1 or ZF == 1 - logically less than or equal (not above)
  byte LGT = 0x7; // CF == 0 and ZF == 0 - logically greater than (above)
  byte   S = 0x8; // SF == 1 - (sign) negative??
  byte  NS = 0x9; // SF == 0 - (not sign) positive or zero??
  byte  PE = 0xA; // PF == 1 - even parity or unordered floating point #s
  byte  PO = 0xB; // PF == 0 - odd parity or ordered floating point #s
  byte  LT = 0xC; // SF != OF - less than
  byte  GE = 0xD; // SF == OF - greater than or equal (not less than)
  byte  LE = 0xE; // ZF == 1 or SF != OF - less than or equal (not greater than)
  byte  GT = 0xF; // ZF == 0 and SF == OF - greater than

  // scale factors for SIB bytes
  short BYTE  = 0;
  short SHORT = 1;
  short WORD  = 2;
  short LONG  = 3;

}
