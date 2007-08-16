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
package org.jikesrvm.tools.bootImageWriter;

import org.jikesrvm.VM_Constants;
import org.vmmagic.unboxed.Address;

/**
 * Manifest constants for bootimage writer.
 */
public interface BootImageWriterConstants extends VM_Constants {

  /**
   * Address to associate with objects that haven't yet been placed into image.
   * Any Address that's unaligned will do.
   */
  Address OBJECT_NOT_ALLOCATED = Address.fromIntSignExtend(0xeeeeeee1);

  /**
   * Address to associate with objects that are not to be placed into image.
   * Any Address that's unaligned will do.
   */
  Address OBJECT_NOT_PRESENT = Address.fromIntSignExtend(0xeeeeeee2);

  /**
   * Starting index for objects in VM_TypeDictionary.
   * = 1, since slot 0 is reserved for null
   */
  int FIRST_TYPE_DICTIONARY_INDEX = 1;
}

