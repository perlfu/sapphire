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

import java.lang.annotation.Annotation;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.objectmodel.VM_TIB;
import org.jikesrvm.runtime.VM_Statics;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * A description of a java type.
 *
 * This class is the base of the java type system.
 * To the three kinds of java objects
 * (class-instances, array-instances, primitive-instances)
 * there are three corresponding
 * subclasses of VM_Type: VM_Class, VM_Array, VM_Primitive.
 * <p>
 * A VM_Class is constructed in four phases:
 * <ul>
 * <li> A "load" phase reads the ".class" file but does not attempt to
 *      examine any of the symbolic references present there. This is done
 *      by the VM_Class constructor as a result of a VM_TypeReference being
 *      resolved.
 *
 * <li> A "resolve" phase follows symbolic references as needed to discover
 *   ancestry, to measure field sizes, and to allocate space in the jtoc
 *   for the class's static fields and methods.
 *
 * <li>  An "instantiate" phase initializes and
 * installs the type information block and static methods.
 *
 * <li> An "initialize" phase runs the class's static initializer.
 * </ul>
 *
 * VM_Array's are constructed in a similar fashion.
 *
 * VM_Primitive's are constructed ab initio.
 * Their "resolution", "instantiation", and "initialization" phases
 * are no-ops.
 */
@NonMoving
public abstract class VM_Type extends VM_AnnotatedElement
    implements VM_ClassLoaderConstants, VM_SizeConstants, VM_Constants {

  /** Next space in the the type array */
  private static int nextId = 1;
  /** All types */
  private static VM_Type[] types = new VM_Type[1000];

  /** Canonical representation of no fields */
  protected static final VM_Field[] emptyVMField = new VM_Field[0];
  /** Canonical representation of no methods */
  protected static final VM_Method[] emptyVMMethod = new VM_Method[0];
  /** Canonical representation of no VM classes */
  protected static final VM_Class[] emptyVMClass = new VM_Class[0];

  /*
   * We hold on to a number of special types here for easy access.
   */
  public static final VM_Primitive VoidType;
  public static final VM_Primitive BooleanType;
  public static final VM_Primitive ByteType;
  public static final VM_Primitive ShortType;
  public static final VM_Primitive IntType;
  public static final VM_Primitive LongType;
  public static final VM_Primitive FloatType;
  public static final VM_Primitive DoubleType;
  public static final VM_Primitive CharType;
  public static final VM_Class JavaLangObjectType;
  public static final VM_Array JavaLangObjectArrayType;
  public static final VM_Class JavaLangClassType;
  public static final VM_Class JavaLangThrowableType;
  public static final VM_Class JavaLangStringType;
  public static final VM_Class JavaLangCloneableType;
  public static final VM_Class JavaIoSerializableType;
  public static final VM_Class MagicType;
  public static final VM_Primitive WordType;
  public static final VM_Array WordArrayType;
  public static final VM_Primitive AddressType;
  public static final VM_Array AddressArrayType;
  public static final VM_Class ObjectReferenceType;
  public static final VM_Array ObjectReferenceArrayType;
  public static final VM_Primitive OffsetType;
  public static final VM_Array OffsetArrayType;
  public static final VM_Primitive ExtentType;
  public static final VM_Array ExtentArrayType;
  public static final VM_Primitive CodeType;
  public static final VM_Array CodeArrayType;
  public static final VM_Class TIBType;
  public static final VM_Class ITableType;
  public static final VM_Class ITableArrayType;
  public static final VM_Class IMTType;
  public static final VM_Class ProcessorTableType;
  public static final VM_Class FunctionTableType;

  static {
    // Primitive types
    VoidType = VM_TypeReference.Void.resolve().asPrimitive();
    BooleanType = VM_TypeReference.Boolean.resolve().asPrimitive();
    ByteType = VM_TypeReference.Byte.resolve().asPrimitive();
    ShortType = VM_TypeReference.Short.resolve().asPrimitive();
    IntType = VM_TypeReference.Int.resolve().asPrimitive();
    LongType = VM_TypeReference.Long.resolve().asPrimitive();
    FloatType = VM_TypeReference.Float.resolve().asPrimitive();
    DoubleType = VM_TypeReference.Double.resolve().asPrimitive();
    CharType = VM_TypeReference.Char.resolve().asPrimitive();
    // Jikes RVM primitives
    AddressType = VM_TypeReference.Address.resolve().asPrimitive();
    WordType = VM_TypeReference.Word.resolve().asPrimitive();
    OffsetType = VM_TypeReference.Offset.resolve().asPrimitive();
    ExtentType = VM_TypeReference.Extent.resolve().asPrimitive();
    CodeType = VM_TypeReference.Code.resolve().asPrimitive();
    ObjectReferenceType = VM_TypeReference.ObjectReference.resolve().asClass();
    // Jikes RVM classes
    MagicType = VM_TypeReference.Magic.resolve().asClass();
    // Array types
    CodeArrayType = VM_TypeReference.CodeArray.resolve().asArray();
    WordArrayType = VM_TypeReference.WordArray.resolve().asArray();
    AddressArrayType = VM_TypeReference.AddressArray.resolve().asArray();
    ObjectReferenceArrayType = VM_TypeReference.ObjectReferenceArray.resolve().asArray();
    OffsetArrayType = VM_TypeReference.OffsetArray.resolve().asArray();
    ExtentArrayType = VM_TypeReference.ExtentArray.resolve().asArray();
    // Runtime Tables
    TIBType = VM_TypeReference.TIB.resolve().asClass();
    ITableType = VM_TypeReference.ITable.resolve().asClass();
    ITableArrayType = VM_TypeReference.ITableArray.resolve().asClass();
    IMTType = VM_TypeReference.IMT.resolve().asClass();
    ProcessorTableType = VM_TypeReference.ProcessorTable.resolve().asClass();
    FunctionTableType = VM_TypeReference.FunctionTable.resolve().asClass();
    // Java clases
    JavaLangObjectType = VM_TypeReference.JavaLangObject.resolve().asClass();
    JavaLangObjectArrayType = VM_TypeReference.JavaLangObjectArray.resolve().asArray();
    JavaLangClassType = VM_TypeReference.JavaLangClass.resolve().asClass();
    JavaLangThrowableType = VM_TypeReference.JavaLangThrowable.resolve().asClass();
    JavaLangStringType = VM_TypeReference.JavaLangString.resolve().asClass();
    JavaLangCloneableType = VM_TypeReference.JavaLangCloneable.resolve().asClass();
    JavaIoSerializableType = VM_TypeReference.JavaIoSerializable.resolve().asClass();
  }

  /**
   * Canonical type reference for this VM_Type instance
   */
  protected final VM_TypeReference typeRef;

  /**
   * Type id -- used to index into typechecking datastructures
   */
  @Entrypoint
  protected final int id;

  /**
   * index of jtoc slot that has type information block for this VM_Type
   */
  protected final int tibOffset;

  /**
   * instance of java.lang.Class corresponding to this type
   */
  private final Class<?> classForType;

  /**
   * Number of [ in descriptor for arrays; -1 for primitives; 0 for
   * classes. NB this field must appear in all VM_Types for fast type
   * checks (See {@link org.jikesrvm.compilers.opt.hir2lir.DynamicTypeCheckExpansion}).
   */
  @Entrypoint
  protected final int dimension;
  /**
   * Number of superclasses to Object. Known immediately for
   * primitives and arrays, but only after resolving for classes. NB
   * this field must appear in all VM_Types for fast object array
   * store checks (See {@link org.jikesrvm.compilers.opt.hir2lir.DynamicTypeCheckExpansion}).
   */
  @Entrypoint
  protected int depth;
  /**
   * cached VM_Array that corresponds to arrays of this type.
   * (null ->> not created yet).
   */
  private VM_Array cachedElementType;

  /**
   * The superclass ids for this type.
   */
  protected short[] superclassIds;

  /**
   * The interface implementation array for this type.
   */
  protected int[] doesImplement;

  /**
   * Create an instance of a {@link VM_Type}
   * @param typeRef The canonical type reference for this type.
   * @param classForType The java.lang.Class representation
   * @param dimension The dimensionality
   * @param annotations array of runtime visible annotations
   */
  protected VM_Type(VM_TypeReference typeRef, Class<?> classForType, int dimension, VM_Annotation[] annotations) {
    super(annotations);
    this.typeRef = typeRef;
    this.tibOffset = VM_Statics.allocateReferenceSlot(false).toInt();
    this.id = nextId(this);
    this.classForType = classForType;
    this.dimension = dimension;

    /* install partial type information block (no method dispatch table) for use in type checking. */
    VM_TIB tib = MM_Interface.newTIB(0);
    tib.setType(this);
    VM_Statics.setSlotContents(getTibOffset(), tib);
  }

  /**
   * Create an instance of a {@link VM_Type}
   * @param typeRef The canonical type reference for this type.
   * @param dimension The dimensionality
   * @param annotations array of runtime visible annotations
   */
  protected VM_Type(VM_TypeReference typeRef, int dimension, VM_Annotation[] annotations) {
    super(annotations);
    this.typeRef = typeRef;
    this.tibOffset = VM_Statics.allocateReferenceSlot(false).toInt();
    this.id = nextId(this);
    this.classForType = createClassForType(this, typeRef);
    this.dimension = dimension;

    /* install partial type information block (no method dispatch table) for use in type checking. */
    VM_TIB tib = MM_Interface.newTIB(0);
    tib.setType(this);
    VM_Statics.setSlotContents(getTibOffset(), tib);
  }

  /**
   * Canonical type reference for this type.
   */
  @Uninterruptible
  public final VM_TypeReference getTypeRef() {
    return typeRef;
  }

  /**
   * Get the numeric identifier for this type
   */
  @Uninterruptible
  public final int getId() {
    return id;
  }

  /**
   * Instance of java.lang.Class corresponding to this type.
   * This is commonly used for reflection.
   */
  public final Class<?> getClassForType() {
    if (VM.runningVM) {
      // Resolve the class so that we don't need to resolve it
      // in reflection code
      if (!isResolved()) {
        resolve();
      }
      return classForType;
    } else {
      return createClassForType(this, getTypeRef());
    }
  }

  /**
   * Get offset of tib slot from start of jtoc, in bytes.
   */
  @Uninterruptible
  public final Offset getTibOffset() {
    return Offset.fromIntSignExtend(tibOffset);
  }

  /**
   * Get the class loader for this type
   */
  @Uninterruptible
  public final ClassLoader getClassLoader() {
    return typeRef.getClassLoader();
  }

  /**
   * Should assertions be enabled on this type?
   * @return false
   */
  public boolean getDesiredAssertionStatus() {
    return false;
  }

  /**
   * Descriptor for this type.
   * For a class, something like "Ljava/lang/String;".
   * For an array, something like "[I" or "[Ljava/lang/String;".
   * For a primitive, something like "I".
   */
  @Uninterruptible
  public final VM_Atom getDescriptor() {
    return typeRef.getName();
  }

  /**
   * Define hashCode(), to allow use of consistent hash codes during
   * bootImage writing and run-time
   */
  @Override
  public final int hashCode() {
    return typeRef.hashCode();
  }

  /**
   * get number of superclasses to Object
   *   0 java.lang.Object, VM_Primitive, and VM_Classes that are interfaces
   *   1 for VM_Arrays and classes that extend Object directly
   */
  @Uninterruptible
  public abstract int getTypeDepth();

  /**
   * Reference Count GC: Is a reference of this type contained in
   * another object inherently acyclic (without cycles) ?
   */
  @Uninterruptible
  public abstract boolean isAcyclicReference();

  /**
   * Number of [ in descriptor for arrays; -1 for primitives; 0 for classes
   */
  @Uninterruptible
  public abstract int getDimensionality();

  /**
   * @return this cast to a VM_Class
   */
  @Uninterruptible
  public final VM_Class asClass() {
    return (VM_Class) this;
  }

  /**
   * @return this cast to a VM_Array
   */
  @Uninterruptible
  public final VM_Array asArray() {
    return (VM_Array) this;
  }

  /**
   * @return this cast to a VM_Primitive
   */
  @Uninterruptible
  public final VM_Primitive asPrimitive() {
    return (VM_Primitive) this;
  }

  // Convenience methods.
  //
  /** @return is this type void? */
  @Uninterruptible
  public final boolean isVoidType() {
    return this == VoidType;
  }

  /** @return is this type the primitive boolean? */
  @Uninterruptible
  public final boolean isBooleanType() {
    return this == BooleanType;
  }

  /** @return is this type the primitive byte? */
  @Uninterruptible
  public final boolean isByteType() {
    return this == ByteType;
  }

  /** @return is this type the primitive short? */
  @Uninterruptible
  public final boolean isShortType() {
    return this == ShortType;
  }

  /** @return is this type the primitive int? */
  @Uninterruptible
  public final boolean isIntType() {
    return this == IntType;
  }

  /** @return is this type the primitive long? */
  @Uninterruptible
  public final boolean isLongType() {
    return this == LongType;
  }

  /** @return is this type the primitive float? */
  @Uninterruptible
  public final boolean isFloatType() {
    return this == FloatType;
  }

  /** @return is this type the primitive double? */
  @Uninterruptible
  public final boolean isDoubleType() {
    return this == DoubleType;
  }

  /** @return is this type the primitive char? */
  @Uninterruptible
  public final boolean isCharType() {
    return this == CharType;
  }

  /**
   * @return is this type the primitive int like? ie is it held as an
   * int on the JVM stack
   */
  @Uninterruptible
  public final boolean isIntLikeType() {
    return isBooleanType() || isByteType() || isShortType() || isIntType() || isCharType();
  }

  /** @return is this type the class Object? */
  @Uninterruptible
  public final boolean isJavaLangObjectType() {
    return this == JavaLangObjectType;
  }

  /** @return is this type the class Throwable? */
  @Uninterruptible
  public final boolean isJavaLangThrowableType() {
    return this == JavaLangThrowableType;
  }

  /** @return is this type the class String? */
  @Uninterruptible
  public final boolean isJavaLangStringType() {
    return this == JavaLangStringType;
  }

  /**
   * Get array type corresponding to "this" array element type.
   */
  public final VM_Array getArrayTypeForElementType() {
    if (cachedElementType == null) {
      VM_TypeReference tr = typeRef.getArrayTypeForElementType();
      cachedElementType = tr.resolve().asArray();
      /*  Can't fail to resolve the type, because the element type already
          exists (it is 'this') and the VM creates array types itself without
          any possibility of error if the element type is already loaded. */
    }
    return cachedElementType;
  }

  /**
   * get superclass id vector (@see VM_DynamicTypeCheck)
   */
  @Uninterruptible
  public final short[] getSuperclassIds() {
    return superclassIds;
  }

  /**
   * get doesImplement vector (@see VM_DynamicTypeCheck)
   */
  @Uninterruptible
  public final int[] getDoesImplement() {
    return doesImplement;
  }

  /**
   * Allocate entry in types array and add it (NB resize array if it's
   * not long enough)
   */
  private static synchronized int nextId(VM_Type it) {
    int ans = nextId++;
    if (ans == types.length) {
      VM_Type[] newTypes = new VM_Type[types.length + 500];
      for (int i = 0; i < types.length; i++) {
        newTypes[i] = types[i];
      }
      types = newTypes;
    }
    types[ans] = it;
    return ans;
  }

  /**
   * How many types have been created?
   * Only intended to be used by the bootimage writer!
   */
  @Uninterruptible
  public static int numTypes() {
    return nextId - 1;
  }

  /**
   * Get all the created types.
   * Only intended to be used by the bootimage writer!
   */
  @Uninterruptible
  public static VM_Type[] getTypes() {
    return types;
  }

  /**
   * Get the type for the given id
   */
  @Uninterruptible
  public static VM_Type getType(int id) {
    return types[id];
  }

  /**
   * Utility to create a java.lang.Class for the given type using the
   * given type reference
   */
  protected static Class<?> createClassForType(VM_Type type, VM_TypeReference typeRef) {
    if (VM.runningVM) {
      return java.lang.JikesRVMSupport.createClass(type);
    } else {
      Exception x;
      try {
        VM_Atom className = typeRef.getName();
        if (className.isAnnotationClass()) {
          return Class.forName(className.annotationClassToAnnotationInterface(), false, VM_Type.class.getClassLoader());
        } else if (className.isClassDescriptor()) {
          return Class.forName(className.classNameFromDescriptor(), false, VM_Type.class.getClassLoader());
        } else {
          String classNameString = className.toString();
          if (classNameString.equals("V")) {
            return void.class;
          } else if(classNameString.equals("I")){
            return int.class;
          } else if(classNameString.equals("J")){
            return long.class;
          } else if(classNameString.equals("F")){
            return float.class;
          } else if(classNameString.equals("D")){
            return double.class;
          } else if(classNameString.equals("C")){
            return char.class;
          } else if(classNameString.equals("S")){
            return short.class;
          } else if(classNameString.equals("Z")){
            return boolean.class;
          } else if(classNameString.equals("B")){
            return byte.class;
          } else {
            return Class.forName(classNameString.replace('/', '.'), false, VM_Type.class.getClassLoader());
          }
        }
      } catch (ClassNotFoundException e) { x = e; } catch (SecurityException e) { x = e; }
      if (typeRef.isArrayType() && typeRef.getArrayElementType().isCodeType()) {
        // ignore - we expect not to have a concrete version of the code class
        return null;
      } else if (!VM.runningVM) {
        // Give a warning as this is probably a protection issue for
        // the tool and JVM
        VM.sysWriteln("Warning unable to find Java class for RVM type");
        x.printStackTrace();
        return null;
      } else {
        throw new Error("Unable to find Java class for RVM type", x);
      }
    }
  }

  /**
   * Find specified virtual method description.
   * @param memberName   method name - something like "foo"
   * @param memberDescriptor method descriptor - something like "I" or "()I"
   * @return method description (null --> not found)
   */
  public final VM_Method findVirtualMethod(VM_Atom memberName, VM_Atom memberDescriptor) {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    VM_Method[] methods = getVirtualMethods();
    for (int i = 0, n = methods.length; i < n; ++i) {
      VM_Method method = methods[i];
      if (method.getName() == memberName && method.getDescriptor() == memberDescriptor) {
        return method;
      }
    }
    return null;
  }

  /**
   * Return the method at the given TIB slot
   * @param slot the slot that contains the method
   * @return the method at that slot
   */
  public final VM_Method getTIBMethodAtSlot(int slot) {
    int index = VM_TIB.getVirtualMethodIndex(slot);
    VM_Method[] methods = getVirtualMethods();
    if (VM.VerifyAssertions) VM._assert(methods[index].getOffset().toInt() == slot << LOG_BYTES_IN_ADDRESS);
    return methods[index];
  }
  // Methods implemented in VM_Primitive, VM_Array or VM_Class

  /**
   * Get the annotation implementing the specified class or null during boot
   * image write time
   */
  protected <T extends Annotation> T getBootImageWriteTimeAnnotation(Class<T> annotationClass) {
    return getClassForType().getAnnotation(annotationClass);
  }

  /**
   * Resolution status.
   * If the class/array has been "resolved", then size and offset information is
   * available by which the compiler can generate code to access this
   * class/array's
   * fields/methods via direct loads/stores/calls (rather than generating
   * code to access fields/methods symbolically, via dynamic linking stubs).
   * Primitives are always treated as "resolved".
   */
  @Uninterruptible
  public abstract boolean isResolved();

  /**
   * Instantiation status.
   * If the class/array has been "instantiated",
   * then all its methods have been compiled
   * and its type information block has been placed in the jtoc.
   * Primitives are always treated as "instantiated".
   */
  @Uninterruptible
  public abstract boolean isInstantiated();

  /**
   * Initialization status.
   * If the class has been "initialized",
   * then its <clinit> method has been executed.
   * Arrays have no <clinit> methods so they become
   * "initialized" immediately upon "instantiation".
   * Primitives are always treated as "initialized".
   */
  @Uninterruptible
  public abstract boolean isInitialized();

  /**
   * Only intended to be used by the BootImageWriter
   */
  public abstract void markAsBootImageClass();

  /**
   * Is this class part of the virtual machine's boot image?
   */
  @Uninterruptible
  public abstract boolean isInBootImage();

  /**
   * Get the offset in instances of this type assigned to the thin lock word.
   * Offset.max() if instances of this type do not have thin lock words.
   */
  @Uninterruptible
  public abstract Offset getThinLockOffset();

  /**
   * @return whether or not this is an instance of VM_Class?
   */
  @Uninterruptible
  public abstract boolean isClassType();

  /**
   * @return whether or not this is an instance of VM_Array?
   */
  @Uninterruptible
  public abstract boolean isArrayType();

  /**
   * @return whether or not this is a primitive type
   */
  @Uninterruptible
  public abstract boolean isPrimitiveType();

  /**
   * @return whether or not this is a reference (ie non-primitive) type.
   */
  @Uninterruptible
  public abstract boolean isReferenceType();

  /**
   * Space required when this type is stored on the stack
   * (or as a field), in words.
   * Ie. 0, 1, or 2 words:
   * <ul>
   * <li> reference types (classes and arrays) require 1 word
   * <li> void types require 0 words
   * <li> long and double types require 2 words
   * <li> all other primitive types require 1 word
   * </ul>
   */
  @Uninterruptible
  public abstract int getStackWords();

  /**
   * Number of bytes in memory required to represent the type
   */
  @Uninterruptible
  public abstract int getMemoryBytes();

  /**
   * Cause resolution to take place.
   * This will cause slots to be allocated in the jtoc.
   */
  public abstract void resolve();

  /**
   * This method is only called by the bootimage writer.
   * It is called after {@link #resolve()} has been called on all
   * bootimaage types but before {@link #instantiate()} has been called
   * on any bootimaage type.
   * This provides a hook to compute various summaries that cannot be computed before types
   * are resolved.
   */
  public abstract void allBootImageTypesResolved();

  /**
   * Cause instantiation to take place.
   * This will cause the class's methods to be compiled and slots in the
   * jtoc to be filled-in.
   */
  public abstract void instantiate();

  /**
   * Cause initialization to take place.
   * This will cause the class's <clinit> method to be executed.
   */
  public abstract void initialize();

  /**
   * Does this type override java.lang.Object.finalize()?
   */
  @Uninterruptible
  public abstract boolean hasFinalizer();

  /**
   * Static fields of this class/array type.
   */
  public abstract VM_Field[] getStaticFields();

  /**
   * Non-static fields of this class/array type
   * (composed with supertypes, if any).
   */
  public abstract VM_Field[] getInstanceFields();

  /**
   * Statically dispatched methods of this class/array type.
   */
  public abstract VM_Method[] getStaticMethods();

  /**
   * Virtually dispatched methods of this class/array type
   * (composed with supertypes, if any).
   */
  public abstract VM_Method[] getVirtualMethods();

  /**
   * Runtime type information for this class/array type.
   */
  @Uninterruptible
  public abstract VM_TIB getTypeInformationBlock();

  /**
   * Set the specialized method for a class or array.
   */
  public final void setSpecializedMethod(int id, VM_CodeArray code) {
    getTypeInformationBlock().setSpecializedMethod(id, code);
  }

  /**
   * The memory manager's allocator id for this type.
   */
  private int mmAllocator;

  /**
   * Record the allocator information the memory manager holds about this
   * type.
   *
   * @param mmType the type to record
   */
  public final void setMMAllocator(int allocator) {
    this.mmAllocator = allocator;
  }

  /**
   * This returns the allocator id as supplied by the memory manager.
   * The method is located here as this is the only common superclass of VM_Array
   * and VM_Class, and due to performance reasons this needs to be a non-abstract
   * method. For VM_Primitive this field is unused.
   *
   * @return the allocator id previously recorded.
   */
  @Uninterruptible
  @Inline
  public final int getMMAllocator() {
    return mmAllocator;
  }

  /**
   * Is this field a type that must never move?
   */
  public boolean isNonMoving() {
    return hasNonMovingAnnotation();
  }
}
