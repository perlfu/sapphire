/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Interface to dynamic libraries of underlying operating system.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_DynamicLibrary {

  private String libName;
  private int libHandler;

  /**
   * load a dynamic library and maintain it in this object
   * @param libraryName library name
   * @return library system handler (-1: not found or couldn't be created)
   */ 
  public VM_DynamicLibrary(String libraryName) {
    // convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now)
    //
    byte[] asciiName = new byte[libraryName.length() + 1]; // +1 for null terminator
    libraryName.getBytes(0, libraryName.length(), asciiName, 0);

    // make sure we have enough stack to load the library.  
    // This operation has been
    // known to require more than 20K of stack.
    VM_Thread myThread = VM_Thread.getCurrentThread();
    int stackNeededInBytes =  VM_StackframeLayoutConstants.STACK_SIZE_DLOPEN -
      (VM_Magic.getFramePointer() - myThread.stackLimit);
    if (stackNeededInBytes > 0 ) {
      if (myThread.hasNativeStackFrame())
        throw new java.lang.StackOverflowError("dlopen");
      else
        VM_Thread.resizeCurrentStack(myThread.stack.length + 
                                     (stackNeededInBytes >> 2),
                                     null); 
    }

    // PIN(asciiName);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    libHandler = VM.sysCall1(bootRecord.sysDlopenIP, 
                             VM_Magic.objectAsAddress(asciiName));
    // UNPIN(asciiName);

    if (libHandler==0) {
      VM.sysWrite("error loading library: " + libraryName);
      VM.sysWrite("\n");
      throw new UnsatisfiedLinkError();
    }

    libName = new String(libraryName);

    // initialize the JNI environment if not already done
    // if building with RVM_WITH_JNI flag, an  error would have been
    // thrown in the sysDlopen call before we get here
    VM_JNIEnvironment.boot();
  }



  /**
   * look up this dynamic library for a symbol
   * @param symbolName symbol name
   * @return symbol system handler 
   * (actually an address to an AixLinkage triplet)
   *           (-1: not found or couldn't be created)
   */ 
  public int getSymbol(String symbolName) {
    // convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now)
    //
    byte[] asciiName = new byte[symbolName.length() + 1]; // +1 for null terminator
    symbolName.getBytes(0, symbolName.length(), asciiName, 0);

    // PIN(asciiName);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    int address = VM.sysCall2(bootRecord.sysDlsymIP, libHandler, 
                              VM_Magic.objectAsAddress(asciiName));
    // UNPIN(asciiName);

    return address;
  }

  /**
   * unload a dynamic library
   * should destroy this object, maybe this should be in the finalizer?
   */
  public void unload() {
    VM.sysWrite("VM_DynamicLibrary.unload: not implemented yet \n");
  }

  /**
   * Tell the operating system to remove the dynamic library from the 
   * system space.
   */
  public void clean() {
    VM.sysWrite("VM_DynamicLibrary.clean: not implemented yet \n");
  }

  public String toString() {
    return "dynamic library " + libName + ", handler=" + libHandler;
  }
}
