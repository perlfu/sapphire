package org.mmtk.utility.options;

import org.vmutil.options.StringOption;

public class ConcurrentTriggerMethod extends StringOption {
  public static final int ALLOCATION  = 0;
  public static final int PERCENTAGE  = 1;
  public static final int PERIOD      = 2;
  public static final int TIME        = 3;
  public static final int OOGC_ALLOCATION = 4;
  
  public ConcurrentTriggerMethod() {
    super(Options.set, "Concurrent Trigger Method",
        "Concurrent Trigger Method (percentage, allocaton, period, time, OOGCAllocation)", "percentage");
  }
  
  public int method() {
    if (value.equals("allocation")) return ALLOCATION;
    if (value.equals("percentage")) return PERCENTAGE;
    if (value.equals("period")) return PERIOD;
    if (value.equals("time")) return TIME;
    if (value.equals("OOGCAllocation")) return OOGC_ALLOCATION;
    return -1;
  }
  
  @Override
  protected void validate() {
    failIf(method() < 0, "concurrent trigger should be one of percentage, allocation, time");
  }
}
