/*
 * (C) Copyright IBM Corp. 2001
 */
interface VM_ObjectLayoutConstants {
   //--------------------------------------------------------------------------------------------//
   //                                 Object layout conventions.                                 //
   //--------------------------------------------------------------------------------------------//

   // For scalar objects, we lay out the fields right (hi mem) to left (lo mem).
   // For array objects, we lay out the elements left (lo mem) to right (hi mem).
   // Every object's header contains a "type information block" field and a "status" field.
   // Object references always point 3 words to right of header.
   //
   // |<- lo memory                                                    hi memory ->|
   //
   // scalar-object layout:
   //
   // +------+------+------+------+------+------+ - -  + - -  +
   // |fldN-1| fldx | fld1 | fld0 | tib  |status|
   // +------+------+------+------+------+------+ - -  + - -  +
   //                             .             .       ^objref
   //                             .<---header-->.
   //                             .             .
   //  array-object layout:       +------+------+------+------+------+------+------+
   //                             | tib  |status| len  | elt0 | elt1 | ...  |eltN-1|
   //                             +------+------+------+------+------+------+------+
   //                                   /        \      ^objref
   //                                  /          \
   //                                 /            \
   //                                /              \
   //                               xxxx.xxxx.xxxx.xxxx
   //                                              ^gc-byte
   //
   // This model allows efficient array access: the array pointer can be
   // used directly in the base+offset subscript calculation, with no
   // additional constant required.
   //
   // This model allows free null pointer checking: since the first access to any
   // object is via a negative offset (length field for an array, regular field
   // for an object), that reference will wrap around to hi memory (address 0xffffxxxx)
   // in the case of a null pointer. The high segment of memory is not mapped to the
   // current process, so loads/stores through such a pointer will cause a trap that
   // we can catch with a unix signal handler.
   //
   
   // Header for all objects
   //
   // offset from object ref to header, in bytes
   static final int OBJECT_HEADER_OFFSET  = (VM.BuildForConcurrentGC ? -16 :
			(VM.CompileForGCTracing ? -24 : -12));
   // offset from object ref to .tib field, in bytes
   static final int OBJECT_TIB_OFFSET     = (VM.BuildForConcurrentGC ? -16 :
			(VM.CompileForGCTracing ? -24 : -12));
   // offset from object ref to .status field, in bytes
   static final int OBJECT_STATUS_OFFSET  = (VM.BuildForConcurrentGC ? -12 :
			(VM.CompileForGCTracing ? -20 : -8));
   // offset from object ref to .refcount field, in bytes
   static final int OBJECT_REFCOUNT_OFFSET  = (VM.BuildForConcurrentGC ? -8 : 0);
   // offset from object ref to  GC byte of the status field, in bytes
   static final int OBJECT_GC_BYTE_OFFSET = (VM.BuildForConcurrentGC ? -9 :
			(VM.CompileForGCTracing ? -17 : -5));
   
   // offset from object ref to .oid field, in bytes
   static final int OBJECT_OID_OFFSET	= (VM.CompileForGCTracing ? -12 : 0);
   // offset from object ref to .link field, in bytes
   static final int OBJECT_LINK_OFFSET	= (VM.CompileForGCTracing ? -16 : 0);
   // offset from object ref to OBJECT_DEATH field, in bytes
   static final int OBJECT_DEATH_OFFSET	= (VM.CompileForGCTracing ? -8 : 0);
   // amount by which tracing causes headers to grow
   static final int TRACE_HEADER_EXTRA	= (VM.CompileForGCTracing ? 12 : 0);
                                                                                  

   // Header for scalar objects, only
   //
   // size of header (.tib + .status), in bytes
   static final int SCALAR_HEADER_SIZE    =   (VM.BuildForConcurrentGC ? 12 :
			(VM.CompileForGCTracing ? 20 : 8));

   // Header for array objects, only
   //
   // size of header (.tib + .status + .length), in bytes
   static final int ARRAY_HEADER_SIZE     =  (VM.BuildForConcurrentGC ? 16 :
			(VM.CompileForGCTracing ? 24 : 12));
   static final int ARRAY_LENGTH_OFFSET   =  -4; // offset from object ref to .length field, in bytes
   static final int ARRAY_ELEMENT_OFFSET  =   0; // offset from object ref to element 0, in bytes

   // Special value returned by VM_ClassLoader.getFieldOffset() or VM_ClassLoader.getMethodOffset()
   // to indicate fields or methods that must be accessed via dynamic linking code because their 
   // offset is not yet known or the class's static initializer has not yet been run.
   //
   // We choose a value that will never match a valid jtoc-, instance-, 
   // or virtual method table- offset. Zero is a good value because:
   //      slot 0 of jtoc is never used
   //      instance field offsets are always negative w.r.t. object pointer
   //      virtual method offsets are always positive w.r.t. TIB pointer
   //      0 is a "free" (default) data initialization value
   //
   public static final int NEEDS_DYNAMIC_LINK = 0;
   
   // Layout of "status" field in object header.
   // The status field of each object contains bits used for housekeeping details.
   // It contains space for the object's:
   //   - hash code
   //   - garbage collection state
   //   - synchronization (lock/wait) state
   //
   // Here's a picture of the status field bits:
   //                                   "gc byte"
   //     F                             |-------|
   //     0TTT TTTT TTTT TTCC CCCC uuHH HHHH HHBM
   //     ^bit 31                               ^bit 0

   static final int OBJECT_FAT_LOCK_MASK      = 0x80000000; // (F)  1 bit to identify locks that are heavy
   static final int OBJECT_LOCK_ID_MASK       = 0x7ffff000; // (Z) 19 bits for id of a heavy lock
   static final int OBJECT_THREAD_ID_MASK     = 0x7ffc0000; // (T) 13 bits for id of thread that owns the lock
   static final int OBJECT_LOCK_COUNT_MASK    = 0x0003f000; // (C)  6 bits for lock count (this is overkill, we don't really need more than 2 or 3 bits)
   static final int OBJECT_HASHCODE_MASK      = 0x000003fc; // (H)  8 bits for hashcode
   static final int OBJECT_BARRIER_MASK       = 0x00000002; // (B)  1 bit for writebarrier
   static final int OBJECT_GC_MARK_MASK       = 0x00000001; // (M)  1 bit for collector (must be rightmost because it's used to distinguish "real" status word from a forwarding pointer)
// static final int OBJECT_LOCK_LOCAL_MASK    = 0x????????; // (L)  1 bit to identify locks that are thread local (see David Bacon) // not yet used
   static final int OBJECT_UNUSED_MASK        = 0x00000c00; // (u)  1 bit as yet unallocated
   static final int OBJECT_UNLOCK_MASK        = ~(OBJECT_FAT_LOCK_MASK | OBJECT_LOCK_ID_MASK); // all but the lock related bits
   static final int OBJECT_HASHCODE_MARK_MASK =  (OBJECT_HASHCODE_MASK | OBJECT_GC_MARK_MASK); // combined bits
   static final int OBJECT_HASHCODE_UNIT      = 4;          // add to increment hashcode
   static final int OBJECT_LOCK_COUNT_SHIFT   = 12;         // for extracting lock count
   static final int OBJECT_LOCK_COUNT_UNIT    = 0x00001000; // add to increment count
   static final int OBJECT_THREAD_ID_SHIFT    = 18;         // for extracting id of owning thread
   static final int OBJECT_LOCK_ID_SHIFT      = 12;         // for extracting id of fat lock

   //--------------------------------------------------------------------------------------------//
   //                      Type Information Block (TIB) Layout Constants                         //
   //--------------------------------------------------------------------------------------------//
   // NOTE: only a subset of the fixed TIB slots (1..6) will actually
   //       be allocated in any configuration. 
   //       When a slots is not allocated, we slide the other slots up.
   //       The interface slots (1..k) are only allocated when using IMT
   //       directly embedded in the TIB
   //
   //                               Object[] (type info block)        VM_Type (class info)
   //                                  /                              /
   //          +--------------------+              +--------------+
   //          |    TIB pointer     |              |  TIB pointer |
   //          +--------------------+              +--------------+
   //          |      status        |              |    status    |
   //          +--------------------+              +--------------+
   //          |      length        |              |    field0    |
   //          +--------------------+              +--------------+
   //  TIB:  0:|       type         +------------> |     ...      |
   //          +--------------------+              +--------------+
   //        1:|   superclass ids   +-->           |   fieldN-1   |
   //          +--------------------+              +--------------+
   //        2:|  implements trits  +-->
   //          +--------------------+
   //        3:|  array element TIB +-->              
   //          +--------------------+
   //        4:|     type cache     +-->              
   //          +--------------------+
   //        5:|     iTABLES        +-->              
   //          +--------------------+
   //        6:|  indirect IMT      +-->              
   //          +--------------------+
   //        1:|  interface slot 0  |              
   //          +--------------------+              
   //          |       ...          |
   //          +--------------------+
   //        K:| interface slot K-1 |
   //          +--------------------+
   //      K+1:|  virtual method 0  +-----+
   //          +--------------------+     |        
   //          |       ...          |     |                         INSTRUCTION[] (machine code)
   //          +--------------------+     |                        /
   //      K+N:| virtual method N-1 |     |        +--------------+
   //          +--------------------+     |        |  TIB pointer |
   //                                     |        +--------------+
   //                                     |        |    status    |
   //                                     |        +--------------+
   //                                     |        |    length    |
   //                                     |        +--------------+
   //                                     +------->|    code0     |
   //                                              +--------------+
   //                                              |      ...     |
   //                                              +--------------+
   //                                              |    codeN-1   |
   //                                              +--------------+
   //
   
   // Number of slots reserved for interface method pointers.
   //
   static final int IMT_METHOD_SLOTS = 
     VM.BuildForIMTInterfaceInvocation ? 29 : 0;
   
   static final int TIB_INTERFACE_METHOD_SLOTS = 
     VM.BuildForEmbeddedIMT ? IMT_METHOD_SLOTS : 0;
   
   // First slot of tib points to VM_Type (slot 0 in above diagram).
   //
   static final int TIB_TYPE_INDEX = 0;

   // A vector of ids for classes that this one extends. 
   // (see vm/classLoader/VM_DynamicTypeCheck.java)
   //
   static final int TIB_SUPERCLASS_IDS_INDEX = 
     TIB_TYPE_INDEX + (VM.BuildForFastDynamicTypeCheck ? 1 : 0);

   // A vector of maybe, yes, or no answers to the question 
   // "Does this class implement the ith interface?"  
   // (see vm/classLoader/VM_DynamicTypeCheck.java)
   //
   static final int TIB_IMPLEMENTS_TRITS_INDEX = 
     TIB_SUPERCLASS_IDS_INDEX + (VM.BuildForFastDynamicTypeCheck ? 1 : 0);
    
   // The TIB of the elements type of an array (may be null in fringe cases
   // when element type couldn't be resolved during array resolution).
   // Will be null when not an array.
   //
   static final int TIB_ARRAY_ELEMENT_TIB_INDEX = 
     TIB_IMPLEMENTS_TRITS_INDEX + (VM.BuildForFastDynamicTypeCheck ? 1 : 0);

   // If !VM.BuildForFastDynamicTypeChecking then allocate 1 TIB entry to 
   // hold type cache
   static final int TIB_TYPE_CACHE_TIB_INDEX = 
     TIB_ARRAY_ELEMENT_TIB_INDEX + (!VM.BuildForFastDynamicTypeCheck ? 1 : 0);
    
   // If VM.ITableInterfaceInvocation then allocate 1 TIB entry to hold 
   // an array of ITABLES
   static final int TIB_ITABLES_TIB_INDEX = 
     TIB_TYPE_CACHE_TIB_INDEX + (VM.BuildForITableInterfaceInvocation ? 1 : 0);
   
   // If VM.BuildForIndirectIMT then allocate 1 TIB entry to hold a
   // pointer to the IMT
   static final int TIB_IMT_TIB_INDEX = 
     TIB_ITABLES_TIB_INDEX + (VM.BuildForIndirectIMT ? 1 : 0);
     
   // Next group of slots point to interface method code blocks 
   // (slots 1..K in above diagram).
   static final int TIB_FIRST_INTERFACE_METHOD_INDEX = TIB_IMT_TIB_INDEX + 1;
 
   // Next group of slots point to virtual method code blocks 
   // (slots K+1..K+N in above diagram).
   static final int TIB_FIRST_VIRTUAL_METHOD_INDEX = 
     TIB_FIRST_INTERFACE_METHOD_INDEX + TIB_INTERFACE_METHOD_SLOTS;

   // Flag to mark interface methods that haven't yet been assigned a 
   // slot in their interface method table.
   static final int UNRESOLVED_INTERFACE_METHOD_OFFSET = -1;
}
