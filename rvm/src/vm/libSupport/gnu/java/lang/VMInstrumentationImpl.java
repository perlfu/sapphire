/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002, 2004, 2005
 */
//$Id: JikesRVMSupport.java,v 1.16 2006/03/01 12:23:56 dgrove-oss Exp $
package gnu.java.lang;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.ClassDefinition;

import com.ibm.JikesRVM.classloader.VM_Type;
import com.ibm.JikesRVM.classloader.VM_Class;
import com.ibm.JikesRVM.classloader.VM_Array;


/**
 * Jikes RVM implementation of VMInstrumentationImpl
 *
 * @author Elias Naur
 */
final class VMInstrumentationImpl {
  static boolean isRedefineClassesSupported() {
    return false;
  }

  static void redefineClasses(Instrumentation inst,
    ClassDefinition[] definitions) {
    throw new UnsupportedOperationException();
  }

  static Class[] getAllLoadedClasses() {
    return java.lang.JikesRVMSupport.getAllLoadedClasses();
  }

  static Class[] getInitiatedClasses(ClassLoader loader) {
    return java.lang.JikesRVMSupport.getInitiatedClasses(loader);
  }

  static long getObjectSize(Object objectToSize) {
    Class cl = objectToSize.getClass();
    VM_Type vmType = java.lang.JikesRVMSupport.getTypeForClass(cl);
    if (cl.isArray()) {
      VM_Array vmArray = (VM_Array)vmType;
      int nelements = ((Object[])objectToSize).length;
      return vmArray.getInstanceSize(nelements);
    } else {
      VM_Class vmClass = (VM_Class)vmType;
      return vmClass.getInstanceSize();
    }
  }
}
