/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang.reflect;

import com.ibm.JikesRVM.librarySupport.ReflectionSupport;
import com.ibm.JikesRVM.classloader.VM_Field;
import com.ibm.JikesRVM.classloader.VM_TypeReference;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 *
 */
public final class Field extends AccessibleObject implements Member {

    VM_Field field;

    /**
     * Prevent this class from being instantiated.
     */
    private Field() {}
    
    Field( VM_Field f ) {
	field = f;
    }
    
    public boolean equals(Object object) {
        if ( object == null) return false;
	if(this == object) return true;
	
	if(!(object instanceof Field)) return false;
	
        Field other = (Field) object;
        if ( field != null ) 
	    return field.equals( other.field );
        else 
	    return super.equals( object );
    }

    public Object get(Object object) throws IllegalAccessException, IllegalArgumentException
    {
	// TODO: check for Illegal Access Exception and Illegal Argument Exception
	
	if ((object == null) && (!field.isStatic()))  
	    throw new java.lang.NullPointerException();
	
	return (field.getObject(object));
    }

    public boolean getBoolean(Object object) throws IllegalAccessException, IllegalArgumentException {
	return field.getBooleanValue( object );
    }
    

    public byte getByte(Object object) throws IllegalAccessException, IllegalArgumentException {
	return field.getByteValue( object );
    }

    public char getChar(Object object) throws IllegalAccessException, IllegalArgumentException {
	return field.getCharValue( object );
    }

    public Class getDeclaringClass() {
	return field.getDeclaringClass().getClassForType();
    }

    public double getDouble(Object object) throws IllegalAccessException, IllegalArgumentException {
	return field.getDoubleValue( object );
    }

    public float getFloat(Object object) throws IllegalAccessException, IllegalArgumentException {
	return field.getFloatValue( object );
    }

    public int getInt(Object object) throws IllegalAccessException, IllegalArgumentException {
	return field.getIntValue( object );
    }

    public long getLong(Object object) throws IllegalAccessException, IllegalArgumentException {
	return field.getLongValue( object );
    }

    public int getModifiers() {
	return field.getModifiers();
    }

    public String getName() {
      return field.getName().toString();
    }
    
    public short getShort(Object object) throws IllegalAccessException, IllegalArgumentException {
      return field.getShortValue( object );
    }

    public String getSignature() {
      return field.getDescriptor().toString();
    }

    public Class getType() {
      try {
	return field.getType().resolve().getClassForType();
      } catch (ClassNotFoundException e) {
	throw new InternalError("How can this happen??");
      }
    }

    public int hashCode() {
	return getName().hashCode();
    }

    private void checkWriteAccess(Object obj) 
	throws IllegalAccessException, IllegalArgumentException 
    {
	if (! field.isStatic()) {
	    if (obj == null)
		throw new NullPointerException();

	    if (!field.getDeclaringClass().getClassForType().isInstance(obj))
		throw new IllegalArgumentException();
	}

	if (field.isFinal())
	    throw new IllegalAccessException();
    }

    public void set(Object object, Object value) 
	throws IllegalAccessException, IllegalArgumentException 
    {
	checkWriteAccess(object);
	
	ReflectionSupport.setField(this,object,value);
    }
    
    public void setBoolean(Object object, boolean value) 
	throws IllegalAccessException, IllegalArgumentException
    {
	checkWriteAccess(object);

	field.setBooleanValue( object, value );
    }

    public void setByte(Object object, byte value) 
	throws IllegalAccessException, IllegalArgumentException
    {
	checkWriteAccess(object);

	field.setByteValue( object, value );
    }

    public void setChar(Object object, char value)
	throws IllegalAccessException, IllegalArgumentException
    {
	checkWriteAccess(object);

	field.setCharValue( object, value );
    }

    public void setDouble(Object object, double value)
	throws IllegalAccessException, IllegalArgumentException
    {
	checkWriteAccess(object);

	field.setDoubleValue( object, value );
    }

    public void setFloat(Object object, float value)
	throws IllegalAccessException, IllegalArgumentException
    {
	checkWriteAccess(object);

	field.setFloatValue( object, value );
    }

    public void setInt(Object object, int value)
	throws IllegalAccessException, IllegalArgumentException
    {
	checkWriteAccess(object);

	field.setIntValue( object, value );
    }

    public void setLong(Object object, long value)
	throws IllegalAccessException, IllegalArgumentException
    {
	checkWriteAccess(object);

	field.setLongValue( object, value );
    }

    public void setShort(Object object, short value)
	throws IllegalAccessException, IllegalArgumentException
    {
	checkWriteAccess(object);

	field.setShortValue( object, value );
    }

    public String toString() {
	StringBuffer buf;
	Class current;
	int arity = 0;

	buf = new StringBuffer();
	buf.append(Modifier.toString(getModifiers()));
	buf.append(" ");

	current = getType();
	while(current.isArray())
	{
		current = current.getComponentType();
		arity++;
	}
	buf.append(current.getName());
	for(;arity > 0; arity--) buf.append("[]");

	buf.append(" ");
	buf.append(getDeclaringClass().getName());
	buf.append(".");
	buf.append(getName());
	return buf.toString();
    }
}
