/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import com.ibm.JikesRVM.memoryManagers.JMTk.VMResource;
import com.ibm.JikesRVM.memoryManagers.JMTk.LazyMmapper;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_ProcessorLock;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Synchronization;

/**
 * Common utility functions used by various garbage collectors
 *
 * @author Stephen Smith
 */  
public class Util implements VM_Constants, Constants, VM_Uninterruptible {

  private final static boolean TRACE = false;

  private static final VM_ProcessorLock outOfMemoryLock = new VM_ProcessorLock();
  private static boolean outOfMemoryReported = false;

  private static Object[] tibForArrayType;
  private static Object[] tibForClassType;
  private static Object[] tibForPrimitiveType;

  public static final void boot (VM_BootRecord theBootRecord) throws VM_PragmaInterruptible {
    // get addresses of TIBs for VM_Array & VM_Class used for testing Type ptrs
    VM_Type t = VM_Array.getPrimitiveArrayType(10);
    tibForArrayType = VM_ObjectModel.getTIB(t);
    tibForPrimitiveType = VM_ObjectModel.getTIB(VM_Type.IntType);
    t = VM_Magic.getObjectType(VM_BootRecord.the_boot_record);
    tibForClassType = VM_ObjectModel.getTIB(t);
  }    

  // check if an address appears to point to an instance of VM_Type
  public static boolean validType(VM_Address typeAddress) throws VM_PragmaUninterruptible {
     if (!mappedVMRef(typeAddress))
      return false;  // type address is outside of heap

    // check if types tib is one of three possible values
    Object[] typeTib = VM_ObjectModel.getTIB(typeAddress);
    return ( (typeTib == tibForClassType) || 
             (typeTib == tibForArrayType) ||
             (typeTib == tibForPrimitiveType));
  }

  /**
   * dump all threads & their stacks starting at the frame identified
   * by the threads saved contextRegisters (ip & fp fields).
   */
  public static void dumpAllThreadStacks() throws VM_PragmaUninterruptible {
      VM_Address ip, fp;
      VM_Thread  t;
      VM_Scheduler.trace("\ndumpAllThreadStacks","dumping stacks for all threads");
      for (int i=0; i<VM_Scheduler.threads.length; i++) {
          t = VM_Scheduler.threads[i];
          if (t == null) continue;
          VM.sysWrite("\n Thread "); t.dump(); VM.sysWrite("\n");
          // start stack dump using fp & ip in threads saved context registers
          ip = t.contextRegisters.getInnermostInstructionAddress();
          fp = t.contextRegisters.getInnermostFramePointer();
          VM_Scheduler.dumpStack(ip,fp);
      }
      VM.sysWrite("\ndumpAllThreadStacks: end of thread stacks\n\n");
  }  // dumpAllThreadStacks

  /**
   * check if a ref, its tib pointer & type pointer are all in the heap
   */
  public static boolean validObject(Object ref) throws VM_PragmaUninterruptible {
      return validRef(VM_Magic.objectAsAddress(ref));
  }

  public static boolean validRef(VM_Address ref) throws VM_PragmaUninterruptible {

    if (ref.isZero()) return true;
     if (!mappedVMRef(ref)) {
      VM.sysWrite("validRef: REF outside heap, ref = "); VM.sysWrite(ref); VM.sysWrite("\n");
      VMResource.showAll();
      return false;
    }
    if (MM_Interface.MOVES_OBJECTS) {
      if (VM_AllocatorHeader.isForwarded(ref) ||
          VM_AllocatorHeader.isBeingForwarded(ref)) {
        return true; // TODO: actually follow forwarding pointer (need to bound recursion when things are broken!!)
      }
    }
    
    Object[] tib = VM_ObjectModel.getTIB(ref);
    VM_Address tibAddr = VM_Magic.objectAsAddress(tib);
    if (!mappedVMRef(ref)) {
      VM.sysWrite("validRef: TIB outside heap, ref = "); VM.sysWrite(ref);
      VM.sysWrite(" tib = ");VM.sysWrite(tibAddr);
      VM.sysWrite("\n");
      return false;
    }
    if (tibAddr.isZero()) {
      VM.sysWrite("validRef: TIB is Zero! "); VM.sysWrite(ref);
      VM.sysWrite("\n");
      return false;
    }
    if (tib.length == 0) {
      VM.sysWrite("validRef: TIB length zero, ref = "); VM.sysWrite(ref);
      VM.sysWrite(" tib = ");VM.sysWrite(tibAddr);
      VM.sysWrite("\n");
      return false;
    }

    VM_Address type = VM_Magic.objectAsAddress(tib[0]);
    if (!validType(type)) {
      VM.sysWrite("validRef: invalid TYPE, ref = "); VM.sysWrite(ref);
      VM.sysWrite(" tib = ");
      VM.sysWrite(VM_Magic.objectAsAddress(tib));
      VM.sysWrite(" type = ");VM.sysWrite(type); VM.sysWrite("\n");
      return false;
    }
    return true;
  }  // validRef

  public static boolean mappedVMRef (VM_Address ref) throws VM_PragmaUninterruptible {
    return VMResource.refInVM(ref) && LazyMmapper.refIsMapped(ref);
  }

   public static void dumpRef(VM_Address ref) throws VM_PragmaUninterruptible {
     VM.sysWrite("REF=");
     if (ref.isZero()) {
       VM.sysWrite("NULL\n");
       return;
     }
     VM.sysWrite(ref);
     if (!mappedVMRef(ref)) {
       VM.sysWrite(" (REF OUTSIDE OF HEAP OR NOT MAPPED)\n");
       return;
     }
     VM_ObjectModel.dumpHeader(ref);
     VM_Address tib = VM_Magic.objectAsAddress(VM_ObjectModel.getTIB(ref));
     if (!MM_Interface.mightBeTIB(tib)) {
       VM.sysWrite(" (INVALID TIB: CLASS NOT ACCESSIBLE)\n");
       return;
     }
     VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));
     VM_Address itype = VM_Magic.objectAsAddress(type);
     VM.sysWrite(" TYPE=");
     VM.sysWrite(itype);
     if (!validType(itype)) {
       VM.sysWrite(" (INVALID TYPE: CLASS NOT ACCESSIBLE)\n");
       return;
     }
     VM.sysWrite(" CLASS=");
     VM.sysWrite(type.getDescriptor());
     VM.sysWrite("\n");
   }


  public static void printclass(VM_Address ref) throws VM_PragmaUninterruptible {
    if (validRef(ref)) {
      VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));
      if (validRef(VM_Magic.objectAsAddress(type)))
        VM.sysWrite(type.getDescriptor());
    }
  }


  public static boolean addrInBootImage(VM_Address addr) {
    return (addr.GE(VM_Interface.bootImageStart())) && (addr.LT(VM_Interface.bootImageEnd()));
  }

  /**
   * Print OutOfMemoryError message and exit.
   * TODO: make it possible to throw an exception, but this will have
   * to be done without doing further allocations (or by using temp space)
   */
  public static void outOfMemory(String heapName, int heapSize, String commandLine) throws VM_PragmaUninterruptible {
    outOfMemoryLock.lock();
    if (!outOfMemoryReported) {
      outOfMemoryReported = true;
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      VM.sysWriteln("\nOutOfMemoryError");
      VM.sysWriteln("Failing heap was ",heapName);
      VM.sysWriteln("Current heap size = ", heapSize / 1024, " Kb");
      VM.sysWriteln("Specify a larger heap using ", commandLine);
      // call shutdown while holding the processor lock
      VM.shutdown(VM.exitStatusMiscTrouble);
    } else {
      outOfMemoryLock.release();
      while(true);  // spin until VM shuts down
    }
  }
} 
