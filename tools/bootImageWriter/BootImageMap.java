/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import com.ibm.JikesRVM.*;

import org.vmmagic.unboxed.*;

/**
 * Correlate objects in host jdk with corresponding objects in target rvm
 * bootimage.
 *
 * @author Derek Lieber
 * @version 03 Jan 2000
 */
public class BootImageMap extends BootImageWriterMessages
  implements BootImageWriterConstants
{
  /**
   * Key->Entry map
   */
  private static final Hashtable keyToEntry;

  /**
   * objectId->Entry map
   */
  private static final ArrayList objectIdToEntry;

  /**
   * Entry used to represent null object
   */
  private static final Entry nullEntry;

  /**
   * Unique ID value
   */
  private static int idGenerator;

  /**
   * Create unique ID number
   */
  private static Address newId() {
      return Address.fromIntZeroExtend(idGenerator++);
  }

  /**
   * Prepare for use.
   */
  static {
    keyToEntry      =  new Hashtable(5000);
    objectIdToEntry =  new ArrayList(5000);
    idGenerator = 0;
    // predefine "null" object
    nullEntry = new Entry(newId(), null, Address.zero());
    // slot 0 reserved for "null" object entry
    objectIdToEntry.add(nullEntry);
  }

  /**
   * Key for looking up map entry.
   */
  private static class Key {
    /**
     * JDK object.
     */
    final Object jdkObject;

    /**
     * Constructor.
     * @param jdkObject the object to associate with the key
     */
    public Key(Object jdkObject) { this.jdkObject = jdkObject; }

    /**
     * Returns a hash code value for the key.
     * @return a hash code value for this key
     */
    public int hashCode() { return System.identityHashCode(jdkObject); }

    /**
     * Indicates whether some other key is "equal to" this one.
     * @param that the object with which to compare
     * @return true if this key is the same as the that argument;
     *         false otherwise
     */
    public boolean equals(Object that) {
      return (that instanceof Key) && jdkObject == ((Key)that).jdkObject;
    }
  }

  /**
   * Map entry associated with a key.
   */
  public static class Entry {
    /**
     * Unique id associated with a jdk/rvm object pair.
     */
    final Address objectId;

    /**
     * JDK object.
     */
    final Object jdkObject;

    /**
     * Address of corresponding rvm object in bootimage
     * (OBJECT_NOT_ALLOCATED --> hasn't been written to image yet)
     */
    Address imageAddress;

    /**
     * Constructor.
     * @param objectId unique id
     * @param jdkObject the JDK object
     * @param imageAddress the address of the object in the bootimage
     */
    public Entry(Address objectId, Object jdkObject, Address imageAddress) {
      this.objectId     = objectId;
      this.jdkObject    = jdkObject;
      this.imageAddress = imageAddress;
    }
  }

  /**
   * Find or create map entry for a jdk/rvm object pair.
   * @param jdkObject JDK object
   * @return map entry for the given object
   */
  public static Entry findOrCreateEntry(Object jdkObject) {
    if (jdkObject == null)
      return nullEntry;

    synchronized (BootImageMap.class) {
      Key key   = new Key(jdkObject);
      Entry entry = (Entry) keyToEntry.get(key);
      if (entry == null) {
        entry = new Entry(newId(), jdkObject, OBJECT_NOT_ALLOCATED);
        keyToEntry.put(key, entry);
        objectIdToEntry.add(entry);
      }   
      return entry;
    }
  }

  /**
   * Get jdk object corresponding to an object id.
   * @param objectId object id
   * @return jdk object
   */
  public static Object getObject(int objectId) {
    return ((Entry) objectIdToEntry.get(objectId)).jdkObject;
  }

  /**
   * Get bootimage offset of an object.
   * @param jdkObject JDK object
   * @return offset of corresponding rvm object within bootimage, in bytes
   */
  public static Address getImageAddress(Object jdkObject, boolean fatalIfNotFound) {
    BootImageMap.Entry mapEntry = BootImageMap.findOrCreateEntry(jdkObject);
    if (mapEntry.imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
      if (fatalIfNotFound) {
        fail(jdkObject + " is not in bootimage");
      } else {
        return Address.zero();
      }
    }
    return mapEntry.imageAddress;
  }

  /**
   * @return enumeration of all the entries
   */
  public static Enumeration elements() {
    return keyToEntry.elements();
  }
}

