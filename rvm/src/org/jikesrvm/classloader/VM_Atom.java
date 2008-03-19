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

import static org.jikesrvm.classloader.VM_ClassLoaderConstants.ArrayTypeCode;
import static org.jikesrvm.classloader.VM_ClassLoaderConstants.BooleanTypeCode;
import static org.jikesrvm.classloader.VM_ClassLoaderConstants.ByteTypeCode;
import static org.jikesrvm.classloader.VM_ClassLoaderConstants.CharTypeCode;
import static org.jikesrvm.classloader.VM_ClassLoaderConstants.ClassTypeCode;
import static org.jikesrvm.classloader.VM_ClassLoaderConstants.DoubleTypeCode;
import static org.jikesrvm.classloader.VM_ClassLoaderConstants.FloatTypeCode;
import static org.jikesrvm.classloader.VM_ClassLoaderConstants.IntTypeCode;
import static org.jikesrvm.classloader.VM_ClassLoaderConstants.LongTypeCode;
import static org.jikesrvm.classloader.VM_ClassLoaderConstants.ShortTypeCode;
import static org.jikesrvm.classloader.VM_ClassLoaderConstants.VoidTypeCode;

import java.io.UTFDataFormatException;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_Statics;
import org.jikesrvm.util.VM_HashMap;
import org.jikesrvm.util.VM_StringUtilities;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * An  utf8-encoded byte string.
 *
 * VM_Atom's are interned (canonicalized)
 * so they may be compared for equality using the "==" operator.
 *
 * VM_Atoms are used to represent names, descriptors, and string literals
 * appearing in a class's constant pool.
 *
 * There is almost always a zero-length VM_Atom, since any class which
 * contains statements like:
 *          return "";
 * will have one in its constant pool.
 */
public final class VM_Atom {

  /**
   * Used to canonicalize VM_Atoms: possibly non-canonical VM_Atom => VM_Atom
   */
  private static final VM_HashMap<VM_Atom, VM_Atom> dictionary = new VM_HashMap<VM_Atom, VM_Atom>();

  /**
   * Dictionary of all VM_Atom instances.
   */
  private static VM_Atom[] atoms = new VM_Atom[16000];

  /**
   * Used to assign ids. Don't use id 0 to allow clients to use id 0 as a 'null'.
   */
  private static int nextId = 1;

  /**
   * The utf8 value this atom represents
   */
  private final byte[] val;

  /**
   * The id of this atom
   */
  private final int id;

  /**
   * A reference to either a unicode String encoding the atom, an offset in the
   * JTOC holding a unicode string encoding the atom or null.
   */
  private Object unicodeStringOrJTOCoffset;

  /**
   *@return the id of this atom.
   */
  int getId() { return id; }

  /**
   * Find or create an atom.
   * @param str atom value, as string literal whose characters are unicode
   * @return atom
   */
  @Pure
  public static VM_Atom findOrCreateUnicodeAtom(String str) {
    return findOrCreate(null, true, str);
  }

  /**
   * Find an atom.
   * @param str atom value, as string literal whose characters are unicode
   * @return atom or null if it doesn't already exist
   */
  public static VM_Atom findUnicodeAtom(String str) {
    return findOrCreate(null, false, str);
  }

  /**
   * Find or create an atom.
   * @param str atom value, as string literal whose characters are from
   *            ascii subset of unicode (not including null)
   * @return atom
   */
  @Pure
  public static VM_Atom findOrCreateAsciiAtom(String str) {
    return findOrCreate(null, true, str);
  }

  /**
   * Find an atom.
   * @param str atom value, as string literal whose characters are from
   *            ascii subset of unicode (not including null)
   * @return atom or null if it doesn't already exist
   */
  public static VM_Atom findAsciiAtom(String str) {
    return findOrCreate(null, false, str);
  }

  /**
   * Find or create an atom.
   * @param utf8 atom value, as utf8 encoded bytes
   * @return atom
   */
  @Pure
  public static VM_Atom findOrCreateUtf8Atom(byte[] utf8) {
    return findOrCreate(utf8, true, null);
  }

  /**
   * Find an atom.
   * @param utf8 atom value, as utf8 encoded bytes
   * @return atom or null it it doesn't already exist
   */
  public static VM_Atom findUtf8Atom(byte[] utf8) {
    return findOrCreate(utf8, false, null);
  }

  /**
   * Find an atom from the subsequence of another
   * @param utf8 byte backing of atom
   * @param off offset of new atom
   * @param len length of new atom
   * @param str possible string encoding of atom or null
   * @return atom
   */
  private static VM_Atom findOrCreate(byte[] utf8, int off, int len, String str) {
    if (str != null) {
      // string substring is cheap, so try to find using this if possible
      VM_Atom val = new VM_Atom(null, -1, str.substring(off, off+len));
      val = dictionary.get(val);
      if (val != null) return val;
    }
    byte[] val = new byte[len];
    for (int i = 0; i < len; ++i) {
      val[i] = utf8[off++];
    }
    return findOrCreate(val, true, null);
  }

  /**
   * This is the findOrCreate() method through which all VM_Atoms are
   * ultimately created.   The constructor for VM_Atom is a private method, so
   * someone has to call one of the public findOrCreate() methods to get a new
   * one.  And they all feed through here.
   */
  private static VM_Atom findOrCreate(byte[] bytes, boolean create, String str) {
    VM_Atom val = new VM_Atom(bytes, -1, str);
    val = dictionary.get(val);
    if (val != null || !create) return val;
    synchronized(VM_Atom.class) {
      val = new VM_Atom(bytes, nextId++, str);
      if (val.id == atoms.length) {
        VM_Atom[] tmp = new VM_Atom[atoms.length + 1000];
        System.arraycopy(atoms, 0, tmp, 0, atoms.length);
        atoms = tmp;
      }
      atoms[val.id] = val;
      dictionary.put(val, val);
    }
    return val;
  }

  /**
   * @param id the id of an Atom
   * @return the VM_Atom whose id was given
   */
  @Pure
  @Uninterruptible
  public static VM_Atom getAtom(int id) {
    return atoms[id];
  }

  //-------------//
  // conversions //
  //-------------//

  /**
   * Return printable representation of "this" atom.
   * Does not correctly handle UTF8 translation.
   */
  @Pure
  public String toString() {
    return VM_StringUtilities.asciiBytesToString(val);
  }

  /**
   * Get at a string-like representation without doing any heap allocation.
   * Hideous but necessary.  We will use it in the VM_PrintContainer class.
   */
  @Uninterruptible
  public byte[] toByteArray() {
    return val;
  }

  /**
   * Return atom as a string literal
   */
  @Pure
  public synchronized String toUnicodeString() throws java.io.UTFDataFormatException {
    if (unicodeStringOrJTOCoffset == null) {
      String s = VM_UTF8Convert.fromUTF8(val);
      if (VM.runningVM) {
        s = InternedStrings.internUnfoundString(s);
        unicodeStringOrJTOCoffset = s;
      } else {
        s = s.intern();
        int offset = VM_Statics.findOrCreateObjectLiteral(s);
        unicodeStringOrJTOCoffset = offset;
      }
      return s;
    } else if (unicodeStringOrJTOCoffset instanceof String) {
      return (String)unicodeStringOrJTOCoffset;
    } else {
      if (VM.runningVM) {
        return (String)VM_Statics.getSlotContentsAsObject(Offset.fromIntSignExtend((Integer)unicodeStringOrJTOCoffset));
      } else {
        return VM_UTF8Convert.fromUTF8(val).intern();
      }
    }
  }

  /**
   * Atom as string literal or null if atom hasn't been converted
   */
  private synchronized String toUnicodeStringInternal() {
    if (unicodeStringOrJTOCoffset == null) {
      return null;
    } else if (unicodeStringOrJTOCoffset instanceof String) {
      return (String)unicodeStringOrJTOCoffset;
    } else {
      if (VM.runningVM) {
        return (String)VM_Statics.getSlotContentsAsObject(Offset.fromIntSignExtend((Integer)unicodeStringOrJTOCoffset));
      } else {
        try {
          return VM_UTF8Convert.fromUTF8(val).intern();
        } catch (UTFDataFormatException e) {
          throw new Error("Error in UTF data encoding: ", e);
        }
      }
    }
  }

  /**
   * Offset of an atom's string in the JTOC, for string literals
   * @return Offset of string literal in JTOC
   * @throws java.io.UTFDataFormatException
   */
  public synchronized int getStringLiteralOffset() throws java.io.UTFDataFormatException {
    if (unicodeStringOrJTOCoffset == null) {
      String s = VM_UTF8Convert.fromUTF8(val);
      if (VM.runningVM) {
        s = InternedStrings.internUnfoundString(s);
      } else {
        s = s.intern();
      }
      int offset = VM_Statics.findOrCreateObjectLiteral(s);
      unicodeStringOrJTOCoffset = offset;
      return offset;
    } else if (unicodeStringOrJTOCoffset instanceof String) {
      int offset = VM_Statics.findOrCreateObjectLiteral(unicodeStringOrJTOCoffset);
      unicodeStringOrJTOCoffset = offset;
      return offset;
    } else {
      return (Integer)unicodeStringOrJTOCoffset;
    }
  }

  /**
   * Return array descriptor corresponding to "this" array-element descriptor.
   * this: array-element descriptor - something like "I" or "Ljava/lang/Object;"
   * @return array descriptor - something like "[I" or "[Ljava/lang/Object;"
   */
  @Pure
  VM_Atom arrayDescriptorFromElementDescriptor() {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 0);
    }
    byte[] sig = new byte[1 + val.length];
    sig[0] = (byte) '[';
    for (int i = 0, n = val.length; i < n; ++i) {
      sig[i + 1] = val[i];
    }
    return findOrCreate(sig, true, null);
  }

  /**
   * Return class descriptor corresponding to "this" class name.
   * this: class name       - something like "java/lang/Object"
   * @return class descriptor - something like "Ljava/lang/Object;"
   */
  @Pure
  public VM_Atom descriptorFromClassName() {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 0);
    }
    if (val[0] == '[') return this;
    byte[] sig = new byte[1 + val.length + 1];
    sig[0] = (byte) 'L';
    for (int i = 0, n = val.length; i < n; ++i) {
      sig[i + 1] = val[i];
    }
    sig[sig.length - 1] = (byte) ';';
    return findOrCreate(sig, true, null);
  }

  /**
   * Return class name corresponding to "this" class descriptor.
   * this: class descriptor - something like "Ljava/lang/String;"
   * @return class name - something like "java.lang.String"
   */
  @Pure
  public String classNameFromDescriptor() {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 0);
      VM._assert(val[0] == 'L' && val[val.length - 1] == ';');
    }
    if (unicodeStringOrJTOCoffset == null) {
      return VM_StringUtilities.asciiBytesToString(val, 1, val.length - 2).replace('/', '.');
    } else {
      return toUnicodeStringInternal().substring(1, val.length-1).replace('/','.');
    }
  }

  /**
   * Return name of class file corresponding to "this" class descriptor.
   * this: class descriptor - something like "Ljava/lang/String;"
   * @return class file name  - something like "java/lang/String.class"
   */
  @Pure
  public String classFileNameFromDescriptor() {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 0);
      VM._assert(val[0] == 'L' && val[val.length - 1] == ';');
    }
    if (unicodeStringOrJTOCoffset == null) {
      return VM_StringUtilities.asciiBytesToString(val, 1, val.length - 2).replace('/', '.') + ".class";
    } else {
      return toUnicodeStringInternal().substring(1, val.length-1).replace('/','.') + ".class";
    }
  }

  //----------------//
  // classification //
  //----------------//

  /**
   * Is "this" atom a reserved member name?
   * Note: Sun has reserved all member names starting with '<' for future use.
   *       At present, only <init> and <clinit> are used.
   */
  @Uninterruptible
  @Pure
  public boolean isReservedMemberName() {
    if (VM.VerifyAssertions) VM._assert(val.length > 0);
    return val[0] == '<';
  }

  /**
   * Is "this" atom a class descriptor?
   */
  @Uninterruptible
  @Pure
  public boolean isClassDescriptor() {
    if (VM.VerifyAssertions) VM._assert(val.length > 0);
    return val[0] == 'L';
  }

  /**
   * Is "this" atom an array descriptor?
   */
  @Uninterruptible
  @Pure
  public boolean isArrayDescriptor() {
    if (VM.VerifyAssertions) VM._assert(val.length > 0);
    return val[0] == '[';
  }

  /**
   * Is "this" atom a method descriptor?
   */
  @Uninterruptible
  @Pure
  public boolean isMethodDescriptor() {
    if (VM.VerifyAssertions) VM._assert(val.length > 0);
    return val[0] == '(';
  }

  //--------------------//
  // descriptor parsing //
  //--------------------//

  /**
   * Parse "this" method descriptor to obtain description of method's
   * return type.
   * this: method descriptor - something like "(III)V"
   * @return type description
   */
  @Pure
  public VM_TypeReference parseForReturnType(ClassLoader cl) {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 0);
      VM._assert(val[0] == '(', "Method descriptors start with `(`");
    }
    int i = 0;
    while (val[i++] != ')') {
      if (VM.VerifyAssertions) {
        VM._assert(i < val.length, "Method descriptor missing closing ')'");
      }
    }
    if (VM.VerifyAssertions) {
      VM._assert(i < val.length, "Method descriptor missing type after closing ')'");
    }
    switch (val[i]) {
      case VoidTypeCode:
        return VM_TypeReference.Void;
      case BooleanTypeCode:
        return VM_TypeReference.Boolean;
      case ByteTypeCode:
        return VM_TypeReference.Byte;
      case ShortTypeCode:
        return VM_TypeReference.Short;
      case IntTypeCode:
        return VM_TypeReference.Int;
      case LongTypeCode:
        return VM_TypeReference.Long;
      case FloatTypeCode:
        return VM_TypeReference.Float;
      case DoubleTypeCode:
        return VM_TypeReference.Double;
      case CharTypeCode:
        return VM_TypeReference.Char;
      case ClassTypeCode:   // fall through
      case ArrayTypeCode:
        return VM_TypeReference.findOrCreate(cl, findOrCreate(val, i, val.length - i, toUnicodeStringInternal()));
      default:
        if (VM.VerifyAssertions) {
          VM._assert(false,
                     "Need a valid method descriptor; got \"" +
                     this +
                     "\"; can't parse the character '" +
                     ((char)val[i]) +
                     "'");
        }
        return null;            // NOTREACHED
    }
  }

  /**
   * Parse "this" method descriptor to obtain descriptions of method's
   * parameters.
   * this: method descriptor     - something like "(III)V"
   * @return parameter descriptions
   */
  @Pure
  public VM_TypeReference[] parseForParameterTypes(ClassLoader cl) {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 0);
      VM._assert(val[0] == '(', "Method descriptors start with `(`");
    }
    VM_TypeReferenceVector sigs = new VM_TypeReferenceVector();
    int i = 1;
    while (true) {
      if (VM.VerifyAssertions) {
        VM._assert(i < val.length, "Method descriptor missing closing `)`");
      }

      switch (val[i++]) {
        case VoidTypeCode:
          sigs.addElement(VM_TypeReference.Void);
          continue;
        case BooleanTypeCode:
          sigs.addElement(VM_TypeReference.Boolean);
          continue;
        case ByteTypeCode:
          sigs.addElement(VM_TypeReference.Byte);
          continue;
        case ShortTypeCode:
          sigs.addElement(VM_TypeReference.Short);
          continue;
        case IntTypeCode:
          sigs.addElement(VM_TypeReference.Int);
          continue;
        case LongTypeCode:
          sigs.addElement(VM_TypeReference.Long);
          continue;
        case FloatTypeCode:
          sigs.addElement(VM_TypeReference.Float);
          continue;
        case DoubleTypeCode:
          sigs.addElement(VM_TypeReference.Double);
          continue;
        case CharTypeCode:
          sigs.addElement(VM_TypeReference.Char);
          continue;
        case ClassTypeCode: {
          int off = i - 1;
          while (val[i++] != ';') {
            if (VM.VerifyAssertions) {
              VM._assert(i < val.length, "class descriptor missing a final ';'");
            }
          }
          sigs.addElement(VM_TypeReference
              .findOrCreate(cl, findOrCreate(val, off, i - off, toUnicodeStringInternal())));
          continue;
        }
        case ArrayTypeCode: {
          int off = i - 1;
          while (val[i] == ArrayTypeCode) {
            if (VM.VerifyAssertions) {
              VM._assert(i < val.length, "malformed array descriptor");
            }
            ++i;
          }
          if (val[i++] == ClassTypeCode) while (val[i++] != ';') ;
          sigs.addElement(VM_TypeReference.findOrCreate(cl, findOrCreate(val, off, i - off, toUnicodeStringInternal())));
          continue;
        }
        case(byte) ')': // end of parameter list
          return sigs.finish();

        default:
          if (VM.VerifyAssertions) {
            VM._assert(false,
                       "The class descriptor \"" +
                       this +
                       "\" contains the illegal" +
                       " character '" +
                       ((char)val[i]) +
                       "'");
          }
      }
    }
  }

  /**
   * Parse "this" method descriptor to obtain descriptions of method's
   * parameters as classes.
   * this: method descriptor     - something like "(III)V"
   * @return parameter classes
   */
  @Pure
  public Class<?>[] parseForParameterClasses(ClassLoader cl) {
    VM_TypeReference[] typeRefs = this.parseForParameterTypes(cl);
    Class<?>[] classes = new Class<?>[typeRefs.length];
    for (int i=0; i < typeRefs.length; i++) {
      VM_TypeReference t = typeRefs[i];
      classes[i] = t.resolve().getClassForType();
    }
    return classes;
  }

  /**
   * Return the underlying set of bytes for the VM_Atom.  This can be used
   * to perform comparisons without requiring the allocation of a string.
   */
  @Uninterruptible
  public byte[] getBytes() {
    return val;
  }

  /**
   * Parse "this" field, parameter, or return descriptor to obtain its
   * type code.
   * this: descriptor - something like "Ljava/lang/String;" or "[I" or "I"
   * @return type code  - something like ObjectTypeCode, ArrayTypeCode, or
   * IntTypeCode
   *
   * The type code will be one of the following constants:
   *
   * <pre>
   *               constant         value
   *           ----------------     -----
   *            ClassTypeCode        'L'
   *            ArrayTypeCode        '['
   *            VoidTypeCode         'V'
   *            BooleanTypeCode      'Z'
   *            ByteTypeCode         'B'
   *            ShortTypeCode        'S'
   *            IntTypeCode          'I'
   *            LongTypeCode         'J'
   *            FloatTypeCode        'F'
   *            DoubleTypeCode       'D'
   *            CharTypeCode         'C'
   * </pre>
   */
  @Pure
  public byte parseForTypeCode() throws IllegalArgumentException {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 0);
    }
    return val[0];
  }

  /**
   * Parse "this" array descriptor to obtain number of dimensions in
   * corresponding array type.
   * this: descriptor     - something like "[Ljava/lang/String;" or "[[I"
   * @return dimensionality - something like "1" or "2"
   */
  @Pure
  public int parseForArrayDimensionality() {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 1, "An array descriptor has at least two characters");
      VM._assert(val[0] == '[', "An array descriptor must start with '['");
    }
    for (int i = 0; ; ++i) {
      if (VM.VerifyAssertions) {
        VM._assert(i < val.length, "Malformed array descriptor: it can't just have [ characters");
      }
      if (val[i] != '[') {
        return i;
      }
    }
  }

  /**
   * Parse "this" array descriptor to obtain type code for its element type.
   * this: descriptor - something like "[Ljava/lang/String;" or "[I"
   * @return type code  - something like VM.ObjectTypeCode or VM.IntTypeCode
   * The type code will be one of the constants appearing in the table above.
   *
   * Implementation note: This is supposed to be uninterruptible, since another
   * allegedly uninterruptible method (VM_Array.getLogElementSize()) calls it.
   */
  @Uninterruptible
  @Pure
  public byte parseForArrayElementTypeCode() {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 1, "An array descriptor has at least two characters");
      VM._assert(val[0] == '[', "An array descriptor must start with '['");
    }
    return val[1];
  }

  /**
   * Return the innermost element type reference for an array
   */
  @Pure
  public VM_Atom parseForInnermostArrayElementDescriptor() {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 1, "An array descriptor has at least two characters");
      VM._assert(val[0] == '[', "An array descriptor must start with '['");
    }
    int i = 0;
    while (val[i] == '[') {
      if (VM.VerifyAssertions) {
        VM._assert(i < val.length, "Malformed array descriptor: it can't just have [ characters");
      }
      i++;
    }
    return findOrCreate(val, i, val.length - i, toUnicodeStringInternal());
  }

  /**
   * Parse "this" array descriptor to obtain descriptor for array's element
   * type.
   * this: array descriptor         - something like "[I"
   * @return array element descriptor - something like "I"
   */
  @Pure
  public VM_Atom parseForArrayElementDescriptor() {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 1, "An array descriptor has at least two characters");
      VM._assert(val[0] == '[', "An array descriptor must start with '['");
    }
    return findOrCreate(val, 1, val.length - 1, toUnicodeStringInternal());
  }

  /**
   * The set of class prefixes that MUST be loaded by bootstrap classloader.
   * @see #isBootstrapClassDescriptor()
   */
  private static final byte[][] BOOTSTRAP_CLASS_PREFIX_SET =
      {"Ljava/".getBytes(),
       "Lorg/jikesrvm/".getBytes(),
       "Lgnu/java/".getBytes(),
       "Lgnu/classpath/debug/".getBytes(),
       "Lgnu/classpath/jdwp/".getBytes(),
       "Lgnu/classpath/NotImplementedException".getBytes(),
       "Lgnu/classpath/Pair".getBytes(),
       "Lgnu/classpath/Pointer".getBytes(),
       "Lgnu/classpath/Pointer32".getBytes(),
       "Lgnu/classpath/Pointer64".getBytes(),
       "Lgnu/classpath/ServiceFactory".getBytes(),
       "Lgnu/classpath/ServiceProviderLoadingAction".getBytes(),
       "Lgnu/classpath/SystemProperties".getBytes(),
       "Lorg/vmmagic/".getBytes(),
       "Lorg/mmtk/".getBytes()};

  /**
   * The set of class prefixes that MUST NOT be loaded by bootstrap classloader.
   * @see #isBootstrapClassDescriptor()
   */
  private static final byte[][] NON_BOOTSTRAP_CLASS_PREFIX_SET =
      {"Lorg/jikesrvm/tools/ant/".getBytes(),
       "Lorg/jikesrvm/tools/apt/".getBytes(),
       "Lorg/jikesrvm/tools/template/".getBytes()};

  /**
   * The set of class prefixes for core RVM classes.
   * @see #isRVMDescriptor()
   */
  private static final byte[][] RVM_CLASS_PREFIXES =
      {"Lorg/jikesrvm/".getBytes(), "Lorg/vmmagic/".getBytes(), "Lorg/mmtk/".getBytes()};

  /**
   * @return true if this is a class descriptor of a bootstrap class
   * (ie a class that must be loaded by the bootstrap class loader)
   */
  @Pure
  public boolean isBootstrapClassDescriptor() {
    non_bootstrap_outer:
    for (final byte[] test : NON_BOOTSTRAP_CLASS_PREFIX_SET) {
      if (test.length > val.length) continue;
      for (int j = 0; j < test.length; j++) {
        if (val[j] != test[j]) {
          continue non_bootstrap_outer;
        }
      }
      return false;
    }
    bootstrap_outer:
    for (final byte[] test : BOOTSTRAP_CLASS_PREFIX_SET) {
      if (test.length > val.length) continue;
      for (int j = 0; j < test.length; j++) {
        if (val[j] != test[j]) {
          continue bootstrap_outer;
        }
      }
      return true;
    }
    return false;
  }

  /**
   * @return true if this is a class descriptor of a RVM core class.  This is
   * defined as one that it would be unwise to invalidate, since invalidating
   * it might make it impossible to recompile.
   */
  @Pure
  public boolean isRVMDescriptor() {
    outer:
    for (final byte[] test : RVM_CLASS_PREFIXES) {
      if (test.length > val.length) continue;
      for (int j = 0; j < test.length; j++) {
        if (val[j] != test[j]) {
          continue outer;
        }
      }
      return true;
    }
    return false;
  }

  //-------------//
  // annotations //
  //-------------//

  /**
   * Create an annotation name from a class name. For example
   * Lfoo.bar; becomes Lfoo.bar$$; NB in Sun VMs the annotation name
   * of the first annotation is $Proxy1. Classpath may later rely on
   * this to implement serialization correctly.
   */
  @Pure
  public VM_Atom annotationInterfaceToAnnotationClass() {
    byte[] annotationClassName_tmp = new byte[val.length + 2];
    System.arraycopy(val, 0, annotationClassName_tmp, 0, val.length - 1);
    annotationClassName_tmp[val.length - 1] = '$';
    annotationClassName_tmp[val.length] = '$';
    annotationClassName_tmp[val.length + 1] = ';';
    return VM_Atom.findOrCreateUtf8Atom(annotationClassName_tmp);
  }

  /**
   * Create a class name from a type name. For example Lfoo.bar$$;
   * becomes the string foo.bar
   */
  @Pure
  public String annotationClassToAnnotationInterface() {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 0);
      VM._assert(val[0] == 'L' && val[val.length - 1] == ';', toString());
    }
    return VM_StringUtilities.asciiBytesToString(val, 1, val.length - 4).replace('/', '.');
  }

  /**
   * Is this an annotation class name of the form Lfoo.bar$$;
   */
  @Pure
  public boolean isAnnotationClass() {
    return (val.length > 4) && (val[val.length - 3] == '$') && (val[val.length - 2] == '$');
  }

  //-----------//
  // debugging //
  //-----------//

  @Uninterruptible
  public void sysWrite() {
    for (int i = 0, n = val.length; i < n; ++i) {
      VM.sysWrite((char) val[i]);
    }
  }

  @Uninterruptible
  public int length() {
    return val.length;
  }

  /**
   * Create atom from the key that maps to it.
   */
  private VM_Atom(byte[] val, int id, String str) {
    this.id = id;
    this.unicodeStringOrJTOCoffset = str;
    if ((val == null) && (id != -1)) {
      this.val = VM_UTF8Convert.toUTF8(str);
    } else {
      this.val = val;
    }
  }

  /*
   * Hash table utilities
   */
  /**
   * Return the hashCode of an atom, this equals the unicode string encoding of
   * the atom
   */
  public int hashCode() {
    try {
      if (unicodeStringOrJTOCoffset != null) {
        return toUnicodeStringInternal().hashCode();
      } else {
        return VM_UTF8Convert.computeStringHashCode(val);
      }
    } catch (UTFDataFormatException e) {
      return 0;
    }
  }

  /**
   * Outside of this class atoms are canonical and should be compared using ==.
   * This method is used to maintain atoms in internal hash tables and shouldn't
   * be used externally.
   */
  @Pure
  public boolean equals(Object other) {
    // quick test as atoms are generally canonical
    if (this == other) {
      return true;
    } else {
      if (other instanceof VM_Atom) {
        VM_Atom that = (VM_Atom)other;
        // if the atoms are well formed then their identifiers are unique
        if ((that.id != -1) && (this.id != -1)) {
          return that.id == this.id;
        }
        // one atom isn't well formed, can we do a string comparison to work out equality?
        if ((this.unicodeStringOrJTOCoffset != null) && (that.unicodeStringOrJTOCoffset != null)) {
          return toUnicodeStringInternal().equals(that.toUnicodeStringInternal());
        }
        try {
          // perform byte by byte comparison
          byte[] val1;
          if (that.val != null) {
            val1 = that.val;
          } else {
            val1 = VM_UTF8Convert.toUTF8(that.toUnicodeString());
          }
          byte[] val2;
          if (this.val != null) {
            val2 = this.val;
          } else {
            val2 = VM_UTF8Convert.toUTF8(toUnicodeString());
          }
          if (val1.length == val2.length) {
            for (int i = 0; i < val1.length; i++) {
              if (val1[i] != val2[i]) return false;
            }
            return true;
          }
        } catch (UTFDataFormatException e) {
          throw new Error("Error in UTF data encoding: ",e);
        }
      }
      return false;
    }
  }


  /**
   * Inner class responsible for string interning. This class' initializer is
   * run during booting.
   */
  private static class InternedStrings {
    /**
     * Look up for interned strings.
     */
    private static final WeakHashMap<String,WeakReference<String>> internedStrings =
      new WeakHashMap<String,WeakReference<String>>();

    /**
     * Find an interned string but don't create it if not found
     * @param str string to lookup
     * @return the interned string or null if it isn't interned
     */
    static synchronized String findInternedString(String str) {
      WeakReference<String> ref;
      ref = internedStrings.get(str);
      if (ref != null) {
        String s = ref.get();
        if (s != null) {
          return s;
        }
      }
      return null;
    }

    /**
     * Find a string literal from an atom
     * @param str string to find
     * @return the string literal or null
     */
    static String findAtomString(String str) {
      VM_Atom atom = findUnicodeAtom(str);
      if (atom != null) {
        try {
          return atom.toUnicodeString();
        } catch (UTFDataFormatException e) {
          throw new Error("Error in UTF data encoding: ", e);
        }
      }
      return null;
    }

    /**
     * Intern a string that is not an atom or already interned string
     * @param str string to intern
     * @return interned string
     */
    static synchronized String internUnfoundString(String str) {
      // double check string isn't found as we're holding the lock on the class
      String s = findInternedString(str);
      if (s != null) return s;
      // If we get to here, then there is no interned version of the String.
      // So we make one.
      WeakReference<String> ref = new WeakReference<String>(str);
      internedStrings.put(str, ref);
      return str;
    }
  }

  /**
   * External string intern method called from String.intern. This method should
   * return a canonical string encoding for the given string and this string
   * should also be canonical with string literals.
   * @param str string to intern
   * @return interned version of string
   */
  public static String internString(String str) {
    // Has the string already been interned
    String s = InternedStrings.findInternedString(str);
    if (s != null) return s;

    // Check to see if this is a StringLiteral:
    s = InternedStrings.findAtomString(str);
    if (s != null) return s;

    // Intern this string
    return InternedStrings.internUnfoundString(str);
  }
}
