/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * A method that has been compiled into machine code by one of our compilers.
 * @author Bowen Alpern
 * @author Derek Lieber
 */
class VM_CompiledMethod {
  //-----------//
  // interface //
  //-----------//
   
  final int             getId()           { return id;           }
  final VM_Method       getMethod()       { return method;       }
  final INSTRUCTION[]   getInstructions() { return instructions; }
  final VM_CompilerInfo getCompilerInfo() { return compilerInfo; }
   
  //----------------//
  // implementation //
  //----------------//
   
  private int             id;           // index of this compiled method in VM_ClassLoader.compiledMethods[]
  private VM_Method       method;       // method
  private INSTRUCTION[]   instructions; // machine code for that method
  private VM_CompilerInfo compilerInfo; // tables and maps for handling exceptions, gc, etc.
   
  VM_CompiledMethod(int id, VM_Method method, 
		    INSTRUCTION[] instructions, 
		    VM_CompilerInfo compilerInfo) {
    this.id           = id;
    this.method       = method;
    this.instructions = instructions;
    this.compilerInfo = compilerInfo;
  }
}
