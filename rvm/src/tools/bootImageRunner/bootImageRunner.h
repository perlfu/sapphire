/*
 * (C) Copyright IBM Corp 2001,2002,2003
 */
// $Id$
/* Handles "extern" declarations shared among files in
 * rvm/src/tools/bootImageRunner/.
 * @author: Steven Augart, based on contributions from everyone who ever
 * touched files in that directory.
 *
 */
// #include "bootImageRunner.h"	// In rvm/src/tools/bootImageRunner
// Sink for messages relating to serious errors detected by C runtime.
//
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif    

extern FILE *SysErrorFile;    // sink for serious error messages
extern FILE *SysErrorFile;	// libvm.C
// extern int SysErrorFd;	// in IA32 libvm.C, not in powerpc.
 
// Sink for trace messages produced by VM.sysWrite().
extern FILE *SysTraceFile;	// libvm.C
extern int   SysTraceFd;	// libvm.C

// Command line arguments to be passed to boot image.
//
extern char **	JavaArgs;	// libvm.C
extern int	JavaArgc; 	// libvm.C

// Emit trace information?
//
extern int lib_verbose;		// libvm.C

// command line arguments
// startup configuration option with default values
extern const char *bootFilename;	/* Defined in libvm.C */
// name of program that will load and run RVM
extern char *Me;		// Defined in libvm.C

/* libvm.C and RunBootImage.C */
extern unsigned initialHeapSize;
extern unsigned maximumHeapSize;
extern int verboseBoot;

/* Defined in libvm.C; used in RunBootImage.C */
extern int createJVM(int);
/* Used in libvm.C; Defined in sys.C */
extern int getArrayLength(void* ptr);

/* Used in libvm.C; Defined in sys.C */
extern int getTimeSlice_msec(void);

/* sys.C and RunBootImage.C */
extern void findMappable(void);

#if defined(RVM_FOR_POWERPC)
/* Used in libvm.C, sys.C.  Defined in assembly code: */
extern void bootThread(int jtoc, int pr, int ti_or_ip, int fp); // assembler routine
#elif defined(RVM_FOR_IA32)
extern int bootThread(int ti_or_ip, int jtoc, int pr, int sp); // assembler routine
#else
// Some files (e.g., syswrap.C) don't need bootThread's prototype declared,
// but do need a few other prototypes here.  So we don't make it an error:
// #error "Warning: Undefined configuration: neither IA32 nor POWERPC: won't declare bootThread()"
#endif

// These are defined in libvm.C.
#if !defined(RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS)
extern void *getJTOC(void);
extern int getProcessorsOffset(void);
#endif // RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS

/* These are defined in sys.C; used in syswrap.C */
extern pthread_key_t VmProcessorIdKey;
extern pthread_key_t IsVmProcessorKey;

// Defined in sys.C.; used in libvm.C
extern void sysSyncCache(caddr_t, int size);
// Defined in libvm.C.  Used in sys.C.
extern void processTimerTick(void);

#ifdef __cplusplus
}
#endif    
