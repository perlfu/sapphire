/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * A symbolic or physical register.
 * A wrapper around an OPT_Register that may contain program-point specific
 * information about the value denoted by the OPT_Register.
 * 
 * TODO: This class is due for a refactor into subclasses
 * to split out the symbolic & physical registers and to create
 * special behavior for symbolic registers used as phi operands and
 * as validation (guard) operands.
 * 
 * @see OPT_Operand
 * @author Mauricio Serrano
 * @author John Whaley
 * @modified Stephen Fink
 */
public final class OPT_RegisterOperand extends OPT_Operand {

  /** 
   * Register object that this operand uses.
   */
  OPT_Register register;

  /**
   * Inferred data type of the contents of the register.
   */
  VM_Type type;

  /**
   * scratch word that can be used for different optimizations.
   * The first 16 bits are reserved for internal purposes. The remaining
   * 16 bits can be used for other purposes. Note: if packing is
   * supported, the scratch word could be split. 
   */
   int scratch;

  static private final int PARAMETER    = 0x01000000; // the register operand is for a parameter
  static private final int NON_VOLATILE = 0x02000000;

  static private final int EXTANT        = 0x04000000; // is this an extant object?
  static private final int UNUSED_BIT2   = 0x08000000; 
  // type of a RegisterOperand can be in one of three states:
  // a- declared: the type obtained from a getfield,getstatic,putfield,putstatic,array load
  // b- precise:  obtained from a NEW.
  // c- computed: (default) computed from propagating types.
  static private final int DECLARED_TYPE= 0x10000000;
  static private final int PRECISE_TYPE = 0x20000000;
  // the following applies only to integer-like types:
  static private final int POSITIVE     = 0x40000000;

  static private final int FLAGS_MASK   = EXTANT | DECLARED_TYPE | PRECISE_TYPE | POSITIVE;

  // the following is used to represent ranges of registers in a register operand
  static private final int RANGE_MASK   = 0x00ff0000;

  // the following available for analysis-specific extra information 
  static private final int INFO_MASK    = 0x0000ffff;


  boolean isParameter()       {   return (scratch & PARAMETER    ) != 0; }
  boolean isNonVolatile()     {   return (scratch & NON_VOLATILE ) != 0; }

  boolean isExtant()          {   return (scratch & EXTANT       ) != 0; }
  boolean isDeclaredType()    {   return (scratch & DECLARED_TYPE) != 0; }
  boolean isPreciseType()     {   return (scratch & PRECISE_TYPE ) != 0; }
  boolean isPositiveInt()     {   return (scratch & POSITIVE     ) != 0; }
  boolean isDeclaredOrPreciseType() {  return (scratch & (DECLARED_TYPE|PRECISE_TYPE)) != 0; }

  boolean isRange()           {   return (scratch & RANGE_MASK)    != 0; }
  int     getRange()          {   return (scratch & RANGE_MASK)    >>>16;}

  void    setParameter()      {   scratch |=  PARAMETER;                 }
  void    setNonVolatile()    {   scratch |=  NON_VOLATILE;              }
  void    setExtant()         {   scratch |=  EXTANT;                    }
  void    setDeclaredType()   {   scratch |=  DECLARED_TYPE;             }
  void    setPreciseType()    {   scratch |=  PRECISE_TYPE;              }
  void    setPositiveInt()    {   scratch |=  POSITIVE;                  }
  void    setRange(int n)     {   scratch |=  (n << 16) & RANGE_MASK;    }

  void    clearParameter()    {   scratch &= ~PARAMETER;        }
  void    clearNonVolatile()  {   scratch &= ~NON_VOLATILE;     }
  void    clearExtant()       {   scratch &= ~EXTANT;           }
  void    clearDeclaredType() {   scratch &= ~DECLARED_TYPE;    }
  void    clearPreciseType()  {   scratch &= ~PRECISE_TYPE;     }

  int  getFlags() {
     return scratch & FLAGS_MASK;
  }

  void setFlags(int inFlag) {
     scratch = (scratch & ~FLAGS_MASK) | inFlag;
  }

  void clearFlags() {
     scratch = scratch & ~FLAGS_MASK;
  }
  
  void addFlags(int inFlag) {
     scratch |= inFlag;
  }

  void setInheritableFlags(OPT_RegisterOperand src) {
    // Currently all flags are inheritable, so no need to mask
    setFlags(src.getFlags());
  }

  void meetInheritableFlags(OPT_RegisterOperand other) {
    // Currently all flags are "meetable", so no need to mask
    setFlags(getFlags() & other.getFlags());
  }

  // Return true if we have any bits set (flag true) that other doesn't
  // It's ok for other to have bits set true that we have set to false.
  boolean hasLessConservativeFlags(OPT_RegisterOperand other) {
     return other.getFlags() != (getFlags() | other.getFlags());
  }     

  final int getInfo() {
     return scratch & INFO_MASK;
  }

  final void setInfo(int value) {
     scratch = (scratch & ~INFO_MASK) | (value & INFO_MASK);
  }

  /* Some bits used to characterize guards.  TODO: Maybe declare a new
     type OPT_GuardOperand extends OPT_RegisterOperand, and save this
     state there? */
  int scratch2;

  static private final int TAKEN     = 0x00000001; // guard operand that
  					    // represents a taken branch
  static private final int NOT_TAKEN = 0x00000002; // guard operand that
  					// represents a not taken branch
  static private final int BOUNDS_CHECK = 0x00000004; // guard operand that
  					// originates from a bounds-check
  static private final int NULL_CHECK = 0x00000008; // guard operand that
  					// originates from a null-check
  boolean isTaken()       	{   return (scratch2 & TAKEN) != 0; }
  boolean isNotTaken()     	{   return (scratch2 & NOT_TAKEN) != 0; }
  boolean isBoundsCheck()       {   return (scratch2 & BOUNDS_CHECK) != 0; }
  boolean isNullCheck()         {   return (scratch2 & NULL_CHECK) != 0; }

  void    setTaken()      	{   scratch2 |=  TAKEN;                 }
  void    setNotTaken()      	{   scratch2 |=  NOT_TAKEN;             }
  void    setBoundsCheck()     	{   scratch2 |=  BOUNDS_CHECK;          }
  void    setNullCheck()     	{   scratch2 |=  NULL_CHECK;            }

  void    clearTaken()    	{   scratch2 &= ~TAKEN;        		}
  void    clearNotTaken()    	{   scratch2 &= ~NOT_TAKEN;    		}
  void    clearBoundsCheck()   	{   scratch2 &= ~BOUNDS_CHECK;  	}
  void    clearNullCheck()   	{   scratch2 &= ~NULL_CHECK;  	        }


  /* optimizations can use it for different purposes, as long as
    they are not used simultaneously */
  Object scratchObject;


  /* since there is not multiple inheritance in Java, I am copying the 
     accessor functions & fields of LinkedListElement.
     This field is used to maintain lists of USEs and DEFs */
  public final void setNext(OPT_RegisterOperand Next) {
     scratchObject = Next;
  }

  public final void append(OPT_RegisterOperand next) {
     scratchObject = next;
  }

  public final OPT_RegisterOperand getNext() { 
     return (OPT_RegisterOperand)scratchObject;
  }


  /**
   * Constructs a new register operand with the given register and data type.
   * 
   * @param reg register object
   * @param typ data type
   */
  OPT_RegisterOperand(OPT_Register reg, VM_Type typ) {
    register = reg; 
    type = typ;
  }

  OPT_RegisterOperand(OPT_Register reg, VM_Type typ, int flags) {
    register = reg; 
    type = typ; 
    scratch = flags;
  }


  void setRegister(OPT_Register replacement) {
    register = replacement;
  }

  /**
   * Returns a copy of this register operand as an operand
   */
  OPT_Operand copy() {
    return copyRO();
  }

  /**
   * Returns a copy of this register operand as a register operand
   * NOTE: preserves both the scratch word and scratchObject.
   * Preserving scratch & (FLAGS_MASK|RANGE_MASK) is required in all cases
   * Several phases also depend on scratch and/or scratchObject being copied
   */
  OPT_RegisterOperand copyRO() {
    OPT_RegisterOperand temp = new OPT_RegisterOperand(register, type);
    temp.scratch = scratch; 
    temp.scratch2 = scratch2; 
    temp.scratchObject = scratchObject;
    return temp;
  }

  /**
   * Returns a copy of this use register operand as another use reg operand.
   */
  OPT_RegisterOperand copyU2U() {
    return copyRO();
  }

  /**
   * Returns a copy of this def register operand as a use.
   */
  OPT_RegisterOperand copyD2U() {
    return copyRO();
  }

  /**
   * Returns a copy of this use register operand as a def.
   */
  OPT_RegisterOperand copyU2D() {
    return copyRO();
  }

  /**
   * Returns a copy of this def register operand as a def.
   */
  OPT_RegisterOperand copyD2D() {
    return copyRO();
  }


  /**
   * Returns whether the given operand is a register operand and has the same
   * register object.
   * 
   * @param op operand to compare against
   */
  boolean similar(OPT_Operand op) {
    return (op instanceof OPT_RegisterOperand) &&
      (register == ((OPT_RegisterOperand)op).register);
  }

  /**
   * Returns the string representation of this operand.
   */
  public String toString() {
    String s = register.toString();
    int r = getRange();
    if (r != 0) {
       OPT_Register reg;
       for (reg = register; r > 0; reg = reg.getNext(),r--);
       s += ".."+reg;
    }
    if (type != OPT_ClassLoaderProxy.VALIDATION_TYPE) {
      s  = s + "("+type;
      if (isExtant())       s += ",x";
      if (isDeclaredType()) s += ",d";
      if (isPreciseType())  s += ",p";
      if (isPositiveInt())  s += ",+";
      s += ")";
    } else {
      s += "(GUARD)";
    }
    return s.toString();
  }

}
