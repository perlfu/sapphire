/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$
package com.ibm.JikesRVM;
import com.ibm.JikesRVM.PrintContainer; /* This import statement isn't
                                     necessary, but is here for documentation
                                     purposes. --S. Augart */ 
import com.ibm.JikesRVM.classloader.VM_Member;
import com.ibm.JikesRVM.classloader.VM_Atom;
import com.ibm.JikesRVM.classloader.VM_Class;

import java.io.PrintWriter;
import java.io.PrintStream;

/**
 * This interface is implemented by com.ibm.JikesRVM.PrintContainer.  The
 * interfaces is used by our java.lang.Throwable to print stack traces.
 *
 * @author Steven Augart (w/ brainstorming by David Grove)
 */
public abstract class PrintLN {
  //  PrintLN(PrintWriter out);
  //  PrintLN(PrintStream out);
  public boolean isSysWrite() { return false; }
  // Transitional method; will go away RSN
  /** @deprecated superseded by <code>isSysWrite()</code> */
  public boolean isVMSysWriteln() { return isSysWrite(); }
  public boolean isSystemErr() { return false; }
  public abstract void flush();
  
  public abstract void println();
  public void println(String s) {
    print(s);
    println();
  }
  public abstract void print(String s);

  /* Here, we are writing code to make sure that we do not rely upon any
   * external memory accesses. */
  // largest power of 10 representable as a Java integer.
  // (max int is            2147483647)
  final int max_int_pow10 = 1000000000;

  public void print(int n) {
    boolean suppress_leading_zero = true;
    if (n == 0x80000000) {
      print("-2147483648");
      return;
    } else if (n == 0) {
      print('0');
      return;
    } else if (n < 0) {
      print('-');
      n = -n;
    }     
    /* We now have a positive # of the proper range.  Will need to exit from
       the bottom of the loop. */
    for (int p = max_int_pow10; p >= 1; p /= 10) {
      int digit = n / p;
      n -= digit * p;
      if (digit == 0 && suppress_leading_zero)
        continue;
      suppress_leading_zero = false;
      char c = (char) ('0' + digit);
      print(c);
    }
  }

  public void printHex(int n) {
    print("0x");
    // print exactly 8 hexadec. digits.
    for (int i = 32 - 4; i >= 0; i -= 4) {
      int digit = (n >>> i) & 0x0000000F;               // fill with 0 bits.
      char c;

      if (digit <= 9) {
        c = (char) ('0' + digit);
      } else {
        c = (char) ('A' + (digit - 10));
      }
      print(c);
    }
  }

  public abstract void print(char c);

//   /** Print the name of the class to which the argument belongs.
//    * 
//    * @param o Print the name of the class to which o belongs. */
//   public void printClassName(Object o) {
    
//   }

  /** Print the name of the class represented by the class descriptor.
   * 
   * @param descriptor The class descriptor whose name we'll print. */
  public void printClassName(VM_Atom descriptor) {
    // toByteArray does not allocate; just returns an existing descriptor.
    byte[] val = descriptor.toByteArray();

    if (VM.VerifyAssertions) 
      VM._assert(val[0] == 'L' && val[val.length-1] == ';'); 
    for (int i = 1; i < val.length - 1; ++i) {
      char c = (char) val[i];
      if (c == '/')
        print('.');
      else
        print(c);
    }
    // We could do this in an emergency.  But we don't need to.
    // print(descriptor);
  }

  /* Code related to VM_Atom.classNameFromDescriptor() */
  public void print(VM_Class class_) {
    // getDescriptor() does no allocation.
    VM_Atom descriptor = class_.getDescriptor(); 
    printClassName(descriptor);
  }

    // A kludgy alternative:
//     public void print(VM_Class c) {
//       VM_Atom descriptor = c.getDescriptor();
//       try {
//      print(descriptor.classNameFromDescriptor());
//       } catch(OutOfMemoryError e) {
//      print(descriptor);
//       }
//     }

    // No such method:
    //public void print(VM_Class c) {
    //      VM.sysWrite(c);
    //    }


  /* Here we need to imitate the work that would normally be done by
   * VM_Member.toString() (which VM_Method.toString() inherits) */
  public void print(VM_Member m) {
    print(m.getDeclaringClass()); // VM_Class
    print('.');
    print(m.getName());
    print(' ');
    print(m.getDescriptor());
  }

  public void print(VM_Atom a) {
    byte[] val = a.toByteArray();
    for (int i = 0; i < val.length; ++i) {
      print((char) val[i]);
    }
  }
}

