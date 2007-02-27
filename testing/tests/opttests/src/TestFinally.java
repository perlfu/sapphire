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
class TestFinally
   {
   static int
   foo(int a, int b)
      {
      try 
         {
         return a / b;
         }

      catch(Exception e)
         {
         return 1;
         }

      finally 
         {
         return 2;
         }

      // not reached
      }

   static int
   foo1(int a, int b)
      {
      int c = 0;
      if (a > 0)
       try { return a/b;
       }
       catch(Exception e) {
          c = 1;
       }
       finally {
          c = 2;
       }
      else
         return 0;
      c= c + b;
      return c;
   }


   static Object lock = new Object();

   static boolean
   foo2(int a) {
     synchronized(lock) {
        return foo2a(a) == 1;
     }
   }

   static int foo2a(int a) {
     return a+1;
   }

   static boolean
   foo3(int a, int b) {
      int c = 0;
      if (a > 0)
       try { return a/b == 0;
       }
       catch(Exception e) {
          c = 1;
       }
       finally {
          int x = a >> 3;
          int y = b + 2;
          c = foo2a(y&(x & 0x77))-1;
       }
      else
         return false;
      c= c + b;
      return c >= a; 
   }

   static int foo4(int a, int b) {
      int x;
      try {
         return a/b;
      } catch (Exception e) {
         x = 10;
      }
      finally {
         try {
            x = b/a + 3000;
         } catch (Exception e) {
            x = b;
         }
      }
      return x;
   }
   
   public static void 
   main(String args[])
      {
   // VM.boot();
      run();
      }

   public static boolean run() {
      System.out.println("TestFinally");

      System.out.println(foo(1,0));
      System.out.println(foo1(1,0));
      System.out.println(foo2(1));
      System.out.println(foo3(1,1));
      System.out.println(foo4(100,10));
      System.out.println(foo4(0,10));
      System.out.println(foo4(100,0));
      
      try  {
         System.out.println("hi");      // jsr
      }
      finally {
         System.out.println("bye");
      }                              // ret
      return true;
   }

}
