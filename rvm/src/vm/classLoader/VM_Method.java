/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * A method of a java class.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public abstract class VM_Method extends VM_Member {

  /**
   * current compiled method for this method
   */
  protected VM_CompiledMethod currentCompiledMethod;

  /**
   * exceptions this method might throw (null --> none)
   */
  protected final VM_TypeReference[] exceptionTypes;      

  /**
   * @param declaringClass the VM_Class object of the class that declared this field
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param exceptionTypes exceptions thrown by this method.
   */
  protected VM_Method(VM_Class declaringClass, VM_MemberReference memRef, 
		      int modifiers, VM_TypeReference[] exceptionTypes) {
    super(declaringClass, memRef, modifiers & APPLICABLE_TO_METHODS);
    memRef.asMethodReference().setResolvedMember(this);
    this.exceptionTypes = exceptionTypes;
  }
  
  /**
   * Called from {@link VM_Class#VM_Class(VM_TypeReference, DataInputStream)} to create an
   * instance of a VM_Method by reading the relevant data from the argument bytecode stream.
   * 
   * @param declaringClass the VM_Class object of the class that declared this method
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param input the DataInputStream to read the method's attributes from
   */
  static VM_Method readMethod(VM_Class declaringClass, VM_MemberReference memRef,
			      int modifiers, DataInputStream input) throws IOException {
    ClassLoader cl = declaringClass.getClassLoader();

    int tmp_localWords = 0;
    int tmp_operandWords = 0;      
    byte[] tmp_bytecodes = null;       
    VM_ExceptionHandlerMap tmp_exceptionHandlerMap = null;
    VM_TypeReference[] tmp_exceptionTypes = null;
    int[] tmp_lineNumberMap = null;      

    // Read the attributes
    for (int i = 0, n = input.readUnsignedShort(); i<n; i++) {
      VM_Atom attName   = declaringClass.getUtf(input.readUnsignedShort());
      int     attLength = input.readInt();

      // Only bother to interpret non-boring Method attributes
      if (attName == VM_ClassLoader.codeAttributeName) {
        tmp_operandWords = input.readUnsignedShort();
        tmp_localWords   = input.readUnsignedShort();
        tmp_bytecodes = new byte[input.readInt()];
        input.readFully(tmp_bytecodes);
        int cnt = input.readUnsignedShort();
        if (cnt != 0) {
          tmp_exceptionHandlerMap = new VM_ExceptionHandlerMap(input, declaringClass, cnt);
	}
	// Read the attributes portion of the code attribute
	for (int j = 0, n2 = input.readUnsignedShort(); j<n2; j++) {
	  attName   = declaringClass.getUtf(input.readUnsignedShort());
	  attLength = input.readInt();

	  if (attName == VM_ClassLoader.lineNumberTableAttributeName) {
	    cnt = input.readUnsignedShort();
	    if (cnt != 0) {
	      tmp_lineNumberMap = new int[cnt];
	      for (int k = 0; k<cnt; k++) {
		int startPC = input.readUnsignedShort();
		int lineNumber = input.readUnsignedShort();
		tmp_lineNumberMap[k] = (lineNumber << BITS_IN_SHORT) | startPC;
	      }
	    }
	  } else {
	    // All other entries in the attribute portion of the code attribute are boring.
	    input.skipBytes(attLength);
	  }
	}
      } else if (attName == VM_ClassLoader.exceptionsAttributeName) {
        int cnt = input.readUnsignedShort();
        if (cnt != 0) {
          tmp_exceptionTypes = new VM_TypeReference[cnt];
          for (int j = 0, m = tmp_exceptionTypes.length; j < m; ++j) {
            tmp_exceptionTypes[j] = declaringClass.getTypeRef(input.readUnsignedShort());
	  }
        }
      } else {
	// all other method attributes are boring
        input.skipBytes(attLength);
      }
    }

    if ((modifiers & ACC_NATIVE) != 0) {
      return new VM_NativeMethod(declaringClass, memRef, modifiers, tmp_exceptionTypes);
    } else if ((modifiers & ACC_ABSTRACT) != 0) {
      return new VM_AbstractMethod(declaringClass, memRef, modifiers, tmp_exceptionTypes);
    } else {
      return new VM_NormalMethod(declaringClass, memRef, modifiers, tmp_exceptionTypes,
				 tmp_localWords, tmp_operandWords, tmp_bytecodes, 
				 tmp_exceptionHandlerMap, tmp_lineNumberMap);
    }
  }

  /**
   * Is this method a class initializer?
   */
  public final boolean isClassInitializer() throws VM_PragmaUninterruptible { 
    return getName() == VM_ClassLoader.StandardClassInitializerMethodName;  
  }

  /**
   * Is this method an object initializer?
   */
  public final boolean isObjectInitializer() throws VM_PragmaUninterruptible { 
    return getName() == VM_ClassLoader.StandardObjectInitializerMethodName; 
  }

  /**
   * Is this method a compiler-generated object initializer helper?
   */
  public final boolean isObjectInitializerHelper() throws VM_PragmaUninterruptible { 
    return getName() == VM_ClassLoader.StandardObjectInitializerHelperMethodName; 
  }

  /**
   * Type of this method's return value.
   */
  public final VM_TypeReference getReturnType() throws VM_PragmaUninterruptible {
    return memRef.asMethodReference().getReturnType();
  }

  /**
   * Type of this method's parameters.
   * Note: does *not* include implicit "this" parameter, if any.
   */
  public final VM_TypeReference[] getParameterTypes() throws VM_PragmaUninterruptible {
    return memRef.asMethodReference().getParameterTypes();
  }

  /**
   * Space required by this method for its parameters, in words.
   * Note: does *not* include implicit "this" parameter, if any.
   */
  public final int getParameterWords() throws VM_PragmaUninterruptible {
    return memRef.asMethodReference().getParameterWords();
  }

  /**
   * Has machine code been generated for this method's bytecodes?
   */
  public final boolean isCompiled() {
    return currentCompiledMethod != null;
  }

  /**
   * Get the current compiled method for this method.
   * Will return null if there is no current compiled method!
   * @return compiled method
   */ 
  public final synchronized VM_CompiledMethod getCurrentCompiledMethod() {
    return currentCompiledMethod;
  }

  /**
   * Declared as statically dispatched?
   */
  public final boolean isStatic() throws VM_PragmaUninterruptible {
    return (modifiers & ACC_STATIC) != 0;
  }

  /**
   * Declared as non-overridable by subclasses?
   */
  public final boolean isFinal() throws VM_PragmaUninterruptible {
    return (modifiers & ACC_FINAL) != 0;
  }

  /**
   * Guarded by monitorenter/monitorexit?
   */
  public final boolean isSynchronized() throws VM_PragmaUninterruptible {
    return (modifiers & ACC_SYNCHRONIZED) != 0;
  }

  /**
   * Not implemented in java?
   */
  public final boolean isNative() throws VM_PragmaUninterruptible {
    return (modifiers & ACC_NATIVE) != 0;
  }

  /**
   * Implemented in subclass?
   */
  public final boolean isAbstract() throws VM_PragmaUninterruptible {
    return (modifiers & ACC_ABSTRACT) != 0;
  }

  /**
   * Exceptions thrown by this method - 
   * something like { "java/lang/IOException", "java/lang/EOFException" }
   * @return info (null --> method doesn't throw any exceptions)
   */
  public final VM_TypeReference[] getExceptionTypes() throws VM_PragmaUninterruptible {
    return exceptionTypes;
  }

  /**
   * Is this method interruptible?
   * In other words, should the compiler insert threadswitch points
   * aka yieldpoints in method prologue, epilogue, and backwards branches.
   * Also, methods that are Uninterruptible do not have stackoverflow checks
   * in the method prologue (since there is no mechanism for handling a stackoverflow
   * that doesn't violate the uninterruptiblity of the method).
   * A method is Uninterruptible if 
   * <ul>
   * <li> It is not a <clinit> or <init> method.
   * <li> It is not the synthetic 'this' method used by jikes to
   *      factor out default initializers for <init> methods.
   * <li> it throws the <CODE>VM_PragmaUninterruptible</CODE> exception.
   * <li> its declaring class directly implements the <CODE>VM_Uninterruptible</CODE>
   *      interface and the method does not throw the <CODE>VM_PragmaInterruptible</CODE>
   *      exception.
   * </ul>
   */
  public final boolean isInterruptible() {
    if (isClassInitializer() || isObjectInitializer()) return true;
    if (isObjectInitializerHelper()) return true;
    if (VM_PragmaInterruptible.declaredBy(this)) return true;
    if (VM_PragmaUninterruptible.declaredBy(this)) return false;
    VM_Class[] interfaces = getDeclaringClass().getDeclaredInterfaces();
    for (int i = 0, n = interfaces.length; i < n; ++i) {
      if (interfaces[i].isUninterruptibleType()) return false;
    }
    return true;
  }

  /**
   * Has this method been marked as mandatory to inline?
   * ie., it throws the <CODE>VM_PragmaInline</CODE> exception?
   */
  public final boolean hasInlinePragma() {
    return VM_PragmaInline.declaredBy(this);
  }
    
  /**
   * Has this method been marked as forbidden to inline?
   * ie., it throws the <CODE>VM_PragmaNoInline</CODE> or
   * the <CODE>VM_PragmaNoOptCompile</CODE> exception?
   */
  public final boolean hasNoInlinePragma() {
    return VM_PragmaNoInline.declaredBy(this) || VM_PragmaNoOptCompile.declaredBy(this);
  }
    
  /**
   * Has this method been marked as no opt compile?
   * ie., it throws the <CODE>VM_PragmaNoOptCompile</CODE> exception?
   */
  public final boolean hasNoOptCompilePragma() {
    return VM_PragmaNoOptCompile.declaredBy(this);
  }
    
  /**
   * @return true if the method may write to a given field
   */
  public boolean mayWrite(VM_Field field) {
    return true; // be conservative.  native methods can write to anything
  }

  //------------------------------------------------------------------//
  //                        Section 2.                                //
  // The following are available after the declaring class has been   //
  // "resolved".                                                      //
  //------------------------------------------------------------------//

  /**
   * Get the current instructions for the given method.
   */
  public final synchronized VM_CodeArray getCurrentInstructions() {
    if (VM.VerifyAssertions) VM._assert(declaringClass.isResolved());
    if (isCompiled()) {
      return currentCompiledMethod.getInstructions();
    } else if (!VM.writingBootImage || isNative()) {
      return VM_LazyCompilationTrampolineGenerator.getTrampoline();
    } else {
      compile(); 
      return currentCompiledMethod.getInstructions();
    }
  }

  /**
   * Generate machine code for this method if valid
   * machine code doesn't already exist. 
   * Return the resulting VM_CompiledMethod object.
   * 
   * @return VM_CompiledMethod object representing the result of the compilation.
   */
  public final synchronized void compile() {
    if (VM.VerifyAssertions) VM._assert(declaringClass.isResolved());
    if (isCompiled()) return;

    if (VM.TraceClassLoading && VM.runningVM)  VM.sysWrite("VM_Method: (begin) compiling " + this + "\n");

    VM_CompiledMethod cm = genCode();

    // Ensure that cm wasn't invalidated while it was being compiled.
    synchronized(cm) {
      if (cm.isInvalid()) {
	VM_CompiledMethods.setCompiledMethodObsolete(cm);
      } else {
	currentCompiledMethod = cm;
      }
    }

    if (VM.TraceClassLoading && VM.runningVM)  VM.sysWrite("VM_Method: (end)   compiling " + this + "\n");
  }

  protected abstract VM_CompiledMethod genCode();

  //----------------------------------------------------------------//
  //                        Section 3.                              //
  // The following are available after the declaring class has been // 
  // "instantiated".                                                //
  //----------------------------------------------------------------//

  /**
   * Change machine code that will be used by future executions of this method 
   * (ie. optimized <-> non-optimized)
   * @param compiledMethod new machine code
   * Side effect: updates jtoc or method dispatch tables 
   * ("type information blocks")
   *              for this class and its subclasses
   */ 
  public final synchronized void replaceCompiledMethod(VM_CompiledMethod compiledMethod) {
    if (VM.VerifyAssertions) VM._assert(declaringClass.isInstantiated());
    // If we're replacing with a non-null compiledMethod, ensure that is still valid!
    if (compiledMethod != null) {
      synchronized(compiledMethod) {
	if (compiledMethod.isInvalid()) return;
      }
    }
      
    // Grab version that is being replaced
    VM_CompiledMethod oldCompiledMethod = currentCompiledMethod;
    currentCompiledMethod = compiledMethod;

    // Install the new method in jtoc/tib. If virtual, will also replace in
    // all subclasses that inherited the method.
    getDeclaringClass().updateMethod(this);

    // Now that we've updated the jtoc/tib, old version is obsolete
    if (oldCompiledMethod != null) {
      VM_CompiledMethods.setCompiledMethodObsolete(oldCompiledMethod);
    }
  }

  /**
   * If CM is the current compiled code for this, then invaldiate it. 
   */
  public final synchronized void invalidateCompiledMethod(VM_CompiledMethod cm) {
    if (VM.VerifyAssertions) VM._assert(declaringClass.isInstantiated());
    if (currentCompiledMethod == cm) {
      replaceCompiledMethod(null);
    }
  }
}
