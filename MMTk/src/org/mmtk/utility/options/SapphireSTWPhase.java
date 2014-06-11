package org.mmtk.utility.options;

import org.vmutil.options.StringOption;

public class SapphireSTWPhase extends StringOption {
	public static String ALLOC = "alloc";
	public static String COPY = "copy";
	public static String FLIP = "flip";
	public static String ROOT = "root";
	
  public SapphireSTWPhase() {
    super(Options.set, "Sapphire STW Phase",
        "Phases that should be performed in stop-the-world manner, separated with comma", "");
  }

  public boolean isStopTheWorld(String phaseId) {
  	return value.contains(phaseId);
  }
}
