
/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */

/*
 * @author Ton Ngo
 */
#ifndef _disasm_hpp
#define _disasm_hpp

#include "ihnpdsm.h"

/*---------------------------------------------------------------------------*/
/* Disassemble one instruction                                               */
/* Wrapper around primary ihnpdsm.cpp functionality.                         */
/*---------------------------------------------------------------------------*/
extern "C" PARLIST *Disassemble(
  char *pHexBuffer,             /* output: hex dump of instruction bytes  */
  size_t HexBuffer_sz,
  char *pMnemonicBuffer,        /* output: instruction mnemonic string    */
  size_t MnemonicBuffer_sz,
  char *pOperandBuffer,         /* output: operands string                */
  size_t OperandBuffer_sz,
  char *pDataBuffer,            /* input:  buffer of bytes to disassemble */
  int  *fInvalid,               /* output: disassembly successful: 1 or 0 */
  PARLIST *disassemblyp    /* output: Where the other data will be returned */
    );

#endif
