/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * This class provides a layer of abstraction that the rest of the VM must
 * use in order to access the current <code>VM_Processor</code> object.
 *
 * @see VM_Processor
 *
 * @author Stephen Fink
 */
final class VM_ProcessorLocalState implements VM_Uninterruptible {
  
  /**
   * The C bootstrap program has placed a pointer to the initial
   * VM_Processor in the processor register.  This is OK, so do nothing.
   */
  static void boot() {
    // do nothing - everything is already set up.
  }

  /**
   * Return the current VM_Processor object
   */
  static VM_Processor getCurrentProcessor() {
    VM_Magic.pragmaInline();
    return VM_Magic.getProcessorRegister();
  }

  /**
   * Set the current VM_Processor object
   */
  static void setCurrentProcessor(VM_Processor p) {
    VM_Magic.pragmaInline();
    VM_Magic.setProcessorRegister(p);
  }
}
