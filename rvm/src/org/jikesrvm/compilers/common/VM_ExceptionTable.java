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
package org.jikesrvm.compilers.common;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Services;
import org.jikesrvm.classloader.VM_DynamicTypeCheck;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.objectmodel.VM_TIB;
import org.vmmagic.unboxed.Offset;

/**
 * Encoding of try ranges in the final machinecode and the
 * corresponding exception type and catch block start.
 */
public abstract class VM_ExceptionTable {

  /**
   * An eTable array encodes the exception tables using 4 ints for each
   */
  protected static final int TRY_START = 0;
  protected static final int TRY_END = 1;
  protected static final int CATCH_START = 2;
  protected static final int EX_TYPE = 3;

  /**
   * Return the machine code offset for the catch block that will handle
   * the argument exceptionType,or -1 if no such catch block exists.
   *
   * @param eTable the encoded exception table to search
   * @param instructionOffset the offset of the instruction after the PEI.
   * @param exceptionType the type of exception that was raised
   * @return the machine code offset of the catch block.
   */
  public static int findCatchBlockForInstruction(int[] eTable, Offset instructionOffset, VM_Type exceptionType) {
    for (int i = 0, n = eTable.length; i < n; i += 4) {
      // note that instructionOffset points to the instruction after the PEI
      // so the range check here must be "offset >  beg && offset <= end"
      // and not                         "offset >= beg && offset <  end"
      //
      // offset starts are sorted by starting point
      if (instructionOffset.sGT(Offset.fromIntSignExtend(eTable[i + TRY_START])) &&
          instructionOffset.sLE(Offset.fromIntSignExtend(eTable[i + TRY_END]))) {
        VM_Type lhs = VM_Type.getType(eTable[i + EX_TYPE]);
        if (lhs == exceptionType) {
          return eTable[i + CATCH_START];
        } else if (lhs.isInitialized()) {
          VM_TIB rhsTIB = exceptionType.getTypeInformationBlock();
          if (VM_DynamicTypeCheck.instanceOfClass(lhs.asClass(), rhsTIB)) {
            return eTable[i + CATCH_START];
          }
        }
      }
    }
    return -1;
  }

  /**
   * Print an encoded exception table.
   * @param eTable the encoded exception table to print.
   */
  public static void printExceptionTable(int[] eTable) {
    int length = eTable.length;
    VM.sysWriteln("Exception Table:");
    VM.sysWriteln("    trystart   tryend    catch    type");
    for (int i = 0; i < length; i += 4) {
      VM.sysWriteln("    " +
                    VM_Services.getHexString(eTable[i + TRY_START], true) +
                    " " +
                    VM_Services.getHexString(eTable[i + TRY_END], true) +
                    " " +
                    VM_Services.getHexString(eTable[i + CATCH_START], true) +
                    "    " +
                    VM_Type.getType(eTable[i + EX_TYPE]));
    }
  }
}



