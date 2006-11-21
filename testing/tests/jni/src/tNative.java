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
 * Test native method
 *
 * @author unascribed
 */

class tNative
{
  public static native int nativeFoo(int count);

  public static void main(String args[])
  {
    System.out.println("Attempting to load dynamic library ...");
    System.out.println("(the LIBPATH env variable must be set for this directory)");

    System.loadLibrary("tNative");
    
    int returnValue = nativeFoo(17);
    System.out.println("First nativeFoo return " + returnValue);
    
    returnValue = nativeFoo(30);
    System.out.println("Second nativeFoo return " + returnValue);
    
  }
}
