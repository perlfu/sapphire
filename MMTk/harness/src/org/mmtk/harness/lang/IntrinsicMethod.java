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
package org.mmtk.harness.lang;

import java.util.List;

import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.vm.Collection;
import org.mmtk.vm.VM;

/**
 * A method that is implemented directly in Java rather than in the scripting language.
 *
 * This class also contains definitions of the built-in intrinsic methods.
 */
public class IntrinsicMethod extends Method {

  /** The actual Java method */
  private final java.lang.reflect.Method method;
  /** The return type, in scripting language terms */
  private final Type returnType;
  /** The Java signature of the method */
  private final Class<?>[] signature;

  /************************************************************************
   *                helper methods for the constructors
   *
   */

  /**
   * Find the appropriate scripting language type for a given Java type
   *
   * @param externalType The java type
   * @return
   */
  private static Type internalType(Class<?> externalType) {
    if (externalType.equals(int.class) ||
        externalType.equals(Integer.class) ||
        externalType.equals(IntValue.class)) {
      return Type.INT;
    } else if (externalType.equals(String.class) ||
        externalType.equals(StringValue.class)) {
      return Type.STRING;
    } else if (externalType.equals(ObjectValue.class)) {
      return Type.OBJECT;
    } else if (externalType.equals(boolean.class) ||
        externalType.equals(Boolean.class) ||
        externalType.equals(BoolValue.class)) {
      return Type.BOOLEAN;
    }
    return null;
  }

  /**
   * Do a reflective method lookup.  We look for a method with a first parameter
   * of 'Env env', which is hidden from the script-language specification of the
   * method.
   *
   * @param className
   * @param methodName
   * @param signature
   * @return
   */
  private static java.lang.reflect.Method getJavaMethod(String className,
      String methodName, Class<?>[] signature) {
    try {
      Class<?> klass = Class.forName(className);
      Class<?>[] realSignature = new Class<?>[signature.length+1];
      realSignature[0] = Env.class;
      System.arraycopy(signature, 0, realSignature, 1, signature.length);
      return klass.getDeclaredMethod(methodName, realSignature);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Turn a string into a class object, applying some simple mappings for
   * primitives etc.
   * @param param
   * @return
   */
  private static Class<?> classForParam(String param) {
    Class<?> r;
    if (param.equals("int")) {
      r = int.class;
    } else if (param.equals("long")) {
      r = long.class;
    } else if (param.equals("byte")) {
      r = byte.class;
    } else if (param.equals("short")) {
      r = short.class;
    } else if (param.equals("char")) {
      r = char.class;
    } else if (param.equals("boolean")) {
      r = boolean.class;
    } else try {
      r = Class.forName(param);
    } catch (ClassNotFoundException e) {
      // As a last chance, try looking for the class in java.lang
      try {
        r = Class.forName("java.lang."+param);
      } catch (ClassNotFoundException f) {
        throw new RuntimeException(e);
      }
    }
    return r;
  }

  /**
   * Turn an array of strings (class names) into an array of Class objects
   * @param params
   * @return
   */
  private static Class<?>[] classesForParams(List<String> params) {
    Class<?>[] result = new Class<?>[params.size()];
    for (int i=0; i < params.size(); i++) {
      result[i] = classForParam(params.get(i));
    }
    return result;
  }


  /************************************************************************
   *
   *              Constructors
   *
   */

  /**
   * Constructor
   */
  public IntrinsicMethod(String name, String className, String methodName, List<String> params) {
    this(name,className, methodName,classesForParams(params));
  }

  /**
   * Create an intrinsic that calls a static method.
   * @param name Script language name of the method
   * @param className Java class in which the intrinsic occurs
   * @param methodName Java name of the method
   * @param signature Java types of the parameters, excluding the mandatory Env first parameter
   */
  public IntrinsicMethod(String name, String className, String methodName, Class<?>[] signature) {
    this(name,getJavaMethod(className, methodName,signature),signature);
  }

  /**
   * Create an intrinsic that calls a static method with no parameters
   *
   * @param name Script language name of the method
   * @param className Java class in which the intrinsic occurs
   * @param methodName Java name of the method
   */
  public IntrinsicMethod(String name, String className, String methodName) {
    this(name,className,methodName,new Class<?>[] {});
  }

  /**
   * Internal 'helper' constructor
   * @param name Script-language name of the method
   * @param method Java Method
   * @param signature Java signature (without the mandatory Env parameter)
   */
  private IntrinsicMethod(String name, java.lang.reflect.Method method, Class<?>[] signature) {
    this(name,method,internalType(method.getReturnType()),signature);
  }


  /**
   * Internal constructor - ultimately all constructors call this one.
   * @param name Script-language name of the method
   * @param method Java Method
   * @param returnType Script-language return type
   * @param signature Java signature (without the mandatory Env parameter)
   */
  private IntrinsicMethod(String name, java.lang.reflect.Method method, Type returnType, Class<?>[] signature) {
    super(name,signature.length);
    this.method = method;
    this.returnType = returnType;
    this.signature = signature;
  }

  /**
   * Take a script-language value and return the corresponding Java value.
   * This can only be done for a limited number of types ...
   *
   * @param v The script-language value
   * @param klass The Java target type
   * @return An object of type Class<klass>.
   */
  private Object marshall(Value v, Class<?> klass) {
    switch (v.type()) {
      case INT:
        if (klass.isAssignableFrom(IntValue.class)) {
          return v;
        } else {
          return new Integer(v.getIntValue());
        }
      case BOOLEAN:
        if (klass.isAssignableFrom(BoolValue.class)) {
          return v;
        } else {
          return new Boolean(v.getBoolValue());
        }
      case STRING:
        if (klass.isAssignableFrom(StringValue.class)) {
          return v;
        } else {
          return v.getStringValue();
        }
      case OBJECT:
        if (klass.isAssignableFrom(ObjectValue.class)) {
          return v;
        } else {
          throw new RuntimeException("Can't marshall an object into a Java Object");
        }
      default:
        throw new RuntimeException("Unknown type in intrinsic call");
    }
  }

  /**
   * Marshall an array of values, adding the mandatory Env value.
   * @param env
   * @param params
   * @return
   */
  private Object[] marshall(Env env, Value[] params) {
    assert params.length == signature.length : "Signature doesn't match params";
    Object[] marshalled = new Object[params.length+1];
    marshalled[0] = env;
    for (int i=0; i < params.length; i++) {
      marshalled[i+1] = marshall(params[i], signature[i]);
    }
    return marshalled;
  }

  /**
   * Convert a return type from a Java type to a scripting language Value.
   * @param obj
   * @return
   */
  private Value unMarshall(Object obj) {
    if (obj instanceof Integer) {
      assert returnType == Type.INT : "mismatched return types";
      return new IntValue(((Integer)obj).intValue());
    } else if (obj instanceof String) {
      assert returnType == Type.STRING : "mismatched return types";
      return new StringValue((String)obj);
    } else if (obj instanceof Boolean) {
      assert returnType == Type.BOOLEAN : "mismatched return types";
      return new BoolValue(((Boolean)obj).booleanValue());
    }
    throw new RuntimeException("Can't unmarshall a "+obj.getClass().getCanonicalName());
  }

  /**
   * Invoke the intrinsic method.
   * @param env
   * @param values
   * @return
   */
  private Object invoke(Env env, Value[] values) {
    Trace.trace(Item.INTRINSIC,"Executing "+toString());
    try {
      Object result = method.invoke(null, marshall(env,values));
      return result;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Evaluate the method as an expression
   * @see Method{@link #eval(Env, Value...)}
   */
  @Override
  public Value eval(Env env, Value...values) {
    return unMarshall(invoke(env,values));
  }

  /**
   * Execute the method as a statement
   * @see Method{@link #exec(Env, Value...)
   */
  @Override
  public void exec(Env env, Value...values) {
    invoke(env,values);
  }

  /**
   * Convert to a string
   */
  public String toString() {
    return method.getName();
  }

  /**************************************************************************
   *
   * "built in" intrinsic functions
   *
   */

  /**
   * Force GC
   * @param env
   * @return
   */
  public static void gc(Env env) {
    VM.collection.triggerCollection(Collection.EXTERNAL_GC_TRIGGER);
  }

  /**
   * Return the thread ID
   * @param env
   * @return
   */
  public static int threadId(Env env) {
    return Mutator.current().getContext().getId();
  }

  /**
   * Return the (identity) hash code
   * @param env
   * @param val
   * @return
   */
  public static int hash(Env env, ObjectValue val) {
    return env.hash(val.getObjectValue());
  }

  /**
   * Set the random seed for this thread
   * @param env
   * @param seed
   */
  public static void setRandomSeed(Env env, int seed) {
    env.random().setSeed(seed);
  }

  /**
   * A random integer in the closed interval [low..high].
   * @param env
   * @param low
   * @param high
   * @return
   */
  public static int random(Env env, int low, int high) {
    return (int)(env.random().nextInt(high-low+1) + low);
  }

  /**
   * Dump the heap
   */
  public static void heapDump(Env env) {
    Mutator.dumpHeap();
  }

  /**
   * Unit test method for the Intrinsic method
   */
  public static String testMethod(Env env, int x, boolean y, String string, ObjectValue val) {
    return String.format("successfully called testMethod(%d,%b,%s,%s)", x,y,string,val.toString());
  }
}
