/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

/**
 * This pragma indicates that a particular method should never be inlined
 * by the optimizing compiler.
 * 
 * @author Stephen Fink
 */
public class VM_PragmaNoInline extends VM_PragmaException {
  private static final VM_Class vmClass = getVMClass(VM_PragmaNoInline.class);
  public static boolean declaredBy(VM_Method method) {
    return declaredBy(vmClass, method);
  }
}
