/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Convert an IR object from MIR to final Machinecode
 *
 * @author Dave Grove
 * @author Stephen Fink
 */
final class OPT_ConvertMIRtoMC extends OPT_OptimizationPlanCompositeElement {

  /**
   * Create this phase element as a composite of other elements.
   */
  OPT_ConvertMIRtoMC() {
    super("Generate Machine Code", new OPT_OptimizationPlanElement[] {
       // Step 1: Final MIR Expansion
       new OPT_OptimizationPlanAtomicElement(new FinalMIRExpansionDriver()),
       // Step 2: Assembly and map generation.
       new OPT_OptimizationPlanAtomicElement(new AssemblerDriver())
       });
  }

  /**
   * A compiler phase that drives final MIR expansion.
   */
  private static final class FinalMIRExpansionDriver extends OPT_CompilerPhase {
    final String getName () {
      return "Final MIR Expansion";
    }
  
    final boolean printingEnabled (OPT_Options options, boolean before) {
      return !before && options.PRINT_FINAL_MIR;
    }
  
    // this class has no instance fields.
    final OPT_CompilerPhase newExecution (OPT_IR ir) {
      return this;
    }

    final void perform (OPT_IR ir) {
      OPT_Options options = ir.options;
      VM_Method method = ir.method;

      if (OPT_IR.SANITY_CHECK) {
        ir.verify("right before Final MIR Expansion", true);
      }

      int approxMachinecodeSize = OPT_FinalMIRExpansion.expand(ir);
      ir.MIRInfo.machinecode = new INSTRUCTION[approxMachinecodeSize];
    }
  }
  /**
   * A compiler phase that generates machine code instructions and maps.
   */
  private static final class AssemblerDriver extends OPT_CompilerPhase
    implements VM_Constants {

    final String getName () {
      return "Assembler Driver";
    }
  
    final boolean printingEnabled (OPT_Options options, boolean before) {
      //don't bother printing afterwards, PRINT_MACHINECODE handles that
      return before && options.DEBUG_CODEGEN;
    }
  
    // this class has no instance fields.
    final OPT_CompilerPhase newExecution (OPT_IR ir) {
      return this;
    }
  
    final void perform (OPT_IR ir) {
      OPT_Options options = ir.options;
      VM_Method method = ir.method;
  
      boolean shouldPrint =
        (options.PRINT_MACHINECODE) &&
        (!ir.options.hasMETHOD_TO_PRINT() ||
         ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString()));
      
      
      if (OPT_IR.SANITY_CHECK) {
        ir.verify("right before machine codegen", true);
      }
  
      //////////
      // STEP 2: Generate the machinecode array.
      // As part of the generation, the machinecode offset
      // of every instruction will be set by calling setmcOffset.
      //////////
      int codeLength = OPT_Assembler.generateCode(ir, shouldPrint);
  
      //////////
      // STEP 3: Generate all the mapping information 
      // associated with the machine code.
      //////////
      VM_OptCompilerInfo info = ir.MIRInfo.info;
  
      // 3a: Create the exception table
      info.createFinalExceptionTable(ir);
      // 3b: Create the primary machine code map
      info.createFinalMCMap(ir, codeLength);
  
      if (shouldPrint) {
        // print exception tables (if any)
        VM_OptExceptionTable etable = info.getExceptionTable();
        if (etable != null)
  	etable.printExceptionTable();
        OPT_Compiler.bottom("Final machine code", method);
      }
  
      if (VM.runningVM)
        VM_Memory.sync(VM_Magic.objectAsAddress(ir.MIRInfo.machinecode), 
  		     codeLength << LG_INSTRUCTION_WIDTH);
    }
  
    public void verify(OPT_IR ir) {
      /* Do nothing, IR invariants violated by final expansion*/
    }
  }
}
