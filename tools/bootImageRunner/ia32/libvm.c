/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

/**
 * C runtime support for virtual machine.
 *
 * This file deals with loading of the vm boot image into a memory segment and
 * branching to its startoff code. It also deals with interrupt and exception handling.
 * The file "sys.C" contains the o/s support services required by the java class libraries.
 *
 * IA32 version for Linux
 */

#ifndef _GNU_SOURCE
#define _GNU_SOURCE             // so that string.h will include strsignal()
#endif
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <unistd.h>
#include <sys/mman.h>
#include <errno.h>
#include <string.h>
#include <stdarg.h>             // va_list, va_start(), va_end()
#ifndef __USE_GNU         // Deal with current ucontext ugliness.  The current
#define __USE_GNU         // mess of asm/ucontext.h & sys/ucontext.h and their
#include <sys/ucontext.h> // mutual exclusion on more recent versions of gcc
#undef __USE_GNU          // dictates this ugly hack.
#else
#include <sys/ucontext.h>
#endif
#include <assert.h>

#ifdef __APPLE__
#include "osx_ucontext.h"
#endif

#ifdef __linux__
#include "linux_ucontext.h"
#endif

#if defined (__SVR4) && defined (__sun)
typedef unsigned int u_int32_t;
#include "solaris_ucontext.h"
#endif

#define __STDC_FORMAT_MACROS    // include PRIxPTR
#include <inttypes.h>           // PRIxPTR, uintptr_t

#include <pthread.h>

/* Interface to virtual machine data structures. */
#define NEED_EXIT_STATUS_CODES
#define NEED_BOOT_RECORD_DECLARATIONS
#define NEED_VIRTUAL_MACHINE_DECLARATIONS
#define NEED_MEMORY_MANAGER_DECLARATIONS
#include <InterfaceDeclarations.h>

extern "C" void setLinkage(BootRecord*);

#include "../bootImageRunner.h" // In tools/bootImageRunner

// These are definitions of items declared in bootImageRunner.h
/* Sink for messages relating to serious errors detected by C runtime. */
FILE *SysErrorFile = stderr;
static int SysErrorFd = 2;              // not used outside this file.

/* Sink for trace messages produced by VM.sysWrite(). */
FILE *SysTraceFile = stderr;
int SysTraceFd = 2;

/* Command line arguments to be passed to virtual machine. */
const char **JavaArgs;
int JavaArgc;

/* global; startup configuration option with default values */
const char *bootCodeFilename = 0;

/* global; startup configuration option with default values */
const char *bootDataFilename = 0;

/* global; startup configuration option with default values */
const char *bootRMapFilename = 0;

/* Emit trace information? */
int lib_verbose = 0;

/* Location of jtoc within virtual machine image. */
static unsigned VmToc;

/* TOC offset of Scheduler.dumpStackAndDie */
static Offset DumpStackAndDieOffset;

/* TOC offset of Scheduler.processors[] */
static Offset ProcessorsOffset;

/* TOC offset of Scheduler.debugRequested */
static Offset DebugRequestedOffset;

/* name of program that will load and run RVM */
char *Me;

static BootRecord *bootRecord;

static void vwriteFmt(int fd, const char fmt[], va_list ap)
    NONNULL(2) __attribute__((format (printf, 2, 0)));
static void vwriteFmt(int fd, size_t bufsz, const char fmt[], va_list ap)
    NONNULL(3) __attribute__((format (printf, 3, 0)));
#if 0                           // this isn't needed right now, but may be in
                                // the future.
static void writeTrace(const char fmt[], ...)
    NONNULL(1) __attribute__((format (printf, 1, 2)));
#endif
static void writeErr(const char fmt[], ...)
    NONNULL(1) __attribute__((format (printf, 1, 2)));


static int
pageRoundUp(int size)
{
    int pageSize = 4096;
    return (size + pageSize - 1) / pageSize * pageSize;
}

/*
 * Bootimage is loaded and ready for execution:
 * you can set a breakpoint here with gdb.
 */
int
boot (int ip, int pr, int sp)
{
    return bootThread (ip, pr, sp);
}

#include <disasm.h>

unsigned int
getInstructionFollowing(unsigned int faultingInstructionAddress)
{
    int Illegal = 0;
    char HexBuffer[256], MnemonicBuffer[256], OperandBuffer[256];
    //, AddrBuffer[256];
    PARLIST p_st;
    PARLIST *p;

    p = Disassemble(HexBuffer,
                    sizeof HexBuffer,
                    MnemonicBuffer,
                    sizeof MnemonicBuffer,
                    OperandBuffer,
                    sizeof OperandBuffer,
                    (char *) faultingInstructionAddress,
                    &Illegal, &p_st);
    if (Illegal)
        return faultingInstructionAddress;
    else
    {
        if ( lib_verbose)
            fprintf(SysTraceFile, "failing instruction : %s %s\n",
                    MnemonicBuffer, OperandBuffer);
        return faultingInstructionAddress + p->retleng;
    }
}

// alignment checking: helps with making decision when an alignment trap occurs
#ifdef RVM_WITH_ALIGNMENT_CHECKING
void
getInstOpcode(unsigned int faultingInstructionAddress, char MnemonicBuffer[256])
{
    int Illegal = 0;
    char HexBuffer[256], OperandBuffer[256];
    PARLIST p_st;
    PARLIST *p;

    p = Disassemble(HexBuffer,
                    sizeof HexBuffer,
                    MnemonicBuffer,
                    sizeof MnemonicBuffer,
                    OperandBuffer,
                    sizeof OperandBuffer,
                    (char *) faultingInstructionAddress,
                    &Illegal, &p_st);
    if (Illegal) {
      strcpy(MnemonicBuffer, "illegal");
    }
}
#endif // RVM_WITH_ALIGNMENT_CHECKING

static int
inRVMAddressSpace(Address addr)
{
    /* get the boot record */
    Address *heapRanges = bootRecord->heapRanges;
    for (int which = 0; which < MAXHEAPS; which++) {
        Address start = heapRanges[2 * which];
        Address end = heapRanges[2 * which + 1];
        // Test against sentinel.
        if (start == ~(Address) 0 && end == ~ (Address) 0) break;
        if (start <= addr  && addr < end) {
            return true;
        }
    }
    return false;
}

static int
isVmSignal(unsigned int ip, unsigned int vpAddress)
{
    return inRVMAddressSpace(ip) && inRVMAddressSpace(vpAddress);
}

#if 0                           // this isn't needed right now, but may be in
                                // the future.
static void
writeTrace(const char fmt[], ...)
{
    va_list ap;
    va_start(ap, fmt);
    vwriteFmt(SysTraceFd, fmt, ap);
    va_end(ap);
}
#endif


static void
writeErr(const char fmt[], ...)
{
    va_list ap;
    va_start(ap, fmt);
    vwriteFmt(SysErrorFd, fmt, ap);
    va_end(ap);
}

static void
vwriteFmt(int fd, const char fmt[], va_list ap)
{
    vwriteFmt(fd, 100, fmt, ap);
}


/* Does the real work. */
static void
vwriteFmt(int fd, size_t bufsz, const char fmt[], va_list ap)
{
    char buf[bufsz];
    int nchars
        = vsnprintf(buf, bufsz, fmt, ap);
    size_t count;
    if (nchars < 0) {
        const char badmsg[] = "vsnprintf() returned a negative number;  impossible (or very old C library version)\n";
        write(fd, badmsg, sizeof badmsg - 1);
        nchars = sizeof buf - 1;
    }
    if ((unsigned) nchars >= bufsz) {
        vwriteFmt(fd, bufsz + 100, fmt, ap);
        return;
    }
    count = nchars;
    char *bp = buf;
    ssize_t nwrote = 0;
    bool num_rerun = 0;

    while (nchars > 0) {
        nwrote = write(fd, buf, nchars);
        if (nwrote < 0) {
            /* XXX We should handle this in some intelligent fashion.   But
             * how?  Do we have any means of complaining left? */
            if (errno == EINTR)
                continue;
            // Any other way to handle the rest?
            break;
        }
        if (nwrote == 0) {
            if (++num_rerun > 20) {
                break;          // too many reruns.  Any other error indicator?
            }
            continue;
        }
        num_rerun = 0;          // we made progress.
        bp += nwrote;
        nchars -= nwrote;
    }
}

// variables and helper methods for alignment checking

#ifdef RVM_WITH_ALIGNMENT_CHECKING

// these vars help implement the two-phase trap handler approach for alignment checking
static unsigned int alignCheckHandlerJumpLocation = 0; // 
static unsigned char alignCheckHandlerInstBuf[100]; // ought to be enough to hold two instructions :P

// if enabled, print a character for each alignment trap (whether or not we ignore it)
static int alignCheckVerbose = 0;

// statistics defined in sys.C
extern volatile int numNativeAlignTraps;
extern volatile int numEightByteAlignTraps;
extern volatile int numBadAlignTraps;

// called by the hardware trap handler if we're dealing with an alignment error;
// returns true iff the trap handler should return immediately
int handleAlignmentTrap(int signo, void* context) {
    if (signo == SIGBUS) {
      // first turn off alignment checks for the handler so we can don't cause another one here
      __asm__ __volatile__("pushf\n\t"
                           "andl $0xfffbffff,(%esp)\n\t"
                           "popf");

      // get the structures we need
      ucontext_t *uc = (ucontext_t *) context;  // user context
      mcontext_t *mc = &(uc->uc_mcontext);      // machine context
      greg_t  *gregs = mc->gregs;               // general purpose registers
      // get the faulting IP
      unsigned int localInstructionAddress     = gregs[REG_EIP];
      
      // decide what kind of alignment error this is and whether to ignore it;
      // if we ignore it, then the normal handler will take care of it
      int ignore = 0;
      if (!inRVMAddressSpace(localInstructionAddress)) {
        // the IP is outside the VM, so ignore it
        if (alignCheckVerbose) {
          fprintf(SysTraceFile, "N"); // native code, apparently
        }
        ignore = 1;
        numNativeAlignTraps++;
      } else {
        char buffer[256];
        getInstOpcode(localInstructionAddress, buffer);
        if (strncmp(buffer, "fld",  3) == 0 ||
            strncmp(buffer, "fst",  3) == 0 ||
            strncmp(buffer, "fild", 4) == 0 ||
            strncmp(buffer, "fist", 4) == 0 ||
            strncmp(buffer, "fstp", 4) == 0 ||
            strncmp(buffer, "fmul", 4) == 0 ||
            strncmp(buffer, "fdiv", 4) == 0 ||
            strncmp(buffer, "fadd", 4) == 0 ||
            strncmp(buffer, "fsub", 4) == 0) {
          // an 8-byte access -- ignore it
          if (alignCheckVerbose) {
            fprintf(SysTraceFile, "8"); // definitely an 8-byte acccess
          }
          ignore = 1;
          numEightByteAlignTraps++;
        } else {
          // any other unaligned access -- these are probably bad
          if (alignCheckVerbose) {
            fprintf(SysTraceFile, "*"); // other
          }
          fprintf(SysTraceFile, "\nFaulting opcode: %s\n", buffer);
          fflush(SysTraceFile);
          ignore = 0;
          numBadAlignTraps++;
        }
      }
      
      if (ignore) {
        // we can ignore the exception by returning to a code block
        // that we create that consists of
        // (1) a copy of the faulting instruction
        // (2) a trap
        // we turn off alignment exceptions so we execute the faulting instruction and then trap;
        // the trap will end up in the "if alignCheckHandlerJumpLocation)" section below
        alignCheckHandlerJumpLocation = getInstructionFollowing(localInstructionAddress);
        int length = alignCheckHandlerJumpLocation - localInstructionAddress;
        if (!length) {
          fprintf(SysTraceFile, "\nUnexpected zero length\n");
          exit(1);
        }
        for (int i = 0; i < length; i++) {
          alignCheckHandlerInstBuf[i] = ((unsigned char*)localInstructionAddress)[i];
        }
        alignCheckHandlerInstBuf[length] = 0xCD; // INT opcode
        alignCheckHandlerInstBuf[length + 1] = 0x43; // not sure which interrupt this is, but it works
        // save the next instruction
        gregs[REG_EFL] &= 0xfffbffff;
        gregs[REG_EIP] = (unsigned int)(void*)alignCheckHandlerInstBuf;
        return 1;
      }
    }
    
    // alignment checking: handle the second phase of align traps that the code above decided to ignore
    if (alignCheckHandlerJumpLocation) {
      // get needed structures
      ucontext_t *uc = (ucontext_t *) context;  // user context
      mcontext_t *mc = &(uc->uc_mcontext);      // machine context
      greg_t  *gregs = mc->gregs;               // general purpose registers
      // turn alignment exceptions back on
      gregs[REG_EFL] |= 0x00040000;
      // jump to the instruction following the original faulting instruction
      gregs[REG_EIP] = alignCheckHandlerJumpLocation;
      // clear the location so we don't end up here on the next trap
      alignCheckHandlerJumpLocation = 0;
      return 1;
    }
    
    return 0;
}

#endif // RVM_WITH_ALIGNMENT_CHECKING

void
hardwareTrapHandler(int signo, siginfo_t *si, void *context)
{
    // alignment checking: handle hardware alignment exceptions
    #ifdef RVM_WITH_ALIGNMENT_CHECKING
    if (signo == SIGBUS || alignCheckHandlerJumpLocation) {
      int returnNow = handleAlignmentTrap(signo, context);
      if (returnNow) {
        return;
      }
    }
    #endif // RVM_WITH_ALIGNMENT_CHECKING
	
    unsigned int localInstructionAddress;
    static pthread_mutex_t exceptionLock = PTHREAD_MUTEX_INITIALIZER;

    /*
     * Get the exceptionLock. We can not call pthread_mutex_lock,
     * it might block and we could catch another signal
     */
    for(;;) {
      int lockrc = pthread_mutex_trylock( &exceptionLock );
      if (lockrc == 0) break;        // We have the lock
      if (lockrc == EBUSY) continue; // Busy, we *must* spin

      writeErr("invalid result from pthread_mutex_trylock %d\n", lockrc);
      signal(signo, SIG_DFL);
      raise(signo);
      // We should never get here.
      _exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
    }

    unsigned int localVirtualProcessorAddress;
    unsigned int localFrameAddress;
    unsigned int localJTOC = VmToc;

    int isRecoverable;

    unsigned int instructionFollowing;

    /*
     * fill local working variables from context saved by OS trap handler
     * mechanism
     */
    localInstructionAddress     = IA32_EIP(context);
    localVirtualProcessorAddress = IA32_ESI(context);

    // We are prepared to handle these kinds of "recoverable" traps:
    //
    //  1. SIGSEGV - a null object dereference of the form "obj[-fieldOffset]"
    //               that wraps around to segment 0xf0000000.
    //
    //  2. SIGFPE  - interger divide by zero trap
    //
    //  3. SIGTRAP - stack overflow trap
    //
    // Anything else indicates some sort of unrecoverable vm error.
    //
    isRecoverable = 0;

    if (isVmSignal(localInstructionAddress, localVirtualProcessorAddress))
    {
        if (signo == SIGSEGV /*&& check the adddress TODO */)
            isRecoverable = 1;

        else if (signo == SIGFPE)
            isRecoverable = 1;

        else if (signo == SIGTRAP)
            isRecoverable = 0;
            
        // alignment checking: hardware alignment exceptions are recoverable (i.e., we want to jump to the Java handler)
        #ifdef RVM_WITH_ALIGNMENT_CHECKING
        else if (signo == SIGBUS)
            isRecoverable = 1;
        #endif // RVM_WITH_ALIGNMENT_CHECKING
            
        else
            writeErr("%s: WHOOPS.  Got a signal (%s; #%d) that the hardware signal handler wasn't prepared for.\n", Me,  strsignal(signo), signo);
    } else {
        writeErr("%s: TROUBLE.  Got a signal (%s; #%d) from outside the VM's address space.\n", Me,  strsignal(signo), signo);
    }




    if (lib_verbose || !isRecoverable)
    {
        writeErr("%s:%s trapped signal %d (%s)\n", Me,
                 isRecoverable? "" : " UNRECOVERABLE",
                 signo, strsignal(signo));

        writeErr("handler stack 0x%08x\n",
                 (unsigned) &localInstructionAddress);
        if (signo == SIGSEGV)
            writeErr("si->si_addr   0x%08x\n", (unsigned) si->si_addr);
        writeErr("gs            0x%08x\n", IA32_GS(context));
        writeErr("fs            0x%08x\n", IA32_FS(context));
        writeErr("es            0x%08x\n", IA32_ES(context));
        writeErr("ds            0x%08x\n", IA32_DS(context));
        writeErr("edi           0x%08x\n", IA32_EDI(context));
        writeErr("esi -- PR/VP  0x%08x\n", IA32_ESI(context));
        writeErr("ebp           0x%08x\n", IA32_EBP(context));
        writeErr("esp -- SP     0x%08x\n", IA32_ESP(context));
        writeErr("ebx           0x%08x\n", IA32_EBX(context));
        writeErr("edx           0x%08x\n", IA32_EDX(context));
        writeErr("ecx           0x%08x\n", IA32_ECX(context));
        writeErr("eax           0x%08x\n", IA32_EAX(context));
        writeErr("ss            0x%08x\n", IA32_SS(context));
        writeErr("eip           0x%08x\n", IA32_EIP(context));
        writeErr("cs            0x%08x\n", IA32_CS(context));
        writeErr("trapno        0x%08x\n", IA32_TRAPNO(context));
        writeErr("err           0x%08x\n", IA32_ERR(context));
        writeErr("eflags        0x%08x\n", IA32_EFLAGS(context));
        // writeErr("esp_at_signal 0x%08x\n", IA32_UESP(context));
/* null if fp registers haven't been used yet */
        writeErr("fpstate       0x%08x\n",
                                        (unsigned) IA32_FPSTATE(context));
        writeErr("oldmask       0x%08lx\n",
                                        (unsigned long) IA32_OLDMASK(context));
        writeErr("cr2           0x%08lx\n",
                                        /* seems to contain mem address that
                                         * faulting instruction was trying to
                                         * access */
                                        (unsigned long) IA32_FPFAULTDATA(context));

        /*
         * There are 8 floating point registers, each 10 bytes wide.
         * See /usr/include/asm/sigcontext.h
         */

//Solaris doesn't seem to support these
#if !(defined (__SVR4) && defined (__sun)) 
	if (IA32_FPREGS(context)) {
		for (int reg=0; reg<8; reg++) {
			writeErr("fp%d 0x%04x%04x%04x%04x%04x\n",
					reg,
					IA32_STMM(context, reg, 0) & 0xffff,
					IA32_STMM(context, reg, 1) & 0xffff,
					IA32_STMM(context, reg, 2) & 0xffff,
					IA32_STMM(context, reg, 3) & 0xffff,
					IA32_STMMEXP(context, reg) & 0xffff);
		}
}
#endif
        if (isRecoverable) {
            fprintf(SysTraceFile, "%s: normal trap\n", Me);
        } else {
            fprintf(SysTraceFile, "%s: internal error\n", Me);
        }
    }

    /* test validity of virtual processor address */
    {
        unsigned int vp_hn;  /* the high nibble of the vp address value */
        vp_hn = localVirtualProcessorAddress >> 28;
        if (vp_hn < 3 || !inRVMAddressSpace(localVirtualProcessorAddress))
        {
            writeErr("invalid vp address (not an address - high nibble %d)\n",
                     vp_hn);
            signal(signo, SIG_DFL);
            raise(signo);
            // We should never get here.
            _exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
        }
    }


    /* get the frame pointer from processor object  */
    localFrameAddress =
        *(unsigned *) (localVirtualProcessorAddress + Processor_framePointer_offset);

    /* test validity of frame address */
    {
        unsigned int fp_hn;
        fp_hn = localFrameAddress >> 28;
        if (fp_hn < 3 || !inRVMAddressSpace(localFrameAddress))
        {
            writeErr("invalid frame address %x"
            " (not an address - high nibble %d)\n",
                                 localFrameAddress, fp_hn);
            signal(signo, SIG_DFL);
            raise(signo);
            // We should never get here.
            _exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
        }
    }


    int HardwareTrapMethodId = bootRecord->hardwareTrapMethodId;
    unsigned int javaExceptionHandlerAddress =
        *(unsigned int *) (localJTOC + bootRecord->deliverHardwareExceptionOffset);

    DumpStackAndDieOffset = bootRecord->dumpStackAndDieOffset;

    /* get the active thread id */
    unsigned int threadObjectAddress =
        *(unsigned int*) (localVirtualProcessorAddress + Processor_activeThread_offset);

    /* then get its hardware exception registers */
    unsigned int registers =
        *(unsigned int *) (threadObjectAddress +
                           RVMThread_exceptionRegisters_offset);

    /* get the addresses of the gps and other fields in the Registers object */
    unsigned *vmr_gprs  = *(unsigned **) ((char *) registers + Registers_gprs_offset);
    unsigned *vmr_ip    =  (unsigned *)  ((char *) registers + Registers_ip_offset);
    unsigned *vmr_fp    =  (unsigned *)  ((char *) registers + Registers_fp_offset);
    unsigned *vmr_inuse =  (unsigned *)  ((char *) registers + Registers_inuse_offset);

    long unsigned int *sp;
    long unsigned int *fp;

    /* Test for recursive errors -- if so, take one final stacktrace and exit
     */
    if (*vmr_inuse || !isRecoverable) {
        if (*vmr_inuse)
            fprintf(SysTraceFile,
                     "%s: internal error: recursive use of"
                    " hardware exception registers (exiting)\n", Me);
        /*
         * Things went badly wrong, so attempt to generate a useful error dump
         * before exiting by returning to Scheduler.dumpStackAndDie passing
         * it the fp of the offending thread.
         *
         * We could try to continue, but sometimes doing so results
         * in cascading failures
         * and it's hard to tell what the real problem was.
         */
        int dumpStack = *(int *) ((char *) localJTOC + DumpStackAndDieOffset);

        /* setup stack frame to contain the frame pointer */
        sp = (long unsigned int *) IA32_ESP(context);

        /* put fp as a  parameter on the stack  */
        IA32_ESP(context) = IA32_ESP(context) - 4;
        sp = (long unsigned int *) IA32_ESP(context);
        *sp = localFrameAddress;
        IA32_EAX(context) = localFrameAddress; // must pass localFrameAddress in first param register!

        /* put a return address of zero on the stack */
        IA32_ESP(context) = IA32_ESP(context) - 4;
        sp = (long unsigned int *) IA32_ESP(context);
        *sp = 0;

        /* set up to goto dumpStackAndDie routine ( in Scheduler) as if called */
        IA32_EIP(context) = dumpStack;
        *vmr_inuse = false;

        pthread_mutex_unlock( &exceptionLock );
        return;
    }

    *vmr_inuse = 1;                     /* mark in use to avoid infinite loop */

    /* move gp registers to Registers object */
    vmr_gprs[Constants_EAX] = IA32_EAX(context);
    vmr_gprs[Constants_ECX] = IA32_ECX(context);
    vmr_gprs[Constants_EDX] = IA32_EDX(context);
    vmr_gprs[Constants_EBX] = IA32_EBX(context);
    vmr_gprs[Constants_ESP] = IA32_ESP(context);
    vmr_gprs[Constants_EBP] = IA32_EBP(context);
    vmr_gprs[Constants_ESI] = IA32_ESI(context);
    vmr_gprs[Constants_EDI] = IA32_EDI(context);

    /* set the next instruction for the failing frame */
    instructionFollowing = getInstructionFollowing(localInstructionAddress);


    /*
     * Advance ESP to the guard region of the stack.
     * Enables opt compiler to have ESP point to somewhere
     * other than the bottom of the frame at a PEI (see bug 2570).
     *
     * We'll execute the entire code sequence for
     * Runtime.deliverHardwareException et al. in the guard region of the
     * stack to avoid bashing stuff in the bottom opt-frame.
     */
    sp = (long unsigned int *) IA32_ESP(context);
    uintptr_t stackLimit
        = *(unsigned *)(threadObjectAddress + RVMThread_stackLimit_offset);
    if ((uintptr_t) sp <= stackLimit - 384) {
        writeErr("sp (0x%08" PRIxPTR ")too far below stackLimit (0x%08" PRIxPTR ")to recover\n", (uintptr_t) sp, stackLimit);
        signal(signo, SIG_DFL);
        raise(signo);
        // We should never get here.
        _exit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
    }
    sp = (long unsigned int *)stackLimit - 384;
    stackLimit -= Constants_STACK_SIZE_GUARD;
    *(unsigned *)(threadObjectAddress + RVMThread_stackLimit_offset) = stackLimit;
    *(unsigned *)(IA32_ESI(context) + Processor_activeThreadStackLimit_offset) = stackLimit;

    /* Insert artificial stackframe at site of trap. */
    /* This frame marks the place where "hardware exception registers" were saved. */
    sp = (long unsigned int *) ((char *) sp - Constants_STACKFRAME_HEADER_SIZE);
    fp = (long unsigned int *) ((char *) sp - 4 - Constants_STACKFRAME_BODY_OFFSET); /*  4 = wordsize  */

    /* fill in artificial stack frame */
    *(int *) ((char *) fp + Constants_STACKFRAME_FRAME_POINTER_OFFSET)
        = localFrameAddress;
    *(int *) ((char *) fp + Constants_STACKFRAME_METHOD_ID_OFFSET)
        = HardwareTrapMethodId;
    *(int *) ((char *) fp + Constants_STACKFRAME_RETURN_ADDRESS_OFFSET)
        = instructionFollowing;

    /* fill in call to "deliverHardwareException" */
    sp = (long unsigned int *) ((char *) sp - 4);       /* first parameter is type of trap */

    if (signo == SIGSEGV) {
        *(int *) sp = Runtime_TRAP_NULL_POINTER;

        /* an int immediate instruction produces a SIGSEGV signal.
           An int 3 instruction a trap fault */
        if (*(unsigned char *)(localInstructionAddress) == 0xCD) {
            // is INT imm instruction
            unsigned char code = *(unsigned char*)(localInstructionAddress+1);
            code -= Constants_RVM_TRAP_BASE;
            *(int *) sp = code;
        }
    }

    else if (signo == SIGFPE) {
        *(int *) sp = Runtime_TRAP_DIVIDE_BY_ZERO;
    }

    else if (signo == SIGTRAP) {
        *(int *) sp = Runtime_TRAP_UNKNOWN;

        //fprintf(SysTraceFile, "op code is 0x%x",*(unsigned char *)(localInstructionAddress));
        //fprintf(SysTraceFile, "next code is 0x%x",*(unsigned char *)(localInstructionAddress+1));
        if (*(unsigned char *)(localInstructionAddress - 1) == 0xCC) {
            // is INT 3 instruction
        }
    }

    else {
        *(int *) sp = Runtime_TRAP_UNKNOWN;
    }

    IA32_EAX(context) = *(int *)sp; // also pass first param in EAX.
    if (lib_verbose)
        fprintf(SysTraceFile, "Trap code is 0x%x\n", IA32_EAX(context));

    sp = (long unsigned int *) ((char *) sp - 4);       /* next parameter is info for array bounds trap */
    *(int *) sp = *(unsigned *) (localVirtualProcessorAddress + Processor_arrayIndexTrapParam_offset);
    IA32_EDX(context) = *(int *)sp; // also pass second param in EDX.
    sp = (long unsigned int *) ((char *) sp - 4);       /* return address - looks like called from failing instruction */
    *(int *) sp = instructionFollowing;

    /* store instructionFollowing and fp in Registers,ip and Registers.fp */
    *vmr_ip = instructionFollowing;
    *vmr_fp = localFrameAddress;

    if (lib_verbose)
        fprintf(SysTraceFile, "Set vmr_fp to 0x%x\n", localFrameAddress);

    /* set up context block to look like the artificial stack frame is
     * returning  */
    IA32_ESP(context) = (int) sp;
    IA32_EBP(context) = (int) fp;
    *(unsigned int *) (localVirtualProcessorAddress + Processor_framePointer_offset) = (int) fp;

    /* setup to return to deliver hardware exception routine */
    IA32_EIP(context) = javaExceptionHandlerAddress;

    pthread_mutex_unlock( &exceptionLock );
}


static void
softwareSignalHandler(int signo,
                      siginfo_t UNUSED *si,
                      void *context)
{
    // asynchronous signal used to awaken internal debugger
    if (signo == SIGQUIT) {
        // Turn on debug-request flag.
        // Note that "jtoc" is not necessarily valid, because we might have interrupted
        // C-library code, so we use boot image jtoc address (== VmToc) instead.
        // !!TODO: if vm moves table, it must tell us so we can update "VmToc".
        // For now, we assume table is fixed in boot image and never moves.
        //
        unsigned *flag = (unsigned *)((char *)VmToc + DebugRequestedOffset);
        write(SysTraceFd, Me, strlen(Me));
        if (*flag) {
            static const char *message = ": debug request already in progress, please wait\n";
            write(SysTraceFd, message, strlen(message));
        } else {
            static const char *message = ": debug requested, waiting for a thread switch\n";
            write(SysTraceFd, message, strlen(message));
            *flag = 1;
        }
        return;
    }

    /** We need to adapt this code so that we run the exit handlers
        appropriately. */

    if (signo == SIGTERM) {
        // Presumably we received this signal because someone wants us
        // to shut down.  Exit directly (unless the lib_verbose flag is set).
        // TODO: Run the shutdown hooks instead.
        if (!lib_verbose) {
            /* Now reraise the signal.  We reactivate the signal's
               default handling, which is to terminate the process.
               We could just call `exit' or `abort',
               but reraising the signal sets the return status
               from the process correctly.
               TODO: Go run shutdown hooks before we re-raise the signal. */
            signal(signo, SIG_DFL);
            raise(signo);
        }


        DumpStackAndDieOffset = bootRecord->dumpStackAndDieOffset;

        unsigned int localJTOC = VmToc;
        int dumpStack = *(int *) ((char *) localJTOC + DumpStackAndDieOffset);

        /* get the frame pointer from processor object  */
        unsigned int localVirtualProcessorAddress       = IA32_ESI(context);
        unsigned int localFrameAddress =
            *(unsigned *) (localVirtualProcessorAddress + Processor_framePointer_offset);

        /* setup stack frame to contain the frame pointer */
        long unsigned int *sp = (long unsigned int *) IA32_ESP(context);

        /* put fp as a  parameter on the stack  */
        IA32_ESP(context) = IA32_ESP(context) - 4;
        sp = (long unsigned int *) IA32_ESP(context);
        *sp = localFrameAddress;
        // must pass localFrameAddress in first param register!
        IA32_EAX(context) = localFrameAddress;

        /* put a return address of zero on the stack */
        IA32_ESP(context) = IA32_ESP(context) - 4;
        sp = (long unsigned int *) IA32_ESP(context);
        *sp = 0;

        /* goto dumpStackAndDie routine (in Scheduler) as if called */
        IA32_EIP(context) = dumpStack;
        return;
    }

    /* Default case. */
    fprintf(SysTraceFile, "%s: got an unexpected software signal (# %d)", Me, signo);
#if defined __GLIBC__ && defined _GNU_SOURCE
    fprintf(SysTraceFile, " %s", strsignal(signo));
#endif
    fprintf(SysTraceFile, "; ignoring it.\n");
}

static void*
mapImageFile(const char *fileName, const void *targetAddress, int prot,
             unsigned *roundedImageSize) {

    /* open and mmap the image file.
     * create bootRegion
     */
    FILE *fin = fopen (fileName, "r");
    if (!fin) {
        fprintf(SysTraceFile, "%s: can't find bootimage file\"%s\"\n", Me, fileName);
        return 0;
    }

    /* measure image size */
    if (lib_verbose)
        fprintf(SysTraceFile, "%s: loading from \"%s\"\n", Me, fileName);
    fseek (fin, 0L, SEEK_END);
    unsigned actualImageSize = ftell(fin);
    *roundedImageSize = pageRoundUp(actualImageSize);
    fseek (fin, 0L, SEEK_SET);


    void *bootRegion = 0;
    bootRegion = mmap((void*)targetAddress, *roundedImageSize,
		      prot,
		      MAP_FIXED | MAP_PRIVATE | MAP_NORESERVE,
		      fileno(fin), 0);
    if (bootRegion == (void *) MAP_FAILED) {
        fprintf(SysErrorFile, "%s: mmap failed (errno=%d): %s\n",
		Me, errno, strerror(errno));
        return 0;
    }
    if (bootRegion != targetAddress) {
	fprintf(SysErrorFile, "%s: Attempted to mmap in the address %p; "
			     " got %p instead.  This should never happen.",
		Me, bootRegion, targetAddress);
	/* Don't check the return value.  This is insane already.
	 * If we weren't part of a larger runtime system, I'd abort at this
	 * point.  */
	(void) munmap(bootRegion, *roundedImageSize);
	return 0;
    }

    /* Quoting from the Linux mmap(2) manual page:
       "closing the file descriptor does not unmap the region."
    */
    if (fclose (fin) != 0) {
        fprintf(SysErrorFile, "%s: close failed (errno=%d)\n", Me, errno);
        return 0;
    }
    return bootRegion;
}




/* Returns 1 upon any errors.   Never returns except to report an error. */
int
createVM(int UNUSED vmInSeparateThread)
{
    /* don't buffer trace or error message output */
    setbuf (SysErrorFile, 0);
    setbuf (SysTraceFile, 0);

    unsigned roundedDataRegionSize;
    void *bootDataRegion = mapImageFile(bootDataFilename,
                                        bootImageDataAddress,
                                        PROT_READ | PROT_WRITE,
                                        &roundedDataRegionSize);
    if (bootDataRegion != bootImageDataAddress)
        return 1;

    unsigned roundedCodeRegionSize;
    void *bootCodeRegion = mapImageFile(bootCodeFilename,
                                        bootImageCodeAddress,
                                        PROT_READ | PROT_WRITE | PROT_EXEC,
                                        &roundedCodeRegionSize);
    if (bootCodeRegion != bootImageCodeAddress)
        return 1;

    unsigned roundedRMapRegionSize;
    void *bootRMapRegion = mapImageFile(bootRMapFilename,
                                        bootImageRMapAddress,
                                        PROT_READ,
                                        &roundedRMapRegionSize);
    if (bootRMapRegion != bootImageRMapAddress)
        return 1;


    /* validate contents of boot record */
    bootRecord = (BootRecord *) bootDataRegion;

    if (bootRecord->bootImageDataStart != (unsigned) bootDataRegion) {
        fprintf(SysErrorFile, "%s: image load error: built for 0x%08x but loaded at 0x%08x\n",
                Me, bootRecord->bootImageDataStart, (unsigned) bootDataRegion);
        return 1;
    }

    if (bootRecord->bootImageCodeStart != (unsigned) bootCodeRegion) {
        fprintf(SysErrorFile, "%s: image load error: built for 0x%08x but loaded at 0x%08x\n",
                Me, bootRecord->bootImageCodeStart, (unsigned) bootCodeRegion);
        return 1;
    }

    if (bootRecord->bootImageRMapStart != (unsigned) bootRMapRegion) {
        fprintf(SysErrorFile, "%s: image load error: built for 0x%08x but loaded at 0x%08x\n",
                Me, bootRecord->bootImageRMapStart, (unsigned) bootRMapRegion);
        return 1;
    }

    if ((bootRecord->spRegister % 4) != 0) {
        fprintf(SysErrorFile, "%s: image format error: sp (0x%08x) is not word aligned\n",
                 Me, bootRecord->spRegister);
        return 1;
    }

    if ((bootRecord->ipRegister % 4) != 0) {
        fprintf(SysErrorFile, "%s: image format error: ip (0x%08x) is not word aligned\n",
                 Me, bootRecord->ipRegister);
        return 1;
    }

    if (((u_int32_t *) bootRecord->spRegister)[-1] != 0xdeadbabe) {
        fprintf(SysErrorFile,
                 "%s: image format error: missing stack sanity check marker (0x%08x)\n",
                 Me, ((int *) bootRecord->spRegister)[-1]);
        return 1;
    }

    /* remember jtoc location for later use by trap handler */
    VmToc = bootRecord->tocRegister;

    /* get and remember JTOC offset of Scheduler.processors[] */
    ProcessorsOffset = bootRecord->greenProcessorsOffset;

    // remember JTOC offset of Scheduler.DebugRequested
    //
    DebugRequestedOffset = bootRecord->debugRequestedOffset;

    /* write freespace information into boot record */
    bootRecord->initialHeapSize  = initialHeapSize;
    bootRecord->maximumHeapSize  = maximumHeapSize;
    bootRecord->bootImageDataStart   = (int) bootDataRegion;
    bootRecord->bootImageDataEnd     = (int) bootDataRegion + roundedDataRegionSize;
    bootRecord->bootImageCodeStart   = (int) bootCodeRegion;
    bootRecord->bootImageCodeEnd     = (int) bootCodeRegion + roundedCodeRegionSize;
    bootRecord->bootImageRMapStart   = (int) bootRMapRegion;
    bootRecord->bootImageRMapEnd     = (int) bootRMapRegion + roundedRMapRegionSize;
    bootRecord->verboseBoot      = verboseBoot;

    /* write sys.C linkage information into boot record */

    setLinkage(bootRecord);
    if (lib_verbose) {
        fprintf(SysTraceFile, "%s: boot record contents:\n", Me);
        fprintf(SysTraceFile, "   bootImageDataStart:   0x%08x\n",
                bootRecord->bootImageDataStart);
        fprintf(SysTraceFile, "   bootImageDataEnd:     0x%08x\n",
                bootRecord->bootImageDataEnd);
        fprintf(SysTraceFile, "   bootImageCodeStart:   0x%08x\n",
                bootRecord->bootImageCodeStart);
        fprintf(SysTraceFile, "   bootImageCodeEnd:     0x%08x\n",
                bootRecord->bootImageCodeEnd);
        fprintf(SysTraceFile, "   bootImageRMapStart:   0x%08x\n",
                bootRecord->bootImageRMapStart);
        fprintf(SysTraceFile, "   bootImageRMapEnd:     0x%08x\n",
                bootRecord->bootImageRMapEnd);
        fprintf(SysTraceFile, "   initialHeapSize:      0x%08x\n",
                bootRecord->initialHeapSize);
        fprintf(SysTraceFile, "   maximumHeapSize:      0x%08x\n",
                bootRecord->maximumHeapSize);
        fprintf(SysTraceFile, "   tiRegister:           0x%08x\n",
                bootRecord->tiRegister);
        fprintf(SysTraceFile, "   spRegister:           0x%08x\n",
                bootRecord->spRegister);
        fprintf(SysTraceFile, "   ipRegister:           0x%08x\n",
                bootRecord->ipRegister);
        fprintf(SysTraceFile, "   tocRegister:          0x%08x\n",
                bootRecord->tocRegister);
        fprintf(SysTraceFile, "   sysConsoleWriteCharIP:0x%08x\n",
                bootRecord->sysConsoleWriteCharIP);
        fprintf(SysTraceFile, "   ...etc...                   \n");
    }

    /* install a stack for hardwareTrapHandler() to run on */
    stack_t stack;

    memset (&stack, 0, sizeof stack);
    stack.ss_sp = new char[SIGSTKSZ];

    stack.ss_size = SIGSTKSZ;
    if (sigaltstack (&stack, 0)) {
        fprintf(SysErrorFile, "%s: sigaltstack failed (errno=%d)\n",
                Me, errno);
        return 1;
    }

    /* install hardware trap signal handler */
    struct sigaction action;

    memset (&action, 0, sizeof action);
    action.sa_sigaction = &hardwareTrapHandler;
    /*
     * mask all signal from reaching the signal handler while the signal
     * handler is running
     */
    if (sigfillset(&(action.sa_mask))) {
        fprintf(SysErrorFile, "%s: sigfillset failed (errno=%d)\n", Me, errno);
        return 1;
    }
    /*
     * exclude the signal used to wake up the daemons
     */
    if (sigdelset(&(action.sa_mask), SIGCONT)) {
        fprintf(SysErrorFile, "%s: sigdelset failed (errno=%d)\n", Me, errno);
        return 1;
    }

    action.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_RESTART;
    if (sigaction (SIGSEGV, &action, 0)) {
        fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
        return 1;
    }
    if (sigaction (SIGFPE, &action, 0)) {
        fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
        return 1;
    }
    if (sigaction (SIGTRAP, &action, 0)) {
        fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
        return 1;
    }

    // alignment checking: we want the handler to handle alignment exceptions
    if (sigaction (SIGBUS, &action, 0)) {
        fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
        return 1;
    }

    /* install software signal handler */
    action.sa_sigaction = &softwareSignalHandler;
    if (sigaction (SIGALRM, &action, 0)) {      /* catch timer ticks (so we can timeslice user level threads) */
        fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
        return 1;
    }
    if (sigaction (SIGQUIT, &action, 0)) { /* catch QUIT to invoke debugger
                                            * thread */
        fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
        return 1;
    }
    if (sigaction (SIGTERM, &action, 0)) { /* catch TERM to dump and die */
        fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
        return 1;
    }

    // Ignore "write (on a socket) with nobody to read it" signals so
    // that sysWriteBytes() will get an EPIPE return code instead of trapping.
    //
    memset (&action, 0, sizeof action);
    action.sa_handler = SIG_IGN;
    if (sigaction(SIGPIPE, &action, 0)) {
        fprintf(SysErrorFile, "%s: sigaction failed (errno=%d)\n", Me, errno);
        return 1;
    }

    /* set up initial stack frame */
    int ip   = bootRecord->ipRegister;
    int jtoc = bootRecord->tocRegister;
    int pr;
    int *sp  = (int *) bootRecord->spRegister;
    {
        unsigned *processors
            = *(unsigned **) (bootRecord->tocRegister
                              + bootRecord->greenProcessorsOffset);
        pr      = processors[GreenScheduler_PRIMORDIAL_PROCESSOR_ID];

        /* initialize the thread id jtoc, and framepointer fields in the primordial
         * processor object.
         */
        *(unsigned int *) (pr + Processor_threadId_offset)
            = Scheduler_PRIMORDIAL_THREAD_INDEX
                << ThinLockConstants_TL_THREAD_ID_SHIFT;
        *(unsigned int *) (pr + Processor_framePointer_offset)
            = (int)sp - 8;
    }

    *--sp = 0xdeadbabe;         /* STACKFRAME_RETURN_ADDRESS_OFFSET */
    *--sp = Constants_STACKFRAME_SENTINEL_FP; /* STACKFRAME_FRAME_POINTER_OFFSET */
    *--sp = Constants_INVISIBLE_METHOD_ID; /* STACKFRAME_METHOD_ID_OFFSET */
    *--sp = 0; /* STACKFRAME_NEXT_INSTRUCTION_OFFSET (for AIX compatability) */

    // fprintf(SysTraceFile, "%s: here goes...\n", Me);
    int rc = boot (ip, pr, (int) sp);

    fprintf(SysErrorFile, "%s: createVM(): boot() returned; failed to create a virtual machine.  rc=%d.  Bye.\n", Me, rc);
    return 1;
}


// Get address of JTOC.
extern "C" void *
getJTOC(void)
{
    return (void*) VmToc;
}

// Get offset of Scheduler.processors in JTOC.
extern "C" Offset
getProcessorsOffset(void)
{
    return ProcessorsOffset;
}
