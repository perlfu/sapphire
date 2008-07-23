#!/bin/sh
#
#  This file is part of the Jikes RVM project (http://jikesrvm.org).
#
#  This file is licensed to You under the Common Public License (CPL);
#  You may not use this file except in compliance with the License. You
#  may obtain a copy of the License at
#
#      http://www.opensource.org/licenses/cpl1.0.php
#
#  See the COPYRIGHT.txt file distributed with this work for information
#  regarding copyright ownership.
#

FILENAME=$1

cat $2 > $FILENAME

# Function to emit _Reg assembler routines
function emitBinaryReg() {
  acronym=$1
  opStr=$2
  rmrCode=$3
  rrmCode=$4
  sizeOrPrefix=$5
  ext=
  code=
  prefix=
  if [ x$sizeOrPrefix = xbyte ]; then
    ext=_Byte
    code=" (byte) "
  elif [ x$sizeOrPrefix = xword ]; then
    ext=_Word
    code=" (word) "
    prefix="
        setMachineCodes(mi++, (byte) 0x66);"
  elif [ x$sizeOrPrefix != x ]; then
    prefix="
    setMachineCodes(mi++, (byte) $sizeOrPrefix);"
  fi
  cat >> $FILENAME <<EOF
  /**
   * Generate a register--register ${acronym}. That is,
   * <PRE>
   * [dstReg] ${opStr}= ${code} srcReg
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegInd_Reg${ext}(GPR dstReg, GPR srcReg) {
    int miStart = mi; ${prefix}
    setMachineCodes(mi++, (byte) ${rmrCode});
    emitRegIndirectRegOperands(dstReg, srcReg);
    if (lister != null) lister.RNR(miStart, "${acronym}", dstReg, srcReg);
  }

  /**
   * Generate a register-offset--register ${acronym}. That is,
   * <PRE>
   * [dstReg<<dstScale + dstDisp] ${opStr}= ${code} srcReg
   * </PRE>
   *
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,4})
  public final void emit${acronym}_RegOff_Reg${ext}(GPR dstIndex, short dstScale, Offset dstDisp, GPR srcReg) {
    int miStart = mi; ${prefix}
    setMachineCodes(mi++, (byte) ${rmrCode});
    emitRegOffRegOperands(dstIndex, dstScale, dstDisp, srcReg);
    if (lister != null) lister.RFDR(miStart, "${acronym}", dstIndex, dstScale, dstDisp, srcReg);
  }

  // [dstDisp] ${opStr}= $code srcReg
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  public final void emit${acronym}_Abs_Reg${ext}(Address dstDisp, GPR srcReg) {
    int miStart = mi; ${prefix}
    setMachineCodes(mi++, (byte) ${rmrCode});
    emitAbsRegOperands(dstDisp, srcReg);
    if (lister != null) lister.RAR(miStart, "${acronym}", dstDisp, srcReg);
  }

  // [dstBase + dstIndex<<scale + dstDisp] ${opStr}= $code srcReg
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,5})
  public final void emit${acronym}_RegIdx_Reg${ext}(GPR dstBase, GPR dstIndex, short scale, Offset dstDisp, GPR srcReg) {
    int miStart = mi; ${prefix}
    setMachineCodes(mi++, (byte) ${rmrCode});
    emitSIBRegOperands(dstBase, dstIndex, scale, dstDisp, srcReg);
    if (lister != null) lister.RXDR(miStart, "${acronym}", dstBase, dstIndex, scale, dstDisp, srcReg);
  }

  // [dstReg + dstDisp] ${opStr}= $code srcReg
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3})
  public final void emit${acronym}_RegDisp_Reg${ext}(GPR dstReg, Offset disp, GPR srcReg) {
    int miStart = mi; ${prefix}
    setMachineCodes(mi++, (byte) ${rmrCode});
    emitRegDispRegOperands(dstReg, disp, srcReg);
    if (lister != null) lister.RDR(miStart, "${acronym}", dstReg, disp, srcReg);
  }

  // dstReg ${opStr}= $code srcReg
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg${ext}(GPR dstReg, GPR srcReg) {
    int miStart = mi; ${prefix}
    setMachineCodes(mi++, (byte) ${rmrCode});
    emitRegRegOperands(dstReg, srcReg);
    if (lister != null) lister.RR(miStart, "${acronym}", dstReg, srcReg);
  }

EOF
    if [ x$rrmCode != xnone ]; then
    cat >> $FILENAME <<EOF
  // dstReg ${opStr}= $code [srcReg + srcDisp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_Reg_RegDisp${ext}(GPR dstReg, GPR srcReg, Offset disp) {
    int miStart = mi; ${prefix}
    setMachineCodes(mi++, (byte) ${rrmCode});
    emitRegDispRegOperands(srcReg, disp, dstReg);
    if (lister != null) lister.RRD(miStart, "${acronym}", dstReg, srcReg, disp);
  }

  // dstReg ${opStr}= $code [srcIndex<<scale + srcDisp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegOff${ext}(GPR dstReg, GPR srcIndex, short scale, Offset srcDisp) {
    int miStart = mi; ${prefix}
    setMachineCodes(mi++, (byte) ${rrmCode});
    emitRegOffRegOperands(srcIndex, scale, srcDisp, dstReg);
    if (lister != null) lister.RRFD(miStart, "${acronym}", dstReg, srcIndex, scale, srcDisp);
  }

  // dstReg ${opStr}= $code [srcDisp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Abs${ext}(GPR dstReg, Address srcDisp) {
    int miStart = mi; ${prefix}
    setMachineCodes(mi++, (byte) ${rrmCode});
    emitAbsRegOperands(srcDisp, dstReg);
    if (lister != null) lister.RRA(miStart, "${acronym}", dstReg, srcDisp);
  }

  // dstReg ${opStr}= $code [srcBase + srcIndex<<scale + srcDisp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_Reg_RegIdx${ext}(GPR dstReg, GPR srcBase, GPR srcIndex, short scale, Offset srcDisp) {
    int miStart = mi; ${prefix}
    setMachineCodes(mi++, (byte) ${rrmCode});
    emitSIBRegOperands(srcBase, srcIndex, scale, srcDisp, dstReg);
    if (lister != null) lister.RRXD(miStart, "${acronym}", dstReg, srcBase, srcIndex, scale, srcDisp);
  }

  // dstReg ${opStr}= $code [srcReg]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegInd${ext}(GPR dstReg, GPR srcReg) {
    int miStart = mi; ${prefix}
    setMachineCodes(mi++, (byte) ${rrmCode});
    emitRegIndirectRegOperands(srcReg, dstReg);
    if (lister != null) lister.RRN(miStart, "${acronym}", dstReg, srcReg);
  }

EOF
    fi
}

# Function to emit _Imm assembler routines for 16/32 bit immediates
function emitBinaryImmWordOrDouble() {
  acronym=$1
  opStr=$2
  eaxOpcode=$3
  imm8Code=$4
  imm32Code=$5
  immExtOp=$6
  sizeOrPrefix=$7
  local ext=
  local code=
  local prefix=
  if [ x$sizeOrPrefix = xword ]; then
    ext=_Word
    code=" (word) "
    emitImm=emitImm16
    prefix="
            setMachineCodes(mi++, (byte) 0x66);"
  elif [ x$sizeOrPrefix != x ]; then
    prefix="
            setMachineCodes(mi++, (byte) $sizeOrPrefix);"
  else
    emitImm=emitImm32
  fi
  cat >> $FILENAME <<EOF
  // dstReg ${opStr}= ${code} imm
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Imm${ext}(GPR dstReg, int imm) {
    int miStart = mi;$prefix
EOF
  if [ x$imm8Code = xnone ]; then
  cat >> $FILENAME <<EOF
    if (false) {
EOF
  else
  cat >> $FILENAME <<EOF
    if (fits(imm,8)) {
        setMachineCodes(mi++, (byte) ${imm8Code});
        // "register ${immExtOp}" is really part of the opcode
        emitRegRegOperands(dstReg, GPR.getForOpcode(${immExtOp}));
        emitImm8((byte)imm);
EOF
  fi
  if [ x$eaxOpcode != xnone ]; then
  cat >> $FILENAME <<EOF
    } else if (dstReg == EAX) {
        setMachineCodes(mi++, (byte) $eaxOpcode);
        ${emitImm}(imm);
EOF
  fi
  if [ x$imm32Code = xnone ]; then
  cat >> $FILENAME <<EOF
    } else {
        throw new InternalError("Data too large for ${acronym} instruction");
    }
EOF
  else
  cat >> $FILENAME <<EOF
    } else {
        setMachineCodes(mi++, (byte) ${imm32Code});
        // "register ${immExtOp}" is really part of the opcode
        emitRegRegOperands(dstReg, GPR.getForOpcode(${immExtOp}));
        ${emitImm}(imm);
    }
EOF
  fi
  cat >> $FILENAME <<EOF
        if (lister != null) lister.RI(miStart, "${acronym}", dstReg, imm);
    }

  // [dstReg + dstDisp] ${opStr}= ${code} imm
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegDisp_Imm${ext}(GPR dstReg, Offset disp, int imm) {
    int miStart = mi;$prefix
EOF
  if [ x$imm8Code = xnone ]; then
  cat >> $FILENAME <<EOF
    if (false) {
EOF
  else
  cat >> $FILENAME <<EOF
    if (fits(imm,8)) {
        setMachineCodes(mi++, (byte) ${imm8Code});
        // "register ${immExtOp}" is really part of the opcode
        emitRegDispRegOperands(dstReg, disp, GPR.getForOpcode(${immExtOp}));
        emitImm8((byte)imm);
EOF
  fi
  if [ x$imm32Code = xnone ]; then
  cat >> $FILENAME <<EOF
    } else {
        throw new InternalError("Data too large for ${acronym} instruction");
    }
EOF
  else
  cat >> $FILENAME <<EOF
    } else {
        setMachineCodes(mi++, (byte) ${imm32Code});
        // "register ${immExtOp}" is really part of the opcode
        emitRegDispRegOperands(dstReg, disp, GPR.getForOpcode(${immExtOp}));
        ${emitImm}(imm);
    }
EOF
  fi
  cat >> $FILENAME <<EOF
    if (lister != null) lister.RDI(miStart, "${acronym}", dstReg, disp, imm);
  }

  // [dstIndex<<scale + dstDisp] ${opStr}= ${code} imm
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegOff_Imm${ext}(GPR dstIndex, short scale, Offset dstDisp, int imm) {
    int miStart = mi;$prefix
EOF
  if [ x$imm8Code = xnone ]; then
  cat >> $FILENAME <<EOF
    if (false) {
EOF
  else
  cat >> $FILENAME <<EOF
    if (fits(imm,8)) {
        setMachineCodes(mi++, (byte) ${imm8Code});
        // "register ${immExtOp}" is really part of the opcode
        emitRegOffRegOperands(dstIndex, scale, dstDisp, GPR.getForOpcode(${immExtOp}));
        emitImm8((byte)imm);
EOF
  fi
  if [ x$imm32Code = xnone ]; then
  cat >> $FILENAME <<EOF
    } else {
        throw new InternalError("Data too large for ${acronym} instruction");
    }
EOF
  else
  cat >> $FILENAME <<EOF
    } else {
        setMachineCodes(mi++, (byte) ${imm32Code});
        // "register ${immExtOp}" is really part of the opcode
        emitRegOffRegOperands(dstIndex, scale, dstDisp, GPR.getForOpcode(${immExtOp}));
        emitImm32(imm);
    }
EOF
  fi
  cat >> $FILENAME <<EOF
    if (lister != null) lister.RFDI(miStart, "${acronym}", dstIndex, scale, dstDisp, imm);
  }

  // [dstDisp] ${opStr}= ${code} imm
  public final void emit${acronym}_Abs_Imm${ext}(Address dstDisp, int imm) {
    int miStart = mi;$prefix
EOF
  if [ x$imm8Code = xnone ]; then
  cat >> $FILENAME <<EOF
    if (false) {
EOF
  else
  cat >> $FILENAME <<EOF
    if (fits(imm,8)) {
        setMachineCodes(mi++, (byte) ${imm8Code});
        // "register ${immExtOp}" is really part of the opcode
        emitAbsRegOperands(dstDisp, GPR.getForOpcode(${immExtOp}));
        emitImm8((byte)imm);
EOF
  fi
  if [ x$imm32Code = xnone ]; then
  cat >> $FILENAME <<EOF
    } else {
        throw new InternalError("Data too large for ${acronym} instruction");
    }
EOF
  else
  cat >> $FILENAME <<EOF
    } else {
        setMachineCodes(mi++, (byte) ${imm32Code});
        // "register ${immExtOp}" is really part of the opcode
        emitAbsRegOperands(dstDisp, GPR.getForOpcode(${immExtOp}));
        emitImm32(imm);
    }
EOF
  fi
  cat >> $FILENAME <<EOF
    if (lister != null) lister.RAI(miStart, "${acronym}", dstDisp, imm);
  }

  // [dstBase + dstIndex<<scale + dstDisp] ${opStr}= ${code} imm
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegIdx_Imm${ext}(GPR dstBase, GPR dstIndex, short scale, Offset dstDisp, int imm) {
    int miStart = mi;$prefix
EOF
  if [ x$imm8Code = xnone ]; then
  cat >> $FILENAME <<EOF
    if (false) {
EOF
  else
  cat >> $FILENAME <<EOF
    if (fits(imm,8)) {
        setMachineCodes(mi++, (byte) ${imm8Code});
        // "register ${immExtOp}" is really part of the opcode
        emitSIBRegOperands(dstBase, dstIndex, scale, dstDisp, GPR.getForOpcode(${immExtOp}));
        emitImm8((byte)imm);
EOF
  fi
  if [ x$imm32Code = xnone ]; then
  cat >> $FILENAME <<EOF
    } else {
        throw new InternalError("Data too large for ${acronym} instruction");
    }
EOF
  else
  cat >> $FILENAME <<EOF
    } else {
        setMachineCodes(mi++, (byte) ${imm32Code});
        // "register ${immExtOp}" is really part of the opcode
        emitSIBRegOperands(dstBase, dstIndex, scale, dstDisp, GPR.getForOpcode(${immExtOp}));
        emitImm32(imm);
    }
EOF
  fi
  cat >> $FILENAME <<EOF
    if (lister != null) lister.RXDI(miStart, "${acronym}", dstBase, dstIndex, scale, dstDisp, imm);
  }

  // [dstReg] ${opStr}= ${code} imm
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegInd_Imm${ext}(GPR dstReg, int imm) {
    int miStart = mi;$prefix
EOF
  if [ x$imm8Code = xnone ]; then
  cat >> $FILENAME <<EOF
    if (false) {
EOF
  else
  cat >> $FILENAME <<EOF
    if (fits(imm,8)) {
        setMachineCodes(mi++, (byte) ${imm8Code});
        // "register ${immExtOp}" is really part of the opcode
        emitRegIndirectRegOperands(dstReg, GPR.getForOpcode(${immExtOp}));
        emitImm8((byte)imm);
EOF
  fi
  if [ x$imm32Code = xnone ]; then
  cat >> $FILENAME <<EOF
    } else {
        throw new InternalError("Data too large for ${acronym} instruction");
    }
EOF
  else
  cat >> $FILENAME <<EOF
    } else {
        setMachineCodes(mi++, (byte) ${imm32Code});
        // "register ${immExtOp}" is really part of the opcode
        emitRegIndirectRegOperands(dstReg, GPR.getForOpcode(${immExtOp}));
        ${emitImm}(imm);
    }
EOF
  fi
  cat >> $FILENAME <<EOF
    if (lister != null) lister.RNI(miStart, "${acronym}", dstReg, imm);
  }

EOF
}

# Function to emit _Imm_Byte assembler routines
function emitBinaryImmByte() {
  acronym=$1
  opStr=$2
  eaxOpcode=$3
  imm8Code=$4
  imm32Code=$5
  immExtOp=$6
  size=$7
  ext=
  code=
  prefix=
  cat >> $FILENAME <<EOF
  // dstReg ${opStr}= (byte) imm
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Imm_Byte(GPR dstReg, int imm) {
    int miStart = mi;
    if (dstReg == EAX) {
        setMachineCodes(mi++, (byte) $eaxOpcode);
        emitImm8(imm);
    } else {
        setMachineCodes(mi++, (byte) ${imm32Code});
        // "register ${immExtOp}" is really part of the opcode
        emitRegRegOperands(dstReg, GPR.getForOpcode(${immExtOp}));
        emitImm8(imm);
    }
        if (lister != null) lister.RI(miStart, "${acronym}", dstReg, imm);
    }

  // [dstReg + dstDisp] ${opStr}= (byte) imm
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegDisp_Imm_Byte(GPR dstReg, Offset disp, int imm) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${imm32Code});
    // "register ${immExtOp}" is really part of the opcode
    emitRegDispRegOperands(dstReg, disp, GPR.getForOpcode(${immExtOp}));
    emitImm8(imm);
    if (lister != null) lister.RDI(miStart, "${acronym}", dstReg, disp, imm);
  }

  // [dstBase + dstIndex<<scale + dstDisp] ${opStr}= (byte) imm
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegIdx_Imm_Byte(GPR dstBase, GPR dstIndex, short scale, Offset dstDisp, int imm) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${imm32Code});
    // "register ${immExtOp}" is really part of the opcode
    emitSIBRegOperands(dstBase, dstIndex, scale, dstDisp, GPR.getForOpcode(${immExtOp}));
    emitImm8(imm);
    if (lister != null) lister.RXDI(miStart, "${acronym}", dstBase, dstIndex, scale, dstDisp, imm);
  }

  // [dstIndex<<scale + dstDisp] ${opStr}= (byte) imm
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegOff_Imm_Byte(GPR dstIndex, short scale, Offset dstDisp, int imm) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${imm32Code});
    // "register ${immExtOp}" is really part of the opcode
    emitRegOffRegOperands(dstIndex, scale, dstDisp, GPR.getForOpcode(${immExtOp}));
    emitImm8(imm);
    if (lister != null) lister.RFDI(miStart, "${acronym}", dstIndex, scale, dstDisp, imm);
  }

  // [dstDisp] ${opStr}= (byte) imm
  public final void emit${acronym}_Abs_Imm_Byte(Address dstDisp, int imm) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${imm32Code});
    // "register ${immExtOp}" is really part of the opcode
    emitAbsRegOperands(dstDisp, GPR.getForOpcode(${immExtOp}));
    emitImm8(imm);
    if (lister != null) lister.RAI(miStart, "${acronym}", dstDisp, imm);
  }

  // [dstReg] ${opStr}= (byte) imm
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegInd_Imm_Byte(GPR dstReg, int imm) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${imm32Code});
    // "register ${immExtOp}" is really part of the opcode
    emitRegIndirectRegOperands(dstReg, GPR.getForOpcode(${immExtOp}));
    emitImm8(imm);
    if (lister != null) lister.RNI(miStart, "${acronym}", dstReg, imm);
  }

EOF
}

# Emit _Reg, _Reg_Word, _Reg_Byte, _Imm, _Imm_Word and _Imm_Byte suffixes
# $1 = acronym
# $2 = opStr
# $3 = eaxOpcode (_Imm, _Imm_Word)
# $4 = imm8code
# $5 = imm32code
# $6 = immExtOp
# $7 = rmrCode
# $8 = rrmCode
# $9 = eaxOpcode (_Imm_Byte)
# ${10} = imm32code (_Imm_Byte)
# ${11} = rmrCode
# ${12} = rrmCode
function emitBinaryAcc () {
  emitBinaryReg $1 $2 $7 $8
  emitBinaryReg $1 $2 $7 $8 word
  emitBinaryReg $1 $2 ${11} ${12} byte
  emitBinaryImmWordOrDouble $1 $2 $3 $4 $5 $6
  emitBinaryImmWordOrDouble $1 $2 $3 $4 $5 $6 word
  emitBinaryImmByte $1 $2 $9 none ${10} $6
}
#             1   2   3    4    5    6   7    8    9    10   11   12
emitBinaryAcc ADC +CF 0x15 0x83 0x81 0x2 0x11 0x13 0x14 0x80 0x10 0x12
emitBinaryAcc ADD +   0x05 0x83 0x81 0x0 0x01 0x03 0x04 0x80 0x00 0x02
emitBinaryAcc AND \&  0x25 0x83 0x81 0x4 0x21 0x23 0x24 0x80 0x20 0x22
emitBinaryAcc CMP =   0x3D 0x83 0x81 0x7 0x39 0x3B 0x3C 0x80 0x38 0x3A
emitBinaryAcc OR \|   0x0D 0x83 0x81 0x1 0x09 0x0B 0x0C 0x80 0x08 0x0A
emitBinaryAcc SBB -CF 0x1D 0x83 0x81 0x3 0x19 0x1B 0x1C 0x80 0x18 0x1A
emitBinaryAcc SUB -   0x2D 0x83 0x81 0x5 0x29 0x2B 0x2C 0x80 0x28 0x2A
emitBinaryAcc TEST \& 0xA9 none 0xF7 0x0 0x85 none 0xA8 0xF6 0x84 none
emitBinaryAcc XOR \~  0x35 0x83 0x81 0x6 0x31 0x33 0x34 0x80 0x30 0x32

emitBinaryReg MOV \: 0x89 0x8B
emitBinaryReg MOV \: 0x88 0x8A byte
emitBinaryReg MOV \: 0x89 0x8B word

emitBinaryReg CMPXCHG \<\-\> 0xB1 none 0x0F

function emitBT() {
  acronym=$1
  opStr=$2
  rmrCode=$3
  immExtOp=$4
  prefix=0x0F
  immCode=0xBA
  emitBinaryReg $acronym $opStr $rmrCode none $prefix
  emitBinaryImmWordOrDouble $acronym $opStr none $immCode none $immExtOp 0x0F
}

emitBT BT BT 0xA3 0x4
emitBT BTC BTC 0xBB 0x7
emitBT BTR BTR 0xB3 0x6
emitBT BTS BTS 0xAB 0x5

function emitCall() {
  acronym=$1
  rel8Code=$2
  rel32Code=$3
  rmCode=$4
  rmExtCode=$5
  cat >> $FILENAME <<EOF
  // pc = {future address from label | imm}
  public final void emit${acronym}_ImmOrLabel(int imm, int label) {
    if (imm == 0)
        emit${acronym}_Label( label );
    else
        emit${acronym}_Imm( imm );
  }

  /**
   *  Branch to the given target with a ${acronym} instruction
   * <PRE>
   * IP = (instruction @ label)
   * </PRE>
   *
   *  This emit method is expecting only a forward branch (that is
   * what the Label operand means); it creates a ForwardReference
   * to the given label, and puts it into the assembler's list of
   * references to resolve.  This emitter knows the branch is
   * unconditional, so it uses
   * {@link org.jikesrvm.compilers.common.assembler.ForwardReference.UnconditionalBranch}
   * as the forward reference type to create.
   *
   *  All forward branches have a label as the branch target; clients
   * can arbirarily associate labels and instructions, but must be
   * consistent in giving the chosen label as the target of branches
   * to an instruction and calling resolveForwardBranches with the
   * given label immediately before emitting the target instruction.
   * See the header comments of ForwardReference for more details.
   *
   * @param label the label associated with the branch target instrucion
   */
  public final void emit${acronym}_Label(int label) {

      // if alignment checking on, force alignment here
      if (VM.AlignmentChecking) {
        while (((mi + 5) % 4) != 0) {
          emitNOP();
        }
      }

      int miStart = mi;
      ForwardReference r =
        new ForwardReference.UnconditionalBranch(mi, label);
      forwardRefs = ForwardReference.enqueue(forwardRefs, r);
      setMachineCodes(mi++, (byte) ${rel32Code});
      mi += 4; // leave space for displacement
      if (lister != null) lister.I(miStart, "${acronym}", label);
  }

  // pc = imm
  public final void emit${acronym}_Imm(int imm) {
    int miStart = mi;
EOF
  if [ $rel8Code != none ]; then
    cat >> $FILENAME <<EOF
    // can we fit the offset from the next instruction into 8
    // bits, assuming this instruction is 2 bytes (which it will
        // be if the offset fits into 8 bits)?
    int relOffset = imm - (mi + 2);
        if (fits(relOffset,8)) {
        // yes, so use short form.
        setMachineCodes(mi++, (byte) $rel8Code);
        emitImm8((byte) relOffset);
        } else {
        // no, must use 32 bit offset and ignore relOffset to
        // account for the fact that this instruction now has to
        // be 5 bytes long.
EOF
  fi
  cat >> $FILENAME <<EOF
        setMachineCodes(mi++, (byte) $rel32Code);
        // offset of next instruction (this instruction is 5 bytes,
        // but we just accounted for one of them in the mi++ above)
        emitImm32(imm - (mi + 4));
EOF
  if [ $rel8Code != none ]; then
    cat >> $FILENAME <<EOF
    }
EOF
  fi
  cat >> $FILENAME <<EOF
    if (lister != null) lister.I(miStart, "${acronym}", imm);
  }

  // pc = dstReg
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg(GPR dstReg) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) $rmCode);
    // "register $rmExtCode" is really part of the $acronym opcode
    emitRegRegOperands(dstReg, GPR.getForOpcode($rmExtCode));
    if (lister != null) lister.R(miStart, "${acronym}", dstReg);
  }

  // pc = [dstReg + destDisp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegDisp(GPR dstReg, Offset disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) $rmCode);
    // "register $rmExtCode" is really part of the $acronym opcode
    emitRegDispRegOperands(dstReg, disp, GPR.getForOpcode($rmExtCode));
    if (lister != null) lister.RD(miStart, "${acronym}", dstReg, disp);
  }

  // pc = [dstReg]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegInd(GPR dstReg) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) $rmCode);
    // "register $rmExtCode" is really part of the $acronym opcode
    emitRegIndirectRegOperands(dstReg, GPR.getForOpcode($rmExtCode));
    if (lister != null) lister.RN(miStart, "${acronym}", dstReg);
  }

  // pc = [dstIndex<<scale + dstDisp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegOff(GPR dstIndex, short scale, Offset dstDisp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) $rmCode);
    // "register $rmExtCode" is really part of the $acronym opcode
    emitRegOffRegOperands(dstIndex, scale, dstDisp, GPR.getForOpcode($rmExtCode));
    if (lister != null) lister.RFD(miStart, "${acronym}", dstIndex, scale, dstDisp);
  }

  // pc = [dstDisp]
  public final void emit${acronym}_Abs(Address dstDisp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) $rmCode);
    // "register $rmExtCode" is really part of the $acronym opcode
    emitAbsRegOperands(dstDisp, GPR.getForOpcode($rmExtCode));
    if (lister != null) lister.RA(miStart, "${acronym}", dstDisp);
  }

    // pc = [dstBase + dstIndex<<scale + dstDisp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegIdx(GPR dstBase, GPR dstIndex, short scale, Offset dstDisp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) $rmCode);
    // "register $rmExtCode" is really part of the $acronym opcode
    emitSIBRegOperands(dstBase, dstIndex, scale, dstDisp, GPR.getForOpcode($rmExtCode));
    if (lister != null) lister.RXD(miStart, "${acronym}", dstBase, dstIndex, scale, dstDisp);
  }

EOF
}

emitCall CALL none 0xE8 0xFF 0x2
emitCall JMP 0xEB 0xE9 0xFF 0x4


emitUnaryAcc() {
    acronym=$1
    opStr=$2
    rOpCode=$3
    rmOpCode=$4
    rmOpExt=$5
    size=$6
  ext=
  code=
  prefix=
  if [ x$size = xbyte ]; then
    ext=_Byte
    code=" (byte) "
  elif [ x$size = xword ]; then
    ext=_Word
    code=" (word) "
    prefix="
        setMachineCodes(mi++, (byte) 0x66);"
  fi
  if [ $rOpCode != none ]; then
    cat >> $FILENAME <<EOF
  // $opStr ${code} reg
  public void emit${acronym}_Reg${ext}(GPR reg) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) ($rOpCode | reg.value()));
    if (lister != null) lister.R(miStart, "$acronym", reg);
  }
EOF
    else
    cat >> $FILENAME <<EOF
  // $opStr ${code} reg
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg${ext}(GPR reg) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) $rmOpCode);
    emitRegRegOperands(reg, GPR.getForOpcode($rmOpExt));
    if (lister != null) lister.R(miStart, "$acronym", reg);
  }
EOF
    fi
    cat >> $FILENAME <<EOF
  // $opStr ${code} [reg + disp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegDisp${ext}(GPR reg, Offset disp) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) $rmOpCode);
    // "register $rmOpExt" is really part of the opcode
    emitRegDispRegOperands(reg, disp, GPR.getForOpcode($rmOpExt));
    if (lister != null) lister.RD(miStart, "$acronym", reg, disp);
  }

  // $opStr ${code} [reg]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegInd${ext}(GPR reg) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) $rmOpCode);
    // "register $rmOpExt" is really part of the opcode
    emitRegIndirectRegOperands(reg, GPR.getForOpcode($rmOpExt));
    if (lister != null) lister.RN(miStart, "$acronym", reg);
  }

  // $opStr ${code} [index<<scale + disp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegOff${ext}(GPR index, short scale, Offset disp) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) $rmOpCode);
    // "register $rmOpExt" is really part of the opcode
    emitRegOffRegOperands(index, scale, disp, GPR.getForOpcode($rmOpExt));
    if (lister != null) lister.RFD(miStart, "$acronym", index, scale, disp);
  }

  // $opStr ${code} [disp]
  public final void emit${acronym}_Abs${ext}(Address disp) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) $rmOpCode);
    // "register $rmOpExt" is really part of the opcode
    emitAbsRegOperands(disp, GPR.getForOpcode($rmOpExt));
    if (lister != null) lister.RA(miStart, "$acronym", disp);
  }

  // $opStr ${code} [base + index<<scale + disp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegIdx${ext}(GPR base, GPR index, short scale, Offset disp) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) $rmOpCode);
    // "register $rmOpExt" is really part of the opcode
    emitSIBRegOperands(base, index, scale, disp, GPR.getForOpcode($rmOpExt));
    if (lister != null) lister.RXD(miStart, "$acronym", base, index, scale, disp);
  }

EOF
}

emitUnaryAcc DEC -- 0x48 0xFF 0x1
emitUnaryAcc INC ++ 0x40 0xFF 0x0
emitUnaryAcc NEG - none 0xF7 0x3
emitUnaryAcc NOT \~ none 0xF7 0x2

emitUnaryAcc DEC -- 0x48 0xFF 0x1 word
emitUnaryAcc INC ++ 0x40 0xFF 0x0 word
emitUnaryAcc NEG - none 0xF7 0x3 word
emitUnaryAcc NOT \~ none 0xF7 0x2 word

emitUnaryAcc DEC -- none 0xFE 0x1 byte
emitUnaryAcc INC ++ none 0xFE 0x0 byte
emitUnaryAcc NEG - none 0xF6 0x3 byte
emitUnaryAcc NOT \~ none 0xF6 0x2 byte

emitBSWAP() {
    cat >> $FILENAME <<EOF
  // BSWAP reg
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emitBSWAP_Reg(GPR reg) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) (0xC8+reg.value()));
    if (lister != null) lister.R(miStart, "bswap", reg);
  }
EOF
}

emitBSWAP

emitMD() {
    acronym=$1
    opStr=$2
    opExt=$3
cat >> $FILENAME<<EOF
  // EAX:EDX = EAX $opStr srcReg
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg(GPR dstReg, GPR srcReg) {
      int miStart = mi;
      if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
      setMachineCodes(mi++, (byte) 0xF7);
      emitRegRegOperands(srcReg, GPR.getForOpcode(${opExt}));
      if (lister != null) lister.RR(miStart, "$acronym", dstReg, srcReg);
  }

  // EAX:EDX = EAX $opStr [srcReg + disp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegDisp(GPR dstReg, GPR srcReg, Offset disp) {
      int miStart = mi;
      if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
      setMachineCodes(mi++, (byte) 0xF7);
      emitRegDispRegOperands(srcReg, disp, GPR.getForOpcode(${opExt}));
      if (lister != null) lister.RRD(miStart, "$acronym", dstReg, srcReg, disp);
  }

  // EAX:EDX = EAX $opStr [srcReg]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegInd(GPR dstReg, GPR srcReg) {
      int miStart = mi;
      if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
      setMachineCodes(mi++, (byte) 0xF7);
      emitRegIndirectRegOperands(srcReg, GPR.getForOpcode(${opExt}));
      if (lister != null) lister.RRN(miStart, "$acronym", dstReg, srcReg);
  }

  // EAX:EDX = EAX $opStr [baseReg + idxRef<<scale + disp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_Reg_RegIdx(GPR dstReg, GPR baseReg, GPR idxReg, short scale, Offset disp) {
      int miStart = mi;
      if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
      setMachineCodes(mi++, (byte) 0xF7);
      emitSIBRegOperands(baseReg, idxReg, scale, disp, GPR.getForOpcode(${opExt}));
      if (lister != null) lister.RRXD(miStart, "$acronym", dstReg, baseReg, idxReg, scale, disp);
  }

  // EAX:EDX = EAX $opStr [idxRef<<scale + disp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegOff(GPR dstReg, GPR idxReg, short scale, Offset disp) {
      int miStart = mi;
      if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
      setMachineCodes(mi++, (byte) 0xF7);
      emitRegOffRegOperands(idxReg, scale, disp, GPR.getForOpcode(${opExt}));
      if (lister != null) lister.RRFD(miStart, "$acronym", dstReg, idxReg, scale, disp);
  }

  // EAX:EDX = EAX $opStr [disp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Abs(GPR dstReg, Address disp) {
      int miStart = mi;
      if (VM.VerifyAssertions) VM._assert(dstReg == EAX);
      setMachineCodes(mi++, (byte) 0xF7);
      emitAbsRegOperands(disp, GPR.getForOpcode(${opExt}));
      if (lister != null) lister.RRA(miStart, "$acronym", dstReg, disp);
  }

EOF
}

emitMD DIV / 0x6
emitMD IDIV u/ 0x7
emitMD MUL \* 0x4
emitMD IMUL1 \* 0x5


emitMoveSubWord() {
    acronym=$1
    desc=$2
    rm8code=$3
    rm16code=$4
cat >> $FILENAME <<EOF
  // dstReg := (byte) srcReg ($desc)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg_Byte(GPR dstReg, GPR srcReg) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm8code);
    emitRegRegOperands(srcReg, dstReg);
    if (lister != null) lister.RR(miStart, "$acronym", dstReg, srcReg);
  }

  // dstReg := (byte) [srcReg + disp] ($desc)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegDisp_Byte(GPR dstReg, GPR srcReg, Offset disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm8code);
    emitRegDispRegOperands(srcReg, disp, dstReg);
    if (lister != null) lister.RRD(miStart, "$acronym", dstReg, srcReg, disp);
  }

  // dstReg := (byte) [srcReg] ($desc)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegInd_Byte(GPR dstReg, GPR srcReg) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm8code);
    emitRegIndirectRegOperands(srcReg, dstReg);
    if (lister != null) lister.RRN(miStart, "$acronym", dstReg, srcReg);
  }

  // dstReg := (byte) [srcIndex<<scale + disp] ($desc)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegOff_Byte(GPR dstReg, GPR srcIndex, short scale, Offset disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm8code);
    emitRegOffRegOperands(srcIndex, scale, disp, dstReg);
    if (lister != null) lister.RRFD(miStart, "$acronym", dstReg, srcIndex, scale, disp);
  }

  // dstReg := (byte) [disp] ($desc)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Abs_Byte(GPR dstReg, Address disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm8code);
    emitAbsRegOperands(disp, dstReg);
    if (lister != null) lister.RRA(miStart, "$acronym", dstReg, disp);
  }

  // dstReg := (byte) [srcBase + srcIndex<<scale + disp] ($desc)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_Reg_RegIdx_Byte(GPR dstReg, GPR srcBase, GPR srcIndex, short scale, Offset disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm8code);
    emitSIBRegOperands(srcBase, srcIndex, scale, disp, dstReg);
    if (lister != null) lister.RRXD(miStart, "$acronym", dstReg, srcBase, srcIndex, scale, disp);
  }

  // dstReg := (word) srcReg ($desc)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg_Word(GPR dstReg, GPR srcReg) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm16code);
    emitRegRegOperands(srcReg, dstReg);
    if (lister != null) lister.RR(miStart, "$acronym", dstReg, srcReg);
  }

  // dstReg := (word) [srcReg + disp] ($desc)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegDisp_Word(GPR dstReg, GPR srcReg, Offset disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm16code);
    emitRegDispRegOperands(srcReg, disp, dstReg);
    if (lister != null) lister.RRD(miStart, "$acronym", dstReg, srcReg, disp);
  }

  // dstReg := (word) [srcReg] ($desc)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegInd_Word(GPR dstReg, GPR srcReg) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm16code);
    emitRegIndirectRegOperands(srcReg, dstReg);
    if (lister != null) lister.RRN(miStart, "$acronym", dstReg, srcReg);
  }

  // dstReg := (word) [srcIndex<<scale + disp] ($desc)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegOff_Word(GPR dstReg, GPR srcIndex, short scale, Offset disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm16code);
    emitRegOffRegOperands(srcIndex, scale, disp, dstReg);
    if (lister != null) lister.RRFD(miStart, "$acronym", dstReg, srcIndex, scale, disp);
  }

  // dstReg := (word) [disp] ($desc)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Abs_Word(GPR dstReg, Address disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm16code);
    emitAbsRegOperands(disp, dstReg);
    if (lister != null) lister.RRA(miStart, "$acronym", dstReg, disp);
  }

  // dstReg := (word) [srcBase + srcIndex<<scale + disp] ($desc)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_Reg_RegIdx_Word(GPR dstReg, GPR srcBase, GPR srcIndex, short scale, Offset disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) $rm16code);
    emitSIBRegOperands(srcBase, srcIndex, scale, disp, dstReg);
    if (lister != null) lister.RRXD(miStart, "$acronym", dstReg, srcBase, srcIndex, scale, disp);
    }

EOF
}

emitMoveSubWord MOVSX "sign extended" 0xBE 0xBF
emitMoveSubWord MOVZX "zero extended" 0xB6 0xB7

emitShift () {
    acronym=$1
    descr=$2
    onceOp=$3
    regOp=$4
    immOp=$5
    opExt=$6
    size=$7
    ext=
    code=
    prefix=
    if [ x$size = xbyte ]; then
    ext=_Byte
    code=" (byte) "
    elif [ x$size = xword ]; then
    ext=_Word
    code=" (word) "
    prefix="
        setMachineCodes(mi++, (byte) 0x66);"
    fi
cat >> $FILENAME <<EOF
  // $descr of reg by imm
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Imm${ext}(GPR reg, int imm) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(fits(imm,8)); ${prefix}
    if (imm == 1) {
        setMachineCodes(mi++, (byte) ${onceOp});
        emitRegRegOperands(reg, GPR.getForOpcode($opExt));
    } else {
        setMachineCodes(mi++, (byte) ${immOp});
        emitRegRegOperands(reg, GPR.getForOpcode($opExt));
        emitImm8((byte)imm);
    }
    if (lister != null) lister.RI(miStart, "$acronym", reg, imm);
    }

  // $descr of [reg] by imm
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegInd_Imm${ext}(GPR reg, int imm) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(fits(imm,8)); ${prefix}
    if (imm == 1) {
        setMachineCodes(mi++, (byte) ${onceOp});
        emitRegIndirectRegOperands(reg, GPR.getForOpcode($opExt));
    } else {
        setMachineCodes(mi++, (byte) ${immOp});
        emitRegIndirectRegOperands(reg, GPR.getForOpcode($opExt));
        emitImm8((byte)imm);
    }
    if (lister != null) lister.RNI(miStart, "$acronym", reg, imm);
    }

  // $descr of [reg + disp] by imm
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegDisp_Imm${ext}(GPR reg, Offset disp, int imm) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(fits(imm,8)); ${prefix}
    if (imm == 1) {
        setMachineCodes(mi++, (byte) ${onceOp});
        emitRegDispRegOperands(reg, disp, GPR.getForOpcode($opExt));
    } else {
        setMachineCodes(mi++, (byte) ${immOp});
        emitRegDispRegOperands(reg, disp, GPR.getForOpcode($opExt));
        emitImm8((byte)imm);
    }
    if (lister != null) lister.RDI(miStart, "$acronym", reg, disp, imm);
    }

  // $descr of [index<<scale + disp] by imm
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegOff_Imm${ext}(GPR index, short scale, Offset disp, int imm) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(fits(imm,8)); ${prefix}
    if (imm == 1) {
        setMachineCodes(mi++, (byte) ${onceOp});
        emitRegOffRegOperands(index, scale, disp, GPR.getForOpcode($opExt));
    } else {
        setMachineCodes(mi++, (byte) ${immOp});
        emitRegOffRegOperands(index, scale, disp, GPR.getForOpcode($opExt));
        emitImm8((byte)imm);
    }
    if (lister != null) lister.RFDI(miStart, "$acronym", index, scale, disp, imm);
    }

  // $descr of [disp] by imm
  public final void emit${acronym}_Abs_Imm${ext}(Address disp, int imm) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(fits(imm,8)); ${prefix}
    if (imm == 1) {
        setMachineCodes(mi++, (byte) ${onceOp});
        emitAbsRegOperands(disp, GPR.getForOpcode($opExt));
    } else {
        setMachineCodes(mi++, (byte) ${immOp});
        emitAbsRegOperands(disp, GPR.getForOpcode($opExt));
        emitImm8((byte)imm);
    }
    if (lister != null) lister.RAI(miStart, "$acronym", disp, imm);
  }

  // $descr of [base + index<<scale + disp] by imm
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegIdx_Imm${ext}(GPR base, GPR index, short scale, Offset disp, int imm) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(fits(imm,8)); ${prefix}
    if (imm == 1) {
        setMachineCodes(mi++, (byte) ${onceOp});
        emitSIBRegOperands(base, index, scale, disp, GPR.getForOpcode($opExt));
    } else {
        setMachineCodes(mi++, (byte) ${immOp});
        emitSIBRegOperands(base, index, scale, disp, GPR.getForOpcode($opExt));
        emitImm8((byte)imm);
    }
    if (lister != null) lister.RXDI(miStart, "$acronym", base, index, scale, disp, imm);
  }

  // $descr of dataReg by shiftBy
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg${ext}(GPR dataReg, GPR shiftBy) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX); ${prefix}
    setMachineCodes(mi++, (byte) $regOp);
    emitRegRegOperands(dataReg, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RR(miStart, "$acronym", dataReg, shiftBy);
  }

  // $descr of [dataReg] by shiftBy
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegInd_Reg${ext}(GPR dataReg, GPR shiftBy) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX); ${prefix}
    setMachineCodes(mi++, (byte) $regOp);
    emitRegIndirectRegOperands(dataReg, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RNR(miStart, "$acronym", dataReg, shiftBy);
  }

  // $descr of [dataReg + disp] by shiftBy
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3})
  public final void emit${acronym}_RegDisp_Reg${ext}(GPR dataReg, Offset disp, GPR shiftBy) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX); ${prefix}
    setMachineCodes(mi++, (byte) $regOp);
    emitRegDispRegOperands(dataReg, disp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RDR(miStart, "$acronym", dataReg, disp, shiftBy);
  }

  // $descr of [indexReg<<scale + disp] by shiftBy
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,4})
  public final void emit${acronym}_RegOff_Reg${ext}(GPR indexReg, short scale, Offset disp, GPR shiftBy) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX); ${prefix}
    setMachineCodes(mi++, (byte) $regOp);
    emitRegOffRegOperands(indexReg, scale, disp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RFDR(miStart, "$acronym", indexReg, scale, disp, shiftBy);
  }

  // $descr of [disp] by shiftBy
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  public final void emit${acronym}_Abs_Reg${ext}(Address disp, GPR shiftBy) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX); ${prefix}
    setMachineCodes(mi++, (byte) $regOp);
    emitAbsRegOperands(disp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RAR(miStart, "$acronym", disp, shiftBy);
  }

  // $descr of [baseReg + indexReg<<scale + disp] by shiftBy
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,5})
  public final void emit${acronym}_RegIdx_Reg${ext}(GPR baseReg, GPR indexReg, short scale, Offset disp, GPR shiftBy) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX); ${prefix}
    setMachineCodes(mi++, (byte) $regOp);
    emitSIBRegOperands(baseReg, indexReg, scale, disp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RXDR(miStart, "$acronym", baseReg, indexReg, scale, disp, shiftBy);
  }

EOF
}

emitShift SAL "arithmetic shift left" 0xD0 0xD2 0xC0 0x4 byte
emitShift SAL "arithmetic shift left" 0xD1 0xD3 0xC1 0x4 word
emitShift SAL "arithmetic shift left" 0xD1 0xD3 0xC1 0x4

emitShift SAR "arithmetic shift right" 0xD0 0xD2 0xC0 0x7 byte
emitShift SAR "arithmetic shift right" 0xD1 0xD3 0xC1 0x7 word
emitShift SAR "arithmetic shift right" 0xD1 0xD3 0xC1 0x7

emitShift SHL "logical shift left" 0xD0 0xD2 0xC0 0x4 byte
emitShift SHL "logical shift left" 0xD1 0xD3 0xC1 0x4 word
emitShift SHL "logical shift left" 0xD1 0xD3 0xC1 0x4

emitShift SHR "logical shift right" 0xD0 0xD2 0xC0 0x5 byte
emitShift SHR "logical shift right" 0xD1 0xD3 0xC1 0x5 word
emitShift SHR "logical shift right" 0xD1 0xD3 0xC1 0x5

emitShift RCL "rotate left with carry" 0xD0 0xD2 0xC0 0x2 byte
emitShift RCL "rotate left with carry" 0xD1 0xD3 0xC1 0x2 word
emitShift RCL "rotate left with carry " 0xD1 0xD3 0xC1 0x2

emitShift RCR "rotate right with carry" 0xD0 0xD2 0xC0 0x3 byte
emitShift RCR "rotate right with carry" 0xD1 0xD3 0xC1 0x3 word
emitShift RCR "rotate right with carry" 0xD1 0xD3 0xC1 0x3

emitShift ROL "rotate left" 0xD0 0xD2 0xC0 0x0 byte
emitShift ROL "rotate left" 0xD1 0xD3 0xC1 0x0 word
emitShift ROL "rotate left" 0xD1 0xD3 0xC1 0x0

emitShift ROR "rotate right" 0xD0 0xD2 0xC0 0x1 byte
emitShift ROR "rotate right" 0xD1 0xD3 0xC1 0x1 word
emitShift ROR "rotate right" 0xD1 0xD3 0xC1 0x1

emitShiftDouble() {
    acronym=$1
    opStr=$2
    immOp=$3
    regOp=$4
    cat >> $FILENAME <<EOF
  // left ${opStr}= shiftBy (with bits from right shifted in)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg_Imm(GPR left, GPR right, int shiftBy) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${immOp});
    emitRegRegOperands(left, right);
    emitImm8((byte)shiftBy);
    if (lister != null) lister.RRI(miStart, "$acronym", left, right, shiftBy);
  }

  // [left] ${opStr}= shiftBy (with bits from right shifted in)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegInd_Reg_Imm(GPR left, GPR right, int shiftBy) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${immOp});
    emitRegIndirectRegOperands(left, right);
    emitImm8((byte)shiftBy);
    if (lister != null) lister.RNRI(miStart, "$acronym", left, right, shiftBy);
  }

  // [left + disp] ${opStr}= shiftBy (with bits from right shifted in)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3})
  public final void emit${acronym}_RegDisp_Reg_Imm(GPR left, Offset disp, GPR right, int shiftBy) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${immOp});
    emitRegDispRegOperands(left, disp, right);
    emitImm8((byte)shiftBy);
    if (lister != null) lister.RDRI(miStart, "$acronym", left, disp, right, shiftBy);
  }

  // [leftBase + leftIndex<<scale + disp] ${opStr}= shiftBy (with bits from right shifted in)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,5})
  public final void emit${acronym}_RegIdx_Reg_Imm(GPR leftBase, GPR leftIndex, short scale, Offset disp, GPR right, int shiftBy) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${immOp});
    emitSIBRegOperands(leftBase, leftIndex, scale, disp, right);
    emitImm8((byte)shiftBy);
    if (lister != null) lister.RXDRI(miStart, "$acronym", leftBase, leftIndex, scale, disp, right, shiftBy);
  }

  // [leftIndex<<scale + disp] ${opStr}= shiftBy (with bits from right shifted in)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,4})
  public final void emit${acronym}_RegOff_Reg_Imm(GPR leftIndex, short scale, Offset disp, GPR right, int shiftBy) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${immOp});
    emitRegOffRegOperands(leftIndex, scale, disp, right);
    emitImm8((byte)shiftBy);
    if (lister != null) lister.RFDRI(miStart, "$acronym", leftIndex, scale, disp, right, shiftBy);
  }

  // [disp] ${opStr}= shiftBy (with bits from right shifted in)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  public final void emit${acronym}_Abs_Reg_Imm(Address disp, GPR right, int shiftBy) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${immOp});
    emitAbsRegOperands(disp, right);
    emitImm8((byte)shiftBy);
    if (lister != null) lister.RARI(miStart, "$acronym", disp, right, shiftBy);
  }

  // left ${opStr}= shiftBy (with bits from right shifted in)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_Reg_Reg_Reg(GPR left, GPR right, GPR shiftBy) {
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${regOp});
    emitRegRegOperands(left, right);
    if (lister != null) lister.RRR(miStart, "$acronym", left, right, shiftBy);
  }

  // [left] ${opStr}= shiftBy (with bits from right shifted in)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_RegInd_Reg_Reg(GPR left, GPR right, GPR shiftBy) {
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${regOp});
    emitRegIndirectRegOperands(left, right);
    if (lister != null) lister.RNRR(miStart, "$acronym", left, right, shiftBy);
  }

  // [left + disp] ${opStr}= shiftBy (with bits from right shifted in)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3,4})
  public final void emit${acronym}_RegDisp_Reg_Reg(GPR left, Offset disp, GPR right, GPR shiftBy) {
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${regOp});
    emitRegDispRegOperands(left, disp, right);
    if (lister != null) lister.RDRR(miStart, "$acronym", left, disp, right, shiftBy);
  }

  // [leftBase + leftIndex<<scale + disp] ${opStr}= shiftBy (with bits from right shifted in)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,5,6})
  public final void emit${acronym}_RegIdx_Reg_Reg(GPR leftBase, GPR leftIndex, short scale, Offset disp, GPR right, GPR shiftBy) {
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${regOp});
    emitSIBRegOperands(leftBase, leftIndex, scale, disp, right);
    if (lister != null) lister.RXDRR(miStart, "$acronym", leftBase, leftIndex, scale, disp, right, shiftBy);
  }

  // [leftIndex<<scale + disp] ${opStr}= shiftBy (with bits from right shifted in)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,4,5})
  public final void emit${acronym}_RegOff_Reg_Reg(GPR leftIndex, short scale, Offset disp, GPR right, GPR shiftBy) {
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${regOp});
    emitRegOffRegOperands(leftIndex, scale, disp, right);
    if (lister != null) lister.RFDRR(miStart, "$acronym", leftIndex, scale, disp, right, shiftBy);
  }

  // [disp] ${opStr}= shiftBy (with bits from right shifted in)
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2,3})
  public final void emit${acronym}_Abs_Reg_Reg(Address disp, GPR right, GPR shiftBy) {
    if (VM.VerifyAssertions) VM._assert(shiftBy == ECX);
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${regOp});
    emitAbsRegOperands(disp, right);
    if (lister != null) lister.RARR(miStart, "$acronym", disp, right, shiftBy);
  }

EOF
}

emitShiftDouble SHLD \<\< 0xA4 0xA5
emitShiftDouble SHRD \<\< 0xAC 0xAD

emitStackOp() {
    acronym=$1
    op1=$2
    op2=$3
    regCode=$4
    memCode=$5
    memExt=$6
    imm8Code=$7
    imm32Code=$8
    cat >> $FILENAME <<EOF
  // $op1 dstReg, SP $op2 4
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg (GPR dstReg) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ($regCode + dstReg.value()));
    if (lister != null) lister.R(miStart, "${acronym}", dstReg);
  }

  // $op1 [dstReg + dstDisp], SP $op2 4
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegDisp (GPR dstReg, Offset disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${memCode});
    emitRegDispRegOperands(dstReg, disp, GPR.getForOpcode(${memExt}));
    if (lister != null) lister.RD(miStart, "${acronym}", dstReg, disp);
  }

  // $op1 [dstReg], SP $op2 4
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegInd (GPR dstReg) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${memCode});
    emitRegIndirectRegOperands(dstReg, GPR.getForOpcode(${memExt}));
    if (lister != null) lister.RN(miStart, "${acronym}", dstReg);
  }

  // $op1 [dstBase + dstNdx<<scale + dstDisp], SP $op2 4
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegIdx (GPR base, GPR index, short scale, Offset disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${memCode});
    emitSIBRegOperands(base, index, scale, disp, GPR.getForOpcode(${memExt}));
    if (lister != null) lister.RXD(miStart, "${acronym}", base, index, scale, disp);
  }

  // $op1 [dstNdx<<scale + dstDisp], SP $op2 4
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegOff (GPR index, short scale, Offset disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${memCode});
    emitRegOffRegOperands(index, scale, disp, GPR.getForOpcode(${memExt}));
    if (lister != null) lister.RFD(miStart, "${acronym}", index, scale, disp);
  }

  // $op1 [dstDisp], SP $op2 4
  public final void emit${acronym}_Abs (Address disp) {
    int miStart = mi;
    setMachineCodes(mi++, (byte) ${memCode});
    emitAbsRegOperands(disp, GPR.getForOpcode(${memExt}));
    if (lister != null) lister.RA(miStart, "${acronym}", disp);
  }

EOF
    if [ $imm32Code != none ]; then
    cat >> $FILENAME <<EOF
  // $op1 imm, SP $op2 4
  public final void emit${acronym}_Imm(int imm) {
    int miStart = mi;
    if (fits(imm, 8)) {
      setMachineCodes(mi++, (byte) ${imm8Code});
      emitImm8(imm);
    } else {
      setMachineCodes(mi++, (byte) ${imm32Code});
      emitImm32(imm);
    }
    if (lister != null) lister.I(miStart, "${acronym}", imm);
  }

EOF
    fi
}

emitStackOp POP pop -= 0x58 0x8F 0x0 none none
emitStackOp PUSH push += 0x50 0xFF 0x6 0x6A 0x68

# SSE/2 instructions
emitSSE2Op() {
  prefix=$1
  prefix2=$2
  acronym=$3
  opCode=$4
  opCode2=$5
  condByte=$6
  fromRegType=$7
  toRegType=$8
  
  # Pairs of opcodes, both optional. 
  # opCode is for going *into* XMMs and between XMMs
  # opCode2 is for going *out* of XMMs
  # Reg_Reg defaults to opCode, but created for opCode2 if opCode none or missing
  # an example is MOVD_Reg_Reg(EAX, XMM1)
  # TODO: Reg_Reg (see above) is potentially confusing.
  # TODO: Check for bad/missed ops.
  
  if [ x$opCode2 == x ]; then
    opCode2=none;
  fi
  
  if [ x$fromRegType == x ]; then
    fromRegType=XMM
  fi

  if [ x$toRegType == x ]; then
    toRegType=XMM
  fi

  condLine=
  if [[ x$condByte != x ]] && [[ x$condByte != xnone ]]; then
    condLine="
    setMachineCodes(mi++, (byte) ${condByte});"
  fi

  prefix1Line=
  if [[ x$prefix != xnone ]]; then
    prefix1Line="
    setMachineCodes(mi++, (byte) ${prefix});"
  fi

  prefix2Line=
  if [[ x$prefix2 != xnone ]]; then
    prefix2Line="
    setMachineCodes(mi++, (byte) ${prefix2});"
  fi
  
  if [ x$opCode != xnone ]; then 
    cat >> $FILENAME <<EOF
  
  // dstReg ${opStr}= $code srcReg
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg($toRegType dstReg, $fromRegType srcReg) {
    int miStart = mi;$prefix1Line
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode});
    emitRegRegOperands(srcReg, dstReg);$condLine
    if (lister != null) lister.RR(miStart, "${acronym}", dstReg, srcReg);
  }

  // dstReg ${opStr}= $code [srcReg + srcDisp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegDisp($toRegType dstReg, GPR srcReg, Offset disp) {
    int miStart = mi;$prefix1Line
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode});
    emitRegDispRegOperands(srcReg, disp, dstReg);$condLine
    if (lister != null) lister.RRD(miStart, "${acronym}", dstReg, srcReg, disp);
  }

  // dstReg ${opStr}= $code [srcIndex<<scale + srcDisp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegOff($toRegType dstReg, GPR srcIndex, short scale, Offset srcDisp) {
    int miStart = mi;$prefix1Line
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode});
    emitRegOffRegOperands(srcIndex, scale, srcDisp, dstReg);$condLine
    if (lister != null) lister.RRFD(miStart, "${acronym}", dstReg, srcIndex, scale, srcDisp);
  }

  // dstReg ${opStr}= $code [srcDisp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Abs($toRegType dstReg, Address srcDisp) {
    int miStart = mi;$prefix1Line
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode});
    emitAbsRegOperands(srcDisp, dstReg);$condLine
    if (lister != null) lister.RRA(miStart, "${acronym}", dstReg, srcDisp);
  }

  // dstReg ${opStr}= $code [srcBase + srcIndex<<scale + srcDisp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_Reg_RegIdx($toRegType dstReg, GPR srcBase, GPR srcIndex, short scale, Offset srcDisp) {
    int miStart = mi;$prefix1Line
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode});
    emitSIBRegOperands(srcBase, srcIndex, scale, srcDisp, dstReg);$condLine
    if (lister != null) lister.RRXD(miStart, "${acronym}", dstReg, srcBase, srcIndex, scale, srcDisp);
  }

  // dstReg ${opStr}= $code [srcReg]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegInd($toRegType dstReg, GPR srcReg) {
    int miStart = mi;$prefix1Line
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode});
    emitRegIndirectRegOperands(srcReg, dstReg);$condLine
    if (lister != null) lister.RRN(miStart, "${acronym}", dstReg, srcReg);
  }
  
EOF
  fi

  if [[ x$opCode2 != xnone ]] && [[ x$opCode == xnone ]]; then
    cat >> $FILENAME <<EOF
  
  // dstReg ${opStr}= $code srcReg
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg($toRegType dstReg, $fromRegType srcReg) {
    int miStart = mi;$prefix2Line
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode2});
    emitRegRegOperands(dstReg, srcReg);$condLine
    if (lister != null) lister.RR(miStart, "${acronym}", dstReg, srcReg);
  }
EOF
  fi
  
  if [ x$opCode2 != xnone ]; then
    cat >> $FILENAME <<EOF
  
  /**
   * Generate a register--register ${acronym}. That is,
   * <PRE>
   * [dstReg] ${opStr}= ${code} srcReg
   * </PRE>
   *
   * @param dstReg the destination register
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegInd_Reg(GPR dstReg, $fromRegType srcReg) {
    int miStart = mi;$prefix2Line
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode2});
    emitRegIndirectRegOperands(dstReg, srcReg);
    if (lister != null) lister.RNR(miStart, "${acronym}", dstReg, srcReg);
  }

  /**
   * Generate a register-offset--register ${acronym}. That is,
   * <PRE>
   * [dstReg<<dstScale + dstDisp] ${opStr}= ${code} srcReg
   * </PRE>
   *
   * @param dstIndex the destination index register
   * @param dstScale the destination shift amount
   * @param dstDisp the destination displacement
   * @param srcReg the source register
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,4})
  public final void emit${acronym}_RegOff_Reg${ext}(GPR dstIndex, short dstScale, Offset dstDisp, $fromRegType srcReg) {
    int miStart = mi;$prefix2Line
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode2});
    emitRegOffRegOperands(dstIndex, dstScale, dstDisp, srcReg);
    if (lister != null) lister.RFDR(miStart, "${acronym}", dstIndex, dstScale, dstDisp, srcReg);
  }

  // [dstDisp] ${opStr}= $code srcReg
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  public final void emit${acronym}_Abs_Reg${ext}(Address dstDisp, $fromRegType srcReg) {
    int miStart = mi;$prefix2Line
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode2});
    emitAbsRegOperands(dstDisp, srcReg);
    if (lister != null) lister.RAR(miStart, "${acronym}", dstDisp, srcReg);
  }

  // [dstBase + dstIndex<<scale + dstDisp] ${opStr}= $code srcReg
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,5})
  public final void emit${acronym}_RegIdx_Reg${ext}(GPR dstBase, GPR dstIndex, short scale, Offset dstDisp, $fromRegType srcReg) {
    int miStart = mi;$prefix2Line
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode2});
    emitSIBRegOperands(dstBase, dstIndex, scale, dstDisp, srcReg);
    if (lister != null) lister.RXDR(miStart, "${acronym}", dstBase, dstIndex, scale, dstDisp, srcReg);
  }

  // [dstReg + dstDisp] ${opStr}= $code srcReg
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,3})
  public final void emit${acronym}_RegDisp_Reg${ext}(GPR dstReg, Offset disp, $fromRegType srcReg) {
    int miStart = mi;$prefix2Line
    setMachineCodes(mi++, (byte) 0x0F);
    setMachineCodes(mi++, (byte) ${opCode2});
    emitRegDispRegOperands(dstReg, disp, srcReg);
    if (lister != null) lister.RDR(miStart, "${acronym}", dstReg, disp, srcReg);
  }

EOF
  fi
}

# Single precision FP ops.
emitSSE2Op 0xF3 none ADDSS 0x58 none
emitSSE2Op 0xF3 none SUBSS 0x5C none
emitSSE2Op 0xF3 none MULSS 0x59 none
emitSSE2Op 0xF3 none DIVSS 0x5E none
emitSSE2Op 0xF3 0xF3 MOVSS 0x10 0x11
emitSSE2Op none none MOVLPS 0x12 0x13
emitSSE2Op 0xF3 none SQRTSS 0x51 none
emitSSE2Op 0xF3 none CVTSS2SD 0x5A none
emitSSE2Op 0xF3 none CVTSI2SS 0x2A none none GPR XMM
emitSSE2Op 0xF3 none CVTSS2SI 0x2D none none XMM GPR
emitSSE2Op 0xF3 none CVTTSS2SI 0x2C none none XMM GPR

# Single precision FP comparisons.
emitSSE2Op none none UCOMISS 0x2E none
emitSSE2Op 0xF3 none CMPEQSS 0xC2 none 0
emitSSE2Op 0xF3 none CMPLTSS 0xC2 none 1
emitSSE2Op 0xF3 none CMPLESS 0xC2 none 2
emitSSE2Op 0xF3 none CMPUNORDSS 0xC2 none 3
emitSSE2Op 0xF3 none CMPNESS 0xC2 none 4
emitSSE2Op 0xF3 none CMPNLTSS 0xC2 none 5
emitSSE2Op 0xF3 none CMPNLESS 0xC2 none 6
emitSSE2Op 0xF3 none CMPORDSS 0xC2 none 7

# Generic data move ops.
emitSSE2Op none none MOVD none 0x7E none MM GPR
emitSSE2Op none none MOVD 0x6E none none GPR MM
emitSSE2Op none 0x66 MOVD none 0x7E none XMM GPR
emitSSE2Op 0x66 none MOVD 0x6E none none GPR XMM
# NB there is a related MOVQ for x86 64 that handles 64bit GPRs to/from MM/XMM registers
emitSSE2Op none none MOVQ 0x6F 0x7F none MM MM
emitSSE2Op 0xF3 0x66 MOVQ 0x7E 0xD6 none XMM XMM

# Double precision FP ops.
emitSSE2Op 0xF2 none ADDSD 0x58 none
emitSSE2Op 0xF2 none SUBSD 0x5C none
emitSSE2Op 0xF2 none MULSD 0x59 none
emitSSE2Op 0xF2 none DIVSD 0x5E none
emitSSE2Op 0xF2 0xF2 MOVSD 0x10 0x11
emitSSE2Op 0x66 0x66 MOVLPD 0x12 0x13
emitSSE2Op 0xF2 none SQRTSD 0x51 none
emitSSE2Op 0xF2 none CVTSI2SD 0x2A none none GPR XMM
emitSSE2Op 0xF2 none CVTSD2SS 0x5A none
emitSSE2Op 0xF2 none CVTSD2SI 0x2D none none XMM GPR
emitSSE2Op 0xF2 none CVTTSD2SI 0x2C none none XMM GPR

# Double precision comparison ops.
emitSSE2Op 0x66 none UCOMISD 0x2E none
emitSSE2Op 0xF2 none CMPEQSD 0xC2 none 0
emitSSE2Op 0xF2 none CMPLTSD 0xC2 none 1
emitSSE2Op 0xF2 none CMPLESD 0xC2 none 2
emitSSE2Op 0xF2 none CMPUNORDSD 0xC2 none 3
emitSSE2Op 0xF2 none CMPNESD 0xC2 none 4
emitSSE2Op 0xF2 none CMPNLTSD 0xC2 none 5
emitSSE2Op 0xF2 none CMPNLESD 0xC2 none 6
emitSSE2Op 0xF2 none CMPORDSD 0xC2 none 7

# Long ops.
emitSSE2Op none none PSLLQ 0xF3 none none MM MM
emitSSE2Op none none PSRLQ 0xD3 none none MM MM
emitSSE2Op 0x66 none PSLLQ 0xF3 none
emitSSE2Op 0x66 none PSRLQ 0xD3 none

emitFloatMemAcc() {
    local acronym=$1
    local op=$2
    local mOpExt=$3
    local opcode=$4
    local size=$5
    if [ x${size} = xquad ]; then
    ext="_Quad"
    elif [ x${size} = xword ]; then
    ext="_Word"
    else
        ext=""
    fi
    cat >> $FILENAME <<EOF
  // dstReg ${op}= (${size}) [srcReg + disp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegDisp${ext}(FPR dstReg, GPR srcReg, Offset disp) {
    int miStart = mi;
    // Must store result to top of stack
    if (VM.VerifyAssertions) VM._assert(dstReg == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    // The ``register'' ${mOpExt} is really part of the opcode
    emitRegDispRegOperands(srcReg, disp, GPR.getForOpcode(${mOpExt}));
    if (lister != null) lister.RRD(miStart, "${acronym}", dstReg, srcReg, disp);
  }

  // dstReg ${op}= (${size}) [srcReg]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegInd${ext}(FPR dstReg, GPR srcReg) {
    int miStart = mi;
    // Must store result to top of stack
    if (VM.VerifyAssertions) VM._assert(dstReg == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    // The ``register'' ${mOpExt} is really part of the opcode
    emitRegIndirectRegOperands(srcReg, GPR.getForOpcode(${mOpExt}));
    if (lister != null) lister.RRN(miStart, "${acronym}", dstReg, srcReg);
  }

  // dstReg ${op}= (${size}) [srcBase + srcIndex<<scale + disp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2,3})
  public final void emit${acronym}_Reg_RegIdx${ext}(FPR dstReg, GPR srcBase, GPR srcIndex, short scale, Offset disp) {
    int miStart = mi;
    // Must store result to top of stack
    if (VM.VerifyAssertions) VM._assert(dstReg == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    // The ``register'' ${mOpExt} is really part of the opcode
    emitSIBRegOperands(srcBase, srcIndex, scale, disp, GPR.getForOpcode(${mOpExt}));
    if (lister != null) lister.RRXD(miStart, "${acronym}", dstReg, srcBase, srcIndex, scale, disp);
  }

  // dstReg ${op}= (${size}) [srcIndex<<scale + disp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_RegOff${ext}(FPR dstReg, GPR srcIndex, short scale, Offset disp) {
    int miStart = mi;
    // Must store result to top of stack
    if (VM.VerifyAssertions) VM._assert(dstReg == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    // The ``register'' ${mOpExt} is really part of the opcode
    emitRegOffRegOperands(srcIndex, scale, disp, GPR.getForOpcode(${mOpExt}));
    if (lister != null) lister.RRFD(miStart, "${acronym}", dstReg, srcIndex, scale, disp);
  }

  // dstReg ${op}= (${size}) [disp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_Reg_Abs${ext}(FPR dstReg, Address disp) {
    int miStart = mi;
    // Must store result to top of stack
    if (VM.VerifyAssertions) VM._assert(dstReg == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    // The ``register'' ${mOpExt} is really part of the opcode
    emitAbsRegOperands(disp, GPR.getForOpcode(${mOpExt}));
    if (lister != null) lister.RRA(miStart, "${acronym}", dstReg, disp);
  }

EOF
}

emitFloatBinAcc() {
    acronym=$1
    intAcronym=$2
    popAcronym=$3
    op=$4
    mOpExt=$5
    to0Op=$6
    toIop=$7

    emitFloatMemAcc $acronym $op $mOpExt 0xD8
    emitFloatMemAcc $acronym $op $mOpExt 0xDC quad
    emitFloatMemAcc $intAcronym $op $mOpExt 0xDA
    emitFloatMemAcc $intAcronym $op $mOpExt 0xDE word

    cat >> $FILENAME <<EOF
  // dstReg ${op}= srcReg
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg(FPR dstReg, FPR srcReg) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(srcReg == FP0 || dstReg == FP0);
    if (dstReg == FP0) {
    setMachineCodes(mi++, (byte) 0xD8);
    setMachineCodes(mi++, (byte) (${to0Op} | srcReg.value()));
    } else if (srcReg == FP0) {
    setMachineCodes(mi++, (byte) 0xDC);
    setMachineCodes(mi++, (byte) (${toIop} | dstReg.value()));
    }
    if (lister != null) lister.RR(miStart, "${acronym}", dstReg, srcReg);
  }

  // srcReg ${op}= ST(0); pop stack
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${popAcronym}_Reg_Reg(FPR dstReg, FPR srcReg) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(srcReg == FP0);
    setMachineCodes(mi++, (byte) 0xDE);
    setMachineCodes(mi++, (byte) (${toIop} | dstReg.value()));
    if (lister != null) lister.R(miStart, "${popAcronym}", dstReg);
  }

EOF
}

emitFloatBinAcc FADD FIADD FADDP + 0 0xC0 0xC0
emitFloatBinAcc FDIV FIDIV FDIVP / 6 0xF0 0xF8
emitFloatBinAcc FDIVR FIDIVR FDIVRP / 7 0xF8 0xF0
emitFloatBinAcc FMUL FIMUL FMULP x 1 0xC8 0xC8
emitFloatBinAcc FSUB FISUB FSUBP - 4 0xE0 0xE8
emitFloatBinAcc FSUBR FISUBR FSUBRP - 5 0xE8 0xE0

emitFloatMem() {
    acronym=$1
    op=$2
    opcode=$3
    extCode=$4
    size=$5
    if [ x${size} = xquad ]; then
    ext="_Quad"
    elif [ x${size} = xword ]; then
    ext="_Word"
    else
        ext=""
    fi
    if [ ${acronym} = FILD -o ${acronym} = FLD ]; then
    pre="_Reg"
    preArg="FPR dummy, "
    postArg=""
    else
    pre=""
    ext=_Reg${ext}
    preArg=""
    postArg=", FPR dummy"
    fi
    cat >> $FILENAME <<EOF
  // top of stack ${op} (${size:-double word}) [reg + disp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}${pre}_RegDisp${ext}(${preArg}GPR reg, Offset disp${postArg}) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dummy == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegDispRegOperands(reg, disp, GPR.getForOpcode(${extCode}));
    if (lister != null) lister.RD(miStart, "${acronym}", reg, disp);
  }

  // top of stack ${op} (${size:-double word}) [reg]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}${pre}_RegInd${ext}(${preArg}GPR reg${postArg}) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dummy == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegIndirectRegOperands(reg, GPR.getForOpcode(${extCode}));
    if (lister != null) lister.RN(miStart, "${acronym}", reg);
  }

  // top of stack ${op} (${size:-double word}) [baseReg + idxReg<<scale + disp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}${pre}_RegIdx${ext}(${preArg}GPR baseReg, GPR idxReg, short scale, Offset disp${postArg}) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dummy == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    emitSIBRegOperands(baseReg, idxReg, scale, disp, GPR.getForOpcode(${extCode}));
    if (lister != null) lister.RXD(miStart, "${acronym}", baseReg, idxReg, scale, disp);
  }

  // top of stack ${op} (${size:-double word}) [idxReg<<scale + disp]
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}${pre}_RegOff${ext}(${preArg}GPR idxReg, short scale, Offset disp${postArg}) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dummy == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegOffRegOperands(idxReg, scale, disp, GPR.getForOpcode(${extCode}));
    if (lister != null) lister.RFD(miStart, "${acronym}", idxReg, scale, disp);
  }

  // top of stack ${op} (${size:-double word}) [disp]
  public final void emit${acronym}${pre}_Abs${ext}(${preArg}Address disp${postArg}) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(dummy == FP0);
    setMachineCodes(mi++, (byte) ${opcode});
    emitAbsRegOperands(disp, GPR.getForOpcode(${extCode}));
    if (lister != null) lister.RA(miStart, "${acronym}", disp);
  }

EOF
}

emitFloatMem FLD "loaded from" 0xD9 0
emitFloatMem FLD "loaded from" 0xDD 0 quad
emitFloatMem FILD "loaded from" 0xDF 0 word
emitFloatMem FILD "loaded from" 0xDB 0
emitFloatMem FILD "loaded from" 0xDF 5 quad
emitFloatMem FIST "stored to" 0xDF 2 word
emitFloatMem FIST "stored to" 0xDB 2
emitFloatMem FISTP "stored to" 0xDF 3 word
emitFloatMem FISTP "stored to" 0xDB 3
emitFloatMem FISTP "stored to" 0xDF 7 quad
emitFloatMem FST "stored to" 0xD9 2
emitFloatMem FST "stored to" 0xDD 2 quad
emitFloatMem FSTP "stored to" 0xD9 3
emitFloatMem FSTP "stored to" 0xDD 3 quad

emitFloatCmp() {
    acronym=$1
    opcode1=$2
    opcode2=$3
    cat >> $FILENAME <<EOF
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_Reg_Reg (FPR reg1, FPR reg2) {
    int miStart = mi;
    if (VM.VerifyAssertions) VM._assert(reg1 == FP0);
    setMachineCodes(mi++, (byte) ${opcode1});
    setMachineCodes(mi++, (byte)  (${opcode2} | reg2.value()));
    if (lister != null) lister.RR(miStart, "${acronym}", reg1, reg2);
  }


EOF
}

emitFloatCmp FCOMI 0xDB 0xF0
emitFloatCmp FCOMIP 0xDF 0xF0
emitFloatCmp FUCOMI 0xDB 0xE8
emitFloatCmp FUCOMIP 0xDF 0xE8

emitMoveImms() {
    opcode=$1
    size=$2
    if [ x$size = xbyte ]; then
    ext="_Byte"
    prefix=""
    immWrite=emitImm8
    elif [ x$size = xword ]; then
    ext="_Word"
    prefix="
      setMachineCodes(mi++, (byte) 0x66);
"
    immWrite=emitImm16
    else
    ext=""
    prefix=""
    immWrite=emitImm32
    fi
cat >> $FILENAME <<EOF
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emitMOV_RegInd_Imm${ext}(GPR dst, int imm) {
      int miStart = mi;$prefix
      setMachineCodes(mi++, (byte) $opcode);
      emitRegIndirectRegOperands(dst, GPR.getForOpcode(0x0));
      ${immWrite}(imm);
      if (lister != null) lister.RNI(miStart, "MOV", dst, imm);
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emitMOV_RegDisp_Imm${ext}(GPR dst, Offset disp, int imm) {
      int miStart = mi;$prefix
      setMachineCodes(mi++, (byte) $opcode);
      emitRegDispRegOperands(dst, disp, GPR.getForOpcode(0x0));
      ${immWrite}(imm);
      if (lister != null) lister.RDI(miStart, "MOV", dst, disp, imm);
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emitMOV_RegIdx_Imm${ext}(GPR dst, GPR idx, short scale, Offset disp, int imm) {
      int miStart = mi;$prefix
      setMachineCodes(mi++, (byte) $opcode);
      emitSIBRegOperands(dst, idx, scale, disp, GPR.getForOpcode(0x0));
      ${immWrite}(imm);
      if (lister != null) lister.RXDI(miStart, "MOV", dst, idx, scale, disp, imm);
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emitMOV_RegOff_Imm${ext}(GPR idx, short scale, Offset disp, int imm) {
      int miStart = mi;$prefix
      setMachineCodes(mi++, (byte) $opcode);
      emitRegOffRegOperands(idx, scale, disp, GPR.getForOpcode(0x0));
      ${immWrite}(imm);
      if (lister != null) lister.RFDI(miStart, "MOV", idx, scale, disp, imm);
  }

  public final void emitMOV_Abs_Imm${ext}(Address disp, int imm) {
      int miStart = mi;$prefix
      setMachineCodes(mi++, (byte) $opcode);
      emitAbsRegOperands(disp, GPR.getForOpcode(0x0));
      ${immWrite}(imm);
      if (lister != null) lister.RAI(miStart, "MOV", disp, imm);
  }

EOF
}

emitMoveImms 0xC6 byte
emitMoveImms 0xC7 word
emitMoveImms 0xC7

emitFSTATE() {
  acronym=$1
  comment=$2
  opcode=$3
  opExt=$4
  pre=$5
  if [ x$pre != x ]; then
     prefix="
    setMachineCodes(mi++, (byte) ${pre});"
  else
     prefix=""
  fi

cat >> $FILENAME <<EOF
  // ${comment}
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegDisp (GPR dstReg, Offset disp) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegDispRegOperands(dstReg, disp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RD(miStart, "${acronym}", dstReg, disp);
  }

  // ${comment}
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegInd (GPR dstReg) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegIndirectRegOperands(dstReg, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RN(miStart, "${acronym}", dstReg);
  }

  // ${comment}
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1,2})
  public final void emit${acronym}_RegIdx (GPR baseReg, GPR indexReg, short scale, Offset disp) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) ${opcode});
    emitSIBRegOperands(baseReg, indexReg, scale, disp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RXD(miStart, "${acronym}", baseReg, indexReg, scale, disp);
  }

  // ${comment}
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${acronym}_RegOff (GPR indexReg, short scale, Offset disp) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) ${opcode});
    emitRegOffRegOperands(indexReg, scale, disp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RFD(miStart, "${acronym}", indexReg, scale, disp);
  }

  // ${comment}
  public final void emit${acronym}_Abs (Address disp) {
    int miStart = mi;$prefix
    setMachineCodes(mi++, (byte) ${opcode});
    emitAbsRegOperands(disp, GPR.getForOpcode(${opExt}));
    if (lister != null) lister.RA(miStart, "${acronym}", disp);
  }

EOF
}

emitFSTATE FNSAVE "save FPU state ignoring pending exceptions" 0xDD 6
emitFSTATE FSAVE "save FPU state respecting pending exceptions" 0xDD 6 0x9B
emitFSTATE FRSTOR "restore FPU state" 0xDD 4
emitFSTATE FLDCW "load FPU control word" 0xD9 5
emitFSTATE FSTCW "store FPU control word, checking for exceptions" 0xD9 7 0x9B
emitFSTATE FNSTCW "store FPU control word, ignoring exceptions" 0xD9 7


emitFCONST() {
opcode=$1
value=$2
opExt=$3

cat >> $FILENAME <<EOF
  // load ${value} into FP0
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void emit${opcode}_Reg(FPR dstReg) {
    if (VM.VerifyAssertions) VM._assert(dstReg == FP0);
    int miStart = mi;
    setMachineCodes(mi++, (byte) 0xD9);
    setMachineCodes(mi++, (byte) ${opExt});
    if (lister != null) lister.R(miStart, "${opcode}", dstReg);
  }

EOF

}

emitFCONST FLD1 "1.0" 0xE8
emitFCONST FLDL2T "log_2(10)" 0xE9
emitFCONST FLDL2E "log_2(e)" 0xEA
emitFCONST FLDPI "pi" 0xEB
emitFCONST FLDLG2 "log_10(2)" 0xEC
emitFCONST FLDLN2 "log_e(2)" 0xED
emitFCONST FLDZ "0.0" 0xEE

cat >> $FILENAME <<EOF
}
EOF
