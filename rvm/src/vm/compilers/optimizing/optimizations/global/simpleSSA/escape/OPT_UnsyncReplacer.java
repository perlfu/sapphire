/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Replace calls to synchronized methods to calls specialized to be
 * unsynchronized.
 *
 * @author Stephen Fink
 */
public class OPT_UnsyncReplacer implements OPT_Operators {
  private static boolean DEBUG = false;

  /** 
   * Generate an instance of this class for a particular
   * instantiation site.
   *
   * @param inst the allocation site 
   * @param ir governing ir
   * @return the object, or null if illegal 
   */
  public static OPT_UnsyncReplacer getReplacer (OPT_Instruction inst, 
      OPT_IR ir) {
    OPT_Register r = New.getResult(inst).register;
    return  new OPT_UnsyncReplacer(r, ir.options);
  }

  /** 
   * Perform the transformation
   */
  public void transform () {
    // first change the defs
    for (OPT_RegisterOperand def = reg.defList; def != null; 
        def = (OPT_RegisterOperand)def.getNext()) {
      transform(def);
    }
    // now fix the uses
    for (OPT_RegisterOperand use = reg.useList; use != null; 
        use = (OPT_RegisterOperand)use.getNext()) {
      transform(use);
    }
  }

  /** 
   * @param r the register operand target of the allocation 
   * @param options controlling compiler options
   */
  private OPT_UnsyncReplacer (OPT_Register r, OPT_Options options) {
    reg = r;
    this.options = options;
  }

  /** 
   * Perform the transformation for a given register appearance
   *
   * @param rop the def or use to check
   * @param options controlling compiler options
   */
  private void transform (OPT_RegisterOperand rop) {
    OPT_Instruction inst = rop.instruction;
    switch (inst.getOpcode()) {
      case CALL_opcode:
        OPT_RegisterOperand invokee = Call.getParam(inst, 0).asRegister();
        if (invokee == rop) {
          // replace with equivalent call on the synthetic 
          // unsynchronized type
          OPT_MethodOperand mop = Call.getMethod(inst);
          if (mop.method.isSynchronized()) {
            mop.spMethod = context.findOrCreateSpecializedVersion(mop.method);
            if (DEBUG)
              VM.sysWrite("Identified call " + inst + 
                  " for unsynchronization\n");
          }
        }
        break;
      case MONITORENTER_opcode:
        if (DEBUG) {
          VM.sysWrite("Removing " + inst);
        }
        if (!options.NO_CACHE_FLUSH) {
          inst.insertBefore(Empty.create(READ_CEILING));
        }
        OPT_DefUse.removeInstructionAndUpdateDU(inst);
        break;
      case MONITOREXIT_opcode:
        if (DEBUG) {
          VM.sysWrite("Removing " + inst);
        }
        if (!options.NO_CACHE_FLUSH) {
          inst.insertAfter(Empty.create(WRITE_FLOOR));
        }
        OPT_DefUse.removeInstructionAndUpdateDU(inst);
        break;
      default:
        // no action necessary
        break;
    }
  }
  /**
   * The register to replace
   */
  private OPT_Register reg; 
  /**
   * Controlling compiler options
   */
  private OPT_Options options;  
  /**
   * Singleton: a single context representing "specialize this method when
   * the invokee of this method is thread-local"
   */
  private static OPT_InvokeeThreadLocalContext context = 
      new OPT_InvokeeThreadLocalContext();
}



