/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/**
 * @author unascribed
 */
public class myMain {

  public static int compute (String[] args) {
    // System.out.println(args[0].substring(0,8) + "-----" + args[0].substring(12));
    if (args[0].startsWith("C thread") &&
        args[0].endsWith("Hello from C"))      
      return 123;
    else 
      return 456;
  }

  public static void main(String[] args) {
    // System.out.println(System.getProperty("java.class.path"));
    // System.out.println("   from Java: Hello World! ");
    // System.out.println("   I am thread: " + Thread.currentThread().getName());
    // VM.debugBreakpoint();
    // for (int i=0; i<args.length; i++) {
    //  System.out.println("   arg " + i + " : " + args[i]);
    // }
    
  }
}
