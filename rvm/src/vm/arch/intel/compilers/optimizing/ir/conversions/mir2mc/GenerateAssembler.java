/*
 * (C) Copyright IBM Corp. 2001
 */
import java.io.*;
import java.util.*;
import java.lang.reflect.*;

class GenerateAssembler {

    /** Global flag controlling printing of debugging information */
    static final boolean DEBUG = false;

    /** Global reference to the assembler being generated */
    static FileWriter out;

    /**
     * Write a single string to the assembler source file.
     * @param String s  The string to be written
     */
    private static void emit(String s) {
	try {
	    out.write(s, 0, s.length());
	} catch (IOException e) {
	    e.printStackTrace();
	    System.exit(-1);
	}
    }

    /**
     * Write tabification to the assembler source file.  This is used 
     * to make the generates source more readable by identing it.
     * @param int level  The level of indentation to generate
     */
    private static void emitTab(int level) {
	for(int i = 0; i < level; i++) emit("  ");
    }

    /**
     *  Global reference to the OPT_InstructionFormatTable class that 
     * contains descriptions of each optimizing compiler instruction
     * format that sis visible to the assembler (i.e. the MIR_* 
     * instruction formats.
     */
    private static Class formats;

    /**
     *  Load the instruction format table, and throw up if that is
     * not possible.
     */
    static {
	try {
	    formats = Class.forName("OPT_InstructionFormatTables");
	} catch (ClassNotFoundException e) {
	    e.printStackTrace();
	    System.exit( -1 );
	}
    }

    /**
     *  Global reference to the opcode argument table for the current
     * opcode being processed.  This table is null unless some of the
     * operands in the OPT_Instruction are to ignored when generating
     * code for the opcode.  Ignoring arguments is an ad-hock special
     * case that is controlled by the global opcodeArgTable.
     */
    static int[] currentOpcodeArgTable;

    /**
     *  Global reference to the table of symbolic names of the arguments
     * to the current MIR_ instruction format.  This information is read
     * from the OPT_InstructionFormatTable
     */
    static String[] currentOpcodeSymbolicNames;

    /**
     *  The current IA32 opcode being processed.  This is the name of
     * IA32 instruction.  Typically, it is the name of the opt compiler
     * IA32_* opcode as well, but there are exceptions in that multiple
     * IA32_* opcodes can map to the same IA32 instruction
     */
    static String currentOpcode;

    /**
     *  The current MIR_* instruction format for the IA32_* opt compiler
     * opcode(s) being processed.
     */
    static String currentFormat;

    /**
     *  Global table mapping opt compiler IA32_* opcodes to arrays
     * listing the set of OPT_Instruction operands that are to be used
     * as arguments to the IA32 architecture instruction.  This is used
     * when an instruction has extra operands that are not used in
     * assembly (e.g. CALL) has mappings only for such instructions.
     */
    static Hashtable opcodeArgTables;

    /**
     *  Initialize the opcodeArgTables table
     */
    static {
	opcodeArgTables = new Hashtable();
	opcodeArgTables.put("CALL", new int[]{2});
	opcodeArgTables.put("INT", new int[]{1});
	opcodeArgTables.put("CDQ", new int[]{0});
	opcodeArgTables.put("DIV", new int[]{1,2});
	opcodeArgTables.put("IDIV", new int[]{1,2});
	opcodeArgTables.put("MUL", new int[]{1,2});
	opcodeArgTables.put("IMUL1", new int[]{1,2});
	opcodeArgTables.put("DIV", new int[]{1,2});
	opcodeArgTables.put("IDIV", new int[]{1,2});
	opcodeArgTables.put("SET", new int[]{1,0});
	opcodeArgTables.put("CMPXCHG", new int[]{1,2});
    }

    /**
     *  Set the current opcode.  This sets four global fields:
     * the currentOpcode, the currentOpcodeArgTable, the currentFormat
     * and the currentOpcodeSymbolicNames.
     *
     * @param opcode  The IA32 architecture opcode to make the current opcode
     */
    static void setCurrentOpcode(String opcode) {
	try {
	    currentOpcode = opcode;
	    currentOpcodeArgTable = (int[]) opcodeArgTables.get( opcode );
	    currentFormat = OPT_OperatorFormatTable.getFormat( opcode );
	    Field f = formats.getDeclaredField(currentFormat+"ParameterNames");
	    currentOpcodeSymbolicNames = (String[]) f.get( null );
	} catch (Throwable e) {
	    System.err.println("Cannot handle VM_Assembler opcode " + opcode);
	    e.printStackTrace();
	    System.exit( -1 );
	}
    }

    /**
     * Constant representing immediate arguments to VM_Assembler calls
     */
    static final int Immediate = 0;
    /**
     * Constant representing register arguments to VM_Assembler calls.
     * This covers the cases when a register is encoded into the mod/rm
     * byte; the VM_Assembler handles the detais of generating either 
     * the reg bits of the mod/rm byte or encoding a register as mod 11.
     */
    static final int Register = 1;
    /**
     * Constant representing condition arguments to VM_Assembler calls.
     * Such operands are not arguments to the ultimate IA32 machine 
     * code instruction, but they are used to calculate the opcode that
     * is generated.
     */
    static final int Condition = 2;
    /**
     * Constant representing IA32 memory operands that use register-
     * displacement addressing mode (usually mod bits 01 and 10) arguments 
     * to VM_Assembler calls.  The VM_Assembler takes care of choosing the
     * right mode for the size of the displacement, so this one mode
     * covers two of the four addressing modes the IA32 has.  The
     * VM_Assembler also handles the special cases in which this mode
     * requires weird SIB bytes.
     */
    static final int RegisterDisplacement = 3;
    /**
     * Constant representing arguments to VM_Assembler calls that use the
     * scaled-index-base (SIB) addressing mode in the special way that does
     * not use a base register.  The OPT_Assembler simply assumes it has
     * an [index < < scale + disp] addressing mode, and the VM_Assembler takes
     * care of generating the special mod/rm that causes the base register
     * to be ignored.
     */
    static final int RegisterOffset = 4;
    /**
     * Constant representing scaled-index-base (SIB) mode arguments to 
     * VM_Assembler calls.
     */
    static final int RegisterIndexed = 5;
    /**
     * Constant representing register-indirect arguments to VM_Assembler 
     * calls.  This mode handles what is (usually) mod 00 in the mod/rm
     * byte.
     */
    static final int RegisterIndirect = 6;
    /**
     * Constant representing labels used as branch targets.  While code
     * is being generated, the machine code offset for a forward branch
     * cannot, in general, be computed as the target code has not been
     * generated yet.  The OPT_Assembler uses synthetic code offsets,
     * based upon the order of OPT_Instructions in the code being 
     * compiled, to communicate forward branch targets to the 
     * VM_Assembler.  These synthetic offsets are passed to the
     * VM_Assembler where it expected Label arguments.
     */
    static final int Label = 7;
    /**
     * Constant representing arguments to VM_Assembler calls in which
     * it may be either a backward branch target (resolved to an
     * immediate being the exact branch displacement) or a forward
     * branch (which will be a synthetic Label).
     */
    static final int LabelOrImmediate = 8;

    /**
     * How many different sizes of instruction operand are there, not
     * counting the standard double word.
     */
    static final int SIZES = 3;
    /**
     * Constant representing instructions that operate upon bytes
     */
    static final int Byte = 9;
    /**
     * Constant representing instructions that operate upon words (16 bits)
     */
    static final int Word = 10;
    /**
     * Constant representing instructions that operate upon quad words (64 bits)
     */
    static final int Quad = 11;

    /**
     *  This array denotes all possible encodings in a VM_Assembler emitter
     * function.  It includes all possible operand types and all possible
     * instruction sizes.  For all of the constants corresponding to a 
     * possible operand type or instruction size, the corresponding entry
     * is this table holds the string that the VM_Assembler uses to denote
     * that operand type or instruction size.
     *
     * This table is used when parsing a VM_Assembler emitter name to create 
     * a descriptor that denotes the operand size and types of the given
     * emitter in terms of the constants.
     *
     * This table is also used when generating the OPT_Assembler emitter
     * functions to allow the generator to pick which queries to use to
     * dispatch an OPT_Instruction to the appropriate VM_Assembler emitter.
     */
    static final String[] encoding = 
    {"Imm",		// encoding[Immediate]
     "Reg",		// encoding[Register]
     "Cond",		// encoding[Condition]
     "RegDisp",		// encoding[RegisterDisplacement]
     "RegOff",		// encoding[RegisterOffset]
     "RegIdx",		// encoding[RegisterIndexed]
     "RegInd",		// encoding[RegisterIndirect]
     "Label",		// encoding[Label]
     "ImmOrLabel",	// encoding[LabelOrImmediate]
     "Byte",
     "Word",
     "Quad"};

    /**
     * For a given string representing a valid operand encoding for the 
     * VM_Assembler, return the corresponding OPT_Assembler constant.  This
     * function only looks for encodings of operand types, and will not
     * accept strings that correspond to size encodings.
     *
     * @param str A valid VM_Assembler encoding of operand type
     * @return The OPT_Assembler constant corresponding to str, or -1 if none
     *
     */
    private static int getEncoding(String str) {
	for(int i = 0; i < encoding.length - SIZES; i++)
	    if (encoding[i].equals(str))
		return i;

	return -1;
    }

    /**
     * For a given string representing a valid size encoding for the 
     * VM_Assembler, return the corresponding OPT_Assembler constant.  This
     * function only looks for encodings of sizes, and will not accept 
     * strings that correspond to operand types.
     *
     * @param str A valid VM_Assembler encoding of operand size
     * @return The OPT_Assembler constant corresponding to str, or -1 if none
     *
     */
    private static int getSize(String str) {
	for(int i = encoding.length - SIZES; i < encoding.length; i++)
	    if (encoding[i].equals(str))
		return i;

	return -1;
    }

    /**
     * For a given operand number, return a string which is a valid Java
     * expression for reading that operand out of the current instruction.
     * This function uses the currentOpcodSymbolicNames table to determine
     * the appropriate accessor (e.g. getValue if the current name is Value),
     * and it uses the currentOpcodeArgTable (in cases where it has an
     * entry for the kind of instruction being processed) to determine which
     * operand in OPT_Instruction corresponds to operand sought.
     *
     * @param op  The operand number sought.
     * @return A Java expression for adcessing the requested operand.
     */
    private static String getOperand(int op) {
	try {
	    if (currentOpcodeArgTable == null)
		return currentFormat + ".get" + currentOpcodeSymbolicNames[op] + "(inst)";
	    else
		return currentFormat + ".get" + currentOpcodeSymbolicNames[currentOpcodeArgTable[op]] + "(inst)";
	} catch (ArrayIndexOutOfBoundsException e) {
	    System.err.println(currentOpcode + ": cannot access operand " + op  + ":");
	    for(int i = 0; i < currentOpcodeSymbolicNames.length; i++)
		System.err.println( currentOpcodeSymbolicNames[i] );
	    System.exit( -1 );
	    return null;
	}
    }

    /**
     * Given an operand number and an encoding, generate a test to
     * determine whether the given operand matches the encoding.  That
     * is, generate code to the OPT_Assembler that examines a given operand
     * of the current OPT_Instruction, and determines whether it is of
     * the type encoded by the given encoding.  This is used to generate the
     * if statements of the dispatch functions for each opt compiler opcode.
     *
     * @param argNumber The argument to examine
     * @param argEncoding The encoding for which to check 
     */
    private static void emitTest(int argNumber, int argEncoding) {   
	if (argEncoding < encoding.length - SIZES)
	    emit("is" + encoding[argEncoding] + "(" + getOperand(argNumber) + ")");
	else
	    emit("is" + encoding[argEncoding] + "(inst)");
    }

    /**
     * Generate code to verify that a given operand matches a given encoding.
     * Since the IA32 architecture is not exactly orthogonal (please note
     * the charitable understatement), there are cases when the opt assembler
     * can determine the VM_Assembler emitter to call without looking at
     * all (or, in some cases, any) of the arguments of the OPT_Instruction.
     * An example is the ENTER instruction that only takes one immediate
     * parameter, so the opt assembler could simply call that VM_Assembler
     * emiiter without checking that argument is really an immediate. In 
     * such cases, the opt assembler generates guarded tests that verify 
     * that OPT_Instruction operand actually matches the required encoding.
     * This function emits such tests to the assembler being generated.
     *
     * @param argNumber The argument to examine
     * @param argEncoding The encoding for which to check
     * @param level current level for generating pretty, tabified output
     */
    private static void emitVerify(int argNumber, int argEncoding, int level) {   
	emitTab(level);
	emit("if (VM.VerifyAssertions && !");
	emitTest(argNumber, argEncoding);
	emit(") VM.assert(false, inst.toString());\n");
    }

    /**
     * Generate code to fetch all the arguments needed for a given operand
     * number and encoding.  The different argument encodings of the
     * VM_Assembler need different arguments to be passed to the emitter
     * function.  For instance, a register-displacement mode operand
     * needs to be given a base register and an immediate displacement.
     * This function generates the appropriate arguments given the
     * operand number and encoding; that is, it generates reads of the
     * appropriate OPT_Instruction argument and fetches of the appropriate
     * pieces of information from the operand.
     * 
     * @param argNumber The argument being generated.
     * @param argEcoding The encoding to use.
     */
    private static void emitArgs(int argNumber, int argEncoding) {
	String op = getOperand(argNumber);
	if (argEncoding == LabelOrImmediate)
	    emit("getImm(" + op + "), getLabel(" + op + ")");
    	else if (argEncoding == RegisterDisplacement)
	    emit("getBase(" + op + "), getDisp(" + op + ")");
	else if (argEncoding == RegisterOffset)
	    emit("getIndex(" + op + "), getScale(" + op + 
		 "), getDisp(" + op + ")");
	else if (argEncoding == RegisterIndexed)
	    emit("getBase(" + op + "), getIndex(" + op + 
		 "), getScale(" + op + "), getDisp(" + op + ")");
	else if (argEncoding == RegisterIndirect)
	    emit("getBase(" + op + ")");
	else 
	    emit("get" + encoding[argEncoding] + "(" + op + ")");
    }

    /**
     *  This exception class is used to indicate that GenerateAssembler
     * found an emit* method in the vM_Assembler that it does not 
     * understand. To generate the OPT_Assembler for a given 
     * IA32 OPT_Operator, GenerateAssembler looks at all of the emit* 
     * methods for the corresponding IA32 opcode in the VM_Assembler.  It 
     * parses each name to determine what kinds of operands it expects and
     * what size operands it uses; this requires the emit* methods to
     * have stylized names (see the header comment of VM_Assembler for 
     * details).  If an emit* method name does not have the stylized 
     * format required, GenerateAssembler will throw a BadEmitMethod
     * exception and abort.
     */
    static class BadEmitMethod extends RuntimeException {

	/**
	 *  Create a BadEmitMethod exception indicating that 
	 * GenerateAssembler cannot understand the code portion
	 * of the method name methodName.
	 *
	 * @param methodName The method name causing trouble
	 * @param code The portion of methodName that does not parse
	 */
	BadEmitMethod(String methodName, String code) {
	    super("cannot interpret method " + methodName + "(" + code + ")");
	}

    }

    /**
     *  An EmitterDescriptor represents a single emit method from the
     * VM_Assembler: it explicitly represents the types of operands the
     * method expects, their number, and the size of the data it uses.
     * When GenerateAssembler encounters an emit* method from the 
     * VM_Assembler, it creates an EmitterDescriptor for it.  Based upon 
     * the stlyized form the method name is required to have, the
     * EmitterDexcriptor represents information about its arguments. This 
     * information is stored in terms of the GenerateAssembler constants 
     * that represent operand type and size.
     * <P>
     * The EmitterDescriptor class encapsulates the logic for parsing the 
     * stylized emit* method names that the VM_Assembler has, and turning
     * them into the explicit representation that GenerateAssembler uses.  
     * If parsing a name fails, a {@link GenerateAssembler.BadEmitMethod} 
     * runtime exception is thrown and assembler generation is aborted.
     * <P>
     * <HR>
     * <EM>See the descriptions of the GenerateAssembler constants:</EM>
     * <DL>
     * <DT> <EM>Operand types</EM>
     * <DI> 
     *  <UL>
     *   <LI> {@link #Immediate}
     *   <LI> {@link #Label}
     *   <LI> {@link #LabelOrImmediate}
     *   <LI> {@link #Register}
     *   <LI> {@link #RegisterIndirect}
     *   <LI> {@link #RegisterOffset}
     *   <LI> {@link #RegisterIndexed}
     *  </UL>
     * <DT> <EM>Data size</EM>
     *  <UL>
     *   <LI> {@link #Byte}
     *   <LI> {@link #Word}
     *   <LI> {@link #Quad}
     *  </UL>
     * </DL>
     */
    static class EmitterDescriptor {
	private int size;
	private int count;
	private final int args[];

	/**
	 * Create an EmitterDescriptor for the given methodName.  This 
	 * conmstructor creates a descriptor that represents explicitly 
	 * the types and size of the operands of the given emit* method.
	 * This constructor encapsulate the logic to parse the given
	 * method name into the appropriate explicit representation.
	 */
	EmitterDescriptor(String methodName) {
	    StringTokenizer toks = new StringTokenizer(methodName, "_");
	    toks.nextElement(); // first element is emitXXX;
	    args = new int[ toks.countTokens() ];
	    this.size = 0;
	    this.count = 0;
	    for(int i = 0; i < args.length; i++) {
		String cs = toks.nextToken();
		int code = getEncoding(cs);
		int size = GenerateAssembler.getSize(cs);

		if (DEBUG) {
		    System.err.println(methodName + "[" + i + "] is " + code + "," + size + " for " + cs);
		}

		if (code != -1)
		    args[count++] = code;
		else if (size != -1)
		    this.size = size;
		else
		    throw new BadEmitMethod( methodName, cs );
	    }
	}

	/**
	 *  This method checks whether the emit* method represented by
	 * this EmitterDescriptor expects the argument type represented
	 * by enc as its argument'th operand.  If enc is an operand type
	 * encoding, this method checks wether the given argument is of
	 * the appropriate type.  If enc is an operand size encoding,
	 * the argument parameter is ignored, and this method checks
	 * whether the emit* method represented operates upon data of
	 * the desired size.
	 * <P>
	 * <EM>See the descriptions of the GenerateAssembler constants:</EM>
	 * <DL>
	 * <DT> <EM>Operand types</EM>
	 * <DI> 
	 *  <UL>
	 *   <LI> {@link #Immediate}
	 *   <LI> {@link #Label}
	 *   <LI> {@link #LabelOrImmediate}
	 *   <LI> {@link #Register}
	 *   <LI> {@link #RegisterIndirect}
	 *   <LI> {@link #RegisterOffset}
	 *   <LI> {@link #RegisterIndexed}
	 *  </UL>
	 * <DT> <EM>Data size</EM>
	 *  <UL>
	 *   <LI> {@link #Byte}
	 *   <LI> {@link #Word}
	 *   <LI> {@link #Quad}
	 *  </UL>
	 * </DL>
	 * <P>
	 * @param argument The operand number examined 
	 * @param enc The argument type queried, as encoded as one of
	 *    the operand type constants used throughout 
	 *    GenerateAssembler.
	 *
	 * @return True if this method expects an argument type encoded
	 *    by enc as its argument'th operand, and false otherwise.
	 */
	boolean argMatchesEncoding(int argument, int enc) {
	    if (enc < encoding.length - SIZES)
		return (count > argument) && args[argument] == enc;
	    else
		return size == enc;
	}

	/**
	 * Access the array that stores the encodings of the arguments
	 * to the emit method represented by this EmitterDescriptor.
	 *
	 * @return the array of argument encodings
	 */
	int[] getArgs() { return args; }

	/**
	 * Access the data size operated upon by emit method represented 
	 * by this EmitterDescriptor.
	 *
	 * @return data size for this descriptor
	 */
	int getSize() { return size; }

	/**
	 * Access the number of operands operated upon by emit method 
	 * represented by this EmitterDescriptor.
	 *
	 * @return number of operands for this descriptor
	 */
	int getCount() { return count; }

	public String toString() {
	    StringBuffer s = new StringBuffer();
	    s.append ("ed:");
	    for(int i = 0; i < count; i++)
		s.append(" " + encoding[args[i]]);
	    if (size != 0) s.append(" (" + encoding[size] + ")");
	    return s.toString();
	}
    }
    
    static class EmitterSet {
	private final Set emitters = new HashSet();

	public String toString() {
	    StringBuffer s = new StringBuffer();
	    s.append("Emitter Set of:\n");
	    Iterator i = emitters.iterator();
	    while (i.hasNext()) 
		s.append(i.next().toString() + "\n");
	    
	    s.append("-------------\n");
	    return s.toString();
	}

	boolean isSingleton() {
	    return  (emitters.size() == 1);
	}

	void add(EmitterDescriptor ed) {
	    emitters.add( ed );
	}

	private int countEncoding(int n, int code) {
	    Iterator i = emitters.iterator();
	    int count = 0;
	    while (i.hasNext())
		if (((EmitterDescriptor)i.next()).argMatchesEncoding(n, code))
		    count++;
	    return count;
	}

	private int getEncodingSplit(int n, int code) {
	    int count = countEncoding(n, code);
	    return Math.abs( (emitters.size() - count) - count );
	}

	static class SplitRecord {
	    int argument;
	    int test;

	    SplitRecord(int argument, int test) {
		this.argument = argument;
		this.test = test;
	    }
	}

	private EmitterSet[] makeSplit(SplitRecord split) {
	    int arg = split.argument;
	    int test = split.test;
	    EmitterSet yes = new EmitterSet();
	    EmitterSet no = new EmitterSet();
	    Iterator i = emitters.iterator();
	    while (i.hasNext()) {
		EmitterDescriptor ed = (EmitterDescriptor) i.next();
		if (ed.argMatchesEncoding(arg, test))
		    yes.add( ed );
		else
		    no.add( ed );
	    }

	    return new EmitterSet[]{yes, no};
	}

	SplitRecord split() {
	    int splitArg = -1;
	    int splitTest = -1;
	    int splitDiff = 1000;
	    for(int arg = 0; arg < 4; arg++) {
		for (int test = 0; test < encoding.length; test++) {
		    int c = getEncodingSplit(arg, test);
		    if (c == 0)
			return new SplitRecord(arg, test);
		    else if (c < splitDiff) {
			splitArg = arg;
			splitTest = test;
			splitDiff = c;
		    }
		}
	    }

	    return new SplitRecord(splitArg, splitTest);
	}

	private void emitEmitCall(String opcode, int[] args, int count, int level, int size) {
	    emitTab(level);
	    emit("emit" + opcode);
	    for(int i = 0; i < count; i++)
		emit("_" + encoding[args[i]]);
	    if (size != 0) emit("_" + encoding[size]);

	    if (count == 0)
		emit("();\n");
	    else {
		emit("(");
		for(int i = 0; i < count; i++) {
		    emit("\n");
		    emitTab(level+1);
		    emitArgs(i, args[i]);
		    if (i == count-1)
			emit(");\n");
		    else
			emit(",");
		}
	    }
	}

	private void emitSingleton(String opcode, boolean[][] testsPerformed, int level) {
	    EmitterDescriptor ed = 
		(EmitterDescriptor) emitters.iterator().next();

	    int[] args = ed.getArgs();
	    int count = ed.getCount();
	    for(int i = 0; i < count; i++) 
		if (! testsPerformed[i][args[i]])
		    emitVerify(i, args[i], level);

	    int size = ed.getSize();
	    if (size != 0) {
		boolean needed = true;

		for(int i = 0; i < count; i++) 
		    if (testsPerformed[i][size])
			needed = false;
		    
		if (needed)
		    emitVerify(0, size, level);

		if (size == Byte)
		    for(int i = 0; i < count; i++) 
			if (args[i] == Register)
			    if (currentOpcode.indexOf("MOVZX") == -1 &&
				currentOpcode.indexOf("MOVSX") == -1)
			    {
				emitTab(level);
				emit("if (VM.VerifyAssertions && !(");
				emitArgs(i, Register);
				emit(" < 4)) VM.assert(false, inst.toString());\n");
			    }
		
	    }

	    emitEmitCall(opcode, args, count, level, ed.getSize());
	}

	private void emitSet(String opcode, boolean[][] testsPerformed, int level) {
	    if (emitters.isEmpty()) {
		// do nothing
	    } else if (isSingleton())
		emitSingleton(opcode, testsPerformed, level);
	    else {
		SplitRecord rec = split();

		if (DEBUG) {
		    for(int i = 0; i < level; i++) System.err.print("  ");
		    System.err.println("split of " + opcode + "[" + rec.argument + "] for " + encoding[rec.test]);
		}

		if (testsPerformed[rec.argument][rec.test] == true) {
		    System.err.println("repeated split of " + opcode + "[" + rec.argument + "] for " + encoding[rec.test]);
		    System.err.println( this );
		    System.exit( -1 );
		}

		testsPerformed[rec.argument][rec.test] = true;
		EmitterSet[] splits = makeSplit(rec);
		emitTab(level);	emit("if (");
		emitTest( rec.argument, rec.test );
		emit(") {\n");
		splits[0].emitSet(opcode, testsPerformed, level+1);
		emit("\n"); emitTab(level); emit("} else {\n");
		splits[1].emitSet(opcode, testsPerformed, level+1);
		emitTab(level); emit("}\n");
		testsPerformed[rec.argument][rec.test] = false;
	    }
	}
    }

    static Class lowLevelAsm;

    private static EmitterSet 
	buildSetForOpcode(Method[] emitters, String opcode)
    {
	EmitterSet s = new EmitterSet();
	for(int i = 0; i < emitters.length; i++) {
	    Method m = emitters[i];
	    if (m.getName().startsWith("emit" + opcode + "_") 
		                    ||
		m.getName().equals("emit" + opcode))
	    {
		s.add(new EmitterDescriptor(m.getName()));
	    }
	}

	return s;
    }

    private static Set excludedOpcodes;

    static {
	excludedOpcodes = new HashSet();
	excludedOpcodes.add("FSAVE");
	excludedOpcodes.add("FADDP");
	excludedOpcodes.add("FDIVP");
	excludedOpcodes.add("FDIVRP");
	excludedOpcodes.add("FSUBP");
	excludedOpcodes.add("FSUBRP");
	excludedOpcodes.add("FMULP");
	excludedOpcodes.add("FNSTSW");
	excludedOpcodes.add("FUCOMPP");
	excludedOpcodes.add("SAHF");
	excludedOpcodes.add("NOP");
	excludedOpcodes.add("RDTSC");
	excludedOpcodes.add("ENTER");
	excludedOpcodes.add("LEAVE");
    }

    private static Set getOpcodes(Method[] emitters) {
	Set s = new HashSet();
	for(int i = 0; i < emitters.length; i++) {
	    String name = emitters[i].getName();
	    if (DEBUG) System.out.println(name);
	    if (name.startsWith("emit")) {
		int posOf_ = name.indexOf('_');
		if (posOf_ != -1) {
		    String opcode = name.substring(4, posOf_);
		    if (! excludedOpcodes.contains(opcode)) s.add( opcode );
		} else {
		    String opcode = name.substring(4);
		    // make sure it is an opcode
		    if (opcode.equals(opcode.toUpperCase(Locale.getDefault())))
			if (! excludedOpcodes.contains(opcode))
			    s.add( opcode );
		}
	    }
	}

	return s;
    }

    private static Set getErrorOpcodes(Set emittedOpcodes) {
	Iterator e = OPT_OperatorFormatTable.getOpcodes();
	Set errorOpcodes = new HashSet();
	while (e.hasNext()) {
	    String opcode = (String) e.next();
	    if (! emittedOpcodes.contains(opcode))
		errorOpcodes.add( opcode );
	}

	return errorOpcodes;
    }

    private static Set getMatchingOperators(String lowLevelOpcode) {
	Iterator e = OPT_OperatorFormatTable.getOpcodes();
	Set matchingOperators = new HashSet();
	while (e.hasNext()) {
	    String o = (String) e.next();
	    if (o.equals(lowLevelOpcode) || o.startsWith(lowLevelOpcode+"$"))
		matchingOperators.add( o );
	}

	return matchingOperators;
    }

    public static void main(String[] args) {
	try {
	    out = new FileWriter(System.getProperty("generateToDir") + "/OPT_Assembler.java");
	} catch (IOException e) {
	    e.printStackTrace();
	    System.exit( -1 );
	}

	try {
	    lowLevelAsm = Class.forName("VM_Assembler");
	} catch (ClassNotFoundException e) {
	    e.printStackTrace();
	    System.exit( -1 );
	}

	emit("import instructionFormats.*;\n\n");

	emit("class OPT_Assembler extends OPT_AssemblerBase {\n\n");

	emitTab(1); emit("OPT_Assembler(int bcSize, boolean print) {\n");
	emitTab(2);   emit("super(bcSize, print);\n");
	emitTab(1); emit("}");
	emit("\n\n");

	Method[] emitters = lowLevelAsm.getDeclaredMethods();
	Set opcodes = getOpcodes(emitters);

	Iterator i = opcodes.iterator();
	while (i.hasNext()) {
	    String opcode = (String) i.next();
	    setCurrentOpcode( opcode );
	    emitTab(1);
	    emit("private void do" + opcode + "(OPT_Instruction inst) {\n");
	    EmitterSet emitter = buildSetForOpcode(emitters, opcode);
	    boolean[][] tp = new boolean[4][ encoding.length ];
	    emitter.emitSet(opcode, tp, 2);
	    emitTab(1);
	    emit("}\n\n");
	}

	emitTab(1); emit("private int instructionCount = 0;\n\n");

	emitTab(1); emit("void doInst(OPT_Instruction inst) {\n");
	emitTab(2);    emit("resolveForwardReferences(++instructionCount);\n");
	emitTab(2);    emit("switch (inst.getOpcode()) {\n");

	Set emittedOpcodes = new HashSet();

	i = opcodes.iterator();
	while (i.hasNext()) {
	    String opcode = (String) i.next();
	    Iterator operators = getMatchingOperators( opcode ).iterator();
	    while ( operators.hasNext() ) {
		Object operator = operators.next();
		emitTab(3); 
		emittedOpcodes.add( operator );
		emit("case IA32_" + operator + "_opcode:\n");
	    }
	    emitTab(4);    emit("do" + opcode + "(inst);\n");
	    emitTab(4);    emit("break;\n");
	}

	// Kludge for IA32_LOCK which needs to call emitLockNextInstruction
	emittedOpcodes.add("LOCK");
	emitTab(3);    emit("case IA32_LOCK_opcode:\n");
	emitTab(4);    emit("emitLockNextInstruction();\n");
	emitTab(4);    emit("break;\n");
	
	Set errorOpcodes = getErrorOpcodes( emittedOpcodes );
	if (! errorOpcodes.isEmpty()) {
	    i = errorOpcodes.iterator();
	    while (i.hasNext()) {
		emitTab(3); 
		emit("case IA32_" + i.next() + "_opcode:\n");
	    }
	    emitTab(4); emit("throw new OPT_OptimizingCompilerException(inst + \" has unimplemented IA32 opcode (check excludedOpcodes)\");\n");
	}
	
	emitTab(2);    emit("}\n");
	emitTab(2);    emit("inst.setmcOffset( mi );\n");
	emitTab(1); emit("}\n\n");
	
	emit("\n}\n");

	try {
	    out.close();
	} catch (IOException e) {
	    e.printStackTrace();
	    System.exit( -1 );
	}
    }
}
