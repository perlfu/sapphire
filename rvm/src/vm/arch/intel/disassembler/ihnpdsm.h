/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/*
 * @author Dave Toll
 */
/*****************************************************************************\
***                                                                         ***
***        COPYRIGHT   I B M   CORPORATION  1983, 1984, 1985                ***
***                                   1986, 1987, 1988, 1989                ***
***                                                                         ***
***        LICENSED MATERIAL  -  PROGRAM PROPERTY OF I B M                  ***
***                                                                         ***
***        REFER TO COPYRIGHT INSTRUCTIONS: FORM G120-2083                  ***
***                                                                         ***
\*****************************************************************************/
#ifndef P__DSM_C
#define P__DSM_C

// disable return of instruction data parameters other than
// length and type
#undef RETPARMS
#define RETPARMS 0

typedef char           CHAR;
typedef unsigned char  UCHAR;
typedef short          SHORT;
typedef unsigned short USHORT;
typedef long           LONG;
typedef unsigned long  ULONG;
typedef unsigned       BIT;
typedef unsigned int   UINT;           /* this is not a PM convention but to */
                                       /* eliminate compiler warning, use    */
                                       /* USHORT when port to 32 bits        */

typedef enum   {                       /* type of returned operand info      */
         notype        = 0 ,
         membtype      = 1 ,
         memwtype      = 2 ,
         memwwtype     = 3 ,
         jreltype      = 4 ,
         jnearmemtype  = 5 ,
         jnearregtype  = 6 ,
         jfartype      = 7 ,
         jfarimmtype   = 8 ,
         intntype      = 9 ,
         xlattype      = 10,
         retneartype   = 11,
         retfartype    = 12,
         intrettype    = 13,
         illegtype     = 14,
         LEAtype       = 15,
         escmemtype    = 16,
         escapetype    = 17,
         BOUNDtype     = 18,
         LGDTtype      = 19,
         segovtype     = 20,
         regimmedtype  = 21,
         creltype      = 22,
         cnearmemtype  = 23,
         cnearregtype  = 24,
         cfartype      = 25,
         cfarimmtype   = 26,
         reptype       = 27,             
         strbtype      = 28,             
         strwtype      = 29} RETTYPE;


/* fields have been ordered so packing will not affect the structure */
typedef struct parlist {             /* the parameter area                   */
  UCHAR  *iptr;                      /* machine code ->                      */
  UCHAR  *hbuffer;                   /* hex output buffer ->                 */
  UCHAR  *mbuffer;                   /* mnemonic output buffer ->            */
  UCHAR  *ibuffer;                   /* operand output buffer ->             */
  ULONG  instr_EIP;                  /* EIP value @ this instruction         */
  UINT   flagbits; /*mt*/            /* flag bits :                          */
                                     /* bit 1 (1) => ESC orders are decoded  */
                                     /*              287/387 orders          */
                                     /*       (0) => decoded as "ESC"        */
                                     /*                                      */
                                     /* bit 0 (1) => do 386 32-bit decode    */
                                     /*       (0) => do 16-bit decode        */
         #define  use32mask     1
         #define  N387mask      2
  ULONG  retoffset;                  /* returned displacement/offset         */
  #if RETPARMS
    UINT   retbits;  /*mt*/          /* returned bit flags:                  */
                                     /* bit 0 (1) => operand size is 32 bits */
                                     /*       (0) => otherwise 16 bits       */
                                     /* bit 1 (1) => address size is 32 bits */
                                     /*       (0) => otherwiseis 16 bits     */
    ULONG  retimmed;                 /* immediate value if any               */
    USHORT retescape;                /* ESC instructions opcode              */
    USHORT retseg;                   /* returned segment field               */
  #endif
  UCHAR  retleng;                    /* length of dis-assembled instr        */
  RETTYPE rettype;                   /* type of returned operand info        */
  #if RETPARMS
    UCHAR  retreg;                   /* returned register field              */
    UCHAR  retbase;                  /* returned base register field         */
    UCHAR  retindex;                 /* returned index register field        */
    UCHAR  retscale;                 /* returned scale factor field          */
  #endif
} PARLIST;

void p__DisAsm (PARLIST * parmptr, int print);

#endif
