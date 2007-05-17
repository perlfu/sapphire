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
package org.jikesrvm.compilers.opt;

/**
 * Describes a reservation on a particular resource.
 * A reservation is for a continuous period of time.
 * Used in OPT_OperatorClass.
 *
 * @see OPT_OperatorClass
 */
final class OPT_ResourceReservation {
  // Resource Class.
  private int rclass;
  /**
   * Start Time.
   */
  final int start;
  /**
   * Duration of Use.
   */
  final int duration;

  /**
   * Creates a new reservation for specified resource class
   * starting at 0 with given duration.
   *
   * @param rclass resource class
   * @param duration duration
   * @see #OPT_ResourceReservation(int,int,int)
   */
  public OPT_ResourceReservation(int rclass, int duration) {
    this(rclass, 0, duration);
  }

  /**
   * Creates a new reservation for specified resource class
   * starting at specified time with given duration.
   *
   * @param rclass resource class
   * @param start start time
   * @param duration duration
   */
  public OPT_ResourceReservation(int rclass, int start, int duration) {
    this.rclass = rclass;
    this.start = start;
    this.duration = duration;
  }

  /**
   * The resource class of this reservation.
   *
   * @return resource class of this reservation
   */
  public int rclass() {
    return (rclass & 0x7FFFFFFF);
  }

  /**
   * Checks whether this reservation is for all available units of the class.
   *
   * @return true if it's a global reservation, false otherwise
   */
  public boolean isGlobal() {
    return (rclass & 0x80000000) != 0;
  }

  // Compares this reservation with another reservation
  // For internal use only.
  private int compareTo(OPT_ResourceReservation r) {
    if (rclass() != r.rclass()) {
      return rclass() - r.rclass();
    }
    if (rclass != r.rclass) {
      // if either of them is global then global goes first
      return r.rclass - rclass;
    }
    if (start != r.start) {
      return start - r.start;
    }
    return duration - r.duration;
  }

  /**
   * Sorts an array of reservations in accordance with internal ordering.
   *
   * @param usage array of reservations
   */
  public static void sort(OPT_ResourceReservation[] usage) {
    // bubble sort
    for (int i = 0; i < usage.length; i++) {
      for (int j = i; j > 0 && usage[j - 1].compareTo(usage[j]) > 0; j--) {
        OPT_ResourceReservation t = usage[j];
        usage[j] = usage[j - 1];
        usage[j - 1] = t;
      }
    }
  }

  /**
   * Compares this object against the specified object.
   *
   * @param o   The object to compare with
   * @return <code>true</code> if the objects are the same; <code>false</code> otherwise.
   */
  public boolean equals(Object o) {
    if (!(o instanceof OPT_ResourceReservation)) {
      return false;
    }
    return compareTo((OPT_ResourceReservation) o) == 0;
  }

  /**
   * Checks whether this reservation conflicts with specified reservation.
   *
   * @param rsrv the reservation to check
   * @return true if the reservations conflict; false otherwise.
   */
  public boolean conflicts(OPT_ResourceReservation rsrv) {
    return (rclass() == rsrv.rclass() && start < rsrv.start + rsrv.duration && start + duration > rsrv.start);
  }
}



