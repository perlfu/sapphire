/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.Enumeration;
import  java.util.NoSuchElementException;
import instructionFormats.*;

/**
 * This class translates from bytecode to HIR.
 * <p>
 * The only public entry point is OPT_BC2IR.generateHIR.
 * generateHIR is passed an argument OPT_GenerationContext.
 * The context is assumed to be "empty" but "initialized." Invoking
 * generateHIR on a context results in it being "filled in" with the HIR
 * for the method (and for any inlined methods) as specified by the
 * state of the context.
 * <p>
 * The basic idea is to abstractly interpret the bytecode stream,
 * translating it into a register-based IR along the way.  At each program
 * point BC2IR has an abstract stack and an abstract local variable array.
 * Based on this, and on the bytecode, it can generate instructions.
 * It also does a number of forward flow-sensitive dataflow analyses and
 * optimistic optimizations in the process. There's lots of details in
 * John Whaley's master thesis from MIT.  However, one needs to be careful
 * because this code has substantial diverged from the system described in 
 * his thesis.
 * Some optimizations/features described in Johns's thesis are not implemented
 * here. Some optimizations/features implemented here are not described 
 * in John's thesis.
 * In particular this code takes a different approach to JSRs (inlining them),
 * and has more advanced and effective implementation of the inlining 
 * transformation. <p>
 * 
 * @author Jong-Deok Choi
 * @author Stephen Fink
 * @author Dave Grove
 * @author Igor Pechtchanski
 * @author Mauricio Serrano
 * @author John Whaley
 *
 * @see OPT_IRGenOptions
 * @see OPT_GenerationContext
 * @see OPT_ConvertBCtoHIR
 */
final class OPT_BC2IR implements OPT_IRGenOptions, 
				 OPT_Operators, 
				 VM_BytecodeConstants, 
				 OPT_Constants {
  /**
   * Dummy slot.
   * Used to deal with the fact the longs/doubles take
   * two words of stack space/local space to represent.
   * This field needs to be accessed by several of the IR classes,
   * but is not intended to be referenced by general client code.
   */
  static final DummyStackSlot DUMMY = new DummyStackSlot();

  /**
   * Generate HIR as specified by the argument OPT_GenerationContext.
   * As a result of calling this method, the cfg field of the generation 
   * context is populated with basic blocks and instructions.  
   * Additionally, other fields of the generation context will be modified 
   * to summarize what happened during IR generation.
   * <p>
   * This is the only external entry point to BC2IR.
   * <p>
   * Note: most clients should be calling methods in
   * OPT_ConvertBCtoHIR or in OPT_Inliner rather than invoking
   * OPT_BC2IR.generateHIR directly.
   *
   * @param context the generation context
   */
  public static void generateHIR(OPT_GenerationContext context) {
    new OPT_BC2IR(context).generateHIR();
  }

  //////////////////////////////////////////
  // vvv Implementation details below vvv //
  //////////////////////////////////////////
  /**
   * The generation context.
   */
  private OPT_GenerationContext gc;

  /**
   * Bytecodes for the method being generated.
   */
  private OPT_BytecodeInfo bcInfo;

  // Fields to support generation of instructions/blocks
  /**
   * The set of BasicBlockLEs we are generating
   */
  private BBSet blocks;

  /**
   * Bytecode index of current instruction.
   */
  private int instrIndex;

  /**
   * Last instruction generated (for ELIM_COPY_LOCALS)
   */
  private OPT_Instruction lastInstr;

  /**
   * Does basic block end here?
   */
  private boolean endOfBasicBlock;

  /**
   * Do we fall through to the next basic block?
   */
  private boolean fallThrough;

  /**
   * Current BBLE.
   */
  private BasicBlockLE currentBBLE;

  /**
   * Current simulated stack state.
   */
  private OperandStack stack;

  /**
   * Current state of local variables.
   */
  private OPT_Operand[] _localState;

  /**
   * Index of next basic block.
   */
  private int runoff;
  
  //////////
  // End of field declarations
  //////////

  // Prevent external instantiation
  private OPT_BC2IR() {}

  /**
   * Construct the BC2IR object for the generation context.
   * After the constructor completes, we're ready to start generating
   * HIR from bytecode 0 of context.method.
   * 
   * @param context the context to generate HIR into
   */
  private OPT_BC2IR(OPT_GenerationContext context) {
    gc = context;
    bcInfo = new OPT_BytecodeInfo(context.method);
    // initialize the local state from context.arguments
    _localState = new OPT_Operand[context.method.getLocalWords()];
    for (int argIdx = 0, localIdx = 0; argIdx < context.arguments.length;) {
      VM_Type argType = context.arguments[argIdx].getType();
      _localState[localIdx++] = context.arguments[argIdx++];
      if (argType.isLongType() || argType.isDoubleType()) {
        _localState[localIdx++] = DUMMY;
      }
    }
    // Initialize simulated stack.
    stack = new OperandStack(context.method.getOperandWords());
    // Initialize BBSet.
    blocks = new BBSet(context, bcInfo, _localState);
    // Finish preparing to generate from bytecode 0
    currentBBLE = blocks.getEntry();
    gc.prologue.insertOut(currentBBLE.block);
    if (DBG_CFG) 
      db("Added CFG edge from "+gc.prologue+" to "+currentBBLE.block);
    runoff = currentBBLE.max;
  }

  /**
   * Main generation loop.
   */
  private void generateHIR() {
    // Constructor initialized generation state to start 
    // generating from bytecode 0, so get the ball rolling.
    if (DBG_BB) db("bbl: " + printBlocks());
    generateFrom(0);
    // While there are more blocks that need it, pick one and generate it.
    for (currentBBLE = blocks.getNextEmptyBlock(currentBBLE); 
	 currentBBLE != null; 
	 currentBBLE = blocks.getNextEmptyBlock(currentBBLE)) {
      // Found a block. Set the generation state appropriately.
      currentBBLE.clearSelfRegen();
      runoff = Math.min(blocks.getNextBlockBytecodeIndex(currentBBLE), 
			currentBBLE.max);
      if (currentBBLE.stackState == null) {
        stack.clear();
      } else {
        stack = currentBBLE.stackState.copy();
      }
      _localState = currentBBLE.copyLocalState();
      if (DBG_BB) db("bbl: " + printBlocks());
      // Generate it!
      generateFrom(currentBBLE.low);
    }
    // Construct initial code order, commit to recursive inlines, 
    // insert any synthetic blocks.
    if (DBG_BB) db("doing final pass over basic blocks: " + printBlocks());
    blocks.finalPass();
  }

  /**
   * Generate instructions for a basic block.
   * May discover other basic blocks that need to be generated along the way.
   *
   * @param fromIndex bytecode index to start from
   */
  private void generateFrom(int fromIndex) {
    if (DBG_BB) {
      db("generating code into " + currentBBLE + " with runoff " + runoff);
    }
    currentBBLE.setGenerated();
    endOfBasicBlock = fallThrough = false;
    lastInstr = null;
    bcInfo.setInstruction(fromIndex);
    while (true) {
      // Must keep currentBBLE.high up-to-date in case we try to jump into 
      // the middle of the block we're currently generating.  Simply updating 
      // high once endsBasicBlock is true doesn't enable us to catch this case.
      currentBBLE.high = instrIndex = bcInfo.bcIndex;
      int code = bcInfo.getNextInstruction();
      if (DBG_BCPARSE) {
        db("parsing " + instrIndex + " : 0x" + Integer.toHexString(code));
      }
      OPT_Instruction s = null;
      switch (code) {
      case JBC_nop:
	break;

      case JBC_aconst_null:
	push(new OPT_NullConstantOperand());
	break;

      case JBC_iconst_m1:case JBC_iconst_0:case JBC_iconst_1:
      case JBC_iconst_2:case JBC_iconst_3:case JBC_iconst_4:
      case JBC_iconst_5:
	push(new OPT_IntConstantOperand(code - JBC_iconst_0));
	break;

      case JBC_lconst_0:case JBC_lconst_1:
	pushDual(new OPT_LongConstantOperand(code - JBC_lconst_0));
	break;

      case JBC_fconst_0:
	push(new OPT_FloatConstantOperand(0.f));
	break;

      case JBC_fconst_1:
	push(new OPT_FloatConstantOperand(1.f));
	break;

      case JBC_fconst_2:
	push(new OPT_FloatConstantOperand(2.f));
	break;

      case JBC_dconst_0:
	pushDual(new OPT_DoubleConstantOperand(0.));
	break;

      case JBC_dconst_1:
	pushDual(new OPT_DoubleConstantOperand(1.));
	break;

      case JBC_bipush:
	push(new OPT_IntConstantOperand(bcInfo.getByteValue()));
	break;
	
      case JBC_sipush:
	push(new OPT_IntConstantOperand(bcInfo.getShortValue()));
	break;

      case JBC_ldc:
	push(bcInfo.getConstantOperand(code));
	break;

      case JBC_ldc_w:
	push(bcInfo.getConstantOperand(code));
	break;

      case JBC_ldc2_w:
	pushDual(bcInfo.getConstantOperand(code));
	break;

      case JBC_iload:
	s = do_iload(bcInfo.getLocalNumber());
	break;

      case JBC_lload:
	s = do_lload(bcInfo.getLocalNumber());
	break;

      case JBC_fload:
	s = do_fload(bcInfo.getLocalNumber());
	break;

      case JBC_dload:
	s = do_dload(bcInfo.getLocalNumber());
	break;

      case JBC_aload:
	s = do_aload(bcInfo.getLocalNumber());
	break;

      case JBC_iload_0:case JBC_iload_1:case JBC_iload_2:case JBC_iload_3:
	s = do_iload(code - JBC_iload_0);
	break;

      case JBC_lload_0:case JBC_lload_1:case JBC_lload_2:case JBC_lload_3:
	s = do_lload(code - JBC_lload_0);
	break;
      
      case JBC_fload_0:case JBC_fload_1:case JBC_fload_2:case JBC_fload_3:
	s = do_fload(code - JBC_fload_0);
	break;
      
      case JBC_dload_0:case JBC_dload_1:case JBC_dload_2:case JBC_dload_3:
	s = do_dload(code - JBC_dload_0);
	break;

      case JBC_aload_0:case JBC_aload_1:case JBC_aload_2:case JBC_aload_3:
	s = do_aload(code - JBC_aload_0);
	break;

      case JBC_iaload:
	{
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  if (VM.VerifyAssertions)
	    assertIsType(ref, OPT_ClassLoaderProxy.IntArrayType);
	  s = _aloadHelper(INT_ALOAD, ref, index, VM_Type.IntType);
	}
	break;

      case JBC_laload:
	{
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  if (VM.VerifyAssertions)
	    assertIsType(ref, OPT_ClassLoaderProxy.LongArrayType);
	  s = _aloadHelper(LONG_ALOAD, ref, index, VM_Type.LongType);
	}
	break;

      case JBC_faload:
	{
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  if (VM.VerifyAssertions)
	    assertIsType(ref, OPT_ClassLoaderProxy.FloatArrayType);
	  s = _aloadHelper(FLOAT_ALOAD, ref, index, VM_Type.FloatType);
	}
	break;
	  
      case JBC_daload:
	{
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  if (VM.VerifyAssertions)
	    assertIsType(ref, OPT_ClassLoaderProxy.DoubleArrayType);
	  s = _aloadHelper(DOUBLE_ALOAD, ref, index, VM_Type.DoubleType);
	}
	break;

      case JBC_aaload:
	{
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  VM_Type type = getRefTypeOf(ref).asArray().getElementType();
	  if (VM.VerifyAssertions) VM.assert(type.isReferenceType());
	  s = _aloadHelper(REF_ALOAD, ref, index, type);
	}
	break;

      case JBC_baload:
	{
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  VM_Type type = getArrayTypeOf(ref);
	  if (VM.VerifyAssertions) {
	    VM.assert(type == OPT_ClassLoaderProxy.ByteArrayType || 
		      type == OPT_ClassLoaderProxy.BooleanArrayType);
	  }
	  if (type == OPT_ClassLoaderProxy.ByteArrayType)
	    s = _aloadHelper(BYTE_ALOAD, ref, index, VM_Type.ByteType);
	  else 
	    s = _aloadHelper(UBYTE_ALOAD, ref, index, VM_Type.BooleanType);
	}
	break;

      case JBC_caload:
	{
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  if (VM.VerifyAssertions)
	    assertIsType(ref, OPT_ClassLoaderProxy.CharArrayType);
	  s = _aloadHelper(USHORT_ALOAD, ref, index, VM_Type.CharType);
	}
	break;
	
      case JBC_saload:
	{
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  if (VM.VerifyAssertions)
	    assertIsType(ref, OPT_ClassLoaderProxy.ShortArrayType);
	  s = _aloadHelper(SHORT_ALOAD, ref, index, VM_Type.ShortType);
	}
	break;

      case JBC_istore:
	s = do_store(bcInfo.getLocalNumber(), popInt());
	break;

      case JBC_lstore:
	s = do_store(bcInfo.getLocalNumber(), popLong());
	break;

      case JBC_fstore:
	s = do_store(bcInfo.getLocalNumber(), popFloat());
	break;

      case JBC_dstore:
	s = do_store(bcInfo.getLocalNumber(), popDouble());
	break;

      case JBC_astore:
	s = do_astore(bcInfo.getLocalNumber());
	break;

      case JBC_istore_0:case JBC_istore_1:case JBC_istore_2:case JBC_istore_3:
	s = do_store(code - JBC_istore_0, popInt());
	break;

      case JBC_lstore_0:case JBC_lstore_1:case JBC_lstore_2:case JBC_lstore_3:
	s = do_store(code - JBC_lstore_0, popLong());
	break;

      case JBC_fstore_0:case JBC_fstore_1:case JBC_fstore_2:case JBC_fstore_3:
	s = do_store(code - JBC_fstore_0, popFloat());
	break;

      case JBC_dstore_0:case JBC_dstore_1:case JBC_dstore_2:case JBC_dstore_3:
	s = do_store(code - JBC_dstore_0, popDouble());
	break;

      case JBC_astore_0:case JBC_astore_1:case JBC_astore_2:case JBC_astore_3:
	s = do_astore(code - JBC_astore_0);
	break;

      case JBC_iastore:
	{
	  OPT_Operand val = popInt();
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  if (VM.VerifyAssertions)
	    assertIsType(ref, OPT_ClassLoaderProxy.IntArrayType);
	  s = AStore.create(INT_ASTORE, val, ref, index,
			    new OPT_LocationOperand(VM_Type.IntType),
			    getCurrentGuard());
	}
	break;

      case JBC_lastore:
	{
	  OPT_Operand val = popLong();
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  if (VM.VerifyAssertions)
	    assertIsType(ref, OPT_ClassLoaderProxy.LongArrayType);
	  s = AStore.create(LONG_ASTORE, val, ref, index, 
			    new OPT_LocationOperand(VM_Type.LongType),
			    getCurrentGuard());
	}
	break;

      case JBC_fastore:
	{
	  OPT_Operand val = popFloat();
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  if (VM.VerifyAssertions)
	    assertIsType(ref, OPT_ClassLoaderProxy.FloatArrayType);
	  s = AStore.create(FLOAT_ASTORE, val, ref, index, 
			    new OPT_LocationOperand(VM_Type.FloatType),
			    getCurrentGuard());
	}
	break;

      case JBC_dastore:
	{
	  OPT_Operand val = popDouble();
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  if (VM.VerifyAssertions)
	    assertIsType(ref, OPT_ClassLoaderProxy.DoubleArrayType);
	  s = AStore.create(DOUBLE_ASTORE, val, ref, index, 
			    new OPT_LocationOperand(VM_Type.DoubleType),
			    getCurrentGuard());
	}
	break;

      case JBC_aastore:
	{
	  OPT_Operand val = pop();
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  VM_Type type = getRefTypeOf(ref).asArray().getElementType();
	  if (VM.VerifyAssertions) VM.assert(type.isReferenceType());
	  if (do_CheckStore(ref, val, type))
	    break;
	  s = AStore.create(REF_ASTORE, val, ref, index,
			    new OPT_LocationOperand(type),
			    getCurrentGuard());
	  if (VM_Collector.NEEDS_WRITE_BARRIER)
	    rectifyStateWithErrorHandler();
	}
	break;

      case JBC_bastore:
	{
	  OPT_Operand val = popInt();
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  VM_Type type = getArrayTypeOf(ref);
	  if (VM.VerifyAssertions) {
	    VM.assert(type == OPT_ClassLoaderProxy.ByteArrayType || 
		      type == OPT_ClassLoaderProxy.BooleanArrayType);
	  }
	  if (type == OPT_ClassLoaderProxy.ByteArrayType)
	    type = VM_Type.ByteType;
	  else 
	    type = VM_Type.BooleanType;
	  s = AStore.create(BYTE_ASTORE, val, ref, index,
			    new OPT_LocationOperand(type),
			    getCurrentGuard());
	}
	break;

      case JBC_castore:
	{
	  OPT_Operand val = popInt();
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  if (VM.VerifyAssertions)
	    assertIsType(ref, OPT_ClassLoaderProxy.CharArrayType);
	  s = AStore.create(SHORT_ASTORE, val, ref, index,
			    new OPT_LocationOperand(VM_Type.CharType),
			    getCurrentGuard());
	}
	break;

      case JBC_sastore:
	{
	  OPT_Operand val = popInt();
	  OPT_Operand index = popInt();
	  OPT_Operand ref = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(ref) || do_BoundsCheck(ref, index))
	    break;
	  if (VM.VerifyAssertions)
	    assertIsType(ref, OPT_ClassLoaderProxy.ShortArrayType);
	  s = AStore.create(SHORT_ASTORE, val, ref, index,
			    new OPT_LocationOperand(VM_Type.ShortType),
			    getCurrentGuard());
	}
	break;

      case JBC_pop:
	stack.pop();
	break;

      case JBC_pop2:
	stack.pop2();
	break;

      case JBC_dup:
	{
	  OPT_Operand op1 = stack.pop();
	  stack.push(op1);
	  s = pushCopy(op1);
	}
	break;

      case JBC_dup_x1:
	{
	  OPT_Operand op1 = stack.pop();
	  OPT_Operand op2 = stack.pop();
	  stack.push(op1);
	  stack.push(op2);
	  s = pushCopy(op1);
	}
	break;

      case JBC_dup_x2:
	{
	  OPT_Operand op1 = stack.pop();
	  OPT_Operand op2 = stack.pop();
	  OPT_Operand op3 = stack.pop();
	  stack.push(op1);
	  stack.push(op3);
	  stack.push(op2);
	  s = pushCopy(op1);
	}
	break;

      case JBC_dup2:
	{
	  OPT_Operand op1 = stack.pop();
	  OPT_Operand op2 = stack.pop();
	  stack.push(op2);
	  stack.push(op1);
	  s = pushCopy(op2);
	  if (s != null) {
	    appendInstruction(s);
	    s = null;
	  }
	  s = pushCopy(op1);
	}
	break;

      case JBC_dup2_x1:
	{
	  OPT_Operand op1 = stack.pop();
	  OPT_Operand op2 = stack.pop();
	  OPT_Operand op3 = stack.pop();
	  stack.push(op2);
	  stack.push(op1);
	  stack.push(op3);
	  s = pushCopy(op2);
	  if (s != null) {
	    appendInstruction(s);
	    s = null;
	  }
	  s = pushCopy(op1);
	}
	break;

      case JBC_dup2_x2:
	{
	  OPT_Operand op1 = stack.pop();
	  OPT_Operand op2 = stack.pop();
	  OPT_Operand op3 = stack.pop();
	  OPT_Operand op4 = stack.pop();
	  stack.push(op2);
	  stack.push(op1);
	  stack.push(op4);
	  stack.push(op3);
	  s = pushCopy(op2);
	  if (s != null) {
	    appendInstruction(s);
	    s = null;
	  }
	  s = pushCopy(op1);
	}
	break;

      case JBC_swap:
	{
	  OPT_Operand op1 = stack.pop();
	  OPT_Operand op2 = stack.pop();
	  stack.push(op1);
	  stack.push(op2);
	}
	break;

      case JBC_iadd:
	{
	  OPT_Operand op2 = popInt();
	  OPT_Operand op1 = popInt();
	  s = _binaryHelper(INT_ADD, op1, op2, VM_Type.IntType);
	}
	break;

      case JBC_ladd:
	{
	  OPT_Operand op2 = popLong();
	  OPT_Operand op1 = popLong();
	  s = _binaryDualHelper(LONG_ADD, op1, op2, VM_Type.LongType);
	}
	break;

      case JBC_fadd:
	{
	  OPT_Operand op2 = popFloat();
	  OPT_Operand op1 = popFloat();
	  s = _binaryHelper(FLOAT_ADD, op1, op2, VM_Type.FloatType);
	}
	break;

      case JBC_dadd:
	{
	  OPT_Operand op2 = popDouble();
	  OPT_Operand op1 = popDouble();
	  s = _binaryDualHelper(DOUBLE_ADD, op1, op2, VM_Type.DoubleType);
	}
	break;

      case JBC_isub:
	{
	  OPT_Operand op2 = popInt();
	  OPT_Operand op1 = popInt();
	  s = _binaryHelper(INT_SUB, op1, op2, VM_Type.IntType);
	}
	break;

      case JBC_lsub:
	{
	  OPT_Operand op2 = popLong();
	  OPT_Operand op1 = popLong();
	  s = _binaryDualHelper(LONG_SUB, op1, op2, VM_Type.LongType);
	}
	break;

      case JBC_fsub:
	{
	  OPT_Operand op2 = popFloat();
	  OPT_Operand op1 = popFloat();
	  s = _binaryHelper(FLOAT_SUB, op1, op2, VM_Type.FloatType);
	}
	break;

      case JBC_dsub:
	{
	  OPT_Operand op2 = popDouble();
	  OPT_Operand op1 = popDouble();
	  s = _binaryDualHelper(DOUBLE_SUB, op1, op2, VM_Type.DoubleType);
	}
	break;

      case JBC_imul:
	{
	  OPT_Operand op2 = popInt();
	  OPT_Operand op1 = popInt();
	  s = _binaryHelper(INT_MUL, op1, op2, VM_Type.IntType);
	}
	break;

      case JBC_lmul:
	{
	  OPT_Operand op2 = popLong();
	  OPT_Operand op1 = popLong();
	  s = _binaryDualHelper(LONG_MUL, op1, op2, VM_Type.LongType);
	}
	break;

      case JBC_fmul:
	{
	  OPT_Operand op2 = popFloat();
	  OPT_Operand op1 = popFloat();
	  s = _binaryHelper(FLOAT_MUL, op1, op2, VM_Type.FloatType);
	}
	break;

      case JBC_dmul:
	{
	  OPT_Operand op2 = popDouble();
	  OPT_Operand op1 = popDouble();
	  s = _binaryDualHelper(DOUBLE_MUL, op1, op2, VM_Type.DoubleType);
	}
	break;

      case JBC_idiv:
	{
	  clearCurrentGuard();
	  OPT_Operand op2 = popInt();
	  OPT_Operand op1 = popInt();
	  if (do_IntZeroCheck(op2))
	    break;
	  s = _guardedBinaryHelper(INT_DIV, op1, op2, getCurrentGuard(), 
				   VM_Type.IntType);
	}
	break;

      case JBC_ldiv:
	{
	  clearCurrentGuard();
	  OPT_Operand op2 = popLong();
	  OPT_Operand op1 = popLong();
	  if (do_LongZeroCheck(op2))
	    break;
	  s = _guardedBinaryDualHelper(LONG_DIV, op1, op2, getCurrentGuard(), 
				       VM_Type.LongType);
	}
	break;

      case JBC_fdiv:
	{
	  OPT_Operand op2 = popFloat();
	  OPT_Operand op1 = popFloat();
	  s = _binaryHelper(FLOAT_DIV, op1, op2, VM_Type.FloatType);
	}
	break;

      case JBC_ddiv:
	{
	  OPT_Operand op2 = popDouble();
	  OPT_Operand op1 = popDouble();
	  s = _binaryDualHelper(DOUBLE_DIV, op1, op2, VM_Type.DoubleType);
	}
	break;

      case JBC_irem:
	{
	  clearCurrentGuard();
	  OPT_Operand op2 = popInt();
	  OPT_Operand op1 = popInt();
	  if (do_IntZeroCheck(op2))
	    break;
	  s = _guardedBinaryHelper(INT_REM, op1, op2, getCurrentGuard(), 
				   VM_Type.IntType);
	}
	break;

      case JBC_lrem:
	{
	  clearCurrentGuard();
	  OPT_Operand op2 = popLong();
	  OPT_Operand op1 = popLong();
	  if (do_LongZeroCheck(op2))
	    break;
	  s = _guardedBinaryDualHelper(LONG_REM, op1, op2, getCurrentGuard(), 
				       VM_Type.LongType);
	}
	break;

      case JBC_frem:
	{
	  OPT_Operand op2 = popFloat();
	  OPT_Operand op1 = popFloat();
	  s = _binaryHelper(FLOAT_REM, op1, op2, VM_Type.FloatType);
	}
	break;

      case JBC_drem:
	{
	  OPT_Operand op2 = popDouble();
	  OPT_Operand op1 = popDouble();
	  s = _binaryDualHelper(DOUBLE_REM, op1, op2, VM_Type.DoubleType);
	}
	break;

      case JBC_ineg:
	s = _unaryHelper(INT_NEG, popInt(), VM_Type.IntType);
	break;

      case JBC_lneg:
	s = _unaryDualHelper(LONG_NEG, popLong(), VM_Type.LongType);
	break;

      case JBC_fneg:
	s = _unaryHelper(FLOAT_NEG, popFloat(), VM_Type.FloatType);
	break;

      case JBC_dneg:
	s = _unaryDualHelper(DOUBLE_NEG, popDouble(), VM_Type.DoubleType);
	break;

      case JBC_ishl:
	{
	  OPT_Operand op2 = popInt();
	  OPT_Operand op1 = popInt();
	  s = _binaryHelper(INT_SHL, op1, op2, VM_Type.IntType);
	}
	break;

      case JBC_lshl:
	{
	  OPT_Operand op2 = popInt();
	  OPT_Operand op1 = popLong();
	  s = _binaryDualHelper(LONG_SHL, op1, op2, VM_Type.LongType);
	}
	break;
      
      case JBC_ishr:
	{
	  OPT_Operand op2 = popInt();
	  OPT_Operand op1 = popInt();
	  s = _binaryHelper(INT_SHR, op1, op2, VM_Type.IntType);
	}
	break;

      case JBC_lshr:
	{
	  OPT_Operand op2 = popInt();
	  OPT_Operand op1 = popLong();
	  s = _binaryDualHelper(LONG_SHR, op1, op2, VM_Type.LongType);
	} 
	break;

      case JBC_iushr:
	{
	  OPT_Operand op2 = popInt();
	  OPT_Operand op1 = popInt();
	  s = _binaryHelper(INT_USHR, op1, op2, VM_Type.IntType);
	}
	break;

      case JBC_lushr:
	{
	  OPT_Operand op2 = popInt();
	  OPT_Operand op1 = popLong();
	  s = _binaryDualHelper(LONG_USHR, op1, op2, VM_Type.LongType);
	}
	break;
      
      case JBC_iand:
	{
	  OPT_Operand op2 = popInt();
	  OPT_Operand op1 = popInt();
	  s = _binaryHelper(INT_AND, op1, op2, VM_Type.IntType);
	}
	break;

      case JBC_land:
	{
	  OPT_Operand op2 = popLong();
	  OPT_Operand op1 = popLong();
	  s = _binaryDualHelper(LONG_AND, op1, op2, VM_Type.LongType);
	}
	break;

      case JBC_ior:
	{
	  OPT_Operand op2 = popInt();
	  OPT_Operand op1 = popInt();
	  s = _binaryHelper(INT_OR, op1, op2, VM_Type.IntType);
	}
	break;

      case JBC_lor:
	{
	  OPT_Operand op2 = popLong();
	  OPT_Operand op1 = popLong();
	  s = _binaryDualHelper(LONG_OR, op1, op2, VM_Type.LongType);
	}
	break;

      case JBC_ixor:
	{
	  OPT_Operand op2 = popInt();
	  OPT_Operand op1 = popInt();
	  s = _binaryHelper(INT_XOR, op1, op2, VM_Type.IntType);
	}
	break;

      case JBC_lxor:
	{
	  OPT_Operand op2 = popLong();
	  OPT_Operand op1 = popLong();
	  s = _binaryDualHelper(LONG_XOR, op1, op2, VM_Type.LongType);
	}
	break;

      case JBC_iinc:
	{
	  int index = bcInfo.getLocalNumber();
	  s = do_iinc(index, bcInfo.getByteValue());
	}
	break;

      case JBC_i2l:
	s = _unaryDualHelper(INT_2LONG, popInt(), VM_Type.LongType);
	break;

      case JBC_i2f:
	s = _unaryHelper(INT_2FLOAT, popInt(), VM_Type.FloatType);
	break;

      case JBC_i2d:
	s = _unaryDualHelper(INT_2DOUBLE, popInt(), VM_Type.DoubleType);
	break;

      case JBC_l2i:
	s = _unaryHelper(LONG_2INT, popLong(), VM_Type.IntType);
	break;

      case JBC_l2f:
	s = _unaryHelper(LONG_2FLOAT, popLong(), VM_Type.FloatType);
	break;

      case JBC_l2d:
	s = _unaryDualHelper(LONG_2DOUBLE, popLong(), VM_Type.DoubleType);
	break;

      case JBC_f2i:
	s = _unaryHelper(FLOAT_2INT, popFloat(), VM_Type.IntType);
	break;

      case JBC_f2l:
	s = _unaryDualHelper(FLOAT_2LONG, popFloat(), VM_Type.LongType);
	break;

      case JBC_f2d:
	s = _unaryDualHelper(FLOAT_2DOUBLE, popFloat(), VM_Type.DoubleType);
	break;

      case JBC_d2i:
	s = _unaryHelper(DOUBLE_2INT, popDouble(), VM_Type.IntType);
	break;

      case JBC_d2l:
	s = _unaryDualHelper(DOUBLE_2LONG, popDouble(), VM_Type.LongType);
	break;

      case JBC_d2f:
	s = _unaryHelper(DOUBLE_2FLOAT, popDouble(), VM_Type.FloatType);
	break;

      case JBC_int2byte:
	s = _unaryHelper(INT_2BYTE, popInt(), VM_Type.ByteType);
	break;

      case JBC_int2char:
	s = _unaryHelper(INT_2USHORT, popInt(), VM_Type.CharType);
	break;

      case JBC_int2short:
	s = _unaryHelper(INT_2SHORT, popInt(), VM_Type.ShortType);
	break;

      case JBC_lcmp:
	{
	  OPT_Operand op2 = popLong();
	  OPT_Operand op1 = popLong();
	  s = _binaryHelper(LONG_CMP, op1, op2, VM_Type.IntType);
	}
	break;

      case JBC_fcmpl:
	{
	  OPT_Operand op2 = popFloat();
	  OPT_Operand op1 = popFloat();
	  s = _binaryHelper(FLOAT_CMPL, op1, op2, VM_Type.IntType);
	}
	break;
      
      case JBC_fcmpg:
	{
	  OPT_Operand op2 = popFloat();
	  OPT_Operand op1 = popFloat();
	  s = _binaryHelper(FLOAT_CMPG, op1, op2, VM_Type.IntType);
	}
	break;

      case JBC_dcmpl:
	{
	  OPT_Operand op2 = popDouble();
	  OPT_Operand op1 = popDouble();
	  s = _binaryHelper(DOUBLE_CMPL, op1, op2, VM_Type.IntType);
	}
	break;

      case JBC_dcmpg:
	{
	  OPT_Operand op2 = popDouble();
	  OPT_Operand op1 = popDouble();
	  s = _binaryHelper(DOUBLE_CMPG, op1, op2, VM_Type.IntType);
	}
	break;

      case JBC_ifeq:
	s = _intIfHelper(OPT_ConditionOperand.EQUAL());
	break;

      case JBC_ifne:
	s = _intIfHelper(OPT_ConditionOperand.NOT_EQUAL());
	break;

      case JBC_iflt:
	s = _intIfHelper(OPT_ConditionOperand.LESS());
	break;

      case JBC_ifge:
	s = _intIfHelper(OPT_ConditionOperand.GREATER_EQUAL());
	break;

      case JBC_ifgt:
	s = _intIfHelper(OPT_ConditionOperand.GREATER());
	break;

      case JBC_ifle:
	s = _intIfHelper(OPT_ConditionOperand.LESS_EQUAL());
	break;

      case JBC_if_icmpeq:
	s = _intIfCmpHelper(OPT_ConditionOperand.EQUAL());
	break;

      case JBC_if_icmpne:
	s = _intIfCmpHelper(OPT_ConditionOperand.NOT_EQUAL());
	break;

      case JBC_if_icmplt:
	s = _intIfCmpHelper(OPT_ConditionOperand.LESS());
	break;

      case JBC_if_icmpge:
	s = _intIfCmpHelper(OPT_ConditionOperand.GREATER_EQUAL());
	break;

      case JBC_if_icmpgt:
	s = _intIfCmpHelper(OPT_ConditionOperand.GREATER());
	break;

      case JBC_if_icmple:
	s = _intIfCmpHelper(OPT_ConditionOperand.LESS_EQUAL());
	break;

      case JBC_if_acmpeq:
	s = _refIfCmpHelper(OPT_ConditionOperand.EQUAL());
	break;

      case JBC_if_acmpne:
	s = _refIfCmpHelper(OPT_ConditionOperand.NOT_EQUAL());
	break;

      case JBC_goto:
	{
	  int offset = bcInfo.getBranchTarget();
	  if (offset != 3)   // skip generating frivolous goto's
	    s = _gotoHelper(offset);
	}
	break;

      case JBC_jsr:
	s = _jsrHelper(bcInfo.getBranchTarget());
	break;

      case JBC_ret:
	s = _retHelper(bcInfo.getLocalNumber());
	break;

      case JBC_tableswitch:
	{
	  OPT_Operand op0 = popInt();
	  bcInfo.alignSwitch();
	  int defaultoff = bcInfo.getSwitchDefaultTarget();
	  int low = bcInfo.getSwitchLowValue();
	  int high = bcInfo.getSwitchHighValue();
	  int number = high - low + 1;
	  if (CF_TABLESWITCH && op0 instanceof OPT_IntConstantOperand) {
	    int v1 = ((OPT_IntConstantOperand)op0).value;
	    int offset;
	    if ((v1 < low) || (v1 > high))
	      offset = defaultoff; 
	    else 
	      offset = bcInfo.getTableSwitchOffsetForConstant(v1, low, high);
	    bcInfo.skipTableSwitchTargets(number);
	    if (DBG_CF) {
	      db("changed tableswitch to goto because index (" + v1 + 
		 ") is constant");
	    }
	    s = _gotoHelper(offset);
	    break;
	  }
	  s = TableSwitch.create(TABLESWITCH, op0, null, null, 
				 new OPT_IntConstantOperand(low), 
				 new OPT_IntConstantOperand(high), 
				 generateTarget(defaultoff), 
				 new OPT_BranchProfileOperand(),
				 number*2);
	  double approxProb = 1/(number+1); // num targets + default
	  for (int i = 0; i < number; ++i) {
	    TableSwitch.setTarget(s, i, 
				  generateTarget(bcInfo.getSwitchTarget(i)));
	    TableSwitch.
	      setBranchProfile(s, i, new OPT_BranchProfileOperand(approxProb));
	  }
	}
	break;

      case JBC_lookupswitch:
	{
	  OPT_Operand op0 = popInt();
	  bcInfo.alignSwitch();
	  int defaultoff = bcInfo.getSwitchDefaultTarget();
	  int numpairs = bcInfo.getLookupSwitchNumberOfPairs();
	  if (numpairs == 0) {
	    s = _gotoHelper(defaultoff);
	    break;
	  }
	  if (CF_LOOKUPSWITCH && op0 instanceof OPT_IntConstantOperand) {
	    int v1 = ((OPT_IntConstantOperand)op0).value;
	    int offset = defaultoff;
	    for (int i = 0; i < numpairs; ++i) {
	      int match = bcInfo.getSwitchValue(i);
	      if (match == v1) {
		offset = bcInfo.getSwitchTarget(i);
		bcInfo.skipLookupSwitchPairs(numpairs, i + 1);
		break;        // for
	      } else {
		bcInfo.skipSwitchValue(i);
	      }
	    }
	    if (DBG_CF) {
	      db("changed lookupswitch to goto because index (" + v1
		 + ") is constant");
	    }
	    s = _gotoHelper(offset);
	    break;
	  }
	  s = LookupSwitch.create(LOOKUPSWITCH, op0, null, null, 
				  generateTarget(defaultoff), 
				  new OPT_BranchProfileOperand(),numpairs*3);
	  double approxProb = 1/(numpairs+1); // num targets + default
	  for (int i = 0; i < numpairs; ++i) {
	    LookupSwitch.setMatch(s, i, new OPT_IntConstantOperand
				  (bcInfo.getSwitchValue(i)));
	    LookupSwitch.setTarget(s, i, 
				   generateTarget(bcInfo.getSwitchTarget(i)));
	    LookupSwitch.
	      setBranchProfile(s, i, new OPT_BranchProfileOperand(approxProb));
	  }
	}
	break;

      case JBC_ireturn:
	_returnHelper(INT_MOVE, popInt());
	break;

      case JBC_lreturn:
	_returnHelper(LONG_MOVE, popLong());
	break;

      case JBC_freturn:
	_returnHelper(FLOAT_MOVE, popFloat());
	break;

      case JBC_dreturn:
	_returnHelper(DOUBLE_MOVE, popDouble());
	break;

      case JBC_areturn:
	{
	  OPT_Operand op0 = popRef();
	  if (VM.VerifyAssertions && !op0.isDefinitelyNull()) {
	    VM_Type retType = getRefTypeOf(op0);
	    // fudge to deal with conservative approximation 
	    // in OPT_ClassLoaderProxy.proxy.findCommonSuperclass
	    if (retType != VM_Type.JavaLangObjectType)
	      assertIsAssignable(gc.method.getReturnType(), getRefTypeOf(op0));
	  }
	  _returnHelper(REF_MOVE, op0);
	}
	break;

      case JBC_return:
	_returnHelper(null, null);
	break;

      case JBC_getstatic:
	{
	  int constantPoolIndex = bcInfo.getFieldReferenceIndex();
	  VM_Field field = bcInfo.getFieldReference(constantPoolIndex);
	  // use results of field analysis to refine type.
	  VM_Type fieldType = field.getType();
	  VM_Type concreteType = null;
          OPT_RegisterOperand t = null;
	  if (field.getType().isClassType() && 
	      field.getType().asClass().isLoaded()) {
	    concreteType = OPT_FieldAnalysis.getConcreteType(field);
	  }
	  if (concreteType != null)
	    fieldType = concreteType;
	  t = gc.temps.makeTemp(fieldType);
	  if (concreteType == null)
	    t.setDeclaredType(); 
	  else 
	    t.setPreciseType();
	  boolean unresolved = field.needsDynamicLink(gc.method);
	  if (!unresolved) field = field.resolve();
	  OPT_Operator operator = unresolved?GETSTATIC_UNRESOLVED:GETSTATIC;
	  s = GetStatic.create(operator, t, makeStaticFieldRef(field));
	  // optimization: don't load if
	  //    0) it's not a float/double constant
	  //       (a) not incredibly useful since we can't 
	  //	       embed fp literals in the instruction anyways
	  //       (b) causes precision/rounding errors in some cases.
	  //	1) field is resolved
	  //	2) running the VM
	  // 	3) field is final
	  //	4) declaring class is initialized
	  //
	  // instead, use the constant value stored in the field
	  // 0)
	  if ((fieldType != VM_Type.DoubleType) && 
	      (fieldType != VM_Type.FloatType)) {
	    // 1) 2)
	    if (!unresolved && VM.runningVM) {
	      // 3)
	      if (field.isFinal()) {
		VM_Class declaringClass = field.getDeclaringClass();
		// 4)
		if (declaringClass.isInitialized()) {
		  // optimize: change load to a move from a constant operand
		  operator = OPT_IRTools.getMoveOp(field.getType(), false);
		  OPT_ConstantOperand rhsOperand = 
		    OPT_RVMIRTools.getStaticFieldValue(field);
		  if (rhsOperand != null) {
		    s = Move.create(operator, t, rhsOperand);
		    s.position = gc.inlineSequence;
		    s.bcIndex = instrIndex;
		  }
		}
	      }
	    }
	  }
	  push(t.copyD2U(), fieldType);
	  if (unresolved)
	    rectifyStateWithErrorHandler();
	}
	break;

      case JBC_putstatic:
	{
	  int constantPoolIndex = bcInfo.getFieldReferenceIndex();
	  VM_Field field = bcInfo.getFieldReference(constantPoolIndex);
	  VM_Type fieldType = field.getType();
	  OPT_Operand r = pop(fieldType);
	  boolean unresolved = field.needsDynamicLink(gc.method);
	  if (!unresolved) field = field.resolve();
	  OPT_Operator operator = unresolved?PUTSTATIC_UNRESOLVED:PUTSTATIC;
	  s = PutStatic.create(operator, r, makeStaticFieldRef(field));
	  if (unresolved)
	    rectifyStateWithErrorHandler();
	}
	break;

      case JBC_getfield:
	{
	  OPT_Operand op1 = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(op1))
	    break;
	  int constantPoolIndex = bcInfo.getFieldReferenceIndex();
	  VM_Field field = bcInfo.getFieldReference(constantPoolIndex);
	  VM_Type fieldType = field.getType();
	  // use results of field analysis to refine type.
	  VM_Type concreteType = null;
          OPT_RegisterOperand t = null;
	  if (field.getType().isClassType() && 
	      field.getType().asClass().isLoaded()) {
	    concreteType = OPT_FieldAnalysis.getConcreteType(field);
	  }
	  if (concreteType != null)
	    fieldType = concreteType;
	  t = gc.temps.makeTemp(fieldType);
	  if (concreteType == null)
	    t.setDeclaredType(); 
	  else 
	    t.setPreciseType();
	  boolean unresolved = field.needsDynamicLink(gc.method);
	  if (!unresolved) field = field.resolve();
	  OPT_Operator operator = unresolved?GETFIELD_UNRESOLVED:GETFIELD;
	  s = GetField.create(operator, t, op1, makeInstanceFieldRef(field), 
			      getCurrentGuard());
	  push(t.copyD2U(), fieldType);
	  if (unresolved)
	    rectifyStateWithErrorHandler();
	}
	break;

      case JBC_putfield:
	{
	  int constantPoolIndex = bcInfo.getFieldReferenceIndex();
	  VM_Field field = bcInfo.getFieldReference(constantPoolIndex);
	  VM_Type fieldType = field.getType();
	  OPT_Operand val = pop(fieldType);
	  OPT_Operand obj = popRef();
	  clearCurrentGuard();
	  if (do_NullCheck(obj))
	    break;
	  boolean unresolved = field.needsDynamicLink(gc.method);
	  if (!unresolved) field = field.resolve();
	  boolean willHaveWB = 
	    VM_Collector.NEEDS_WRITE_BARRIER && !field.getType().isPrimitiveType();
	  OPT_Operator operator = unresolved?PUTFIELD_UNRESOLVED:PUTFIELD;
	  s = PutField.create(operator, val, obj, makeInstanceFieldRef(field), 
			      getCurrentGuard());
	  if (unresolved || willHaveWB)
	    rectifyStateWithErrorHandler();
	}
	break;

      case JBC_invokevirtual:
	{
	  if (gc.options.PRINT_DETAILED_INLINE_REPORT) {
	    OPT_InlineReport.beginNewDecision();
	    OPT_InlineReport.setCallType(OPT_InlineReport.INVOKE_VIRTUAL);
	  }
	  int constantPoolIndex = bcInfo.getMethodReferenceIndex();
	  VM_Method meth = bcInfo.getMethodReference(constantPoolIndex);
	  boolean unresolved = meth.needsDynamicLink(gc.method);
	  if (!unresolved) meth = meth.resolve();
	  OPT_MethodOperand methOp = 
	    OPT_MethodOperand.VIRTUAL(meth, unresolved);
	  s = _callHelper(methOp);
	  if (s == null) {
	    if (gc.options.PRINT_DETAILED_INLINE_REPORT) {
	      OPT_InlineReport.instructionNull();
	    }
	    break;
	  }

	  // try to reduce the number of targets.
	  // If we succeed, we'll update meth, unresolved, 
	  // and s's method operand.
	  OPT_Operand ref = Call.getParam(s, 0);
	  boolean receiverClassResolved = false;
	  boolean isExtant = false;
	  VM_Type type = null;
	  if (ref.isRegister()) {
	    isExtant = ref.asRegister().isExtant();
	    type = ref.asRegister().type;
	  } else if (ref.isStringConstant()) {
	    isExtant = true;
	    type = VM_Type.JavaLangStringType;
	  } else if (VM.VerifyAssertions)
	    VM.assert(false, "unexpected receiver");
	  receiverClassResolved = type.isResolved();
	  if ((meth.getDeclaringClass() != type) && type.isClassType()) {
	    VM_Method vmeth = lookupMethod(meth, type.asClass());
	    if (vmeth != null && vmeth != meth) {
	      // Update meth to be the refined vmeth
	      meth = vmeth;
	      unresolved = meth.needsDynamicLink(gc.method);
	      if (!unresolved) meth = meth.resolve();
	      Call.setMethod(s, OPT_MethodOperand.VIRTUAL(meth, unresolved, true));
	    }
	  }
	  if (gc.options.PRINT_DETAILED_INLINE_REPORT)
	    OPT_InlineReport.classUnresolved(unresolved, meth);
	  // If we don't need dynamic linking code, think about inlining
	  if (!unresolved) {
	    // if the type is precise and receiverClassResolved is true
	    // then we know FOR CERTAIN that this call will resolve to meth.
	    // By ensuring that recieverClassResolved, we make sure that 
	    // lookupMethod was able to actually search the class hierarchy 
	    // and set meth appropriately.
	    VM_Method computedTarget = null;
	    if (receiverClassResolved && 
		((ref.isStringConstant()) || 
		 ref.asRegister().isPreciseType())) {
	      computedTarget = meth;
	      if (gc.options.PRINT_DETAILED_INLINE_REPORT)
		OPT_InlineReport.isPreciseType();
	    }
            OPT_InlineDecision id = shouldInline(s, computedTarget, isExtant);
            if (maybeInlineMethod(id, s)) {
              return;
            }
          }

	  // CALL must be treated as potential throw of anything
	  rectifyStateWithExceptionHandlers(); 
	}
	break;

      case JBC_invokespecial:
	{
	  if (gc.options.PRINT_DETAILED_INLINE_REPORT) {
	    OPT_InlineReport.beginNewDecision();
	    OPT_InlineReport.setCallType(OPT_InlineReport.INVOKE_SPECIAL);
	  }
	  int constantPoolIndex = bcInfo.getMethodReferenceIndex();
	  VM_Method meth = bcInfo.getMethodReference(constantPoolIndex);
	  // Note: this is not the usual needsDynamicLink 
	  // due to semantics of invokespecial
	  // See comments in VM_OptLinker, OPT_Convert2Low, VM_Linker.  
	  boolean unresolved = 
	    !meth.getDeclaringClass().isResolved() ||
	    (VM_Class.findSpecialMethod(meth) == null);
	  if (!unresolved) meth = meth.resolve();
	  s = _callHelper(OPT_MethodOperand.SPECIAL(meth, unresolved));
	  if (s == null) {
	    if (gc.options.PRINT_DETAILED_INLINE_REPORT) {
	      OPT_InlineReport.instructionNull();
	    }
	    break;
	  }
	  if (gc.options.PRINT_DETAILED_INLINE_REPORT)
	    OPT_InlineReport.classUnresolved(unresolved, meth);
	  // If we don't need dynamic linking code, think about inlining
	  if (!unresolved) {
	    if (maybeInlineMethod(shouldInline(s, null, false), s)) {
	      return;
	    }
	  }
	  // CALL must be treated as potential throw of anything
	  rectifyStateWithExceptionHandlers(); 
	}
	break;

      case JBC_invokestatic:
	{
	  if (gc.options.PRINT_DETAILED_INLINE_REPORT) {
	    OPT_InlineReport.beginNewDecision();
	    OPT_InlineReport.setCallType(OPT_InlineReport.INVOKE_STATIC);
	  }
	  int constantPoolIndex = bcInfo.getMethodReferenceIndex();
	  VM_Method meth = bcInfo.getMethodReference(constantPoolIndex);
	  if (meth.getDeclaringClass().isMagicType()) {
	    try {
	      OPT_GenerateMagic.generateMagic(this, gc, meth);
	      if (gc.options.PRINT_DETAILED_INLINE_REPORT) {
		OPT_InlineReport.isMagic(meth);
	      }
	      break;
	    } catch (OPT_MagicNotImplementedException e) {
	      if (gc.options.PRINT_DETAILED_INLINE_REPORT) {
		OPT_InlineReport.unimplementedMagic(Call.getMethod(s).method);
	      }
	      if (gc.options.SKIP_UNKNOWN_MAGIC) {
		s = _callHelper(OPT_MethodOperand.STATIC(meth, false));
		// CALL must be treated as potential throw of anything
		rectifyStateWithExceptionHandlers();
		break;
	      } else {
		throw  (e);
	      }
	    }
	  }
	  boolean unresolved = meth.needsDynamicLink(gc.method);
	  if (!unresolved) meth = meth.resolve();
	  s = _callHelper(OPT_MethodOperand.STATIC(meth, unresolved));
	  if (gc.options.PRINT_DETAILED_INLINE_REPORT)
	    OPT_InlineReport.classUnresolved(unresolved, meth);

	  // If we don't need dynamic linking code, think about inlining
	  if (!unresolved) {
	    if (maybeInlineMethod(shouldInline(s, null, false), s)) {
	      return;
	    }
	  }
	  // CALL must be treated as potential throw of anything
	  rectifyStateWithExceptionHandlers();
	}
	break;

      case JBC_invokeinterface:
	{
	  if (gc.options.PRINT_DETAILED_INLINE_REPORT) {
	    OPT_InlineReport.beginNewDecision();
	    OPT_InlineReport.setCallType(OPT_InlineReport.INVOKE_INTERFACE);
	  }
	  int constantPoolIndex = bcInfo.getMethodReferenceIndex();
	  VM_Method meth = bcInfo.getMethodReference(constantPoolIndex);
	  bcInfo.eatInvokeInterfaceGarbage();
	  OPT_MethodOperand methOp = OPT_MethodOperand.INTERFACE(meth, false);
	  s = _callHelper(methOp);
	  if (s == null) {
	    if (gc.options.PRINT_DETAILED_INLINE_REPORT) {
	      OPT_InlineReport.instructionNull();
	    }
	    break;
	  }

	  OPT_RegisterOperand ref = Call.getParam(s, 0).asRegister();
	  VM_Class refType = ref.type.asClass();
	  VM_Type interfaceType = meth.getDeclaringClass();
	  boolean requiresImplementsTest = 
	    VM.BuildForIMTInterfaceInvocation ||
	    (VM.BuildForITableInterfaceInvocation && VM.DirectlyIndexedITables);

	  // Step 1: handle the possibility of ghost references
	  try {
	    VM_Method resolvedMethodRef = meth.resolveInterfaceMethod(false);
	    if (resolvedMethodRef == null) {
	      if (requiresImplementsTest) {
		// might be a ghost reference. 
		// Must do the more involved typechecking sequence
		OPT_RegisterOperand tibPtr = 
		  gc.temps.makeTemp(OPT_ClassLoaderProxy.JavaLangObjectArrayType);
		OPT_Instruction getTib = 
		    GuardedUnary.create(GET_OBJ_TIB, tibPtr, 
		      ref.copyU2U(), 
		      getCurrentGuard());
		appendInstruction(getTib);
		getTib.bcIndex = RUNTIME_SERVICES_BCI;

		OPT_MethodOperand dtcRoutine = 
		    OPT_MethodOperand.STATIC(OPT_DynamicTypeCheckExpansion.unresolvedInterfaceMethod);
		OPT_Instruction callCheck =
		    Call.create2(CALL, null, null, dtcRoutine, 
		      new OPT_IntConstantOperand(meth.getDictionaryId()),
		      tibPtr.copyD2U());
		appendInstruction(callCheck);
		callCheck.bcIndex = RUNTIME_SERVICES_BCI;

		requiresImplementsTest = false; // the above call subsumes the test
	      }
	    } else {
	      meth = resolvedMethodRef;
	    }
	  } catch (VM_ResolutionException e) {
	    // actually can't be thrown when we pass false for canLoad.
	  }

	  // Step 2: Invokeinterface requires a dynamic type check
	  //         to ensure that the receiver object actually
	  //         implements the interface.  This is necessary
	  //         because loading a class does not load the interfaces
	  //         that class implements, and thus the verifier cannot
	  //         detect incompatible class changes.
	  //         If we're building for FastInterfaceInvocation then
	  //         this test must be made explicit in the calling sequence.
	  //         In the slow interface invocation path, it is handled
	  //         deep in the guts of the dispatching code.
	  // NOTE:   We don't insert the test here if we are doing to
	  //         do a guarded inline of an interface call.  That case
	  //         is handled within OPT_Inliner.java.  However, if 
	  //         we virtualize the call, we do need to handle the DTC
	  //         here, since OPT_Inliner won't have enough information
	  //         to determine what the right thing to do is.
	  if (requiresImplementsTest) {
	    // Attempt to avoid inserting the check by seeing if the 
	    // known static type of the receiver implements the interface.
	    if (refType.isResolved() && !refType.isInterface()) {
	      byte doesImplement = 
		OPT_ClassLoaderProxy.proxy.isAssignableWith(interfaceType, 
							    refType);
	      requiresImplementsTest = doesImplement != YES;
	    }
	  }

	  // Step 3: Attempt to resolve the interface call to 
	  //         a particular virtual method.
	  //         Note: step 2 and step 3 are independent
	  //         and it is not that case that being able 
	  //         to do 2 implies 3 or that doing 3 implies 2.  
	  VM_Method vmeth;
	  if (refType.isResolved() && !refType.isInterface() &&
	      (vmeth = lookupMethod(meth, refType)) != null) {
	    // We're going to virtualize it. Must deal with the DTC here.
	    if (requiresImplementsTest) {
	      appendInstruction(TypeCheck.create(CHECKCAST_INTERFACE_NOTNULL,
						 ref.copyU2U(),
						 makeTypeOperand(interfaceType),
						 getCurrentGuard()));
	    }
	    boolean unresolved = vmeth.needsDynamicLink(gc.method);
	    if (!unresolved) vmeth = vmeth.resolve();
	    Call.setMethod(s, OPT_MethodOperand.VIRTUAL(vmeth, unresolved, true));
	    if (gc.options.PRINT_DETAILED_INLINE_REPORT)
	      OPT_InlineReport.classUnresolved(unresolved, meth);
	    // Think about trying to inline it
	    if (!unresolved) {
	      // if the type is precise, then we know FOR CERTAIN that
	      // this call will resolve to meth
	      VM_Method computedTarget = ref.isPreciseType() ? vmeth : null;
	      if (computedTarget != null && 
		  gc.options.PRINT_DETAILED_INLINE_REPORT)
		OPT_InlineReport.isPreciseType();
		
	      OPT_InlineDecision id = 
		shouldInline(s, computedTarget, ref.isExtant());
              if (maybeInlineMethod(id, s)) {
                return;
              }
	    }
	  } else {
	    // try to inline a predicted target for the interface invocation
            OPT_InlineDecision id = shouldInline(s, null, false);
            if (maybeInlineMethod(id, s)) {
              return;
            } else {
	      if (requiresImplementsTest) {
		appendInstruction(TypeCheck.create(CHECKCAST_INTERFACE_NOTNULL,
						   ref.copyU2U(),
						   makeTypeOperand(interfaceType),
						   getCurrentGuard()));
	      }
	    }
	  }
	  // CALL must be treated as potential throw of anything
	  rectifyStateWithExceptionHandlers(); 
	}
	break;

      case JBC_xxxunusedxxx:
	OPT_OptimizingCompilerException.UNREACHABLE();
	break;

      case JBC_new:
	{
	  int constantPoolIndex = bcInfo.getTypeReferenceIndex();
	  VM_Type klass = bcInfo.getTypeReference(constantPoolIndex);
	  OPT_TypeOperand klassOp = makeTypeOperand(klass);
	  OPT_RegisterOperand t = gc.temps.makeTemp(klass);
	  t.setPreciseType();
	  markGuardlessNonNull(t);
	  OPT_Operator operator;
	  if (klass.isInitialized() || klass.asClass().isInBootImage()) {
	    operator = NEW;
	  } else { 
	    operator = NEW_UNRESOLVED;
	  }
	  s = New.create(operator, t, klassOp);
	  push(t.copyD2U());
	  rectifyStateWithErrorHandler();
	}
	break;

      case JBC_newarray:
	{
	  byte atype = (byte)bcInfo.getByteValue();
	  VM_Array array = VM_Array.getPrimitiveArrayType(atype);
	  OPT_TypeOperand arrayOp = makeTypeOperand(array);
	  OPT_RegisterOperand t = gc.temps.makeTemp(array);
	  t.setPreciseType();
	  markGuardlessNonNull(t);
	  s = NewArray.create(NEWARRAY, t, arrayOp, popInt());
	  push(t.copyD2U()); 
	  VM_Class et = 
	    OPT_ClassLoaderProxy.JavaLangNegativeArraySizeExceptionType;
	  rectifyStateWithExceptionHandler(et);
	}
	break;

      case JBC_anewarray:
	{
	  int constantPoolIndex = bcInfo.getTypeReferenceIndex();
	  VM_Type elementTypeRef = bcInfo.getTypeReference(constantPoolIndex);
	  VM_Array array = elementTypeRef.getArrayTypeForElementType();
	  OPT_TypeOperand arrayOp = makeTypeOperand(array);
	  OPT_RegisterOperand t = gc.temps.makeTemp(array);
	  t.setPreciseType();
	  markGuardlessNonNull(t);
	  s = NewArray.create(NEWARRAY, t, arrayOp, popInt());
	  push(t.copyD2U()); 
	  rectifyStateWithErrorHandler();
	  VM_Class et = 
	    OPT_ClassLoaderProxy.JavaLangNegativeArraySizeExceptionType;
	  rectifyStateWithExceptionHandler(et);
	}
	break;

      case JBC_arraylength:
	{
	  OPT_Operand op1 = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(op1))
	    break;
	  if (VM.VerifyAssertions)
	    VM.assert(getArrayTypeOf(op1).isArrayType());
	  OPT_RegisterOperand t = gc.temps.makeTempInt();
	  s = GuardedUnary.create(ARRAYLENGTH, t, op1, getCurrentGuard());
	  push(t.copyD2U());
	}
	break;

      case JBC_athrow:
	{
	  OPT_Operand op0 = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(op0))
	    break;
	  VM_Type type = getRefTypeOf(op0);
	  if (VM.VerifyAssertions) {
	    // fudge to handle conservative approximation of 
	    // OPT_ClassLoaderProxy.proxy.findCommonSuperclass
	    if (type != VM_Type.JavaLangObjectType)
	      assertIsAssignable(VM_Type.JavaLangThrowableType, type);
	  }
	  if (!gc.method.getDeclaringClass().isInterruptible()) {
	    // prevent code motion in or out of uninterruptible code sequence
	    appendInstruction(Empty.create(UNINT_END));
	  }
	  endOfBasicBlock = true;
	  OPT_BasicBlock definiteTarget = 
	    rectifyStateWithExceptionHandler(type, true);
	  if (definiteTarget != null) {
	    appendInstruction(CacheOp.create(SET_CAUGHT_EXCEPTION, op0));
	    s = Goto.create(GOTO, definiteTarget.makeJumpTarget());
	  } else {
	    s = Athrow.create(ATHROW, (OPT_RegisterOperand)op0);
	  }
	}
	break;

      case JBC_checkcast:
	{
	  int constantPoolIndex = bcInfo.getTypeReferenceIndex();
	  VM_Type typeRef = bcInfo.getTypeReference(constantPoolIndex);
	  OPT_TypeOperand typeOp = makeTypeOperand(typeRef);
	  OPT_Operand op2 = pop();
	  if (VM.VerifyAssertions) VM.assert(op2.isRef());
	  if (CF_CHECKCAST) {
	    if (op2.isDefinitelyNull()) {
	      push(op2);
	      if (DBG_CF) db("skipped gen of null checkcast");
	      break;
	    }
	    VM_Type type = getRefTypeOf(op2);  // non-null, null case above
	    if (OPT_ClassLoaderProxy.proxy.isAssignableWith(typeRef,type)==YES){
	      push(op2);
	      if (DBG_CF)
		db("skipped gen of checkcast of " + op2 + " from "
		   + typeRef + " to " + type);
	      break;
	    }
	  }

	  OPT_RegisterOperand reg = (OPT_RegisterOperand)op2;
	  if (gc.options.NO_CHECKCAST) {
	    // Unsafely eliminate all checkcasts
	  } else {
	    if (isNonNull(reg)) {
	      s = TypeCheck.create(CHECKCAST_NOTNULL, reg, typeOp, 
				   getGuard(reg));
	    } else {
	      s = TypeCheck.create(CHECKCAST, reg, typeOp);
	    }
	  }
	  reg = reg.copyU2U();
	  reg.type = typeRef;
	  push(reg);
	  VM_Class et = OPT_ClassLoaderProxy.JavaLangClassCastExceptionType;
	  rectifyStateWithExceptionHandler(et);
	}
	break;

      case JBC_instanceof:
	{
	  int constantPoolIndex = bcInfo.getTypeReferenceIndex();
	  VM_Type typeRef = bcInfo.getTypeReference(constantPoolIndex);
	  OPT_TypeOperand typeOp = makeTypeOperand(typeRef);
	  OPT_Operand op2 = pop();
	  if (VM.VerifyAssertions) VM.assert(op2.isRef());
	  if (CF_INSTANCEOF) {
	    if (op2.isDefinitelyNull()) {
	      push(new OPT_IntConstantOperand(0));
	      if (DBG_CF) db("skipped gen of null instanceof");
	      break;
	    }
	    VM_Type type = getRefTypeOf(op2);                 // non-null
	    int answer = 
	      OPT_ClassLoaderProxy.proxy.isAssignableWith(typeRef, type);
	    if (answer == YES) {
	      if (isNonNull(op2)) {
		push(new OPT_IntConstantOperand(1));
		if (DBG_CF)
		  db("skipped gen of instanceof of " + op2 + " from "
		     + typeRef + " to " + type);
		break;
	      } else {
                // !!TODO: For boolean variables, is any non-zero integer
                // equivalent to true? If so, in this case, we can replace
                // t <- o2 instanceof type with t <- o2. (SJF)
	      }
	    } else if (answer == NO) {
	      push(new OPT_IntConstantOperand(0));
	      break;
	    }
	  }

	  OPT_RegisterOperand t = gc.temps.makeTempInt();
	  if (isNonNull(op2)) {
	    s = InstanceOf.create(INSTANCEOF_NOTNULL, t, typeOp, op2, 
				 getGuard(op2));
	  } else {
	    s = InstanceOf.create(INSTANCEOF, t, typeOp, op2);
	  }
	  push(t.copyD2U());
	  if (!typeRef.isInstantiated())
	    rectifyStateWithErrorHandler();
	}
	break;

      case JBC_monitorenter:
	{
	  OPT_Operand op0 = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(op0))
	    break;
	  if (VM.VerifyAssertions) VM.assert(op0.isRef());
	  if (gc.options.MONITOR_NOP) {
	    s = null;
	  } else {
	    s = MonitorOp.create(MONITORENTER, op0, getCurrentGuard());
	  }
	}
	break;

      case JBC_monitorexit:
	{
	  OPT_Operand op0 = pop();
	  clearCurrentGuard();
	  if (do_NullCheck(op0))
	    break;
	  if (gc.options.MONITOR_NOP) {
	    s = null;
	  } else {
	    s = MonitorOp.create(MONITOREXIT, op0, getCurrentGuard());
	  }
	  VM_Class et =  
	    OPT_ClassLoaderProxy.JavaLangIllegalMonitorStateExceptionType;
	  rectifyStateWithExceptionHandler(et);
	}
	break;

      case JBC_wide:
	{
	  int widecode = bcInfo.getWideOpcode();
	  int index = bcInfo.getWideLocalNumber();
	  switch (widecode) {
	  case JBC_iload:
	    s = do_iload(index);
	    break;

	  case JBC_lload:
	    s = do_lload(index);
	    break;

	  case JBC_fload:
	    s = do_fload(index);
	    break;

	  case JBC_dload:
	    s = do_dload(index);
	    break;

	  case JBC_aload:
	    s = do_aload(index);
	    break;

	  case JBC_istore:
	    s = do_store(index, popInt());
	    break;

	  case JBC_lstore:
	    s = do_store(index, popLong());
	    break;

	  case JBC_fstore:
	    s = do_store(index, popFloat());
	    break;

	  case JBC_dstore:
	    s = do_store(index, popDouble());
	    break;

	  case JBC_astore:
	    s = do_astore(index);
	    break;

	  case JBC_iinc:
	    s = do_iinc(index, bcInfo.getShortValue());
	    break;

	  case JBC_ret:
	    s = _retHelper(index);
	    break;

	  default:
	    OPT_OptimizingCompilerException.UNREACHABLE();
	    break;
	  }
	}
	break;

      case JBC_multianewarray:
	{
	  int constantPoolIndex = bcInfo.getTypeReferenceIndex();
	  VM_Type arrayType = bcInfo.getTypeReference(constantPoolIndex);
	  OPT_TypeOperand typeOp = makeTypeOperand(arrayType);
	  int dimensions = bcInfo.getArrayDimension();
	  // Step 1: Create an int array to hold the dimensions.
	  OPT_TypeOperand dimArrayType = 
	    makeTypeOperand(OPT_ClassLoaderProxy.IntArrayType);
	  OPT_RegisterOperand dimArray = 
	    gc.temps.makeTemp(OPT_ClassLoaderProxy.IntArrayType);
	  markGuardlessNonNull(dimArray);
	  dimArray.setPreciseType();
	  appendInstruction(NewArray.create(NEWARRAY, dimArray, dimArrayType, 
					    new OPT_IntConstantOperand(dimensions)));
	  // Step 2: Assign the dimension values to dimArray
	  for (int i = dimensions; i > 0; i--) {
	    OPT_LocationOperand loc = new OPT_LocationOperand(VM_Type.IntType);
	    appendInstruction(AStore.create(INT_ASTORE, popInt(), 
					    dimArray.copyD2U(), 
					    new OPT_IntConstantOperand(i - 1), 
					    loc, new OPT_TrueGuardOperand()));
	  }
	  // Step 3: Actually create the multiD array
	  OPT_RegisterOperand result = gc.temps.makeTemp(arrayType);
	  markGuardlessNonNull(result);
	  result.setPreciseType();
	  appendInstruction(NewArray.create(NEWOBJMULTIARRAY, result, 
					    typeOp, dimArray.copyD2U()));
	  push(result.copyD2U());
	  rectifyStateWithErrorHandler();
	  VM_Class et = 
	    OPT_ClassLoaderProxy.JavaLangNegativeArraySizeExceptionType;
	  rectifyStateWithExceptionHandler(et);
	}
	break;

      case JBC_ifnull:
	s = _refIfNullHelper(OPT_ConditionOperand.EQUAL());
	break;

      case JBC_ifnonnull:
	s = _refIfNullHelper(OPT_ConditionOperand.NOT_EQUAL());
	break;

      case JBC_goto_w:
	{
	  int offset = bcInfo.getWideBranchTarget();
	  if (offset != 5)         // skip generating frivolous goto's
	    s = _gotoHelper(offset);
	}
	break;

      case JBC_jsr_w:
	s = _jsrHelper(bcInfo.getWideBranchTarget());
	break;
	
      default:
	OPT_OptimizingCompilerException.UNREACHABLE();
	break;
      }

      if (s != null && !currentBBLE.isSelfRegen()) {
        appendInstruction(s);
      }
      // check runoff
      if (VM.VerifyAssertions) VM.assert(bcInfo.bcIndex <= runoff);
      if (!endOfBasicBlock && bcInfo.bcIndex == runoff) {
        if (DBG_BB)
          db("runoff occurred! current basic block: " + currentBBLE + 
	     ", runoff = " + runoff);
        endOfBasicBlock = fallThrough = true;
      }
      if (endOfBasicBlock) {
        if (currentBBLE.isSelfRegen()) {
          // This block ended in a goto that jumped into the middle of it.
          // Through away all out edges from this block, they're out of date
          // because we're going to have to regenerate this block.
          currentBBLE.block.deleteOut();
          if (DBG_CFG)
	    db("Deleted all out edges of " + currentBBLE.block);
          return;
        }
        if (fallThrough) {
          if (VM.VerifyAssertions) VM.assert(bcInfo.bcIndex < bcInfo.bcLength);
          // Get/Create fallthrough BBLE and record it as 
          // currentBBLE's fallThrough.
          currentBBLE.fallThrough = getOrCreateBlock(bcInfo.bcIndex);
        }
        return;
      }
    } 
  }

  private VM_Method lookupMethod(VM_Method meth, VM_Class cls) {
    VM_Method newmeth = null;
    if (cls.isResolved() && !cls.isInterface()) {
      for (; (newmeth == null) && (cls != null); cls = cls.getSuperClass()) {
        newmeth = 
	  (VM_Method)cls.findDeclaredMethod(meth.getName(), 
					    meth.getDescriptor());
      }
    }
    return newmeth;
  }

  private OPT_Instruction _unaryHelper(OPT_Operator operator, 
				       OPT_Operand val, 
				       VM_Type type) {
    OPT_RegisterOperand t = gc.temps.makeTemp(type);
    OPT_Instruction s = Unary.create(operator, t, val);
    byte simp = OPT_Simplifier.simplify(s);
    if ((simp == OPT_Simplifier.MOVE_FOLDED) ||
	(simp == OPT_Simplifier.MOVE_REDUCED)) {
      gc.temps.release(t);
      push(Move.getClearVal(s));
      return null;
    } else {
      push(t.copyD2U());
      return s;
    }
  }

  private OPT_Instruction _unaryDualHelper(OPT_Operator operator, 
					   OPT_Operand val, 
					   VM_Type type) {
    OPT_RegisterOperand t = gc.temps.makeTemp(type);
    OPT_Instruction s = Unary.create(operator, t, val);
    byte simp = OPT_Simplifier.simplify(s);
    if ((simp == OPT_Simplifier.MOVE_FOLDED) ||
	(simp == OPT_Simplifier.MOVE_REDUCED)) {
      gc.temps.release(t);
      pushDual(Move.getClearVal(s));
      return null;
    } else {
      pushDual(t.copyD2U());
      return s;
    }
  }

  private OPT_Instruction _binaryHelper(OPT_Operator operator, 
					OPT_Operand op1, 
					OPT_Operand op2, 
					VM_Type type) {
    OPT_RegisterOperand t = gc.temps.makeTemp(type);
    OPT_Instruction s = Binary.create(operator, t, op1, op2);
    byte simp = OPT_Simplifier.simplify(s);
    if ((simp == OPT_Simplifier.MOVE_FOLDED) ||
	(simp == OPT_Simplifier.MOVE_REDUCED)) {
      gc.temps.release(t);
      push(Move.getClearVal(s));
      return null;
    } else {
      push(t.copyD2U());
      return s;
    }
  }

  private OPT_Instruction _guardedBinaryHelper(OPT_Operator operator, 
					       OPT_Operand op1, 
					       OPT_Operand op2, 
					       OPT_Operand guard, 
					       VM_Type type) {
    OPT_RegisterOperand t = gc.temps.makeTemp(type);
    OPT_Instruction s = GuardedBinary.create(operator, t, op1, op2, guard);
    byte simp = OPT_Simplifier.simplify(s);
    if ((simp == OPT_Simplifier.MOVE_FOLDED) ||
	(simp == OPT_Simplifier.MOVE_REDUCED)) {
      gc.temps.release(t);
      push(Move.getClearVal(s));
      return null;
    } else {
      push(t.copyD2U());
      return s;
    }
  }

  private OPT_Instruction _binaryDualHelper (OPT_Operator operator, 
                                             OPT_Operand op1, 
                                             OPT_Operand op2, 
                                             VM_Type type) {
    OPT_RegisterOperand t = gc.temps.makeTemp(type);
    OPT_Instruction s = Binary.create(operator, t, op1, op2);
    byte simp = OPT_Simplifier.simplify(s);
    if ((simp == OPT_Simplifier.MOVE_FOLDED) ||
	(simp == OPT_Simplifier.MOVE_REDUCED)) {
      gc.temps.release(t);
      pushDual(Move.getClearVal(s));
      return null;
    } else {
      pushDual(t.copyD2U());
      return s;
    }
  }

  private OPT_Instruction _guardedBinaryDualHelper(OPT_Operator operator, 
						   OPT_Operand op1, 
						   OPT_Operand op2, 
						   OPT_Operand guard, 
						   VM_Type type) {
    OPT_RegisterOperand t = gc.temps.makeTemp(type);
    OPT_Instruction s = GuardedBinary.create(operator, t, op1, op2, guard);
    byte simp = OPT_Simplifier.simplify(s);
    if ((simp == OPT_Simplifier.MOVE_FOLDED) ||
	(simp == OPT_Simplifier.MOVE_REDUCED)) {
      gc.temps.release(t);
      pushDual(Move.getClearVal(s));
      return null;
    } else {
      pushDual(t.copyD2U());
      return s;
    }
  }

  private OPT_Instruction _moveHelper(OPT_Operator operator, 
				      OPT_Operand val, 
				      VM_Type type) {
    OPT_RegisterOperand t = gc.temps.makeTemp(type);
    push(t.copyD2U()); 
    OPT_Instruction s = Move.create(operator, t, val);
    s.position = gc.inlineSequence;
    s.bcIndex = instrIndex;
    return s;
  }

  private OPT_Instruction _moveDualHelper(OPT_Operator operator, 
					  OPT_Operand val, 
					  VM_Type type) {
    OPT_RegisterOperand t = gc.temps.makeTemp(type);
    pushDual(t.copyD2U());      // reg def -> last use
    OPT_Instruction s = Move.create(operator, t, val);
    s.position = gc.inlineSequence;
    s.bcIndex = instrIndex;
    return s;
  }

  private OPT_Instruction _aloadHelper(OPT_Operator operator, 
				       OPT_Operand ref, 
				       OPT_Operand index, 
				       VM_Type type) {
    OPT_RegisterOperand t = gc.temps.makeTemp(type);
    t.setDeclaredType();
    OPT_LocationOperand loc = new OPT_LocationOperand(type);
    OPT_Instruction s = ALoad.create(operator, t, ref, index, loc, 
                                     getCurrentGuard());
    t = t.copyD2U();
    if (type == VM_Type.LongType || type == VM_Type.DoubleType)
      pushDual(t); 
    else 
      push(t);
    return s;
  }

  /**
   * Pop method parameters off the expression stack.
   * If a non-void return, then create a result operand and push it 
   * on the stack.
   * Create the call instruction and initialize all it's operands.
   * Return the created call instruction 
   * (or null if do_NullCheck replaced it with an athrow).
   */
  private OPT_Instruction _callHelper(OPT_MethodOperand methOp) {
    int numHiddenParams = methOp.isStatic() ? 0 : 1;
    VM_Type[] params = methOp.method.getParameterTypes();
    OPT_Instruction s = Call.create(CALL, null, null, null, null,  
				    params.length + numHiddenParams);
    for (int i = params.length - 1; i >= 0; i--)
      Call.setParam(s, i + numHiddenParams, pop(params[i]));
    if (numHiddenParams != 0) {
      OPT_Operand ref = pop();
      Call.setParam(s, 0, ref);
      clearCurrentGuard();
      if (do_NullCheck(ref))
        return null;
      Call.setGuard(s, getCurrentGuard());
    }
    VM_Type rtype = methOp.method.getReturnType();
    if (!rtype.isVoidType()) {
      OPT_RegisterOperand op0 = gc.temps.makeTemp(rtype);
      Call.setResult(s, op0);
      push(op0.copyD2U(), rtype);
    }
    Call.setMethod(s, methOp);
    s.position = gc.inlineSequence;
    s.bcIndex = instrIndex;
    return s;
  }

  private void _returnHelper(OPT_Operator operator, OPT_Operand val) {
    if (gc.resultReg != null) {
      VM_Type returnType = val.getType();
      OPT_RegisterOperand ret = 
	new OPT_RegisterOperand(gc.resultReg, returnType);
      boolean returningRegister = false;
      if (val.isRegister()) {
        returningRegister = true;
        ret.setInheritableFlags(val.asRegister());
        setGuard(ret, getGuard(val));
      }
      appendInstruction(Move.create(operator, ret, val));
      // pass analysis facts about val back to our caller
      if (gc.result == null) {
        if (returningRegister) {
          gc.result = ret.copyD2U();
        } else {
          gc.result = val.copy();
        }
      } else {
        OPT_Operand meet = OPT_Operand.meet(gc.result, val, gc.resultReg);
        // Return value can't be forced to bottom...violation of Java spec.
        if (VM.VerifyAssertions) VM.assert(meet != null);
        gc.result = meet;
      }
    }
    appendInstruction(gc.epilogue.makeGOTO());
    currentBBLE.block.insertOut(gc.epilogue);
    if (DBG_CFG)
      db("Added CFG edge from " + currentBBLE.block + " to " + gc.epilogue);
    endOfBasicBlock = true;
  }

  //// APPEND INSTRUCTION.
  /**
   * Append an instruction to the current basic block.
   *
   * @param s instruction to append
   */
  void appendInstruction(OPT_Instruction s) {
    currentBBLE.block.appendInstruction(s);
    s.position = gc.inlineSequence;
    s.bcIndex = instrIndex;
    lastInstr = s;
    if (DBG_INSTR) db("-> " + s.bcIndex + ":\t" + s);
  }

  /**
   * HACK: Mark current basic block unsafe for scheduling.
   * TODO: remove when we've got UNINT_BEGIN/END working correctly.
   */
  final void markBBUnsafeForScheduling () {
    currentBBLE.block.setUnsafeToSchedule();
  }

  //// MAKE A FIELD REFERENCE.
  /**
   * Make a field reference operand referring to the given field with the
   * given type.
   *
   * @param f desired field
   */
  private OPT_LocationOperand makeStaticFieldRef(VM_Field f) {
    return new OPT_LocationOperand(f);
  }

  private OPT_LocationOperand makeInstanceFieldRef(VM_Field f) {
    return new OPT_LocationOperand(f);
  }

  //// MAKE A TYPE REFERENCE.
  /**
   * Make a type operand that refers to the given type.
   *
   * @param typ desired type
   */
  private OPT_TypeOperand makeTypeOperand(VM_Type typ) {
    return new OPT_TypeOperand(typ);
  }

  //// LOAD LOCAL VARIABLE ONTO STACK.
  /**
   * Simulate a load from a given local variable of an int.
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   */
  private OPT_Instruction do_iload(int index) {
    OPT_Operand r = getLocal(index);
    if (VM.VerifyAssertions) VM.assert(r.isIntLike());
    if (LOCALS_ON_STACK) {
      push(r);
      return null;
    } else 
      return _moveHelper(INT_MOVE, r, VM_Type.IntType);
  }

  /**
   * Simulate a load from a given local variable of a float.
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   */
  private OPT_Instruction do_fload(int index) {
    OPT_Operand r = getLocal(index);
    if (VM.VerifyAssertions) VM.assert(r.isFloat());
    if (LOCALS_ON_STACK) {
      push(r);
      return null;
    } else 
      return _moveHelper(FLOAT_MOVE, r, VM_Type.FloatType);
  }

  /**
   * Simulate a load from a given local variable of a reference.
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   */
  private OPT_Instruction do_aload(int index) {
    OPT_Operand r = getLocal(index);
    if (VM.VerifyAssertions) VM.assert(r.isRef());
    if (LOCALS_ON_STACK) {
      push(r);
      return null;
    } else 
      return _moveHelper(REF_MOVE, r, r.getType());
  }

  /**
   * Simulate a load from a given local variable of a long.
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   */
  private OPT_Instruction do_lload(int index) {
    OPT_Operand r = getLocalDual(index);
    if (VM.VerifyAssertions) VM.assert(r.isLong());
    if (LOCALS_ON_STACK) {
      pushDual(r);
      return null;
    } else 
      return _moveDualHelper(LONG_MOVE, r, VM_Type.LongType);
  }

  /**
   * Simulate a load from a given local variable of a double.
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   */
  private OPT_Instruction do_dload(int index) {
    OPT_Operand r = getLocalDual(index);
    if (VM.VerifyAssertions) VM.assert(r.isDouble());
    if (LOCALS_ON_STACK) {
      pushDual(r);
      return null;
    } else 
      return _moveDualHelper(DOUBLE_MOVE, r, VM_Type.DoubleType);
  }

  //// INCREMENT A LOCAL VARIABLE.
  /**
   * Simulate the incrementing of a given int local variable.
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   * @param amount amount to increment by
   */
  private OPT_Instruction do_iinc(int index, int amount) {
    OPT_Operand r = getLocal(index);
    if (VM.VerifyAssertions) VM.assert(r.isIntLike());
    if (LOCALS_ON_STACK) {
      replaceLocalsOnStack(index, VM_Type.IntType);
    }
    OPT_RegisterOperand op0 = gc.makeLocal(index, VM_Type.IntType);
    if (r instanceof OPT_IntConstantOperand) {
      // do constant folding.
      int res = amount + ((OPT_IntConstantOperand)r).value;
      OPT_IntConstantOperand val = new OPT_IntConstantOperand(res);
      if (CP_IN_LOCALS)
        setLocal(index, val); 
      else 
        setLocal(index, op0);
      OPT_Instruction s = Move.create(INT_MOVE, op0, val);
      s.position = gc.inlineSequence;
      s.bcIndex = instrIndex;
      return s;
    }
    setLocal(index, op0);
    return Binary.create(INT_ADD, op0, r, new OPT_IntConstantOperand(amount));
  }

  //// POP FROM STACK AND STORE INTO LOCAL VARIABLE.
  /**
   * Simulate a store into a given local variable of an int/long/double/float
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   */
  private OPT_Instruction do_store(int index, OPT_Operand op1) {
    VM_Type type = op1.getType();
    boolean Dual = (type == VM_Type.LongType) || (type == VM_Type.DoubleType);
    if (LOCALS_ON_STACK) {
      replaceLocalsOnStack(index, type);
    }
    if (ELIM_COPY_LOCALS) {
      if (op1 instanceof OPT_RegisterOperand) {
        OPT_RegisterOperand rop1 = (OPT_RegisterOperand)op1;
        OPT_Register r1 = rop1.register;
        if (lastInstr != null && ResultCarrier.conforms(lastInstr) && 
            ResultCarrier.hasResult(lastInstr) && !r1.isLocal() && 
	    r1 == ResultCarrier.getResult(lastInstr).register) {
          if (DBG_ELIMCOPY) db("eliminated copy " + op1 + " to" + index);
          OPT_RegisterOperand newop0 = gc.makeLocal(index, rop1);
          ResultCarrier.setResult(lastInstr, newop0);
          if (Dual) {
            setLocalDual(index, newop0);
          } else {
            setLocal(index, newop0);
          }
          gc.temps.release(rop1);
          return null;
        }
      }
    }
    OPT_RegisterOperand op0 = (op1 instanceof OPT_RegisterOperand) ? 
                              gc.makeLocal(index, (OPT_RegisterOperand)op1) : 
                              gc.makeLocal(index, type);
    OPT_Operand set = op0;
    if (CP_IN_LOCALS)
      set = (op1 instanceof OPT_RegisterOperand) ? op0 : op1;
    if (Dual)
      setLocalDual(index, set); 
    else 
      setLocal(index, set);
    OPT_Instruction s = 
      Move.create(OPT_IRTools.getMoveOp(type, false), op0, op1);
    s.position = gc.inlineSequence;
    s.bcIndex = instrIndex;
    return s;
  }

  /**
   * Simulate a store into a given local variable of an object ref.
   * Returns generated instruction (or null if no instruction generated.)
   *
   * @param index local variable number
   */
  private OPT_Instruction do_astore(int index) {
    OPT_Operand op1 = pop();
    if (op1 instanceof ReturnAddressOperand) {
      setLocal(index, op1);
      return null;
    }
    boolean doConstantProp = false;
    if (op1 instanceof OPT_NullConstantOperand) {
      doConstantProp = true;
    }
    VM_Type type = op1.getType();
    if (LOCALS_ON_STACK)
      replaceLocalsOnStack(index, type);
    if (ELIM_COPY_LOCALS) {
      if (op1 instanceof OPT_RegisterOperand) {
        OPT_RegisterOperand rop1 = (OPT_RegisterOperand)op1;
        OPT_Register r1 = rop1.register;
        if (lastInstr != null && ResultCarrier.conforms(lastInstr) && 
            ResultCarrier.hasResult(lastInstr) && !r1.isLocal() && 
	    r1 == ResultCarrier.getResult(lastInstr).register) {
          if (DBG_ELIMCOPY)
	    db("eliminated copy " + op1 + " to " + index);
          OPT_RegisterOperand newop0 = gc.makeLocal(index, rop1);
          ResultCarrier.setResult(lastInstr, newop0);
          setLocal(index, newop0);
          gc.temps.release(rop1);
          return null;
        }
      }
    }
    OPT_RegisterOperand op0;
    if (op1 instanceof OPT_RegisterOperand) {
      OPT_RegisterOperand rop1 = (OPT_RegisterOperand)op1;
      op0 = gc.makeLocal(index, rop1);
      if (hasGuard(rop1)) {
	OPT_RegisterOperand g0 = gc.makeNullCheckGuard(op0.register);
	appendInstruction(Move.create(GUARD_MOVE, g0.copyRO(),getGuard(rop1)));
	setGuard(op0, g0);
      }
    } else {
      op0 = gc.makeLocal(index, type);
    }
    if (CP_IN_LOCALS)
      setLocal(index, doConstantProp ? op1 : op0); 
    else 
      setLocal(index, op0);
    OPT_Instruction s = Move.create(REF_MOVE, op0, op1);
    s.position = gc.inlineSequence;
    s.bcIndex = instrIndex;
    return s;
  }

  //// PUSH OPERAND ONTO THE STACK.
  /**
   * Push a single width operand (int, float, ref, ...) on the simulated stack.
   *
   * @param r operand to push
   */
  void push(OPT_Operand r) {
    if (VM.VerifyAssertions) VM.assert(r.instruction == null);
    stack.push(r);
  }

  /**
   * Push a double width operand (long, double) on the simulated stack.
   *
   * @param r operand to push
   */
  void pushDual(OPT_Operand r) {
    if (VM.VerifyAssertions) VM.assert(r.instruction == null);
    stack.push(DUMMY);
    stack.push(r);
  }

  /**
   * Push an operand of the specified type on the simulated stack.
   *
   * @param r operand to push
   * @param type data type of operand
   */
  void push(OPT_Operand r, VM_Type type) {
    if (VM.VerifyAssertions) VM.assert(r.instruction == null);
    if (type.isVoidType())
      return;
    if (type.isLongType() || type.isDoubleType())
      pushDual(r); 
    else 
      push(r);
  }

  /**
   * Push a copy of the given operand onto simulated stack.
   *
   * @param op1 operand to push
   * @param b1 bytecode index to associate with the pushed operand
   */
  private OPT_Instruction pushCopy(OPT_Operand op1, int b1) {
    if (VM.VerifyAssertions) VM.assert(op1.instruction == null);
    if (op1 instanceof OPT_RegisterOperand) {
      OPT_RegisterOperand reg = (OPT_RegisterOperand)op1;
      if (!reg.register.isLocal())
        lastInstr = null;       // to prevent eliminating this temporary.
      stack.push(reg.copy());
    } else {
      stack.push(op1.copy());
    }
    return null;
  }

  /**
   * Push a copy of the given operand onto simulated stack.
   *
   * @param op1 operand to push
   */
  private OPT_Instruction pushCopy(OPT_Operand op1) {
    if (VM.VerifyAssertions) VM.assert(op1.instruction == null);
    if (op1 instanceof OPT_RegisterOperand) {
      OPT_RegisterOperand reg = (OPT_RegisterOperand)op1;
      if (!reg.register.isLocal())
        lastInstr = null;       // to prevent eliminating this temporary.
      stack.push(reg.copy()); 
    } else {
      stack.push(op1.copy()); 
    }
    return null;
  }

  //// POP OPERAND FROM THE STACK.
  /**
   * Pop an operand from the stack. No type checking is performed.
   */
  OPT_Operand pop() {
    return stack.pop();
  }

  /**
   * Pop an int operand from the stack.
   */
  OPT_Operand popInt() {
    OPT_Operand r = pop();
    if (VM.VerifyAssertions) VM.assert(r.isIntLike(), r.toString());
    return r;
  }

  /**
   * Pop a float operand from the stack.
   */
  OPT_Operand popFloat() {
    OPT_Operand r = pop();
    if (VM.VerifyAssertions) VM.assert(r.isFloat());
    return r;
  }

  /**
   * Pop a ref operand from the stack.
   */
  OPT_Operand popRef() {
    OPT_Operand r = pop();
    if (VM.VerifyAssertions) VM.assert(r.isRef());
    return r;
  }

  /**
   * Pop a long operand from the stack.
   */
  OPT_Operand popLong() {
    OPT_Operand r = pop();
    if (VM.VerifyAssertions) VM.assert(r.isLong());
    popDummy();
    return r;
  }

  /**
   * Pop a double operand from the stack.
   */
  OPT_Operand popDouble() {
    OPT_Operand r = pop();
    if (VM.VerifyAssertions) VM.assert(r.isDouble());
    popDummy();
    return r;
  }

  /**
   * Pop a dummy operand from the stack.
   */
  void popDummy() {
    OPT_Operand r = pop();
    if (VM.VerifyAssertions) VM.assert(r == DUMMY);
  }

  /**
   * Pop an operand of the given type from the stack.
   */
  OPT_Operand pop(VM_Type type) {
    OPT_Operand r = pop();
    // Can't assert the following due to approximations by 
    // OPT_ClassLoaderProxy.proxy.findCommonSuperclass
    // if (VM.VerifyAssertions) assertIsType(r, type);
    if (type.isLongType() || type.isDoubleType())
      popDummy();
    return r;
  }


  //// SUBROUTINES.
  private OPT_Instruction _jsrHelper(int offset) {
    // (1) notify the BBSet that we have reached a JSR bytecode.
    //     This enables the more complex JSR-aware implementation of 
    //     BBSet.getOrCreateBlock.
    blocks.seenJSR(); 

    // (2) push return address on expression stack
    push(new ReturnAddressOperand(bcInfo.bcIndex));

    // (3) generate GOTO to subroutine body.
    OPT_BranchOperand branch = generateTarget(offset);
    return Goto.create(GOTO, branch);
  }

  private OPT_Instruction _retHelper(int var) {
    // (1) consume the return address from the specified local variable
    OPT_Operand local = getLocal(var);
    ReturnAddressOperand ra = (ReturnAddressOperand)local;
    setLocal(var, null); // must set local null before calling getOrCreateBlock!!
    BasicBlockLE rb = getOrCreateBlock(ra.retIndex);

    // (2) generate a GOTO to the return site.
    currentBBLE.block.insertOut(rb.block);
    endOfBasicBlock = true;
    if (DBG_CFG) db("Added CFG edge from "+currentBBLE.block+" to "+ rb.block);
    return Goto.create(GOTO, rb.block.makeJumpTarget());
  }

  //// GET TYPE OF AN OPERAND.
  /**
   * Return the data type of the given operand, assuming that the operand is
   * an array reference. (and not a null constant.)
   *
   * @param op operand to get type of
   */
  private VM_Type getArrayTypeOf(OPT_Operand op) {
    if (VM.VerifyAssertions) VM.assert(!op.isDefinitelyNull());
    return op.asRegister().type;
  }

  /**
   * Return the data type of the given operand, assuming that the operand is
   * a reference. (and not a null constant.)
   *
   * @param op operand to get type of
   */
  private VM_Type getRefTypeOf(OPT_Operand op) {
    if (VM.VerifyAssertions) VM.assert(!op.isDefinitelyNull());
    // op must be a RegisterOperand or StringConstantOperand
    if (op instanceof OPT_StringConstantOperand)
      return VM_Type.JavaLangStringType; 
    else 
      return op.asRegister().type;
  }

  //// HELPER FUNCTIONS FOR ASSERTION VERIFICATION
  /**
   * Assert that the given operand is of the given type, or of
   * a subclass of the given type.
   *
   * @param op operand to check
   * @param type expected type of operand
   */
  private void assertIsType(OPT_Operand op, VM_Type type) {
    if (VM.VerifyAssertions) {
      if (op.isDefinitelyNull()) {
        VM.assert(type.isReferenceType());
      } else if (op.isIntLike()) {
        VM.assert(type.isIntLikeType());
      } else {
        VM_Type type1 = op.getType();
        VM.assert(OPT_ClassLoaderProxy.proxy.isAssignableWith(type, type1)
		  != NO);
      }
    }
  }

  /**
   * Assert that the given child type is a subclass of the given parent type.
   *
   * @param parentType parent type
   * @param childType child type
   */
  private void assertIsAssignable(VM_Type parentType, VM_Type childType) {
    if (VM.VerifyAssertions)
      VM.assert(OPT_ClassLoaderProxy.proxy.isAssignableWith(parentType, 
							    childType) != NO);
  }

  //// DEBUGGING.
  /**
   * Print a debug string to the sysWrite stream
   *
   * @param val string to print
   */
  private void db(String val) {
    VM.sysWrite("IRGEN " + gc.method.getDeclaringClass().getName() + "."
		+ gc.method.getName() + ":" + val + "\n");
  }
  /**
   * Return a string representation of the current basic block set.
   */
  private String printBlocks() {
    StringBuffer res = new StringBuffer();
    for (Enumeration e = blocks.contents(); e.hasMoreElements();) {
      BasicBlockLE b = (BasicBlockLE)e.nextElement();
      if (b == currentBBLE)
	res.append("*");
      res.append(b.toString());
      res.append(" ");
    }
    return res.toString();
  }


  //// GENERATE CHECK INSTRUCTIONS.
  private OPT_Operand currentGuard;

  public static boolean isNonNull(OPT_Operand op) {
    if (op instanceof OPT_RegisterOperand) {
      OPT_RegisterOperand rop = (OPT_RegisterOperand)op;
      if (VM.VerifyAssertions)
        VM.assert((rop.scratchObject == null) || 
                  (rop.scratchObject instanceof OPT_RegisterOperand) || 
                  (rop.scratchObject instanceof OPT_TrueGuardOperand));
      return rop.scratchObject != null;
    } else if (op instanceof OPT_StringConstantOperand) {
      return true;
    } else {
      return false;
    }
  }

  public static boolean hasGuard(OPT_RegisterOperand rop) {
    return rop.scratchObject != null;
  }

  public static boolean hasLessConservativeGuard(OPT_RegisterOperand rop1, 
						 OPT_RegisterOperand rop2) {
    if (rop1.scratchObject == rop2.scratchObject)
      return false;
    if (rop1.scratchObject instanceof OPT_Operand) {
      if (rop2.scratchObject instanceof OPT_Operand) {
	OPT_Operand op1 = (OPT_Operand)rop1.scratchObject;
	OPT_Operand op2 = (OPT_Operand)rop2.scratchObject;
	if (op2 instanceof OPT_TrueGuardOperand) {
	  // rop2 is top therefore rop1 can't be less conservative!
	  return false; 
	} else {
	  return !(op1.similar(op2));
	}
      } else {
        return true;
      }
    } else {
      // rop1 is bottom, therefore is most conservative guard possible
      return false; 
    }
  }
  
  public void markGuardlessNonNull(OPT_RegisterOperand rop) {
    OPT_RegisterOperand g = gc.makeNullCheckGuard(rop.register);
    appendInstruction(Move.create(GUARD_MOVE, g, new OPT_TrueGuardOperand()));
    rop.scratchObject = g.copy();
  }

  public static OPT_Operand getGuard(OPT_Operand op) {
    if (op instanceof OPT_RegisterOperand) {
      OPT_RegisterOperand rop = (OPT_RegisterOperand)op;
      if (VM.VerifyAssertions) {
        VM.assert((rop.scratchObject == null) || 
                  (rop.scratchObject instanceof OPT_RegisterOperand)
                  || (rop.scratchObject instanceof OPT_TrueGuardOperand));
      }
      return (OPT_Operand)rop.scratchObject;
    }
    if (VM.VerifyAssertions)
      VM.assert(op instanceof OPT_StringConstantOperand);
    return new OPT_TrueGuardOperand();
  }

  public static void setGuard(OPT_RegisterOperand rop, OPT_Operand guard) {
    rop.scratchObject = guard;
  }

  private void setCurrentGuard(OPT_Operand guard) {
    if (currentGuard instanceof OPT_RegisterOperand) {
      if (VM.VerifyAssertions)
        VM.assert(!(guard instanceof OPT_TrueGuardOperand));
      // shouldn't happen given current generation --dave.
      OPT_RegisterOperand combined = gc.temps.makeTempValidation();
      appendInstruction(Binary.create(GUARD_COMBINE, combined, 
				      getCurrentGuard(), guard.copy()));
      currentGuard = combined;
    } else {
      currentGuard = guard;
    }
  }

  private void clearCurrentGuard() {
    currentGuard = null;
  }

  private OPT_Operand getCurrentGuard() {
    // This check is needed for when guards are (unsafely) turned off
    if (currentGuard!=null)
      return currentGuard.copy();
    return null;
  }

  /**
   * Generate a null-check instruction for the given operand.
   * @return true if an unconditional throw is generated, false otherwise
   */
  private boolean do_NullCheck(OPT_Operand ref) {
    if (gc.options.NO_NULL_CHECK)
      return false;
    if (ref.isDefinitelyNull()) {
      if (DBG_CF) db("generating definite exception: null_check of definitely null");
      endOfBasicBlock = true;
      rectifyStateWithNullPtrExceptionHandler();
      appendInstruction(Trap.create(TRAP, gc.temps.makeTempValidation(), 
				    OPT_TrapCodeOperand.NullPtr()));
      return true;
    }
    if (ref instanceof OPT_RegisterOperand) {
      OPT_RegisterOperand rop = (OPT_RegisterOperand)ref;
      if (hasGuard(rop)) {
        OPT_Operand guard = getGuard(rop);
        setCurrentGuard(guard);
        if (DBG_ELIMNULL)
          db("null check of "+ref+" is not necessary; guarded by "+guard);
        return false;
      }
      // rop is possibly null, insert the null check, 
      // rectify with exception handler, update the guard state.
      OPT_RegisterOperand guard = gc.makeNullCheckGuard(rop.register);
      appendInstruction(NullCheck.create(NULL_CHECK, guard, ref.copy()));
      rectifyStateWithNullPtrExceptionHandler();
      setCurrentGuard(guard);
      setGuard(rop, guard);
      if (DBG_ELIMNULL) db(rop + " is guarded by " + guard);
      // Now, try to leverage this null check by updating 
      // other unguarded (and thus potentially null)
      // OPT_RegisterOperands representing the same OPT_Register.
      if (rop.register.isLocal()) {
        // We want to learn that downstream of this nullcheck, other
        // uses of this local variable will also be non-null.
        // BUT, we MUST NOT just directly set the guard of the appropriate
        // element of our locals array (operands in the local array 
        // may appear in previously generated instructions).
        // Therefore we call getLocal (which internally makes a copy), 
        // mark the copy with the new guard
        // and finally store the copy back into the local state.
        int number = gc.getLocalNumberFor(rop.register, rop.type);
        if (number != -1) {
          OPT_Operand loc = getLocal(number);
          if (loc instanceof OPT_RegisterOperand) {
            if (DBG_ELIMNULL)
              db("setting local #" + number + "(" + loc + ") to non-null");
            setGuard((OPT_RegisterOperand)loc, guard);
          }
          setLocal(number, loc);
        }
      }
      // At least within this basic block we know that all subsequent uses 
      // of ref will be non null, since they are guarded by the null check 
      // instruction we just inserted.  Update all RegisterOperands with 
      // this register currently on the expression stack appropriately.
      // Stack rectification will ensure that we don't propagate this 
      // non-nullness to a use that is not dominated by the null check in 
      // the current basic block.
      for (int i = stack.getSize() - 1; i >= 0; --i) {
        OPT_Operand sop = stack.getFromTop(i);
        if (sop instanceof OPT_RegisterOperand) {
          OPT_RegisterOperand sreg = (OPT_RegisterOperand)sop;
          if (sreg.register == rop.register) {
            if (hasGuard(sreg)) {
              if (DBG_ELIMNULL)
                db(sreg+" on stack already with guard "+getGuard(sreg));
            } else {
              if (DBG_ELIMNULL)
                db("setting "+sreg+" on stack to be guarded by "+guard);
              setGuard(sreg, guard);
            }
          }
        }
      }
      return false;
    } else {
      // cannot be null becuase it's not in a register.
      if (DBG_ELIMNULL)
        db("skipped generation of a null-check instruction for non-register "
	   + ref);
      setCurrentGuard(new OPT_TrueGuardOperand());
      return false;
    }
  }

  /**
   * Generate a boundscheck instruction for the given operand and index.
   * @return true if an unconditional throw is generated, false otherwise
   */
  private boolean do_BoundsCheck(OPT_Operand ref, OPT_Operand index) {
    // Unsafely eliminate all bounds checks
    if (gc.options.NO_BOUNDS_CHECK)
      return false;
    OPT_RegisterOperand guard = gc.temps.makeTempValidation();
    appendInstruction(BoundsCheck.create(BOUNDS_CHECK, guard, ref.copy(), 
					 index.copy(), getCurrentGuard()));
    setCurrentGuard(guard);
    rectifyStateWithArrayBoundsExceptionHandler();
    return false;
  }

  /**
   * Generate a check for 0 for the given operand
   * @return true if an unconditional trap is generated, false otherwise
   */
  private boolean do_IntZeroCheck(OPT_Operand div) {
    if (div instanceof OPT_IntConstantOperand) {
      if (((OPT_IntConstantOperand)div).value == 0) {
	endOfBasicBlock = true;
	rectifyStateWithArithmeticExceptionHandler();
	appendInstruction(Trap.create(TRAP, gc.temps.makeTempValidation(), 
				      OPT_TrapCodeOperand.DivByZero()));
        return true;
      } else {
        if (DBG_CF)
          db("skipped gen of int_zero_check of " + div.asIntConstant().value);
        setCurrentGuard(new OPT_TrueGuardOperand());
        return false;
      }
    }
    OPT_RegisterOperand guard = gc.temps.makeTempValidation();
    appendInstruction(ZeroCheck.create(INT_ZERO_CHECK, guard, div.copy()));
    setCurrentGuard(guard);
    rectifyStateWithArithmeticExceptionHandler();
    return false;
  }

  /**
   * Generate a check for 0 for the given operand
   * @return true if an unconditional trap is generated, false otherwise
   */
  private boolean do_LongZeroCheck(OPT_Operand div) {
    if (div instanceof OPT_LongConstantOperand) {
      if (((OPT_LongConstantOperand)div).value == 0) {
	endOfBasicBlock = true;
	rectifyStateWithArithmeticExceptionHandler();
	appendInstruction(Trap.create(TRAP, gc.temps.makeTempValidation(), 
				      OPT_TrapCodeOperand.DivByZero()));
        return true;
      } else {
        if (DBG_CF)
          db("skipped gen of long_zero_check of "+div.asLongConstant().value);
        setCurrentGuard(new OPT_TrueGuardOperand());
        return false;
      }
    }
    OPT_RegisterOperand guard = gc.temps.makeTempValidation();
    appendInstruction(ZeroCheck.create(LONG_ZERO_CHECK, guard, div.copy()));
    setCurrentGuard(guard);
    rectifyStateWithArithmeticExceptionHandler();
    return false;
  }

  /**
   * Generate a storecheck for the given array and elem
   * @return true if an unconditional throw is generated, false otherwise
   */
  private boolean do_CheckStore(OPT_Operand ref, OPT_Operand elem, 
                                 VM_Type elemType) {
    if (gc.options.NO_CHECKSTORE)
      return false;     // Unsafely eliminate all store checks
    if (CF_CHECKSTORE) {
      // NOTE: BE WARY OF ADDITIONAL OPTIMZATIONS. 
      // ARRAY SUBTYPING IS SUBTLE (see JLS 10.10) --dave
      if (elem.isDefinitelyNull()) {
        if (DBG_TYPE) db("skipping checkstore of null constant");
        return false;
      }
      if (elemType.isArrayType()) {
        VM_Type elemType2 = elemType;
        do {
          elemType2 = ((VM_Array)elemType2).getElementType();
        } while (elemType2.isArrayType());
        if (elemType2.isPrimitiveType() || 
	    (elemType2.isLoaded() && ((VM_Class)elemType2).isFinal())) {
          VM_Type myElemType = getRefTypeOf(elem);
          if (myElemType == elemType) {
            if (DBG_TYPE)
              db("eliminating checkstore to an array with a final element type "
		 + elemType);
            return false;
          } else {
	    // run time check is still necessary
          }
        }
      } else {
        // elemType is class
        if (elemType.isLoaded() && ((VM_Class)elemType).isFinal()) {
          if (getRefTypeOf(elem) == elemType) {
            if (DBG_TYPE)
              db("eliminating checkstore to an array with a final element type "
		 + elemType);
            return false;
          } else {
	    // run time check is still necessary
          }
        }
      }
    }

    OPT_RegisterOperand guard = gc.temps.makeTempValidation();
    appendInstruction(StoreCheck.create(OBJARRAY_STORE_CHECK, guard, 
					ref.copy(), elem.copy(), 
					getCurrentGuard()));
    setCurrentGuard(guard);
    rectifyStateWithArrayStoreExceptionHandler();
    return false;
  }

  //// GENERATE BRANCHING INSTRUCTIONS.
  /**
   * Get or create a block at the specified target.
   * Rectifies current state with target state. 
   * Instructions to rectify state are appended to currentBBLE.
   * If the target is between bcInfo.bcIndex and runoff, runoff is
   * updated to be target.
   *
   * @param target target index
   */
  private BasicBlockLE getOrCreateBlock(int target) {
    return getOrCreateBlock(target, currentBBLE, stack, _localState);
  }

  /**
   * Get or create a block at the specified target.
   * If simStack is non-null, rectifies stack state with target stack state.
   * If simLocals is non-null, rectifies local state with target local state.
   * Any instructions needed to rectify stack/local state are appended to from.
   * If the target is between bcInfo.bcIndex and runoff, runoff is
   * updated to be target.
   *
   * @param target target index
   * @param from the block from which control is being transfered
   *                  and to which stack rectification instructions are added.
   * @param simStack stack state to rectify, or null
   * @param simLocals local state to rectify, or null
   */
  private BasicBlockLE getOrCreateBlock(int target, 
					BasicBlockLE from, 
					OperandStack simStack, 
					OPT_Operand[] simLocals) {
    if ((target > bcInfo.bcIndex) && (target < runoff)) {
      if (DBG_BB) db("updating runoff from " + runoff + " to " + target);
      runoff = target;
    }
    return blocks.getOrCreateBlock(target, from, simStack, simLocals);
  }


  private OPT_BranchOperand generateTarget(int offset) {
    BasicBlockLE targetbble = getOrCreateBlock(offset + instrIndex);
    currentBBLE.block.insertOut(targetbble.block);
    endOfBasicBlock = true;
    if (DBG_CFG)
      db("Added CFG edge from "+currentBBLE.block+" to "+ targetbble.block);
    return targetbble.block.makeJumpTarget();
  }
  
  // GOTO
  private OPT_Instruction _gotoHelper(int offset) {
    return Goto.create(GOTO, generateTarget(offset));
  }

  // helper function for if?? bytecodes
  private OPT_Instruction _intIfHelper(OPT_ConditionOperand cond) {
    int offset = bcInfo.getBranchTarget();
    OPT_Operand op0 = popInt();
    if (offset == 3)
      return null;             // remove frivolous IFs
    if (CF_INTIF && op0 instanceof OPT_IntConstantOperand) {
      if (cond.evaluate(((OPT_IntConstantOperand)op0).value, 0)) {
        if (DBG_CF)
          db(cond + ": changed branch to goto because predicate (" + op0
	     + ") is constant true");
        return _gotoHelper(offset);
      } else {
        if (DBG_CF)
          db(cond + ": eliminated branch because predicate (" + op0 + 
	     ") is constant false");
        return null;
      }
    }
    fallThrough = true;
    if (!(op0 instanceof OPT_RegisterOperand)) {
      if (DBG_CF) db("generated int_ifcmp of "+op0+" with 0");
      OPT_RegisterOperand guard = gc.temps.makeTempValidation();
      return IfCmp.create(INT_IFCMP, guard, op0, 
			  new OPT_IntConstantOperand(0), 
			  cond, generateTarget(offset),
			  new OPT_BranchProfileOperand());
    }
    OPT_RegisterOperand val = (OPT_RegisterOperand)op0;
    OPT_BranchOperand branch = null;
    if (lastInstr != null) {
      switch (lastInstr.getOpcode()) {
      case INSTANCEOF_opcode:
	{
	  if (DBG_TYPE) db("last instruction was instanceof");
	  OPT_RegisterOperand res = InstanceOf.getResult(lastInstr);
	  if (DBG_TYPE) db("result was in "+res+", we are checking "+val);
	  if (val.register != res.register)
	    break;            // not our value
	  OPT_Operand ref = InstanceOf.getRef(lastInstr);
	  // should've been constant folded anyway
	  if (!(ref instanceof OPT_RegisterOperand))
	    break;
	  OPT_RegisterOperand guard = null;
	  // Propagate types and non-nullness along the CFG edge where we 
	  // know that refReg is an instanceof type2
	  OPT_RegisterOperand refReg = (OPT_RegisterOperand)ref;
	  VM_Type type2 = InstanceOf.getType(lastInstr).type;
	  if (cond.isNOT_EQUAL()) {
	    // IS an instance of on the branch-taken edge
	    boolean generated = false;
	    if (refReg.register.isLocal()) {
	      int locNum = gc.getLocalNumberFor(refReg.register, refReg.type);
	      if (locNum != -1) {
		OPT_Operand loc = getLocal(locNum);
		if (loc instanceof OPT_RegisterOperand) {
		  if (DBG_TYPE)
		    db(val + 
		       " is from instanceof test, propagating new type of "
		       + refReg + " (" + type2 + ") to basic block at "
		       + offset);
		  OPT_RegisterOperand locr = (OPT_RegisterOperand)loc;
		  OPT_RegisterOperand tlocr = locr.copyU2U();
		  guard = gc.makeNullCheckGuard(tlocr.register);
		  setGuard(tlocr, guard.copyD2U());
		  tlocr.type = type2;
		  tlocr.clearDeclaredType();
		  tlocr.clearPreciseType();
		  setLocal(locNum, tlocr);
		  branch = generateTarget(offset);
		  generated = true;
		  setLocal(locNum, locr);
		}
	      }
	    }
	    if (!generated) {
	      branch = generateTarget(offset);
	    }
	  } else if (cond.isEQUAL()) {
	    // IS an instance of on the fallthrough edge.
	    branch = generateTarget(offset);
	    if (refReg.register.isLocal()) {
	      int locNum = gc.getLocalNumberFor(refReg.register, refReg.type);
	      if (locNum != -1) {
		OPT_Operand loc = getLocal(locNum);
		if (loc instanceof OPT_RegisterOperand) {
		  if (DBG_TYPE)
		    db(val + 
		       " is from instanceof test, propagating new type of "
		       + refReg + " (" + type2 + ") along fallthrough edge");
		  OPT_RegisterOperand locr = (OPT_RegisterOperand)loc;
		  guard = gc.makeNullCheckGuard(locr.register);
		  setGuard(locr, guard.copyD2U());
		  locr.type = type2;
		  locr.clearDeclaredType();
		  setLocal(locNum, loc);
		}
	      }
	    }
	  }
	  if (guard == null)
	    guard = gc.temps.makeTempValidation();
	  return IfCmp.create(INT_IFCMP, guard, val, 
			      new OPT_IntConstantOperand(0), 
			      cond, branch,new OPT_BranchProfileOperand());
	}
      case INSTANCEOF_NOTNULL_opcode:
	{
	  if (DBG_TYPE) db("last instruction was instanceof");
	  OPT_RegisterOperand res = InstanceOf.getResult(lastInstr);
	  if (DBG_TYPE)
	    db("result was in " + res + ", we are checking " + val);
	  if (val.register != res.register)
	    break;            // not our value
	  OPT_Operand ref = InstanceOf.getRef(lastInstr);
	  // should've been constant folded anyway
	  if (!(ref instanceof OPT_RegisterOperand))
	    break;
	  // Propagate types along the CFG edge where we know that 
	  // refReg is an instanceof type2
	  OPT_RegisterOperand refReg = (OPT_RegisterOperand)ref;
	  VM_Type type2 = InstanceOf.getType(lastInstr).type;
	  if (cond.isNOT_EQUAL()) {
	    // IS an instance of on the branch-taken edge
	    boolean generated = false;
	    if (refReg.register.isLocal()) {
	      int locNum = gc.getLocalNumberFor(refReg.register, refReg.type);
	      if (locNum != -1) {
		OPT_Operand loc = getLocal(locNum);
		if (loc instanceof OPT_RegisterOperand) {
		  if (DBG_TYPE)
		    db(val + 
		       " is from instanceof test, propagating new type of "
		       + refReg + " (" + type2 + ") to basic block at "
		       + offset);
		  OPT_RegisterOperand locr = (OPT_RegisterOperand)loc;
		  OPT_RegisterOperand tlocr = locr.copyU2U();
		  tlocr.type = type2;
		  tlocr.clearDeclaredType();
		  tlocr.clearPreciseType();
		  setLocal(locNum, tlocr);
		  branch = generateTarget(offset);
		  generated = true;
		  setLocal(locNum, locr);
		}
	      }
	    }
	    if (!generated) {
	      branch = generateTarget(offset);
	    }
	  } else if (cond.isEQUAL()) {
	    // IS an instance of on the fallthrough edge.
	    branch = generateTarget(offset);
	    if (refReg.register.isLocal()) {
	      int locNum = gc.getLocalNumberFor(refReg.register, refReg.type);
	      if (locNum != -1) {
		OPT_Operand loc = getLocal(locNum);
		if (loc instanceof OPT_RegisterOperand) {
		  if (DBG_TYPE)
		    db(val + 
		       " is from instanceof test, propagating new type of "
		       + refReg + " (" + type2 + ") along fallthrough edge");
		  OPT_RegisterOperand locr = (OPT_RegisterOperand)loc;
		  locr.type = type2;
		  locr.clearDeclaredType();
		  setLocal(locNum, loc);
		}
	      }
	    }
	  }
	  OPT_RegisterOperand guard = gc.temps.makeTempValidation();
	  return IfCmp.create(INT_IFCMP, guard, val, 
			      new OPT_IntConstantOperand(0), 
			      cond, branch,new OPT_BranchProfileOperand());
	}
      case DOUBLE_CMPG_opcode:case DOUBLE_CMPL_opcode:
      case FLOAT_CMPG_opcode:case FLOAT_CMPL_opcode:case LONG_CMP_opcode:
	{
	  OPT_RegisterOperand res = Binary.getResult(lastInstr);
	  if (val.register != res.register)
	    break;            // not our value
	  OPT_Operator operator = null;
	  switch (lastInstr.getOpcode()) {
	  case DOUBLE_CMPG_opcode:
	    operator = DOUBLE_IFCMPG;
	    break;
	  case DOUBLE_CMPL_opcode:
	    operator = DOUBLE_IFCMPL;
	    break;
	  case FLOAT_CMPG_opcode:
	    operator = FLOAT_IFCMPG;
	    break;
	  case FLOAT_CMPL_opcode:
	    operator = FLOAT_IFCMPL;
	    break;
	  case LONG_CMP_opcode:
	    operator = LONG_IFCMP;
	    break;
	  default:
	    OPT_OptimizingCompilerException.UNREACHABLE();
	    break;
	  }
	  OPT_Operand val1 = Binary.getClearVal1(lastInstr);
	  OPT_Operand val2 = Binary.getClearVal2(lastInstr);
	  if (!(val1 instanceof OPT_RegisterOperand)) {
	    // swap operands
	    OPT_Operand temp = val1;
	    val1 = val2;
	    val2 = temp;
	    cond = cond.flipOperands();
	  }
	  lastInstr.remove();
	  lastInstr = null;
	  branch = generateTarget(offset);
	  OPT_RegisterOperand guard = gc.temps.makeTempValidation();
	  return IfCmp.create(operator, guard, val1, val2, cond, 
			      branch, new OPT_BranchProfileOperand());
	}
      default:
	// Fall through and Insert INT_IFCMP
	break;
      }
    }
    branch = generateTarget(offset);
    OPT_RegisterOperand guard = gc.temps.makeTempValidation();
    return IfCmp.create(INT_IFCMP, guard, val, 
			new OPT_IntConstantOperand(0), 
			cond, branch, new OPT_BranchProfileOperand());
  }

  // helper function for if_icmp?? bytecodes
  private OPT_Instruction _intIfCmpHelper(OPT_ConditionOperand cond) {
    int offset = bcInfo.getBranchTarget();
    OPT_Operand op1 = popInt();
    OPT_Operand op0 = popInt();
    if (offset == 3)
      return null;             // remove frivolous INF_IFCMPs
    if (!(op0 instanceof OPT_RegisterOperand)) {
      // swap operands
      OPT_Operand temp = op0;
      op0 = op1;
      op1 = temp;
      cond = cond.flipOperands();
    }
    if (CF_INTIFCMP && 
	(op0 instanceof OPT_IntConstantOperand) && 
	(op1 instanceof OPT_IntConstantOperand)) {
      if (cond.evaluate(((OPT_IntConstantOperand)op0).value, 
			((OPT_IntConstantOperand)op1).value)) {
        if (DBG_CF)
          db(cond + ": changed branch to goto because predicate (" + op0
	     + ", " + op1 + ") is constant true");
        return _gotoHelper(offset);
      } else {
        if (DBG_CF)
          db(cond + ": eliminated branch because predicate (" + op0 + 
	     "," + op1 + ") is constant false");
        return null;
      }
    }
    fallThrough = true;
    OPT_RegisterOperand guard = gc.temps.makeTempValidation();
    return IfCmp.create(INT_IFCMP, guard, op0, op1, cond, 
			generateTarget(offset),
			new OPT_BranchProfileOperand());
  }

  // helper function for ifnull/ifnonnull bytecodes
  private OPT_Instruction _refIfNullHelper(OPT_ConditionOperand cond) {
    if (VM.VerifyAssertions) VM.assert(cond.isEQUAL() || cond.isNOT_EQUAL());
    int offset = bcInfo.getBranchTarget();
    OPT_Operand op0 = popRef();
    if (offset == 3)
      return null;             // remove frivolous REF_IFs
    if (CF_REFIF) {
      if (op0.isDefinitelyNull()) {
        if (cond.isEQUAL()) {
          if (DBG_CF)
            db(cond+": changed branch to goto because predicate is true");
          return _gotoHelper(offset);
        } else {
          if (DBG_CF)
            db(cond+": eliminated branch because predicate is false");
          return null;
        }
      }
      if (isNonNull(op0)) {
        if (cond.isNOT_EQUAL()) {
          if (DBG_CF)
            db(cond + ": changed branch to goto because predicate is true");
          return _gotoHelper(offset);
        } else {
          if (DBG_CF)
            db(cond + ": eliminated branch because predicate is false");
          return null;
        }
      }
    }
    OPT_RegisterOperand ref = (OPT_RegisterOperand)op0;
    OPT_BranchOperand branch = null;
    OPT_RegisterOperand guard = null;
    if (cond.isEQUAL()) {
      branch = generateTarget(offset);
      if (ref.register.isLocal()) {
        int locNum = gc.getLocalNumberFor(ref.register, ref.type);
        if (locNum != -1) {
          OPT_Operand loc = getLocal(locNum);
          if (loc instanceof OPT_RegisterOperand) {
            OPT_RegisterOperand locr = (OPT_RegisterOperand)loc;
            guard = gc.makeNullCheckGuard(locr.register);
            setGuard(locr, guard.copyD2U());
            setLocal(locNum, loc);
          }
        }
      }
    } else {
      boolean generated = false;
      if (ref.register.isLocal()) {
        int locNum = gc.getLocalNumberFor(ref.register, ref.type);
        if (locNum != -1) {
          OPT_Operand loc = getLocal(locNum);
          if (loc instanceof OPT_RegisterOperand) {
            OPT_RegisterOperand locr = (OPT_RegisterOperand)loc;
            OPT_RegisterOperand tlocr = locr.copyU2U();
            guard = gc.makeNullCheckGuard(locr.register);
            setGuard(tlocr, guard.copyD2U());
            setLocal(locNum, tlocr);
            branch = generateTarget(offset);
            generated = true;
            setLocal(locNum, locr);
          }
        }
      }
      if (!generated) {
        branch = generateTarget(offset);
      }
    }
    fallThrough = true;
    if (guard == null)
      guard = gc.temps.makeTempValidation();
    return IfCmp.create(REF_IFCMP, guard, ref, 
			new OPT_NullConstantOperand(), cond, branch,
			new OPT_BranchProfileOperand());
  }
  
  // helper function for if_acmp?? bytecodes
  private OPT_Instruction _refIfCmpHelper(OPT_ConditionOperand cond) {
    if (VM.VerifyAssertions) VM.assert(cond.isEQUAL() || cond.isNOT_EQUAL());
    int offset = bcInfo.getBranchTarget();
    OPT_Operand op1 = popRef();
    OPT_Operand op0 = popRef();
    if (offset == 3)
      return null;             // remove frivolous REF_IFCMPs
    if (!(op0 instanceof OPT_RegisterOperand)) {
      // swap operands
      OPT_Operand temp = op0;
      op0 = op1;
      op1 = temp;
      cond = cond.flipOperands();
    }
    if (CF_REFIFCMP && op0.isDefinitelyNull() && op1.isDefinitelyNull()) {
      if (cond.isEQUAL()) {
        if (DBG_CF)
          db(cond + ": changed branch to goto because predicate is true");
        return _gotoHelper(offset);
      } else {
        if (DBG_CF)
          db(cond + ": eliminated branch because predicate is false");
        return null;
      }
    }
    fallThrough = true;
    OPT_RegisterOperand guard = gc.temps.makeTempValidation();
    return IfCmp.create(REF_IFCMP, guard, op0, op1, 
			cond, generateTarget(offset),
			new OPT_BranchProfileOperand());
  }

  //// REPLACE LOCALS ON STACK.
  // Replaces copies of local <#index,type> with 
  // newly-generated temporaries, and
  // generates the necessary move instructions.
  private void replaceLocalsOnStack(int index, VM_Type type) {
    int i;
    int size = stack.getSize();
    for (i = 0; i < size; ++i) {
      OPT_Operand op = stack.getFromTop(i);
      if (gc.isLocal(op, index, type)) {
        OPT_RegisterOperand lop = (OPT_RegisterOperand)op;
        OPT_RegisterOperand t = gc.temps.makeTemp(lop);
        OPT_Instruction s = 
	  Move.create(OPT_IRTools.getMoveOp(t.type, false), t, op);
        stack.replaceFromTop(i, t.copyD2U());
        s.position = gc.inlineSequence;
        s.bcIndex = instrIndex;
        if (DBG_LOCAL)
          db("replacing local "+index+" at "+i+" from tos with "+ t);
        appendInstruction(s);
      }
    }
  }

  //////////
  // EXCEPTION HANDLERS.
  //////////
  // Some common cases to make the code more readable...
  private OPT_BasicBlock rectifyStateWithNullPtrExceptionHandler() {
    return rectifyStateWithNullPtrExceptionHandler(false);
  }
  private OPT_BasicBlock rectifyStateWithArrayBoundsExceptionHandler() {
    return rectifyStateWithArrayBoundsExceptionHandler(false);
  }
  private OPT_BasicBlock rectifyStateWithArithmeticExceptionHandler() {
    return rectifyStateWithArithmeticExceptionHandler(false);
  }
  private OPT_BasicBlock rectifyStateWithArrayStoreExceptionHandler() {
    return rectifyStateWithArrayStoreExceptionHandler(false);
  }
  private OPT_BasicBlock rectifyStateWithErrorHandler() {
    return rectifyStateWithErrorHandler(false);
  }
  private void rectifyStateWithExceptionHandlers() {
    rectifyStateWithExceptionHandlers(false);
  }

  private OPT_BasicBlock rectifyStateWithExceptionHandler(VM_Type exceptionType) {
    return rectifyStateWithExceptionHandler(exceptionType, false);
  }
  private OPT_BasicBlock rectifyStateWithNullPtrExceptionHandler(boolean linkToExitIfUncaught) {
    VM_Type et = OPT_ClassLoaderProxy.JavaLangNullPointerExceptionType;
    return rectifyStateWithExceptionHandler(et, linkToExitIfUncaught);
  }
  private OPT_BasicBlock rectifyStateWithArrayBoundsExceptionHandler(boolean linkToExitIfUncaught) {
    VM_Type et = OPT_ClassLoaderProxy.JavaLangArrayIndexOutOfBoundsExceptionType;
    return rectifyStateWithExceptionHandler(et, linkToExitIfUncaught);
  }
  private OPT_BasicBlock rectifyStateWithArithmeticExceptionHandler(boolean linkToExitIfUncaught) {
    VM_Type et = OPT_ClassLoaderProxy.JavaLangArithmeticExceptionType;
    return rectifyStateWithExceptionHandler(et, linkToExitIfUncaught);
  }
  private OPT_BasicBlock rectifyStateWithArrayStoreExceptionHandler(boolean linkToExitIfUncaught) {
    VM_Type et = OPT_ClassLoaderProxy.JavaLangArrayStoreExceptionType;
    return rectifyStateWithExceptionHandler(et, linkToExitIfUncaught);
  }
  private OPT_BasicBlock rectifyStateWithErrorHandler(boolean linkToExitIfUncaught) {
    VM_Type et = OPT_ClassLoaderProxy.JavaLangErrorType;
    return rectifyStateWithExceptionHandler(et, linkToExitIfUncaught);
  }

  // If exactly 1 catch block is guarenteed to catch the exception, 
  // then we return it.
  // Returning null means that no such block was found.
  private OPT_BasicBlock rectifyStateWithExceptionHandler(VM_Type exceptionType, 
							  boolean linkToExitIfUncaught) {
    currentBBLE.block.setCanThrowExceptions();
    int catchTargets = 0;
    if (currentBBLE.handlers != null) {
      for (int i = 0; i < currentBBLE.handlers.length; i++) {
        HandlerBlockLE xbble = currentBBLE.handlers[i];
        byte mustCatch = xbble.mustCatchException(exceptionType);
        if (mustCatch != NO || 
	    xbble.mayCatchException(exceptionType) != NO) {
          if (DBG_EX)
            db("PEI of type " + exceptionType + " could be caught by "
	       + xbble + " rectifying locals");
          catchTargets++;
          blocks.rectifyLocals(_localState, xbble);
          currentBBLE.block.insertOut(xbble.entryBlock);
          if (DBG_CFG)
            db("Added CFG edge from " + currentBBLE.block + 
               " to " + xbble.entryBlock);
        }
        if (mustCatch == YES) {
          if (DBG_EX)
            db("\t" + xbble + " will defintely catch exceptions of type "
	       + exceptionType);
          if (DBG_EX && catchTargets == 1)
            db("\t  and it is the only target");
          return (catchTargets == 1) ? xbble.entryBlock : null;
        }
      }
    }
    // Now, consider the enclosing exception context.
    // NOTE: Because the locals of the current method can't 
    // possibly matter to the locals of the enclosing method, it is 
    // sufficient to add a CFG edge (no need to rectify locals).
    // It is the responsibility of the BC2IR object generating the 
    // caller method to ensure that the exposed handler blocks are 
    // generated if they are reachable from a callee.
    // See maybeInlineMethod.
    if (gc.enclosingHandlers != null) {
      for (OPT_BasicBlockEnumeration e = gc.enclosingHandlers.enumerator(); 
           e.hasMoreElements();) {
        OPT_ExceptionHandlerBasicBlock xbb = 
	  (OPT_ExceptionHandlerBasicBlock)e.next();
        byte mustCatch = xbb.mustCatchException(exceptionType);
        if (mustCatch != NO || 
	    xbb.mayCatchException(exceptionType) != NO) {
          if (DBG_EX)
            db("PEI of type " + exceptionType + 
               " could be caught by enclosing handler " + xbb);
          catchTargets++;
          currentBBLE.block.insertOut(xbb);
          if (DBG_CFG)
            db("Added CFG edge from " + currentBBLE.block + " to " + xbb);
        }
        if (mustCatch == YES) {
          if (DBG_EX)
            db("\t" + xbb + " will defintely catch exceptions of type "
	       + exceptionType);
          if (DBG_EX && catchTargets == 1)
            db("\t  and it is the only target");
          return (catchTargets == 1) ? xbb : null;
        }
      }
    }
    // If we get to here, then we didn't find a handler block that 
    // is guarenteed to catch the exception. Therefore deal with the 
    // possibly uncaught exception.
    currentBBLE.block.setMayThrowUncaughtException();
    if (linkToExitIfUncaught) {
      if (DBG_EX)
        db("added explicit edge from " + currentBBLE + " to outermost exit");
      currentBBLE.block.insertOut(gc.exit);
      if (DBG_CFG)
        db("Added CFG edge from " + currentBBLE.block + " to exit");
    }
    return null;
  }

  /*
   * Very similar to the above, but since we aren't told what might be thrown,
   * we are forced to connect to every in scope handler and can't 
   * identify a definite target.
   */
  private void rectifyStateWithExceptionHandlers(boolean linkToExitIfUncaught) {
    currentBBLE.block.setCanThrowExceptions();
    currentBBLE.block.setMayThrowUncaughtException();
    if (linkToExitIfUncaught) {
      if (DBG_EX)
        db("PEI of unknown type caused edge from " + currentBBLE + 
           " to outermost exit");
      currentBBLE.block.insertOut(gc.exit);
      if (DBG_CFG)
        db("Added CFG edge from " + currentBBLE.block + " to exit");
    }
    if (currentBBLE.handlers != null) {
      for (int i = 0; i < currentBBLE.handlers.length; i++) {
        HandlerBlockLE xbble = currentBBLE.handlers[i];
        if (DBG_EX)
          db("PEI of unknown type could be caught by " + xbble + 
             " rectifying locals");
        blocks.rectifyLocals(_localState, xbble);
        currentBBLE.block.insertOut(xbble.entryBlock);
        if (DBG_CFG)
          db("Added CFG edge from "+currentBBLE.block+" to "+xbble.entryBlock);
      }
    }
    // Now, consider the enclosing exception context; ditto NOTE above.
    if (gc.enclosingHandlers != null) {
      for (OPT_BasicBlockEnumeration e = gc.enclosingHandlers.enumerator(); 
           e.hasMoreElements();) {
        OPT_ExceptionHandlerBasicBlock xbb = 
	  (OPT_ExceptionHandlerBasicBlock)e.next();
        if (DBG_EX)
          db("PEI of unknown type could be caught by enclosing handler "+xbb);
        currentBBLE.block.insertOut(xbb);
        if (DBG_CFG)
	  db("Added CFG edge from "+currentBBLE.block+" to "+xbb);
      }
    }
  }

  //////////
  // INLINING support
  //////////
  /** 
   * Should we inline a call site?
   *
   * @param call the call instruction being considered for inlining
   * @param computedTarget non-NULL => we're CERTAIN that the call will 
   *         resolve to this target
   * @param isExtant is the receiver of a virtual method an extant object?
   */
  private OPT_InlineDecision shouldInline(OPT_Instruction call, 
					  VM_Method computedTarget, 
					  boolean isExtant) {
    OPT_CompilationState state = 
      new OPT_CompilationState(call, 
			       gc.localMCSizeEstimate+gc.parentMCSizeEstimate,
			       computedTarget, isExtant, gc.options, 
			       gc.original_cmid);
    OPT_InlineDecision d = gc.inlinePlan.shouldInline(state);
    if (gc.options.PRINT_DETAILED_INLINE_REPORT) {
      OPT_InlineReport.setValues(state);
      OPT_InlineReport.setDecision(d);
    }
    return d;
  }

  /**
   * Attempt to inline a method. This may fail.
   *
   * @param inlDec the inline decision for this call site
   * @param callSite the call instruction we are attempting to inline
   * @return true if inlining succeeded, false otherwise
   */
  private boolean maybeInlineMethod(OPT_InlineDecision inlDec, 
				    OPT_Instruction callSite) {
    if (inlDec.isNO()) {
      return false;
    }
    // Execute the inline decision.
    // NOTE: It is tempting to wrap the call to OPT_Inliner.execute in 
    // a try/catch block that suppresses MagicNotImplemented failures
    // by "backing out" the attempted inlining of a method that contained
    // an unimplemented magic.  Unfortunately, this is somewhat hard to do
    // cleanly, since exceptional control flow can inject control flow graph
    // edges from inlinedContext to blocks in the enclosing caller CFG.
    // These are not easy to find and remove because inlinedContext is not
    // well-formed (the exception was thrown while generating the IR, in 
    // particular before calling finalPass, therefore the inlined CFG
    // is not formed and finding all of its member blocks is somewhat awkward).
    // We could write code to deal with this, but since in practice the
    // opt compiler implements all but a few fringe magics, it is just fine
    // to completely give up rather than take heroic measures here.
    // In a few cases we do care about, we use VM_Magic.pragmaNoInline to
    // prevent the opt compiler from inlining a method that contains an
    // unimplemented magic.
    OPT_GenerationContext inlinedContext = 
      OPT_Inliner.execute(inlDec, gc, 
			  currentBBLE.block.exceptionHandlers, callSite);
    
    // TODO: We're currently not keeping track if any of the 
    // enclosing exception handlers are actually reachable from 
    // this inlined callee.  
    // Therefore we simply force all of them to be generated wrt
    // the state of the local variables in currentBBLE.
    // This can result in generating unreachable handlers 
    // (if no PEI can reach them) and in generating suboptimal 
    // catch blocks (by merging in currentBBLE's local state
    // into catch blocks that can't actually be reached from the inlined CFG).
    // I strongly suspect it's not worth worrying about this.....
    // dead code elimination should zap the unreachable handlers, 
    // and we shouldn't care too  much about the
    // optimization possibilities lost by the extra local rectification.
    // Especially since the odds of currentBBLE actually having 
    // unreachable handler blocks is darn close to zero. --dave 9/21/99.
    // NOTE: No need to add CFG edges (they were added as needed 
    // during generation of the callee)
    if (currentBBLE.handlers != null) {
      for (int i = 0; i < currentBBLE.handlers.length; i++) {
        blocks.rectifyLocals(_localState, currentBBLE.handlers[i]);
      }
    }
    if (inlinedContext.epilogue != null) {
      // Wrap a synthetic BBLE around OPT_GenerationContext.epilogue and
      // pass it as from to getOrCreateBlock.
      // This causes any compensation code inserted by getOrCreateBlock
      // into the epilogue of the inlined method (see inlineTest7)
      BasicBlockLE epilogueBBLE = new BasicBlockLE();
      epilogueBBLE.block = inlinedContext.epilogue;
      if (inlinedContext.result != null) {
        // If the call has a result, _callHelper allocated a new 
        // temp for it and pushed it onto the expression stack.
        // But, since we successfully inlined the call and 
        // inlinedContext.epilogue != null,
        // we can use inlinedContext.result to obtain better 
        // downstream information about the inlined callee's return value.
        // Therefore we'll pop the old callSite.result off the stack 
        // and push result instead.
        // NOTE: It's critical that we pop callSite.result 
        // _before_ we copy the stack state into epilogueBBLE!
        // Otherwise we'll end up with bogus code in the inlined 
        // method's prologue due to stack saving!!!!
        VM_Type resultType = Call.getResult(callSite).type;
        pop(resultType);        // throw away callSite.result
      }
      blocks.rectifyStacks(currentBBLE.block, stack, epilogueBBLE);
      if (inlinedContext.result != null) {
        VM_Type resultType = Call.getResult(callSite).type;
        push(inlinedContext.result, resultType);
      }
      epilogueBBLE.copyIntoLocalState(_localState);
      BasicBlockLE afterBBLE = 
	blocks.getOrCreateBlock(bcInfo.bcIndex, epilogueBBLE, 
				stack, _localState);
      // Create the InliningBlockLE and initialize fallThrough links.
      InliningBlockLE inlinedCallee = new InliningBlockLE(inlinedContext);
      currentBBLE.fallThrough = inlinedCallee;
      inlinedCallee.epilogueBBLE = epilogueBBLE;
      epilogueBBLE.fallThrough = afterBBLE;
    } else {
      // All exits from the callee were via throws.
      // Therefore the next basic block is unreachable (unless
      // there is a branch to it from somewhere else in the current method,
      // which will naturally be handled when we generate the branch).
      InliningBlockLE inlinedCallee = new InliningBlockLE(inlinedContext);
      currentBBLE.fallThrough = inlinedCallee;
    }
    endOfBasicBlock = true;
    return true;
  }


  //// LOCAL STATE.
  /**
   * Gets the specified local variable. This can return an OPT_RegisterOperand
   * which refers to the given local, or some other kind of operand (if the
   * local variable is assumed to contain a particular value.)
   *
   * @param i local variable number
   */
  private OPT_Operand getLocal(int i) {
    OPT_Operand local = _localState[i];
    if (DBG_LOCAL) db("getting local " + i + " for use: " + local);
    return local.copy();
  }

  /**
   * Gets the specified local variable (long, double). This can return an
   * OPT_RegisterOperand which refers to the given local, or some other kind
   * of operand (if the local variable is assumed to contain a given value.)
   *
   * @param i local variable number
   */
  private OPT_Operand getLocalDual(int i) {
    if (VM.VerifyAssertions) VM.assert(_localState[i + 1] == DUMMY);
    OPT_Operand local = _localState[i];
    if (DBG_LOCAL) db("getting local " + i + " for use: " + local);
    return local.copy();
  }

  /**
   * Set the specified local variable
   *
   * @param i local variable number
   * @param op OPT_Operand to store in the local
   */
  private void setLocal(int i, OPT_Operand op) {
    if (DBG_LOCAL) db("setting local " + i + " with " + op);
    _localState[i] = op;
  }

  /**
   * Set the specified local variable
   *
   * @param i local variable number
   * @param op OPT_Operand to store in the local
   */
  private void setLocalDual(int i, OPT_Operand op) {
    if (DBG_LOCAL) db("setting dual local " + i + " with " + op);
    _localState[i] = op;
    _localState[i + 1] = DUMMY;
  }



  //////////////////////////////////////////////
  // vvv Various classes for internal use vvv //
  //////////////////////////////////////////////

  /**
   * A somewhat complex subtask of IR generation is to discover and maintain
   * the set of basic blocks that are being generated.
   * This class encapsulates that functionality.
   * The backing data store is a red/black tree, but there are a number of
   * very specialized operations that are performed during search/insertion 
   * so we roll our own instead of using one from the standard library.
   */
  private static final class BBSet implements OPT_IRGenOptions {
    /** root of the backing red/black tree*/
    private BasicBlockLE root;

    /**
     * is it the case that we can ignore JSR processing because
     * BC2IR has not yet generated a JSR bytecode in this method?
     */
    private boolean noJSR = true;

    /** entry block of the CFG */
    private BasicBlockLE entry;

    /** associated generation context */
    private OPT_GenerationContext gc;

    /** associated bytecodes */
    private OPT_BytecodeInfo bcInfo;

    // Fields to support generation/identification of catch blocks
    /** Start bytecode index for each exception handler ranges */
    private int[] startPCs;

    /** End bytecode index for each exception handler range */
    private int[] endPCs;

    /** Start bytecode index of each the exception handler */
    private int[] handlerPCs;

    /** Type of exception handled by each exception handler range. */
    private OPT_TypeOperand[] exceptionTypes;
    
    
    /**
     * Initialize the BBSet to handle basic block generation for the argument
     * generation context and bytecode info.
     * @param gc the generation context to generate blocks for
     * @param bcInfo the bytecodes of said generation context
     * @param localState the state of the local variables for the block
     *                   beginning at bytecode 0.
     */
    BBSet(OPT_GenerationContext gc, 
	  OPT_BytecodeInfo bcInfo,
	  OPT_Operand[] localState) {
      this.gc = gc;
      this.bcInfo = bcInfo;
      
      // Set up internal data structures to deal with exception handlers
      parseExceptionTables();

      // Create the entry block, setting root as a sideffect.
      entry = _createBBLE(0, null, null, false); 
      entry.setStackKnown(); 
      entry.copyIntoLocalState(localState);
    }

    /** return the entry BBLE */
    BasicBlockLE getEntry() { return entry; }

    /**
     * Notify the BBSet that BC2IR has encountered a JSR bytecode.
     * This enables more complex logic in getOrCreateBlock to drive
     * the basic block specialization that is the key to JSR inlining.
     */
    void seenJSR() { noJSR = false; }

    /**
     * Return a enumeration of the BasicBlockLE's currently in the BBSet.
     */ 
    Enumeration contents() {
      return TreeEnumerator.enumFromRoot(root);
    }

    /**
     * Gets the bytecode index of the block in the set which has the
     * next-higher bytecode index.
     * Returns bcInfo.bcLength if x is currently the block with the highest
     * starting bytecode index.
     * @param x basic block to start at.
     */
    int getNextBlockBytecodeIndex(BasicBlockLE x) {
      BasicBlockLE nextBBLE = getSuccessor(x, x.low);
      return nextBBLE == null ? bcInfo.bcLength : nextBBLE.low;
    }

    /**
     * Finds the next ungenerated block, starting at the argument 
     * block and searching forward, wrapping around to the beginning. 
     * If all blocks are generated, it returns null.
     * @param start the basic block at which to start looking.
     */
    BasicBlockLE getNextEmptyBlock(BasicBlockLE start) {
      if (DBG_BBSET) db("getting the next empty block after " + start);
      
      // Look for an ungenerated block after start.
      TreeEnumerator e = TreeEnumerator.enumFromNode(start); 
      while (e.hasMoreElements()) {
	BasicBlockLE block = e.next();
	if (DBG_BBSET)
	  db("Considering block "+block+" "+block.genState());
	if (block.isReadyToGenerate()) {
	  if (DBG_BBSET) db("block " + block + " is not yet generated");
	  return block;
	}
      }

      // There were none. Start looking from the beginning.
      if (DBG_BBSET) db("at end of bytecodes, restarting at beginning");
      e = TreeEnumerator.enumFromRoot(root);
      while (true) {
	BasicBlockLE block = e.next();
	if (block == start) {
	  if (DBG_BBSET) db("wrapped around, no more empty blocks");
	  return null;
	}
	if (DBG_BBSET)
	  db("Considering block "+block+" "+block.genState());
	if (block.isReadyToGenerate()) {
	  if (DBG_BBSET) db("block " + block + " is not yet generated");
	  return block;
	}
      }
    }

    /**
     * Get or create a block at the specified target.
     * If simStack is non-null, rectifies stack state with target stack state.
     * If simLocals is non-null, rectifies local state with target local state.
     * Any instructions needed to rectify stack/local state are appended to
     * from.
     *
     * @param target target index
     * @param from the block from which control is being transfered
     *                  and to which rectification instructions are added.
     * @param simStack stack state to rectify, or null
     * @param simLocals local state to rectify, or null
     */
    BasicBlockLE getOrCreateBlock(int target, 
				  BasicBlockLE from, 
				  OperandStack simStack, 
				  OPT_Operand[] simLocals) {
      if (DBG_BB) {
	db("getting block " + target + ", match stack: " + 
	   (simStack != null) + " match locals: " + (simLocals != null));
      }
      return getOrCreateBlock(root, true, target, from, simStack, simLocals);
    }

    /**
     * Mark a previously generated block for regeneration.
     * We define this method here so that in the future
     * we can implement a more efficient getNextEmptyBlock that 
     * (1) avoids generating lots of blocks when a CFG predecessor has a 
     * pending regeneration and (2) avoids the scan through all blocks when
     * there are no more blocks left to generate.
     */
    private void markBlockForRegeneration(BasicBlockLE p) {
      if (DBG_REGEN) db("marking " + p + " for regeneration");
      if (p.fallThrough != null && p.fallThrough instanceof InliningBlockLE) {
	// if the fallthrough out edge of this block is an 
	// InlineMethodBasicBlock, then the inlined method must also be 
	// regenerated.  In preparation for this, we must delete all out 
	// edges from the inlined method to the caller. 
	// (These arise from thrown/caught exceptions.)
	InliningBlockLE imbb = (InliningBlockLE)p.fallThrough;
	imbb.deleteAllOutEdges();
	gc.localMCSizeEstimate -= imbb.gc.localMCSizeEstimate;
      }
      // discard any "real" instructions in the block
      if (!p.block.isEmpty()) {
	p.block.start.getNext().setPrev(null);
	p.block.end.getPrev().setNext(null);
	p.block.start.linkWithNext(p.block.end);
      }
      p.setSelfRegen();
      p.clearGenerated();
      p.fallThrough = null;
      // If p had a non-empty stack on entry, we need to go through it
      // and copy all of its operands (they may point to instructions
      // we just blew away, but then again they may not (may not be in p),
      // so we can't simply null out the instruction field);
      if (p.stackState != null) {
	int i = p.stackState.getSize();
	while (i-- > 0) {
	  OPT_Operand op = p.stackState.getFromTop(i);
	  p.stackState.replaceFromTop(i, op.copy());
	}
      }
    }

    /**
     * Rectify the given stack state with the state contained in the given 
     * BBLE, adding the necessary move instructions to the end of the given 
     * basic block to make register numbers agree.
     * <p>
     * BASIC: Expression stacks are constrained to contain only
     * OPT_RegisterOperands and ReturnAddressOperand across basic block 
     * boundaries.
     * <p>
     * ADVANCED: TODO: Eliminate the BASIC restriction.
     *  Key issue is being able to lazily insert the required moves
     *  in each basic block.
     *  An alternative approach would be to insert the moves eagerly
     *  keeping track of the register assigned for a stack location,
     *  but keep the restricted value on the stack until we are forced to 
     *  lower it in the operand lattice to a register operand.
     * <p>
     * @param block basic block to append move instructions to
     * @param stack stack to copy
     * @param p BBLE to copy stack state into
     */
    void rectifyStacks(OPT_BasicBlock block, OperandStack stack, 
		       BasicBlockLE p) {
      if (stack == null || stack.isEmpty()) {
	if (VM.VerifyAssertions) VM.assert(p.stackState == null);
	if (!p.isStackKnown())
	  p.setStackKnown();
	if (DBG_STACK)
	  db("Rectified empty expression stack into "+p+"("+p.block+")");
	return;
      }
      boolean generated = p.isGenerated();
      // (1) Don't allow constants on the expression stack across blocks.
      //     (the BASIC assumption). We'd really like to weaken this 
      //     restriction, but doing so is somewhat complex.  
      //     Fixing this is feature 146581.
      for (int i = 0; i < stack.getSize(); i++) {
	OPT_Operand sop = stack.getFromTop(i);
	if (sop.isConstant()) {
	  OPT_RegisterOperand rop = gc.temps.makeTemp(sop);
	  OPT_Instruction move = 
	    Move.create(OPT_IRTools.getMoveOp(rop.type, false), rop, sop);
	  move.bcIndex = RECTIFY_BCI;
	  move.position = gc.inlineSequence;
	  block.appendInstructionRespectingTerminalBranch(move);
	  stack.replaceFromTop(i, rop.copyD2U());
	  if (DBG_STACK)
	    db("Inserted " + move + " into " + block + 
	       " to remove constant from expression stack");
	}
      }
      // (2) Rectify the stacks.
      if (!p.isStackKnown()) {
	// First time we reached p. Thus, its expression stack 
	// is implicitly top and the meet degenerates to a copy operation 
	// with possibly some register renaming.
	// (We need to ensure that non-local registers appear at 
	// most once on each expression stack).
	if (DBG_STACK) {
	  db("First stack rectifiction for " + p + "(" + 
	     p.block + ") simply saving");
	}
	if (VM.VerifyAssertions) VM.assert(p.stackState == null);
	p.stackState = new OperandStack(stack.getCapacity());
	for (int i = stack.getSize() - 1; i >= 0; i--) {
	  OPT_Operand op = stack.getFromTop(i);
	  if (op == DUMMY) {
	    p.stackState.push(DUMMY);
	  } else if (op instanceof ReturnAddressOperand) {
	    p.stackState.push(op.copy());
	  } else {
	    // By BASIC assumption, op is a RegisterOperand.
	    OPT_RegisterOperand rop = op.asRegister();
	    if (rop.register.isLocal()) {
	      OPT_RegisterOperand temp = gc.temps.makeTemp(rop);
	      temp.setInheritableFlags(rop);
	      setGuard(temp, getGuard(rop));
	      OPT_Instruction move = 
		Move.create(OPT_IRTools.getMoveOp(rop.type, false), temp, rop);
	      move.bcIndex = RECTIFY_BCI;
	      move.position = gc.inlineSequence;
	      block.appendInstructionRespectingTerminalBranch(move);
	      p.stackState.push(temp.copy());
	      if (DBG_STACK)
		db("Inserted " + move + " into " + block + " to rename local");
	    } else {
	      p.stackState.push(rop.copy());
	    }
	  }
	}
	p.setStackKnown();
	return;
      } else {
	// A real rectification.
	// We need to update mergedStack such that 
	// mergedStack[i] = meet(mergedStack[i], stack[i]).
	if (DBG_STACK) db("rectifying stacks");
	if (VM.VerifyAssertions)
	  VM.assert(stack.getSize() == p.stackState.getSize());
	for (int i = 0; i < stack.getSize(); ++i) {
	  OPT_Operand sop = stack.getFromTop(i);
	  OPT_Operand mop = p.stackState.getFromTop(i);
	  if ((sop == DUMMY) || (sop instanceof ReturnAddressOperand)) {
	    if (VM.VerifyAssertions) VM.assert(mop.similar(sop));
	  } else {
	    // By BASIC assumption, sop and mop must be OPT_RegisterOperands.
	    OPT_RegisterOperand rsop = sop.asRegister();
	    OPT_RegisterOperand rmop = mop.asRegister();
	    if (rmop.register != rsop.register) {
	      // must insert move at end of block to get register #s to match
	      OPT_RegisterOperand temp = rsop.copyRO();
	      temp.setRegister(rmop.register);
	      OPT_Instruction move = 
		Move.create(OPT_IRTools.getMoveOp(temp.type, false), 
			    temp, rsop);
	      move.bcIndex = RECTIFY_BCI;
	      move.position = gc.inlineSequence;
	      block.appendInstructionRespectingTerminalBranch(move);
	      if (DBG_STACK)
		db("Inserted "+move+" into "+block+" to rectify reg numbers");
	    }
	    OPT_Operand meet = OPT_Operand.meet(rmop, rsop, rmop.register);
	    if (DBG_STACK) db("Meet of "+rmop+" and "+rsop+" is "+ meet);
	    if (meet != rmop) {
	      if (generated) {
		if (DBG_STACK)
		  db("\t...forced to regenerate " + p + " (" + p.block + 
		     ") because of this");
		markBlockForRegeneration(p);
		generated = false;
		p.block.deleteOut();
		if (DBG_CFG) db("Deleted all out edges of " + p.block);
	      }
	      p.stackState.replaceFromTop(i, meet);
	    }
	  }
	}
      }
    }

    /**
     * Rectify the given local variable state with the local variable state
     * stored in the given BBLE.
     *
     * @param localState local variable state to rectify
     * @param p target BBLE to rectify state to
     */
    void rectifyLocals(OPT_Operand[] localState, BasicBlockLE p) {
      if (!p.isLocalKnown()) {
	if (DBG_LOCAL)
	  db("rectifying with heretofore unknown locals, changing to save");
	p.copyIntoLocalState(localState);
	return;
      }
      if (DBG_LOCAL) db("rectifying current local state with " + p);
      boolean generated = p.isGenerated();
      OPT_Operand[] incomingState = localState;
      OPT_Operand[] presentState = p.localState;
      if (VM.VerifyAssertions)
	VM.assert(incomingState.length == presentState.length);
      for (int i = 0, n = incomingState.length; i < n; ++i) {
	OPT_Operand pOP = presentState[i];
	OPT_Operand iOP = incomingState[i];
	if (pOP == iOP) {
	  if (DBG_LOCAL)
	    db("local states have the exact same operand "+pOP+" for local "+i);
	} else {
	  boolean untyped = 
	    (pOP == null || pOP == DUMMY || pOP instanceof ReturnAddressOperand);
	  OPT_Operand mOP = 
	    OPT_Operand.meet(pOP, iOP, 
			     untyped?null:gc.localReg(i, pOP.getType()));
	  if (DBG_LOCAL) db("Meet of " + pOP + " and " + iOP + " is " + mOP);
	  if (mOP != pOP) {
	    if (generated) {
	      if (DBG_LOCAL)
		db("\t...forced to regenerate " + p + " (" + p.block + 
		   ") because of this");
	      markBlockForRegeneration(p);
	      generated = false;
	      p.block.deleteOut();
	      if (DBG_CFG) db("Deleted all out edges of " + p.block);
	    }
	    presentState[i] = mOP;
	  }
	}
      }
    }

    /**
     * Do a final pass over the generated basic blocks to create 
     * the initial code ordering. All blocks generated for the method 
     * will be inserted after gc.prologue.
     * NOTE: Only some CFG edges are created here..... 
     * we're mainly just patching together a code linearization.
     */
    void finalPass() {
      TreeEnumerator e = TreeEnumerator.enumFromRoot(root);
      OPT_BasicBlock cop = gc.prologue;
      BasicBlockLE curr = getEntry();
      BasicBlockLE next = null;
    top: 
      while (true) {
	// Step 0: If curr is the first block in a catch block, 
	// inject synthetic entry block too.
	if (curr instanceof HandlerBlockLE) {
	  // tell our caller that we actually put a handler in the final CFG.
	  gc.generatedExceptionHandlers = true; 
	  HandlerBlockLE hcurr = (HandlerBlockLE)curr;
	  if (DBG_FLATTEN) {
	    db("injecting handler entry block "+ hcurr.entryBlock+
	       " before "+hcurr);
	  }
	  gc.cfg.insertAfterInCodeOrder(cop, hcurr.entryBlock);
	  cop = hcurr.entryBlock;
	}
	// Step 1: Insert curr in the code order (after cop, updating cop).
	if (DBG_FLATTEN) db("flattening: " + curr + " (" + curr.block + ")");
	curr.setInCodeOrder();
	gc.cfg.insertAfterInCodeOrder(cop, curr.block);
	cop = curr.block;
	if (DBG_FLATTEN) {
	  db("Current Code order for " + gc.method + "\n");
	  for (OPT_BasicBlock bb = gc.prologue; bb != null; 
	       bb = (OPT_BasicBlock)bb.getNext()) {
	    VM.sysWrite(bb + "\n");
	  }
	}
	// Step 1.1 Sometimes (rarely) there will be an inscope 
	// exception handler that wasn't actually generated.  If this happens, 
	// make a new, filtered EHBBB to avoid later confusion.
	if (curr.handlers != null) {
	  int notGenerated = 0;
	  for (int i = 0; i < curr.handlers.length; i++) {
	    if (!curr.handlers[i].isGenerated()) {
	      if (DBG_EX || DBG_FLATTEN) {
		db("Will remove unreachable handler " + curr.handlers[i]
		   + " from " + curr);
	      }
	      notGenerated++;
	    }
	  }
	  if (notGenerated > 0) {
	    if (notGenerated == curr.handlers.length) {
	      if (DBG_EX || DBG_FLATTEN) {
		db("No (local) handlers were actually reachable for " + 
		   curr + "; setting to caller");
	      }
	      curr.block.exceptionHandlers =
		curr.block.exceptionHandlers.getCaller();
	    } else {
	      OPT_ExceptionHandlerBasicBlock[] nlh = 
		new OPT_ExceptionHandlerBasicBlock[curr.handlers.length - 
						  notGenerated];
	      for (int i = 0, j = 0; i < curr.handlers.length; i++) {
		if (curr.handlers[i].isGenerated()) {
		  nlh[j++] = curr.handlers[i].entryBlock;
		} else {
		  if (VM.VerifyAssertions) {
		    VM.assert(curr.handlers[i].entryBlock.hasZeroIn(), 
			      "Non-generated handler with CFG edges");
		  }
		}
	      }
	      curr.block.exceptionHandlers = 
		new OPT_ExceptionHandlerBasicBlockBag(nlh, 
						      curr.block.exceptionHandlers.getCaller());
	    }
	  }
	}
	// Step 2: Identify the next basic block to add to the code order.
	// curr wants to fallthrough to an inlined method.  
	// Inject the entire inlined CFG in the code order.
	// There's some fairly complicated coordination between this code, 
	// OPT_GenerationContext, and maybeInlineMethod.  Sorry, but you'll 
	// have to take a close look at all of these to see how it
	// all fits together....--dave
	if (curr.fallThrough != null && 
	    curr.fallThrough instanceof InliningBlockLE) {
	  InliningBlockLE icurr = (InliningBlockLE)curr.fallThrough;
	  OPT_BasicBlock forw = cop.nextBasicBlockInCodeOrder();
	  OPT_BasicBlock calleeEntry = icurr.gc.cfg.firstInCodeOrder();
	  OPT_BasicBlock calleeExit = icurr.gc.cfg.lastInCodeOrder();
	  gc.cfg.breakCodeOrder(cop, forw);
	  gc.cfg.linkInCodeOrder(cop, icurr.gc.cfg.firstInCodeOrder());
	  gc.cfg.linkInCodeOrder(icurr.gc.cfg.lastInCodeOrder(), forw);
	  cop.insertOut(calleeEntry);
	  if (DBG_CFG)
	    db("Added CFG edge from " + cop + " to " + calleeEntry);
	  if (icurr.epilogueBBLE != null) {
	    if (DBG_FLATTEN)
	      db("injected " + icurr + " between " + curr + " and " + 
		 icurr.epilogueBBLE.fallThrough);
	    if (VM.VerifyAssertions) {
	      VM.assert(icurr.epilogueBBLE.block ==
			icurr.gc.cfg.lastInCodeOrder());
	    }
	    curr = icurr.epilogueBBLE;
	    cop = curr.block;
	  } else {
	    if (DBG_FLATTEN) db("injected " + icurr + " after " + curr);
	    curr = icurr;
	    cop = calleeExit;
	  }
	}
	next = curr.fallThrough;
	if (DBG_FLATTEN && next == null)
	  db(curr + " has no fallthrough case, getting next block");
	if (next != null) {
	  // Add the CFG edge from currBBLE.block to nextBBLE.block
	  curr.block.insertOut(next.block);
	  if (DBG_CFG)
	    db("Added CFG edge from " + curr.block + " to " + next.block);
	  if (next.isInCodeOrder()) {
	    if (DBG_FLATTEN)
	      db("fallthrough " + next + " is already flattened, adding goto");
	    curr.block.appendInstruction(next.block.makeGOTO());
	    // set next to null to indicate no "real" fall through
	    next = null;
	  }
	}
	if (next == null) {
	  // Can't process fallthroughblock, so get next BBLE from enumeration
	  while (true) {
	    if (!e.hasMoreElements()) {
	      // all done.
	      if (DBG_FLATTEN) db("no more blocks! all done");
	      break  top;
	    }
	    next = e.next();
	    if (DBG_FLATTEN) db("looking at " + next);
	    if (!next.isGenerated()) {
	      if (DBG_FLATTEN) db("block " + next + " was not generated");
	      continue;
	    }
	    if (!next.isInCodeOrder())
	      break;
	  }
	  if (DBG_FLATTEN) db("found unflattened block: " + next);
	}
	curr = next;
      }
      // If the epilogue was unreachable, remove it from the code order and cfg
      // and set gc.epilogue to null.
      if (gc.epilogue.hasZeroIn()) {
	if (DBG_FLATTEN || DBG_CFG)
	  db("Deleting unreachable epilogue " + gc.epilogue);
	gc.cfg.removeFromCodeOrder(gc.epilogue);

	// remove the node from the graph AND adjust its edge info
	gc.epilogue.remove();
	gc.epilogue.deleteIn();
	gc.epilogue.deleteOut();
	if (VM.VerifyAssertions) VM.assert(gc.epilogue.hasZeroOut());
	gc.epilogue = null;
      }
      // if gc has an unlockAndRethrow block that was not used, then remove it
      if (gc.unlockAndRethrow != null && gc.unlockAndRethrow.hasZeroIn()) {
	gc.cfg.removeFromCFGAndCodeOrder(gc.unlockAndRethrow);
	gc.enclosingHandlers.remove( gc.unlockAndRethrow );
      }

      if (DBG_FLATTEN) {
	db("Current Code order for " + gc.method + "\n");
	for (OPT_BasicBlock bb = gc.prologue; 
	     bb != null; 
	     bb = (OPT_BasicBlock)bb.getNext()) {
	  bb.printExtended();
	}
      }
      if (DBG_FLATTEN) {
	db("Final CFG for " + gc.method + "\n");
	gc.cfg.printDepthFirst();
      }
    }


    //////////////////////////////////////////
    // Gory implementation details of BBSet //
    //////////////////////////////////////////

    /**
     * Print a debug string to the sysWrite stream.
     * @param val string to print
     */
    private final void db(String val) {
      VM.sysWrite("IRGEN " + gc.method.getDeclaringClass().getName() + "."
		  + gc.method.getName() + ":" + val + "\n");
    }

    /**
     * Initialize the global exception handler arrays for the method.<p>
     * TODO: If we modified VM_ExceptionMap to substitute 
     * java.lang.Throwable for 'null' then we could just use eMap directly...
     */
    private void parseExceptionTables() {
      VM_ExceptionHandlerMap eMap = gc.method.getExceptionHandlerMap();
      if (eMap == null)	return;  // method has no exception handling ranges.
      startPCs = eMap.getStartPC();
      endPCs = eMap.getEndPC();
      handlerPCs = eMap.getHandlerPC();
      int numExceptionHandlers = startPCs.length;
      exceptionTypes = new OPT_TypeOperand[numExceptionHandlers];
      for (int i = 0; i < numExceptionHandlers; i++) {
	if (eMap.getExceptionType(i) == null) {
	  // A finally block...set to java.lang.Throwable to avoid
	  // needing to think about this case anywhere else.
	  exceptionTypes[i] = new OPT_TypeOperand(VM_Type.JavaLangThrowableType);
	} else {
	  exceptionTypes[i] = new OPT_TypeOperand(eMap.getExceptionType(i));
	}
      }
    }

    /**
     * Initialize bble's handlers array based on startPCs/endPCs.
     * In the process, new HandlerBlockLE's may be created 
     * (by the call to getOrCreateBlock). <p>
     * PRECONDITION: bble.low and bble.max have already been correctly 
     * set to reflect the invariant that a basic block is in exactly one 
     * "handler range."
     * Also initializes bble.block.exceptionHandlers.
     */
    private void initializeExceptionHandlers(BasicBlockLE bble, 
					     OPT_Operand[] simLocals) {
      if (startPCs != null) {
	java.util.HashSet caughtTypes = new java.util.HashSet();
	for (int i = 0; i < startPCs.length; i++) {
	  VM_Type caughtType = exceptionTypes[i].type;
	  if (bble.low >= startPCs[i] && bble.max <= endPCs[i] && 
	      !caughtTypes.contains(caughtType)) {
	    // bble's basic block is contained within this handler's range.
	    HandlerBlockLE eh = 
	      (HandlerBlockLE)getOrCreateBlock(handlerPCs[i], 
					       bble, null, simLocals);
	    if (DBG_EX) db("Adding handler " + eh + " to " + bble);
	    caughtTypes.add(caughtType);
	    bble.addHandler(eh);
	  }
	}
      }
      if (bble.handlers != null) {
	OPT_ExceptionHandlerBasicBlock[] ehbbs = 
	  new OPT_ExceptionHandlerBasicBlock[bble.handlers.length];
	for (int i = 0; i < bble.handlers.length; i++) {
	  ehbbs[i] = bble.handlers[i].entryBlock;
	}
	bble.block.exceptionHandlers = 
	  new OPT_ExceptionHandlerBasicBlockBag(ehbbs, gc.enclosingHandlers);
      } else {
	bble.block.exceptionHandlers = gc.enclosingHandlers;
      }
    }
    
    /**
     * Given a starting bytecode index, find the greatest bcIndex that 
     * is still has the same inscope exception handlers.
     * @param bcIndex the start bytecode index
     */
    private int exceptionEndRange(int bcIndex) {
      int max = bcInfo.bcLength;
      if (startPCs != null) {
	for (int i = 0; i < startPCs.length; i++) {
	  int spc = startPCs[i];
	  if (bcIndex < spc && max > spc) {
	    max = spc;
	  }
	}
	for (int i = 0; i < endPCs.length; i++) {
	  int epc = endPCs[i];
	  if (bcIndex < epc && max > epc) {
	    max = epc;
	  }
	}
      }
      return max;
    }


    /**
     * We specialize basic blocks with respect to the return addresses
     * they have on their expression stack and/or in their local variables
     * on entry to the block. This has the effect of inlining the 
     * subroutine body at all of the JSR sites that invoke it.
     * This is the key routine: it determines whether or not the 
     * argument simState (stack and locals) contains compatible 
     * return addresses as the candidate BasicBlockLE.
     * <p>
     * The main motivation for inlining away all JSR's is that it eliminates 
     * the "JSR problem" for type accurate GC.  It is also simpler to 
     * implement and arguably results in more efficient generated code 
     * (assuming that we don't get horrific code bloat).
     * To deal with the code bloat, we detect excessive code duplication and
     * stop IR generation (bail out to the baseline compiler).
     *
     * @param simStack the expression stack to match
     * @param simLocals the local variables to match
     * @param candBBLE, the candidate BaseicBlockLE
     */
    private boolean matchingJSRcontext(OperandStack simStack,
				       OPT_Operand[] simLocals,
				       BasicBlockLE candBBLE) {
      if (DBG_INLINE_JSR) 
	db("Matching JSR context of argument stack/locals against "+candBBLE);
      
      int numRA = 0;
      if (simStack != null && candBBLE.isStackKnown()) {
	for (int i = simStack.getSize() - 1; i >= 0; i--) {
	  OPT_Operand op = simStack.getFromTop(i);
	  if (op instanceof ReturnAddressOperand) {
	    if (numRA++ > MAX_RETURN_ADDRESSES) {
	      throw new OPT_OperationNotImplementedException("Too many subroutines");
	    }
	    if (DBG_INLINE_JSR) db("simStack operand "+i+" is "+op);
	    OPT_Operand cop = candBBLE.stackState.getFromTop(i);
	    if (!OPT_Operand.conservativelyApproximates(cop, op)) {
	      if (DBG_INLINE_JSR) db("Not Matching: "+cop+" and "+op);
	      return false;
	    } else {
	      if (DBG_INLINE_JSR) db("operand "+cop+" is compatible with "+op);
	    }
	  }
	}
      }

      if (simLocals != null && candBBLE.isLocalKnown()) {
	for (int i = 0; i < simLocals.length; i++) {
	  OPT_Operand op = simLocals[i];
	  if (op instanceof ReturnAddressOperand) {
	    if (numRA++ > MAX_RETURN_ADDRESSES) {
	      throw new OPT_OperationNotImplementedException("Too many subroutines");
	    }
	    if (DBG_INLINE_JSR) db("simLocal "+i+" is "+op);
	    OPT_Operand cop = candBBLE.localState[i];
	    if (!OPT_Operand.conservativelyApproximates(cop, op)) {
	      if (DBG_INLINE_JSR) db("Not Matching: "+cop+" and "+op);
	      return false;
	    } else {
	      if (DBG_INLINE_JSR) db("operand "+cop+" is compatible with "+op);
	    }
	  }
	}
      }

      if (DBG_INLINE_JSR) db("Found "+candBBLE+" to be compatible");
      return true;
    }


    /**
     * Get or create a block at the specified target.
     * If simStack is non-null, rectifies stack state with target stack state.
     * If simLocals is non-null, rectifies local state with target local state.
     * Any instructions needed to rectify stack/local state are appended to
     * from.
     * As blocks are created, they are added to the red/black tree below x.
     *   
     * @param x starting node for search.
     * @param shouldCreate should we create the block if we run off the tree?
     * @param target target index
     * @param from the block from which control is being transfered
     *                  and to which rectification instructions are added.
     * @param simStack stack state to rectify, or null
     * @param simLocals local state to rectify, or null
     */
    private BasicBlockLE getOrCreateBlock(BasicBlockLE x, 
					  boolean shouldCreate,
					  int target, 
					  BasicBlockLE from, 
					  OperandStack simStack, 
					  OPT_Operand[] simLocals) {
      if (target < x.low) {
	if (x.left == null) {
	  return condCreateAndInit(x, shouldCreate, target, from, 
				   simStack, simLocals, true);
	} else {
	  if (DBG_BBSET) db("following left branch from "+x+" to "+x.left);
	  return getOrCreateBlock(x.left, shouldCreate, target,
				  from, simStack, simLocals);
	}
      } else if (target > x.low) {
	if ((x.low < target) && (target <= x.high)) {
	  // the target points to the middle of x; mark x for regen
	  if (DBG_BBSET) db("target points to middle of " + x);
	  markBlockForRegeneration(x);
	  x.high = x.low;
	  x.block.deleteOut();
	  if (DBG_CFG) db("Deleted all out edges of " + x.block);
	}
	if (x.right == null) {
	  return condCreateAndInit(x, shouldCreate, target, from, 
				   simStack, simLocals, false);
	} else {
	  if (DBG_BBSET) db("following right branch from "+x+" to "+x.right);
	  return getOrCreateBlock(x.right, shouldCreate, target,
				  from, simStack, simLocals);
	}
      } else { 
	// found a basic block at the target bytecode index.
	if (noJSR || matchingJSRcontext(simStack, simLocals, x)) {
	  if (DBG_BBSET) db("found block " + x + " (" + x.block + ")");
	  if (simStack != null) rectifyStacks(from.block, simStack, x);
	  if (simLocals != null) rectifyLocals(simLocals, x);
	  return x;
	}
	if (DBG_BBSET) db("found block "+x+", but JSR context didn't match");
	if (x.left == null) {
	  if (x.right == null) {
	    return condCreateAndInit(x, shouldCreate, target, from, 
				     simStack, simLocals, true);
	  } else {
	    if (DBG_BBSET)
	      db(x + " has only right child, continuing down that branch");
	    return getOrCreateBlock(x.right, shouldCreate, target, from, 
				    simStack, simLocals);
	  }
	} else {
	  if (x.right == null) {
	    if (DBG_BBSET)
	      db(x + " has only left child, continuing down that branch");
	    return getOrCreateBlock(x.left, shouldCreate, target, from,
				    simStack, simLocals);
	  } else {
	    if (DBG_BBSET)
	      db(x + " has two children, searching left branch first");
	    BasicBlockLE bble = getOrCreateBlock(x.left, false, target, 
						 from, simStack, simLocals);
	    if (bble != null) {
	      return bble;
	    } else {
	      if (DBG_BBSET)
		db("didn't find " + target + 
		   " on left branch, continuing down right branch");
	      return getOrCreateBlock(x.right, shouldCreate, target, from,
				      simStack, simLocals);
	    } 
	  }
	}
      }
    }


    /**
     * Conditionally create a block at the specified target as a child of x.
     * If simStack is non-null, rectifies stack state with target stack state.
     * If simLocals is non-null, rectifies local state with target local state.
     * Any instructions needed to rectify stack/local state are appended to
     * from.
     *   
     * @param x starting node for search.
     * @param shouldCreate should we create the block if we run off the tree?
     * @param target target index
     * @param from the block from which control is being transfered
     *                  and to which rectification instructions are added.
     * @param simStack stack state to rectify, or null
     * @param simLocals local state to rectify, or null
     * @param left are we creating a left child of parent?
     * @return the newly create block, or null if !shouldCreate
     */
    private BasicBlockLE condCreateAndInit(BasicBlockLE x, 
					   boolean shouldCreate,
					   int target, 
					   BasicBlockLE from, 
					   OperandStack simStack, 
					   OPT_Operand[] simLocals,
					   boolean left) {
      BasicBlockLE bble = null;
      if (shouldCreate) {
	bble = _createBBLE(target, simLocals, x, left);
	if (simStack != null)
	  rectifyStacks(from.block, simStack, bble);
	if (simLocals != null)
	  bble.copyIntoLocalState(simLocals);
      }
      return bble;
    }

    /**
     * Allocate a new BBLE at the given bcIndex.
     * If bcIndex is the start of an handler block, 
     * then a HandlerBlockLE is created.
     * After the BBLE is created, its handlers data structure is initialized
     * (which may cause other blocks to be created).
     * @param bcIndex the bytecode index at which the block should be created.
     * @param simLocals the localState to pass (via initializeExceptionHandler)to
     *                  to getOrCreateBlock if we need to create BBLEs for
     *                  exception handlers.  This is only actually used if 
     *                  !noJSR.  We don't need the expression stack, since
     *                  the only thing on the expression stack on entry to
     *                  a handler block is the exception object (and thus
     *                  we can skip scanning the expression stack for
     *                  return addresses when creating a handler block).
     * @param parent parent in Red/Black tree
     * @param left are we creating a left child of parent?
     */
    private BasicBlockLE _createBBLE(int bcIndex,
				     OPT_Operand[] simLocals,
				     BasicBlockLE parent, 
				     boolean left) {
      BasicBlockLE newBBLE = null;
      if (handlerPCs != null) {
	for (int i = 0; i < handlerPCs.length; i++) {
	  if (handlerPCs[i] == bcIndex) {
	    if (newBBLE == null) {
	      newBBLE = 
		new HandlerBlockLE(bcIndex, gc.inlineSequence,
				   exceptionTypes[i], gc.temps, 
				   gc.method.getOperandWords(),
				   gc.cfg);
	      ((HandlerBlockLE)newBBLE).entryBlock.firstRealInstruction().
		position = gc.inlineSequence;
	    } else {
	      ((HandlerBlockLE)newBBLE).addCaughtException(exceptionTypes[i]);
	    }
	  }
	}
      }
      if (newBBLE == null)
	newBBLE = new BasicBlockLE(bcIndex, gc.inlineSequence, gc.cfg);

      // Set newBBLE.max to encode exception ranges
      newBBLE.max = exceptionEndRange(bcIndex);

      if (DBG_BBSET) db("Created " + newBBLE);

      // Now, insert newBBLE into our backing Red/Black tree before we call 
      // initializeExceptionHandlers.  
      // We must do it in this order because initExHand may in turn call 
      // _createBBLE to create new handler blocks, and our tree must contain 
      // newBBLE before we can correctly insert another block.
      treeInsert(parent, newBBLE, left);

      initializeExceptionHandlers(newBBLE, simLocals);
      return newBBLE;
    }


    /**
     * Returns the basic block which has the next-higher bytecode index.
     * Returns null if x is the highest block.
     * @param x basic block at which to start the search for a higher block
     * @param value the contents of x.low (makes tail call elim work better
     *              if we avoid the obvious 1 argument wrapper function)
     */
    private BasicBlockLE getSuccessor(BasicBlockLE x, int value) {
      if (x.right != null) return minimumBB(x.right, value);
      BasicBlockLE y = x.parent;
      while ((y != null) && (x == y.right)) {
	x = y;
	y = x.parent;
      }
      // at this point either x is the root, or x is the left child of y
      if ((y == null) || (y.low != value)) return y;
      return getSuccessor(y, value);
    }
    private BasicBlockLE minimumBB(BasicBlockLE x, int value) {
      if (x.left != null) return minimumBB(x.left, value);
      if (value == x.low) return getSuccessor(x, value);
      return x;
    }

    /**
     * Insert newBBLE as a child of parent in our Red/Black tree.
     * @param parent the parent node
     * @param child  the new child node
     * @param left   is the child the left or right child of parent?
     */
    private void treeInsert(BasicBlockLE parent, 
			    BasicBlockLE newBBLE, 
			    boolean left) {
      if (parent == null) {
	if (VM.VerifyAssertions) VM.assert(root == null);
	root = newBBLE;
	root.setBlack();
	if (DBG_BBSET) db("inserted "+newBBLE+" as root of tree");
      } else {
	if (left) {
	  parent.left = newBBLE;
	} else {
	  parent.right = newBBLE;
	}
	newBBLE.parent = parent;
	if (DBG_BBSET) {
	  db("inserted new block " + newBBLE + " as " + 
	     (left ? "left" : "right") + " child of " + parent);
	}
	fixupBBSet(newBBLE);
      }
    }

    /**
     * Performs tree fixup (restore Red/Black invariants) after adding a 
     * new node to the tree.
     * @param x node that was added.
     */
    private void fixupBBSet(BasicBlockLE x) {
      if (DBG_BBSET) db("fixing up tree after inserting " + x);
      x.setRed();
      while (x != root) {
	BasicBlockLE xp = x.parent;
	if (xp.isBlack())
	  break;
	if (DBG_BBSET) db(x + " and its parent " + xp + " are both red");
	BasicBlockLE xpp = xp.parent;
	if (DBG_BBSET) db(xp + "'s parent is " + xpp);
	if (xp == xpp.left) {
	  BasicBlockLE y = xpp.right;
	  if ((y != null) && y.isRed()) {
	    xp.setBlack();
	    y.setBlack();
	    xpp.setRed();
	    x = xpp;
	  } else {
	    if (x == xp.right) {
	      x = xp;
	      leftRotateBBSet(xp);
	      xp = x.parent;
	      xpp = xp.parent;
	    }
	    xp.setBlack();
	    xpp.setRed();
	    rightRotateBBSet(xpp);
	  }
	} else {
	  BasicBlockLE y = xpp.left;
	  if ((y != null) && y.isRed()) {
	    xp.setBlack();
	    y.setBlack();
	    xpp.setRed();
	    x = xpp;
	  } else {
	    if (x == xp.left) {
	      x = xp;
	      rightRotateBBSet(xp);
	      xp = x.parent;
	      xpp = xp.parent;
	    }
	    xp.setBlack();
	    xpp.setRed();
	    leftRotateBBSet(xpp);
	  }
	}
      }
      root.setBlack();
      // verifyTree();
      return;
    }

    private void leftRotateBBSet(BasicBlockLE x) {
      if (DBG_BBSET) db("performing left tree rotation");
      BasicBlockLE y = x.right;
      BasicBlockLE yl = y.left;
      x.right = yl;
      if (yl != null)
	yl.parent = x;
      BasicBlockLE xp = x.parent;
      y.parent = xp;
      if (xp == null)
	root = y; 
      else if (x == xp.left)
	xp.left = y; 
      else 
	xp.right = y;
      y.left = x;
      x.parent = y;
    }

    private void rightRotateBBSet(BasicBlockLE x) {
      if (DBG_BBSET) db("performing right tree rotation");
      BasicBlockLE y = x.left;
      BasicBlockLE yr = y.right;
      x.left = yr;
      if (yr != null)
	yr.parent = x;
      BasicBlockLE xp = x.parent;
      y.parent = xp;
      if (xp == null)
	root = y; 
      else if (x == xp.right)
	xp.right = y; 
      else 
	xp.left = y;
      y.right = x;
      x.parent = y;
    }

    private void verifyTree() {
      if (VM.VerifyAssertions) {
	VM.assert(root.isBlack());
	verifyTree(root, -1, bcInfo.bcLength);
	countBlack(root);
      }
    }

    private void verifyTree(BasicBlockLE node, int min, int max) {
      if (VM.VerifyAssertions) {
	VM.assert(node.low >= min);
	VM.assert(node.low <= max);
	if (node.left != null) {
	  VM.assert(node.isBlack() || node.left.isBlack());
	  VM.assert(node.left.parent == node);
	  verifyTree(node.left, min, node.low);
	}
	if (node.right != null) {
	  VM.assert(node.isBlack() || node.right.isBlack());
	  VM.assert(node.right.parent == node);
	  verifyTree(node.right, node.low, max);
	}
      }
    }

    private int countBlack(BasicBlockLE node) {
      if (node == null)	return 1;
      int left = countBlack(node.left);
      int right = countBlack(node.right);
      if (VM.VerifyAssertions) VM.assert(left == right);
      if (node.isBlack())
	left++;
      return left;
    }

    private static final class TreeEnumerator implements Enumeration {
      BasicBlockLE node;

      static TreeEnumerator enumFromRoot(BasicBlockLE root) {
	if (root.left != null) {
	  do {
	    root = root.left;
	  } while (root.left != null);
	}
	return new TreeEnumerator(root);
      }

      static TreeEnumerator enumFromNode(BasicBlockLE node) {
	return new TreeEnumerator(node);
      }

      private TreeEnumerator(BasicBlockLE node) {
	this.node = node;
      }

      public boolean hasMoreElements() {
	return (node != null);
      }

      public BasicBlockLE next() {
	BasicBlockLE retVal = node;
	if (retVal == null)
	  throw  new NoSuchElementException();
	if (retVal.right != null) {
	  node = retVal.right;
	  while (node.left != null) {
	    node = node.left;
	  }
	} else {
	  BasicBlockLE x = retVal;
	  node = x.parent;
	  while ((node != null) && (node.right == x)) {
	    x = node;
	    node = x.parent;
	  }
	}
	return retVal;
      }

      public Object nextElement() {
	return next();
      }
    }
  }


  /**
   * Simulated Operand Stack
   */
  private static final class OperandStack {

    OPT_Operand[] stack;
    int top;

    OperandStack(int size) {
      stack = new OPT_Operand[size];
      top = 0;
    }

    OperandStack copy() {
      OperandStack newss = new OperandStack(stack.length);
      newss.top = top;
      System.arraycopy(stack, 0, newss.stack, 0, top);
      return newss;
    }

    void clear() { 
      top = 0;
    }

    void push(OPT_Operand val) {
      if (VM.VerifyAssertions) VM.assert(val.instruction == null);
      stack[top++] = val;
    }

    OPT_Operand pop() {
      return stack[--top];
    }

    OPT_Operand peek(int depth) {
      return stack[top - depth - 1];
    }

    void pop2() {
      pop();
      pop();
    }

    void swap() {
      OPT_Operand v1 = pop();
      OPT_Operand v2 = pop();
      push(v1);
      push(v2);
    }

    boolean isEmpty() {
      return (top == 0);
    }

    int getSize() {
      return top;
    }

    int getCapacity() {
      return stack.length;
    }

    OPT_Operand getFromTop (int n) {
      return stack[top - n - 1];
    }

    void replaceFromTop(int n, OPT_Operand op) {
      if (VM.VerifyAssertions) VM.assert(op.instruction == null);
      stack[top - n - 1] = op;
    }
  }

  /**
   * This class is used as a 'wrapper' to a basic block to hold
   * information that is necessary only for IR generation.
   */
  private static class BasicBlockLE {
    // Used by BC2IR to maintain red/black tree of BBLE's during generation
    BasicBlockLE parent, left, right;

    /** Start bytecode of this BBLE */
    int low;

    /** Current end bytecode of this BBLE */
    int high;

    /** Maximum possible bytecode of this BBLE (wrt exception ranges) */
    int max;

    /** Basic block that this BBLE refers to. */
    OPT_BasicBlock block;

    /** State of the stack at the start of this basic block. */
    OperandStack stackState;

    /** State of the local variables at the start of this basic block. */
    OPT_Operand[] localState;

    /**
     * The desired fallthrough (next in code order) BBLE (may be null).
     * NOTE: we may not always end up actually falling through 
     * (see BBSet.finalPass).
     */
    BasicBlockLE fallThrough;

    /**
     * The exception handler BBLE's for this block (null if none)
     */
    HandlerBlockLE[] handlers;

    /**
     * Encoding of random boolean state
     */
    private byte flags;
    private static final byte STACK_KNOWN = 0x01;
    private static final byte LOCAL_KNOWN = 0x02;
    private static final byte SELF_REGEN = 0x04;
    private static final byte GENERATED = 0x08;
    private static final byte COLOR = 0x10;           //(Red = 0, Black = 1)
    private static final byte IN_CODE_ORDER = 0x20;

    final void setStackKnown() { flags |= STACK_KNOWN; }
    final void clearStackKnown() { flags &= ~STACK_KNOWN; }
    final boolean isStackKnown() { return (flags & STACK_KNOWN) != 0; }

    final void setLocalKnown() { flags |= LOCAL_KNOWN; }
    final void clearLocalKnown() { flags &= ~LOCAL_KNOWN; }
    final boolean isLocalKnown() { return (flags & LOCAL_KNOWN) != 0; }

    final void setSelfRegen() { flags |= SELF_REGEN; }
    final void clearSelfRegen() { flags &= ~SELF_REGEN; }
    final boolean isSelfRegen() { return (flags & SELF_REGEN) != 0; }

    final void setGenerated() { flags |= GENERATED; }
    final void clearGenerated() { flags &= ~GENERATED; }
    final boolean isGenerated() { return (flags & GENERATED) != 0; }

    final void setBlack() { flags |= COLOR; }
    final boolean isBlack() { return (flags & COLOR) != 0; }

    final void setRed() { flags &= ~COLOR; }
    final boolean isRed() { return (flags & COLOR) == 0; }

    final void setInCodeOrder() { flags |= IN_CODE_ORDER; }
    final void clearInCodeOrder() { flags &= ~IN_CODE_ORDER; }
    final boolean isInCodeOrder() { return (flags & IN_CODE_ORDER) != 0; }

    /**
     * Is the BBLE ready to generate?
     */
    final boolean isReadyToGenerate() {
      // (isStackKnown() && isLocalKnown && !isGenerated)
      byte READY_MASK = STACK_KNOWN | LOCAL_KNOWN | GENERATED;
      byte READY_VAL = STACK_KNOWN | LOCAL_KNOWN;  
      return (flags & READY_MASK) == READY_VAL;
    }


    /**
     * Save a shallow copy of the given local variable state into this.
     * @param _localState local variable state to save
     */
    final void copyIntoLocalState(OPT_Operand[] _localState) {
      localState = new OPT_Operand[_localState.length];
      System.arraycopy(_localState, 0, localState, 0, _localState.length);
      setLocalKnown();
    }

    /**
     * Return a shallow copy of my local state.
     */
    final OPT_Operand[] copyLocalState() {
      OPT_Operand[] ls = new OPT_Operand[localState.length];
      System.arraycopy(localState, 0, ls, 0, localState.length);
      return ls;
    }

    /**
     * Add an exception handler BBLE to the handlers array.
     * NOTE: this isn't incredibly efficient, but empirically the expected
     * number of handlers per basic block is 0, with an observed
     * maximum across 10,000+ methods of 3.  
     * Until this changes, we just don't care.
     */
    final void addHandler(HandlerBlockLE handler) {
      if (handlers == null) {
        handlers = new HandlerBlockLE[1];
        handlers[0] = handler;
      } else {
        for (int i = 0; i < handlers.length; i++) {
          if (handlers[i] == handler)
            return;             //already there (was in emap more than once)
        }
        int n = handlers.length;
        HandlerBlockLE[] tmp = new HandlerBlockLE[n + 1];
        for (int i = 0; i < n; i++) {
          tmp[i] = handlers[i];
        }
        tmp[n] = handler;
        handlers = tmp;
      }
    }

    /**
     * Create a new BBLE (and basic block) for the specified bytecode index.
     *
     * @param loc bytecode index
     * @param position the inline sequence
     * @param cfg OPT_ControlFlowGraph into which the block 
     *            will eventually be inserted
     */
    BasicBlockLE(int loc, OPT_InlineSequence position, 
		 OPT_ControlFlowGraph cfg) {
      block = new OPT_BasicBlock(loc, position, cfg);
      low = loc;
      high = loc;
    }

    // Only for use by subclasses to avoid above constructor.
    protected BasicBlockLE() { }
    
    /**
     * Returns a string representation of this BBLE.
     */
    public String toString() {
      if (isGenerated())
        return "(" + low + "," + high + "," + max + ")";
      if (isReadyToGenerate())
        return "{" + low + "," + max + "}";
      return "[" + low + "," + max + "]";
    }

    /**
     * Returns a string representation of state that determines if the BBLE
     * is ready to be generated */
    public String genState() {
      return "(sk="+ isStackKnown() + ", lk=" + isLocalKnown() +
	", gen=" + isGenerated() + ")";
    }
  }

  /**
   * Extend BasicBlockLE for handler blocks
   */
  private static final class HandlerBlockLE extends BasicBlockLE {
    /**
     * The OPT_RegisterOperand that code should use to access
     * the caught exception object
     */
    OPT_RegisterOperand exceptionObject;

    /**
     * The synthetic entry basic block for this handler.
     * It contains the instruction sequence to get the caught exception object
     * into a "normal" register operand (exceptionObject);
     */
    OPT_ExceptionHandlerBasicBlock entryBlock;

    /**
     * Create a new exception handler BBLE (and exception handler basic block)
     * for the specified bytecode index and exception type.
     *
     * @param loc bytecode index
     * @param position inline sequence
     * @param ex exception type
     * @param temps the register pool to allocate exceptionObject from
     * @param exprStackSize max size of expression stack
     * @param cfg OPT_ControlFlowGraph into which the block 
     *            will eventually be inserted
     */
    HandlerBlockLE(int loc, OPT_InlineSequence position,
		   OPT_TypeOperand eType, OPT_RegisterPool temps, 
		   int exprStackSize, OPT_ControlFlowGraph cfg) {
      super();
      entryBlock = 
	new OPT_ExceptionHandlerBasicBlock(SYNTH_CATCH_BCI, position, eType, cfg);
      block = new OPT_BasicBlock(loc, position, cfg);
      // NOTE: We intentionally use throwable rather than eType to avoid 
      // having the complexity of having to regenerate the handler when a 
      // new type of caught exception is added. Since we shouldn't care about
      // the performance of code in exception handling blocks, this 
      // should be the right tradeoff.
      exceptionObject = temps.makeTemp(VM_Type.JavaLangThrowableType);
      low = loc;
      high = loc;
      // Set up expression stack on entry to have the caught exception operand.
      stackState = new OperandStack(exprStackSize);
      stackState.push(exceptionObject);
      setStackKnown();
      // entry block contains instructions to transfer the caught 
      // exception object to exceptionObject.
      OPT_Instruction s = 
	Nullary.create(GET_CAUGHT_EXCEPTION, exceptionObject.copyD2D());
      entryBlock.appendInstruction(s);
      s.bcIndex = SYNTH_CATCH_BCI;
      entryBlock.insertOut(block);
    }

    void addCaughtException(OPT_TypeOperand et) {
      entryBlock.addCaughtException(et);
    }

    byte mayCatchException(VM_Type et) {
      return entryBlock.mayCatchException(et);
    }

    byte mustCatchException(VM_Type et) {
      return entryBlock.mustCatchException(et);
    }
  }

  /**
   * Extend BasicBlockLE to support inlining during IR generation.
   */
  private static final class InliningBlockLE extends BasicBlockLE {
    OPT_GenerationContext gc;
    BasicBlockLE epilogueBBLE;

    InliningBlockLE(OPT_GenerationContext c) {
      super();
      gc = c;
    }

    public String toString() {
      return "(Inline method " + gc.method + ")";
    }

    /**
     * delete the outgoing CFG edges from all 
     * basic blocks in the callee (gc.cfg).
     * This is used when the BBLE preceeding the inlined 
     * method block needs to be regenerated, thus forcing 
     * us to discard the callee IR (which may contains
     * control flow links to the caller IR because of exception handlers).
     * <p> 
     * TODO: One might be able to do this more efficiently by  
     * keeping track of the exposed edges in the generation context
     * and commiting them once the top level generation
     * completes.  Probably not worth it, since we expect this 
     * method to be called very infrequently.
     */
    void deleteAllOutEdges() {
      for (OPT_BasicBlock bb = gc.cfg.firstInCodeOrder(); bb != null; 
          bb = bb.nextBasicBlockInCodeOrder()) {
        bb.deleteOut();
      }
    }
  }

  /**
   * Dummy stack slot
   * @see OPT_BC2IR#DUMMY
   */
  private static final class DummyStackSlot extends OPT_Operand {
    OPT_Operand copy() { return this; }
    boolean similar(OPT_Operand op) { return (op instanceof DummyStackSlot); }
    public String toString() { return "<DUMMY>"; }
  }

  /**
   * ReturnAddress operand. Used to represent the address pushed on
   * the expression stack by a JSR instruction.
   */
  public static final class ReturnAddressOperand extends OPT_Operand {
    int retIndex;
    ReturnAddressOperand(int ri) { retIndex = ri; }
    OPT_Operand copy() { return this; }
    boolean similar(OPT_Operand op) {
      return (op instanceof ReturnAddressOperand) && 
	(retIndex == ((ReturnAddressOperand)op).retIndex);
    }
    public String toString() {
      return "<return address " + retIndex + ">";
    }
  }
}
