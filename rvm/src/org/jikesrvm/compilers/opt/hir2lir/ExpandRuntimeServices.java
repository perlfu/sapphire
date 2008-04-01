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
package org.jikesrvm.compilers.opt.hir2lir;

import static org.jikesrvm.compilers.opt.driver.Constants.RUNTIME_SERVICES_BCI;
import static org.jikesrvm.compilers.opt.ir.Operators.ATHROW_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.CALL;
import static org.jikesrvm.compilers.opt.ir.Operators.GETFIELD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GETSTATIC_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ASTORE;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITORENTER_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITOREXIT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NEWARRAY_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NEWARRAY;
import static org.jikesrvm.compilers.opt.ir.Operators.NEWARRAY_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NEWOBJMULTIARRAY_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NEW_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NEW_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PUTFIELD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PUTSTATIC_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_MOVE;

import java.lang.reflect.Constructor;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Array;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.classloader.VM_FieldReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.Simple;
import org.jikesrvm.compilers.opt.controlflow.BranchOptimizations;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.inlining.InlineDecision;
import org.jikesrvm.compilers.opt.inlining.Inliner;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.Athrow;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.GetStatic;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.MonitorOp;
import org.jikesrvm.compilers.opt.ir.Multianewarray;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.New;
import org.jikesrvm.compilers.opt.ir.NewArray;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TypeOperand;
import org.jikesrvm.memorymanagers.mminterface.MM_Constants;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.runtime.VM_Entrypoints;

/**
 * As part of the expansion of HIR into LIR, this compile phase
 * replaces all HIR operators that are implemented as calls to
 * VM service routines with CALLs to those routines.
 * For some (common and performance critical) operators, we
 * may optionally inline expand the call (depending on the
 * the values of the relevant compiler options and/or VM_Controls).
 * This pass is also responsible for inserting write barriers
 * if we are using an allocator that requires them. Write barriers
 * are always inline expanded.
 */
public final class ExpandRuntimeServices extends CompilerPhase {

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor =
      getCompilerPhaseConstructor(ExpandRuntimeServices.class);

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  public boolean shouldPerform(OptOptions options) {
    return true;
  }

  public String getName() {
    return "Expand Runtime Services";
  }

  public void reportAdditionalStats() {
    VM.sysWrite("  ");
    VM.sysWrite(container.counter1 / container.counter2 * 100, 2);
    VM.sysWrite("% Infrequent RS calls");
  }

  /**
   * Given an HIR, expand operators that are implemented as calls to
   * runtime service methods. This method should be called as one of the
   * first steps in lowering HIR into LIR.
   *
   * @param ir  The HIR to expand
   */
  public void perform(IR ir) {
    ir.gc.resync(); // resync generation context -- yuck...

    Instruction next;
    for (Instruction inst = ir.firstInstructionInCodeOrder(); inst != null; inst = next) {
      next = inst.nextInstructionInCodeOrder();
      int opcode = inst.getOpcode();

      switch (opcode) {

        case NEW_opcode: {
          TypeOperand Type = New.getClearType(inst);
          VM_Class cls = (VM_Class) Type.getVMType();
          IntConstantOperand hasFinalizer = IRTools.IC(cls.hasFinalizer() ? 1 : 0);
          VM_Method callSite = inst.position.getMethod();
          IntConstantOperand allocator = IRTools.IC(MM_Interface.pickAllocator(cls, callSite));
          IntConstantOperand align = IRTools.IC(VM_ObjectModel.getAlignment(cls));
          IntConstantOperand offset = IRTools.IC(VM_ObjectModel.getOffsetForAlignment(cls));
          Operand tib = ConvertToLowLevelIR.getTIB(inst, ir, Type);
          if (VM.BuildForIA32 && VM.runningVM) {
            // shield BC2IR from address constants
            RegisterOperand tmp = ir.regpool.makeTemp(VM_TypeReference.TIB);
            inst.insertBefore(Move.create(REF_MOVE, tmp, tib));
            tib = tmp.copyRO();
          }
          IntConstantOperand site = IRTools.IC(MM_Interface.getAllocationSite(true));
          VM_Method target = VM_Entrypoints.resolvedNewScalarMethod;
          Call.mutate7(inst,
                       CALL,
                       New.getClearResult(inst),
                       IRTools.AC(target.getOffset()),
                       MethodOperand.STATIC(target),
                       IRTools.IC(cls.getInstanceSize()),
                       tib,
                       hasFinalizer,
                       allocator,
                       align,
                       offset,
                       site);
          next = inst.prevInstructionInCodeOrder();
          if (ir.options.INLINE_NEW) {
            if (inst.getBasicBlock().getInfrequent()) container.counter1++;
            container.counter2++;
            if (!ir.options.FREQ_FOCUS_EFFORT || !inst.getBasicBlock().getInfrequent()) {
              inline(inst, ir);
            }
          }
        }
        break;

        case NEW_UNRESOLVED_opcode: {
          int typeRefId = New.getType(inst).getTypeRef().getId();
          VM_Method target = VM_Entrypoints.unresolvedNewScalarMethod;
          IntConstantOperand site = IRTools.IC(MM_Interface.getAllocationSite(true));
          Call.mutate2(inst,
                       CALL,
                       New.getClearResult(inst),
                       IRTools.AC(target.getOffset()),
                       MethodOperand.STATIC(target),
                       IRTools.IC(typeRefId),
                       site);
        }
        break;

        case NEWARRAY_opcode: {
          TypeOperand Array = NewArray.getClearType(inst);
          VM_Array array = (VM_Array) Array.getVMType();
          Operand numberElements = NewArray.getClearSize(inst);
          boolean inline = numberElements instanceof IntConstantOperand;
          Operand width = IRTools.IC(array.getLogElementSize());
          Operand headerSize = IRTools.IC(VM_ObjectModel.computeArrayHeaderSize(array));
          VM_Method callSite = inst.position.getMethod();
          IntConstantOperand allocator = IRTools.IC(MM_Interface.pickAllocator(array, callSite));
          IntConstantOperand align = IRTools.IC(VM_ObjectModel.getAlignment(array));
          IntConstantOperand offset = IRTools.IC(VM_ObjectModel.getOffsetForAlignment(array));
          Operand tib = ConvertToLowLevelIR.getTIB(inst, ir, Array);
          if (VM.BuildForIA32 && VM.runningVM) {
            // shield BC2IR from address constants
            RegisterOperand tmp = ir.regpool.makeTemp(VM_TypeReference.TIB);
            inst.insertBefore(Move.create(REF_MOVE, tmp, tib));
            tib = tmp.copyRO();
          }
          IntConstantOperand site = IRTools.IC(MM_Interface.getAllocationSite(true));
          VM_Method target = VM_Entrypoints.resolvedNewArrayMethod;
          Call.mutate8(inst,
                       CALL,
                       NewArray.getClearResult(inst),
                       IRTools.AC(target.getOffset()),
                       MethodOperand.STATIC(target),
                       numberElements,
                       width,
                       headerSize,
                       tib,
                       allocator,
                       align,
                       offset,
                       site);
          next = inst.prevInstructionInCodeOrder();
          if (inline && ir.options.INLINE_NEW) {
            if (inst.getBasicBlock().getInfrequent()) container.counter1++;
            container.counter2++;
            if (!ir.options.FREQ_FOCUS_EFFORT || !inst.getBasicBlock().getInfrequent()) {
              inline(inst, ir);
            }
          }
        }
        break;

        case NEWARRAY_UNRESOLVED_opcode: {
          int typeRefId = NewArray.getType(inst).getTypeRef().getId();
          Operand numberElements = NewArray.getClearSize(inst);
          VM_Method target = VM_Entrypoints.unresolvedNewArrayMethod;
          IntConstantOperand site = IRTools.IC(MM_Interface.getAllocationSite(true));
          Call.mutate3(inst,
                       CALL,
                       NewArray.getClearResult(inst),
                       IRTools.AC(target.getOffset()),
                       MethodOperand.STATIC(target),
                       numberElements,
                       IRTools.IC(typeRefId),
                       site);
        }
        break;

        case NEWOBJMULTIARRAY_opcode: {
          int dimensions = Multianewarray.getNumberOfDimensions(inst);
          VM_Method callSite = inst.position.getMethod();
          int typeRefId = Multianewarray.getType(inst).getTypeRef().getId();
          if (dimensions == 2) {
            VM_Method target = VM_Entrypoints.optNew2DArrayMethod;
            Call.mutate4(inst,
                         CALL,
                         Multianewarray.getClearResult(inst),
                         IRTools.AC(target.getOffset()),
                         MethodOperand.STATIC(target),
                         IRTools.IC(callSite.getId()),
                         Multianewarray.getClearDimension(inst, 0),
                         Multianewarray.getClearDimension(inst, 1),
                         IRTools.IC(typeRefId));
          } else {
            // Step 1: Create an int array to hold the dimensions.
            TypeOperand dimArrayType = new TypeOperand(VM_Array.IntArray);
            RegisterOperand dimArray = ir.regpool.makeTemp(VM_TypeReference.IntArray);
            dimArray.setPreciseType();
            next =  NewArray.create(NEWARRAY, dimArray, dimArrayType, new IntConstantOperand(dimensions));
            inst.insertBefore(next);
            // Step 2: Assign the dimension values to dimArray
            for (int i = 0; i < dimensions; i++) {
              LocationOperand loc = new LocationOperand(VM_TypeReference.Int);
              inst.insertBefore(AStore.create(INT_ASTORE,
                                Multianewarray.getClearDimension(inst, i),
                                dimArray.copyD2U(),
                                IRTools.IC(i),
                                loc,
                                IRTools.TG()));
            }
            // Step 3. Plant call to VM_OptLinker.newArrayArray
            VM_Method target = VM_Entrypoints.optNewArrayArrayMethod;
            Call.mutate3(inst,
                         CALL,
                         Multianewarray.getClearResult(inst),
                         IRTools.AC(target.getOffset()),
                         MethodOperand.STATIC(target),
                         IRTools.IC(callSite.getId()),
                         dimArray.copyD2U(),
                         IRTools.IC(typeRefId));
          }
        }
        break;

        case ATHROW_opcode: {
          VM_Method target = VM_Entrypoints.athrowMethod;
          MethodOperand methodOp = MethodOperand.STATIC(target);
          methodOp.setIsNonReturningCall(true);   // Record the fact that this is a non-returning call.
          Call.mutate1(inst, CALL, null, IRTools.AC(target.getOffset()), methodOp, Athrow.getClearValue(inst));
        }
        break;

        case MONITORENTER_opcode: {
          if (ir.options.NO_SYNCHRO) {
            inst.remove();
          } else {
            Operand ref = MonitorOp.getClearRef(inst);
            VM_Type refType = ref.getType().peekType();
            if (refType != null && !refType.getThinLockOffset().isMax()) {
              VM_Method target = VM_Entrypoints.inlineLockMethod;
              Call.mutate2(inst,
                           CALL,
                           null,
                           IRTools.AC(target.getOffset()),
                           MethodOperand.STATIC(target),
                           MonitorOp.getClearGuard(inst),
                           ref,
                           IRTools.AC(refType.getThinLockOffset()));
              if (inst.getBasicBlock().getInfrequent()) container.counter1++;
              container.counter2++;
              if (!ir.options.FREQ_FOCUS_EFFORT || !inst.getBasicBlock().getInfrequent()) {
                inline(inst, ir);
              }
            } else {
              VM_Method target = VM_Entrypoints.lockMethod;
              Call.mutate1(inst,
                           CALL,
                           null,
                           IRTools.AC(target.getOffset()),
                           MethodOperand.STATIC(target),
                           MonitorOp.getClearGuard(inst),
                           ref);
            }
          }
          break;
        }

        case MONITOREXIT_opcode: {
          if (ir.options.NO_SYNCHRO) {
            inst.remove();
          } else {
            Operand ref = MonitorOp.getClearRef(inst);
            VM_Type refType = ref.getType().peekType();
            if (refType != null && !refType.getThinLockOffset().isMax()) {
              VM_Method target = VM_Entrypoints.inlineUnlockMethod;
              Call.mutate2(inst,
                           CALL,
                           null,
                           IRTools.AC(target.getOffset()),
                           MethodOperand.STATIC(target),
                           MonitorOp.getClearGuard(inst),
                           ref,
                           IRTools.AC(refType.getThinLockOffset()));
              if (inst.getBasicBlock().getInfrequent()) container.counter1++;
              container.counter2++;
              if (!ir.options.FREQ_FOCUS_EFFORT || !inst.getBasicBlock().getInfrequent()) {
                inline(inst, ir);
              }
            } else {
              VM_Method target = VM_Entrypoints.unlockMethod;
              Call.mutate1(inst,
                           CALL,
                           null,
                           IRTools.AC(target.getOffset()),
                           MethodOperand.STATIC(target),
                           MonitorOp.getClearGuard(inst),
                           ref);
            }
          }
        }
        break;

        case REF_ASTORE_opcode: {
          if (MM_Constants.NEEDS_WRITE_BARRIER) {
            VM_Method target = VM_Entrypoints.arrayStoreWriteBarrierMethod;
            Instruction wb =
                Call.create3(CALL,
                             null,
                             IRTools.AC(target.getOffset()),
                             MethodOperand.STATIC(target),
                             AStore.getClearGuard(inst),
                             AStore.getArray(inst).copy(),
                             AStore.getIndex(inst).copy(),
                             AStore.getValue(inst).copy());
            wb.bcIndex = RUNTIME_SERVICES_BCI;
            wb.position = inst.position;
            inst.replace(wb);
            next = wb.prevInstructionInCodeOrder();
            if (ir.options.INLINE_WRITE_BARRIER) {
              inline(wb, ir, true);
            }
          }
        }
        break;

        case REF_ALOAD_opcode: {
          if (MM_Constants.NEEDS_READ_BARRIER) {
            VM_Method target = VM_Entrypoints.arrayLoadReadBarrierMethod;
            Instruction rb =
              Call.create2(CALL,
                           ALoad.getClearResult(inst),
                           IRTools.AC(target.getOffset()),
                           MethodOperand.STATIC(target),
                           ALoad.getClearGuard(inst),
                           ALoad.getArray(inst).copy(),
                           ALoad.getIndex(inst).copy());
            rb.bcIndex = RUNTIME_SERVICES_BCI;
            rb.position = inst.position;
            inst.replace(rb);
            next = rb.prevInstructionInCodeOrder();
            inline(rb, ir, true);
          }
        }
        break;

        case PUTFIELD_opcode: {
          if (MM_Constants.NEEDS_WRITE_BARRIER) {
            LocationOperand loc = PutField.getLocation(inst);
            VM_FieldReference fieldRef = loc.getFieldRef();
            if (!fieldRef.getFieldContentsType().isPrimitiveType()) {
              VM_Field field = fieldRef.peekResolvedField();
              if (field == null || !field.isUntraced()) {
                VM_Method target = VM_Entrypoints.putfieldWriteBarrierMethod;
                Instruction wb =
                    Call.create4(CALL,
                                 null,
                                 IRTools.AC(target.getOffset()),
                                 MethodOperand.STATIC(target),
                                 PutField.getClearGuard(inst),
                                 PutField.getRef(inst).copy(),
                                 PutField.getOffset(inst).copy(),
                                 PutField.getValue(inst).copy(),
                                 IRTools.IC(fieldRef.getId()));
                wb.bcIndex = RUNTIME_SERVICES_BCI;
                wb.position = inst.position;
                inst.replace(wb);
                next = wb.prevInstructionInCodeOrder();
                if (ir.options.INLINE_WRITE_BARRIER) {
                  inline(wb, ir, true);
                }
              }
            }
          }
        }
        break;

        case GETFIELD_opcode: {
          if (MM_Constants.NEEDS_READ_BARRIER) {
            LocationOperand loc = GetField.getLocation(inst);
            VM_FieldReference fieldRef = loc.getFieldRef();
            if (GetField.getResult(inst).getType().isReferenceType()) {
              VM_Field field = fieldRef.peekResolvedField();
              if (field == null || !field.isUntraced()) {
                VM_Method target = VM_Entrypoints.getfieldReadBarrierMethod;
                Instruction rb =
                  Call.create3(CALL,
                               GetField.getClearResult(inst),
                               IRTools.AC(target.getOffset()),
                               MethodOperand.STATIC(target),
                               GetField.getClearGuard(inst),
                               GetField.getRef(inst).copy(),
                               GetField.getOffset(inst).copy(),
                               IRTools.IC(fieldRef.getId()));
                rb.bcIndex = RUNTIME_SERVICES_BCI;
                rb.position = inst.position;
                inst.replace(rb);
                next = rb.prevInstructionInCodeOrder();
                inline(rb, ir, true);
              }
            }
          }
        }
        break;

        case PUTSTATIC_opcode: {
          if (MM_Constants.NEEDS_PUTSTATIC_WRITE_BARRIER) {
            LocationOperand loc = PutStatic.getLocation(inst);
            VM_FieldReference field = loc.getFieldRef();
            if (!field.getFieldContentsType().isPrimitiveType()) {
              VM_Method target = VM_Entrypoints.putstaticWriteBarrierMethod;
              Instruction wb =
                  Call.create3(CALL,
                               null,
                               IRTools.AC(target.getOffset()),
                               MethodOperand.STATIC(target),
                               PutStatic.getOffset(inst).copy(),
                               PutStatic.getValue(inst).copy(),
                               IRTools.IC(field.getId()));
              wb.bcIndex = RUNTIME_SERVICES_BCI;
              wb.position = inst.position;
              inst.replace(wb);
              next = wb.prevInstructionInCodeOrder();
              if (ir.options.INLINE_WRITE_BARRIER) {
                inline(wb, ir, true);
              }
            }
          }
        }
        break;

        case GETSTATIC_opcode: {
          if (MM_Constants.NEEDS_GETSTATIC_READ_BARRIER) {
            LocationOperand loc = GetStatic.getLocation(inst);
            VM_FieldReference field = loc.getFieldRef();
            if (!field.getFieldContentsType().isPrimitiveType()) {
              VM_Method target = VM_Entrypoints.getstaticReadBarrierMethod;
              Instruction rb =
                  Call.create2(CALL,
                               GetStatic.getClearResult(inst),
                               IRTools.AC(target.getOffset()),
                               MethodOperand.STATIC(target),
                               GetStatic.getOffset(inst).copy(),
                               IRTools.IC(field.getId()));
              rb.bcIndex = RUNTIME_SERVICES_BCI;
              rb.position = inst.position;
              inst.replace(rb);
              next = rb.prevInstructionInCodeOrder();
              inline(rb, ir, true);
            }
          }
        }
        break;

        default:
          break;
      }
    }

    // If we actually inlined anything, clean up the mess
    if (didSomething) {
      branchOpts.perform(ir, true);
      _os.perform(ir);
    }
    // signal that we do not intend to use the gc in other phases anymore.
    ir.gc.close();
  }

  /**
   * Inline a call instruction
   */
  private void inline(Instruction inst, IR ir) {
    inline(inst, ir, false);
  }

  /**
   * Inline a call instruction
   */
  private void inline(Instruction inst, IR ir, boolean noCalleeExceptions) {
    // Save and restore inlining control state.
    // Some options have told us to inline this runtime service,
    // so we have to be sure to inline it "all the way" not
    // just 1 level.
    boolean savedInliningOption = ir.options.INLINE;
    boolean savedExceptionOption = ir.options.NO_CALLEE_EXCEPTIONS;
    ir.options.INLINE = true;
    ir.options.NO_CALLEE_EXCEPTIONS = noCalleeExceptions;
    boolean savedOsrGI = ir.options.OSR_GUARDED_INLINING;
    ir.options.OSR_GUARDED_INLINING = false;
    try {
      InlineDecision inlDec =
          InlineDecision.YES(Call.getMethod(inst).getTarget(), "Expansion of runtime service");
      Inliner.execute(inlDec, ir, inst);
    } finally {
      ir.options.INLINE = savedInliningOption;
      ir.options.NO_CALLEE_EXCEPTIONS = savedExceptionOption;
      ir.options.OSR_GUARDED_INLINING = savedOsrGI;
    }
    didSomething = true;
  }

  private final Simple _os = new Simple(1, false, false, false);
  private final BranchOptimizations branchOpts = new BranchOptimizations(-1, true, true);
  private boolean didSomething = false;

  //private final IntConstantOperand IRTools.IC(int x) { return IRTools.IRTools.IC(x); }
  //private final AddressConstantOperand IRTools.AC(Address x) { return IRTools.IRTools.AC(x); }
  //private final AddressConstantOperand IRTools.AC(Offset x) { return IRTools.IRTools.AC(x); }

}
