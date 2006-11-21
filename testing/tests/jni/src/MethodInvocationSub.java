/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Sub class of MethodInvocation to test CallNonVirtual<type>Method
 *
 * @author Ton Ngo
 */
class MethodInvocationSub extends MethodInvocation {


  /************************************************************
   * Virtual methods serving as target to be invoked from native code
   * receive arguments of every type, return one of each type
   * These override the same methods in the superclass MethodInvocation
   */

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)Z        */
  public boolean virtualReturnBoolean(byte val0, char val1, short val2,  
                                  int val3, long val4, float val5,  
                                  double val6, Object val7, boolean val8) {
    return val8;
  }
  
  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)B        */
  public byte virtualReturnByte(byte val0, char val1, short val2,  
                                  int val3, long val4, float val5,  
                                  double val6, Object val7, boolean val8) {
    return (byte) (val0 + 9);
  }
  
  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)C        */
  public char virtualReturnChar(byte val0, char val1, short val2,  
                                  int val3, long val4, float val5,  
                                  double val6, Object val7, boolean val8) {
    if (val1=='x')
      return 'q';
    else
      return 'r';    // didn't get the expected argument, force test to fail
  }
  
  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)S        */
  public short virtualReturnShort(byte val0, char val1, short val2,  
                                  int val3, long val4, float val5,  
                                  double val6, Object val7, boolean val8) {
    return (short) (val2 + 29);
  }
  
  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)I        */
  public int virtualReturnInt(byte val0, char val1, short val2,  
                                  int val3, long val4, float val5,  
                                  double val6, Object val7, boolean val8) {
    return val3 + 99;
  }
  
  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)J        */
  public long virtualReturnLong(byte val0, char val1, short val2,  
                                  int val3, long val4, float val5,  
                                  double val6, Object val7, boolean val8) {
    return val4 + 2000;
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)F        */
  public float virtualReturnFloat(byte val0, char val1, short val2,  
                                  int val3, long val4, float val5,  
                                  double val6, Object val7, boolean val8) {
    return val5 + (float) 64.0;
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)D        */
  public double virtualReturnDouble(byte val0, char val1, short val2,  
                                  int val3, long val4, float val5,  
                                  double val6, Object val7, boolean val8) {
    return val6 + (double) 2000.0;
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)         */
  public void virtualReturnVoid(byte val0, char val1, short val2,  
                                  int val3, long val4, float val5,  
                                  double val6, Object val7, boolean val8) {
    testFlagForVoid = 123;     // update the flag to indicate success
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)Ljava/lang/Object;        */
  public Object virtualReturnObject(byte val0, char val1, short val2,  
                                  int val3, long val4, float val5,  
                                  double val6, Object val7, boolean val8) {
    return new String("Hot stuff");
  }


  /************************************************************
   * Dummy constructor to get to the virtual methods
   */
  public MethodInvocationSub () {

  }  

}
