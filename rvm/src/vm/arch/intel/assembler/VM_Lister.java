/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
/** 
 * @author Julian Dolby
 */
class VM_Lister implements VM_Constants {

  private static final int PREFIX_AREA_SIZE = 4;
  private static final int OP_AREA_SIZE     = 9;
  private static final int SOURCE_AREA_SIZE = 16;
  private static final int DEST_AREA_SIZE   = 16;

  VM_Assembler asm;

  VM_Lister (VM_Assembler asm) {
    this.asm = asm;
  }
  
  final void OP (int i, String op) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right("", DEST_AREA_SIZE));
    VM.sysWrite(right("", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
  
  final void I (int i, String op, int n) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(decimal(n) + " ", DEST_AREA_SIZE));
    VM.sysWrite(right("", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
    
  final void R (int i, String op, byte R0) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right("", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
 
  final void RD (int i, String op, byte R0, int d) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(decimal(d) + "[" + GPR_NAMES[R0] + "]", DEST_AREA_SIZE));
    VM.sysWrite(right("", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
  
  final void RI (int i, String op, byte R0, int n) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right(decimal(n) + " ", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
 
  final void RDI (int i, String op, byte R0, int d, int n) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(decimal(d) + "[" + GPR_NAMES[R0] + "]", DEST_AREA_SIZE));
    VM.sysWrite(right(decimal(n) + " ", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RNI (int i, String op, byte R0, int n) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right("[" + GPR_NAMES[R0] + "]", DEST_AREA_SIZE));
    VM.sysWrite(right(decimal(n) + " ", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RR (int i, String op, byte R0, byte R1) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R1]:GPR_NAMES[R1] + " ", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
  
  final void RDR (int i, String op, byte R0, int d, byte R1) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(decimal(d) + "[" + GPR_NAMES[R0] + "]", DEST_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R1]:GPR_NAMES[R1] + " ", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
 
  final void RDRI (int i, String op, byte R0, int d, byte R1, int imm) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(decimal(d) + "[" + GPR_NAMES[R0] + "]", DEST_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R1]:GPR_NAMES[R1] + " ", SOURCE_AREA_SIZE));
    VM.sysWrite(right(decimal(imm), SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
 
  final void RRD (int i, String op, byte R0, byte R1, int d) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right(decimal(d) + "[" + GPR_NAMES[R1] + "]", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
 
  final void RNR (int i, String op, byte R0, byte R1) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right("[" + GPR_NAMES[R0] + "]", DEST_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R1]:GPR_NAMES[R1] + " ", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
 
  final void RN (int i, String op, byte R0) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right("[" + GPR_NAMES[R0] + "]", DEST_AREA_SIZE));
    VM.sysWrite(right(" ", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
 
  final void RRN (int i, String op, byte R0, byte R1) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right("[" + GPR_NAMES[R1] + "]", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
 
  final void RXD (int i, String op, byte R0, byte X, short s, int d) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right("[" + decimal(d) +  "+" + GPR_NAMES[R0] + "+" + GPR_NAMES[X] + "<<" + decimal(s) + "]", DEST_AREA_SIZE));
    VM.sysWrite(right("", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RXDI (int i, String op, byte R0, byte X, short s, int d, int n) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right("[" + decimal(d) +  "+" + GPR_NAMES[R0] + "+" + GPR_NAMES[X] + "<<" + decimal(s) + "]", DEST_AREA_SIZE));
    VM.sysWrite(right(decimal(n), SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RFD (int i, String op, byte X, short s, int d) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right("[" + decimal(d) +  "+" + GPR_NAMES[X] + "<<" + decimal(s) + "]", DEST_AREA_SIZE));
    VM.sysWrite(right("", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RA (int i, String op, int d) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right("[" + hex(d) + "]", DEST_AREA_SIZE));
    VM.sysWrite(right("", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RFDI (int i, String op, byte X, short s, int d, int n) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right("[" + decimal(d) + "+" + GPR_NAMES[X] + "<<" + decimal(s) + "]", DEST_AREA_SIZE));
    VM.sysWrite(right(decimal(n), SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RAI (int i, String op, int d, int n) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right("[" + hex(d) + "]", DEST_AREA_SIZE));
    VM.sysWrite(right(decimal(n), SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RRR (int i, String op, byte R0, byte R1, byte R2) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R1]:GPR_NAMES[R1] + " ", SOURCE_AREA_SIZE) );
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R2]:GPR_NAMES[R2] + " ", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
  
  final void RNRI (int i, String op, byte R0, byte R1, int imm) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right("[" + GPR_NAMES[R0] + "] ", DEST_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R1]:GPR_NAMES[R1] + " ", SOURCE_AREA_SIZE) );
    VM.sysWrite(right(decimal(imm), SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
  
  final void RRI (int i, String op, byte R0, byte R1, int imm) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R1]:GPR_NAMES[R1] + " ", SOURCE_AREA_SIZE) );
    VM.sysWrite(right(decimal(imm), SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
  
  final void RRXD (int i, String op, byte R0, byte R1, byte X, short s, int d) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0], DEST_AREA_SIZE));
    VM.sysWrite(right("[" + decimal(d) +  "+" + GPR_NAMES[R1] + "+" + GPR_NAMES[X] + "<<" + decimal(s) + "]", SOURCE_AREA_SIZE));
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
  
  final void RXDR (int i, String op, byte R0, byte X, short s, int d, byte R1) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right("[" + decimal(d) +  "+" + GPR_NAMES[R1] + "+" + GPR_NAMES[X] + "<<" + decimal(s) + "]", SOURCE_AREA_SIZE));
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RXDRI (int i, String op, byte R0, byte X, short s, int d, byte R1, int imm) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right("[" + decimal(d) +  "+" + GPR_NAMES[R1] + "+" + GPR_NAMES[X] + "<<" + decimal(s) + "]", SOURCE_AREA_SIZE));
    VM.sysWrite(right(decimal(imm), SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RXDRR (int i, String op, byte R0, byte X, short s, int d, byte R1, byte R2) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right("[" + decimal(d) +  "+" + GPR_NAMES[R1] + "+" + GPR_NAMES[X] + "<<" + decimal(s) + "]", SOURCE_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R2]:GPR_NAMES[R2] + " ", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RRFD (int i, String op, byte R0, byte X, short s, int d) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0], DEST_AREA_SIZE));
    VM.sysWrite(right("[" + decimal(d) +  "+" + GPR_NAMES[X] + "<<" + decimal(s) + "]", SOURCE_AREA_SIZE));
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
  
  final void RFDR (int i, String op, byte X, short s, int d, byte R0) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right("[" + decimal(d) +  "+" + GPR_NAMES[X] + "<<" + decimal(s) + "]", SOURCE_AREA_SIZE));
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RFDRI (int i, String op, byte X, short s, int d, byte R0, int imm) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right("[" + decimal(d) + "+" + GPR_NAMES[X] + "<<" + decimal(s) + "]", SOURCE_AREA_SIZE));
    VM.sysWrite(right(decimal(imm), SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RFDRR (int i, String op, byte X, short s, int d, byte R0, byte R2) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right("[" + decimal(d) + "+" + GPR_NAMES[X] + "<<" + decimal(s) + "]", SOURCE_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R2]:GPR_NAMES[R2] + " ", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RRA (int i, String op, byte R0, int d) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0], DEST_AREA_SIZE));
    VM.sysWrite(right("[" + hex(d) + "]", SOURCE_AREA_SIZE));
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }
  
  final void RAR (int i, String op, int d, byte R0) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right("[" + hex(d) + "]", SOURCE_AREA_SIZE));
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RARI (int i, String op, int d, byte R0, int imm) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right("[" + hex(d) + "]", SOURCE_AREA_SIZE));
    VM.sysWrite(right(decimal(imm), SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  final void RARR (int i, String op, int d, byte R0, byte R2) {
    VM.sysWrite(right(hex(i),6) + "| ");
    VM.sysWrite(right("", PREFIX_AREA_SIZE) + " ");
    VM.sysWrite( left(op, OP_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R0]:GPR_NAMES[R0] + " ", DEST_AREA_SIZE));
    VM.sysWrite(right("[" + hex(d) + "]", SOURCE_AREA_SIZE));
    VM.sysWrite(right(isFP(op)?FPR_NAMES[R2]:GPR_NAMES[R2] + " ", SOURCE_AREA_SIZE) + " | ");
    asm.writeLastInstruction(i);
    VM.sysWrite("\n");
  }

  private final static boolean isFP(String op) {
    return op.startsWith("F");
  }

  private final static String left (String s, int w) {
    int n = s.length();
    if (w < n) return s.substring(0,w);
    for (int i=n; i<w; i++) {
      s = s + " ";
    }
    return s; 
  }

  private final static String left (int i, int w) {
    return left(decimal(i), w); 
  }

  private final static String right (String s, int w) {
    int n = s.length();
    if (w < n) return s.substring(n-w);
    for (int i=n; i<w; i++) {
      s = " " + s;
    } 
    return s; 
  }

  private final static String right (int i, int w) {
    return right(decimal(i), w); 
  }

  final static String decimal (int n) {
    if (n==0) return "0";
    String sign = "";
    if (n<0) {
      sign = "-";
      n = -n;
    }
    String result = "";
    while (0<n) {
      int i = n%10;
      n /= 10;
      if (i==0) result = "0" + result;
      else if (i==1) result = "1" + result;
      else if (i==2) result = "2" + result;
      else if (i==3) result = "3" + result;
      else if (i==4) result = "4" + result;
      else if (i==5) result = "5" + result;
      else if (i==6) result = "6" + result;
      else if (i==7) result = "7" + result;
      else if (i==8) result = "8" + result;
      else if (i==9) result = "9" + result;
    }
    return (sign + result);
  }

  private final static String decimal (short s) {
    return decimal((int) s);
  }

  final static String hex (int i) {
    return (hex((short) (i>>16)) + hex((short) i));
  }

  final static String hex (short i) {
    return (hex((byte) (i>>8)) + hex((byte) i));
  }

  final static String hex (byte b) {
    int  i = b & 0xFF;
    byte j = (byte) (i/0x10);
    String s;
         if (j==0x0) s = "0";
    else if (j==0x1) s = "1";
    else if (j==0x2) s = "2";
    else if (j==0x3) s = "3";
    else if (j==0x4) s = "4";
    else if (j==0x5) s = "5";
    else if (j==0x6) s = "6";
    else if (j==0x7) s = "7";
    else if (j==0x8) s = "8";
    else if (j==0x9) s = "9";
    else if (j==0xA) s = "A";
    else if (j==0xB) s = "B";
    else if (j==0xC) s = "C";
    else if (j==0xD) s = "D";
    else if (j==0xE) s = "E";
    else             s = "F";
    j = (byte) (i%0x10);
    String t;
	 if (j==0x0) t = "0";
    else if (j==0x1) t = "1";
    else if (j==0x2) t = "2";
    else if (j==0x3) t = "3";
    else if (j==0x4) t = "4";
    else if (j==0x5) t = "5";
    else if (j==0x6) t = "6";
    else if (j==0x7) t = "7";
    else if (j==0x8) t = "8";
    else if (j==0x9) t = "9";
    else if (j==0xA) t = "A";
    else if (j==0xB) t = "B";
    else if (j==0xC) t = "C";
    else if (j==0xD) t = "D";
    else if (j==0xE) t = "E";
    else             t = "F";
    return s + t;
  }

  final void noteBytecode (int i, String bcode) {
    VM.sysWrite("[" + decimal(i) + "] " + bcode + "\n");
  }

  final void comment (int i, String comment) {
    VM.sysWrite(right(hex(i),6) + "| " + comment + "\n");
  }

  final void comefrom (int i, int j) {
    VM.sysWrite(right(hex(i),6) + "| <<< " + right(hex(j),6) + "\n");
  }

  // Print assembler listing of all methods of a class
  //
  public static void main(String args[]) throws Exception {
    int arg=0;
    String classpath = System.getProperty("java.class.path");
    if (args[0].equals("-classpath")) {
      classpath=args[1];
      arg=2;
    }
    VM.initForTool(classpath);
    // force loading of VM and VM_Magic classes, so calls to their methods
    // will not need "patch" code (dynamic linking)  ...DL
    loadType(VM_Atom.findOrCreateAsciiAtom("LVM;"));
    loadType(VM_Atom.findOrCreateAsciiAtom("LVM_Magic;"));
    loadType(VM_Atom.findOrCreateAsciiAtom("LVM_Allocator;"));
    // each (remaining) arg is a class name
    for (int n=args.length; arg<n; ++arg) {
      String className = args[arg].replace('.','/');
      System.out.println("Translating methods for class " + className);
      System.out.println();
      VM_Class c = (VM_Class)loadType(VM_Atom.findOrCreateAsciiAtom(className)
				      .descriptorFromClassName());
      VM_Method methods[] = c.getDeclaredMethods();
      for (int j = 0, m = methods.length; j < m; ++j) {
	VM_Method method = methods[j];
	if (method.isNative() || method.isAbstract()) {
	  System.out.println("[no code for " + method + "]");
	  continue;
	}
	list(method);
        System.out.println();
      }
    }
  }

  static VM_Type loadType(VM_Atom descriptor) throws VM_ResolutionException {
    VM_Type type = VM_ClassLoader.findOrCreateType(descriptor);
    type.load();
    type.resolve();
    type.instantiate();
    return type;
  }
      
  public static void list (VM_Method m) {

      // removed because flags are final
      //    VM.TraceAssembler         = true;
      //    VM.TraceCompilation       = true;
      //    VM.PrintAssemblerWarnings = true;
    System.out.println("" + m);
    VM_Compiler.compile(m);
  }

}
