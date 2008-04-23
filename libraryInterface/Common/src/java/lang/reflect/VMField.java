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
package java.lang.reflect;

import java.lang.annotation.Annotation;

import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.classloader.VM_Type;

import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_Runtime;

/**
 * Implementation of java.lang.reflect.Field for JikesRVM.
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API.
 */
public final class VMField {

  final VM_Field field;
  Field f;

  // Prevent this class from being instantiated.
  @SuppressWarnings("unused")
  private VMField() {
    field = null;
  }

  // For use by JikesRVMSupport
  VMField(VM_Field f) {
    field = f;
  }

  public boolean equals(Object object) {
    if (object instanceof Field) {
      return field == ((Field)object).f.field;
    } else {
      return false;
    }
  }

  Object get(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);

    if (field.isReferenceType()) {
      return field.getObjectValueUnchecked(object);
    }
    VM_TypeReference type = field.getType();
    if (type.isIntType()) {
      return field.getIntValueUnchecked(object);
    } else if (type.isCharType()) {
      return field.getCharValueUnchecked(object);
    } else if (type.isShortType()) {
      return field.getShortValueUnchecked(object);
    } else if (type.isLongType()) {
      return field.getLongValueUnchecked(object);
    } else if (type.isByteType()) {
      return field.getByteValueUnchecked(object);
    } else if (type.isBooleanType()) {
      return field.getBooleanValueUnchecked(object);
    } else if (type.isDoubleType()) {
      return field.getDoubleValueUnchecked(object);
    } else {
      if (VM.VerifyAssertions) VM._assert(type.isFloatType());
      return field.getFloatValueUnchecked(object);
    }
  }

  boolean getBoolean(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return getBooleanInternal(object);
  }

  byte getByte(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return getByteInternal(object);
  }

  char getChar(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return getCharInternal(object);
  }

  public Class<?> getDeclaringClass() {
    return field.getDeclaringClass().getClassForType();
  }

  double getDouble(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return getDoubleInternal(object);
  }

  float getFloat(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return getFloatInternal(object);
  }

  int getInt(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return getIntInternal(object);
  }

  long getLong(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return getLongInternal(object);
  }

  int getModifiersInternal() {
    return field.getModifiers();
  }

  public String getName() {
    return field.getName().toString();
  }

  public short getShort(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return getShortInternal(object);
  }

  Class<?> getType() {
    return field.getType().resolve().getClassForType();
  }

  void set(Object object, Object value)
    throws IllegalAccessException, IllegalArgumentException     {
    checkWriteAccess(object);

    if (field.isReferenceType()) {
      if (value != null) {
        VM_Type valueType = VM_ObjectModel.getObjectType(value);
        VM_Type fieldType;
        try {
          fieldType = field.getType().resolve();
        } catch (NoClassDefFoundError e) {
          throw new IllegalArgumentException("field type mismatch");
        }
        if (fieldType != valueType &&
            !VM_Runtime.isAssignableWith(fieldType, valueType)) {
          throw new IllegalArgumentException("field type mismatch");
        }
      }
      field.setObjectValueUnchecked(object, value);
    } else if (value instanceof Character) {
      setCharInternal(object, (Character) value);
    } else if (value instanceof Double) {
      setDoubleInternal(object, (Double) value);
    } else if (value instanceof Float) {
      setFloatInternal(object, (Float) value);
    } else if (value instanceof Long) {
      setLongInternal(object, (Long) value);
    } else if (value instanceof Integer) {
      setIntInternal(object, (Integer) value);
    } else if (value instanceof Short) {
      setShortInternal(object, (Short) value);
    } else if (value instanceof Byte) {
      setByteInternal(object, (Byte) value);
    } else if (value instanceof Boolean) {
      setBooleanInternal(object, (Boolean) value);
    } else {
      throw new IllegalArgumentException("field type mismatch");
    }
  }

  void setBoolean(Object object, boolean value)
    throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object);
    setBooleanInternal(object, value);
  }

   void setByte(Object object, byte value)
    throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object);
    setByteInternal(object, value);
  }

  void setChar(Object object, char value)
    throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object);
    setCharInternal(object, value);
  }

  void setDouble(Object object, double value)
    throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object);
    setDoubleInternal(object, value);
  }

  void setFloat(Object object, float value)
    throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object);
    setFloatInternal(object, value);
  }

  void setInt(Object object, int value)
    throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object);
    setIntInternal(object, value);
  }

  void setLong(Object object, long value)
    throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object);
    setLongInternal(object, value);
  }

  void setShort(Object object, short value)
    throws IllegalAccessException, IllegalArgumentException   {
    checkWriteAccess(object);
    setShortInternal(object, value);
  }

  private void checkReadAccess(Object obj) throws IllegalAccessException,
                                                  IllegalArgumentException,
                                                  ExceptionInInitializerError {

    VM_Class declaringClass = field.getDeclaringClass();
    if (!field.isStatic()) {
      if (obj == null) {
        throw new NullPointerException();
      }

      VM_Type objType = VM_ObjectModel.getObjectType(obj);
      if (objType != declaringClass && !VM_Runtime.isAssignableWith(declaringClass, objType)) {
        throw new IllegalArgumentException();
      }
    }

    if (!field.isPublic() && !f.isAccessible()) {
      VM_Class accessingClass = VM_Class.getClassFromStackFrame(3);
      JikesRVMSupport.checkAccess(field, accessingClass);
    }

    if (field.isStatic() && !declaringClass.isInitialized()) {
      try {
        VM_Runtime.initializeClassForDynamicLink(declaringClass);
      } catch (Throwable e) {
        ExceptionInInitializerError ex = new ExceptionInInitializerError();
        ex.initCause(e);
        throw ex;
      }
    }
  }

  private void checkWriteAccess(Object obj) throws IllegalAccessException,
                                                   IllegalArgumentException,
                                                   ExceptionInInitializerError {

    VM_Class declaringClass = field.getDeclaringClass();
    if (!field.isStatic()) {
      if (obj == null) {
        throw new NullPointerException();
      }

      VM_Type objType = VM_ObjectModel.getObjectType(obj);
      if (objType != declaringClass && !VM_Runtime.isAssignableWith(declaringClass, objType)) {
        throw new IllegalArgumentException();
      }
    }

    if (!field.isPublic() && !f.isAccessible()) {
      VM_Class accessingClass = VM_Class.getClassFromStackFrame(3);
      JikesRVMSupport.checkAccess(field, accessingClass);
    }

    if (field.isFinal())
      throw new IllegalAccessException();

    if (field.isStatic() && !declaringClass.isInitialized()) {
      try {
        VM_Runtime.initializeClassForDynamicLink(declaringClass);
      } catch (Throwable e) {
        ExceptionInInitializerError ex = new ExceptionInInitializerError();
        ex.initCause(e);
        throw ex;
      }
    }
  }

  public boolean getBooleanInternal(Object object) throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (!type.isBooleanType()) throw new IllegalArgumentException("field type mismatch");
    return field.getBooleanValueUnchecked(object);
  }

  private byte getByteInternal(Object object) throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (!type.isByteType()) throw new IllegalArgumentException("field type mismatch");
    return field.getByteValueUnchecked(object);
  }

  private char getCharInternal(Object object) throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (!type.isCharType()) throw new IllegalArgumentException("field type mismatch");
    return field.getCharValueUnchecked(object);
  }

  private double getDoubleInternal(Object object) throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (type.isDoubleType()) {
      return field.getDoubleValueUnchecked(object);
    } else if (type.isFloatType()) {
      return (double)field.getFloatValueUnchecked(object);
    } else if (type.isLongType()) {
      return (double)field.getLongValueUnchecked(object);
    } else if (type.isIntType()) {
      return (double)field.getIntValueUnchecked(object);
    } else if (type.isShortType()) {
      return (double)field.getShortValueUnchecked(object);
    } else if (type.isCharType()) {
      return (double)field.getCharValueUnchecked(object);
    } else if (type.isByteType()) {
      return (double)field.getByteValueUnchecked(object);
    } else {
      throw new IllegalArgumentException("field type mismatch");
    }
  }

  private float getFloatInternal(Object object) throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (type.isFloatType()) {
      return field.getFloatValueUnchecked(object);
    } else if (type.isLongType()) {
      return (float)field.getLongValueUnchecked(object);
    } else if (type.isIntType()) {
      return (float)field.getIntValueUnchecked(object);
    } else if (type.isShortType()) {
      return (float)field.getShortValueUnchecked(object);
    } else if (type.isCharType()) {
      return (float)field.getCharValueUnchecked(object);
    } else if (type.isByteType()) {
      return (float)field.getByteValueUnchecked(object);
    } else {
      throw new IllegalArgumentException("field type mismatch");
    }
  }

  private int getIntInternal(Object object) throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (type.isIntType()) {
      return field.getIntValueUnchecked(object);
    } else if (type.isShortType()) {
      return (int)field.getShortValueUnchecked(object);
    } else if (type.isCharType()) {
      return (int)field.getCharValueUnchecked(object);
    } else if (type.isByteType()) {
      return (int)field.getByteValueUnchecked(object);
    } else {
      throw new IllegalArgumentException("field type mismatch");
    }
  }

  private long getLongInternal(Object object) throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (type.isLongType()) {
      return field.getLongValueUnchecked(object);
    } else if (type.isIntType()) {
      return (long)field.getIntValueUnchecked(object);
    } else if (type.isShortType()) {
      return (long)field.getShortValueUnchecked(object);
    } else if (type.isCharType()) {
      return (long)field.getCharValueUnchecked(object);
    } else if (type.isByteType()) {
      return (long)field.getByteValueUnchecked(object);
    } else {
      throw new IllegalArgumentException("field type mismatch");
    }
  }

  private short getShortInternal(Object object) throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (type.isShortType()) {
      return field.getShortValueUnchecked(object);
    } else if (type.isByteType()) {
      return (short)field.getByteValueUnchecked(object);
    } else {
      throw new IllegalArgumentException("field type mismatch");
    }
  }

  private void setBooleanInternal(Object object, boolean value)
    throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (type.isBooleanType())
      field.setBooleanValueUnchecked(object, value);
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  private void setByteInternal(Object object, byte value) throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (type.isByteType())
      field.setByteValueUnchecked(object, value);
    else if (type.isLongType())
      field.setLongValueUnchecked(object, (long)value);
    else if (type.isIntType())
      field.setIntValueUnchecked(object, (int)value);
    else if (type.isShortType())
      field.setShortValueUnchecked(object, (short)value);
    else if (type.isCharType())
      field.setCharValueUnchecked(object, (char)value);
    else if (type.isDoubleType())
      field.setDoubleValueUnchecked(object, (double)value);
    else if (type.isFloatType())
      field.setFloatValueUnchecked(object, (float)value);
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  private void setCharInternal(Object object, char value) throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (type.isCharType())
      field.setCharValueUnchecked(object, value);
    else if (type.isLongType())
      field.setLongValueUnchecked(object, (long)value);
    else if (type.isIntType())
      field.setIntValueUnchecked(object, (int)value);
    else if (type.isShortType())
      field.setShortValueUnchecked(object, (short)value);
    else if (type.isDoubleType())
      field.setDoubleValueUnchecked(object, (double)value);
    else if (type.isFloatType())
      field.setFloatValueUnchecked(object, (float)value);
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  private void setDoubleInternal(Object object, double value) throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (type.isDoubleType())
      field.setDoubleValueUnchecked(object, value);
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  private void setFloatInternal(Object object, float value) throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (type.isFloatType())
      field.setFloatValueUnchecked(object, value);
    else if (type.isDoubleType())
      field.setDoubleValueUnchecked(object, (double)value);
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  private void setIntInternal(Object object, int value) throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (type.isIntType())
      field.setIntValueUnchecked(object, value);
    else if (type.isLongType())
      field.setLongValueUnchecked(object, (long) value);
    else if (type.isDoubleType())
      field.setDoubleValueUnchecked(object, (double)value);
    else if (type.isFloatType())
      field.setFloatValueUnchecked(object, (float)value);
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  private void setLongInternal(Object object, long value) throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (type.isLongType())
      field.setLongValueUnchecked(object, value);
    else if (type.isDoubleType())
      field.setDoubleValueUnchecked(object, (double)value);
    else if (type.isFloatType())
      field.setFloatValueUnchecked(object, (float)value);
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  private void setShortInternal(Object object, short value) throws IllegalArgumentException {
    VM_TypeReference type = field.getType();
    if (type.isShortType())
      field.setShortValueUnchecked(object, value);
    else if (type.isLongType())
      field.setLongValueUnchecked(object, (long)value);
    else if (type.isIntType())
      field.setIntValueUnchecked(object, (int)value);
    else if (type.isDoubleType())
      field.setDoubleValueUnchecked(object, (double)value);
    else if (type.isFloatType())
      field.setFloatValueUnchecked(object, (float)value);
    else
      throw new IllegalArgumentException("field type mismatch");
  }

  // AnnotatedElement interface

  Annotation[] getDeclaredAnnotations() {
    return field.getDeclaredAnnotations();
  }

  <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return field.getAnnotation(annotationClass);
  }

  String getSignature() {
    return field.getSignature().toString();
  }

}
