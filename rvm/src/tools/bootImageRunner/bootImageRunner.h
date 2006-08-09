/*
 * (C) Copyright IBM Corp 2001,2002,2003,2004
 */
// $Id$
/* Handles "extern" declarations shared among files in
 * rvm/src/tools/bootImageRunner/.
 * @author: Steven Augart, based on contributions from everyone who ever
 * touched files in that directory.
 *
 */
// #include "bootImageRunner.h"	// In rvm/src/tools/bootImageRunner

#include <stdio.h>
#include <inttypes.h>

#ifdef __cplusplus
extern "C" {
#endif    
#if 0
} /* un-confuse the auto-indenter */
#endif

#include "../../include/cAttributePortability.h"

#if defined(RVM_FOR_32_ADDR)
#define VM_Offset int32_t
#elif defined(RVM_FOR_64_ADDR)
#define VM_Offset int64_t
#endif
// Sink for messages relating to serious errors detected by C runtime.
extern FILE *SysErrorFile;    // sink for serious error messages
extern FILE *SysErrorFile;	// libvm.C
// extern int SysErrorFd;	// in IA32 libvm.C, not in powerpc.
 
// Sink for trace messages produced by VM.sysWrite().
extern FILE *SysTraceFile;	// libvm.C
extern int   SysTraceFd;	// libvm.C

// Command line arguments to be passed to boot image.
//
extern const char **	JavaArgs;	// libvm.C
extern int	JavaArgc; 	// libvm.C

// Emit trace information?
//
extern int lib_verbose;		// libvm.C

// command line arguments
// startup configuration option with default values
extern const char *bootDataFilename;	/* Defined in libvm.C */
extern const char *bootCodeFilename;	/* Defined in libvm.C */
extern const char *bootRMapFilename;	/* Defined in libvm.C */
// name of program that will load and run RVM
extern char *Me;		// Defined in libvm.C

/* libvm.C and RunBootImage.C */
extern uint64_t initialHeapSize;
extern uint64_t maximumHeapSize;
// #define RVM_WITH_FLEXIBLE_STACK_SIZES
#ifdef RVM_WITH_FLEXIBLE_STACK_SIZES
extern uint64_t initialStackSize;
extern uint64_t stackGrowIncrement;
extern uint64_t maximumStackSize;
#endif // RVM_WITH_FLEXIBLE_STACK_SIZES

/* Defined in RunBootImage.C */
unsigned int parse_memory_size(
    const char *sizeName, const char *sizeFlag, 
    const char *defaultFactor, unsigned roundTo,
    const char *token, const char *subtoken, bool *fastExit);

extern int verboseBoot;

/* Set to 1 for true, 0 for false, -1 for Debian auto-detection. */ 
    extern int rvm_singleVirtualProcessor;
    


/* Defined in libvm.C; used in RunBootImage.C */
extern int createVM(int);
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
extern void *getJTOC(void);
extern VM_Offset getProcessorsOffset(void);

/* These are defined in sys.C; used in syswrap.C */
extern pthread_key_t VmProcessorKey;
extern pthread_key_t IsVmProcessorKey;

// Defined in sys.C.; used in libvm.C
extern void sysSyncCache(void *, size_t size);
// Defined in sys.C.  Used in libvm.C.
extern void processTimerTick(void);

#if 0
{ /* un-confuse the auto-indenter */
#endif
#ifdef __cplusplus
}
#endif    
