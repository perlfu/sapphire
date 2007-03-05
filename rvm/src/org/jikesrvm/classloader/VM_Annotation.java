/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2006
 */
package org.jikesrvm.classloader;

import org.jikesrvm.VM_Statics;
import org.jikesrvm.VM_Runtime;
import org.jikesrvm.VM_Reflection;
import java.io.DataInputStream;
import java.io.IOException;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.pragma.Uninterruptible;
import java.lang.annotation.Annotation;
import java.util.Arrays;

/**
 * Internal representation of an annotation. We synthetically create
 * actual annotations {@link VM_Class}.
 *
 * @author Ian Rogers
 */
public final class VM_Annotation {
  /**
   * The type of the annotation. This is an interface name that the
   * annotation value will implement
   */
  private final VM_Atom type;
  /**
   * Members of this annotation
   */
  private final AnnotationMember[] elementValuePairs;
  /**
   * The class loader that loaded this annotation
   */
  private final ClassLoader classLoader;
  /**
   * A reference to the constructor of the base annotation
   */
  private static final VM_MethodReference baseAnnotationInitMethod;

  /**
   * The concrete annotation represented by this VM_Annotation
   */
  private Annotation value;

  /**
   * Class constructor
   */
  static {
    baseAnnotationInitMethod = (VM_MethodReference)
      VM_MemberReference.findOrCreate(VM_TypeReference.findOrCreate("Lorg/jikesrvm/classloader/VM_Annotation$BaseAnnotation;"),
                                      VM_Atom.findOrCreateAsciiAtom("<init>"),
                                      VM_Atom.findOrCreateAsciiAtom("(Lorg/jikesrvm/classloader/VM_Annotation;)V")
                                      );
    if(baseAnnotationInitMethod == null) {
      throw new Error("Error creating reference to base annotation");
    }
  }

  /**
   * Construct a read annotation
   * @param type the name of the type this annotation's value will
   * implement
   * @param elementValuePairs values for the fields in the annotation
   * that override the defaults
   * @param classLoader the class loader being used to load this annotation
   */
  private VM_Annotation(VM_Atom type, AnnotationMember[] elementValuePairs, ClassLoader classLoader){
    this.type = type;
    this.elementValuePairs = elementValuePairs;
    this.classLoader = classLoader;
  }

  /**
   * Read an annotation attribute from the class file
   *
   * @param constantPool from constant pool being loaded
   * @param input the data being rea
   */
  static VM_Annotation readAnnotation (int[] constantPool, DataInputStream input,
                                       ClassLoader classLoader) throws IOException, ClassNotFoundException {
    VM_Atom type;
    // Read type
    int typeIndex = input.readUnsignedShort();
    type = VM_Class.getUtf(constantPool, typeIndex);
    // Read values
    int numAnnotationMembers = input.readUnsignedShort();
    AnnotationMember[] elementValuePairs = new AnnotationMember[numAnnotationMembers];
    for(int i=0; i < numAnnotationMembers; i++) {
      elementValuePairs[i] = AnnotationMember.readAnnotationMember(constantPool, input, classLoader);
    }
    // Arrays.sort(elementValuePairs);
    VM_Annotation result = new VM_Annotation(type, elementValuePairs, classLoader);
    return result;
  }

  /**
   * Return the annotation represented by this VM_Annotation. If this
   * is the first time this annotation has been accessed the subclass
   * of annotation this class represents needs creating.
   * @return the annotation represented
   */
  Annotation getValue() {
    if (value == null) {
      value = createValue();
    }
    return value;
  }

  /**
   * Create an instance of this type of annotation with the values
   * given in the members
   *
   * @return the created annotation
   */
  private Annotation createValue() {
    // Find the annotation then find its implementing class
    VM_Class annotationInterface = VM_TypeReference.findOrCreate(classLoader, type).resolve().asClass();
    if(!annotationInterface.isResolved()) {
      annotationInterface.resolve();
    }
    VM_Class annotationClass = annotationInterface.getAnnotationClass();
    if(!annotationClass.isResolved()) {
      annotationClass.resolve();
    }
    if(!annotationClass.isInitialized()) {
      VM_Runtime.initializeClassForDynamicLink(annotationClass);
    }
    // Construct an instance with default values
    Annotation annotationInstance = (Annotation)VM_Runtime.resolvedNewScalar(annotationClass);
    VM_Method defaultConstructor = annotationClass.getConstructorMethods()[0];
    VM_Reflection.invoke(defaultConstructor, annotationInstance, new VM_Annotation[]{this});
    // Override default values with those given in the element value pairs
    VM_Field[] annotationClassFields = annotationClass.getDeclaredFields();
    for (AnnotationMember evp : elementValuePairs) {
      VM_Atom evpFieldName = evp.getNameAsFieldName();
      for (VM_Field field : annotationClassFields) {
        if (field.getName() == evpFieldName) {
          evp.setValueToField(field, annotationInstance);
        }
      }
    }
    return annotationInstance;
  }

  /**
   * Return a string representation of the annotation of the form
   * "@type(name1=val1, ...nameN=valN)"
   */
  public String toString() {
    String result = type.toString();
    result = "@" + result.substring(1,result.length()-1) + "(";
    if (elementValuePairs != null){
      for(int i=0; i < elementValuePairs.length; i++) {
        result += elementValuePairs[i];
        if(i < (elementValuePairs.length - 1)) {
          result += ", ";
        }
      }
    }
    result += ")";
    return result;
  }

  /**
   * Read the element_value field of an annotation
   *
   * @param constantPool the constant pool for the class being read
   * @param input stream to read from
   * @return object representing the value read
   */
  static Object readValue(int[] constantPool, DataInputStream input,
                          ClassLoader classLoader) throws IOException, ClassNotFoundException {
    // Read element value's tag and decode
    byte elementValue_tag = input.readByte();
    Object value;
    switch (elementValue_tag) {
    case 'B':
      {
        Offset offset = VM_Class.getLiteralOffset(constantPool, input.readUnsignedShort());
        value = (byte) VM_Statics.getSlotContentsAsInt(offset);
        break;
      }
    case 'C':
      {
        Offset offset = VM_Class.getLiteralOffset(constantPool, input.readUnsignedShort());
        value = (char) VM_Statics.getSlotContentsAsInt(offset);
        break;
      }
    case 'D': 
      {
        Offset offset = VM_Class.getLiteralOffset(constantPool, input.readUnsignedShort());
        long longValue = VM_Statics.getSlotContentsAsLong(offset);
        value = Double.longBitsToDouble(longValue);
        break;
      }
    case 'F':
      {
        Offset offset = VM_Class.getLiteralOffset(constantPool, input.readUnsignedShort());
        int intValue = VM_Statics.getSlotContentsAsInt(offset);
        value = Float.intBitsToFloat(intValue);
        break;
      }
    case 'I':
      {
        Offset offset = VM_Class.getLiteralOffset(constantPool, input.readUnsignedShort());
        value = VM_Statics.getSlotContentsAsInt(offset);
        break;
      }
    case 'J':
      {
        Offset offset = VM_Class.getLiteralOffset(constantPool, input.readUnsignedShort());
        value = VM_Statics.getSlotContentsAsLong(offset);
        break;
      }
    case 'S':
      {
        Offset offset = VM_Class.getLiteralOffset(constantPool, input.readUnsignedShort());
        value = (short) VM_Statics.getSlotContentsAsInt(offset);
        break;
      }
    case 'Z':
      {
        Offset offset = VM_Class.getLiteralOffset(constantPool, input.readUnsignedShort());
        value = VM_Statics.getSlotContentsAsInt(offset) == 1;
        break;
      }
    case 's':
      {
        value = VM_Class.getUtf(constantPool, input.readUnsignedShort()).toString();
        break;
      }
    case 'e':
      {
        int typeNameIndex = input.readUnsignedShort();
        @SuppressWarnings("unchecked")
        Class enumType = VM_TypeReference.findOrCreate(classLoader,
                                                       VM_Class.getUtf(constantPool, typeNameIndex)
                                                       ).resolve().getClassForType();
        int constNameIndex = input.readUnsignedShort();
        
        @SuppressWarnings("unchecked") // Yes, we're breaking type safety here.
        Enum tmp = Enum.valueOf(enumType, VM_Class.getUtf(constantPool, constNameIndex).toString());
        value = tmp;
        break;
      }
    case 'c':
      {
        int classInfoIndex = input.readUnsignedShort();
        value= Class.forName(VM_Class.getUtf(constantPool, classInfoIndex).toString());
        break;
      }
    case '@':
      value = VM_Annotation.readAnnotation(constantPool, input, classLoader);
      break;
    case '[':
      {
        int numValues = input.readUnsignedShort();
        Object[] array = new Object[numValues];
        for (int i=0; i < numValues; i++) {
          array[i] = readValue(constantPool, input, classLoader);
        }
        value = array;
        break;
      }
    default:
      value = null;
      throw new ClassFormatError("Unknown element_value tag '" +
                                 (char)elementValue_tag + "'");
    }
    return value;
  }
  
  /**
   * Return the VM_TypeReference of the declared annotation, ie an
   * interface and not the class object of this instance
   *
   * @return VM_TypeReferernce of interface annotation object implements
   */
  VM_TypeReference annotationType() {
    return VM_TypeReference.findOrCreate(classLoader, type);
  }

  @Uninterruptible
  VM_Atom getType() { return type; }

  @Uninterruptible
  ClassLoader getClassLoader() { return classLoader; }

  /**
   * Are two annotations logically equivalent?
   *
   * todo: for performance reasons if we dynamically generated the
   * bytecode for this method, rather than using reflection, the
   * performance should be better.
   */
  static boolean equals(BaseAnnotation a, VM_Annotation vmA,
                        BaseAnnotation b, VM_Annotation vmB) {
    if(vmA.type != vmB.type) {
      return false;
    }
    else {
      VM_Class annotationInterface = VM_TypeReference.findOrCreate(vmA.classLoader, vmA.type).resolve().asClass();
      VM_Class annotationClass = annotationInterface.getAnnotationClass();
      VM_Field[] annotationClassFields = annotationClass.getDeclaredFields();
      for (VM_Field annotationClassField : annotationClassFields) {
        Object objA = annotationClassField.getObjectUnchecked(a);
        Object objB = annotationClassField.getObjectUnchecked(b);
        if (!objA.getClass().isArray()) {
          if (!objA.equals(objB)) {
            return false;
          }
        } else {
          return Arrays.equals((Object[]) objA, (Object[]) objB);
        }
      }
      return true;
    }
  }

  /**
   * Compute the hashCode for an instance of an annotation
   *
   * todo: for performance reasons if we dynamically generated the
   * bytecode for this method, rather than using reflection, the
   * performance should be better.
   */
  public int hashCode(BaseAnnotation a) {
    VM_Class annotationInterface = VM_TypeReference.findOrCreate(classLoader, type).resolve().asClass();
    VM_Class annotationClass = annotationInterface.getAnnotationClass();
    VM_Field[] annotationClassFields = annotationClass.getDeclaredFields();
    String typeString = type.toString();
    int result = typeString.substring(1, typeString.length() - 1).hashCode();
    for (VM_Field field : annotationClassFields) {
      String name = field.getName().toString();
      name = name.substring(0, name.length() - 6); // remove "_field" from name
      Object value = field.getObjectUnchecked(a);
      int part_result = name.hashCode() * 127;
      if (value.getClass().isArray()) {
        part_result ^= Arrays.hashCode((Object[]) value);
      } else {
        part_result ^= value.hashCode();
      }
      result += part_result;
    }
    return result;
  }

  /**
   * @return member reference to init method of BaseAnnotation
   */
  static VM_MethodReference getBaseAnnotationInitMemberReference() {
    if(baseAnnotationInitMethod == null) {
      throw new Error("Error creating reference to base annotation");
    }
    return baseAnnotationInitMethod;
  }

  /**
   * The superclass for all annotation instances
   */
  abstract static class BaseAnnotation implements Annotation {
    /**
     * The VM_Annotation that this annotation is an instance of
     */
    private final VM_Annotation vmAnnotation;
    /**
     * Constructor, called via VM_Annotation.createValue
     */
    BaseAnnotation(VM_Annotation vmAnnotation) {
      this.vmAnnotation = vmAnnotation;
    }
    /**
     * Return a string representation of the annotation of the form
     * "@type(name1=val1, ...nameN=valN)"
     */
    public String toString() {
      return vmAnnotation.toString();
    }
    /**
     * Return the Class object of the declared annotation, ie an
     * interface and not the class object of this instance
     *
     * @return Class object of interface annotation object implements
     */
    @SuppressWarnings("unchecked") // We intentionally break type-safety
    public Class<? extends Annotation> annotationType() {
      return (Class<? extends Annotation>)vmAnnotation.annotationType().resolve().getClassForType();
    }
    /**
     * Are two annotations logically equivalent?
     */
    public boolean equals(Object o) {
      if (o instanceof BaseAnnotation) {
        if(o == this) {
          return true;
        }
        else{
          BaseAnnotation b =  (BaseAnnotation)o;
          return VM_Annotation.equals(this, this.vmAnnotation,
                                      b, b.vmAnnotation);
        }
      }
      else {
        return false;
      }
    }
    /**
     * Compute the hash code of an annotation using the standard
     * algorithm {@link java.lang.annotation.Annotation#hashCode()}
     */
    public int hashCode() {
      return vmAnnotation.hashCode(this);
    }
  }

  /**
   * A class to decode and hold the name and its associated value for
   * an annotation member
   */
  private static final class AnnotationMember implements Comparable<AnnotationMember> {
    /**
     * Name of element
     */
    private final VM_Atom name;
    /**
     * Elements value, decoded from its tag
     */
    private final Object value;
    /**
     * Construct a read value pair
     */
    private AnnotationMember(VM_Atom name, Object value) {
      this.name = name;
      this.value = value;
    }
    /**
     * Read the pair from the input stream and create object
     * @param constantPool the constant pool for the class being read
     * @param input stream to read from
     * @param classLoader the class loader being used to load this annotation
     * @return a newly created annotation member
     */
    static AnnotationMember readAnnotationMember (int[] constantPool, DataInputStream input,
                                                  ClassLoader classLoader) throws IOException, ClassNotFoundException {
      // Read name of pair
      int elemNameIndex = input.readUnsignedShort();
      VM_Atom name = VM_Class.getUtf(constantPool, elemNameIndex);
      Object value = VM_Annotation.readValue(constantPool, input, classLoader);
      return new AnnotationMember(name, value);
    }
    /**
     * Return name as it would appear in a class implementing this
     * annotation
     */
    VM_Atom getNameAsFieldName() {
      return VM_Atom.findAsciiAtom(name.toString() + "_field");
    }
    /**
     * Set the value to the given field of the given annotation
     */
    void setValueToField(VM_Field field, Annotation annotation) {
      if(value instanceof Boolean) {
        field.setBooleanValueUnchecked(annotation, (Boolean) value);
      }
      else if(value instanceof Integer) {
        field.setIntValueUnchecked(annotation, (Integer) value);
      }
      else if(value instanceof Long) {
        field.setLongValueUnchecked(annotation, (Long) value);
      }
      else if(value instanceof Byte) {
        field.setByteValueUnchecked(annotation, (Byte) value);
      }
      else if(value instanceof Character) {
        field.setCharValueUnchecked(annotation, (Character) value);
      }
      else if(value instanceof Short) {
        field.setShortValueUnchecked(annotation, (Short) value);
      }
      else if(value instanceof Float) {
        field.setFloatValueUnchecked(annotation, (Float) value);
      }
      else if(value instanceof Double) {
        field.setDoubleValueUnchecked(annotation, (Double) value);
      }
      else {
        field.setObjectValueUnchecked(annotation, value);
      }
    }
    /**
     * String representation of the value pair of the form
     * "name=value"
     */
    public String toString() {
      String result = name.toString() + "=";
      if (value instanceof Object[]) {
        result += "{";
        Object[] a = (Object[])value;
        for(int i=0; i < a.length; i++) {
          result += a[i].toString();
          if (i < (a.length -1)) {
            result += ", ";
          }
          result += "}";
        }
      } else {
        result += value.toString();
      }
      return result;
    }
    /**
     * Ordering for sorted annotation members
     */
    public int compareTo(AnnotationMember am) {
      if(am.name != this.name) {
        return am.name.toString().compareTo(this.name.toString());
      } else {
        if(value.getClass().isArray()) {
          return Arrays.hashCode((Object[])value) - Arrays.hashCode((Object[])am.value);
        }
        else {
          @SuppressWarnings("unchecked") // True generic programming, we can't type check it in Java
          Comparable<Object> cValue = (Comparable)value;
          return cValue.compareTo(am.value);
        }
      }
    }
  }
}
