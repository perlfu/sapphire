/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.StringTokenizer;

/**
 * Represents an inlining sequence.
 * Used to uniquely identify program locations.
 * 
 * @author Igor Pechtchanski
 * @author Stephen Fink
 */
final class OPT_InlineSequence {
  final static boolean DEBUG=false;

  /**
   * Current method.
   */
  VM_Method method;

  /**
   * Caller info.  null if none.
   */
  OPT_InlineSequence caller;

  /**
   * bytecode index (in caller) of call site
   */
  int bcIndex;

  /**
   * @return contents of {@link #method}
   */
  public VM_Method getMethod() { 
    return method; 
  }

  /**
   * @return contents of {@link #caller}
   */
  public OPT_InlineSequence getCaller() {
    return caller; 
  }

  /**
   */
   public boolean equals(Object o) {
      if (!(o instanceof OPT_InlineSequence)) return false;
      OPT_InlineSequence is = (OPT_InlineSequence)o;
      if (method == null) return (is.method == null);
      if (!method.equals(is.method)) return false;
      if (bcIndex != is.bcIndex) return false;
      if (caller == null) return (is.caller== null);
      return (caller.equals(is.caller));
   }

  /**
   * Constructs a new top-level inline sequence operand.
   * 
   * @param method current method
   */
  OPT_InlineSequence(VM_Method method) {
    this(method, null, -1);
  }

  /**
   * Constructs a new inline sequence operand.
   *
   * @param method current method
   * @param caller caller info
   * @param bcIndex bytecode index of call site
   */
  OPT_InlineSequence(VM_Method method,
		     OPT_InlineSequence caller,
		     int bcIndex) {
    this.method = method;
    this.caller = caller;
    this.bcIndex = bcIndex;
  }

  /**
   * Returns the string representation of this inline sequence.
   */
  public String toString() {
    StringBuffer sb = new StringBuffer(" ");
    for (OPT_InlineSequence is = this; is != null; is = is.caller)
      sb.append(is.method.getDeclaringClass().getDescriptor()).append(" ").
	append(is.method.getName()).append(" ").
	append(is.method.getDescriptor()).append(" ").
	append(is.bcIndex).append(" ");
    return sb.toString();
  }
  
  /**
   * return the depth of inlining: (0 corresponds to no inlining)
   */
  public int getInlineDepth() {
    int depth = 0;
    OPT_InlineSequence parent = this.caller;
    while (parent != null) { 
      depth++;
      parent = parent.caller;
    }
    return depth;
  }

  /**
   * Return the root method of this inline sequence
   */
  public VM_Method getRootMethod() {
    OPT_InlineSequence parent = this;
    while (parent.caller != null) { 
      parent = parent.caller;
    }
    return parent.method;
  }

  /**
   * Does this inline sequence contain a given method?
   */
  public boolean containsMethod(VM_Method m) {
    if (method == m) return true;
    if (caller == null) return false;
    return (caller.containsMethod(m));
  }


  /**
   * Constructs a new inline sequence operand from a string generated by 
   * OPT_InlineSequence.toString()
   * 
   * WARNING: multiple calls to this constructor will not reuse previously 
   *          created inline sequences.  It is currenly used only for testing
   * 
   * @param string The string of characters defining the OPT_InlineSequence
   */
  OPT_InlineSequence(String string) {
    readObject(string);
  }
  
  /**
   * Constructs a new inline sequence operand from a string generated by 
   * OPT_InlineSequence.toString()
   * 
   * WARNING: multiple calls to this constructor will not reuse previously 
   *          created inline sequences. 
   * 
   * @param string The string of characters defining the OPT_InlineSequence
   */
  void readObject(String inputString) {
    if (OPT_InlineSequence.DEBUG) {
      VM.sysWrite("OPT_InlineSequence, considering string '");
      VM.sysWrite(inputString);
      VM.sysWrite("'\n");
    }
    
    int bytecodeOffset = 0;
    // Get the method and BC index 
    StringTokenizer parser = new StringTokenizer(inputString);
    String nextToken1 = parser.nextToken();
    String nextToken2 = parser.nextToken();
    String nextToken3 = parser.nextToken();
    VM_Method method = null;
    VM_Atom methodClass = VM_Atom.findOrCreateUnicodeAtom(nextToken1);
    VM_Atom methodName = VM_Atom.findOrCreateUnicodeAtom(nextToken2);
    VM_Atom methodDescriptor = VM_Atom.findOrCreateUnicodeAtom(nextToken3);
    method = VM_ClassLoader.findOrCreateMethod(methodClass,methodName,methodDescriptor);
    if (OPT_InlineSequence.DEBUG) {
      VM.sysWrite("Inline Sequence method: ");
      VM.sysWrite(methodClass);
      VM.sysWrite(" ");
      VM.sysWrite(methodName);
      VM.sysWrite(" ");
      VM.sysWrite(methodDescriptor);
      VM.sysWrite("\n");
    }
    String nextToken4 = parser.nextToken();
    bytecodeOffset = Integer.parseInt( nextToken4 );
    
    if (OPT_InlineSequence.DEBUG) {
      VM.sysWrite(" at BC: ");
      VM.sysWrite(bytecodeOffset);
      VM.sysWrite("\n");
    }
    
    OPT_InlineSequence caller = null;
    if (parser.hasMoreElements()) {
      // There is a caller, so recursively create inline sequence
      // for caller. Yuk: to get the rest of the tokens in one
      // string, give it a token that's not there to get rest of
      // line.
      String restOfLine = parser.nextToken("\n"); 
      caller = new OPT_InlineSequence(restOfLine);
    }
    
    this.method = method;
    this.bcIndex = bytecodeOffset;
    this.caller = caller;
  }
  

  /** 
   * Return a hashcode for this object.  
   *
   * TODO: Figure out a better hashcode.  Efficiency doesn't matter
   * for now.
   *
   * @return the hashcode for this object.
   */
    public int hashCode () {
      return  bcIndex;
    }


  public java.util.Enumeration enumerateFromRoot()  {
    return new java.util.Enumeration()  {
      OPT_Stack stack;
      {
	stack = new OPT_Stack();
	OPT_InlineSequence parent = OPT_InlineSequence.this;
	while (parent.caller != null) { 
	  stack.push(parent);
	  parent = parent.caller;
	}
      }
      public boolean hasMoreElements()  {
	return !stack.isEmpty();
      }
      public Object nextElement() {
	return stack.pop();
      }
    };
  }
}
