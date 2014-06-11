package org.mmtk.utility.options;

import org.vmutil.options.IntOption;

public class ConcurrentCopyTransactionSize extends IntOption {
  public ConcurrentCopyTransactionSize() {
    super(Options.set, "Concurrent Copy Transaction Size", "Concurrent copy transaction bytes", 64);
  }
  
  /**
   * Only accept values between 0 and 32768 (inclusive)
   */
  @Override
  protected void validate() {
    failIf((this.value < 0) || (this.value > 32768), "Transaction must be between 0 and 32768");
  }
}
