/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Runtime;

/**
 * Dynamic linking via indirection tables. <p>
 *
 * The main idea for dynamic linking is that we maintain
 * arrays of member offsets indexed by the member's
 * dynamic linking id. The generated code at a dynamically linked
 * site will load the appropriate value from the offset table and
 * check to see if the value is valid. If it is, then no dynamic linking
 * is required.  If the value is invalid, then resolveDynamicLink
 * is invoked to perfrom any required dynamic class loading.
 * After member resolution and class loading completes, we can
 * store the offset value in the offset table.
 * Thus when resolve method returns, execution can be restarted
 * by reloading/indexing the offset table. <p>
 *
 * <p> NOTE: We believe that only use of invokespecial that could possibly
 * require dynamic linking is that of invoking an object initializer.
 */
public class VM_TableBasedDynamicLinker implements VM_Constants {

  private static int[] memberOffsets;

  static {
    memberOffsets = MM_Interface.newContiguousIntArray(16000);
    if (NEEDS_DYNAMIC_LINK != 0) {
      java.util.Arrays.fill(memberOffsets, NEEDS_DYNAMIC_LINK);
    }
  }

  /**
   * Cause dynamic linking of the VM_Member whose member reference id is given.
   * Invoked directly from (baseline) compiled code.
   * @param memberId the dynamicLinkingId of the method to link.
   * @return returns the offset of the member.
   */
  public static int resolveMember(int memberId) throws NoClassDefFoundError {
    VM_MemberReference ref = VM_MemberReference.getMemberRef(memberId);
    return resolveMember(ref);
  }

  /**
   * Cause dynamic linking of the argument VM_MemberReference
   * @param ref reference to the member to link
   * @return returns the offset of the member.
   */
  public static int resolveMember(VM_MemberReference ref) throws NoClassDefFoundError {
    VM_Member resolvedMember = ref.resolveMember();
    VM_Class declaringClass = resolvedMember.getDeclaringClass();
    VM_Runtime.initializeClassForDynamicLink(declaringClass);
    int offset = resolvedMember.getOffset().toInt();
    if (VM.VerifyAssertions) VM._assert(offset != NEEDS_DYNAMIC_LINK);
    memberOffsets[ref.getId()] = offset;
    return offset;
  }

  /**
   * Method invoked from VM_MemberReference to
   * ensure that there is space in the dynamic linking table for
   * the given member reference.
   */
  static synchronized void ensureCapacity(int id) {
    if (id == memberOffsets.length) {
      int oldLen = memberOffsets.length;
      int[] tmp1 = MM_Interface.newContiguousIntArray(oldLen * 2);
      System.arraycopy(memberOffsets, 0, tmp1, 0, oldLen);
      if (NEEDS_DYNAMIC_LINK != 0) {
        java.util.Arrays.fill(tmp1, oldLen, tmp1.length, NEEDS_DYNAMIC_LINK);
      }
      VM_Magic.sync(); // be sure array initialization is visible before we publish the reference!
      memberOffsets = tmp1;
    }
  }
}
