/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.mmtk.utility.Log;

import org.vmmagic.pragma.*;

/**
 * A time option that stores values at a microsecond granularity.
 * 
 *
 * @author Daniel Frampton
 */
public class MicrosecondsOption extends Option {
  // values
  protected int defaultValue;
  protected int value;

  /**
   * Create a new microsecond option.
   * 
   * @param name The space separated name for the option.
   * @param desc The purpose of the option
   * @param defaultUs The default value of the option (usec).
   */
  protected MicrosecondsOption(String name, String desc, int defaultUs) {
    super(MICROSECONDS_OPTION, name, desc);
    this.value = this.defaultValue = defaultUs;
  }

  /**
   * Read the current value of the option in microseconds.
   * 
   * @return The option value.
   */
  @Uninterruptible
  public int getMicroseconds() { 
    return this.value;
  }

  /**
   * Read the current value of the option in milliseconds.
   * 
   * @return The option value.
   */
  @Uninterruptible
  public int getMilliseconds() { 
    return this.value / 1000;
  }

  /**
   * Read the default value of the option in microseconds.
   * 
   * @return The default value.
   */
  @Uninterruptible
  public int getDefaultMicroseconds() { 
    return this.defaultValue;
  }

  /**
   * Read the default value of the option in milliseconds.
   * 
   * @return The default value.
   */
  @Uninterruptible
  public int getDefaultMilliseconds() { 
    return this.defaultValue / 1000;
  }

  /**
   * Update the value of the option, echoing the change if the echoOptions
   * option is set. An error occurs if the value is negative, and then the
   * validate method is called to allow subclasses to perform any additional
   * validation.
   * 
   * @param value The new value for the option.
   */
  public void setMicroseconds(int value) {
    int oldValue = this.value;
    if (Options.echoOptions.getValue()) {
      Log.write("Option '");
      Log.write(this.getKey());
      Log.write("' set ");
      Log.write(oldValue);
      Log.write(" -> ");
      Log.writeln(value);
    }
    failIf(value < 0, "Unreasonable " + this.getName() + " value");
    this.value = value;
    validate();
  }

  /**
   * Log the option value in raw format - delegate upwards
   * for fancier formatting.
   * 
   * @param format Output format (see Option.java for possible values)
   */
  @Override
  void log(int format) {
    switch (format) {
      case RAW:
        Log.write(value);
        break;
      default:
        super.log(format);
    }
  }
}
