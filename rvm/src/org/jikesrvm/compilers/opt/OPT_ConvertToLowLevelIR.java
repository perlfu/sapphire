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
package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;
import static org.jikesrvm.VM_Constants.LOG_BYTES_IN_ADDRESS;
import static org.jikesrvm.VM_Constants.LOG_BYTES_IN_INT;
import static org.jikesrvm.VM_Constants.NEEDS_DYNAMIC_LINK;
import static org.jikesrvm.VM_Constants.TIB_IMT_TIB_INDEX;
import static org.jikesrvm.VM_Constants.TIB_ITABLES_TIB_INDEX;
import org.jikesrvm.adaptive.VM_AosEntrypoints;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.classloader.VM_InterfaceInvocation;
import org.jikesrvm.classloader.VM_InterfaceMethodSignature;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.classloader.VM_TypeReference;
import static org.jikesrvm.compilers.opt.OPT_Constants.RUNTIME_SERVICES_BCI;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.BoundsCheck;
import org.jikesrvm.compilers.opt.ir.CacheOp;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.GetStatic;
import org.jikesrvm.compilers.opt.ir.Goto;
import org.jikesrvm.compilers.opt.ir.GuardedUnary;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.IfCmp2;
import org.jikesrvm.compilers.opt.ir.InlineGuard;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.LookupSwitch;
import org.jikesrvm.compilers.opt.ir.LowTableSwitch;
import org.jikesrvm.compilers.opt.ir.OPT_AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BranchOperand;
import org.jikesrvm.compilers.opt.ir.OPT_BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ConditionOperand;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_IRTools;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_LocationOperand;
import org.jikesrvm.compilers.opt.ir.OPT_MethodOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_Operator;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ARRAYLENGTH;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BOUNDS_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BYTE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BYTE_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BYTE_LOAD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BYTE_STORE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CALL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CALL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CHECKCAST_NOTNULL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CHECKCAST_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CHECKCAST_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_LOAD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_STORE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_LOAD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_STORE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GETFIELD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GETSTATIC_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GET_CLASS_OBJECT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GET_CLASS_TIB;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GET_OBJ_TIB;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GET_TYPE_FROM_TIB;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GOTO;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IG_CLASS_TEST_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IG_METHOD_TEST_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INSTANCEOF_NOTNULL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INSTANCEOF_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INSTANCEOF_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_2ADDRSigExt;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_2ADDRZerExt;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_ADD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_IFCMP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_IFCMP2;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_LOAD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_SHL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_STORE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_ZERO_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_LOAD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_STORE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_ZERO_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LOOKUPSWITCH;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LOOKUPSWITCH_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LOWTABLESWITCH;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.MUST_IMPLEMENT_INTERFACE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.OBJARRAY_STORE_CHECK_NOTNULL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.OBJARRAY_STORE_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PUTFIELD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PUTSTATIC_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_IFCMP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_LOAD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_STORE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.RESOLVE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.RESOLVE_MEMBER_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.SHORT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.SHORT_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.SHORT_LOAD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.SHORT_STORE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.SYSCALL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.TABLESWITCH_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.TRAP_IF;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.UBYTE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.UBYTE_LOAD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.USHORT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.USHORT_LOAD;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.OPT_TIBConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_TrapCodeOperand;
import org.jikesrvm.compilers.opt.ir.OPT_TypeOperand;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.Store;
import org.jikesrvm.compilers.opt.ir.TableSwitch;
import org.jikesrvm.compilers.opt.ir.TrapIf;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.ZeroCheck;
import org.jikesrvm.memorymanagers.mminterface.MM_Constants;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.runtime.VM_Magic;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Converts all remaining instructions with HIR-only operators into
 * an equivalent sequence of LIR operators.
 */
public abstract class OPT_ConvertToLowLevelIR extends OPT_IRTools {

  /**
   * We have slightly different ideas of what the LIR should look like
   * for IA32 and PowerPC.  The main difference is that for IA32
   * instead of bending over backwards in BURS to rediscover array
   * loads, (where we can use base + index*scale addressing modes),
   * we'll leave array loads in the LIR.
   */
  static final boolean LOWER_ARRAY_ACCESS = VM.BuildForPowerPC;
  /**
   * Plant virtual calls via the JTOC rather than from the tib of an
   * object when possible
   */
  static final boolean CALL_VIA_JTOC = false;

  /**
   * Converts the given HIR to LIR.
   *
   * @param ir IR to convert
   */
  static void convert(OPT_IR ir, OPT_Options options) {
    boolean didArrayStoreCheck = false;
    for (OPT_Instruction s = ir.firstInstructionInCodeOrder(); s != null; s = s.nextInstructionInCodeOrder()) {

      switch (s.getOpcode()) {
        case GETSTATIC_opcode: {
          OPT_LocationOperand loc = GetStatic.getClearLocation(s);
          OPT_RegisterOperand result = GetStatic.getClearResult(s);
          OPT_Operand address = ir.regpool.makeJTOCOp(ir, s);
          OPT_Operand offset = GetStatic.getClearOffset(s);
          Load.mutate(s, OPT_IRTools.getLoadOp(loc.getFieldRef(), true), result, address, offset, loc);
        }
        break;

        case PUTSTATIC_opcode: {
          OPT_LocationOperand loc = PutStatic.getClearLocation(s);
          OPT_Operand value = PutStatic.getClearValue(s);
          OPT_Operand address = ir.regpool.makeJTOCOp(ir, s);
          OPT_Operand offset = PutStatic.getClearOffset(s);
          Store.mutate(s, OPT_IRTools.getStoreOp(loc.getFieldRef(), true), value, address, offset, loc);
        }
        break;

        case PUTFIELD_opcode: {
          OPT_LocationOperand loc = PutField.getClearLocation(s);
          OPT_Operand value = PutField.getClearValue(s);
          OPT_Operand address = PutField.getClearRef(s);
          OPT_Operand offset = PutField.getClearOffset(s);
          Store.mutate(s,
                       OPT_IRTools.getStoreOp(loc.getFieldRef(), false),
                       value,
                       address,
                       offset,
                       loc,
                       PutField.getClearGuard(s));
        }
        break;

        case GETFIELD_opcode: {
          OPT_LocationOperand loc = GetField.getClearLocation(s);
          OPT_RegisterOperand result = GetField.getClearResult(s);
          OPT_Operand address = GetField.getClearRef(s);
          OPT_Operand offset = GetField.getClearOffset(s);
          Load.mutate(s,
                      OPT_IRTools.getLoadOp(loc.getFieldRef(), false),
                      result,
                      address,
                      offset,
                      loc,
                      GetField.getClearGuard(s));
        }
        break;

        case INT_ALOAD_opcode:
          doArrayLoad(s, ir, INT_LOAD, 2);
          break;

        case LONG_ALOAD_opcode:
          doArrayLoad(s, ir, LONG_LOAD, 3);
          break;

        case FLOAT_ALOAD_opcode:
          doArrayLoad(s, ir, FLOAT_LOAD, 2);
          break;

        case DOUBLE_ALOAD_opcode:
          doArrayLoad(s, ir, DOUBLE_LOAD, 3);
          break;

        case REF_ALOAD_opcode:
          doArrayLoad(s, ir, REF_LOAD, LOG_BYTES_IN_ADDRESS);
          break;

        case BYTE_ALOAD_opcode:
          doArrayLoad(s, ir, BYTE_LOAD, 0);
          break;

        case UBYTE_ALOAD_opcode:
          doArrayLoad(s, ir, UBYTE_LOAD, 0);
          break;

        case USHORT_ALOAD_opcode:
          doArrayLoad(s, ir, USHORT_LOAD, 1);
          break;

        case SHORT_ALOAD_opcode:
          doArrayLoad(s, ir, SHORT_LOAD, 1);
          break;

        case INT_ASTORE_opcode:
          doArrayStore(s, ir, INT_STORE, 2);
          break;

        case LONG_ASTORE_opcode:
          doArrayStore(s, ir, LONG_STORE, 3);
          break;

        case FLOAT_ASTORE_opcode:
          doArrayStore(s, ir, FLOAT_STORE, 2);
          break;

        case DOUBLE_ASTORE_opcode:
          doArrayStore(s, ir, DOUBLE_STORE, 3);
          break;

        case REF_ASTORE_opcode:
          doArrayStore(s, ir, REF_STORE, LOG_BYTES_IN_ADDRESS);
          break;

        case BYTE_ASTORE_opcode:
          doArrayStore(s, ir, BYTE_STORE, 0);
          break;

        case SHORT_ASTORE_opcode:
          doArrayStore(s, ir, SHORT_STORE, 1);
          break;

        case CALL_opcode:
          s = callHelper(s, ir);
          break;

        case SYSCALL_opcode:
          // If the SYSCALL is using a symbolic address, convert that to
          // a sequence of loads off the BootRecord to find the appropriate field.
          if (Call.getMethod(s) != null) {
            expandSysCallTarget(s, ir);
          }
          break;

        case TABLESWITCH_opcode:
          s = tableswitch(s, ir);
          break;

        case LOOKUPSWITCH_opcode:
          s = lookup(s, ir);
          break;

        case OBJARRAY_STORE_CHECK_opcode:
          s = OPT_DynamicTypeCheckExpansion.arrayStoreCheck(s, ir, true);
          didArrayStoreCheck = true;
          break;

        case OBJARRAY_STORE_CHECK_NOTNULL_opcode:
          s = OPT_DynamicTypeCheckExpansion.arrayStoreCheck(s, ir, false);
          didArrayStoreCheck = true;
          break;

        case CHECKCAST_opcode:
        case CHECKCAST_UNRESOLVED_opcode:
          s = OPT_DynamicTypeCheckExpansion.checkcast(s, ir);
          break;

        case CHECKCAST_NOTNULL_opcode:
          s = OPT_DynamicTypeCheckExpansion.checkcastNotNull(s, ir);
          break;

        case MUST_IMPLEMENT_INTERFACE_opcode:
          s = OPT_DynamicTypeCheckExpansion.mustImplementInterface(s, ir);
          break;

        case IG_CLASS_TEST_opcode:
          IfCmp.mutate(s,
                       REF_IFCMP,
                       ir.regpool.makeTempValidation(),
                       getTIB(s, ir, InlineGuard.getClearValue(s), InlineGuard.getClearGuard(s)),
                       getTIB(s, ir, InlineGuard.getGoal(s).asType()),
                       OPT_ConditionOperand.NOT_EQUAL(),
                       InlineGuard.getClearTarget(s),
                       InlineGuard.getClearBranchProfile(s));
          break;

        case IG_METHOD_TEST_opcode: {
          OPT_MethodOperand methOp = InlineGuard.getClearGoal(s).asMethod();
          OPT_Operand t1 = getTIB(s, ir, InlineGuard.getClearValue(s), InlineGuard.getClearGuard(s));
          OPT_Operand t2 = getTIB(s, ir, methOp.getTarget().getDeclaringClass());
          IfCmp.mutate(s,
                       REF_IFCMP,
                       ir.regpool.makeTempValidation(),
                       getInstanceMethod(s, ir, t1, methOp.getTarget()),
                       getInstanceMethod(s, ir, t2, methOp.getTarget()),
                       OPT_ConditionOperand.NOT_EQUAL(),
                       InlineGuard.getClearTarget(s),
                       InlineGuard.getClearBranchProfile(s));
          break;
        }

        case INSTANCEOF_opcode:
        case INSTANCEOF_UNRESOLVED_opcode:
          s = OPT_DynamicTypeCheckExpansion.instanceOf(s, ir);
          break;

        case INSTANCEOF_NOTNULL_opcode:
          s = OPT_DynamicTypeCheckExpansion.instanceOfNotNull(s, ir);
          break;

        case INT_ZERO_CHECK_opcode: {
          TrapIf.mutate(s,
                        TRAP_IF,
                        ZeroCheck.getClearGuardResult(s),
                        ZeroCheck.getClearValue(s),
                        IC(0),
                        OPT_ConditionOperand.EQUAL(),
                        OPT_TrapCodeOperand.DivByZero());
        }
        break;

        case LONG_ZERO_CHECK_opcode: {
          TrapIf.mutate(s,
                        TRAP_IF,
                        ZeroCheck.getClearGuardResult(s),
                        ZeroCheck.getClearValue(s),
                        LC(0),
                        OPT_ConditionOperand.EQUAL(),
                        OPT_TrapCodeOperand.DivByZero());
        }
        break;

        case BOUNDS_CHECK_opcode: {
          // get array_length from array_ref
          OPT_RegisterOperand array_length =
              InsertGuardedUnary(s,
                                 ir,
                                 ARRAYLENGTH,
                                 VM_TypeReference.Int,
                                 BoundsCheck.getClearRef(s),
                                 BoundsCheck.getClearGuard(s));
          //  In UN-signed comparison, a negative index will look like a very
          //  large positive number, greater than array length.
          //  Thus length LLT index is false iff 0 <= index <= length
          TrapIf.mutate(s,
                        TRAP_IF,
                        BoundsCheck.getClearGuardResult(s),
                        array_length.copyD2U(),
                        BoundsCheck.getClearIndex(s),
                        OPT_ConditionOperand.LOWER_EQUAL(),
                        OPT_TrapCodeOperand.ArrayBounds());
        }
        break;

        case GET_CLASS_OBJECT_opcode: {
          OPT_Operand TIB = getTIB(s, ir, (OPT_TypeOperand) Unary.getClearVal(s));
          OPT_RegisterOperand type = ir.regpool.makeTemp(VM_TypeReference.VM_Type);
          s.insertBefore(Unary.create(GET_TYPE_FROM_TIB, type, TIB));
          // Get the java.lang.Class object from the VM_Type object
          // TODO: Valid location operand?
          Load.mutate(s,
                      REF_LOAD,
                      Unary.getClearResult(s),
                      type.copyD2U(),
                      AC(VM_Entrypoints.classForTypeField.getOffset()),
                      null);
        }
        break;

        case RESOLVE_MEMBER_opcode:
          s = resolveMember(s, ir);
          break;

        default:
          break;
      }
    }
    // Eliminate possible redundant trap block from array store checks
    if (didArrayStoreCheck) {
      branchOpts.perform(ir, true);
    }
  }

  private static OPT_BranchOptimizations branchOpts = new OPT_BranchOptimizations(-1, true, true);

  /**
   * Expand a tableswitch.
   * @param s the instruction to expand
   * @param ir the containing IR
   * @return the last OPT_Instruction in the generated LIR sequence.
   */
  static OPT_Instruction tableswitch(OPT_Instruction s, OPT_IR ir) {

    OPT_Instruction s2;
    int lowLimit = TableSwitch.getLow(s).value;
    int highLimit = TableSwitch.getHigh(s).value;
    int number = highLimit - lowLimit + 1;
    if (VM.VerifyAssertions) {
      VM._assert(number > 0);    // also checks that there are < 2^31 targets
    }
    OPT_Operand val = TableSwitch.getClearValue(s);
    OPT_BranchOperand defaultLabel = TableSwitch.getClearDefault(s);
    if (number < 8) {           // convert into a lookupswitch
      OPT_Instruction l =
          LookupSwitch.create(LOOKUPSWITCH,
                              val,
                              null,
                              null,
                              defaultLabel,
                              TableSwitch.getClearDefaultBranchProfile(s),
                              number * 3);
      for (int i = 0; i < number; i++) {
        LookupSwitch.setMatch(l, i, IC(lowLimit + i));
        LookupSwitch.setTarget(l, i, TableSwitch.getClearTarget(s, i));
        LookupSwitch.setBranchProfile(l, i, TableSwitch.getClearBranchProfile(s, i));
      }
      s.insertAfter(l);
      return s.remove();
    }
    OPT_RegisterOperand reg = val.asRegister();
    OPT_BasicBlock BB1 = s.getBasicBlock();
    OPT_BasicBlock BB2 = BB1.splitNodeAt(s, ir);
    OPT_BasicBlock defaultBB = defaultLabel.target.getBasicBlock();

    /******* First basic block */
    OPT_RegisterOperand t;
    if (lowLimit != 0) {
      t = InsertBinary(s, ir, INT_ADD, VM_TypeReference.Int, reg, IC(-lowLimit));
    } else {
      t = reg.copyU2U();
    }
    OPT_BranchProfileOperand defaultProb = TableSwitch.getClearDefaultBranchProfile(s);
    s.replace(IfCmp.create(INT_IFCMP,
        ir.regpool.makeTempValidation(),
        t,
        IC(highLimit - lowLimit),
        OPT_ConditionOperand.HIGHER(),
        defaultLabel,
        defaultProb));
    // Reweight branches to account for the default branch going. If
    // the default probability was ALWAYS then when we recompute the
    // weight to be a proportion of the total number of branches.
    final boolean defaultIsAlways = defaultProb.takenProbability >= 1f;
    final float weight = defaultIsAlways ? 1f / number : 1f / (1f - defaultProb.takenProbability);

    /********** second Basic Block ******/
    s2 = LowTableSwitch.create(LOWTABLESWITCH, t.copyRO(), number * 2);
    boolean containsDefault = false;
    for (int i = 0; i < number; i++) {
      OPT_BranchOperand b = TableSwitch.getClearTarget(s, i);
      LowTableSwitch.setTarget(s2, i, b);
      OPT_BranchProfileOperand bp = TableSwitch.getClearBranchProfile(s, i);
      if (defaultIsAlways) {
        bp.takenProbability = weight;
      } else {
        bp.takenProbability *= weight;
      }
      LowTableSwitch.setBranchProfile(s2, i, bp);
      if (b.target == defaultLabel.target) {
        containsDefault = true;
      }
    }
    // Fixup the CFG and code order.
    BB1.insertOut(BB2);
    BB1.insertOut(defaultBB);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    if (!containsDefault) {
      BB2.deleteOut(defaultBB);
    }
    // Simplify a fringe case...
    // if all targets of the LOWTABLESWITCH are the same,
    // then just use a GOTO instead of the LOWTABLESWITCH.
    // This actually happens (very occasionally), and is easy to test for.
    if (BB2.getNumberOfNormalOut() == 1) {
      BB2.appendInstruction(Goto.create(GOTO, LowTableSwitch.getTarget(s2, 0)));
    } else {
      BB2.appendInstruction(s2);
    }
    // continue at next BB
    s = BB2.lastInstruction();

    return s;
  }

  /**
   * Expand a lookupswitch.
   * @param switchInstr  The instruction to expand
   * @param ir           The containing IR
   * @return the next {@link OPT_Instruction} after the generated LIR sequence.
   */
  static OPT_Instruction lookup(OPT_Instruction switchInstr, OPT_IR ir) {
    OPT_Instruction bbend = switchInstr.nextInstructionInCodeOrder();
    OPT_BasicBlock thisBB = bbend.getBasicBlock();
    OPT_BasicBlock nextBB = thisBB.nextBasicBlockInCodeOrder();
    // Blow away the old Normal ControlFlowGraph edges to prepare for new links
    thisBB.deleteNormalOut();
    switchInstr.remove();
    OPT_BranchOperand defTarget = LookupSwitch.getClearDefault(switchInstr);
    OPT_BasicBlock defaultBB = defTarget.target.getBasicBlock();
    int high = LookupSwitch.getNumberOfTargets(switchInstr) - 1;
    if (high < 0) {
      // no cases in switch; just jump to defaultBB
      thisBB.appendInstruction(Goto.create(GOTO, defTarget));
      thisBB.insertOut(defaultBB);
    } else {
      OPT_Operand match = LookupSwitch.getValue(switchInstr);
      if (match.isConstant()) {
        // switch on a constant
        int value = match.asIntConstant().value;
        int numMatches = LookupSwitch.getNumberOfMatches(switchInstr);
        OPT_BranchOperand target = LookupSwitch.getDefault(switchInstr);
        for (int i = 0; i < numMatches; i++) {
          if (value == LookupSwitch.getMatch(switchInstr, i).value) {
            target = LookupSwitch.getTarget(switchInstr, i);
            break;
          }
        }
        thisBB.appendInstruction(Goto.create(GOTO, target));
        thisBB.insertOut(target.target.getBasicBlock());
      } else {
        OPT_RegisterOperand reg = match.asRegister();

        // If you're not already at the end of the code order
        if (nextBB != null) {
          ir.cfg.breakCodeOrder(thisBB, nextBB);
        }
        // generate the binary search tree into thisBB
        OPT_BasicBlock lastNewBB =
            _lookupswitchHelper(switchInstr, reg, defaultBB, ir, thisBB, 0, high, Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (nextBB != null) {
          ir.cfg.linkInCodeOrder(lastNewBB, nextBB);
        }
      }
    }

    // skip all the instrs just inserted by _lookupswitchHelper
    if (nextBB != null) {
      return nextBB.firstInstruction();
    } else {
      return thisBB.lastInstruction();
    }
  }

  /**
   * Helper function to generate the binary search tree for
   * a lookupswitch bytecode
   *
   * @param switchInstr the lookupswitch instruction
   * @param defaultBB the basic block of the default case
   * @param ir the ir object
   * @param curBlock the basic block to insert instructions into
   * @param reg the RegisterOperand that contains the valued being switched on
   * @param low the low index of cases (operands of switchInstr)
   * @param high the high index of cases (operands of switchInstr)
   * @param min
   * @param max
   * @return the last basic block created
   */
  private static OPT_BasicBlock _lookupswitchHelper(OPT_Instruction switchInstr, OPT_RegisterOperand reg,
                                                    OPT_BasicBlock defaultBB, OPT_IR ir, OPT_BasicBlock curBlock,
                                                    int low, int high, int min, int max) {
    if (VM.VerifyAssertions) {
      VM._assert(low <= high, "broken control logic in _lookupswitchHelper");
    }

    int middle = (low + high) >> 1;             // find middle

    // The following are used below to store the computed branch
    // probabilities for the branches that are created to implement
    // the binary search.  Used only if basic block frequencies available
    float lessProb = 0.0f;
    float greaterProb = 0.0f;
    float equalProb = 0.0f;
    float sum = 0.0f;

    // Sum the probabilities for all targets < middle
    for (int i = low; i < middle; i++) {
      lessProb += LookupSwitch.getBranchProfile(switchInstr, i).takenProbability;
    }

    // Sum the probabilities for all targets > middle
    for (int i = middle + 1; i <= high; i++) {
      greaterProb += LookupSwitch.getBranchProfile(switchInstr, i).takenProbability;
    }
    equalProb = LookupSwitch.getBranchProfile(switchInstr, middle).takenProbability;

    // The default case is a bit of a kludge.  We know the total
    // probability of executing the default case, but we have no
    // idea which paths are taken to get there.  For now, we'll
    // assume that all paths that went to default were because the
    // value was less than the smallest switch value.  This ensures
    // that all basic block appearing in the switch will have the
    // correct weights (but the blocks in the binary switch
    // generated may not).
    if (low == 0) {
      lessProb += LookupSwitch.getDefaultBranchProfile(switchInstr).takenProbability;
    }

    // Now normalize them so they are relative to the sum of the
    // branches being considered in this piece of the subtree
    sum = lessProb + equalProb + greaterProb;
    if (sum > 0) {  // check for divide by zero
      lessProb /= sum;
      equalProb /= sum;
      greaterProb /= sum;
    }

    OPT_IntConstantOperand val = LookupSwitch.getClearMatch(switchInstr, middle);
    int value = val.value;
    OPT_BasicBlock greaterBlock = middle == high ? defaultBB : curBlock.createSubBlock(0, ir);
    OPT_BasicBlock lesserBlock = low == middle ? defaultBB : curBlock.createSubBlock(0, ir);
    // Generate this level of tests
    OPT_BranchOperand branch = LookupSwitch.getClearTarget(switchInstr, middle);
    OPT_BasicBlock branchBB = branch.target.getBasicBlock();
    curBlock.insertOut(branchBB);
    if (low != high) {
      if (value == min) {
        curBlock.appendInstruction(IfCmp.create(INT_IFCMP,
            ir.regpool.makeTempValidation(),
            reg.copy(),
            val,
            OPT_ConditionOperand.EQUAL(),
            branchBB.makeJumpTarget(),
            new OPT_BranchProfileOperand(equalProb)));
      } else {

        // To compute the probability of the second compare, the first
        // probability must be removed since the second branch is
        // considered only if the first fails.
        float secondIfProb = 0.0f;
        sum = equalProb + greaterProb;
        if (sum > 0) {
          // if divide by zero, leave as is
          secondIfProb = equalProb / sum;
        }

        curBlock.appendInstruction(IfCmp2.create(INT_IFCMP2,
            ir.regpool.makeTempValidation(),
            reg.copy(),
            val,
            OPT_ConditionOperand.LESS(),
            lesserBlock.makeJumpTarget(),
            new OPT_BranchProfileOperand(lessProb),
            OPT_ConditionOperand.EQUAL(),
            branchBB.makeJumpTarget(),
            new OPT_BranchProfileOperand(secondIfProb)));
        curBlock.insertOut(lesserBlock);
      }
    } else {      // Base case: middle was the only case left to consider
      if (min == max) {
        curBlock.appendInstruction(Goto.create(GOTO, branch));
        curBlock.insertOut(branchBB);
      } else {
        curBlock.appendInstruction(IfCmp.create(INT_IFCMP,
            ir.regpool.makeTempValidation(),
            reg.copy(),
            val,
            OPT_ConditionOperand.EQUAL(),
            branchBB.makeJumpTarget(),
            new OPT_BranchProfileOperand(equalProb)));
        OPT_BasicBlock newBlock = curBlock.createSubBlock(0, ir);
        curBlock.insertOut(newBlock);
        ir.cfg.linkInCodeOrder(curBlock, newBlock);
        curBlock = newBlock;
        curBlock.appendInstruction(defaultBB.makeGOTO());
        curBlock.insertOut(defaultBB);
      }
    }
    // Generate sublevels as needed and splice together instr & bblist
    if (middle < high) {
      curBlock.insertOut(greaterBlock);
      ir.cfg.linkInCodeOrder(curBlock, greaterBlock);
      curBlock = _lookupswitchHelper(switchInstr, reg, defaultBB, ir, greaterBlock, middle + 1, high, value + 1, max);
    }
    if (low < middle) {
      ir.cfg.linkInCodeOrder(curBlock, lesserBlock);
      curBlock = _lookupswitchHelper(switchInstr, reg, defaultBB, ir, lesserBlock, low, middle - 1, min, value - 1);
    }
    return curBlock;
  }

  /**
   * Expand an array load.
   * @param s the instruction to expand
   * @param ir the containing IR
   * @param op the load operator to use
   * @param logwidth the log base 2 of the element type's size
   */
  public static void doArrayLoad(OPT_Instruction s, OPT_IR ir, OPT_Operator op, int logwidth) {
    if (LOWER_ARRAY_ACCESS) {
      OPT_RegisterOperand result = ALoad.getClearResult(s);
      OPT_Operand array = ALoad.getClearArray(s);
      OPT_Operand index = ALoad.getClearIndex(s);
      OPT_Operand offset;
      OPT_LocationOperand loc = ALoad.getClearLocation(s);
      if (index instanceof OPT_IntConstantOperand) {  // constant propagation
        offset = AC(Address.fromIntZeroExtend(((OPT_IntConstantOperand) index).value << logwidth));
      } else {
        if (logwidth != 0) {
          offset = InsertBinary(s, ir, INT_SHL, VM_TypeReference.Int, index, IC(logwidth));
          offset = InsertUnary(s, ir, INT_2ADDRZerExt, VM_TypeReference.Offset, offset.copy());
        } else {
          offset = InsertUnary(s, ir, INT_2ADDRZerExt, VM_TypeReference.Offset, index);
        }
      }
      Load.mutate(s, op, result, array, offset, loc, ALoad.getClearGuard(s));
    }
  }

  /**
   * Expand an array store.
   * @param s the instruction to expand
   * @param ir the containing IR
   * @param op the store operator to use
   * @param logwidth the log base 2 of the element type's size
   */
  public static void doArrayStore(OPT_Instruction s, OPT_IR ir, OPT_Operator op, int logwidth) {
    if (LOWER_ARRAY_ACCESS) {
      OPT_Operand value = AStore.getClearValue(s);
      OPT_Operand array = AStore.getClearArray(s);
      OPT_Operand index = AStore.getClearIndex(s);
      OPT_Operand offset;
      OPT_LocationOperand loc = AStore.getClearLocation(s);
      if (index instanceof OPT_IntConstantOperand) {// constant propagation
        offset = AC(Address.fromIntZeroExtend(((OPT_IntConstantOperand) index).value << logwidth));
      } else {
        if (logwidth != 0) {
          offset = InsertBinary(s, ir, INT_SHL, VM_TypeReference.Int, index, IC(logwidth));
          offset = InsertUnary(s, ir, INT_2ADDRZerExt, VM_TypeReference.Offset, offset.copy());
        } else {
          offset = InsertUnary(s, ir, INT_2ADDRZerExt, VM_TypeReference.Offset, index);
        }
      }
      Store.mutate(s, op, value, array, offset, loc, AStore.getClearGuard(s));
    }
  }

  /**
   * Helper method for call expansion.
   * @param v the call instruction
   * @param ir the containing IR
   * @return the last expanded instruction
   */
  static OPT_Instruction callHelper(OPT_Instruction v, OPT_IR ir) {
    if (!Call.hasMethod(v)) {
      if (VM.VerifyAssertions) VM._assert(Call.getAddress(v) instanceof OPT_RegisterOperand);
      return v; // nothing to do....very low level call to address already in the register.
    }

    OPT_MethodOperand methOp = Call.getMethod(v);

    // Handle recursive invocations.
    if (methOp.hasPreciseTarget() && methOp.getTarget() == ir.method) {
      Call.setAddress(v, new OPT_BranchOperand(ir.firstInstructionInCodeOrder()));
      return v;
    }

    /* RRB 100500 */
    // generate direct call to specialized method if the method operand
    // has been marked as a specialized call.
    if (VM.runningVM) {
      OPT_SpecializedMethod spMethod = methOp.spMethod;
      if (spMethod != null) {
        int smid = spMethod.getSpecializedMethodIndex();
        Call.setAddress(v, getSpecialMethod(v, ir, smid));
        return v;
      }
    }

    // Used mainly (only?) by OSR
    if (methOp.hasDesignatedTarget()) {
      Call.setAddress(v, InsertLoadOffsetJTOC(v, ir, REF_LOAD, VM_TypeReference.CodeArray, methOp.jtocOffset));
      return v;
    }

    if (methOp.isStatic()) {
      if (VM.VerifyAssertions) VM._assert(Call.hasAddress(v));
      Call.setAddress(v, InsertLoadOffsetJTOC(v, ir, REF_LOAD, VM_TypeReference.CodeArray, Call.getClearAddress(v)));
    } else if (methOp.isVirtual()) {
      if (VM.VerifyAssertions) VM._assert(Call.hasAddress(v));
      if (CALL_VIA_JTOC && methOp.hasPreciseTarget()) {
        // Call to precise type can go via JTOC
        VM_Method target = methOp.getTarget();
        Call.setAddress(v,
                        InsertLoadOffsetJTOC(v,
                                             ir,
                                             REF_LOAD,
                                             VM_TypeReference.CodeArray,
                                             target.findOrCreateJtocOffset()));
      } else {
        OPT_Operand tib = getTIB(v, ir, Call.getParam(v, 0).copy(), Call.getGuard(v).copy());
        Call.setAddress(v,
                        InsertLoadOffset(v,
                                         ir,
                                         REF_LOAD,
                                         VM_TypeReference.CodeArray,
                                         tib,
                                         Call.getClearAddress(v),
                                         null,
                                         TG()));
      }
    } else if (methOp.isSpecial()) {
      VM_Method target = methOp.getTarget();
      if (target == null || target.isObjectInitializer() || target.isStatic()) {
        // target == null => we are calling an unresolved <init> method.
        Call.setAddress(v, InsertLoadOffsetJTOC(v, ir, REF_LOAD, VM_TypeReference.CodeArray, Call.getClearAddress(v)));
      } else {
        if (CALL_VIA_JTOC) {
          Call.setAddress(v,
                          InsertLoadOffsetJTOC(v,
                                               ir,
                                               REF_LOAD,
                                               VM_TypeReference.CodeArray,
                                               target.findOrCreateJtocOffset()));
        } else {
          // invoking a virtual method; do it via TIB of target's declaring class.
          OPT_Operand tib = getTIB(v, ir, target.getDeclaringClass());
          Call.setAddress(v,
                          InsertLoadOffset(v,
                                           ir,
                                           REF_LOAD,
                                           VM_TypeReference.CodeArray,
                                           tib,
                                           Call.getClearAddress(v),
                                           null,
                                           TG()));
        }
      }
    } else {
      if (VM.VerifyAssertions) VM._assert(methOp.isInterface());
      if (VM.VerifyAssertions) VM._assert(!Call.hasAddress(v));
      if (VM.BuildForIMTInterfaceInvocation) {
        // SEE ALSO: OPT_FinalMIRExpansion (for hidden parameter)
        OPT_Operand RHStib = getTIB(v, ir, Call.getParam(v, 0).copy(), Call.getGuard(v).copy());
        VM_InterfaceMethodSignature sig = VM_InterfaceMethodSignature.findOrCreate(methOp.getMemberRef());
        Offset offset = sig.getIMTOffset();
        OPT_RegisterOperand address = null;
        if (VM.BuildForEmbeddedIMT) {
          address = InsertLoadOffset(v, ir, REF_LOAD, VM_TypeReference.CodeArray, RHStib.copy(), offset);
        } else {
          OPT_RegisterOperand IMT =
              InsertLoadOffset(v,
                               ir,
                               REF_LOAD,
                               VM_TypeReference.JavaLangObjectArray,
                               RHStib.copy(),
                               Offset.fromIntZeroExtend(TIB_IMT_TIB_INDEX << LOG_BYTES_IN_ADDRESS));
          address = InsertLoadOffset(v, ir, REF_LOAD, VM_TypeReference.CodeArray, IMT.copyD2U(), offset);

        }
        Call.setAddress(v, address);
      } else if (VM
          .BuildForITableInterfaceInvocation &&
                                             VM
                                                 .DirectlyIndexedITables &&
                                                                         methOp.hasTarget() &&
                                                                         methOp.getTarget().getDeclaringClass().isResolved()) {
        VM_Class I = methOp.getTarget().getDeclaringClass();
        OPT_Operand RHStib = getTIB(v, ir, Call.getParam(v, 0).copy(), Call.getGuard(v).copy());
        OPT_RegisterOperand iTables =
            InsertLoadOffset(v,
                             ir,
                             REF_LOAD,
                             VM_TypeReference.JavaLangObjectArray,
                             RHStib.copy(),
                             Offset.fromIntZeroExtend(TIB_ITABLES_TIB_INDEX << LOG_BYTES_IN_ADDRESS));
        OPT_RegisterOperand iTable =
            InsertLoadOffset(v,
                             ir,
                             REF_LOAD,
                             VM_TypeReference.JavaLangObjectArray,
                             iTables.copyD2U(),
                             Offset.fromIntZeroExtend(I.getInterfaceId() << LOG_BYTES_IN_ADDRESS));
        OPT_RegisterOperand address =
            InsertLoadOffset(v,
                             ir,
                             REF_LOAD,
                             VM_TypeReference.CodeArray,
                             iTable.copyD2U(),
                             Offset.fromIntZeroExtend(VM_InterfaceInvocation.getITableIndex(I,
                                                                                            methOp.getMemberRef().getName(),
                                                                                            methOp.getMemberRef().getDescriptor()) <<
                                                                                                                                   LOG_BYTES_IN_ADDRESS));
        Call.setAddress(v, address);
      } else {
        int itableIndex = -1;
        if (VM.BuildForITableInterfaceInvocation && methOp.hasTarget()) {
          VM_Class I = methOp.getTarget().getDeclaringClass();
          // search ITable variant
          itableIndex =
              VM_InterfaceInvocation.getITableIndex(I,
                                                    methOp.getMemberRef().getName(),
                                                    methOp.getMemberRef().getDescriptor());
        }
        if (itableIndex == -1) {
          // itable index is not known at compile-time.
          // call "invokeinterface" to resolve the object and method id
          // into a method address
          OPT_RegisterOperand realAddrReg = ir.regpool.makeTemp(VM_TypeReference.CodeArray);
          VM_Method target = VM_Entrypoints.invokeInterfaceMethod;
          OPT_Instruction vp =
              Call.create2(CALL,
                           realAddrReg,
                           AC(target.getOffset()),
                           OPT_MethodOperand.STATIC(target),
                           Call.getParam(v, 0).asRegister().copyU2U(),
                           IC(methOp.getMemberRef().getId()));
          vp.position = v.position;
          vp.bcIndex = RUNTIME_SERVICES_BCI;
          v.insertBefore(vp);
          callHelper(vp, ir);
          Call.setAddress(v, realAddrReg.copyD2U());
          return v;
        } else {
          // itable index is known at compile-time.
          // call "findITable" to resolve object + interface id into
          // itable address
          OPT_RegisterOperand iTable = ir.regpool.makeTemp(VM_TypeReference.JavaLangObjectArray);
          OPT_Operand RHStib = getTIB(v, ir, Call.getParam(v, 0).copy(), Call.getGuard(v).copy());
          VM_Method target = VM_Entrypoints.findItableMethod;
          OPT_Instruction fi =
              Call.create2(CALL,
                           iTable,
                           AC(target.getOffset()),
                           OPT_MethodOperand.STATIC(target),
                           RHStib,
                           IC(methOp.getTarget().getDeclaringClass().getInterfaceId()));
          fi.position = v.position;
          fi.bcIndex = RUNTIME_SERVICES_BCI;
          v.insertBefore(fi);
          callHelper(fi, ir);
          OPT_RegisterOperand address =
              InsertLoadOffset(v,
                               ir,
                               REF_LOAD,
                               VM_TypeReference.CodeArray,
                               iTable.copyD2U(),
                               Offset.fromIntZeroExtend(itableIndex << LOG_BYTES_IN_ADDRESS));
          Call.setAddress(v, address);
          return v;
        }
      }
    }
    return v;
  }

  /**
   * Generate the code to resolve a member (field/method) reference.
   * @param s the RESOLVE_MEMBER instruction to expand
   * @param ir the containing ir object
   * @return the last expanded instruction
   */
  private static OPT_Instruction resolveMember(OPT_Instruction s, OPT_IR ir) {
    OPT_Operand memberOp = Unary.getClearVal(s);
    OPT_RegisterOperand offset = Unary.getClearResult(s);
    int dictId;
    if (memberOp instanceof OPT_LocationOperand) {
      dictId = ((OPT_LocationOperand) memberOp).getFieldRef().getId();
    } else {
      dictId = ((OPT_MethodOperand) memberOp).getMemberRef().getId();
    }

    OPT_BranchProfileOperand bp = OPT_BranchProfileOperand.never();
    OPT_BasicBlock predBB = s.getBasicBlock();
    OPT_BasicBlock succBB = predBB.splitNodeAt(s.getPrev(), ir);
    OPT_BasicBlock testBB = predBB.createSubBlock(s.bcIndex, ir, 1f - bp.takenProbability);
    OPT_BasicBlock resolveBB = predBB.createSubBlock(s.bcIndex, ir, bp.takenProbability);
    s.remove();

    // Get the offset from the appropriate VM_ClassLoader array
    // and check to see if it is valid
    OPT_RegisterOperand offsetTable = getStatic(testBB.lastInstruction(), ir, VM_Entrypoints.memberOffsetsField);
    testBB.appendInstruction(Load.create(INT_LOAD,
                                         offset.copyRO(),
                                         offsetTable,
                                         AC(Offset.fromIntZeroExtend(dictId << LOG_BYTES_IN_INT)),
                                         new OPT_LocationOperand(VM_TypeReference.Int),
                                         TG()));
    testBB.appendInstruction(Unary.create(INT_2ADDRSigExt, offset, offset.copy()));
    testBB.appendInstruction(IfCmp.create(REF_IFCMP,
        ir.regpool.makeTempValidation(),
        offset.copy(),
        AC(Address.fromIntSignExtend(NEEDS_DYNAMIC_LINK)),
        OPT_ConditionOperand.EQUAL(),
        resolveBB.makeJumpTarget(),
        bp));

    // Handle the offset being invalid
    resolveBB.appendInstruction(CacheOp.mutate(s, RESOLVE, memberOp));
    resolveBB.appendInstruction(testBB.makeGOTO());

    // Put together the CFG links & code order
    predBB.insertOut(testBB);
    ir.cfg.linkInCodeOrder(predBB, testBB);
    testBB.insertOut(succBB);
    testBB.insertOut(resolveBB);
    ir.cfg.linkInCodeOrder(testBB, succBB);
    resolveBB.insertOut(testBB);              // backedge
    ir.cfg.addLastInCodeOrder(resolveBB);  // stick resolution code in outer space.
    return testBB.lastInstruction();
  }

  /**
   * Insert a binary instruction before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param o1 the first operand
   * @param o2 the second operand
   * @return the result operand of the inserted instruction
   */
  public static OPT_RegisterOperand InsertBinary(OPT_Instruction s, OPT_IR ir, OPT_Operator operator,
                                                 VM_TypeReference type, OPT_Operand o1, OPT_Operand o2) {
    OPT_RegisterOperand t = ir.regpool.makeTemp(type);
    s.insertBefore(Binary.create(operator, t, o1, o2));
    return t.copyD2U();
  }

  /**
   * Insert a unary instruction before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param o1 the operand
   * @return the result operand of the inserted instruction
   */
  static OPT_RegisterOperand InsertUnary(OPT_Instruction s, OPT_IR ir, OPT_Operator operator, VM_TypeReference type,
                                         OPT_Operand o1) {
    OPT_RegisterOperand t = ir.regpool.makeTemp(type);
    s.insertBefore(Unary.create(operator, t, o1));
    return t.copyD2U();
  }

  /**
   * Insert a guarded unary instruction before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param o1 the operand
   * @param guard the guard operand
   * @return the result operand of the inserted instruction
   */
  static OPT_RegisterOperand InsertGuardedUnary(OPT_Instruction s, OPT_IR ir, OPT_Operator operator,
                                                VM_TypeReference type, OPT_Operand o1, OPT_Operand guard) {
    OPT_RegisterOperand t = ir.regpool.makeTemp(type);
    s.insertBefore(GuardedUnary.create(operator, t, o1, guard));
    return t.copyD2U();
  }

  /**
   * Insert a load off the JTOC before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param offset the offset to load at
   * @return the result operand of the inserted instruction
   */
  static OPT_RegisterOperand InsertLoadOffsetJTOC(OPT_Instruction s, OPT_IR ir, OPT_Operator operator,
                                                  VM_TypeReference type, Offset offset) {
    return InsertLoadOffset(s,
                            ir,
                            operator,
                            type,
                            ir.regpool.makeJTOCOp(ir, s),
                            AC(offset),
                            new OPT_LocationOperand(offset),
                            null);
  }

  /**
   * Insert a load off the JTOC before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param offset the offset to load at
   * @return the result operand of the inserted instruction
   */
  static OPT_RegisterOperand InsertLoadOffsetJTOC(OPT_Instruction s, OPT_IR ir, OPT_Operator operator,
                                                  VM_TypeReference type, OPT_Operand offset) {
    return InsertLoadOffset(s, ir, operator, type, ir.regpool.makeJTOCOp(ir, s), offset, null, null);
  }

  /**
   * Insert a load off before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param reg2 the base to load from
   * @param offset the offset to load at
   * @return the result operand of the inserted instruction
   */
  static OPT_RegisterOperand InsertLoadOffset(OPT_Instruction s, OPT_IR ir, OPT_Operator operator,
                                              VM_TypeReference type, OPT_Operand reg2, Offset offset) {
    return InsertLoadOffset(s, ir, operator, type, reg2, offset, null, null);
  }

  /**
   * Insert a load off before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param reg2 the base to load from
   * @param offset the offset to load at
   * @param guard the guard operand
   * @return the result operand of the inserted instruction
   */
  static OPT_RegisterOperand InsertLoadOffset(OPT_Instruction s, OPT_IR ir, OPT_Operator operator,
                                              VM_TypeReference type, OPT_Operand reg2, Offset offset,
                                              OPT_Operand guard) {
    return InsertLoadOffset(s, ir, operator, type, reg2, offset, null, guard);
  }

  /**
   * Insert a load off before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param reg2 the base to load from
   * @param offset the offset to load at
   * @param loc the location operand
   * @param guard the guard operand
   * @return the result operand of the inserted instruction
   */
  static OPT_RegisterOperand InsertLoadOffset(OPT_Instruction s, OPT_IR ir, OPT_Operator operator,
                                              VM_TypeReference type, OPT_Operand reg2, Offset offset,
                                              OPT_LocationOperand loc, OPT_Operand guard) {
    return InsertLoadOffset(s, ir, operator, type, reg2, AC(offset), loc, guard);
  }

  /**
   * Insert a load off before s in the instruction stream.
   * @param s the instruction to insert before
   * @param ir the containing IR
   * @param operator the operator to insert
   * @param type the type of the result
   * @param reg2 the base to load from
   * @param offset the offset to load at
   * @param loc the location operand
   * @param guard the guard operand
   * @return the result operand of the inserted instruction
   */
  static OPT_RegisterOperand InsertLoadOffset(OPT_Instruction s, OPT_IR ir, OPT_Operator operator,
                                              VM_TypeReference type, OPT_Operand reg2, OPT_Operand offset,
                                              OPT_LocationOperand loc, OPT_Operand guard) {
    OPT_RegisterOperand regTarget = ir.regpool.makeTemp(type);
    OPT_Instruction s2 = Load.create(operator, regTarget, reg2, offset, loc, guard);
    s.insertBefore(s2);
    return regTarget.copyD2U();
  }

  /** get the tib from the object pointer to by obj */
  static OPT_Operand getTIB(OPT_Instruction s, OPT_IR ir, OPT_Operand obj, OPT_Operand guard) {
    if (obj.isObjectConstant()) {
      // NB Constant types must already be resolved
      try {
        VM_Type type = obj.getType().resolve();
        return new OPT_TIBConstantOperand(type);
      } catch (NoClassDefFoundError e) {
        if (VM.runningVM) throw e;
        // Class not found during bootstrap due to chasing a class
        // only valid in the bootstrap JVM
      }
    }
    OPT_RegisterOperand res = ir.regpool.makeTemp(VM_TypeReference.JavaLangObjectArray);
    OPT_Instruction s2 = GuardedUnary.create(GET_OBJ_TIB, res, obj, guard);
    s.insertBefore(s2);
    return res.copyD2U();
  }

  /** get the class tib for type */
  static OPT_Operand getTIB(OPT_Instruction s, OPT_IR ir, VM_Type type) {
    return new OPT_TIBConstantOperand(type);
    //return getTIB(s, ir, new OPT_TypeOperand(type));
  }

  /** get the class tib for type */
  static OPT_Operand getTIB(OPT_Instruction s, OPT_IR ir, OPT_TypeOperand type) {
    VM_Type t = type.getVMType();
    if (VM.BuildForIA32 && !MM_Constants.MOVES_TIBS && VM.runningVM && t != null && t.isResolved()) {
      Address addr = VM_Magic.objectAsAddress(t.getTypeInformationBlock());
      return new OPT_AddressConstantOperand(addr);
    } else if (!t.isResolved()) {
      OPT_RegisterOperand res = ir.regpool.makeTemp(VM_TypeReference.JavaLangObjectArray);
      s.insertBefore(Unary.create(GET_CLASS_TIB, res, type));
      return res.copyD2U();
    } else {
      return new OPT_TIBConstantOperand(t);
    }
  }

  /**
   * Get an instance method from a TIB
   */
  static OPT_RegisterOperand getInstanceMethod(OPT_Instruction s, OPT_IR ir, OPT_Operand tib, VM_Method method) {
    return InsertLoadOffset(s, ir, REF_LOAD, VM_TypeReference.CodeArray, tib, method.getOffset());
  }

  /**
   * Load an instance field.
   * @param s
   * @param ir
   * @param obj
   * @param field
   */
  public static OPT_RegisterOperand getField(OPT_Instruction s, OPT_IR ir, OPT_RegisterOperand obj, VM_Field field) {
    return getField(s, ir, obj, field, null);
  }

  /**
   * Load an instance field.
   * @param s
   * @param ir
   * @param obj
   * @param field
   * @param guard
   */
  static OPT_RegisterOperand getField(OPT_Instruction s, OPT_IR ir, OPT_RegisterOperand obj, VM_Field field,
                                      OPT_Operand guard) {
    return InsertLoadOffset(s,
                            ir,
                            OPT_IRTools.getLoadOp(field.getType(), field.isStatic()),
                            field.getType(),
                            obj,
                            field.getOffset(),
                            new OPT_LocationOperand(field),
                            guard);
  }

  /*  RRB 100500 */
  /**
   * support for direct call to specialized method.
   */
  static OPT_RegisterOperand getSpecialMethod(OPT_Instruction s, OPT_IR ir, int smid) {
    //  First, get the pointer to the JTOC offset pointing to the
    //  specialized Method table
    OPT_RegisterOperand reg =
        InsertLoadOffsetJTOC(s,
                             ir,
                             REF_LOAD,
                             VM_TypeReference.JavaLangObjectArray,
                             VM_AosEntrypoints.specializedMethodsField.getOffset());
    OPT_RegisterOperand instr =
        InsertLoadOffset(s,
                         ir,
                         REF_LOAD,
                         VM_TypeReference.CodeArray,
                         reg,
                         Offset.fromIntZeroExtend(smid << LOG_BYTES_IN_INT));
    return instr;
  }

  /**
   * Expand symbolic SysCall target into a chain of loads from the bootrecord to
   * the desired target address.
   */
  public static void expandSysCallTarget(OPT_Instruction s, OPT_IR ir) {
    OPT_MethodOperand sysM = Call.getMethod(s);
    OPT_RegisterOperand t1 = getStatic(s, ir, VM_Entrypoints.the_boot_recordField);
    VM_Field target = sysM.getMemberRef().asFieldReference().resolve();
    OPT_Operand ip = getField(s, ir, t1, target);
    Call.setAddress(s, ip);
  }

  /**
   * Load a static field.
   * @param s
   * @param ir
   * @param field
   */
  public static OPT_RegisterOperand getStatic(OPT_Instruction s, OPT_IR ir, VM_Field field) {
    return InsertLoadOffsetJTOC(s,
                                ir,
                                OPT_IRTools.getLoadOp(field.getType(), field.isStatic()),
                                field.getType(),
                                field.getOffset());
  }
}
