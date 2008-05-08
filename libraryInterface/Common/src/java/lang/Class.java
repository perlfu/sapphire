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
package java.lang;

import java.io.InputStream;
import java.io.Serializable;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.AllPermission;
import java.security.Permissions;

import java.lang.annotation.Annotation;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.JikesRVMSupport;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import java.net.URL;
import java.util.ArrayList;

import org.jikesrvm.classloader.*;

import org.jikesrvm.VM_Callbacks;
import org.jikesrvm.runtime.VM_Reflection;
import org.jikesrvm.runtime.VM_Runtime;
import org.jikesrvm.VM_UnimplementedError;

/**
 * Implementation of java.lang.Class for JikesRVM.
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API.
 */
public final class Class<T> implements Serializable, Type, AnnotatedElement, GenericDeclaration {
  private static final class StaticData {
    static final ProtectionDomain unknownProtectionDomain;

    static {
      Permissions permissions = new Permissions();
      permissions.add(new AllPermission());
      unknownProtectionDomain = new ProtectionDomain(null, permissions);
    }
  }

  static final long serialVersionUID = 3206093459760846163L;

  /**
   * Prevents this class from being instantiated, except by the
   * create method in this class.
   */
  private Class() {}

  /**
   * This field holds the VM_Type object for this class.
   */
  VM_Type type;

  /**
   * This field holds the protection domain of this class.
   */
  ProtectionDomain pd;

  /**
   * The signers of this class
   */
  Object[] signers;

  public boolean desiredAssertionStatus() {
    return type.getDesiredAssertionStatus();
  }

  public static Class<?> forName(String typeName) throws ClassNotFoundException {
    ClassLoader parentCL = VM_Class.getClassLoaderFromStackFrame(1);
    return forNameInternal(typeName, true, parentCL);
  }

  public static Class<?> forName(String className,
                              boolean initialize,
                              ClassLoader classLoader)
    throws ClassNotFoundException,
           LinkageError,
           ExceptionInInitializerError {
    if (classLoader == null) {
      SecurityManager security = System.getSecurityManager();
      if (security != null) {
        ClassLoader parentCL = VM_Class.getClassLoaderFromStackFrame(1);
        if (parentCL != null) {
          try {
            security.checkPermission(new RuntimePermission("getClassLoader"));
          } catch (SecurityException e) {
            throw new ClassNotFoundException("Security exception when" +
                                             " trying to get a classloader so we can load the" +
                                             " class named \"" + className +"\"", e);
          }
        }
      }
      classLoader = VM_BootstrapClassLoader.getBootstrapClassLoader();
    }
    return forNameInternal(className, initialize, classLoader);
  }

  public Class<?>[] getClasses() throws SecurityException {
    checkMemberAccess(Member.PUBLIC);
    if (!type.isClassType()) return new Class[0];

    ArrayList<Class<?>> publicClasses = new ArrayList<Class<?>>();
    for (Class<?> c = this; c != null; c = c.getSuperclass()) {
      c.checkMemberAccess(Member.PUBLIC);
      VM_TypeReference[] declaredClasses = c.type.asClass().getDeclaredClasses();
      if (declaredClasses != null) {
        for (VM_TypeReference declaredClass : declaredClasses) {
          if (declaredClass != null) {
            VM_Class dc = declaredClass.resolve().asClass();
            if (dc.isPublic()) {
              publicClasses.add(dc.getClassForType());
            }
          }
        }
      }
    }
    Class<?>[] result = new Class[publicClasses.size()];
    result = publicClasses.toArray(result);
    return result;
  }

  public ClassLoader getClassLoader() {
    SecurityManager security = System.getSecurityManager();
    if (security != null) {
      ClassLoader parentCL = VM_Class.getClassLoaderFromStackFrame(1);
      if (parentCL != null) {
        security.checkPermission(new RuntimePermission("getClassLoader"));
      }
    }
    ClassLoader cl = type.getClassLoader();
    return cl == VM_BootstrapClassLoader.getBootstrapClassLoader() ? null : cl;
  }

  public Class<?> getComponentType() {
    return type.isArrayType() ? type.asArray().getElementType().getClassForType(): null;
  }

  public Constructor<T> getConstructor(Class<?>[] parameterTypes)
    throws NoSuchMethodException, SecurityException {

    checkMemberAccess(Member.PUBLIC);
    if (!type.isClassType()) throw new NoSuchMethodException();

    VM_Method[] methods = type.asClass().getConstructorMethods();
    for (VM_Method method : methods) {
      if (method.isPublic() &&
          parametersMatch(method.getParameterTypes(), parameterTypes)) {
        return JikesRVMSupport.createConstructor(method);
      }
    }

    throw new NoSuchMethodException("<init> " + parameterTypes);
  }

  public Constructor<?>[] getConstructors() throws SecurityException {
    checkMemberAccess(Member.PUBLIC);
    if (!type.isClassType()) return new Constructor[0];

    VM_Method[] methods = type.asClass().getConstructorMethods();
    Collector coll = new Collector(methods.length);
    for (VM_Method method : methods) {
      if (method.isPublic()) {
        coll.collect(JikesRVMSupport.createConstructor(method));
      }
    }
    return coll.constructorArray();
  }

  public Class<?>[] getDeclaredClasses() throws SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) return new Class[0];

    // Get array of declared classes from VM_Class object
    VM_Class cls = type.asClass();
    VM_TypeReference[] declaredClasses = cls.getDeclaredClasses();

    // The array can be null if the class has no declared inner class members
    if (declaredClasses == null)
      return new Class[0];

    // Count the number of actual declared inner and static classes.
    // (The array may contain null elements, which we want to skip.)
    int count = 0;
    int length = declaredClasses.length;
    for (int i = 0; i < length; ++i) {
      if (declaredClasses[i] != null) {
        ++count;
      }
    }

    // Now build actual result array.
    Class<?>[] result = new Class[count];
    count = 0;
    for (int i = 0; i < length; ++i) {
      if (declaredClasses[i] != null) {
        result[count++] = declaredClasses[i].resolve().getClassForType();
      }
    }

    return result;
  }

  public Constructor<T> getDeclaredConstructor(Class<?>[] parameterTypes)
    throws NoSuchMethodException, SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) throw new NoSuchMethodException();

    VM_Method[] methods = type.asClass().getConstructorMethods();
    for (int i = 0, n = methods.length; i < n; ++i) {
      VM_Method method = methods[i];
      if (parametersMatch(method.getParameterTypes(), parameterTypes)) {
        return JikesRVMSupport.createConstructor(method);
      }
    }

    throw new NoSuchMethodException("<init> " + parameterTypes);
  }

  public Constructor<?>[] getDeclaredConstructors() throws SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) return new Constructor[0];

    VM_Method[] methods = type.asClass().getConstructorMethods();
    Constructor<?>[] ans = new Constructor[methods.length];
    for (int i = 0; i<methods.length; i++) {
      ans[i] = JikesRVMSupport.createConstructor(methods[i]);
    }
    return ans;
  }

  public Field getDeclaredField(String name)
    throws NoSuchFieldException, SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) throw new NoSuchFieldException();

    VM_Atom aName = VM_Atom.findUnicodeAtom(name);
    if (aName == null) throw new NoSuchFieldException(name);
    VM_Field[] fields = type.asClass().getDeclaredFields();
    for (VM_Field field : fields) {
      if (field.getName() == aName) {
        return JikesRVMSupport.createField(field);
      }
    }

    throw new NoSuchFieldException(name);
  }

  public Field[] getDeclaredFields() throws SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) return new Field[0];

    VM_Field[] fields = type.asClass().getDeclaredFields();
    Field[] ans = new Field[fields.length];
    for (int i = 0; i < fields.length; i++) {
      ans[i] = JikesRVMSupport.createField(fields[i]);
    }
    return ans;
  }

  public Method getDeclaredMethod(String name, Class<?>[] parameterTypes)
    throws NoSuchMethodException, SecurityException {
    checkMemberAccess(Member.DECLARED);

    if (!type.isClassType())  {
      throw new NoSuchMethodException(name + parameterTypes);
    }

    VM_Atom aName = VM_Atom.findUnicodeAtom(name);
    if (aName == null ||
        aName == VM_ClassLoader.StandardClassInitializerMethodName ||
        aName == VM_ClassLoader.StandardObjectInitializerMethodName) {
        // null means that we don't have such an atom;
        // <init> and <clinit> are not methods.
        throw new NoSuchMethodException(name + parameterTypes);
      }

    VM_Method[] methods = type.asClass().getDeclaredMethods();
    Method answer = null;
    for (VM_Method meth : methods) {
      if (meth.getName() == aName &&
          parametersMatch(meth.getParameterTypes(), parameterTypes)) {
        if (answer == null) {
          answer = JikesRVMSupport.createMethod(meth);
        } else {
          Method m2 = JikesRVMSupport.createMethod(meth);
          if (answer.getReturnType().isAssignableFrom(m2.getReturnType())) {
            answer = m2;
          }
        }
      }
    }

    if (answer != null) return answer;

    throw new NoSuchMethodException(name + parameterTypes);
  }

  public Method[] getDeclaredMethods() throws SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) return new Method[0];

    VM_Method[] methods = type.asClass().getDeclaredMethods();
    Collector coll = new Collector(methods.length);
    for (VM_Method meth : methods) {
      if (!meth.isClassInitializer() && !meth.isObjectInitializer()) {
        coll.collect(JikesRVMSupport.createMethod(meth));
      }
    }
    return coll.methodArray();
  }

  public Class<?> getDeclaringClass() {
    if (!type.isClassType()) return null;
    VM_TypeReference dc = type.asClass().getDeclaringClass();
    if (dc == null) return null;
    return dc.resolve().getClassForType();
  }

  public Field getField(String name) throws NoSuchFieldException, SecurityException {
    checkMemberAccess(Member.PUBLIC);
    if (!type.isClassType()) throw new NoSuchFieldException();

    VM_Atom aName = VM_Atom.findUnicodeAtom(name);
    if (aName == null) throw new NoSuchFieldException(name);

    Field ans = getFieldInternal(aName);

    if (ans == null) {
      throw new NoSuchFieldException(name);
    } else {
      return ans;
    }
  }

  public Field[] getFields() throws SecurityException {
    checkMemberAccess(Member.PUBLIC);

    VM_Field[] static_fields = type.getStaticFields();
    VM_Field[] instance_fields = type.getInstanceFields();
    Collector coll = new Collector(static_fields.length + instance_fields.length);
    for (VM_Field field : static_fields) {
      if (field.isPublic()) {
        coll.collect(JikesRVMSupport.createField(field));
      }
    }
    for (VM_Field field : instance_fields) {
      if (field.isPublic()) {
        coll.collect(JikesRVMSupport.createField(field));
      }
    }

    return coll.fieldArray();
  }

  public Class<?>[] getInterfaces() {
    if (type.isArrayType()) {
      // arrays implement JavaLangSerializable & JavaLangCloneable
      return new Class[] { VM_Type.JavaLangCloneableType.getClassForType(),
                           VM_Type.JavaIoSerializableType.getClassForType() };
    } else if (type.isClassType()) {
      VM_Class[] interfaces  = type.asClass().getDeclaredInterfaces();
      Class<?>[]    jinterfaces = new Class[interfaces.length];
      for (int i = 0; i != interfaces.length; i++)
        jinterfaces[i] = interfaces[i].getClassForType();
      return jinterfaces;
    } else {
      return new Class[0];
    }
  }

  public Method getMethod(String name, Class<?>[] parameterTypes) throws NoSuchMethodException, SecurityException {
    checkMemberAccess(Member.PUBLIC);

    if (!type.isClassType()) throw new NoSuchMethodException(name + parameterTypes);

    VM_Atom aName = VM_Atom.findUnicodeAtom(name);
    if (aName == null ||
        aName == VM_ClassLoader.StandardClassInitializerMethodName ||
        aName == VM_ClassLoader.StandardObjectInitializerMethodName) {
      // null means that we don't have such an atom; <init> and <clinit> are not methods.
      throw new NoSuchMethodException(name + parameterTypes);
    }

    // (1) Scan the declared public methods of this class and each of its superclasses
    for (VM_Class current = type.asClass(); current != null; current = current.getSuperClass()) {
      VM_Method[] methods = current.getDeclaredMethods();
      Method answer = null;
      for (VM_Method meth : methods) {
        if (meth.getName() == aName && meth.isPublic() &&
            parametersMatch(meth.getParameterTypes(), parameterTypes)) {
          if (answer == null) {
            answer = JikesRVMSupport.createMethod(meth);
          } else {
            Method m2 = JikesRVMSupport.createMethod(meth);
            if (answer.getReturnType().isAssignableFrom(m2.getReturnType())) {
              answer = m2;
            }
          }
        }
      }
      if (answer != null) return answer;
    }

    // (2) Now we need to consider methods inherited from interfaces.
    //     Because we inject the requisite Miranda methods, we can do this simply
    //     by looking at this class's virtual methods instead of searching interface hierarchies.
    VM_Method[] methods = type.asClass().getVirtualMethods();
    Method answer = null;
    for (VM_Method meth : methods) {
      if (meth.getName() == aName && meth.isPublic() &&
          parametersMatch(meth.getParameterTypes(), parameterTypes)) {
        if (answer == null) {
          answer = JikesRVMSupport.createMethod(meth);
        } else {
          Method m2 = JikesRVMSupport.createMethod(meth);
          if (answer.getReturnType().isAssignableFrom(m2.getReturnType())) {
            answer = m2;
          }
        }
      }
    }

    if (answer != null) return answer;

    throw new NoSuchMethodException(name + parameterTypes);
  }

  public Method[] getMethods() throws SecurityException {
    checkMemberAccess(Member.PUBLIC);

    VM_Method[] static_methods = type.getStaticMethods();
    VM_Method[] virtual_methods = type.getVirtualMethods();
    Collector coll = new Collector(static_methods.length + virtual_methods.length);
    for (VM_Method meth : static_methods) {
      if (meth.isPublic()) {
        coll.collect(JikesRVMSupport.createMethod(meth));
      }
    }
    for (VM_Method meth : virtual_methods) {
      if (meth.isPublic()) {
        coll.collect(JikesRVMSupport.createMethod(meth));
      }
    }
    return coll.methodArray();
  }

  public int getModifiers() {
    if (type.isClassType()) {
      return type.asClass().getModifiers();
    } else if (type.isArrayType()) {
      VM_Type innermostElementType = type.asArray().getInnermostElementType();
      int result = Modifier.FINAL;
      if (innermostElementType.isClassType()) {
        int component = innermostElementType.asClass().getModifiers();
        result |= (component & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE));
      } else {
        result |= Modifier.PUBLIC; // primitive
      }
      return result;
    } else {
      return Modifier.PUBLIC | Modifier.FINAL;
    }
  }

  public String getName() {
    return type.toString();
  }

  public Package getPackage() {
    ClassLoader cl = type.getClassLoader();
    return cl.getPackage(getPackageName());
  }

  public ProtectionDomain getProtectionDomain() {
    SecurityManager security = System.getSecurityManager();
    if (security != null) {
      security.checkPermission(new RuntimePermission("getProtectionDomain"));
    }
    return pd == null ? StaticData.unknownProtectionDomain : pd;
  }

  public URL getResource(String resName) {
    ClassLoader loader = type.getClassLoader();
    if (loader == VM_BootstrapClassLoader.getBootstrapClassLoader())
      return ClassLoader.getSystemResource(toResourceName(resName));
    else
      return loader.getResource(toResourceName(resName));
  }

  public InputStream getResourceAsStream(String resName) {
    ClassLoader loader = type.getClassLoader();
    if (loader == VM_BootstrapClassLoader.getBootstrapClassLoader())
      return ClassLoader.getSystemResourceAsStream(toResourceName(resName));
    else
      return loader.getResourceAsStream(toResourceName(resName));
  }

  public Object[] getSigners() {
    if (signers == null) {
      return null;
    } else {
      return signers.clone();
    }
  }

  @SuppressWarnings("unchecked")
  public Class<? super T> getSuperclass() {
    if (type.isArrayType()) {
      return Object.class;
    } else if (type.isClassType()) {
      VM_Class myClass = type.asClass();
      if (myClass.isInterface()) return null;
      VM_Type supe = myClass.getSuperClass();
      return supe == null ? null : (Class<? super T>) supe.getClassForType();
    } else {
      return null;
    }
  }

  public boolean isAnnotation() {
    return type.isClassType() && type.asClass().isAnnotation();
  }

  public boolean isArray() {
    return type.isArrayType();
  }

  public boolean isAssignableFrom(Class<?> cls) {
    return type == cls.type || VM_Runtime.isAssignableWith(type, cls.type);
  }

  public boolean isInstance(Object object) {
    if (object == null) return false;
    if (isPrimitive())  return false;
    return isAssignableFrom(object.getClass());
  }

  public boolean isInterface() {
    return type.isClassType() && type.asClass().isInterface();
  }

  public boolean isPrimitive() {
    return type.isPrimitiveType();
  }

  public boolean isSynthetic() {
    return type.isClassType() && type.asClass().isSynthetic();
  }

  public T newInstance() throws IllegalAccessException,
                                     InstantiationException,
                                     ExceptionInInitializerError,
                                     SecurityException {
    // Basic checks
    checkMemberAccess(Member.PUBLIC);
    if (!type.isClassType())
      throw new InstantiationException();

    VM_Class cls = type.asClass();

    if (cls.isAbstract() || cls.isInterface())
      throw new InstantiationException();

    if (!cls.isInitialized()) {
      cls.resolve();
      cls.instantiate();
      cls.initialize();
    }

    // Find the defaultConstructor
    VM_Method defaultConstructor = null;
    VM_Method[] methods = type.asClass().getConstructorMethods();
    for (VM_Method method : methods) {
      if (method.getParameterTypes().length == 0) {
        defaultConstructor = method;
        break;
      }
    }
    if (defaultConstructor == null)
      throw new InstantiationException();

    // Check that caller is allowed to access it
    if (!defaultConstructor.isPublic()) {
      VM_Class accessingClass = VM_Class.getClassFromStackFrame(1);
      JikesRVMSupport.checkAccess(defaultConstructor, accessingClass);
    }

    // Ensure that the class is initialized
    if (!cls.isInitialized()) {
      try {
        VM_Runtime.initializeClassForDynamicLink(cls);
      } catch (Throwable e) {
        ExceptionInInitializerError ex = new ExceptionInInitializerError();
        ex.initCause(e);
        throw ex;
      }
    }

    // Allocate an uninitialized instance;
    @SuppressWarnings("unchecked") // yes, we're giving an anonymous object a type.
    T obj = (T)VM_Runtime.resolvedNewScalar(cls);

    // Run the default constructor on the it.
    VM_Reflection.invoke(defaultConstructor, obj, null);

    return obj;
  }

  public String toString() {
    String name = getName();
    if (isPrimitive()) {
      return name;
    } else if (isInterface()) {
      return "interface "+name;
    } else {
      return "class "+name;
    }
  }

  /*
   * Implementation below
   */

  /**
   * Create a java.lang.Class corresponding to a given VM_Type
   */
  static <T> Class<T> create(VM_Type type) {
    Class<T> c = new Class<T>();
    c.type = type;
    return c;
  }

  void setSigners(Object[] signers) {
    this.signers = signers;
  }

  private static Class<?> forNameInternal(String className,
                                       boolean initialize,
                                       ClassLoader classLoader)
    throws ClassNotFoundException,
           LinkageError,
           ExceptionInInitializerError {
    try {
      if (className.startsWith("[")) {
        if (!validArrayDescriptor(className)) {
          throw new ClassNotFoundException(className);
        }
      }
      VM_Atom descriptor = VM_Atom
        .findOrCreateAsciiAtom(className.replace('.','/'))
        .descriptorFromClassName();
      VM_TypeReference tRef = VM_TypeReference.findOrCreate(classLoader, descriptor);
      VM_Type ans = tRef.resolve();
      VM_Callbacks.notifyForName(ans);
      if (initialize && !ans.isInitialized()) {
        ans.resolve();
        ans.instantiate();
        ans.initialize();
      }
      return ans.getClassForType();
    } catch (NoClassDefFoundError ncdfe) {
      Throwable cause2 = ncdfe.getCause();
      ClassNotFoundException cnf;
      // If we get a NCDFE that was caused by a CNFE, throw the original CNFE.
      if (cause2 instanceof ClassNotFoundException)
        cnf = (ClassNotFoundException) cause2;
      else
        cnf = new ClassNotFoundException(className, ncdfe);
      throw cnf;
    }
  }


  private Field getFieldInternal(VM_Atom name) {
    VM_Class ctype = type.asClass();
    // (1) Check my public declared fields
    VM_Field[] fields = ctype.getDeclaredFields();
    for (VM_Field field : fields) {
      if (field.isPublic() && field.getName() == name) {
        return JikesRVMSupport.createField(field);
      }
    }

    // (2) Check superinterfaces
    VM_Class[] interfaces = ctype.getDeclaredInterfaces();
    for (VM_Class anInterface : interfaces) {
      Field ans = anInterface.getClassForType().getFieldInternal(name);
      if (ans != null) return ans;
    }

    // (3) Check superclass (if I have one).
    if (ctype.getSuperClass() != null) {
      return ctype.getSuperClass().getClassForType().getFieldInternal(name);
    }

    return null;
  }


  private String getPackageName() {
    String name = getName();
    int index = name.lastIndexOf('.');
    if (index >= 0) return name.substring(0, index);
    return "";
  }

  private String toResourceName(String resName) {
    // Turn package name into a directory path
    if (resName.charAt(0) == '/') return resName.substring(1);

    String qualifiedClassName = getName();
    int classIndex = qualifiedClassName.lastIndexOf('.');
    if (classIndex == -1) return resName; // from a default package
    return qualifiedClassName.substring(0, classIndex + 1).replace('.', '/') + resName;
  }

  private static boolean validArrayDescriptor(String name) {
    int i;
    int length = name.length();

    for (i = 0; i < length; i++)
      if (name.charAt(i) != '[') break;
    if (i == length) return false;      // string of only ['s

    if (i == length - 1) {
      switch (name.charAt(i)) {
      case 'B': return true;    // byte
      case 'C': return true;    // char
      case 'D': return true;    // double
      case 'F': return true;    // float
      case 'I': return true;    // integer
      case 'J': return true;    // long
      case 'S': return true;    // short
      case 'Z': return true;    // boolean
      default:  return false;
      }
    } else if (name.charAt(i) != 'L') {
      return false;    // not a class descriptor
    } else if (name.charAt(length - 1) != ';') {
      return false;     // ditto
    }
    return true;                        // a valid class descriptor
  }

  /**
   * Utility for security checks
   */
  private void checkMemberAccess(int type) {
    SecurityManager security = System.getSecurityManager();
    if (security != null) {
      security.checkMemberAccess(this, type);
      String packageName = getPackageName();
      if(packageName != "") {
        security.checkPackageAccess(packageName);
      }
    }
  }

  /**
   * Compare parameter lists for agreement.
   */
  private boolean parametersMatch(VM_TypeReference[] lhs, Class<?>[] rhs) {
    if (rhs == null) return lhs.length == 0;
    if (lhs.length != rhs.length) return false;

    for (int i = 0, n = lhs.length; i < n; ++i) {
      if (rhs[i] == null) return false;
      if (lhs[i].resolve() != rhs[i].type) {
        return false;
      }
    }
    return true;
  }

  // aux class to build up collections of things
  private static final class Collector {
    private int n = 0;
    private final Object[] coll;

    Collector(int max) {
      coll = new Object[max];
    }

    void collect(Object thing) {
      coll[n++] = thing;
    }

    // repeat for each class of interest
    //
    Method[] methodArray() {
      Method[] ans = new Method[n];
      System.arraycopy(coll, 0, ans, 0, n);
      return ans;
    }

    Field[] fieldArray() {
      Field[] ans = new Field[n];
      System.arraycopy(coll, 0, ans, 0, n);
      return ans;
    }

    Constructor<?>[] constructorArray() {
      Constructor<?>[] ans = new Constructor[n];
      System.arraycopy(coll, 0, ans, 0, n);
      return ans;
    }
  }

  public boolean isAnonymousClass() {
    if (type.isClassType()) {
      return type.asClass().isAnonymousClass();
    } else {
      return false;
    }
  }

  public boolean isLocalClass() {
    if (type.isClassType()) {
      return type.asClass().isLocalClass();
    } else {
      return false;
    }
  }

  public boolean isMemberClass() {
    if (type.isClassType()) {
      return type.asClass().isMemberClass();
    } else {
      return false;
    }
  }

  // AnnotatedElement interface

  public Annotation[] getDeclaredAnnotations() {
    return type.getDeclaredAnnotations();
  }

  public Annotation[] getAnnotations() {
    return type.getAnnotations();
  }

  //@Override
  public <U extends Annotation> U getAnnotation(Class<U> annotationClass) {
    return type.getAnnotation(annotationClass);
  }

  //@Override
  public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
    return type.isAnnotationPresent(annotationClass);
  }

  // Generics support

  public Type[] getGenericInterfaces()  {
    if (type.isPrimitiveType()) {
      return new Type[0];
    } else if (type.isArrayType()) {
      // arrays implement JavaLangSerializable & JavaLangCloneable
      return new Class[] { VM_Type.JavaLangCloneableType.getClassForType(),
                           VM_Type.JavaIoSerializableType.getClassForType()};
    } else {
      VM_Class klass = type.asClass();
      VM_Atom sig = klass.getSignature();
      if (sig == null) {
        return getInterfaces();
      } else {
        return JikesRVMHelpers.getInterfaceTypesFromSignature(this, sig);
      }
    }
  }

  public Type getGenericSuperclass() {
    if (type.isArrayType()) {
      return Object.class;
     } else if (type.isPrimitiveType() ||
               (type.isClassType() && type.asClass().isInterface()) ||
               this == Object.class) {
      return null;
     } else {
       VM_Class klass = type.asClass();
       VM_Atom sig = klass.getSignature();
       if (sig == null) {
         return getSuperclass();
       } else {
         return JikesRVMHelpers.getSuperclassType(this, sig);
      }
    }
  }

  public TypeVariable<Class<T>>[] getTypeParameters() {
    if (!type.isClassType()) {
      return new TypeVariable[0];
    } else {
      VM_Class klass = type.asClass();
      VM_Atom sig = klass.getSignature();
      if (sig == null) {
        return new TypeVariable[0];
      } else {
        return JikesRVMHelpers.getTypeParameters(this, sig);
      }
    }
  }

  public String getSimpleName() {
    if (type.isArrayType()) {
      return getComponentType().getSimpleName() + "[]";
    } else {
      String fullName = getName();
      return fullName.substring(fullName.lastIndexOf(".") + 1);
    }
  }

  public String getCanonicalName() {
    if (type.isArrayType()) {
      String componentName = getComponentType().getCanonicalName();
      if (componentName != null)
        return componentName + "[]";
    }
    if (isMemberClass()) {
      String memberName = getDeclaringClass().getCanonicalName();
      if (memberName != null)
        return memberName + "." + getSimpleName();
    }
    if (isLocalClass() || isAnonymousClass())
      return null;
    return getName();
  }

  public Class<?> getEnclosingClass() {
    if (type.isClassType()) {
      VM_TypeReference enclosingClass = type.asClass().getEnclosingClass();
      if(enclosingClass != null) {
        return enclosingClass.resolve().getClassForType();
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  public Constructor<?> getEnclosingConstructor() {
    throw new VM_UnimplementedError();
  }

  public Method getEnclosingMethod() {
    throw new VM_UnimplementedError();
  }

  public T cast(Object obj) {
    if (obj != null && ! isInstance(obj))
      throw new ClassCastException();
    return (T)obj;
  }

  // Enumeration support

  public T[] getEnumConstants() {
    if (isEnum()) {
      try {
        return (T[])getMethod("values", new Class[0]).invoke(null, new Object[0]);
      } catch (NoSuchMethodException exception) {
        throw new Error("Enum lacks values() method");
      } catch (IllegalAccessException exception) {
        throw new Error("Unable to access Enum class");
      } catch (InvocationTargetException exception) {
        throw new RuntimeException("The values method threw an exception",
                                   exception);
      }
    } else {
      return null;
    }
  }

  public boolean isEnum() {
    if(type.isClassType()) {
      return type.asClass().isEnum();
    } else {
      return false;
    }
  }

  /**
   * Utility method for use by classes in this package.
   */
  static void setAccessible(final AccessibleObject obj) {
    AccessController.doPrivileged(new PrivilegedAction<Object>() {
        public Object run() {
            obj.setAccessible(true);
            return null;
          }
      });
  }

  public <U> Class<? extends U> asSubclass(Class<U> klass) {
    if (! klass.isAssignableFrom(this))
      throw new ClassCastException();
    return (Class<? extends U>) this;
  }
}
