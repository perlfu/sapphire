/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Driver routine for register allocation
 *
 * @author Stephen Fink
 */
final class OPT_NewRegisterAllocator extends OPT_OptimizationPlanCompositeElement {

  OPT_NewRegisterAllocator() {
    super("Register Allocation", new OPT_OptimizationPlanElement[] {
      // 1. Prepare for the allocation
      new OPT_OptimizationPlanAtomicElement(new RegisterAllocPreparation()), 
      // 2. Perform the allocation, using the live information
      new OPT_OptimizationPlanAtomicElement(new OPT_NewLinearScan())
    });
  }
  
  final boolean shouldPerform(OPT_Options options) { return true; }
  final String getName() { return "RegAlloc"; }
  final boolean printingEnabled(OPT_Options options, boolean before) {
    return options.PRINT_REGALLOC;
  }

  private static class RegisterAllocPreparation extends OPT_CompilerPhase {
    final boolean shouldPerform (OPT_Options options) {
      return true;
    }

    final String getName () {
      return  "Register Allocation Preparation";
    }

    final boolean printingEnabled (OPT_Options options, boolean before) {
      return false;
    }

    /**
     * create the stack manager
     */
    final public void perform (OPT_IR ir) {

      ir.stackManager.prepare(ir);

      // This code assumes that we are running linear scan, if not 
      // we better know now
      if (VM.VerifyAssertions) {
	VM.assert(ir.options.useLinearScanRegAlloc());
      }
	
    }

  }
}
